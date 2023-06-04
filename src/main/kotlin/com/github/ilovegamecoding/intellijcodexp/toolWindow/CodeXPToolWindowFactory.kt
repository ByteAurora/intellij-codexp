package com.github.ilovegamecoding.intellijcodexp.toolWindow

import com.github.ilovegamecoding.intellijcodexp.form.CodeXPChallengeForm
import com.github.ilovegamecoding.intellijcodexp.form.CodeXPDashboard
import com.github.ilovegamecoding.intellijcodexp.listeners.CodeXPListener
import com.github.ilovegamecoding.intellijcodexp.manager.CodeXPNotificationManager
import com.github.ilovegamecoding.intellijcodexp.model.CodeXPChallenge
import com.github.ilovegamecoding.intellijcodexp.services.CodeXPService
import com.github.ilovegamecoding.intellijcodexp.util.StringUtil
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.content.ContentFactory
import java.awt.BorderLayout
import java.awt.Font
import java.awt.GridBagConstraints
import java.awt.GridLayout
import javax.swing.BorderFactory
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.event.DocumentEvent
import javax.swing.event.DocumentListener


class CodeXPToolWindowFactory : ToolWindowFactory {
    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        // Get the CodeXP service
        val codeXPService = ApplicationManager.getApplication().getService(CodeXPService::class.java)

        // Create the dashboard
        val codeXPDashboard = CodeXPDashboard()

        initializeUI(codeXPService, codeXPDashboard)

