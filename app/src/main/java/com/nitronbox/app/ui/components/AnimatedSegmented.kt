package com.nitronbox.app.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.nitronbox.app.ui.theme.NitronTheme
import com.nitronbox.app.ui.theme.SurfaceLevel
import com.nitronbox.app.ui.theme.nitronSurface
import com.nitronbox.app.ui.theme.pressableRipple

/**
 * Segmented control whose selection pill slides smoothly under the chosen label.
 * The indicator is drawn behind the row (no intrinsics or subcomposition involved),
 * so it is safe inside lazy lists and panels.
 */
@Composable
fun <T> AnimatedSegmented(
    options: List<Pair<String, T>>,
    selected: T,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (options.isEmpty()) return
    val index = options.indexOfFirst { it.second == selected }.coerceAtLeast(0)
    val progress by animateFloatAsState(
        targetValue = index.toFloat(),
        animationSpec = spring(dampingRatio = 0.85f, stiffness = 380f),
        label = "segmentIndicator",
    )
    val indicatorColor = NitronTheme.colors.primary
    Row(
        modifier
            .fillMaxWidth()
            .nitronSurface(SurfaceLevel.Muted, NitronTheme.shapes.pill)
            .padding(3.dp)
            .drawBehind {
                val w = size.width / options.size
                val radius = size.height / 2f
                drawRoundRect(
                    color = indicatorColor,
                    topLeft = Offset(w * progress, 0f),
                    size = Size(w, size.height),
                    cornerRadius = CornerRadius(radius, radius),
                )
            },
    ) {
        options.forEach { (label, value) ->
            val isSelected = value == selected
            Text(
                label,
                style = MaterialTheme.typography.labelMedium,
                color = if (isSelected) NitronTheme.colors.onPrimary else NitronTheme.colors.textSecondary,
                textAlign = TextAlign.Center,
                maxLines = 1,
                modifier = Modifier
                    .weight(1f)
                    .pressableRipple(shape = NitronTheme.shapes.pill) { onSelect(value) }
                    .padding(vertical = 8.dp),
            )
        }
    }
}
