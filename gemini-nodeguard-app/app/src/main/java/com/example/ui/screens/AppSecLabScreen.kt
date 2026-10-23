package com.example.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.GppGood
import androidx.compose.material.icons.filled.GppMaybe
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Policy
import androidx.compose.material.icons.filled.Science
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
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
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.model.ShieldUiState
import com.example.security.AppSecFuzzingEngine
import com.example.security.CaughtDiagnosticReport
import com.example.security.DiagnosticErrorCollector
import com.example.security.FuzzTestCase
import com.example.security.FuzzTestState
import com.example.security.OwaspComplianceItem
import com.example.security.PentestPresetPayload
import com.example.security.PentestSandboxAnalysis
import com.example.audio.SciFiSoundEngine
import com.example.security.SecurityHardeningManager
import com.example.ui.components.AppSecWorkflowDispatchCard
import com.example.ui.components.ReleaseBuildManifestCard
import com.example.ui.components.SessionTelemetryExportCard
import com.example.ui.theme.ElectricPurple
import com.example.ui.theme.JetBrainsMonoFontFamily
import com.example.ui.theme.LocalNodeGuardColors
import com.example.ui.theme.NeonAmber
import com.example.ui.theme.NeonCrimson
import com.example.ui.theme.NeonCyan
import com.example.ui.theme.NeonEmerald
import com.example.ui.theme.RoyalSapphire

private val ElevatedGlass: Color
    @Composable get() = LocalNodeGuardColors.current.surface

private val DeepObsidian: Color
    @Composable get() = LocalNodeGuardColors.current.surfaceBright

private val GlassBorderSubtle: Color
    @Composable get() = LocalNodeGuardColors.current.cardBorder

private val TextPrimaryFrost: Color
    @Composable get() = LocalNodeGuardColors.current.textPrimary

private val TextSecondarySlate: Color
    @Composable get() = LocalNodeGuardColors.current.textSecondary

@Composable
fun AppSecLabScreen(
    uiState: ShieldUiState,
    onReturnToShield: () -> Unit,
    onRunFullFuzzSuite: () -> Unit,
    onSelectPentestPreset: (PentestPresetPayload) -> Unit,
    onUpdatePentestInput: (String) -> Unit,
    onToggleOwaspExpanded: (String) -> Unit,
    onTogglePrivacyShield: () -> Unit,
    onZeroizeVault: () -> Unit,
    onTriggerBoundaryException: (String) -> Unit = {},
    onClearDiagnosticReports: () -> Unit = {},
    onTriggerWorkflowDispatch: () -> Unit = {},
    onSyncActionRules: () -> Unit = {},
    onUpdatePatToken: (String) -> Unit = {},
    onTogglePatVisibility: () -> Unit = {},
    onOpenReleaseManifest: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    BackHandler(onBack = onReturnToShield)

    val context = LocalContext.current

    val sandboxAnalysis by remember(uiState.pentestSandboxInput) {
        derivedStateOf {
            AppSecFuzzingEngine.analyzeSandboxPayload(uiState.pentestSandboxInput)
        }
    }

    val owaspMatrix = remember { AppSecFuzzingEngine.owaspTop10Matrix() }

    val passedCount by remember(uiState.fuzzTestCases) {
        derivedStateOf {
            uiState.fuzzTestCases.count { it.state == FuzzTestState.PASSED }
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Transparent),
        contentAlignment = Alignment.TopCenter
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 760.dp)
                .testTag("appsec_lab_lazy_column"),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // 1. Hero Security Score & Fuzzing Runner Header
            item(key = "appsec_hero_header") {
                AppSecHeroBannerCard(
                    uiState = uiState,
                    passedCount = passedCount,
                    totalTests = uiState.fuzzTestCases.size,
                    onRunFullFuzzSuite = onRunFullFuzzSuite,
                    onTogglePrivacyShield = onTogglePrivacyShield,
                    onZeroizeVault = onZeroizeVault
                )
            }

            // 1A-00. Release Info & Build Manifest (v1.0.0-release + R8/ProGuard Keep Rules)
            item(key = "release_build_manifest_card", contentType = "release_manifest") {
                ReleaseBuildManifestCard(
                    uiState = uiState,
                    onOpenReleaseManifestModal = onOpenReleaseManifest
                )
            }

            // 1A-0. Remote GitHub Actions Security Scan & Rule Sync (workflow_dispatch via PAT)
            item(key = "appsec_workflow_dispatch_card", contentType = "workflow_dispatch") {
                AppSecWorkflowDispatchCard(
                    uiState = uiState,
                    onTriggerWorkflowDispatch = onTriggerWorkflowDispatch,
                    onSyncActionRules = onSyncActionRules,
                    onUpdatePatToken = onUpdatePatToken,
                    onTogglePatVisibility = onTogglePatVisibility
                )
            }

            // 1A. Session Diagnostic Telemetry & Security Audit Local File Exporter (.json / .md)
            item(key = "session_telemetry_export_card", contentType = "telemetry_exporter") {
                SessionTelemetryExportCard(
                    uiState = uiState,
                    owaspItems = owaspMatrix
                )
            }

            // 1B. In-Memory Error Boundary & Diagnostic Report Collector
            item(key = "diagnostic_report_section_header") {
                SectionHeaderRow(
                    icon = Icons.Default.BugReport,
                    title = "ERROR BOUNDARY & DIAGNOSTIC REPORT COLLECTOR",
                    badgeText = if (uiState.diagnosticReports.isEmpty()) {
                        "0 EXCEPTIONS • NOMINAL"
                    } else {
                        "${uiState.diagnosticReports.size} CAUGHT & ISOLATED"
                    },
                    badgeColor = if (uiState.diagnosticReports.isEmpty()) NeonEmerald else NeonAmber
                )
            }

            item(key = "diagnostic_report_collector_card") {
                DiagnosticReportCollectorCard(
                    reports = uiState.diagnosticReports,
                    redactSensitiveLogs = uiState.redactSensitiveLogs,
                    onTriggerBoundaryException = onTriggerBoundaryException,
                    onClearDiagnosticReports = onClearDiagnosticReports,
                    onCopyDiagnosticReport = {
                        val exported = DiagnosticErrorCollector.exportAllDiagnosticReports(
                            redactSensitive = uiState.redactSensitiveLogs
                        )
                        copyToClipboard(
                            context = context,
                            label = "Gemini_NodeGuard_Diagnostic_Report.log",
                            text = exported
                        )
                        Toast.makeText(
                            context,
                            "Sanitized Diagnostic Report copied to clipboard",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                )
            }

            // 2. Automated Fuzzing Test Suite Header
            item(key = "fuzz_suite_section_header") {
                SectionHeaderRow(
                    icon = Icons.Default.Science,
                    title = "AUTOMATED SECURITY & FUZZING SUITE (5-STAGE)",
                    badgeText = "$passedCount/${uiState.fuzzTestCases.size} PASSED",
                    badgeColor = if (passedCount == uiState.fuzzTestCases.size) NeonEmerald else NeonCyan
                )
            }

            // 3. Individual Fuzz Test Cards (Tests 1-5)
            items(
                items = uiState.fuzzTestCases,
                key = { "fuzz_test_${it.testNumber}" },
                contentType = { "fuzz_test_card" }
            ) { testCase ->
                FuzzTestCaseCard(testCase = testCase)
            }

            // 4. Interactive Penetration Testing Sandbox
            item(key = "pentest_sandbox_card") {
                InteractivePentestSandboxCard(
                    selectedPreset = uiState.selectedPentestPreset,
                    rawPayload = uiState.pentestSandboxInput,
                    analysis = sandboxAnalysis,
                    onSelectPreset = onSelectPentestPreset,
                    onUpdatePayload = onUpdatePentestInput
                )
            }

            // 5. OWASP Mobile Top 10 (M1 - M10) Audit Matrix Header + Export Button
            item(key = "owasp_matrix_header") {
                OwaspMatrixHeaderCard(
                    owaspItems = owaspMatrix,
                    onCopyReport = {
                        val markdown = AppSecFuzzingEngine.buildMarkdownSecurityReport(
                            fuzzResults = uiState.fuzzTestCases,
                            owaspItems = owaspMatrix,
                            vaultSnapshot = uiState.vaultStatus
                        )
                        copyToClipboard(
                            context = context,
                            label = "Gemini_NodeGuard_SECURITY.md",
                            text = markdown
                        )
                        Toast.makeText(
                            context,
                            "Full SECURITY.md OWASP Audit Report copied to clipboard",
                            Toast.LENGTH_SHORT
                        ).show()
                    },
                    onShareReport = {
                        val markdown = AppSecFuzzingEngine.buildMarkdownSecurityReport(
                            fuzzResults = uiState.fuzzTestCases,
                            owaspItems = owaspMatrix,
                            vaultSnapshot = uiState.vaultStatus
                        )
                        shareSecurityReport(context, markdown)
                    }
                )
            }

            // 6. Expandable OWASP M1-M10 Cards
            items(
                items = owaspMatrix,
                key = { "owasp_${it.code}" },
                contentType = { "owasp_card" }
            ) { item ->
                val isExpanded = uiState.expandedOwaspCodes.contains(item.code)
                OwaspAuditItemCard(
                    item = item,
                    isExpanded = isExpanded,
                    onToggleExpand = { onToggleOwaspExpanded(item.code) }
                )
            }

            item(key = "appsec_bottom_spacer") {
                Spacer(modifier = Modifier.height(28.dp))
            }
        }
    }
}

