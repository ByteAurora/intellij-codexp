package com.github.ilovegamecoding.intellijcodexp.presentation.overlay

import com.github.ilovegamecoding.intellijcodexp.domain.CodeXPProgressResult
import com.github.ilovegamecoding.intellijcodexp.domain.collection.ChoiceAward
import com.github.ilovegamecoding.intellijcodexp.domain.collection.CrestDesign
import com.github.ilovegamecoding.intellijcodexp.enums.CodeXPNotificationType
import com.github.ilovegamecoding.intellijcodexp.enums.EffectStyle
import com.github.ilovegamecoding.intellijcodexp.models.CodeXPConfiguration
import kotlin.math.exp

/** Immutable presentation snapshots. These never award XP or mutate progress. */
internal data class EffectOptions(
    val style: EffectStyle,
    val reducedMotion: Boolean,
) {
    val subtle: Boolean get() = style == EffectStyle.SUBTLE
    val timing: EffectTiming
        get() =
            when {
                reducedMotion -> EffectTiming.REDUCED
                subtle -> EffectTiming.SUBTLE
                else -> EffectTiming.CELEBRATORY
            }

    companion object {
        fun from(config: CodeXPConfiguration): EffectOptions = EffectOptions(EffectStyle.fromStoredValue(config.effectStyle), config.reducedMotion)
    }
}

private const val ACHIEVEMENT_TIME_SCALE = 1.5

internal fun achievementKeyframeMs(elapsedMs: Long): Long = (elapsedMs / ACHIEVEMENT_TIME_SCALE).toLong()

/** Only visible achievement phases stretch; gaps and the independent XP label use wall time. */
internal enum class EffectTiming(
    val challengeKeyframes: Long,
    levelGap: Long,
    val levelKeyframes: Long,
    val choiceKeyframes: Long,
    val equipKeyframes: Long,
    val xp: Long,
) {
    REDUCED(1800, 300, 2000, 1800, 1200, 1250),
    SUBTLE(2100, 250, 2200, 2100, 1100, 1100),
    CELEBRATORY(2850, 250, 3800, 2600, 1500, 1450),
    ;

    val challenge = (challengeKeyframes * ACHIEVEMENT_TIME_SCALE).toLong()
    val levelAt = challenge + levelGap
    val level = (levelKeyframes * ACHIEVEMENT_TIME_SCALE).toLong()
    val choice = (choiceKeyframes * ACHIEVEMENT_TIME_SCALE).toLong()
    val equip = (equipKeyframes * ACHIEVEMENT_TIME_SCALE).toLong()
}

internal data class ChallengeEffect(
    val id: String,
    val name: String,
    val description: String,
    val reward: Long,
    val count: Int = 1,
)

internal data class LevelEffect(
    val from: Int,
    val to: Int,
)

internal data class AchievementEffect(
    val challenge: ChallengeEffect?,
    val level: LevelEffect?,
    val options: EffectOptions,
    val crest: CrestDesign? = null,
    val choice: ChoiceAward? = null,
    val choiceCount: Long = choice?.count ?: 0,
    val equippedEventId: String? = null,
) {
    val choiceDuration: Long get() = options.timing.choice
    val equipDuration: Long get() = options.timing.equip
    val choiceAt: Long
        get() =
            if (level != null) {
                (if (challenge != null) options.timing.levelAt else 0) + options.timing.level
            } else if (challenge != null) {
                options.timing.challenge
            } else {
                0
            }
    val equipAt: Long get() = choiceAt + if (choice != null) choiceDuration else 0
    val duration: Long get() = equipAt + if (equippedEventId != null) equipDuration else 0

    companion object {
        fun from(
            result: CodeXPProgressResult,
            config: CodeXPConfiguration,
        ): AchievementEffect? {
            if (CodeXPNotificationType.fromStoredValue(config.notificationType) != CodeXPNotificationType.CODEXP) return null
            val challenge =
                result.completedChallenge?.takeIf { config.showCompleteChallengeNotification }?.let {
                    ChallengeEffect(it.id, it.name, it.description.replace("[goal]", it.goal.toString()), it.rewardXP)
                }
            val level =
                if (config.showLevelUpNotification && result.xpChanges.any { it.isLevelUp }) {
                    LevelEffect(
                        result.xpChanges
                            .first()
                            .beforeLevelInfo.level,
                        result.xpChanges
                            .last()
                            .currentLevelInfo.level,
                    )
                } else {
                    null
                }
            val choice = result.choiceAward?.takeIf { config.showLevelUpNotification }
            return if (challenge == null && level == null && choice == null) null else AchievementEffect(challenge, level, EffectOptions.from(config), choice = choice)
        }
    }
}

