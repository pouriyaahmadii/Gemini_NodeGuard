package com.example.ui.screens

import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddLink
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Dataset
import androidx.compose.material.icons.filled.FilterAlt
import androidx.compose.material.icons.filled.IosShare
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.audio.SciFiSoundEngine
import com.example.bridge.DynamicTargetEndpointRule
import com.example.model.ShieldUiState
import com.example.parser.NodeScanStatus
import com.example.parser.ProxyNode
import com.example.parser.ProxyProtocol
import com.example.parser.ScannerFilter
import com.example.security.SecurityHardeningManager
import com.example.ui.components.BrandEmblemType
import com.example.ui.components.ImportNodesDialog
import com.example.ui.components.MiniNodeSparkline
import com.example.ui.components.NodeRawTelemetryBottomSheet
import com.example.ui.components.SeamlessLogoBox
import com.example.ui.theme.ElectricPurple
import com.example.ui.theme.LocalNodeGuardColors
import com.example.ui.theme.NeonAmber
import com.example.ui.theme.NeonCrimson
import com.example.ui.theme.NeonCyan
import com.example.ui.theme.NeonEmerald
import com.example.ui.theme.RoyalSapphire
import com.example.ui.theme.spatialGlassCard
import kotlin.math.min
import kotlin.math.roundToInt
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun ScannerScreen(
    uiState: ShieldUiState,
    onReturnToShield: () -> Unit,
    onLoad15SampleNodes: () -> Unit,
    onOpenImportDialog: () -> Unit,
    onDismissImportDialog: () -> Unit,
    onImportBulkNodes: (String) -> Unit,
    onStartBatchScan: () -> Unit,
    onStopBatchScan: () -> Unit,
    onSetConcurrencyLimit: (Int) -> Unit,
    onSetProbeTimeoutMs: (Int) -> Unit,
    onSelectFilter: (ScannerFilter) -> Unit,
    onSelectDynamicRule: (String?) -> Unit = {},
    onInspectNode: (ProxyNode?) -> Unit,
    onActivateNodeInShield: (ProxyNode) -> Unit,
    onToggleRedaction: () -> Unit,
    modifier: Modifier = Modifier
) {
    BackHandler(onBack = onReturnToShield)

    val context = LocalContext.current
    val palette = LocalNodeGuardColors.current

    // Debounced Search Input for 120fps Instantaneous Node Filtering
    var rawSearchQuery by remember { mutableStateOf("") }
    var debouncedSearchQuery by remember { mutableStateOf("") }

    LaunchedEffect(rawSearchQuery) {
        if (rawSearchQuery.isBlank()) {
            debouncedSearchQuery = ""
        } else {
            delay(160L)
            debouncedSearchQuery = rawSearchQuery.trim()
        }
    }

    val activeDynamicRule by remember(uiState.dynamicEndpointRules, uiState.selectedDynamicRuleId) {
        derivedStateOf {
            uiState.dynamicEndpointRules.firstOrNull { it.id == uiState.selectedDynamicRuleId }
        }
    }

    val filteredNodes by remember(
        uiState.scannerNodes,
        uiState.selectedScannerFilter,
        activeDynamicRule,
        debouncedSearchQuery
    ) {
        derivedStateOf {
            val rule = activeDynamicRule
            val baseList = if (rule != null) {
                uiState.scannerNodes.filter { node ->
                    matchesDynamicEndpointRule(node, rule)
                }
            } else {
                uiState.scannerNodes.filter { node ->
                    when (uiState.selectedScannerFilter) {
                        ScannerFilter.ALL -> true
                        ScannerFilter.VERIFIED_200 -> node.scanStatus == NodeScanStatus.VERIFIED_200
                        ScannerFilter.GEO_BLOCKED_403 ->
                            node.scanStatus == NodeScanStatus.GEO_BLOCKED_403 ||
                                node.scanStatus == NodeScanStatus.REGION_LOCK_400
                        ScannerFilter.TIMEOUT -> node.scanStatus == NodeScanStatus.TIMEOUT
                        ScannerFilter.SSRF_BLOCKED ->
                            node.scanStatus == NodeScanStatus.SSRF_BLOCKED || node.isPrivateOrLoopbackRisk
                        ScannerFilter.PROTO_VLESS -> node.protocol == ProxyProtocol.VLESS
                        ScannerFilter.PROTO_VMESS -> node.protocol == ProxyProtocol.VMESS
                        ScannerFilter.PROTO_HYSTERIA2 -> node.protocol == ProxyProtocol.HYSTERIA2
                        ScannerFilter.PROTO_TROJAN -> node.protocol == ProxyProtocol.TROJAN
                        ScannerFilter.PROTO_TUIC -> node.protocol == ProxyProtocol.TUIC
                    }
                }
            }

            if (debouncedSearchQuery.isEmpty()) {
                baseList
            } else {
                val q = debouncedSearchQuery.lowercase()
                baseList.filter { node ->
                    node.remark.lowercase().contains(q) ||
                        node.protocol.displayName.lowercase().contains(q) ||
                        node.exitRegion.lowercase().contains(q) ||
                        node.sni.lowercase().contains(q)
                }
            }
        }
    }

    val verifiedWorkingNodes by remember(uiState.scannerNodes) {
        derivedStateOf {
            uiState.scannerNodes.filter { it.scanStatus == NodeScanStatus.VERIFIED_200 }
        }
    }

    val verifiedNodesCount = verifiedWorkingNodes.size

    // Shared hologram scanline progress for active probing cards
    val hologramTransition = rememberInfiniteTransition(label = "hologram_scanline_transition")
    val hologramScanProgress by hologramTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1350, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "hologram_scan_progress"
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
                .widthIn(max = 660.dp)
                .testTag("scanner_lazy_column"),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 108.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // 1. Scanner Header & Import / Sample Loader Controls
            item(key = "scanner_header", contentType = "header") {
                ScannerHeaderCard(
                    uiState = uiState,
                    verifiedCount = verifiedNodesCount,
                    onOpenImportDialog = onOpenImportDialog,
                    onLoad15SampleNodes = onLoad15SampleNodes,
                    onStartBatchScan = onStartBatchScan,
                    onStopBatchScan = onStopBatchScan,
                    onToggleRedaction = onToggleRedaction
                )
            }

            // 2. Coroutine Concurrency Semaphore & Timeout Sliders
            item(key = "concurrency_controls", contentType = "controls") {
                ConcurrencyControlCard(
                    concurrencyLimit = uiState.concurrencyLimit,
                    probeTimeoutMs = uiState.probeTimeoutMs,
                    isBatchScanning = uiState.isBatchScanning,
                    activeCoroutinesCount = uiState.activeCoroutinesCount,
                    completedScanCount = uiState.completedScanCount,
                    totalNodeCount = uiState.scannerNodes.size,
                    onSetConcurrencyLimit = onSetConcurrencyLimit,
                    onSetProbeTimeoutMs = onSetProbeTimeoutMs
                )
            }

            // 3. Verified Working Google Nodes Output Panel (Individual Copy Link + Copy All)
            if (verifiedWorkingNodes.isNotEmpty()) {
                item(key = "verified_working_output_panel", contentType = "verified_panel") {
                    VerifiedWorkingLinksPanel(
                        verifiedNodes = verifiedWorkingNodes,
                        redactSensitiveLogs = uiState.redactSensitiveLogs,
                        onCopySingleNode = { targetNode ->
                            SciFiSoundEngine.playCopyLaser(context)
                            copyTextToClipboard(
                                context = context,
                                label = "Verified Google Gemini Proxy URI",
                                text = targetNode.cleanUri,
                                toastMessage = "Copied ${targetNode.remark} (${targetNode.protocol.displayName} 200 OK)"
                            )
                        },
                        onCopyAllVerified = {
                            SciFiSoundEngine.playCopyLaser(context)
                            val joined = verifiedWorkingNodes.joinToString("\n") { it.cleanUri }
                            copyTextToClipboard(
                                context = context,
                                label = "All Verified Gemini Nodes",
                                text = joined,
                                toastMessage = "Copied all ${verifiedWorkingNodes.size} working Google/Gemini links!"
                            )
                        },
                        onCopyBase64Sub = {
                            SciFiSoundEngine.playCopyLaser(context)
                            val joined = verifiedWorkingNodes.joinToString("\n") { it.cleanUri }
                            val base64Sub = android.util.Base64.encodeToString(
                                joined.toByteArray(Charsets.UTF_8),
                                android.util.Base64.NO_WRAP
                            )
                            copyTextToClipboard(
                                context = context,
                                label = "Verified Gemini Base64 Subscription",
                                text = base64Sub,
                                toastMessage = "Copied Base64 Subscription (${verifiedWorkingNodes.size} verified nodes)!"
                            )
                        }
                    )
                }
            }

            // 4. Horizontal Scrollable Filter Chips + Debounced Search Input + Dynamic GitHub Action Rules
            item(key = "scanner_filters", contentType = "filters") {
                ScannerFilterChipRow(
                    allNodes = uiState.scannerNodes,
                    selectedFilter = uiState.selectedScannerFilter,
                    dynamicRules = uiState.dynamicEndpointRules,
                    selectedDynamicRuleId = uiState.selectedDynamicRuleId,
                    latestWorkflowRun = uiState.latestWorkflowRunNumber,
                    searchQuery = rawSearchQuery,
                    onUpdateSearchQuery = { rawSearchQuery = it },
                    onSelectFilter = onSelectFilter,
                    onSelectDynamicRule = onSelectDynamicRule
                )
            }

            // 5. Ergonomic Subscription Node Cards with One-Tap Magnetic Copy Capsule & Bezier Sparkline
            itemsIndexed(
                items = filteredNodes,
                key = { _, node -> node.id },
                contentType = { _, _ -> "proxy_node_card" }
            ) { index, node ->
                ProxyNodeGlassCard(
                    node = node,
                    staggerIndex = index,
                    isHologramScanning = uiState.isBatchScanning || node.scanStatus == NodeScanStatus.SCANNING,
                    hologramScanProgress = hologramScanProgress,
                    redactSensitiveLogs = uiState.redactSensitiveLogs,
                    onInspectTelemetry = { onInspectNode(node) },
                    onCopyCleanUri = {
                        SciFiSoundEngine.playCopyLaser(context)
                        copyTextToClipboard(
                            context = context,
                            label = "NodeGuard Clean URI",
                            text = node.cleanUri,
                            toastMessage = "Copied functional ${node.protocol.displayName} link (EXTRA_IS_SENSITIVE)"
                        )
                    },
                    onExportToClient = {
                        SciFiSoundEngine.playHoloClick()
                        exportNodesToExternalClient(
                            context = context,
                            title = "Export ${node.remark} to Sing-box / v2rayNG",
                            payload = node.cleanUri
                        )
                    },
                    modifier = Modifier.animateItem(
                        fadeInSpec = tween(durationMillis = 240),
                        placementSpec = spring(
                            dampingRatio = 0.72f,
                            stiffness = Spring.StiffnessMediumLow
                        ),
                        fadeOutSpec = tween(durationMillis = 160)
                    )
                )
            }
        }

        // Floating Frosted-Glass Bottom Bulk Bar ("Copy All Verified Links (N)", "Import Sub", "Filter: Working Only")
        FloatingScannerGlassDock(
            verifiedCount = verifiedNodesCount,
            isWorkingFilterActive = uiState.selectedScannerFilter == ScannerFilter.VERIFIED_200 && uiState.selectedDynamicRuleId == null,
            onCopyAllVerified = {
                SciFiSoundEngine.playCopyLaser(context)
                val verifiedUris = verifiedWorkingNodes.joinToString("\n") { it.cleanUri }
                if (verifiedUris.isEmpty()) {
                    Toast.makeText(
                        context,
                        "No 200 OK Verified nodes available yet.",
                        Toast.LENGTH_SHORT
                    ).show()
                } else {
                    copyTextToClipboard(
                        context = context,
                        label = "All Verified Gemini Links",
                        text = verifiedUris,
                        toastMessage = "Copied all $verifiedNodesCount verified Google/Gemini links!"
                    )
                }
            },
            onOpenImportSub = {
                SciFiSoundEngine.playHoloClick()
                onOpenImportDialog()
            },
            onToggleWorkingOnlyFilter = {
                SciFiSoundEngine.playHoloClick()
                val nextFilter = if (uiState.selectedScannerFilter == ScannerFilter.VERIFIED_200) {
                    ScannerFilter.ALL
                } else {
                    ScannerFilter.VERIFIED_200
                }
                onSelectFilter(nextFilter)
            },
            onShareVerified = {
                SciFiSoundEngine.playCopyLaser(context)
                val verifiedUris = verifiedWorkingNodes.joinToString("\n") { it.cleanUri }
                if (verifiedUris.isEmpty()) {
                    Toast.makeText(
                        context,
                        "No 200 OK Verified nodes available to export yet.",
                        Toast.LENGTH_SHORT
                    ).show()
                } else {
                    exportNodesToExternalClient(
                        context = context,
                        title = "Export $verifiedNodesCount Verified Gemini Nodes",
                        payload = verifiedUris
                    )
                }
            },
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(horizontal = 16.dp, vertical = 10.dp)
        )

        // Import Bulk URIs / Subscription Dialog
        if (uiState.isImportDialogOpen) {
            ImportNodesDialog(
                onDismiss = onDismissImportDialog,
                onConfirmImport = onImportBulkNodes
            )
        }

        // Inspect Raw Telemetry ModalBottomSheet
        uiState.inspectedNode?.let { inspected ->
            NodeRawTelemetryBottomSheet(
                node = inspected,
                redactSensitiveLogs = uiState.redactSensitiveLogs,
                onDismiss = { onInspectNode(null) },
                onCopyCleanUri = { target ->
                    SciFiSoundEngine.playCopyLaser(context)
                    copyTextToClipboard(
                        context = context,
                        label = "NodeGuard Clean URI",
                        text = target.cleanUri,
                        toastMessage = "Copied functional link for ${target.remark}"
                    )
                },
                onShareToClient = { target ->
                    SciFiSoundEngine.playHoloClick()
                    exportNodesToExternalClient(
                        context = context,
                        title = "Export ${target.remark} to Sing-box / v2rayNG",
                        payload = target.cleanUri
                    )
                },
                onActivateInShield = onActivateNodeInShield
            )
        }
    }
}

