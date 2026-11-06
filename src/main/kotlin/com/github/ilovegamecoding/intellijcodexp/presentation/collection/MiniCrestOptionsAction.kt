package com.github.ilovegamecoding.intellijcodexp.presentation.collection

import com.github.ilovegamecoding.intellijcodexp.presentation.overlay.CodeXPOverlayController
import com.github.ilovegamecoding.intellijcodexp.services.CodeXPService
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.DumbAwareAction

class MiniCrestOptionsAction : DumbAwareAction() {
    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

    override fun update(e: AnActionEvent) {
        e.presentation.isEnabled = e.project != null && ApplicationManager.getApplication().getService(CodeXPService::class.java).equippedCrest() != null
    }

    override fun actionPerformed(e: AnActionEvent) {
        e.project?.getService(CodeXPOverlayController::class.java)?.showMiniCrestOptions(e.dataContext)
    }
}
