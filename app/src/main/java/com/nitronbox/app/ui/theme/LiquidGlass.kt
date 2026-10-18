package com.nitronbox.app.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy

/**
 * Backdrop recording the whole chat content (wallpaper + messages). Panels floating above the
 * content (header, composer, drawers, dialogs) sample it to render the Liquid Glass material.
 * Null means the flat surface style is active and no glass is drawn.
 */
val LocalLiquidContentBackdrop = compositionLocalOf<Backdrop?> { null }

/**
 * Backdrop recording only the wallpaper. Surfaces that live inside the content itself (message
 * bubbles) cannot sample [LocalLiquidContentBackdrop] — that would record a layer into itself —
 * so they refract the static wallpaper instead.
 */
val LocalLiquidWallpaperBackdrop = compositionLocalOf<Backdrop?> { null }

/** True when the Liquid Glass style is active and a content backdrop is provided. */
val liquidGlassActive: Boolean
    @Composable @ReadOnlyComposable get() = LocalLiquidContentBackdrop.current != null

/**
 * A Liquid Glass panel: refracts [backdrop] through vibrancy + blur + a rounded-rect lens,
 * then lays a translucent tint on top for readability. Effects are RenderEffect-based and
 * degrade gracefully: blur needs Android 12+, the lens needs Android 13+; older devices keep
 * the tinted glass look without distortion. Content is clipped to [shape] by the backdrop layer.
 */
fun Modifier.liquidGlassPanel(backdrop: Backdrop, shape: Shape): Modifier = composed {
    val colors = NitronTheme.colors
    val tint = colors.background.copy(alpha = if (colors.isDark) 0.30f else 0.44f)
    drawBackdrop(
        backdrop = backdrop,
        shape = { shape },
        effects = {
            vibrancy()
            blur(10.dp.toPx())
            lens(18.dp.toPx(), 52.dp.toPx(), depthEffect = true)
        },
        onDrawSurface = { drawRect(tint) },
    )
}

/**
 * A lighter glass for message bubbles: samples the wallpaper backdrop with a soft lens and
 * without the floating shadow, so long lists stay quiet and cheap to draw.
 */
fun Modifier.liquidGlassBubble(backdrop: Backdrop, shape: Shape): Modifier = composed {
    val colors = NitronTheme.colors
    val tint = colors.background.copy(alpha = if (colors.isDark) 0.34f else 0.50f)
    drawBackdrop(
        backdrop = backdrop,
        shape = { shape },
        shadow = null,
        effects = {
            vibrancy()
            blur(8.dp.toPx())
            lens(14.dp.toPx(), 36.dp.toPx(), depthEffect = true)
        },
        onDrawSurface = { drawRect(tint) },
    )
}

/**
 * A denser Liquid Glass for content cards (settings, lists): same refraction, but a stronger
 * tint so gray secondary text stays readable over busy wallpapers.
 */
fun Modifier.liquidGlassCard(backdrop: Backdrop, shape: Shape): Modifier = composed {
    val colors = NitronTheme.colors
    val tint = colors.background.copy(alpha = if (colors.isDark) 0.52f else 0.62f)
    drawBackdrop(
        backdrop = backdrop,
        shape = { shape },
        effects = {
            vibrancy()
            blur(10.dp.toPx())
            lens(14.dp.toPx(), 40.dp.toPx(), depthEffect = true)
        },
        onDrawSurface = { drawRect(tint) },
    )
}

/**
 * Flat surface normally, Liquid Glass when a content backdrop is available. Use ONLY for
 * surfaces that are NOT rendered inside the recorded content layer (settings cards, drawer
 * rows, sheets): anything inside the layer must keep sampling the wallpaper backdrop instead.
 * Uses the denser card tint so text on the card keeps its contrast.
 */
@Composable
fun Modifier.glassOrSurface(level: SurfaceLevel, shape: Shape): Modifier {
    val backdrop = LocalLiquidContentBackdrop.current
    return if (backdrop != null) {
        liquidGlassCard(backdrop, shape).pointerInput(Unit) {}
    } else {
        nitronSurface(level, shape)
    }
}

/**
 * Secondary text color for Liquid Glass: over translucent glass the palette's gray
 * textSecondary dissolves into the wallpaper, so it is pulled towards textPrimary.
 * Identity in the flat style.
 */
val liquidTextSecondary: Color
    @Composable @ReadOnlyComposable
    get() = if (liquidGlassActive) NitronTheme.colors.textPrimary.copy(alpha = 0.82f)
    else NitronTheme.colors.textSecondary

/** Same rescue for tertiary captions, hints and timestamps on glass. */
val liquidTextTertiary: Color
    @Composable @ReadOnlyComposable
    get() = if (liquidGlassActive) NitronTheme.colors.textPrimary.copy(alpha = 0.62f)
    else NitronTheme.colors.textTertiary

/** Hairline divider color that stays visible on glass. */
val liquidDividerColor: Color
    @Composable @ReadOnlyComposable
    get() = if (liquidGlassActive) NitronTheme.colors.textPrimary.copy(alpha = 0.16f)
    else NitronTheme.colors.border

/** Liquid Glass background for a DropdownMenu when the style is active; no-op otherwise. */
@Composable
fun Modifier.glassMenu(): Modifier {
    val backdrop = LocalLiquidContentBackdrop.current
    return if (backdrop != null) liquidGlassPanel(backdrop, NitronTheme.shapes.medium) else this
}

/** Transparent menu container in Liquid Glass mode (the glass draws the surface instead). */
@Composable
fun glassMenuColor(): Color =
    if (LocalLiquidContentBackdrop.current != null) Color.Transparent
    else androidx.compose.material3.MaterialTheme.colorScheme.surfaceContainer
