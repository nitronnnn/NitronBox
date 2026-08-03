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
import androidx.compose.foundation.layout.matchParentSize
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

private const val RefractionShader = """
uniform shader content;
uniform float2 resolution;

half4 main(float2 point) {
    float2 uv = point / resolution;
    float2 edge = min(uv, 1.0 - uv);
    float edgeDistance = min(edge.x, edge.y);
    float edgeStrength = 1.0 - smoothstep(0.0, 0.16, edgeDistance);
    float2 direction = normalize((uv - 0.5) + float2(0.0001, 0.0001));
    float wave = sin((uv.x + uv.y) * 18.0) * 1.4;
    float2 displaced = point - direction * edgeStrength * 7.5 + float2(wave, -wave) * edgeStrength;
    half4 color = content.eval(clamp(displaced, float2(0.0), resolution));
    half prism = half(edgeStrength * 0.075);
    color.rgb += half3(prism * uv.y, prism * 0.35, prism * (1.0 - uv.x));
    return color;
}
"""

/** Physical-style glass shell. Content is placed above the affected background layer. */
@Composable
fun LiquidGlassSurface(
    modifier: Modifier = Modifier,
    shape: RoundedCornerShape = RoundedCornerShape(24.dp),
    blurRadius: Dp = 18.dp,
    content: @Composable BoxScope.() -> Unit,
) {
    Box(modifier = modifier.liquidGlass(shape)) {
        GlassEffectLayer(shape, blurRadius)

        // Curved 45-degree reflection, independent from content and refraction.
        Box(
            Modifier.matchParentSize().clip(shape).background(
                Brush.linearGradient(
                    colorStops = arrayOf(
                        0.00f to Color.White.copy(alpha = 0.24f),
                        0.18f to Color.White.copy(alpha = 0.08f),
                        0.42f to Color.Transparent,
                        0.74f to Color.Transparent,
                        1.00f to Color(0xFF9B82FF).copy(alpha = 0.08f),
                    ),
                    start = Offset.Zero,
                    end = Offset.Infinite,
                )
            )
        )

        Box(Modifier.matchParentSize().glassRefractionFallback(shape))
        Box(Modifier.matchParentSize().glassBevel(shape))
        content()
    }
}

/** Shadow and clipping shared by every glass control and surface. */
fun Modifier.liquidGlass(shape: RoundedCornerShape = RoundedCornerShape(24.dp)): Modifier = composed {
    shadow(
        elevation = 18.dp,
        shape = shape,
        clip = false,
        ambientColor = Color.Black.copy(alpha = 0.16f),
        spotColor = Color.Black.copy(alpha = 0.24f),
    ).clip(shape)
}

@Composable
private fun BoxScope.GlassEffectLayer(shape: RoundedCornerShape, blurRadius: Dp) {
    if (Build.VERSION.SDK_INT >= 33) {
        AgslGlassLayer(shape, blurRadius)
    } else {
        val effect = remember(blurRadius) {
            if (Build.VERSION.SDK_INT >= 31) {
                RenderEffect.createBlurEffect(blurRadius.value, blurRadius.value, Shader.TileMode.CLAMP).asComposeRenderEffect()
            } else null
        }
        Box(
            Modifier.matchParentSize()
                .graphicsLayer { renderEffect = effect; clip = true; this.shape = shape }
                .background(glassFill(), shape)
        )
    }
}

@RequiresApi(33)
@Composable
private fun BoxScope.AgslGlassLayer(shape: RoundedCornerShape, blurRadius: Dp) {
    val shader = remember { RuntimeShader(RefractionShader) }
    val effect = remember(shader, blurRadius) {
        val refraction = RenderEffect.createRuntimeShaderEffect(shader, "content")
        val blur = RenderEffect.createBlurEffect(blurRadius.value, blurRadius.value, Shader.TileMode.CLAMP)
        RenderEffect.createChainEffect(refraction, blur).asComposeRenderEffect()
    }
    Box(
        Modifier.matchParentSize()
            .onSizeChanged { shader.setFloatUniform("resolution", it.width.toFloat(), it.height.toFloat()) }
            .graphicsLayer { renderEffect = effect; clip = true; this.shape = shape }
            .background(glassFill(), shape)
    )
}

private fun glassFill() = Brush.linearGradient(
    colorStops = arrayOf(
        0.00f to Color(0xFFDDF3FF).copy(alpha = 0.20f),
        0.32f to Color(0xFF8EB7E8).copy(alpha = 0.08f),
        0.68f to Color(0xFF5F6EA8).copy(alpha = 0.07f),
        1.00f to Color(0xFF8B6DDE).copy(alpha = 0.13f),
    )
)

private fun Modifier.glassRefractionFallback(shape: RoundedCornerShape) = drawWithCache {
    val topRefraction = Brush.linearGradient(
        listOf(Color(0xFF9DEBFF).copy(alpha = 0.16f), Color.Transparent, Color(0xFF967DFF).copy(alpha = 0.10f)),
        start = Offset(0f, 0f), end = Offset(size.width, size.height),
    )
    val bottomRefraction = Brush.linearGradient(
        listOf(Color.Transparent, Color(0xFFB7F4FF).copy(alpha = 0.07f), Color(0xFF8C6DFF).copy(alpha = 0.13f)),
        start = Offset(0f, size.height), end = Offset(size.width, 0f),
    )
    onDrawBehind {
        drawRoundRect(topRefraction, size = Size(size.width, size.height * .34f), cornerRadius = androidx.compose.ui.geometry.CornerRadius(26.dp.toPx()))
        drawRoundRect(bottomRefraction, topLeft = Offset(0f, size.height * .72f), size = Size(size.width, size.height * .28f), cornerRadius = androidx.compose.ui.geometry.CornerRadius(26.dp.toPx()))
    }
}.clip(shape)

private fun Modifier.glassBevel(shape: RoundedCornerShape) = border(
    width = 1.dp,
    brush = Brush.linearGradient(
        colorStops = arrayOf(
            0.00f to Color.White.copy(alpha = 0.34f),
            0.28f to Color.White.copy(alpha = 0.14f),
            0.62f to Color(0xFF96B9E8).copy(alpha = 0.10f),
            1.00f to Color(0xFFB3A2FF).copy(alpha = 0.28f),
        )
    ),
    shape = shape,
)
