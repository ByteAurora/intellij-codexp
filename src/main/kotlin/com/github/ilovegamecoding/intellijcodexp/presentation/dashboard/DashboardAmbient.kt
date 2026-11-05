package com.github.ilovegamecoding.intellijcodexp.presentation.dashboard

import com.github.ilovegamecoding.intellijcodexp.presentation.collection.CollectionMotion
import com.github.ilovegamecoding.intellijcodexp.presentation.collection.CollectionMotionFrame
import com.intellij.openapi.util.SystemInfo
import com.intellij.ui.mac.foundation.Foundation
import java.awt.GraphicsEnvironment

/** The dashboard uses the collection clock and elapsed time, repainting only its crest. */
internal class DashboardAmbient(
    private val crest: DashboardCrest,
    osReducedMotion: () -> Boolean = ::systemReducedMotion,
    now: () -> Long = System::nanoTime,
    automaticClock: Boolean = true,
    private val reducedChanged: (Boolean) -> Unit = {},
) {
    private var visible = false
    private var reduced = false
    private var osReduced = false
    private var disposed = false
    private var configured = false
    private val motion =
        CollectionMotion(
            ::repaintCrest,
            {
                osReducedMotion().also {
                    osReduced = it
                    reducedChanged(reduced || osReduced)
                }
            },
            now,
            automaticClock,
        )
    val isRunning: Boolean get() = motion.isRunning

    fun configure(
        subtle: Boolean,
        reducedMotion: Boolean,
    ) {
        if (disposed || (configured && crest.subtle == subtle && reduced == reducedMotion)) return
        configured = true
        crest.subtle = subtle
        reduced = reducedMotion
        motion.configure(reducedMotion)
        reducedChanged(reduced || osReduced)
        repaintCrest()
    }

    fun visibilityChanged(showing: Boolean) {
        if (disposed) return
        visible = showing
        // The dashboard stays animated while visible, even when its project window loses focus.
        motion.visibilityChanged(showing, windowActive = true, hasOwnedInViewport = true)
        repaintCrest()
    }

    private fun repaintCrest() {
        crest.animationRunning = motion.isRunning
        if (motion.isRunning) {
            crest.motionFrame = motion.frame("dashboard", true)
        } else if (reduced || osReduced) {
            crest.motionFrame = CollectionMotionFrame()
        }
        // A hidden dashboard retains its last pose without accumulating paused wall time.
        if (visible) crest.repaint()
    }

    /** Test hook retains the dashboard's existing millisecond time unit. */
    internal fun tick(now: Long) = motion.tick(now * 1_000_000)

    fun dispose() {
        disposed = true
        motion.dispose()
        crest.animationRunning = false
    }
}

/** macOS supplies this preference via NSWorkspace. Other systems use the explicit CodeXP option. */
internal fun systemReducedMotion(): Boolean {
    if (!SystemInfo.isMac || GraphicsEnvironment.isHeadless()) return false
    return try {
        Foundation.invoke(Foundation.invoke("NSWorkspace", "sharedWorkspace"), "accessibilityDisplayShouldReduceMotion").booleanValue()
    } catch (_: LinkageError) {
        true // A missing native bridge must not force motion on an accessibility-sensitive surface.
    } catch (_: RuntimeException) {
        true
    }
}
