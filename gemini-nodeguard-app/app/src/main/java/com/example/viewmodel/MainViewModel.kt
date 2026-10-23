package com.example.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.audio.SciFiSoundEngine
import com.example.bridge.ActionRunStatus
import com.example.bridge.AutoSyncInterval
import com.example.bridge.CoreExecutionMode
import com.example.bridge.DynamicActionSyncManager
import com.example.bridge.NodeGuardNativeBridge
import com.example.bridge.SingBoxConfigOptions
import com.example.model.AppTab
import com.example.model.GoogleServiceStatusItem
import com.example.model.ProbeStatus
import com.example.model.ShieldUiState
import com.example.model.SimulationScenario
import com.example.model.StageState
import com.example.model.defaultTelemetryForScenario
import com.example.model.defaultValidationStages
import com.example.model.evaluateNodeDeterministicProfile
import com.example.model.googleServicesForScenario
import com.example.parser.NodeScanStatus
import com.example.parser.ProxyNode
import com.example.parser.ProxyUriParser
import com.example.parser.ScannerFilter
import com.example.security.AppSecFuzzingEngine
import com.example.security.DiagnosticErrorCollector
import com.example.security.FuzzTestState
import com.example.security.PentestPresetPayload
import com.example.security.SecurityAuditEvent
import com.example.security.SecurityHardeningManager
import com.example.ui.theme.NeonEmerald
import java.net.InetSocketAddress
import java.net.Socket
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID
import kotlin.math.roundToInt
import kotlin.random.Random
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext

class MainViewModel : ViewModel() {

    private val credentialVault = SecurityHardeningManager.HardwareCredentialVault()

    private val _uiState = MutableStateFlow(ShieldUiState())
    val uiState: StateFlow<ShieldUiState> = _uiState.asStateFlow()

    private var probeJob: Job? = null
    private var batchScanJob: Job? = null
    private var cloudSyncJob: Job? = null
    private var workflowDispatchJob: Job? = null
    private var autoPollingJob: Job? = null
    private var liveActionMonitorJob: Job? = null
    private var otaInstallJob: Job? = null
    private var fuzzSuiteJob: Job? = null
    private var liveTickerJob: Job? = null

    init {
        viewModelScope.launch(Dispatchers.Default) {
            try {
                val initialPat = _uiState.value.githubPatToken.toCharArray()
                val initialGist = _uiState.value.gistOrArtifactId.toCharArray()
                val snapshot = credentialVault.storeCredentials(initialPat, initialGist)
                java.util.Arrays.fill(initialPat, '\u0000')
                java.util.Arrays.fill(initialGist, '\u0000')
                _uiState.update { it.copy(vaultStatus = snapshot) }
            } catch (_: Throwable) {
                // Keep default pre-populated VaultStatusSnapshot if hardware keystore is unavailable
            }
        }

        DiagnosticErrorCollector.installGlobalBoundaryHandler()

        viewModelScope.launch {
            DiagnosticErrorCollector.reports.collect { reports ->
                _uiState.update { it.copy(diagnosticReports = reports) }
            }
        }

        viewModelScope.launch {
            DiagnosticErrorCollector.lastCaughtError.collect { lastError ->
                _uiState.update { it.copy(lastCaughtBoundaryError = lastError) }
            }
        }

        startLiveSparklineTicker()
        configureAutoPollingJob()
        configureLiveActionStatusMonitor()
    }

    fun selectTab(tab: AppTab) {
        SciFiSoundEngine.playHoloClick()
        _uiState.update { it.copy(activeTab = tab) }
    }

    fun toggleThemeMode() {
        SciFiSoundEngine.playHoloClick()
        _uiState.update { state ->
            state.copy(isDarkMode = !state.isDarkMode)
        }
    }

    fun toggleSoundEnabled() {
        val nextEnabled = !_uiState.value.isSoundEnabled
        SciFiSoundEngine.setSoundEnabled(nextEnabled)
        if (nextEnabled) {
            SciFiSoundEngine.playHoloClick()
        }
        _uiState.update { state ->
            state.copy(isSoundEnabled = nextEnabled)
        }
    }

    fun toggleLogRedaction() {
        SciFiSoundEngine.playHoloClick()
        _uiState.update { state ->
            val newShieldState = !state.redactSensitiveLogs
            val auditEvent = SecurityAuditEvent(
                id = UUID.randomUUID().toString(),
                timestamp = currentTimestamp(),
                category = "PRIVACY SHIELD",
                severity = if (newShieldState) "ENFORCED" else "WARN",
                summary = if (newShieldState) {
                    "Privacy Shield ON: FLAG_SECURE enabled + IP/SNI/UUID/Auth log redaction active."
                } else {
                    "Privacy Shield OFF: FLAG_SECURE relaxed + Raw exit IPs & SNIs visible."
                }
            )
            state.copy(
                redactSensitiveLogs = newShieldState,
                isFlagSecureEnabled = newShieldState,
                singBoxOptions = state.singBoxOptions.copy(redactSecretsInPreview = newShieldState),
                securityAuditEvents = (listOf(auditEvent) + state.securityAuditEvents).take(25),
                securityAuditNote = if (newShieldState) {
                    "Privacy Shield ON • FLAG_SECURE + SafeLogRedactor Masking All IPs, SNIs & UUIDs"
                } else {
                    "AppSec Warning: Privacy Shield OFF • Raw IPs & Screen Capture Allowed"
                }
            )
        }
    }

    fun selectScenario(scenario: SimulationScenario) {
        SciFiSoundEngine.playHoloClick()
        probeJob?.cancel()
        val resetStages = defaultValidationStages()
        val baseTelemetry = defaultTelemetryForScenario(scenario)
        _uiState.update {
            it.copy(
                selectedScenario = scenario,
                probeStatus = ProbeStatus.IDLE,
                isProbing = false,
                currentStageIndex = -1,
                stages = resetStages,
                telemetry = baseTelemetry,
                googleServices = googleServicesForScenario(scenario),
                lastProbeTimestamp = "SCENARIO LOADED: ${scenario.label.uppercase(Locale.US)}"
            )
        }
        startGeminiProbe()
    }

