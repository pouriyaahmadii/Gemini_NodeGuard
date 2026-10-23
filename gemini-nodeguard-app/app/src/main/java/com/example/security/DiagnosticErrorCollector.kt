package com.example.security

import androidx.compose.runtime.Immutable
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

/**
 * Immutable data model representing a single runtime exception caught by the
 * Gemini NodeGuard Error Boundary or Coroutine/Thread exception interceptor.
 */
@Immutable
data class CaughtDiagnosticReport(
    val id: String = UUID.randomUUID().toString().take(8).uppercase(Locale.US),
    val timestamp: String,
    val originModule: String,
    val exceptionClass: String,
    val message: String,
    val stackTracePreview: String,
    val threadName: String,
    val severity: String = "CRITICAL_CAUGHT",
    val isBoundaryRecovered: Boolean = true
) {
    fun getSanitizedMessage(redactSensitive: Boolean): String {
        return SecurityHardeningManager.SafeLogRedactor.redactDiagnosticLog(message, redactSensitive)
    }

    fun getSanitizedStackTrace(redactSensitive: Boolean): String {
        return SecurityHardeningManager.SafeLogRedactor.redactDiagnosticLog(stackTracePreview, redactSensitive)
    }

    fun toFormattedDiagnosticBlock(redactSensitive: Boolean): String {
        val safeMsg = getSanitizedMessage(redactSensitive)
        val safeStack = getSanitizedStackTrace(redactSensitive)
        return buildString {
            appendLine("=== DIAGNOSTIC REPORT [#$id] ===")
            appendLine("Timestamp      : $timestamp")
            appendLine("Origin Module  : $originModule")
            appendLine("Thread         : $threadName")
            appendLine("Exception Type : $exceptionClass")
            appendLine("Boundary State : ${if (isBoundaryRecovered) "RECOVERED (ISOLATED)" else "UNHANDLED"}")
            appendLine("Message        : $safeMsg")
            appendLine("--- Stack Trace (Top Frames) ---")
            appendLine(safeStack)
        }
    }
}

/**
 * Lightweight, zero-dependency, thread-safe in-memory Error Log Collector & Error Boundary engine.
 * Captures runtime exceptions across Coroutines, UI callbacks, and background workers,
 * automatically scrubs sensitive IPs/UUIDs/PATs via [SecurityHardeningManager.SafeLogRedactor],
 * and exposes live telemetry for the AppSec Lab 'Diagnostic Report' panel.
 */
object DiagnosticErrorCollector {
    private const val MAX_BUFFER_SIZE = 25

    private val _reports = MutableStateFlow<List<CaughtDiagnosticReport>>(emptyList())
    val reports: StateFlow<List<CaughtDiagnosticReport>> = _reports.asStateFlow()

    private val _lastCaughtError = MutableStateFlow<CaughtDiagnosticReport?>(null)
    val lastCaughtError: StateFlow<CaughtDiagnosticReport?> = _lastCaughtError.asStateFlow()

    @Volatile
    private var globalHandlerInstalled = false

    /**
     * Reusable [CoroutineExceptionHandler] that routes uncaught coroutine exceptions
     * into the in-memory diagnostic ring buffer without crashing the process.
     */
    val boundaryCoroutineExceptionHandler = CoroutineExceptionHandler { context, throwable ->
        recordException(
            throwable = throwable,
            originModule = "CoroutineBoundary[${context[kotlinx.coroutines.CoroutineName]?.name ?: "IO-Worker"}]"
        )
    }

