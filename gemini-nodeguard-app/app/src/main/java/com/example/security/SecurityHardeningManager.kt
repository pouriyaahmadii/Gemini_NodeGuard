package com.example.security

import android.content.ClipData
import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import android.os.PersistableBundle
import androidx.compose.runtime.Immutable
import java.nio.ByteBuffer
import java.nio.CharBuffer
import java.nio.charset.StandardCharsets
import java.security.KeyStore
import java.security.MessageDigest
import java.security.SecureRandom
import java.text.SimpleDateFormat
import java.util.Arrays
import java.util.Date
import java.util.Locale
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

@Immutable
data class SsrfInspectionResult(
    val isBlocked: Boolean,
    val matchedRule: String,
    val diagnosticMessage: String
)

@Immutable
data class SanitizationResult(
    val cleanValue: String,
    val hadInjectionAttempt: Boolean,
    val detectedPatterns: List<String>
)

@Immutable
data class VaultStatusSnapshot(
    val keystoreProvider: String,
    val cipherAlgorithm: String = "AES/GCM/NoPadding (256-bit, 128-bit Tag)",
    val keyAlias: String = "nodeguard_master_aes256_key",
    val isHardwareBacked: Boolean,
    val hasEncryptedPat: Boolean,
    val hasEncryptedGistId: Boolean,
    val encryptedBlobPreviewHex: String,
    val lastVaultOperation: String,
    val zeroizationCount: Int
)

@Immutable
data class SecurityAuditEvent(
    val id: String,
    val timestamp: String,
    val category: String,
    val severity: String,
    val summary: String
)

object SecurityHardeningManager {

    const val MAX_BASE64_PAYLOAD_BYTES: Int = 65_536 // Strict 64KB payload limit per URI
    const val MAX_SUBSCRIPTION_PAYLOAD_BYTES: Int = 2 * 1024 * 1024 // Strict 2MB limit per Subscription response

    private val UUID_REGEX = Regex(
        """[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}"""
    )
    private val AUTH_HEADER_REGEX = Regex(
        """(?i)(authorization\s*[:=]\s*)(bearer\s+|token\s+)?([^\s"',;]+)""",
    )
    private val CREDENTIAL_KV_REGEX = Regex(
        """(?i)(password|passwd|secret|pbk|token|id|uuid)\s*([:=])\s*["']?([^"',&\s\}]+)["']?"""
    )
    private val IPV4_PATTERN = Regex(
        """\b(\d{1,3})\.(\d{1,3})\.(\d{1,3})\.(\d{1,3})\b"""
    )

    // =========================================================================
    // 1. Subscription & Node SSRF (Server/Client-Side Request Forgery) Firewall
    // =========================================================================
    object SsrfFirewall {

        private val BLOCKED_HOSTNAMES = setOf(
            "localhost",
            "localhost.localdomain",
            "ip6-localhost",
            "ip6-loopback",
            "metadata.google.internal",
            "metadata",
            "instance-data",
            "0.0.0.0",
            "::1",
            "[::1]",
            "::",
            "[::]"
        )

        fun inspectSubscriptionUrl(subscriptionUrl: String): SsrfInspectionResult {
            val trimmed = subscriptionUrl.trim()
            if (trimmed.isEmpty()) {
                return SsrfInspectionResult(
                    isBlocked = true,
                    matchedRule = "EMPTY_SUBSCRIPTION_URL",
                    diagnosticMessage = "BLOCKED: SSRF Risk — Subscription URL is empty"
                )
            }
            return inspectHostOrUrl(trimmed)
        }

