package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Commit
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Radar
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.audio.SciFiSoundEngine
import com.example.bridge.ActionRunStatus
import com.example.bridge.DynamicActionSyncManager
import com.example.bridge.DynamicTargetEndpointRule
import com.example.ui.theme.ElectricPurple
import com.example.ui.theme.LocalNodeGuardColors
import com.example.ui.theme.NeonAmber
import com.example.ui.theme.NeonCyan
import com.example.ui.theme.NeonEmerald
import com.example.ui.theme.RoyalSapphire
import com.example.ui.theme.spatialGlassCard
import kotlin.math.max

/**
 * RealTimeGitHubActionStatusCard:
 * Dedicated Tab 3 (Cloud & Core) telemetry component powered by [DynamicActionSyncManager].
 * Displays:
 * 1. Visual indicator for workflow run status (Animated orbital radar ring + 3-stage pipeline bar)
 * 2. Real-time workflow timestamps, Run #, Commit SHA, and active step summary
 * 3. Active Target Endpoints dynamically injected by the GitHub Action (`remote-action-config.json`)
 */
@Composable
fun RealTimeGitHubActionStatusCard(
    repoSlug: String,
    actionRunStatus: ActionRunStatus,
    isLiveActionMonitorEnabled: Boolean,
    liveActionStepSummary: String,
    liveMonitorPollCount: Int,
    latestWorkflowRunNumber: Int,
    latestCommitHash: String,
    latestTimestamp: String,
    dynamicEndpointRules: List<DynamicTargetEndpointRule>,
    selectedDynamicRuleId: String?,
    isDispatchingWorkflow: Boolean,
    isCloudSyncing: Boolean,
    onSetLiveActionMonitorEnabled: (Boolean) -> Unit,
    onPollActionStatusNow: () -> Unit,
    onTriggerWorkflowDispatch: () -> Unit,
    onHotReloadActionOutput: () -> Unit,
    onSelectTargetEndpoint: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val palette = LocalNodeGuardColors.current

    val effectiveEndpoints by remember(dynamicEndpointRules) {
        derivedStateOf {
            dynamicEndpointRules.ifEmpty { DynamicActionSyncManager.initialDynamicEndpoints() }
        }
    }

    val effectiveRepoSlug by remember(repoSlug) {
        derivedStateOf {
            repoSlug.trim().ifEmpty { DynamicActionSyncManager.DEFAULT_REPO_SLUG }
        }
    }

    var showAllEndpoints by remember { mutableStateOf(true) }

    val statusAccentColor by animateColorAsState(
        targetValue = actionRunStatus.badgeColor,
        animationSpec = tween(durationMillis = 320),
        label = "RealtimeStatusAccentColor"
    )

    val cardBorderColor by animateColorAsState(
        targetValue = when (actionRunStatus) {
            ActionRunStatus.IN_PROGRESS -> NeonCyan.copy(alpha = 0.65f)
            ActionRunStatus.QUEUED -> NeonAmber.copy(alpha = 0.60f)
            ActionRunStatus.COMPLETED -> NeonEmerald.copy(alpha = 0.50f)
        },
        animationSpec = tween(durationMillis = 350),
        label = "RealtimeCardBorderColor"
    )

    val pipelineProgress by animateFloatAsState(
        targetValue = when (actionRunStatus) {
            ActionRunStatus.QUEUED -> 0.34f
            ActionRunStatus.IN_PROGRESS -> 0.68f
            ActionRunStatus.COMPLETED -> 1.0f
        },
        animationSpec = tween(durationMillis = 420, easing = FastOutSlowInEasing),
        label = "RealtimePipelineProgress"
    )

    val infiniteTransition = rememberInfiniteTransition(label = "RealtimeActionPulseTransition")
    val radarSweepDegrees by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2400, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "RadarSweepDegrees"
    )
    val beaconPulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.35f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 900, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "BeaconPulseAlpha"
    )

    Column(
        modifier = modifier
            .fillMaxWidth()
            .spatialGlassCard(cornerRadius = 22.dp, accentTint = cardBorderColor)
            .padding(18.dp)
            .testTag("realtime_github_action_status_card")
    ) {
        // 1. Header Row with Visual Status Orb + Workflow Metadata + Live Monitor Switch
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            RealTimeWorkflowStatusVisualOrb(
                status = actionRunStatus,
                statusColor = statusAccentColor,
                sweepAngleDegrees = radarSweepDegrees,
                pulseAlpha = beaconPulseAlpha,
                isLiveMonitoring = isLiveActionMonitorEnabled
            )

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = "REAL-TIME GITHUB ACTION STATUS",
                        style = MaterialTheme.typography.titleSmall,
                        color = palette.textPrimary,
                        fontWeight = FontWeight.ExtraBold
                    )
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(statusAccentColor.copy(alpha = 0.16f))
                            .border(
                                1.dp,
                                statusAccentColor.copy(alpha = 0.55f),
                                RoundedCornerShape(6.dp)
                            )
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                            .testTag("realtime_workflow_status_badge")
                    ) {
                        Text(
                            text = actionRunStatus.displayLabel,
                            style = MaterialTheme.typography.labelSmall,
                            color = statusAccentColor,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                Spacer(modifier = Modifier.height(2.dp))

                Text(
                    text = "$effectiveRepoSlug • nodeguard-scanner.yml",
                    style = MaterialTheme.typography.labelSmall,
                    color = palette.textSecondary,
                    fontFamily = FontFamily.Monospace,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = if (isLiveActionMonitorEnabled) "LIVE POLL" else "PAUSED",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (isLiveActionMonitorEnabled) NeonCyan else palette.textMuted,
                    fontWeight = FontWeight.Bold
                )
                Switch(
                    checked = isLiveActionMonitorEnabled,
                    onCheckedChange = { enabled ->
                        SciFiSoundEngine.playHoloClick()
                        onSetLiveActionMonitorEnabled(enabled)
                    },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = NeonCyan,
                        checkedTrackColor = NeonCyan.copy(alpha = 0.25f)
                    ),
                    modifier = Modifier.testTag("realtime_status_live_monitor_switch")
                )
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // 2. Visual 3-Stage Workflow Run Status Indicator Bar (Queued -> In Progress -> Completed)
        WorkflowThreeStageVisualIndicator(
            currentStatus = actionRunStatus,
            progressFraction = pipelineProgress,
            pulseAlpha = beaconPulseAlpha,
            statusColor = statusAccentColor
        )

        Spacer(modifier = Modifier.height(12.dp))

        // 3. Timestamps, Run Number & Commit SHA Telemetry Strip
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(palette.surfaceBright)
                .border(1.dp, palette.cardBorder, RoundedCornerShape(14.dp))
                .padding(12.dp)
                .testTag("realtime_action_timestamps_panel")
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Workflow Run # & Commit SHA
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(NeonCyan.copy(alpha = 0.12f))
                            .padding(horizontal = 7.dp, vertical = 3.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Radar,
                            contentDescription = "Workflow Run Number",
                            tint = NeonCyan,
                            modifier = Modifier.size(13.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "Run #$latestWorkflowRunNumber",
                            style = MaterialTheme.typography.labelSmall,
                            color = NeonCyan,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.testTag("realtime_action_run_number_text")
                        )
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(ElectricPurple.copy(alpha = 0.14f))
                            .padding(horizontal = 7.dp, vertical = 3.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Commit,
                            contentDescription = "Commit Hash",
                            tint = ElectricPurple,
                            modifier = Modifier.size(13.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = latestCommitHash,
                            style = MaterialTheme.typography.labelSmall,
                            color = ElectricPurple,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.testTag("realtime_action_commit_hash_text")
                        )
                    }
                }

                // Poll Counter Badge
                Text(
                    text = "Polls: $liveMonitorPollCount",
                    style = MaterialTheme.typography.labelSmall,
                    color = palette.textMuted,
                    fontFamily = FontFamily.Monospace
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Timestamp Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = Icons.Filled.AccessTime,
                        contentDescription = "Last Action Timestamp",
                        tint = NeonAmber,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Last Run Timestamp: ",
                        style = MaterialTheme.typography.labelSmall,
                        color = palette.textSecondary
                    )
                    Text(
                        text = latestTimestamp,
                        style = MaterialTheme.typography.labelSmall,
                        color = palette.textPrimary,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.testTag("realtime_action_timestamp_text")
                    )
                }

                // Quick Poll Now Action
                Box(
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .clip(RoundedCornerShape(8.dp))
                        .background(NeonCyan.copy(alpha = 0.12f))
                        .border(1.dp, NeonCyan.copy(alpha = 0.45f), RoundedCornerShape(8.dp))
                        .clickable {
                            SciFiSoundEngine.playHoloClick()
                            onPollActionStatusNow()
                        }
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                        .testTag("realtime_poll_status_now_button"),
                    contentAlignment = Alignment.Center
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Filled.Refresh,
                            contentDescription = "Poll Action Status Now",
                            tint = NeonCyan,
                            modifier = Modifier.size(13.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "Poll Now",
                            style = MaterialTheme.typography.labelSmall,
                            color = NeonCyan,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            // Active Workflow Step Summary
            Text(
                text = "Active Step: $liveActionStepSummary",
                style = MaterialTheme.typography.bodySmall,
                color = palette.textSecondary,
                fontFamily = FontFamily.Monospace,
                modifier = Modifier.testTag("realtime_action_step_summary_text")
            )
        }

        Spacer(modifier = Modifier.height(14.dp))

        // 4. Active Target Endpoints Section Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Filled.Dns,
                    contentDescription = "Active Target Endpoints",
                    tint = NeonEmerald,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Column {
                    Text(
                        text = "ACTIVE TARGET ENDPOINTS (${effectiveEndpoints.size})",
                        style = MaterialTheme.typography.labelMedium,
                        color = palette.textPrimary,
                        fontWeight = FontWeight.ExtraBold
                    )
                    Text(
                        text = "Synced via DynamicActionSyncManager • Tap endpoint to filter Scanner",
                        style = MaterialTheme.typography.labelSmall,
                        color = palette.textSecondary
                    )
                }
            }

            Box(
                modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .clip(RoundedCornerShape(8.dp))
                    .background(palette.surfaceBright)
                    .border(1.dp, palette.cardBorder, RoundedCornerShape(8.dp))
                    .clickable {
                        SciFiSoundEngine.playHoloClick()
                        showAllEndpoints = !showAllEndpoints
                    }
                    .padding(horizontal = 10.dp, vertical = 5.dp)
                    .testTag("realtime_toggle_endpoints_button"),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = if (showAllEndpoints) "Collapse" else "Expand (${effectiveEndpoints.size})",
                    style = MaterialTheme.typography.labelSmall,
                    color = NeonCyan,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        AnimatedVisibility(
            visible = showAllEndpoints,
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 10.dp)
                    .testTag("realtime_active_target_endpoints_list"),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                effectiveEndpoints.forEach { rule ->
                    val isSelected = selectedDynamicRuleId == rule.id
                    ActiveTargetEndpointItemRow(
                        rule = rule,
                        isSelected = isSelected,
                        onSelectEndpoint = {
                            SciFiSoundEngine.playHoloClick()
                            onSelectTargetEndpoint(rule.id)
                        }
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // 5. Quick Action Row (Dispatch Workflow Run & Hot-Reload Output)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Button(
                onClick = {
                    SciFiSoundEngine.playHoloClick()
                    onTriggerWorkflowDispatch()
                },
                enabled = !isDispatchingWorkflow,
                colors = ButtonDefaults.buttonColors(
                    containerColor = RoyalSapphire,
                    contentColor = Color.White
                ),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .weight(1f)
                    .minimumInteractiveComponentSize()
                    .testTag("realtime_card_dispatch_button")
            ) {
                Icon(
                    imageVector = Icons.Filled.Bolt,
                    contentDescription = "Dispatch Workflow",
                    tint = NeonCyan,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = if (isDispatchingWorkflow) "Dispatching..." else "Dispatch Run",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            OutlinedButton(
                onClick = {
                    SciFiSoundEngine.playHoloClick()
                    onHotReloadActionOutput()
                },
                enabled = !isCloudSyncing,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .weight(1f)
                    .minimumInteractiveComponentSize()
                    .testTag("realtime_card_hot_reload_button")
            ) {
                Icon(
                    imageVector = Icons.Filled.Sync,
                    contentDescription = "Sync Endpoints & Nodes",
                    tint = NeonEmerald,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = if (isCloudSyncing) "Syncing..." else "Sync Endpoints",
                    style = MaterialTheme.typography.labelMedium,
                    color = NeonEmerald,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

/**
 * Animated orbital visual indicator for the current GitHub Action workflow run status.
 * Uses strict non-zero canvas size guards to prevent zero-radius radialGradient crashes.
 */
@Composable
private fun RealTimeWorkflowStatusVisualOrb(
    status: ActionRunStatus,
    statusColor: Color,
    sweepAngleDegrees: Float,
    pulseAlpha: Float,
    isLiveMonitoring: Boolean
) {
    Box(
        modifier = Modifier
            .size(54.dp)
            .drawWithCache {
                val safeWidth = size.width
                val safeHeight = size.height
                val minDim = minOf(safeWidth, safeHeight)
                val safeRadius = max(minDim / 2f, 1f)
                val centerPoint = Offset(safeWidth / 2f, safeHeight / 2f)

                val glowBrush = Brush.radialGradient(
                    colors = listOf(
                        statusColor.copy(alpha = 0.38f),
                        statusColor.copy(alpha = 0.10f),
                        Color.Transparent
                    ),
                    center = centerPoint,
                    radius = safeRadius
                )

                val ringStroke = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round)

                onDrawBehind {
                    if (size.width <= 1f || size.height <= 1f) return@onDrawBehind

                    drawCircle(
                        brush = glowBrush,
                        radius = safeRadius * (0.85f + 0.15f * pulseAlpha),
                        center = centerPoint
                    )

                    drawCircle(
                        color = statusColor.copy(alpha = 0.28f),
                        radius = max(safeRadius - 4.dp.toPx(), 1f),
                        center = centerPoint,
                        style = Stroke(width = 1.2.dp.toPx())
                    )

                    if (status == ActionRunStatus.IN_PROGRESS || isLiveMonitoring) {
                        rotate(degrees = sweepAngleDegrees, pivot = centerPoint) {
                            val arcDiameter = max((safeRadius - 4.dp.toPx()) * 2f, 2f)
                            drawArc(
                                color = statusColor,
                                startAngle = -90f,
                                sweepAngle = 110f,
                                useCenter = false,
                                topLeft = Offset(
                                    centerPoint.x - arcDiameter / 2f,
                                    centerPoint.y - arcDiameter / 2f
                                ),
                                size = Size(arcDiameter, arcDiameter),
                                style = ringStroke
                            )
                        }
                    } else {
                        drawCircle(
                            color = statusColor.copy(alpha = 0.85f),
                            radius = max(safeRadius - 4.dp.toPx(), 1f),
                            center = centerPoint,
                            style = ringStroke
                        )
                    }
                }
            }
            .testTag("realtime_workflow_visual_orb"),
        contentAlignment = Alignment.Center
    ) {
        SeamlessLogoBox(
            emblemType = BrandEmblemType.GOOGLE_GEMINI_API,
            boxSize = 38.dp,
            accentOverride = statusColor
        )
    }
}

/**
 * 3-Stage Pipeline Indicator for `Queued` -> `In Progress` -> `Completed`
 */
@Composable
private fun WorkflowThreeStageVisualIndicator(
    currentStatus: ActionRunStatus,
    progressFraction: Float,
    pulseAlpha: Float,
    statusColor: Color
) {
    val palette = LocalNodeGuardColors.current
    val stages = remember {
        listOf(
            ActionRunStatus.QUEUED to Icons.Filled.Schedule,
            ActionRunStatus.IN_PROGRESS to Icons.Filled.PlayArrow,
            ActionRunStatus.COMPLETED to Icons.Filled.CheckCircle
        )
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(palette.surfaceBright)
            .border(1.dp, palette.cardBorder, RoundedCornerShape(12.dp))
            .padding(horizontal = 12.dp, vertical = 10.dp)
            .testTag("realtime_workflow_stage_pipeline")
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            stages.forEachIndexed { index, (stage, icon) ->
                val isReached = currentStatus.ordinal >= stage.ordinal
                val isActive = currentStatus == stage
                val stageTint = when {
                    isActive -> stage.badgeColor
                    isReached -> NeonEmerald
                    else -> palette.textMuted
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp),
                    modifier = Modifier.testTag("workflow_stage_pill_${stage.name.lowercase()}")
                ) {
                    Box(
                        modifier = Modifier
                            .size(22.dp)
                            .clip(CircleShape)
                            .background(
                                if (isActive) {
                                    stageTint.copy(alpha = 0.18f + 0.12f * pulseAlpha)
                                } else if (isReached) {
                                    stageTint.copy(alpha = 0.15f)
                                } else {
                                    Color.Transparent
                                }
                            )
                            .border(
                                width = 1.dp,
                                color = if (isReached) stageTint else palette.cardBorder,
                                shape = CircleShape
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = icon,
                            contentDescription = stage.displayLabel,
                            tint = stageTint,
                            modifier = Modifier.size(13.dp)
                        )
                    }

                    Text(
                        text = stage.displayLabel,
                        style = MaterialTheme.typography.labelSmall,
                        color = if (isReached) palette.textPrimary else palette.textMuted,
                        fontWeight = if (isActive) FontWeight.ExtraBold else FontWeight.Medium
                    )
                }

                if (index < stages.lastIndex) {
                    Spacer(modifier = Modifier.width(4.dp))
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Animated progress bar track
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(palette.cardBorder.copy(alpha = 0.45f))
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(fraction = progressFraction.coerceIn(0.05f, 1f))
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(
                        Brush.horizontalGradient(
                            colors = listOf(
                                NeonCyan,
                                statusColor
                            )
                        )
                    )
            )
        }
    }
}

/**
 * Individual row for each Active Target Endpoint rule inside [RealTimeGitHubActionStatusCard].
 */
@Composable
private fun ActiveTargetEndpointItemRow(
    rule: DynamicTargetEndpointRule,
    isSelected: Boolean,
    onSelectEndpoint: () -> Unit
) {
    val palette = LocalNodeGuardColors.current
    val borderColor by animateColorAsState(
        targetValue = if (isSelected) rule.accentColor else rule.accentColor.copy(alpha = 0.35f),
        animationSpec = tween(durationMillis = 220),
        label = "EndpointRowBorderColor"
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .minimumInteractiveComponentSize()
            .clip(RoundedCornerShape(14.dp))
            .background(
                if (isSelected) {
                    rule.accentColor.copy(alpha = 0.14f)
                } else {
                    palette.surfaceBright
                }
            )
            .border(
                width = if (isSelected) 1.5.dp else 1.dp,
                color = borderColor,
                shape = RoundedCornerShape(14.dp)
            )
            .clickable(onClick = onSelectEndpoint)
            .padding(horizontal = 12.dp, vertical = 10.dp)
            .testTag("realtime_target_endpoint_${rule.id}"),
        verticalAlignment = Alignment.CenterVertically
    ) {
        SeamlessLogoBox(
            emblemType = rule.emblemType,
            boxSize = 40.dp,
            accentOverride = rule.accentColor
        )

        Spacer(modifier = Modifier.width(10.dp))

        Column(modifier = Modifier.weight(1f)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = rule.serviceName,
                    style = MaterialTheme.typography.labelMedium,
                    color = palette.textPrimary,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(5.dp))
                        .background(rule.accentColor.copy(alpha = 0.16f))
                        .padding(horizontal = 5.dp, vertical = 1.dp)
                ) {
                    Text(
                        text = rule.chipTag,
                        style = MaterialTheme.typography.labelSmall,
                        color = rule.accentColor,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Spacer(modifier = Modifier.height(2.dp))

            Text(
                text = "https://${rule.endpointHost}${rule.endpointPath}",
                style = MaterialTheme.typography.labelSmall,
                color = NeonCyan,
                fontFamily = FontFamily.Monospace,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(modifier = Modifier.height(2.dp))

            Text(
                text = "Rule: ${rule.protocolRule}",
                style = MaterialTheme.typography.labelSmall,
                color = palette.textSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        Spacer(modifier = Modifier.width(8.dp))

        Column(horizontalAlignment = Alignment.End) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .background(NeonEmerald.copy(alpha = 0.16f))
                    .border(1.dp, NeonEmerald.copy(alpha = 0.5f), RoundedCornerShape(6.dp))
                    .padding(horizontal = 6.dp, vertical = 2.dp)
            ) {
                Text(
                    text = "HTTP ${rule.expectedHttpCode}",
                    style = MaterialTheme.typography.labelSmall,
                    color = NeonEmerald,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(modifier = Modifier.height(4.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = if (isSelected) "Filtering" else "Filter",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (isSelected) rule.accentColor else palette.textMuted,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.width(2.dp))
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                    contentDescription = "Filter Scanner by ${rule.serviceName}",
                    tint = if (isSelected) rule.accentColor else palette.textMuted,
                    modifier = Modifier.size(12.dp)
                )
            }
        }
    }
}
