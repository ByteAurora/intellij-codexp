package com.github.ilovegamecoding.intellijcodexp

import com.github.ilovegamecoding.intellijcodexp.domain.CodeXPProgressEngine
import com.github.ilovegamecoding.intellijcodexp.enums.CodeXPNotificationType
import com.github.ilovegamecoding.intellijcodexp.enums.EffectStyle
import com.github.ilovegamecoding.intellijcodexp.enums.Event
import com.github.ilovegamecoding.intellijcodexp.enums.PositionToDisplayGainedXP
import com.github.ilovegamecoding.intellijcodexp.models.CodeXPConfiguration
import com.github.ilovegamecoding.intellijcodexp.models.CodeXPState
import com.github.ilovegamecoding.intellijcodexp.presentation.overlay.AchievementEffect
import com.github.ilovegamecoding.intellijcodexp.presentation.overlay.AchievementTimeline
import com.github.ilovegamecoding.intellijcodexp.presentation.overlay.ChallengeEffect
import com.github.ilovegamecoding.intellijcodexp.presentation.overlay.EffectOptions
import com.github.ilovegamecoding.intellijcodexp.presentation.overlay.LevelEffect
import com.github.ilovegamecoding.intellijcodexp.presentation.overlay.XpCluster
import com.github.ilovegamecoding.intellijcodexp.presentation.overlay.achievementBounds
import com.github.ilovegamecoding.intellijcodexp.presentation.overlay.layoutXpText
import com.github.ilovegamecoding.intellijcodexp.presentation.overlay.xpBounds
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.awt.Font
import java.awt.Point
import java.awt.Rectangle

class AchievementMotionTest {
    private val options = EffectOptions(EffectStyle.CELEBRATORY, false)

    private fun effect(
        id: String = "challenge",
        to: Int = 2,
    ) = AchievementEffect(ChallengeEffect(id, "Build", "Build once", 100), LevelEffect(1, to), options)

    @Test
    fun `burst accumulates Long XP without restarting entrance`() {
        val cluster = XpCluster(2, 100, options)
        repeat(7) { cluster.add(2, 260L + it * 160) }
        assertEquals(100, cluster.startedAt)
        assertEquals(16, cluster.total)
        assertEquals(8, cluster.hits)
        cluster.add(Int.MAX_VALUE.toLong(), 1400)
        cluster.sample(2300)
        assertEquals(Int.MAX_VALUE.toLong() + 16, cluster.total)
        assertEquals(cluster.total.toDouble(), cluster.displayed, .05)
        assertTrue(cluster.alive(2849))
        assertFalse(cluster.alive(2850))
        assertEquals(0f, cluster.alpha(2850), 0f)
    }

    @Test
    fun `elapsed time interpolation is independent of timer frequency`() {
        val fine = XpCluster(2, 0, options)
        val coarse = XpCluster(2, 0, options)
        fine.add(30, 10)
        coarse.add(30, 10)
        (20..210 step 10).forEach { fine.sample(it.toLong()) }
        coarse.sample(210)
        assertEquals(fine.displayed, coarse.displayed, .0001)
    }

    @Test
    fun `challenge reward level sequence uses approved timing and stops after final frame`() {
        val timeline = AchievementTimeline()
        timeline.enqueue(effect(), 100)
        assertNotNull(timeline.frame(100)?.challengeAge)
        assertNotNull(timeline.frame(2949)?.challengeAge)
        assertNull(timeline.frame(2950)?.challengeAge)
        assertNull(timeline.frame(3199)?.levelAge)
        assertEquals(0L, timeline.frame(3200)?.levelAge)
        assertNull(timeline.frame(7000))
        assertFalse(timeline.isActive)
        assertEquals(2200, EffectOptions(EffectStyle.SUBTLE, false).timing.level)
        assertEquals(3800, options.timing.level)
    }

    @Test
    fun `queue bounds and coalescing preserve reward totals and final level`() {
        val timeline = AchievementTimeline()
        repeat(100) { timeline.enqueue(effect("c$it", it + 2), 0) }
        assertEquals(3, timeline.pendingCount)
        var now = 0L
        var reward = 0L
        var challenges = 0
        var highest = 0
        while (timeline.isActive) {
            val frame = timeline.frame(now) ?: break
            reward += frame.effect.challenge!!.reward
            challenges += frame.effect.challenge!!.count
            highest = maxOf(highest, frame.effect.level!!.to)
            now += frame.effect.duration
            timeline.frame(now)
        }
        assertEquals(10000, reward)
        assertEquals(100, challenges)
        assertEquals(101, highest)
    }

    @Test
    fun `duplicates cancellation and long EDT pause cannot replay a storm`() {
        val timeline = AchievementTimeline()
        timeline.enqueue(effect(), 0)
        timeline.enqueue(effect(), 1)
        assertEquals(0, timeline.pendingCount)
        timeline.enqueue(effect("next", 3), 2)
        assertNull(timeline.frame(50000))
        timeline.enqueue(effect("third", 4), 50001)
        timeline.cancel()
        assertNull(timeline.frame(50002))
        timeline.enqueue(effect("third", 4), 50003)
        assertFalse(timeline.isActive)
    }

    @Test
    fun `notification combinations do not change recorded rewards or create extra levels`() {
        for (challengeEnabled in listOf(false, true)) {
            for (levelEnabled in listOf(false, true)) {
                val state = CodeXPState(xp = 299)
                val engine = CodeXPProgressEngine()
                engine.initialize(state)
                val challenge = state.challenges.getValue(Event.CUT)
                challenge.progress = challenge.goal - 1
                challenge.rewardXP = 1000
                val result = engine.recordEvent(state, Event.CUT)
                val config = CodeXPConfiguration(showCompleteChallengeNotification = challengeEnabled, showLevelUpNotification = levelEnabled)
                val presentation = AchievementEffect.from(result, config)
                assertEquals(presentation, AchievementEffect.from(result, config.copy(showGainedXP = false)))
                assertEquals(challengeEnabled, presentation?.challenge != null)
                assertEquals(levelEnabled, presentation?.level != null)
                if (levelEnabled) {
                    assertEquals(1, presentation!!.level!!.from)
                    assertEquals(
                        result.xpChanges
                            .last()
                            .currentLevelInfo.level,
                        presentation.level!!.to,
                    )
                }
                assertEquals(1300, state.xp)
                assertEquals(1, state.completedChallenges.size)
                assertEquals(1, state.getEventCount(Event.CUT))
                config.notificationType = CodeXPNotificationType.INTELLIJ.storedValue
                assertNull(AchievementEffect.from(result, config))
                assertEquals(1300, state.xp)
            }
        }
    }

    @Test
    fun `viewport bounds remain inside small scrolled and HiDPI editors`() {
        for (scale in listOf(1.0, 1.5, 2.0)) {
            for (width in listOf(160, 320, 768, 1400)) {
                val viewport = Rectangle(300, 900, width, 270)
                for (subtle in listOf(false, true)) assertTrue(viewport.contains(achievementBounds(viewport, subtle, scale)))
                val layout = layoutXpText("+9,223,372,036,854,775,807 XP", Font(Font.MONOSPACED, Font.PLAIN, (14 * scale).toInt()), width, (2 * scale).toInt())
                for (position in PositionToDisplayGainedXP.entries) {
                    assertTrue(viewport.contains(xpBounds(Point(301, 901), position, 24, layout, viewport, (8 * scale).toInt())))
                    assertTrue(viewport.contains(xpBounds(Point(300 + width, 1170), position, 24, layout, viewport, (8 * scale).toInt())))
                }
            }
        }
    }
}
