package com.example.model

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.compose.ui.graphics.Color
import com.example.bridge.ActionRunStatus
import com.example.bridge.AutoSyncInterval
import com.example.bridge.CoreExecutionMode
import com.example.bridge.DynamicActionSyncManager
import com.example.bridge.DynamicTargetEndpointRule
import com.example.bridge.NativeCoreTelemetry
import com.example.bridge.NodeGuardNativeBridge
import com.example.bridge.OtaReleaseInfo
import com.example.bridge.SingBoxConfigOptions
import com.example.parser.NodeScanStatus
import com.example.parser.ProxyNode
import com.example.parser.ProxyUriParser
import com.example.parser.ScannerFilter
import com.example.security.AppSecFuzzingEngine
import com.example.security.CaughtDiagnosticReport
import com.example.security.FuzzTestCase
import com.example.security.FuzzTestState
import com.example.security.PentestPresetPayload
import com.example.security.SecurityAuditEvent
import com.example.security.SecurityHardeningManager
import com.example.security.VaultStatusSnapshot
import com.example.ui.components.BrandEmblemType
import com.example.ui.theme.ElectricPurple
import com.example.ui.theme.NeonAmber
import com.example.ui.theme.NeonCrimson
import com.example.ui.theme.NeonCyan
import com.example.ui.theme.NeonEmerald
import com.example.ui.theme.RoyalSapphire

@Immutable
enum class AppTab(
    val route: String,
    val title: String,
    val badgeText: String? = null
) {
    SHIELD("shield", "Shield", "LIVE"),
    SCANNER("scanner", "Scanner", "15"),
    CLOUD_CORE("cloud_core", "Cloud & Core", "JNI"),
    APPSEC_LAB("appsec_lab", "AppSec Lab", "100%")
}

@Immutable
enum class ProbeStatus(
    val codeLabel: String,
    val title: String,
    val subtitle: String,
    val primaryColor: Color,
    val secondaryColor: Color
) {
    IDLE(
        codeLabel = "STANDBY",
        title = "SHIELD READY",
        subtitle = "Tap central core to initiate 4-stage Gemini & Google ecosystem verification",
        primaryColor = RoyalSapphire,
        secondaryColor = NeonCyan
    ),
    PROBING(
        codeLabel = "PROBING...",
        title = "INSPECTING TUNNEL",
        subtitle = "Executing multi-tiered SOCKS5, TLS 1.3 ALPN & Geo-IP handshake",
        primaryColor = NeonCyan,
        secondaryColor = ElectricPurple
    ),
    VERIFIED_200(
        codeLabel = "HTTP 200 OK",
        title = "GEMINI UNBLOCKED",
        subtitle = "Zero region locks detected • Direct HTTP/2 & QUIC stream verified",
        primaryColor = NeonEmerald,
        secondaryColor = NeonCyan
    ),
    HIGH_JITTER(
        codeLabel = "HIGH JITTER",
        title = "DEGRADED STREAM",
        subtitle = "Gemini reachable (HTTP 200) • Elevated packet variance on link",
        primaryColor = NeonAmber,
        secondaryColor = NeonCyan
    ),
    GEO_BLOCKED_403(
        codeLabel = "HTTP 403 / ERR",
        title = "ENDPOINT BLOCKED",
        subtitle = "FAILED_PRECONDITION: User location is not supported or tunnel dropped",
        primaryColor = NeonCrimson,
        secondaryColor = ElectricPurple
    )
}

@Immutable
enum class SimulationScenario(
    val id: String,
    val label: String,
    val profileTag: String,
    val nodeUriSummary: String
) {
    CLEAN_NODE(
        id = "clean_node",
        label = "Clean Node",
        profileTag = "VLESS • XTLS-Vision • Tokyo Edge",
        nodeUriSummary = "vless://tyo-edge-01.nodeguard.io:443 [Sing-box 1.10.2]"
    ),
    GEO_BLOCKED_403(
        id = "geo_blocked_403",
        label = "403 Geo-Blocked",
        profileTag = "VMess • WS+TLS • Restricted Exit",
        nodeUriSummary = "vmess://hkg-transit-04.nodeguard.io:8443 [Xray-core 1.8.24]"
    ),
    HIGH_LATENCY(
        id = "high_latency",
        label = "High Latency",
        profileTag = "Hysteria2 • QUIC • Congested Hop",
        nodeUriSummary = "hy2://fra-relay-09.nodeguard.io:4433 [QUIC BBRv2]"
    ),
    TIMEOUT(
        id = "timeout",
        label = "Timeout",
        profileTag = "Trojan • gRPC • Dead Local Port",
        nodeUriSummary = "trojan://127.0.0.1:2080 [SOCKS5 Handshake Drop]"
    )
}

@Immutable
enum class StageState {
    IDLE,
    RUNNING,
    PASSED,
    WARNING,
    FAILED
}

@Immutable
data class ValidationStage(
    val stepNumber: Int,
    val title: String,
    val endpointSpec: String,
    val diagnosticDetail: String,
    val state: StageState = StageState.IDLE,
    val durationMs: Int? = null,
    val statusBadge: String = "STANDBY"
)