internal data class AchievementFrame(
    val effect: AchievementEffect,
    val age: Long,
    val motionSeconds: Double = age / 1000.0,
) {
    val choiceAge: Long? get() = (age - effect.choiceAt).takeIf { effect.choice != null && it in 0 until effect.choiceDuration }
    val equipAge: Long? get() = (age - effect.equipAt).takeIf { effect.equippedEventId != null && it in 0 until effect.equipDuration }
    val challengeAge: Long? get() = age.takeIf { effect.challenge != null && it < effect.options.timing.challenge }
    val levelAge: Long?
        get() =
            (age - if (effect.challenge != null) effect.options.timing.levelAt else 0).takeIf {
                effect.level != null && it >= 0 && it < effect.options.timing.level
            }
}

internal data class LevelNumber(
    val value: Int,
    val offset: Double = 0.0,
    val scale: Double = 1.0,
    val alpha: Double = 1.0,
)

/** One direct old -> actual target transition, including jumps and changes in digit count. */
internal fun levelNumbers(
    level: LevelEffect,
    options: EffectOptions,
    age: Long,
): List<LevelNumber> {
    if (options.reducedMotion || level.from == level.to) return listOf(LevelNumber(level.to))
    val start = if (options.subtle) 280 else 620
    val duration = if (options.subtle) 520 else 740
    val progress = unit((age - start) / duration.toDouble()).let { it * it * (3 - 2 * it) }
    return when (progress) {
        0.0 -> {
            listOf(LevelNumber(level.from))
        }

        1.0 -> {
            listOf(LevelNumber(level.to))
        }

        else -> {
            listOf(
                LevelNumber(level.from, -progress, 1 - .12 * progress, 1 - progress),
                LevelNumber(level.to, 1 - progress, .88 + .12 * progress, progress),
            )
        }
    }
}

/** A bounded queue with a single clock. Overflow is summarized, never replayed as a notification storm. */
internal class AchievementTimeline {
    private val pending = ArrayDeque<AchievementEffect>()
    private val seenChallenges = LinkedHashSet<String>()
    private var highestLevel = 0
    private val choiceWatermarks = linkedMapOf<String, Long>()
    private val seenEquips = LinkedHashSet<String>()
    private var current: AchievementEffect? = null
    private var startedAt = 0L
    val pendingCount: Int get() = pending.size
    val isActive: Boolean get() = current != null

    fun enqueue(
        effect: AchievementEffect,
        now: Long,
    ) {
        var challenge = effect.challenge
        if (challenge != null) {
            if (!seenChallenges.add(challenge.id)) challenge = null
            if (seenChallenges.size > 256) seenChallenges.remove(seenChallenges.first())
        }
        val level = effect.level?.takeIf { it.to > highestLevel }
        if (level != null) highestLevel = level.to
        val choice =
            effect.choice?.let { award ->
                val seen = choiceWatermarks[award.generation] ?: 0
                if (award.last <= seen) {
                    null
                } else {
                    award.copy(first = maxOf(award.first, seen + 1)).also {
                        choiceWatermarks[award.generation] = award.last
                        if (choiceWatermarks.size > 16) choiceWatermarks.remove(choiceWatermarks.keys.first())
                    }
                }
            }
        val equipped = effect.equippedEventId?.takeIf { seenEquips.add(it) }
        if (seenEquips.size > 128) seenEquips.remove(seenEquips.first())
        if (challenge == null && level == null && choice == null && equipped == null) return
        val next = effect.copy(challenge = challenge, level = level, choice = choice, choiceCount = choice?.count ?: 0, equippedEventId = equipped)
        if (current == null) {
            current = next
            startedAt = now
        } else if (pending.isNotEmpty() && (pending.size >= 3 || (next.challenge == null && pending.last().challenge == null))) {
            pending.addLast(merge(pending.removeLast(), next))
        } else {
            pending.addLast(next)
        }
    }

