package com.github.ilovegamecoding.intellijcodexp.presentation.collection

import com.github.ilovegamecoding.intellijcodexp.CodeXPBundle
import com.github.ilovegamecoding.intellijcodexp.listeners.CodeXPEffectListener
import com.github.ilovegamecoding.intellijcodexp.listeners.CodeXPStateListener
import com.github.ilovegamecoding.intellijcodexp.models.CodeXPConfiguration
import com.github.ilovegamecoding.intellijcodexp.models.CodeXPConfiguration.Companion.MINI_CREST_SIZES
import com.github.ilovegamecoding.intellijcodexp.presentation.dashboard.systemReducedMotion
import com.github.ilovegamecoding.intellijcodexp.presentation.overlay.EffectOptions
import com.github.ilovegamecoding.intellijcodexp.presentation.overlay.XpAbsorptionTarget
import com.github.ilovegamecoding.intellijcodexp.presentation.overlay.onEdt
import com.github.ilovegamecoding.intellijcodexp.services.CodeXPService
import com.intellij.codeInsight.lookup.Lookup
import com.intellij.codeInsight.lookup.LookupEvent
import com.intellij.codeInsight.lookup.LookupEx
import com.intellij.codeInsight.lookup.LookupListener
import com.intellij.codeInsight.lookup.LookupManager
import com.intellij.codeInsight.lookup.LookupManagerListener
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.editor.event.CaretEvent
import com.intellij.openapi.editor.event.CaretListener
import com.intellij.openapi.editor.event.VisibleAreaListener
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.FileEditorManagerEvent
import com.intellij.openapi.fileEditor.FileEditorManagerListener
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.popup.JBPopup
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.ui.popup.JBPopupListener
import com.intellij.openapi.ui.popup.LightweightWindowEvent
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.wm.IdeGlassPane
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.openapi.wm.WindowManager
import com.intellij.openapi.wm.ex.ToolWindowManagerListener
import com.intellij.ui.awt.RelativePoint
import com.intellij.ui.scale.JBUIScale
import com.intellij.util.ui.JBUI
import java.awt.AWTEvent
import java.awt.Component
import java.awt.Frame
import java.awt.KeyboardFocusManager
import java.awt.Point
import java.awt.Rectangle
import java.awt.Toolkit
import java.awt.Window
import java.awt.event.AWTEventListener
import java.awt.event.ComponentEvent
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.awt.event.WindowEvent
import java.beans.PropertyChangeListener
import javax.swing.JLayeredPane
import javax.swing.MenuSelectionManager
import javax.swing.SwingUtilities
import javax.swing.event.ChangeListener

