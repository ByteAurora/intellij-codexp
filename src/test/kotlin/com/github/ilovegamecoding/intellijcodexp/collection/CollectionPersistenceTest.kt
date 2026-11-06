package com.github.ilovegamecoding.intellijcodexp.collection

import com.github.ilovegamecoding.intellijcodexp.domain.collection.CollectionEngine
import com.github.ilovegamecoding.intellijcodexp.domain.collection.CollectionFacts
import com.github.ilovegamecoding.intellijcodexp.domain.collection.CollectionKind
import com.github.ilovegamecoding.intellijcodexp.domain.collection.CollectionOutcome
import com.github.ilovegamecoding.intellijcodexp.domain.collection.CollectionStore
import com.github.ilovegamecoding.intellijcodexp.models.CodeXPCollectionState
import com.github.ilovegamecoding.intellijcodexp.models.CodeXPLevel
import com.github.ilovegamecoding.intellijcodexp.models.CodeXPState
import com.intellij.openapi.util.JDOMUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.intellij.util.xmlb.SkipDefaultValuesSerializationFilters
import com.intellij.util.xmlb.XmlSerializer
import org.jdom.Element

class CollectionPersistenceTest : BasePlatformTestCase() {
    fun testAbsentCollectionDataLoadsEmptyWithoutApplyingAnyPolicyOrUnlockingItems() {
        val state = XmlSerializer.deserialize(Element("CodeXPCollectionState"), CodeXPCollectionState::class.java)
        val fixture = CollectionFixture(state)
        fixture.engine.preview(CollectionFixtures.STARTER)
        fixture.engine.equipped(CollectionKind.CREST)
        assertEquals(CodeXPCollectionState(), fixture.store.snapshot())
        val xml = XmlSerializer.serialize(state, SkipDefaultValuesSerializationFilters())
        assertTrue(xml.children.isEmpty())
    }

    fun testAllCollectionFieldsAndUnknownIdsSurviveRepeatedXmlRoundTrips() {
        val state =
            CodeXPCollectionState(
                ownedIds = mutableSetOf(CollectionFixtures.STARTER, "crest.future", "title.future"),
                unseenIds = mutableSetOf("crest.future", "future.orphan"),
                equippedIds = mutableMapOf("crest" to "crest.future", "title" to "title.future", "future.slot" to "future.item"),
                choiceCredits = Long.MAX_VALUE - 3,
                creditedEntitlements = mutableMapOf("unknown-source" to Long.MAX_VALUE),
            )
        var restored = state
        repeat(3) {
            restored = roundTrip(restored)
            val fixture = CollectionFixture(restored)
            assertNull(fixture.engine.equipped(CollectionKind.CREST))
            assertNull(fixture.engine.preview("crest.future").definition)
            assertEquals(state, fixture.store.snapshot())
            assertEquals(xml(state), xml(restored))
        }
        val future = state.copy(schemaVersion = 99)
        assertEquals(future, roundTrip(future))
    }

    fun testReloadCannotRefundSpentChoicesReplayGrantsOrMarkAcknowledgedItemsAsNew() {
        val fixture = CollectionFixture()
        fixture.engine.recordChoiceEntitlement(fixture.session, "fixture-source", 5)
        fixture.engine.choose(fixture.session, CollectionFixtures.CHOICE_CREST, CollectionFixtures.facts())
        fixture.engine.equip(fixture.session, CollectionKind.CREST, CollectionFixtures.CHOICE_CREST)
        fixture.engine.acknowledge(fixture.session, CollectionFixtures.CHOICE_CREST)
        var saved = fixture.store.snapshot()
        repeat(3) {
            val store = CollectionStore(roundTrip(saved))
            val engine = CollectionEngine(CollectionFixtures.catalog(), store)
            assertEquals(CollectionOutcome.UNCHANGED, engine.recordChoiceEntitlement(store.session(), "fixture-source", 5).outcome)
            assertEquals(CollectionOutcome.ALREADY_OWNED, engine.choose(store.session(), CollectionFixtures.CHOICE_CREST, CollectionFixtures.facts()).outcome)
            assertEquals(saved, store.snapshot())
            assertFalse(engine.preview(CollectionFixtures.CHOICE_CREST).unseen)
            assertEquals(CollectionFixtures.CHOICE_CREST, engine.equipped(CollectionKind.CREST)?.id)
            saved = store.snapshot()
        }
        assertEquals(3L, saved.choiceCredits)
        assertEquals(mapOf("fixture-source" to 5L), saved.creditedEntitlements)
    }

