package com.github.ilovegamecoding.intellijcodexp.collection

import com.github.ilovegamecoding.intellijcodexp.domain.CodeXPGrowthPolicy
import com.github.ilovegamecoding.intellijcodexp.domain.CodeXPProgressEngine
import com.github.ilovegamecoding.intellijcodexp.domain.collection.CodeXPCollectionCatalog
import com.github.ilovegamecoding.intellijcodexp.domain.collection.CollectionRoute
import com.github.ilovegamecoding.intellijcodexp.enums.Event
import com.github.ilovegamecoding.intellijcodexp.models.CodeXPChallengeFactory
import com.github.ilovegamecoding.intellijcodexp.models.CodeXPState
import com.github.ilovegamecoding.intellijcodexp.services.CodeXPCollectionCoordinator
import com.github.ilovegamecoding.intellijcodexp.services.MemoryCollectionPersistence
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import java.io.File

class ChoiceRewardPacingTest : BasePlatformTestCase() {
    fun testEveryBoundaryAndTheCeilingWithoutLosingOverflowXp() {
        var boundary = 0L
        for (earned in 0L..200L) {
            val required = minOf(50000L + earned * 5000L, 100000L)
            val at = CodeXPGrowthPolicy.choiceProgress(boundary)
            assertEquals(earned, at.additionalCredits)
            assertEquals(0L, at.progressXP)
            assertEquals(required, at.requiredXP)
            val before = CodeXPGrowthPolicy.choiceProgress(boundary + required - 1)
            assertEquals(earned, before.additionalCredits)
            assertEquals(required - 1, before.progressXP)
            boundary += required
        }
        val huge = CodeXPGrowthPolicy.choiceProgress(Long.MAX_VALUE)
        assertEquals(10L + (Long.MAX_VALUE - 725000L) / 100000L, huge.additionalCredits)
        assertEquals((Long.MAX_VALUE - 725000L) % 100000L, huge.progressXP)
        assertEquals(100000L, huge.requiredXP)
        assertEquals(100000L, CodeXPGrowthPolicy.choiceIntervalXP(Long.MAX_VALUE))
    }

    fun testBurstOrFrequentSmallEventsProduceExactlyTheSameCreditsAndRemainder() {
        var earned = 0L
        var remainder = 0L
        val chunks = listOf(2L, 49998L, 3L, 399900L, 10L, 127L, 2000000L)
        for (xp in chunks) {
            val part = CodeXPGrowthPolicy.choiceProgress(remainder + xp, earned)
            earned += part.additionalCredits
            remainder = part.progressXP
        }
        val once = CodeXPGrowthPolicy.choiceProgress(chunks.sum())
        assertEquals(once.additionalCredits, earned)
        assertEquals(once.progressXP, remainder)
        val late = CodeXPGrowthPolicy.choiceProgress(399900L, 1000L)
        assertEquals(3L, late.additionalCredits)
        assertEquals(99900L, late.progressXP)
    }

    fun testGiftsSpendingAndIdleDaysDoNotChangeTheNextRequirement() {
        val coordinator = CodeXPCollectionCoordinator(MemoryCollectionPersistence())
        val profile = coordinator.load(CodeXPState(xp = CodeXPGrowthPolicy.capXP))
        assertEquals(1L, coordinator.snapshot().credits)
        assertEquals(50000L, coordinator.snapshot().choiceIntervalXP)
        profile.xp += 105000
        coordinator.reconcile()
        assertEquals(3L, coordinator.snapshot().credits)
        assertEquals(60000L, coordinator.snapshot().choiceIntervalXP)
        repeat(3) {
            val state = coordinator.snapshot()
            coordinator.choose(state.session, state.entries.first { it.canAcquire }.id)
        }
        assertEquals(0L, coordinator.snapshot().credits)
        repeat(30) { coordinator.reconcile() }
        assertEquals(60000L, coordinator.snapshot().choiceIntervalXP)
        assertEquals(0L, coordinator.snapshot().choiceProgressXP)
    }

    fun testAggregateScenarioModelMatchesActualEventEngine() {
        val engine = CodeXPProgressEngine()
        for (event in Event.entries.filter { it != Event.NONE }) {
            val state = CodeXPState()
            engine.initialize(state)
            repeat(2100) { engine.recordEvent(state, event) }
            assertEquals(event.name, state.xp, scenarioXP(mapOf(event to 2100), 1))
        }
    }

    fun testAssumedActiveDayScenariosKeepCollectionProgressWithoutInfiniteCostGrowth() {
        // Counted plugin events, not measured users or calendar days; paste is one event, not its text length.
        val scenarios =
            linkedMapOf(
                "Light editing" to listOf(1500, 5, 15, 15, 250, 40, 80, 20, 4, 8, 3, 200),
                "Regular coding" to listOf(5000, 10, 40, 40, 1000, 120, 300, 60, 20, 30, 10, 700),
                "Heavy coding" to listOf(12000, 25, 100, 100, 2200, 300, 700, 180, 40, 70, 30, 1400),
                "Review and debugging" to listOf(500, 10, 40, 60, 150, 20, 60, 30, 40, 50, 30, 1000),
            )
        val events = Event.entries.filter { it != Event.NONE }
        val items = CodeXPCollectionCatalog.content.items.count { it.route == CollectionRoute.CHOICE }
        assertEquals(18, items)
        val requirements = (0 until items).map { CodeXPGrowthPolicy.choiceIntervalXP(it.toLong()) }
        assertEquals(1525000L, requirements.sum())
        val rows = mutableListOf("scenario,active_days_to_MAX,post_MAX_days_fixed_50k,post_MAX_days_progressive,mean_XP_per_active_day_after_MAX")
        val budgets = mutableListOf("scenario," + events.joinToString(",") { it.name })
        for ((name, amounts) in scenarios) {
            val counts = events.zip(amounts).toMap()
            val totals = (0..600).map { scenarioXP(counts, it) }
            val capped = totals.indexOfFirst { it >= CodeXPGrowthPolicy.capXP }
            val fixed = totals.indexOfFirst { it >= CodeXPGrowthPolicy.capXP + 50000L * items } - capped
            val progressive = totals.indexOfFirst { CodeXPGrowthPolicy.choiceProgress((it - CodeXPGrowthPolicy.capXP).coerceAtLeast(0)).additionalCredits >= items } - capped
            assertTrue(capped > 0 && fixed > 0 && progressive > fixed)
            assertTrue(progressive < fixed * 2)
            if (name == "Regular coding") assertEquals(34, progressive)
            if (name == "Heavy coding") assertEquals(15, progressive)
            val average = (totals[capped + 90] - totals[capped]) / 90
            rows += "$name,$capped,$fixed,$progressive,$average"
            budgets += name + "," + amounts.joinToString(",")
        }
        val directory = File("build/reports/choice-pacing").apply { mkdirs() }
        File(directory, "assumed-event-budgets.csv").writeText(budgets.joinToString("\n") + "\n")
        File(directory, "scenario-results.csv").writeText(rows.joinToString("\n") + "\n")
    }

    private fun scenarioXP(
        daily: Map<Event, Int>,
        days: Int,
    ): Long =
        CodeXPChallengeFactory.createEventDefaultChallenges().sumOf { challenge ->
            var events = (daily[challenge.event] ?: 0).toLong() * days
            var goal = challenge.goal
            var reward = challenge.rewardXP
            var xp = events * challenge.event.xpValue
            while (events >= goal) {
                events -= goal
                xp += reward
                goal += challenge.goalIncrement
                reward += challenge.rewardXPIncrement
            }
            xp
        }
}
