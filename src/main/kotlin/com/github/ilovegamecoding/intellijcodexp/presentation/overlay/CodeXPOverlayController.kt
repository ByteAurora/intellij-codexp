package com.github.ilovegamecoding.intellijcodexp.presentation.overlay

import com.github.ilovegamecoding.intellijcodexp.CodeXPBundle
import com.github.ilovegamecoding.intellijcodexp.enums.Event
import com.github.ilovegamecoding.intellijcodexp.listeners.CodeXPEventListener
import com.github.ilovegamecoding.intellijcodexp.listeners.CodeXPListener
import com.github.ilovegamecoding.intellijcodexp.models.CodeXPChallenge
import com.github.ilovegamecoding.intellijcodexp.models.CodeXPLevel
import com.github.ilovegamecoding.intellijcodexp.services.CodeXPService
import com.github.ilovegamecoding.intellijcodexp.utils.StringUtil
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project

/**
 * Displays CodeXP overlay effects for a single project window.
 */
@Service(Service.Level.PROJECT)
class CodeXPOverlayController(
    private val project: Project,
) : CodeXPEventListener,
    CodeXPListener,
    Disposable {
    private val codeXPService: CodeXPService =
        ApplicationManager
            .getApplication()
            .getService(CodeXPService::class.java)

    private val xpGainEffectRenderer = XpGainEffectRenderer()
    private val dialogPresenter = CodeXPOverlayDialogPresenter(project)

    init {
        val connection = ApplicationManager.getApplication().messageBus.connect(this)
        connection.subscribe(CodeXPEventListener.CODEXP_EVENT, this)
        connection.subscribe(CodeXPListener.CODEXP, this)

        dialogPresenter.initialize()
    }

    override fun eventOccurred(
        event: Event,
        dataContext: DataContext?,
    ) {
        val context = dataContext ?: return
        if (!belongsToProject(context)) {
            return
        }

        xpGainEffectRenderer.render(event, context, codeXPService.state.codeXPConfiguration)
    }

    override fun xpUpdated(levelInfo: CodeXPLevel) {
    }

    override fun levelUp(
        levelInfo: CodeXPLevel,
        dataContext: DataContext?,
    ) {
        if (!belongsToProject(dataContext)) {
            return
        }

        dialogPresenter.show(
            CodeXPOverlayDialog.createDialog(
                CodeXPBundle.message("overlay.level.up.title"),
                CodeXPBundle.message(
                    "overlay.level.up.main",
                    StringUtil.numberToStringWithCommas(levelInfo.level.toLong()),
                ),
                CodeXPBundle.message(
                    "overlay.level.up.sub",
                    StringUtil.numberToStringWithCommas(levelInfo.totalXPForNextLevel),
                ),
            ),
        )
    }

    override fun challengeUpdated(
        event: Event,
        challenge: CodeXPChallenge,
        newChallenge: CodeXPChallenge?,
    ) {
    }

    override fun challengeCompleted(
        event: Event,
        challenge: CodeXPChallenge,
        dataContext: DataContext?,
    ) {
        if (!belongsToProject(dataContext)) {
            return
        }

        dialogPresenter.show(
            CodeXPOverlayDialog.createDialog(
                CodeXPBundle.message("overlay.challenge.completed.title"),
                CodeXPBundle.message("overlay.challenge.completed.main", challenge.name.lowercase()),
                CodeXPBundle.message(
                    "overlay.challenge.completed.sub",
                    StringUtil.numberToStringWithCommas(challenge.rewardXP),
                ),
            ),
        )
    }

    private fun belongsToProject(dataContext: DataContext?): Boolean {
        dataContext ?: return false
        return CommonDataKeys.PROJECT.getData(dataContext) == project
    }

    override fun dispose() {
        xpGainEffectRenderer.dispose()
        dialogPresenter.dispose()
    }
}
