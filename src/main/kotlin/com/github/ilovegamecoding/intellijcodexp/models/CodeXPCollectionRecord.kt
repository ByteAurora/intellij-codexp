package com.github.ilovegamecoding.intellijcodexp.models

/** One atomic sidecar record: migration, entitlements, ownership, balance and a recovery checkpoint. */
data class CodeXPCollectionRecord(
    var schemaVersion: Int = 1,
    var generation: String = "",
    var resetCount: Long = 0,
    /** Fresh challenge IDs survive 2.2.0 round trips even when it strips collectionGeneration. */
    var generationAnchors: MutableSet<String> = mutableSetOf(),
    var migration: CodeXPCollectionMigration? = null,
    var collection: CodeXPCollectionState = CodeXPCollectionState(),
    var checkpoint: CodeXPState = CodeXPState(),
    /** Oldest typing challenge for each discarded legacy profile; 2.2.0 retains it until reset. */
    var retiredLegacyLineages: MutableSet<String> = mutableSetOf(),
) {
    fun detachedCopy(): CodeXPCollectionRecord =
        copy(
            generationAnchors = generationAnchors.toMutableSet(),
            retiredLegacyLineages = retiredLegacyLineages.toMutableSet(),
            migration = migration?.copy(),
            collection = collection.detachedCopy(),
            checkpoint = checkpoint.detachedCopy(),
        )
}

data class CodeXPCollectionMigration(
    var version: Int = 0,
    var policyId: String = "",
    var baselineXP: Long = 0,
    /** The old formula applied to baselineXP, not an asserted historical maximum. */
    var previousLevel: Int = 1,
    var capXP: Long = 0,
    var intervalXP: Long = 0,
    var noticePending: Boolean = false,
    var retroactiveItems: Int = 0,
    var levelOffset: Long = 0,
    /** Completed old intervals remain earned; XP after this boundary uses the current choice curve. */
    var choiceAnchorXP: Long = 0,
    var choiceAnchorCredits: Long = 0,
)
