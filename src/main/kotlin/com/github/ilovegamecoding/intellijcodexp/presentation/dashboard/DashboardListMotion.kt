package com.github.ilovegamecoding.intellijcodexp.presentation.dashboard

import com.intellij.openapi.Disposable
import java.awt.Rectangle
import javax.swing.Timer
import kotlin.math.abs

/** One coalescing timer: 16ms while cards move, 33ms for highlights, stopped when idle. */
internal class DashboardListMotion(
    val rowHeight: Int,
    private val clock: () -> Long = { System.nanoTime() / 1_000_000 },
    private val repaintFrame: () -> Unit,
) : Disposable {
    private data class Move(
        val from: Double,
        val to: Double,
    )

    private data class Highlight(
        val start: Long,
        var lastEvent: Long,
    )

    private val timer = Timer(33) { tick(clock()) }.apply { isCoalesce = true }
    private var indices = emptyMap<String, Int>()
    private val held = mutableMapOf<String, Double>()
    private var moves = emptyMap<String, Move>()
    private val highlights = linkedMapOf<String, Highlight>()
    private val lastStarts = linkedMapOf<String, Long>()
    private var pendingAt: Long? = null
    private var startedAt = 0L
    private var visible = false
    private var reduced = false
    private var subtle = false
    private var disposed = false
    var order = emptyList<String>()
        private set
    var movementStarts = 0
        private set
    val isRunning: Boolean get() = timer.isRunning
    internal val frameDelayMs: Int get() = timer.delay
    internal val firstFrameDelayMs: Int get() = timer.initialDelay
    val hasMovement: Boolean get() = moves.isNotEmpty() || pendingAt != null
    val movingIds: Set<String> get() = held.keys + moves.keys

    fun configure(
        subtle: Boolean,
        reduced: Boolean,
    ) {
        this.subtle = subtle
        this.reduced = reduced
        if (reduced && hasMovement) {
            finishMovement()
            repaintFrame()
        }
        syncTimer()
    }

    fun visibilityChanged(showing: Boolean) {
        if (disposed) return
        visible = showing
        if (!showing) {
            finishMovement()
            highlights.clear()
            lastStarts.clear()
            repaintFrame()
        }
        syncTimer()
    }

    fun update(
        newOrder: List<String>,
        changedIds: Set<String>,
        viewport: Rectangle,
    ) {
        if (disposed) return
        val now = clock()
        val different = newOrder != order
        if (different) {
            val oldPositions = (visibleIds(viewport) + movingIds).associateWith { y(it, now) }
            val oldIndices = indices
            order = newOrder
            indices = newOrder.withIndex().associate { it.value to it.index }
            if (visible && !reduced && changedIds.isNotEmpty() && oldIndices.isNotEmpty() && viewport.y <= 2) {
                val wanted = (oldPositions.keys + visibleIds(viewport)).filter { it in indices }.take(128)
                held.clear()
                wanted.forEach { id -> held[id] = oldPositions[id] ?: oldIndices[id]?.let { it * rowHeight.toDouble() } ?: -rowHeight.toDouble() }
                // First-event deadline: subsequent keystrokes cannot postpone this indefinitely.
                if (pendingAt == null) pendingAt = now + 160
            } else {
                finishMovement()
            }
        }
        if (visible) {
            changedIds.filter { it in indices }.forEach { id ->
                highlights[id]?.lastEvent = now
                if (id !in highlights) {
                    val previous = lastStarts[id]
                    val start = if (previous != null && now - previous < 800) previous else now
                    highlights[id] = Highlight(start, now)
                    lastStarts[id] = start
                }
            }
            while (highlights.size > 128) highlights.remove(highlights.keys.first())
            while (lastStarts.size > 128) lastStarts.remove(lastStarts.keys.first())
        }
        highlights.keys.retainAll(indices.keys)
        syncTimer()
        repaintFrame()
    }

    fun y(
        id: String,
        now: Long = clock(),
    ): Double {
        moves[id]?.let { move ->
            val t = ((now - startedAt) / 270.0).coerceIn(0.0, 1.0)
            val eased = 1 - (1 - t) * (1 - t) * (1 - t)
            return move.from + (move.to - move.from) * eased
        }
        return held[id] ?: (indices[id] ?: 0) * rowHeight.toDouble()
    }

    fun highlight(
        id: String,
        now: Long = clock(),
    ): Float {
        val pulse = highlights[id] ?: return 0f
        val elapsed = now - pulse.lastEvent
        val maximum = if (subtle) .28 else .46
        if (reduced) return if (elapsed < 600) maximum.toFloat() else 0f
        val rise = ((now - pulse.start) / 120.0).coerceIn(0.0, 1.0)
        val fade = (1 - (elapsed - 120) / 500.0).coerceIn(0.0, 1.0)
        return (maximum * rise * fade).toFloat()
    }

    fun visibleIds(viewport: Rectangle): List<String> {
        if (order.isEmpty()) return emptyList()
        val first = (viewport.y / rowHeight - 1).coerceIn(0, order.lastIndex)
        val count = (viewport.height / rowHeight + 4).coerceIn(4, 64)
        return order.subList(first, minOf(order.size, first + count))
    }

    internal fun tick(now: Long) {
        if (!visible || disposed) return
        if (pendingAt?.let { now >= it } == true) {
            val candidates = held.keys + moves.keys
            val next =
                candidates
                    .filter { it in indices }
                    .associateWith { id -> Move(y(id, now), indices.getValue(id) * rowHeight.toDouble()) }
                    .filterValues { abs(it.to - it.from) > .1 }
            moves = next
            held.clear()
            pendingAt = null
            startedAt = now
            if (moves.isNotEmpty()) movementStarts++
        } else if (moves.isNotEmpty() && now - startedAt >= 270) {
            if (pendingAt != null) moves.forEach { (id, move) -> held[id] = move.to }
            moves = emptyMap()
        }
        val removed = highlights.entries.removeIf { now - it.value.lastEvent >= if (reduced) 600 else 620 }
        if (!reduced || removed || hasMovement) repaintFrame()
        syncTimer()
    }

    fun settle() {
        finishMovement()
        syncTimer()
        repaintFrame()
    }

    private fun finishMovement() {
        pendingAt = null
        held.clear()
        moves = emptyMap()
    }

    private fun syncTimer() {
        val interval = if (!reduced && moves.isNotEmpty()) 16 else 33
        val changed = timer.delay != interval || timer.initialDelay != interval
        timer.delay = interval
        timer.initialDelay = interval
        if (disposed || !visible || (!hasMovement && highlights.isEmpty())) {
            timer.stop()
        } else if (changed && timer.isRunning) {
            // setDelay alone leaves the already scheduled 33ms wakeup in place.
            timer.restart()
        } else if (!timer.isRunning) {
            timer.start()
        }
    }

    override fun dispose() {
        disposed = true
        finishMovement()
        highlights.clear()
        lastStarts.clear()
        syncTimer()
    }
}