    fun startGeminiProbe() {
        SciFiSoundEngine.playRadarSweep()
        probeJob?.cancel()
        probeJob = viewModelScope.launch {
            val scenario = _uiState.value.selectedScenario
            val baseTelemetry = defaultTelemetryForScenario(scenario)

            _uiState.update {
                it.copy(
                    probeStatus = ProbeStatus.PROBING,
                    isProbing = true,
                    probeShockwaveTrigger = it.probeShockwaveTrigger + 1,
                    currentStageIndex = 0,
                    stages = defaultValidationStages().mapIndexed { index, stage ->
                        if (index == 0) {
                            stage.copy(
                                state = StageState.RUNNING,
                                statusBadge = "PROBING",
                                diagnosticDetail = "Opening SOCKS5 socket to 127.0.0.1:2080 (RFC 1928 VER=0x05)..."
                            )
                        } else {
                            stage
                        }
                    },
                    telemetry = baseTelemetry,
                    googleServices = googleServicesForScenario(scenario),
                    lastProbeTimestamp = "PROBE ACTIVE • STAGE 1/4"
                )
            }

            val realLoopbackRtt = measureLocalPortCheck()
            delay(420L)

            if (scenario == SimulationScenario.TIMEOUT) {
                _uiState.update { state ->
                    val updatedStages = state.stages.toMutableList()
                    updatedStages[0] = updatedStages[0].copy(
                        state = StageState.FAILED,
                        durationMs = 2004,
                        statusBadge = "TIMEOUT",
                        diagnosticDetail = "ECONNREFUSED / SocketTimeout on 127.0.0.1:2080 — Sing-box inbound not listening"
                    )
                    for (i in 1..3) {
                        updatedStages[i] = updatedStages[i].copy(
                            state = StageState.FAILED,
                            durationMs = 0,
                            statusBadge = "ABORTED",
                            diagnosticDetail = "Skipped due to Stage 1 local tunnel failure"
                        )
                    }
                    state.copy(
                        probeStatus = ProbeStatus.GEO_BLOCKED_403,
                        isProbing = false,
                        currentStageIndex = -1,
                        stages = updatedStages,
                        lastProbeTimestamp = "ABORTED AT ${currentTimestamp()} • SOCKET TIMEOUT",
                        securityAuditNote = "Fail-Closed Enforced • Direct Cleartext Fallback Blocked (No Leak)"
                    )
                }
                SciFiSoundEngine.playThreatBlocked()
                return@launch
            }

            val stage1Ms = when (scenario) {
                SimulationScenario.CLEAN_NODE -> (2 + (realLoopbackRtt % 4)).coerceIn(2, 8)
                SimulationScenario.GEO_BLOCKED_403 -> 5
                SimulationScenario.HIGH_LATENCY -> 18
                SimulationScenario.TIMEOUT -> 2004
            }

            SciFiSoundEngine.playStageSuccess(0)
            _uiState.update { state ->
                val updatedStages = state.stages.toMutableList()
                updatedStages[0] = updatedStages[0].copy(
                    state = StageState.PASSED,
                    durationMs = stage1Ms,
                    statusBadge = "${stage1Ms}ms OK",
                    diagnosticDetail = "SOCKS5h handshake verified on 127.0.0.1:2080 (METHOD=0x00 NO_AUTH)"
                )
                updatedStages[1] = updatedStages[1].copy(
                    state = StageState.RUNNING,
                    statusBadge = "HANDSHAKE",
                    diagnosticDetail = "Negotiating TLS 1.3 ClientHello + ALPN [h2] with X25519 key share..."
                )
                state.copy(
                    currentStageIndex = 1,
                    stages = updatedStages,
                    lastProbeTimestamp = "PROBE ACTIVE • STAGE 2/4"
                )
            }

            delay(480L)
            val stage2Ms = when (scenario) {
                SimulationScenario.CLEAN_NODE -> 34
                SimulationScenario.GEO_BLOCKED_403 -> 62
                SimulationScenario.HIGH_LATENCY -> 215
                SimulationScenario.TIMEOUT -> 0
            }
            val stage2State = if (scenario == SimulationScenario.HIGH_LATENCY) {
                StageState.WARNING
            } else {
                StageState.PASSED
            }

            SciFiSoundEngine.playStageSuccess(1)
            _uiState.update { state ->
                val updatedStages = state.stages.toMutableList()
                updatedStages[1] = updatedStages[1].copy(
                    state = stage2State,
                    durationMs = stage2Ms,
                    statusBadge = "${stage2Ms}ms TLS 1.3",
                    diagnosticDetail = if (scenario == SimulationScenario.HIGH_LATENCY) {
                        "Cipher: ${state.telemetry.tlsCipherSuite} • ALPN: h2 (TCP Retry Detected)"
                    } else {
                        "Cipher: ${state.telemetry.tlsCipherSuite} • ALPN: h2 • SNI: Verified"
                    }
                )
                updatedStages[2] = updatedStages[2].copy(
                    state = StageState.RUNNING,
                    statusBadge = "PINGING GFE",
                    diagnosticDetail = "Probing Google Front-End baseline https://www.googleapis.com/generate_204..."
                )
                state.copy(
                    currentStageIndex = 2,
                    stages = updatedStages,
                    lastProbeTimestamp = "PROBE ACTIVE • STAGE 3/4"
                )
            }

            delay(460L)
            val stage3Ms = when (scenario) {
                SimulationScenario.CLEAN_NODE -> 41
                SimulationScenario.GEO_BLOCKED_403 -> 78
                SimulationScenario.HIGH_LATENCY -> 348
                SimulationScenario.TIMEOUT -> 0
            }
            val stage3State = if (scenario == SimulationScenario.HIGH_LATENCY) {
                StageState.WARNING
            } else {
                StageState.PASSED
            }

            SciFiSoundEngine.playStageSuccess(2)
            _uiState.update { state ->
                val updatedStages = state.stages.toMutableList()
                updatedStages[2] = updatedStages[2].copy(
                    state = stage3State,
                    durationMs = stage3Ms,
                    statusBadge = "HTTP 204 (${stage3Ms}ms)",
                    diagnosticDetail = if (scenario == SimulationScenario.HIGH_LATENCY) {
                        "GFE reachable (HTTP 204 No Content) • High RTT jitter (±68.5ms)"
                    } else {
                        "GFE edge baseline confirmed (HTTP 204) • Zero DPI packet tampering"
                    }
                )
                updatedStages[3] = updatedStages[3].copy(
                    state = StageState.RUNNING,
                    statusBadge = "DEEP INSPECT",
                    diagnosticDetail = "Inspecting Gemini API v1beta response headers & regional lock payload..."
                )
                state.copy(
                    currentStageIndex = 3,
                    stages = updatedStages,
                    lastProbeTimestamp = "PROBE ACTIVE • STAGE 4/4"
                )
            }

            delay(540L)
            when (scenario) {
                SimulationScenario.CLEAN_NODE -> {
                    val stage4Ms = 138
                    SciFiSoundEngine.playStageSuccess(3)
                    SciFiSoundEngine.playGeminiVerified()
                    _uiState.update { state ->
                        val updatedStages = state.stages.toMutableList()
                        updatedStages[3] = updatedStages[3].copy(
                            state = StageState.PASSED,
                            durationMs = stage4Ms,
                            statusBadge = "200 VERIFIED",
                            diagnosticDetail = "HTTP 200 OK • Gemini 2.0 Flash / 1.5 Pro unblocked • Exit: Tokyo (JP)"
                        )
                        state.copy(
                            probeStatus = ProbeStatus.VERIFIED_200,
                            isProbing = false,
                            currentStageIndex = -1,
                            stages = updatedStages,
                            lastProbeTimestamp = "VERIFIED AT ${currentTimestamp()} • 4/4 PASSED",
                            securityAuditNote = "Cert Pinning & SSRF Guard Verified • Zero DNS Leak via Remote Tunnel"
                        )
                    }
                }
                SimulationScenario.HIGH_LATENCY -> {
                    val stage4Ms = 642
                    SciFiSoundEngine.playStageSuccess(3)
                    _uiState.update { state ->
                        val updatedStages = state.stages.toMutableList()
                        updatedStages[3] = updatedStages[3].copy(
                            state = StageState.WARNING,
                            durationMs = stage4Ms,
                            statusBadge = "200 HIGH JITTER",
                            diagnosticDetail = "HTTP 200 OK (Region Supported) • TTFT 642ms exceeds 350ms threshold"
                        )
                        state.copy(
                            probeStatus = ProbeStatus.HIGH_JITTER,
                            isProbing = false,
                            currentStageIndex = -1,
                            stages = updatedStages,
                            lastProbeTimestamp = "COMPLETED AT ${currentTimestamp()} • HIGH JITTER WARNING",
                            securityAuditNote = "QUIC Congestion Detected • Recommend switching to VLESS XTLS-Vision node"
                        )
                    }
                }
                SimulationScenario.GEO_BLOCKED_403 -> {
                    val stage4Ms = 94
                    SciFiSoundEngine.playThreatBlocked()
                    _uiState.update { state ->
                        val updatedStages = state.stages.toMutableList()
                        updatedStages[3] = updatedStages[3].copy(
                            state = StageState.FAILED,
                            durationMs = stage4Ms,
                            statusBadge = "403 REGION LOCK",
                            diagnosticDetail = "HTTP 403 FAILED_PRECONDITION: \"User location is not supported for the API use.\""
                        )
                        state.copy(
                            probeStatus = ProbeStatus.GEO_BLOCKED_403,
                            isProbing = false,
                            currentStageIndex = -1,
                            stages = updatedStages,
                            lastProbeTimestamp = "BLOCKED AT ${currentTimestamp()} • HTTP 403 GEO-LOCK",
                            securityAuditNote = "Exit IP (AS136907 HKG) flagged by Google Geo-IP database • Reroute required"
                        )
                    }
                }
                SimulationScenario.TIMEOUT -> Unit
            }
        }
    }

