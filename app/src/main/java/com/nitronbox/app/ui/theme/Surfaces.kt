package com.nitronbox.app.ui.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp

/** Flat surface roles. There is no translucency: hierarchy comes from fill + hairline borders. */
enum class SurfaceLevel {
    /** Matches the window background; used for large quiet regions. */
    Base,

    /** Subtle gray fill for secondary containers (user bubbles, inputs, code). */
    Muted,

    /** Solid surface with a hairline border; the default card. */
    Raised,

    /** Raised surface with a soft shadow for menus, dialogs, and popovers. */
    Overlay,
}

fun Modifier.nitronSurface(
    level: SurfaceLevel = SurfaceLevel.Raised,
    shape: Shape? = null,
): Modifier = composed {
    val colors = NitronTheme.colors
    val resolvedShape = shape ?: NitronTheme.shapes.medium
    when (level) {
        SurfaceLevel.Base -> this.clip(resolvedShape).background(colors.background)
        SurfaceLevel.Muted -> this.clip(resolvedShape).background(colors.surfaceMuted)
        SurfaceLevel.Raised -> this
            .clip(resolvedShape)
            .background(colors.surface)
            .border(1.dp, colors.border, resolvedShape)
        SurfaceLevel.Overlay -> this
            .shadow(12.dp, resolvedShape, ambientColor = Color.Black.copy(alpha = 0.10f), spotColor = Color.Black.copy(alpha = 0.16f))
            .clip(resolvedShape)
            .background(colors.surface)
            .border(1.dp, colors.border, resolvedShape)
    }
}

/** A 1dp hairline outline with no fill, for dividers-as-borders and ghost controls. */
fun Modifier.hairlineBorder(shape: Shape? = null, color: Color? = null): Modifier = composed {
    val colors = NitronTheme.colors
    this.border(1.dp, color ?: colors.border, shape ?: NitronTheme.shapes.small)
}

/** A single hairline separator line that reads as a border, not a Material divider. */
fun Modifier.hairlineUnderline(color: Color? = null): Modifier = composed {
    val c = color ?: NitronTheme.colors.border
    this.drawBehind {
        drawLine(c, Offset(0f, size.height), Offset(size.width, size.height), 1.dp.toPx())
    }
}

/** Minimal, native press feedback: bounded ripple clipped to the surface's own shape. */
fun Modifier.pressableRipple(
    enabled: Boolean = true,
    shape: Shape? = null,
    onClick: () -> Unit,
): Modifier = composed {
    val resolvedShape = shape ?: NitronTheme.shapes.medium
    this.clip(resolvedShape).clickable(enabled = enabled, onClick = onClick)
}

/** Flat, full-bleed background. Replaces the old ambient gradient/glass backdrop. */
@Composable
fun NitronBackground(
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    Box(modifier = modifier.fillMaxSize().background(NitronTheme.colors.background)) {
        content()
    }
}
