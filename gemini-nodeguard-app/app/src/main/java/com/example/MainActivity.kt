package com.example

import android.app.Activity
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.Hub
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.outlined.BugReport
import androidx.compose.material.icons.outlined.CloudSync
import androidx.compose.material.icons.outlined.Hub
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.model.AppTab
import com.example.security.CaughtDiagnosticReport
import com.example.security.DiagnosticErrorCollector
import com.example.ui.components.BrandTopAppBar
import com.example.ui.components.CosmicParticleBackground
import com.example.ui.components.HolographicOtaUpdateDialog
import com.example.ui.components.ReleaseManifestDialog
import com.example.ui.screens.AppSecLabScreen
import com.example.ui.screens.CloudCoreScreen
import com.example.ui.screens.ScannerScreen
import com.example.ui.screens.ShieldScreen
import com.example.ui.theme.GeminiNodeGuardTheme
import com.example.ui.theme.JetBrainsMonoFontFamily
import com.example.ui.theme.LocalNodeGuardColors
import com.example.ui.theme.NeonAmber
import com.example.ui.theme.NeonCyan
import com.example.viewmodel.MainViewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Ensure FLAG_SECURE is cleared on the Window so Android SurfaceFlinger does not
        // output a solid black frame to the virtual display stream in browser previews.
        try {
            window?.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
        } catch (_: Throwable) {
            // Safe fallback if window flags are unavailable
        }
        try {
            enableEdgeToEdge()
        } catch (_: Throwable) {
            // Safe fallback on custom host window managers
        }
        DiagnosticErrorCollector.installGlobalBoundaryHandler()
        setContent {
            GeminiNodeGuardApp()
        }
    }
}

