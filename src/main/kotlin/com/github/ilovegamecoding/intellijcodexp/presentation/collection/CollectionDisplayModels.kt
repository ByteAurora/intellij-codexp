package com.github.ilovegamecoding.intellijcodexp.presentation.collection

import com.github.ilovegamecoding.intellijcodexp.CodeXPBundle
import com.github.ilovegamecoding.intellijcodexp.domain.CodeXPGrowthPolicy
import com.github.ilovegamecoding.intellijcodexp.domain.collection.CollectionKind
import com.github.ilovegamecoding.intellijcodexp.domain.collection.CollectionSession
import com.github.ilovegamecoding.intellijcodexp.domain.collection.CrestDesign
import com.github.ilovegamecoding.intellijcodexp.presentation.dashboard.number

/** No raw definition/name on locked entries: search, tooltips and accessibility share this projection. */
data class CollectionEntry(
    val id: String,
    val kind: CollectionKind,
    val name: String,
    val condition: String,
    val progress: String,
    val fraction: Double,
    val owned: Boolean,
    val unseen: Boolean,
    val equipped: Boolean,
    val choice: Boolean,
    val canAcquire: Boolean,
    val design: CrestDesign?,
    val conditionHint: String,
) {
    val status: String get() =
        when {
            equipped -> CodeXPBundle.message("collection.status.equipped")
            unseen -> CodeXPBundle.message("collection.status.new")
            owned -> CodeXPBundle.message("collection.filter.owned")
            canAcquire -> CodeXPBundle.message("collection.status.available")
            else -> CodeXPBundle.message("collection.filter.locked")
        }
    val accessibleName: String get() = CodeXPBundle.message("collection.entry.accessible", name, condition, progress, status)

    fun matches(query: String): Boolean = "$name $condition".contains(query.trim(), ignoreCase = true)
}

data class CollectionDisplaySnapshot(
    val session: CollectionSession,
    val entries: List<CollectionEntry>,
    val displayedLevel: Int,
    val previousLevel: Int,
    val credits: Long,
    val equippedCrest: CrestDesign?,
    val equippedTitle: String?,
    val reducedMotion: Boolean,
    val subtle: Boolean,
    val choiceProgressXP: Long,
    val choiceIntervalXP: Long,
    val migrationNotice: String?,
    val storageProblem: String?,
) {
    val choiceCreditHelp: String get() =
        if (displayedLevel < CodeXPGrowthPolicy.MAX_LEVEL) {
            CodeXPBundle.message(
                "collection.credits.rule",
                CodeXPGrowthPolicy.MAX_LEVEL,
                number(CodeXPGrowthPolicy.CHOICE_INTERVAL_XP),
                number(CodeXPGrowthPolicy.CHOICE_INTERVAL_INCREMENT_XP),
                number(CodeXPGrowthPolicy.CHOICE_MAX_INTERVAL_XP),
            )
        } else {
            CodeXPBundle.message("collection.credits.next", number(choiceIntervalXP - choiceProgressXP))
        }
}
