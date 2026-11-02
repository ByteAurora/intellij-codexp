package com.github.ilovegamecoding.intellijcodexp.presentation.overlay

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.WindowManager
import com.intellij.ui.scale.JBUIScale
import com.intellij.util.ui.JBUI
import java.awt.Rectangle
import javax.swing.JLayeredPane
import javax.swing.Timer

/** EDT confined. A single elapsed-time clock is active only while effects are visible. */
internal class CodeXPOverlayDialogPresenter(
    private val project: Project,
) {
    private val timeline = AchievementTimeline()
    private val surface = CodeXPOverlayDialog()
    private val timer = Timer(16) { tick() }.apply { isCoalesce = true }
    private var editor: Editor? = null
    private var pane: JLayeredPane? = null
    private var disposed = false

    fun show(
        effect: AchievementEffect,
        target: Editor?,
    ) {
        if (disposed || project.isDisposed || target?.isDisposed == true) return
        if (target != editor) cancel()
        editor = target
        val root =
            WindowManager
                .getInstance()
                .getIdeFrame(project)
                ?.component
                ?.rootPane ?: target?.contentComponent?.rootPane
        val layer = root?.layeredPane ?: return
        if (pane != layer) {
            detach()
            pane = layer
            // Below completion popups; contains(false) lets all pointer input reach the editor.
            layer.add(surface, JLayeredPane.PALETTE_LAYER, 0)
        }
        timeline.enqueue(effect, motionTimeMillis())
        tick()
        if (timeline.isActive) timer.start()
    }

    private fun tick() {
        val layer = pane
        val target = editor
        if (disposed || project.isDisposed || layer == null || target?.isDisposed == true || (target != null && !target.contentComponent.isShowing)) {
            cancel()
            return
        }
        val frame = timeline.frame(motionTimeMillis())
        if (frame == null) {
            cancel()
            return
        }
        val client = Rectangle(0, 0, layer.width, layer.height)
        if (client.width < JBUI.scale(100) || client.height < JBUI.scale(100)) {
            cancel()
            return
        }
        val bounds = achievementBounds(client, frame.effect.options.subtle, JBUIScale.scale(1f).toDouble())
        if (surface.bounds != bounds) {
            val old = surface.bounds
            surface.bounds = bounds
            layer.repaint(old.x, old.y, old.width, old.height)
        }
        if (surface.frame?.effect != frame.effect) {
            surface.accessibleContext.accessibleName =
                frame.effect.let {
                    listOfNotNull(it.challenge?.name, it.challenge?.let { c -> AchievementPainter.xpText(c.reward) }, it.level?.let { l -> "Level ${l.from} → ${l.to}" }).joinToString(". ")
                }
        }
        surface.frame = frame
        surface.isVisible = true
        surface.repaint()
    }

    fun release(released: Editor) {
        if (editor === released) cancel()
    }

    fun cancel() {
        timer.stop()
        timeline.cancel()
        surface.frame = null
        detach()
        editor = null
    }

    private fun detach() {
        pane?.let {
            val old = surface.bounds
            it.remove(surface)
            it.repaint(old.x, old.y, old.width, old.height)
        }
        pane = null
    }

    fun dispose() {
        disposed = true
        cancel()
    }
}

internal fun achievementBounds(
    client: Rectangle,
    subtle: Boolean,
    scale: Double,
): Rectangle {
    val margin = (12 * scale).toInt()
    val width = minOf(((if (subtle) 340 else 520) * scale).toInt(), (client.width - margin * 2).coerceAtLeast(1))
    val height = minOf((390 * scale).toInt(), (client.height * .70 - margin * 2).toInt().coerceAtLeast(1))
    val x = client.x + (client.width - width) / 2
    val y = (client.y + (client.height * .35).toInt() - height / 2).coerceIn(client.y, client.y + client.height - height)
    return Rectangle(x, y, width, height)
}
