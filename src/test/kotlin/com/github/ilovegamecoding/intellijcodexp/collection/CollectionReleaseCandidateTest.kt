package com.github.ilovegamecoding.intellijcodexp.collection

import com.github.ilovegamecoding.intellijcodexp.domain.CodeXPGrowthPolicy
import com.github.ilovegamecoding.intellijcodexp.domain.CodeXPProgressEngine
import com.github.ilovegamecoding.intellijcodexp.domain.collection.CodeXPCollectionCatalog
import com.github.ilovegamecoding.intellijcodexp.domain.collection.CollectionKind
import com.github.ilovegamecoding.intellijcodexp.domain.collection.CollectionOutcome
import com.github.ilovegamecoding.intellijcodexp.domain.collection.CollectionRoute
import com.github.ilovegamecoding.intellijcodexp.enums.Event
import com.github.ilovegamecoding.intellijcodexp.models.CodeXPLevel
import com.github.ilovegamecoding.intellijcodexp.models.CodeXPState
import com.github.ilovegamecoding.intellijcodexp.presentation.collection.CodeXPCollectionPanel
import com.github.ilovegamecoding.intellijcodexp.presentation.collection.CollectionFilter
import com.github.ilovegamecoding.intellijcodexp.presentation.dashboard.CodeXPDashboardController
import com.github.ilovegamecoding.intellijcodexp.presentation.dashboard.CodeXPDashboardPanel
import com.github.ilovegamecoding.intellijcodexp.services.CodeXPCollectionCoordinator
import com.github.ilovegamecoding.intellijcodexp.services.CodeXPService
import com.github.ilovegamecoding.intellijcodexp.services.FileCollectionPersistence
import com.github.ilovegamecoding.intellijcodexp.services.MemoryCollectionPersistence
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.JDOMUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.intellij.util.xmlb.XmlSerializer
import java.io.IOException
import java.nio.file.Files

class CollectionReleaseCandidateTest : BasePlatformTestCase() {
    fun testTripleRequirementsPreserveEarnedLevelsAtMigrationAndEveryBoundary() {
        assertEquals(1651968L, CodeXPGrowthPolicy.capXP)
        assertEquals(1636968L, CodeXPGrowthPolicy.xpAtLevel(149))
        assertEquals(286968L, CodeXPGrowthPolicy.xpAtLevel(59))
        assertEquals(301968L, CodeXPGrowthPolicy.xpAtLevel(60))
        for (level in 2..58) {
            val boundary = CollectionProductFixtures.legacyXP(level)
            for (xp in listOf(boundary - 1, boundary, boundary + 1)) {
                val adjusted = CodeXPGrowthPolicy.levelInfo(xp, CodeXPGrowthPolicy.grandfatheredLevelOffset(xp))
                assertEquals("legacy at $xp", CodeXPLevel.createLegacyLevelInfo(xp).level, adjusted.level)
            }
        }
        for (level in 2..150) {
            val boundary = CodeXPGrowthPolicy.xpAtLevel(level)
            assertEquals(level - 1, CodeXPLevel.createLevelInfo(boundary - 1).level)
            assertEquals(level, CodeXPLevel.createLevelInfo(boundary).level)
        }
        assertEquals(150, CodeXPLevel.createLevelInfo(Long.MAX_VALUE).level)
        assertEquals(0L, CodeXPLevel.createLevelInfo(Long.MAX_VALUE).totalXPForNextLevel)
    }

    fun testProductionCatalogHasThirtyApprovedCrestsAndTwentyFourTitlesWithExactRoutes() {
        val content = CodeXPCollectionCatalog.content
        assertEquals(54, content.items.size)
        assertEquals(
            54,
            content.items
                .map { it.definition.id }
                .distinct()
                .size,
        )
        for (route in CollectionRoute.entries) {
            assertEquals(10, content.items.count { it.definition.kind == CollectionKind.CREST && it.route == route })
            assertEquals(8, content.items.count { it.definition.kind == CollectionKind.TITLE && it.route == route })
        }
        val families = content.items.mapNotNull { it.design }.groupBy { it.family }
        assertEquals(10, families.size)
        families.values.forEach { assertEquals(listOf(1, 2, 3), it.map { form -> form.form }) }
        assertFalse(content.items.any { it.definition.id.contains("preview") })
    }