    fun frame(now: Long): AchievementFrame? {
        while (true) {
            val effect = current ?: return null
            val age = (now - startedAt).coerceAtLeast(0)
            if (age < effect.duration) return AchievementFrame(effect, age)
            startedAt += effect.duration
            current = pending.removeFirstOrNull()
        }
    }

    fun cancel() {
        current = null
        pending.clear()
    }

    fun reset() {
        cancel()
        seenChallenges.clear()
        highestLevel = 0
        choiceWatermarks.clear()
        seenEquips.clear()
    }

    private fun merge(
        first: AchievementEffect,
        second: AchievementEffect,
    ): AchievementEffect {
        val challenge =
            when {
                first.challenge == null -> {
                    second.challenge
                }

                second.challenge == null -> {
                    first.challenge
                }

                else -> {
                    second.challenge.copy(
                        reward = first.challenge.reward + second.challenge.reward,
                        count = first.challenge.count + second.challenge.count,
                    )
                }
            }
        val level =
            when {
                first.level == null -> second.level
                second.level == null -> first.level
                else -> LevelEffect(minOf(first.level.from, second.level.from), maxOf(first.level.to, second.level.to))
            }
        val choice =
            when {
                first.choice == null -> second.choice
                second.choice == null -> first.choice
                first.choice.generation != second.choice.generation -> second.choice
                else -> first.choice.copy(first = minOf(first.choice.first, second.choice.first), last = maxOf(first.choice.last, second.choice.last))
            }
        return first.copy(challenge = challenge, level = level, crest = second.crest ?: first.crest, choice = choice, choiceCount = first.choiceCount + second.choiceCount, equippedEventId = second.equippedEventId ?: first.equippedEventId)
    }
}

/** Accumulation extends the reading time, while entrance and movement keep their original start. */
internal class XpCluster(
    initialValue: Long,
    val startedAt: Long,
    val options: EffectOptions,
) {
    var total: Long = initialValue
        private set
    var hits: Int = 1
        private set
    var lastAt: Long = startedAt
        private set
    var displayed: Double = initialValue.toDouble()
        private set
    private var sampledAt = startedAt

    fun add(
        value: Long,
        now: Long,
    ) {
        sample(now)
        total += value
        hits++
        lastAt = now
        if (options.reducedMotion) displayed = total.toDouble()
    }

    fun sample(now: Long) {
        val dt = (now - sampledAt).coerceAtLeast(0)
        displayed += (total - displayed) * (1 - exp(-dt / 75.0))
        // Always show the exact earned value before fading, even after a very large reward.
        if (total - displayed < 0.05 || now - lastAt >= minOf(600, options.timing.xp - 450)) displayed = total.toDouble()
        sampledAt = now
    }

    fun alive(now: Long): Boolean = now - lastAt < options.timing.xp

    fun alpha(now: Long): Float = (unit((now - startedAt) / 130.0) * unit((options.timing.xp - (now - lastAt)) / 450.0)).toFloat()
}

internal fun motionTimeMillis(): Long = System.nanoTime() / 1_000_000

internal fun unit(value: Double): Double = value.coerceIn(0.0, 1.0)

internal fun ease(value: Double): Double = 1 - (1 - unit(value)).let { it * it * it }

internal fun frameAlpha(
    age: Long,
    duration: Long,
    reduced: Boolean,
): Float = (unit(age / if (reduced) 130.0 else 220.0) * unit((duration - age) / if (reduced) 180.0 else 650.0)).toFloat()
