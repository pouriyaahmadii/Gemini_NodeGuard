package com.example.ui.theme

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

@Immutable
data class NodeGuardAdaptivePalette(
    val isDark: Boolean,
    val background: Color,
    val logoEdgeBackground: Color,
    val surface: Color,
    val surfaceBright: Color,
    val cardBorder: Color,
    val primaryAccent: Color,
    val secondaryAccent: Color,
    val tertiaryAccent: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val textMuted: Color
)

val LocalNodeGuardColors = staticCompositionLocalOf {
    NodeGuardAdaptivePalette(
        isDark = true,
        background = ObsidianAtmosphericBase,
        logoEdgeBackground = DarkLogoBackground,
        surface = SpatialGlassSurface,
        surfaceBright = ElevatedGlassBright,
        cardBorder = GlassBorderSubtle,
        primaryAccent = NeonCyan,
        secondaryAccent = GoogleBlue,
        tertiaryAccent = ElectricPurple,
        textPrimary = TextPrimaryFrost,
        textSecondary = TextSecondarySlate,
        textMuted = TextMutedTech
    )
}

val CyberShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(18.dp),
    large = RoundedCornerShape(22.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

/**
 * Multi-Layered Spatial Glassmorphism Modifier (120FPS drawWithCache):
 * - Dark Mode ("Obsidian Spatial Glass"):
 *   Translucent `#0D1326` at 72% alpha, subtle top-left glass refraction sheen, and a `0.75.dp`
 *   specular inner-highlight border (brighter at top-left, fading to transparent at bottom-right).
 * - Light Mode ("Alabaster Frosted Quartz"):
 *   Soft alabaster pearl `#F8FAFC` surface with crisp `1.dp` `#E2E8F0` slate-tinted border and subtle elevation.
 */
@Composable
fun Modifier.spatialGlassCard(
    cornerRadius: Dp = 20.dp,
    accentTint: Color? = null,
    elevationDp: Dp = 6.dp
): Modifier {
    val palette = LocalNodeGuardColors.current
    val shape = RoundedCornerShape(cornerRadius)
    return this
        .shadow(
            elevation = if (palette.isDark) elevationDp else (elevationDp * 0.45f),
            shape = shape,
            ambientColor = if (palette.isDark) Color.Black.copy(alpha = 0.55f) else DeepSapphire.copy(alpha = 0.08f),
            spotColor = (accentTint ?: palette.primaryAccent).copy(alpha = if (palette.isDark) 0.18f else 0.08f)
        )
        .clip(shape)
        .drawWithCache {
            val w = size.width
            val h = size.height
            if (w <= 0f || h <= 0f) {
                return@drawWithCache onDrawBehind {}
            }

            val cornerPx = cornerRadius.toPx()
            val cornerRad = CornerRadius(cornerPx, cornerPx)

            // Dark Mode: #0D1326 at 72% alpha + subtle top-left refraction sheen
            // Light Mode: #F8FAFC alabaster pearl surface + pure white top sheen
            val baseSurfaceColor = if (palette.isDark) {
                Color(0xB80D1326)
            } else {
                LightElevatedSurface
            }

            val topSheenBrush = Brush.linearGradient(
                colors = if (palette.isDark) {
                    listOf(
                        (accentTint ?: RoyalSapphire).copy(alpha = 0.09f),
                        Color(0x08FFFFFF),
                        Color.Transparent
                    )
                } else {
                    listOf(
                        Color.White.copy(alpha = 0.85f),
                        (accentTint ?: DeepSapphire).copy(alpha = 0.03f),
                        Color.Transparent
                    )
                },
                start = Offset(0f, 0f),
                end = Offset(w * 0.85f, h * 0.85f)
            )

            // 0.75dp specular inner-highlight border in Dark Mode (brighter top-left -> transparent bottom-right)
            // 1.0dp crisp #E2E8F0 slate-tinted border in Light Mode
            val borderStroke = Stroke(
                width = if (palette.isDark) 0.75.dp.toPx() else 1.dp.toPx()
            )
            val specularBorderBrush = if (palette.isDark) {
                Brush.linearGradient(
                    colors = listOf(
                        (accentTint ?: SpecularHighlightTopLeft).copy(alpha = 0.52f),
                        Color(0x2E94A3B8),
                        SpecularHighlightBottomRight
                    ),
                    start = Offset(0f, 0f),
                    end = Offset(w, h)
                )
            } else {
                Brush.linearGradient(
                    colors = listOf(
                        if (accentTint != null) accentTint.copy(alpha = 0.35f) else LightBorderSubtle,
                        LightBorderSubtle,
                        LightBorderSubtle.copy(alpha = 0.75f)
                    ),
                    start = Offset(0f, 0f),
                    end = Offset(w, h)
                )
            }

            onDrawBehind {
                if (size.width <= 0f || size.height <= 0f) return@onDrawBehind

                drawRoundRect(
                    color = baseSurfaceColor,
                    cornerRadius = cornerRad
                )
                drawRoundRect(
                    brush = topSheenBrush,
                    cornerRadius = cornerRad
                )
                drawRoundRect(
                    brush = specularBorderBrush,
                    cornerRadius = cornerRad,
                    style = borderStroke
                )
            }
        }
}

@Composable
fun GeminiNodeGuardTheme(
    isDarkMode: Boolean = true,
    content: @Composable () -> Unit
) {
    val animSpec = tween<Color>(durationMillis = 360)

    val background by animateColorAsState(
        targetValue = if (isDarkMode) ObsidianAtmosphericBase else LightLogoBackground,
        animationSpec = animSpec,
        label = "theme_bg"
    )
    val logoEdgeBg by animateColorAsState(
        targetValue = if (isDarkMode) DarkLogoBackground else LightLogoBackground,
        animationSpec = animSpec,
        label = "theme_logo_edge_bg"
    )
    val surface by animateColorAsState(
        targetValue = if (isDarkMode) SpatialGlassSurface else LightElevatedSurface,
        animationSpec = animSpec,
        label = "theme_surface"
    )
    val surfaceBright by animateColorAsState(
        targetValue = if (isDarkMode) ElevatedGlassBright else LightSurfaceBright,
        animationSpec = animSpec,
        label = "theme_surface_bright"
    )
    val cardBorder by animateColorAsState(
        targetValue = if (isDarkMode) GlassBorderSubtle else LightBorderSubtle,
        animationSpec = animSpec,
        label = "theme_border"
    )
    val primaryAccent by animateColorAsState(
        targetValue = if (isDarkMode) NeonCyan else DeepSapphire,
        animationSpec = animSpec,
        label = "theme_primary_accent"
    )
    val secondaryAccent by animateColorAsState(
        targetValue = if (isDarkMode) GoogleBlue else RoyalSapphire,
        animationSpec = animSpec,
        label = "theme_secondary_accent"
    )
    val tertiaryAccent by animateColorAsState(
        targetValue = if (isDarkMode) ElectricPurple else ElectricViolet,
        animationSpec = animSpec,
        label = "theme_tertiary_accent"
    )
    val textPrimary by animateColorAsState(
        targetValue = if (isDarkMode) TextPrimaryFrost else LightTextPrimary,
        animationSpec = animSpec,
        label = "theme_text_primary"
    )
    val textSecondary by animateColorAsState(
        targetValue = if (isDarkMode) TextSecondarySlate else LightTextSecondary,
        animationSpec = animSpec,
        label = "theme_text_secondary"
    )
    val textMuted by animateColorAsState(
        targetValue = if (isDarkMode) TextMutedTech else LightTextMuted,
        animationSpec = animSpec,
        label = "theme_text_muted"
    )

    val adaptivePalette = NodeGuardAdaptivePalette(
        isDark = isDarkMode,
        background = background,
        logoEdgeBackground = logoEdgeBg,
        surface = surface,
        surfaceBright = surfaceBright,
        cardBorder = cardBorder,
        primaryAccent = primaryAccent,
        secondaryAccent = secondaryAccent,
        tertiaryAccent = tertiaryAccent,
        textPrimary = textPrimary,
        textSecondary = textSecondary,
        textMuted = textMuted
    )

    val m3ColorScheme = if (isDarkMode) {
        darkColorScheme(
            primary = primaryAccent,
            onPrimary = DarkLogoBackground,
            primaryContainer = surfaceBright,
            onPrimaryContainer = primaryAccent,
            secondary = secondaryAccent,
            onSecondary = TextPrimaryFrost,
            secondaryContainer = surface,
            onSecondaryContainer = primaryAccent,
            tertiary = tertiaryAccent,
            onTertiary = TextPrimaryFrost,
            tertiaryContainer = surfaceBright,
            onTertiaryContainer = tertiaryAccent,
            background = background,
            onBackground = textPrimary,
            surface = surface,
            onSurface = textPrimary,
            surfaceVariant = surfaceBright,
            onSurfaceVariant = textSecondary,
            outline = cardBorder,
            outlineVariant = textMuted,
            error = NeonCrimson,
            onError = TextPrimaryFrost
        )
    } else {
        lightColorScheme(
            primary = primaryAccent,
            onPrimary = LightLogoBackground,
            primaryContainer = surfaceBright,
            onPrimaryContainer = primaryAccent,
            secondary = secondaryAccent,
            onSecondary = LightLogoBackground,
            secondaryContainer = surface,
            onSecondaryContainer = primaryAccent,
            tertiary = tertiaryAccent,
            onTertiary = LightLogoBackground,
            tertiaryContainer = surfaceBright,
            onTertiaryContainer = tertiaryAccent,
            background = background,
            onBackground = textPrimary,
            surface = surface,
            onSurface = textPrimary,
            surfaceVariant = surfaceBright,
            onSurfaceVariant = textSecondary,
            outline = cardBorder,
            outlineVariant = textMuted,
            error = NeonCrimson,
            onError = LightLogoBackground
        )
    }

    CompositionLocalProvider(LocalNodeGuardColors provides adaptivePalette) {
        MaterialTheme(
            colorScheme = m3ColorScheme,
            typography = Typography,
            shapes = CyberShapes,
            content = content
        )
    }
}

@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = true,
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    GeminiNodeGuardTheme(isDarkMode = darkTheme, content = content)
}