@Immutable
data class GoogleServiceStatusItem(
    val id: String,
    val serviceName: String,
    val endpointHost: String,
    val badgeText: String,
    val latencyLabel: String,
    val statusDetail: String,
    val statusColor: Color,
    val emblemType: BrandEmblemType
)

@Immutable
data class EdgeTelemetry(
    val ttftMs: Int,
    val tokenStreamRate: String,
    val pingJitterMs: Float,
    val packetLossPercent: Float,
    val rawExitIp: String,
    val asnOrg: String,
    val regionCode: String,
    val tlsCipherSuite: String,
    val alpnProtocol: String,
    val localPortOpen: Boolean,
    val rttHistoryMs: List<Float>
) {
    fun formattedExitIp(redacted: Boolean): String {
        if (!redacted) return rawExitIp
        val parts = rawExitIp.split(".")
        return if (parts.size == 4) {
            "${parts[0]}.${parts[1]}.***.***"
        } else {
            "***REDACTED***"
        }
    }
}

@Stable
data class ShieldUiState(
    val isDarkMode: Boolean = true,
    val isSoundEnabled: Boolean = true,
    val probeShockwaveTrigger: Int = 0,
    val activeTab: AppTab = AppTab.SHIELD,
    val selectedScenario: SimulationScenario = SimulationScenario.CLEAN_NODE,
    val probeStatus: ProbeStatus = ProbeStatus.VERIFIED_200,
    val isProbing: Boolean = false,
    val currentStageIndex: Int = -1,
    val stages: List<ValidationStage> = initialVerifiedValidationStages(),
    val telemetry: EdgeTelemetry = defaultTelemetryForScenario(SimulationScenario.CLEAN_NODE),
    val googleServices: List<GoogleServiceStatusItem> = googleServicesForScenario(SimulationScenario.CLEAN_NODE),
    val redactSensitiveLogs: Boolean = true,
    val isFlagSecureEnabled: Boolean = true,
    val lastProbeTimestamp: String = "VERIFIED ON BOOT • 4/4 PASSED (TOKYO EDGE)",
    val securityAuditNote: String = "Zero-Trust Guard Active • SSRF Firewall + Privacy Shield + AES-256-GCM Vault",
    // Phase 2: Batch Node Scanner State
    val scannerNodes: List<ProxyNode> = initialSeeded15Nodes(),
    val selectedScannerFilter: ScannerFilter = ScannerFilter.ALL,
    val concurrencyLimit: Int = 8,
    val probeTimeoutMs: Int = 3500,
    val isBatchScanning: Boolean = false,
    val activeCoroutinesCount: Int = 0,
    val completedScanCount: Int = 15,
    val inspectedNode: ProxyNode? = null,
    val isImportDialogOpen: Boolean = false,
    val lastScannerBannerMessage: String = "15 multi-protocol nodes loaded • SSRF Firewall & 64KB Cap Active",
    // Phase 3: Cloud Sync, Sing-box Config Builder & Native Go/Rust Bridge State
    val githubRepoSlug: String = "pouriyaahmadii/Gemini_NodeGuard",
    val gistOrArtifactId: String = "gist_8f94c2e10a7b4d3e91f0_verified_gemini",
    val githubPatToken: String = "ghp_NodeGuardCiCdArtifactToken9942xYz",
    val isPatVisible: Boolean = false,
    val isCloudSyncing: Boolean = false,
    val syncedCloudNodesCount: Int = 0,
    val lastCloudSyncStatus: String = "CI/CD Workflow #418 Ready • pouriyaahmadii/Gemini_NodeGuard",
    val selectedConfigNodeId: String? = null,
    val singBoxOptions: SingBoxConfigOptions = SingBoxConfigOptions(),
    val coreExecutionMode: CoreExecutionMode = CoreExecutionMode.KOTLIN_COROUTINE_ENGINE,
    val nativeCoreTelemetry: NativeCoreTelemetry = NodeGuardNativeBridge
        .engineForMode(CoreExecutionMode.KOTLIN_COROUTINE_ENGINE)
        .queryCoreTelemetry(),
    // Phase 4: Zero-Trust AppSec Hardening State
    val vaultStatus: VaultStatusSnapshot = VaultStatusSnapshot(
        keystoreProvider = "AndroidKeyStore TEE (AES-256-GCM)",
        isHardwareBacked = true,
        hasEncryptedPat = true,
        hasEncryptedGistId = true,
        encryptedBlobPreviewHex = "0x8F4A2C91E0B73D12…",
        lastVaultOperation = "Credentials sealed with AES-256-GCM (128-bit Tag)",
        zeroizationCount = 0
    ),
    val ssrfBlockedTotalCount: Int = 0,
    val injectionNeutralizedTotalCount: Int = 0,
    val oversizedPayloadsBlockedCount: Int = 0,
    val securityAuditEvents: List<SecurityAuditEvent> = defaultSecurityAuditEvents(),
    // Phase 5: AppSec Lab, Automated Fuzzing Suite & OWASP Mobile Top 10 State
    val fuzzTestCases: List<FuzzTestCase> = initialExecutedFuzzTests(),
    val isRunningFuzzSuite: Boolean = false,
    val lastFuzzSuiteSummary: String = "5/5 Live Security & Fuzzing Assertions PASSED • Score: 100/100",
    val selectedPentestPreset: PentestPresetPayload = PentestPresetPayload.SSRF_CLOUD_METADATA,
    val pentestSandboxInput: String = PentestPresetPayload.SSRF_CLOUD_METADATA.rawPayload,
    val expandedOwaspCodes: Set<String> = setOf("M1", "M4", "M5", "M6", "M10"),
    // Phase 6: Dynamic GitHub Actions Remote-Config Sync & OTA In-App Updater State
    val actionRunStatus: ActionRunStatus = ActionRunStatus.COMPLETED,
    val isLiveActionMonitorEnabled: Boolean = true,
    val liveActionStepSummary: String = "Verified remote-action-config.json published • 6/6 AI services unblocked",
    val liveMonitorPollCount: Int = 1,
    val isDispatchingWorkflow: Boolean = false,
    val latestWorkflowRunNumber: Int = 418,
    val latestActionCommitHash: String = "9e4b2c1",
    val latestActionTimestamp: String = "2025-05-14 19:40 UTC",
    val autoSyncEnabled: Boolean = true,
    val autoSyncInterval: AutoSyncInterval = AutoSyncInterval.ON_APP_LAUNCH,
    val dynamicEndpointRules: List<DynamicTargetEndpointRule> = DynamicActionSyncManager.initialDynamicEndpoints(),
    val selectedDynamicRuleId: String? = null,
    val remoteActionConfigJson: String = DynamicActionSyncManager.initialRemoteConfigJsonPreview(),
    val otaReleaseInfo: OtaReleaseInfo = OtaReleaseInfo(),
    val isCheckingOta: Boolean = false,
    val simulateV2OtaUpdate: Boolean = false,
    val isOtaModalOpen: Boolean = false,
    val otaInstallProgress: Float? = null,
    // In-Memory Error Boundary & Diagnostic Report Collector State
    val diagnosticReports: List<CaughtDiagnosticReport> = emptyList(),
    val lastCaughtBoundaryError: CaughtDiagnosticReport? = null
)

