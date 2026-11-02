package com.github.ilovegamecoding.intellijcodexp.presentation.dashboard

import com.github.ilovegamecoding.intellijcodexp.domain.CodeXPProgressResult
import com.github.ilovegamecoding.intellijcodexp.listeners.CodeXPEffectListener
import com.github.ilovegamecoding.intellijcodexp.listeners.CodeXPStateListener
import com.github.ilovegamecoding.intellijcodexp.models.CodeXPState
import com.github.ilovegamecoding.intellijcodexp.services.CodeXPService
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.util.Disposer
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import javax.swing.SwingUtilities

/** One app-state subscription per dashboard; immutable snapshots cross onto the EDT. */
internal class CodeXPDashboardController(
    private val codeXPService: CodeXPService,
    private val dashboard: CodeXPDashboardPanel,
    private val dashboardDisposable: Disposable,
) {
    private data class Update(
        val state: CodeXPState,
        val snapshot: DashboardSnapshot,
        val changedIds: Set<String>,
        val reset: Boolean,
    )

    private val pending = AtomicReference<Update?>()
    private val scheduled = AtomicBoolean(false)

    @Volatile private var disposed = false
    private var initialized = false
    private var lastState: CodeXPState? = null
    private var observedState: CodeXPState? = null
    private var completed = emptyList<DashboardChallenge>()

    fun initialize() {
        if (initialized) return
        initialized = true
        Disposer.register(dashboardDisposable, dashboard)
        Disposer.register(
            dashboardDisposable,
            Disposable {
                disposed = true
                pending.set(null)
            },
        )
        dashboard.onNicknameChanged = codeXPService::updateNickname
        dashboard.onBecameVisible = { refresh() }
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
                lastState = null
                refresh()
            },
        )
        refresh()
    }

    private fun refresh(changedIds: Set<String> = emptySet()) {
        if (disposed) return
        val state = codeXPService.state
        val reset = observedState != null && observedState !== state
        observedState = state
        if (lastState !== state || completed.size != state.completedChallenges.size) {
            completed = state.completedChallenges.asReversed().map(DashboardChallenge::from)
            lastState = state
        }
        val snapshot = DashboardSnapshot.from(state, completed)
        if (SwingUtilities.isEventDispatchThread()) {
            val waiting = pending.getAndSet(null)
            if (reset) dashboard.clearTransientEffects()
            dashboard.apply(snapshot, (waiting?.takeIf { it.state === state }?.changedIds.orEmpty() + changedIds).takeLastBounded())
        } else {
            pending.updateAndGet { previous -> Update(state, snapshot, (previous?.takeIf { it.state === state }?.changedIds.orEmpty() + changedIds).takeLastBounded(), reset || previous?.reset == true) }
            schedule()
        }
    }

    private fun schedule() {
        if (!scheduled.compareAndSet(false, true)) return
        SwingUtilities.invokeLater {
            scheduled.set(false)
            val update = pending.getAndSet(null)
            if (!disposed && update != null && update.state === codeXPService.state) {
                if (update.reset) dashboard.clearTransientEffects()
                dashboard.apply(update.snapshot, update.changedIds)
            }
            if (!disposed && pending.get() != null) schedule()
        }
    }

    private fun Set<String>.takeLastBounded(): Set<String> = if (size <= 128) this else toList().takeLast(128).toSet()
}
