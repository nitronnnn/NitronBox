package com.nitronbox.app.ui.theme

import androidx.compose.runtime.compositionLocalOf

/**
 * Global background-effect configuration, driven by the Appearance settings:
 * whether panel overlays blur the content beneath, the maximum blur radius,
 * and whether panels themselves are frosted translucent instead of solid.
 */
data class UiFxConfig(
    val blurEnabled: Boolean = true,
    val blurRadius: Float = 18f,
    val blurredPanels: Boolean = true,
)

val LocalUiFx = compositionLocalOf { UiFxConfig() }
