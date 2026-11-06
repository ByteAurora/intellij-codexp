package com.github.ilovegamecoding.intellijcodexp.presentation.collection

import com.github.ilovegamecoding.intellijcodexp.presentation.dashboard.systemReducedMotion
import com.intellij.openapi.Disposable
import com.intellij.openapi.diagnostic.Logger
import javax.swing.Timer
import kotlin.math.exp

/** One EDT clock for visible collection windows and dashboard crests. Renderers own no timers. */
internal object CollectionAnimationClock {
    // Leave room for EDT dispatch and coalesced painting; 16ms wakeups measured only 52fps on macOS.
    const val FRAME_MS = 12
    private val clients = linkedSetOf<CollectionMotion>()
    private val timer = Timer(FRAME_MS) { clients.toTypedArray().forEach { it.tick(System.nanoTime()) } }.apply { isCoalesce = true }

    fun subscribe(client: CollectionMotion) {
        clients.add(client)
        if (!timer.isRunning) timer.start()
    }

    fun unsubscribe(client: CollectionMotion) {
        clients.remove(client)
        if (clients.isEmpty()) timer.stop()
    }

    internal val subscriberCount: Int get() = clients.size
}

internal data class CollectionMotionFrame(
    val seconds: Double = 0.0,
    val emphasis: Double = 0.0,
    val animated: Boolean = false,
)

/** Time, focus and accessibility policy are independent of painting and deterministic in tests. */
internal class CollectionMotion(
    private val repaintVisible: () -> Unit,
    private val osReducedMotion: () -> Boolean = ::systemReducedMotion,
    private val now: () -> Long = System::nanoTime,
    private val automaticClock: Boolean = true,
) : Disposable {
    private var showing = false
    private var active = false
    private var visibleOwned = false
    private var reduced = false
    private var systemReduced = false
    private var preferenceDeadline = 0L
    private var previousTick = 0L
    private var elapsed = 0.0
    private var accentId: String? = null
    private var accentAt = Double.NEGATIVE_INFINITY
    private var disposed = false
    var isRunning = false
        private set

    fun configure(reducedMotion: Boolean) {
        reduced = reducedMotion
        refresh()
    }

    fun visibilityChanged(
        visible: Boolean,
        windowActive: Boolean,
        hasOwnedInViewport: Boolean,
    ) {
        val activated = !showing || !active
        showing = visible
        active = windowActive
        visibleOwned = hasOwnedInViewport
        if (activated) preferenceDeadline = 0
        refresh()
    }

    private fun refresh() {
        if (disposed) return
        val time = now()
        if (showing && active && time >= preferenceDeadline) {
            systemReduced = osReducedMotion()
            preferenceDeadline = time + 2_000_000_000L
        }
        val running = showing && active && visibleOwned && !reduced && !systemReduced
        if (isRunning == running) return
        isRunning = running
        previousTick = if (running) time else 0
        if (automaticClock) {
            if (running) CollectionAnimationClock.subscribe(this) else CollectionAnimationClock.unsubscribe(this)
        }
        if (!running) accentId = null
        repaintVisible()
    }

    fun emphasize(
        id: String,
        owned: Boolean,
    ): Boolean {
        if (!isRunning || !owned || disposed) return false
        accentId = id
        accentAt = elapsed
        repaintVisible()
        return true
    }

    fun frame(
        id: String,
        owned: Boolean,
    ): CollectionMotionFrame {
        if (!isRunning || !owned) return CollectionMotionFrame()
        val age = elapsed - accentAt
        return CollectionMotionFrame(elapsed, if (id == accentId && age in 0.0..2.6) exp(-age * 1.6) else 0.0, true)
    }

    internal fun tick(time: Long) {
        if (!isRunning || disposed) return
        refresh()
        if (!isRunning) return
        elapsed += (time - previousTick).coerceAtLeast(0) / 1_000_000_000.0
        previousTick = time
        repaintVisible()
    }

    override fun dispose() {
        if (disposed) return
        disposed = true
        isRunning = false
        accentId = null
        CollectionAnimationClock.unsubscribe(this)
    }
}

/** Opt-in RC measurement of completed Swing paints, not a claim about monitor presentation FPS. */
internal class CollectionFrameMetrics(
    private val logPrefix: String = "COLLECTION_FRAMES",
) {
    private val enabled = java.lang.Boolean.getBoolean("codexp.collection.measure")
    private val intervals = DoubleArray(240)
    private val paints = DoubleArray(240)
    private var previous = 0L
    private var count = 0

    fun pause() {
        previous = 0
        count = 0
    }

    fun painted(
        start: Long,
        end: Long,
        width: Int,
        height: Int,
        ownedVisible: Int,
        scale: Double,
        refreshRate: Int,
    ) {
        if (!enabled) return
        if (previous != 0L) {
            intervals[count] = (start - previous) / 1_000_000.0
            paints[count] = (end - start) / 1_000_000.0
            count++
        }
        previous = start
        if (count != intervals.size) return
        val sortedIntervals = intervals.sorted()
        val sortedPaints = paints.sorted()
        val fps = 1000.0 / intervals.average()
        val dropped = intervals.count { it > 25.0 }
        Logger.getInstance(CollectionFrameMetrics::class.java).info(
            "$logPrefix samples=$count fps=%.2f intervalP50=%.3f intervalP95=%.3f paintP50=%.3f paintP95=%.3f over25ms=$dropped viewport=${width}x$height ownedVisible=$ownedVisible scale=$scale monitorHz=$refreshRate".format(
                java.util.Locale.ROOT,
                fps,
                sortedIntervals[120],
                sortedIntervals[228],
                sortedPaints[120],
                sortedPaints[228],
            ),
        )
        count = 0
    }
}
