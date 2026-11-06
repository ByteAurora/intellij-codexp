package com.github.ilovegamecoding.intellijcodexp.presentation.collection

import com.intellij.openapi.Disposable
import javax.swing.Timer

/** EDT-only visibility transition. Reversals continue from the current opacity, not an endpoint. */
internal class MiniCrestVisibility(
    private val render: (opacity: Float, showing: Boolean, interactive: Boolean) -> Unit,
    private val clock: () -> Long = System::nanoTime,
) : Disposable {
    var targetVisible = false
        private set
    var opacity = 0f
        private set
    var dimmed = false
        private set
    private var targetOpacity = 0f
    private var from = 0f
    private var started = 0L
    private var disposed = false
    private val timer = Timer(12) { advance(clock()) }.apply { isCoalesce = true }
    internal val isRunning: Boolean get() = timer.isRunning

    fun setVisible(
        visible: Boolean,
        reducedMotion: Boolean,
        dimmed: Boolean = false,
    ) {
        if (disposed) return
        val nextOpacity =
            if (!visible) {
                0f
            } else if (dimmed) {
                .32f
            } else {
                1f
            }
        if (visible == targetVisible && nextOpacity == targetOpacity) {
            if (reducedMotion && timer.isRunning) finish()
            return
        }
        if (timer.isRunning) advance(clock())
        from = opacity
        targetVisible = visible
        this.dimmed = visible && dimmed
        targetOpacity = nextOpacity
        started = clock()
        if (reducedMotion) {
            finish()
        } else {
            publish()
            timer.start()
        }
    }

    internal fun advance(now: Long) {
        if (disposed || !timer.isRunning) return
        val duration = if (targetVisible) 240_000_000.0 else 200_000_000.0
        val t = ((now - started) / duration).coerceIn(0.0, 1.0).toFloat()
        if (t >= 1f) {
            finish()
        } else {
            val eased = t * t * (3 - 2 * t)
            opacity = from + (targetOpacity - from) * eased
            publish()
        }
    }

    private fun finish() {
        timer.stop()
        opacity = targetOpacity
        publish()
    }

    private fun publish() = render(opacity, targetVisible || opacity > 0f, targetVisible)

    fun reset() {
        timer.stop()
        targetVisible = false
        targetOpacity = 0f
        dimmed = false
        opacity = 0f
        publish()
    }

    override fun dispose() {
        reset()
        disposed = true
    }
}