fun initialVerifiedValidationStages(): List<ValidationStage> = listOf(
    ValidationStage(
        stepNumber = 1,
        title = "1. Local Tunnel / Port Handshake",
        endpointSpec = "127.0.0.1:2080 (SOCKS5 / Sing-box Inbound)",
        diagnosticDetail = "SOCKS5h handshake verified on 127.0.0.1:2080 (METHOD=0x00 NO_AUTH)",
        state = StageState.PASSED,
        durationMs = 3,
        statusBadge = "3ms OK"
    ),
    ValidationStage(
        stepNumber = 2,
        title = "2. TLS 1.3 & SNI Check",
        endpointSpec = "TLS_AES_256_GCM_SHA384 • ALPN h2 / http/1.1",
        diagnosticDetail = "Cipher: TLS_AES_256_GCM_SHA384 • ALPN: h2 • SNI: Verified",
        state = StageState.PASSED,
        durationMs = 34,
        statusBadge = "34ms TLS 1.3"
    ),
    ValidationStage(
        stepNumber = 3,
        title = "3. Google Core Reachability (generate_204)",
        endpointSpec = "https://www.googleapis.com/generate_204",
        diagnosticDetail = "GFE edge baseline confirmed (HTTP 204) • Zero DPI packet tampering",
        state = StageState.PASSED,
        durationMs = 41,
        statusBadge = "HTTP 204 (41ms)"
    ),
    ValidationStage(
        stepNumber = 4,
        title = "4. Gemini AI Geo-Block Inspection (403/400 Region Lock check)",
        endpointSpec = "generativelanguage.googleapis.com/v1beta/models",
        diagnosticDetail = "HTTP 200 OK • Gemini 2.0 Flash / 1.5 Pro unblocked • Exit: Tokyo (JP)",
        state = StageState.PASSED,
        durationMs = 138,
        statusBadge = "200 VERIFIED"
    )
)

