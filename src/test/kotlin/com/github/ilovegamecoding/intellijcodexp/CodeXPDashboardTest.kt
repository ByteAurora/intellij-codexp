package com.github.ilovegamecoding.intellijcodexp

import com.github.ilovegamecoding.intellijcodexp.enums.Event
import com.github.ilovegamecoding.intellijcodexp.form.CodeXPDashboardForm
import com.github.ilovegamecoding.intellijcodexp.models.CodeXPState
import com.github.ilovegamecoding.intellijcodexp.presentation.dashboard.CodeXPDashboardController
import com.github.ilovegamecoding.intellijcodexp.services.CodeXPService
import com.intellij.openapi.application.ApplicationManager
import com.intellij.testFramework.fixtures.BasePlatformTestCase

class CodeXPDashboardTest : BasePlatformTestCase() {
    fun testDashboardRendersProgressAndCompletedChallenges() {
        val service = ApplicationManager.getApplication().getService(CodeXPService::class.java)
        val original = service.state
        try {
            service.loadState(CodeXPState())
            service.state.codeXPConfiguration.showLevelUpNotification = false
            service.state.codeXPConfiguration.showCompleteChallengeNotification = false
            val form = CodeXPDashboardForm()
            assertNotNull(form.pMain)
            CodeXPDashboardController(service, form, testRootDisposable).initialize()
            assertEquals("0", form.lblTotalXP.text)
            assertEquals(Event.entries.size - 1, form.pChallenges.componentCount)

            service.eventOccurred(Event.TYPING, null)
            assertEquals("2", form.lblTotalXP.text)

            val challenge = service.state.challenges.getValue(Event.CUT)
            challenge.progress = challenge.goal - 1
            service.eventOccurred(Event.CUT, null)
            assertEquals("1", form.lblCompletedChallengesCount.text)

            form.cbShowCompletedChallenges.isSelected = true
            assertEquals(1, form.pCompletedChallenges.componentCount)
            form.cbShowCompletedChallenges.isSelected = false
            assertEquals(0, form.pCompletedChallenges.componentCount)
            form.cbShowCompletedChallenges.isSelected = true
            assertEquals(1, form.pCompletedChallenges.componentCount)
        } finally {
            service.loadState(original)
        }
    }
}
