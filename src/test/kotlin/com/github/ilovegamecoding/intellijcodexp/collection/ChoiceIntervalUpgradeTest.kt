package com.github.ilovegamecoding.intellijcodexp.collection

import com.github.ilovegamecoding.intellijcodexp.domain.CodeXPGrowthPolicy
import com.github.ilovegamecoding.intellijcodexp.models.CodeXPCollectionRecord
import com.github.ilovegamecoding.intellijcodexp.models.CodeXPState
import com.github.ilovegamecoding.intellijcodexp.services.CodeXPCollectionCoordinator
import com.github.ilovegamecoding.intellijcodexp.services.CodeXPCollectionPersistence
import com.github.ilovegamecoding.intellijcodexp.services.FileCollectionPersistence
import com.github.ilovegamecoding.intellijcodexp.services.MemoryCollectionPersistence
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import java.io.IOException
import java.nio.file.Files

class ChoiceIntervalUpgradeTest : BasePlatformTestCase() {
    fun testFixedFiftyThousandCandidateWithAnOlderAnchorUpgradesOnlyOnce() {
        val storage = FileCollectionPersistence(Files.createTempDirectory("codexp-choice-v3"))
        val profile = previousCandidate(storage)
        profile.xp += 50000
        val record = storage.load()!!
        record.checkpoint = profile.detachedCopy()
        record.migration =
            record.migration!!.copy(
                version = 3,
                policyId = CodeXPGrowthPolicy.PREVIOUS_FIXED_CHOICE_ID,
                intervalXP = 50000,
                choiceAnchorXP = 125000,
                choiceAnchorCredits = 5,
            )
        record.collection.creditedEntitlements["postcap.intervals.v1"] = 6
        storage.save(record)
        var coordinator = CodeXPCollectionCoordinator(storage)
        repeat(3) {
            coordinator = CodeXPCollectionCoordinator(storage)
            coordinator.load(profile)
            assertNull(coordinator.snapshot().storageProblem)
            assertEquals(3L, coordinator.snapshot().credits)
            assertEquals(12345L, coordinator.snapshot().choiceProgressXP)
            assertEquals(80000L, coordinator.snapshot().choiceIntervalXP)
            assertEquals(175000L, storage.load()!!.migration!!.choiceAnchorXP)
            assertNull(coordinator.takeChoiceAward())
        }
        profile.xp += 67655
        coordinator.reconcile()
        assertEquals(4L, coordinator.snapshot().credits)
        assertEquals(85000L, coordinator.snapshot().choiceIntervalXP)
        assertEquals(0L, coordinator.snapshot().choiceProgressXP)
    }

    fun testOldBalanceAndActualPartialXpSurviveUpgradeSpendAndFileReload() {
        val storage = FileCollectionPersistence(Files.createTempDirectory("codexp-choice-interval"))
        val profile = previousCandidate(storage)
        var coordinator = CodeXPCollectionCoordinator(storage)
        val restored = coordinator.load(profile)
        val xp = restored.xp
        val offset = restored.levelOffset
        assertEquals(3L, coordinator.snapshot().credits)
        assertEquals(12345L, coordinator.snapshot().choiceProgressXP)
        assertEquals(75000L, coordinator.snapshot().choiceIntervalXP)
        assertNull(coordinator.takeChoiceAward())
        val item = coordinator.snapshot().entries.first { it.canAcquire }
        coordinator.choose(coordinator.snapshot().session, item.id)
        coordinator.equip(coordinator.snapshot().session, item.kind, item.id)
        repeat(3) {
            coordinator = CodeXPCollectionCoordinator(storage)
            coordinator.load(restored)
            assertEquals(xp, restored.xp)
            assertEquals(offset, restored.levelOffset)
            assertEquals(2L, coordinator.snapshot().credits)
            assertEquals(12345L, coordinator.snapshot().choiceProgressXP)
            assertEquals(item.design, coordinator.snapshot().equippedCrest)
            assertNull(coordinator.takeChoiceAward())
        }
        restored.xp += 62654
        coordinator.reconcile()
        assertEquals(2L, coordinator.snapshot().credits)
        assertEquals(74999L, coordinator.snapshot().choiceProgressXP)
        restored.xp++
        coordinator.reconcile()
        assertEquals(3L, coordinator.snapshot().credits)
        assertEquals(0L, coordinator.snapshot().choiceProgressXP)
        assertEquals(1L, coordinator.takeChoiceAward()!!.count)
        assertEquals(6L, coordinator.savedRecord().collection.creditedEntitlements["postcap.intervals.v1"])
        assertTrue(coordinator.reset(CodeXPState()))
        assertEquals(0L, coordinator.savedRecord().migration!!.choiceAnchorXP)
        assertEquals(0L, coordinator.savedRecord().migration!!.choiceAnchorCredits)
    }

