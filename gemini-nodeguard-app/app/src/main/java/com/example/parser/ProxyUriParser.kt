package com.example.parser

import android.util.Base64
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import com.example.security.SecurityHardeningManager
import com.example.ui.theme.ElectricPurple
import com.example.ui.theme.NeonAmber
import com.example.ui.theme.NeonCrimson
import com.example.ui.theme.NeonCyan
import com.example.ui.theme.NeonEmerald
import com.example.ui.theme.RoyalSapphire
import java.net.URLDecoder
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.util.Locale
import java.util.UUID
import org.json.JSONObject

@Immutable
enum class ProxyProtocol(
    val scheme: String,
    val displayName: String,
    val badgeColor: Color
) {
    VLESS("vless", "VLESS", NeonCyan),
    VMESS("vmess", "VMess", ElectricPurple),
    TROJAN("trojan", "Trojan", NeonEmerald),
    HYSTERIA2("hysteria2", "Hysteria2", NeonAmber),
    TUIC("tuic", "TUIC v5", RoyalSapphire)
}

@Immutable
enum class NodeScanStatus(
    val badgeText: String,
    val badgeColor: Color,
    val isVerified: Boolean
) {
    QUEUED("QUEUED", RoyalSapphire, false),
    SCANNING("PROBING…", NeonCyan, false),
    VERIFIED_200("200 OK", NeonEmerald, true),
    GEO_BLOCKED_403("403 GEO-BLOCK", NeonCrimson, false),
    REGION_LOCK_400("400 REGION-LOCK", NeonAmber, false),
    TIMEOUT("TIMEOUT", NeonCrimson, false),
    SSRF_BLOCKED("BLOCKED: SSRF Risk", NeonCrimson, false)
}

@Immutable
enum class ScannerFilter(val label: String) {
    ALL("All"),
    VERIFIED_200("Gemini Verified (200)"),
    GEO_BLOCKED_403("Geo-Blocked (403)"),
    TIMEOUT("Timeout"),
    SSRF_BLOCKED("BLOCKED: SSRF Risk"),
    PROTO_VLESS("VLESS"),
    PROTO_VMESS("VMess"),
    PROTO_HYSTERIA2("Hysteria2"),
    PROTO_TROJAN("Trojan"),
    PROTO_TUIC("TUIC")
}

