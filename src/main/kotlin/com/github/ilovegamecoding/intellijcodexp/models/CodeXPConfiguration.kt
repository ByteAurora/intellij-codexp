package com.github.ilovegamecoding.intellijcodexp.models

import com.github.ilovegamecoding.intellijcodexp.enums.PositionToDisplayGainedXP

/**
 * CodeXPConfiguration class
 *
 * This class is used to store the configuration of the plugin.
 */
data class CodeXPConfiguration(
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
    var positionToDisplayGainedXP: PositionToDisplayGainedXP = PositionToDisplayGainedXP.TOP_RIGHT
)