package com.example.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.ui.theme.DarkLogoBackground
import com.example.ui.theme.DeepSapphire
import com.example.ui.theme.ElectricPurple
import com.example.ui.theme.ElectricViolet
import com.example.ui.theme.GoogleBlue
import com.example.ui.theme.LightLogoBackground
import com.example.ui.theme.LocalNodeGuardColors
import com.example.ui.theme.NeonAmber
import com.example.ui.theme.NeonCrimson
import com.example.ui.theme.NeonCyan
import com.example.ui.theme.NeonEmerald
import kotlin.math.min
import kotlin.math.sin

@Immutable
enum class BrandEmblemType {
    GEMINI_NODEGUARD,
    GOOGLE_GEMINI_API,
    GOOGLE_AI_STUDIO,
    GOOGLE_SEARCH_CORE,
    YOUTUBE_STREAM
}

/**
 * SeamlessLogoBox:
 * Engineered so the official resized Gemini NodeGuard brand logo (`R.drawable.gemini_nodeguard_logo`)
 * and Google service emblems blend 100% invisibly into the UI without ever showing a rectangular box border:
 * 1. Matches container surface 1:1 with the exact logo background (`#070B19` / `#010511` in Dark Mode, `#FFFFFF` in Light Mode).
 * 2. Applies `BlendMode.Screen` (Dark Mode) / `BlendMode.Multiply` (Light Mode) compositing.
 * 3. Feathers outer edges with a smooth radial vignette alpha mask (`Brush.radialGradient` + `BlendMode.DstIn`).
 */
