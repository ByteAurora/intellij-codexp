package com.github.ilovegamecoding.intellijcodexp.model

/**
 * CodeXPChallenge class
 * CodeXPChallenge is a class that represents a challenge. Challenges are used to track the progress of the user.
 *
 * @constructor Create empty CodeXPChallenge.
 */
class CodeXPChallenge() {
    /**
     * Type of challenge.
     */
    var type: Int = -1

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
     * Current value of challenge.
     */
    var value: Long = 0L

    /**
     * Goal of challenge.
     */
    var goal: Long = 0L

    /**
     * Constructor for CodeXPChallenge with all parameters.
     */
    constructor(
        type: Int,
        name: String,
        description: String,
        rewardXP: Long,
        value: Long,
        goal: Long
    ) : this() {
        this.type = type
        this.name = name
        this.description = description
        this.rewardXP = rewardXP
        this.value = value
        this.goal = goal
    }

    /**
     * Type of challenge.
     *
     * !! Do not ever change the order of the enum values. If you want to add a new challenge type, add it to the end of the enum !!
     */
    enum class Type {
        TOTAL_XP,
        ACTION_COUNT,
        BUILD_COUNT,
        DEBUG_COUNT,
        RUN_COUNT,
        COMMIT_COUNT,
        PUSH_COUNT,
        MERGE_COUNT,
        PULL_COUNT,
        TYPING_COUNT,
    }
}