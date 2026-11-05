package com.github.ilovegamecoding.intellijcodexp.models

import com.github.ilovegamecoding.intellijcodexp.enums.CodeXPNotificationType
import com.github.ilovegamecoding.intellijcodexp.enums.EffectStyle
import com.github.ilovegamecoding.intellijcodexp.enums.PositionToDisplayGainedXP

/**
 * CodeXPConfiguration class
 *
 * This class is used to store the configuration of the plugin.
 */
data class CodeXPConfiguration(
    /**
     * Notification type.
     */
    var notificationType: String = CodeXPNotificationType.CODEXP.storedValue,
    /**
     * Show a notification when the user levels up.
     */
    var showLevelUpNotification: Boolean = true,
    /**
     * Show a notification when the user completes a challenge.
     */
    var showCompleteChallengeNotification: Boolean = true,
    /**
     * Show a notification when the user gains XP.
     */
    var showGainedXP: Boolean = true,
    /**
     * Gained XP display position.
     */
    var positionToDisplayGainedXP: PositionToDisplayGainedXP = PositionToDisplayGainedXP.TOP_RIGHT,
    /** Stored as text so unknown values from newer versions can safely fall back. */
    var effectStyle: String = EffectStyle.CELEBRATORY.storedValue,
    var reducedMotion: Boolean = false,
    var miniCrestEnabled: Boolean = true,
    var miniCrestSize: Int = DEFAULT_MINI_CREST_SIZE,
    var miniCrestX: Double = 1.0,
    var miniCrestY: Double = 1.0,
    /** Retained for older profiles; mini crests are now always expanded and draggable. */
    var miniCrestPinned: Boolean = false,
    var miniCrestCollapsed: Boolean = false,
    /** Old and default anchors include an inset; dragging saves an anchor relative to the full window. */
    var miniCrestWindowAnchor: Boolean = false,
) {
    fun sanitizeMiniCrest() {
        miniCrestPinned = false
        miniCrestCollapsed = false
        if (miniCrestSize !in MINI_CREST_SIZES) miniCrestSize = DEFAULT_MINI_CREST_SIZE
        miniCrestX = if (miniCrestX.isFinite()) miniCrestX.coerceIn(0.0, 1.0) else 1.0
        miniCrestY = if (miniCrestY.isFinite()) miniCrestY.coerceIn(0.0, 1.0) else 1.0
    }

    companion object {
        internal const val DEFAULT_MINI_CREST_SIZE = 96
        internal val MINI_CREST_SIZES = listOf(DEFAULT_MINI_CREST_SIZE, 128, 164, 256)
    }
}
