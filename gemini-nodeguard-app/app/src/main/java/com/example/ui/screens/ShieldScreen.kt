package com.example.ui.screens

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Radar
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.model.EdgeTelemetry
import com.example.model.GoogleServiceStatusItem
import com.example.model.ProbeStatus
import com.example.model.ShieldUiState
import com.example.model.SimulationScenario
import com.example.model.StageState
import com.example.model.ValidationStage
import com.example.ui.components.BrandEmblemType
import com.example.ui.components.CyberShieldRadar
import com.example.ui.components.LatencySparklineCard
import com.example.ui.components.SeamlessLogoBox
import com.example.ui.theme.ElectricPurple
import com.example.ui.theme.LocalNodeGuardColors
import com.example.ui.theme.NeonAmber
import com.example.ui.theme.NeonCrimson
import com.example.ui.theme.NeonCyan
import com.example.ui.theme.NeonEmerald
import com.example.ui.theme.RoyalSapphire
import com.example.ui.theme.spatialGlassCard
import java.util.Locale

@Composable
fun ShieldScreen(
    uiState: ShieldUiState,
    onSelectScenario: (SimulationScenario) -> Unit,
    onStartProbe: () -> Unit,
    onToggleRedaction: () -> Unit,
    modifier: Modifier = Modifier
) {
    val palette = LocalNodeGuardColors.current
    val activeAccent by animateColorAsState(
        targetValue = uiState.probeStatus.primaryColor,
        animationSpec = tween(400),
        label = "shield_screen_accent"
    )

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Transparent),
        contentAlignment = Alignment.TopCenter
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 680.dp)
                .testTag("shield_lazy_column"),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 1. Spatial Glass Top Header & Seamless Logo Banner
            item(key = "shield_header", contentType = "header") {
                ShieldHeaderBanner(
                    uiState = uiState,
                    accentColor = activeAccent,
                    onToggleRedaction = onToggleRedaction
                )
            }

            // 2. Top Simulation Scenario Chip Selector
            item(key = "scenario_selector", contentType = "chips") {
                ScenarioSelectorRow(
                    selectedScenario = uiState.selectedScenario,
                    onSelectScenario = onSelectScenario
                )
            }

            // 3. Centerpiece 3D-Depth CyberShieldRadar & Primary Probe CTA
            item(key = "cyber_radar_core", contentType = "radar") {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .spatialGlassCard(cornerRadius = 24.dp, accentTint = activeAccent, elevationDp = 8.dp)
                        .padding(horizontal = 18.dp, vertical = 20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    CyberShieldRadar(
                        probeStatus = uiState.probeStatus,
                        isProbing = uiState.isProbing,
                        ttftMs = uiState.telemetry.ttftMs,
                        onTriggerProbe = onStartProbe,
                        shockwaveTrigger = uiState.probeShockwaveTrigger
                    )

                    Spacer(modifier = Modifier.height(18.dp))

                    Button(
                        onClick = onStartProbe,
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = activeAccent,
                            contentColor = if (palette.isDark) Color(0xFF040711) else Color.White
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp)
                            .testTag("start_probe_button")
                    ) {
                        Icon(
                            imageVector = when {
                                uiState.isProbing -> Icons.Default.Radar
                                uiState.probeStatus == ProbeStatus.IDLE -> Icons.Default.PlayArrow
                                else -> Icons.Default.Refresh
                            },
                            contentDescription = null,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = when {
                                uiState.isProbing -> stringResource(id = R.string.btn_probing_active)
                                uiState.probeStatus == ProbeStatus.IDLE -> stringResource(id = R.string.btn_start_probe)
                                else -> stringResource(id = R.string.btn_reverify_probe)
                            },
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = uiState.lastProbeTimestamp,
                        style = MaterialTheme.typography.labelSmall,
                        color = palette.textMuted
                    )
                }
            }

            // 4. Hardware-Diagnostic 4-Stage Multi-Tiered Validation Stepper
            item(key = "validation_stepper", contentType = "stepper") {
                ValidationStepperCard(
                    stages = uiState.stages,
                    ttftMs = uiState.telemetry.ttftMs,
                    accentColor = activeAccent
                )
            }

            // 5. Google Services Live Status Bento Grid (12dp Bento gaps)
            item(key = "google_services_matrix", contentType = "google_matrix") {
                GoogleEcosystemMatrixSection(
                    services = uiState.googleServices
                )
            }

            // 6. Spatial Glass Bento Grid Telemetry & Bezier Sparkline
            item(key = "bento_telemetry", contentType = "telemetry") {
                BentoTelemetrySection(
                    telemetry = uiState.telemetry,
                    probeStatus = uiState.probeStatus,
                    redactSensitiveLogs = uiState.redactSensitiveLogs,
                    securityAuditNote = uiState.securityAuditNote,
                    accentColor = activeAccent
                )
            }

            item(key = "shield_bottom_spacer") {
                Spacer(modifier = Modifier.height(12.dp))
            }
        }
    }
}

