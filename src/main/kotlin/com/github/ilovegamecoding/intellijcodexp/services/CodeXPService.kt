package com.github.ilovegamecoding.intellijcodexp.services

import com.github.ilovegamecoding.intellijcodexp.domain.CodeXPProgressEngine
import com.github.ilovegamecoding.intellijcodexp.domain.collection.CollectionKind
import com.github.ilovegamecoding.intellijcodexp.domain.collection.CollectionOutcome
import com.github.ilovegamecoding.intellijcodexp.domain.collection.CollectionResult
import com.github.ilovegamecoding.intellijcodexp.domain.collection.CollectionSession
import com.github.ilovegamecoding.intellijcodexp.enums.EffectStyle
import com.github.ilovegamecoding.intellijcodexp.enums.Event
import com.github.ilovegamecoding.intellijcodexp.listeners.CodeXPEffectListener
import com.github.ilovegamecoding.intellijcodexp.listeners.CodeXPEventListener
import com.github.ilovegamecoding.intellijcodexp.listeners.CodeXPStateListener
import com.github.ilovegamecoding.intellijcodexp.models.CodeXPConfiguration
import com.github.ilovegamecoding.intellijcodexp.models.CodeXPState
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.PathManager
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.util.messages.MessageBus
import java.nio.file.Path

/**
 * CodeXPService class
 *
 * This is the main class for the plugin. It manages the state of the plugin, including challenges, user progress, and more.
 * All data stored in the plugin is contained within this class.
 */
@Service(Service.Level.APP)
@State(
    name = "com.github.ilovegamecoding.intellijcodexp.services.CodeXP",
    storages = [Storage("CodeXP.xml")],
)
class CodeXPService internal constructor(
    persistence: CodeXPCollectionPersistence,
) : PersistentStateComponent<CodeXPState>,
    CodeXPEventListener,
    Disposable {
    constructor() : this(
        if (ApplicationManager.getApplication().isUnitTestMode) {
            MemoryCollectionPersistence(remember = false)
        } else {
            FileCollectionPersistence(Path.of(PathManager.getConfigPath(), "options", "codexp-collection"))
        },
    )

    /**
     * The state of the CodeXP plugin
     */
    private var codeXPState: CodeXPState = CodeXPState()
    internal var hasExistingProfile: Boolean = false
        private set

    /**
     * The message bus for the plugin
     */
    private val messageBus: MessageBus = ApplicationManager.getApplication().messageBus

    /**
     * The connection to the message bus
     */
    private val connection = messageBus.connect(this)

    private val progressEngine = CodeXPProgressEngine()
    private val progressCoordinator = CodeXPProgressCoordinator(messageBus)
    private val collections = CodeXPCollectionCoordinator(persistence)

    init {
        // Connect to the application message bus
        connection.subscribe(CodeXPEventListener.CODEXP_EVENT, this)
    }

    @Synchronized
    override fun getState(): CodeXPState = codeXPState

    override fun noStateLoaded() {
        super.noStateLoaded()
        initialize(hasPersistedProfile = false)
    }

    @Synchronized
    override fun loadState(codeXPState: CodeXPState) {
        this.codeXPState = codeXPState
        initialize()
        messageBus.syncPublisher(CodeXPStateListener.STATE).stateChanged()
    }

    override fun dispose() {
        collections.checkpoint()
    }

    @Synchronized
    override fun eventOccurred(
        event: Event,
        dataContext: DataContext?,
    ) {
        val result = progressEngine.recordEvent(codeXPState, event)
        collections.reconcile()
        progressCoordinator.handleProgressResult(
            state = codeXPState,
            result = result.copy(choiceAward = collections.takeChoiceAward()),
            dataContext = dataContext,
        )
    }

    @Synchronized
    fun updateNickname(nickname: String) {
        if (codeXPState.nickname == nickname) return
        codeXPState.nickname = nickname
        collections.markDirty()
        messageBus.syncPublisher(CodeXPStateListener.STATE).stateChanged()
    }

    /** Reuse the canonical initial state so future fields cannot accidentally survive a reset. */
    @Synchronized
    fun resetAllExceptNickname(): Boolean {
        val initial = CodeXPState(nickname = codeXPState.nickname)
        progressEngine.initialize(initial)
        if (!collections.reset(initial)) {
            messageBus.syncPublisher(CodeXPStateListener.STATE).stateChanged()
            return false
        }
        codeXPState = initial
        ApplicationManager.getApplication().getService(CodeXPGuideService::class.java).reset()
        messageBus.syncPublisher(CodeXPStateListener.STATE).stateChanged()
        return true
    }

    @Synchronized
    fun updateConfiguration(configuration: CodeXPConfiguration) {
        configuration.sanitizeMiniCrest()
        configuration.effectStyle = EffectStyle.fromStoredValue(configuration.effectStyle).storedValue
        codeXPState.codeXPConfiguration = configuration
        collections.markDirty()
        messageBus.syncPublisher(CodeXPStateListener.STATE).stateChanged()
    }

    @Synchronized
    fun collectionSnapshot() = collections.snapshot()

    @Synchronized
    fun equippedCrest() = collections.equippedCrest()

    @Synchronized
    fun equipCollection(
        session: CollectionSession,
        kind: CollectionKind,
        id: String?,
        dataContext: DataContext? = null,
    ): CollectionResult {
        val result = publishCollection(collections.equip(session, kind, id))
        if (result.outcome == CollectionOutcome.APPLIED && kind == CollectionKind.CREST && id != null) {
            collections.equippedCrest()?.let { design ->
                messageBus.syncPublisher(CodeXPEffectListener.EFFECTS).crestEquipped(
                    java.util.UUID
                        .randomUUID()
                        .toString(),
                    design,
                    dataContext,
                )
            }
        }
        return result
    }

    @Synchronized
    fun acknowledgeCollection(
        session: CollectionSession,
        id: String,
    ): CollectionResult = publishCollection(collections.acknowledge(session, id))

    @Synchronized
    fun chooseCollection(
        session: CollectionSession,
        id: String,
    ): CollectionResult = publishCollection(collections.choose(session, id))

    @Synchronized
    fun acknowledgeCollectionMigration(): CollectionResult = publishCollection(collections.acknowledgeMigration())

    private fun publishCollection(result: CollectionResult): CollectionResult {
        if (result.outcome == CollectionOutcome.APPLIED || result.outcome == CollectionOutcome.SAVE_FAILED) messageBus.syncPublisher(CodeXPStateListener.STATE).stateChanged()
        return result
    }

    /**
     * Initialize the plugin.
     */
    private fun initialize(hasPersistedProfile: Boolean = true) {
        codeXPState.codeXPConfiguration.sanitizeMiniCrest()
        codeXPState.codeXPConfiguration.effectStyle = EffectStyle.fromStoredValue(codeXPState.codeXPConfiguration.effectStyle).storedValue
        val legacyLineage = codeXPState.legacyLineage()
        progressEngine.initialize(codeXPState)
        codeXPState = collections.load(codeXPState, hasPersistedProfile, legacyLineage)
        hasExistingProfile = hasPersistedProfile || codeXPState.xp > 0 || codeXPState.completedChallenges.isNotEmpty()
        codeXPState.codeXPConfiguration.sanitizeMiniCrest()
        progressEngine.initialize(codeXPState)
    }
}
