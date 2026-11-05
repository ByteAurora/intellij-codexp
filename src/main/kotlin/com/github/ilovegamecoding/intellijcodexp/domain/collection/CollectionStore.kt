package com.github.ilovegamecoding.intellijcodexp.domain.collection

import com.github.ilovegamecoding.intellijcodexp.models.CodeXPCollectionState

/** Opaque, in-memory lifetime token. Reset invalidates commands queued by every old window. */
class CollectionSession internal constructor()

enum class CollectionOutcome {
    APPLIED,
    UNCHANGED,
    UNKNOWN_ITEM,
    NOT_OWNED,
    ALREADY_OWNED,
    WRONG_KIND,
    NOT_CHOOSABLE,
    LOCKED,
    INSUFFICIENT_CREDITS,
    INVALID_ENTITLEMENT,
    CREDIT_OVERFLOW,
    READ_ONLY_STATE,
    SAVE_FAILED,
    STALE_SESSION,
}

data class CollectionResult(
    val outcome: CollectionOutcome,
    val acquiredIds: Set<String> = emptySet(),
    val creditsAdded: Long = 0,
)

/**
 * One shared owner per profile, injected into all windows. Neither an IDE service nor disk storage.
 * Transactions and save snapshots take the same lock; callers never receive live mutable state.
 * A future persistence adapter must save the entire snapshot atomically, including its entitlement
 * watermarks, and must not replace this owner with one instance per project/window.
 */
class CollectionStore(
    initialState: CodeXPCollectionState = CodeXPCollectionState(),
) {
    private val lock = Any()
    private var state = initialState.detachedCopy()
    private var currentSession = CollectionSession()

    fun session(): CollectionSession = synchronized(lock) { currentSession }

    fun snapshot(): CodeXPCollectionState = synchronized(lock) { state.detachedCopy() }

    internal fun update(
        session: CollectionSession,
        action: (CodeXPCollectionState) -> CollectionResult,
    ): CollectionResult =
        synchronized(lock) {
            if (session !== currentSession) return@synchronized CollectionResult(CollectionOutcome.STALE_SESSION)
            if (!isWritable()) return@synchronized CollectionResult(CollectionOutcome.READ_ONLY_STATE)
            val candidate = state.detachedCopy()
            val result = action(candidate)
            if (result.outcome == CollectionOutcome.APPLIED) state = candidate
            result
        }

    /** Clears all collection fields, including unknown IDs and grant watermarks. No profile is touched. */
    fun reset(session: CollectionSession): CollectionResult =
        synchronized(lock) {
            if (session !== currentSession) return@synchronized CollectionResult(CollectionOutcome.STALE_SESSION)
            state = CodeXPCollectionState()
            currentSession = CollectionSession()
            CollectionResult(CollectionOutcome.APPLIED)
        }

    private fun isWritable(): Boolean =
        state.schemaVersion == 1 && state.choiceCredits >= 0 &&
            state.creditedEntitlements.all { (source, credited) -> source.isNotBlank() && credited >= 0 }
}
