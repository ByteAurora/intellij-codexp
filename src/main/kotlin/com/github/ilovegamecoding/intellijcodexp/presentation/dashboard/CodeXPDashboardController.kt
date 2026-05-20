package com.github.ilovegamecoding.intellijcodexp.presentation.dashboard

import com.github.ilovegamecoding.intellijcodexp.enums.Event
import com.github.ilovegamecoding.intellijcodexp.form.CodeXPChallengeForm
import com.github.ilovegamecoding.intellijcodexp.form.CodeXPDashboardForm
import com.github.ilovegamecoding.intellijcodexp.listeners.CodeXPEventListener
import com.github.ilovegamecoding.intellijcodexp.listeners.CodeXPListener
import com.github.ilovegamecoding.intellijcodexp.models.CodeXPChallenge
import com.github.ilovegamecoding.intellijcodexp.models.CodeXPLevel
import com.github.ilovegamecoding.intellijcodexp.services.CodeXPService
import com.github.ilovegamecoding.intellijcodexp.utils.StringUtil
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.application.ApplicationManager
import java.awt.GridBagConstraints
import java.awt.event.ItemEvent
import javax.swing.JPanel
import javax.swing.event.DocumentEvent
import javax.swing.event.DocumentListener

/**
 * Coordinates dashboard state binding, updates, and message bus subscriptions.
 */