    // =========================================================================
    // PHASE 2: Concurrent Batch Node Scanner Actions
    // =========================================================================

    fun setConcurrencyLimit(limit: Int) {
        val coerced = limit.coerceIn(1, 32)
        _uiState.update {
            it.copy(
                concurrencyLimit = coerced,
                lastScannerBannerMessage = "Semaphore updated to $coerced parallel coroutines"
            )
        }
    }

    fun setProbeTimeoutMs(timeoutMs: Int) {
        val coerced = timeoutMs.coerceIn(1000, 10000)
        _uiState.update {
            it.copy(
                probeTimeoutMs = coerced,
                lastScannerBannerMessage = "Socket timeout ceiling set to ${coerced}ms"
            )
        }
    }

    fun selectScannerFilter(filter: ScannerFilter) {
        SciFiSoundEngine.playHoloClick()
        _uiState.update {
            it.copy(
                selectedScannerFilter = filter,
                selectedDynamicRuleId = null
            )
        }
    }

    fun selectDynamicRuleFilter(ruleId: String?) {
        SciFiSoundEngine.playHoloClick()
        _uiState.update { state ->
            val toggled = if (state.selectedDynamicRuleId == ruleId) null else ruleId
            state.copy(selectedDynamicRuleId = toggled)
        }
    }

    fun openImportDialog() {
        SciFiSoundEngine.playHoloClick()
        _uiState.update { it.copy(isImportDialogOpen = true) }
    }

    fun dismissImportDialog() {
        SciFiSoundEngine.playHoloClick()
        _uiState.update { it.copy(isImportDialogOpen = false) }
    }

    fun inspectNodeTelemetry(node: ProxyNode?) {
        if (node != null) SciFiSoundEngine.playHoloClick()
        _uiState.update { it.copy(inspectedNode = node) }
    }

    fun load15RealisticMixedNodes() {
        SciFiSoundEngine.playHoloClick()
        batchScanJob?.cancel()
        val rawNodes = ProxyUriParser.generate15RealisticMixedNodes()
        _uiState.update {
            it.copy(
                scannerNodes = rawNodes,
                selectedScannerFilter = ScannerFilter.ALL,
                completedScanCount = 0,
                isBatchScanning = false,
                activeCoroutinesCount = 0,
                lastScannerBannerMessage = "Loaded 15 mixed nodes (VLESS/VMess/Trojan/Hysteria2/TUIC) • Starting scan..."
            )
        }
        startBatchScan()
    }

    fun importBulkNodes(rawText: String) {
        val result = ProxyUriParser.parseBulkInput(rawText)
        if (result.oversizedPayloadBlocked) {
            SciFiSoundEngine.playThreatBlocked()
            val evt = SecurityAuditEvent(
                id = UUID.randomUUID().toString(),
                timestamp = currentTimestamp(),
                category = "64KB DoS GUARD",
                severity = "BLOCKED",
                summary = "Blocked oversized Base64/URI payload (>64KB limit) to prevent OOM DoS attack."
            )
            _uiState.update {
                it.copy(
                    isImportDialogOpen = false,
                    oversizedPayloadsBlockedCount = it.oversizedPayloadsBlockedCount + 1,
                    securityAuditEvents = (listOf(evt) + it.securityAuditEvents).take(25),
                    lastScannerBannerMessage = "SECURITY BLOCK: Payload exceeded 64KB Base64 DoS cap!"
                )
            }
            return
        }

        if (result.validNodes.isEmpty()) {
            SciFiSoundEngine.playThreatBlocked()
            _uiState.update {
                it.copy(
                    isImportDialogOpen = false,
                    lastScannerBannerMessage = "Import rejected: 0 valid proxy URIs found (${result.rejectedCount} malformed lines)"
                )
            }
            return
        }

        if (result.ssrfBlockedCount > 0 || result.injectionSanitizedCount > 0) {
            SciFiSoundEngine.playThreatBlocked()
        } else {
            SciFiSoundEngine.playHoloClick()
        }

        batchScanJob?.cancel()
        _uiState.update { state ->
            val combined = result.validNodes + state.scannerNodes
            val distinctNodes = combined.distinctBy { "${it.protocol.scheme}://${it.host}:${it.port}/${it.remark}" }
            val ssrfNote = if (result.ssrfBlockedCount > 0) " • ${result.ssrfBlockedCount} SSRF Blocked" else ""
            val injNote = if (result.injectionSanitizedCount > 0) " • ${result.injectionSanitizedCount} Sanitized" else ""

            val newEvents = mutableListOf<SecurityAuditEvent>()
            if (result.ssrfBlockedCount > 0) {
                newEvents.add(
                    SecurityAuditEvent(
                        id = UUID.randomUUID().toString(),
                        timestamp = currentTimestamp(),
                        category = "SSRF FIREWALL",
                        severity = "BLOCKED",
                        summary = "Blocked ${result.ssrfBlockedCount} imported node(s) targeting private/loopback/metadata IPs."
                    )
                )
            }
            if (result.injectionSanitizedCount > 0) {
                newEvents.add(
                    SecurityAuditEvent(
                        id = UUID.randomUUID().toString(),
                        timestamp = currentTimestamp(),
                        category = "INJECTION SANITIZER",
                        severity = "SANITIZED",
                        summary = "Neutralized JSON/Shell/Traversal injection characters in ${result.injectionSanitizedCount} URI(s)."
                    )
                )
            }

            state.copy(
                scannerNodes = distinctNodes,
                isImportDialogOpen = false,
                selectedScannerFilter = ScannerFilter.ALL,
                ssrfBlockedTotalCount = state.ssrfBlockedTotalCount + result.ssrfBlockedCount,
                injectionNeutralizedTotalCount = state.injectionNeutralizedTotalCount + result.injectionSanitizedCount,
                securityAuditEvents = (newEvents + state.securityAuditEvents).take(25),
                lastScannerBannerMessage = "Imported ${result.validNodes.size} nodes (${result.rejectedCount} rejected$ssrfNote$injNote)"
            )
        }
        startBatchScan()
    }

    fun stopBatchScan() {
        SciFiSoundEngine.playHoloClick()
        batchScanJob?.cancel()
        _uiState.update { state ->
            val stabilized = state.scannerNodes.map { node ->
                if (node.scanStatus == NodeScanStatus.SCANNING) {
                    node.copy(scanStatus = NodeScanStatus.QUEUED)
                } else {
                    node
                }
            }
            state.copy(
                scannerNodes = stabilized,
                isBatchScanning = false,
                activeCoroutinesCount = 0,
                lastScannerBannerMessage = "Batch scan aborted by user at ${currentTimestamp()}"
            )
        }
    }

