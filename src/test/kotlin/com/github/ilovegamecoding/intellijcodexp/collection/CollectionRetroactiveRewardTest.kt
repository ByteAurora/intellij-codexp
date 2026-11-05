package com.github.ilovegamecoding.intellijcodexp.collection

import com.github.ilovegamecoding.intellijcodexp.domain.collection.CollectionKind
import com.github.ilovegamecoding.intellijcodexp.domain.collection.CollectionOutcome
import com.github.ilovegamecoding.intellijcodexp.models.CodeXPCollectionState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CollectionRetroactiveRewardTest {
    @Test
    fun `all previously attained tiers are granted once without replacing equipment or awarding choices`() {
        val fixture =
            CollectionFixture(
                CodeXPCollectionState(
                    ownedIds = mutableSetOf(CollectionFixtures.STARTER),
                    equippedIds = mutableMapOf("crest" to CollectionFixtures.STARTER),
                    choiceCredits = 4,
                    creditedEntitlements = mutableMapOf("fixture-source" to 7),
                ),
                CollectionFixtures.levelCatalog(),
            )
        val facts = CollectionFixtures.facts(highestAchievedLevel = 20)
        val expected = setOf(CollectionFixtures.LEVEL_TWO, CollectionFixtures.LEVEL_FIVE, CollectionFixtures.LEVEL_NINE)
        assertEquals(expected, fixture.engine.unlock(fixture.session, facts).acquiredIds)
        assertEquals(expected, fixture.store.snapshot().unseenIds)
        assertEquals(expected + CollectionFixtures.STARTER, fixture.store.snapshot().ownedIds)
        assertEquals(CollectionFixtures.STARTER, fixture.engine.equipped(CollectionKind.CREST)?.id)
        assertEquals(4L, fixture.store.snapshot().choiceCredits)
        assertEquals(mapOf("fixture-source" to 7L), fixture.store.snapshot().creditedEntitlements)
        expected.forEach { fixture.engine.acknowledge(fixture.session, it) }
        val acknowledged = fixture.store.snapshot()
        repeat(3) {
            assertEquals(CollectionOutcome.UNCHANGED, fixture.engine.unlock(fixture.session, facts).outcome)
            assertEquals(acknowledged, fixture.store.snapshot())
        }
        // An older/out-of-order observation cannot revoke the backfilled rewards.
        fixture.engine.unlock(fixture.session, CollectionFixtures.facts(highestAchievedLevel = 3))
        assertEquals(acknowledged, fixture.store.snapshot())
    }

    @Test
    fun `an expanded catalog grants only missing historical rewards and does not replay acknowledged ones`() {
        val fixture =
            CollectionFixture(
                CodeXPCollectionState(
                    ownedIds = mutableSetOf(CollectionFixtures.STARTER, CollectionFixtures.LEVEL_TWO),
                    equippedIds = mutableMapOf("crest" to CollectionFixtures.LEVEL_TWO),
                ),
                CollectionFixtures.levelCatalog(),
            )
        assertEquals(CollectionOutcome.UNCHANGED, fixture.engine.unlock(fixture.session, CollectionFixtures.facts(xp = Long.MAX_VALUE)).outcome)
        val result = fixture.engine.unlock(fixture.session, CollectionFixtures.facts(highestAchievedLevel = 9))
        assertEquals(setOf(CollectionFixtures.LEVEL_FIVE, CollectionFixtures.LEVEL_NINE), result.acquiredIds)
        val awarded = fixture.store.snapshot()
        assertEquals(result.acquiredIds, awarded.unseenIds)
        assertTrue(awarded.ownedIds.contains(CollectionFixtures.LEVEL_TWO))
        assertEquals(CollectionFixtures.LEVEL_TWO, fixture.engine.equipped(CollectionKind.CREST)?.id)
    }
}