internal class CodeXPDashboardController(
    private val codeXPService: CodeXPService,
    private val dashboardForm: CodeXPDashboardForm,
    private val dashboardDisposable: Disposable,
) {
    private val eventStatisticPanels: MutableMap<Event, JPanel> = mutableMapOf()
    private val challengeForms: MutableMap<Event, CodeXPChallengeForm> = mutableMapOf()

    /**
     * Initializes dashboard UI state and message bus subscriptions.
     */
    fun initialize() {
        initializeNickname()
        initializeEventStatisticsAndChallenges()
        initializeCompletedChallenges()
        initializeConnection()

        updateXPInfo(CodeXPLevel.createLevelInfo(codeXPService.state.xp))
    }

    private fun initializeNickname() {
        dashboardForm.tfNickname.text = codeXPService.state.nickname
        dashboardForm.tfNickname.document.addDocumentListener(
            object : DocumentListener {
                override fun insertUpdate(e: DocumentEvent) {
                    updateNickname()
                }

                override fun removeUpdate(e: DocumentEvent) {
                    updateNickname()
                }

                override fun changedUpdate(e: DocumentEvent) {
                    updateNickname()
                }
            },
        )
    }

    private fun initializeEventStatisticsAndChallenges() {
        Event.entries
            .filter { it != Event.NONE }
            .forEachIndexed { index, event ->
                addEventStatistic(event, index)
                addActiveChallenge(event, index)
            }
    }

    private fun addEventStatistic(
        event: Event,
        rowIndex: Int,
    ) {
        val eventStatisticPanel =
            CodeXPEventStatisticRenderer.create(
                event = event,
                eventCount = codeXPService.state.getEventCount(event),
            )
        dashboardForm.pEventStatistics.add(eventStatisticPanel, createFillConstraints(rowIndex))
        eventStatisticPanels[event] = eventStatisticPanel
    }

    private fun addActiveChallenge(
        event: Event,
        rowIndex: Int,
    ) {
        codeXPService.state.challenges[event]?.let { challenge ->
            val challengeForm = CodeXPChallengeRenderer.create(challenge)
            dashboardForm.pChallenges.add(challengeForm.pChallenge, createFillConstraints(rowIndex))
            challengeForms[event] = challengeForm
        }
    }

    private fun initializeCompletedChallenges() {
        dashboardForm.lblCompletedChallengesCount.text =
            StringUtil.numberToStringWithCommas(
                codeXPService
                    .state
                    .completedChallenges
                    .size
                    .toLong(),
            )
        dashboardForm.cbShowCompletedChallenges.isSelected = codeXPService.state.showCompletedChallenges

        if (codeXPService.state.showCompletedChallenges) {
            renderCompletedChallenges()
        }

        dashboardForm.cbShowCompletedChallenges.addItemListener { event ->
            val isSelected = event.stateChange == ItemEvent.SELECTED
            codeXPService.setCompletedChallengesVisible(isSelected)

            if (isSelected) {
                renderCompletedChallenges()
            } else {
                dashboardForm.pCompletedChallenges.removeAll()
            }
            refreshCompletedChallenges()
        }
    }

    private fun initializeConnection() {
        val connection = ApplicationManager.getApplication().messageBus.connect(dashboardDisposable)

        connection.subscribe(
            CodeXPEventListener.CODEXP_EVENT,
            object : CodeXPEventListener {
                override fun eventOccurred(
                    event: Event,
                    dataContext: DataContext?,
                ) {
                    eventStatisticPanels[event]?.let { panel ->
                        CodeXPEventStatisticRenderer.updateCount(panel, codeXPService.state.getEventCount(event))
                    }
                }
            },
        )

        connection.subscribe(
            CodeXPListener.CODEXP,
            object : CodeXPListener {
                override fun xpUpdated(levelInfo: CodeXPLevel) {
                    updateXPInfo(levelInfo)
                }

                override fun levelUp(
                    levelInfo: CodeXPLevel,
                    dataContext: DataContext?,
                ) {
                }

                override fun challengeUpdated(
                    event: Event,
                    challenge: CodeXPChallenge,
                    newChallenge: CodeXPChallenge?,
                ) {
                    updateChallenge(event, challenge, newChallenge)
                }

                override fun challengeCompleted(
                    event: Event,
                    challenge: CodeXPChallenge,
                    dataContext: DataContext?,
                ) {
                }
            },
        )
    }

    private fun updateChallenge(
        event: Event,
        challenge: CodeXPChallenge,
        newChallenge: CodeXPChallenge?,
    ) {
        val challengeForm = challengeForms[event] ?: return

        if (newChallenge == null) {
            CodeXPChallengeRenderer.updateProgress(challenge, challengeForm)
            return
        }

        updateCompletedChallengeCount()
        if (codeXPService.state.showCompletedChallenges) {
            addCompletedChallenge(challenge)
            refreshCompletedChallenges()
        }
        CodeXPChallengeRenderer.update(newChallenge, challengeForm)
    }

    private fun updateNickname() {
        codeXPService.updateNickname(dashboardForm.tfNickname.text)
    }

    private fun updateXPInfo(levelInfo: CodeXPLevel) {
        val (currentLevel, xpIntoCurrentLevel, progressToNextLevel) = levelInfo

        dashboardForm.lblTotalXP.text = StringUtil.numberToStringWithCommas(codeXPService.state.xp)
        dashboardForm.lblCurrentLevel.text = StringUtil.numberToStringWithCommas(currentLevel.toLong())
        dashboardForm.lblNextLevel.text = StringUtil.numberToStringWithCommas((currentLevel + 1).toLong())
        dashboardForm.lblCurrentLevelXP.text = StringUtil.numberToStringWithCommas(xpIntoCurrentLevel)
        dashboardForm.pbCurrentLevelProgress.value = progressToNextLevel
        dashboardForm.pbCurrentLevelProgress.string = "$progressToNextLevel %"
        dashboardForm.lblLevel.text = StringUtil.numberToStringWithCommas(currentLevel.toLong())
    }

    private fun renderCompletedChallenges() {
        dashboardForm.pCompletedChallenges.removeAll()
        codeXPService.state.completedChallenges.forEach(::addCompletedChallenge)
    }

    private fun addCompletedChallenge(completedChallenge: CodeXPChallenge) {
        dashboardForm.pCompletedChallenges.add(
            CodeXPChallengeRenderer.create(completedChallenge).pChallenge,
            createFillConstraints(dashboardForm.pCompletedChallenges.componentCount),
        )
    }

    private fun updateCompletedChallengeCount() {
        dashboardForm.lblCompletedChallengesCount.text =
            StringUtil.numberToStringWithCommas(
                codeXPService
                    .state
                    .completedChallenges
                    .size
                    .toLong(),
            )
    }

    private fun refreshCompletedChallenges() {
        dashboardForm.pCompletedChallenges.revalidate()
        dashboardForm.pCompletedChallenges.repaint()
    }

    private fun createFillConstraints(rowIndex: Int): GridBagConstraints =
        GridBagConstraints().apply {
            weightx = 1.0
            fill = GridBagConstraints.HORIZONTAL
            gridy = rowIndex
        }
}
