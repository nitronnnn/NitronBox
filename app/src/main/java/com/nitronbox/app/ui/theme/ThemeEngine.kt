package com.nitronbox.app.ui.theme

import android.content.Context
import android.net.Uri
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.State
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nitronbox.app.data.model.FontSource
import com.nitronbox.app.data.model.SurfaceConfiguration
import com.nitronbox.app.data.model.ThemeMode
import com.nitronbox.app.data.model.TypographyConfiguration
import com.nitronbox.app.data.model.WorkspaceTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Neutral, flat color set. The visual language is deliberately minimal: solid surfaces separated
 * by hairline borders, a single restrained interactive accent, and no glass, blur, or gradients.
 */
@Immutable
data class NitronColors(
    val background: Color,
    val surface: Color,
    val surfaceMuted: Color,
    val surfaceHover: Color,
    val border: Color,
    val borderStrong: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val textTertiary: Color,
    val primary: Color,
    val onPrimary: Color,
    val accent: Color,
    val onAccent: Color,
    val destructive: Color,
    val codeBackground: Color,
    val userBubble: Color,
    val selection: Color,
    val isDark: Boolean,
)

@Immutable
data class NitronShapes(
    val extraSmall: RoundedCornerShape,
    val small: RoundedCornerShape,
    val medium: RoundedCornerShape,
    val large: RoundedCornerShape,
    val extraLarge: RoundedCornerShape,
    val pill: RoundedCornerShape,
)

@Immutable
data class NitronSpacing(
    val xs: Dp,
    val sm: Dp,
    val md: Dp,
    val lg: Dp,
    val xl: Dp,
    val xxl: Dp,
    val xxxl: Dp,
)

@Immutable
data class NitronMotion(
    val reduceMotion: Boolean,
)

val LocalNitronColors = compositionLocalOf { lightNitronColors() }
val LocalNitronShapes = compositionLocalOf { defaultShapes(12f) }
val LocalNitronSpacing = compositionLocalOf {
    NitronSpacing(4.dp, 8.dp, 12.dp, 16.dp, 20.dp, 24.dp, 32.dp)
}
val LocalNitronMotion = compositionLocalOf { NitronMotion(reduceMotion = false) }

object NitronTheme {
    val colors: NitronColors
        @Composable @ReadOnlyComposable get() = LocalNitronColors.current
    val shapes: NitronShapes
        @Composable @ReadOnlyComposable get() = LocalNitronShapes.current
    val spacing: NitronSpacing
        @Composable @ReadOnlyComposable get() = LocalNitronSpacing.current
    val motion: NitronMotion
        @Composable @ReadOnlyComposable get() = LocalNitronMotion.current
}

/** Hook for a downloadable-font implementation (for example FontsContractCompat). */
fun interface DownloadableFontResolver {
    suspend fun resolve(query: String): FontFamily?
}

