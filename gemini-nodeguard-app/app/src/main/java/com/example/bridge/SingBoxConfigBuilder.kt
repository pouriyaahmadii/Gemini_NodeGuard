package com.example.bridge

import androidx.compose.runtime.Immutable
import com.example.parser.ProxyNode
import com.example.parser.ProxyProtocol

@Immutable
data class SingBoxConfigOptions(
    val dohServerUrl: String = "https://1.1.1.1/dns-query",
    val enableRemoteDnsGuard: Boolean = true,
    val enableTunInbound: Boolean = false,
    val enableStrictRoute: Boolean = true,
    val redactSecretsInPreview: Boolean = true
)

object SingBoxConfigBuilder {

    val GEMINI_EXPLICIT_DOMAINS = listOf(
        "generativelanguage.googleapis.com",
        "gemini.google.com",
        "aistudio.google.com",
        "colab.research.google.com",
        "alkalimakersuite-pa.clients6.google.com",
        "makersuite.google.com",
        "ai.google.dev",
        "proactive-pa.googleapis.com"
    )

    fun buildCompleteSingBoxConfig(
        primaryNode: ProxyNode,
        allVerifiedNodes: List<ProxyNode>,
        options: SingBoxConfigOptions
    ): String {
        val redacted = options.redactSecretsInPreview
        val cleanHost = if (redacted) {
            primaryNode.maskedHost(true).substringBeforeLast(":")
        } else {
            primaryNode.host
        }
        val cleanCred = primaryNode.maskedCredential(redacted)
        val dnsStrategy = if (options.enableRemoteDnsGuard) "prefer_ipv4" else "ipv4_only"
        val autoGroupTags = (listOf(primaryNode) + allVerifiedNodes)
            .distinctBy { it.remark }
            .take(6)
            .map { escapeJson(it.remark) }

        return buildString {
            appendLine("{")
            appendLine("  \"log\": {")
            appendLine("    \"disabled\": false,")
            appendLine("    \"level\": \"info\",")
            appendLine("    \"timestamp\": true")
            appendLine("  },")

            // 1. DNS-over-HTTPS with Remote DNS Strategy to prevent DNS leaks
            appendLine("  \"dns\": {")
            appendLine("    \"servers\": [")
            appendLine("      {")
            appendLine("        \"tag\": \"remote-doh-dns\",")
            appendLine("        \"address\": \"${escapeJson(options.dohServerUrl)}\",")
            appendLine("        \"address_resolver\": \"bootstrap-dns\",")
            appendLine("        \"strategy\": \"$dnsStrategy\",")
            appendLine("        \"detour\": \"gemini-proxy\"")
            appendLine("      },")
            appendLine("      {")
            appendLine("        \"tag\": \"bootstrap-dns\",")
            appendLine("        \"address\": \"223.5.5.5\",")
            appendLine("        \"strategy\": \"ipv4_only\",")
            appendLine("        \"detour\": \"direct\"")
            appendLine("      },")
            appendLine("      {")
            appendLine("        \"tag\": \"dns-block\",")
            appendLine("        \"address\": \"rcode://success\"")
            appendLine("      }")
            appendLine("    ],")
            appendLine("    \"rules\": [")
            appendLine("      {")
            appendLine("        \"outbound\": \"any\",")
            appendLine("        \"server\": \"bootstrap-dns\"")
            appendLine("      },")
            appendLine("      {")
            appendLine("        \"domain\": [")
            GEMINI_EXPLICIT_DOMAINS.forEachIndexed { idx, domain ->
                val comma = if (idx == GEMINI_EXPLICIT_DOMAINS.lastIndex) "" else ","
                appendLine("          \"$domain\"$comma")
            }
            appendLine("        ],")
            appendLine("        \"domain_suffix\": [")
            appendLine("          \".googleapis.com\",")
            appendLine("          \".google.dev\"")
            appendLine("        ],")
            appendLine("        \"server\": \"remote-doh-dns\",")
            appendLine("        \"disable_cache\": false")
            appendLine("      }")
            appendLine("    ],")
            appendLine("    \"final\": \"remote-doh-dns\",")
            appendLine("    \"strategy\": \"$dnsStrategy\",")
            appendLine("    \"independent_cache\": true")
            appendLine("  },")

            // 2. Inbounds: Local Mixed SOCKS5/HTTP (127.0.0.1:2080) + optional TUN
            appendLine("  \"inbounds\": [")
            appendLine("    {")
            appendLine("      \"type\": \"mixed\",")
            appendLine("      \"tag\": \"mixed-in\",")
            appendLine("      \"listen\": \"127.0.0.1\",")
            appendLine("      \"listen_port\": 2080,")
            appendLine("      \"sniff\": true,")
            appendLine("      \"sniff_override_destination\": ${options.enableRemoteDnsGuard}")
            if (options.enableTunInbound) {
                appendLine("    },")
                appendLine("    {")
                appendLine("      \"type\": \"tun\",")
                appendLine("      \"tag\": \"tun-in\",")
                appendLine("      \"interface_name\": \"nodeguard-tun0\",")
                appendLine("      \"inet4_address\": \"172.19.0.1/30\",")
                appendLine("      \"mtu\": 9000,")
                appendLine("      \"auto_route\": true,")
                appendLine("      \"strict_route\": ${options.enableStrictRoute},")
                appendLine("      \"stack\": \"system\",")
                appendLine("      \"sniff\": true,")
                appendLine("      \"sniff_override_destination\": true")
                appendLine("    }")
            } else {
                appendLine("    }")
            }
            appendLine("  ],")

            // 3. Outbounds: Primary Verified Gemini Node + URLTest Group + Direct/DNS
            appendLine("  \"outbounds\": [")
            appendLine("    {")
            appendLine("      \"type\": \"selector\",")
            appendLine("      \"tag\": \"gemini-proxy\",")
            appendLine("      \"outbounds\": [")
            appendLine("        \"gemini-urltest\",")
            autoGroupTags.forEachIndexed { idx, tag ->
                val comma = if (idx == autoGroupTags.lastIndex) "" else ","
                appendLine("        \"$tag\"$comma")
            }
            appendLine("      ],")
            appendLine("      \"default\": \"${escapeJson(primaryNode.remark)}\"")
            appendLine("    },")
            appendLine("    {")
            appendLine("      \"type\": \"urltest\",")
            appendLine("      \"tag\": \"gemini-urltest\",")
            appendLine("      \"outbounds\": [")
            autoGroupTags.forEachIndexed { idx, tag ->
                val comma = if (idx == autoGroupTags.lastIndex) "" else ","
                appendLine("        \"$tag\"$comma")
            }
            appendLine("      ],")
            appendLine("      \"url\": \"https://generativelanguage.googleapis.com/v1beta/models\",")
            appendLine("      \"interval\": \"3m\",")
            appendLine("      \"tolerance\": 50")
            appendLine("    },")

            // Primary selected node outbound block
            appendLine("    {")
            appendLine("      \"type\": \"${primaryNode.protocol.scheme}\",")
            appendLine("      \"tag\": \"${escapeJson(primaryNode.remark)}\",")
            appendLine("      \"server\": \"${escapeJson(cleanHost)}\",")
            appendLine("      \"server_port\": ${primaryNode.port},")
            when (primaryNode.protocol) {
                ProxyProtocol.VLESS, ProxyProtocol.VMESS, ProxyProtocol.TUIC ->
                    appendLine("      \"uuid\": \"${escapeJson(cleanCred)}\",")
                ProxyProtocol.TROJAN, ProxyProtocol.HYSTERIA2 ->
                    appendLine("      \"password\": \"${escapeJson(cleanCred)}\",")
            }
            if (primaryNode.flow.isNotBlank()) {
                appendLine("      \"flow\": \"${escapeJson(primaryNode.flow)}\",")
            }
            appendLine("      \"transport\": {")
            appendLine("        \"type\": \"${escapeJson(primaryNode.transport)}\",")
            appendLine("        \"path\": \"${escapeJson(primaryNode.pathOrServiceName.ifEmpty { "/" })}\"")
            appendLine("      },")
            appendLine("      \"tls\": {")
            appendLine("        \"enabled\": ${primaryNode.security != "none"},")
            appendLine("        \"server_name\": \"${escapeJson(primaryNode.sni)}\",")
            val alpnItems = primaryNode.alpn.split(",").map { it.trim() }.filter { it.isNotEmpty() }
            appendLine("        \"alpn\": [${alpnItems.joinToString(", ") { "\"${escapeJson(it)}\"" }}],")
            if (primaryNode.realityPublicKey.isNotBlank()) {
                val pbkDisplay = if (redacted) "${primaryNode.realityPublicKey.take(12)}...REDACTED" else primaryNode.realityPublicKey
                appendLine("        \"reality\": {")
                appendLine("          \"enabled\": true,")
                appendLine("          \"public_key\": \"${escapeJson(pbkDisplay)}\",")
                appendLine("          \"short_id\": \"${escapeJson(primaryNode.realityShortId)}\"")
                appendLine("        },")
            }
            appendLine("        \"utls\": {")
            appendLine("          \"enabled\": true,")
            appendLine("          \"fingerprint\": \"${escapeJson(primaryNode.fingerprint)}\"")
            appendLine("        }")
            appendLine("      }")
            appendLine("    },")
            appendLine("    {")
            appendLine("      \"type\": \"direct\",")
            appendLine("      \"tag\": \"direct\"")
            appendLine("    },")
            appendLine("    {")
            appendLine("      \"type\": \"dns\",")
            appendLine("      \"tag\": \"dns-out\"")
            appendLine("    },")
            appendLine("    {")
            appendLine("      \"type\": \"block\",")
            appendLine("      \"tag\": \"block\"")
            appendLine("    }")
            appendLine("  ],")

