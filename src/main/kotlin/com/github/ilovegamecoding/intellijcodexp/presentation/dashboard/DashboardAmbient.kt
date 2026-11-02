package com.github.ilovegamecoding.intellijcodexp.presentation.dashboard

import com.intellij.openapi.util.SystemInfo
import com.intellij.ui.mac.foundation.Foundation
import java.awt.GraphicsEnvironment
import javax.swing.Timer

/** Ambient motion is capped at 20 FPS and repaints only the crest decoration. */
internal class DashboardAmbient(
    private val crest: DashboardCrest,
    private val osReducedMotion: () -> Boolean = ::systemReducedMotion,
    private val reducedChanged: (Boolean) -> Unit = {},
) {
    private val timer = Timer(50) { tick(System.nanoTime() / 1_000_000) }.apply { isCoalesce = true }
    private var visible = false
    private var activeWindow = false
    private var reduced = false
    private var osReduced = false
    private var disposed = false
    private var configured = false
    private var nextPreferenceRead = 0L
    val isRunning: Boolean get() = timer.isRunning

    fun configure(
        subtle: Boolean,
        reducedMotion: Boolean,
    ) {
        if (configured && crest.subtle == subtle && reduced == reducedMotion) return
        configured = true
        crest.subtle = subtle
        reduced = reducedMotion
        refreshPreferences()
    }

    fun visibilityChanged(
        showing: Boolean,
        windowActive: Boolean,
    ) {
        visible = showing
        activeWindow = windowActive
        refreshPreferences()
    }

    private fun refreshPreferences() {
        if (disposed) return
        osReduced = osReducedMotion()
        reducedChanged(reduced || osReduced)
        if (visible && activeWindow && !reduced && !osReduced) {
            timer.start()
        } else {
            timer.stop()
            crest.ambientPhase = 0.0
        }
        if (visible) crest.repaint()
    }

    internal fun tick(now: Long) {
        if (!timer.isRunning || disposed) return
        if (now >= nextPreferenceRead) {
            nextPreferenceRead = now + 2000
            refreshPreferences()
            if (!timer.isRunning) return
        }
        val period = if (crest.subtle) 42000 else 28000
        crest.ambientPhase = (now % period).toDouble() / period
        crest.repaint()
    }

    fun dispose() {
        disposed = true
        timer.stop()
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