/** Project-owned presentation only. State is sampled on events, never on animation frames. */
internal class MiniCrestController(
    private val project: Project,
    private val service: CodeXPService,
    private val xpObscures: (JLayeredPane, Rectangle) -> Boolean = { _, _ -> false },
    private val effectsObscure: (JLayeredPane, Rectangle) -> Boolean,
) : Disposable {
    internal val surface =
        MiniCrestSurface().apply {
            isVisible = false
            visibilityAlpha = 0f
            interactive = false
        }
    private var hasCrest = false
    private var configuration = service.state.codeXPConfiguration.copy()
    private var pane: JLayeredPane? = null
    private var window: Window? = null
    private var disposed = false
    private var queued = false
    private var dragOrigin: Point? = null
    private var dragBounds: Point? = null
    private var dragged = false
    private var menu: JBPopup? = null
    private var contextLifetime: Disposable? = null
    private var osReduced = false
    private var lookup: LookupEx? = null
    private val lookupListener =
        object : LookupListener {
            override fun lookupShown(event: LookupEvent) = scheduleRefresh()

            override fun uiRefreshed() = scheduleRefresh()
        }
    private val motion = CollectionMotion(::repaintCrest, { systemReducedMotion().also { osReduced = it } })
    private val visibility =
        MiniCrestVisibility({ opacity, showing, interactive ->
            surface.visibilityAlpha = opacity
            surface.interactive = interactive
            surface.isVisible = showing
            if (!showing) {
                motion.visibilityChanged(false, false, false)
                if (!hasCrest) surface.design = null
            }
            surface.repaint()
        })
    private val menus = MenuSelectionManager.defaultManager()
    private val menuListener = ChangeListener { scheduleRefresh() }
    private val focusListener = PropertyChangeListener { scheduleRefresh() }
    private val windowListener =
        AWTEventListener { event ->
            // Resolve the new focused window after the transfer, including owned dialogs.
            if (event is WindowEvent || (event is ComponentEvent && (event.component === pane || event.component === window))) scheduleRefresh()
        }

    init {
        val profileEvents = ApplicationManager.getApplication().messageBus.connect(this)
        profileEvents.subscribe(CodeXPStateListener.STATE, CodeXPStateListener { onEdt(::stateChanged) })
        profileEvents.subscribe(
            CodeXPEffectListener.EFFECTS,
            CodeXPEffectListener { result, _ ->
                // The profile level is shared even when a different project earned the XP.
                if (result.xpChanges.any { it.isLevelUp }) onEdt(::stateChanged)
            },
        )
        val connection = project.messageBus.connect(this)
        connection.subscribe(
            ToolWindowManagerListener.TOPIC,
            object : ToolWindowManagerListener {
                override fun stateChanged(toolWindowManager: ToolWindowManager) = scheduleRefresh()
            },
        )
        connection.subscribe(LookupManagerListener.TOPIC, LookupManagerListener { _, current -> observeLookup(current) })
        observeLookup(LookupManager.getInstance(project).activeLookup)
        connection.subscribe(
            FileEditorManagerListener.FILE_EDITOR_MANAGER,
            object : FileEditorManagerListener {
                override fun selectionChanged(event: FileEditorManagerEvent) = scheduleRefresh()
            },
        )
        val events = EditorFactory.getInstance().eventMulticaster
        events.addCaretListener(
            object : CaretListener {
                override fun caretPositionChanged(event: CaretEvent) {
                    if (event.editor.project === project) scheduleRefresh()
                }
            },
            this,
        )
        events.addVisibleAreaListener(VisibleAreaListener { if (it.editor.project === project) scheduleRefresh() }, this)
        Toolkit.getDefaultToolkit().addAWTEventListener(windowListener, AWTEvent.WINDOW_EVENT_MASK or AWTEvent.WINDOW_FOCUS_EVENT_MASK or AWTEvent.COMPONENT_EVENT_MASK)
        KeyboardFocusManager.getCurrentKeyboardFocusManager().addPropertyChangeListener("focusOwner", focusListener)
        KeyboardFocusManager.getCurrentKeyboardFocusManager().addPropertyChangeListener("focusedWindow", focusListener)
        menus.addChangeListener(menuListener)
        val mouse =
            object : MouseAdapter() {
                override fun mousePressed(event: MouseEvent) {
                    dragged = false
                    if (!event.isConsumed && !event.isPopupTrigger && SwingUtilities.isLeftMouseButton(event)) {
                        dragOrigin = event.locationOnScreen
                        dragBounds = surface.location
                        scheduleRefresh()
                    }
                }

                override fun mouseDragged(event: MouseEvent) {
                    val origin = dragOrigin ?: return
                    val start = dragBounds ?: return
                    val layer = pane ?: return
                    val delta = Point(event.xOnScreen - origin.x, event.yOnScreen - origin.y)
                    if (!dragged && delta.distance(0.0, 0.0) < JBUI.scale(3)) return
                    dragged = true
                    val anchor = miniCrestAnchor(Point(start.x + delta.x, start.y + delta.y), Rectangle(layer.size), configuration, scale())
                    configuration.miniCrestX = anchor.first
                    configuration.miniCrestY = anchor.second
                    configuration.miniCrestWindowAnchor = true
                    position(layer)
                }

                override fun mouseReleased(event: MouseEvent) {
                    dragOrigin = null
                    dragBounds = null
                    if (dragged) {
                        val x = configuration.miniCrestX
                        val y = configuration.miniCrestY
                        diagnostic("moved anchor=$x,$y")
                        update { it.copy(miniCrestX = x, miniCrestY = y, miniCrestWindowAnchor = true) }
                    }
                    scheduleRefresh()
                }
            }
        surface.onControlRequested = {
            if (!dragged) showMenu(Point(surface.width / 2, surface.height / 2))
        }
        surface.addMouseListener(mouse)
        surface.addMouseMotionListener(mouse)
        surface.control.addMouseListener(mouse)
        surface.control.addMouseMotionListener(mouse)
        stateChanged()
    }

    private fun stateChanged() {
        if (disposed || project.isDisposed) return
        val state = service.state
        configuration =
            state.codeXPConfiguration
                .copy()
                .apply { sanitizeMiniCrest() }
        val design = service.equippedCrest()
        hasCrest = design != null
        // Retain the last artwork until an unequip fade has finished.
        if (design != null || !surface.isVisible) surface.design = design
        surface.level = state.levelInfo().level
        surface.options = EffectOptions.from(configuration)
        motion.configure(configuration.reducedMotion)
        scheduleRefresh()
    }

    fun scheduleRefresh() {
        if (disposed || queued) return
        queued = true
        // Presentation-only work must also run while Settings holds a modal event loop.
        ApplicationManager.getApplication().invokeLater({
            queued = false
            if (!disposed && !project.isDisposed) refresh()
        }, ModalityState.any())
    }

    private fun observeLookup(current: Lookup?) {
        lookup?.removeLookupListener(lookupListener)
        lookup = current as? LookupEx
        lookup?.addLookupListener(lookupListener)
        scheduleRefresh()
    }

    private fun refresh() {
        val root =
            WindowManager
                .getInstance()
                .getIdeFrame(project)
                ?.component
                ?.rootPane
        val layer = root?.layeredPane
        if (pane !== layer) {
            detach()
            pane = layer
            window = layer?.let(SwingUtilities::getWindowAncestor)
            layer?.add(surface, JLayeredPane.PALETTE_LAYER, -1)
            val glass = root?.glassPane as? IdeGlassPane
            if (glass != null) {
                val lifetime = Disposer.newDisposable("Mini crest context menu")
                contextLifetime = lifetime
                glass.addMousePreprocessor(MiniCrestContextGesture(::crestPoint, ::showMenu), lifetime)
            }
        }
        val owner = window
        if (layer == null || owner == null) {
            visibility.reset()
            motion.visibilityChanged(false, false, false)
            return
        }
        position(layer)
        val editor = FileEditorManager.getInstance(project).selectedTextEditor
        val focus = KeyboardFocusManager.getCurrentKeyboardFocusManager().focusedWindow
        val active = miniCrestWindowActive(owner, focus)
        val ownMenu = ownMenuOpen()
        val eligible =
            configuration.miniCrestEnabled && hasCrest && layer.isShowing && (active || ownMenu) &&
                !effectsObscure(layer, surface.bounds) &&
                ToolWindowManager.getInstance(project).getToolWindow("CodeXP Dashboard")?.isVisible != true
        var nearCaret = false
        if (eligible && editor != null && !editor.isDisposed && editor.contentComponent.isShowing && dragOrigin == null) {
            val caret = editor.visualPositionToXY(editor.caretModel.visualPosition)
            val point = SwingUtilities.convertPoint(editor.contentComponent, caret, layer)
            val avoidance = Rectangle(point.x - JBUI.scale(24), point.y - editor.lineHeight, JBUI.scale(180), editor.lineHeight * 3)
            nearCaret = surface.bounds.intersects(avoidance)
        }
        val visible = eligible && surface.width >= JBUI.scale(20) && surface.height >= JBUI.scale(20)
        val nearXp = visible && xpObscures(layer, surface.bounds)
        val nearLookup = visible && lookupCoversMiniCrest(lookup?.component, layer, surface.bounds)
        val dimmed = dragOrigin == null && (nearCaret || nearXp || nearLookup)
        if (visibility.targetVisible != visible || visibility.dimmed != (visible && dimmed)) diagnostic("visible=$visible active=$active ownMenu=$ownMenu caret=$nearCaret xp=$nearXp dimmed=$dimmed")
        // Dimming improves readability without disabling dragging. Popups stay above the palette layer.
        visibility.setVisible(visible, configuration.reducedMotion || osReduced, dimmed = dimmed)
        motion.visibilityChanged(surface.isVisible, active && !ownMenu, surface.isVisible)
        // The animation clock already paints visible artwork; caret events must not add extra frames.
        if (!motion.isRunning) repaintCrest()
    }

    private fun position(layer: JLayeredPane) {
        val bounds = miniCrestBounds(Rectangle(layer.size), configuration, scale())
        if (surface.bounds != bounds) {
            val old = surface.bounds
            surface.bounds = bounds
            surface.doLayout()
            layer.repaint(old.x, old.y, old.width, old.height)
        }
    }

    private fun scale() = JBUIScale.scale(1f).toDouble()

    private fun repaintCrest() {
        surface.animationRunning = motion.isRunning
        if (motion.isRunning) {
            surface.frame = motion.frame("mini", true)
        } else if (configuration.reducedMotion || osReduced) {
            surface.frame = CollectionMotionFrame()
        }
        if (!motion.isRunning) surface.pauseMetrics()
        if (surface.isVisible) surface.repaint()
    }

    private fun update(change: (CodeXPConfiguration) -> CodeXPConfiguration) {
        service.updateConfiguration(change(service.state.codeXPConfiguration.copy()))
    }

    private fun ownMenuOpen() = menu?.isVisible == true

    fun xpAbsorptionTarget(layer: JLayeredPane): XpAbsorptionTarget? {
        if (disposed || pane !== layer || !surface.isShowing || !visibility.targetVisible || visibility.opacity < .9f || !motion.isRunning ||
            !configuration.miniCrestEnabled || surface.design == null || dragOrigin != null || ownMenuOpen() || window?.isActive != true
        ) {
            return null
        }
        return XpAbsorptionTarget(
            Point(surface.x + surface.width / 2, surface.y + surface.height / 2),
            minOf(surface.width, surface.height) * if (surface.options.subtle) .42 else .48,
            surface.design,
            minOf(surface.width / 240.0, surface.height / 210.0),
        )
    }

    private fun diagnostic(message: String) {
        if (java.lang.Boolean.getBoolean("codexp.collection.measure")) Logger.getInstance(MiniCrestController::class.java).info("MINI_CREST_STATE $message")
    }

    private fun crestPoint(event: MouseEvent): Point? {
        if (!surface.isShowing || !surface.interactive || ownMenuOpen() || menus.selectedPath.isNotEmpty() || JBPopupFactory.getInstance().isPopupActive) return null
        return SwingUtilities.convertPoint(event.component, event.point, surface).takeIf(surface::onArtwork)
    }

    fun showOptions(context: DataContext) = showMenu { it.showInBestPositionFor(context) }

    private fun showMenu(point: Point) = showMenu { it.show(RelativePoint(surface, point)) }

    internal fun contextActions(): Map<String, () -> Unit> {
        val actions = linkedMapOf<String, () -> Unit>()
        for (size in MINI_CREST_SIZES) {
            val label = if (size == configuration.miniCrestSize) CodeXPBundle.message("mini.size.current", size) else CodeXPBundle.message("mini.size", size)
            actions[label] = { if (size != configuration.miniCrestSize) update { it.copy(miniCrestSize = size) } }
        }
        val enabled = configuration.miniCrestEnabled
        actions[if (enabled) CodeXPBundle.message("mini.hide") else CodeXPBundle.message("mini.show")] = { update { it.copy(miniCrestEnabled = !enabled) } }
        return actions
    }

    private fun showMenu(show: (JBPopup) -> Unit) {
        if (ownMenuOpen()) return
        val actions = contextActions()
        val popup =
            JBPopupFactory
                .getInstance()
                .createPopupChooserBuilder(actions.keys.toList())
                .setTitle(CodeXPBundle.message("settings.mini.title"))
                .setRequestFocus(true) // The user explicitly opened these keyboard-accessible controls.
                .setItemChosenCallback { actions[it]?.invoke() }
                .createPopup()
        menu = popup
        popup.addListener(
            object : JBPopupListener {
                override fun onClosed(event: LightweightWindowEvent) {
                    scheduleRefresh()
                }
            },
        )
        show(popup)
        diagnostic("controls showing=${popup.isVisible}")
    }

    private fun detach() {
        visibility.reset()
        contextLifetime?.let(Disposer::dispose)
        contextLifetime = null
        pane?.let {
            val old = surface.bounds
            it.remove(surface)
            it.repaint(old.x, old.y, old.width, old.height)
        }
        pane = null
        window = null
    }

    override fun dispose() {
        if (disposed) return
        disposed = true
        lookup?.removeLookupListener(lookupListener)
        lookup = null
        motion.dispose()
        menus.removeChangeListener(menuListener)
        KeyboardFocusManager.getCurrentKeyboardFocusManager().removePropertyChangeListener("focusOwner", focusListener)
        KeyboardFocusManager.getCurrentKeyboardFocusManager().removePropertyChangeListener("focusedWindow", focusListener)
        Toolkit.getDefaultToolkit().removeAWTEventListener(windowListener)
        menu?.cancel()
        onEdt {
            visibility.dispose()
            detach()
        }
    }
}

/** Stay in the IDE's own layer behind its dialogs, never above them or in another project. */
internal fun miniCrestWindowActive(
    owner: Window,
    focused: Window?,
): Boolean {
    if (owner is Frame && owner.extendedState and Frame.ICONIFIED != 0) return false
    if (focused == null) return owner.isActive
    var current: Window? = focused
    var active = false
    while (current != null) {
        active = active || current.isActive
        if (current === owner) return active
        current = current.owner
    }
    return false
}

/** A lookup can be active on each keystroke without ever showing a completion popup. */
internal fun lookupCoversMiniCrest(
    popup: Component?,
    layer: JLayeredPane,
    crest: Rectangle,
): Boolean {
    if (popup == null || !popup.isShowing || popup.width <= 0 || popup.height <= 0) return false
    val parent = popup.parent ?: return false
    if (!SwingUtilities.isDescendingFrom(popup, layer)) {
        val owner = SwingUtilities.getWindowAncestor(layer) ?: return false
        val popupWindow = SwingUtilities.getWindowAncestor(popup) ?: return false
        if (generateSequence(popupWindow) { it.owner }.none { it === owner }) return false
    }
    return crest.intersects(SwingUtilities.convertRectangle(parent, popup.bounds, layer))
}
