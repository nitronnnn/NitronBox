package com.nitronbox.app.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
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
 * Custom bottom sheet in the app's own window: slides up, rounded top corners, taps on the
 * scrim dismiss. Being in-window lets the screen blur everything beneath it.
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
    AnimatedVisibility(
        visible = visible,
        enter = slideInVertically(tween(260)) { it } + fadeIn(tween(200)),
        exit = slideOutVertically(tween(220)) { it } + fadeOut(tween(180)),
        modifier = modifier,
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .heightIn(max = 900.dp * maxHeightFraction)
                .clip(RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
                .background(NitronTheme.colors.background)
                .pointerInput(Unit) {},
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
    AnimatedVisibility(
        visible = visible,
        enter = scaleIn(tween(200), initialScale = 0.92f) + fadeIn(tween(160)),
        exit = scaleOut(tween(160), targetScale = 0.94f) + fadeOut(tween(140)),
        modifier = modifier,
    ) {
        Box(
            Modifier
                .padding(horizontal = 32.dp)
                .clip(NitronTheme.shapes.large)
                .background(NitronTheme.colors.surface)
                .pointerInput(Unit) {},
        ) {
            content()
        }
    }
}

/**
 * Tappable brand mark: each tap spins the logo a full turn around its center with a
 * motion-blur approximation that fades as the spin settles.
 */
@Composable
fun SpinningLogo(
    size: Dp,
    modifier: Modifier = Modifier,
) {
    val rotation = remember { Animatable(0f) }
    var spinning by remember { mutableStateOf(false) }
    val blurTarget = if (spinning) 2.6.dp else 0.dp
    var blurValue by remember { mutableFloatStateOf(0f) }
    val blurDp = blurValue.dp
    LaunchedEffect(spinning) {
        if (spinning) {
            // Motion blur: strongest at the start of the spin, easing out with it.
            val blurAnim = Animatable(2.6f)
            launchBlurEasing(blurAnim) { blurValue = it }
            rotation.animateTo(rotation.value + 360f, animationSpec = tween(900))
            spinning = false
            blurValue = 0f
        }
    }
    Box(
        modifier
            .size(size)
            .clickable { if (!spinning) spinning = true }
            .blur(blurDp),
        contentAlignment = Alignment.Center,
    ) {
        NitronLogo(modifier = Modifier.size(size))
    }
}

private suspend fun launchBlurEasing(anim: Animatable<Float, *>, onValue: (Float) -> Unit) {
    anim.animateTo(0f, animationSpec = tween(850)) { onValue(anim.value) }
}