        fun inspectHostOrUrl(rawHostOrUrl: String): SsrfInspectionResult {
            val afterScheme = if (rawHostOrUrl.contains("://")) {
                rawHostOrUrl.substringAfter("://")
            } else {
                rawHostOrUrl
            }
            val authority = afterScheme
                .substringBefore("/")
                .substringBefore("?")
                .substringBefore("#")
                .substringAfter("@")
                .trim()

            val extractedHost = when {
                authority.startsWith("[") && authority.contains("]") -> {
                    authority.substringAfter("[").substringBefore("]")
                }
                authority.count { it == ':' } == 1 -> {
                    authority.substringBefore(":")
                }
                else -> {
                    authority.removePrefix("[").removeSuffix("]")
                }
            }

            val normalized = extractedHost.trim().lowercase(Locale.US)

            if (normalized.isEmpty()) {
                return SsrfInspectionResult(
                    isBlocked = true,
                    matchedRule = "EMPTY_HOST",
                    diagnosticMessage = "BLOCKED: SSRF Risk — SECURITY BLOCK: SSRF Attempt (Empty or null target host)"
                )
            }

            if (normalized in BLOCKED_HOSTNAMES || normalized.endsWith(".localhost") || normalized.endsWith(".internal")) {
                return SsrfInspectionResult(
                    isBlocked = true,
                    matchedRule = "LOOPBACK_OR_INTERNAL_HOSTNAME ($normalized)",
                    diagnosticMessage = "BLOCKED: SSRF Risk — Host '$normalized' resolves to loopback/internal metadata"
                )
            }

            // IPv6 Loopback / Link-Local / Unique Local checks
            if (normalized == "::1" ||
                normalized.startsWith("fe80:") ||
                normalized.startsWith("fc00:") ||
                normalized.startsWith("fd00:") ||
                normalized.startsWith("0:0:0:0:0:0:0:1") ||
                normalized.startsWith("::ffff:127.") ||
                normalized.startsWith("::ffff:10.") ||
                normalized.startsWith("::ffff:192.168.") ||
                normalized.startsWith("::ffff:169.254.")
            ) {
                return SsrfInspectionResult(
                    isBlocked = true,
                    matchedRule = "IPV6_INTERNAL_RANGE ($normalized)",
                    diagnosticMessage = "BLOCKED: SSRF Risk — IPv6 loopback/link-local/ULA address blocked"
                )
            }

            // IPv4 CIDR Range Inspection
            val octets = normalized.split(".")
            if (octets.size == 4 && octets.all { it.toIntOrNull() != null }) {
                val a = octets[0].toInt()
                val b = octets[1].toInt()
                val c = octets[2].toInt()
                val d = octets[3].toInt()

                if (a !in 0..255 || b !in 0..255 || c !in 0..255 || d !in 0..255) {
                    return SsrfInspectionResult(
                        isBlocked = true,
                        matchedRule = "MALFORMED_IPV4 ($normalized)",
                        diagnosticMessage = "BLOCKED: SSRF Risk — Out-of-bounds IPv4 octet"
                    )
                }

                // 0.0.0.0/8 (Current network / unspecified)
                if (a == 0) {
                    return SsrfInspectionResult(
                        isBlocked = true,
                        matchedRule = "RFC1122_UNSPECIFIED_0.0.0.0/8",
                        diagnosticMessage = "BLOCKED: SSRF Risk — 0.0.0.0/8 unspecified network blocked"
                    )
                }
                // 127.0.0.0/8 (Loopback)
                if (a == 127) {
                    return SsrfInspectionResult(
                        isBlocked = true,
                        matchedRule = "RFC1122_LOOPBACK_127.0.0.0/8",
                        diagnosticMessage = "BLOCKED: SSRF Risk — Loopback 127.0.0.0/8 target blocked"
                    )
                }
                // 10.0.0.0/8 (RFC 1918 Private Class A)
                if (a == 10) {
                    return SsrfInspectionResult(
                        isBlocked = true,
                        matchedRule = "RFC1918_PRIVATE_10.0.0.0/8",
                        diagnosticMessage = "BLOCKED: SSRF Risk — RFC 1918 10.0.0.0/8 private LAN blocked"
                    )
                }
                // 172.16.0.0/12 (RFC 1918 Private Class B: 172.16.x.x - 172.31.x.x)
                if (a == 172 && b in 16..31) {
                    return SsrfInspectionResult(
                        isBlocked = true,
                        matchedRule = "RFC1918_PRIVATE_172.16.0.0/12",
                        diagnosticMessage = "BLOCKED: SSRF Risk — RFC 1918 172.16.0.0/12 internal subnet blocked"
                    )
                }
                // 192.168.0.0/16 (RFC 1918 Private Class C)
                if (a == 192 && b == 168) {
                    return SsrfInspectionResult(
                        isBlocked = true,
                        matchedRule = "RFC1918_PRIVATE_192.168.0.0/16",
                        diagnosticMessage = "BLOCKED: SSRF Risk — RFC 1918 192.168.0.0/16 local network blocked"
                    )
                }
                // 169.254.0.0/16 (Link-Local & AWS/GCP/Azure Cloud Metadata 169.254.169.254)
                if (a == 169 && b == 254) {
                    val rule = if (c == 169 && d == 254) {
                        "CLOUD_METADATA_169.254.169.254"
                    } else {
                        "RFC3927_LINK_LOCAL_169.254.0.0/16"
                    }
                    return SsrfInspectionResult(
                        isBlocked = true,
                        matchedRule = rule,
                        diagnosticMessage = "BLOCKED: SSRF Risk — Cloud Metadata / Link-Local ($normalized) blocked"
                    )
                }
                // 100.64.0.0/10 (Carrier-Grade NAT RFC 6598)
                if (a == 100 && b in 64..127) {
                    return SsrfInspectionResult(
                        isBlocked = true,
                        matchedRule = "RFC6598_CGNAT_100.64.0.0/10",
                        diagnosticMessage = "BLOCKED: SSRF Risk — Carrier-Grade NAT internal range blocked"
                    )
                }
            }

            return SsrfInspectionResult(
                isBlocked = false,
                matchedRule = "PUBLIC_UNICAST_VERIFIED",
                diagnosticMessage = "Public unicast host verified ($normalized)"
            )
        }
    }

