package com.github.ilovegamecoding.intellijcodexp.toolWindow

import com.github.ilovegamecoding.intellijcodexp.enums.Event
import com.github.ilovegamecoding.intellijcodexp.form.CodeXPChallengeForm
import com.github.ilovegamecoding.intellijcodexp.form.CodeXPDashboardForm
import com.github.ilovegamecoding.intellijcodexp.listeners.CodeXPEventListener
import com.github.ilovegamecoding.intellijcodexp.listeners.CodeXPListener
import com.github.ilovegamecoding.intellijcodexp.models.CodeXPChallenge
import com.github.ilovegamecoding.intellijcodexp.models.CodeXPLevel
import com.github.ilovegamecoding.intellijcodexp.services.CodeXPService
import com.github.ilovegamecoding.intellijcodexp.utils.StringUtil
import com.intellij.openapi.actionSystem.DataContext
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
import java.awt.event.ItemEvent
import javax.swing.BorderFactory
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.event.DocumentEvent
import javax.swing.event.DocumentListener


/**
 * CodeXPToolWindowFactory class
 *
 * This class creates the tool window for the CodeXP plugin.
 */
class CodeXPToolWindowFactory : ToolWindowFactory {
    /**
     * CodeXP service.
     */
    private lateinit var codeXPService: CodeXPService

    /**
     * CodeXP dashboard form.
     */
    private lateinit var codeXPDashboardForm: CodeXPDashboardForm

    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        // Get the CodeXP service
        codeXPService = ApplicationManager.getApplication().getService(CodeXPService::class.java)

        // Create the dashboard
        codeXPDashboardForm = CodeXPDashboardForm()

        initializeUI()

