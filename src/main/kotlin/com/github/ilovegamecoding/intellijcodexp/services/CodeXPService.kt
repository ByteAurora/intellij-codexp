package com.github.ilovegamecoding.intellijcodexp.services

import com.github.ilovegamecoding.intellijcodexp.model.CodeXPChallenge
import com.intellij.openapi.components.*
import com.intellij.util.xmlb.XmlSerializerUtil

/**
 * CodeXPService class
 *
 * This is the main class for the plugin. It manages the state of the plugin, including challenges, user progress, and more.
 * All data stored in the plugin is contained within this class.
 */
@Service(Service.Level.APP)
@State(
    name = "com.github.ilovegamecoding.intellijcodexp.services.CodeXP",
    storages = [Storage("CodeXP.xml")]
)
class CodeXPService : PersistentStateComponent<CodeXPService> {
    /**
     * All current challenges yet to be completed.
     */
    var challenges = mutableMapOf<Int, CodeXPChallenge>()

    /**
     * All completed challenges.
     */
    var completedChallenges = mutableMapOf<Int, CodeXPChallenge>()

    /**
     * Plugin has been executed before.
     */
    var hasExecuted: Boolean = false

    /**
     * Nickname of the user.
     */
    var nickname: String = ""

    override fun getState(): CodeXPService {
        return this
    }

    override fun loadState(state: CodeXPService) {
        XmlSerializerUtil.copyBean(state, this)
    }

    /**
     * Reset the plugin.
     */
    fun resetPlugin() {
        challenges.clear()
        completedChallenges.clear()
        hasExecuted = false
        nickname = ""
    }

    /**
     * Executes a given lambda function only if the plugin has not been executed before.
     *
     * @param challenges Challenges to add to the plugin.
     */
    fun initializePlugin(challenges: List<CodeXPChallenge>) {
        if (!hasExecuted) {
            // TODO: Add logic to execute only once

            hasExecuted = true
        }

        challenges.forEach(this::addChallenge)
    }

    /**
     * Add a challenge to the plugin.
     *
     * @param challenge The challenge to add.
     */
    fun addChallenge(challenge: CodeXPChallenge) {
        if (challenges.containsKey(challenge.type) || completedChallenges.containsKey(challenge.type)) return
        challenges[challenge.type] = challenge
    }

    /**
     * Increase the value of a challenge.
     *
     * @param type The type of the challenge.
     * @param amount The amount to increase the challenge value by.
     */
    fun increaseChallengeValue(type: CodeXPChallenge.Type, amount: Int) {
        val challenge = challenges[type.ordinal] ?: return

        challenge.value += amount
        if (challenge.value >= challenge.goal) { // If challenge is completed
            challenges.remove(challenge.type)
            completedChallenges[challenge.type] = challenge
        }
    }
}