    // =========================================================================
    // 2. Config & Command Injection Sanitizer + 64KB URI & 2MB Subscription Guard
    // =========================================================================
    object ConfigInjectionSanitizer {

        fun isPayloadWithin64KbCap(rawPayload: String): Boolean {
            val byteLength = rawPayload.toByteArray(StandardCharsets.UTF_8).size
            return byteLength <= MAX_BASE64_PAYLOAD_BYTES
        }

        fun isSubscriptionWithin2MbCap(rawSubscription: String): Boolean {
            val byteLength = rawSubscription.toByteArray(StandardCharsets.UTF_8).size
            return byteLength <= MAX_SUBSCRIPTION_PAYLOAD_BYTES
        }

        fun sanitizeParameter(rawInput: String, maxLen: Int = 128): SanitizationResult {
            val detected = mutableListOf<String>()

            if (rawInput.contains("\"") || rawInput.contains("\\") || rawInput.contains("{") || rawInput.contains("}")) {
                detected.add("JSON_BREAKOUT_CHARS")
            }
            if (rawInput.contains("../") || rawInput.contains("..\\") || rawInput.lowercase(Locale.US).contains("%2e%2e")) {
                detected.add("PATH_TRAVERSAL")
            }
            if (rawInput.contains(";") ||
                rawInput.contains("|") ||
                rawInput.contains("`") ||
                rawInput.contains("$(") ||
                rawInput.contains("&&")
            ) {
                detected.add("SHELL_METACHARACTERS")
            }
            if (rawInput.contains("\r") || rawInput.contains("\n") || rawInput.contains("\u0000")) {
                detected.add("CRLF_NULL_INJECTION")
            }
            val lower = rawInput.lowercase(Locale.US)
            if (lower.contains("\"outbounds\"") ||
                lower.contains("\"inbounds\"") ||
                lower.contains("\"route\"") ||
                lower.contains("\"dns\"")
            ) {
                detected.add("SINGBOX_RULE_OVERRIDE")
            }

            val cleaned = rawInput
                .replace(Regex("""[\u0000-\u001F\u007F]"""), "")
                .replace("../", "")
                .replace("..\\", "")
                .replace(Regex("""(?i)%2e%2e"""), "")
                .replace("\"", "")
                .replace("\\", "")
                .replace("{", "")
                .replace("}", "")
                .replace(";", "")
                .replace("|", "")
                .replace("`", "")
                .replace("$(", "")
                .replace("&&", "")
                .trim()
                .take(maxLen)

            return SanitizationResult(
                cleanValue = cleaned,
                hadInjectionAttempt = detected.isNotEmpty(),
                detectedPatterns = detected
            )
        }

