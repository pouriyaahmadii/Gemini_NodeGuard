package com.example.security

import android.content.Context
import android.net.Uri
import androidx.compose.runtime.Immutable
import com.example.model.ShieldUiState
import com.example.parser.NodeScanStatus
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

@Immutable
enum class AuditExportFormat(
    val id: String,
    val displayLabel: String,
    val fileExtension: String,
    val mimeType: String
) {
    JSON(
        id = "json",
        displayLabel = "JSON (.json)",
        fileExtension = "json",
        mimeType = "application/json"
    ),
    MARKDOWN(
        id = "markdown",
        displayLabel = "Markdown (.md)",
        fileExtension = "md",
        mimeType = "text/markdown"
    )
}

@Immutable
data class SavedExportFileRecord(
    val fileName: String,
    val absolutePath: String,
    val format: AuditExportFormat,
    val sizeBytes: Long,
    val lastModifiedFormatted: String
) {
    val formattedSize: String
        get() = if (sizeBytes >= 1024) {
            String.format(Locale.US, "%.1f KB", sizeBytes / 1024f)
        } else {
            "$sizeBytes B"
        }
}

/**
 * SessionTelemetryExporter:
 * Serializes the session's diagnostic telemetry data (Shield radar, 4-stage pipeline,
 * Subscription Scanner summary, GitHub Actions sync state, and Error Boundary logs)
 * alongside full Security Audit results (Keystore Vault, 5-stage Fuzzing Suite, and OWASP M1-M10)
 * into formatted JSON or Markdown files for local storage access.
 */
object SessionTelemetryExporter {

    private const val EXPORT_DIR_NAME = "nodeguard_audits"

    fun defaultExportFileName(format: AuditExportFormat): String {
        val stamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        return "gemini_nodeguard_audit_$stamp.${format.fileExtension}"
    }

    /**
     * Generates either the formatted JSON (2-space indented) or Markdown export payload
     * from the current [ShieldUiState] and [OwaspComplianceItem] matrix.
     */
    fun buildExportPayload(
        uiState: ShieldUiState,
        owaspItems: List<OwaspComplianceItem>,
        format: AuditExportFormat
    ): String {
        return when (format) {
            AuditExportFormat.JSON -> buildFormattedJsonPayload(uiState, owaspItems)
            AuditExportFormat.MARKDOWN -> buildFormattedMarkdownPayload(uiState, owaspItems)
        }
    }

    /**
     * Builds a comprehensive, sanitized JSON document (`toString(2)`) of session telemetry
     * and security audit results.
     */
    fun buildFormattedJsonPayload(
        uiState: ShieldUiState,
        owaspItems: List<OwaspComplianceItem>
    ): String {
        val nowUtc = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).format(Date())
        val redact = uiState.redactSensitiveLogs

