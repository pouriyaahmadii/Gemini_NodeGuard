package com.example.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.model.ProbeStatus
import com.example.ui.theme.ElectricPurple
import com.example.ui.theme.LocalNodeGuardColors
import com.example.ui.theme.NeonCyan
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin
import kotlinx.coroutines.launch

@Immutable
private data class CompassTickSegment(
    val start: Offset,
    val end: Offset,
    val tier: Int // 2 = cardinal (45°), 1 = major (15°), 0 = fine (5°)
)

@Immutable
private data class WaveformRayVector(
    val cosAngle: Float,
    val sinAngle: Float,
    val phaseSeed: Float
)

/**
 * 3D-Depth CyberShieldRadar (120FPS Zero-Allocation Precision Instrument):
 * - Outer Ring: 360° compass rose with 72 fine degree tick-marks rotating smoothly at low opacity.
 * - Middle Ring: Counter-rotating segmented arc gauge with smooth easing + secondary orbital arcs.
 * - Inner Core: Soft sine-curve breathing aura + sweep beam with gradient trail (`sweepGradient`)
 *   and crisp vector shield emblem in the center.
 * - All `Brush`, `Path`, `Stroke`, and tick geometries are memoized inside `drawWithCache` so
 *   zero objects are allocated inside `onDrawBehind` during 60/120fps frames.
 */