    fun testCollectionOperationsAndResetLeaveExistingProfileXpHistoryNicknameAndSettingsUnchanged() {
        val profile = legacyProfile()
        assertTrue(profile.xp > 0)
        assertTrue(profile.completedChallenges.isNotEmpty())
        assertTrue(profile.nickname.isNotBlank())
        val before = JDOMUtil.writeElement(XmlSerializer.serialize(profile))
        val facts =
            CollectionFacts(
                totalXP = profile.xp,
                eventCounts = profile.eventCounts,
                completedChallenges =
                    profile.completedChallenges
                        .groupingBy { it.event }
                        .eachCount()
                        .mapValues { it.value.toLong() },
            )
        val fixture = CollectionFixture()
        fixture.engine.unlock(fixture.session, facts)
        fixture.engine.recordChoiceEntitlement(fixture.session, "fixture-source", 3)
        fixture.engine.choose(fixture.session, CollectionFixtures.CHOICE_CREST, facts)
        fixture.engine.equip(fixture.session, CollectionKind.CREST, CollectionFixtures.CHOICE_CREST)
        assertEquals(fixture.store.snapshot(), roundTrip(fixture.store.snapshot()))
        fixture.store.reset(fixture.session)
        assertEquals(CodeXPCollectionState(), fixture.store.snapshot())
        assertEquals(before, JDOMUtil.writeElement(XmlSerializer.serialize(profile)))
        assertFalse(before.contains("CodeXPCollectionState"))
    }

    fun testHistoricalLevelBackfillSurvivesReloadAndPreservesAllExistingProfileDataAndEquipment() {
        val profile = legacyProfile()
        val before = JDOMUtil.writeElement(XmlSerializer.serialize(profile))
        // Test evidence uses the unchanged 2.2.0 calculation, not a proposed cap or replacement curve.
        val legacyLevel = CodeXPLevel.createLegacyLevelInfo(profile.xp).level
        assertTrue(legacyLevel >= 9)
        val facts = CollectionFacts(totalXP = profile.xp, highestAchievedLevel = legacyLevel)
        val fixture =
            CollectionFixture(
                CodeXPCollectionState(
                    ownedIds = mutableSetOf(CollectionFixtures.STARTER),
                    equippedIds = mutableMapOf("crest" to CollectionFixtures.STARTER),
                ),
                CollectionFixtures.levelCatalog(),
            )
        val result = fixture.engine.unlock(fixture.session, facts)
        assertEquals(setOf(CollectionFixtures.LEVEL_TWO, CollectionFixtures.LEVEL_FIVE, CollectionFixtures.LEVEL_NINE), result.acquiredIds)
        result.acquiredIds.forEach { fixture.engine.acknowledge(fixture.session, it) }
        val saved = fixture.store.snapshot()
        repeat(3) {
            val reloaded = CollectionFixture(roundTrip(saved), CollectionFixtures.levelCatalog())
            assertEquals(CollectionOutcome.UNCHANGED, reloaded.engine.unlock(reloaded.session, facts).outcome)
            val restoredState = reloaded.store.snapshot()
            assertEquals(saved, restoredState)
            assertTrue(restoredState.unseenIds.isEmpty())
            assertEquals(CollectionFixtures.STARTER, reloaded.engine.equipped(CollectionKind.CREST)?.id)
            assertEquals(before, JDOMUtil.writeElement(XmlSerializer.serialize(profile)))
        }
    }

    private fun legacyProfile(): CodeXPState {
        val resource = checkNotNull(javaClass.getResource("/upgrade/v2.1.1/codexp-2.1.1-default.xml"))
        val root = JDOMUtil.load(resource)
        return XmlSerializer.deserialize(root.getChild("component") ?: root, CodeXPState::class.java)
    }

    private fun roundTrip(state: CodeXPCollectionState): CodeXPCollectionState = XmlSerializer.deserialize(XmlSerializer.serialize(state, SkipDefaultValuesSerializationFilters()), CodeXPCollectionState::class.java)

    private fun xml(state: CodeXPCollectionState): String = JDOMUtil.writeElement(XmlSerializer.serialize(state))
}