    fun testExistingLevelProgressIsPreservedWithoutAddingXpOrReapplyingOffsetOnRestart() {
        val persistence = MemoryCollectionPersistence()
        val legacy = CodeXPState(xp = 450)
        val profile = CodeXPCollectionCoordinator(persistence).load(legacy)
        assertEquals(450L, profile.xp)
        assertEquals(2, profile.levelInfo().level)
        assertEquals(450L, profile.levelInfo().xpIntoCurrentLevel)
        assertEquals(945L, profile.levelInfo().totalXPForNextLevel)
        profile.xp += 495
        repeat(3) {
            val restored = CodeXPCollectionCoordinator(persistence).load(roundTrip(profile))
            assertEquals(945L, restored.xp)
            assertEquals(3, restored.levelInfo().level)
            assertEquals(0L, restored.levelInfo().xpIntoCurrentLevel)
            assertFalse(JDOMUtil.writeElement(XmlSerializer.serialize(restored)).contains("levelOffset"))
        }
    }

    fun testFreshProfileFirstLevelWithRealTypingAndSaveChallengeRewards() {
        val engine = CodeXPProgressEngine()
        for ((event, firstLevelUp) in listOf(Event.TYPING to 300, Event.SAVE to 30)) {
            val state = CodeXPState()
            engine.initialize(state)
            repeat(firstLevelUp - 1) {
                assertFalse(engine.recordEvent(state, event).xpChanges.any { it.isLevelUp })
            }
            assertEquals(1, state.levelInfo().level)
            assertEquals(1, engine.recordEvent(state, event).xpChanges.count { it.isLevelUp })
            assertEquals(2, state.levelInfo().level)
            assertEquals(if (event == Event.TYPING) 1000L else 1300L, state.xp)
        }
    }

    fun testPreviousCandidateLedgerUpgradePreservesSpentCreditsRemainderAndEquipment() {
        val persistence = MemoryCollectionPersistence()
        val oldCap = CodeXPGrowthPolicy.previousCapXP
        val coordinator = CodeXPCollectionCoordinator(persistence)
        val profile = coordinator.load(CodeXPState(xp = oldCap))
        val item = coordinator.snapshot().entries.first { it.canAcquire }
        val session = coordinator.snapshot().session
        coordinator.choose(session, item.id)
        coordinator.equip(session, item.kind, item.id)
        profile.xp += 49999
        coordinator.reconcile()
        val v1 = coordinator.savedRecord()
        v1.migration = v1.migration!!.copy(version = 1, policyId = CodeXPGrowthPolicy.PREVIOUS_ID, capXP = oldCap, levelOffset = 0, intervalXP = 25000)
        v1.collection.choiceCredits = 1
        v1.collection.creditedEntitlements["postcap.intervals.v1"] = 1
        persistence.save(v1)
        val upgraded = CodeXPCollectionCoordinator(persistence)
        val current = upgraded.load(roundTrip(profile))
        assertEquals(1L, upgraded.snapshot().credits)
        assertEquals(24999L, upgraded.snapshot().choiceProgressXP)
        assertEquals(item.design, upgraded.snapshot().equippedCrest)
        assertEquals(4, persistence.load()!!.migration!!.version)
        assertEquals(55000L, upgraded.snapshot().choiceIntervalXP)
        current.xp += 30000
        upgraded.reconcile()
        assertEquals(1L, upgraded.snapshot().credits)
        current.xp++
        upgraded.reconcile()
        assertEquals(2L, upgraded.snapshot().credits)
        assertEquals(0L, upgraded.snapshot().choiceProgressXP)
        assertEquals(1L, upgraded.takeChoiceAward()!!.count)
    }