/**
 * Floating Frosted-Glass Bottom Bulk Bar inside the Scanner tab:
 * Provides one-thumb access to:
 * 1. "Copy All Verified Links (N)"
 * 2. "Import Sub"
 * 3. "Filter: Working Only"
 * 4. Quick Share/Export action (`testTag("fab_export_verified_nodes")`)
 */
@Composable
private fun FloatingScannerGlassDock(
    verifiedCount: Int,
    isWorkingFilterActive: Boolean,
    onCopyAllVerified: () -> Unit,
    onOpenImportSub: () -> Unit,
    onToggleWorkingOnlyFilter: () -> Unit,
    onShareVerified: () -> Unit,
    modifier: Modifier = Modifier
) {
    val palette = LocalNodeGuardColors.current
    Row(
        modifier = modifier
            .fillMaxWidth()
            .widthIn(max = 660.dp)
            .spatialGlassCard(cornerRadius = 22.dp, accentTint = NeonEmerald, elevationDp = 12.dp)
            .padding(horizontal = 10.dp, vertical = 8.dp)
            .testTag("floating_scanner_glass_dock"),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 1. Copy All Verified Links (N)
        AnimatedCopyLinkChip(
            label = "Copy Verified ($verifiedCount)",
            copiedLabel = "Copied ($verifiedCount) ✓",
            accentColor = NeonEmerald,
            onClick = onCopyAllVerified,
            testTag = "dock_copy_all_verified_button",
            modifier = Modifier.weight(1.35f)
        )

        // 2. Import Sub
        Box(
            modifier = Modifier
                .weight(0.95f)
                .minimumInteractiveComponentSize()
                .clip(RoundedCornerShape(14.dp))
                .background(palette.logoEdgeBackground.copy(alpha = 0.85f))
                .border(0.85.dp, palette.primaryAccent.copy(alpha = 0.45f), RoundedCornerShape(14.dp))
                .clickable(onClick = onOpenImportSub)
                .padding(horizontal = 8.dp, vertical = 9.dp)
                .testTag("dock_import_sub_button"),
            contentAlignment = Alignment.Center
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.AddLink,
                    contentDescription = "Import Subscription",
                    tint = palette.primaryAccent,
                    modifier = Modifier.size(15.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = "Import Sub",
                    style = MaterialTheme.typography.labelSmall,
                    color = palette.textPrimary,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        // 3. Filter: Working Only
        Box(
            modifier = Modifier
                .weight(1.1f)
                .minimumInteractiveComponentSize()
                .clip(RoundedCornerShape(14.dp))
                .background(
                    if (isWorkingFilterActive) {
                        NeonEmerald.copy(alpha = 0.22f)
                    } else {
                        palette.logoEdgeBackground.copy(alpha = 0.85f)
                    }
                )
                .border(
                    width = if (isWorkingFilterActive) 1.25.dp else 0.85.dp,
                    color = if (isWorkingFilterActive) NeonEmerald else palette.cardBorder,
                    shape = RoundedCornerShape(14.dp)
                )
                .clickable(onClick = onToggleWorkingOnlyFilter)
                .padding(horizontal = 8.dp, vertical = 9.dp)
                .testTag("dock_filter_working_only_button"),
            contentAlignment = Alignment.Center
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.FilterAlt,
                    contentDescription = "Filter Working Only",
                    tint = if (isWorkingFilterActive) NeonEmerald else palette.textSecondary,
                    modifier = Modifier.size(15.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = if (isWorkingFilterActive) "Working ✓" else "Working Only",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (isWorkingFilterActive) NeonEmerald else palette.textPrimary,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        // 4. Quick Share / Export Verified Nodes
        IconButton(
            onClick = onShareVerified,
            modifier = Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(NeonEmerald.copy(alpha = 0.18f))
                .border(0.85.dp, NeonEmerald.copy(alpha = 0.55f), RoundedCornerShape(12.dp))
                .testTag("fab_export_verified_nodes")
        ) {
            Icon(
                imageVector = Icons.Default.IosShare,
                contentDescription = stringResource(id = R.string.fab_export_verified),
                tint = NeonEmerald,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

@Composable
private fun VerifiedWorkingLinksPanel(
    verifiedNodes: List<ProxyNode>,
    redactSensitiveLogs: Boolean,
    onCopySingleNode: (ProxyNode) -> Unit,
    onCopyAllVerified: () -> Unit,
    onCopyBase64Sub: () -> Unit
) {
    val palette = LocalNodeGuardColors.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .spatialGlassCard(cornerRadius = 20.dp, accentTint = NeonEmerald)
            .padding(16.dp)
            .testTag("verified_working_links_panel"),
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
                    imageVector = Icons.Default.Verified,
                    contentDescription = null,
                    tint = NeonEmerald,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Column {
                    Text(
                        text = "VERIFIED WORKING GOOGLE & GEMINI NODES (${verifiedNodes.size})",
                        style = MaterialTheme.typography.labelLarge,
                        color = NeonEmerald,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "HTTP 200 OK • Ready for Sing-box, v2rayNG, NekoBox & Clash Meta",
                        style = MaterialTheme.typography.labelSmall,
                        color = palette.textSecondary
                    )
                }
            }
        }

        // Bulk Export Actions: Copy All Google-Verified Links + Copy Base64 Sub
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            AnimatedCopyLinkChip(
                label = "Copy All Verified (${verifiedNodes.size})",
                accentColor = NeonEmerald,
                onClick = onCopyAllVerified,
                testTag = "copy_all_verified_links_button",
                modifier = Modifier.weight(1f)
            )
            AnimatedCopyLinkChip(
                label = "Copy Base64 Sub",
                accentColor = palette.primaryAccent,
                onClick = onCopyBase64Sub,
                testTag = "copy_base64_sub_button",
                modifier = Modifier.weight(1f)
            )
        }

        verifiedNodes.take(5).forEach { node ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(palette.logoEdgeBackground.copy(alpha = 0.80f))
                    .border(0.75.dp, palette.cardBorder, RoundedCornerShape(12.dp))
                    .padding(horizontal = 12.dp, vertical = 9.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = node.protocol.displayName,
                            style = MaterialTheme.typography.labelSmall,
                            color = node.protocol.badgeColor,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = node.remark,
                            style = MaterialTheme.typography.labelMedium,
                            color = palette.textPrimary,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Text(
                        text = "${node.maskedHost(redactSensitiveLogs)} • TTFT ${node.ttftMs ?: 128}ms • ${node.exitRegion}",
                        style = MaterialTheme.typography.labelSmall,
                        color = palette.textSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Spacer(modifier = Modifier.width(8.dp))

                AnimatedCopyLinkChip(
                    label = "Copy Link • کپی لینک",
                    accentColor = palette.primaryAccent,
                    onClick = { onCopySingleNode(node) },
                    testTag = "verified_panel_copy_${node.remark}"
                )
            }
        }
    }
}

@Composable
private fun ScannerHeaderCard(
    uiState: ShieldUiState,
    verifiedCount: Int,
    onOpenImportDialog: () -> Unit,
    onLoad15SampleNodes: () -> Unit,
    onStartBatchScan: () -> Unit,
    onStopBatchScan: () -> Unit,
    onToggleRedaction: () -> Unit
) {
    val palette = LocalNodeGuardColors.current
    val blockedCount = uiState.scannerNodes.count {
        it.scanStatus == NodeScanStatus.GEO_BLOCKED_403 ||
            it.scanStatus == NodeScanStatus.REGION_LOCK_400 ||
            it.scanStatus == NodeScanStatus.SSRF_BLOCKED
    }
    val timeoutCount = uiState.scannerNodes.count { it.scanStatus == NodeScanStatus.TIMEOUT }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .spatialGlassCard(cornerRadius = 20.dp, accentTint = palette.primaryAccent)
            .padding(18.dp)
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
                    emblemType = BrandEmblemType.GOOGLE_GEMINI_API,
                    boxSize = 44.dp,
                    accentOverride = palette.primaryAccent
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        text = stringResource(id = R.string.scanner_header_title),
                        style = MaterialTheme.typography.titleLarge,
                        color = palette.textPrimary
                    )
                    Text(
                        text = stringResource(id = R.string.scanner_header_subtitle),
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
                        color = if (uiState.redactSensitiveLogs) NeonEmerald.copy(alpha = 0.5f) else NeonAmber.copy(alpha = 0.5f),
                        shape = RoundedCornerShape(12.dp)
                    )
                    .testTag("scanner_redact_toggle_button")
            ) {
                Icon(
                    imageVector = if (uiState.redactSensitiveLogs) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                    contentDescription = stringResource(id = R.string.cd_redact_toggle),
                    tint = if (uiState.redactSensitiveLogs) NeonEmerald else NeonAmber
                )
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Summary Metric Badges Row (12dp Bento gap)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            ScannerStatBadge(
                label = "200 VERIFIED",
                count = verifiedCount,
                color = NeonEmerald,
                modifier = Modifier.weight(1f)
            )
            ScannerStatBadge(
                label = "403 / SSRF",
                count = blockedCount,
                color = NeonCrimson,
                modifier = Modifier.weight(1f)
            )
            ScannerStatBadge(
                label = "TIMEOUT",
                count = timeoutCount,
                color = NeonAmber,
                modifier = Modifier.weight(1f)
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Import & Load 15 Realistic Mixed Nodes Row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            OutlinedButton(
                onClick = onOpenImportDialog,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .weight(0.95f)
                    .height(48.dp)
                    .testTag("open_import_dialog_button")
            ) {
                Icon(
                    imageVector = Icons.Default.AddLink,
                    contentDescription = null,
                    tint = palette.primaryAccent,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = stringResource(id = R.string.btn_import_nodes),
                    style = MaterialTheme.typography.labelMedium,
                    color = palette.primaryAccent,
                    fontWeight = FontWeight.Bold
                )
            }

            OutlinedButton(
                onClick = onLoad15SampleNodes,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .weight(1.25f)
                    .height(48.dp)
                    .testTag("load_15_nodes_button")
            ) {
                Icon(
                    imageVector = Icons.Default.Dataset,
                    contentDescription = null,
                    tint = palette.tertiaryAccent,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "Load 15 Mixed Nodes",
                    style = MaterialTheme.typography.labelMedium,
                    color = palette.textPrimary,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Primary Batch Scan Trigger Button
        Button(
            onClick = if (uiState.isBatchScanning) onStopBatchScan else onStartBatchScan,
            shape = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = if (uiState.isBatchScanning) NeonCrimson else palette.primaryAccent,
                contentColor = if (palette.isDark && !uiState.isBatchScanning) Color(0xFF040711) else Color.White
            ),
            modifier = Modifier
                .fillMaxWidth()
                .height(50.dp)
                .testTag("batch_scan_button")
        ) {
            Icon(
                imageVector = if (uiState.isBatchScanning) Icons.Default.Stop else Icons.Default.PlayArrow,
                contentDescription = null,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = if (uiState.isBatchScanning) {
                    "${stringResource(id = R.string.btn_stop_batch_scan)} (${uiState.completedScanCount}/${uiState.scannerNodes.size})"
                } else {
                    "${stringResource(id = R.string.btn_start_batch_scan)} (${uiState.scannerNodes.size} NODES)"
                },
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = uiState.lastScannerBannerMessage,
            style = MaterialTheme.typography.labelSmall,
            color = palette.textSecondary
        )
    }
}

@Composable
private fun ScannerStatBadge(
    label: String,
    count: Int,
    color: Color,
    modifier: Modifier = Modifier
) {
    val palette = LocalNodeGuardColors.current
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(palette.logoEdgeBackground.copy(alpha = 0.80f))
            .border(0.75.dp, color.copy(alpha = 0.38f), RoundedCornerShape(14.dp))
            .padding(vertical = 9.dp, horizontal = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = count.toString(),
            style = MaterialTheme.typography.titleLarge,
            color = color,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = palette.textSecondary
        )
    }
}

@Composable
private fun ConcurrencyControlCard(
    concurrencyLimit: Int,
    probeTimeoutMs: Int,
    isBatchScanning: Boolean,
    activeCoroutinesCount: Int,
    completedScanCount: Int,
    totalNodeCount: Int,
    onSetConcurrencyLimit: (Int) -> Unit,
    onSetProbeTimeoutMs: (Int) -> Unit
) {
    val palette = LocalNodeGuardColors.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .spatialGlassCard(cornerRadius = 18.dp, accentTint = palette.primaryAccent)
            .padding(16.dp)
            .testTag("concurrency_controls_card")
    ) {
        // Concurrency Limit Slider (1 to 32)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = stringResource(id = R.string.label_concurrency_limit),
                style = MaterialTheme.typography.labelSmall,
                color = palette.textSecondary
            )
            Text(
                text = "$concurrencyLimit Coroutines",
                style = MaterialTheme.typography.labelMedium,
                color = palette.primaryAccent,
                fontWeight = FontWeight.Bold
            )
        }

        Slider(
            value = concurrencyLimit.toFloat(),
            onValueChange = { onSetConcurrencyLimit(it.roundToInt()) },
            valueRange = 1f..32f,
            steps = 30,
            colors = SliderDefaults.colors(
                thumbColor = palette.primaryAccent,
                activeTrackColor = palette.primaryAccent,
                inactiveTrackColor = palette.logoEdgeBackground
            ),
            modifier = Modifier
                .fillMaxWidth()
                .testTag("concurrency_slider")
        )

        Spacer(modifier = Modifier.height(4.dp))

        // Probe Socket Timeout Slider (1000ms to 10000ms)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = stringResource(id = R.string.label_probe_timeout),
                style = MaterialTheme.typography.labelSmall,
                color = palette.textSecondary
            )
            Text(
                text = "${probeTimeoutMs} ms",
                style = MaterialTheme.typography.labelMedium,
                color = palette.tertiaryAccent,
                fontWeight = FontWeight.Bold
            )
        }

        Slider(
            value = probeTimeoutMs.toFloat(),
            onValueChange = {
                val rounded = ((it / 250f).roundToInt() * 250).coerceIn(1000, 10000)
                onSetProbeTimeoutMs(rounded)
            },
            valueRange = 1000f..10000f,
            steps = 35,
            colors = SliderDefaults.colors(
                thumbColor = palette.tertiaryAccent,
                activeTrackColor = palette.tertiaryAccent,
                inactiveTrackColor = palette.logoEdgeBackground
            ),
            modifier = Modifier
                .fillMaxWidth()
                .testTag("timeout_slider")
        )

        if (isBatchScanning) {
            Spacer(modifier = Modifier.height(6.dp))
            val progress = if (totalNodeCount > 0) {
                completedScanCount.toFloat() / totalNodeCount.toFloat()
            } else {
                0f
            }
            LinearProgressIndicator(
                progress = { progress },
                color = palette.primaryAccent,
                trackColor = palette.logoEdgeBackground,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(RoundedCornerShape(50))
            )
            Spacer(modifier = Modifier.height(6.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Active Workers: $activeCoroutinesCount / $concurrencyLimit",
                    style = MaterialTheme.typography.labelSmall,
                    color = palette.primaryAccent
                )
                Text(
                    text = "Completed: $completedScanCount / $totalNodeCount",
                    style = MaterialTheme.typography.labelSmall,
                    color = NeonEmerald
                )
            }
        }
    }
}

