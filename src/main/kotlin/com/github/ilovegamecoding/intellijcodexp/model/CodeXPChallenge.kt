package com.github.ilovegamecoding.intellijcodexp.model

import com.github.ilovegamecoding.intellijcodexp.services.CodeXPService

/**
 * CodeXPChallenge class
 *
 * CodeXPChallenge is a class that represents a challenge. Challenges are used to track the progress of the user.
 */
class CodeXPChallenge() {
    /**
     * Event of challenge.
     */
    var event: CodeXPService.Event = CodeXPService.Event.NONE

    /**
     * Name of challenge.
     */
    var name: String = ""

    /**
     * Description of challenge.
     */
    var description: String = ""

    /**
     * Reward XP of challenge when completed.
     */
    var rewardXP: Long = 0L

    /**
     * Current progress of challenge.
     */
    var progress: Long = 0L

    /**
     * Goal of challenge.
     */
    var goal: Long = 0L

    /**
     * Constructor for CodeXPChallenge with all parameters.
     */
    constructor(
        event: CodeXPService.Event,
        name: String,
        description: String,
        rewardXP: Long,
        progress: Long,
        goal: Long
    ) : this() {
        this.event = event
        this.name = name
        this.description = description
        this.rewardXP = rewardXP
        this.progress = progress
        this.goal = goal
    }
}