        val root = JSONObject().apply {
            put("schemaVersion", "1.4.0")
            put("application", "Gemini NodeGuard")
            put("exportedAtUtc", nowUtc)
            put("zeroTrustLogRedaction", redact)

            // 1. Live Shield & Edge Telemetry
            val shieldObj = JSONObject().apply {
                put("probeStatus", uiState.probeStatus.name)
                put("probeStatusTitle", uiState.probeStatus.title)
                put("selectedScenario", uiState.selectedScenario.label)
                put("lastProbeTimestamp", uiState.lastProbeTimestamp)
                put("ttftMs", uiState.telemetry.ttftMs)
                put("pingJitterMs", uiState.telemetry.pingJitterMs.toDouble())
                put("packetLossPercent", uiState.telemetry.packetLossPercent.toDouble())
                put("tokenStreamRate", uiState.telemetry.tokenStreamRate)
                put("exitIp", uiState.telemetry.formattedExitIp(redact))
                put("asnOrg", uiState.telemetry.asnOrg)
                put("regionCode", uiState.telemetry.regionCode)
                put("tlsCipherSuite", uiState.telemetry.tlsCipherSuite)
                put("alpnProtocol", uiState.telemetry.alpnProtocol)

                val stagesArray = JSONArray()
                uiState.stages.forEach { stage ->
                    stagesArray.put(
                        JSONObject().apply {
                            put("stepNumber", stage.stepNumber)
                            put("title", stage.title)
                            put("endpointSpec", stage.endpointSpec)
                            put("state", stage.state.name)
                            put("statusBadge", stage.statusBadge)
                            put("diagnosticDetail", stage.diagnosticDetail)
                        }
                    )
                }
                put("validationStages", stagesArray)
            }
            put("shieldDiagnosticTelemetry", shieldObj)

            // 2. Subscription Scanner Telemetry Summary
            val verifiedNodes = uiState.scannerNodes.filter { it.scanStatus == NodeScanStatus.VERIFIED_200 }
            val blockedNodesCount = uiState.scannerNodes.count {
                it.scanStatus == NodeScanStatus.GEO_BLOCKED_403 ||
                    it.scanStatus == NodeScanStatus.REGION_LOCK_400
            }
            val ssrfBlockedCount = uiState.scannerNodes.count {
                it.scanStatus == NodeScanStatus.SSRF_BLOCKED || it.isPrivateOrLoopbackRisk
            }
            val timeoutCount = uiState.scannerNodes.count { it.scanStatus == NodeScanStatus.TIMEOUT }

            val scannerObj = JSONObject().apply {
                put("totalNodesCount", uiState.scannerNodes.size)
                put("verified200Count", verifiedNodes.size)
                put("geoBlocked403Count", blockedNodesCount)
                put("ssrfBlockedCount", ssrfBlockedCount)
                put("timeoutCount", timeoutCount)
                put("concurrencyLimit", uiState.concurrencyLimit)
                put("probeTimeoutMs", uiState.probeTimeoutMs)

                val verifiedArray = JSONArray()
                verifiedNodes.take(15).forEach { node ->
                    verifiedArray.put(
                        JSONObject().apply {
                            put("remark", node.remark)
                            put("protocol", node.protocol.displayName)
                            put("host", node.maskedHost(redact))
                            put("sni", node.maskedSni(redact))
                            put("ttftMs", node.ttftMs ?: 0)
                            put("exitRegion", node.exitRegion)
                        }
                    )
                }
                put("verifiedNodes", verifiedArray)
            }
            put("scannerTelemetry", scannerObj)

            // 3. GitHub Actions Remote-Config Sync Telemetry
            val githubSyncObj = JSONObject().apply {
                put("repositorySlug", uiState.githubRepoSlug)
                put("latestWorkflowRunNumber", uiState.latestWorkflowRunNumber)
                put("workflowRunStatus", uiState.actionRunStatus.displayLabel)
                put("latestCommitHash", uiState.latestActionCommitHash)
                put("latestActionTimestamp", uiState.latestActionTimestamp)
                put("autoSyncEnabled", uiState.autoSyncEnabled)
                put("autoSyncInterval", uiState.autoSyncInterval.label)
                put("activeTargetEndpointsCount", uiState.dynamicEndpointRules.size)
            }
            put("githubActionsTelemetry", githubSyncObj)

            // 4. Security Audit & Fuzzing Results
            val securityObj = JSONObject().apply {
                val vaultObj = JSONObject().apply {
                    put("cipherAlgorithm", uiState.vaultStatus.cipherAlgorithm)
                    put("keystoreProvider", uiState.vaultStatus.keystoreProvider)
                    put("keyAlias", uiState.vaultStatus.keyAlias)
                    put("isHardwareBacked", uiState.vaultStatus.isHardwareBacked)
                    put("hasEncryptedPat", uiState.vaultStatus.hasEncryptedPat)
                    put("flagSecureEnabled", uiState.isFlagSecureEnabled)
                }
                put("cryptographicVault", vaultObj)

                val fuzzArray = JSONArray()
                uiState.fuzzTestCases.forEach { test ->
                    fuzzArray.put(
                        JSONObject().apply {
                            put("testNumber", test.testNumber)
                            put("title", test.title)
                            put("expectedDefense", test.expectedDefense)
                            put("state", test.state.name)
                            put("executionTimeMs", test.executionTimeMs ?: 0L)
                            put("assertionResultDetail", test.assertionResultDetail)
                        }
                    )
                }
                put("automatedFuzzSuite", fuzzArray)

                val owaspArray = JSONArray()
                owaspItems.forEach { item ->
                    owaspArray.put(
                        JSONObject().apply {
                            put("code", item.code)
                            put("title", item.title)
                            put("threatVector", item.threatVector)
                            put("implementedDefense", item.implementedDefense)
                            put("codeReference", item.codeReference)
                            put("score", item.score)
                        }
                    )
                }
                put("owaspMobileTop10Matrix", owaspArray)

                val diagArray = JSONArray()
                uiState.diagnosticReports.forEach { report ->
                    diagArray.put(
                        JSONObject().apply {
                            put("id", report.id)
                            put("timestamp", report.timestamp)
                            put("originModule", report.originModule)
                            put("exceptionClass", report.exceptionClass)
                            put("threadName", report.threadName)
                            put("severity", report.severity)
                            put("isBoundaryRecovered", report.isBoundaryRecovered)
                            put("message", report.getSanitizedMessage(redact))
                            put("stackTracePreview", report.getSanitizedStackTrace(redact))
                        }
                    )
                }
                put("caughtDiagnosticReports", diagArray)
            }
            put("securityAuditResults", securityObj)
        }

