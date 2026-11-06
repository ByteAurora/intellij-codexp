package com.github.ilovegamecoding.intellijcodexp.collection

import com.github.ilovegamecoding.intellijcodexp.domain.CodeXPProgressEngine
import com.github.ilovegamecoding.intellijcodexp.domain.collection.CollectionKind
import com.github.ilovegamecoding.intellijcodexp.domain.collection.CollectionOutcome
import com.github.ilovegamecoding.intellijcodexp.enums.Event
import com.github.ilovegamecoding.intellijcodexp.models.CodeXPChallenge
import com.github.ilovegamecoding.intellijcodexp.models.CodeXPCollectionRecord
import com.github.ilovegamecoding.intellijcodexp.models.CodeXPState
import com.github.ilovegamecoding.intellijcodexp.services.CodeXPCollectionCoordinator
import com.github.ilovegamecoding.intellijcodexp.services.CodeXPCollectionPersistence
import com.github.ilovegamecoding.intellijcodexp.services.FileCollectionPersistence
import com.github.ilovegamecoding.intellijcodexp.services.MemoryCollectionPersistence
import com.intellij.openapi.util.JDOMUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import java.io.IOException
import java.nio.file.Files

class CollectionRecoveryTest : BasePlatformTestCase() {
    fun testInterruptedFirstCommitCanRetryOnceIncludingStagedEarlierRcWrites() {
        for (earlierFormat in listOf(false, true)) {
            val directory = Files.createTempDirectory("codexp-first-write")
            val interrupted = CodeXPCollectionCoordinator(FileCollectionPersistence(directory) { throw IOException("first commit interrupted") })
            val before = interrupted.load(CollectionProductFixtures.profile(175, 0))
            assertNotNull(interrupted.snapshot().storageProblem)
            assertTrue(before.collectionGeneration.isEmpty())
            if (earlierFormat) {
                val prepared = directory.resolve("first.pending")
                val revision = JDOMUtil.load(prepared).getAttributeValue("revision")
                Files.move(prepared, directory.resolve("$revision.pending"))
                Files.writeString(directory.resolve("initialized"), "CodeXP collection storage v1\n")
            }

            val reopened = CodeXPCollectionCoordinator(FileCollectionPersistence(directory))
            reopened.load(before)
            assertNull(reopened.snapshot().storageProblem)
            val choice = reopened.snapshot().entries.first { it.canAcquire }
            assertEquals(1L, reopened.snapshot().credits)
            assertEquals(CollectionOutcome.APPLIED, reopened.choose(reopened.snapshot().session, choice.id).outcome)
            val again = CodeXPCollectionCoordinator(FileCollectionPersistence(directory))
            again.load(before)
            assertEquals(0L, again.snapshot().credits)
            assertEquals(CollectionOutcome.ALREADY_OWNED, again.choose(again.snapshot().session, choice.id).outcome)
        }
    }

    fun testInterruptedLaterCommitCannotDisguiseAMissingCommittedPointer() {
        val directory = Files.createTempDirectory("codexp-lost-commit")
        var failSave = false
        val coordinator = CodeXPCollectionCoordinator(FileCollectionPersistence(directory) { if (failSave) throw IOException("interrupted") })
        val profile = coordinator.load(CollectionProductFixtures.profile(175, 0))
        val choice = coordinator.snapshot().entries.first { it.canAcquire }
        assertEquals(CollectionOutcome.APPLIED, coordinator.choose(coordinator.snapshot().session, choice.id).outcome)
        failSave = true
        assertEquals(CollectionOutcome.SAVE_FAILED, coordinator.equip(coordinator.snapshot().session, choice.kind, choice.id).outcome)
        Files.writeString(directory.resolve("initialized"), "CodeXP collection storage v1\n")
        Files.delete(directory.resolve("current.xml"))
        val reopened = CodeXPCollectionCoordinator(FileCollectionPersistence(directory))
        reopened.load(profile)
        assertNotNull(reopened.snapshot().storageProblem)
        assertEquals(CollectionOutcome.READ_ONLY_STATE, reopened.choose(reopened.snapshot().session, choice.id).outcome)
        assertFalse(Files.exists(directory.resolve("current.xml")))
    }

