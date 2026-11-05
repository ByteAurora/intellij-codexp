package com.github.ilovegamecoding.intellijcodexp.collection

import com.github.ilovegamecoding.intellijcodexp.domain.collection.CollectionAcquisition
import com.github.ilovegamecoding.intellijcodexp.domain.collection.CollectionCatalog
import com.github.ilovegamecoding.intellijcodexp.domain.collection.CollectionEngine
import com.github.ilovegamecoding.intellijcodexp.domain.collection.CollectionKind
import com.github.ilovegamecoding.intellijcodexp.domain.collection.CollectionOutcome
import com.github.ilovegamecoding.intellijcodexp.domain.collection.CollectionRequirement
import com.github.ilovegamecoding.intellijcodexp.models.CodeXPCollectionState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.Callable
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class CollectionConcurrencyTest {
    @Test
    fun `two windows cannot spend the final credit on different items`() {
        val catalog =
            CollectionCatalog(
                listOf("crest.fixture.first", "crest.fixture.second").map {
                    CollectionFixtures.item(it, CollectionKind.CREST, CollectionAcquisition.Choice(1, CollectionRequirement.Always))
                },
            )
        val fixture = CollectionFixture(CodeXPCollectionState(choiceCredits = 1), catalog)
        val windows = List(2) { CollectionEngine(catalog, fixture.store) }
        val results =
            race(
                catalog.items().mapIndexed { index, item ->
                    { windows[index].choose(fixture.session, item.id, CollectionFixtures.facts()) }
                },
            )
        assertEquals(1, results.count { it.outcome == CollectionOutcome.APPLIED })
        assertEquals(1, results.count { it.outcome == CollectionOutcome.INSUFFICIENT_CREDITS })
        val snapshot = fixture.store.snapshot()
        assertEquals(0L, snapshot.choiceCredits)
        assertEquals(results.flatMap { it.acquiredIds }.toSet(), snapshot.ownedIds)
        assertEquals(snapshot.ownedIds, snapshot.unseenIds)
        assertTrue(snapshot.equippedIds.isEmpty())
    }

    @Test
    fun `repeated simultaneous purchases acquire and charge once even with enough credits`() {
        val fixture = CollectionFixture(CodeXPCollectionState(choiceCredits = 100))
        val windows = List(8) { CollectionEngine(fixture.catalog, fixture.store) }
        val results = race(windows.map { engine -> { engine.choose(fixture.session, CollectionFixtures.CHOICE_CREST, CollectionFixtures.facts()) } })
        assertEquals(1, results.count { it.outcome == CollectionOutcome.APPLIED })
        assertEquals(7, results.count { it.outcome == CollectionOutcome.ALREADY_OWNED })
        assertEquals(98L, fixture.store.snapshot().choiceCredits)
        assertEquals(setOf(CollectionFixtures.CHOICE_CREST), fixture.store.snapshot().ownedIds)
    }

    @Test
    fun `out of order concurrent grants and save snapshots remain consistent`() {
        val fixture = CollectionFixture()
        val results =
            race(
                (1L..16L).map { earned ->
                    {
                        val result = fixture.engine.recordChoiceEntitlement(fixture.session, "fixture-source", earned)
                        val saved = fixture.store.snapshot()
                        assertEquals(saved.choiceCredits, saved.creditedEntitlements.getValue("fixture-source"))
                        result
                    }
                },
            )
        assertEquals(16L, results.sumOf { it.creditsAdded })
        assertEquals(16L, fixture.store.snapshot().choiceCredits)
        assertEquals(16L, fixture.store.snapshot().creditedEntitlements["fixture-source"])
    }

    @Test
    fun `reset clears every collection field and rejects commands queued by old windows`() {
        val fixture =
            CollectionFixture(
                CodeXPCollectionState(
                    ownedIds = mutableSetOf(CollectionFixtures.STARTER, "future.item"),
                    unseenIds = mutableSetOf("future.item"),
                    equippedIds = mutableMapOf("crest" to CollectionFixtures.STARTER, "future.slot" to "future.item"),
                    choiceCredits = 8,
                    creditedEntitlements = mutableMapOf("fixture-source" to 10),
                ),
            )
        val otherWindow = CollectionEngine(fixture.catalog, fixture.store)
        assertEquals(CollectionOutcome.APPLIED, fixture.store.reset(fixture.session).outcome)
        assertEquals(CodeXPCollectionState(), fixture.store.snapshot())
        assertNotSame(fixture.session, fixture.store.session())
        assertEquals(CollectionOutcome.STALE_SESSION, otherWindow.recordChoiceEntitlement(fixture.session, "fixture-source", 100).outcome)
        assertEquals(CollectionOutcome.STALE_SESSION, otherWindow.choose(fixture.session, CollectionFixtures.CHOICE_CREST, CollectionFixtures.facts()).outcome)
        assertEquals(CollectionOutcome.STALE_SESSION, otherWindow.equip(fixture.session, CollectionKind.CREST, CollectionFixtures.STARTER).outcome)
        assertEquals(CollectionOutcome.STALE_SESSION, otherWindow.acknowledge(fixture.session, "future.item").outcome)
        assertEquals(CollectionOutcome.STALE_SESSION, otherWindow.unlock(fixture.session, CollectionFixtures.facts()).outcome)
        assertEquals(CollectionOutcome.STALE_SESSION, fixture.store.reset(fixture.session).outcome)
        assertEquals(CodeXPCollectionState(), fixture.store.snapshot())
        assertEquals(1L, otherWindow.recordChoiceEntitlement(fixture.store.session(), "fixture-source", 1).creditsAdded)
    }

    @Test
    fun `a session from another profile cannot mutate this store`() {
        val first = CollectionFixture()
        val second = CollectionFixture()
        assertEquals(CollectionOutcome.STALE_SESSION, second.engine.recordChoiceEntitlement(first.session, "fixture-source", 10).outcome)
        assertEquals(CodeXPCollectionState(), second.store.snapshot())
    }

    private fun <T> race(actions: List<() -> T>): List<T> {
        val executor = Executors.newFixedThreadPool(actions.size)
        val ready = CountDownLatch(actions.size)
        val start = CountDownLatch(1)
        try {
            val results =
                actions.map { action ->
                    executor.submit(
                        Callable {
                            ready.countDown()
                            check(start.await(10, TimeUnit.SECONDS))
                            action()
                        },
                    )
                }
            assertTrue(ready.await(10, TimeUnit.SECONDS))
            start.countDown()
            return results.map { it.get(10, TimeUnit.SECONDS) }
        } finally {
            start.countDown()
            executor.shutdownNow()
            assertTrue(executor.awaitTermination(10, TimeUnit.SECONDS))
        }
    }
}