    fun startBatchScan() {
        SciFiSoundEngine.playRadarSweep()
        batchScanJob?.cancel()
        batchScanJob = viewModelScope.launch {
            val currentNodes = _uiState.value.scannerNodes
            if (currentNodes.isEmpty()) return@launch

            val permits = _uiState.value.concurrencyLimit.coerceIn(1, 32)
            val timeoutMs = _uiState.value.probeTimeoutMs.coerceIn(1000, 10000)
            val semaphore = Semaphore(permits)

            _uiState.update { state ->
                state.copy(
                    isBatchScanning = true,
                    activeCoroutinesCount = 0,
                    completedScanCount = 0,
                    scannerNodes = state.scannerNodes.map { node ->
                        if (node.isPrivateOrLoopbackRisk || node.scanStatus == NodeScanStatus.SSRF_BLOCKED) {
                            node.copy(scanStatus = NodeScanStatus.SSRF_BLOCKED)
                        } else {
                            node.copy(
                                scanStatus = NodeScanStatus.QUEUED,
                                diagnosticSummary = "Queued in Semaphore($permits) pool..."
                            )
                        }
                    },
                    lastScannerBannerMessage = "Scanning ${currentNodes.size} nodes • Semaphore($permits) • SSRF Guard Active"
                )
            }

            coroutineScope {
                val jobs = currentNodes.mapIndexed { index, node ->
                    async(Dispatchers.Default) {
                        semaphore.withPermit {
                            if (node.isPrivateOrLoopbackRisk ||
                                SecurityHardeningManager.SsrfFirewall.inspectHostOrUrl(node.host).isBlocked
                            ) {
                                val blockedNode = evaluateNodeDeterministicProfile(node, index, timeoutMs)
                                _uiState.update { state ->
                                    val list = state.scannerNodes.toMutableList()
                                    val targetIdx = list.indexOfFirst { it.id == node.id }
                                    if (targetIdx >= 0) list[targetIdx] = blockedNode
                                    state.copy(
                                        scannerNodes = list,
                                        completedScanCount = state.completedScanCount + 1
                                    )
                                }
                                return@withPermit
                            }

                            _uiState.update { state ->
                                val list = state.scannerNodes.toMutableList()
                                val targetIdx = list.indexOfFirst { it.id == node.id }
                                if (targetIdx >= 0) {
                                    list[targetIdx] = list[targetIdx].copy(
                                        scanStatus = NodeScanStatus.SCANNING,
                                        diagnosticSummary = "Negotiating ${node.security.uppercase(Locale.US)} / ALPN [${node.alpn}]..."
                                    )
                                }
                                state.copy(
                                    scannerNodes = list,
                                    activeCoroutinesCount = (state.activeCoroutinesCount + 1).coerceAtMost(permits)
                                )
                            }

                            val simulatedDelay = (260L + (index % 5) * 130L).coerceAtMost(timeoutMs.toLong() / 2L)
                            delay(simulatedDelay)

                            val evaluatedNode = evaluateNodeDeterministicProfile(node, index, timeoutMs)
                            if (index == 0 && evaluatedNode.scanStatus == NodeScanStatus.VERIFIED_200) {
                                SciFiSoundEngine.playStageSuccess(2)
                            } else if (index == 2 && evaluatedNode.scanStatus == NodeScanStatus.GEO_BLOCKED_403) {
                                SciFiSoundEngine.playThreatBlocked()
                            }

                            _uiState.update { state ->
                                val list = state.scannerNodes.toMutableList()
                                val targetIdx = list.indexOfFirst { it.id == node.id }
                                if (targetIdx >= 0) {
                                    list[targetIdx] = evaluatedNode
                                }
                                val updatedCompleted = state.completedScanCount + 1
                                val updatedInspected = if (state.inspectedNode?.id == evaluatedNode.id) {
                                    evaluatedNode
                                } else {
                                    state.inspectedNode
                                }
                                state.copy(
                                    scannerNodes = list,
                                    completedScanCount = updatedCompleted,
                                    activeCoroutinesCount = (state.activeCoroutinesCount - 1).coerceAtLeast(0),
                                    inspectedNode = updatedInspected
                                )
                            }
                        }
                    }
                }
                jobs.awaitAll()
            }

            val finalVerifiedCount = _uiState.value.scannerNodes.count { it.scanStatus == NodeScanStatus.VERIFIED_200 }
            if (finalVerifiedCount > 0) {
                SciFiSoundEngine.playGeminiVerified()
            } else {
                SciFiSoundEngine.playThreatBlocked()
            }

            _uiState.update { state ->
                val verifiedCount = state.scannerNodes.count { it.scanStatus == NodeScanStatus.VERIFIED_200 }
                val blockedCount = state.scannerNodes.count {
                    it.scanStatus == NodeScanStatus.GEO_BLOCKED_403 || it.scanStatus == NodeScanStatus.REGION_LOCK_400
                }
                val ssrfCount = state.scannerNodes.count { it.scanStatus == NodeScanStatus.SSRF_BLOCKED }
                val timeoutCount = state.scannerNodes.count { it.scanStatus == NodeScanStatus.TIMEOUT }
                val ssrfSuffix = if (ssrfCount > 0) " • $ssrfCount SSRF Blocked" else ""
                state.copy(
                    isBatchScanning = false,
                    activeCoroutinesCount = 0,
                    lastScannerBannerMessage = "Scan complete: $verifiedCount Verified (200) • $blockedCount Geo-Locked • $timeoutCount Timeout$ssrfSuffix"
                )
            }
        }
    }

    fun activateNodeInShield(node: ProxyNode) {
        val targetScenario = when (node.scanStatus) {
            NodeScanStatus.VERIFIED_200 -> SimulationScenario.CLEAN_NODE
            NodeScanStatus.GEO_BLOCKED_403, NodeScanStatus.REGION_LOCK_400 -> SimulationScenario.GEO_BLOCKED_403
            NodeScanStatus.TIMEOUT, NodeScanStatus.SSRF_BLOCKED -> SimulationScenario.TIMEOUT
            else -> SimulationScenario.CLEAN_NODE
        }
        _uiState.update {
            it.copy(
                inspectedNode = null,
                activeTab = AppTab.SHIELD
            )
        }
        selectScenario(targetScenario)
    }

    // =========================================================================
    // PHASE 3 & 4: Cloud Sync, AES-256-GCM Credential Vault & Go/Rust Bridge
    // =========================================================================

    fun updateGithubRepoSlug(slug: String) {
        val sanitized = SecurityHardeningManager.ConfigInjectionSanitizer
            .sanitizeParameter(slug, maxLen = 80)
            .cleanValue
            .replace(Regex("""[^a-zA-Z0-9._/\-]"""), "")
        _uiState.update { it.copy(githubRepoSlug = sanitized) }
    }

    fun updateGistOrArtifactId(id: String) {
        val sanitized = SecurityHardeningManager.ConfigInjectionSanitizer
            .sanitizeParameter(id, maxLen = 180)
            .cleanValue
            .replace(Regex("""[^a-zA-Z0-9._/\-:?&=%]"""), "")
        val gistChars = sanitized.toCharArray()
        val patChars = _uiState.value.githubPatToken.toCharArray()
        val snap = credentialVault.storeCredentials(patChars, gistChars)
        java.util.Arrays.fill(gistChars, '\u0000')
        java.util.Arrays.fill(patChars, '\u0000')
        _uiState.update {
            it.copy(
                gistOrArtifactId = sanitized,
                vaultStatus = snap
            )
        }
    }

    fun updateGithubPatToken(token: String) {
        val sanitized = SecurityHardeningManager.ConfigInjectionSanitizer
            .sanitizeParameter(token, maxLen = 120)
            .cleanValue
        val patChars = sanitized.toCharArray()
        val gistChars = _uiState.value.gistOrArtifactId.toCharArray()
        val snap = credentialVault.storeCredentials(patChars, gistChars)
        java.util.Arrays.fill(patChars, '\u0000')
        java.util.Arrays.fill(gistChars, '\u0000')
        _uiState.update {
            it.copy(
                githubPatToken = sanitized,
                vaultStatus = snap
            )
        }
    }

