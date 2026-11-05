package com.github.ilovegamecoding.intellijcodexp.domain.collection

import com.github.ilovegamecoding.intellijcodexp.enums.Event

enum class CollectionKind(
    val storageKey: String,
) {
    CREST("crest"),
    TITLE("title"),
}

/** Explicit read-only inputs, never a second persisted XP counter or a level policy. */
class CollectionFacts(
    val totalXP: Long,
    eventCounts: Map<Event, Long> = emptyMap(),
    completedChallenges: Map<Event, Long> = emptyMap(),
    /** Explicit historical evidence from a reviewed policy; absent evidence never implies a level. */
    val highestAchievedLevel: Int? = null,
) {
    val eventCounts: Map<Event, Long> = eventCounts.toMap()
    val completedChallenges: Map<Event, Long> = completedChallenges.toMap()

    init {
        require(totalXP >= 0)
        require(highestAchievedLevel == null || highestAchievedLevel >= 1)
        require(this.eventCounts.values.all { it >= 0 })
        require(this.completedChallenges.values.all { it >= 0 })
    }
}

sealed interface CollectionRequirement {
    fun isMet(facts: CollectionFacts): Boolean

    data object Always : CollectionRequirement {
        override fun isMet(facts: CollectionFacts): Boolean = true
    }

    data class TotalXP(
        val minimum: Long,
    ) : CollectionRequirement {
        init {
            require(minimum > 0)
        }

        override fun isMet(facts: CollectionFacts): Boolean = facts.totalXP >= minimum
    }

    /** A previously achieved level qualifies even if a future policy displays a lower level. */
    data class AchievedLevel(
        val minimum: Int,
    ) : CollectionRequirement {
        init {
            require(minimum >= 1)
        }

        override fun isMet(facts: CollectionFacts): Boolean = facts.highestAchievedLevel?.let { it >= minimum } ?: false
    }

    data class EventCount(
        val event: Event,
        val minimum: Long,
    ) : CollectionRequirement {
        init {
            require(event != Event.NONE && minimum > 0)
        }

        override fun isMet(facts: CollectionFacts): Boolean = (facts.eventCounts[event] ?: 0) >= minimum
    }

    data class CompletedChallenges(
        val minimum: Long,
        val event: Event? = null,
    ) : CollectionRequirement {
        init {
            require(minimum > 0 && event != Event.NONE)
        }

        override fun isMet(facts: CollectionFacts): Boolean {
            if (event != null) return (facts.completedChallenges[event] ?: 0) >= minimum
            // Compare without overflowing when multiple imported counters approach Long.MAX_VALUE.
            var remaining = minimum
            for (count in facts.completedChallenges.values) {
                if (count >= remaining) return true
                remaining -= count
            }
            return false
        }
    }

    /** Values supplied here are validated, completed ordinals, never the active challenge number. */
    data class CompletedOrdinal(
        val minimum: Long,
    ) : CollectionRequirement {
        init {
            require(minimum > 0)
        }

        override fun isMet(facts: CollectionFacts): Boolean = facts.completedChallenges.values.any { it >= minimum }
    }

    class AllOf(
        requirements: List<CollectionRequirement>,
    ) : CollectionRequirement {
        private val requirements = requirements.toList()

        init {
            require(this.requirements.isNotEmpty())
        }

        override fun isMet(facts: CollectionFacts): Boolean = requirements.all { it.isMet(facts) }
    }
}

sealed interface CollectionAcquisition {
    val requirement: CollectionRequirement

    data class Automatic(
        override val requirement: CollectionRequirement,
    ) : CollectionAcquisition

    data class Choice(
        val cost: Long,
        override val requirement: CollectionRequirement,
    ) : CollectionAcquisition {
        init {
            require(cost > 0)
        }
    }
}

data class CollectionItemDefinition(
    val id: String,
    val kind: CollectionKind,
    val displayName: String,
    val acquisition: CollectionAcquisition,
) {
    init {
        require(id.matches(Regex("[a-z0-9]+(?:[._-][a-z0-9]+)*"))) { "Use a stable, non-localized item ID." }
        require(displayName.isNotBlank())
    }
}

/** No shipping catalog, release quantity, default prices or progression numbers are selected here. */
class CollectionCatalog(
    items: List<CollectionItemDefinition>,
) {
    private val byId = items.associateBy { it.id }

    init {
        require(byId.size == items.size) { "Collection IDs must be unique." }
    }

    fun find(id: String): CollectionItemDefinition? = byId[id]

    fun items(): List<CollectionItemDefinition> = byId.values.toList()
}
