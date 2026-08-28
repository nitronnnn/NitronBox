package com.nitronbox.app.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import kotlinx.coroutines.coroutineScope
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.setValue
import kotlinx.coroutines.launch
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import dev.chrisbanes.haze.HazeTint
import dev.chrisbanes.haze.hazeEffect
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.nitronbox.app.ui.theme.LocalHazeState
import com.nitronbox.app.ui.theme.LocalUiFx
import com.nitronbox.app.ui.theme.NitronTheme

/** Fading black scrim that swallows taps to dismiss the overlay beneath it. */
@Composable
fun OverlayScrim(
    visible: Boolean,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    AnimatedVisibility(visible, enter = fadeIn(tween(180)), exit = fadeOut(tween(180))) {
        Box(
            modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.5f))
                .pointerInput(onDismiss) { detectTapGestures { onDismiss() } },
        )
    }
}

/**
 * Panel surface material: real backdrop blur (Haze) when the screen provides a haze state
 * and frosted panels are enabled; a solid surface otherwise.
 */
@Composable
fun Modifier.frostPanel(shape: androidx.compose.ui.graphics.Shape): Modifier {
    val fx = LocalUiFx.current
    val state = LocalHazeState.current
    val surfaceColor = NitronTheme.colors.background
    val tintColor = surfaceColor.copy(alpha = 0.78f)
    val radius = fx.blurRadius.dp
    val base = if (fx.blurredPanels && fx.blurEnabled && state != null) {
        Modifier.hazeEffect(state) {
            backgroundColor = surfaceColor
            tints = listOf(HazeTint(tintColor))
            blurRadius = radius
            noiseFactor = 0f
        }
    } else {
        Modifier.background(NitronTheme.colors.background)
    }
    return clip(shape).then(base).pointerInput(Unit) {}
}

/**
 * Custom bottom panel in the app's own window: slides up with an enter animation even when
 * freshly composed, rounded top corners, scrim dismiss. Being in-window lets the screen blur
 * everything beneath it; the panel itself can be frosted translucent (Appearance settings).
 */
@Composable
fun NitronBottomPanel(
    visible: Boolean,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    maxHeightFraction: Float = 0.86f,
    content: @Composable BoxScope.() -> Unit,
) {
    OverlayScrim(visible, onDismiss)
    // MutableTransitionState(false) makes the enter animation play on first composition.
    val appear = remember { MutableTransitionState(false) }.apply { targetState = true }
    AnimatedVisibility(
        visibleState = appear,
        enter = slideInVertically(tween(280)) { it } + fadeIn(tween(200)),
        exit = slideOutVertically(tween(220)) { it } + fadeOut(tween(180)),
        modifier = modifier,
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .heightIn(max = 900.dp * maxHeightFraction)
                .imePadding()
                .frostPanel(RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)),
        ) {
            content()
        }
    }
}

/** Custom centered dialog card: dark rounded surface with scale+fade, matching the app style. */
@Composable
fun NitronCenterDialog(
    visible: Boolean,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    OverlayScrim(visible, onDismiss)
    val appear = remember { MutableTransitionState(false) }.apply { targetState = true }
    AnimatedVisibility(
        visibleState = appear,
        enter = scaleIn(tween(200), initialScale = 0.92f) + fadeIn(tween(160)),
        exit = scaleOut(tween(160), targetScale = 0.94f) + fadeOut(tween(140)),
        modifier = modifier,
    ) {
        Box(
            Modifier
                .padding(horizontal = 32.dp)
                .imePadding()
                .frostPanel(NitronTheme.shapes.large),
        ) {
            content()
        }
    }
}

/**
 * Tappable brand mark: each tap spins the logo a full turn around its center while a
 * motion-blur approximation peaks at the start of the spin and eases out with it.
 */
@Composable
fun SpinningLogo(
    size: Dp,
    modifier: Modifier = Modifier,
) {
    val rotation = remember { Animatable(0f) }
    val motionBlur = remember { Animatable(0f) }
    var spinning by remember { mutableStateOf(false) }

    LaunchedEffect(spinning) {
        if (spinning) {
            motionBlur.snapTo(2.8f)
            coroutineScope {
                launch { motionBlur.animateTo(0f, animationSpec = tween(850)) }
                rotation.animateTo(rotation.value + 360f, animationSpec = tween(900))
            }
            spinning = false
        }
    }
    Box(
        modifier
            .size(size)
            .clickable { if (!spinning) spinning = true }
            .blur(motionBlur.value.dp)
            .graphicsLayer { rotationZ = rotation.value },
        contentAlignment = Alignment.Center,
    ) {
        NitronLogo(modifier = Modifier.size(size))
    }
}
