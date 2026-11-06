package com.github.ilovegamecoding.intellijcodexp.presentation.collection

import com.github.ilovegamecoding.intellijcodexp.domain.collection.CrestDesign
import com.github.ilovegamecoding.intellijcodexp.presentation.dashboard.DashboardPalette
import com.github.ilovegamecoding.intellijcodexp.presentation.smoothArtwork
import java.awt.AlphaComposite
import java.awt.BasicStroke
import java.awt.Graphics2D
import java.awt.geom.Arc2D
import java.awt.image.BufferedImage
import kotlin.math.ceil
import kotlin.math.hypot

/** Cached, device-scale edge light; no contour unions or blur kernels on animation frames. */
internal object CrestImpactPainter {
    const val HALF_WIDTH = 132.0
    const val HALF_HEIGHT = 120.0

    private data class Key(
        val design: CrestDesign,
        val dark: Boolean,
        val density: Double,
    )

    private val sprites = LinkedHashMap<Key, BufferedImage>(32, .75f, true)
    private var cachedPixels = 0

    fun paint(
        g: Graphics2D,
        design: CrestDesign,
        progress: Double,
        subtle: Boolean,
    ) {
        val p = progress.coerceIn(0.0, 1.0)
        if (p <= 0 || p >= 1) return
        val density = (ceil(hypot(g.transform.scaleX, g.transform.shearX) * 4) / 4).coerceIn(.5, 4.0)
        val sprite = sprite(design, density)
        val attack = (p / .18).coerceAtMost(1.0)
        val fade = 1 - ((p - .28) / .72).coerceIn(0.0, 1.0)
        val opacity = (attack * fade * fade * if (subtle) .72 else 1.0).toFloat()
        val copy = g.create() as Graphics2D
        try {
            copy.smoothArtwork()
            // The light travels around both sides of the actual outline, then leaves a soft echo.
            val reveal = (p / .52).coerceAtMost(1.0)
            val angle = 360 * (1 - (1 - reveal) * (1 - reveal))
            copy.clip(Arc2D.Double(-180.0, -180.0, 360.0, 360.0, 90 - angle / 2, angle, Arc2D.PIE))
            copy.composite = AlphaComposite.SrcOver.derive(opacity)
            copy.drawImage(sprite, -132, -120, 264, 240, null)
            if (p > .28) {
                val expansion = 1 + .035 * (p - .28) / .72
                copy.scale(expansion, expansion)
                copy.composite = AlphaComposite.SrcOver.derive(opacity * .25f)
                copy.drawImage(sprite, -132, -120, 264, 240, null)
            }
        } finally {
            copy.dispose()
        }
    }

    internal fun sprite(
        design: CrestDesign,
        density: Double,
    ): BufferedImage =
        synchronized(sprites) {
            val key = Key(design, DashboardPalette.background.red < 128, density)
            sprites
                .getOrPut(key) {
                    val image = BufferedImage(ceil(264 * density).toInt(), ceil(240 * density).toInt(), BufferedImage.TYPE_INT_ARGB_PRE)
                    val g = image.createGraphics()
                    try {
                        g.smoothArtwork()
                        g.scale(image.width / 264.0, image.height / 240.0)
                        g.translate(HALF_WIDTH, HALF_HEIGHT)
                        val edge = CollectionCrestPainter.impactOutline(design)
                        val colors = crestColors(design, key.dark)
                        for ((width, alpha) in listOf(10f to .08f, 5f to .23f, 1.5f to .95f)) {
                            g.color = if (width > 2) colors.second else colors.first
                            g.composite = AlphaComposite.SrcOver.derive(alpha)
                            g.stroke = BasicStroke(width, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND)
                            g.draw(edge)
                        }
                    } finally {
                        g.dispose()
                    }
                    cachedPixels += image.width * image.height
                    image
                }.also {
                    while (cachedPixels > 4_000_000 && sprites.size > 1) {
                        val oldest = sprites.entries.iterator()
                        val removed = oldest.next().value
                        cachedPixels -= removed.width * removed.height
                        oldest.remove()
                    }
                }
        }
}