    /**
     * Installs a defensive JVM uncaught exception interceptor that records runtime exceptions
     * into the in-memory ring buffer before delegating or recovering.
     */
    fun installGlobalBoundaryHandler() {
        if (globalHandlerInstalled) return
        synchronized(this) {
            if (globalHandlerInstalled) return
            val previousHandler = Thread.getDefaultUncaughtExceptionHandler()
            Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
                runCatching {
                    recordException(
                        throwable = throwable,
                        originModule = "GlobalThreadBoundary[${thread.name}]"
                    )
                }
                // Delegate to previous handler if not a recoverable background worker thread
                if (thread.name.contains("main", ignoreCase = true)) {
                    previousHandler?.uncaughtException(thread, throwable)
                }
            }
            globalHandlerInstalled = true
        }
    }

    /**
     * Executes [block] inside an Error Boundary. If any runtime exception occurs,
     * it is caught, logged to the in-memory Diagnostic Report collector, and [onRecovered] is invoked.
     */
    inline fun <T> runWithErrorBoundary(
        originModule: String,
         crossinline onRecovered: (CaughtDiagnosticReport) -> T,
        block: () -> T
    ): T {
        return try {
            block()
        } catch (t: Throwable) {
            val report = recordException(t, originModule)
            onRecovered(report)
        }
    }

    /**
     * Records a caught [Throwable] into the bounded in-memory ring buffer (max 25 entries).
     */
    fun recordException(
        throwable: Throwable,
        originModule: String,
        severity: String = "BOUNDARY_CAUGHT"
    ): CaughtDiagnosticReport {
        val formatter = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US)
        val timestamp = formatter.format(Date())
        val threadName = Thread.currentThread().name

        val topStackFrames = throwable.stackTrace
            .take(6)
            .joinToString("\n") { element ->
                "  at ${element.className}.${element.methodName}(${element.fileName ?: "UnknownSource"}:${element.lineNumber})"
            }
            .ifBlank { "  at com.example.security.ErrorBoundary.capture(NativeBridge:0)" }

        val rawMessage = throwable.message ?: "${throwable::class.java.simpleName} thrown with null message"

        val report = CaughtDiagnosticReport(
            timestamp = timestamp,
            originModule = originModule,
            exceptionClass = throwable::class.java.name,
            message = rawMessage,
            stackTracePreview = topStackFrames,
            threadName = threadName,
            severity = severity,
            isBoundaryRecovered = true
        )

        _reports.update { current ->
            (listOf(report) + current).take(MAX_BUFFER_SIZE)
        }
        _lastCaughtError.value = report
        return report
    }

    /**
     * Simulates a realistic caught runtime exception inside the Error Boundary so security auditors
     * can verify the Diagnostic Report UI and SafeLogRedactor scrubbing in AppSec Lab.
     */
    fun simulateBoundaryException(presetType: String): CaughtDiagnosticReport {
        return runWithErrorBoundary(
            originModule = when (presetType) {
                "TLS_HANDSHAKE" -> "XrayTlsRealitySocket[ALPN-H2]"
                "JNI_BRIDGE" -> "NodeGuardNativeBridge[UniFFI-Rust]"
                "SUB_PARSER" -> "ProxyUriParser[VMess-JSON]"
                else -> "DynamicActionSync[GitHub-API]"
            },
            onRecovered = { it }
        ) {
            when (presetType) {
                "TLS_HANDSHAKE" -> throw javax.net.ssl.SSLHandshakeException(
                    "REALITY uTLS Chrome_124 handshake aborted by peer 185.220.101.44:443 (UUID=a3f1e9c0-5b12-4d3e-8f9a-112233445566)"
                )
                "JNI_BRIDGE" -> throw UnsatisfiedLinkError(
                    "JNI boundary fault in libnodeguard_rust.so: slice bounds out of range at offset 0x00F4A0 (auth Bearer ghp_SecretToken998877665544332211)"
                )
                "SUB_PARSER" -> throw IllegalArgumentException(
                    "Malformed VMess Base64 frame from 198.51.100.77: unexpected EOF in JSON outbound stream"
                )
                else -> throw IllegalStateException(
                    "Runtime state desync in GithubActionSync worker (endpoint=104.18.22.91, session=9b2d4f8a-1c3e-4a5b-9d8e-7f6a5b4c3d2e)"
                )
            }
        }
    }

    fun dismissBanner() {
        _lastCaughtError.value = null
    }

    fun clearAllReports() {
        _reports.value = emptyList()
        _lastCaughtError.value = null
    }

    fun exportAllDiagnosticReports(redactSensitive: Boolean): String {
        val currentList = _reports.value
        if (currentList.isEmpty()) {
            return "=== GEMINI NODEGUARD DIAGNOSTIC REPORT ===\nStatus: 0 Runtime Exceptions Recorded (All Error Boundaries Nominal)"
        }
        return buildString {
            appendLine("====================================================")
            appendLine("GEMINI NODEGUARD — IN-MEMORY DIAGNOSTIC REPORT")
            appendLine("Total Caught Exceptions : ${currentList.size} / $MAX_BUFFER_SIZE max")
            appendLine("PII / IP Log Redaction  : ${if (redactSensitive) "ENABLED (ZERO-TRUST)" else "RAW"}")
            appendLine("====================================================")
            appendLine()
            currentList.forEach { report ->
                appendLine(report.toFormattedDiagnosticBlock(redactSensitive))
            }
        }
    }
}