        // Add the dashboard to the tool window
        val contentFactory = ContentFactory.SERVICE.getInstance()
        val scrollPane = JBScrollPane(codeXPDashboard.pMain)
        val rootPanel = JPanel(BorderLayout())
        rootPanel.add(BorderLayout.CENTER, scrollPane)
        val content = contentFactory.createContent(rootPanel, null, false)
        toolWindow.contentManager.addContent(content)
    }

    override fun shouldBeAvailable(project: Project) = true

    /**
     * Initializes the UI of the dashboard.
     *
     * @param codeXPService CodeXP service.
     * @param codeXPDashboard CodeXP dashboard.
     */
    private fun initializeUI(codeXPService: CodeXPService, codeXPDashboard: CodeXPDashboard) {
        // Listen to events from the CodeXP service
        val connection = ApplicationManager.getApplication().messageBus.connect()

        // Set nickname and listen changes from the text field
        codeXPDashboard.tfNickname.text = codeXPService.state.nickname
        codeXPDashboard.tfNickname.document.addDocumentListener(object : DocumentListener {
            override fun insertUpdate(e: DocumentEvent) {
                updateNickname(codeXPService, codeXPDashboard)
            }

            override fun removeUpdate(e: DocumentEvent) {
                updateNickname(codeXPService, codeXPDashboard)
            }

            override fun changedUpdate(e: DocumentEvent) {
                updateNickname(codeXPService, codeXPDashboard)
            }
        })

        // GridBagConstraints for the event statistics and challenges
        val gridBagConstraints = GridBagConstraints()
        gridBagConstraints.weightx = 1.0
        gridBagConstraints.fill = GridBagConstraints.HORIZONTAL

        val eventStaticForms = HashMap<CodeXPService.Event, JPanel>()
        val challengeForms = HashMap<CodeXPService.Event, CodeXPChallengeForm>()

        CodeXPService.Event.values().forEachIndexed { index, event -> // Add ui for each event
            if (event != CodeXPService.Event.NONE) { // Ignore the NONE event type
                // Initialize event statistics
                gridBagConstraints.gridy = index

                val pEvent = JPanel(GridLayout(1, 3))
                pEvent.border = BorderFactory.createEmptyBorder(0, 16, 8, 0)
                val lblEventName = JLabel(event.name)
                lblEventName.font = Font("Noto Sans SemiBold", Font.PLAIN, 14)
                lblEventName.border = BorderFactory.createEmptyBorder(0, 32, 0, 0)
                val lblEventReward = JLabel("+" + StringUtil.numberToStringWithCommas(event.xpValue) + " XP")
                lblEventReward.font = Font("Noto Sans SemiBold", Font.PLAIN, 14)
                lblEventReward.border = BorderFactory.createEmptyBorder(0, 32, 0, 0)
                val lblEventCount =
                    JLabel(StringUtil.numberToStringWithCommas(codeXPService.state.getEventCount(event)))
                lblEventCount.font = Font("Noto Sans Medium", Font.PLAIN, 14)
                lblEventCount.horizontalAlignment = JLabel.CENTER

                pEvent.add(lblEventName)
                pEvent.add(lblEventReward)
                pEvent.add(lblEventCount)
                codeXPDashboard.pEventStatistics.add(pEvent, gridBagConstraints)

                eventStaticForms[event] = pEvent

                // Initialize challenges
                val challengeForm = createChallengeForm(codeXPService.state.challenges[event]!!)
                codeXPDashboard.pChallenges.add(challengeForm.pChallenge, gridBagConstraints)
                challengeForms[event] = challengeForm
            }
        }

        // Initialize completed challenges
        codeXPService.state.completedChallenges.forEach { completedChallenge ->
            gridBagConstraints.gridy = codeXPDashboard.pCompletedChallenges.componentCount

            val challengeForm = createChallengeForm(completedChallenge)
            codeXPDashboard.pCompletedChallenges.add(challengeForm.pChallenge, gridBagConstraints)
        }
        codeXPDashboard.lblCompletedChallengesCount.text =
            StringUtil.numberToStringWithCommas(codeXPService.state.completedChallenges.size.toLong())

        // Update the dashboard when events occur
        connection.subscribe(CodeXPListener.CODEXP_EVENT, object : CodeXPListener {
            override fun eventOccurred(event: CodeXPService.Event) {
                (eventStaticForms[event]!!.getComponent(2) as JLabel).text =
                    StringUtil.numberToStringWithCommas(codeXPService.state.getEventCount(event))

                val currentChallenge = codeXPService.state.challenges[event]!!
                val beforeChallengeForm = challengeForms[event]!!

                if (currentChallenge.id == beforeChallengeForm.challengeID) { // Challenge is not completed
                    updateChallengeProgress(currentChallenge, beforeChallengeForm)
                } else {  // Before challenge is completed
                    gridBagConstraints.gridy = codeXPDashboard.pCompletedChallenges.componentCount
                    codeXPService.state.completedChallenges.find { it.id == beforeChallengeForm.challengeID }
                        ?.let { createChallengeForm(it).pChallenge }?.let {
                            codeXPDashboard.pCompletedChallenges.add(
                                it,
                                gridBagConstraints
                            )
                        }

                    setChallengeToForm(currentChallenge, challengeForms[event]!!)
                    codeXPDashboard.lblCompletedChallengesCount.text =
                        StringUtil.numberToStringWithCommas(codeXPService.state.completedChallenges.size.toLong())
                }

                updateXPInfo(codeXPService, codeXPDashboard)
            }
        })

        updateXPInfo(codeXPService, codeXPDashboard)
    }

    /**
     * Updates user nickname on the dashboard.
     *
     * @param codeXPService CodeXP service.
     * @param codeXPDashboard CodeXP dashboard.
     */
    private fun updateNickname(codeXPService: CodeXPService, codeXPDashboard: CodeXPDashboard) {
        codeXPService.state.nickname = codeXPDashboard.tfNickname.text
    }

    /**
     * Updates the XP info on the dashboard.
     *
     * @param codeXPService CodeXP service.
     * @param codeXPDashboard CodeXP dashboard.
     */
    private fun updateXPInfo(codeXPService: CodeXPService, codeXPDashboard: CodeXPDashboard) {
        val totalXP = codeXPService.state.xp
        codeXPDashboard.lblTotalXP.text = StringUtil.numberToStringWithCommas(totalXP)
        val (currentLevel, xpIntoCurrentLevel, progressToNextLevel, xpToNextLevel) = calculateLevelAndProgress(
            totalXP
        )


        val beforeLevel = codeXPDashboard.lblCurrentLevel.text.toInt()
        if (beforeLevel != currentLevel && beforeLevel != 0) {
            CodeXPNotificationManager.notifyLevelUp(codeXPService.state.nickname, currentLevel, xpToNextLevel)
        }

        codeXPDashboard.lblCurrentLevel.text = StringUtil.numberToStringWithCommas(currentLevel.toLong())
        codeXPDashboard.lblNextLevel.text = StringUtil.numberToStringWithCommas((currentLevel + 1).toLong())
        codeXPDashboard.lblCurrentLevelXP.text = StringUtil.numberToStringWithCommas(xpIntoCurrentLevel)
        codeXPDashboard.pbCurrentLevelProgress.value = progressToNextLevel
        codeXPDashboard.pbCurrentLevelProgress.string = "$progressToNextLevel %"
        codeXPDashboard.lblLevel.text = StringUtil.numberToStringWithCommas(currentLevel.toLong())
    }

    /**
     * Updates challenge progress on the challenge item.
     *
     * @param challenge Challenge.
     * @param challengeForm Challenge form.
     */
    private fun updateChallengeProgress(challenge: CodeXPChallenge, challengeForm: CodeXPChallengeForm) {
        val progressPercentage = ((challenge.progress.toDouble() / challenge.goal) * 100).toInt()
        challengeForm.lblChallengeProgress.text = progressPercentage.toString()
        challengeForm.pbChallengeProgress.value = progressPercentage
    }

    /**
     * Creates a challenge form.
     *
     * @param challenge Challenge to create the form for.
     * @return Challenge form.
     */
    private fun createChallengeForm(challenge: CodeXPChallenge): CodeXPChallengeForm {
        val challengeForm = CodeXPChallengeForm()
        challengeForm.challengeID = challenge.id
        challengeForm.pChallenge.border = BorderFactory.createEmptyBorder(16, 32, 0, 32)
        challengeForm.lblChallengeName.text = challenge.name
        challengeForm.lblChallengeReward.text = StringUtil.numberToStringWithCommas(challenge.rewardXP)
        challengeForm.lblChallengeDescription.text =
            challenge.description.replace("[goal]", StringUtil.numberToStringWithCommas(challenge.goal))

        if (challenge.progress >= challenge.goal) {
            challengeForm.lblChallengeProgress.isVisible = false
            challengeForm.pbChallengeProgress.isVisible = false
            challengeForm.lblChallengePercentageIcon.isVisible = false
        } else {
            updateChallengeProgress(challenge, challengeForm)
        }
        return challengeForm
    }

    /**
     * Sets challenge to the form.
     *
     * @param challenge Challenge to set.
     * @param challengeForm Challenge form.
     */
    private fun setChallengeToForm(challenge: CodeXPChallenge, challengeForm: CodeXPChallengeForm) {
        challengeForm.challengeID = challenge.id
        challengeForm.pChallenge.border = BorderFactory.createEmptyBorder(16, 32, 0, 32)
        challengeForm.lblChallengeName.text = challenge.name
        challengeForm.lblChallengeReward.text = StringUtil.numberToStringWithCommas(challenge.rewardXP)
        challengeForm.lblChallengeDescription.text =
            challenge.description.replace("[goal]", StringUtil.numberToStringWithCommas(challenge.goal))

        if (challenge.progress >= challenge.goal) {
            challengeForm.lblChallengeProgress.isVisible = false
            challengeForm.pbChallengeProgress.isVisible = false
            challengeForm.lblChallengePercentageIcon.isVisible = false
        } else {
            updateChallengeProgress(challenge, challengeForm)
        }
    }

    /**
     * Level info data class.
     */
    data class LevelInfo(
        /**
         * Current level.
         */
        val currentLevel: Int,

        /**
         * XP into the current level.
         */
        val xpIntoCurrentLevel: Long,

        /**
         * Progress to the next level.
         */
        val progressToNextLevel: Int,

        /**
         * XP needed to reach the next level.
         */
        val xpToNextLevel: Long
    )

    /**
     * Calculates the level and progress to the next level.
     *
     * @param totalXP Total XP.
     * @return Level info data class.
     */
    private fun calculateLevelAndProgress(totalXP: Long): LevelInfo {
        var level = 1
        var currentLevelXP = 300.0
        var xp = 300.0

        while (totalXP >= xp) {
            level++
            currentLevelXP *= 1.05
            xp += currentLevelXP
        }

        val xpIntoCurrentLevel = totalXP - (xp - currentLevelXP)
        val progress = (xpIntoCurrentLevel / currentLevelXP) * 100

        return LevelInfo(level, xpIntoCurrentLevel.toLong(), progress.toInt(), currentLevelXP.toLong())
    }
}

