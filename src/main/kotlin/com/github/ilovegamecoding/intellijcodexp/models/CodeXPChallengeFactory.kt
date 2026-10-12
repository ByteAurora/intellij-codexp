package com.github.ilovegamecoding.intellijcodexp.models

import com.github.ilovegamecoding.intellijcodexp.CodeXPBundle
import com.github.ilovegamecoding.intellijcodexp.enums.Event

/**
 * CodeXPChallenge class
 *
 * CodeXPChallenge is a class that represents a challenge. Challenges are used to track the progress of the user.
 */
object CodeXPChallengeFactory {
    /**
     * Creates a list of default challenges.
     *
     * @return List of default challenges.
     */
    fun createEventDefaultChallenges(): List<CodeXPChallenge> =
        listOf(
            createChallenge(
                event = Event.TYPING,
                nameKey = "challenge.typing.name",
                descriptionKey = "challenge.typing.description",
                goal = 100,
                rewardXP = 100,
                rewardXPIncrement = 200,
            ),
            createChallenge(
                event = Event.CUT,
                nameKey = "challenge.cut.name",
                descriptionKey = "challenge.cut.description",
                goal = 10,
                rewardXP = 100,
                rewardXPIncrement = 150,
            ),
            createChallenge(
                event = Event.COPY,
                nameKey = "challenge.copy.name",
                descriptionKey = "challenge.copy.description",
                goal = 10,
                rewardXP = 100,
                rewardXPIncrement = 150,
            ),
            createChallenge(
                event = Event.PASTE,
                nameKey = "challenge.paste.name",
                descriptionKey = "challenge.paste.description",
                goal = 10,
                rewardXP = 100,
                rewardXPIncrement = 150,
            ),
            createChallenge(
                event = Event.BACKSPACE,
                nameKey = "challenge.backspace.name",
                descriptionKey = "challenge.backspace.description",
                goal = 50,
                rewardXP = 100,
                rewardXPIncrement = 150,
            ),
            createChallenge(
                event = Event.TAB,
                nameKey = "challenge.tab.name",
                descriptionKey = "challenge.tab.description",
                goal = 50,
                rewardXP = 100,
                rewardXPIncrement = 150,
            ),
            createChallenge(
                event = Event.ENTER,
                nameKey = "challenge.enter.name",
                descriptionKey = "challenge.enter.description",
                goal = 10,
                rewardXP = 100,
                rewardXPIncrement = 200,
            ),
            createChallenge(
                event = Event.SAVE,
                nameKey = "challenge.save.name",
                descriptionKey = "challenge.save.description",
                goal = 10,
                rewardXP = 300,
                rewardXPIncrement = 400,
            ),
            createChallenge(
                event = Event.BUILD,
                nameKey = "challenge.build.name",
                descriptionKey = "challenge.build.description",
                goal = 10,
                rewardXP = 150,
                rewardXPIncrement = 200,
            ),
            createChallenge(
                event = Event.RUN,
                nameKey = "challenge.run.name",
                descriptionKey = "challenge.run.description",
                goal = 10,
                rewardXP = 200,
                rewardXPIncrement = 250,
            ),
            createChallenge(
                event = Event.DEBUG,
                nameKey = "challenge.debug.name",
                descriptionKey = "challenge.debug.description",
                goal = 10,
                rewardXP = 300,
                rewardXPIncrement = 400,
            ),
            createChallenge(
                event = Event.ACTION,
                nameKey = "challenge.action.name",
                descriptionKey = "challenge.action.description",
                goal = 20,
                rewardXP = 100,
                rewardXPIncrement = 120,
            ),
        )

    /**
     * Creates a challenge with the given parameters.
     *
     * @param event Event of challenge.
     * @param nameKey Resource key for the challenge name.
     * @param descriptionKey Resource key for the challenge description.
     * @param goal Goal of challenge.
     * @param rewardXP Reward XP of challenge when completed.
     * @param rewardXPIncrement Increment of reward XP of challenge when completed.
     * @return Challenge with the given parameters.
     */
    private fun createChallenge(
        event: Event,
        nameKey: String,
        descriptionKey: String,
        goal: Long,
        rewardXP: Long,
        rewardXPIncrement: Long,
    ): CodeXPChallenge =
        CodeXPChallenge(
            event = event,
            name = CodeXPBundle.message(nameKey),
            description = CodeXPBundle.message(descriptionKey),
            progress = 0,
            goal = goal,
            goalIncrement = goal,
            rewardXP = rewardXP,
            rewardXPIncrement = rewardXPIncrement,
        )
}