        return root.toString(2)
    }

    /**
     * Builds a comprehensive Markdown (`.md`) report containing both session diagnostic telemetry
     * and the full OWASP + Fuzzing + Error Boundary security audit.
     */
    fun buildFormattedMarkdownPayload(
        uiState: ShieldUiState,
        owaspItems: List<OwaspComplianceItem>
    ): String {
        val nowUtc = SimpleDateFormat("yyyy-MM-dd HH:mm:ss 'UTC'", Locale.US).format(Date())
        val redact = uiState.redactSensitiveLogs
        val verifiedNodes = uiState.scannerNodes.filter { it.scanStatus == NodeScanStatus.VERIFIED_200 }
        val passedFuzz = uiState.fuzzTestCases.count { it.state == FuzzTestState.PASSED }

        return buildString {
            appendLine("# Gemini NodeGuard — Session Diagnostic Telemetry & Security Audit")
            appendLine("- **Export Timestamp:** `$nowUtc`")
            appendLine("- **Zero-Trust PII/IP Log Redaction:** `${if (redact) "ENABLED" else "RAW"}`")
            appendLine("- **Window Privacy (`FLAG_SECURE`):** `${if (uiState.isFlagSecureEnabled) "ACTIVE" else "DISABLED"}`")
            appendLine("- **Keystore Vault Cipher:** `${uiState.vaultStatus.cipherAlgorithm}` (`${uiState.vaultStatus.keystoreProvider}`)")
            appendLine()

            appendLine("## 1. Live Shield & Edge Network Telemetry")
            appendLine("- **Probe Status:** `${uiState.probeStatus.title}` (`${uiState.probeStatus.codeLabel}`)")
            appendLine("- **Active Scenario:** `${uiState.selectedScenario.label}`")
            appendLine("- **Gemini TTFT Latency:** `${uiState.telemetry.ttftMs} ms` (${uiState.telemetry.tokenStreamRate})")
            appendLine("- **Ping Jitter / Packet Loss:** `±${uiState.telemetry.pingJitterMs} ms` / `${uiState.telemetry.packetLossPercent}%`")
            appendLine("- **Exit IP & Autonomous System:** `${uiState.telemetry.formattedExitIp(redact)}` • `${uiState.telemetry.asnOrg}` (`${uiState.telemetry.regionCode}`)")
            appendLine("- **TLS Handshake & ALPN:** `${uiState.telemetry.tlsCipherSuite}` • `${uiState.telemetry.alpnProtocol}`")
            appendLine()
            appendLine("### 4-Stage Multi-Tiered Google/Gemini Validation Pipeline")
            appendLine("| Step | Stage | Endpoint | Status | Diagnostic Detail |")
            appendLine("|---|---|---|---|---|")
            uiState.stages.forEach { stage ->
                appendLine("| ${stage.stepNumber} | **${stage.title}** | `${stage.endpointSpec}` | `${stage.statusBadge}` | ${stage.diagnosticDetail} |")
            }
            appendLine()

            appendLine("## 2. Subscription Scanner & GitHub Actions Sync Telemetry")
            appendLine("- **Total Subscription Nodes:** `${uiState.scannerNodes.size}` (`${verifiedNodes.size}` HTTP 200 OK Verified)")
            appendLine("- **Coroutine Concurrency / Timeout:** `${uiState.concurrencyLimit} workers` / `${uiState.probeTimeoutMs} ms`")
            appendLine("- **GitHub Actions Repository:** `${uiState.githubRepoSlug}` (Run `#${uiState.latestWorkflowRunNumber}` • `${uiState.actionRunStatus.displayLabel}`)")
            appendLine("- **Latest Action Commit & Timestamp:** `${uiState.latestActionCommitHash}` at `${uiState.latestActionTimestamp}`")
            appendLine()

            appendLine("## 3. Automated Security & Fuzzing Suite ($passedFuzz/${uiState.fuzzTestCases.size} Passed)")
            appendLine("| # | Fuzzing Vector | Status | Execution Time | Assertion Verdict |")
            appendLine("|---|---|---|---|---|")
            uiState.fuzzTestCases.forEach { test ->
                val timeStr = test.executionTimeMs?.let { "${it}ms" } ?: "N/A"
                appendLine("| ${test.testNumber} | ${test.title} | **${test.state.name}** | `$timeStr` | ${test.assertionResultDetail} |")
            }
            appendLine()

            appendLine("## 4. OWASP Mobile Top 10 (M1–M10) Compliance Matrix")
            appendLine("| ID | OWASP Category | Threat Vector | Implemented Defense | Score |")
            appendLine("|---|---|---|---|---|")
            owaspItems.forEach { item ->
                appendLine("| **${item.code}** | ${item.title} | ${item.threatVector} | ${item.implementedDefense} (`${item.codeReference}`) | `${item.score}` |")
            }
            appendLine()

            appendLine("## 5. In-Memory Error Boundary & Diagnostic Reports (${uiState.diagnosticReports.size} Caught)")
            if (uiState.diagnosticReports.isEmpty()) {
                appendLine("_0 Runtime Exceptions Recorded — All Error Boundaries Nominal._")
            } else {
                uiState.diagnosticReports.forEach { report ->
                    appendLine("```text")
                    appendLine(report.toFormattedDiagnosticBlock(redact).trimEnd())
                    appendLine("```")
                }
            }
        }
    }

    /**
     * Resolves the app-specific local storage directory (`files/nodeguard_audits`)
     * that requires zero storage permissions on all Android versions.
     */
    fun resolveLocalExportDirectory(context: Context): File {
        val baseDir = context.getExternalFilesDir(null) ?: context.filesDir
        val exportDir = File(baseDir, EXPORT_DIR_NAME)
        if (!exportDir.exists()) {
            exportDir.mkdirs()
        }
        return exportDir
    }

    /**
     * Writes the formatted JSON or Markdown content to app-local storage (`nodeguard_audits/`)
     * and returns the saved [SavedExportFileRecord].
     */
    suspend fun saveToAppLocalStorage(
        context: Context,
        format: AuditExportFormat,
        content: String,
        customFileName: String? = null
    ): Result<SavedExportFileRecord> = withContext(Dispatchers.IO) {
        runCatching {
            val exportDir = resolveLocalExportDirectory(context)
            val safeName = (customFileName ?: defaultExportFileName(format))
                .replace(Regex("[^a-zA-Z0-9._-]"), "_")
            val targetFile = File(exportDir, safeName)
            targetFile.writeText(content, Charsets.UTF_8)
            toSavedRecord(targetFile)
        }
    }

    /**
     * Writes the formatted JSON or Markdown content to a user-selected document [Uri]
     * returned by `ActivityResultContracts.CreateDocument`.
     */
    suspend fun writeToDocumentUri(
        context: Context,
        uri: Uri,
        content: String
    ): Result<Int> = withContext(Dispatchers.IO) {
        runCatching {
            val bytes = content.toByteArray(Charsets.UTF_8)
            context.contentResolver.openOutputStream(uri)?.use { out ->
                out.write(bytes)
                out.flush()
            } ?: error("Unable to open output stream for selected URI")
            bytes.size
        }
    }

    /**
     * Lists saved `.json` and `.md` export files in local storage, sorted newest first.
     */
    suspend fun listSavedLocalExports(context: Context): List<SavedExportFileRecord> =
        withContext(Dispatchers.IO) {
            runCatching {
                val exportDir = resolveLocalExportDirectory(context)
                val files = exportDir.listFiles { file ->
                    file.isFile && (file.name.endsWith(".json", ignoreCase = true) || file.name.endsWith(".md", ignoreCase = true))
                } ?: emptyArray()
                files.sortedByDescending { it.lastModified() }
                    .take(10)
                    .map { toSavedRecord(it) }
            }.getOrDefault(emptyList())
        }

    /**
     * Deletes a saved local export file by name.
     */
    suspend fun deleteSavedLocalExport(context: Context, fileName: String): Boolean =
        withContext(Dispatchers.IO) {
            runCatching {
                val exportDir = resolveLocalExportDirectory(context)
                val target = File(exportDir, fileName)
                if (target.exists() && target.parentFile?.canonicalPath == exportDir.canonicalPath) {
                    target.delete()
                } else {
                    false
                }
            }.getOrDefault(false)
        }

    /**
     * Reads the content of a saved local export file for preview/copying.
     */
    suspend fun readSavedLocalExport(context: Context, fileName: String): String? =
        withContext(Dispatchers.IO) {
            runCatching {
                val exportDir = resolveLocalExportDirectory(context)
                val target = File(exportDir, fileName)
                if (target.exists() && target.parentFile?.canonicalPath == exportDir.canonicalPath) {
                    target.readText(Charsets.UTF_8)
                } else {
                    null
                }
            }.getOrNull()
        }

    private fun toSavedRecord(file: File): SavedExportFileRecord {
        val fmt = if (file.name.endsWith(".json", ignoreCase = true)) {
            AuditExportFormat.JSON
        } else {
            AuditExportFormat.MARKDOWN
        }
        val dateStr = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)
            .format(Date(file.lastModified()))
        return SavedExportFileRecord(
            fileName = file.name,
            absolutePath = file.absolutePath,
            format = fmt,
            sizeBytes = file.length(),
            lastModifiedFormatted = dateStr
        )
    }
}
