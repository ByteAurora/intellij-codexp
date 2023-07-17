package com.github.ilovegamecoding.intellijcodexp.models

import com.github.ilovegamecoding.intellijcodexp.enums.Event

object CodeXPChallengeFactory {
    fun createEventDefaultChallenges(): List<CodeXPChallenge> {
        return listOf(
            createChallenge(
                event = Event.TYPING,
                name = "Typing Challenge", description = "Typing [goal] times.",
                goal = 100, rewardXP = 100, rewardXPIncrement = 200
            ),
            createChallenge(
                event = Event.PASTE,
                name = "Paste Challenge", description = "Paste [goal] times.",
                goal = 10, rewardXP = 100, rewardXPIncrement = 150
            ),
            createChallenge(
                event = Event.BACKSPACE,
                name = "Backspace Challenge", description = "Press the backspace key [goal] times.",
                goal = 50, rewardXP = 100, rewardXPIncrement = 150
            ),
            createChallenge(
                event = Event.TAB,
                name = "Tab Challenge",
                description = "Press the tab key [goal] times.",
                goal = 50, rewardXP = 100, rewardXPIncrement = 150
            ),
            createChallenge(
                event = Event.ENTER,
                name = "Enter Challenge", description = "Press the enter key [goal] times.",
                goal = 10, rewardXP = 100, rewardXPIncrement = 200
            ),
            createChallenge(
                event = Event.SAVE,
                name = "Save Challenge", description = "Save [goal] times.",
                goal = 10, rewardXP = 300, rewardXPIncrement = 400
            ),
            createChallenge(
                event = Event.BUILD,
                name = "Build Challenge", description = "Build [goal] times.",
                goal = 10, rewardXP = 150, rewardXPIncrement = 200
            ),
            createChallenge(
                event = Event.RUN,
                name = "Run Challenge", description = "Run [goal] times.",
                goal = 10, rewardXP = 200, rewardXPIncrement = 250
            ),
            createChallenge(
                event = Event.DEBUG,
                name = "Debug Challenge", description = "Debug [goal] times.",
                goal = 10, rewardXP = 300, rewardXPIncrement = 400
            ),
            createChallenge(
                event = Event.ACTION,
                name = "Action Challenge", description = "Perform [goal] actions.",
                goal = 20, rewardXP = 100, rewardXPIncrement = 120
            )
        )
    }

    private fun createChallenge(
        event: Event,
        name: String,
        description: String,
        goal: Long,
        rewardXP: Long,
        rewardXPIncrement: Long
    ): CodeXPChallenge {
        return CodeXPChallenge(
            event = event,
            name = name,
            description = description,
            progress = 0,
            goal = goal,
            goalIncrement = goal,
            rewardXP = rewardXP,
            rewardXPIncrement = rewardXPIncrement
        )
    }
}