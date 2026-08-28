package com.nitronbox.app.ui.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.nitronbox.app.data.settings.WallpaperPreset
import com.nitronbox.app.ui.components.NitronLogo

/**
 * Theme-aware wallpaper behind the chat. Presets are subtle two-tone gradients tuned per
 * light/dark so chat content stays readable; CUSTOM shows a user-picked image under a scrim.
 */
@Composable
fun WallpaperBackdrop(
    preset: WallpaperPreset,
    customImageUri: String?,
    modifier: Modifier = Modifier,
) {
    val dark = NitronTheme.colors.isDark
    when (preset) {
        WallpaperPreset.NONE -> Unit

        WallpaperPreset.MIDNIGHT -> GradientWallpaper(
            modifier = modifier,
            colors = if (dark) listOf(Color(0xFF0B1428), Color(0xFF16233F)) else listOf(Color(0xFFEAF1FF), Color(0xFFD9E7FF)),
        )

        WallpaperPreset.AURORA -> GradientWallpaper(
            modifier = modifier,
            colors = if (dark) listOf(Color(0xFF07231D), Color(0xFF0F3A30)) else listOf(Color(0xFFE7FFF6), Color(0xFFCFF2E5)),
        )

        WallpaperPreset.SUNSET -> GradientWallpaper(
            modifier = modifier,
            colors = if (dark) listOf(Color(0xFF2B0F1E), Color(0xFF40202E)) else listOf(Color(0xFFFFEFE7), Color(0xFFFFDfD6)),
        )

        WallpaperPreset.GRAPHITE -> GradientWallpaper(
            modifier = modifier,
            colors = if (dark) listOf(Color(0xFF0E0E10), Color(0xFF1B1B20)) else listOf(Color(0xFFF2F2F4), Color(0xFFE4E4EA)),
        )

        WallpaperPreset.LOGO -> Box(modifier.background(NitronTheme.colors.background)) {
            val glow = if (dark) Color(0xFF22222E) else Color(0xFFE9E9EF)
            Box(
                Modifier
                    .fillMaxSize()
                    .background(
                        Brush.radialGradient(
                            colors = listOf(glow.copy(alpha = if (dark) 0.6f else 0.55f), Color.Transparent),
                        ),
                    ),
            )
            val top = if (dark) Color.White else Color(0xFF3C3C42)
            val bottom = if (dark) Color(0xFF8E8E96) else Color(0xFFA2A2AA)
            NitronLogo(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(56.dp),
                leftTop = top.copy(alpha = if (dark) 0.9f else 0.85f),
                leftBottom = bottom.copy(alpha = if (dark) 0.7f else 0.65f),
                rightTop = top.copy(alpha = if (dark) 0.9f else 0.85f),
                rightBottom = bottom.copy(alpha = if (dark) 0.7f else 0.65f),
            )
        }

        WallpaperPreset.CUSTOM -> {
            if (customImageUri != null) {
                val context = LocalContext.current
                Box(modifier.background(NitronTheme.colors.background)) {
                    AsyncImage(
                        model = customImageUri,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                    // Scrim keeps bubbles readable over arbitrary photos.
                    Box(
                        Modifier
                            .fillMaxSize()
                            .background(NitronTheme.colors.background.copy(alpha = if (dark) 0.62f else 0.55f)),
                    )
                }
            } else {
                GradientWallpaper(modifier, if (dark) listOf(Color(0xFF101014), Color(0xFF1C1C22)) else listOf(Color(0xFFF4F4F6), Color(0xFFE8E8EE)))
            }
        }
    }
}

@Composable
private fun GradientWallpaper(modifier: Modifier = Modifier, colors: List<Color>) {
    Box(
        modifier.background(
            Brush.verticalGradient(colors),
        ),
    )
}