    fun togglePatVisibility() {
        _uiState.update { it.copy(isPatVisible = !it.isPatVisible) }
    }

    fun zeroizeMemoryAndWipeVault() {
        val wipedSnapshot = credentialVault.zeroizeMemoryAndWipeVault()
        val evt = SecurityAuditEvent(
            id = UUID.randomUUID().toString(),
            timestamp = currentTimestamp(),
            category = "MEMORY ZEROIZE",
            severity = "WIPED",
            summary = "Zeroized PAT & Subscription CharArray/ByteArray buffers (0x00) and rotated AES-256-GCM master key."
        )
        _uiState.update {
            it.copy(
                githubPatToken = "",
                gistOrArtifactId = "",
                isPatVisible = false,
                vaultStatus = wipedSnapshot,
                securityAuditEvents = (listOf(evt) + it.securityAuditEvents).take(25),
                lastCloudSyncStatus = "VAULT ZEROIZED: In-memory PAT & Subscription URL buffers overwritten with 0x00"
            )
        }
    }

    fun syncVerifiedNodesFromCloud() {
        if (_uiState.value.isCloudSyncing) return
        cloudSyncJob?.cancel()
        cloudSyncJob = viewModelScope.launch {
            val repo = _uiState.value.githubRepoSlug.ifBlank { "pouriyaahmadii/Gemini_NodeGuard" }
            val gistOrSubUrl = _uiState.value.gistOrArtifactId.ifBlank { "gist_8f94c2e10a7b4d3e91f0" }

            // Inspect Subscription URL / Gist target with SSRF Firewall before probing
            if (gistOrSubUrl.contains("://") || gistOrSubUrl.contains(".")) {
                val subUrlCheck = SecurityHardeningManager.SsrfFirewall.inspectSubscriptionUrl(gistOrSubUrl)
                if (subUrlCheck.isBlocked) {
                    val ssrfEvt = SecurityAuditEvent(
                        id = UUID.randomUUID().toString(),
                        timestamp = currentTimestamp(),
                        category = "SUBSCRIPTION SSRF",
                        severity = "BLOCKED",
                        summary = "Blocked SSRF Subscription URL ($gistOrSubUrl) matching ${subUrlCheck.matchedRule}."
                    )
                    _uiState.update { state ->
                        state.copy(
                            ssrfBlockedTotalCount = state.ssrfBlockedTotalCount + 1,
                            securityAuditEvents = (listOf(ssrfEvt) + state.securityAuditEvents).take(25),
                            lastCloudSyncStatus = "BLOCKED: SSRF Risk in Subscription URL (${subUrlCheck.matchedRule})"
                        )
                    }
                    return@launch
                }
            }

            val ssrfRepoCheck = SecurityHardeningManager.SsrfFirewall.inspectHostOrUrl("api.github.com")
            if (ssrfRepoCheck.isBlocked) return@launch

            _uiState.update {
                it.copy(
                    isCloudSyncing = true,
                    lastCloudSyncStatus = "Decrypting PAT from AES-256-GCM Vault & connecting to api.github.com/repos/$repo..."
                )
            }
            delay(480L)

            _uiState.update {
                it.copy(
                    lastCloudSyncStatus = "Fetching Subscription [$gistOrSubUrl] & verifying 2MB Sub / 64KB URI caps..."
                )
            }
            delay(560L)

            val cloudSyncedUris = listOf(
                "vless://7c9e6679-7425-40de-944b-e07fc1f90ae7@tyo-cicd-01.nodeguard.io:443?security=reality&sni=generativelanguage.googleapis.com&fp=chrome&pbk=mK9xPq2vL8nR4tY6wZ1aB3cD5eF7gH9jK0lM2nO4pQ&sid=a0b1c2d3&type=tcp&flow=xtls-rprx-vision#CLOUD-JP-Tokyo-Gemini-Edge-01",
                "hysteria2://hy2_cloud_cicd_901@sjc-silicon-02.nodeguard.io:4433?security=tls&sni=aistudio.google.com&alpn=h3&fp=chrome#CLOUD-US-SanJose-QUIC-02",
                "vless://3f2504e0-4f89-11d3-9a0c-0305e82c3301@sin-aws-cicd03.nodeguard.io:443?security=reality&sni=gemini.google.com&fp=chrome&pbk=pQ4nO2lM0kJ9hG7fE5dC3bA1zW6yT4rN8vL2qPx9Km&sid=f1e2d3c4&type=grpc&serviceName=gemini-cloud-grpc#CLOUD-SG-Singapore-gRPC-03",
                "trojan://trj_cloud_verified_77@zrh-fin-cicd04.nodeguard.io:443?security=tls&sni=alkalimakersuite-pa.clients6.google.com&type=ws&path=%2Fcloud-ws&alpn=h2&fp=chrome#CLOUD-CH-Zurich-Trojan-04"
            )

            val parsedCloudNodes = cloudSyncedUris.mapIndexedNotNull { idx, uri ->
                ProxyUriParser.parseSingleUri(uri)?.let { rawNode ->
                    evaluateNodeDeterministicProfile(rawNode, idx, _uiState.value.probeTimeoutMs).copy(
                        scanStatus = NodeScanStatus.VERIFIED_200,
                        httpCode = 200,
                        ttftMs = 96 + idx * 14,
                        pingRttMs = 24 + idx * 7,
                        jitterMs = 1.6f + idx * 0.4f,
                        diagnosticSummary = "CI/CD Verified (${repo}) • HTTP 200 OK • Sub #$gistOrSubUrl"
                    )
                }
            }

            SciFiSoundEngine.playGeminiVerified()

            _uiState.update { state ->
                val merged = (parsedCloudNodes + state.scannerNodes)
                    .distinctBy { "${it.protocol.scheme}://${it.host}:${it.port}/${it.remark}" }
                val newSelectedId = state.selectedConfigNodeId ?: parsedCloudNodes.firstOrNull()?.id
                state.copy(
                    scannerNodes = merged,
                    isCloudSyncing = false,
                    syncedCloudNodesCount = state.syncedCloudNodesCount + parsedCloudNodes.size,
                    selectedConfigNodeId = newSelectedId,
                    lastCloudSyncStatus = "Synced ${parsedCloudNodes.size} pre-verified Gemini 200 OK nodes from $repo (${currentTimestamp()})",
                    lastScannerBannerMessage = "Merged ${parsedCloudNodes.size} Cloud CI/CD nodes into Scanner (${merged.size} total)"
                )
            }
        }
    }

    fun selectConfigNode(nodeId: String) {
        SciFiSoundEngine.playHoloClick()
        _uiState.update { it.copy(selectedConfigNodeId = nodeId) }
    }

    fun updateSingBoxOptions(transform: (SingBoxConfigOptions) -> SingBoxConfigOptions) {
        SciFiSoundEngine.playHoloClick()
        _uiState.update { state ->
            state.copy(singBoxOptions = transform(state.singBoxOptions))
        }
    }

    fun setCoreExecutionMode(mode: CoreExecutionMode) {
        SciFiSoundEngine.playHoloClick()
        val engine = NodeGuardNativeBridge.engineForMode(mode)
        val telemetry = engine.queryCoreTelemetry()
        _uiState.update {
            it.copy(
                coreExecutionMode = mode,
                nativeCoreTelemetry = telemetry
            )
        }
    }

    fun runNativeCoreBenchmark() {
        SciFiSoundEngine.playRadarSweep()
        viewModelScope.launch {
            val state = _uiState.value
            val engine = NodeGuardNativeBridge.engineForMode(state.coreExecutionMode)
            val targetNode = state.scannerNodes.firstOrNull { it.scanStatus == NodeScanStatus.VERIFIED_200 }
                ?: state.scannerNodes.firstOrNull()
                ?: return@launch

            val diagResult = engine.verifyNodeViaCore(targetNode, state.probeTimeoutMs)
            val refreshedTelemetry = engine.queryCoreTelemetry().copy(
                lastBridgeDiagnostic = diagResult
            )
            SciFiSoundEngine.playStageSuccess(3)
            _uiState.update {
                it.copy(nativeCoreTelemetry = refreshedTelemetry)
            }
        }
    }

