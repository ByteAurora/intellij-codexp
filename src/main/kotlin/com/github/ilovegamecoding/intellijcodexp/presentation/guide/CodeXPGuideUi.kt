package com.github.ilovegamecoding.intellijcodexp.presentation.guide

import com.github.ilovegamecoding.intellijcodexp.CodeXPBundle
import com.github.ilovegamecoding.intellijcodexp.presentation.dashboard.CodeXPDashboardRoot
import com.github.ilovegamecoding.intellijcodexp.services.CodeXPGuideService
import com.github.ilovegamecoding.intellijcodexp.services.CodeXPService
import com.intellij.notification.NotificationAction
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.options.ShowSettingsUtil
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindowManager

internal object CodeXPGuideUi {
    fun service(): CodeXPGuideService {
        val app = ApplicationManager.getApplication()
        val progress = app.getService(CodeXPService::class.java)
        return app.getService(CodeXPGuideService::class.java).also { it.initialize(progress.hasExistingProfile) }
    }

    fun offer(project: Project) {
        val guide = service()
        val run = guide.claimNotice() ?: return
        val title = CodeXPBundle.message(if (run.intro) "guide.notice.intro.title" else "guide.notice.update.title", run.releases.last().version)
        val body = CodeXPBundle.message(if (run.intro) "guide.notice.intro.body" else "guide.notice.update.body")
        NotificationGroupManager
            .getInstance()
            .getNotificationGroup("CodeXP Guide")
            .createNotification(title, body, NotificationType.INFORMATION)
            .addAction(NotificationAction.createSimpleExpiring(CodeXPBundle.message("guide.notice.open")) { open(project) })
            .addAction(NotificationAction.createSimpleExpiring(CodeXPBundle.message("guide.notice.dismiss")) { guide.complete(run) })
            .notify(project)
    }

    fun open(project: Project) {
        if (project.isDisposed) return
        project.getService(CodeXPGuideWindow::class.java).open()
    }

    fun openDestination(
        project: Project,
        destination: GuideDestination,
    ) {
        if (project.isDisposed) return
        if (destination == GuideDestination.SETTINGS) {
            ShowSettingsUtil.getInstance().showSettingsDialog(project, "CodeXP")
            return
        }
        val window = ToolWindowManager.getInstance(project).getToolWindow("CodeXP Dashboard") ?: return
        window.activate({
            if (!project.isDisposed) {
                (
                    window.contentManager.contents
                        .firstOrNull()
                        ?.component as? CodeXPDashboardRoot
                )?.showDestination(destination)
            }
        })
    }
}