    fun testOnlyHighestCompletedOrdinalOfOneKindCountsAndLockedNamesStayMasked() {
        val profile = CollectionProductFixtures.profile(1, 0)
        profile.completedChallenges.add(CollectionProductFixtures.challenge(9))
        profile.completedChallenges.add(CollectionProductFixtures.challenge(9, Event.COPY))
        profile.completedChallenges.add(CollectionProductFixtures.challenge(300).apply { progress = goal - 1 })
        profile.challenges[Event.SAVE] = CollectionProductFixtures.challenge(300)
        val coordinator = CodeXPCollectionCoordinator(MemoryCollectionPersistence())
        coordinator.load(profile)
        assertFalse(coordinator.snapshot().entries.any { it.kind == CollectionKind.CREST && it.id.endsWith(".challenge") && it.owned })
        profile.completedChallenges.add(CollectionProductFixtures.challenge(10))
        coordinator.reconcile()
        assertEquals(2, coordinator.snapshot().entries.count { it.kind == CollectionKind.CREST && it.id.endsWith(".challenge") && it.owned })
        coordinator.snapshot().entries.filter { !it.owned }.forEach { item ->
            val rawName =
                CodeXPCollectionCatalog.content
                    .find(item.id)!!
                    .definition.displayName
            assertFalse(item.name.contains(rawName))
            assertFalse(item.accessibleName.contains(rawName))
            assertFalse(item.matches(rawName))
        }
    }

    fun testLegacy175MigratesOncePreservesRecordsAndNeverAutoEquipsOrConvertsHistoricalExcess() {
        val persistence = MemoryCollectionPersistence()
        var profile = CollectionProductFixtures.profile(175, 310)
        val before = legacyXml(profile)
        var coordinator = CodeXPCollectionCoordinator(persistence)
        profile = coordinator.load(profile)
        assertEquals(before, legacyXml(profile))
        assertEquals(150, coordinator.snapshot().displayedLevel)
        assertEquals(175, coordinator.snapshot().previousLevel)
        assertTrue(coordinator.snapshot().entries.none { it.progress.contains("Lv.175") })
        assertEquals(36, coordinator.snapshot().entries.count { it.owned })
        assertEquals(1L, coordinator.snapshot().credits)
        assertNull(coordinator.snapshot().equippedCrest)
        assertNull(coordinator.snapshot().equippedTitle)
        assertNotNull(coordinator.snapshot().migrationNotice)
        coordinator.acknowledgeMigration()
        repeat(3) {
            coordinator = CodeXPCollectionCoordinator(persistence)
            profile = coordinator.load(roundTrip(profile))
            assertEquals(before, legacyXml(profile))
            assertEquals(1L, coordinator.snapshot().credits)
            assertNull(coordinator.snapshot().migrationNotice)
        }
        assertEquals(CollectionProductFixtures.legacyXP(175), coordinator.savedRecord().migration!!.baselineXP)
        assertEquals(175, coordinator.savedRecord().migration!!.previousLevel)
    }

    fun testNewProfileEarnsOnlyNewPostCapIntervalsIncludingLargeRewardsAndExactBoundaries() {
        val coordinator = CodeXPCollectionCoordinator(MemoryCollectionPersistence())
        val profile = coordinator.load(CodeXPState())
        assertEquals(0L, coordinator.snapshot().credits)
        for ((xp, credits) in listOf(
            CodeXPGrowthPolicy.capXP - 1 to 0L,
            CodeXPGrowthPolicy.capXP to 0L,
            CodeXPGrowthPolicy.capXP + 25000 to 0L,
            CodeXPGrowthPolicy.capXP + 49999 to 0L,
            CodeXPGrowthPolicy.capXP + 50000 to 1L,
            CodeXPGrowthPolicy.capXP + 104999 to 1L,
            CodeXPGrowthPolicy.capXP + 105000 to 2L,
            CodeXPGrowthPolicy.capXP + 300000 to 5L,
        )) {
            profile.xp = xp
            coordinator.reconcile()
            assertEquals(credits, coordinator.snapshot().credits)
        }
        repeat(3) { coordinator.reconcile() }
        assertEquals(5L, coordinator.snapshot().credits)
        assertEquals(5L, coordinator.savedRecord().collection.creditedEntitlements["postcap.intervals.v1"])
    }

