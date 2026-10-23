package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.audio.SciFiSoundEngine
import com.example.bridge.ActionRunStatus
import com.example.model.ShieldUiState
import com.example.security.SecurityHardeningManager
import com.example.ui.theme.ElectricPurple
import com.example.ui.theme.JetBrainsMonoFontFamily
import com.example.ui.theme.LocalNodeGuardColors
import com.example.ui.theme.NeonAmber
import com.example.ui.theme.NeonCyan
import com.example.ui.theme.NeonEmerald
import com.example.ui.theme.RoyalSapphire
import com.example.ui.theme.spatialGlassCard

@Composable
fun AppSecWorkflowDispatchCard(
    uiState: ShieldUiState,
    onTriggerWorkflowDispatch: () -> Unit,
    onSyncActionRules: () -> Unit,
    onUpdatePatToken: (String) -> Unit,
    onTogglePatVisibility: () -> Unit,
    modifier: Modifier = Modifier
) {
    val palette = LocalNodeGuardColors.current
    var showPatEditor by remember { mutableStateOf(false) }

    val hasConfiguredPat = uiState.githubPatToken.isNotBlank()
    val isBusy = uiState.isDispatchingWorkflow || uiState.isCloudSyncing
    val targetRepo = uiState.githubRepoSlug.ifBlank { "pouriyaahmadii/Gemini_NodeGuard" }

    val sanitizedStatusText = remember(uiState.lastCloudSyncStatus, uiState.redactSensitiveLogs) {
        SecurityHardeningManager.SafeLogRedactor.redactDiagnosticLog(
            rawLog = uiState.lastCloudSyncStatus,
            redactEnabled = uiState.redactSensitiveLogs
        )
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .spatialGlassCard(
                cornerRadius = 20.dp,
                accentTint = NeonCyan
            )
            .testTag("appsec_workflow_dispatch_card")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // 1. Header Row: Icon + Title + Live Workflow Run Status Pill
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(NeonCyan.copy(alpha = 0.14f))
                            .border(
                                width = 1.dp,
                                color = NeonCyan.copy(alpha = 0.45f),
                                shape = RoundedCornerShape(12.dp)
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.CloudSync,
                            contentDescription = "Remote GitHub Actions Security Scan",
                            tint = NeonCyan,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    Column {
                        Text(
                            text = "REMOTE GITHUB ACTIONS SECURITY SCAN",
                            style = MaterialTheme.typography.labelMedium.copy(
                                fontFamily = JetBrainsMonoFontFamily,
                                fontWeight = FontWeight.ExtraBold,
                                color = palette.textPrimary,
                                letterSpacing = 0.6.sp
                            )
                        )
                        Text(
                            text = "workflow_dispatch • PAT Auth & Live Rule Sync ($targetRepo)",
                            style = MaterialTheme.typography.labelSmall.copy(
                                color = palette.textSecondary,
                                fontSize = 11.sp
                            ),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                // Live Workflow Status Badge
                Surface(
                    shape = RoundedCornerShape(999.dp),
                    color = uiState.actionRunStatus.badgeColor.copy(alpha = 0.14f),
                    border = BorderStroke(1.dp, uiState.actionRunStatus.badgeColor.copy(alpha = 0.55f))
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(7.dp)
                                .clip(CircleShape)
                                .background(uiState.actionRunStatus.badgeColor)
                        )
                        Text(
                            text = "#${uiState.latestWorkflowRunNumber} • ${uiState.actionRunStatus.displayLabel}",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontFamily = JetBrainsMonoFontFamily,
                                fontFeatureSettings = "tnum",
                                color = uiState.actionRunStatus.badgeColor,
                                fontWeight = FontWeight.Bold,
                                fontSize = 10.sp
                            )
                        )
                    }
                }
            }

            // 2. Configured PAT Credential Bar + Quick Inline Config Toggle
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = palette.surfaceBright,
                border = BorderStroke(1.dp, palette.cardBorder)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(
                                imageVector = if (hasConfiguredPat) Icons.Default.Lock else Icons.Default.Key,
                                contentDescription = null,
                                tint = if (hasConfiguredPat) NeonEmerald else NeonAmber,
                                modifier = Modifier.size(16.dp)
                            )
                            Column {
                                Text(
                                    text = if (hasConfiguredPat) {
                                        "GitHub PAT Sealed in AES-256-GCM Vault"
                                    } else {
                                        "No GitHub PAT Configured (Using Safe Preview Fallback)"
                                    },
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        color = palette.textPrimary,
                                        fontWeight = FontWeight.Bold
                                    )
                                )
                                Text(
                                    text = "Endpoint: /repos/$targetRepo/actions/workflows/nodeguard-scanner.yml/dispatches",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontFamily = JetBrainsMonoFontFamily,
                                        color = palette.textSecondary,
                                        fontSize = 10.sp
                                    ),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }

                        Surface(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .clickable {
                                    SciFiSoundEngine.playHoloClick()
                                    showPatEditor = !showPatEditor
                                }
                                .testTag("appsec_configure_pat_toggle"),
                            shape = RoundedCornerShape(8.dp),
                            color = RoyalSapphire.copy(alpha = 0.16f),
                            border = BorderStroke(1.dp, NeonCyan.copy(alpha = 0.4f))
                        ) {
                            Text(
                                text = if (showPatEditor) "HIDE PAT" else "CONFIG PAT",
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontFamily = JetBrainsMonoFontFamily,
                                    color = NeonCyan,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 10.sp
                                )
                            )
                        }
                    }

                    AnimatedVisibility(
                        visible = showPatEditor,
                        enter = expandVertically() + fadeIn(),
                        exit = shrinkVertically() + fadeOut()
                    ) {
                        OutlinedTextField(
                            value = uiState.githubPatToken,
                            onValueChange = onUpdatePatToken,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 4.dp)
                                .testTag("appsec_pat_input"),
                            singleLine = true,
                            label = {
                                Text(
                                    text = "GitHub Personal Access Token (repo / workflow scope)",
                                    style = MaterialTheme.typography.labelSmall
                                )
                            },
                            visualTransformation = if (uiState.isPatVisible) {
                                VisualTransformation.None
                            } else {
                                PasswordVisualTransformation()
                            },
                            trailingIcon = {
                                IconButton(
                                    onClick = {
                                        SciFiSoundEngine.playHoloClick()
                                        onTogglePatVisibility()
                                    },
                                    modifier = Modifier.testTag("appsec_toggle_pat_visibility")
                                ) {
                                    Icon(
                                        imageVector = if (uiState.isPatVisible) {
                                            Icons.Default.VisibilityOff
                                        } else {
                                            Icons.Default.Visibility
                                        },
                                        contentDescription = "Toggle PAT Visibility",
                                        tint = NeonCyan
                                    )
                                }
                            },
                            textStyle = MaterialTheme.typography.bodySmall.copy(
                                fontFamily = JetBrainsMonoFontFamily,
                                color = palette.textPrimary
                            ),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = NeonCyan,
                                unfocusedBorderColor = palette.cardBorder,
                                focusedLabelColor = NeonCyan,
                                unfocusedLabelColor = palette.textSecondary
                            )
                        )
                    }
                }
            }

            // 3. Dedicated Action Row: Trigger workflow_dispatch + Sync Rules Now
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Button(
                    onClick = onTriggerWorkflowDispatch,
                    enabled = !isBusy,
                    modifier = Modifier
                        .weight(1f)
                        .height(50.dp)
                        .testTag("appsec_workflow_dispatch_button"),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = NeonCyan,
                        contentColor = Color(0xFF03111A),
                        disabledContainerColor = NeonCyan.copy(alpha = 0.35f),
                        disabledContentColor = Color(0xFF03111A).copy(alpha = 0.7f)
                    )
                ) {
                    if (isBusy) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp,
                            color = Color(0xFF03111A)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (uiState.isDispatchingWorkflow) {
                                "DISPATCHING SCAN (#${uiState.latestWorkflowRunNumber})..."
                            } else {
                                "SYNCING NEW RULES..."
                            },
                            style = MaterialTheme.typography.labelLarge.copy(
                                fontFamily = JetBrainsMonoFontFamily,
                                fontFeatureSettings = "tnum",
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 12.sp
                            ),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Default.Bolt,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "TRIGGER SCAN & SYNC RULES",
                            style = MaterialTheme.typography.labelLarge.copy(
                                fontFamily = JetBrainsMonoFontFamily,
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 12.sp
                            ),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                OutlinedButton(
                    onClick = onSyncActionRules,
                    enabled = !isBusy,
                    modifier = Modifier
                        .height(50.dp)
                        .testTag("appsec_sync_rules_button"),
                    shape = RoundedCornerShape(14.dp),
                    border = BorderStroke(1.dp, ElectricPurple.copy(alpha = 0.75f)),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = ElectricPurple)
                ) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = "Hot-Reload Security & Probing Rules",
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "SYNC RULES",
                        style = MaterialTheme.typography.labelMedium.copy(
                            fontFamily = JetBrainsMonoFontFamily,
                            fontWeight = FontWeight.Bold
                        )
                    )
                }
            }

            // 4. Synced Dynamic Probing & Security Rules Strip
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Security,
                            contentDescription = null,
                            tint = NeonEmerald,
                            modifier = Modifier.size(14.dp)
                        )
                        Text(
                            text = "ACTIVE SYNCED ENDPOINT RULES (${uiState.dynamicEndpointRules.size})",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontFamily = JetBrainsMonoFontFamily,
                                fontFeatureSettings = "tnum",
                                color = palette.textSecondary,
                                fontWeight = FontWeight.Bold,
                                fontSize = 10.sp
                            )
                        )
                    }

                    Text(
                        text = "Commit ${uiState.latestActionCommitHash} • ${uiState.latestActionTimestamp}",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontFamily = JetBrainsMonoFontFamily,
                            fontFeatureSettings = "tnum",
                            color = NeonCyan,
                            fontSize = 10.sp
                        )
                    )
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    uiState.dynamicEndpointRules.forEach { rule ->
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = rule.accentColor.copy(alpha = 0.12f),
                            border = BorderStroke(1.dp, rule.accentColor.copy(alpha = 0.45f))
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.CheckCircle,
                                    contentDescription = null,
                                    tint = rule.accentColor,
                                    modifier = Modifier.size(12.dp)
                                )
                                Column {
                                    Text(
                                        text = "${rule.serviceName} (${rule.expectedHttpCode})",
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            fontFamily = JetBrainsMonoFontFamily,
                                            fontFeatureSettings = "tnum",
                                            color = palette.textPrimary,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 10.sp
                                        )
                                    )
                                    Text(
                                        text = "${rule.endpointHost} • ${rule.protocolRule}",
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            fontFamily = JetBrainsMonoFontFamily,
                                            color = palette.textSecondary,
                                            fontSize = 9.sp
                                        )
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // 5. Live Workflow & Rule-Sync Diagnostic Console Line
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = palette.surfaceBright,
                border = BorderStroke(1.dp, palette.cardBorder)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 10.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Terminal,
                        contentDescription = null,
                        tint = if (uiState.actionRunStatus == ActionRunStatus.COMPLETED) NeonEmerald else NeonCyan,
                        modifier = Modifier.size(14.dp)
                    )
                    Text(
                        text = sanitizedStatusText,
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontFamily = JetBrainsMonoFontFamily,
                            fontFeatureSettings = "tnum",
                            color = palette.textPrimary,
                            fontSize = 10.sp
                        ),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.testTag("appsec_workflow_status_text")
                    )
                }
            }
        }
    }
}
