package com.github.ilovegamecoding.intellijcodexp.services

import com.github.ilovegamecoding.intellijcodexp.domain.CodeXPProgressEngine
import com.github.ilovegamecoding.intellijcodexp.enums.EffectStyle
import com.github.ilovegamecoding.intellijcodexp.enums.Event
import com.github.ilovegamecoding.intellijcodexp.listeners.CodeXPEventListener
import com.github.ilovegamecoding.intellijcodexp.listeners.CodeXPStateListener
import com.github.ilovegamecoding.intellijcodexp.models.CodeXPConfiguration
import com.github.ilovegamecoding.intellijcodexp.models.CodeXPState
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.util.messages.MessageBus

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
class CodeXPService :
    PersistentStateComponent<CodeXPState>,
    CodeXPEventListener,
    Disposable {
    /**
     * The state of the CodeXP plugin
     */
    private var codeXPState: CodeXPState = CodeXPState()

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

    init {
        // Connect to the application message bus
        connection.subscribe(CodeXPEventListener.CODEXP_EVENT, this)
    }

    override fun getState(): CodeXPState = codeXPState

    override fun noStateLoaded() {
        super.noStateLoaded()
        initialize()
    }

    override fun loadState(codeXPState: CodeXPState) {
        this.codeXPState = codeXPState
        initialize()
        messageBus.syncPublisher(CodeXPStateListener.STATE).stateChanged()
    }

    override fun dispose() {
    }

    override fun eventOccurred(
        event: Event,
        dataContext: DataContext?,
    ) {
        progressCoordinator.handleProgressResult(
            state = codeXPState,
            result = progressEngine.recordEvent(codeXPState, event),
            dataContext = dataContext,
        )
    }

    fun updateNickname(nickname: String) {
        if (codeXPState.nickname == nickname) return
        codeXPState.nickname = nickname
        messageBus.syncPublisher(CodeXPStateListener.STATE).stateChanged()
    }

    /** Reuse the canonical initial state so future fields cannot accidentally survive a reset. */
    fun resetAllExceptNickname() {
        loadState(CodeXPState(nickname = codeXPState.nickname))
    }

    fun updateConfiguration(configuration: CodeXPConfiguration) {
        configuration.effectStyle = EffectStyle.fromStoredValue(configuration.effectStyle).storedValue
        codeXPState.codeXPConfiguration = configuration
        messageBus.syncPublisher(CodeXPStateListener.STATE).stateChanged()
    }

    /**
     * Initialize the plugin.
     */
    private fun initialize() {
        codeXPState.codeXPConfiguration.effectStyle = EffectStyle.fromStoredValue(codeXPState.codeXPConfiguration.effectStyle).storedValue
        progressEngine.initialize(codeXPState)
    }
}