    fun testLegacyAboveCapStartsFutureIntervalsAtMigrationXpAndSpendingCannotReplay() {
        val persistence = MemoryCollectionPersistence()
        var coordinator = CodeXPCollectionCoordinator(persistence)
        var profile = coordinator.load(CollectionProductFixtures.profile(175, 0))
        val baseline = profile.xp
        val choice = coordinator.snapshot().entries.first { it.canAcquire }
        val oldSession = coordinator.snapshot().session
        assertEquals(CollectionOutcome.APPLIED, coordinator.choose(oldSession, choice.id).outcome)
        assertEquals(0L, coordinator.snapshot().credits)
        assertNull(coordinator.snapshot().equippedCrest)
        profile.xp = baseline + 49999
        coordinator.reconcile()
        assertEquals(0L, coordinator.snapshot().credits)
        profile.xp++
        coordinator.reconcile()
        assertEquals(1L, coordinator.snapshot().credits)
        coordinator = CodeXPCollectionCoordinator(persistence)
        profile = coordinator.load(roundTrip(profile).apply { collectionGeneration = "" }) // 2.2.0 strips unknown fields.
        assertEquals(baseline + 50000, profile.xp)
        assertEquals(1L, coordinator.snapshot().credits)
        assertEquals(CollectionOutcome.STALE_SESSION, coordinator.choose(oldSession, choice.id).outcome)
        assertEquals(CollectionOutcome.ALREADY_OWNED, coordinator.choose(coordinator.snapshot().session, choice.id).outcome)
        assertEquals(175, coordinator.snapshot().previousLevel)
    }

    fun testChoiceSaveFailureRollsBackBothOwnershipAndCreditAndRecoveryNeverRefundsCommittedSpend() {
        val directory = Files.createTempDirectory("codexp-choice-atomic")
        var fail = false
        val persistence = FileCollectionPersistence(directory) { if (fail) throw IOException("injected failure before commit") }
        val coordinator = CodeXPCollectionCoordinator(persistence)
        val profile = coordinator.load(CollectionProductFixtures.profile(175, 0))
        val choice = coordinator.snapshot().entries.first { it.canAcquire }
        fail = true
        assertEquals(CollectionOutcome.SAVE_FAILED, coordinator.choose(coordinator.snapshot().session, choice.id).outcome)
        assertEquals(1L, coordinator.snapshot().credits)
        assertFalse(
            coordinator
                .snapshot()
                .entries
                .first { it.id == choice.id }
                .owned,
        )
        assertEquals(1L, persistence.load()!!.collection.choiceCredits)
        fail = false
        assertEquals(CollectionOutcome.APPLIED, coordinator.choose(coordinator.snapshot().session, choice.id).outcome)
        val manifest = JDOMUtil.load(directory.resolve("current.xml"))
        Files.writeString(directory.resolve("${manifest.getAttributeValue("revision")}.xml"), "broken")
        val recovered = CodeXPCollectionCoordinator(FileCollectionPersistence(directory))
        recovered.load(roundTrip(profile))
        assertEquals(0L, recovered.snapshot().credits)
        assertTrue(
            recovered
                .snapshot()
                .entries
                .first { it.id == choice.id }
                .owned,
        )
        assertEquals(CollectionOutcome.ALREADY_OWNED, recovered.choose(recovered.snapshot().session, choice.id).outcome)
    }