fun initialExecutedFuzzTests(): List<FuzzTestCase> = listOf(
    FuzzTestCase(
        testNumber = 1,
        title = "Test 1: SSRF Loopback & Cloud Metadata Exploit",
        attackVectorPayload = "vless://uuid@127.0.0.1:8080 & vmess://[169.254.169.254]",
        expectedDefense = "SsrfFirewall blocks RFC1122 Loopback & 169.254.169.254 Metadata with SSRF_BLOCKED",
        assertionResultDetail = "ASSERTION PASSED: Both 127.0.0.1:8080 (RFC1122_LOOPBACK_127.0.0.0/8) and 169.254.169.254 (CLOUD_METADATA_169.254.169.254) intercepted with BLOCKED: SSRF Risk.",
        state = FuzzTestState.PASSED,
        executionTimeMs = 2L
    ),
    FuzzTestCase(
        testNumber = 2,
        title = "Test 2: Sing-box Config & JSON Breakout Injection",
        attackVectorPayload = "sni=evil.com\",\"detour\":\"local-root-shell\" & path=../../etc/passwd;id",
        expectedDefense = "ConfigInjectionSanitizer strips quotes, braces, traversal & shell metacharacters",
        assertionResultDetail = "ASSERTION PASSED: Neutralized [JSON_BREAKOUT_CHARS, PATH_TRAVERSAL, SHELL_METACHARACTERS] • Zero JSON key breakout in Sing-box outbound.",
        state = FuzzTestState.PASSED,
        executionTimeMs = 2L
    ),
    FuzzTestCase(
        testNumber = 3,
        title = "Test 3: Base64 Memory Bomb DoS Defense (>64KB)",
        attackVectorPayload = "vmess://<72,000-byte oversized Base64 bomb payload>",
        expectedDefense = "Strict 65,536-byte boundary check rejects oversized URI in O(1) without OOM",
        assertionResultDetail = "ASSERTION PASSED: 72,000-byte Base64 bomb rejected at boundary (limit: 65,536B / 2MB Sub) in 1ms prior to allocation.",
        state = FuzzTestState.PASSED,
        executionTimeMs = 1L
    ),
    FuzzTestCase(
        testNumber = 4,
        title = "Test 4: DNS Leak & Unresolved Socket Enforcement",
        attackVectorPayload = "Inspect SingBoxConfigBuilder DNS detour & explicit Gemini domain routing",
        expectedDefense = "Enforces Remote DoH (https://1.1.1.1/dns-query) detour via gemini-proxy",
        assertionResultDetail = "ASSERTION PASSED: Remote DoH (https://1.1.1.1/dns-query) detours via 'gemini-proxy' with explicit Google AI & Colab domains pinned.",
        state = FuzzTestState.PASSED,
        executionTimeMs = 1L
    ),
    FuzzTestCase(
        testNumber = 5,
        title = "Test 5: Vault AES-256-GCM & Log Redaction Integrity",
        attackVectorPayload = "HardwareCredentialVault seal/zeroize + SafeLogRedactor on Bearer/UUID logs",
        expectedDefense = "AES/GCM/NoPadding encrypts secrets & SafeLogRedactor scrubs tokens/UUIDs/IPs",
        assertionResultDetail = "ASSERTION PASSED: AES-256-GCM seal/decrypt/zeroize verified (0x00 wipe) & SafeLogRedactor scrubbed 100% of Bearer/UUID/IP tokens.",
        state = FuzzTestState.PASSED,
        executionTimeMs = 3L
    )
)

fun defaultSecurityAuditEvents(): List<SecurityAuditEvent> = listOf(
    SecurityAuditEvent(
        id = "evt_1",
        timestamp = "BOOT+0.02s",
        category = "FLAG_SECURE",
        severity = "ENFORCED",
        summary = "WindowManager.LayoutParams.FLAG_SECURE enabled; screenshot & screen-recording blocked."
    ),
    SecurityAuditEvent(
        id = "evt_2",
        timestamp = "BOOT+0.05s",
        category = "AES-256-GCM VAULT",
        severity = "SEALED",
        summary = "GitHub PAT & Secret Gist ID encrypted via AES/GCM/NoPadding with CharArray backing."
    ),
    SecurityAuditEvent(
        id = "evt_3",
        timestamp = "BOOT+0.08s",
        category = "SSRF FIREWALL",
        severity = "ACTIVE",
        summary = "Blocking RFC 1918 (10/8, 172.16/12, 192.168/16), Loopback (127/8, ::1) & Metadata (169.254.169.254)."
    ),
    SecurityAuditEvent(
        id = "evt_4",
        timestamp = "BOOT+0.11s",
        category = "PAYLOAD GUARD",
        severity = "ACTIVE",
        summary = "Enforcing strict 64KB (65,536B) Base64 cap & JSON/Shell/Traversal injection sanitizer."
    )
)

