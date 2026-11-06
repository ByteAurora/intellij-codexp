package com.github.ilovegamecoding.intellijcodexp.domain.collection

data class CollectionPreview(
    val id: String,
    /** Null means unavailable in this catalog; the raw ID is retained for future versions. */
    val definition: CollectionItemDefinition?,
    val owned: Boolean,
    val unseen: Boolean,
    val equipped: Boolean,
)

/** Pure collection commands. The existing XP engine, service, load path and UI do not invoke this. */
class CollectionEngine(
    private val catalog: CollectionCatalog,
    private val store: CollectionStore,
) {
    /** Locked entries may be previewed. Previewing does not acknowledge or equip anything. */
    fun preview(id: String): CollectionPreview {
        val state = store.snapshot()
        val definition = catalog.find(id)
        return CollectionPreview(
            id = id,
            definition = definition,
            owned = id in state.ownedIds,
            unseen = id in state.unseenIds,
            equipped = definition != null && id in state.ownedIds && state.equippedIds[definition.kind.storageKey] == id,
        )
    }

    /** Null requests the presentation's fallback; it never overwrites a stored unknown selection. */
    fun equipped(kind: CollectionKind): CollectionItemDefinition? {
        val state = store.snapshot()
        val id = state.equippedIds[kind.storageKey] ?: return null
        return catalog.find(id)?.takeIf { it.kind == kind && id in state.ownedIds }
    }

    /** Backfills every satisfied definition, including skipped tiers; never replaces equipment. */
    fun unlock(
        session: CollectionSession,
        facts: CollectionFacts,
    ): CollectionResult =
        store.update(session) { state ->
            val newlyOwned =
                catalog
                    .items()
                    .filter { item ->
                        val acquisition = item.acquisition
                        item.id !in state.ownedIds && acquisition is CollectionAcquisition.Automatic && acquisition.requirement.isMet(facts)
                    }.map { it.id }
                    .toSet()
            if (newlyOwned.isEmpty()) return@update CollectionResult(CollectionOutcome.UNCHANGED)
            state.ownedIds.addAll(newlyOwned)
            state.unseenIds.addAll(newlyOwned)
            CollectionResult(CollectionOutcome.APPLIED, acquiredIds = newlyOwned)
        }

    /**
     * Credit a monotonically increasing cumulative entitlement from an explicitly chosen policy.
     * This is not an XP conversion policy: the caller supplies the stable source ID and total earned.
     * Equal/older totals do nothing even after spending or reloading. Balance and watermark commit
     * together. Reusing a source for a different policy requires a separately reviewed migration.
     */
    fun recordChoiceEntitlement(
        session: CollectionSession,
        sourceId: String,
        totalEarned: Long,
    ): CollectionResult =
        store.update(session) { state ->
            if (sourceId.isBlank() || totalEarned < 0) return@update CollectionResult(CollectionOutcome.INVALID_ENTITLEMENT)
            val previous = state.creditedEntitlements[sourceId] ?: 0
            if (totalEarned <= previous) return@update CollectionResult(CollectionOutcome.UNCHANGED)
            val added = totalEarned - previous
            if (added > Long.MAX_VALUE - state.choiceCredits) return@update CollectionResult(CollectionOutcome.CREDIT_OVERFLOW)
            state.choiceCredits += added
            state.creditedEntitlements[sourceId] = totalEarned
            CollectionResult(CollectionOutcome.APPLIED, creditsAdded = added)
        }

    fun choose(
        session: CollectionSession,
        id: String,
        facts: CollectionFacts,
    ): CollectionResult =
        store.update(session) { state ->
            val item = catalog.find(id) ?: return@update CollectionResult(CollectionOutcome.UNKNOWN_ITEM)
            if (id in state.ownedIds) return@update CollectionResult(CollectionOutcome.ALREADY_OWNED)
            val acquisition = item.acquisition as? CollectionAcquisition.Choice ?: return@update CollectionResult(CollectionOutcome.NOT_CHOOSABLE)
            if (!acquisition.requirement.isMet(facts)) return@update CollectionResult(CollectionOutcome.LOCKED)
            if (state.choiceCredits < acquisition.cost) return@update CollectionResult(CollectionOutcome.INSUFFICIENT_CREDITS)
            state.choiceCredits -= acquisition.cost
            state.ownedIds.add(id)
            state.unseenIds.add(id)
            CollectionResult(CollectionOutcome.APPLIED, acquiredIds = setOf(id))
        }

    fun equip(
        session: CollectionSession,
        kind: CollectionKind,
        id: String?,
    ): CollectionResult =
        store.update(session) { state ->
            if (id != null) {
                val item = catalog.find(id) ?: return@update CollectionResult(CollectionOutcome.UNKNOWN_ITEM)
                if (item.kind != kind) return@update CollectionResult(CollectionOutcome.WRONG_KIND)
                if (id !in state.ownedIds) return@update CollectionResult(CollectionOutcome.NOT_OWNED)
            }
            if (state.equippedIds[kind.storageKey] == id) return@update CollectionResult(CollectionOutcome.UNCHANGED)
            if (id == null) state.equippedIds.remove(kind.storageKey) else state.equippedIds[kind.storageKey] = id
            CollectionResult(CollectionOutcome.APPLIED)
        }

    /** Explicit acknowledgement is independent of previewing, acquiring and equipping. */
    fun acknowledge(
        session: CollectionSession,
        id: String,
    ): CollectionResult =
        store.update(session) { state ->
            if (id !in state.ownedIds) return@update CollectionResult(CollectionOutcome.NOT_OWNED)
            if (!state.unseenIds.remove(id)) return@update CollectionResult(CollectionOutcome.UNCHANGED)
            CollectionResult(CollectionOutcome.APPLIED)
        }
}