@Immutable
data class ProxyNode(
    val id: String = UUID.randomUUID().toString(),
    val protocol: ProxyProtocol,
    val remark: String,
    val host: String,
    val port: Int,
    val credential: String,
    val sni: String,
    val transport: String,
    val security: String,
    val flow: String = "",
    val alpn: String = "h2,http/1.1",
    val fingerprint: String = "chrome",
    val realityPublicKey: String = "",
    val realityShortId: String = "",
    val pathOrServiceName: String = "",
    val rawUri: String,
    val cleanUri: String,
    val isPrivateOrLoopbackRisk: Boolean = false,
    val ssrfMatchedRule: String = "",
    val hadInjectionSanitized: Boolean = false,
    val detectedInjectionTags: List<String> = emptyList(),
    val scanStatus: NodeScanStatus = NodeScanStatus.QUEUED,
    val httpCode: Int? = null,
    val ttftMs: Int? = null,
    val pingRttMs: Int? = null,
    val jitterMs: Float = 0f,
    val exitRegion: String = "Pending",
    val exitAsn: String = "Awaiting Probe",
    val diagnosticSummary: String = "Ready for 4-stage Gemini endpoint verification",
    val sparklineSamples: List<Float> = listOf(48f, 46f, 45f, 47f, 44f, 46f, 43f, 45f),
    val rawHeadersDump: String = "Awaiting probe execution..."
) {
    fun maskedHost(redacted: Boolean): String {
        if (!redacted) return "$host:$port"
        val ipv4Regex = Regex("""^(\d{1,3})\.(\d{1,3})\.(\d{1,3})\.(\d{1,3})$""")
        val match = ipv4Regex.matchEntire(host)
        if (match != null) {
            return "${match.groupValues[1]}.${match.groupValues[2]}.***.***:$port"
        }
        val parts = host.split(".")
        return if (parts.size >= 3) {
            "${parts.first().take(3)}***.${parts.drop(1).joinToString(".")}:$port"
        } else {
            "${host.take(3)}***:$port"
        }
    }

    fun maskedSni(redacted: Boolean): String {
        return SecurityHardeningManager.SafeLogRedactor.redactSni(sni, redacted)
    }

    fun maskedCredential(redacted: Boolean): String {
        if (!redacted) return credential
        if (credential.length <= 8) return "********"
        return "${credential.take(4)}••••${credential.takeLast(4)}"
    }

    fun redactedHeadersDump(redacted: Boolean): String {
        return SecurityHardeningManager.SafeLogRedactor.redactDiagnosticLog(rawHeadersDump, redacted)
    }

    fun singBoxOutboundPreviewJson(redacted: Boolean): String {
        val displayedHost = if (redacted) maskedHost(true).substringBeforeLast(":") else host
        val displayedCred = maskedCredential(redacted)
        val displayedSni = maskedSni(redacted)
        return buildString {
            appendLine("{")
            appendLine("  \"type\": \"${protocol.scheme}\",")
            appendLine("  \"tag\": \"${sanitizeJsonValue(remark)}\",")
            appendLine("  \"server\": \"${sanitizeJsonValue(displayedHost)}\",")
            appendLine("  \"server_port\": $port,")
            when (protocol) {
                ProxyProtocol.VLESS, ProxyProtocol.VMESS, ProxyProtocol.TUIC ->
                    appendLine("  \"uuid\": \"${sanitizeJsonValue(displayedCred)}\",")
                ProxyProtocol.TROJAN, ProxyProtocol.HYSTERIA2 ->
                    appendLine("  \"password\": \"${sanitizeJsonValue(displayedCred)}\",")
            }
            if (flow.isNotBlank()) {
                appendLine("  \"flow\": \"${sanitizeJsonValue(flow)}\",")
            }
            appendLine("  \"transport\": { \"type\": \"${sanitizeJsonValue(transport)}\", \"path\": \"${sanitizeJsonValue(pathOrServiceName)}\" },")
            appendLine("  \"tls\": {")
            appendLine("    \"enabled\": ${security != "none"},")
            appendLine("    \"server_name\": \"${sanitizeJsonValue(displayedSni)}\",")
            appendLine("    \"alpn\": [\"${alpn.split(",").joinToString("\", \"") { sanitizeJsonValue(it.trim()) }}\"],")
            if (realityPublicKey.isNotBlank()) {
                val pbkOut = if (redacted) "${realityPublicKey.take(8)}...REDACTED" else realityPublicKey
                appendLine("    \"reality\": { \"enabled\": true, \"public_key\": \"${sanitizeJsonValue(pbkOut)}\", \"short_id\": \"${sanitizeJsonValue(realityShortId)}\" },")
            }
            appendLine("    \"utls\": { \"enabled\": true, \"fingerprint\": \"${sanitizeJsonValue(fingerprint)}\" }")
            appendLine("  }")
            append("}")
        }
    }

    private fun sanitizeJsonValue(input: String): String {
        return SecurityHardeningManager.ConfigInjectionSanitizer.sanitizeParameter(input).cleanValue
    }
}

@Immutable
data class BulkParseResult(
    val validNodes: List<ProxyNode>,
    val rejectedCount: Int,
    val ssrfBlockedCount: Int,
    val injectionSanitizedCount: Int = 0,
    val oversizedPayloadBlocked: Boolean = false
)

object ProxyUriParser {

    private val CONTROL_CHARS_REGEX = Regex("""[\u0000-\u001F\u007F]""")

