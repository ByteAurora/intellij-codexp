package com.github.ilovegamecoding.intellijcodexp.services

import com.github.ilovegamecoding.intellijcodexp.CodeXPBundle
import com.github.ilovegamecoding.intellijcodexp.domain.CodeXPGrowthPolicy
import com.github.ilovegamecoding.intellijcodexp.domain.collection.ChoiceAward
import com.github.ilovegamecoding.intellijcodexp.domain.collection.CodeXPCollectionCatalog
import com.github.ilovegamecoding.intellijcodexp.domain.collection.CollectionContent
import com.github.ilovegamecoding.intellijcodexp.domain.collection.CollectionEngine
import com.github.ilovegamecoding.intellijcodexp.domain.collection.CollectionFacts
import com.github.ilovegamecoding.intellijcodexp.domain.collection.CollectionKind
import com.github.ilovegamecoding.intellijcodexp.domain.collection.CollectionOutcome
import com.github.ilovegamecoding.intellijcodexp.domain.collection.CollectionResult
import com.github.ilovegamecoding.intellijcodexp.domain.collection.CollectionRoute
import com.github.ilovegamecoding.intellijcodexp.domain.collection.CollectionSession
import com.github.ilovegamecoding.intellijcodexp.domain.collection.CollectionStore
import com.github.ilovegamecoding.intellijcodexp.enums.EffectStyle
import com.github.ilovegamecoding.intellijcodexp.enums.Event
import com.github.ilovegamecoding.intellijcodexp.models.CodeXPChallengeFactory
import com.github.ilovegamecoding.intellijcodexp.models.CodeXPCollectionMigration
import com.github.ilovegamecoding.intellijcodexp.models.CodeXPCollectionRecord
import com.github.ilovegamecoding.intellijcodexp.models.CodeXPCollectionState
import com.github.ilovegamecoding.intellijcodexp.models.CodeXPLevel
import com.github.ilovegamecoding.intellijcodexp.models.CodeXPState
import com.github.ilovegamecoding.intellijcodexp.presentation.collection.CollectionDisplaySnapshot
import com.github.ilovegamecoding.intellijcodexp.presentation.collection.CollectionEntry
import java.io.IOException
import java.util.UUID

