package com.nitronbox.app.ui.theme

import dev.chrisbanes.haze.HazeState

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
    val panelBlurRadius: Float = 24f,
)

val LocalUiFx = compositionLocalOf { UiFxConfig() }

/** Per-screen haze state; screens that blur their content provide it here. */
val LocalHazeState = compositionLocalOf<HazeState?> { null }