@Composable
fun NitronBoxTheme(
    workspaceTheme: WorkspaceTheme = WorkspaceTheme(),
    downloadableFontResolver: DownloadableFontResolver? = null,
    content: @Composable () -> Unit,
) {
    val systemDark = isSystemInDarkTheme()
    val dark = when (workspaceTheme.mode) {
        ThemeMode.SYSTEM -> systemDark
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    val palette = workspaceTheme.palette
    val colors = remember(workspaceTheme, dark) { buildColors(palette, dark) }
    val fontFamily by rememberWorkspaceFont(workspaceTheme.typography, downloadableFontResolver)
    val typography = remember(fontFamily, workspaceTheme.typography.scale) {
        nitronTypography(fontFamily, workspaceTheme.typography.scale.coerceIn(0.85f, 1.35f))
    }
    val shapes = remember(workspaceTheme.surface.cornerRadiusDp) {
        defaultShapes(workspaceTheme.surface.cornerRadiusDp.coerceIn(0f, 24f))
    }
    val motion = remember(workspaceTheme.reduceMotion) { NitronMotion(workspaceTheme.reduceMotion) }

    androidx.compose.runtime.CompositionLocalProvider(
        LocalNitronColors provides colors,
        LocalNitronShapes provides shapes,
        LocalNitronMotion provides motion,
    ) {
        MaterialTheme(
            colorScheme = colors.toMaterialScheme(),
            typography = typography,
            shapes = Shapes(
                extraSmall = shapes.extraSmall,
                small = shapes.small,
                medium = shapes.medium,
                large = shapes.large,
                extraLarge = shapes.extraLarge,
            ),
            content = content,
        )
    }
}

@Composable
private fun rememberWorkspaceFont(
    configuration: TypographyConfiguration,
    downloadableFontResolver: DownloadableFontResolver?,
): State<FontFamily> {
    val context = LocalContext.current
    val family = remember(configuration) { mutableStateOf<FontFamily>(FontFamily.SansSerif) }
    LaunchedEffect(configuration, downloadableFontResolver) {
        family.value = when (configuration.source) {
            FontSource.SYSTEM -> systemFont(configuration.familyName)
            FontSource.LOCAL_FILE -> configuration.localFontUri
                ?.let { cacheSafFont(context, Uri.parse(it)) }
                ?: FontFamily.SansSerif
            FontSource.GOOGLE_FONTS -> configuration.googleFontQuery
                ?.let { downloadableFontResolver?.resolve(it) }
                ?: FontFamily.SansSerif
        }
    }
    return family
}

private fun systemFont(name: String): FontFamily = when (name.lowercase()) {
    "serif", "noto serif", "georgia" -> FontFamily.Serif
    "monospace", "jetbrains mono" -> FontFamily.Monospace
    "cursive" -> FontFamily.Cursive
    else -> FontFamily.SansSerif
}

/**
 * Validates and caches a SAF font. Runtime Typeface adaptation is deliberately delegated to a
 * DownloadableFontResolver because Compose FontFamily requires a Font implementation/resource.
 */
private suspend fun cacheSafFont(context: Context, uri: Uri): FontFamily? = withContext(Dispatchers.IO) {
    runCatching {
        val cacheFile = File(context.cacheDir, "font-${uri.toString().hashCode()}.tmp")
        context.contentResolver.openInputStream(uri)?.use { input ->
            cacheFile.outputStream().use(input::copyTo)
        } ?: return@runCatching null
        if (cacheFile.length() > 0) FontFamily.SansSerif else null
    }.getOrNull()
}

private fun defaultShapes(radius: Float) = NitronShapes(
    extraSmall = RoundedCornerShape((radius * 0.5f).dp),
    small = RoundedCornerShape((radius * 0.66f).dp),
    medium = RoundedCornerShape(radius.dp),
    large = RoundedCornerShape((radius + 4f).dp),
    extraLarge = RoundedCornerShape((radius + 8f).dp),
    pill = RoundedCornerShape(50),
)

private fun buildColors(palette: com.nitronbox.app.data.model.ThemePalette, dark: Boolean): NitronColors {
    val background = Color(if (dark) palette.darkBackground else palette.lightBackground)
    val surface = Color(if (dark) palette.darkSurface else palette.lightSurface)
    val surfaceMuted = Color(if (dark) palette.darkMuted else palette.lightMuted)
    val border = Color(if (dark) palette.darkBorder else palette.lightBorder)
    val textPrimary = Color(if (dark) palette.darkText else palette.lightText)
    val textSecondary = if (dark) Color(0xFF9A9A9A) else Color(0xFF6F6F6F)
    val textTertiary = if (dark) Color(0xFF6E6E6E) else Color(0xFF9B9B9B)
    val accent = Color(palette.accent)
    val destructive = Color(palette.destructive)
    return NitronColors(
        background = background,
        surface = surface,
        surfaceMuted = surfaceMuted,
        surfaceHover = if (dark) Color(0xFF1C1C1C) else Color(0xFFF0F0F0),
        border = border,
        borderStrong = if (dark) Color(0xFF3A3A3A) else Color(0xFFD4D4D4),
        textPrimary = textPrimary,
        textSecondary = textSecondary,
        textTertiary = textTertiary,
        // Primary action is monochrome (Vercel-style): near-black on light, near-white on dark.
        primary = if (dark) Color(0xFFEDEDED) else Color(0xFF171717),
        onPrimary = if (dark) Color(0xFF0A0A0A) else Color(0xFFFFFFFF),
        accent = accent,
        onAccent = Color.White,
        destructive = destructive,
        codeBackground = if (dark) Color(0xFF0D0D0D) else Color(0xFFFAFAFA),
        userBubble = if (dark) Color(0xFF1F1F1F) else Color(0xFFF4F4F5),
        selection = accent.copy(alpha = if (dark) 0.32f else 0.20f),
        isDark = dark,
    )
}

private fun NitronColors.toMaterialScheme(): ColorScheme {
    val base = if (isDark) {
        darkColorScheme(
            primary = primary,
            onPrimary = onPrimary,
            secondary = accent,
            onSecondary = onAccent,
            background = background,
            onBackground = textPrimary,
            surface = surface,
            onSurface = textPrimary,
            surfaceVariant = surfaceMuted,
            onSurfaceVariant = textSecondary,
            outline = border,
            outlineVariant = border,
            error = destructive,
            scrim = Color.Black,
        )
    } else {
        lightColorScheme(
            primary = primary,
            onPrimary = onPrimary,
            secondary = accent,
            onSecondary = onAccent,
            background = background,
            onBackground = textPrimary,
            surface = surface,
            onSurface = textPrimary,
            surfaceVariant = surfaceMuted,
            onSurfaceVariant = textSecondary,
            outline = border,
            outlineVariant = border,
            error = destructive,
            scrim = Color.Black,
        )
    }
    return base
}

private fun nitronTypography(font: FontFamily, scale: Float): Typography {
    fun style(weight: FontWeight, size: Float, line: Float, tracking: Float) = TextStyle(
        fontFamily = font,
        fontWeight = weight,
        fontSize = (size * scale).sp,
        lineHeight = (line * scale).sp,
        letterSpacing = tracking.sp,
    )
    return Typography(
        displaySmall = style(FontWeight.SemiBold, 30f, 36f, -1.2f),
        headlineLarge = style(FontWeight.SemiBold, 24f, 30f, -0.8f),
        headlineMedium = style(FontWeight.SemiBold, 20f, 26f, -0.5f),
        headlineSmall = style(FontWeight.SemiBold, 18f, 24f, -0.3f),
        titleLarge = style(FontWeight.Medium, 17f, 22f, -0.2f),
        titleMedium = style(FontWeight.Medium, 15f, 20f, -0.1f),
        titleSmall = style(FontWeight.Medium, 14f, 18f, 0f),
        bodyLarge = style(FontWeight.Normal, 16f, 24f, 0f),
        bodyMedium = style(FontWeight.Normal, 14f, 20f, 0f),
        bodySmall = style(FontWeight.Normal, 13f, 18f, 0f),
        labelLarge = style(FontWeight.Medium, 14f, 18f, 0f),
        labelMedium = style(FontWeight.Medium, 13f, 16f, 0.1f),
        labelSmall = style(FontWeight.Medium, 12f, 15f, 0.2f),
    )
}

private fun lightNitronColors(): NitronColors = buildColors(
    com.nitronbox.app.data.model.ThemePalette(),
    dark = false,
)