@Composable
fun GeminiNodeGuardApp(
    mainViewModel: MainViewModel = viewModel()
) {
    val uiState by mainViewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var isReleaseManifestOpen by remember { mutableStateOf(false) }

    LaunchedEffect(uiState.isFlagSecureEnabled) {
        try {
            val activity = context as? Activity ?: return@LaunchedEffect
            activity.window?.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
        } catch (_: Throwable) {
            // Ignore window flag exceptions on virtual displays
        }
    }

    GeminiNodeGuardTheme(isDarkMode = uiState.isDarkMode) {
        val palette = LocalNodeGuardColors.current

        Scaffold(
            modifier = Modifier
                .fillMaxSize()
                .background(palette.background),
            containerColor = palette.background,
            contentWindowInsets = WindowInsets.safeDrawing,
            topBar = {
                BrandTopAppBar(
                    isDarkMode = uiState.isDarkMode,
                    isSoundEnabled = uiState.isSoundEnabled,
                    isPrivacyShieldActive = uiState.redactSensitiveLogs,
                    isFlagSecureActive = uiState.isFlagSecureEnabled,
                    hasSealedVault = uiState.vaultStatus.hasEncryptedPat,
                    isOtaUpdateAvailable = uiState.otaReleaseInfo.isUpdateAvailable,
                    onToggleThemeMode = mainViewModel::toggleThemeMode,
                    onToggleSoundEnabled = mainViewModel::toggleSoundEnabled,
                    onTogglePrivacyShield = mainViewModel::toggleLogRedaction,
                    onOpenOtaModal = mainViewModel::openOtaModal,
                    onOpenReleaseManifest = { isReleaseManifestOpen = true }
                )
            },
            bottomBar = {
                CyberNeonBottomBar(
                    activeTab = uiState.activeTab,
                    onSelectTab = mainViewModel::selectTab
                )
            }
        ) { innerPadding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(palette.background)
                    .padding(innerPadding)
            ) {
                // Global 60fps Cosmic Particle & Nebula Telemetry Background
                CosmicParticleBackground(
                    isScanningActive = uiState.isProbing ||
                        uiState.isBatchScanning ||
                        uiState.isCloudSyncing ||
                        uiState.isDispatchingWorkflow ||
                        uiState.isRunningFuzzSuite,
                    activeWorkers = uiState.activeCoroutinesCount
                )

                Column(modifier = Modifier.fillMaxSize()) {
                    AnimatedVisibility(visible = uiState.lastCaughtBoundaryError != null) {
                        uiState.lastCaughtBoundaryError?.let { caughtError ->
                            ErrorBoundaryRecoveryBanner(
                                report = caughtError,
                                redactSensitive = uiState.redactSensitiveLogs,
                                isOnAppSecTab = uiState.activeTab == AppTab.APPSEC_LAB,
                                onOpenDiagnosticReport = {
                                    mainViewModel.selectTab(AppTab.APPSEC_LAB)
                                },
                                onDismiss = mainViewModel::dismissBoundaryErrorBanner
                            )
                        }
                    }

                    Box(modifier = Modifier.weight(1f)) {
                        when (uiState.activeTab) {
                            AppTab.SHIELD -> ShieldScreen(
                                uiState = uiState,
                                onSelectScenario = mainViewModel::selectScenario,
                                onStartProbe = mainViewModel::startGeminiProbe,
                                onToggleRedaction = mainViewModel::toggleLogRedaction
                            )
                            AppTab.SCANNER -> ScannerScreen(
                                uiState = uiState,
                                onReturnToShield = { mainViewModel.selectTab(AppTab.SHIELD) },
                                onLoad15SampleNodes = mainViewModel::load15RealisticMixedNodes,
                                onOpenImportDialog = mainViewModel::openImportDialog,
                                onDismissImportDialog = mainViewModel::dismissImportDialog,
                                onImportBulkNodes = mainViewModel::importBulkNodes,
                                onStartBatchScan = mainViewModel::startBatchScan,
                                onStopBatchScan = mainViewModel::stopBatchScan,
                                onSetConcurrencyLimit = mainViewModel::setConcurrencyLimit,
                                onSetProbeTimeoutMs = mainViewModel::setProbeTimeoutMs,
                                onSelectFilter = mainViewModel::selectScannerFilter,
                                onSelectDynamicRule = mainViewModel::selectDynamicRuleFilter,
                                onInspectNode = mainViewModel::inspectNodeTelemetry,
                                onActivateNodeInShield = mainViewModel::activateNodeInShield,
                                onToggleRedaction = mainViewModel::toggleLogRedaction
                            )
                            AppTab.CLOUD_CORE -> CloudCoreScreen(
                                uiState = uiState,
                                onReturnToShield = { mainViewModel.selectTab(AppTab.SHIELD) },
                                onOpenScannerTab = { mainViewModel.selectTab(AppTab.SCANNER) },
                                onUpdateRepoSlug = mainViewModel::updateGithubRepoSlug,
                                onUpdateGistId = mainViewModel::updateGistOrArtifactId,
                                onUpdatePatToken = mainViewModel::updateGithubPatToken,
                                onTogglePatVisibility = mainViewModel::togglePatVisibility,
                                onSyncCloudNodes = mainViewModel::syncVerifiedNodesFromCloud,
                                onTriggerWorkflowDispatch = mainViewModel::triggerGithubWorkflowDispatch,
                                onHotReloadActionOutput = mainViewModel::hotReloadActionOutput,
                                onSetLiveActionMonitorEnabled = mainViewModel::setLiveActionMonitorEnabled,
                                onPollActionStatusNow = mainViewModel::pollActionRunStatusNow,
                                onSetAutoSyncEnabled = mainViewModel::setAutoSyncEnabled,
                                onSetAutoSyncInterval = mainViewModel::setAutoSyncInterval,
                                onJumpToScannerWithRule = { ruleId ->
                                    mainViewModel.selectDynamicRuleFilter(ruleId)
                                    mainViewModel.selectTab(AppTab.SCANNER)
                                },
                                onCheckOtaRelease = mainViewModel::checkGitHubReleasesOta,
                                onToggleSimulateV2Ota = mainViewModel::toggleSimulateV2Update,
                                onOpenOtaModal = mainViewModel::openOtaModal,
                                onSelectConfigNode = mainViewModel::selectConfigNode,
                                onUpdateSingBoxOptions = mainViewModel::updateSingBoxOptions,
                                onSetCoreExecutionMode = mainViewModel::setCoreExecutionMode,
                                onRunNativeBenchmark = mainViewModel::runNativeCoreBenchmark,
                                onZeroizeVault = mainViewModel::zeroizeMemoryAndWipeVault,
                                onToggleRedaction = mainViewModel::toggleLogRedaction
                            )
                            AppTab.APPSEC_LAB -> AppSecLabScreen(
                                uiState = uiState,
                                onReturnToShield = { mainViewModel.selectTab(AppTab.SHIELD) },
                                onRunFullFuzzSuite = mainViewModel::runFullSecurityAndFuzzingSuite,
                                onSelectPentestPreset = mainViewModel::selectPentestPreset,
                                onUpdatePentestInput = mainViewModel::updatePentestSandboxInput,
                                onToggleOwaspExpanded = mainViewModel::toggleOwaspItemExpanded,
                                onTogglePrivacyShield = mainViewModel::toggleLogRedaction,
                                onZeroizeVault = mainViewModel::zeroizeMemoryAndWipeVault,
                                onTriggerBoundaryException = mainViewModel::triggerSimulatedBoundaryException,
                                onClearDiagnosticReports = mainViewModel::clearDiagnosticReports,
                                onTriggerWorkflowDispatch = mainViewModel::triggerGithubWorkflowDispatch,
                                onSyncActionRules = mainViewModel::hotReloadActionOutput,
                                onUpdatePatToken = mainViewModel::updateGithubPatToken,
                                onTogglePatVisibility = mainViewModel::togglePatVisibility,
                                onOpenReleaseManifest = { isReleaseManifestOpen = true }
                            )
                        }
                    }
                }

                if (uiState.isOtaModalOpen) {
                    HolographicOtaUpdateDialog(
                        otaInfo = uiState.otaReleaseInfo,
                        installProgress = uiState.otaInstallProgress,
                        onDismiss = mainViewModel::dismissOtaModal,
                        onStartOneTapUpdate = mainViewModel::startOtaInstallSimulation
                    )
                }

                if (isReleaseManifestOpen) {
                    ReleaseManifestDialog(
                        uiState = uiState,
                        onDismiss = { isReleaseManifestOpen = false }
                    )
                }
            }
        }
    }
}