    fun testResetFailureKeepsAllDataAndSuccessfulResetFencesOldXmlAndBackups() {
        val directory = Files.createTempDirectory("codexp-reset-fence")
        var fail = false
        val persistence = FileCollectionPersistence(directory) { if (fail) throw IOException("reset failed") }
        val coordinator = CodeXPCollectionCoordinator(persistence)
        val oldProfile = roundTrip(coordinator.load(CollectionProductFixtures.profile(175, 20)))
        val oldSession = coordinator.snapshot().session
        val choice = coordinator.snapshot().entries.first { it.canAcquire }
        coordinator.choose(oldSession, choice.id)
        val initial = CodeXPState(nickname = oldProfile.nickname).apply { CodeXPProgressEngine().initialize(this) }
        fail = true
        assertFalse(coordinator.reset(initial))
        assertTrue(
            coordinator
                .snapshot()
                .entries
                .first { it.id == choice.id }
                .owned,
        )
        fail = false
        assertTrue(coordinator.reset(initial))
        assertEquals(CollectionOutcome.STALE_SESSION, coordinator.equip(oldSession, choice.kind, choice.id).outcome)
        val manifest = JDOMUtil.load(directory.resolve("current.xml"))
        Files.writeString(directory.resolve("${manifest.getAttributeValue("revision")}.xml"), "broken reset primary")
        val recovered = CodeXPCollectionCoordinator(FileCollectionPersistence(directory))
        val restored = recovered.load(oldProfile.apply { collectionGeneration = "" })
        assertEquals(0L, restored.xp)
        assertEquals(oldProfile.nickname, restored.nickname)
        assertTrue(restored.completedChallenges.isEmpty())
        assertEquals(0L, recovered.snapshot().credits)
        assertFalse(
            recovered
                .snapshot()
                .entries
                .first { it.id == choice.id }
                .owned,
        )
        assertEquals(0L, recovered.savedRecord().migration!!.baselineXP)
        assertEquals(1, recovered.savedRecord().migration!!.previousLevel)
        assertTrue(
            recovered
                .savedRecord()
                .collection.creditedEntitlements
                .isEmpty(),
        )
    }

    fun testMissingOrCorruptCommitPointerFailsClosedInsteadOfRepeatingMigration() {
        val directory = Files.createTempDirectory("codexp-readonly")
        val persistence = FileCollectionPersistence(directory)
        val first = CodeXPCollectionCoordinator(persistence)
        val profile = first.load(CollectionProductFixtures.profile(175, 0))
        val choice = first.snapshot().entries.first { it.canAcquire }
        first.choose(first.snapshot().session, choice.id)
        Files.delete(directory.resolve("current.xml"))
        val reopened = CodeXPCollectionCoordinator(FileCollectionPersistence(directory))
        reopened.load(roundTrip(profile))
        assertNotNull(reopened.snapshot().storageProblem)
        assertEquals(0L, reopened.snapshot().credits)
        assertEquals(CollectionOutcome.READ_ONLY_STATE, reopened.choose(reopened.snapshot().session, choice.id).outcome)
        assertFalse(Files.exists(directory.resolve("current.xml")))
    }

