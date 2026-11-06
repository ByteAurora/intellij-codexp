package com.github.ilovegamecoding.intellijcodexp.activities

import com.github.ilovegamecoding.intellijcodexp.presentation.guide.CodeXPGuideUi
import com.github.ilovegamecoding.intellijcodexp.presentation.overlay.CodeXPOverlayController
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity

/**
 * Initializes project-window CodeXP UI controllers after a project opens.
 */
class CodeXPStartupActivity : ProjectActivity {
    override suspend fun execute(project: Project) {
        project.getService(CodeXPOverlayController::class.java)
        ApplicationManager.getApplication().invokeLater {
            if (!project.isDisposed) CodeXPGuideUi.offer(project)
        }
    }
}