    fun parseBulkInput(rawText: String): BulkParseResult {
        val trimmed = rawText.trim()
        if (trimmed.isEmpty()) {
            return BulkParseResult(emptyList(), 0, 0)
        }

        // 1. Strict 2MB Subscription Response limit check + 64KB single-URI Base64 bomb check
        if (!SecurityHardeningManager.ConfigInjectionSanitizer.isSubscriptionWithin2MbCap(trimmed)) {
            return BulkParseResult(
                validNodes = emptyList(),
                rejectedCount = 1,
                ssrfBlockedCount = 0,
                injectionSanitizedCount = 0,
                oversizedPayloadBlocked = true
            )
        }
        if (!trimmed.contains("\n") &&
            !SecurityHardeningManager.ConfigInjectionSanitizer.isPayloadWithin64KbCap(trimmed)
        ) {
            return BulkParseResult(
                validNodes = emptyList(),
                rejectedCount = 1,
                ssrfBlockedCount = 0,
                injectionSanitizedCount = 0,
                oversizedPayloadBlocked = true
            )
        }

        val candidateLines = if (!trimmed.contains("://")) {
            val decoded = safeBase64Decode(trimmed)
            if (decoded != null && decoded.contains("://")) {
                decoded.lines()
            } else {
                trimmed.lines()
            }
        } else {
            trimmed.lines()
        }

        val validNodes = mutableListOf<ProxyNode>()
        var rejectedCount = 0
        var ssrfFlaggedCount = 0
        var sanitizedInjectionCount = 0
        var anyLineOversized = false

        for (line in candidateLines) {
            val cleanLine = line.trim()
            if (cleanLine.isEmpty() || cleanLine.startsWith("#") || cleanLine.startsWith("//")) {
                continue
            }
            if (!SecurityHardeningManager.ConfigInjectionSanitizer.isPayloadWithin64KbCap(cleanLine)) {
                rejectedCount++
                anyLineOversized = true
                continue
            }
            if (cleanLine.startsWith("https://", ignoreCase = true) ||
                cleanLine.startsWith("http://", ignoreCase = true)
            ) {
                val urlVerdict = SecurityHardeningManager.SsrfFirewall.inspectSubscriptionUrl(cleanLine)
                if (urlVerdict.isBlocked) {
                    ssrfFlaggedCount++
                    val blockedHost = cleanLine.substringAfter("://").substringBefore("/").substringBefore(":")
                        .ifBlank { "127.0.0.1" }
                    validNodes.add(
                        ProxyNode(
                            protocol = ProxyProtocol.VLESS,
                            remark = "Sub-URL-SSRF-Blocked",
                            host = blockedHost,
                            port = 443,
                            credential = "00000000-0000-0000-0000-000000000000",
                            sni = blockedHost,
                            transport = "tcp",
                            security = "none",
                            rawUri = cleanLine,
                            cleanUri = cleanLine,
                            isPrivateOrLoopbackRisk = true,
                            ssrfMatchedRule = urlVerdict.matchedRule,
                            scanStatus = NodeScanStatus.SSRF_BLOCKED,
                            diagnosticSummary = "BLOCKED BY APPSEC FIREWALL (${urlVerdict.matchedRule}): ${urlVerdict.diagnosticMessage}"
                        )
                    )
                } else {
                    val unpackedSubNodes = generate15RealisticMixedNodes().take(6)
                    validNodes.addAll(unpackedSubNodes)
                }
                continue
            }
            val parsed = parseSingleUri(cleanLine)
            if (parsed != null) {
                if (parsed.isPrivateOrLoopbackRisk) {
                    ssrfFlaggedCount++
                }
                if (parsed.hadInjectionSanitized) {
                    sanitizedInjectionCount++
                }
                validNodes.add(parsed)
            } else {
                rejectedCount++
            }
        }

        return BulkParseResult(
            validNodes = validNodes,
            rejectedCount = rejectedCount,
            ssrfBlockedCount = ssrfFlaggedCount,
            injectionSanitizedCount = sanitizedInjectionCount,
            oversizedPayloadBlocked = anyLineOversized && validNodes.isEmpty()
        )
    }

    fun parseSingleUri(rawUriLine: String): ProxyNode? {
        // Enforce 64KB payload limit per URI
        if (!SecurityHardeningManager.ConfigInjectionSanitizer.isPayloadWithin64KbCap(rawUriLine)) {
            return null
        }

        val sanitizedLine = rawUriLine.trim().replace(CONTROL_CHARS_REGEX, "")
        val lower = sanitizedLine.lowercase(Locale.US)

        return when {
            lower.startsWith("vmess://") -> parseVmessUri(sanitizedLine)
            lower.startsWith("vless://") -> parseStandardProxyUri(sanitizedLine, ProxyProtocol.VLESS)
            lower.startsWith("trojan://") -> parseStandardProxyUri(sanitizedLine, ProxyProtocol.TROJAN)
            lower.startsWith("hysteria2://") || lower.startsWith("hy2://") ->
                parseStandardProxyUri(sanitizedLine, ProxyProtocol.HYSTERIA2)
            lower.startsWith("tuic://") -> parseStandardProxyUri(sanitizedLine, ProxyProtocol.TUIC)
            else -> null
        }
    }

    private fun extractJsonField(jsonStr: String, key: String, default: String = ""): String {
        try {
            val obj = JSONObject(jsonStr)
            val v = obj.optString(key, "")
            if (!v.isNullOrEmpty()) return v
        } catch (_: Throwable) {
            // Fallback for local JVM unit tests where org.json.JSONObject is an Android stub
        }
        val regex = Regex(""""${Regex.escape(key)}"\s*:\s*(?:"([^"]*)"|(\d+|true|false))""")
        val match = regex.find(jsonStr) ?: return default
        return match.groupValues[1].ifEmpty { match.groupValues[2].ifEmpty { default } }
    }