private fun matchesDynamicEndpointRule(
    node: ProxyNode,
    rule: DynamicTargetEndpointRule
): Boolean {
    if (node.scanStatus != NodeScanStatus.VERIFIED_200) return false
    val sniMatch = node.sni.contains(rule.endpointHost.substringBefore("."), ignoreCase = true) ||
        node.diagnosticSummary.contains(rule.serviceName.substringBefore(" "), ignoreCase = true)
    if (sniMatch) return true
    val ruleProto = rule.protocolRule.lowercase()
    return when (node.protocol) {
        ProxyProtocol.VLESS -> ruleProto.contains("vless") || ruleProto.contains("xtls") || ruleProto.contains("doh")
        ProxyProtocol.HYSTERIA2 -> ruleProto.contains("hysteria2") || ruleProto.contains("quic")
        ProxyProtocol.TROJAN -> ruleProto.contains("trojan") || ruleProto.contains("tls")
        ProxyProtocol.VMESS -> ruleProto.contains("websocket") || ruleProto.contains("ws")
        ProxyProtocol.TUIC -> ruleProto.contains("quic") || ruleProto.contains("mux")
    }
}

@Composable
private fun ScannerFilterChipRow(
    allNodes: List<ProxyNode>,
    selectedFilter: ScannerFilter,
    dynamicRules: List<DynamicTargetEndpointRule>,
    selectedDynamicRuleId: String?,
    latestWorkflowRun: Int,
    searchQuery: String,
    onUpdateSearchQuery: (String) -> Unit,
    onSelectFilter: (ScannerFilter) -> Unit,
    onSelectDynamicRule: (String?) -> Unit
) {
    val palette = LocalNodeGuardColors.current
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        // Instantaneous Debounced Search Input
        OutlinedTextField(
            value = searchQuery,
            onValueChange = onUpdateSearchQuery,
            singleLine = true,
            placeholder = {
                Text(
                    text = "Search nodes by country, protocol (VLESS/Hysteria2), or SNI...",
                    style = MaterialTheme.typography.bodySmall,
                    color = palette.textMuted
                )
            },
            leadingIcon = {
                Icon(
                    imageVector = Icons.Default.Search,
                    contentDescription = "Search Proxy Nodes",
                    tint = palette.primaryAccent,
                    modifier = Modifier.size(18.dp)
                )
            },
            trailingIcon = {
                if (searchQuery.isNotEmpty()) {
                    IconButton(onClick = { onUpdateSearchQuery("") }) {
                        Icon(
                            imageVector = Icons.Default.Clear,
                            contentDescription = "Clear Search",
                            tint = palette.textMuted,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            },
            shape = RoundedCornerShape(14.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = palette.primaryAccent,
                unfocusedBorderColor = palette.cardBorder,
                focusedContainerColor = palette.surface,
                unfocusedContainerColor = palette.surface,
                focusedTextColor = palette.textPrimary,
                unfocusedTextColor = palette.textPrimary
            ),
            textStyle = MaterialTheme.typography.bodyMedium,
            modifier = Modifier
                .fillMaxWidth()
                .testTag("scanner_search_input")
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            ScannerFilter.entries.forEach { filter ->
                val isSelected = selectedDynamicRuleId == null && filter == selectedFilter
                val count = allNodes.count { node ->
                    when (filter) {
                        ScannerFilter.ALL -> true
                        ScannerFilter.VERIFIED_200 -> node.scanStatus == NodeScanStatus.VERIFIED_200
                        ScannerFilter.GEO_BLOCKED_403 ->
                            node.scanStatus == NodeScanStatus.GEO_BLOCKED_403 ||
                                node.scanStatus == NodeScanStatus.REGION_LOCK_400
                        ScannerFilter.TIMEOUT -> node.scanStatus == NodeScanStatus.TIMEOUT
                        ScannerFilter.SSRF_BLOCKED ->
                            node.scanStatus == NodeScanStatus.SSRF_BLOCKED || node.isPrivateOrLoopbackRisk
                        ScannerFilter.PROTO_VLESS -> node.protocol == ProxyProtocol.VLESS
                        ScannerFilter.PROTO_VMESS -> node.protocol == ProxyProtocol.VMESS
                        ScannerFilter.PROTO_HYSTERIA2 -> node.protocol == ProxyProtocol.HYSTERIA2
                        ScannerFilter.PROTO_TROJAN -> node.protocol == ProxyProtocol.TROJAN
                        ScannerFilter.PROTO_TUIC -> node.protocol == ProxyProtocol.TUIC
                    }
                }

                val chipColor = when (filter) {
                    ScannerFilter.ALL -> palette.primaryAccent
                    ScannerFilter.VERIFIED_200 -> NeonEmerald
                    ScannerFilter.GEO_BLOCKED_403 -> NeonCrimson
                    ScannerFilter.TIMEOUT -> NeonAmber
                    ScannerFilter.SSRF_BLOCKED -> NeonCrimson
                    ScannerFilter.PROTO_VLESS -> NeonCyan
                    ScannerFilter.PROTO_VMESS -> palette.tertiaryAccent
                    ScannerFilter.PROTO_HYSTERIA2 -> NeonAmber
                    ScannerFilter.PROTO_TROJAN -> NeonEmerald
                    ScannerFilter.PROTO_TUIC -> palette.secondaryAccent
                }

                Box(
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .clip(RoundedCornerShape(12.dp))
                        .background(if (isSelected) chipColor.copy(alpha = 0.20f) else palette.surface)
                        .border(
                            width = if (isSelected) 1.25.dp else 0.75.dp,
                            color = if (isSelected) chipColor else palette.cardBorder,
                            shape = RoundedCornerShape(12.dp)
                        )
                        .clickable { onSelectFilter(filter) }
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                        .testTag("filter_chip_${filter.name.lowercase()}"),
                    contentAlignment = Alignment.Center
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = filter.label,
                            style = MaterialTheme.typography.labelMedium,
                            color = if (isSelected) palette.textPrimary else palette.textSecondary,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(palette.logoEdgeBackground.copy(alpha = 0.80f))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = count.toString(),
                                style = MaterialTheme.typography.labelSmall,
                                color = chipColor,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }

        // Server-Driven GitHub Actions Dynamic Target Endpoint Filter Chips
        if (dynamicRules.isNotEmpty()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(palette.tertiaryAccent.copy(alpha = 0.16f))
                        .border(0.75.dp, palette.tertiaryAccent.copy(alpha = 0.45f), RoundedCornerShape(8.dp))
                        .padding(horizontal = 8.dp, vertical = 5.dp)
                ) {
                    Text(
                        text = "ACTION #$latestWorkflowRun RULES",
                        style = MaterialTheme.typography.labelSmall,
                        color = palette.tertiaryAccent,
                        fontWeight = FontWeight.Bold
                    )
                }

                dynamicRules.forEach { rule ->
                    val isRuleSelected = selectedDynamicRuleId == rule.id
                    val matchCount = allNodes.count { matchesDynamicEndpointRule(it, rule) }
                    Box(
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .clip(RoundedCornerShape(12.dp))
                            .background(
                                if (isRuleSelected) rule.accentColor.copy(alpha = 0.20f) else palette.surface
                            )
                            .border(
                                width = if (isRuleSelected) 1.25.dp else 0.75.dp,
                                color = if (isRuleSelected) rule.accentColor else rule.accentColor.copy(alpha = 0.45f),
                                shape = RoundedCornerShape(12.dp)
                            )
                            .clickable { onSelectDynamicRule(rule.id) }
                            .padding(horizontal = 10.dp, vertical = 7.dp)
                            .testTag("dynamic_rule_chip_${rule.id}"),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = rule.chipTag,
                                style = MaterialTheme.typography.labelSmall,
                                color = if (isRuleSelected) palette.textPrimary else rule.accentColor,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(palette.logoEdgeBackground.copy(alpha = 0.80f))
                                    .padding(horizontal = 5.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text = matchCount.toString(),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = rule.accentColor,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Redesigned Ergonomic Subscription Node Card (120FPS Spatial Glass):
 * - Left: Protocol emblem pill (`VLESS`, `VMess`, `Hysteria2`, `Trojan`) with subtle tinted glass
 *   background + Country/Node title + masked server host.
 * - Center-Right: Live mini Bezier sparkline + color-coded latency pill (`42 ms`) + compact
 *   verified service tags (`✓ Gemini`, `✓ AI Studio`, `✓ Google 200`).
 * - Action Bar / Right Edge: Sleek high-contrast "Copy Link" (`کپی لینک`) capsule button with
 *   magnetic tap scale (`scale(0.95f)` on press -> morphs into an Emerald `"Copied ✓"` pill).
 */
@Composable
private fun ProxyNodeGlassCard(
    node: ProxyNode,
    staggerIndex: Int,
    isHologramScanning: Boolean,
    hologramScanProgress: Float,
    redactSensitiveLogs: Boolean,
    onInspectTelemetry: () -> Unit,
    onCopyCleanUri: () -> Unit,
    onExportToClient: () -> Unit,
    modifier: Modifier = Modifier
) {
    val palette = LocalNodeGuardColors.current
    val statusColor = node.scanStatus.badgeColor

    // Staggered Spring-Physics Entry Animation on GPU RenderThread
    val entryScale = remember { Animatable(0.94f) }
    val entryTranslationY = remember { Animatable(18f) }

    LaunchedEffect(node.id) {
        delay((staggerIndex.coerceAtMost(8) * 24L))
        launch {
            entryScale.animateTo(
                targetValue = 1f,
                animationSpec = spring(
                    dampingRatio = 0.72f,
                    stiffness = Spring.StiffnessMediumLow
                )
            )
        }
        launch {
            entryTranslationY.animateTo(
                targetValue = 0f,
                animationSpec = spring(
                    dampingRatio = 0.72f,
                    stiffness = Spring.StiffnessMediumLow
                )
            )
        }
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .graphicsLayer {
                scaleX = entryScale.value
                scaleY = entryScale.value
                translationY = entryTranslationY.value
            }
            .spatialGlassCard(cornerRadius = 20.dp, accentTint = statusColor)
            .drawWithCache {
                val width = size.width
                val height = size.height
                if (width <= 0f || height <= 0f) {
                    return@drawWithCache onDrawBehind {}
                }

                val bandHeight = maxOf(height * 0.32f, 16f)
                val scanLineStrokeWidth = 1.4.dp.toPx()
                val scanLineColor = NeonCyan.copy(alpha = 0.60f)
                val bandColors = listOf(
                    Color.Transparent,
                    NeonCyan.copy(alpha = if (palette.isDark) 0.14f else 0.08f),
                    ElectricPurple.copy(alpha = if (palette.isDark) 0.10f else 0.06f),
                    Color.Transparent
                )

                onDrawBehind {
                    if (!isHologramScanning || size.width <= 0f || size.height <= 0f) return@onDrawBehind

                    val scanY = (hologramScanProgress * (height + bandHeight)) - (bandHeight * 0.5f)
                    val topY = (scanY - bandHeight * 0.5f).coerceIn(0f, height)
                    val bottomY = maxOf((scanY + bandHeight * 0.5f).coerceIn(0f, height), topY + 1f)

                    drawRect(
                        brush = Brush.verticalGradient(
                            colors = bandColors,
                            startY = topY,
                            endY = bottomY
                        ),
                        topLeft = Offset(0f, topY),
                        size = Size(width, bottomY - topY)
                    )
                    val lineY = scanY.coerceIn(0f, height)
                    drawLine(
                        color = scanLineColor,
                        start = Offset(0f, lineY),
                        end = Offset(width, lineY),
                        strokeWidth = scanLineStrokeWidth
                    )
                }
            }
            .clickable { onInspectTelemetry() }
            .padding(16.dp)
            .testTag("proxy_node_card_${node.remark}")
    ) {
        // Top Row: Left Protocol Emblem Pill + Country/Node Title + Right Latency & Status Pill
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                // Protocol Emblem Pill (VLESS, VMess, Hysteria2, Trojan) with tinted glass background
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(node.protocol.badgeColor.copy(alpha = 0.16f))
                        .border(0.85.dp, node.protocol.badgeColor.copy(alpha = 0.65f), RoundedCornerShape(8.dp))
                        .padding(horizontal = 9.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = node.protocol.displayName,
                        style = MaterialTheme.typography.labelSmall,
                        color = node.protocol.badgeColor,
                        fontWeight = FontWeight.Bold
                    )
                }
                Spacer(modifier = Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = node.remark,
                        style = MaterialTheme.typography.titleMedium,
                        color = palette.textPrimary,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "${node.maskedHost(redactSensitiveLogs)} • ${node.exitRegion}",
                        style = MaterialTheme.typography.labelSmall,
                        color = palette.textSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Spacer(modifier = Modifier.width(8.dp))

            // Color-Coded Latency Pill (tabular numerals `tnum`) + Status Badge
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                node.ttftMs?.let { ms ->
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(statusColor.copy(alpha = 0.14f))
                            .border(0.75.dp, statusColor.copy(alpha = 0.48f), RoundedCornerShape(8.dp))
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Bolt,
                                contentDescription = null,
                                tint = statusColor,
                                modifier = Modifier.size(12.dp)
                            )
                            Spacer(modifier = Modifier.width(2.dp))
                            Text(
                                text = "$ms ms",
                                style = MaterialTheme.typography.labelSmall,
                                color = statusColor,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(statusColor.copy(alpha = 0.16f))
                        .border(0.85.dp, statusColor, RoundedCornerShape(8.dp))
                        .padding(horizontal = 9.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = node.scanStatus.badgeText,
                        style = MaterialTheme.typography.labelSmall,
                        color = statusColor,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Middle Row: Compact Verified Service Tags (`✓ Gemini`, `✓ AI Studio`, `✓ Google 200`) + Live Mini Bezier Sparkline
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                // Compact Verified Service Tags
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (node.scanStatus == NodeScanStatus.VERIFIED_200) {
                        VerifiedServiceMiniPill(label = "✓ Gemini", color = NeonCyan)
                        VerifiedServiceMiniPill(label = "✓ AI Studio", color = ElectricPurple)
                        VerifiedServiceMiniPill(label = "✓ Google 200", color = NeonEmerald)
                    } else {
                        VerifiedServiceMiniPill(
                            label = "${node.transport.uppercase()} • ${node.security.uppercase()}",
                            color = palette.primaryAccent
                        )
                        VerifiedServiceMiniPill(
                            label = "SNI: ${node.maskedSni(redactSensitiveLogs)}",
                            color = statusColor
                        )
                    }
                }

                Text(
                    text = node.diagnosticSummary,
                    style = MaterialTheme.typography.bodySmall,
                    color = palette.textSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            // Live Mini Bezier Sparkline
            MiniNodeSparkline(
                samples = node.sparklineSamples,
                strokeColor = statusColor,
                modifier = Modifier
                    .width(98.dp)
                    .height(40.dp)
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Action Bar / Right Edge: Inspect, Share, and High-Contrast Magnetic "Copy Link • کپی لینک" Capsule
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            NodeActionChip(
                icon = Icons.Default.Terminal,
                label = stringResource(id = R.string.action_inspect_telemetry),
                accentColor = RoyalSapphire,
                onClick = onInspectTelemetry,
                testTag = "inspect_node_${node.remark}",
                modifier = Modifier.weight(0.95f)
            )
            NodeActionChip(
                icon = Icons.Default.IosShare,
                label = stringResource(id = R.string.action_export_client),
                accentColor = NeonEmerald,
                onClick = onExportToClient,
                testTag = "export_node_${node.remark}",
                modifier = Modifier.weight(0.85f)
            )
            AnimatedCopyLinkChip(
                label = if (node.scanStatus == NodeScanStatus.VERIFIED_200) {
                    "Copy Link • کپی لینک"
                } else {
                    stringResource(id = R.string.action_copy_uri)
                },
                copiedLabel = "Copied ✓",
                accentColor = palette.primaryAccent,
                onClick = onCopyCleanUri,
                testTag = "copy_node_${node.remark}",
                modifier = Modifier.weight(1.35f)
            )
        }
    }
}

@Composable
private fun VerifiedServiceMiniPill(
    label: String,
    color: Color
) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(color.copy(alpha = 0.12f))
            .border(0.75.dp, color.copy(alpha = 0.38f), RoundedCornerShape(6.dp))
            .padding(horizontal = 6.dp, vertical = 2.dp)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = color,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

/**
 * AnimatedCopyLinkChip:
 * High-contrast capsule button with magnetic tap scale animation (`scale(0.95f)` on press via
 * GPU `graphicsLayer`), expanding neon ring burst, and morphing Emerald `"Copied ✓"` state.
 */
@Composable
private fun AnimatedCopyLinkChip(
    label: String,
    accentColor: Color,
    onClick: () -> Unit,
    testTag: String,
    modifier: Modifier = Modifier,
    copiedLabel: String = "Copied ✓"
) {
    val palette = LocalNodeGuardColors.current
    val scope = rememberCoroutineScope()
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    var isCopiedConfirmed by remember { mutableStateOf(false) }
    val ringBurstProgress = remember { Animatable(0f) }

    // Magnetic tap scale animation (scale 0.95f on press, 1.0f at rest) on GPU RenderThread
    val magneticScale by animateFloatAsState(
        targetValue = if (isPressed) 0.95f else 1.0f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "magnetic_copy_scale"
    )

    val activeColor by animateColorAsState(
        targetValue = if (isCopiedConfirmed) NeonEmerald else accentColor,
        animationSpec = tween(220),
        label = "copy_chip_accent"
    )

    Box(
        modifier = modifier
            .minimumInteractiveComponentSize()
            .graphicsLayer {
                scaleX = magneticScale
                scaleY = magneticScale
            }
            .clip(RoundedCornerShape(50))
            .background(
                if (isCopiedConfirmed) {
                    NeonEmerald.copy(alpha = 0.22f)
                } else {
                    activeColor.copy(alpha = if (palette.isDark) 0.16f else 0.12f)
                }
            )
            .drawWithCache {
                if (size.width <= 0f || size.height <= 0f) {
                    return@drawWithCache onDrawBehind {}
                }
                val centerPt = Offset(size.width / 2f, size.height / 2f)
                val maxR = maxOf(min(size.width, size.height) * 0.95f, 1f)
                val ringStroke = Stroke(width = 2.2.dp.toPx())

                onDrawBehind {
                    val burst = ringBurstProgress.value
                    if (burst <= 0.01f || size.width <= 0f || size.height <= 0f) return@onDrawBehind
                    val currentRadius = maxOf(maxR * burst, 1f)
                    val alpha = (1f - burst).coerceIn(0f, 1f)
                    drawCircle(
                        color = NeonEmerald.copy(alpha = alpha * 0.9f),
                        radius = currentRadius,
                        center = centerPt,
                        style = ringStroke
                    )
                    drawCircle(
                        color = NeonCyan.copy(alpha = alpha * 0.55f),
                        radius = maxOf(currentRadius * 0.72f, 1f),
                        center = centerPt,
                        style = ringStroke
                    )
                }
            }
            .border(
                width = if (isCopiedConfirmed) 1.4.dp else 1.dp,
                color = activeColor.copy(alpha = if (isCopiedConfirmed) 0.90f else 0.62f),
                shape = RoundedCornerShape(50)
            )
            .clickable(
                interactionSource = interactionSource,
                indication = null
            ) {
                onClick()
                scope.launch {
                    isCopiedConfirmed = true
                    ringBurstProgress.snapTo(0.05f)
                    ringBurstProgress.animateTo(
                        targetValue = 1f,
                        animationSpec = tween(durationMillis = 500, easing = FastOutSlowInEasing)
                    )
                    ringBurstProgress.snapTo(0f)
                    delay(1000L)
                    isCopiedConfirmed = false
                }
            }
            .padding(horizontal = 12.dp, vertical = 9.dp)
            .testTag(testTag),
        contentAlignment = Alignment.Center
    ) {
        AnimatedContent(
            targetState = isCopiedConfirmed,
            transitionSpec = {
                (scaleIn(animationSpec = spring(dampingRatio = 0.65f)) + fadeIn(tween(140)))
                    .togetherWith(scaleOut(tween(110)) + fadeOut(tween(110)))
            },
            label = "copy_link_morph"
        ) { copied ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Icon(
                    imageVector = if (copied) Icons.Default.CheckCircle else Icons.Default.ContentCopy,
                    contentDescription = null,
                    tint = if (copied) NeonEmerald else activeColor,
                    modifier = Modifier.size(14.dp)
                )
                Spacer(modifier = Modifier.width(5.dp))
                Text(
                    text = if (copied) copiedLabel else label,
                    style = MaterialTheme.typography.labelSmall,
                    color = if (copied) NeonEmerald else palette.textPrimary,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun NodeActionChip(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    accentColor: Color,
    onClick: () -> Unit,
    testTag: String,
    modifier: Modifier = Modifier
) {
    val palette = LocalNodeGuardColors.current
    Box(
        modifier = modifier
            .minimumInteractiveComponentSize()
            .clip(RoundedCornerShape(50))
            .background(palette.logoEdgeBackground.copy(alpha = 0.80f))
            .border(0.85.dp, accentColor.copy(alpha = 0.42f), RoundedCornerShape(50))
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 9.dp)
            .testTag(testTag),
        contentAlignment = Alignment.Center
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = accentColor,
                modifier = Modifier.size(14.dp)
            )
            Spacer(modifier = Modifier.width(5.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = palette.textPrimary,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

private fun copyTextToClipboard(
    context: Context,
    label: String,
    text: String,
    toastMessage: String
) {
    SecurityHardeningManager.SafeClipboard.copyToClipboard(
        context = context,
        label = label,
        text = text,
        isSensitive = true
    )
    Toast.makeText(context, toastMessage, Toast.LENGTH_SHORT).show()
}

private fun exportNodesToExternalClient(
    context: Context,
    title: String,
    payload: String
) {
    SecurityHardeningManager.SafeClipboard.copyToClipboard(
        context = context,
        label = title,
        text = payload,
        isSensitive = true
    )

    try {
        val sendIntent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, title)
            putExtra(Intent.EXTRA_TEXT, payload)
        }
        val chooser = Intent.createChooser(sendIntent, title).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(chooser)
    } catch (_: Exception) {
        Toast.makeText(
            context,
            "$title copied to clipboard for Sing-box / v2rayNG import",
            Toast.LENGTH_SHORT
        ).show()
    }
}