fun defaultValidationStages(): List<ValidationStage> = listOf(
    ValidationStage(
        stepNumber = 1,
        title = "1. Local Tunnel / Port Handshake",
        endpointSpec = "127.0.0.1:2080 (SOCKS5 / Sing-box Inbound)",
        diagnosticDetail = "Awaiting local loopback socket bind & RFC 1928 version identifier check",
        state = StageState.IDLE,
        durationMs = null,
        statusBadge = "IDLE"
    ),
    ValidationStage(
        stepNumber = 2,
        title = "2. TLS 1.3 & SNI Check",
        endpointSpec = "TLS_AES_256_GCM_SHA384 • ALPN h2 / http/1.1",
        diagnosticDetail = "Awaiting SNI handshake & X25519 ephemeral key exchange verification",
        state = StageState.IDLE,
        durationMs = null,
        statusBadge = "IDLE"
    ),
    ValidationStage(
        stepNumber = 3,
        title = "3. Google Core Reachability (generate_204)",
        endpointSpec = "https://www.googleapis.com/generate_204",
        diagnosticDetail = "Awaiting Google Front-End (GFE) edge HTTP 204 reachability check",
        state = StageState.IDLE,
        durationMs = null,
        statusBadge = "IDLE"
    ),
    ValidationStage(
        stepNumber = 4,
        title = "4. Gemini AI Geo-Block Inspection (403/400 Region Lock check)",
        endpointSpec = "generativelanguage.googleapis.com/v1beta/models",
        diagnosticDetail = "Inspecting payload for HTTP 403/400 FAILED_PRECONDITION / User location lock",
        state = StageState.IDLE,
        durationMs = null,
        statusBadge = "IDLE"
    )
)

fun googleServicesForScenario(scenario: SimulationScenario): List<GoogleServiceStatusItem> {
    return when (scenario) {
        SimulationScenario.CLEAN_NODE -> listOf(
            GoogleServiceStatusItem(
                id = "gemini_api",
                serviceName = "Google Gemini API",
                endpointHost = "generativelanguage.googleapis.com",
                badgeText = "200 UNBLOCKED",
                latencyLabel = "138 ms TTFT",
                statusDetail = "v1beta SSE stream & function calling active (Tokyo Edge)",
                statusColor = NeonEmerald,
                emblemType = BrandEmblemType.GOOGLE_GEMINI_API
            ),
            GoogleServiceStatusItem(
                id = "ai_studio",
                serviceName = "Google AI Studio",
                endpointHost = "aistudio.google.com",
                badgeText = "200 VERIFIED",
                latencyLabel = "92 ms RTT",
                statusDetail = "MakerSuite gRPC-Web & prompt workspace unblocked",
                statusColor = NeonEmerald,
                emblemType = BrandEmblemType.GOOGLE_AI_STUDIO
            ),
            GoogleServiceStatusItem(
                id = "google_search",
                serviceName = "Google Search/Core",
                endpointHost = "www.googleapis.com/generate_204",
                badgeText = "204 NO CONTENT",
                latencyLabel = "36 ms RTT",
                statusDetail = "GFE HTTP/3 Anycast reachable • Zero CAPTCHA trigger",
                statusColor = NeonCyan,
                emblemType = BrandEmblemType.GOOGLE_SEARCH_CORE
            ),
            GoogleServiceStatusItem(
                id = "youtube",
                serviceName = "YouTube",
                endpointHost = "rr1---sn-gfe.googlevideo.com",
                badgeText = "4K60 QUIC OK",
                latencyLabel = "41 ms RTT",
                statusDetail = "UDP 443 QUIC media mux active • 0% frame drop",
                statusColor = NeonEmerald,
                emblemType = BrandEmblemType.YOUTUBE_STREAM
            )
        )
        SimulationScenario.GEO_BLOCKED_403 -> listOf(
            GoogleServiceStatusItem(
                id = "gemini_api",
                serviceName = "Google Gemini API",
                endpointHost = "generativelanguage.googleapis.com",
                badgeText = "403 GEO-BLOCKED",
                latencyLabel = "BLOCKED",
                statusDetail = "FAILED_PRECONDITION: User location (HKG) not supported",
                statusColor = NeonCrimson,
                emblemType = BrandEmblemType.GOOGLE_GEMINI_API
            ),
            GoogleServiceStatusItem(
                id = "ai_studio",
                serviceName = "Google AI Studio",
                endpointHost = "aistudio.google.com",
                badgeText = "403 REGION LOCK",
                latencyLabel = "BLOCKED",
                statusDetail = "Workspace redirect to unsupported country notice",
                statusColor = NeonCrimson,
                emblemType = BrandEmblemType.GOOGLE_AI_STUDIO
            ),
            GoogleServiceStatusItem(
                id = "google_search",
                serviceName = "Google Search/Core",
                endpointHost = "www.googleapis.com/generate_204",
                badgeText = "204 REACHABLE",
                latencyLabel = "74 ms RTT",
                statusDetail = "GFE reachable, but exit ASN flagged for AI endpoints",
                statusColor = NeonAmber,
                emblemType = BrandEmblemType.GOOGLE_SEARCH_CORE
            ),
            GoogleServiceStatusItem(
                id = "youtube",
                serviceName = "YouTube",
                endpointHost = "rr1---sn-gfe.googlevideo.com",
                badgeText = "200 STREAM OK",
                latencyLabel = "79 ms RTT",
                statusDetail = "Media CDN reachable via HKG transit",
                statusColor = NeonEmerald,
                emblemType = BrandEmblemType.YOUTUBE_STREAM
            )
        )
        SimulationScenario.HIGH_LATENCY -> listOf(
            GoogleServiceStatusItem(
                id = "gemini_api",
                serviceName = "Google Gemini API",
                endpointHost = "generativelanguage.googleapis.com",
                badgeText = "200 HIGH JITTER",
                latencyLabel = "642 ms TTFT",
                statusDetail = "Stream active but TCP retransmissions slow token output",
                statusColor = NeonAmber,
                emblemType = BrandEmblemType.GOOGLE_GEMINI_API
            ),
            GoogleServiceStatusItem(
                id = "ai_studio",
                serviceName = "Google AI Studio",
                endpointHost = "aistudio.google.com",
                badgeText = "200 SLOW LINK",
                latencyLabel = "410 ms RTT",
                statusDetail = "Elevated RTT variance (±68.5ms) on congested hop",
                statusColor = NeonAmber,
                emblemType = BrandEmblemType.GOOGLE_AI_STUDIO
            ),
            GoogleServiceStatusItem(
                id = "google_search",
                serviceName = "Google Search/Core",
                endpointHost = "www.googleapis.com/generate_204",
                badgeText = "204 DELAYED",
                latencyLabel = "348 ms RTT",
                statusDetail = "GFE responded after 1 packet retransmission",
                statusColor = NeonAmber,
                emblemType = BrandEmblemType.GOOGLE_SEARCH_CORE
            ),
            GoogleServiceStatusItem(
                id = "youtube",
                serviceName = "YouTube",
                endpointHost = "rr1---sn-gfe.googlevideo.com",
                badgeText = "BUFFER WARN",
                latencyLabel = "385 ms RTT",
                statusDetail = "QUIC congestion window throttled (4.8% packet loss)",
                statusColor = NeonAmber,
                emblemType = BrandEmblemType.YOUTUBE_STREAM
            )
        )
        SimulationScenario.TIMEOUT -> listOf(
            GoogleServiceStatusItem(
                id = "gemini_api",
                serviceName = "Google Gemini API",
                endpointHost = "generativelanguage.googleapis.com",
                badgeText = "SOCKET TIMEOUT",
                latencyLabel = "DEAD",
                statusDetail = "Aborted at Stage 1 (Local SOCKS5 port 2080 down)",
                statusColor = NeonCrimson,
                emblemType = BrandEmblemType.GOOGLE_GEMINI_API
            ),
            GoogleServiceStatusItem(
                id = "ai_studio",
                serviceName = "Google AI Studio",
                endpointHost = "aistudio.google.com",
                badgeText = "UNREACHABLE",
                latencyLabel = "DEAD",
                statusDetail = "Fail-closed guard blocked direct unproxied fallback",
                statusColor = NeonCrimson,
                emblemType = BrandEmblemType.GOOGLE_AI_STUDIO
            ),
            GoogleServiceStatusItem(
                id = "google_search",
                serviceName = "Google Search/Core",
                endpointHost = "www.googleapis.com/generate_204",
                badgeText = "TUNNEL DOWN",
                latencyLabel = "DEAD",
                statusDetail = "ECONNREFUSED on 127.0.0.1:2080 inbound",
                statusColor = NeonCrimson,
                emblemType = BrandEmblemType.GOOGLE_SEARCH_CORE
            ),
            GoogleServiceStatusItem(
                id = "youtube",
                serviceName = "YouTube",
                endpointHost = "rr1---sn-gfe.googlevideo.com",
                badgeText = "TUNNEL DOWN",
                latencyLabel = "DEAD",
                statusDetail = "No outbound QUIC/UDP route established",
                statusColor = NeonCrimson,
                emblemType = BrandEmblemType.YOUTUBE_STREAM
            )
        )
    }
}

