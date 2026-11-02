package com.github.ilovegamecoding.intellijcodexp.presentation.overlay

import com.github.ilovegamecoding.intellijcodexp.domain.CodeXPProgressResult
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
                reducedMotion -> EffectTiming(1800, 2100, 2000, 1250)
                subtle -> EffectTiming(2100, 2350, 2200, 1100)
                else -> EffectTiming(2850, 3100, 3800, 1450)
            }

    companion object {
        fun from(config: CodeXPConfiguration): EffectOptions = EffectOptions(EffectStyle.fromStoredValue(config.effectStyle), config.reducedMotion)
    }
}

internal data class EffectTiming(
    val challenge: Long,
    val levelAt: Long,
    val level: Long,
    val xp: Long,
)

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
) {
    val duration: Long
        get() =
            if (level != null) {
                (if (challenge != null) options.timing.levelAt else 0) + options.timing.level
            } else if (challenge != null) {
                options.timing.challenge
            } else {
                0
            }

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
            return if (challenge == null && level == null) null else AchievementEffect(challenge, level, EffectOptions.from(config))
        }
    }
}

internal data class AchievementFrame(
    val effect: AchievementEffect,
    val age: Long,
) {
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
        if (challenge == null && level == null) return
        val next = effect.copy(challenge = challenge, level = level)
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
        return first.copy(challenge = challenge, level = level)
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