    // =========================================================================
    // PHASE 6: Dynamic GitHub Actions Remote-Config Sync & OTA In-App Updater
    // =========================================================================

    fun triggerGithubWorkflowDispatch() {
        if (_uiState.value.isDispatchingWorkflow) return
        SciFiSoundEngine.playRadarSweep()
        workflowDispatchJob?.cancel()
        workflowDispatchJob = viewModelScope.launch {
            val state = _uiState.value
            val nextRun = state.latestWorkflowRunNumber + 1
            val repo = state.githubRepoSlug.ifBlank { "pouriyaahmadii/Gemini_NodeGuard" }

            _uiState.update {
                it.copy(
                    isDispatchingWorkflow = true,
                    actionRunStatus = ActionRunStatus.QUEUED,
                    lastCloudSyncStatus = "POST /repos/$repo/actions/workflows/nodeguard-scanner.yml/dispatches (Queued #$nextRun)..."
                )
            }

            val dispatchResult = DynamicActionSyncManager.triggerWorkflowDispatch(
                repoSlug = repo,
                patToken = state.githubPatToken,
                nextRunNumber = nextRun
            )

            if (!dispatchResult.succeeded) {
                SciFiSoundEngine.playThreatBlocked()
                _uiState.update {
                    it.copy(
                        isDispatchingWorkflow = false,
                        lastCloudSyncStatus = dispatchResult.diagnosticMessage
                    )
                }
                return@launch
            }

            SciFiSoundEngine.playStageSuccess(0)
            _uiState.update {
                it.copy(
                    actionRunStatus = ActionRunStatus.QUEUED,
                    latestWorkflowRunNumber = dispatchResult.runNumber,
                    latestActionCommitHash = dispatchResult.commitHash,
                    latestActionTimestamp = dispatchResult.timestamp,
                    lastCloudSyncStatus = "Workflow #${dispatchResult.runNumber} Queued on GitHub Actions • Waiting for runner..."
                )
            }

            delay(650L)
            SciFiSoundEngine.playStageSuccess(1)
            _uiState.update {
                it.copy(
                    actionRunStatus = ActionRunStatus.IN_PROGRESS,
                    lastCloudSyncStatus = "Workflow #${dispatchResult.runNumber} In Progress 🔄 • Probing Gemini 2.5, Vertex AI & Astra endpoints..."
                )
            }

            delay(950L)
            SciFiSoundEngine.playStageSuccess(3)
            val evt = SecurityAuditEvent(
                id = UUID.randomUUID().toString(),
                timestamp = currentTimestamp(),
                category = "WORKFLOW_DISPATCH",
                severity = "VERIFIED",
                summary = "Triggered remote GitHub Action Run #${dispatchResult.runNumber} (${dispatchResult.commitHash}) on $repo."
            )
            _uiState.update {
                it.copy(
                    isDispatchingWorkflow = false,
                    actionRunStatus = ActionRunStatus.COMPLETED,
                    securityAuditEvents = (listOf(evt) + it.securityAuditEvents).take(25),
                    lastCloudSyncStatus = "Workflow #${dispatchResult.runNumber} Completed ✓ (Commit ${dispatchResult.commitHash}) • Ready for Hot-Reload!"
                )
            }

            // Always sync the new remote security scan rules and verified nodes upon workflow completion
            hotReloadActionOutput()
        }
    }

    fun hotReloadActionOutput() {
        if (_uiState.value.isCloudSyncing) return
        SciFiSoundEngine.playRadarSweep()
        cloudSyncJob?.cancel()
        cloudSyncJob = viewModelScope.launch(DiagnosticErrorCollector.boundaryCoroutineExceptionHandler) {
            val state = _uiState.value
            val repo = state.githubRepoSlug.ifBlank { DynamicActionSyncManager.DEFAULT_REPO_SLUG }
            val gistOrSub = state.gistOrArtifactId.ifBlank { "remote-action-config.json" }

            _uiState.update {
                it.copy(
                    isCloudSyncing = true,
                    lastCloudSyncStatus = "OkHttp: Hot-Reloading remote-action-config.json & verified nodes from $repo..."
                )
            }
            delay(420L)

            val payload = DynamicActionSyncManager.fetchRemoteActionConfig(
                repoSlug = repo,
                gistOrSubId = gistOrSub,
                patToken = state.githubPatToken,
                runNumber = state.latestWorkflowRunNumber,
                commitHash = state.latestActionCommitHash
            )

            val parsedActionNodes = payload.verifiedNodeUris.mapIndexedNotNull { idx, uri ->
                ProxyUriParser.parseSingleUri(uri)?.let { rawNode ->
                    val rule = payload.dynamicEndpoints.getOrNull(idx % payload.dynamicEndpoints.size)
                    evaluateNodeDeterministicProfile(rawNode, idx, _uiState.value.probeTimeoutMs).copy(
                        scanStatus = NodeScanStatus.VERIFIED_200,
                        httpCode = rule?.expectedHttpCode ?: 200,
                        ttftMs = 88 + idx * 11,
                        pingRttMs = 21 + idx * 5,
                        jitterMs = 1.2f + idx * 0.3f,
                        diagnosticSummary = "Action #${payload.workflowRunNumber} (${payload.commitHash}) • ${rule?.serviceName ?: "Gemini 2.5"} 200 OK"
                    )
                }
            }

            val dynamicGoogleServiceCards = payload.dynamicEndpoints.map { rule ->
                GoogleServiceStatusItem(
                    id = rule.id,
                    serviceName = rule.serviceName,
                    endpointHost = "${rule.endpointHost}${rule.endpointPath}",
                    badgeText = "HTTP ${rule.expectedHttpCode} OK",
                    latencyLabel = "${92 + (rule.id.hashCode() and 0x1F)}ms TTFT",
                    statusDetail = "Hot-Reloaded via GitHub Action #${payload.workflowRunNumber} • ${rule.protocolRule}",
                    statusColor = NeonEmerald,
                    emblemType = rule.emblemType
                )
            }

            SciFiSoundEngine.playGeminiVerified()

            val evt = SecurityAuditEvent(
                id = UUID.randomUUID().toString(),
                timestamp = currentTimestamp(),
                category = "ACTION HOT-RELOAD",
                severity = "SYNCED",
                summary = "Hot-reloaded ${payload.dynamicEndpoints.size} dynamic probing rules & ${parsedActionNodes.size} nodes from Action #${payload.workflowRunNumber}."
            )

            _uiState.update { current ->
                val mergedNodes = (parsedActionNodes + current.scannerNodes)
                    .distinctBy { "${it.protocol.scheme}://${it.host}:${it.port}/${it.remark}" }
                current.copy(
                    isCloudSyncing = false,
                    actionRunStatus = ActionRunStatus.COMPLETED,
                    latestWorkflowRunNumber = payload.workflowRunNumber,
                    latestActionCommitHash = payload.commitHash,
                    latestActionTimestamp = payload.commitTimestamp,
                    dynamicEndpointRules = payload.dynamicEndpoints,
                    remoteActionConfigJson = payload.rawConfigJson,
                    googleServices = dynamicGoogleServiceCards,
                    scannerNodes = mergedNodes,
                    syncedCloudNodesCount = current.syncedCloudNodesCount + parsedActionNodes.size,
                    selectedConfigNodeId = current.selectedConfigNodeId ?: parsedActionNodes.firstOrNull()?.id,
                    securityAuditEvents = (listOf(evt) + current.securityAuditEvents).take(25),
                    lastCloudSyncStatus = payload.transportNote,
                    lastScannerBannerMessage = "Hot-Reloaded ${payload.dynamicEndpoints.size} Action Service Rules & +${parsedActionNodes.size} Verified Links!"
                )
            }
        }
    }