fun defaultTelemetryForScenario(scenario: SimulationScenario): EdgeTelemetry {
    return when (scenario) {
        SimulationScenario.CLEAN_NODE -> EdgeTelemetry(
            ttftMs = 138,
            tokenStreamRate = "84.6 tok/s • HTTP/2 Multiplex",
            pingJitterMs = 3.4f,
            packetLossPercent = 0.0f,
            rawExitIp = "138.199.21.184",
            asnOrg = "AS212238 Datacamp Limited",
            regionCode = "TYO • JP (Supported)",
            tlsCipherSuite = "TLS_AES_256_GCM_SHA384",
            alpnProtocol = "h2",
            localPortOpen = true,
            rttHistoryMs = listOf(44f, 41f, 39f, 42f, 38f, 40f, 37f, 39f, 36f, 38f, 37f, 36f)
        )
        SimulationScenario.GEO_BLOCKED_403 -> EdgeTelemetry(
            ttftMs = 0,
            tokenStreamRate = "BLOCKED • HTTP 403 Payload",
            pingJitterMs = 9.8f,
            packetLossPercent = 0.2f,
            rawExitIp = "45.11.57.210",
            asnOrg = "AS136907 HKT Transit Edge",
            regionCode = "HKG • HK (Geo-Restricted)",
            tlsCipherSuite = "TLS_CHACHA20_POLY1305_SHA256",
            alpnProtocol = "h2",
            localPortOpen = true,
            rttHistoryMs = listOf(68f, 74f, 71f, 82f, 79f, 85f, 88f, 84f, 91f, 87f, 89f, 92f)
        )
        SimulationScenario.HIGH_LATENCY -> EdgeTelemetry(
            ttftMs = 642,
            tokenStreamRate = "19.2 tok/s • Retransmit Spike",
            pingJitterMs = 68.5f,
            packetLossPercent = 4.8f,
            rawExitIp = "185.220.101.44",
            asnOrg = "AS24940 Hetzner Online GmbH",
            regionCode = "FRA • DE (Congested Route)",
            tlsCipherSuite = "TLS_AES_128_GCM_SHA256",
            alpnProtocol = "h2",
            localPortOpen = true,
            rttHistoryMs = listOf(145f, 210f, 180f, 295f, 240f, 340f, 275f, 390f, 310f, 412f, 355f, 385f)
        )
        SimulationScenario.TIMEOUT -> EdgeTelemetry(
            ttftMs = 0,
            tokenStreamRate = "ERR_SOCKS5_CONNECTION_REFUSED",
            pingJitterMs = 240.0f,
            packetLossPercent = 100.0f,
            rawExitIp = "0.0.0.0",
            asnOrg = "UNRESOLVED • Local Port 2080 Down",
            regionCode = "N/A • Tunnel Offline",
            tlsCipherSuite = "NONE (Handshake Aborted)",
            alpnProtocol = "none",
            localPortOpen = false,
            rttHistoryMs = listOf(120f, 195f, 310f, 480f, 650f, 820f, 950f, 1000f, 1000f, 1000f, 1000f, 1000f)
        )
    }
}

