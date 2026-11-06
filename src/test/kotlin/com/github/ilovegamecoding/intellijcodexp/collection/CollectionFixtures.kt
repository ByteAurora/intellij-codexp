package com.github.ilovegamecoding.intellijcodexp.collection

import com.github.ilovegamecoding.intellijcodexp.domain.collection.CollectionAcquisition
import com.github.ilovegamecoding.intellijcodexp.domain.collection.CollectionCatalog
import com.github.ilovegamecoding.intellijcodexp.domain.collection.CollectionEngine
import com.github.ilovegamecoding.intellijcodexp.domain.collection.CollectionFacts
import com.github.ilovegamecoding.intellijcodexp.domain.collection.CollectionItemDefinition
import com.github.ilovegamecoding.intellijcodexp.domain.collection.CollectionKind
import com.github.ilovegamecoding.intellijcodexp.domain.collection.CollectionRequirement
import com.github.ilovegamecoding.intellijcodexp.domain.collection.CollectionStore
import com.github.ilovegamecoding.intellijcodexp.enums.Event
import com.github.ilovegamecoding.intellijcodexp.models.CodeXPCollectionState

/** Arbitrary test fixtures, not proposed shipping items, quantities, prices or XP policy. */
internal object CollectionFixtures {
    const val STARTER = "crest.fixture.starter"
    const val XP = "crest.fixture.xp"
    const val SAVES = "title.fixture.saves"
    const val HISTORY = "title.fixture.history"
    const val CHOICE_CREST = "crest.fixture.choice"
    const val CHOICE_TITLE = "title.fixture.choice"
    const val LEVEL_TWO = "crest.fixture.level-two"
    const val LEVEL_FIVE = "crest.fixture.level-five"
    const val LEVEL_NINE = "title.fixture.level-nine"

    fun levelCatalog(): CollectionCatalog =
        CollectionCatalog(
            listOf(
                checkNotNull(catalog().find(STARTER)),
                item(LEVEL_TWO, CollectionKind.CREST, CollectionAcquisition.Automatic(CollectionRequirement.AchievedLevel(2))),
                item(LEVEL_FIVE, CollectionKind.CREST, CollectionAcquisition.Automatic(CollectionRequirement.AchievedLevel(5))),
                item(LEVEL_NINE, CollectionKind.TITLE, CollectionAcquisition.Automatic(CollectionRequirement.AchievedLevel(9))),
            ),
        )

    fun catalog(): CollectionCatalog =
        CollectionCatalog(
            listOf(
                item(STARTER, CollectionKind.CREST, CollectionAcquisition.Automatic(CollectionRequirement.Always)),
                item(XP, CollectionKind.CREST, CollectionAcquisition.Automatic(CollectionRequirement.TotalXP(11))),
                item(SAVES, CollectionKind.TITLE, CollectionAcquisition.Automatic(CollectionRequirement.EventCount(Event.SAVE, 3))),
                item(HISTORY, CollectionKind.TITLE, CollectionAcquisition.Automatic(CollectionRequirement.CompletedChallenges(2))),
                item(CHOICE_CREST, CollectionKind.CREST, CollectionAcquisition.Choice(2, CollectionRequirement.Always)),
                item(CHOICE_TITLE, CollectionKind.TITLE, CollectionAcquisition.Choice(1, CollectionRequirement.TotalXP(11))),
            ),
        )

    fun item(
        id: String,
        kind: CollectionKind,
        acquisition: CollectionAcquisition,
    ): CollectionItemDefinition = CollectionItemDefinition(id, kind, id, acquisition)

    fun facts(
        xp: Long = 0,
        saves: Long = 0,
        completedSaves: Long = 0,
        completedRuns: Long = 0,
        highestAchievedLevel: Int? = null,
    ): CollectionFacts =
        CollectionFacts(
            totalXP = xp,
            eventCounts = mapOf(Event.SAVE to saves),
            completedChallenges = mapOf(Event.SAVE to completedSaves, Event.RUN to completedRuns),
            highestAchievedLevel = highestAchievedLevel,
        )
}

internal class CollectionFixture(
    state: CodeXPCollectionState = CodeXPCollectionState(),
    val catalog: CollectionCatalog = CollectionFixtures.catalog(),
) {
    val store = CollectionStore(state)
    val engine = CollectionEngine(catalog, store)
    val session = store.session()
}
