package com.example.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.example.ui.theme.AmbientMeshSapphire
import com.example.ui.theme.AmbientMeshViolet
import com.example.ui.theme.DeepSapphire
import com.example.ui.theme.ElectricPurple
import com.example.ui.theme.ElectricViolet
import com.example.ui.theme.LocalNodeGuardColors
import com.example.ui.theme.NeonCyan
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

@Immutable
private data class CosmicParticleNode(
    val normX: Float,
    val normY: Float,
    val speedX: Float,
    val speedY: Float,
    val baseRadiusDp: Float,
    val phaseOffset: Float,
    val isAccent: Boolean
)

/**
 * Obsidian Spatial Glass & Alabaster Frosted Quartz Atmospheric Background (120 FPS Zero-Allocation):
 * - Dark Mode: Deep atmospheric base (`#040711`) with ambient mesh-gradient light pools
 *   (`radialGradient` with 8% opacity Sapphire `#2563EB` and Violet `#7C3AED` in the top viewport).
 * - Light Mode: Pure seamless white top viewport (`#FFFFFF`) transitioning into soft alabaster
 *   pearl (`#F8FAFC`) with subtle sapphire/indigo light pools.
 * - Zero objects (`Brush`, `FloatArray`, `Color`) allocated inside `onDrawBehind` during animation frames.
 */
