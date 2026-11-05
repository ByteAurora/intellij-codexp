package com.github.ilovegamecoding.intellijcodexp.collection

import com.github.ilovegamecoding.intellijcodexp.domain.collection.CollectionAcquisition
import com.github.ilovegamecoding.intellijcodexp.domain.collection.CollectionCatalog
import com.github.ilovegamecoding.intellijcodexp.domain.collection.CollectionEngine
import com.github.ilovegamecoding.intellijcodexp.domain.collection.CollectionKind
import com.github.ilovegamecoding.intellijcodexp.domain.collection.CollectionOutcome
import com.github.ilovegamecoding.intellijcodexp.domain.collection.CollectionRequirement
import com.github.ilovegamecoding.intellijcodexp.models.CodeXPCollectionState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CollectionEngineTest {
    @Test
    fun `preview permits locked items without acquiring acknowledging or equipping`() {
        val fixture = CollectionFixture()
        val before = fixture.store.snapshot()
        val preview = fixture.engine.preview(CollectionFixtures.XP)
        assertEquals(CollectionFixtures.XP, preview.definition?.id)
        assertFalse(preview.owned)
        assertFalse(preview.unseen)
        assertFalse(preview.equipped)
        assertEquals(before, fixture.store.snapshot())
    }

    @Test
    fun `automatic rewards unlock once and never overwrite existing equipment`() {
        val fixture =
            CollectionFixture(
                CodeXPCollectionState(
                    ownedIds = mutableSetOf(CollectionFixtures.STARTER),
                    equippedIds = mutableMapOf("crest" to CollectionFixtures.STARTER),
                ),
            )
        val engine = fixture.engine
        assertEquals(CollectionOutcome.UNCHANGED, engine.unlock(fixture.session, CollectionFixtures.facts(10, 2, 1)).outcome)
        val result = engine.unlock(fixture.session, CollectionFixtures.facts(11, 3, 1, 1))
        val newIds = setOf(CollectionFixtures.XP, CollectionFixtures.SAVES, CollectionFixtures.HISTORY)
        assertEquals(newIds, result.acquiredIds)
        assertEquals(newIds, fixture.store.snapshot().unseenIds)
        assertEquals(CollectionFixtures.STARTER, engine.equipped(CollectionKind.CREST)?.id)
        assertEquals(0L, fixture.store.snapshot().choiceCredits)
        for (id in newIds) engine.acknowledge(fixture.session, id)
        val acknowledged = fixture.store.snapshot()
        assertEquals(CollectionOutcome.UNCHANGED, engine.unlock(fixture.session, CollectionFixtures.facts(100, 10, 10)).outcome)
        assertEquals(acknowledged, fixture.store.snapshot())
    }

    @Test
    fun `choosing spends the defined cost and leaves equipment and unseen independent`() {
        val fixture = CollectionFixture(CodeXPCollectionState(choiceCredits = 3))
        val result = fixture.engine.choose(fixture.session, CollectionFixtures.CHOICE_CREST, CollectionFixtures.facts())
        assertEquals(CollectionOutcome.APPLIED, result.outcome)
        assertEquals(setOf(CollectionFixtures.CHOICE_CREST), result.acquiredIds)
        assertEquals(1L, fixture.store.snapshot().choiceCredits)
        assertTrue(fixture.engine.preview(CollectionFixtures.CHOICE_CREST).unseen)
        assertNull(fixture.engine.equipped(CollectionKind.CREST))
        val after = fixture.store.snapshot()
        assertEquals(CollectionOutcome.ALREADY_OWNED, fixture.engine.choose(fixture.session, CollectionFixtures.CHOICE_CREST, CollectionFixtures.facts()).outcome)
        assertEquals(after, fixture.store.snapshot())
    }

    @Test
    fun `invalid unavailable and condition-only purchases leave every field unchanged`() {
        val fixture = CollectionFixture(CodeXPCollectionState(choiceCredits = 1))
        val before = fixture.store.snapshot()
        val cases =
            listOf(
                "missing" to CollectionOutcome.UNKNOWN_ITEM,
                CollectionFixtures.XP to CollectionOutcome.NOT_CHOOSABLE,
                CollectionFixtures.STARTER to CollectionOutcome.NOT_CHOOSABLE,
                CollectionFixtures.CHOICE_TITLE to CollectionOutcome.LOCKED,
                CollectionFixtures.CHOICE_CREST to CollectionOutcome.INSUFFICIENT_CREDITS,
            )
        for ((id, outcome) in cases) {
            assertEquals(outcome, fixture.engine.choose(fixture.session, id, CollectionFixtures.facts()).outcome)
            assertEquals(before, fixture.store.snapshot())
        }
        assertEquals(CollectionOutcome.NOT_CHOOSABLE, fixture.engine.choose(fixture.session, CollectionFixtures.XP, CollectionFixtures.facts(xp = 11)).outcome)
        assertEquals(before, fixture.store.snapshot())
        assertEquals(CollectionOutcome.APPLIED, fixture.engine.choose(fixture.session, CollectionFixtures.CHOICE_TITLE, CollectionFixtures.facts(xp = 11)).outcome)
        assertEquals(0L, fixture.store.snapshot().choiceCredits)
    }

    @Test
    fun `unowned wrong-kind and unknown equipment cannot be installed`() {
        val fixture = CollectionFixture(CodeXPCollectionState(ownedIds = mutableSetOf(CollectionFixtures.STARTER)))
        val before = fixture.store.snapshot()
        assertEquals(CollectionOutcome.NOT_OWNED, fixture.engine.equip(fixture.session, CollectionKind.CREST, CollectionFixtures.XP).outcome)
        assertEquals(CollectionOutcome.WRONG_KIND, fixture.engine.equip(fixture.session, CollectionKind.TITLE, CollectionFixtures.STARTER).outcome)
        assertEquals(CollectionOutcome.UNKNOWN_ITEM, fixture.engine.equip(fixture.session, CollectionKind.TITLE, "future.unknown").outcome)
        assertEquals(before, fixture.store.snapshot())
    }

    @Test
    fun `equipment slots acknowledgement and ownership change only on explicit commands`() {
        val ids = mutableSetOf(CollectionFixtures.STARTER, CollectionFixtures.SAVES)
        val fixture = CollectionFixture(CodeXPCollectionState(ownedIds = ids, unseenIds = ids.toMutableSet()))
        val engine = fixture.engine
        assertEquals(CollectionOutcome.APPLIED, engine.equip(fixture.session, CollectionKind.CREST, CollectionFixtures.STARTER).outcome)
        assertEquals(CollectionOutcome.APPLIED, engine.equip(fixture.session, CollectionKind.TITLE, CollectionFixtures.SAVES).outcome)
        assertEquals(CollectionOutcome.UNCHANGED, engine.equip(fixture.session, CollectionKind.TITLE, CollectionFixtures.SAVES).outcome)
        assertTrue(engine.preview(CollectionFixtures.SAVES).unseen)
        assertEquals(ids, fixture.store.snapshot().unseenIds)
        assertEquals(CollectionOutcome.APPLIED, engine.acknowledge(fixture.session, CollectionFixtures.SAVES).outcome)
        assertEquals(CollectionOutcome.UNCHANGED, engine.acknowledge(fixture.session, CollectionFixtures.SAVES).outcome)
        assertEquals(CollectionOutcome.NOT_OWNED, engine.acknowledge(fixture.session, CollectionFixtures.XP).outcome)
        assertEquals(CollectionFixtures.SAVES, engine.equipped(CollectionKind.TITLE)?.id)
        engine.equip(fixture.session, CollectionKind.CREST, null)
        assertNull(engine.equipped(CollectionKind.CREST))
        assertEquals(CollectionFixtures.SAVES, engine.equipped(CollectionKind.TITLE)?.id)
        assertEquals(ids, fixture.store.snapshot().ownedIds)
        assertEquals(setOf(CollectionFixtures.STARTER), fixture.store.snapshot().unseenIds)
    }

    @Test
    fun `unknown IDs fall back without discarding data and resolve when a catalog adds them`() {
        val unknown = "crest.future.unavailable"
        val state =
            CodeXPCollectionState(
                ownedIds = mutableSetOf(unknown),
                unseenIds = mutableSetOf(unknown, "future.unseen.orphan"),
                equippedIds = mutableMapOf("crest" to unknown, "future.slot" to "future.slot.item"),
            )
        val fixture = CollectionFixture(state)
        assertNull(fixture.engine.preview(unknown).definition)
        assertTrue(fixture.engine.preview(unknown).owned)
        assertNull(fixture.engine.equipped(CollectionKind.CREST))
        assertEquals(state, fixture.store.snapshot())
        val restoredCatalog = CollectionCatalog(fixture.catalog.items() + CollectionFixtures.item(unknown, CollectionKind.CREST, CollectionAcquisition.Automatic(CollectionRequirement.Always)))
        val newer = CollectionEngine(restoredCatalog, fixture.store)
        assertEquals(unknown, newer.equipped(CollectionKind.CREST)?.id)
        assertEquals(state, fixture.store.snapshot())
    }

    @Test
    fun `inconsistent stored equipment falls back without repairing persisted IDs`() {
        val state = CodeXPCollectionState(equippedIds = mutableMapOf("crest" to CollectionFixtures.XP, "title" to CollectionFixtures.STARTER))
        val fixture = CollectionFixture(state)
        assertNull(fixture.engine.equipped(CollectionKind.CREST))
        assertNull(fixture.engine.equipped(CollectionKind.TITLE))
        assertEquals(state, fixture.store.snapshot())
    }

    @Test
    fun `store detaches input and snapshots so callers cannot bypass transactions`() {
        val initial = CodeXPCollectionState(ownedIds = mutableSetOf(CollectionFixtures.STARTER), choiceCredits = 2)
        val fixture = CollectionFixture(initial)
        val expected = initial.copy(ownedIds = initial.ownedIds.toMutableSet())
        initial.ownedIds.clear()
        initial.choiceCredits = 99
        val snapshot = fixture.store.snapshot()
        snapshot.ownedIds.clear()
        snapshot.unseenIds.add("injected")
        snapshot.equippedIds["crest"] = "injected"
        snapshot.creditedEntitlements["injected"] = 99
        snapshot.choiceCredits = 0
        assertEquals(expected, fixture.store.snapshot())
    }

    @Test
    fun `future schemas and corrupt wallets are preserved read-only`() {
        for (state in listOf(CodeXPCollectionState(schemaVersion = 2), CodeXPCollectionState(choiceCredits = -1), CodeXPCollectionState(creditedEntitlements = mutableMapOf("future" to -1)))) {
            val fixture = CollectionFixture(state)
            assertEquals(CollectionOutcome.READ_ONLY_STATE, fixture.engine.unlock(fixture.session, CollectionFixtures.facts()).outcome)
            assertEquals(CollectionOutcome.READ_ONLY_STATE, fixture.engine.recordChoiceEntitlement(fixture.session, "fixture", 10).outcome)
            assertEquals(CollectionOutcome.READ_ONLY_STATE, fixture.engine.choose(fixture.session, CollectionFixtures.CHOICE_TITLE, CollectionFixtures.facts(11)).outcome)
            assertEquals(state, fixture.store.snapshot())
        }
    }
}
