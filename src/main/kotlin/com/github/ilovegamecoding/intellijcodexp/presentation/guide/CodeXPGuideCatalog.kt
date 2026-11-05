package com.github.ilovegamecoding.intellijcodexp.presentation.guide

internal enum class GuideDestination { DASHBOARD, COLLECTION, SETTINGS }

internal data class GuideStep(
    val id: String,
    val destination: GuideDestination,
)

internal data class GuideRelease(
    val id: String,
    val version: String,
    val steps: List<GuideStep>,
)

internal data class GuideRun(
    val intro: Boolean,
    val releases: List<GuideRelease>,
    val steps: List<GuideStep>,
) {
    val key: String get() = if (intro) "intro" else releases.joinToString("+") { it.id }
}

/** Append releases oldest first. Stable IDs, not the installed patch version, control unread notices. */
internal object CodeXPGuideCatalog {
    val intro =
        listOf(
            GuideStep("xp", GuideDestination.DASHBOARD),
            GuideStep("challenges", GuideDestination.DASHBOARD),
            GuideStep("collection", GuideDestination.COLLECTION),
            GuideStep("credits", GuideDestination.COLLECTION),
            GuideStep("mini", GuideDestination.SETTINGS),
        )
    val releases =
        listOf(
            GuideRelease(
                "collections-2.3",
                "2.3.0",
                listOf(
                    GuideStep("collection", GuideDestination.COLLECTION),
                    GuideStep("growth", GuideDestination.DASHBOARD),
                    GuideStep("credits", GuideDestination.COLLECTION),
                    GuideStep("mini", GuideDestination.SETTINGS),
                ),
            ),
        )
}
