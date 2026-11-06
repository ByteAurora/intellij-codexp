package com.github.ilovegamecoding.intellijcodexp.presentation.collection

import com.github.ilovegamecoding.intellijcodexp.domain.collection.CollectionKind
import com.intellij.openapi.Disposable
import com.intellij.util.ui.JBUI
import java.awt.Graphics
import java.awt.Graphics2D
import javax.swing.JPanel

/** Passive surface: the viewport supplies time; renderers own no timers or listeners. */
internal class CollectionArtwork :
    JPanel(),
    Disposable {
    private var entry: CollectionEntry? = null
    private var snapshot: CollectionDisplaySnapshot? = null
    private var disposed = false
    var frame = CollectionMotionFrame()

    init {
        isOpaque = false
        preferredSize = JBUI.size(240, 210)
    }

    fun show(
        entry: CollectionEntry,
        snapshot: CollectionDisplaySnapshot,
    ) {
        this.entry = entry
        this.snapshot = snapshot
        getAccessibleContext().accessibleName = entry.accessibleName
        toolTipText = entry.accessibleName
    }

    override fun paintComponent(graphics: Graphics) {
        val item = entry ?: return
        val state = snapshot ?: return
        if (disposed) return
        paintCollectionArtwork(graphics as Graphics2D, width, height, item, state, frame)
    }

    override fun dispose() {
        disposed = true
        entry = null
        snapshot = null
    }
}

/** Paint into the caller's pass; a cell renderer must not invoke an unattached Swing component's paint. */
internal fun paintCollectionArtwork(
    graphics: Graphics2D,
    width: Int,
    height: Int,
    item: CollectionEntry,
    state: CollectionDisplaySnapshot,
    frame: CollectionMotionFrame,
) {
    // Title lettering and its underline occupy a shorter band than a crest.
    val scale = minOf(width / 240.0, height / if (item.kind == CollectionKind.TITLE) 104.0 else 210.0)
    if (scale <= 0) return
    val g = graphics.create() as Graphics2D
    try {
        g.translate(width / 2.0, height / 2.0)
        g.scale(scale, scale)
        CollectionCrestPainter.paintAnimated(g, item.design, state.displayedLevel, !item.owned, if (state.reducedMotion) CollectionMotionFrame() else frame, state.subtle, item.name.takeIf { item.owned })
    } finally {
        g.dispose()
    }
}