    fun testTwoProjectViewsShareProductionCatalogEquipmentAndChoiceTransactions() {
        val service = CodeXPService(MemoryCollectionPersistence())
        val scope = Disposer.newDisposable()
        try {
            service.loadState(CollectionProductFixtures.profile(175, 20))
            service.state.xp += CodeXPGrowthPolicy.CHOICE_INTERVAL_XP
            service.eventOccurred(Event.TYPING, null)
            val first = CodeXPDashboardPanel { false }
            val second = CodeXPDashboardPanel { false }
            val panel = CodeXPCollectionPanel(service)
            Disposer.register(scope, panel)
            var purchasing = false
            var receivedPurchaseUpdate = false
            val controller =
                CodeXPDashboardController(service, first, scope) { update ->
                    panel.apply(update)
                    if (purchasing) {
                        receivedPurchaseUpdate = true
                        assertTrue(panel.detailScroll.isVisible && panel.primary.isVisible)
                    }
                }
            controller.isCollectionVisible = { true }
            controller.initialize()
            CodeXPDashboardController(service, second, scope).initialize()
            first.visibilityChanged(true)
            second.visibilityChanged(true)
            val snapshot = service.collectionSnapshot()
            assertTrue(snapshot.credits >= 2 * CodeXPGrowthPolicy.CHOICE_COST)
            val choice = snapshot.entries.first { it.canAcquire }
            panel.filter.selectedItem = CollectionFilter.AVAILABLE
            panel.search.text = choice.name
            panel.grid.selectedIndex = (0 until panel.model.size).first { panel.model[it].id == choice.id }
            assertNull(first.crest.collectionDesign)
            purchasing = true
            panel.primary.doClick()
            purchasing = false
            assertTrue(receivedPurchaseUpdate)
            assertEquals(snapshot.credits - CodeXPGrowthPolicy.CHOICE_COST, service.collectionSnapshot().credits)
            assertEquals(CollectionFilter.OWNED, panel.filter.selectedItem)
            assertEquals(choice.id, panel.grid.selectedValue.id)
            assertTrue(panel.detailScroll.isVisible && panel.primary.isVisible && panel.primary.isEnabled)
            assertEquals(panel.grid.selectedValue.name, panel.detailName.text)
            assertNull(first.crest.collectionDesign)
            assertEquals(CollectionOutcome.ALREADY_OWNED, service.chooseCollection(snapshot.session, choice.id).outcome)
            panel.primary.doClick()
            assertEquals(snapshot.credits - CodeXPGrowthPolicy.CHOICE_COST, service.collectionSnapshot().credits)
            assertEquals(choice.design, first.crest.collectionDesign)
            assertEquals(first.crest.collectionDesign, second.crest.collectionDesign)
            assertEquals("Lv.150 MAX", first.remaining.text)
            panel.filter.selectedItem = CollectionFilter.OWNED
            assertTrue((0 until panel.model.size).all { panel.model[it].owned })
            panel.setKind(CollectionKind.TITLE)
            panel.search.text = "not a title"
            assertEquals(0, panel.model.size)
            service.resetAllExceptNickname()
            assertNull(first.crest.collectionDesign)
            assertEquals("", second.equippedTitle.text)
            assertEquals(0L, service.state.xp)
        } finally {
            Disposer.dispose(scope)
            Disposer.dispose(service)
        }
    }

    private fun roundTrip(profile: CodeXPState): CodeXPState = XmlSerializer.deserialize(XmlSerializer.serialize(profile), CodeXPState::class.java)