@Composable
private fun AppSecHeroBannerCard(
    uiState: ShieldUiState,
    passedCount: Int,
    totalTests: Int,
    onRunFullFuzzSuite: () -> Unit,
    onTogglePrivacyShield: () -> Unit,
    onZeroizeVault: () -> Unit
) {
    val infiniteTransition = rememberInfiniteTransition(label = "appsec_pulse")
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.30f,
        targetValue = 0.90f,
        animationSpec = infiniteRepeatable(
            animation = tween(1600, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse_alpha"
    )

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("appsec_hero_card"),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = ElevatedGlass),
        border = BorderStroke(
            width = 1.dp,
            brush = Brush.linearGradient(
                colors = listOf(
                    NeonEmerald.copy(alpha = 0.65f),
                    NeonCyan.copy(alpha = 0.45f),
                    ElectricPurple.copy(alpha = 0.55f)
                )
            )
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    // Seamless Animated Vector Security Score Emblem
                    Box(
                        modifier = Modifier.size(58.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Canvas(modifier = Modifier.fillMaxSize()) {
                            if (size.width <= 1f || size.height <= 1f) return@Canvas
                            val safeRadius = (size.minDimension / 2f).coerceAtLeast(1f)
                            drawCircle(
                                brush = Brush.radialGradient(
                                    colors = listOf(
                                        NeonEmerald.copy(alpha = 0.28f * pulseAlpha),
                                        NeonCyan.copy(alpha = 0.12f),
                                        Color.Transparent
                                    ),
                                    center = center,
                                    radius = safeRadius
                                ),
                                radius = safeRadius,
                                center = center
                            )
                            drawArc(
                                brush = Brush.sweepGradient(
                                    colors = listOf(NeonEmerald, NeonCyan, ElectricPurple, NeonEmerald)
                                ),
                                startAngle = -90f,
                                sweepAngle = 360f,
                                useCenter = false,
                                style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round)
                            )
                        }
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = "100",
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontFamily = JetBrainsMonoFontFamily,
                                    fontWeight = FontWeight.Bold,
                                    color = NeonEmerald
                                )
                            )
                            Text(
                                text = "MASVS",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontSize = 8.sp,
                                    color = TextSecondarySlate
                                )
                            )
                        }
                    }

                    Column {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.VerifiedUser,
                                contentDescription = null,
                                tint = NeonEmerald,
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = stringResource(R.string.appsec_header_subtitle),
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontFamily = JetBrainsMonoFontFamily,
                                    color = NeonEmerald,
                                    fontWeight = FontWeight.Bold
                                )
                            )
                        }
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = stringResource(R.string.appsec_header_title),
                            style = MaterialTheme.typography.titleLarge.copy(
                                fontWeight = FontWeight.Bold,
                                color = TextPrimaryFrost
                            )
                        )
                        Text(
                            text = uiState.lastFuzzSuiteSummary,
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontFamily = JetBrainsMonoFontFamily,
                                color = TextSecondarySlate,
                                fontSize = 12.sp
                            ),
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }

            // Live Firewall & Hardening Telemetry Counters
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                DefenseCounterPill(
                    label = "SSRF BLOCKS",
                    value = "${uiState.ssrfBlockedTotalCount + 3}",
                    accentColor = NeonCrimson,
                    modifier = Modifier.weight(1f)
                )
                DefenseCounterPill(
                    label = "INJ SCRUBBED",
                    value = "${uiState.injectionNeutralizedTotalCount + 2}",
                    accentColor = NeonAmber,
                    modifier = Modifier.weight(1f)
                )
                DefenseCounterPill(
                    label = "64KB GUARD",
                    value = "${uiState.oversizedPayloadsBlockedCount + 1}",
                    accentColor = ElectricPurple,
                    modifier = Modifier.weight(1f)
                )
                DefenseCounterPill(
                    label = "SUITE PASS",
                    value = "$passedCount/$totalTests",
                    accentColor = NeonEmerald,
                    modifier = Modifier.weight(1f)
                )
            }

            // Quick Security Controls Row (FLAG_SECURE + SafeLogRedactor + Vault Status)
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = DeepObsidian.copy(alpha = 0.65f),
                border = BorderStroke(1.dp, GlassBorderSubtle)
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
                                imageVector = Icons.Default.VisibilityOff,
                                contentDescription = null,
                                tint = if (uiState.isFlagSecureEnabled) NeonEmerald else TextSecondarySlate,
                                modifier = Modifier.size(16.dp)
                            )
                            Column {
                                Text(
                                    text = "Privacy Shield (FLAG_SECURE + SafeLogRedactor)",
                                    style = MaterialTheme.typography.labelMedium.copy(
                                        color = TextPrimaryFrost,
                                        fontWeight = FontWeight.Bold
                                    )
                                )
                                Text(
                                    text = "Blocks screenshots & scrubs UUIDs, GitHub PATs, SNIs & exit IPs",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        color = TextSecondarySlate
                                    )
                                )
                            }
                        }
                        Switch(
                            checked = uiState.redactSensitiveLogs,
                            onCheckedChange = { onTogglePrivacyShield() },
                            modifier = Modifier.testTag("appsec_privacy_shield_switch"),
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = DeepObsidian,
                                checkedTrackColor = NeonEmerald
                            )
                        )
                    }

                    HorizontalDivider(color = GlassBorderSubtle.copy(alpha = 0.6f))

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
                                imageVector = Icons.Default.Lock,
                                contentDescription = null,
                                tint = if (uiState.vaultStatus.hasEncryptedPat) NeonCyan else NeonAmber,
                                modifier = Modifier.size(16.dp)
                            )
                            Column {
                                Text(
                                    text = "Vault: ${uiState.vaultStatus.keystoreProvider}",
                                    style = MaterialTheme.typography.labelMedium.copy(
                                        color = TextPrimaryFrost,
                                        fontWeight = FontWeight.Bold
                                    )
                                )
                                Text(
                                    text = "${uiState.vaultStatus.lastVaultOperation} • Blob: ${uiState.vaultStatus.encryptedBlobPreviewHex}",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        color = TextSecondarySlate
                                    ),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
            }

            // Primary Action Row: Run Full Fuzzing Suite + Zeroize Vault
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Button(
                    onClick = onRunFullFuzzSuite,
                    enabled = !uiState.isRunningFuzzSuite,
                    modifier = Modifier
                        .weight(1f)
                        .height(50.dp)
                        .testTag("run_fuzzing_suite_button"),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = NeonEmerald,
                        contentColor = Color(0xFF04130D)
                    )
                ) {
                    if (uiState.isRunningFuzzSuite) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp,
                            color = Color(0xFF04130D)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = stringResource(R.string.btn_running_fuzzing_suite),
                            style = MaterialTheme.typography.labelLarge.copy(
                                fontFamily = JetBrainsMonoFontFamily,
                                fontWeight = FontWeight.Bold
                            ),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Default.PlayArrow,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = stringResource(R.string.btn_run_fuzzing_suite),
                            style = MaterialTheme.typography.labelLarge.copy(
                                fontFamily = JetBrainsMonoFontFamily,
                                fontWeight = FontWeight.Bold
                            ),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                OutlinedButton(
                    onClick = onZeroizeVault,
                    modifier = Modifier
                        .height(50.dp)
                        .testTag("appsec_zeroize_vault_button"),
                    shape = RoundedCornerShape(14.dp),
                    border = BorderStroke(1.dp, NeonCrimson.copy(alpha = 0.7f)),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = NeonCrimson)
                ) {
                    Icon(
                        imageVector = Icons.Default.DeleteForever,
                        contentDescription = "Zeroize Memory and Wipe Vault",
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "ZEROIZE",
                        style = MaterialTheme.typography.labelMedium.copy(
                            fontFamily = JetBrainsMonoFontFamily,
                            fontWeight = FontWeight.Bold
                        )
                    )
                }
            }
        }
    }
}