    fun testUpgradeCommitFailureCanRetryWithoutDoubleGranting() {
        val memory = MemoryCollectionPersistence()
        val profile = previousCandidate(memory)
        var fail = true
        val storage =
            object : CodeXPCollectionPersistence by memory {
                override fun save(record: CodeXPCollectionRecord) {
                    if (fail) throw IOException("choice migration interrupted")
                    memory.save(record)
                }
            }
        val failed = CodeXPCollectionCoordinator(storage)
        failed.load(profile)
        assertNotNull(failed.snapshot().storageProblem)
        assertEquals(2, memory.load()!!.migration!!.version)
        assertEquals(3L, memory.load()!!.collection.choiceCredits)
        fail = false
        assertTrue(failed.reconcile())
        assertEquals(4, memory.load()!!.migration!!.version)
        val restarted = CodeXPCollectionCoordinator(storage)
        restarted.load(profile)
        assertNull(restarted.snapshot().storageProblem)
        assertEquals(3L, restarted.snapshot().credits)
        assertEquals(12345L, restarted.snapshot().choiceProgressXP)
    }

    fun testUnreconciledOldEarningsAreSettledOnceWithoutCelebration() {
        val storage = MemoryCollectionPersistence()
        val profile = previousCandidate(storage)
        profile.xp += 25000
        val coordinator = CodeXPCollectionCoordinator(storage)
        coordinator.load(profile)
        assertEquals(4L, coordinator.snapshot().credits)
        assertEquals(12345L, coordinator.snapshot().choiceProgressXP)
        assertNull(coordinator.takeChoiceAward())
        repeat(3) { coordinator.reconcile() }
        assertEquals(4L, coordinator.snapshot().credits)
    }

    fun testBelowCapUpgradeDoesNotApplyTheLevelOffsetAgain() {
        val storage = MemoryCollectionPersistence()
        val original = CodeXPCollectionCoordinator(storage)
        val profile = original.load(CodeXPState(xp = 500))
        val offset = profile.levelOffset
        val record = original.savedRecord()
        record.migration = record.migration!!.copy(version = 2, policyId = CodeXPGrowthPolicy.PREVIOUS_CHOICE_ID, intervalXP = 25000)
        storage.save(record)
        val coordinator = CodeXPCollectionCoordinator(storage)
        coordinator.load(profile)
        assertEquals(offset, profile.levelOffset)
        assertEquals(500L, profile.xp)
        assertEquals(1L, coordinator.snapshot().credits)
        profile.xp = CodeXPGrowthPolicy.capXP - offset + 49999
        coordinator.reconcile()
        assertEquals(1L, coordinator.snapshot().credits)
        profile.xp++
        coordinator.reconcile()
        assertEquals(2L, coordinator.snapshot().credits)
    }

    fun testLargestXpAndInvalidPolicyRemainSafe() {
        val storage = MemoryCollectionPersistence()
        val profile = previousCandidate(storage)
        profile.xp = Long.MAX_VALUE
        val coordinator = CodeXPCollectionCoordinator(storage)
        coordinator.load(profile)
        assertNull(coordinator.snapshot().storageProblem)
        assertTrue(coordinator.snapshot().credits > 0)
        val credits = coordinator.snapshot().credits
        repeat(3) { coordinator.reconcile() }
        assertEquals(credits, coordinator.snapshot().credits)
        val invalid = coordinator.savedRecord()
        invalid.migration!!.intervalXP = 0
        storage.save(invalid)
        val readonly = CodeXPCollectionCoordinator(storage)
        readonly.load(profile)
        assertNotNull(readonly.snapshot().storageProblem)
        assertFalse(readonly.reconcile())
    }

    private fun previousCandidate(storage: CodeXPCollectionPersistence): CodeXPState {
        val coordinator = CodeXPCollectionCoordinator(storage)
        val profile = coordinator.load(CodeXPState(xp = CodeXPGrowthPolicy.capXP))
        profile.xp += 5 * 25000 + 12345
        coordinator.checkpoint()
        val record = coordinator.savedRecord()
        record.migration = record.migration!!.copy(version = 2, policyId = CodeXPGrowthPolicy.PREVIOUS_CHOICE_ID, intervalXP = 25000)
        record.collection.creditedEntitlements["postcap.intervals.v1"] = 5
        record.collection.choiceCredits = 3 // Historical spending must not be refunded.
        storage.save(record)
        return profile
    }
}
