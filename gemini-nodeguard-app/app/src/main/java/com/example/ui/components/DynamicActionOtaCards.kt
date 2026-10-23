package com.example.ui.components

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoMode
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.NewReleases
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.RocketLaunch
import androidx.compose.material.icons.filled.SystemUpdateAlt
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.audio.SciFiSoundEngine
import com.example.bridge.ActionRunStatus
import com.example.bridge.AutoSyncInterval
import com.example.bridge.DynamicTargetEndpointRule
import com.example.bridge.OtaReleaseInfo
import com.example.security.SecurityHardeningManager
import com.example.ui.theme.DeepObsidian
import com.example.ui.theme.ElectricPurple
import com.example.ui.theme.LocalNodeGuardColors
import com.example.ui.theme.NeonAmber
import com.example.ui.theme.NeonCyan
import com.example.ui.theme.NeonEmerald
import com.example.ui.theme.RoyalSapphire

@Composable
fun DynamicGitHubActionsSyncCard(
    repoSlug: String,
    actionRunStatus: ActionRunStatus,
    isLiveActionMonitorEnabled: Boolean,
    liveActionStepSummary: String,
    liveMonitorPollCount: Int,
    isDispatchingWorkflow: Boolean,
    isCloudSyncing: Boolean,
    latestWorkflowRunNumber: Int,
    latestCommitHash: String,
    latestTimestamp: String,
    autoSyncEnabled: Boolean,
    autoSyncInterval: AutoSyncInterval,
    dynamicEndpointRules: List<DynamicTargetEndpointRule>,
    remoteConfigJson: String,
    onTriggerWorkflowDispatch: () -> Unit,
    onHotReloadActionOutput: () -> Unit,
    onSetLiveActionMonitorEnabled: (Boolean) -> Unit,
    onPollActionStatusNow: () -> Unit,
    onSetAutoSyncEnabled: (Boolean) -> Unit,
    onSetAutoSyncInterval: (AutoSyncInterval) -> Unit,
    onJumpToScannerWithRule: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val palette = LocalNodeGuardColors.current
    val context = LocalContext.current
    var showRawRemoteJson by remember { mutableStateOf(false) }

    val statusColor by animateColorAsState(
        targetValue = actionRunStatus.badgeColor,
        animationSpec = tween(280),
        label = "action_run_status_color"
    )

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(palette.surface)
            .border(1.5.dp, palette.primaryAccent.copy(alpha = 0.50f), RoundedCornerShape(20.dp))
            .padding(16.dp)
            .testTag("dynamic_github_actions_sync_card"),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Card Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                Icon(
                    imageVector = Icons.Default.RocketLaunch,
                    contentDescription = null,
                    tint = palette.primaryAccent,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Column {
                    Text(
                        text = "DYNAMIC GITHUB ACTIONS REMOTE-CONFIG SYNC",
                        style = MaterialTheme.typography.labelLarge,
                        color = palette.primaryAccent,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Server-Driven Probing Rules & Live workflow_dispatch • $repoSlug",
                        style = MaterialTheme.typography.labelSmall,
                        color = palette.textSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Spacer(modifier = Modifier.width(8.dp))

            // Animated Workflow Run Status Badge (Queued / In Progress 🔄 / Completed ✓)
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(statusColor.copy(alpha = 0.18f))
                    .border(1.dp, statusColor, RoundedCornerShape(8.dp))
                    .padding(horizontal = 10.dp, vertical = 5.dp)
                    .testTag("action_run_status_badge")
            ) {
                Text(
                    text = actionRunStatus.displayLabel,
                    style = MaterialTheme.typography.labelSmall,
                    color = statusColor,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        // Prominent Neon Button: "⚡ Trigger GitHub Action Run Now (Remote Dispatch)"
        Button(
            onClick = onTriggerWorkflowDispatch,
            shape = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = NeonCyan,
                contentColor = DeepObsidian
            ),
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
                .testTag("trigger_github_action_dispatch_button")
        ) {
            Icon(
                imageVector = Icons.Default.Bolt,
                contentDescription = null,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = if (isDispatchingWorkflow) {
                    "⚡ DISPATCHING WORKFLOW #$latestWorkflowRunNumber (${actionRunStatus.displayLabel})..."
                } else {
                    "⚡ Trigger GitHub Action Run Now (Remote Dispatch)"
                },
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        // Live Action Run Status Monitor Telemetry Box (with Dedicated UI Toggle)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(palette.logoEdgeBackground.copy(alpha = 0.85f))
                .border(1.dp, statusColor.copy(alpha = 0.42f), RoundedCornerShape(14.dp))
                .padding(12.dp)
                .testTag("live_action_status_monitor"),
            verticalArrangement = Arrangement.spacedBy(8.dp)
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
                    Box(
                        modifier = Modifier
                            .size(9.dp)
                            .clip(CircleShape)
                            .background(if (isLiveActionMonitorEnabled) statusColor else palette.textMuted)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Text(
                            text = "LIVE ACTION RUN STATUS MONITOR",
                            style = MaterialTheme.typography.labelSmall,
                            color = palette.primaryAccent,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = if (isLiveActionMonitorEnabled) {
                                "Real-time telemetry stream active (Poll #$liveMonitorPollCount)"
                            } else {
                                "Live monitoring paused • Toggle switch to resume"
                            },
                            style = MaterialTheme.typography.labelSmall,
                            color = palette.textSecondary
                        )
                    }
                }

                Switch(
                    checked = isLiveActionMonitorEnabled,
                    onCheckedChange = onSetLiveActionMonitorEnabled,
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color(0xFF04130D),
                        checkedTrackColor = NeonCyan,
                        uncheckedThumbColor = palette.textSecondary,
                        uncheckedTrackColor = palette.surface
                    ),
                    modifier = Modifier.testTag("live_action_status_monitor_switch")
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                MonitorMetricTile(
                    label = "WORKFLOW STATUS",
                    value = actionRunStatus.displayLabel,
                    accent = statusColor,
                    modifier = Modifier.weight(1f)
                )
                MonitorMetricTile(
                    label = "COMMIT HASH",
                    value = latestCommitHash,
                    accent = palette.primaryAccent,
                    modifier = Modifier.weight(1f)
                )
                MonitorMetricTile(
                    label = "ACTION TIMESTAMP",
                    value = latestTimestamp.takeLast(9).ifBlank { latestTimestamp },
                    accent = palette.tertiaryAccent,
                    modifier = Modifier.weight(1f)
                )
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(palette.surface.copy(alpha = 0.85f))
                    .border(1.dp, palette.cardBorder, RoundedCornerShape(10.dp))
                    .padding(horizontal = 10.dp, vertical = 7.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = liveActionStepSummary,
                    style = MaterialTheme.typography.labelSmall,
                    color = palette.textPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "POLL NOW",
                    style = MaterialTheme.typography.labelSmall,
                    color = NeonCyan,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .clickable { onPollActionStatusNow() }
                        .padding(horizontal = 6.dp, vertical = 3.dp)
                        .testTag("poll_action_run_status_now_button")
                )
            }

            // Dynamic Target Endpoints & Protocol Rules from remote-action-config.json
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "DYNAMIC TARGET ENDPOINTS (${dynamicEndpointRules.size} INJECTED RULES)",
                    style = MaterialTheme.typography.labelSmall,
                    color = NeonEmerald,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = if (showRawRemoteJson) "Hide JSON" else "Inspect JSON",
                    style = MaterialTheme.typography.labelSmall,
                    color = palette.primaryAccent,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .clickable {
                            SciFiSoundEngine.playHoloClick()
                            showRawRemoteJson = !showRawRemoteJson
                        }
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                        .testTag("toggle_remote_config_json_button")
                )
            }

            Text(
                text = "Tap any server-driven Google/Gemini rule below to filter matching verified nodes in Tab 2 (Scanner):",
                style = MaterialTheme.typography.labelSmall,
                color = palette.textSecondary
            )

            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                dynamicEndpointRules.forEach { rule ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(palette.surface)
                            .border(1.dp, rule.accentColor.copy(alpha = 0.40f), RoundedCornerShape(10.dp))
                            .clickable { onJumpToScannerWithRule(rule.id) }
                            .padding(horizontal = 10.dp, vertical = 8.dp)
                            .testTag("action_endpoint_rule_${rule.id}"),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(5.dp))
                                        .background(rule.accentColor.copy(alpha = 0.18f))
                                        .padding(horizontal = 6.dp, vertical = 2.dp)
                                ) {
                                    Text(
                                        text = rule.chipTag,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = rule.accentColor,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = rule.serviceName,
                                    style = MaterialTheme.typography.labelMedium,
                                    color = palette.textPrimary,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            Text(
                                text = "${rule.endpointHost}${rule.endpointPath} • ${rule.protocolRule}",
                                style = MaterialTheme.typography.labelSmall,
                                color = palette.textSecondary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        Spacer(modifier = Modifier.width(8.dp))

                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(NeonEmerald.copy(alpha = 0.16f))
                                .border(1.dp, NeonEmerald, RoundedCornerShape(6.dp))
                                .padding(horizontal = 7.dp, vertical = 3.dp)
                        ) {
                            Text(
                                text = "${rule.expectedHttpCode} OK",
                                style = MaterialTheme.typography.labelSmall,
                                color = NeonEmerald,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }

            AnimatedVisibility(visible = showRawRemoteJson) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(palette.logoEdgeBackground)
                        .border(1.dp, palette.cardBorder, RoundedCornerShape(10.dp))
                        .padding(10.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Code,
                                contentDescription = null,
                                tint = palette.primaryAccent,
                                modifier = Modifier.size(15.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "remote-action-config.json (Server-Driven Schema)",
                                style = MaterialTheme.typography.labelSmall,
                                color = palette.primaryAccent,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Text(
                            text = "COPY JSON",
                            style = MaterialTheme.typography.labelSmall,
                            color = NeonEmerald,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier
                                .clickable {
                                    SciFiSoundEngine.playCopyLaser(context)
                                    SecurityHardeningManager.SafeClipboard.copyToClipboard(
                                        context = context,
                                        label = "remote-action-config.json",
                                        text = remoteConfigJson,
                                        isSensitive = false
                                    )
                                    Toast.makeText(
                                        context,
                                        "Copied remote-action-config.json schema!",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                }
                                .padding(4.dp)
                        )
                    }
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 100.dp, max = 180.dp)
                            .verticalScroll(rememberScrollState())
                            .horizontalScroll(rememberScrollState())
                    ) {
                        Text(
                            text = remoteConfigJson,
                            style = MaterialTheme.typography.labelSmall,
                            color = palette.textPrimary
                        )
                    }
                }
            }
        }

        // Auto-Polling & Live Hot-Reload Controls
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(palette.logoEdgeBackground.copy(alpha = 0.80f))
                .border(1.dp, palette.cardBorder, RoundedCornerShape(14.dp))
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
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
                    Icon(
                        imageVector = Icons.Default.AutoMode,
                        contentDescription = null,
                        tint = NeonEmerald,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Text(
                            text = "Auto-Sync with GitHub Actions",
                            style = MaterialTheme.typography.labelMedium,
                            color = palette.textPrimary,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Automatically hot-reloads new service rules & verified links",
                            style = MaterialTheme.typography.labelSmall,
                            color = palette.textSecondary
                        )
                    }
                }

                Switch(
                    checked = autoSyncEnabled,
                    onCheckedChange = onSetAutoSyncEnabled,
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color(0xFF04130D),
                        checkedTrackColor = NeonEmerald,
                        uncheckedThumbColor = palette.textSecondary,
                        uncheckedTrackColor = palette.surface
                    ),
                    modifier = Modifier.testTag("auto_sync_github_actions_switch")
                )
            }

            // Polling Interval Selector Chips: On App Launch, Every 15m, Manual
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                AutoSyncInterval.entries.forEach { interval ->
                    val isSelected = interval == autoSyncInterval
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .minimumInteractiveComponentSize()
                            .clip(RoundedCornerShape(10.dp))
                            .background(
                                if (isSelected) palette.primaryAccent.copy(alpha = 0.20f) else palette.surface
                            )
                            .border(
                                width = if (isSelected) 1.5.dp else 1.dp,
                                color = if (isSelected) palette.primaryAccent else palette.cardBorder,
                                shape = RoundedCornerShape(10.dp)
                            )
                            .clickable { onSetAutoSyncInterval(interval) }
                            .padding(vertical = 8.dp, horizontal = 6.dp)
                            .testTag("auto_sync_interval_${interval.id}"),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = interval.label,
                            style = MaterialTheme.typography.labelSmall,
                            color = if (isSelected) palette.primaryAccent else palette.textSecondary,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                            maxLines = 1
                        )
                    }
                }
            }

            // Hot-Reload Action Output CTA Button
            Button(
                onClick = onHotReloadActionOutput,
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = NeonEmerald,
                    contentColor = Color(0xFF04130D)
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .testTag("hot_reload_action_output_button")
            ) {
                Icon(
                    imageVector = Icons.Default.Refresh,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = if (isCloudSyncing) {
                        "HOT-RELOADING ACTION OUTPUT..."
                    } else {
                        "HOT-RELOAD ACTION OUTPUT (RULES + NODES)"
                    },
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
fun GitHubReleasesOtaUpdaterCard(
    repoSlug: String,
    otaInfo: OtaReleaseInfo,
    isCheckingOta: Boolean,
    simulateV2Update: Boolean,
    onCheckOtaNow: () -> Unit,
    onToggleSimulateV2: (Boolean) -> Unit,
    onOpenOtaModal: () -> Unit,
    modifier: Modifier = Modifier
) {
    val palette = LocalNodeGuardColors.current
    val badgeColor = if (otaInfo.isUpdateAvailable) NeonAmber else NeonEmerald

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(palette.surface)
            .border(
                width = 1.5.dp,
                color = if (otaInfo.isUpdateAvailable) NeonAmber.copy(alpha = 0.65f) else palette.cardBorder,
                shape = RoundedCornerShape(20.dp)
            )
            .padding(16.dp)
            .testTag("github_releases_ota_card"),
        verticalArrangement = Arrangement.spacedBy(10.dp)
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
                Icon(
                    imageVector = Icons.Default.SystemUpdateAlt,
                    contentDescription = null,
                    tint = if (otaInfo.isUpdateAvailable) NeonAmber else palette.primaryAccent,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Column {
                    Text(
                        text = "GITHUB RELEASES OTA IN-APP UPDATER",
                        style = MaterialTheme.typography.labelLarge,
                        color = palette.textPrimary,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "/repos/$repoSlug/releases/latest • Installed: ${otaInfo.currentVersion}",
                        style = MaterialTheme.typography.labelSmall,
                        color = palette.textSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(badgeColor.copy(alpha = 0.18f))
                    .border(1.dp, badgeColor, RoundedCornerShape(8.dp))
                    .padding(horizontal = 9.dp, vertical = 4.dp)
            ) {
                Text(
                    text = if (otaInfo.isUpdateAvailable) "UPDATE: ${otaInfo.releaseTag}" else "UP TO DATE",
                    style = MaterialTheme.typography.labelSmall,
                    color = badgeColor,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        // Simulate v2.0 Update Available Toggle
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(palette.logoEdgeBackground.copy(alpha = 0.82f))
                .border(1.dp, palette.cardBorder, RoundedCornerShape(12.dp))
                .padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Simulate v2.0 Update Available",
                    style = MaterialTheme.typography.labelMedium,
                    color = NeonAmber,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Test the holographic \"New GitHub Action Build Available (v2.0)\" modal & SHA-256 staging",
                    style = MaterialTheme.typography.labelSmall,
                    color = palette.textSecondary
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            Switch(
                checked = simulateV2Update,
                onCheckedChange = onToggleSimulateV2,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = DeepObsidian,
                    checkedTrackColor = NeonAmber,
                    uncheckedThumbColor = palette.textSecondary,
                    uncheckedTrackColor = palette.surface
                ),
                modifier = Modifier.testTag("simulate_v2_ota_switch")
            )
        }

        // Action Buttons: Check Releases API + Open v2.0 Holographic Modal when available
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedButton(
                onClick = onCheckOtaNow,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .weight(1f)
                    .height(48.dp)
                    .testTag("check_ota_release_button")
            ) {
                Icon(
                    imageVector = Icons.Default.CloudSync,
                    contentDescription = null,
                    tint = palette.primaryAccent,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = if (isCheckingOta) "CHECKING..." else "CHECK RELEASES",
                    style = MaterialTheme.typography.labelMedium,
                    color = palette.primaryAccent,
                    fontWeight = FontWeight.Bold
                )
            }

            if (otaInfo.isUpdateAvailable) {
                Button(
                    onClick = onOpenOtaModal,
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = NeonAmber,
                        contentColor = DeepObsidian
                    ),
                    modifier = Modifier
                        .weight(1.25f)
                        .height(48.dp)
                        .testTag("open_v2_ota_modal_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.NewReleases,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "VIEW v2.0 & UPDATE",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

@Composable
fun HolographicOtaUpdateDialog(
    otaInfo: OtaReleaseInfo,
    installProgress: Float?,
    onDismiss: () -> Unit,
    onStartOneTapUpdate: () -> Unit
) {
    val palette = LocalNodeGuardColors.current
    val context = LocalContext.current

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = palette.surface,
        titleContentColor = palette.textPrimary,
        textContentColor = palette.textSecondary,
        title = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(
                                Brush.linearGradient(
                                    colors = listOf(NeonCyan, ElectricPurple)
                                )
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.RocketLaunch,
                            contentDescription = null,
                            tint = DeepObsidian,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Column {
                        Text(
                            text = "New GitHub Action Build Available (v2.0)",
                            style = MaterialTheme.typography.titleMedium,
                            color = NeonCyan,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "View Changelog & One-Tap Update • Tag ${otaInfo.releaseTag} (${otaInfo.commitSha})",
                            style = MaterialTheme.typography.labelSmall,
                            color = palette.textSecondary
                        )
                    }
                }
            }
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(palette.logoEdgeBackground)
                        .border(1.dp, NeonCyan.copy(alpha = 0.40f), RoundedCornerShape(10.dp))
                        .padding(10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text(
                            text = "CURRENT BUILD",
                            style = MaterialTheme.typography.labelSmall,
                            color = palette.textMuted
                        )
                        Text(
                            text = otaInfo.currentVersion,
                            style = MaterialTheme.typography.labelMedium,
                            color = palette.textSecondary,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            text = "NEW ACTION APK (${otaInfo.apkSizeMb})",
                            style = MaterialTheme.typography.labelSmall,
                            color = NeonEmerald
                        )
                        Text(
                            text = otaInfo.latestVersion,
                            style = MaterialTheme.typography.labelMedium,
                            color = NeonEmerald,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                Text(
                    text = "WHAT'S NEW IN GITHUB ACTION BUILD ${otaInfo.releaseTag}:",
                    style = MaterialTheme.typography.labelSmall,
                    color = palette.primaryAccent,
                    fontWeight = FontWeight.Bold
                )

                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    otaInfo.changelogItems.forEach { itemText ->
                        Row(verticalAlignment = Alignment.Top) {
                            Icon(
                                imageVector = Icons.Default.CheckCircle,
                                contentDescription = null,
                                tint = NeonEmerald,
                                modifier = Modifier
                                    .padding(top = 2.dp)
                                    .size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = itemText,
                                style = MaterialTheme.typography.labelSmall,
                                color = palette.textPrimary
                            )
                        }
                    }
                }

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(palette.logoEdgeBackground.copy(alpha = 0.85f))
                        .border(1.dp, palette.cardBorder, RoundedCornerShape(10.dp))
                        .padding(8.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Verified,
                            contentDescription = null,
                            tint = NeonEmerald,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "SHA-256 Digest Pinning:",
                            style = MaterialTheme.typography.labelSmall,
                            color = NeonEmerald,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Text(
                        text = otaInfo.sha256Checksum.take(36) + "…",
                        style = MaterialTheme.typography.labelSmall,
                        color = palette.textSecondary
                    )
                }

                if (installProgress != null) {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = if (installProgress >= 1f) {
                                    "APK Verified & Staged ✓ Ready for PackageInstaller"
                                } else {
                                    "Downloading & Verifying SHA-256 Chunks..."
                                },
                                style = MaterialTheme.typography.labelSmall,
                                color = NeonCyan,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "${(installProgress * 100).toInt()}%",
                                style = MaterialTheme.typography.labelSmall,
                                color = NeonEmerald,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        LinearProgressIndicator(
                            progress = { installProgress },
                            color = NeonEmerald,
                            trackColor = palette.logoEdgeBackground,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(6.dp)
                                .clip(RoundedCornerShape(50))
                        )
                    }
                }
            }
        },
        confirmButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = {
                        SciFiSoundEngine.playHoloClick()
                        try {
                            val browserIntent = Intent(
                                Intent.ACTION_VIEW,
                                Uri.parse(otaInfo.apkDownloadUrl)
                            ).apply {
                                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            }
                            context.startActivity(browserIntent)
                        } catch (_: Exception) {
                            Toast.makeText(
                                context,
                                "Release URL: ${otaInfo.apkDownloadUrl}",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    },
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.testTag("ota_modal_open_github_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.OpenInNew,
                        contentDescription = null,
                        tint = palette.primaryAccent,
                        modifier = Modifier.size(15.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "GITHUB",
                        style = MaterialTheme.typography.labelSmall,
                        color = palette.primaryAccent,
                        fontWeight = FontWeight.Bold
                    )
                }

                Button(
                    onClick = onStartOneTapUpdate,
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = NeonEmerald,
                        contentColor = Color(0xFF04130D)
                    ),
                    modifier = Modifier.testTag("ota_modal_one_tap_update_button")
                ) {
                    Text(
                        text = if (installProgress != null && installProgress >= 1f) {
                            "STAGED ✓"
                        } else {
                            "ONE-TAP UPDATE"
                        },
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.testTag("ota_modal_dismiss_button")
            ) {
                Text(
                    text = "CLOSE",
                    style = MaterialTheme.typography.labelMedium,
                    color = palette.textSecondary
                )
            }
        }
    )
}

@Composable
private fun MonitorMetricTile(
    label: String,
    value: String,
    accent: Color,
    modifier: Modifier = Modifier
) {
    val palette = LocalNodeGuardColors.current
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(palette.surface)
            .border(1.dp, accent.copy(alpha = 0.35f), RoundedCornerShape(10.dp))
            .padding(horizontal = 8.dp, vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = value,
            style = MaterialTheme.typography.labelMedium,
            color = accent,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = palette.textMuted,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}