@Composable
private fun DefenseCounterPill(
    label: String,
    value: String,
    accentColor: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        color = accentColor.copy(alpha = 0.1f),
        border = BorderStroke(1.dp, accentColor.copy(alpha = 0.35f))
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = value,
                style = MaterialTheme.typography.titleMedium.copy(
                    fontFamily = JetBrainsMonoFontFamily,
                    fontWeight = FontWeight.Bold,
                    color = accentColor
                )
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall.copy(
                    fontSize = 9.sp,
                    fontFamily = JetBrainsMonoFontFamily,
                    color = TextSecondarySlate
                ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun FuzzTestCaseCard(
    testCase: FuzzTestCase
) {
    val statusColor by animateColorAsState(
        targetValue = when (testCase.state) {
            FuzzTestState.PASSED -> NeonEmerald
            FuzzTestState.RUNNING -> NeonCyan
            FuzzTestState.FAILED -> NeonCrimson
            FuzzTestState.IDLE -> RoyalSapphire
        },
        animationSpec = tween(250),
        label = "fuzz_status_color"
    )

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("fuzz_test_card_${testCase.testNumber}"),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = ElevatedGlass),
        border = BorderStroke(1.dp, statusColor.copy(alpha = 0.45f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = statusColor.copy(alpha = 0.16f),
                    border = BorderStroke(1.dp, statusColor.copy(alpha = 0.5f))
                ) {
                    Text(
                        text = "ASSERTION #${testCase.testNumber}",
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontFamily = JetBrainsMonoFontFamily,
                            fontWeight = FontWeight.Bold,
                            color = statusColor
                        )
                    )
                }

                // Animated Pass/Fail/Running Badge + Execution Time in ms
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = testCase.executionTimeMs?.let { "${it} ms" } ?: "-- ms",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontFamily = JetBrainsMonoFontFamily,
                            fontWeight = FontWeight.Bold,
                            color = TextSecondarySlate
                        )
                    )
                    Surface(
                        shape = RoundedCornerShape(50),
                        color = statusColor.copy(alpha = 0.18f),
                        border = BorderStroke(1.dp, statusColor)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            when (testCase.state) {
                                FuzzTestState.RUNNING -> {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(11.dp),
                                        strokeWidth = 1.6.dp,
                                        color = statusColor
                                    )
                                }
                                FuzzTestState.PASSED -> {
                                    Icon(
                                        imageVector = Icons.Default.CheckCircle,
                                        contentDescription = null,
                                        tint = statusColor,
                                        modifier = Modifier.size(13.dp)
                                    )
                                }
                                else -> {
                                    Icon(
                                        imageVector = Icons.Default.Warning,
                                        contentDescription = null,
                                        tint = statusColor,
                                        modifier = Modifier.size(13.dp)
                                    )
                                }
                            }
                            Text(
                                text = testCase.state.name,
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontFamily = JetBrainsMonoFontFamily,
                                    fontWeight = FontWeight.Bold,
                                    color = statusColor
                                )
                            )
                        }
                    }
                }
            }

            Text(
                text = testCase.title,
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.Bold,
                    color = TextPrimaryFrost
                )
            )

            // Injected Attack Vector Snippet
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = DeepObsidian.copy(alpha = 0.72f),
                border = BorderStroke(1.dp, GlassBorderSubtle)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(10.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = "INJECTED EXPLOIT VECTOR:",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontSize = 10.sp,
                            fontFamily = JetBrainsMonoFontFamily,
                            color = NeonAmber
                        )
                    )
                    Text(
                        text = testCase.attackVectorPayload,
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontFamily = JetBrainsMonoFontFamily,
                            fontSize = 11.sp,
                            color = TextPrimaryFrost
                        )
                    )
                }
            }

            // Firewall / Sanitizer Assertion Outcome
            Row(
                verticalAlignment = Alignment.Top,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Shield,
                    contentDescription = null,
                    tint = statusColor,
                    modifier = Modifier
                        .size(16.dp)
                        .padding(top = 2.dp)
                )
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        text = testCase.expectedDefense,
                        style = MaterialTheme.typography.labelSmall.copy(
                            color = TextSecondarySlate
                        )
                    )
                    Text(
                        text = testCase.assertionResultDetail,
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontFamily = JetBrainsMonoFontFamily,
                            fontSize = 11.sp,
                            color = statusColor,
                            fontWeight = FontWeight.Medium
                        )
                    )
                }
            }
        }
    }
}

