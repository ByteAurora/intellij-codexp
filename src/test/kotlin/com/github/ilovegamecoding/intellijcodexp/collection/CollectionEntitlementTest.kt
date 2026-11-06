package com.github.ilovegamecoding.intellijcodexp.collection

import com.github.ilovegamecoding.intellijcodexp.domain.collection.CollectionOutcome
import org.junit.Assert.assertEquals
import org.junit.Test

class CollectionEntitlementTest {
    @Test
    fun `cumulative grants cannot replay after spending or an older request`() {
        val fixture = CollectionFixture()
        val engine = fixture.engine
        assertEquals(5L, engine.recordChoiceEntitlement(fixture.session, "fixture-source", 5).creditsAdded)
        engine.choose(fixture.session, CollectionFixtures.CHOICE_CREST, CollectionFixtures.facts())
        val afterSpending = fixture.store.snapshot()
        assertEquals(3L, afterSpending.choiceCredits)
        for (earned in listOf(0L, 3L, 5L)) {
            assertEquals(CollectionOutcome.UNCHANGED, engine.recordChoiceEntitlement(fixture.session, "fixture-source", earned).outcome)
            assertEquals(afterSpending, fixture.store.snapshot())
        }
        assertEquals(2L, engine.recordChoiceEntitlement(fixture.session, "fixture-source", 7).creditsAdded)
        assertEquals(5L, fixture.store.snapshot().choiceCredits)
        assertEquals(mapOf("fixture-source" to 7L), fixture.store.snapshot().creditedEntitlements)
    }

    @Test
    fun `independent sources retain their own high watermarks`() {
        val fixture = CollectionFixture()
        fixture.engine.recordChoiceEntitlement(fixture.session, "first-source", 4)
        fixture.engine.recordChoiceEntitlement(fixture.session, "second-source", 3)
        fixture.engine.recordChoiceEntitlement(fixture.session, "first-source", 5)
        fixture.engine.recordChoiceEntitlement(fixture.session, "second-source", 3)
        assertEquals(8L, fixture.store.snapshot().choiceCredits)
        assertEquals(mapOf("first-source" to 5L, "second-source" to 3L), fixture.store.snapshot().creditedEntitlements)
    }

    @Test(timeout = 1000)
    fun `large grants are arithmetic updates and overflow rolls back the whole grant`() {
        val fixture = CollectionFixture()
        assertEquals(Long.MAX_VALUE, fixture.engine.recordChoiceEntitlement(fixture.session, "large-source", Long.MAX_VALUE).creditsAdded)
        val full = fixture.store.snapshot()
        assertEquals(CollectionOutcome.CREDIT_OVERFLOW, fixture.engine.recordChoiceEntitlement(fixture.session, "other-source", 1).outcome)
        assertEquals(full, fixture.store.snapshot())
        fixture.engine.choose(fixture.session, CollectionFixtures.CHOICE_CREST, CollectionFixtures.facts())
        assertEquals(1L, fixture.engine.recordChoiceEntitlement(fixture.session, "other-source", 1).creditsAdded)
        assertEquals(CollectionOutcome.UNCHANGED, fixture.engine.recordChoiceEntitlement(fixture.session, "large-source", Long.MAX_VALUE).outcome)
        assertEquals(Long.MAX_VALUE - 1, fixture.store.snapshot().choiceCredits)
        assertEquals(mapOf("large-source" to Long.MAX_VALUE, "other-source" to 1L), fixture.store.snapshot().creditedEntitlements)
    }

    @Test
    fun `invalid entitlements cannot consume a watermark or modify the balance`() {
        val fixture = CollectionFixture()
        val before = fixture.store.snapshot()
        assertEquals(CollectionOutcome.INVALID_ENTITLEMENT, fixture.engine.recordChoiceEntitlement(fixture.session, "fixture", -1).outcome)
        assertEquals(CollectionOutcome.INVALID_ENTITLEMENT, fixture.engine.recordChoiceEntitlement(fixture.session, " ", 5).outcome)
        assertEquals(CollectionOutcome.UNCHANGED, fixture.engine.recordChoiceEntitlement(fixture.session, "fixture", 0).outcome)
        assertEquals(before, fixture.store.snapshot())
    }
}