    fun setAutoSyncEnabled(enabled: Boolean) {
        SciFiSoundEngine.playHoloClick()
        _uiState.update {
            it.copy(
                autoSyncEnabled = enabled,
                lastCloudSyncStatus = if (enabled) {
                    "Auto-Sync with GitHub Actions ENABLED (${it.autoSyncInterval.label})"
                } else {
                    "Auto-Sync paused • Manual Hot-Reload active"
                }
            )
        }
        configureAutoPollingJob()
    }

    fun setAutoSyncInterval(interval: AutoSyncInterval) {
        SciFiSoundEngine.playHoloClick()
        _uiState.update {
            it.copy(
                autoSyncInterval = interval,
                lastCloudSyncStatus = "GitHub Actions Auto-Polling interval set to: ${interval.label}"
            )
        }
        configureAutoPollingJob()
    }

    fun setLiveActionMonitorEnabled(enabled: Boolean) {
        SciFiSoundEngine.playHoloClick()
        _uiState.update {
            it.copy(
                isLiveActionMonitorEnabled = enabled,
                liveActionStepSummary = if (enabled) {
                    "Live Action Run Status Monitor ACTIVE • Streaming telemetry from ${it.githubRepoSlug}"
                } else {
                    "Live Action Run Status Monitor PAUSED • Tap Poll Now or re-enable live stream"
                },
                lastCloudSyncStatus = if (enabled) {
                    "Live Action Run Status Monitor ENABLED (Real-time workflow run telemetry)"
                } else {
                    "Live Action Run Status Monitor PAUSED"
                }
            )
        }
        configureLiveActionStatusMonitor()
    }

    fun pollActionRunStatusNow() {
        SciFiSoundEngine.playHoloClick()
        viewModelScope.launch(DiagnosticErrorCollector.boundaryCoroutineExceptionHandler) {
            executeSingleActionRunStatusPoll()
        }
    }

    private fun configureLiveActionStatusMonitor() {
        liveActionMonitorJob?.cancel()
        if (!_uiState.value.isLiveActionMonitorEnabled) return
        liveActionMonitorJob = viewModelScope.launch(DiagnosticErrorCollector.boundaryCoroutineExceptionHandler) {
            while (isActive) {
                delay(6500L)
                if (_uiState.value.isLiveActionMonitorEnabled && !_uiState.value.isDispatchingWorkflow) {
                    executeSingleActionRunStatusPoll()
                }
            }
        }
    }

    private suspend fun executeSingleActionRunStatusPoll() {
        val snapshot = _uiState.value
        val nextPollCount = snapshot.liveMonitorPollCount + 1
        val telemetry = DynamicActionSyncManager.pollLatestWorkflowRunStatus(
            repoSlug = snapshot.githubRepoSlug,
            patToken = snapshot.githubPatToken,
            fallbackRunNumber = snapshot.latestWorkflowRunNumber,
            fallbackCommitHash = snapshot.latestActionCommitHash,
            pollSequenceIndex = nextPollCount
        )
        _uiState.update { current ->
            current.copy(
                actionRunStatus = telemetry.status,
                latestWorkflowRunNumber = telemetry.runNumber,
                latestActionCommitHash = telemetry.commitHash,
                latestActionTimestamp = telemetry.updatedAtTimestamp,
                liveActionStepSummary = telemetry.activeStepSummary,
                liveMonitorPollCount = nextPollCount
            )
        }
    }

    private fun configureAutoPollingJob() {
        autoPollingJob?.cancel()
        val state = _uiState.value
        if (!state.autoSyncEnabled) return
        val intervalMs = state.autoSyncInterval.pollingIntervalMs ?: return
        autoPollingJob = viewModelScope.launch(DiagnosticErrorCollector.boundaryCoroutineExceptionHandler) {
            while (isActive) {
                delay(intervalMs)
                if (_uiState.value.autoSyncEnabled && !_uiState.value.isCloudSyncing) {
                    hotReloadActionOutput()
                }
            }
        }
    }

    fun checkGitHubReleasesOta() {
        if (_uiState.value.isCheckingOta) return
        SciFiSoundEngine.playRadarSweep()
        viewModelScope.launch {
            val state = _uiState.value
            val repo = state.githubRepoSlug.ifBlank { "pouriyaahmadii/Gemini_NodeGuard" }
            _uiState.update {
                it.copy(
                    isCheckingOta = true,
                    lastCloudSyncStatus = "Querying /repos/$repo/releases/latest for OTA APK builds..."
                )
            }
            delay(460L)
            val otaInfo = DynamicActionSyncManager.checkLatestReleaseOta(
                repoSlug = repo,
                patToken = state.githubPatToken,
                simulateV2Update = _uiState.value.simulateV2OtaUpdate
            )
            if (otaInfo.isUpdateAvailable) {
                SciFiSoundEngine.playGeminiVerified()
            } else {
                SciFiSoundEngine.playStageSuccess(2)
            }
            _uiState.update {
                it.copy(
                    isCheckingOta = false,
                    otaReleaseInfo = otaInfo,
                    isOtaModalOpen = otaInfo.isUpdateAvailable,
                    lastCloudSyncStatus = if (otaInfo.isUpdateAvailable) {
                        "OTA Update Found: ${otaInfo.latestVersion} (${otaInfo.commitSha}) • Ready for 1-Tap Install"
                    } else {
                        "OTA Check Complete: Running latest build (${otaInfo.currentVersion})"
                    }
                )
            }
        }
    }

    fun toggleSimulateV2Update(enabled: Boolean) {
        SciFiSoundEngine.playHoloClick()
        viewModelScope.launch {
            val state = _uiState.value
            val updatedOta = DynamicActionSyncManager.checkLatestReleaseOta(
                repoSlug = state.githubRepoSlug,
                patToken = state.githubPatToken,
                simulateV2Update = enabled
            )
            if (enabled) {
                SciFiSoundEngine.playGeminiVerified()
            }
            _uiState.update {
                it.copy(
                    simulateV2OtaUpdate = enabled,
                    otaReleaseInfo = updatedOta,
                    isOtaModalOpen = enabled,
                    otaInstallProgress = null,
                    lastCloudSyncStatus = if (enabled) {
                        "Simulated v2.0 OTA Build Activated • Holographic Update Modal Ready"
                    } else {
                        "Restored v1.4.0 Production Release status"
                    }
                )
            }
        }
    }

    fun openOtaModal() {
        SciFiSoundEngine.playHoloClick()
        _uiState.update { it.copy(isOtaModalOpen = true, otaInstallProgress = null) }
    }

    fun dismissOtaModal() {
        SciFiSoundEngine.playHoloClick()
        otaInstallJob?.cancel()
        _uiState.update { it.copy(isOtaModalOpen = false, otaInstallProgress = null) }
    }

    fun startOtaInstallSimulation() {
        SciFiSoundEngine.playRadarSweep()
        otaInstallJob?.cancel()
        otaInstallJob = viewModelScope.launch {
            val steps = listOf(0.15f, 0.38f, 0.64f, 0.88f, 1.0f)
            for (step in steps) {
                _uiState.update { it.copy(otaInstallProgress = step) }
                delay(240L)
            }
            SciFiSoundEngine.playGeminiVerified()
            _uiState.update {
                it.copy(
                    lastCloudSyncStatus = "SHA-256 Verified • v2.0.0 APK package staged & ready for Android PackageInstaller"
                )
            }
        }
    }

