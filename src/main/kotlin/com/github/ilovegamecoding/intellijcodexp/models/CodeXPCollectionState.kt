package com.github.ilovegamecoding.intellijcodexp.models

/**
 * Inert XML-serializable collection data. Not registered with the IDE or added to [CodeXPState].
 * Ownership, unread discoveries and equipment are independent; previewing never changes them.
 * IDs (including unknown item, slot and entitlement-source IDs) must survive a save/load cycle.
 * XP, levels, History and preferences remain exclusively in the existing profile.
 */
data class CodeXPCollectionState(
    var schemaVersion: Int = 1,
    var ownedIds: MutableSet<String> = mutableSetOf(),
    var unseenIds: MutableSet<String> = mutableSetOf(),
    var equippedIds: MutableMap<String, String> = mutableMapOf(),
    var choiceCredits: Long = 0,
    /** Cumulative entitlements already credited, not the remaining balance or a list of events. */
    var creditedEntitlements: MutableMap<String, Long> = mutableMapOf(),
) {
    internal fun detachedCopy(): CodeXPCollectionState =
        copy(
            ownedIds = ownedIds.toMutableSet(),
            unseenIds = unseenIds.toMutableSet(),
            equippedIds = equippedIds.toMutableMap(),
            creditedEntitlements = creditedEntitlements.toMutableMap(),
        )
}
