package com.example.ui.components

import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.SaveAlt
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.audio.SciFiSoundEngine
import com.example.model.ShieldUiState
import com.example.security.AuditExportFormat
import com.example.security.OwaspComplianceItem
import com.example.security.SavedExportFileRecord
import com.example.security.SecurityHardeningManager
import com.example.security.SessionTelemetryExporter
import com.example.ui.theme.ElectricPurple
import com.example.ui.theme.LocalNodeGuardColors
import com.example.ui.theme.NeonAmber
import com.example.ui.theme.NeonCrimson
import com.example.ui.theme.NeonCyan
import com.example.ui.theme.NeonEmerald
import com.example.ui.theme.RoyalSapphire
import com.example.ui.theme.spatialGlassCard
import kotlinx.coroutines.launch

/**
 * SessionTelemetryExportCard:
 * Interactive AppSec Lab component that exports the session's diagnostic telemetry data
 * (Shield Radar, 4-stage pipeline, Subscription Scanner stats, GitHub Actions sync, and Error Boundary logs)
 * alongside full Security Audit results (Cryptographic Vault, 5-stage Fuzzing Suite, OWASP M1-M10)
 * as a formatted JSON (`.json`) or Markdown (`.md`) file for local storage access.
 */
@Composable
fun SessionTelemetryExportCard(
    uiState: ShieldUiState,
    owaspItems: List<OwaspComplianceItem>,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val palette = LocalNodeGuardColors.current
    val scope = rememberCoroutineScope()

    var selectedFormat by remember { mutableStateOf(AuditExportFormat.JSON) }
    var showPreview by remember { mutableStateOf(false) }
    var savedFiles by remember { mutableStateOf<List<SavedExportFileRecord>>(emptyList()) }
    var lastExportStatusMessage by remember {
        mutableStateOf("Ready • Zero-permission local storage (`nodeguard_audits/`) & SAF Document exporter")
    }
    var isSaving by remember { mutableStateOf(false) }

    val formattedPayload by remember(
        uiState.probeStatus,
        uiState.telemetry,
        uiState.stages,
        uiState.scannerNodes,
        uiState.fuzzTestCases,
        uiState.diagnosticReports,
        uiState.redactSensitiveLogs,
        uiState.latestWorkflowRunNumber,
        uiState.actionRunStatus,
        selectedFormat
    ) {
        derivedStateOf {
            SessionTelemetryExporter.buildExportPayload(
                uiState = uiState,
                owaspItems = owaspItems,
                format = selectedFormat
            )
        }
    }

    // Load existing saved local export files on initial mount
    LaunchedEffect(Unit) {
        savedFiles = SessionTelemetryExporter.listSavedLocalExports(context)
    }

    // System Storage Access Framework (SAF) CreateDocument launcher for JSON
    val createJsonDocumentLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri != null) {
            scope.launch {
                val payload = SessionTelemetryExporter.buildExportPayload(
                    uiState = uiState,
                    owaspItems = owaspItems,
                    format = AuditExportFormat.JSON
                )
                val res = SessionTelemetryExporter.writeToDocumentUri(context, uri, payload)
                res.onSuccess { byteCount ->
                    SciFiSoundEngine.playCopyLaser(context)
                    lastExportStatusMessage = "Saved JSON export ($byteCount bytes) to selected device folder"
                    Toast.makeText(
                        context,
                        "Exported JSON telemetry ($byteCount bytes) to local storage",
                        Toast.LENGTH_SHORT
                    ).show()
                }.onFailure { err ->
                    lastExportStatusMessage = "SAF export error: ${err.message}"
                }
            }
        }
    }

    // System Storage Access Framework (SAF) CreateDocument launcher for Markdown
    val createMarkdownDocumentLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("text/markdown")
    ) { uri ->
        if (uri != null) {
            scope.launch {
                val payload = SessionTelemetryExporter.buildExportPayload(
                    uiState = uiState,
                    owaspItems = owaspItems,
                    format = AuditExportFormat.MARKDOWN
                )
                val res = SessionTelemetryExporter.writeToDocumentUri(context, uri, payload)
                res.onSuccess { byteCount ->
                    SciFiSoundEngine.playCopyLaser(context)
                    lastExportStatusMessage = "Saved Markdown export ($byteCount bytes) to selected device folder"
                    Toast.makeText(
                        context,
                        "Exported Markdown report ($byteCount bytes) to local storage",
                        Toast.LENGTH_SHORT
                    ).show()
                }.onFailure { err ->
                    lastExportStatusMessage = "SAF export error: ${err.message}"
                }
            }
        }
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .spatialGlassCard(cornerRadius = 20.dp, accentTint = NeonCyan)
            .padding(18.dp)
            .testTag("session_telemetry_export_card"),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // 1. Header Row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(NeonCyan.copy(alpha = 0.14f))
                        .border(0.85.dp, NeonCyan.copy(alpha = 0.55f), RoundedCornerShape(12.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Filled.SaveAlt,
                        contentDescription = "Export Diagnostic Telemetry & Security Audit",
                        tint = NeonCyan,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        text = "SESSION DIAGNOSTIC TELEMETRY & AUDIT EXPORTER",
                        style = MaterialTheme.typography.titleSmall,
                        color = palette.textPrimary,
                        fontWeight = FontWeight.ExtraBold
                    )
                    Text(
                        text = "Export Shield, Scanner, Fuzz Suite & OWASP Audit to Local Storage (.json / .md)",
                        style = MaterialTheme.typography.labelSmall,
                        color = palette.textSecondary
                    )
                }
            }

            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(
                        if (uiState.redactSensitiveLogs) NeonEmerald.copy(alpha = 0.16f) else NeonAmber.copy(alpha = 0.16f)
                    )
                    .border(
                        0.75.dp,
                        if (uiState.redactSensitiveLogs) NeonEmerald else NeonAmber,
                        RoundedCornerShape(8.dp)
                    )
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            ) {
                Text(
                    text = if (uiState.redactSensitiveLogs) "PII REDACTED" else "RAW EXPORT",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (uiState.redactSensitiveLogs) NeonEmerald else NeonAmber,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        // 2. Format Selector Row (Formatted JSON vs Formatted Markdown) + Preview Toggle
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AuditExportFormat.entries.forEach { format ->
                val isSelected = selectedFormat == format
                val chipColor by animateColorAsState(
                    targetValue = if (format == AuditExportFormat.JSON) NeonCyan else ElectricPurple,
                    animationSpec = tween(220),
                    label = "export_format_chip_color_${format.id}"
                )

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .minimumInteractiveComponentSize()
                        .clip(RoundedCornerShape(12.dp))
                        .background(
                            if (isSelected) chipColor.copy(alpha = 0.18f) else palette.logoEdgeBackground.copy(alpha = 0.78f)
                        )
                        .border(
                            width = if (isSelected) 1.3.dp else 0.75.dp,
                            color = if (isSelected) chipColor else palette.cardBorder,
                            shape = RoundedCornerShape(12.dp)
                        )
                        .clickable {
                            SciFiSoundEngine.playHoloClick()
                            selectedFormat = format
                        }
                        .padding(horizontal = 12.dp, vertical = 10.dp)
                        .testTag("export_format_chip_${format.id}"),
                    contentAlignment = Alignment.Center
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = if (format == AuditExportFormat.JSON) Icons.Filled.Code else Icons.Filled.Description,
                            contentDescription = format.displayLabel,
                            tint = if (isSelected) chipColor else palette.textSecondary,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = format.displayLabel,
                            style = MaterialTheme.typography.labelMedium,
                            color = if (isSelected) palette.textPrimary else palette.textSecondary,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                        )
                    }
                }
            }

            Box(
                modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .clip(RoundedCornerShape(12.dp))
                    .background(palette.surfaceBright)
                    .border(0.75.dp, palette.cardBorder, RoundedCornerShape(12.dp))
                    .clickable {
                        SciFiSoundEngine.playHoloClick()
                        showPreview = !showPreview
                    }
                    .padding(horizontal = 12.dp, vertical = 10.dp)
                    .testTag("toggle_export_preview_button"),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = if (showPreview) "Hide Preview" else "Preview",
                    style = MaterialTheme.typography.labelSmall,
                    color = NeonCyan,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        // 3. Expandable Live Formatted JSON / Markdown Preview Box
        AnimatedVisibility(
            visible = showPreview,
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(palette.logoEdgeBackground.copy(alpha = 0.88f))
                    .border(0.75.dp, palette.cardBorder, RoundedCornerShape(14.dp))
                    .padding(12.dp)
                    .testTag("export_payload_preview_box"),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "LIVE ${selectedFormat.displayLabel.uppercase()} PAYLOAD (${formattedPayload.toByteArray().size} bytes)",
                        style = MaterialTheme.typography.labelSmall,
                        color = NeonCyan,
                        fontWeight = FontWeight.Bold
                    )

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(NeonCyan.copy(alpha = 0.14f))
                            .clickable {
                                SciFiSoundEngine.playCopyLaser(context)
                                copyExportPayloadToClipboard(
                                    context = context,
                                    label = "Gemini_NodeGuard_Audit.${selectedFormat.fileExtension}",
                                    payload = formattedPayload
                                )
                            }
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                            .testTag("copy_export_preview_button")
                    ) {
                        Icon(
                            imageVector = Icons.Filled.ContentCopy,
                            contentDescription = "Copy Formatted Payload",
                            tint = NeonCyan,
                            modifier = Modifier.size(13.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "Copy",
                            style = MaterialTheme.typography.labelSmall,
                            color = NeonCyan,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 120.dp, max = 220.dp)
                        .verticalScroll(rememberScrollState())
                        .horizontalScroll(rememberScrollState())
                ) {
                    Text(
                        text = formattedPayload,
                        style = MaterialTheme.typography.labelSmall,
                        fontFamily = FontFamily.Monospace,
                        color = palette.textPrimary
                    )
                }
            }
        }

        // 4. Primary Local Storage Export Buttons
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Save directly to App Local Storage (`files/nodeguard_audits/`)
            Button(
                onClick = {
                    if (isSaving) return@Button
                    isSaving = true
                    scope.launch {
                        val result = SessionTelemetryExporter.saveToAppLocalStorage(
                            context = context,
                            format = selectedFormat,
                            content = formattedPayload
                        )
                        result.onSuccess { record ->
                            SciFiSoundEngine.playCopyLaser(context)
                            savedFiles = SessionTelemetryExporter.listSavedLocalExports(context)
                            lastExportStatusMessage = "Saved ${record.fileName} (${record.formattedSize}) to local storage"
                            Toast.makeText(
                                context,
                                "Saved ${record.fileName} to local storage",
                                Toast.LENGTH_SHORT
                            ).show()
                        }.onFailure { err ->
                            lastExportStatusMessage = "Local save error: ${err.message}"
                        }
                        isSaving = false
                    }
                },
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = NeonEmerald,
                    contentColor = Color(0xFF04130D)
                ),
                modifier = Modifier
                    .weight(1.15f)
                    .minimumInteractiveComponentSize()
                    .testTag("save_local_storage_export_button")
            ) {
                Icon(
                    imageVector = Icons.Filled.Download,
                    contentDescription = "Save to Local Storage",
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = if (isSaving) "Saving..." else "Save Local (.${selectedFormat.fileExtension})",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            // Save via System File Picker (SAF CreateDocument)
            OutlinedButton(
                onClick = {
                    SciFiSoundEngine.playHoloClick()
                    val defaultName = SessionTelemetryExporter.defaultExportFileName(selectedFormat)
                    if (selectedFormat == AuditExportFormat.JSON) {
                        createJsonDocumentLauncher.launch(defaultName)
                    } else {
                        createMarkdownDocumentLauncher.launch(defaultName)
                    }
                },
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .weight(1f)
                    .minimumInteractiveComponentSize()
                    .testTag("save_as_document_picker_button")
            ) {
                Icon(
                    imageVector = Icons.Filled.FolderOpen,
                    contentDescription = "Save to Device Folder",
                    tint = NeonCyan,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "Save to Folder...",
                    style = MaterialTheme.typography.labelMedium,
                    color = NeonCyan,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        // Status readout strip
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .background(palette.logoEdgeBackground.copy(alpha = 0.76f))
                .border(0.75.dp, palette.cardBorder, RoundedCornerShape(10.dp))
                .padding(horizontal = 10.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                Icon(
                    imageVector = Icons.Filled.CheckCircle,
                    contentDescription = null,
                    tint = NeonEmerald,
                    modifier = Modifier.size(14.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = lastExportStatusMessage,
                    style = MaterialTheme.typography.labelSmall,
                    color = palette.textSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.testTag("export_status_message_text")
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .background(RoyalSapphire.copy(alpha = 0.18f))
                    .clickable {
                        SciFiSoundEngine.playHoloClick()
                        shareFormattedExport(
                            context = context,
                            fileName = SessionTelemetryExporter.defaultExportFileName(selectedFormat),
                            mimeType = selectedFormat.mimeType,
                            payload = formattedPayload
                        )
                    }
                    .padding(horizontal = 8.dp, vertical = 4.dp)
                    .testTag("share_exported_telemetry_button")
            ) {
                Icon(
                    imageVector = Icons.Filled.Share,
                    contentDescription = "Share Exported Telemetry",
                    tint = NeonCyan,
                    modifier = Modifier.size(13.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = "Share",
                    style = MaterialTheme.typography.labelSmall,
                    color = NeonCyan,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        // 5. Saved Local Storage Files List (`files/nodeguard_audits/`)
        if (savedFiles.isNotEmpty()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("saved_local_exports_list"),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = "SAVED LOCAL STORAGE FILES (${savedFiles.size} IN `nodeguard_audits/`)",
                    style = MaterialTheme.typography.labelSmall,
                    color = NeonEmerald,
                    fontWeight = FontWeight.Bold
                )

                savedFiles.forEach { fileRecord ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(palette.logoEdgeBackground.copy(alpha = 0.80f))
                            .border(0.75.dp, palette.cardBorder, RoundedCornerShape(12.dp))
                            .padding(horizontal = 10.dp, vertical = 8.dp)
                            .testTag("saved_export_item_${fileRecord.fileName}"),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(5.dp))
                                        .background(
                                            if (fileRecord.format == AuditExportFormat.JSON) {
                                                NeonCyan.copy(alpha = 0.16f)
                                            } else {
                                                ElectricPurple.copy(alpha = 0.16f)
                                            }
                                        )
                                        .padding(horizontal = 6.dp, vertical = 2.dp)
                                ) {
                                    Text(
                                        text = fileRecord.format.fileExtension.uppercase(),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = if (fileRecord.format == AuditExportFormat.JSON) NeonCyan else ElectricPurple,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = fileRecord.fileName,
                                    style = MaterialTheme.typography.labelMedium,
                                    color = palette.textPrimary,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            Text(
                                text = "${fileRecord.formattedSize} • ${fileRecord.lastModifiedFormatted}",
                                style = MaterialTheme.typography.labelSmall,
                                color = palette.textMuted
                            )
                        }

                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            IconButton(
                                onClick = {
                                    scope.launch {
                                        val content = SessionTelemetryExporter.readSavedLocalExport(
                                            context = context,
                                            fileName = fileRecord.fileName
                                        )
                                        if (content != null) {
                                            SciFiSoundEngine.playCopyLaser(context)
                                            copyExportPayloadToClipboard(
                                                context = context,
                                                label = fileRecord.fileName,
                                                payload = content
                                            )
                                        }
                                    }
                                },
                                modifier = Modifier.size(34.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.ContentCopy,
                                    contentDescription = "Copy Saved File Content",
                                    tint = NeonCyan,
                                    modifier = Modifier.size(15.dp)
                                )
                            }

                            IconButton(
                                onClick = {
                                    scope.launch {
                                        val deleted = SessionTelemetryExporter.deleteSavedLocalExport(
                                            context = context,
                                            fileName = fileRecord.fileName
                                        )
                                        if (deleted) {
                                            SciFiSoundEngine.playHoloClick()
                                            savedFiles = SessionTelemetryExporter.listSavedLocalExports(context)
                                            lastExportStatusMessage = "Deleted ${fileRecord.fileName} from local storage"
                                        }
                                    }
                                },
                                modifier = Modifier.size(34.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.DeleteForever,
                                    contentDescription = "Delete Saved File",
                                    tint = NeonCrimson,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun copyExportPayloadToClipboard(
    context: Context,
    label: String,
    payload: String
) {
    SecurityHardeningManager.SafeClipboard.copyToClipboard(
        context = context,
        label = label,
        text = payload,
        isSensitive = true
    )
    Toast.makeText(
        context,
        "Copied $label (${payload.toByteArray().size} bytes) to clipboard",
        Toast.LENGTH_SHORT
    ).show()
}

private fun shareFormattedExport(
    context: Context,
    fileName: String,
    mimeType: String,
    payload: String
) {
    SecurityHardeningManager.SafeClipboard.copyToClipboard(
        context = context,
        label = fileName,
        text = payload,
        isSensitive = true
    )
    try {
        val sendIntent = Intent(Intent.ACTION_SEND).apply {
            type = mimeType
            putExtra(Intent.EXTRA_SUBJECT, fileName)
            putExtra(Intent.EXTRA_TEXT, payload)
        }
        val chooser = Intent.createChooser(sendIntent, "Export $fileName").apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(chooser)
    } catch (_: Exception) {
        Toast.makeText(
            context,
            "Copied $fileName to clipboard",
            Toast.LENGTH_SHORT
        ).show()
    }
}
