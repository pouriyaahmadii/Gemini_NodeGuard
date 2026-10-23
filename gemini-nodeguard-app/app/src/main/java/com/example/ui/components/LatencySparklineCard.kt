package com.example.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.ui.theme.LocalNodeGuardColors
import com.example.ui.theme.spatialGlassCard
import kotlin.math.roundToInt

@Composable
fun LatencySparklineCard(
    rttHistoryMs: List<Float>,
    accentColor: Color,
    alpnProtocol: String,
    tlsCipherSuite: String,
    modifier: Modifier = Modifier
) {
    val palette = LocalNodeGuardColors.current
    val animatedAccent by animateColorAsState(
        targetValue = accentColor,
        animationSpec = tween(400),
        label = "sparkline_accent_color"
    )

    val stats by remember(rttHistoryMs) {
        derivedStateOf {
            val minVal = (rttHistoryMs.minOrNull() ?: 0f).roundToInt()
            val maxVal = (rttHistoryMs.maxOrNull() ?: 0f).roundToInt()
            val avgVal = if (rttHistoryMs.isNotEmpty()) rttHistoryMs.average().roundToInt() else 0
            val latestVal = (rttHistoryMs.lastOrNull() ?: 0f).roundToInt()
            listOf(minVal, avgVal, maxVal, latestVal)
        }
    }
    val minRtt = stats[0]
    val avgRtt = stats[1]
    val maxRtt = stats[2]
    val latestRtt = stats[3]

    Column(
        modifier = modifier
            .fillMaxWidth()
            .spatialGlassCard(cornerRadius = 20.dp, accentTint = animatedAccent)
            .padding(18.dp)
            .testTag("latency_sparkline_card")
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = stringResource(id = R.string.bento_sparkline_title),
                    style = MaterialTheme.typography.labelMedium,
                    color = palette.textSecondary
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "$tlsCipherSuite • ALPN $alpnProtocol",
                    style = MaterialTheme.typography.labelSmall,
                    color = palette.textMuted
                )
            }
            Text(
                text = "$latestRtt ms",
                style = MaterialTheme.typography.titleLarge,
                color = animatedAccent,
                fontWeight = FontWeight.Bold
            )
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Custom Compose drawWithCache Bezier-Curve Sparkline Chart (zero draw-loop allocations)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(114.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(palette.logoEdgeBackground.copy(alpha = 0.76f))
                .border(0.75.dp, palette.cardBorder.copy(alpha = 0.65f), RoundedCornerShape(14.dp))
                .padding(horizontal = 10.dp, vertical = 10.dp)
        ) {
            Spacer(
                modifier = Modifier
                    .fillMaxSize()
                    .drawWithCache {
                        if (size.width <= 0f || size.height <= 0f || rttHistoryMs.size < 2) {
                            return@drawWithCache onDrawBehind {}
                        }

                        val width = size.width
                        val height = size.height

                        val dashEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 8f), 0f)
                        val gridStrokeWidth = 0.85.dp.toPx()
                        val curveStroke = Stroke(
                            width = 2.4.dp.toPx(),
                            cap = StrokeCap.Round,
                            join = StrokeJoin.Round
                        )
                        val outerDotRadius = 6.5.dp.toPx()
                        val innerDotRadius = 3.2.dp.toPx()
                        val sampleDotRadius = 1.9.dp.toPx()

                        val rawMin = rttHistoryMs.minOrNull() ?: 0f
                        val rawMax = rttHistoryMs.maxOrNull() ?: 100f
                        val span = (rawMax - rawMin).coerceAtLeast(16f)
                        val paddedMin = (rawMin - span * 0.15f).coerceAtLeast(0f)
                        val paddedMax = rawMax + span * 0.15f
                        val range = (paddedMax - paddedMin).coerceAtLeast(1f)

                        val points = rttHistoryMs.mapIndexed { index, value ->
                            val x = (index.toFloat() / (rttHistoryMs.size - 1).toFloat()) * width
                            val normalizedY = ((value - paddedMin) / range).coerceIn(0f, 1f)
                            val y = height - (normalizedY * height)
                            Offset(x, y)
                        }

                        val strokePath = Path().apply {
                            moveTo(points.first().x, points.first().y)
                            for (i in 0 until points.size - 1) {
                                val current = points[i]
                                val next = points[i + 1]
                                val controlX1 = current.x + (next.x - current.x) * 0.45f
                                val controlY1 = current.y
                                val controlX2 = current.x + (next.x - current.x) * 0.55f
                                val controlY2 = next.y
                                cubicTo(
                                    controlX1, controlY1,
                                    controlX2, controlY2,
                                    next.x, next.y
                                )
                            }
                        }

                        val fillPath = Path().apply {
                            addPath(strokePath)
                            lineTo(points.last().x, height)
                            lineTo(points.first().x, height)
                            close()
                        }

                        val fillGradientBrush = Brush.verticalGradient(
                            colors = listOf(
                                animatedAccent.copy(alpha = 0.30f),
                                animatedAccent.copy(alpha = 0.02f)
                            ),
                            startY = 0f,
                            endY = height.coerceAtLeast(1f)
                        )

                        val gridLineColor = palette.cardBorder.copy(alpha = 0.70f)
                        val outerDotColor = animatedAccent.copy(alpha = 0.32f)
                        val sampleDotColor = animatedAccent.copy(alpha = 0.68f)

                        onDrawBehind {
                            if (size.width <= 0f || size.height <= 0f) return@onDrawBehind

                            for (i in 0..2) {
                                val y = height * (i / 2f)
                                drawLine(
                                    color = gridLineColor,
                                    start = Offset(0f, y),
                                    end = Offset(width, y),
                                    strokeWidth = gridStrokeWidth,
                                    pathEffect = dashEffect
                                )
                            }

                            drawPath(
                                path = fillPath,
                                brush = fillGradientBrush
                            )

                            drawPath(
                                path = strokePath,
                                color = animatedAccent,
                                style = curveStroke
                            )

                            for (index in points.indices) {
                                val pt = points[index]
                                if (index == points.lastIndex) {
                                    drawCircle(
                                        color = outerDotColor,
                                        radius = outerDotRadius,
                                        center = pt
                                    )
                                    drawCircle(
                                        color = palette.textPrimary,
                                        radius = innerDotRadius,
                                        center = pt
                                    )
                                } else if (index % 2 == 0) {
                                    drawCircle(
                                        color = sampleDotColor,
                                        radius = sampleDotRadius,
                                        center = pt
                                    )
                                }
                            }
                        }
                    }
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Min / Avg / Peak telemetry summary footer with tabular numerals
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            SparklineStatPill(label = "MIN RTT", value = "${minRtt}ms")
            SparklineStatPill(label = "MEAN RTT", value = "${avgRtt}ms")
            SparklineStatPill(label = "PEAK RTT", value = "${maxRtt}ms")
        }
    }
}

@Composable
private fun SparklineStatPill(
    label: String,
    value: String
) {
    val palette = LocalNodeGuardColors.current
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = "$label: ",
            style = MaterialTheme.typography.labelSmall,
            color = palette.textMuted
        )
        Text(
            text = value,
            style = MaterialTheme.typography.labelMedium,
            color = palette.textPrimary,
            fontWeight = FontWeight.Bold
        )
    }
}