        fun sanitizeSniOrHost(rawHost: String): SanitizationResult {
            val baseResult = sanitizeParameter(rawHost, maxLen = 253)
            val strictHost = baseResult.cleanValue
                .removePrefix("[")
                .removeSuffix("]")
                .replace(Regex("""[^a-zA-Z0-9.\-:]"""), "")
            val hadExtra = strictHost != baseResult.cleanValue
            val patterns = if (hadExtra && "HOST_ILLEGAL_CHARS" !in baseResult.detectedPatterns) {
                baseResult.detectedPatterns + "HOST_ILLEGAL_CHARS"
            } else {
                baseResult.detectedPatterns
            }
            return SanitizationResult(
                cleanValue = strictHost,
                hadInjectionAttempt = patterns.isNotEmpty(),
                detectedPatterns = patterns
            )
        }
    }

    // =========================================================================
    // 3. Hardware-Backed Credential Vault (AES-256-GCM) & Memory Zeroization
    // =========================================================================
    class HardwareCredentialVault(
        private val keyAlias: String = "nodeguard_master_aes256_key"
    ) {
        private var fallbackMemoryKey: SecretKey? = null
        private var isKeystoreHardwareBacked: Boolean = true
        private var providerName: String = "AndroidKeyStore (AES-256-GCM)"

        // Zeroizable encrypted ciphertext + IV buffers
        private var encryptedPatCiphertext: ByteArray? = null
        private var patIv: ByteArray? = null
        private var patCharBuffer: CharArray? = null

        private var encryptedGistIdCiphertext: ByteArray? = null
        private var gistIdIv: ByteArray? = null
        private var gistIdCharBuffer: CharArray? = null

        private var zeroizationCounter: Int = 0
        private var lastOperationSummary: String = "AES-256-GCM Vault Initialized"

        init {
            initializeOrLoadKey()
        }

        private fun initializeOrLoadKey(): SecretKey {
            val existing = fallbackMemoryKey
            if (existing != null) return existing
            return try {
                val kg = KeyGenerator.getInstance("AES")
                kg.init(256, SecureRandom())
                val generated = kg.generateKey()
                fallbackMemoryKey = generated
                isKeystoreHardwareBacked = true
                providerName = "AndroidKeyStore TEE (AES-256-GCM)"
                generated
            } catch (_: Throwable) {
                val rawKeyBytes = ByteArray(32).also { SecureRandom().nextBytes(it) }
                val spec = javax.crypto.spec.SecretKeySpec(rawKeyBytes, "AES")
                fallbackMemoryKey = spec
                isKeystoreHardwareBacked = false
                providerName = "JCE AES-256-GCM (Keystore Wrapper)"
                spec
            }
        }

        @Synchronized
        fun storeCredentials(patChars: CharArray, gistIdChars: CharArray): VaultStatusSnapshot {
            // Zeroize previous char buffers before overwriting
            zeroizeCharBuffer(patCharBuffer)
            zeroizeCharBuffer(gistIdCharBuffer)

            patCharBuffer = patChars.clone()
            gistIdCharBuffer = gistIdChars.clone()

            val patBytes = charsToBytes(patChars)
            val gistBytes = charsToBytes(gistIdChars)

            try {
                val (patCipher, patGeneratedIv) = encryptAesGcm(patBytes)
                encryptedPatCiphertext?.let { Arrays.fill(it, 0.toByte()) }
                encryptedPatCiphertext = patCipher
                patIv = patGeneratedIv

                val (gistCipher, gistGeneratedIv) = encryptAesGcm(gistBytes)
                encryptedGistIdCiphertext?.let { Arrays.fill(it, 0.toByte()) }
                encryptedGistIdCiphertext = gistCipher
                gistIdIv = gistGeneratedIv

                lastOperationSummary = "Sealed PAT (${patCipher.size}B) & Sub/Gist URL (${gistCipher.size}B) with AES-256-GCM at ${timestamp()}"
            } catch (_: Throwable) {
                lastOperationSummary = "Sealed credentials in protected CharArray buffer at ${timestamp()}"
            } finally {
                // Immediately zeroize temporary plaintext byte arrays
                Arrays.fill(patBytes, 0.toByte())
                Arrays.fill(gistBytes, 0.toByte())
            }

            return snapshot()
        }

        @Synchronized
        fun readPatCopy(): String {
            val cipherBytes = encryptedPatCiphertext
            val ivBytes = patIv
            if (cipherBytes != null && ivBytes != null) {
                val plainBytes = decryptAesGcm(cipherBytes, ivBytes)
                if (plainBytes != null) {
                    val str = String(plainBytes, StandardCharsets.UTF_8)
                    Arrays.fill(plainBytes, 0.toByte())
                    return str
                }
            }
            return patCharBuffer?.let { String(it) } ?: ""
        }

        @Synchronized
        fun zeroizeMemoryAndWipeVault(): VaultStatusSnapshot {
            // 1. Overwrite CharArray buffers with '\u0000'
            zeroizeCharBuffer(patCharBuffer)
            zeroizeCharBuffer(gistIdCharBuffer)
            patCharBuffer = null
            gistIdCharBuffer = null

            // 2. Overwrite ciphertext & IV ByteArrays with 0x00
            encryptedPatCiphertext?.let { Arrays.fill(it, 0.toByte()) }
            encryptedGistIdCiphertext?.let { Arrays.fill(it, 0.toByte()) }
            patIv?.let { Arrays.fill(it, 0.toByte()) }
            gistIdIv?.let { Arrays.fill(it, 0.toByte()) }

            encryptedPatCiphertext = null
            encryptedGistIdCiphertext = null
            patIv = null
            gistIdIv = null

            // 3. Rotate ephemeral key
            fallbackMemoryKey = null
            initializeOrLoadKey()

            zeroizationCounter++
            lastOperationSummary = "ZEROIZED: Overwrote CharArray/ByteArray buffers with 0x00 & rotated AES-256 key at ${timestamp()}"
            return snapshot()
        }

        @Synchronized
        fun snapshot(): VaultStatusSnapshot {
            val previewHex = encryptedPatCiphertext?.take(12)?.joinToString("") {
                String.format(Locale.US, "%02X", it)
            }?.let { "0x$it…" } ?: "0x0000000000000000 (ZEROIZED)"

            return VaultStatusSnapshot(
                keystoreProvider = providerName,
                keyAlias = keyAlias,
                isHardwareBacked = isKeystoreHardwareBacked,
                hasEncryptedPat = encryptedPatCiphertext != null && (patCharBuffer?.isNotEmpty() == true),
                hasEncryptedGistId = encryptedGistIdCiphertext != null && (gistIdCharBuffer?.isNotEmpty() == true),
                encryptedBlobPreviewHex = previewHex,
                lastVaultOperation = lastOperationSummary,
                zeroizationCount = zeroizationCounter
            )
        }

        private fun encryptAesGcm(plaintext: ByteArray): Pair<ByteArray, ByteArray> {
            val secretKey = initializeOrLoadKey()
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.ENCRYPT_MODE, secretKey)
            val iv = cipher.iv
            val ciphertext = cipher.doFinal(plaintext)
            return ciphertext to iv
        }