@Composable
fun CyberShieldRadar(
    probeStatus: ProbeStatus,
    isProbing: Boolean,
    ttftMs: Int,
    onTriggerProbe: () -> Unit,
    modifier: Modifier = Modifier,
    shockwaveTrigger: Int = 0
) {
    val palette = LocalNodeGuardColors.current
    val infiniteTransition = rememberInfiniteTransition(label = "cyber_radar_transition")
    val coroutineScope = rememberCoroutineScope()

    val shockwaveProgress = remember { Animatable(0f) }

    LaunchedEffect(shockwaveTrigger, isProbing) {
        if (shockwaveTrigger > 0 || isProbing) {
            shockwaveProgress.snapTo(0.01f)
            shockwaveProgress.animateTo(
                targetValue = 1f,
                animationSpec = tween(durationMillis = 820, easing = FastOutSlowInEasing)
            )
            shockwaveProgress.snapTo(0f)
        }
    }

    // 1. Primary Radar Sweep Beam (360° continuous)
    val sweepAngle by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = if (isProbing) 1450 else 3800,
                easing = LinearEasing
            ),
            repeatMode = RepeatMode.Restart
        ),
        label = "radar_sweep_angle"
    )

    // 2. Outer 360° Compass Rose Rotation (fine degree ticks)
    val outerCompassRotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = if (isProbing) 12000 else 28000,
                easing = LinearEasing
            ),
            repeatMode = RepeatMode.Restart
        ),
        label = "outer_compass_rotation"
    )

    // 3. Middle Counter-Rotating Segmented Arc Gauge
    val middleGaugeAngle by infiniteTransition.animateFloat(
        initialValue = 360f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = if (isProbing) 2600 else 6800,
                easing = LinearEasing
            ),
            repeatMode = RepeatMode.Restart
        ),
        label = "middle_gauge_angle"
    )

    // 4. Inner Orbital Precision Ring
    val innerOrbitalAngle by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = if (isProbing) 2100 else 5400,
                easing = LinearEasing
            ),
            repeatMode = RepeatMode.Restart
        ),
        label = "inner_orbital_angle"
    )

    // 5. Concentric Sonar Wave (0f -> 1f)
    val sonarPulse by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = if (isProbing) 1150 else 2600,
                easing = FastOutSlowInEasing
            ),
            repeatMode = RepeatMode.Restart
        ),
        label = "sonar_pulse_wave"
    )

    // 6. Sine-Curve Breathing Aura Phase (0f -> 2π)
    val breathingPhase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 6.2831855f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2800, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "core_breathing_phase"
    )

    val probingColorShift by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 900, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "probing_color_shift"
    )

    val targetPrimaryColor = if (probeStatus == ProbeStatus.PROBING) {
        lerpColor(NeonCyan, ElectricPurple, probingColorShift)
    } else {
        probeStatus.primaryColor
    }

    val animatedPrimaryColor by animateColorAsState(
        targetValue = targetPrimaryColor,
        animationSpec = tween(durationMillis = 420),
        label = "animated_primary_color"
    )

    val animatedSecondaryColor by animateColorAsState(
        targetValue = probeStatus.secondaryColor,
        animationSpec = tween(durationMillis = 420),
        label = "animated_secondary_color"
    )

    val radarDescription = stringResource(id = R.string.cd_shield_radar)
    val interactionSource = remember { MutableInteractionSource() }

    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(268.dp)
                .clip(CircleShape)
                .semantics {
                    contentDescription = radarDescription
                    role = Role.Button
                }
                .clickable(
                    interactionSource = interactionSource,
                    indication = ripple(bounded = true, color = animatedPrimaryColor),
                    onClick = {
                        coroutineScope.launch {
                            shockwaveProgress.snapTo(0.01f)
                            shockwaveProgress.animateTo(
                                targetValue = 1f,
                                animationSpec = tween(durationMillis = 820, easing = FastOutSlowInEasing)
                            )
                            shockwaveProgress.snapTo(0f)
                        }
                        onTriggerProbe()
                    }
                )
                .testTag("cyber_shield_radar"),
            contentAlignment = Alignment.Center
        ) {
            Spacer(
                modifier = Modifier
                    .fillMaxSize()
                    .drawWithCache {
                        if (size.width <= 0f || size.height <= 0f) {
                            return@drawWithCache onDrawBehind {}
                        }

                        val canvasWidth = size.width
                        val canvasHeight = size.height
                        val centerPt = Offset(canvasWidth / 2f, canvasHeight / 2f)
                        val maxRadius = maxOf(min(canvasWidth, canvasHeight) / 2f, 1f)

                        val outerBezelRadius = maxOf(maxRadius * 0.96f, 1f)
                        val sweepRadius = maxOf(maxRadius * 0.85f, 1f)
                        val middleGaugeRadius = maxOf(maxRadius * 0.74f, 1f)
                        val middleGaugeSize = Size(middleGaugeRadius * 2f, middleGaugeRadius * 2f)
                        val middleGaugeTopLeft = Offset(
                            centerPt.x - middleGaugeRadius,
                            centerPt.y - middleGaugeRadius
                        )

                        val innerArcRadius = maxOf(maxRadius * 0.59f, 1f)
                        val innerArcSize = Size(innerArcRadius * 2f, innerArcRadius * 2f)
                        val innerArcTopLeft = Offset(
                            centerPt.x - innerArcRadius,
                            centerPt.y - innerArcRadius
                        )

                        val coreRadius = maxOf(maxRadius * 0.34f, 1f)
                        val axisRadius = maxRadius * 0.80f
                        val waveformBaseRadius = maxRadius * 0.42f

                        // Pre-allocated Strokes (zero per-frame allocations)
                        val outerBezelStroke = Stroke(width = 0.85.dp.toPx())
                        val outerRingStroke = Stroke(width = 1.4.dp.toPx())
                        val innerRingStroke = Stroke(width = 0.85.dp.toPx())
                        val pulseStroke = Stroke(width = 2.0.dp.toPx())
                        val shockwaveStroke = Stroke(width = 3.0.dp.toPx())
                        val middleGaugeStroke = Stroke(width = 3.2.dp.toPx(), cap = StrokeCap.Round)
                        val middleTrackStroke = Stroke(width = 1.2.dp.toPx(), cap = StrokeCap.Round)
                        val innerOrbitalStroke = Stroke(width = 1.8.dp.toPx(), cap = StrokeCap.Round)
                        val coreBorderStroke = Stroke(width = 1.8.dp.toPx())
                        val coreInnerSpecularStroke = Stroke(width = 0.9.dp.toPx())
                        val shieldCrestStroke = Stroke(
                            width = 2.1.dp.toPx(),
                            cap = StrokeCap.Round,
                            join = StrokeJoin.Round
                        )

                        val cardinalTickWidth = 1.9.dp.toPx()
                        val majorTickWidth = 1.3.dp.toPx()
                        val fineTickWidth = 0.8.dp.toPx()
                        val axisStrokeWidth = 0.8.dp.toPx()
                        val sweepBeamStrokeWidth = 2.2.dp.toPx()
                        val waveformBarStrokeWidth = 2.0.dp.toPx()

                        // Pre-compute 72 Compass Rose degree ticks (every 5 degrees)
                        val tickOuterRadius = maxRadius * 0.94f
                        val cachedCompassTicks = buildList(72) {
                            for (deg in 0 until 360 step 5) {
                                val rad = Math.toRadians(deg.toDouble())
                                val tier = when {
                                    deg % 45 == 0 -> 2
                                    deg % 15 == 0 -> 1
                                    else -> 0
                                }
                                val tickInnerRadius = when (tier) {
                                    2 -> maxRadius * 0.84f
                                    1 -> maxRadius * 0.875f
                                    else -> maxRadius * 0.905f
                                }
                                add(
                                    CompassTickSegment(
                                        start = Offset(
                                            x = centerPt.x + (tickInnerRadius * cos(rad)).toFloat(),
                                            y = centerPt.y + (tickInnerRadius * sin(rad)).toFloat()
                                        ),
                                        end = Offset(
                                            x = centerPt.x + (tickOuterRadius * cos(rad)).toFloat(),
                                            y = centerPt.y + (tickOuterRadius * sin(rad)).toFloat()
                                        ),
                                        tier = tier
                                    )
                                )
                            }
                        }

                        // Pre-compute 36 audio-reactive waveform ray vectors around the core
                        val cachedWaveformRays = buildList(36) {
                            for (i in 0 until 36) {
                                val rad = Math.toRadians((i * 10).toDouble())
                                add(
                                    WaveformRayVector(
                                        cosAngle = cos(rad).toFloat(),
                                        sinAngle = sin(rad).toFloat(),
                                        phaseSeed = i * 0.55f
                                    )
                                )
                            }
                        }

                        // Pre-compute 3D Shield Emblem Path + Inner Facet Path
                        val shieldW = coreRadius * 0.86f
                        val shieldH = coreRadius * 1.02f
                        val shieldPath = Path().apply {
                            moveTo(centerPt.x, centerPt.y - shieldH * 0.52f)
                            lineTo(centerPt.x + shieldW * 0.52f, centerPt.y - shieldH * 0.28f)
                            lineTo(centerPt.x + shieldW * 0.52f, centerPt.y + shieldH * 0.08f)
                            cubicTo(
                                centerPt.x + shieldW * 0.52f, centerPt.y + shieldH * 0.36f,
                                centerPt.x + shieldW * 0.22f, centerPt.y + shieldH * 0.50f,
                                centerPt.x, centerPt.y + shieldH * 0.58f
                            )
                            cubicTo(
                                centerPt.x - shieldW * 0.22f, centerPt.y + shieldH * 0.50f,
                                centerPt.x - shieldW * 0.52f, centerPt.y + shieldH * 0.36f,
                                centerPt.x - shieldW * 0.52f, centerPt.y + shieldH * 0.08f
                            )
                            lineTo(centerPt.x - shieldW * 0.52f, centerPt.y - shieldH * 0.28f)
                            close()
                        }

                        // Pre-memoized Brushes inside drawWithCache (zero per-frame Brush allocations)
                        val ambientAuraBrush = Brush.radialGradient(
                            colors = listOf(
                                animatedPrimaryColor.copy(alpha = if (palette.isDark) 0.28f else 0.16f),
                                animatedSecondaryColor.copy(alpha = if (palette.isDark) 0.11f else 0.05f),
                                palette.logoEdgeBackground.copy(alpha = 0.0f)
                            ),
                            center = centerPt,
                            radius = maxRadius
                        )

                        val radarSweepBrush = Brush.sweepGradient(
                            0.0f to Color.Transparent,
                            0.62f to Color.Transparent,
                            0.84f to animatedSecondaryColor.copy(alpha = 0.12f),
                            0.96f to animatedPrimaryColor.copy(alpha = 0.36f),
                            1.0f to animatedPrimaryColor.copy(alpha = 0.62f),
                            center = centerPt
                        )

                        val sweepBeamEnd = Offset(centerPt.x + sweepRadius, centerPt.y)
                        val sweepBeamBrush = Brush.linearGradient(
                            colors = listOf(
                                animatedPrimaryColor.copy(alpha = 0.10f),
                                animatedPrimaryColor
                            ),
                            start = centerPt,
                            end = sweepBeamEnd
                        )

                        val coreGlassBrush = Brush.radialGradient(
                            colors = listOf(
                                palette.logoEdgeBackground.copy(alpha = 0.96f),
                                palette.logoEdgeBackground.copy(alpha = 0.90f)
                            ),
                            center = centerPt,
                            radius = coreRadius
                        )

                        val shieldFillBrush = Brush.verticalGradient(
                            colors = listOf(
                                animatedPrimaryColor.copy(alpha = 0.24f),
                                animatedSecondaryColor.copy(alpha = 0.06f)
                            ),
                            startY = centerPt.y - shieldH * 0.5f,
                            endY = maxOf(centerPt.y + shieldH * 0.5f, centerPt.y + 1f)
                        )

                        // Pre-computed static colors for ticks & rings
                        val outerBezelColor = animatedPrimaryColor.copy(alpha = 0.22f)
                        val ringColorOuter = animatedPrimaryColor.copy(alpha = 0.30f)
                        val ringColorInner = animatedPrimaryColor.copy(alpha = 0.14f)
                        val tickColorCardinal = animatedPrimaryColor.copy(alpha = 0.78f)
                        val tickColorMajor = animatedPrimaryColor.copy(alpha = 0.42f)
                        val tickColorFine = animatedPrimaryColor.copy(alpha = 0.18f)
                        val axisLineColor = animatedPrimaryColor.copy(alpha = 0.11f)
                        val middleTrackColor = animatedPrimaryColor.copy(alpha = 0.12f)
                        val middleArcPrimary = animatedPrimaryColor.copy(alpha = 0.68f)
                        val middleArcSecondary = animatedSecondaryColor.copy(alpha = 0.58f)
                        val innerArcPrimary = animatedPrimaryColor.copy(alpha = 0.46f)
                        val innerArcSecondary = animatedSecondaryColor.copy(alpha = 0.42f)
                        val coreBorderColor = animatedPrimaryColor.copy(alpha = 0.68f)
                        val coreSpecularColor = Color.White.copy(alpha = if (palette.isDark) 0.22f else 0.65f)

                        val ringFractions = floatArrayOf(0.34f, 0.50f, 0.66f, 0.84f)
                        val middleGaugeSegments = floatArrayOf(0f, 60f, 120f, 180f, 240f, 300f)

                        onDrawBehind {
                            if (size.width <= 0f || size.height <= 0f) return@onDrawBehind

                            val sineBreathing = 0.88f + 0.12f * ((sin(breathingPhase) + 1f) * 0.5f)

                            // 1. Deep 3D Ambient Breathing Aura
                            drawCircle(
                                brush = ambientAuraBrush,
                                radius = maxOf(maxRadius * sineBreathing, 1f),
                                center = centerPt
                            )

                            // 2. Outer Instrument Bezel Ring + Concentric Telemetry Rings
                            drawCircle(
                                color = outerBezelColor,
                                radius = outerBezelRadius,
                                center = centerPt,
                                style = outerBezelStroke
                            )
                            for (idx in ringFractions.indices) {
                                val fraction = ringFractions[idx]
                                drawCircle(
                                    color = if (idx == 3) ringColorOuter else ringColorInner,
                                    radius = maxOf(maxRadius * fraction, 1f),
                                    center = centerPt,
                                    style = if (idx == 3) outerRingStroke else innerRingStroke
                                )
                            }

                            // 3. Expanding Sonar Wave + Shockwave Ripple on Probe Trigger
                            val pulseRadius = maxOf(maxRadius * (0.30f + 0.58f * sonarPulse), 1f)
                            val pulseAlpha = (1f - sonarPulse) * (if (isProbing) 0.62f else 0.30f)
                            drawCircle(
                                color = animatedPrimaryColor.copy(alpha = pulseAlpha),
                                radius = pulseRadius,
                                center = centerPt,
                                style = pulseStroke
                            )

                            val shockVal = shockwaveProgress.value
                            if (shockVal > 0.01f) {
                                val shockRadius = maxOf(maxRadius * (0.34f + 0.62f * shockVal), 1f)
                                val shockAlpha = (1f - shockVal).coerceIn(0f, 1f) * 0.85f
                                drawCircle(
                                    color = NeonCyan.copy(alpha = shockAlpha),
                                    radius = shockRadius,
                                    center = centerPt,
                                    style = shockwaveStroke
                                )
                                drawCircle(
                                    color = ElectricPurple.copy(alpha = shockAlpha * 0.52f),
                                    radius = maxOf(shockRadius * 0.86f, 1f),
                                    center = centerPt,
                                    style = pulseStroke
                                )
                            }

                            // 4. Outer Ring: 360° Compass Rose (72 Fine Degree Tick-Marks) with smooth rotation
                            rotate(degrees = outerCompassRotation, pivot = centerPt) {
                                for (i in cachedCompassTicks.indices) {
                                    val tick = cachedCompassTicks[i]
                                    drawLine(
                                        color = when (tick.tier) {
                                            2 -> tickColorCardinal
                                            1 -> tickColorMajor
                                            else -> tickColorFine
                                        },
                                        start = tick.start,
                                        end = tick.end,
                                        strokeWidth = when (tick.tier) {
                                            2 -> cardinalTickWidth
                                            1 -> majorTickWidth
                                            else -> fineTickWidth
                                        },
                                        cap = StrokeCap.Round
                                    )
                                }
                            }

                            // 5. Precision Crosshair Axis Lines
                            drawLine(
                                color = axisLineColor,
                                start = Offset(centerPt.x - axisRadius, centerPt.y),
                                end = Offset(centerPt.x + axisRadius, centerPt.y),
                                strokeWidth = axisStrokeWidth
                            )
                            drawLine(
                                color = axisLineColor,
                                start = Offset(centerPt.x, centerPt.y - axisRadius),
                                end = Offset(centerPt.x, centerPt.y + axisRadius),
                                strokeWidth = axisStrokeWidth
                            )

                            // 6. Middle Ring: Counter-Rotating Segmented Arc Gauge
                            drawCircle(
                                color = middleTrackColor,
                                radius = middleGaugeRadius,
                                center = centerPt,
                                style = middleTrackStroke
                            )
                            rotate(degrees = middleGaugeAngle, pivot = centerPt) {
                                for (segIdx in middleGaugeSegments.indices) {
                                    val startDeg = middleGaugeSegments[segIdx]
                                    val isPrimarySeg = segIdx % 2 == 0
                                    drawArc(
                                        color = if (isPrimarySeg) middleArcPrimary else middleArcSecondary,
                                        startAngle = startDeg + 8f,
                                        sweepAngle = if (isPrimarySeg) 36f else 22f,
                                        useCenter = false,
                                        topLeft = middleGaugeTopLeft,
                                        size = middleGaugeSize,
                                        style = middleGaugeStroke
                                    )
                                }
                            }

                            // 7. Inner Counter-Rotating Orbital Ring
                            rotate(degrees = innerOrbitalAngle, pivot = centerPt) {
                                drawArc(
                                    color = innerArcPrimary,
                                    startAngle = 35f,
                                    sweepAngle = 68f,
                                    useCenter = false,
                                    topLeft = innerArcTopLeft,
                                    size = innerArcSize,
                                    style = innerOrbitalStroke
                                )
                                drawArc(
                                    color = innerArcSecondary,
                                    startAngle = 215f,
                                    sweepAngle = 68f,
                                    useCenter = false,
                                    topLeft = innerArcTopLeft,
                                    size = innerArcSize,
                                    style = innerOrbitalStroke
                                )
                            }

                            // 8. Sweep Beam with Gradient Trail (`sweepGradient`)
                            rotate(degrees = sweepAngle, pivot = centerPt) {
                                drawCircle(
                                    brush = radarSweepBrush,
                                    radius = sweepRadius,
                                    center = centerPt
                                )
                                drawLine(
                                    brush = sweepBeamBrush,
                                    start = centerPt,
                                    end = sweepBeamEnd,
                                    strokeWidth = sweepBeamStrokeWidth,
                                    cap = StrokeCap.Round
                                )
                            }

                            // 9. Audio-Reactive Waveform Ring Around the Inner Core
                            val sonarAmplitudeBoost = if (isProbing) 1.50f else 0.95f
                            val maxBarHeight = maxRadius * 0.095f * sonarAmplitudeBoost
                            val phaseRadians = sonarPulse * 6.2831855f
                            for (i in cachedWaveformRays.indices) {
                                val ray = cachedWaveformRays[i]
                                val waveMod = (sin(phaseRadians * 2f + ray.phaseSeed) + 1f) * 0.5f
                                val barLen = (maxRadius * 0.022f) + (maxBarHeight * waveMod)
                                val startR = waveformBaseRadius
                                val endR = waveformBaseRadius + barLen
                                drawLine(
                                    color = animatedPrimaryColor.copy(alpha = 0.28f + 0.42f * waveMod),
                                    start = Offset(
                                        x = centerPt.x + startR * ray.cosAngle,
                                        y = centerPt.y + startR * ray.sinAngle
                                    ),
                                    end = Offset(
                                        x = centerPt.x + endR * ray.cosAngle,
                                        y = centerPt.y + endR * ray.sinAngle
                                    ),
                                    strokeWidth = waveformBarStrokeWidth,
                                    cap = StrokeCap.Round
                                )
                            }

                            // 10. Inner Core Plate + Crisp Vector Shield Emblem
                            drawCircle(
                                brush = coreGlassBrush,
                                radius = coreRadius,
                                center = centerPt
                            )
                            drawCircle(
                                color = coreBorderColor,
                                radius = coreRadius,
                                center = centerPt,
                                style = coreBorderStroke
                            )
                            drawCircle(
                                color = coreSpecularColor,
                                radius = maxOf(coreRadius - 2.5.dp.toPx(), 1f),
                                center = centerPt,
                                style = coreInnerSpecularStroke
                            )

                            drawPath(
                                path = shieldPath,
                                brush = shieldFillBrush
                            )
                            drawPath(
                                path = shieldPath,
                                color = animatedPrimaryColor,
                                style = shieldCrestStroke
                            )
                        }
                    }
            )

            // Center Telemetry Readout Overlay inside Shield Core (GPU graphicsLayer breathing)
            Column(
                modifier = Modifier.graphicsLayer {
                    val scalePulse = 0.98f + 0.03f * ((sin(breathingPhase) + 1f) * 0.5f)
                    scaleX = scalePulse
                    scaleY = scalePulse
                },
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = probeStatus.codeLabel,
                    style = MaterialTheme.typography.labelSmall,
                    color = animatedPrimaryColor,
                    fontWeight = FontWeight.Bold
                )
                if (ttftMs > 0 && probeStatus != ProbeStatus.PROBING) {
                    Text(
                        text = "${ttftMs}ms",
                        style = MaterialTheme.typography.titleMedium,
                        color = palette.textPrimary,
                        fontWeight = FontWeight.Bold
                    )
                } else {
                    Text(
                        text = if (isProbing) "SCAN" else "TAP",
                        style = MaterialTheme.typography.titleMedium,
                        color = palette.textPrimary,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Status Pill Badge & Headline under Radar
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
            modifier = Modifier
                .clip(RoundedCornerShape(50))
                .background(palette.surface)
                .border(0.85.dp, animatedPrimaryColor.copy(alpha = 0.45f), RoundedCornerShape(50))
                .padding(horizontal = 16.dp, vertical = 6.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(animatedPrimaryColor)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = probeStatus.title,
                style = MaterialTheme.typography.labelLarge,
                color = animatedPrimaryColor
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = probeStatus.subtitle,
            style = MaterialTheme.typography.bodyMedium,
            color = palette.textSecondary,
            textAlign = TextAlign.Center,
            lineHeight = 19.sp,
            modifier = Modifier.padding(horizontal = 16.dp)
        )
    }
}

private fun lerpColor(start: Color, stop: Color, fraction: Float): Color {
    val f = fraction.coerceIn(0f, 1f)
    return Color(
        red = start.red + (stop.red - start.red) * f,
        green = start.green + (stop.green - start.green) * f,
        blue = start.blue + (stop.blue - start.blue) * f,
        alpha = start.alpha + (stop.alpha - start.alpha) * f
    )
}
