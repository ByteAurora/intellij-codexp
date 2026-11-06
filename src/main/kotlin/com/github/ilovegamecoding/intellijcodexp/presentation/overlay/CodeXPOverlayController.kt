package com.github.ilovegamecoding.intellijcodexp.presentation.overlay

import com.github.ilovegamecoding.intellijcodexp.domain.CodeXPProgressResult
import com.github.ilovegamecoding.intellijcodexp.listeners.CodeXPEffectListener
import com.github.ilovegamecoding.intellijcodexp.listeners.CodeXPStateListener
import com.github.ilovegamecoding.intellijcodexp.listeners.IdeXpOrigin
import com.github.ilovegamecoding.intellijcodexp.listeners.XpDestination
import com.github.ilovegamecoding.intellijcodexp.presentation.collection.MiniCrestController
import com.github.ilovegamecoding.intellijcodexp.services.CodeXPService
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.editor.event.EditorFactoryEvent
import com.intellij.openapi.editor.event.EditorFactoryListener
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.wm.WindowManager
import javax.swing.SwingUtilities

/** Owns presentation resources for one project, never progress or rewards. */
@Service(Service.Level.PROJECT)
class CodeXPOverlayController(
    private val project: Project,
) : CodeXPEffectListener,
    Disposable {
    private val service = ApplicationManager.getApplication().getService(CodeXPService::class.java)
    private var mini: MiniCrestController? = null
    private val presenter = CodeXPOverlayDialogPresenter(project) { mini?.scheduleRefresh() }
    private val xpRenderer =
        XpGainEffectRenderer(crestTarget = { pane -> if (presenter.isActive) null else mini?.xpAbsorptionTarget(pane) }) { mini?.scheduleRefresh() }
    private var observedState = service.state

    @Volatile private var disposed = false

    init {
        onEdt {
            if (!disposed && !project.isDisposed) {
                mini = MiniCrestController(project, service, xpObscures = xpRenderer::overlaps) { _, _ -> presenter.isActive }.also { Disposer.register(this, it) }
            }
        }
        val connection = ApplicationManager.getApplication().messageBus.connect(this)
        connection.subscribe(CodeXPEffectListener.EFFECTS, this)
        connection.subscribe(
            CodeXPStateListener.STATE,
            CodeXPStateListener {
                val replaced = observedState !== service.state
                observedState = service.state
                onEdt {
                    if (!disposed && (replaced || !service.state.codeXPConfiguration.showGainedXP)) xpRenderer.clear()
                    if (!disposed && replaced) presenter.reset()
                }
            },
        )
        EditorFactory.getInstance().addEditorFactoryListener(
            object : EditorFactoryListener {
                override fun editorReleased(event: EditorFactoryEvent) {
                    onEdt {
                        xpRenderer.release(event.editor)
                        presenter.release(event.editor)
                    }
                }
            },
            this,
        )
    }

    override fun progressRecorded(
        result: CodeXPProgressResult,
        dataContext: DataContext?,
    ) {
        if (disposed || project.isDisposed || dataContext == null) return
        val origin = IdeXpOrigin.KEY.getData(dataContext) ?: IdeXpOrigin.unspecified(dataContext)
        if (origin.project !== project) return
        // Capture ephemeral DataContext and mutable domain/configuration values before deferring to EDT.
        val editor = origin.editor?.takeIf { it.project === project }
        val achievementEditor = CommonDataKeys.EDITOR.getData(dataContext)?.takeIf { it.project === project }
        val recordedState = service.state
        val config = recordedState.codeXPConfiguration.copy()
        val effect = AchievementEffect.from(result, config)?.copy(crest = service.equippedCrest())
        val gained = result.event.xpValue
        onEdt {
            if (disposed || project.isDisposed || service.state !== recordedState) return@onEdt
            if (!config.showGainedXP) {
                xpRenderer.clear()
            } else {
                when (origin.destination) {
                    XpDestination.CARET -> {
                        if (editor != null && !editor.isDisposed) xpRenderer.render(gained, editor, config)
                    }

                    XpDestination.WINDOW -> {
                        val pane =
                            origin.pane ?: WindowManager
                                .getInstance()
                                .getIdeFrame(project)
                                ?.component
                                ?.rootPane
                                ?.layeredPane
                        if (pane != null) xpRenderer.renderWindow(gained, pane, config)
                    }

                    XpDestination.NONE -> {
                        Unit
                    }
                }
            }
            if (effect != null) presenter.show(effect, achievementEditor ?: FileEditorManager.getInstance(project).selectedTextEditor)
        }
    }

    override fun crestEquipped(
        eventId: String,
        design: com.github.ilovegamecoding.intellijcodexp.domain.collection.CrestDesign,
        dataContext: DataContext?,
    ) {
        if (disposed || project.isDisposed || dataContext == null || CommonDataKeys.PROJECT.getData(dataContext) !== project) return
        val state = service.state
        val options = EffectOptions.from(state.codeXPConfiguration.copy())
        onEdt {
            if (!disposed && !project.isDisposed && service.state === state) {
                presenter.show(AchievementEffect(null, null, options, crest = design, equippedEventId = eventId), FileEditorManager.getInstance(project).selectedTextEditor)
            }
        }
    }

    internal fun showMiniCrestOptions(context: DataContext) {
        mini?.showOptions(context)
    }

    override fun dispose() {
        disposed = true
        onEdt {
            xpRenderer.dispose()
            presenter.dispose()
        }
    }
}

internal fun onEdt(action: () -> Unit) {
    if (SwingUtilities.isEventDispatchThread()) action() else ApplicationManager.getApplication().invokeLater(action)
}