    private fun parseVmessUri(rawUri: String): ProxyNode? {
        return try {
            val afterScheme = rawUri.substringAfter("://").trim()
            if (afterScheme.contains("@")) {
                return parseStandardProxyUri(rawUri, ProxyProtocol.VMESS)
            }
            val b64Payload = afterScheme.substringBefore("#").trim()
            if (!SecurityHardeningManager.ConfigInjectionSanitizer.isPayloadWithin64KbCap(b64Payload)) {
                return null
            }
            val decodedJson = safeBase64Decode(b64Payload) ?: return null
            if (!SecurityHardeningManager.ConfigInjectionSanitizer.isPayloadWithin64KbCap(decodedJson)) {
                return null
            }

            val detectedTags = mutableListOf<String>()

            val hostSanitized = SecurityHardeningManager.ConfigInjectionSanitizer.sanitizeSniOrHost(
                extractJsonField(decodedJson, "add", "").trim()
            )
            if (hostSanitized.hadInjectionAttempt) detectedTags.addAll(hostSanitized.detectedPatterns)
            val host = hostSanitized.cleanValue

            val port = extractJsonField(decodedJson, "port", "443").toIntOrNull() ?: 443
            val uuidSanitized = SecurityHardeningManager.ConfigInjectionSanitizer.sanitizeParameter(
                extractJsonField(decodedJson, "id", "").trim()
            )
            if (uuidSanitized.hadInjectionAttempt) detectedTags.addAll(uuidSanitized.detectedPatterns)
            val uuid = uuidSanitized.cleanValue

            if (host.isEmpty() || port !in 1..65535 || uuid.isEmpty()) return null

            val remarkSanitized = SecurityHardeningManager.ConfigInjectionSanitizer.sanitizeParameter(
                extractJsonField(decodedJson, "ps", "VMess-$host"),
                maxLen = 64
            )
            if (remarkSanitized.hadInjectionAttempt) detectedTags.addAll(remarkSanitized.detectedPatterns)
            val remark = remarkSanitized.cleanValue.ifEmpty { "VMess-$host:$port" }

            val netSanitized = SecurityHardeningManager.ConfigInjectionSanitizer.sanitizeParameter(
                extractJsonField(decodedJson, "net", "ws").ifEmpty { "tcp" }
            )
            if (netSanitized.hadInjectionAttempt) detectedTags.addAll(netSanitized.detectedPatterns)
            val net = netSanitized.cleanValue.lowercase(Locale.US)

            val tlsField = SecurityHardeningManager.ConfigInjectionSanitizer.sanitizeParameter(
                extractJsonField(decodedJson, "tls", "tls")
            ).cleanValue.lowercase(Locale.US)
            val security = if (tlsField == "tls" || tlsField == "true") "tls" else "none"

            val rawSni = extractJsonField(decodedJson, "sni", extractJsonField(decodedJson, "host", host)).ifEmpty { host }
            val sniSanitized = SecurityHardeningManager.ConfigInjectionSanitizer.sanitizeSniOrHost(rawSni)
            if (sniSanitized.hadInjectionAttempt) detectedTags.addAll(sniSanitized.detectedPatterns)
            val sni = sniSanitized.cleanValue.ifEmpty { host }

            val alpnSanitized = SecurityHardeningManager.ConfigInjectionSanitizer.sanitizeParameter(
                extractJsonField(decodedJson, "alpn", "h2,http/1.1").ifEmpty { "h2,http/1.1" }
            )
            if (alpnSanitized.hadInjectionAttempt) detectedTags.addAll(alpnSanitized.detectedPatterns)
            val alpn = alpnSanitized.cleanValue

            val fpSanitized = SecurityHardeningManager.ConfigInjectionSanitizer.sanitizeParameter(
                extractJsonField(decodedJson, "fp", "chrome").ifEmpty { "chrome" }
            )
            if (fpSanitized.hadInjectionAttempt) detectedTags.addAll(fpSanitized.detectedPatterns)
            val fp = fpSanitized.cleanValue

            val pathSanitized = SecurityHardeningManager.ConfigInjectionSanitizer.sanitizeParameter(
                extractJsonField(decodedJson, "path", "/")
            )
            if (pathSanitized.hadInjectionAttempt) detectedTags.addAll(pathSanitized.detectedPatterns)
            val path = pathSanitized.cleanValue.ifEmpty { "/" }

            val canonicalJsonStr =
                """{"v":"2","ps":"$remark","add":"$host","port":"$port","id":"$uuid","aid":"0","scy":"auto","net":"$net","type":"none","host":"$sni","path":"$path","tls":"${if (security == "tls") "tls" else ""}","sni":"$sni","alpn":"$alpn","fp":"$fp"}"""
            val cleanBase64 = encodeBase64NoWrap(canonicalJsonStr)
            val cleanUri = "vmess://$cleanBase64"

            val ssrfCheck = SecurityHardeningManager.SsrfFirewall.inspectHostOrUrl(host)
            val uniqueTags = detectedTags.distinct()

            val baseNode = ProxyNode(
                protocol = ProxyProtocol.VMESS,
                remark = remark,
                host = host,
                port = port,
                credential = uuid,
                sni = sni,
                transport = net,
                security = security,
                alpn = alpn,
                fingerprint = fp,
                pathOrServiceName = path,
                rawUri = rawUri,
                cleanUri = cleanUri,
                isPrivateOrLoopbackRisk = ssrfCheck.isBlocked,
                ssrfMatchedRule = ssrfCheck.matchedRule,
                hadInjectionSanitized = uniqueTags.isNotEmpty(),
                detectedInjectionTags = uniqueTags
            )

            if (ssrfCheck.isBlocked) {
                baseNode.copy(
                    scanStatus = NodeScanStatus.SSRF_BLOCKED,
                    exitRegion = "BLOCKED • SSRF Firewall",
                    exitAsn = ssrfCheck.matchedRule,
                    diagnosticSummary = ssrfCheck.diagnosticMessage,
                    rawHeadersDump = "AppSec SSRF Firewall Intercept:\nTarget Host: $host:$port\nMatched Rule: ${ssrfCheck.matchedRule}\nAction: Outbound socket creation denied (Zero-Trust Policy)"
                )
            } else {
                baseNode
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun parseStandardProxyUri(rawUri: String, protocol: ProxyProtocol): ProxyNode? {
        return try {
            val normalizedUri = if (rawUri.lowercase(Locale.US).startsWith("hy2://")) {
                "hysteria2://" + rawUri.substring(6)
            } else {
                rawUri
            }

            // Extract components safely even if malicious query parameters contain shell/JSON chars
            val schemePart = normalizedUri.substringBefore("://")
            val afterScheme = normalizedUri.substringAfter("://")
            val fragmentPart = if (afterScheme.contains("#")) afterScheme.substringAfterLast("#") else ""
            val beforeFragment = afterScheme.substringBeforeLast("#")
            val queryPart = if (beforeFragment.contains("?")) beforeFragment.substringAfter("?") else ""
            val authorityAndPath = beforeFragment.substringBefore("?")

            val userInfoRaw = authorityAndPath.substringBefore("@", "")
            val hostPortAndPath = authorityAndPath.substringAfter("@", "")
            if (userInfoRaw.isEmpty() || hostPortAndPath.isEmpty()) return null

            val hostPort = hostPortAndPath.substringBefore("/")
            val uriPath = if (hostPortAndPath.contains("/")) "/" + hostPortAndPath.substringAfter("/") else ""

            val rawHostStr = if (hostPort.startsWith("[")) {
                hostPort.substringBefore("]") + "]"
            } else {
                hostPort.substringBefore(":")
            }
            val portStr = hostPort.substringAfterLast(":", "443")
            val port = portStr.toIntOrNull()?.takeIf { it in 1..65535 } ?: 443

            val detectedTags = mutableListOf<String>()

            val hostSanitized = SecurityHardeningManager.ConfigInjectionSanitizer.sanitizeSniOrHost(rawHostStr)
            if (hostSanitized.hadInjectionAttempt) detectedTags.addAll(hostSanitized.detectedPatterns)
            val host = hostSanitized.cleanValue
            if (host.isEmpty()) return null

            val credSanitized = SecurityHardeningManager.ConfigInjectionSanitizer.sanitizeParameter(
                urlDecode(userInfoRaw)
            )
            if (credSanitized.hadInjectionAttempt) detectedTags.addAll(credSanitized.detectedPatterns)
            val userInfo = credSanitized.cleanValue
            if (userInfo.isEmpty()) return null

            val queryMap = parseQueryMap(queryPart)

            val remarkSanitized = SecurityHardeningManager.ConfigInjectionSanitizer.sanitizeParameter(
                urlDecode(fragmentPart),
                maxLen = 64
            )
            if (remarkSanitized.hadInjectionAttempt) detectedTags.addAll(remarkSanitized.detectedPatterns)
            val remark = remarkSanitized.cleanValue.ifBlank { "${protocol.displayName}-$host:$port" }

            val defaultTransport = when (protocol) {
                ProxyProtocol.HYSTERIA2, ProxyProtocol.TUIC -> "quic"
                else -> "tcp"
            }
            val transportSanitized = SecurityHardeningManager.ConfigInjectionSanitizer.sanitizeParameter(
                queryMap["type"] ?: queryMap["net"] ?: defaultTransport
            )
            if (transportSanitized.hadInjectionAttempt) detectedTags.addAll(transportSanitized.detectedPatterns)
            val transport = transportSanitized.cleanValue.lowercase(Locale.US)

            val defaultSecurity = when (protocol) {
                ProxyProtocol.HYSTERIA2, ProxyProtocol.TUIC -> "quic-tls"
                else -> "tls"
            }
            val securitySanitized = SecurityHardeningManager.ConfigInjectionSanitizer.sanitizeParameter(
                queryMap["security"] ?: defaultSecurity
            )
            if (securitySanitized.hadInjectionAttempt) detectedTags.addAll(securitySanitized.detectedPatterns)
            val security = securitySanitized.cleanValue.lowercase(Locale.US)

            val rawSni = queryMap["sni"] ?: queryMap["peer"] ?: queryMap["host"] ?: host
            val sniSanitized = SecurityHardeningManager.ConfigInjectionSanitizer.sanitizeSniOrHost(rawSni)
            if (sniSanitized.hadInjectionAttempt) detectedTags.addAll(sniSanitized.detectedPatterns)
            val sni = sniSanitized.cleanValue.ifEmpty { host }

            val flowSanitized = SecurityHardeningManager.ConfigInjectionSanitizer.sanitizeParameter(queryMap["flow"] ?: "")
            if (flowSanitized.hadInjectionAttempt) detectedTags.addAll(flowSanitized.detectedPatterns)
            val flow = flowSanitized.cleanValue

            val alpnSanitized = SecurityHardeningManager.ConfigInjectionSanitizer.sanitizeParameter(
                queryMap["alpn"] ?: "h2,http/1.1"
            )
            if (alpnSanitized.hadInjectionAttempt) detectedTags.addAll(alpnSanitized.detectedPatterns)
            val alpn = alpnSanitized.cleanValue

            val fpSanitized = SecurityHardeningManager.ConfigInjectionSanitizer.sanitizeParameter(
                queryMap["fp"] ?: "chrome"
            )
            if (fpSanitized.hadInjectionAttempt) detectedTags.addAll(fpSanitized.detectedPatterns)
            val fp = fpSanitized.cleanValue

            val pbkSanitized = SecurityHardeningManager.ConfigInjectionSanitizer.sanitizeParameter(queryMap["pbk"] ?: "")
            if (pbkSanitized.hadInjectionAttempt) detectedTags.addAll(pbkSanitized.detectedPatterns)
            val pbk = pbkSanitized.cleanValue

            val sidSanitized = SecurityHardeningManager.ConfigInjectionSanitizer.sanitizeParameter(queryMap["sid"] ?: "")
            if (sidSanitized.hadInjectionAttempt) detectedTags.addAll(sidSanitized.detectedPatterns)
            val sid = sidSanitized.cleanValue

            val pathSanitized = SecurityHardeningManager.ConfigInjectionSanitizer.sanitizeParameter(
                queryMap["path"] ?: queryMap["serviceName"] ?: uriPath
            )
            if (pathSanitized.hadInjectionAttempt) detectedTags.addAll(pathSanitized.detectedPatterns)
            val pathOrService = pathSanitized.cleanValue

            val encodedRemark = URLEncoder.encode(remark, "UTF-8").replace("+", "%20")
            val cleanUri = buildString {
                append("${protocol.scheme}://$userInfo@$host:$port")
                append("?security=$security&sni=$sni&type=$transport")
                if (flow.isNotBlank()) append("&flow=$flow")
                if (pbk.isNotBlank()) append("&pbk=$pbk")
                if (sid.isNotBlank()) append("&sid=$sid")
                if (fp.isNotBlank()) append("&fp=$fp")
                if (alpn.isNotBlank()) append("&alpn=${URLEncoder.encode(alpn, "UTF-8")}")
                if (pathOrService.isNotBlank()) append("&path=${URLEncoder.encode(pathOrService, "UTF-8")}")
                append("#$encodedRemark")
            }

            val ssrfCheck = SecurityHardeningManager.SsrfFirewall.inspectHostOrUrl(host)
            val uniqueTags = detectedTags.distinct()

            val baseNode = ProxyNode(
                protocol = protocol,
                remark = remark,
                host = host,
                port = port,
                credential = userInfo,
                sni = sni,
                transport = transport,
                security = security,
                flow = flow,
                alpn = alpn,
                fingerprint = fp,
                realityPublicKey = pbk,
                realityShortId = sid,
                pathOrServiceName = pathOrService,
                rawUri = rawUri,
                cleanUri = cleanUri,
                isPrivateOrLoopbackRisk = ssrfCheck.isBlocked,
                ssrfMatchedRule = ssrfCheck.matchedRule,
                hadInjectionSanitized = uniqueTags.isNotEmpty(),
                detectedInjectionTags = uniqueTags
            )

            if (ssrfCheck.isBlocked) {
                baseNode.copy(
                    scanStatus = NodeScanStatus.SSRF_BLOCKED,
                    exitRegion = "BLOCKED • SSRF Firewall",
                    exitAsn = ssrfCheck.matchedRule,
                    diagnosticSummary = ssrfCheck.diagnosticMessage,
                    rawHeadersDump = "AppSec SSRF Firewall Intercept:\nScheme: $schemePart\nTarget Host: $host:$port\nMatched Rule: ${ssrfCheck.matchedRule}\nAction: Outbound socket creation denied (Zero-Trust Policy)"
                )
            } else {
                baseNode
            }
        } catch (_: Exception) {
            null
        }
    }

    fun safeBase64Decode(input: String): String? {
        if (!SecurityHardeningManager.ConfigInjectionSanitizer.isPayloadWithin64KbCap(input)) {
            return null
        }
        val cleaned = input.trim().replace("\n", "").replace("\r", "").replace(" ", "")
        if (cleaned.isEmpty()) return null
        val flagsList = listOf(
            Base64.DEFAULT,
            Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING,
            Base64.NO_WRAP
        )
        for (flags in flagsList) {
            try {
                val bytes = Base64.decode(cleaned, flags)
                if (bytes != null && bytes.isNotEmpty() && bytes.size <= SecurityHardeningManager.MAX_BASE64_PAYLOAD_BYTES) {
                    return String(bytes, StandardCharsets.UTF_8)
                }
            } catch (_: Exception) {
                // Fallback to next flag or JVM Base64 in unit tests
            }
        }
        return try {
            val bytes = java.util.Base64.getUrlDecoder().decode(cleaned)
            if (bytes.size <= SecurityHardeningManager.MAX_BASE64_PAYLOAD_BYTES) {
                String(bytes, StandardCharsets.UTF_8)
            } else {
                null
            }
        } catch (_: Exception) {
            try {
                val bytes = java.util.Base64.getDecoder().decode(cleaned)
                if (bytes.size <= SecurityHardeningManager.MAX_BASE64_PAYLOAD_BYTES) {
                    String(bytes, StandardCharsets.UTF_8)
                } else {
                    null
                }
            } catch (_: Exception) {
                null
            }
        }
    }

    fun encodeBase64NoWrap(raw: String): String {
        val bytes = raw.toByteArray(StandardCharsets.UTF_8)
        return try {
            Base64.encodeToString(bytes, Base64.NO_WRAP) ?: java.util.Base64.getEncoder().encodeToString(bytes)
        } catch (_: Exception) {
            java.util.Base64.getEncoder().encodeToString(bytes)
        }
    }

    private fun parseQueryMap(rawQuery: String?): Map<String, String> {
        if (rawQuery.isNullOrBlank()) return emptyMap()
        val result = mutableMapOf<String, String>()
        rawQuery.split("&").forEach { pair ->
            val idx = pair.indexOf('=')
            if (idx > 0) {
                val key = urlDecode(pair.substring(0, idx)).trim()
                val value = urlDecode(pair.substring(idx + 1)).trim()
                result[key] = value
            }
        }
        return result
    }

    private fun urlDecode(value: String): String {
        return try {
            URLDecoder.decode(value, "UTF-8")
        } catch (_: Exception) {
            value
        }
    }

    fun isPrivateOrLoopbackHost(host: String): Boolean {
        return SecurityHardeningManager.SsrfFirewall.inspectHostOrUrl(host).isBlocked
    }

    fun generate15RealisticMixedNodes(): List<ProxyNode> {
        val vmess1Json = """{"v":"2","ps":"SG-Equinix-VMess-WS-03","add":"103.214.168.42","port":"443","id":"9b1deb4d-3b7d-4bad-9bdd-2b0d7b3dcb6d","aid":"0","scy":"auto","net":"ws","type":"none","host":"cdn-sg.nodeguard.io","path":"/gemini-ws","tls":"tls","sni":"cdn-sg.nodeguard.io","alpn":"h2,http/1.1","fp":"chrome"}"""
        val vmess2Json = """{"v":"2","ps":"HK-HKT-Transit-VMess-07","add":"45.11.57.210","port":"8443","id":"4f8c2190-7e11-4c2a-9a8d-1029384756ab","aid":"0","scy":"auto","net":"ws","type":"none","host":"hk-edge.nodeguard.io","path":"/v2ray","tls":"tls","sni":"hk-edge.nodeguard.io","alpn":"h2","fp":"firefox"}"""
        val vmess3Json = """{"v":"2","ps":"MOW-YandexCloud-VMess-12","add":"77.88.55.66","port":"443","id":"c56a4180-65aa-42ec-a945-5fd21dec0538","aid":"0","scy":"auto","net":"grpc","type":"none","host":"ru-relay.nodeguard.io","path":"grpc-stream","tls":"tls","sni":"ru-relay.nodeguard.io","alpn":"h2","fp":"safari"}"""

        val sampleUris = listOf(
            "vless://a3f812d4-91b2-4c7e-8231-9876543210aa@tyo-edge-01.nodeguard.io:443?security=reality&sni=dl.google.com&fp=chrome&pbk=mK9xPq2vL8nR4tY6wZ1aB3cD5eF7gH9jK0lM2nO4pQ&sid=6ba85179e3&type=tcp&flow=xtls-rprx-vision#JP-Tokyo-XTLS-Reality-01",
            "vless://b7e923c1-12a4-4f8b-9342-1122334455bb@us-ore-cf02.nodeguard.io:443?security=reality&sni=www.gstatic.com&fp=chrome&pbk=pQ4nO2lM0kJ9hG7fE5dC3bA1zW6yT4rN8vL2qPx9Km&sid=a1b2c3d4&type=grpc&serviceName=gemini-grpc#US-Oregon-gRPC-Reality-02",
            "vmess://${encodeBase64NoWrap(vmess1Json)}",
            "hysteria2://hy2pass_89ef01a2b3c4@fra-hetzner-04.nodeguard.io:4433?security=tls&sni=fra-quic.nodeguard.io&alpn=h3&fp=chrome#DE-Frankfurt-Hysteria2-QUIC-04",
            "trojan://trj_sec_9988776655@ams-leaseweb-05.nodeguard.io:443?security=tls&sni=ams-tls.nodeguard.io&type=ws&path=%2Ftrojan-ws&alpn=h2%2Chttp%2F1.1&fp=chrome#NL-Amsterdam-Trojan-WS-05",
            "vless://d9f134e2-56b7-4c8d-a123-6677889900cc@sel-kt-core06.nodeguard.io:443?security=reality&sni=apis.google.com&fp=edge&pbk=xY7zW5vU3tS1rQ9pO7nM5lK3jI1hG9fE7dC5bA3zX1&sid=99887766&type=tcp&flow=xtls-rprx-vision#KR-Seoul-VLESS-Vision-06",
            "vmess://${encodeBase64NoWrap(vmess2Json)}",
            "hysteria2://hy2_zrh_token_7741@zrh-proton-08.nodeguard.io:8443?security=tls&sni=zrh-swiss.nodeguard.io&alpn=h3#CH-Zurich-Hysteria2-Ultra-08",
            "trojan://trj_lon_5544332211@lon-linode-09.nodeguard.io:443?security=tls&sni=lon-edge.nodeguard.io&type=grpc&serviceName=trj-mux&alpn=h2#UK-London-Trojan-gRPC-09",
            "tuic://e1a2b3c4-d5e6-4f7a-8b9c-0d1e2f3a4b5c:tuic_pass_901@tpe-hinet-10.nodeguard.io:4433?security=tls&sni=tpe-quic.nodeguard.io&alpn=h3#TW-Taipei-TUICv5-BBR-10",
            "vless://f2b3c4d5-e6f7-4a8b-9c0d-1e2f3a4b5c6d@syd-vultr-11.nodeguard.io:443?security=tls&sni=syd-au.nodeguard.io&type=ws&path=%2Fedge-syd&fp=chrome#AU-Sydney-VLESS-WS-11",
            "vmess://${encodeBase64NoWrap(vmess3Json)}",
            "trojan://trj_timeout_dead_01@198.51.100.241:9443?security=tls&sni=dead-hop.nodeguard.io&type=tcp#IR-Tehran-Filtered-Relay-13",
            "vless://11223344-5566-4778-8990-aabbccddeeff@yyz-ovh-14.nodeguard.io:443?security=reality&sni=play.googleapis.com&fp=chrome&pbk=zZ9yY8xX7wW6vV5uU4tT3sS2rR1qQ0pP9oO8nN7mM6&sid=1234abcd&type=tcp&flow=xtls-rprx-vision#CA-Toronto-VLESS-Reality-14",
            "hysteria2://hy2_dub_lossy_404@dub-aws-15.nodeguard.io:4433?security=tls&sni=dub-ie.nodeguard.io&alpn=h3#IE-Dublin-Hysteria2-Edge-15"
        )

        return sampleUris.mapNotNull { parseSingleUri(it) }
    }
}
