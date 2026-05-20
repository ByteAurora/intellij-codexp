package com.github.ilovegamecoding.intellijcodexp.presentation.dashboard

import com.github.ilovegamecoding.intellijcodexp.form.CodeXPChallengeForm
import com.github.ilovegamecoding.intellijcodexp.models.CodeXPChallenge
import com.github.ilovegamecoding.intellijcodexp.utils.StringUtil
import javax.swing.BorderFactory

/**
 * Renders challenge data into dashboard challenge form components.
 */
internal object CodeXPChallengeRenderer {
    /**
     * Creates a challenge form for the given challenge.
     */
    fun create(challenge: CodeXPChallenge): CodeXPChallengeForm = update(challenge, CodeXPChallengeForm())

    /**
     * Updates an existing challenge form with the given challenge data.
     */
    fun update(
        challenge: CodeXPChallenge,
        challengeForm: CodeXPChallengeForm,
    ): CodeXPChallengeForm {
        with(challengeForm) {
            challengeID = challenge.id
            pChallenge.border = BorderFactory.createEmptyBorder(16, 32, 0, 32)
            lblChallengeName.text = challenge.name
            lblChallengeReward.text = StringUtil.numberToStringWithCommas(challenge.rewardXP)
            lblChallengeDescription.text =
                challenge.description.replace("[goal]", StringUtil.numberToStringWithCommas(challenge.goal))

            if (challenge.progress >= challenge.goal) {
                lblChallengeProgress.isVisible = false
                pbChallengeProgress.isVisible = false
                lblChallengePercentageIcon.isVisible = false
            } else {
                lblChallengeProgress.isVisible = true
                pbChallengeProgress.isVisible = true
                lblChallengePercentageIcon.isVisible = true
                updateProgress(challenge, this)
            }
        }
        return challengeForm
    }

    /**
     * Updates only the progress fields of an existing challenge form.
     */
    fun updateProgress(
        challenge: CodeXPChallenge,
        challengeForm: CodeXPChallengeForm,
    ) {
        val progressPercentage = ((challenge.progress.toDouble() / challenge.goal) * 100).toInt()
        challengeForm.lblChallengeProgress.text = progressPercentage.toString()
        challengeForm.pbChallengeProgress.value = progressPercentage
    }
}