    fun testEarlierRcOrphanedSpendCannotReinitializeEvenIfBothCommittedCopiesAreMissing() {
        val dir = Files.createTempDirectory("codexp-orphaned-spend")
        var fail = false
        val old = CodeXPCollectionCoordinator(FileCollectionPersistence(dir) { if (fail) throw IOException("pending spend") })
        val profile = old.load(CollectionProductFixtures.profile(175, 0))
        val choice = old.snapshot().entries.first { it.canAcquire }
        fail = true
        assertEquals(CollectionOutcome.SAVE_FAILED, old.choose(old.snapshot().session, choice.id).outcome)
        val pointer = dir.resolve("current.xml")
        val revision = JDOMUtil.load(pointer).getAttributeValue("revision")
        Files.delete(pointer)
        Files.delete(dir.resolve("$revision.xml"))
        Files.delete(dir.resolve("$revision.backup.xml"))
        Files.writeString(dir.resolve("initialized"), "CodeXP collection storage v1\n")
        val reopened = CodeXPCollectionCoordinator(FileCollectionPersistence(dir))
        reopened.load(profile)
        assertNotNull(reopened.snapshot().storageProblem)
        assertEquals(CollectionOutcome.READ_ONLY_STATE, reopened.choose(reopened.snapshot().session, choice.id).outcome)
    }

    fun testResetCommitsTheSameStarterItemsAsFreshInstall() {
        val directory = Files.createTempDirectory("codexp-reset-starter")
        val persistence = FileCollectionPersistence(directory)
        val fresh = CodeXPCollectionCoordinator(MemoryCollectionPersistence())
        fresh.load(initial())
        val coordinator = CodeXPCollectionCoordinator(persistence)
        coordinator.load(CollectionProductFixtures.profile(175, 0))
        val prior = coordinator.snapshot()
        val choice = prior.entries.first { it.canAcquire }
        coordinator.choose(prior.session, choice.id)
        coordinator.equip(prior.session, choice.kind, choice.id)
        val reset = initial()
        assertTrue(coordinator.reset(reset))
        val expected = fresh.snapshot().entries.filter { it.owned }
        assertTrue(expected.isNotEmpty())
        assertEquals(expected, coordinator.snapshot().entries.filter { it.owned })
        assertEquals(expected.map { it.id }.toSet(), persistence.load()!!.collection.unseenIds)
        assertNull(coordinator.snapshot().equippedCrest)
        assertEquals(CollectionOutcome.STALE_SESSION, coordinator.equip(prior.session, choice.kind, choice.id).outcome)
        val reopened = CodeXPCollectionCoordinator(FileCollectionPersistence(directory))
        reopened.load(reset)
        assertEquals(expected, reopened.snapshot().entries.filter { it.owned })
    }

    fun testLegacyResetRetainsOnlyItsNewProgressAndRejectsBothEarlierBackups() {
        val directory = Files.createTempDirectory("codexp-legacy-reset")

        fun reopen() = CodeXPCollectionCoordinator(FileCollectionPersistence(directory))
        val first = reopen()
        val before = first.load(CollectionProductFixtures.profile(175, 10))
        val choice = first.snapshot().entries.first { it.canAcquire }
        first.choose(first.snapshot().session, choice.id)
        first.equip(first.snapshot().session, CollectionKind.CREST, choice.id)
        val legacy = before.detachedCopy().apply { collectionGeneration = "" }
        CodeXPProgressEngine().recordEvent(legacy, Event.SAVE)
        val continued = reopen()
        assertEquals(legacy.xp, continued.load(legacy).xp)
        assertEquals(0L, continued.snapshot().credits)
        assertEquals(choice.design, continued.snapshot().equippedCrest)
        continued.checkpoint()

        val newLegacy = initial().apply { CodeXPProgressEngine().recordEvent(this, Event.SAVE) }
        val reset = reopen()
        val adopted = reset.load(newLegacy)
        assertEquals(10L, adopted.xp)
        assertEquals(1L, adopted.getEventCount(Event.SAVE))
        assertTrue(adopted.completedChallenges.isEmpty())
        assertEquals(0L, reset.snapshot().credits)
        assertNull(reset.snapshot().equippedCrest)
        assertNull(reset.snapshot().migrationNotice)
        assertFalse(
            reset
                .snapshot()
                .entries
                .single { it.id == choice.id }
                .owned,
        )
        assertNull(reset.takeChoiceAward())

        val secondLegacy = initial().apply { repeat(2) { CodeXPProgressEngine().recordEvent(this, Event.SAVE) } }
        assertEquals(20L, reopen().load(secondLegacy).xp)
        for (backup in listOf(before, adopted)) {
            val stale = reopen()
            assertEquals(20L, stale.load(backup.detachedCopy().apply { collectionGeneration = "" }).xp)
            assertEquals(0L, stale.snapshot().credits)
            assertNull(stale.snapshot().equippedCrest)
        }
    }

