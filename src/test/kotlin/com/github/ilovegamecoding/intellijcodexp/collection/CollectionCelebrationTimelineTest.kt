package com.github.ilovegamecoding.intellijcodexp.collection

import com.github.ilovegamecoding.intellijcodexp.domain.collection.ChoiceAward
import com.github.ilovegamecoding.intellijcodexp.enums.EffectStyle
import com.github.ilovegamecoding.intellijcodexp.presentation.overlay.AchievementEffect
import com.github.ilovegamecoding.intellijcodexp.presentation.overlay.AchievementTimeline
import com.github.ilovegamecoding.intellijcodexp.presentation.overlay.ChallengeEffect
import com.github.ilovegamecoding.intellijcodexp.presentation.overlay.EffectOptions
import com.github.ilovegamecoding.intellijcodexp.presentation.overlay.LevelEffect
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CollectionCelebrationTimelineTest {
    private val options = EffectOptions(EffectStyle.CELEBRATORY, false)

    private fun choice(
        first: Long,
        last: Long = first,
        generation: String = "generation",
    ) = AchievementEffect(null, null, options, choice = ChoiceAward(generation, first, last))

    @Test fun `challenge then level then choice then equip has one finite duration`() {
        for (style in EffectStyle.entries) {
            for (reduced in listOf(false, true)) {
                val effect = AchievementEffect(ChallengeEffect("challenge", "Build", "Build once", 100), LevelEffect(149, 150), EffectOptions(style, reduced), choice = ChoiceAward("generation", 1, 20), equippedEventId = "equip")
                val timeline = AchievementTimeline()
                timeline.enqueue(effect, 100)
                assertEquals(0L, timeline.frame(100)!!.challengeAge)
                assertNull(timeline.frame(100 + effect.options.timing.challenge)!!.challengeAge)
                assertEquals(0L, timeline.frame(100 + effect.options.timing.levelAt)!!.levelAge)
                val award = timeline.frame(100 + effect.choiceAt)!!
                assertNull(award.levelAge)
                assertEquals(0L, award.choiceAge)
                assertEquals(20L, award.effect.choiceCount)
                assertEquals(0L, timeline.frame(100 + effect.equipAt)!!.equipAge)
                assertNull(timeline.frame(100 + effect.duration))
            }
        }
    }

    @Test fun `duplicate and overlapping awards never replay spent credits`() {
        val timeline = AchievementTimeline()
        timeline.enqueue(choice(1, 3), 0)
        timeline.enqueue(choice(1, 3), 0)
        timeline.enqueue(choice(2, 5), 0)
        assertEquals(1, timeline.pendingCount)
        val next = timeline.frame(choice(1).duration)!!
        assertEquals(4L, next.effect.choice!!.first)
        assertEquals(2L, next.effect.choiceCount)
        timeline.cancel()
        timeline.enqueue(choice(1, 5), 9999)
        assertFalse(timeline.isActive)
        timeline.enqueue(choice(1, 2, "new-generation"), 10000)
        assertEquals(2L, timeline.frame(10000)!!.effect.choiceCount)
    }

    @Test fun `bounded overflow counts only accepted awards even with gaps`() {
        val timeline = AchievementTimeline()
        timeline.enqueue(choice(1), 0)
        for (number in 3L..103L step 2) timeline.enqueue(choice(number), 0)
        assertTrue(timeline.pendingCount <= 3)
        var now = 0L
        var count = 0L
        while (timeline.isActive) {
            val frame = timeline.frame(now) ?: break
            count += frame.effect.choiceCount
            now += frame.effect.duration
            timeline.frame(now)
        }
        assertEquals(52L, count)
    }

    @Test fun `equip dedupe and reset keep lifecycle separate from cancellation`() {
        val timeline = AchievementTimeline()
        val equip = AchievementEffect(null, null, options, equippedEventId = "equipped")
        timeline.enqueue(equip, 0)
        timeline.enqueue(equip, 1)
        assertEquals(0, timeline.pendingCount)
        timeline.cancel()
        timeline.enqueue(equip, 2)
        assertFalse(timeline.isActive)
        timeline.reset()
        timeline.enqueue(equip, 3)
        assertTrue(timeline.isActive)
        assertNull(timeline.frame(100000))
        timeline.reset()
        timeline.enqueue(choice(1), 100001)
        assertEquals(1L, timeline.frame(100001)!!.effect.choiceCount)
    }
}
