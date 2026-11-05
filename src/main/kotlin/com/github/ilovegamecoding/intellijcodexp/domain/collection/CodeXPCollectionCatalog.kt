package com.github.ilovegamecoding.intellijcodexp.domain.collection

import com.github.ilovegamecoding.intellijcodexp.CodeXPBundle
import com.github.ilovegamecoding.intellijcodexp.domain.CodeXPGrowthPolicy

enum class CrestFamily { SEAL, WINGS, WAVE, CRYSTAL, ORBIT, SHIELD, PETALS, FAN, ARCH, GEAR }

data class CrestDesign(
    val family: CrestFamily,
    val form: Int,
)

enum class CollectionRoute { LEVEL, CHALLENGE, CHOICE }

data class CollectionContentItem(
    val definition: CollectionItemDefinition,
    val route: CollectionRoute,
    val target: Int,
    val design: CrestDesign? = null,
)

class CollectionContent(
    val items: List<CollectionContentItem>,
) {
    val catalog = CollectionCatalog(items.map { it.definition })

    fun find(id: String): CollectionContentItem? = items.firstOrNull { it.definition.id == id }
}

/** Stable IDs and approved artwork. All provisional RC thresholds are in CodeXPGrowthPolicy. */
object CodeXPCollectionCatalog {
    val content: CollectionContent by lazy {
        val crests =
            CrestFamily.entries.flatMapIndexed { index, family ->
                CollectionRoute.entries.mapIndexed { form, route ->
                    item(
                        "crest.${family.name.lowercase()}.${route.name.lowercase()}",
                        CollectionKind.CREST,
                        route,
                        when (route) {
                            CollectionRoute.LEVEL -> CodeXPGrowthPolicy.crestLevels[index]
                            CollectionRoute.CHALLENGE -> CodeXPGrowthPolicy.crestChallenges[index]
                            CollectionRoute.CHOICE -> CodeXPGrowthPolicy.MAX_LEVEL
                        },
                        CrestDesign(family, form + 1),
                    )
                }
            }
        val titles =
            listOf(
                "recorder",
                "memo",
                "morning",
                "night",
                "walker",
                "explorer",
                "collector",
                "assembler",
                "curious",
                "observer",
                "freestyle",
                "keyboard",
                "mouse",
                "shortcut",
                "tab",
                "space",
                "dark",
                "light",
                "local",
                "terminal",
                "library",
                "doodle",
                "stroll",
                "offline",
            ).mapIndexed { index, id ->
                val route = CollectionRoute.entries[index / 8]
                item(
                    "title.$id",
                    CollectionKind.TITLE,
                    route,
                    when (route) {
                        CollectionRoute.LEVEL -> CodeXPGrowthPolicy.titleLevels[index]
                        CollectionRoute.CHALLENGE -> CodeXPGrowthPolicy.titleChallenges[index - 8]
                        CollectionRoute.CHOICE -> CodeXPGrowthPolicy.MAX_LEVEL
                    },
                )
            }
        CollectionContent(crests + titles)
    }

    private fun item(
        id: String,
        kind: CollectionKind,
        route: CollectionRoute,
        target: Int,
        design: CrestDesign? = null,
    ): CollectionContentItem {
        val requirement = if (route == CollectionRoute.CHALLENGE) CollectionRequirement.CompletedOrdinal(target.toLong()) else CollectionRequirement.AchievedLevel(target)
        val acquisition = if (route == CollectionRoute.CHOICE) CollectionAcquisition.Choice(CodeXPGrowthPolicy.CHOICE_COST, requirement) else CollectionAcquisition.Automatic(requirement)
        val name = CodeXPBundle.message("collection.item.$id")
        return CollectionContentItem(CollectionItemDefinition(id, kind, name, acquisition), route, target, design)
    }
}