    fun testFailedLegacyResetCommitKeepsInputIdentifiableUntilRetry() {
        val dir = Files.createTempDirectory("codexp-legacy-reset-retry")
        val initialStore = CodeXPCollectionCoordinator(FileCollectionPersistence(dir))
        initialStore.load(CollectionProductFixtures.profile(175, 0))
        val legacy = initial().apply { CodeXPProgressEngine().recordEvent(this, Event.SAVE) }
        val failed = CodeXPCollectionCoordinator(FileCollectionPersistence(dir) { throw IOException("disk full") })
        assertEquals(10L, failed.load(legacy).xp)
        assertNotNull(failed.snapshot().storageProblem)
        assertTrue(legacy.collectionGeneration.isEmpty())
        val writable = CodeXPCollectionCoordinator(FileCollectionPersistence(dir))
        assertEquals(10L, writable.load(legacy).xp)
        assertNull(writable.snapshot().storageProblem)
        assertEquals(0L, writable.snapshot().credits)
        assertEquals(1L, writable.savedRecord().resetCount)
    }

    fun testMissingLegacyFileRecoversCheckpointInsteadOfAdoptingGeneratedChallengeIds() {
        val persistence = MemoryCollectionPersistence()
        val old = CodeXPCollectionCoordinator(persistence)
        old.load(CollectionProductFixtures.profile(175, 20))
        val choice = old.snapshot().entries.first { it.canAcquire }
        old.choose(old.snapshot().session, choice.id)
        val reopened = CodeXPCollectionCoordinator(persistence)
        val recovered = reopened.load(initial(), hasPersistedProfile = false, legacyLineage = null)
        assertEquals(CollectionProductFixtures.legacyXP(175), recovered.xp)
        assertEquals(20, recovered.completedChallenges.size)
        assertEquals(0L, reopened.snapshot().credits)
        assertTrue(
            reopened
                .snapshot()
                .entries
                .single { it.id == choice.id }
                .owned,
        )
        // A sparse persisted legacy XML can also acquire generated IDs during service initialization.
        val sparse = initial()
        assertEquals(recovered.xp, reopened.load(sparse, hasPersistedProfile = true, legacyLineage = null).xp)
        assertTrue(
            reopened
                .snapshot()
                .entries
                .single { it.id == choice.id }
                .owned,
        )
    }

    fun testNoRewardReconciliationNeverTraversesTheSavedCheckpointHistory() {
        val original = CodeXPCollectionCoordinator(MemoryCollectionPersistence())
        val profile = original.load(CollectionProductFixtures.profile(24, 120))
        val saved = original.savedRecord()
        val history = saved.checkpoint.completedChallenges
        var traversals = 0
        saved.checkpoint.completedChallenges =
            object : MutableList<CodeXPChallenge> by history {
                override fun iterator(): MutableIterator<CodeXPChallenge> {
                    traversals++
                    return history.iterator()
                }
            }
        val coordinator =
            CodeXPCollectionCoordinator(
                object : CodeXPCollectionPersistence {
                    override fun load() = saved

                    override fun save(record: CodeXPCollectionRecord) = fail("Unexpected collection write")
                },
            )
        coordinator.load(profile)
        repeat(50) {
            CodeXPProgressEngine().recordEvent(profile, Event.TYPING)
            assertTrue(coordinator.reconcile())
        }
        assertEquals(0, traversals)
    }

    private fun initial() = CodeXPState(nickname = "keep").also { CodeXPProgressEngine().initialize(it) }
}
