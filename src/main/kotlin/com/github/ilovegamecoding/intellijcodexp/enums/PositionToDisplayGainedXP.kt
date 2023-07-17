package com.github.ilovegamecoding.intellijcodexp.enums

/**
 * Position enum for the gained XP label.
 */
enum class PositionToDisplayGainedXP(val x: Int, val y: Int) {
    TOP(0, -1),
    TOP_LEFT(-1, -1),
    LEFT(-1, 0),
    BOTTOM_LEFT(-1, 1),
    BOTTOM(0, 1),
    BOTTOM_RIGHT(1, 1),
    RIGHT(1, 0),
    TOP_RIGHT(1, -1)
}