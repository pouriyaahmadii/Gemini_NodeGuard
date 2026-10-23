package com.example.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.parser.ProxyNode
import com.example.parser.ProxyUriParser
import com.example.ui.theme.DeepObsidian
import com.example.ui.theme.ElectricPurple
import com.example.ui.theme.LocalNodeGuardColors
import com.example.ui.theme.NeonAmber
import com.example.ui.theme.NeonCrimson
import com.example.ui.theme.NeonCyan
import com.example.ui.theme.NeonEmerald

private val ElevatedGlass: Color
    @Composable get() = LocalNodeGuardColors.current.surface

private val ElevatedGlassBright: Color
    @Composable get() = LocalNodeGuardColors.current.surfaceBright

private val GlassBorderSubtle: Color
    @Composable get() = LocalNodeGuardColors.current.cardBorder

private val TextPrimaryFrost: Color
    @Composable get() = LocalNodeGuardColors.current.textPrimary

private val TextSecondarySlate: Color
    @Composable get() = LocalNodeGuardColors.current.textSecondary

private val TextMutedTech: Color
    @Composable get() = LocalNodeGuardColors.current.textMuted

@Composable
fun MiniNodeSparkline(
    samples: List<Float>,
    strokeColor: Color,
    modifier: Modifier = Modifier
) {
    val palette = LocalNodeGuardColors.current
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(palette.logoEdgeBackground.copy(alpha = 0.78f))
            .border(1.dp, strokeColor.copy(alpha = 0.25f), RoundedCornerShape(8.dp))
            .padding(horizontal = 6.dp, vertical = 5.dp)
    ) {
        Spacer(
            modifier = Modifier
                .fillMaxSize()
                .drawWithCache {
                    if (size.width <= 0f || size.height <= 0f || samples.size < 2) {
                        return@drawWithCache onDrawBehind {}
                    }

                    val w = size.width
                    val h = size.height
                    val minVal = (samples.minOrNull() ?: 0f)
                    val maxVal = (samples.maxOrNull() ?: 100f)
                    val span = (maxVal - minVal).coerceAtLeast(10f)
                    val low = (minVal - span * 0.15f).coerceAtLeast(0f)
                    val high = maxVal + span * 0.15f
                    val range = (high - low).coerceAtLeast(1f)

                    val pts = samples.mapIndexed { idx, v ->
                        val x = (idx.toFloat() / (samples.size - 1).toFloat()) * w
                        val normY = ((v - low) / range).coerceIn(0f, 1f)
                        Offset(x, h - normY * h)
                    }

                    val curvePath = Path().apply {
                        moveTo(pts.first().x, pts.first().y)
                        for (i in 0 until pts.size - 1) {
                            val p0 = pts[i]
                            val p1 = pts[i + 1]
                            val cx1 = p0.x + (p1.x - p0.x) * 0.45f
                            val cx2 = p0.x + (p1.x - p0.x) * 0.55f
                            cubicTo(cx1, p0.y, cx2, p1.y, p1.x, p1.y)
                        }
                    }

                    val fillPath = Path().apply {
                        addPath(curvePath)
                        lineTo(pts.last().x, h)
                        lineTo(pts.first().x, h)
                        close()
                    }

                    val fillBrush = Brush.verticalGradient(
                        colors = listOf(
                            strokeColor.copy(alpha = 0.30f),
                            strokeColor.copy(alpha = 0.0f)
                        ),
                        startY = 0f,
                        endY = h.coerceAtLeast(1f)
                    )
                    val lineStroke = Stroke(
                        width = 1.8.dp.toPx(),
                        cap = StrokeCap.Round,
                        join = StrokeJoin.Round
                    )
                    val endDotRadius = 2.2.dp.toPx()
                    val lastPoint = pts.last()

                    onDrawBehind {
                        if (size.width <= 0f || size.height <= 0f) return@onDrawBehind

                        drawPath(
                            path = fillPath,
                            brush = fillBrush
                        )

                        drawPath(
                            path = curvePath,
                            color = strokeColor,
                            style = lineStroke
                        )

                        drawCircle(
                            color = palette.textPrimary,
                            radius = endDotRadius,
                            center = lastPoint
                        )
                    }
                }
        )
    }
}