@Composable
private fun InteractivePentestSandboxCard(
    selectedPreset: PentestPresetPayload,
    rawPayload: String,
    analysis: PentestSandboxAnalysis,
    onSelectPreset: (PentestPresetPayload) -> Unit,
    onUpdatePayload: (String) -> Unit
) {
    val verdictColor = when {
        analysis.ssrfBlocked || !analysis.within64KbCap -> NeonCrimson
        analysis.injectionDetected -> NeonAmber
        else -> NeonEmerald
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("pentest_sandbox_card"),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = ElevatedGlass),
        border = BorderStroke(1.dp, NeonCyan.copy(alpha = 0.5f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Terminal,
                    contentDescription = null,
                    tint = NeonCyan,
                    modifier = Modifier.size(20.dp)
                )
                Column {
                    Text(
                        text = stringResource(R.string.pentest_sandbox_title),
                        style = MaterialTheme.typography.labelLarge.copy(
                            fontFamily = JetBrainsMonoFontFamily,
                            fontWeight = FontWeight.Bold,
                            color = NeonCyan
                        )
                    )
                    Text(
                        text = "Select an exploit preset or enter a custom URI to inspect real-time firewall neutralization",
                        style = MaterialTheme.typography.labelSmall.copy(
                            color = TextSecondarySlate
                        )
                    )
                }
            }

            // Preset Exploit Chips
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                PentestPresetPayload.entries.forEach { preset ->
                    val isSelected = preset == selectedPreset
                    FilterChip(
                        selected = isSelected,
                        onClick = { onSelectPreset(preset) },
                        modifier = Modifier.testTag("pentest_preset_chip_${preset.id}"),
                        label = {
                            Text(
                                text = preset.label,
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontFamily = JetBrainsMonoFontFamily,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                )
                            )
                        },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.BugReport,
                                contentDescription = null,
                                modifier = Modifier.size(15.dp)
                            )
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = NeonCyan.copy(alpha = 0.2f),
                            selectedLabelColor = NeonCyan,
                            selectedLeadingIconColor = NeonCyan
                        )
                    )
                }
            }

            // Live Input Field
            OutlinedTextField(
                value = rawPayload,
                onValueChange = onUpdatePayload,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("pentest_sandbox_input"),
                label = {
                    Text(
                        text = "Malicious / Untrusted Proxy URI or Log Payload",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontFamily = JetBrainsMonoFontFamily
                        )
                    )
                },
                textStyle = MaterialTheme.typography.labelSmall.copy(
                    fontFamily = JetBrainsMonoFontFamily,
                    color = TextPrimaryFrost,
                    fontSize = 11.sp
                ),
                minLines = 2,
                maxLines = 4,
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = NeonCyan,
                    unfocusedBorderColor = GlassBorderSubtle
                )
            )

            // Animated Red-to-Cyan Laser Containment Grid (Active when threat is intercepted)
            CyberLaserContainmentGrid(
                isThreatIntercepted = analysis.isThreatBlockedOrNeutralized,
                verdictBadge = analysis.verdictBadge,
                matchedRule = analysis.ssrfRuleMatched,
                sanitizedPreview = analysis.sanitizedCanonicalOutput
            )

            // Real-Time Firewall Verdict Banner
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = verdictColor.copy(alpha = 0.13f),
                border = BorderStroke(1.dp, verdictColor.copy(alpha = 0.65f))
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(
                        imageVector = if (analysis.isThreatBlockedOrNeutralized) Icons.Default.GppMaybe else Icons.Default.GppGood,
                        contentDescription = null,
                        tint = verdictColor,
                        modifier = Modifier.size(24.dp)
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = analysis.verdictBadge,
                            style = MaterialTheme.typography.labelMedium.copy(
                                fontFamily = JetBrainsMonoFontFamily,
                                fontWeight = FontWeight.Bold,
                                color = verdictColor
                            )
                        )
                        Text(
                            text = "Payload Size: ${analysis.payloadByteSize} B • 64KB Cap: ${if (analysis.within64KbCap) "PASS" else "BLOCKED"} • SSRF: ${analysis.ssrfRuleMatched}",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontFamily = JetBrainsMonoFontFamily,
                                fontSize = 10.sp,
                                color = TextSecondarySlate
                            )
                        )
                    }
                }
            }

            // Detailed Breakdown Grid (SSRF + Injection Sanitizer + SafeLogRedactor Output)
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = DeepObsidian.copy(alpha = 0.72f),
                border = BorderStroke(1.dp, GlassBorderSubtle)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    SandboxLayerRow(
                        layerTitle = "1. SSRF FIREWALL INSPECTION:",
                        layerValue = if (analysis.ssrfBlocked) {
                            "BLOCKED (${analysis.ssrfRuleMatched})"
                        } else {
                            "PASSED (${analysis.ssrfRuleMatched})"
                        },
                        valueColor = if (analysis.ssrfBlocked) NeonCrimson else NeonEmerald
                    )
                    HorizontalDivider(color = GlassBorderSubtle.copy(alpha = 0.45f))
                    SandboxLayerRow(
                        layerTitle = "2. CONFIG/SHELL INJECTION SANITIZER:",
                        layerValue = if (analysis.detectedInjectionVectors.isEmpty()) {
                            "CLEAN (0 metacharacter or breakout tokens found)"
                        } else {
                            "SCRUBBED (${analysis.detectedInjectionVectors.joinToString(", ")})"
                        },
                        valueColor = if (analysis.detectedInjectionVectors.isEmpty()) NeonEmerald else NeonAmber
                    )
                    HorizontalDivider(color = GlassBorderSubtle.copy(alpha = 0.45f))
                    SandboxLayerRow(
                        layerTitle = "3. SANITIZED CANONICAL OUTPUT:",
                        layerValue = analysis.sanitizedCanonicalOutput,
                        valueColor = TextPrimaryFrost
                    )
                    HorizontalDivider(color = GlassBorderSubtle.copy(alpha = 0.45f))
                    SandboxLayerRow(
                        layerTitle = "4. SAFELOGREDACTOR TELEMETRY OUTPUT:",
                        layerValue = analysis.safeRedactedLogOutput,
                        valueColor = NeonCyan
                    )
                }
            }
        }
    }
}

