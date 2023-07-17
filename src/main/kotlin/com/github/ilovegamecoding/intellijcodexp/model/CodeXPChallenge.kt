package com.github.ilovegamecoding.intellijcodexp.model

import com.github.ilovegamecoding.intellijcodexp.enums.Event
import java.util.*

/**
 * CodeXPChallenge class
 *
 * CodeXPChallenge is a class that represents a challenge. Challenges are used to track the progress of the user.
 */
class CodeXPChallenge() {
    /**
     * ID of challenge.
     */
    var id: String = ""

    /**
     * Event of challenge.
     */
    var event: Event = Event.NONE

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
     * Increment of reward XP of challenge when completed.
     */
    var rewardXPIncrement: Long = 0L

    /**
     * Current progress of challenge.
     */
    var progress: Long = 0L

    /**
     * Goal of challenge.
     */
    var goal: Long = 0L

    /**
     * Increment of goal of challenge.
     */
    var goalIncrement: Long = 0L

    /**
     * Constructor for CodeXPChallenge with all parameters.
     */
    constructor(
        event: Event,
        name: String,
        description: String,
        rewardXP: Long,
        rewardXPIncrement: Long,
        progress: Long,
        goal: Long,
        goalIncrement: Long
    ) : this() {
        this.id = UUID.randomUUID().toString()
        this.event = event
        this.name = name
        this.description = description
        this.rewardXP = rewardXP
        this.rewardXPIncrement = rewardXPIncrement
        this.progress = progress
        this.goal = goal
        this.goalIncrement = goalIncrement
    }
}