@Composable
fun SeamlessLogoBox(
    emblemType: BrandEmblemType,
    modifier: Modifier = Modifier,
    boxSize: Dp = 42.dp,
    accentOverride: Color? = null
) {
    val palette = LocalNodeGuardColors.current
    val exactLogoSurface = if (palette.isDark) DarkLogoBackground else LightLogoBackground
    val activeBlendMode = if (palette.isDark) BlendMode.Screen else BlendMode.Multiply

    Box(
        modifier = modifier
            .size(boxSize)
            .clip(CircleShape)
            .background(exactLogoSurface)
            .graphicsLayer {
                compositingStrategy = CompositingStrategy.Offscreen
            }
            .drawWithContent {
                drawContent()
                if (size.width <= 0f || size.height <= 0f) return@drawWithContent
                val radius = maxOf(min(size.width, size.height) / 2f, 1f)
                // Soft radial edge feathering mask so rectangular edges dissolve invisibly into the background
                drawCircle(
                    brush = Brush.radialGradient(
                        0.0f to Color.White,
                        0.72f to Color.White,
                        0.90f to Color.White.copy(alpha = 0.60f),
                        1.0f to Color.Transparent,
                        center = center,
                        radius = radius
                    ),
                    radius = radius,
                    center = center,
                    blendMode = BlendMode.DstIn
                )
            },
        contentAlignment = Alignment.Center
    ) {
        if (emblemType == BrandEmblemType.GEMINI_NODEGUARD) {
            // Render the resized official Gemini NodeGuard Neon Shield Logo with radial edge feathering
            val glowAccent = accentOverride ?: palette.primaryAccent
            Image(
                painter = painterResource(id = R.drawable.gemini_nodeguard_logo),
                contentDescription = "Gemini NodeGuard Official Neon Shield Logo",
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .drawWithContent {
                        drawContent()
                        if (size.width > 0f && size.height > 0f) {
                            val r = maxOf(min(size.width, size.height) / 2f, 1f)
                            drawCircle(
                                brush = Brush.radialGradient(
                                    colors = listOf(
                                        glowAccent.copy(alpha = if (palette.isDark) 0.16f else 0.08f),
                                        Color.Transparent
                                    ),
                                    center = center,
                                    radius = r
                                ),
                                radius = r,
                                center = center,
                                blendMode = activeBlendMode
                            )
                        }
                    }
            )
        } else {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val w = size.width
                val h = size.height
                if (w <= 0f || h <= 0f) return@Canvas
                val center = Offset(w / 2f, h / 2f)
                val r = maxOf(min(w, h) / 2f, 1f)
                val haloRadius = maxOf(r * 0.92f, 1f)

                // Base solid logo plate matching #070B19 (Dark) or #FFFFFF (Light)
                drawCircle(
                    color = exactLogoSurface,
                    radius = r,
                    center = center
                )

                // Ambient blended halo using BlendMode.Screen (Dark) or BlendMode.Multiply (Light)
                val glowAccent = accentOverride ?: palette.primaryAccent
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            glowAccent.copy(alpha = if (palette.isDark) 0.28f else 0.14f),
                            palette.tertiaryAccent.copy(alpha = if (palette.isDark) 0.12f else 0.06f),
                            Color.Transparent
                        ),
                        center = center,
                        radius = haloRadius
                    ),
                    radius = haloRadius,
                    center = center,
                    blendMode = activeBlendMode
                )

                when (emblemType) {
                    BrandEmblemType.GEMINI_NODEGUARD -> Unit

                    BrandEmblemType.GOOGLE_GEMINI_API -> {
                        val geminiBrush = Brush.linearGradient(
                            colors = listOf(
                                if (palette.isDark) NeonCyan else DeepSapphire,
                                GoogleBlue,
                                if (palette.isDark) ElectricPurple else ElectricViolet
                            ),
                            start = Offset(center.x - r * 0.65f, center.y - r * 0.65f),
                            end = Offset(center.x + r * 0.65f, center.y + r * 0.65f)
                        )
                        val mainStar = createFourPointStarPath(center, r * 0.58f)
                        drawPath(path = mainStar, brush = geminiBrush)
                        val miniStar = createFourPointStarPath(
                            Offset(center.x + r * 0.36f, center.y - r * 0.36f),
                            r * 0.20f
                        )
                        drawPath(path = miniStar, color = if (palette.isDark) NeonCyan else DeepSapphire)
                    }

                    BrandEmblemType.GOOGLE_AI_STUDIO -> {
                        val studioBrush = Brush.linearGradient(
                            colors = listOf(GoogleBlue, if (palette.isDark) ElectricPurple else ElectricViolet),
                            start = Offset(0f, 0f),
                            end = Offset(w, h)
                        )
                        val frameSize = r * 1.02f
                        drawRoundRect(
                            brush = studioBrush,
                            topLeft = Offset(center.x - frameSize / 2f, center.y - frameSize / 2f),
                            size = Size(frameSize, frameSize),
                            cornerRadius = CornerRadius(6.dp.toPx(), 6.dp.toPx()),
                            style = Stroke(width = 2.dp.toPx())
                        )
                        val innerSpark = createFourPointStarPath(center, r * 0.36f)
                        drawPath(path = innerSpark, brush = studioBrush)
                    }

                    BrandEmblemType.GOOGLE_SEARCH_CORE -> {
                        val arcR = r * 0.50f
                        val topLeft = Offset(center.x - arcR, center.y - arcR)
                        val arcSize = Size(arcR * 2f, arcR * 2f)
                        val strokeW = 3.2.dp.toPx()
                        drawArc(
                            color = Color(0xFFEA4335),
                            startAngle = 215f,
                            sweepAngle = 95f,
                            useCenter = false,
                            topLeft = topLeft,
                            size = arcSize,
                            style = Stroke(width = strokeW, cap = StrokeCap.Butt)
                        )
                        drawArc(
                            color = Color(0xFFFBBC05),
                            startAngle = 135f,
                            sweepAngle = 80f,
                            useCenter = false,
                            topLeft = topLeft,
                            size = arcSize,
                            style = Stroke(width = strokeW, cap = StrokeCap.Butt)
                        )
                        drawArc(
                            color = Color(0xFF34A853),
                            startAngle = 45f,
                            sweepAngle = 90f,
                            useCenter = false,
                            topLeft = topLeft,
                            size = arcSize,
                            style = Stroke(width = strokeW, cap = StrokeCap.Butt)
                        )
                        drawArc(
                            color = GoogleBlue,
                            startAngle = -15f,
                            sweepAngle = 65f,
                            useCenter = false,
                            topLeft = topLeft,
                            size = arcSize,
                            style = Stroke(width = strokeW, cap = StrokeCap.Butt)
                        )
                        drawLine(
                            color = GoogleBlue,
                            start = center,
                            end = Offset(center.x + arcR, center.y),
                            strokeWidth = strokeW,
                            cap = StrokeCap.Round
                        )
                    }

                    BrandEmblemType.YOUTUBE_STREAM -> {
                        val pillW = r * 1.16f
                        val pillH = r * 0.82f
                        drawRoundRect(
                            color = NeonCrimson,
                            topLeft = Offset(center.x - pillW / 2f, center.y - pillH / 2f),
                            size = Size(pillW, pillH),
                            cornerRadius = CornerRadius(6.dp.toPx(), 6.dp.toPx())
                        )
                        val playPath = Path().apply {
                            moveTo(center.x - r * 0.14f, center.y - r * 0.20f)
                            lineTo(center.x + r * 0.22f, center.y)
                            lineTo(center.x - r * 0.14f, center.y + r * 0.20f)
                            close()
                        }
                        drawPath(path = playPath, color = Color.White)
                    }
                }
            }
        }
    }
}