            // 4. Explicit Gemini AI, AI Studio & Google Colab Routing Rules
            appendLine("  \"route\": {")
            appendLine("    \"auto_detect_interface\": true,")
            appendLine("    \"rules\": [")
            appendLine("      {")
            appendLine("        \"protocol\": \"dns\",")
            appendLine("        \"outbound\": \"dns-out\"")
            appendLine("      },")
            appendLine("      {")
            appendLine("        \"domain\": [")
            GEMINI_EXPLICIT_DOMAINS.forEachIndexed { idx, domain ->
                val comma = if (idx == GEMINI_EXPLICIT_DOMAINS.lastIndex) "" else ","
                appendLine("          \"$domain\"$comma")
            }
            appendLine("        ],")
            appendLine("        \"domain_suffix\": [")
            appendLine("          \".googleapis.com\",")
            appendLine("          \".google.dev\",")
            appendLine("          \".gstatic.com\"")
            appendLine("        ],")
            appendLine("        \"outbound\": \"gemini-proxy\"")
            appendLine("      },")
            appendLine("      {")
            appendLine("        \"ip_is_private\": true,")
            appendLine("        \"outbound\": \"direct\"")
            appendLine("      }")
            appendLine("    ],")
            appendLine("    \"final\": \"gemini-proxy\"")
            appendLine("  }")
            append("}")
        }
    }

    private fun escapeJson(value: String): String {
        return value
            .replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\n", "")
            .replace("\r", "")
    }
}