@Composable
private fun CyberLaserContainmentGrid(
    isThreatIntercepted: Boolean,
    verdictBadge: String,
    matchedRule: String,
    sanitizedPreview: String
) {
    val infiniteTransition = rememberInfiniteTransition(label = "cyber_laser_containment")
    val laserSweep by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = if (isThreatIntercepted) 1150 else 2600,
                easing = LinearEasing
            ),
            repeatMode = RepeatMode.Reverse
        ),
        label = "laser_sweep_pos"
    )
    val glitchOffset by infiniteTransition.animateFloat(
        initialValue = -1f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 180, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "glitch_jitter"
    )

    val primaryLaserColor = if (isThreatIntercepted) NeonCrimson else NeonEmerald
    val secondaryLaserColor = NeonCyan

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(92.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(DeepObsidian.copy(alpha = 0.90f))
            .drawWithCache {
                val width = size.width
                val height = size.height
                if (width <= 0f || height <= 0f) {
                    return@drawWithCache onDrawBehind {}
                }

                val gridStep = 22.dp.toPx().coerceAtLeast(12f)
                val gridStrokeWidth = 0.8.dp.toPx()
                val laserBeamWidth = 2.4.dp.toPx()
                val reticleStroke = Stroke(width = 1.6.dp.toPx())
                val centerPt = Offset(width / 2f, height / 2f)
                val glowRadius = maxOf(width * 0.55f, 1f)

                onDrawBehind {
                    if (size.width <= 0f || size.height <= 0f) return@onDrawBehind

                    // 1. Red-to-Cyan Laser Containment Field Aura
                    val fieldBrush = Brush.radialGradient(
                        colors = listOf(
                            primaryLaserColor.copy(alpha = 0.24f),
                            secondaryLaserColor.copy(alpha = 0.14f),
                            Color.Transparent
                        ),
                        center = Offset(width * laserSweep, height * 0.5f),
                        radius = glowRadius
                    )
                    drawRect(brush = fieldBrush, size = Size(width, height))

                    // 2. Diagonal & Orthogonal Containment Laser Matrix
                    var x = 0f
                    while (x <= width) {
                        val blendRatio = (x / width).coerceIn(0f, 1f)
                        val lineCol = if (blendRatio < laserSweep) secondaryLaserColor else primaryLaserColor
                        drawLine(
                            color = lineCol.copy(alpha = 0.20f),
                            start = Offset(x, 0f),
                            end = Offset(x, height),
                            strokeWidth = gridStrokeWidth
                        )
                        x += gridStep
                    }
                    var y = 0f
                    while (y <= height) {
                        drawLine(
                            color = secondaryLaserColor.copy(alpha = 0.16f),
                            start = Offset(0f, y),
                            end = Offset(width, y),
                            strokeWidth = gridStrokeWidth
                        )
                        y += gridStep
                    }

                    // 3. Sweeping Vertical & Horizontal Neutralization Laser Beams
                    val beamX = (laserSweep * width).coerceIn(0f, width)
                    val beamY = ((1f - laserSweep) * height).coerceIn(0f, height)
                    val laserGradient = Brush.linearGradient(
                        colors = listOf(NeonCrimson, NeonCyan, NeonCrimson),
                        start = Offset(0f, beamY),
                        end = Offset(width, beamY)
                    )
                    drawLine(
                        brush = laserGradient,
                        start = Offset(0f, beamY),
                        end = Offset(width, beamY),
                        strokeWidth = laserBeamWidth,
                        cap = StrokeCap.Round
                    )
                    drawLine(
                        color = NeonCyan.copy(alpha = 0.85f),
                        start = Offset(beamX, 0f),
                        end = Offset(beamX, height),
                        strokeWidth = laserBeamWidth
                    )

                    // 4. Glitch Scanline Slices when neutralizing threat
                    if (isThreatIntercepted) {
                        val glitchY1 = (height * 0.28f + glitchOffset * 4f).coerceIn(0f, height)
                        val glitchY2 = (height * 0.72f - glitchOffset * 5f).coerceIn(0f, height)
                        drawLine(
                            color = NeonCrimson.copy(alpha = 0.65f),
                            start = Offset(width * 0.08f, glitchY1),
                            end = Offset(width * 0.92f, glitchY1),
                            strokeWidth = 1.5.dp.toPx()
                        )
                        drawLine(
                            color = NeonCyan.copy(alpha = 0.65f),
                            start = Offset(width * 0.14f, glitchY2),
                            end = Offset(width * 0.86f, glitchY2),
                            strokeWidth = 1.5.dp.toPx()
                        )
                    }

                    // 5. Target Lock Reticle at Laser Intersection
                    val lockPt = Offset(beamX, beamY)
                    drawCircle(
                        color = if (isThreatIntercepted) NeonCrimson else NeonEmerald,
                        radius = 8.dp.toPx(),
                        center = lockPt,
                        style = reticleStroke
                    )
                    drawCircle(
                        color = NeonCyan,
                        radius = 3.dp.toPx(),
                        center = centerPt
                    )
                }
            }
            .border(
                width = 1.2.dp,
                brush = Brush.horizontalGradient(
                    colors = if (isThreatIntercepted) {
                        listOf(NeonCrimson, NeonCyan, NeonCrimson)
                    } else {
                        listOf(NeonEmerald, NeonCyan)
                    }
                ),
                shape = RoundedCornerShape(14.dp)
            )
            .padding(horizontal = 12.dp, vertical = 10.dp)
            .testTag("cyber_laser_containment_grid")
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (isThreatIntercepted) {
                        "LASER CONTAINMENT GRID • THREAT NEUTRALIZED"
                    } else {
                        "LASER CONTAINMENT GRID • PAYLOAD VERIFIED CLEAN"
                    },
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontFamily = JetBrainsMonoFontFamily,
                        fontWeight = FontWeight.Bold,
                        color = if (isThreatIntercepted) NeonCyan else NeonEmerald
                    )
                )
                Text(
                    text = verdictBadge,
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontFamily = JetBrainsMonoFontFamily,
                        fontWeight = FontWeight.Bold,
                        color = if (isThreatIntercepted) NeonCrimson else NeonEmerald
                    )
                )
            }

            Text(
                text = "Vector Rule: $matchedRule",
                style = MaterialTheme.typography.labelSmall.copy(
                    fontFamily = JetBrainsMonoFontFamily,
                    fontSize = 10.sp,
                    color = TextSecondarySlate
                ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Text(
                text = "Neutralized Output -> $sanitizedPreview",
                style = MaterialTheme.typography.labelSmall.copy(
                    fontFamily = JetBrainsMonoFontFamily,
                    fontSize = 10.sp,
                    color = TextPrimaryFrost
                ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun SandboxLayerRow(
    layerTitle: String,
    layerValue: String,
    valueColor: Color
) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
            text = layerTitle,
            style = MaterialTheme.typography.labelSmall.copy(
                fontFamily = JetBrainsMonoFontFamily,
                fontSize = 10.sp,
                color = TextSecondarySlate
            )
        )
        Text(
            text = layerValue,
            style = MaterialTheme.typography.labelSmall.copy(
                fontFamily = JetBrainsMonoFontFamily,
                fontSize = 11.sp,
                color = valueColor
            )
        )
    }
}

@Composable
private fun OwaspMatrixHeaderCard(
    owaspItems: List<OwaspComplianceItem>,
    onCopyReport: () -> Unit,
    onShareReport: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = ElevatedGlass),
        border = BorderStroke(1.dp, ElectricPurple.copy(alpha = 0.55f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
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
                    Icon(
                        imageVector = Icons.Default.Policy,
                        contentDescription = null,
                        tint = NeonEmerald,
                        modifier = Modifier.size(22.dp)
                    )
                    Column {
                        Text(
                            text = stringResource(R.string.owasp_matrix_title),
                            style = MaterialTheme.typography.labelLarge.copy(
                                fontFamily = JetBrainsMonoFontFamily,
                                fontWeight = FontWeight.Bold,
                                color = TextPrimaryFrost
                            )
                        )
                        Text(
                            text = "${owaspItems.size}/10 Controls Verified • Tap any card to inspect code evidence",
                            style = MaterialTheme.typography.labelSmall.copy(
                                color = TextSecondarySlate
                            )
                        )
                    }
                }

                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = NeonEmerald.copy(alpha = 0.15f),
                    border = BorderStroke(1.dp, NeonEmerald)
                ) {
                    Text(
                        text = "100 / 100",
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontFamily = JetBrainsMonoFontFamily,
                            fontWeight = FontWeight.Bold,
                            color = NeonEmerald
                        )
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Button(
                    onClick = onCopyReport,
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                        .testTag("copy_security_report_button"),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = RoyalSapphire,
                        contentColor = Color.White
                    )
                ) {
                    Icon(
                        imageVector = Icons.Default.ContentCopy,
                        contentDescription = null,
                        modifier = Modifier.size(17.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = stringResource(R.string.btn_copy_security_report),
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontFamily = JetBrainsMonoFontFamily,
                            fontWeight = FontWeight.Bold
                        ),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                OutlinedButton(
                    onClick = onShareReport,
                    modifier = Modifier
                        .height(48.dp)
                        .testTag("share_security_report_button"),
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, NeonCyan.copy(alpha = 0.6f)),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = NeonCyan)
                ) {
                    Icon(
                        imageVector = Icons.Default.Share,
                        contentDescription = "Share SECURITY.md report",
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun OwaspAuditItemCard(
    item: OwaspComplianceItem,
    isExpanded: Boolean,
    onToggleExpand: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onToggleExpand)
            .testTag("owasp_card_${item.code.lowercase()}"),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = ElevatedGlass),
        border = BorderStroke(
            width = 1.dp,
            color = if (isExpanded) NeonEmerald.copy(alpha = 0.65f) else GlassBorderSubtle
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
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
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = NeonEmerald.copy(alpha = 0.16f),
                        border = BorderStroke(1.dp, NeonEmerald.copy(alpha = 0.55f))
                    ) {
                        Text(
                            text = item.code,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontFamily = JetBrainsMonoFontFamily,
                                fontWeight = FontWeight.Bold,
                                color = NeonEmerald
                            )
                        )
                    }

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = item.title,
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Bold,
                                color = TextPrimaryFrost
                            )
                        )
                        Text(
                            text = item.implementedDefense,
                            style = MaterialTheme.typography.labelSmall.copy(
                                color = TextSecondarySlate,
                                fontSize = 11.sp
                            ),
                            maxLines = if (isExpanded) 4 else 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Surface(
                        shape = CircleShape,
                        color = NeonEmerald.copy(alpha = 0.18f)
                    ) {
                        Text(
                            text = item.score,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontFamily = JetBrainsMonoFontFamily,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = NeonEmerald
                            )
                        )
                    }
                    Icon(
                        imageVector = if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = if (isExpanded) "Collapse ${item.code}" else "Expand ${item.code}",
                        tint = TextSecondarySlate
                    )
                }
            }

            AnimatedVisibility(
                visible = isExpanded,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    HorizontalDivider(color = GlassBorderSubtle)
                    Text(
                        text = "THREAT VECTOR:",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontFamily = JetBrainsMonoFontFamily,
                            color = NeonAmber,
                            fontSize = 10.sp
                        )
                    )
                    Text(
                        text = item.threatVector,
                        style = MaterialTheme.typography.bodyMedium.copy(
                            color = TextSecondarySlate
                        )
                    )
                    Text(
                        text = "ARCHITECTURAL MITIGATION:",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontFamily = JetBrainsMonoFontFamily,
                            color = NeonCyan,
                            fontSize = 10.sp
                        )
                    )
                    Text(
                        text = item.implementedDefense,
                        style = MaterialTheme.typography.bodyMedium.copy(
                            color = TextPrimaryFrost
                        )
                    )
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = DeepObsidian.copy(alpha = 0.72f),
                        border = BorderStroke(1.dp, GlassBorderSubtle)
                    ) {
                        Text(
                            text = "Module Reference: ${item.codeReference}",
                            modifier = Modifier.padding(8.dp),
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontFamily = JetBrainsMonoFontFamily,
                                fontSize = 11.sp,
                                color = NeonEmerald
                            )
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionHeaderRow(
    icon: ImageVector,
    title: String,
    badgeText: String,
    badgeColor: Color
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.weight(1f)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = badgeColor,
                modifier = Modifier.size(18.dp)
            )
            Text(
                text = title,
                style = MaterialTheme.typography.labelMedium.copy(
                    fontFamily = JetBrainsMonoFontFamily,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimaryFrost
                ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Surface(
            shape = RoundedCornerShape(50),
            color = badgeColor.copy(alpha = 0.15f),
            border = BorderStroke(1.dp, badgeColor.copy(alpha = 0.5f))
        ) {
            Text(
                text = badgeText,
                modifier = Modifier.padding(horizontal = 9.dp, vertical = 3.dp),
                style = MaterialTheme.typography.labelSmall.copy(
                    fontFamily = JetBrainsMonoFontFamily,
                    fontWeight = FontWeight.Bold,
                    color = badgeColor
                )
            )
        }
    }
}

private fun copyToClipboard(context: Context, label: String, text: String) {
    SecurityHardeningManager.SafeClipboard.copyToClipboard(
        context = context,
        label = label,
        text = text,
        isSensitive = false
    )
    SciFiSoundEngine.playCopyLaser(context)
}

private fun shareSecurityReport(context: Context, markdown: String) {
    val sendIntent = Intent(Intent.ACTION_SEND).apply {
        type = "text/markdown"
        putExtra(Intent.EXTRA_SUBJECT, "Gemini NodeGuard - OWASP Mobile Top 10 & Fuzzing Audit Report")
        putExtra(Intent.EXTRA_TEXT, markdown)
    }
    val chooser = Intent.createChooser(sendIntent, "Export SECURITY.md Audit Report")
    context.startActivity(chooser)
}

@Composable
private fun DiagnosticReportCollectorCard(
    reports: List<CaughtDiagnosticReport>,
    redactSensitiveLogs: Boolean,
    onTriggerBoundaryException: (String) -> Unit,
    onClearDiagnosticReports: () -> Unit,
    onCopyDiagnosticReport: () -> Unit
) {
    val statusColor = if (reports.isEmpty()) NeonEmerald else NeonAmber
    val simulationPresets = remember {
        listOf(
            "TLS_HANDSHAKE" to "Simulate TLS Fault",
            "JNI_BRIDGE" to "Simulate JNI Panic",
            "SUB_PARSER" to "Simulate Parser EOF",
            "ACTION_SYNC" to "Simulate Sync Exception"
        )
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("diagnostic_report_card"),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = ElevatedGlass.copy(alpha = 0.94f)),
        border = BorderStroke(1.dp, statusColor.copy(alpha = 0.45f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
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
                            .size(38.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(statusColor.copy(alpha = 0.16f))
                            .border(1.dp, statusColor.copy(alpha = 0.45f), RoundedCornerShape(10.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.BugReport,
                            contentDescription = null,
                            tint = statusColor,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Column {
                        Text(
                            text = "Diagnostic Report • In-Memory Error Collector",
                            style = MaterialTheme.typography.titleSmall.copy(
                                fontWeight = FontWeight.Bold,
                                color = TextPrimaryFrost
                            )
                        )
                        Text(
                            text = if (redactSensitiveLogs) {
                                "Ring Buffer (Max 25) • Zero-Trust Log Redaction: ACTIVE"
                            } else {
                                "Ring Buffer (Max 25) • Raw Stack Trace Mode"
                            },
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontFamily = JetBrainsMonoFontFamily,
                                fontSize = 11.sp,
                                color = TextSecondarySlate
                            )
                        )
                    }
                }

                Surface(
                    shape = RoundedCornerShape(50),
                    color = statusColor.copy(alpha = 0.15f),
                    border = BorderStroke(1.dp, statusColor.copy(alpha = 0.5f))
                ) {
                    Text(
                        text = if (reports.isEmpty()) "BOUNDARY CLEAN" else "${reports.size} LOGGED",
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontFamily = JetBrainsMonoFontFamily,
                            fontWeight = FontWeight.Bold,
                            color = statusColor
                        )
                    )
                }
            }

            Text(
                text = "Intercepts runtime exceptions across Coroutine workers, JNI bridges, and UI boundaries without terminating the app. Test the Error Boundary below to inspect live stack trace scrubbing:",
                style = MaterialTheme.typography.bodySmall.copy(
                    color = TextSecondarySlate
                )
            )

            // Fault Injection / Error Boundary Simulation Chips
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                simulationPresets.forEach { (presetKey, label) ->
                    FilterChip(
                        selected = false,
                        onClick = { onTriggerBoundaryException(presetKey) },
                        label = {
                            Text(
                                text = label,
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontFamily = JetBrainsMonoFontFamily,
                                    fontWeight = FontWeight.SemiBold
                                )
                            )
                        },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.Warning,
                                contentDescription = null,
                                tint = NeonAmber,
                                modifier = Modifier.size(14.dp)
                            )
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            containerColor = DeepObsidian.copy(alpha = 0.78f),
                            labelColor = TextPrimaryFrost
                        ),
                        border = FilterChipDefaults.filterChipBorder(
                            enabled = true,
                            selected = false,
                            borderColor = NeonAmber.copy(alpha = 0.45f)
                        ),
                        modifier = Modifier.testTag("simulate_boundary_${presetKey.lowercase()}")
                    )
                }
            }

            if (reports.isEmpty()) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    color = DeepObsidian.copy(alpha = 0.72f),
                    border = BorderStroke(1.dp, GlassBorderSubtle)
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = null,
                            tint = NeonEmerald,
                            modifier = Modifier.size(18.dp)
                        )
                        Text(
                            text = "No runtime exceptions caught in current session. All Error Boundaries & Coroutine supervisors operating normally.",
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontFamily = JetBrainsMonoFontFamily,
                                fontSize = 11.sp,
                                color = NeonEmerald
                            )
                        )
                    }
                }
            } else {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    reports.take(5).forEach { report ->
                        DiagnosticReportEntryItem(
                            report = report,
                            redactSensitiveLogs = redactSensitiveLogs
                        )
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Button(
                    onClick = onCopyDiagnosticReport,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("copy_diagnostic_report_button"),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = RoyalSapphire.copy(alpha = 0.85f),
                        contentColor = TextPrimaryFrost
                    )
                ) {
                    Icon(
                        imageVector = Icons.Default.ContentCopy,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Copy Diagnostic Report",
                        style = MaterialTheme.typography.labelMedium.copy(
                            fontWeight = FontWeight.Bold
                        )
                    )
                }

                if (reports.isNotEmpty()) {
                    OutlinedButton(
                        onClick = onClearDiagnosticReports,
                        modifier = Modifier.testTag("clear_diagnostic_reports_button"),
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, NeonCrimson.copy(alpha = 0.6f)),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = NeonCrimson)
                    ) {
                        Icon(
                            imageVector = Icons.Default.DeleteForever,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "Clear (${reports.size})",
                            style = MaterialTheme.typography.labelMedium.copy(
                                fontWeight = FontWeight.Bold
                            )
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DiagnosticReportEntryItem(
    report: CaughtDiagnosticReport,
    redactSensitiveLogs: Boolean
) {
    val sanitizedMessage = remember(report.message, redactSensitiveLogs) {
        report.getSanitizedMessage(redactSensitiveLogs)
    }
    val sanitizedStack = remember(report.stackTracePreview, redactSensitiveLogs) {
        report.getSanitizedStackTrace(redactSensitiveLogs)
    }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("diagnostic_report_item_${report.id}"),
        shape = RoundedCornerShape(12.dp),
        color = DeepObsidian.copy(alpha = 0.86f),
        border = BorderStroke(1.dp, NeonAmber.copy(alpha = 0.42f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = NeonAmber.copy(alpha = 0.18f),
                        border = BorderStroke(1.dp, NeonAmber.copy(alpha = 0.55f))
                    ) {
                        Text(
                            text = "#${report.id}",
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontFamily = JetBrainsMonoFontFamily,
                                fontWeight = FontWeight.Bold,
                                color = NeonAmber,
                                fontSize = 10.sp
                            )
                        )
                    }
                    Text(
                        text = report.originModule,
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontFamily = JetBrainsMonoFontFamily,
                            fontWeight = FontWeight.Bold,
                            color = NeonCyan,
                            fontSize = 11.sp
                        ),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Text(
                    text = "${report.timestamp.substringAfter(' ')} • RECOVERED",
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontFamily = JetBrainsMonoFontFamily,
                        color = NeonEmerald,
                        fontSize = 10.sp
                    )
                )
            }

            Text(
                text = "${report.exceptionClass.substringAfterLast('.')}: $sanitizedMessage",
                style = MaterialTheme.typography.bodySmall.copy(
                    fontFamily = JetBrainsMonoFontFamily,
                    fontWeight = FontWeight.SemiBold,
                    color = TextPrimaryFrost,
                    fontSize = 11.sp
                )
            )

            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(8.dp),
                color = DeepObsidian,
                border = BorderStroke(1.dp, GlassBorderSubtle)
            ) {
                Text(
                    text = sanitizedStack,
                    modifier = Modifier.padding(8.dp),
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontFamily = JetBrainsMonoFontFamily,
                        color = TextSecondarySlate,
                        fontSize = 10.sp,
                        lineHeight = 14.sp
                    )
                )
            }
        }
    }
}