@Composable
private fun ErrorBoundaryRecoveryBanner(
    report: CaughtDiagnosticReport,
    redactSensitive: Boolean,
    isOnAppSecTab: Boolean,
    onOpenDiagnosticReport: () -> Unit,
    onDismiss: () -> Unit
) {
    val palette = LocalNodeGuardColors.current
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .testTag("error_boundary_recovery_banner"),
        shape = RoundedCornerShape(14.dp),
        color = palette.surface.copy(alpha = 0.96f),
        border = androidx.compose.foundation.BorderStroke(1.dp, NeonAmber.copy(alpha = 0.65f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                Icon(
                    imageVector = Icons.Filled.Warning,
                    contentDescription = "Error Boundary Triggered",
                    tint = NeonAmber,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "ERROR BOUNDARY CAUGHT #${report.id} • ${report.originModule}",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontFamily = JetBrainsMonoFontFamily,
                            fontWeight = FontWeight.Bold,
                            color = NeonAmber
                        ),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = report.getSanitizedMessage(redactSensitive),
                        style = MaterialTheme.typography.bodySmall.copy(
                            color = palette.textSecondary
                        ),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            if (!isOnAppSecTab) {
                TextButton(
                    onClick = onOpenDiagnosticReport,
                    modifier = Modifier.testTag("error_boundary_open_appsec_button")
                ) {
                    Text(
                        text = "Diagnostic Report",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontFamily = JetBrainsMonoFontFamily,
                            fontWeight = FontWeight.Bold,
                            color = NeonCyan
                        )
                    )
                }
            }

            IconButton(
                onClick = onDismiss,
                modifier = Modifier
                    .size(32.dp)
                    .testTag("error_boundary_dismiss_button")
            ) {
                Icon(
                    imageVector = Icons.Filled.Close,
                    contentDescription = "Dismiss Diagnostic Banner",
                    tint = palette.textSecondary,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}

@Composable
private fun CyberNeonBottomBar(
    activeTab: AppTab,
    onSelectTab: (AppTab) -> Unit
) {
    val palette = LocalNodeGuardColors.current
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        palette.surface.copy(alpha = 0.92f),
                        palette.background.copy(alpha = 0.98f)
                    )
                )
            )
            .border(
                width = 1.dp,
                color = palette.cardBorder,
                shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp)
            )
            .clip(RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp))
    ) {
        NavigationBar(
            containerColor = palette.surface.copy(alpha = 0.88f),
            tonalElevation = 0.dp,
            modifier = Modifier.testTag("bottom_navigation_bar")
        ) {
            AppTab.entries.forEach { tab ->
                val isSelected = tab == activeTab
                val labelRes = when (tab) {
                    AppTab.SHIELD -> R.string.nav_shield
                    AppTab.SCANNER -> R.string.nav_scanner
                    AppTab.CLOUD_CORE -> R.string.nav_cloud_core
                    AppTab.APPSEC_LAB -> R.string.nav_appsec_lab
                }

                NavigationBarItem(
                    selected = isSelected,
                    onClick = { onSelectTab(tab) },
                    icon = {
                        BadgedBox(
                            badge = {
                                if (tab.badgeText != null) {
                                    Badge(
                                        containerColor = if (isSelected) palette.primaryAccent else palette.surfaceBright,
                                        contentColor = if (isSelected) {
                                            if (palette.isDark) Color(0xFF070B19) else Color.White
                                        } else {
                                            palette.primaryAccent
                                        }
                                    ) {
                                        Text(
                                            text = tab.badgeText,
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            }
                        ) {
                            Icon(
                                imageVector = tabIcon(tab, isSelected),
                                contentDescription = stringResource(id = labelRes)
                            )
                        }
                    },
                    label = {
                        Text(
                            text = stringResource(id = labelRes),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                        )
                    },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = palette.primaryAccent,
                        selectedTextColor = palette.textPrimary,
                        indicatorColor = palette.primaryAccent.copy(alpha = 0.16f),
                        unselectedIconColor = palette.textMuted,
                        unselectedTextColor = palette.textSecondary
                    ),
                    modifier = Modifier.testTag("nav_tab_${tab.route}")
                )
            }
        }
    }
}

private fun tabIcon(tab: AppTab, isSelected: Boolean): ImageVector {
    return when (tab) {
        AppTab.SHIELD -> if (isSelected) Icons.Filled.Shield else Icons.Outlined.Shield
        AppTab.SCANNER -> if (isSelected) Icons.Filled.Hub else Icons.Outlined.Hub
        AppTab.CLOUD_CORE -> if (isSelected) Icons.Filled.CloudSync else Icons.Outlined.CloudSync
        AppTab.APPSEC_LAB -> if (isSelected) Icons.Filled.BugReport else Icons.Outlined.BugReport
    }
}