private fun createFourPointStarPath(center: Offset, radius: Float): Path {
    val curve = radius * 0.22f
    return Path().apply {
        moveTo(center.x, center.y - radius)
        quadraticTo(center.x + curve, center.y - curve, center.x + radius, center.y)
        quadraticTo(center.x + curve, center.y + curve, center.x, center.y + radius)
        quadraticTo(center.x - curve, center.y + curve, center.x - radius, center.y)
        quadraticTo(center.x - curve, center.y - curve, center.x, center.y - radius)
        close()
    }
}

@Composable
fun BrandTopAppBar(
    isDarkMode: Boolean,
    isSoundEnabled: Boolean,
    isPrivacyShieldActive: Boolean,
    isFlagSecureActive: Boolean,
    hasSealedVault: Boolean,
    isOtaUpdateAvailable: Boolean = false,
    onToggleThemeMode: () -> Unit,
    onToggleSoundEnabled: () -> Unit,
    onTogglePrivacyShield: () -> Unit,
    onOpenOtaModal: () -> Unit = {},
    onOpenReleaseManifest: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val palette = LocalNodeGuardColors.current
    val shieldColor by animateColorAsState(
        targetValue = if (isPrivacyShieldActive) NeonEmerald else NeonAmber,
        animationSpec = tween(300),
        label = "privacy_shield_color"
    )
    val soundAccent by animateColorAsState(
        targetValue = if (isSoundEnabled) palette.primaryAccent else palette.textMuted,
        animationSpec = tween(260),
        label = "sound_fx_accent"
    )
    val themeIconRotation by animateFloatAsState(
        targetValue = if (isDarkMode) 0f else 180f,
        animationSpec = tween(durationMillis = 420, easing = FastOutSlowInEasing),
        label = "theme_icon_rotation"
    )

    val soundWaveTransition = rememberInfiniteTransition(label = "sound_wave_transition")
    val wavePhase by soundWaveTransition.animateFloat(
        initialValue = 0f,
        targetValue = 6.2831855f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1100, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "sound_wave_phase"
    )

    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(palette.background)
            .border(width = 1.dp, color = palette.cardBorder)
            .padding(horizontal = 12.dp, vertical = 10.dp)
            .testTag("global_privacy_top_bar"),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Left: Seamless Logo + Brand Identity & v1.0.0-release Manifest Trigger
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.weight(1f)
        ) {
            SeamlessLogoBox(
                emblemType = BrandEmblemType.GEMINI_NODEGUARD,
                boxSize = 40.dp,
                modifier = Modifier
                    .clickable(onClick = onOpenReleaseManifest)
                    .testTag("brand_seamless_logo")
            )
            Spacer(modifier = Modifier.width(8.dp))
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "GEMINI NODEGUARD",
                        style = MaterialTheme.typography.labelLarge,
                        color = palette.textPrimary,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    if (isOtaUpdateAvailable) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(NeonAmber.copy(alpha = 0.22f))
                                .border(1.dp, NeonAmber, RoundedCornerShape(6.dp))
                                .clickable(onClick = onOpenOtaModal)
                                .padding(horizontal = 5.dp, vertical = 1.dp)
                                .testTag("header_ota_v2_badge")
                        ) {
                            Text(
                                text = "v2.0 OTA",
                                style = MaterialTheme.typography.labelSmall,
                                color = NeonAmber,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1
                            )
                        }
                    } else {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(
                                    if (hasSealedVault) NeonEmerald.copy(alpha = 0.16f) else NeonAmber.copy(alpha = 0.16f)
                                )
                                .border(
                                    width = 0.8.dp,
                                    color = if (hasSealedVault) NeonEmerald else NeonAmber,
                                    shape = RoundedCornerShape(6.dp)
                                )
                                .clickable(onClick = onOpenReleaseManifest)
                                .padding(horizontal = 5.dp, vertical = 1.dp)
                                .testTag("header_release_manifest_badge")
                        ) {
                            Text(
                                text = if (hasSealedVault) "v1.0.0 • AES-256" else "v1.0.0 • ZEROIZED",
                                style = MaterialTheme.typography.labelSmall,
                                color = if (hasSealedVault) NeonEmerald else NeonAmber,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1
                            )
                        }
                    }
                }
                Text(
                    text = if (isFlagSecureActive) {
                        "PRIVACY SHIELD • IPs, UUIDs & SNIs Masked"
                    } else {
                        "PRIVACY OFF • Raw Telemetry Unmasked"
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = palette.textSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        Spacer(modifier = Modifier.width(6.dp))

        // Right Controls: Sound FX Toggle (Speaker + Animated Soundwaves) + Dark/Light Mode + Privacy Shield
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            // Sound FX Mute/Unmute Toggle Button with Animated Soundwaves
            Box(
                modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .clip(RoundedCornerShape(12.dp))
                    .background(if (isSoundEnabled) soundAccent.copy(alpha = 0.14f) else palette.surface)
                    .border(
                        width = if (isSoundEnabled) 1.2.dp else 1.dp,
                        color = if (isSoundEnabled) soundAccent.copy(alpha = 0.7f) else palette.cardBorder,
                        shape = RoundedCornerShape(12.dp)
                    )
                    .clickable(onClick = onToggleSoundEnabled)
                    .padding(horizontal = 8.dp, vertical = 8.dp)
                    .testTag("sound_fx_toggle_button"),
                contentAlignment = Alignment.Center
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(3.dp)
                ) {
                    Icon(
                        imageVector = if (isSoundEnabled) {
                            Icons.AutoMirrored.Filled.VolumeUp
                        } else {
                            Icons.AutoMirrored.Filled.VolumeOff
                        },
                        contentDescription = if (isSoundEnabled) "Mute Sci-Fi Audio FX" else "Unmute Sci-Fi Audio FX",
                        tint = soundAccent,
                        modifier = Modifier.size(15.dp)
                    )
                    // Animated 3-bar holographic soundwave indicator
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(1.5.dp),
                        modifier = Modifier.height(12.dp)
                    ) {
                        for (barIdx in 0..2) {
                            val dynamicFraction = if (isSoundEnabled) {
                                (0.35f + 0.65f * ((sin(wavePhase + barIdx * 1.4f) + 1f) * 0.5f))
                                    .coerceIn(0.25f, 1f)
                            } else {
                                0.25f
                            }
                            Box(
                                modifier = Modifier
                                    .width(2.dp)
                                    .height((11f * dynamicFraction).dp)
                                    .clip(RoundedCornerShape(2.dp))
                                    .background(soundAccent)
                            )
                        }
                    }
                }
            }

            // Live Dark / Light Mode Toggle Button
            Box(
                modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .clip(RoundedCornerShape(12.dp))
                    .background(palette.surface)
                    .border(1.dp, palette.cardBorder, RoundedCornerShape(12.dp))
                    .clickable(onClick = onToggleThemeMode)
                    .padding(horizontal = 8.dp, vertical = 8.dp)
                    .testTag("theme_mode_toggle_button"),
                contentAlignment = Alignment.Center
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = if (isDarkMode) Icons.Default.LightMode else Icons.Default.DarkMode,
                        contentDescription = if (isDarkMode) "Switch to Light Mode" else "Switch to Dark Mode",
                        tint = if (isDarkMode) NeonCyan else DeepSapphire,
                        modifier = Modifier
                            .size(15.dp)
                            .rotate(themeIconRotation)
                    )
                    Spacer(modifier = Modifier.width(3.dp))
                    Text(
                        text = if (isDarkMode) "DARK" else "LIGHT",
                        style = MaterialTheme.typography.labelSmall,
                        color = palette.textPrimary,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            // Global Privacy Mode (Eye Icon) Toggle Button
            Box(
                modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .clip(RoundedCornerShape(12.dp))
                    .background(shieldColor.copy(alpha = 0.14f))
                    .border(1.5.dp, shieldColor, RoundedCornerShape(12.dp))
                    .clickable(onClick = onTogglePrivacyShield)
                    .padding(horizontal = 8.dp, vertical = 8.dp)
                    .testTag("global_privacy_shield_toggle"),
                contentAlignment = Alignment.Center
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = if (isPrivacyShieldActive) {
                            Icons.Default.VisibilityOff
                        } else {
                            Icons.Default.Visibility
                        },
                        contentDescription = "Toggle Privacy Mode (Mask IPs & UUIDs + FLAG_SECURE)",
                        tint = shieldColor,
                        modifier = Modifier.size(15.dp)
                    )
                    Spacer(modifier = Modifier.width(3.dp))
                    Text(
                        text = if (isPrivacyShieldActive) "PRIVACY" else "RAW",
                        style = MaterialTheme.typography.labelSmall,
                        color = shieldColor,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}
