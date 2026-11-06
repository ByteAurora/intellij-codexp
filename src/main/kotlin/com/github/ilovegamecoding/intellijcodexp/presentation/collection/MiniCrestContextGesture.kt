package com.github.ilovegamecoding.intellijcodexp.presentation.collection

import com.intellij.openapi.util.SystemInfo
import java.awt.Point
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.SwingUtilities

/** Capture context clicks before the editor, without taking normal clicks or requesting focus. */
internal class MiniCrestContextGesture(
    private val hit: (MouseEvent) -> Point?,
    private val open: (Point) -> Unit,
) : MouseAdapter() {
    private var captured = false

    override fun mousePressed(event: MouseEvent) {
        captured = !event.isConsumed && isContextClick(event) && hit(event) != null
        if (captured) event.consume()
    }

    override fun mouseReleased(event: MouseEvent) {
        if (!captured) return
        captured = false
        event.consume()
        hit(event)?.let(open)
    }

    private fun isContextClick(event: MouseEvent): Boolean =
        event.isPopupTrigger || SwingUtilities.isRightMouseButton(event) ||
            (SystemInfo.isMac && event.isControlDown && SwingUtilities.isLeftMouseButton(event))
}