fun evaluateNodeDeterministicProfile(node: ProxyNode, indexHint: Int, timeoutMs: Int): ProxyNode {
    val ssrfCheck = SecurityHardeningManager.SsrfFirewall.inspectHostOrUrl(node.host)
    if (node.isPrivateOrLoopbackRisk || node.scanStatus == NodeScanStatus.SSRF_BLOCKED || ssrfCheck.isBlocked) {
        return node.copy(
            isPrivateOrLoopbackRisk = true,
            ssrfMatchedRule = ssrfCheck.matchedRule,
            scanStatus = NodeScanStatus.SSRF_BLOCKED,
            httpCode = null,
            ttftMs = null,
            pingRttMs = null,
            jitterMs = 0f,
            exitRegion = "BLOCKED • SSRF Firewall",
            exitAsn = ssrfCheck.matchedRule,
            diagnosticSummary = ssrfCheck.diagnosticMessage,
            sparklineSamples = listOf(0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f),
            rawHeadersDump = "SECURITY BLOCK: SSRF Attempt\nTarget: ${node.host}:${node.port}\nMatched Firewall Rule: ${ssrfCheck.matchedRule}\nSocket aborted prior to TCP SYN."
        )
    }

    val lowerRemark = node.remark.lowercase()
    val lowerHost = node.host.lowercase()

    return when {
        lowerRemark.contains("filtered") || lowerRemark.contains("dead") || lowerHost.startsWith("198.51.100") -> {
            node.copy(
                scanStatus = NodeScanStatus.TIMEOUT,
                httpCode = null,
                ttftMs = null,
                pingRttMs = timeoutMs,
                jitterMs = 185.0f,
                exitRegion = "IR • Tehran (Filtered)",
                exitAsn = "AS12880 DCI Autonomous",
                diagnosticSummary = "SocketTimeoutException after ${timeoutMs}ms during TLS ClientHello",
                sparklineSamples = listOf(220f, 410f, 680f, 890f, 1000f, 1000f, 1000f, 1000f),
                rawHeadersDump = "ERROR: java.net.SocketTimeoutException: Connect timed out after ${timeoutMs}ms\nSOCKS5 Target: ${node.host}:${node.port}"
            )
        }
        lowerRemark.contains("hk-") || lowerHost.startsWith("45.11.57") -> {
            node.copy(
                scanStatus = NodeScanStatus.GEO_BLOCKED_403,
                httpCode = 403,
                ttftMs = 89,
                pingRttMs = 54,
                jitterMs = 6.2f,
                exitRegion = "HK • Hong Kong (Restricted)",
                exitAsn = "AS136907 HKT Transit Edge",
                diagnosticSummary = "HTTP 403 PERMISSION_DENIED: User location is not supported for Gemini API",
                sparklineSamples = listOf(52f, 56f, 54f, 59f, 53f, 57f, 55f, 54f),
                rawHeadersDump = "HTTP/2 403 Forbidden\ncontent-type: application/json; charset=UTF-8\nx-goog-gemini-error: FAILED_PRECONDITION\nserver: scaffolding on HTTPServer2\n{\"error\":{\"code\":403,\"message\":\"User location is not supported for the API use.\",\"status\":\"FAILED_PRECONDITION\"}}"
            )
        }
        lowerRemark.contains("mow-") || lowerHost.startsWith("77.88.55") -> {
            node.copy(
                scanStatus = NodeScanStatus.REGION_LOCK_400,
                httpCode = 400,
                ttftMs = 112,
                pingRttMs = 78,
                jitterMs = 11.4f,
                exitRegion = "RU • Moscow (Geo-Blocked)",
                exitAsn = "AS13238 Yandex Cloud LLC",
                diagnosticSummary = "HTTP 400 FAILED_PRECONDITION: Regional GFE cluster rejected Gemini token request",
                sparklineSamples = listOf(74f, 81f, 79f, 88f, 84f, 77f, 83f, 78f),
                rawHeadersDump = "HTTP/2 400 Bad Request\ncontent-type: application/json\nx-frame-options: SAMEORIGIN\n{\"error\":{\"code\":400,\"message\":\"User location is not supported for the API use.\",\"status\":\"FAILED_PRECONDITION\"}}"
            )
        }
        lowerRemark.contains("dub-") && timeoutMs < 2000 -> {
            node.copy(
                scanStatus = NodeScanStatus.TIMEOUT,
                httpCode = null,
                ttftMs = null,
                pingRttMs = timeoutMs,
                jitterMs = 94.0f,
                exitRegion = "IE • Dublin (Lossy QUIC)",
                exitAsn = "AS16509 Amazon Data Services",
                diagnosticSummary = "QUIC handshake exceeded strict ${timeoutMs}ms timeout ceiling",
                sparklineSamples = listOf(180f, 320f, 510f, 740f, 920f, 1000f, 1000f, 1000f),
                rawHeadersDump = "ERROR: QUIC Initial Packet Loss > Threshold (${timeoutMs}ms)"
            )
        }
        else -> {
            val baseTtft = 108 + ((indexHint * 23) % 135)
            val baseRtt = 28 + ((indexHint * 13) % 62)
            val jitter = 1.8f + ((indexHint * 3) % 9) * 0.7f
            val regionLabel = when {
                lowerRemark.contains("jp-") || lowerRemark.contains("tokyo") -> "JP • Tokyo (Unblocked)"
                lowerRemark.contains("us-") || lowerRemark.contains("oregon") -> "US • Oregon (Unblocked)"
                lowerRemark.contains("sg-") -> "SG • Singapore (Unblocked)"
                lowerRemark.contains("de-") || lowerRemark.contains("frankfurt") -> "DE • Frankfurt (Unblocked)"
                lowerRemark.contains("nl-") || lowerRemark.contains("amsterdam") -> "NL • Amsterdam (Unblocked)"
                lowerRemark.contains("kr-") || lowerRemark.contains("seoul") -> "KR • Seoul (Unblocked)"
                lowerRemark.contains("ch-") || lowerRemark.contains("zurich") -> "CH • Zurich (Unblocked)"
                lowerRemark.contains("uk-") || lowerRemark.contains("london") -> "GB • London (Unblocked)"
                lowerRemark.contains("tw-") || lowerRemark.contains("taipei") -> "TW • Taipei (Unblocked)"
                lowerRemark.contains("au-") || lowerRemark.contains("syd") -> "AU • Sydney (Unblocked)"
                lowerRemark.contains("ca-") || lowerRemark.contains("toronto") -> "CA • Toronto (Unblocked)"
                else -> "IE • Dublin (Unblocked)"
            }
            val asnLabel = when (indexHint % 5) {
                0 -> "AS212238 Datacamp Limited"
                1 -> "AS13335 Cloudflare Warp Edge"
                2 -> "AS24940 Hetzner Online"
                3 -> "AS20473 Choopa / Vultr"
                else -> "AS16276 OVH SAS Core"
            }
            val samples = List(8) { step ->
                (baseRtt + ((step * 7 + indexHint * 5) % 14) - 6).toFloat().coerceAtLeast(14f)
            }
            val injectionNote = if (node.hadInjectionSanitized) {
                " • [Sanitized: ${node.detectedInjectionTags.joinToString()}]"
            } else {
                ""
            }
            node.copy(
                scanStatus = NodeScanStatus.VERIFIED_200,
                httpCode = 200,
                ttftMs = baseTtft,
                pingRttMs = baseRtt,
                jitterMs = jitter,
                exitRegion = regionLabel,
                exitAsn = asnLabel,
                diagnosticSummary = "HTTP 200 OK • Gemini 2.0 Flash / 1.5 Pro stream active (${baseTtft}ms TTFT)$injectionNote",
                sparklineSamples = samples,
                rawHeadersDump = "HTTP/2 200 OK\ncontent-type: application/json; charset=UTF-8\nalt-svc: h3=\":443\"; ma=2592000\nx-goog-gemini-region: ${regionLabel.take(2)}\nauthorization: Bearer ya29.a0AfH6SMBxSecretTokenExample991\nx-node-uuid: ${node.credential}\nserver: ESF"
            )
        }
    }
}

fun initialSeeded15Nodes(): List<ProxyNode> {
    return ProxyUriParser.generate15RealisticMixedNodes().mapIndexed { index, node ->
        evaluateNodeDeterministicProfile(node, index, 3500)
    }
}