    fun testReal220XmlFixturesUpgradeReloadAndCrossCapWithUnchangedChallengeRewards() {
        for (variant in listOf("legacy175", "nearcap", "large-reward", "default", "fresh")) {
            val text = javaClass.getResource("/upgrade/v2.2.0/codexp-2.2.0-$variant.xml")!!.readText()
            assertFalse(text.contains("collectionGeneration"))
            val original = XmlSerializer.deserialize(JDOMUtil.load(text).getChild("component"), CodeXPState::class.java)
            val before = legacyXml(original)
            val persistence = FileCollectionPersistence(Files.createTempDirectory("codexp-220-$variant"))
            var coordinator = CodeXPCollectionCoordinator(persistence)
            var profile = coordinator.load(original)
            assertEquals(before, legacyXml(profile))
            assertEquals(if (variant == "fresh") 0L else 1L, coordinator.snapshot().credits)
            if (variant == "nearcap") {
                assertEquals(149, coordinator.snapshot().displayedLevel)
                CodeXPProgressEngine().recordEvent(profile, Event.SAVE)
                coordinator.reconcile()
                assertEquals(149, coordinator.snapshot().displayedLevel)
                assertEquals(149, profile.levelInfo().level)
                profile.xp += 22 // Last 11 XP under the prior pace now cost 33; Save supplied 10.
                CodeXPProgressEngine().recordEvent(profile, Event.TYPING)
                coordinator.reconcile()
                assertEquals(150, coordinator.snapshot().displayedLevel)
                assertEquals(1L, coordinator.snapshot().choiceProgressXP)
            }
            if (variant == "large-reward") {
                val completed = profile.challenges.getValue(Event.SAVE)
                val oldXP = profile.xp
                val result = CodeXPProgressEngine().recordEvent(profile, Event.SAVE)
                coordinator.reconcile()
                assertEquals(493500L, completed.rewardXP)
                assertEquals(oldXP + 493510L, profile.xp)
                assertEquals(8L, coordinator.snapshot().credits)
                assertEquals(completed.id, result.completedChallenge!!.id)
                assertEquals(completed.goal + completed.goalIncrement, profile.challenges.getValue(Event.SAVE).goal)
                assertEquals(completed.rewardXP + completed.rewardXPIncrement, profile.challenges.getValue(Event.SAVE).rewardXP)
                assertEquals(1, result.xpChanges.count { it.isLevelUp })
            }
            coordinator.checkpoint()
            val finalXP = profile.xp
            val credits = coordinator.snapshot().credits
            coordinator = CodeXPCollectionCoordinator(persistence)
            profile = coordinator.load(roundTrip(profile))
            assertEquals(finalXP, profile.xp)
            assertEquals(credits, coordinator.snapshot().credits)
        }
    }

    fun testResetGenerationAnchorsPreserveNew220ProgressWithoutAcceptingPreResetBackup() {
        val persistence = MemoryCollectionPersistence()
        val first = CodeXPCollectionCoordinator(persistence)
        val old = roundTrip(first.load(CollectionProductFixtures.profile(175, 0)))
        val reset = CodeXPState(nickname = "keep").apply { CodeXPProgressEngine().initialize(this) }
        assertTrue(first.reset(reset))
        val downgraded = roundTrip(reset).apply { collectionGeneration = "" }
        CodeXPProgressEngine().recordEvent(downgraded, Event.SAVE)
        val upgraded = CodeXPCollectionCoordinator(persistence)
        val state = upgraded.load(downgraded)
        assertEquals(10L, state.xp)
        assertEquals(0L, upgraded.snapshot().credits)
        upgraded.checkpoint()
        val stale = CodeXPCollectionCoordinator(persistence)
        assertEquals(10L, stale.load(old.apply { collectionGeneration = "" }).xp)
        assertEquals(0L, stale.snapshot().credits)
    }

    fun testXpOnlyEventsDoNotWriteTheCollectionJournalOnEveryKeystroke() {
        var writes = 0
        val memory = MemoryCollectionPersistence()
        val persistence =
            object : com.github.ilovegamecoding.intellijcodexp.services.CodeXPCollectionPersistence by memory {
                override fun save(record: com.github.ilovegamecoding.intellijcodexp.models.CodeXPCollectionRecord) {
                    writes++
                    memory.save(record)
                }
            }
        val coordinator = CodeXPCollectionCoordinator(persistence)
        val profile = coordinator.load(CodeXPState())
        val initialWrites = writes
        repeat(50) {
            profile.xp++
            coordinator.reconcile()
            coordinator.snapshot()
        }
        assertEquals(initialWrites, writes)
        coordinator.checkpoint()
        assertEquals(initialWrites + 1, writes)
    }

    private fun legacyXml(profile: CodeXPState): String = JDOMUtil.writeElement(XmlSerializer.serialize(profile.copy(collectionGeneration = "")))
}
