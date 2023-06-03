package com.github.ilovegamecoding.intellijcodexp.toolWindow

import com.github.ilovegamecoding.intellijcodexp.StringUtil
import com.github.ilovegamecoding.intellijcodexp.form.CodeXPDashboard
import com.github.ilovegamecoding.intellijcodexp.listeners.CodeXPListener
import com.github.ilovegamecoding.intellijcodexp.services.CodeXPService
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.ui.content.ContentFactory
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
        val content = contentFactory.createContent(codeXPDashboard.pMain, null, false)
        toolWindow.contentManager.addContent(content)
    }

    override fun shouldBeAvailable(project: Project) = true

    /**
     * Initializes the UI of the dashboard.
     *
     * @param codeXPService The CodeXP service.
     * @param codeXPDashboard The CodeXP dashboard.
     */
    private fun initializeUI(codeXPService: CodeXPService, codeXPDashboard: CodeXPDashboard) {
        // Listen to events from the CodeXP service
        val connection = ApplicationManager.getApplication().messageBus.connect()

        val constraints = GridBagConstraints()
        constraints.weightx = 1.0
        constraints.fill = GridBagConstraints.HORIZONTAL
        val eventStatics = HashMap<CodeXPService.Event, JPanel>()
        CodeXPService.Event.values().forEachIndexed { index, event -> // Add ui for each event
            if (event != CodeXPService.Event.NONE) { // Ignore the NONE event type
                constraints.gridy = index

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
                codeXPDashboard.pEventStatistics.add(pEvent, constraints)

                eventStatics[event] = pEvent
            }
        }

        // Update the dashboard when events occur
        connection.subscribe(CodeXPListener.CODEXP_EVENT, object : CodeXPListener {
            override fun eventOccurred(event: CodeXPService.Event) {
                (eventStatics[event]!!.getComponent(2) as JLabel).text =
                    StringUtil.numberToStringWithCommas(codeXPService.state.getEventCount(event))
                updateXPInfo(codeXPService, codeXPDashboard)
            }
        })

        codeXPDashboard.tfNickname.text = codeXPService.state.nickname
        codeXPDashboard.tfNickname.document.addDocumentListener(object : DocumentListener {
            override fun insertUpdate(e: DocumentEvent) {
                codeXPService.state.nickname = codeXPDashboard.tfNickname.text
            }

            override fun removeUpdate(e: DocumentEvent) {
                codeXPService.state.nickname = codeXPDashboard.tfNickname.text
            }

            override fun changedUpdate(e: DocumentEvent) {
                codeXPService.state.nickname = codeXPDashboard.tfNickname.text
            }
        })

        updateXPInfo(codeXPService, codeXPDashboard)
    }

    /**
     * Updates the XP info on the dashboard.
     *
     * @param codeXPService The CodeXP service.
     * @param codeXPDashboard The CodeXP dashboard.
     */
    fun updateXPInfo(codeXPService: CodeXPService, codeXPDashboard: CodeXPDashboard) {
        val totalXP = codeXPService.state.xp
        codeXPDashboard.lblTotalXP.text = StringUtil.numberToStringWithCommas(totalXP)
        val (currentLevel, xpIntoCurrentLevel, progressToNextLevel) = calculateLevelAndProgress(
            totalXP
        )

        codeXPDashboard.lblCurrentLevel.text = StringUtil.numberToStringWithCommas(currentLevel.toLong())
        codeXPDashboard.lblNextLevel.text = StringUtil.numberToStringWithCommas((currentLevel + 1).toLong())
        codeXPDashboard.lblCurrentLevelXP.text = StringUtil.numberToStringWithCommas(xpIntoCurrentLevel)
        codeXPDashboard.pbCurrentLevelProgress.value = progressToNextLevel
        codeXPDashboard.pbCurrentLevelProgress.string = "$progressToNextLevel %"
        codeXPDashboard.lblLevel.text = StringUtil.numberToStringWithCommas(currentLevel.toLong())
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
        val progressToNextLevel: Int
    )

    /**
     * Calculates the level and progress to the next level.
     *
     * @param totalXP The total XP.
     * @return The level info.
     */
    fun calculateLevelAndProgress(totalXP: Long): LevelInfo {
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

        return LevelInfo(level, xpIntoCurrentLevel.toLong(), progress.toInt())
    }
}

