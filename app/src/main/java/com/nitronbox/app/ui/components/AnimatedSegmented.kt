package com.nitronbox.app.ui.components

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.nitronbox.app.ui.theme.NitronTheme
import com.nitronbox.app.ui.theme.SurfaceLevel
import com.nitronbox.app.ui.theme.nitronSurface
import com.nitronbox.app.ui.theme.pressableRipple

/**
 * Segmented control whose selection indicator slides smoothly under the chosen label.
 * Used for theme/language/protocol/creator tabs — anywhere the pill must travel.
 */
@Composable
fun <T> AnimatedSegmented(
    options: List<Pair<String, T>>,
    selected: T,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
) {
    BoxWithConstraints(
        modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min)
            .nitronSurface(SurfaceLevel.Muted, NitronTheme.shapes.pill)
            .padding(3.dp),
    ) {
        val itemWidth = maxWidth / options.size
        val index = options.indexOfFirst { it.second == selected }.coerceAtLeast(0)
        val indicatorX by animateDpAsState(
            targetValue = itemWidth * index,
            animationSpec = spring(dampingRatio = 0.85f, stiffness = 380f),
            label = "segmentIndicator",
        )
        Box(
            Modifier
                .offset(x = indicatorX)
                .width(itemWidth)
                .fillMaxHeight()
                .background(NitronTheme.colors.primary, NitronTheme.shapes.pill),
        )
        Row(Modifier.fillMaxWidth()) {
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
}
