package app.nitronbox.mobile

import android.graphics.RenderEffect
import android.graphics.RuntimeShader
import android.graphics.Shader
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

private const val NeutralRefractionShader = """
uniform shader content;
uniform float2 resolution;

half4 main(float2 point) {
    float2 safeResolution = max(resolution, float2(1.0));
    float2 uv = point / safeResolution;
    float edgeDistance = min(min(uv.x, 1.0 - uv.x), min(uv.y, 1.0 - uv.y));
    float edge = 1.0 - smoothstep(0.0, 0.12, edgeDistance);
    float2 direction = normalize((uv - 0.5) + float2(0.0001));
    float2 samplePoint = clamp(point - direction * edge * 3.0, float2(0.0), safeResolution);
    return content.eval(samplePoint);
}
"""

@Composable
fun LiquidGlassSurface(
    modifier: Modifier = Modifier,
    shape: RoundedCornerShape = RoundedCornerShape(20.dp),
    blurRadius: Dp = 14.dp,
    content: @Composable BoxScope.() -> Unit,
) {
    Box(modifier.liquidGlass(shape)) {
        GlassLayer(shape, blurRadius)
        Box(
            Modifier.matchParentSize().clip(shape).background(
                Brush.linearGradient(
                    colorStops = arrayOf(
                        0.00f to Color.White.copy(alpha = 0.13f),
                        0.24f to Color.White.copy(alpha = 0.035f),
                        0.55f to Color.Transparent,
                        1.00f to Color(0xFF9EDCFF).copy(alpha = 0.035f),
                    )
                )
            )
        )
        Box(Modifier.matchParentSize().edgeRefraction(shape))
        Box(Modifier.matchParentSize().glassBorder(shape))
        content()
    }
}

fun Modifier.liquidGlass(shape: RoundedCornerShape = RoundedCornerShape(20.dp)): Modifier = composed {
    shadow(
        elevation = 12.dp,
        shape = shape,
        clip = false,
        ambientColor = Color.Black.copy(alpha = 0.18f),
        spotColor = Color.Black.copy(alpha = 0.22f),
    ).clip(shape)
}

@Composable
private fun BoxScope.GlassLayer(shape: RoundedCornerShape, blurRadius: Dp) {
    if (Build.VERSION.SDK_INT >= 33) AgslLayer(shape, blurRadius)
    else {
        val effect = remember(blurRadius) {
            if (Build.VERSION.SDK_INT >= 31) RenderEffect.createBlurEffect(
                blurRadius.value, blurRadius.value, Shader.TileMode.CLAMP
            ).asComposeRenderEffect() else null
        }
        Box(
            Modifier.matchParentSize()
                .graphicsLayer { renderEffect = effect; clip = true; this.shape = shape }
                .background(neutralFill(), shape)
        )
    }
}

@RequiresApi(33)
@Composable
private fun BoxScope.AgslLayer(shape: RoundedCornerShape, blurRadius: Dp) {
    val shader = remember { RuntimeShader(NeutralRefractionShader) }
    val effect = remember(shader, blurRadius) {
        RenderEffect.createChainEffect(
            RenderEffect.createRuntimeShaderEffect(shader, "content"),
            RenderEffect.createBlurEffect(blurRadius.value, blurRadius.value, Shader.TileMode.CLAMP),
        ).asComposeRenderEffect()
    }
    Box(
        Modifier.matchParentSize()
            .onSizeChanged { shader.setFloatUniform("resolution", it.width.toFloat(), it.height.toFloat()) }
            .graphicsLayer { renderEffect = effect; clip = true; this.shape = shape }
            .background(neutralFill(), shape)
    )
}

private fun neutralFill() = Brush.verticalGradient(
    listOf(Color(0xFF202A39).copy(alpha = 0.72f), Color(0xFF111824).copy(alpha = 0.78f))
)

private fun Modifier.edgeRefraction(shape: RoundedCornerShape) = drawWithCache {
    val top = Brush.horizontalGradient(
        listOf(Color(0xFFBCEAFF).copy(alpha = 0.09f), Color.Transparent, Color.White.copy(alpha = 0.035f))
    )
    onDrawBehind {
        drawRoundRect(top, size = Size(size.width, size.height * 0.25f), cornerRadius = androidx.compose.ui.geometry.CornerRadius(20.dp.toPx()))
        drawRect(
            Brush.verticalGradient(listOf(Color.Transparent, Color(0xFF8CCFFF).copy(alpha = 0.025f))),
            topLeft = Offset(0f, size.height * 0.65f),
            size = Size(size.width, size.height * 0.35f),
        )
    }
}.clip(shape)

private fun Modifier.glassBorder(shape: RoundedCornerShape) = border(
    1.dp,
    Brush.linearGradient(
        listOf(
            Color.White.copy(alpha = 0.24f),
            Color.White.copy(alpha = 0.07f),
            Color(0xFF9ADFFF).copy(alpha = 0.12f),
        )
    ),
    shape,
)