/** Shared by every project window. All collection mutations commit before becoming visible. */
class CodeXPCollectionCoordinator(
    private val persistence: CodeXPCollectionPersistence,
    private val content: CollectionContent = CodeXPCollectionCatalog.content,
) {
    private var profile = CodeXPState()
    private var record = CodeXPCollectionRecord()
    private var store = CollectionStore()
    private var engine = CollectionEngine(content.catalog, store)
    private var historySize = -1
    private var ordinals: Map<Event, Long> = emptyMap()
    private var readOnly = false
    private var dirty = false
    private var policyNeedsCommit = false
    private var storageProblem: String? = null
    private var celebrationsArmed = false
    private var pendingChoiceAward: ChoiceAward? = null

    fun load(incoming: CodeXPState): CodeXPState = load(incoming, true, incoming.legacyLineage())

    @Synchronized
    internal fun load(
        incoming: CodeXPState,
        hasPersistedProfile: Boolean,
        legacyLineage: String?,
    ): CodeXPState {
        celebrationsArmed = false
        pendingChoiceAward = null
        readOnly = false
        storageProblem = null
        val saved =
            try {
                persistence.load()
            } catch (_: IOException) {
                readOnly = true
                storageProblem = CodeXPBundle.message("collection.error.load")
                null
            }
        record = saved ?: CodeXPCollectionRecord(generation = incoming.collectionGeneration.ifBlank { UUID.randomUUID().toString() })
        val migration = record.migration
        val previousPolicy =
            migration != null && migration.version == 1 && migration.policyId == CodeXPGrowthPolicy.PREVIOUS_ID &&
                migration.capXP == CodeXPGrowthPolicy.previousCapXP && migration.levelOffset == 0L
        val previousChoicePolicy =
            migration != null && migration.version == 2 && migration.policyId == CodeXPGrowthPolicy.PREVIOUS_CHOICE_ID &&
                migration.capXP == CodeXPGrowthPolicy.capXP && migration.levelOffset in 0..CodeXPGrowthPolicy.grandfatheredLevelOffset(Long.MAX_VALUE)
        val previousFixedChoicePolicy =
            migration != null && migration.version == 3 && migration.policyId == CodeXPGrowthPolicy.PREVIOUS_FIXED_CHOICE_ID &&
                migration.capXP == CodeXPGrowthPolicy.capXP && migration.levelOffset in 0..CodeXPGrowthPolicy.grandfatheredLevelOffset(Long.MAX_VALUE)
        val currentPolicy =
            migration != null && migration.version == CodeXPGrowthPolicy.MIGRATION_VERSION && migration.policyId == CodeXPGrowthPolicy.ID &&
                migration.capXP == CodeXPGrowthPolicy.capXP && migration.levelOffset in 0..CodeXPGrowthPolicy.grandfatheredLevelOffset(Long.MAX_VALUE)
        if (record.schemaVersion != 1 || record.resetCount < 0 || (saved != null && migration == null) ||
            (saved != null && (record.generation.isBlank() || record.checkpoint.collectionGeneration != record.generation)) ||
            record.collection.schemaVersion != 1 || record.collection.choiceCredits < 0 ||
            record.collection.creditedEntitlements.any { it.key.isBlank() || it.value < 0 } ||
            (
                migration != null && (
                    (!previousPolicy && !previousChoicePolicy && !previousFixedChoicePolicy && !currentPolicy) || migration.baselineXP < 0 ||
                        migration.intervalXP != (if (previousPolicy || previousChoicePolicy) CodeXPGrowthPolicy.PREVIOUS_CHOICE_INTERVAL_XP else CodeXPGrowthPolicy.CHOICE_INTERVAL_XP) ||
                        migration.choiceAnchorXP < 0 || migration.choiceAnchorCredits < 0 ||
                        migration.choiceAnchorCredits > Long.MAX_VALUE / CodeXPGrowthPolicy.PREVIOUS_CHOICE_INTERVAL_XP ||
                        ((previousPolicy || previousChoicePolicy) && (migration.choiceAnchorXP != 0L || migration.choiceAnchorCredits != 0L))
                )
            )
        ) {
            readOnly = true
            storageProblem = CodeXPBundle.message("collection.error.version")
        }
        val legacyReset = saved != null && hasPersistedProfile && !readOnly && isNewLegacyLineage(incoming, saved, legacyLineage)
        profile =
            when {
                saved == null -> incoming
                !hasPersistedProfile -> saved.checkpoint.detachedCopy()
                legacyReset -> incoming
                saved.resetCount > 0 && !belongsToGeneration(incoming, saved) -> saved.checkpoint.detachedCopy()
                incoming.xp < saved.checkpoint.xp -> saved.checkpoint.detachedCopy()
                else -> incoming
            }
        if (legacyReset) {
            record = nextGeneration(incoming, record.checkpoint)
        } else if (!readOnly && saved != null) {
            profile.collectionGeneration = record.generation
        }
        store = CollectionStore(record.collection)
        engine = CollectionEngine(content.catalog, store)
        historySize = -1
        if (record.migration == null && !readOnly) record.migration = migrationFor(profile.xp)
        policyNeedsCommit = (previousPolicy || previousChoicePolicy || previousFixedChoicePolicy) && !readOnly && !legacyReset
        if (policyNeedsCommit) {
            val levelOffset = if (previousPolicy) CodeXPGrowthPolicy.grandfatheredLevelOffset(profile.xp) else migration!!.levelOffset
            val eligibleXP = CodeXPGrowthPolicy.eligibleChoiceXP(profile.xp, migration.baselineXP, levelOffset)
            val sinceAnchor = (eligibleXP - migration.choiceAnchorXP).coerceAtLeast(0)
            val earned = migration.choiceAnchorCredits + sinceAnchor / migration.intervalXP
            record.migration =
                migration.copy(
                    version = CodeXPGrowthPolicy.MIGRATION_VERSION,
                    policyId = CodeXPGrowthPolicy.ID,
                    capXP = CodeXPGrowthPolicy.capXP,
                    levelOffset = levelOffset,
                    intervalXP = CodeXPGrowthPolicy.CHOICE_INTERVAL_XP,
                    choiceAnchorXP = eligibleXP - sinceAnchor % migration.intervalXP,
                    choiceAnchorCredits = maxOf(earned, record.collection.creditedEntitlements["postcap.intervals.v1"] ?: 0),
                )
        }
        profile.levelOffset = record.migration?.levelOffset ?: 0
        dirty = true
        reconcile()
        celebrationsArmed = true
        return profile
    }

    @Synchronized
    fun reconcile(): Boolean {
        dirty = true
        if (readOnly) return false
        val before = store.snapshot()
        val migration = record.migration ?: return false
        val initial = record.checkpoint.collectionGeneration.isEmpty()
        val acquired = engine.unlock(store.session(), facts()).acquiredIds
        val transitionGift = record.resetCount == 0L && migration.baselineXP > 0
        if (transitionGift) engine.recordChoiceEntitlement(store.session(), "migration.gift.v1", CodeXPGrowthPolicy.MIGRATION_GIFT)
        val total = migration.choiceAnchorCredits + choiceProgress(migration).additionalCredits
        engine.recordChoiceEntitlement(store.session(), "postcap.intervals.v1", total)
        if (before == store.snapshot() && !initial && !policyNeedsCommit) return true
        val candidate = record.detachedCopy()
        if (initial && transitionGift) {
            candidate.migration!!.noticePending = true
            candidate.migration!!.retroactiveItems = acquired.size
        }
        return commit(candidate, before)
    }

    @Synchronized
    fun takeChoiceAward(): ChoiceAward? = pendingChoiceAward.also { pendingChoiceAward = null }

    @Synchronized
    fun equippedCrest() =
        engine
            .equipped(CollectionKind.CREST)
            ?.id
            ?.let(content::find)
            ?.design

    @Synchronized
    fun snapshot(): CollectionDisplaySnapshot {
        val state = store.snapshot()
        val level = profile.levelInfo().level
        val previous = record.migration?.previousLevel ?: 1
        val highestOrdinal = completedOrdinals().values.maxOrNull() ?: 0
        val entries =
            content.items.map { item ->
                val definition = item.definition
                val owned = definition.id in state.ownedIds
                val current = if (item.route == CollectionRoute.CHALLENGE) highestOrdinal else level.toLong()
                val condition =
                    when (item.route) {
                        CollectionRoute.LEVEL -> CodeXPBundle.message("collection.condition.level", item.target)
                        CollectionRoute.CHALLENGE -> CodeXPBundle.message("collection.condition.challenge", item.target)
                        CollectionRoute.CHOICE -> CodeXPBundle.message("collection.condition.choice", item.target, CodeXPGrowthPolicy.CHOICE_COST)
                    }
                val progress =
                    when {
                        item.route == CollectionRoute.CHOICE && current >= item.target -> if (owned) CodeXPBundle.message("collection.progress.acquired") else CodeXPBundle.message("collection.progress.available")
                        item.route == CollectionRoute.CHALLENGE -> CodeXPBundle.message("collection.progress.challenge", current, item.target)
                        else -> CodeXPBundle.message("collection.progress.level", current, item.target)
                    }
                CollectionEntry(
                    definition.id,
                    definition.kind,
                    if (owned) {
                        definition.displayName
                    } else if (definition.kind == CollectionKind.CREST) {
                        CodeXPBundle.message("collection.locked.crest")
                    } else if (item.route == CollectionRoute.CHOICE) {
                        CodeXPBundle.message("collection.choice.hint.${definition.id}")
                    } else {
                        CodeXPBundle.message("collection.locked.title")
                    },
                    condition,
                    progress,
                    (current.toDouble() / item.target).coerceIn(0.0, 1.0),
                    owned,
                    owned && definition.id in state.unseenIds,
                    owned && state.equippedIds[definition.kind.storageKey] == definition.id,
                    item.route == CollectionRoute.CHOICE,
                    !readOnly && !owned && item.route == CollectionRoute.CHOICE && current >= item.target && state.choiceCredits >= CodeXPGrowthPolicy.CHOICE_COST,
                    item.design,
                    when (item.route) {
                        CollectionRoute.LEVEL -> CodeXPBundle.message("collection.hint.level", item.target)
                        CollectionRoute.CHALLENGE -> CodeXPBundle.message("collection.hint.challenge", item.target)
                        CollectionRoute.CHOICE -> condition
                    },
                )
            }
        val migration = record.migration
        val choiceProgress = migration?.let(::choiceProgress) ?: CodeXPGrowthPolicy.choiceProgress(0)
        return CollectionDisplaySnapshot(
            store.session(),
            entries,
            level,
            previous,
            state.choiceCredits,
            engine
                .equipped(CollectionKind.CREST)
                ?.id
                ?.let(content::find)
                ?.design,
            engine.equipped(CollectionKind.TITLE)?.displayName,
            profile.codeXPConfiguration.reducedMotion,
            EffectStyle.fromStoredValue(profile.codeXPConfiguration.effectStyle) == EffectStyle.SUBTLE,
            choiceProgress.progressXP,
            choiceProgress.requiredXP,
            migration?.takeIf { it.noticePending }?.let { CodeXPBundle.message("collection.migration.notice", it.retroactiveItems) },
            storageProblem,
        )
    }

    @Synchronized
    fun equip(
        session: CollectionSession,
        kind: CollectionKind,
        id: String?,
    ): CollectionResult = mutate(session) { engine.equip(session, kind, id) }

    @Synchronized
    fun acknowledge(
        session: CollectionSession,
        id: String,
    ): CollectionResult = mutate(session) { engine.acknowledge(session, id) }

    @Synchronized
    fun choose(
        session: CollectionSession,
        id: String,
    ): CollectionResult = mutate(session) { engine.choose(session, id, facts()) }

    @Synchronized
    fun acknowledgeMigration(): CollectionResult {
        if (readOnly) return CollectionResult(CollectionOutcome.READ_ONLY_STATE)
        if (record.migration?.noticePending != true) return CollectionResult(CollectionOutcome.UNCHANGED)
        val candidate = record.detachedCopy()
        candidate.migration!!.noticePending = false
        return CollectionResult(if (commit(candidate, store.snapshot())) CollectionOutcome.APPLIED else CollectionOutcome.SAVE_FAILED)
    }

    @Synchronized
    fun reset(initial: CodeXPState): Boolean {
        if (readOnly || record.resetCount == Long.MAX_VALUE) return false
        val candidate = nextGeneration(initial, profile)
        val freshStore = CollectionStore()
        val freshEngine = CollectionEngine(content.catalog, freshStore)
        freshEngine.unlock(freshStore.session(), CollectionFacts(initial.xp, highestAchievedLevel = initial.levelInfo().level))
        candidate.collection = freshStore.snapshot()
        candidate.checkpoint = initial.detachedCopy().apply { collectionGeneration = candidate.generation }
        try {
            persistence.save(candidate)
        } catch (_: IOException) {
            storageProblem = CodeXPBundle.message("collection.error.reset")
            return false
        }
        initial.collectionGeneration = candidate.generation
        profile = initial
        record = candidate
        pendingChoiceAward = null
        store = freshStore
        engine = freshEngine
        policyNeedsCommit = false
        historySize = -1
        storageProblem = null
        dirty = false
        return true
    }

    @Synchronized
    fun markDirty() {
        dirty = true
    }

    @Synchronized
    fun checkpoint() {
        if (dirty && !readOnly && reconcile()) commit(record.detachedCopy(), store.snapshot())
    }

    internal fun savedRecord(): CodeXPCollectionRecord = record.detachedCopy()

    private fun nextGeneration(
        initial: CodeXPState,
        previous: CodeXPState,
    ): CodeXPCollectionRecord =
        CodeXPCollectionRecord(
            generation = UUID.randomUUID().toString(),
            resetCount = record.resetCount + 1,
            generationAnchors = initial.challenges.values.mapTo(mutableSetOf()) { it.id },
            retiredLegacyLineages = record.retiredLegacyLineages.toMutableSet().apply { previous.legacyLineage()?.let(::add) },
            migration = migrationFor(initial.xp),
        )

    private fun isNewLegacyLineage(
        incoming: CodeXPState,
        saved: CodeXPCollectionRecord,
        incomingId: String?,
    ): Boolean {
        // Pre-fence RC records may not identify every discarded profile. Keep rejecting those backups.
        if (incomingId == null || incoming.collectionGeneration.isNotEmpty() || saved.retiredLegacyLineages.size.toLong() != saved.resetCount) return false
        val currentId = saved.checkpoint.legacyLineage() ?: return false
        return incomingId != currentId && incomingId !in saved.retiredLegacyLineages && !belongsToGeneration(incoming, saved)
    }

    private fun belongsToGeneration(
        incoming: CodeXPState,
        saved: CodeXPCollectionRecord,
    ): Boolean {
        if (incoming.collectionGeneration.isNotEmpty()) return incoming.collectionGeneration == saved.generation
        val anchors =
            saved.generationAnchors.ifEmpty {
                saved.checkpoint.challenges.values
                    .mapTo(mutableSetOf()) { it.id }
            }
        return (incoming.challenges.values.asSequence() + incoming.completedChallenges.asSequence()).any { it.id.isNotBlank() && it.id in anchors }
    }

    private fun mutate(
        session: CollectionSession,
        action: () -> CollectionResult,
    ): CollectionResult {
        if (session !== store.session()) return CollectionResult(CollectionOutcome.STALE_SESSION)
        if (readOnly) return CollectionResult(CollectionOutcome.READ_ONLY_STATE)
        if (!reconcile()) return CollectionResult(CollectionOutcome.SAVE_FAILED)
        val before = store.snapshot()
        val result = action()
        if (result.outcome != CollectionOutcome.APPLIED) return result
        return if (commit(record.detachedCopy(), before)) result else CollectionResult(CollectionOutcome.SAVE_FAILED)
    }

    private fun commit(
        candidate: CodeXPCollectionRecord,
        before: CodeXPCollectionState,
    ): Boolean {
        candidate.collection = store.snapshot()
        candidate.checkpoint = profile.detachedCopy().apply { collectionGeneration = candidate.generation }
        try {
            persistence.save(candidate)
        } catch (_: IOException) {
            store = CollectionStore(before)
            engine = CollectionEngine(content.catalog, store)
            storageProblem = CodeXPBundle.message("collection.error.save.retry")
            return false
        }
        val previousRights = record.collection.creditedEntitlements["postcap.intervals.v1"] ?: 0
        val committedRights = candidate.collection.creditedEntitlements["postcap.intervals.v1"] ?: 0
        if (celebrationsArmed && committedRights > previousRights) {
            val first = pendingChoiceAward?.takeIf { it.generation == candidate.generation }?.first ?: (previousRights + 1)
            pendingChoiceAward = ChoiceAward(candidate.generation, first, committedRights)
        }
        record = candidate
        profile.collectionGeneration = candidate.generation
        storageProblem = null
        policyNeedsCommit = false
        dirty = false
        return true
    }

    private fun facts(): CollectionFacts =
        CollectionFacts(
            profile.xp.coerceAtLeast(0),
            completedChallenges = completedOrdinals(),
            highestAchievedLevel = maxOf(profile.levelInfo().level, record.migration?.previousLevel ?: 1),
        )

    private fun choiceProgress(migration: CodeXPCollectionMigration) =
        CodeXPGrowthPolicy.choiceProgress(
            (CodeXPGrowthPolicy.eligibleChoiceXP(profile.xp, migration.baselineXP, profile.levelOffset) - migration.choiceAnchorXP).coerceAtLeast(0),
            migration.choiceAnchorCredits,
        )

    private fun completedOrdinals(): Map<Event, Long> {
        if (historySize != profile.completedChallenges.size) {
            historySize = profile.completedChallenges.size
            val highest = mutableMapOf<Event, Long>()
            profile.completedChallenges.forEach { challenge ->
                if (challenge.goal > 0 && challenge.progress >= challenge.goal) {
                    CodeXPChallengeFactory.sequenceNumber(challenge)?.let { ordinal ->
                        highest[challenge.event] = maxOf(highest[challenge.event] ?: 0, ordinal)
                    }
                }
            }
            ordinals = highest
        }
        return ordinals
    }

    private fun migrationFor(xp: Long): CodeXPCollectionMigration =
        CodeXPCollectionMigration(
            version = CodeXPGrowthPolicy.MIGRATION_VERSION,
            policyId = CodeXPGrowthPolicy.ID,
            baselineXP = xp.coerceAtLeast(0),
            previousLevel = CodeXPLevel.createLegacyLevelInfo(xp).level,
            levelOffset = CodeXPGrowthPolicy.grandfatheredLevelOffset(xp),
            capXP = CodeXPGrowthPolicy.capXP,
            intervalXP = CodeXPGrowthPolicy.CHOICE_INTERVAL_XP,
        )
}

// The first typing challenge moves into History on completion. A 2.2.0 reset regenerates it;
// ordinary downgraded writes only strip collectionGeneration.
internal fun CodeXPState.legacyLineage(): String? = (completedChallenges.firstOrNull { it.event == Event.TYPING } ?: challenges[Event.TYPING])?.id?.takeIf { it.isNotBlank() }