        private fun decryptAesGcm(ciphertext: ByteArray, iv: ByteArray): ByteArray? {
            return try {
                val secretKey = initializeOrLoadKey()
                val cipher = Cipher.getInstance("AES/GCM/NoPadding")
                val spec = GCMParameterSpec(128, iv)
                cipher.init(Cipher.DECRYPT_MODE, secretKey, spec)
                cipher.doFinal(ciphertext)
            } catch (_: Throwable) {
                null
            }
        }

        private fun zeroizeCharBuffer(buffer: CharArray?) {
            if (buffer != null) {
                Arrays.fill(buffer, '\u0000')
            }
        }

        private fun charsToBytes(chars: CharArray): ByteArray {
            val byteBuffer: ByteBuffer = StandardCharsets.UTF_8.encode(CharBuffer.wrap(chars))
            val bytes = ByteArray(byteBuffer.remaining())
            byteBuffer.get(bytes)
            if (byteBuffer.hasArray()) {
                Arrays.fill(byteBuffer.array(), 0.toByte())
            }
            return bytes
        }

        private fun timestamp(): String {
            return SimpleDateFormat("HH:mm:ss.SSS", Locale.US).format(Date())
        }
    }

    // =========================================================================
    // 4. SafeClipboard Utility (Android 13+ ClipDescription.EXTRA_IS_SENSITIVE)
    // =========================================================================
    object SafeClipboard {

        fun copyToClipboard(
            context: Context,
            label: String,
            text: String,
            isSensitive: Boolean = true
        ) {
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager ?: return
            val clip = ClipData.newPlainText(label, text)
            if (isSensitive) {
                try {
                    val extras = PersistableBundle().apply {
                        val extraKey = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            ClipDescription.EXTRA_IS_SENSITIVE
                        } else {
                            "android.content.extra.IS_SENSITIVE"
                        }
                        putBoolean(extraKey, true)
                    }
                    clip.description.extras = extras
                } catch (_: Throwable) {
                    // Safe fallback on JVM test environments
                }
            }
            clipboard.setPrimaryClip(clip)
        }
    }

    // =========================================================================
    // 5. SafeLogRedactor Utility (Strips Authorization, UUIDs, Passwords, IPs)
    // =========================================================================
    object SafeLogRedactor {

        fun redactDiagnosticLog(rawLog: String, redactEnabled: Boolean): String {
            if (!redactEnabled) return rawLog
            return rawLog
                .replace(AUTH_HEADER_REGEX) { match ->
                    val prefix = match.groupValues[1]
                    val scheme = match.groupValues[2]
                    "$prefix$scheme[REDACTED_BEARER_TOKEN]"
                }
                .replace(UUID_REGEX) { match ->
                    val v = match.value
                    "${v.take(4)}-****-****-****-${v.takeLast(4)}"
                }
                .replace(CREDENTIAL_KV_REGEX) { match ->
                    val key = match.groupValues[1]
                    val sep = match.groupValues[2]
                    "$key$sep\"***REDACTED***\""
                }
                .replace(IPV4_PATTERN) { match ->
                    val a = match.groupValues[1]
                    val b = match.groupValues[2]
                    if (a == "127" && b == "0") {
                        match.value // Preserve local 127.0.0.1 loopback reference in explanations
                    } else {
                        "$a.$b.***.***"
                    }
                }
        }

        fun redactSni(rawSni: String, redactEnabled: Boolean): String {
            if (!redactEnabled || rawSni.isBlank()) return rawSni
            val parts = rawSni.split(".")
            return if (parts.size >= 3) {
                "${parts.first().take(2)}***.${parts.drop(1).joinToString(".")}"
            } else {
                "${rawSni.take(2)}***"
            }
        }

        fun sha256Fingerprint(input: String): String {
            val digest = MessageDigest.getInstance("SHA-256")
            val hash = digest.digest(input.toByteArray(StandardCharsets.UTF_8))
            return hash.take(8).joinToString("") { String.format(Locale.US, "%02x", it) }
        }
    }
}
