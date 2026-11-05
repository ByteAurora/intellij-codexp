package com.github.ilovegamecoding.intellijcodexp.presentation

import java.awt.Graphics2D
import java.awt.RenderingHints

/** Keep fractional edges in motion instead of snapping each stroke to the device pixel grid. */
internal fun Graphics2D.smoothArtwork() {
    setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
    setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE)
    setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR)
}
