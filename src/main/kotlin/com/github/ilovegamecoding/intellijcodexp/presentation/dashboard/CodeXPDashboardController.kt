package com.github.ilovegamecoding.intellijcodexp.presentation.dashboard

import com.github.ilovegamecoding.intellijcodexp.domain.CodeXPGrowthPolicy
import com.github.ilovegamecoding.intellijcodexp.domain.CodeXPProgressResult
import com.github.ilovegamecoding.intellijcodexp.listeners.CodeXPEffectListener
import com.github.ilovegamecoding.intellijcodexp.listeners.CodeXPStateListener
import com.github.ilovegamecoding.intellijcodexp.models.CodeXPState
import com.github.ilovegamecoding.intellijcodexp.presentation.collection.CollectionDisplaySnapshot
import com.github.ilovegamecoding.intellijcodexp.services.CodeXPService
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.util.Disposer
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import javax.swing.SwingUtilities
import javax.swing.Timer

/** Record every event in the service, but build presentation snapshots only once per visible frame. */
internal class CodeXPDashboardController(
    private val codeXPService: CodeXPService,
    private val dashboard: CodeXPDashboardPanel,
    private val dashboardDisposable: Disposable,
    private val onCollectionSnapshot: (CollectionDisplaySnapshot) -> Unit = {},
) {
    private data class Update(
        val state: CodeXPState,
        val changedIds: Set<String>,
    )

    private val pending = AtomicReference<Update?>()
    private val scheduled = AtomicBoolean(false)
    private val timer = Timer(16) { flushPending() }.apply { isRepeats = false }
    var isCollectionVisible: () -> Boolean = { false }

    @Volatile private var disposed = false
    private var initialized = false
    private var lastState: CodeXPState? = null
    private var observedState: CodeXPState? = null
    private var completed = emptyList<DashboardChallenge>()
    private var renderedCollection: CollectionDisplaySnapshot? = null
    private var dashboardCollection: CollectionDisplaySnapshot? = null

    fun initialize() {
        if (initialized) return
        initialized = true
        Disposer.register(dashboardDisposable, dashboard)
        Disposer.register(
            dashboardDisposable,
            Disposable {
                disposed = true
                pending.set(null)
                timer.stop()
            },
        )
        dashboard.onNicknameChanged = codeXPService::updateNickname
        dashboard.onBecameVisible = ::refreshVisible
        val connection = ApplicationManager.getApplication().messageBus.connect(dashboardDisposable)
        connection.subscribe(
            CodeXPEffectListener.EFFECTS,
            object : CodeXPEffectListener {
                override fun progressRecorded(
                    result: CodeXPProgressResult,
                    dataContext: DataContext?,
                ) = refresh(setOfNotNull(result.updatedChallenge?.id, result.newChallenge?.id, result.completedChallenge?.id))
            },
        )
        connection.subscribe(
            CodeXPStateListener.STATE,
            CodeXPStateListener {
                if (SwingUtilities.isEventDispatchThread()) {
                    lastState = null
                    refreshVisible()
                } else {
                    refresh()
                }
            },
        )
        applyLatest(emptySet(), initialize = true)
    }

    private fun refresh(changedIds: Set<String> = emptySet()) {
        if (disposed) return
        val state = codeXPService.state
        pending.updateAndGet { previous -> Update(state, (previous?.takeIf { it.state === state }?.changedIds.orEmpty() + changedIds).takeLastBounded()) }
        if (!scheduled.compareAndSet(false, true)) return
        SwingUtilities.invokeLater { if (!disposed && scheduled.get()) timer.start() }
    }

    fun refreshVisible() {
        if (disposed) return
        timer.stop()
        scheduled.set(false)
        val update = pending.getAndSet(null)
        applyLatest(update?.takeIf { it.state === codeXPService.state }?.changedIds.orEmpty())
    }

    internal fun flushPending() {
        timer.stop()
        scheduled.set(false)
        val update = pending.getAndSet(null) ?: return
        if (!disposed) applyLatest(update.takeIf { it.state === codeXPService.state }?.changedIds.orEmpty())
    }

    private fun applyLatest(
        changedIds: Set<String>,
        initialize: Boolean = false,
    ) {
        val state = codeXPService.state
        val reset = observedState != null && observedState !== state
        observedState = state
        if (reset) dashboard.clearTransientEffects()
        val dashboardVisible = initialize || dashboard.isDashboardVisible
        val collectionVisible = initialize || isCollectionVisible()
        if (!dashboardVisible && !collectionVisible) return
        val collection = codeXPService.collectionSnapshot()
        if (collectionVisible && collection != renderedCollection) {
            renderedCollection = collection
            onCollectionSnapshot(collection)
        }
        if (!dashboardVisible) return
        if (lastState !== state || completed.size != state.completedChallenges.size) {
            completed = state.completedChallenges.asReversed().map(DashboardChallenge::from)
            lastState = state
        }
        val snapshot = DashboardSnapshot.from(state, completed)
        dashboard.apply(snapshot, changedIds)
        if (collection != dashboardCollection || collection.displayedLevel >= CodeXPGrowthPolicy.MAX_LEVEL) {
            dashboardCollection = collection
            dashboard.applyCollection(collection)
        }
    }

    private fun Set<String>.takeLastBounded(): Set<String> = if (size <= 128) this else toList().takeLast(128).toSet()
}