        // Add the dashboard to the tool window
        val contentFactory = ContentFactory.getInstance()
        val scrollPane = JBScrollPane(codeXPDashboardForm.pMain)
        val rootPanel = JPanel(BorderLayout())
        rootPanel.add(BorderLayout.CENTER, scrollPane)
        val content = contentFactory.createContent(rootPanel, null, false)
        toolWindow.contentManager.addContent(content)
    }

    override fun shouldBeAvailable(project: Project) = true

    /**
     * Initializes the UI of the dashboard.
     */
    private fun initializeUI() {
        val eventStaticForms = HashMap<Event, JPanel>()
        val challengeForms = HashMap<Event, CodeXPChallengeForm>()

        initializeNickname()
        initializeEventStatisticsAndChallenges(eventStaticForms, challengeForms)
        initializeCompletedChallenges()
        initializeConnection(eventStaticForms, challengeForms)

        updateXPInfo(CodeXPLevel.createLevelInfo(codeXPService.state.xp))
    }

    /**
     * Initialize nickname.
     */
    private fun initializeNickname() {
        // Set nickname and listen changes from the text field
        codeXPDashboardForm.tfNickname.text = codeXPService.state.nickname
        codeXPDashboardForm.tfNickname.document.addDocumentListener(object : DocumentListener {
            override fun insertUpdate(e: DocumentEvent) {
                updateNickname()
            }

            override fun removeUpdate(e: DocumentEvent) {
                updateNickname()
            }

            override fun changedUpdate(e: DocumentEvent) {
                updateNickname()
            }
        })
    }

    /**
     * Initialize event statistics and challenges.
     */
    private fun initializeEventStatisticsAndChallenges(
        eventStaticForms: HashMap<Event, JPanel>,
        challengeForms: HashMap<Event, CodeXPChallengeForm>
    ) {
        val gridBagConstraints = GridBagConstraints()
        gridBagConstraints.weightx = 1.0
        gridBagConstraints.fill = GridBagConstraints.HORIZONTAL

        Event.values().forEachIndexed { index, event -> // Add ui for each event
            if (event != Event.NONE) { // Ignore the NONE event type
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
                codeXPDashboardForm.pEventStatistics.add(pEvent, gridBagConstraints)

                eventStaticForms[event] = pEvent

                // Initialize challenges
                codeXPService.state.challenges[event]?.let {
                    val challengeForm = createOrUpdateChallengeForm(it)
                    codeXPDashboardForm.pChallenges.add(challengeForm.pChallenge, gridBagConstraints)
                    challengeForms[event] = challengeForm
                }
            }
        }
    }

    /**
     * Initialize completed challenges.
     */
    private fun initializeCompletedChallenges() {
        val gridBagConstraints = GridBagConstraints()
        gridBagConstraints.weightx = 1.0
        gridBagConstraints.fill = GridBagConstraints.HORIZONTAL

        codeXPDashboardForm.lblCompletedChallengesCount.text =
            StringUtil.numberToStringWithCommas(codeXPService.state.completedChallenges.size.toLong())

        if (codeXPService.state.showCompletedChallenges) {
            updateCompletedChallenges()
        }
        codeXPDashboardForm.cbShowCompletedChallenges.isSelected = codeXPService.state.showCompletedChallenges
        codeXPDashboardForm.cbShowCompletedChallenges.addItemListener { e ->
            if (e.stateChange == ItemEvent.SELECTED) {
                codeXPService.state.showCompletedChallenges = true
                updateCompletedChallenges()
            } else {
                codeXPService.state.showCompletedChallenges = false
                codeXPDashboardForm.pCompletedChallenges.removeAll()
            }
            codeXPDashboardForm.pCompletedChallenges.revalidate()
            codeXPDashboardForm.pCompletedChallenges.repaint()
        }
    }

    /**
     * Initialize connection.
     *
     * @param eventStaticForms Event static forms.
     * @param challengeForms Challenge forms.
     */
    private fun initializeConnection(
        eventStaticForms: HashMap<Event, JPanel>,
        challengeForms: HashMap<Event, CodeXPChallengeForm>
    ) {
        val gridBagConstraints = GridBagConstraints()
        gridBagConstraints.weightx = 1.0
        gridBagConstraints.fill = GridBagConstraints.HORIZONTAL

        // Update the dashboard when events occur
        ApplicationManager.getApplication().messageBus.connect()
            .subscribe(CodeXPEventListener.CODEXP_EVENT, object : CodeXPEventListener {
                override fun eventOccurred(event: Event, dataContext: DataContext?) {
                    (eventStaticForms[event]!!.getComponent(2) as JLabel).text =
                        StringUtil.numberToStringWithCommas(codeXPService.state.getEventCount(event))
                }
            })

        ApplicationManager.getApplication().messageBus.connect()
            .subscribe(CodeXPListener.CODEXP, object : CodeXPListener {
                override fun xpUpdated(levelInfo: CodeXPLevel) {
                    updateXPInfo(levelInfo)
                }

                override fun levelUp(levelInfo: CodeXPLevel) {

                }

                override fun challengeUpdated(
                    event: Event,
                    challenge: CodeXPChallenge,
                    newChallenge: CodeXPChallenge?
                ) {
                    if (newChallenge != null) {
                        codeXPDashboardForm.lblCompletedChallengesCount.text =
                            StringUtil.numberToStringWithCommas(codeXPService.state.completedChallenges.size.toLong())

                        if (codeXPService.state.showCompletedChallenges) {
                            gridBagConstraints.gridy = codeXPDashboardForm.pCompletedChallenges.componentCount
                            codeXPDashboardForm.pCompletedChallenges.add(
                                createOrUpdateChallengeForm(challenge).pChallenge,
                                gridBagConstraints
                            )
                        }
                        createOrUpdateChallengeForm(newChallenge, challengeForms[event]!!)
                    } else {
                        updateChallengeProgress(challenge, challengeForms[event]!!)
                    }
                }

                override fun challengeCompleted(event: Event, challenge: CodeXPChallenge) {

                }
            })
    }

    /**
     * Updates user nickname on the dashboard.
     */
    private fun updateNickname() {
        codeXPService.state.nickname = codeXPDashboardForm.tfNickname.text
    }

    /**
     * Updates the XP info on the dashboard.
     */
    private fun updateXPInfo(levelInfo: CodeXPLevel) {
        codeXPDashboardForm.lblTotalXP.text = StringUtil.numberToStringWithCommas(codeXPService.state.xp)
        val (currentLevel, xpIntoCurrentLevel, progressToNextLevel) = levelInfo

        codeXPDashboardForm.lblCurrentLevel.text = StringUtil.numberToStringWithCommas(currentLevel.toLong())
        codeXPDashboardForm.lblNextLevel.text = StringUtil.numberToStringWithCommas((currentLevel + 1).toLong())
        codeXPDashboardForm.lblCurrentLevelXP.text = StringUtil.numberToStringWithCommas(xpIntoCurrentLevel)
        codeXPDashboardForm.pbCurrentLevelProgress.value = progressToNextLevel
        codeXPDashboardForm.pbCurrentLevelProgress.string = "$progressToNextLevel %"
        codeXPDashboardForm.lblLevel.text = StringUtil.numberToStringWithCommas(currentLevel.toLong())
    }

    /**
     * Updates completed challenges on the dashboard.
     */
    private fun updateCompletedChallenges() {
        // GridBagConstraints for the event statistics and challenges
        val gridBagConstraints = GridBagConstraints()
        gridBagConstraints.weightx = 1.0
        gridBagConstraints.fill = GridBagConstraints.HORIZONTAL

        codeXPService.state.completedChallenges.forEach { completedChallenge ->
            gridBagConstraints.gridy = codeXPDashboardForm.pCompletedChallenges.componentCount
            codeXPDashboardForm.pCompletedChallenges.add(
                createOrUpdateChallengeForm(completedChallenge).pChallenge,
                gridBagConstraints
            )
        }
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
     * Create or update existing challenge form.
     *
     * @param challenge Challenge to create or update.
     * @param challengeForm Challenge form to update.
     * @return Challenge form.
     */
    private fun createOrUpdateChallengeForm(
        challenge: CodeXPChallenge,
        challengeForm: CodeXPChallengeForm? = null
    ): CodeXPChallengeForm {
        val form = challengeForm ?: CodeXPChallengeForm()

        form.challengeID = challenge.id
        form.pChallenge.border = BorderFactory.createEmptyBorder(16, 32, 0, 32)
        form.lblChallengeName.text = challenge.name
        form.lblChallengeReward.text = StringUtil.numberToStringWithCommas(challenge.rewardXP)
        form.lblChallengeDescription.text =
            challenge.description.replace("[goal]", StringUtil.numberToStringWithCommas(challenge.goal))

        if (challenge.progress >= challenge.goal) {
            form.lblChallengeProgress.isVisible = false
            form.pbChallengeProgress.isVisible = false
            form.lblChallengePercentageIcon.isVisible = false
        } else {
            val progressPercentage = ((challenge.progress.toDouble() / challenge.goal) * 100).toInt()
            form.lblChallengeProgress.text = progressPercentage.toString()
            form.pbChallengeProgress.value = progressPercentage
        }
        return form
    }
}

