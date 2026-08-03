package app.nitronbox.mobile

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.skydoves.cloudy.cloudy
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.HazeStyle
import dev.chrisbanes.haze.hazeChild

/**
 * A reusable glass surface. Haze captures and blurs the moving backdrop, while Cloudy is kept on
 * a decoration-only layer so text and controls above it remain crisp.
 */
@Composable
fun LiquidGlassCard(
    hazeState: HazeState,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    val shape = RoundedCornerShape(30.dp)
    val bevel = BorderStroke(
        1.dp,
        Brush.linearGradient(
            0.0f to Color.White.copy(alpha = 0.46f),
            0.35f to Color.White.copy(alpha = 0.14f),
            0.72f to Color(0xFF91DFFF).copy(alpha = 0.12f),
            1.0f to Color.White.copy(alpha = 0.28f),
        ),
    )

    Box(
        modifier = modifier
            .clip(shape)
            .hazeChild(
                state = hazeState,
                shape = shape,
                style = HazeStyle(
                    tint = Color(0xFF101923).copy(alpha = 0.56f),
                    blurRadius = 28.dp,
                    noiseFactor = 0.08f,
                ),
            )
            .border(bevel, shape),
    ) {
        // Cloudy 0.2.0 is a self-blur modifier. Applying it to this decoration-only layer gives
        // the edge glow a soft refraction-like spread without blurring the card's actual content.
        Box(
            modifier = Modifier
                .matchParentSize()
                .cloudy(radius = 25)
                .background(
                    Brush.linearGradient(
                        colors = listOf(
                            Color.White.copy(alpha = 0.11f),
                            Color.Transparent,
                            Color(0xFF5CCBFF).copy(alpha = 0.07f),
                        ),
                        start = Offset.Zero,
                        end = Offset.Infinite,
                    ),
                ),
        )

        // A sharp 45-degree specular reflection is drawn after blur and before content.
        Box(
            modifier = Modifier
                .matchParentSize()
                .drawWithCache {
                    val highlight = Brush.linearGradient(
                        colorStops = arrayOf(
                            0.00f to Color.White.copy(alpha = 0.28f),
                            0.12f to Color.White.copy(alpha = 0.10f),
                            0.28f to Color.Transparent,
                            1.00f to Color.Transparent,
                        ),
                        start = Offset.Zero,
                        end = Offset(size.width, size.height),
                    )
                    onDrawBehind { drawRect(highlight) }
                },
        )

        content()
    }
}