@Composable
fun CosmicParticleBackground(
    isScanningActive: Boolean,
    activeWorkers: Int,
    modifier: Modifier = Modifier
) {
    val palette = LocalNodeGuardColors.current
    val infiniteTransition = rememberInfiniteTransition(label = "cosmic_nebula_transition")

    val speedDuration = when {
        isScanningActive && activeWorkers > 8 -> 6200
        isScanningActive -> 9000
        else -> 20000
    }

    val orbitalPhase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = speedDuration, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "cosmic_orbital_phase"
    )

    val nebulaPulse by infiniteTransition.animateFloat(
        initialValue = 0.82f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 4200, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "cosmic_nebula_pulse"
    )

    val particles = remember {
        listOf(
            CosmicParticleNode(0.12f, 0.14f, 0.20f, 0.16f, 2.0f, 0.1f, true),
            CosmicParticleNode(0.28f, 0.09f, -0.14f, 0.22f, 1.5f, 0.7f, false),
            CosmicParticleNode(0.46f, 0.19f, 0.18f, -0.14f, 2.2f, 1.3f, true),
            CosmicParticleNode(0.74f, 0.12f, -0.19f, 0.17f, 1.7f, 2.1f, false),
            CosmicParticleNode(0.88f, 0.24f, -0.13f, -0.20f, 2.1f, 2.8f, true),
            CosmicParticleNode(0.18f, 0.38f, 0.15f, 0.19f, 1.6f, 3.4f, false),
            CosmicParticleNode(0.36f, 0.44f, -0.21f, 0.13f, 1.9f, 4.0f, true),
            CosmicParticleNode(0.62f, 0.36f, 0.14f, 0.22f, 1.7f, 4.6f, false),
            CosmicParticleNode(0.83f, 0.48f, -0.17f, -0.15f, 2.1f, 5.2f, true),
            CosmicParticleNode(0.11f, 0.64f, 0.20f, -0.17f, 1.8f, 0.4f, false),
            CosmicParticleNode(0.31f, 0.71f, -0.14f, 0.20f, 2.1f, 1.1f, true),
            CosmicParticleNode(0.54f, 0.66f, 0.18f, -0.16f, 1.5f, 1.9f, false),
            CosmicParticleNode(0.77f, 0.69f, -0.16f, 0.18f, 2.0f, 2.6f, true),
            CosmicParticleNode(0.91f, 0.78f, -0.19f, -0.13f, 1.6f, 3.2f, false),
            CosmicParticleNode(0.22f, 0.89f, 0.15f, -0.21f, 1.9f, 3.9f, true),
            CosmicParticleNode(0.49f, 0.86f, -0.18f, 0.15f, 1.7f, 4.7f, false)
        )
    }

    // Pre-allocated scratch buffers so zero arrays or Offset objects are created on the UI thread during draw
    val particleXBuffer = remember { FloatArray(particles.size) }
    val particleYBuffer = remember { FloatArray(particles.size) }

    Spacer(
        modifier = modifier
            .fillMaxSize()
            .drawWithCache {
                val width = size.width
                val height = size.height
                if (width <= 0f || height <= 0f) {
                    return@drawWithCache onDrawBehind {}
                }

                val poolRadiusSapphire = maxOf(width * 0.78f, height * 0.48f, 1f)
                val poolRadiusViolet = maxOf(width * 0.72f, height * 0.44f, 1f)

                val topSapphirePoolCenter = Offset(width * 0.18f, height * 0.12f)
                val topVioletPoolCenter = Offset(width * 0.84f, height * 0.16f)
                val lowerAmbientCenter = Offset(width * 0.50f, height * 0.82f)

                val primaryTone = if (palette.isDark) NeonCyan else DeepSapphire
                val secondaryTone = if (palette.isDark) ElectricPurple else ElectricViolet

                // 8% opacity Sapphire (#2563EB) & Violet (#7C3AED) ambient mesh-gradient light pools in top viewport
                val topSapphirePoolBrush = Brush.radialGradient(
                    colors = listOf(
                        AmbientMeshSapphire.copy(alpha = if (palette.isDark) 0.08f else 0.045f),
                        primaryTone.copy(alpha = if (palette.isDark) 0.03f else 0.015f),
                        Color.Transparent
                    ),
                    center = topSapphirePoolCenter,
                    radius = poolRadiusSapphire
                )

                val topVioletPoolBrush = Brush.radialGradient(
                    colors = listOf(
                        AmbientMeshViolet.copy(alpha = if (palette.isDark) 0.08f else 0.04f),
                        secondaryTone.copy(alpha = if (palette.isDark) 0.025f else 0.012f),
                        Color.Transparent
                    ),
                    center = topVioletPoolCenter,
                    radius = poolRadiusViolet
                )

                val lowerAmbientBrush = Brush.radialGradient(
                    colors = listOf(
                        AmbientMeshSapphire.copy(alpha = if (palette.isDark) 0.045f else 0.025f),
                        Color.Transparent
                    ),
                    center = lowerAmbientCenter,
                    radius = poolRadiusSapphire
                )

                val gridStepPx = 64.dp.toPx().coerceAtLeast(28f)
                val linkThresholdPx = 142.dp.toPx()
                val linkStrokeWidth = 0.8.dp.toPx()
                val gridStrokeWidth = 0.5.dp.toPx()
                val gridColor = primaryTone.copy(alpha = if (palette.isDark) 0.025f else 0.032f)

                val accentDotHaloColor = primaryTone.copy(alpha = if (palette.isDark) 0.18f else 0.11f)
                val accentDotCoreColor = primaryTone.copy(alpha = if (palette.isDark) 0.65f else 0.45f)
                val secondaryDotHaloColor = secondaryTone.copy(alpha = if (palette.isDark) 0.18f else 0.11f)
                val secondaryDotCoreColor = secondaryTone.copy(alpha = if (palette.isDark) 0.65f else 0.45f)

                val radiusPxArray = FloatArray(particles.size) { idx ->
                    particles[idx].baseRadiusDp.dp.toPx().coerceAtLeast(1f)
                }

                onDrawBehind {
                    if (size.width <= 0f || size.height <= 0f) return@onDrawBehind

                    // 1. Ambient Mesh-Gradient Light Pools (Sapphire #2563EB & Violet #7C3AED at 8% opacity)
                    drawCircle(
                        brush = topSapphirePoolBrush,
                        radius = maxOf(poolRadiusSapphire * nebulaPulse, 1f),
                        center = topSapphirePoolCenter
                    )
                    drawCircle(
                        brush = topVioletPoolBrush,
                        radius = maxOf(poolRadiusViolet * nebulaPulse, 1f),
                        center = topVioletPoolCenter
                    )
                    drawCircle(
                        brush = lowerAmbientBrush,
                        radius = maxOf(poolRadiusSapphire * 0.88f, 1f),
                        center = lowerAmbientCenter
                    )

                    // 2. Ultra-subtle Spatial Architectural Grid
                    var gx = 0f
                    while (gx < width) {
                        drawLine(
                            color = gridColor,
                            start = Offset(gx, 0f),
                            end = Offset(gx, height),
                            strokeWidth = gridStrokeWidth
                        )
                        gx += gridStepPx
                    }
                    var gy = 0f
                    while (gy < height) {
                        drawLine(
                            color = gridColor,
                            start = Offset(0f, gy),
                            end = Offset(width, gy),
                            strokeWidth = gridStrokeWidth
                        )
                        gy += gridStepPx
                    }

                    // 3. Update pre-allocated particle buffers (zero heap allocations)
                    val boost = if (isScanningActive) 1.75f else 1.0f
                    val count = particles.size
                    for (idx in 0 until count) {
                        val p = particles[idx]
                        val angle = (orbitalPhase * 6.2831855f * boost) + p.phaseOffset
                        val dx = sin(angle) * p.speedX * 0.08f
                        val dy = cos(angle * 0.85f) * p.speedY * 0.08f
                        val wrappedX = ((p.normX + dx) % 1.0f + 1.0f) % 1.0f
                        val wrappedY = ((p.normY + dy) % 1.0f + 1.0f) % 1.0f
                        particleXBuffer[idx] = wrappedX * width
                        particleYBuffer[idx] = wrappedY * height
                    }

                    // 4. Constellation telemetry links
                    val maxLineAlpha = if (isScanningActive) 0.16f else 0.085f
                    for (i in 0 until count) {
                        val x1 = particleXBuffer[i]
                        val y1 = particleYBuffer[i]
                        for (j in i + 1 until count) {
                            val x2 = particleXBuffer[j]
                            val y2 = particleYBuffer[j]
                            val dx = x1 - x2
                            val dy = y1 - y2
                            if (abs(dx) < linkThresholdPx && abs(dy) < linkThresholdPx) {
                                val dist = sqrt(dx * dx + dy * dy)
                                if (dist < linkThresholdPx) {
                                    val proximity = (1f - (dist / linkThresholdPx)).coerceIn(0f, 1f)
                                    drawLine(
                                        color = primaryTone.copy(alpha = proximity * maxLineAlpha),
                                        start = Offset(x1, y1),
                                        end = Offset(x2, y2),
                                        strokeWidth = linkStrokeWidth
                                    )
                                }
                            }
                        }
                    }

                    // 5. Crisp floating telemetry nodes
                    for (i in 0 until count) {
                        val pt = Offset(particleXBuffer[i], particleYBuffer[i])
                        val node = particles[i]
                        val rPx = radiusPxArray[i]
                        drawCircle(
                            color = if (node.isAccent) accentDotHaloColor else secondaryDotHaloColor,
                            radius = (rPx * 2.2f).coerceAtLeast(1f),
                            center = pt
                        )
                        drawCircle(
                            color = if (node.isAccent) accentDotCoreColor else secondaryDotCoreColor,
                            radius = rPx,
                            center = pt
                        )
                    }
                }
            }
    )
}