@Composable
fun ImportNodesDialog(
    onDismiss: () -> Unit,
    onConfirmImport: (String) -> Unit
) {
    val palette = LocalNodeGuardColors.current
    var inputText by remember { mutableStateOf("") }
    val liveParsePreview by remember(inputText) {
        derivedStateOf { ProxyUriParser.parseBulkInput(inputText) }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = palette.surface,
        titleContentColor = palette.textPrimary,
        textContentColor = palette.textSecondary,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Shield,
                    contentDescription = null,
                    tint = palette.primaryAccent,
                    modifier = Modifier.size(22.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = stringResource(id = R.string.dialog_import_title),
                    style = MaterialTheme.typography.titleLarge,
                    color = palette.textPrimary
                )
            }
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "Supports Subscription URLs (https://...), Base64 subscriptions, VLESS, VMess, Trojan, Hysteria2, and TUIC v5.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = palette.textSecondary
                )
                Spacer(modifier = Modifier.height(10.dp))

                OutlinedTextField(
                    value = inputText,
                    onValueChange = { inputText = it },
                    placeholder = {
                        Text(
                            text = stringResource(id = R.string.dialog_import_hint),
                            style = MaterialTheme.typography.labelSmall,
                            color = palette.textMuted
                        )
                    },
                    textStyle = MaterialTheme.typography.labelSmall.copy(color = palette.textPrimary),
                    minLines = 5,
                    maxLines = 8,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = palette.primaryAccent,
                        unfocusedBorderColor = palette.cardBorder,
                        focusedContainerColor = palette.logoEdgeBackground,
                        unfocusedContainerColor = palette.logoEdgeBackground
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("import_nodes_text_field")
                )

                Spacer(modifier = Modifier.height(10.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Valid: ${liveParsePreview.validNodes.size} • SSRF: ${liveParsePreview.ssrfBlockedCount} • Sanitized: ${liveParsePreview.injectionSanitizedCount}",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (liveParsePreview.ssrfBlockedCount > 0) NeonCrimson else if (liveParsePreview.validNodes.isNotEmpty()) NeonEmerald else palette.textMuted
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    TextButton(
                        onClick = {
                            inputText = """
                                vless://91a2b3c4-d5e6-4f7a-8b9c-0d1e2f3a4b5c@tyo-custom-99.nodeguard.io:443?security=reality&sni=dl.google.com&fp=chrome&pbk=mK9xPq2vL8nR4tY6wZ1aB3cD5eF7gH9jK0lM2nO4pQ&sid=6ba85179e3&type=tcp&flow=xtls-rprx-vision#JP-Tokyo-Custom-Reality
                                hysteria2://hy2_custom_pass@sgp-quic-88.nodeguard.io:4433?security=tls&sni=sgp-quic.nodeguard.io&alpn=h3#SG-Singapore-QUIC-Hop
                            """.trimIndent()
                        },
                        modifier = Modifier.testTag("fill_sample_uris_button")
                    ) {
                        Text(
                            text = "Paste Clean Batch",
                            style = MaterialTheme.typography.labelSmall,
                            color = NeonCyan
                        )
                    }

                    TextButton(
                        onClick = {
                            inputText = """
                                vless://bad00001-1111-2222-3333-444455556666@169.254.169.254:80?security=none&sni=metadata.google.internal&type=tcp#SSRF-Cloud-Metadata-Attack
                                trojan://ssrf_local_pass@127.0.0.1:2080?security=tls&sni=localhost&type=tcp#SSRF-Loopback-127.0.0.1
                                vless://inj00002-1111-2222-3333-444455556666@fra-safe-edge.nodeguard.io:443?security=tls&sni=dl.google.com";rm -rf /;&path=../../etc/passwd#Injected-Params-Node
                            """.trimIndent()
                        },
                        modifier = Modifier.testTag("fill_ssrf_test_uris_button")
                    ) {
                        Text(
                            text = "Paste SSRF/Injection Vectors",
                            style = MaterialTheme.typography.labelSmall,
                            color = NeonAmber
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirmImport(inputText) },
                colors = ButtonDefaults.buttonColors(
                    containerColor = NeonCyan,
                    contentColor = DeepObsidian
                ),
                modifier = Modifier.testTag("confirm_import_nodes_button")
            ) {
                Text(
                    text = "PARSE & SCAN (${liveParsePreview.validNodes.size})",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold
                )
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.testTag("dismiss_import_nodes_button")
            ) {
                Text(
                    text = "CANCEL",
                    style = MaterialTheme.typography.labelMedium,
                    color = TextSecondarySlate
                )
            }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NodeRawTelemetryBottomSheet(
    node: ProxyNode,
    redactSensitiveLogs: Boolean,
    onDismiss: () -> Unit,
    onCopyCleanUri: (ProxyNode) -> Unit,
    onShareToClient: (ProxyNode) -> Unit,
    onActivateInShield: (ProxyNode) -> Unit
) {
    val palette = LocalNodeGuardColors.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = palette.surface,
        contentColor = palette.textPrimary,
        modifier = Modifier.testTag("raw_telemetry_bottom_sheet")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Header Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(node.protocol.badgeColor.copy(alpha = 0.20f))
                                .border(1.dp, node.protocol.badgeColor, RoundedCornerShape(6.dp))
                                .padding(horizontal = 8.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = node.protocol.displayName,
                                style = MaterialTheme.typography.labelSmall,
                                color = node.protocol.badgeColor,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = node.remark,
                            style = MaterialTheme.typography.titleLarge,
                            color = palette.textPrimary,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "${node.maskedHost(redactSensitiveLogs)} • SNI: ${node.maskedSni(redactSensitiveLogs)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = palette.primaryAccent
                    )
                }

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(node.scanStatus.badgeColor.copy(alpha = 0.18f))
                        .border(1.dp, node.scanStatus.badgeColor, RoundedCornerShape(8.dp))
                        .padding(horizontal = 10.dp, vertical = 5.dp)
                ) {
                    Text(
                        text = node.scanStatus.badgeText,
                        style = MaterialTheme.typography.labelMedium,
                        color = node.scanStatus.badgeColor,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            // Protocol & Transport Parameter Matrix
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(palette.logoEdgeBackground.copy(alpha = 0.85f))
                    .border(1.dp, palette.cardBorder, RoundedCornerShape(14.dp))
                    .padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = "PARSED PROTOCOL & TLS/REALITY PARAMETERS",
                    style = MaterialTheme.typography.labelSmall,
                    color = palette.primaryAccent,
                    fontWeight = FontWeight.Bold
                )
                TelemetryKeyValueRow("Transport / Security", "${node.transport.uppercase()} + ${node.security.uppercase()}")
                TelemetryKeyValueRow("UUID / Credential", node.maskedCredential(redactSensitiveLogs))
                TelemetryKeyValueRow("uTLS Fingerprint / ALPN", "${node.fingerprint} • [${node.alpn}]")
                if (node.ssrfMatchedRule.isNotBlank()) {
                    TelemetryKeyValueRow("SSRF Firewall Verdict", node.ssrfMatchedRule)
                }
                if (node.hadInjectionSanitized) {
                    TelemetryKeyValueRow("Sanitized Vectors", node.detectedInjectionTags.joinToString(", "))
                }
                if (node.flow.isNotBlank()) {
                    TelemetryKeyValueRow("XTLS Flow", node.flow)
                }
                if (node.realityPublicKey.isNotBlank()) {
                    TelemetryKeyValueRow("Reality Public Key (pbk)", "${node.realityPublicKey.take(12)}… (sid: ${node.realityShortId})")
                }
                TelemetryKeyValueRow("Exit Region / ASN", "${node.exitRegion} • ${node.exitAsn}")
                TelemetryKeyValueRow(
                    "TTFT / Ping RTT / Jitter",
                    "${node.ttftMs?.let { "${it}ms" } ?: "N/A"} / ${node.pingRttMs?.let { "${it}ms" } ?: "N/A"} / ±${node.jitterMs}ms"
                )
            }

            // Raw HTTP / GFE Response Payload Dump (Protected by SafeLogRedactor)
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(palette.logoEdgeBackground.copy(alpha = 0.88f))
                    .border(1.dp, ElectricPurple.copy(alpha = 0.35f), RoundedCornerShape(14.dp))
                    .padding(14.dp)
            ) {
                Text(
                    text = if (redactSensitiveLogs) {
                        "RAW GEMINI RESPONSE HEADERS (SAFELOGREDACTOR ACTIVE)"
                    } else {
                        "RAW GEMINI ENDPOINT RESPONSE HEADERS (UNMASKED)"
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = ElectricPurple,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = node.redactedHeadersDump(redactSensitiveLogs),
                    style = MaterialTheme.typography.labelSmall,
                    color = palette.textSecondary
                )
            }

            // Sing-box 1.10+ Outbound JSON Preview
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(palette.logoEdgeBackground.copy(alpha = 0.88f))
                    .border(1.dp, NeonEmerald.copy(alpha = 0.35f), RoundedCornerShape(14.dp))
                    .padding(14.dp)
            ) {
                Text(
                    text = "SING-BOX 1.10+ SANITIZED OUTBOUND JSON",
                    style = MaterialTheme.typography.labelSmall,
                    color = NeonEmerald,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = node.singBoxOutboundPreviewJson(redactSensitiveLogs),
                    style = MaterialTheme.typography.labelSmall,
                    color = palette.textPrimary,
                    modifier = Modifier.horizontalScroll(rememberScrollState())
                )
            }

            // Bottom Sheet Actions
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedButton(
                    onClick = { onCopyCleanUri(node) },
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                        .testTag("sheet_copy_uri_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.ContentCopy,
                        contentDescription = null,
                        tint = palette.primaryAccent,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "COPY URI",
                        style = MaterialTheme.typography.labelSmall,
                        color = palette.primaryAccent,
                        fontWeight = FontWeight.Bold
                    )
                }

                OutlinedButton(
                    onClick = { onShareToClient(node) },
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                        .testTag("sheet_export_client_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Share,
                        contentDescription = null,
                        tint = NeonEmerald,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "SING-BOX / V2RAY",
                        style = MaterialTheme.typography.labelSmall,
                        color = NeonEmerald,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Button(
                onClick = { onActivateInShield(node) },
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = palette.primaryAccent,
                    contentColor = if (palette.isDark) DeepObsidian else Color.White
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .testTag("sheet_activate_in_shield_button")
            ) {
                Icon(
                    imageVector = Icons.Default.PlayArrow,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "LOAD NODE INTO LIVE SHIELD RADAR",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
private fun TelemetryKeyValueRow(
    key: String,
    value: String
) {
    val palette = LocalNodeGuardColors.current
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = key,
            style = MaterialTheme.typography.labelSmall,
            color = palette.textMuted
        )
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text = value,
            style = MaterialTheme.typography.labelSmall,
            color = palette.textPrimary,
            fontWeight = FontWeight.Medium
        )
    }
}