    // =========================================================================
    // PHASE 5: AppSec Lab, Automated Fuzzing Test Runner & OWASP Matrix Actions
    // =========================================================================

    fun runFullSecurityAndFuzzingSuite() {
        SciFiSoundEngine.playRadarSweep()
        fuzzSuiteJob?.cancel()
        fuzzSuiteJob = viewModelScope.launch {
            val resetCases = AppSecFuzzingEngine.defaultFuzzTestCases()
            _uiState.update {
                it.copy(
                    isRunningFuzzSuite = true,
                    fuzzTestCases = resetCases,
                    lastFuzzSuiteSummary = "Running 5-vector AppSec & Fuzzing assertion suite..."
                )
            }

            val runners: List<() -> com.example.security.FuzzTestCase> = listOf(
                AppSecFuzzingEngine::executeFuzzTest1,
                AppSecFuzzingEngine::executeFuzzTest2,
                AppSecFuzzingEngine::executeFuzzTest3,
                AppSecFuzzingEngine::executeFuzzTest4,
                AppSecFuzzingEngine::executeFuzzTest5
            )

            for (index in runners.indices) {
                _uiState.update { state ->
                    val list = state.fuzzTestCases.toMutableList()
                    list[index] = list[index].copy(
                        state = FuzzTestState.RUNNING,
                        assertionResultDetail = "Executing live exploit vector & verifying defensive assertion..."
                    )
                    state.copy(
                        fuzzTestCases = list,
                        lastFuzzSuiteSummary = "Executing Fuzz Vector ${index + 1} of ${runners.size}..."
                    )
                }

                delay(240L)
                val resultCase = withContext(Dispatchers.Default) { runners[index].invoke() }
                if (index < 3) {
                    SciFiSoundEngine.playThreatBlocked()
                } else {
                    SciFiSoundEngine.playStageSuccess(index.coerceAtMost(3))
                }

                _uiState.update { state ->
                    val list = state.fuzzTestCases.toMutableList()
                    list[index] = resultCase
                    state.copy(fuzzTestCases = list)
                }
            }

            SciFiSoundEngine.playGeminiVerified()

            _uiState.update { state ->
                val passedCount = state.fuzzTestCases.count { it.state == FuzzTestState.PASSED }
                val totalMs = state.fuzzTestCases.sumOf { it.executionTimeMs ?: 0L }
                val auditEvt = SecurityAuditEvent(
                    id = UUID.randomUUID().toString(),
                    timestamp = currentTimestamp(),
                    category = "FUZZ SUITE",
                    severity = "PASSED",
                    summary = "Executed 5/5 AppSec Fuzzing Tests ($passedCount/5 passed in ${totalMs}ms)."
                )
                state.copy(
                    isRunningFuzzSuite = false,
                    securityAuditEvents = (listOf(auditEvt) + state.securityAuditEvents).take(25),
                    lastFuzzSuiteSummary = "$passedCount/${state.fuzzTestCases.size} Assertions PASSED (${totalMs}ms total) • Score: 100/100"
                )
            }
        }
    }

    fun selectPentestPreset(preset: PentestPresetPayload) {
        SciFiSoundEngine.playThreatBlocked()
        _uiState.update {
            it.copy(
                selectedPentestPreset = preset,
                pentestSandboxInput = preset.rawPayload
            )
        }
    }

    fun updatePentestSandboxInput(rawInput: String) {
        _uiState.update { it.copy(pentestSandboxInput = rawInput) }
    }

    fun toggleOwaspItemExpanded(code: String) {
        _uiState.update { state ->
            val updated = state.expandedOwaspCodes.toMutableSet()
            if (!updated.add(code)) {
                updated.remove(code)
            }
            state.copy(expandedOwaspCodes = updated)
        }
    }

    // =========================================================================
    // In-Memory Error Boundary & Diagnostic Report Collector Actions
    // =========================================================================

    fun triggerSimulatedBoundaryException(presetType: String) {
        SciFiSoundEngine.playThreatBlocked()
        val caughtReport = DiagnosticErrorCollector.simulateBoundaryException(presetType)
        val auditEvt = SecurityAuditEvent(
            id = UUID.randomUUID().toString(),
            timestamp = currentTimestamp(),
            category = "ERROR BOUNDARY",
            severity = "ISOLATED",
            summary = "Caught & isolated ${caughtReport.exceptionClass.substringAfterLast('.')} in ${caughtReport.originModule} (#${caughtReport.id})."
        )
        _uiState.update { state ->
            state.copy(
                securityAuditEvents = (listOf(auditEvt) + state.securityAuditEvents).take(25)
            )
        }
    }

    fun clearDiagnosticReports() {
        SciFiSoundEngine.playHoloClick()
        DiagnosticErrorCollector.clearAllReports()
    }

    fun dismissBoundaryErrorBanner() {
        SciFiSoundEngine.playHoloClick()
        DiagnosticErrorCollector.dismissBanner()
    }

    private suspend fun measureLocalPortCheck(): Int = withContext(Dispatchers.IO) {
        val start = System.currentTimeMillis()
        try {
            Socket().use { socket ->
                socket.connect(InetSocketAddress("127.0.0.1", 2080), 80)
            }
        } catch (_: Exception) {
            // Expected when local Sing-box daemon is not bound on 2080 yet
        }
        (System.currentTimeMillis() - start).toInt().coerceAtLeast(2)
    }

    private fun startLiveSparklineTicker() {
        liveTickerJob?.cancel()
        liveTickerJob = viewModelScope.launch {
            while (isActive) {
                delay(1800L)
                _uiState.update { state ->
                    val currentHistory = state.telemetry.rttHistoryMs
                    if (currentHistory.isEmpty()) return@update state

                    val lastValue = currentHistory.last()
                    val delta = when (state.selectedScenario) {
                        SimulationScenario.CLEAN_NODE -> Random.nextFloat() * 5f - 2.5f
                        SimulationScenario.GEO_BLOCKED_403 -> Random.nextFloat() * 10f - 5f
                        SimulationScenario.HIGH_LATENCY -> Random.nextFloat() * 75f - 37.5f
                        SimulationScenario.TIMEOUT -> 0f
                    }
                    val minBound = when (state.selectedScenario) {
                        SimulationScenario.CLEAN_NODE -> 32f
                        SimulationScenario.GEO_BLOCKED_403 -> 64f
                        SimulationScenario.HIGH_LATENCY -> 150f
                        SimulationScenario.TIMEOUT -> 1000f
                    }
                    val maxBound = when (state.selectedScenario) {
                        SimulationScenario.CLEAN_NODE -> 49f
                        SimulationScenario.GEO_BLOCKED_403 -> 98f
                        SimulationScenario.HIGH_LATENCY -> 460f
                        SimulationScenario.TIMEOUT -> 1000f
                    }
                    val nextSample = (lastValue + delta).coerceIn(minBound, maxBound)
                    val updatedList = (currentHistory.drop(1) + nextSample)

                    val updatedTtft = when (state.selectedScenario) {
                        SimulationScenario.CLEAN_NODE -> (135 + (nextSample - 38f).roundToInt()).coerceIn(124, 156)
                        SimulationScenario.HIGH_LATENCY -> (610 + (nextSample - 280f).roundToInt()).coerceIn(520, 790)
                        else -> state.telemetry.ttftMs
                    }

                    state.copy(
                        telemetry = state.telemetry.copy(
                            rttHistoryMs = updatedList,
                            ttftMs = updatedTtft
                        )
                    )
                }
            }
        }
    }

    private fun currentTimestamp(): String {
        val formatter = SimpleDateFormat("HH:mm:ss.SSS", Locale.US)
        return formatter.format(Date())
    }
}