@Composable
private fun ShieldHeaderBanner(
    uiState: ShieldUiState,
    accentColor: Color,
    onToggleRedaction: () -> Unit
) {
    val palette = LocalNodeGuardColors.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .spatialGlassCard(cornerRadius = 20.dp, accentTint = accentColor)
            .padding(horizontal = 18.dp, vertical = 16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                SeamlessLogoBox(
                    emblemType = BrandEmblemType.GEMINI_NODEGUARD,
                    boxSize = 44.dp,
                    accentOverride = accentColor
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        text = stringResource(id = R.string.app_name),
                        style = MaterialTheme.typography.titleLarge,
                        color = palette.textPrimary
                    )
                    Text(
                        text = stringResource(id = R.string.header_subtitle),
                        style = MaterialTheme.typography.labelSmall,
                        color = palette.primaryAccent
                    )
                }
            }

            IconButton(
                onClick = onToggleRedaction,
                modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .clip(RoundedCornerShape(12.dp))
                    .background(palette.surfaceBright)
                    .border(
                        width = 0.85.dp,
                        color = if (uiState.redactSensitiveLogs) NeonEmerald.copy(alpha = 0.55f) else NeonAmber.copy(alpha = 0.55f),
                        shape = RoundedCornerShape(12.dp)
                    )
                    .testTag("redact_logs_button")
            ) {
                Icon(
                    imageVector = if (uiState.redactSensitiveLogs) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                    contentDescription = stringResource(id = R.string.cd_redact_toggle),
                    tint = if (uiState.redactSensitiveLogs) NeonEmerald else NeonAmber
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Active Sing-box / Xray proxy profile badge row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(palette.logoEdgeBackground.copy(alpha = 0.84f))
                .border(0.75.dp, palette.cardBorder.copy(alpha = 0.7f), RoundedCornerShape(12.dp))
                .padding(horizontal = 12.dp, vertical = 9.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                Icon(
                    imageVector = Icons.Default.Lock,
                    contentDescription = null,
                    tint = palette.primaryAccent,
                    modifier = Modifier.size(14.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = uiState.selectedScenario.nodeUriSummary,
                    style = MaterialTheme.typography.labelSmall,
                    color = palette.textSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = if (uiState.redactSensitiveLogs) "MASKED" else "RAW IP",
                style = MaterialTheme.typography.labelSmall,
                color = if (uiState.redactSensitiveLogs) NeonEmerald else NeonAmber,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
private fun ScenarioSelectorRow(
    selectedScenario: SimulationScenario,
    onSelectScenario: (SimulationScenario) -> Unit
) {
    val palette = LocalNodeGuardColors.current
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = stringResource(id = R.string.sim_mode_label),
                style = MaterialTheme.typography.labelMedium,
                color = palette.textSecondary
            )
            Text(
                text = selectedScenario.profileTag,
                style = MaterialTheme.typography.labelSmall,
                color = palette.primaryAccent
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            SimulationScenario.entries.forEach { scenario ->
                val isSelected = scenario == selectedScenario
                val chipAccent = when (scenario) {
                    SimulationScenario.CLEAN_NODE -> NeonEmerald
                    SimulationScenario.GEO_BLOCKED_403 -> NeonCrimson
                    SimulationScenario.HIGH_LATENCY -> NeonAmber
                    SimulationScenario.TIMEOUT -> ElectricPurple
                }

                Box(
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .clip(RoundedCornerShape(12.dp))
                        .background(
                            if (isSelected) chipAccent.copy(alpha = 0.16f) else palette.surface
                        )
                        .border(
                            width = if (isSelected) 1.25.dp else 0.75.dp,
                            color = if (isSelected) chipAccent else palette.cardBorder,
                            shape = RoundedCornerShape(12.dp)
                        )
                        .clickable { onSelectScenario(scenario) }
                        .padding(horizontal = 14.dp, vertical = 10.dp)
                        .testTag("sim_chip_${scenario.id}"),
                    contentAlignment = Alignment.Center
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(7.dp)
                                .clip(CircleShape)
                                .background(if (isSelected) chipAccent else palette.textMuted)
                        )
                        Spacer(modifier = Modifier.width(7.dp))
                        Text(
                            text = scenario.label,
                            style = MaterialTheme.typography.labelMedium,
                            color = if (isSelected) palette.textPrimary else palette.textSecondary,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                        )
                    }
                }
            }
        }
    }
}

/**
 * Sleek Hardware-Diagnostic 4-Stage Pipeline Stepper:
 * Features a top horizontal diagnostic bar with glowing progress connectors, live millisecond pills,
 * and spring-animated state icons, paired with detailed per-stage cards below.
 */
@Composable
private fun ValidationStepperCard(
    stages: List<ValidationStage>,
    ttftMs: Int,
    accentColor: Color
) {
    val palette = LocalNodeGuardColors.current
    val passedCount = stages.count { it.state == StageState.PASSED || it.state == StageState.WARNING }

    val stageLatencies = remember(ttftMs, stages) {
        val base = if (ttftMs > 0) ttftMs else 148
        listOf(
            (base * 0.14f).toInt().coerceAtLeast(11),
            (base * 0.24f).toInt().coerceAtLeast(19),
            (base * 0.22f).toInt().coerceAtLeast(18),
            (base * 0.40f).toInt().coerceAtLeast(32)
        )
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .spatialGlassCard(cornerRadius = 20.dp, accentTint = accentColor)
            .padding(18.dp)
            .testTag("validation_stepper_card")
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = stringResource(id = R.string.pipeline_section_title),
                style = MaterialTheme.typography.labelMedium,
                color = palette.primaryAccent
            )
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(accentColor.copy(alpha = 0.14f))
                    .border(0.75.dp, accentColor.copy(alpha = 0.45f), RoundedCornerShape(8.dp))
                    .padding(horizontal = 8.dp, vertical = 3.dp)
            ) {
                Text(
                    text = "$passedCount/${stages.size} VERIFIED",
                    style = MaterialTheme.typography.labelSmall,
                    color = accentColor,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Top Horizontal Hardware Diagnostic Bar with Glowing Progress Connectors & Live Millisecond Pills
        HardwareDiagnosticHorizontalBar(
            stages = stages,
            stageLatencies = stageLatencies
        )

        Spacer(modifier = Modifier.height(16.dp))

        stages.forEachIndexed { index, stage ->
            ValidationStageRow(
                stage = stage,
                stageLatencyMs = stageLatencies.getOrElse(index) { 24 },
                isLast = index == stages.lastIndex
            )
        }
    }
}

@Composable
private fun HardwareDiagnosticHorizontalBar(
    stages: List<ValidationStage>,
    stageLatencies: List<Int>
) {
    val palette = LocalNodeGuardColors.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(palette.logoEdgeBackground.copy(alpha = 0.78f))
            .border(0.75.dp, palette.cardBorder, RoundedCornerShape(14.dp))
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        stages.forEachIndexed { index, stage ->
            val stageColor = when (stage.state) {
                StageState.IDLE -> RoyalSapphire
                StageState.RUNNING -> NeonCyan
                StageState.PASSED -> NeonEmerald
                StageState.WARNING -> NeonAmber
                StageState.FAILED -> NeonCrimson
            }
            val iconScale by animateFloatAsState(
                targetValue = if (stage.state == StageState.RUNNING || stage.state == StageState.PASSED) 1.06f else 1.0f,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioMediumBouncy,
                    stiffness = Spring.StiffnessMediumLow
                ),
                label = "hw_stage_icon_scale_$index"
            )
            val latencyVal = stageLatencies.getOrElse(index) { 24 }

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.weight(1f)
            ) {
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .graphicsLayer {
                            scaleX = iconScale
                            scaleY = iconScale
                        }
                        .clip(CircleShape)
                        .background(stageColor.copy(alpha = 0.16f))
                        .border(1.2.dp, stageColor, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    when (stage.state) {
                        StageState.PASSED -> Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = null,
                            tint = stageColor,
                            modifier = Modifier.size(15.dp)
                        )
                        StageState.WARNING -> Icon(
                            imageVector = Icons.Default.Warning,
                            contentDescription = null,
                            tint = stageColor,
                            modifier = Modifier.size(15.dp)
                        )
                        StageState.FAILED -> Icon(
                            imageVector = Icons.Default.Error,
                            contentDescription = null,
                            tint = stageColor,
                            modifier = Modifier.size(15.dp)
                        )
                        else -> Text(
                            text = "S${stage.stepNumber}",
                            style = MaterialTheme.typography.labelSmall,
                            color = stageColor,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                Spacer(modifier = Modifier.height(5.dp))

                // Live Millisecond Pill with Tabular Numerals
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(stageColor.copy(alpha = 0.12f))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = when (stage.state) {
                            StageState.FAILED -> "ERR"
                            StageState.IDLE -> "IDLE"
                            else -> "${latencyVal} ms"
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = stageColor,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            if (index < stages.lastIndex) {
                val nextStage = stages[index + 1]
                val nextColor = when (nextStage.state) {
                    StageState.PASSED -> NeonEmerald
                    StageState.RUNNING -> NeonCyan
                    StageState.WARNING -> NeonAmber
                    StageState.FAILED -> NeonCrimson
                    StageState.IDLE -> palette.cardBorder
                }
                Box(
                    modifier = Modifier
                        .width(18.dp)
                        .height(2.5.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(
                            Brush.horizontalGradient(
                                colors = listOf(stageColor.copy(alpha = 0.85f), nextColor.copy(alpha = 0.65f))
                            )
                        )
                )
            }
        }
    }
}

@Composable
private fun ValidationStageRow(
    stage: ValidationStage,
    stageLatencyMs: Int,
    isLast: Boolean
) {
    val palette = LocalNodeGuardColors.current
    val stageColor = when (stage.state) {
        StageState.IDLE -> RoyalSapphire
        StageState.RUNNING -> NeonCyan
        StageState.PASSED -> NeonEmerald
        StageState.WARNING -> NeonAmber
        StageState.FAILED -> NeonCrimson
    }

    val iconScale by animateFloatAsState(
        targetValue = if (stage.state == StageState.PASSED || stage.state == StageState.RUNNING) 1.05f else 1.0f,
        animationSpec = spring(dampingRatio = 0.65f, stiffness = Spring.StiffnessMediumLow),
        label = "stage_row_icon_scale_${stage.stepNumber}"
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("validation_stage_${stage.stepNumber}"),
        verticalAlignment = Alignment.Top
    ) {
        // Left timeline indicator column
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.width(32.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .graphicsLayer {
                        scaleX = iconScale
                        scaleY = iconScale
                    }
                    .clip(CircleShape)
                    .background(stageColor.copy(alpha = 0.18f))
                    .border(1.25.dp, stageColor, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                when (stage.state) {
                    StageState.PASSED -> Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = null,
                        tint = stageColor,
                        modifier = Modifier.size(16.dp)
                    )
                    StageState.WARNING -> Icon(
                        imageVector = Icons.Default.Warning,
                        contentDescription = null,
                        tint = stageColor,
                        modifier = Modifier.size(16.dp)
                    )
                    StageState.FAILED -> Icon(
                        imageVector = Icons.Default.Error,
                        contentDescription = null,
                        tint = stageColor,
                        modifier = Modifier.size(16.dp)
                    )
                    else -> Text(
                        text = "${stage.stepNumber}",
                        style = MaterialTheme.typography.labelMedium,
                        color = stageColor,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            if (!isLast) {
                Box(
                    modifier = Modifier
                        .width(2.dp)
                        .height(50.dp)
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(
                                    stageColor.copy(alpha = 0.55f),
                                    stageColor.copy(alpha = 0.15f)
                                )
                            )
                        )
                )
            }
        }

        Spacer(modifier = Modifier.width(12.dp))

        // Right stage telemetry details card
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(bottom = if (isLast) 0.dp else 12.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(palette.logoEdgeBackground.copy(alpha = 0.76f))
                .border(0.75.dp, stageColor.copy(alpha = 0.35f), RoundedCornerShape(14.dp))
                .padding(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stage.title,
                    style = MaterialTheme.typography.titleMedium,
                    color = palette.textPrimary,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    if (stage.state == StageState.PASSED || stage.state == StageState.WARNING || stage.state == StageState.RUNNING) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(palette.surfaceBright)
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = "${stageLatencyMs} ms",
                                style = MaterialTheme.typography.labelSmall,
                                color = palette.textPrimary,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(stageColor.copy(alpha = 0.16f))
                            .padding(horizontal = 8.dp, vertical = 3.dp)
                    ) {
                        Text(
                            text = stage.statusBadge,
                            style = MaterialTheme.typography.labelSmall,
                            color = stageColor,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = stage.endpointSpec,
                style = MaterialTheme.typography.labelSmall,
                color = palette.primaryAccent
            )

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = stage.diagnosticDetail,
                style = MaterialTheme.typography.bodyMedium,
                color = palette.textSecondary
            )
        }
    }
}

@Composable
private fun GoogleEcosystemMatrixSection(
    services: List<GoogleServiceStatusItem>
) {
    val palette = LocalNodeGuardColors.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("google_services_matrix_section"),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "GOOGLE & GEMINI ECOSYSTEM LIVE MATRIX",
                style = MaterialTheme.typography.labelMedium,
                color = palette.primaryAccent,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "SEAMLESS LOGO BLEND",
                style = MaterialTheme.typography.labelSmall,
                color = palette.textMuted
            )
        }

        // 2x2 Bento Grid with 12dp gap for the 4 Google Ecosystem Services
        val pairs = services.chunked(2)
        pairs.forEach { rowItems ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                rowItems.forEach { item ->
                    GoogleServiceBentoCard(
                        item = item,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}

@Composable
private fun GoogleServiceBentoCard(
    item: GoogleServiceStatusItem,
    modifier: Modifier = Modifier
) {
    val palette = LocalNodeGuardColors.current
    Column(
        modifier = modifier
            .spatialGlassCard(cornerRadius = 18.dp, accentTint = item.statusColor)
            .padding(16.dp)
            .testTag("google_service_card_${item.id}"),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            SeamlessLogoBox(
                emblemType = item.emblemType,
                boxSize = 40.dp,
                accentOverride = item.statusColor
            )

            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .background(item.statusColor.copy(alpha = 0.16f))
                    .border(0.75.dp, item.statusColor.copy(alpha = 0.45f), RoundedCornerShape(6.dp))
                    .padding(horizontal = 7.dp, vertical = 3.dp)
            ) {
                Text(
                    text = item.badgeText,
                    style = MaterialTheme.typography.labelSmall,
                    color = item.statusColor,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = item.serviceName,
                    style = MaterialTheme.typography.titleMedium,
                    color = palette.textPrimary,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = item.latencyLabel,
                    style = MaterialTheme.typography.labelSmall,
                    color = item.statusColor,
                    fontWeight = FontWeight.Bold
                )
            }

            Text(
                text = item.endpointHost,
                style = MaterialTheme.typography.labelSmall,
                color = palette.primaryAccent,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Text(
                text = item.statusDetail,
                style = MaterialTheme.typography.labelSmall,
                color = palette.textSecondary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun BentoTelemetrySection(
    telemetry: EdgeTelemetry,
    probeStatus: ProbeStatus,
    redactSensitiveLogs: Boolean,
    securityAuditNote: String,
    accentColor: Color
) {
    val palette = LocalNodeGuardColors.current
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = stringResource(id = R.string.bento_section_title),
            style = MaterialTheme.typography.labelMedium,
            color = palette.primaryAccent
        )

        // Top Row: TTFT & Ping Jitter Bento Cards (12dp gap)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            BentoMetricCard(
                icon = Icons.Default.Bolt,
                label = stringResource(id = R.string.bento_ttft_label),
                primaryValue = if (telemetry.ttftMs > 0) "${telemetry.ttftMs} ms" else "BLOCKED",
                secondaryValue = telemetry.tokenStreamRate,
                accentColor = if (telemetry.ttftMs in 1..250) NeonEmerald else accentColor,
                testTag = "bento_ttft_card",
                modifier = Modifier.weight(1f)
            )

            BentoMetricCard(
                icon = Icons.Default.GraphicEq,
                label = stringResource(id = R.string.bento_jitter_label),
                primaryValue = String.format(Locale.US, "±%.1f ms", telemetry.pingJitterMs),
                secondaryValue = String.format(Locale.US, "Loss: %.1f%% • %s", telemetry.packetLossPercent, telemetry.alpnProtocol.uppercase(Locale.US)),
                accentColor = if (telemetry.pingJitterMs < 20f) palette.primaryAccent else NeonAmber,
                testTag = "bento_jitter_card",
                modifier = Modifier.weight(1f)
            )
        }

        // Full-Width Exit IP / ASN & Geo Region Spatial Glass Bento Card
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .spatialGlassCard(cornerRadius = 18.dp, accentTint = palette.primaryAccent)
                .padding(16.dp)
                .testTag("bento_exit_ip_card"),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(RoyalSapphire.copy(alpha = 0.20f))
                        .border(0.85.dp, palette.primaryAccent.copy(alpha = 0.42f), RoundedCornerShape(12.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Public,
                        contentDescription = null,
                        tint = palette.primaryAccent,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        text = stringResource(id = R.string.bento_exit_ip_label),
                        style = MaterialTheme.typography.labelSmall,
                        color = palette.textMuted
                    )
                    Text(
                        text = "${telemetry.formattedExitIp(redactSensitiveLogs)} • ${telemetry.asnOrg}",
                        style = MaterialTheme.typography.titleMedium,
                        color = palette.textPrimary,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Geo-IP Routing: ${telemetry.regionCode}",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (probeStatus == ProbeStatus.GEO_BLOCKED_403) NeonCrimson else NeonEmerald
                    )
                }
            }
        }

        // Custom Compose Canvas Bezier-Curve Latency Sparkline Card
        LatencySparklineCard(
            rttHistoryMs = telemetry.rttHistoryMs,
            accentColor = accentColor,
            alpnProtocol = telemetry.alpnProtocol,
            tlsCipherSuite = telemetry.tlsCipherSuite
        )

        // Security-by-Design Audit Footer Strip
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .spatialGlassCard(cornerRadius = 14.dp, accentTint = palette.primaryAccent, elevationDp = 2.dp)
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.Speed,
                contentDescription = null,
                tint = palette.primaryAccent,
                modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = securityAuditNote,
                style = MaterialTheme.typography.labelSmall,
                color = palette.textSecondary
            )
        }
    }
}

@Composable
private fun BentoMetricCard(
    icon: ImageVector,
    label: String,
    primaryValue: String,
    secondaryValue: String,
    accentColor: Color,
    testTag: String,
    modifier: Modifier = Modifier
) {
    val palette = LocalNodeGuardColors.current
    Column(
        modifier = modifier
            .spatialGlassCard(cornerRadius = 18.dp, accentTint = accentColor)
            .padding(16.dp)
            .testTag(testTag)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = palette.textMuted
            )
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = accentColor,
                modifier = Modifier.size(16.dp)
            )
        }
        Spacer(modifier = Modifier.height(10.dp))
        Text(
            text = primaryValue,
            style = MaterialTheme.typography.headlineMedium,
            color = accentColor,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = secondaryValue,
            style = MaterialTheme.typography.labelSmall,
            color = palette.textSecondary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}
