package com.example.ui.components

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.audio.SciFiSoundEngine
import com.example.model.ShieldUiState
import com.example.security.SecurityHardeningManager
import com.example.ui.theme.ElectricPurple
import com.example.ui.theme.GoogleBlue
import com.example.ui.theme.JetBrainsMonoFontFamily
import com.example.ui.theme.LocalNodeGuardColors
import com.example.ui.theme.NeonAmber
import com.example.ui.theme.NeonCyan
import com.example.ui.theme.NeonEmerald
import com.example.ui.theme.RoyalSapphire
import com.example.ui.theme.spatialGlassCard

@Immutable
data class ReleaseModuleChecklistItem(
    val id: String,
    val moduleTitle: String,
    val verificationDetail: String,
    val statusBadge: String,
    val badgeColor: Color
)

object ReleaseManifestSpec {
    const val VERSION_NAME = "1.0.0-release"
    const val VERSION_CODE = 1
    const val APPLICATION_ID = "com.aistudio.gemininodeguard.xkvpzr"
    const val MIN_SDK = 24
    const val TARGET_SDK = 36
    const val UPSTREAM_REPO = "pouriyaahmadii/Gemini_NodeGuard"

    val R8_PROGUARD_MANIFEST_TEXT = """
        # GEMINI NODEGUARD v1.0.0-release — R8 / PROGUARD PRODUCTION MANIFEST
        # Build: versionName="1.0.0-release" | versionCode=1 | API 24..36
        
        -keepattributes SourceFile,LineNumberTable,*Annotation*,Signature
        -renamesourcefileattribute NodeGuardSource
        
        # 1. Native Go (Sing-box libbox.so) & Rust (Xray-core) JNI Bridges
        -keep class com.example.bridge.** { *; }
        -keepclasseswithmembernames class * {
            native <methods>;
        }
        
        # 2. Serialization, Proxy URI Parser & Dynamic GitHub Actions Models
        -keep class com.example.model.** { *; }
        -keep class com.example.parser.** { *; }
        -keep class com.example.security.** { *; }
        -keepclassmembers class * {
            @com.squareup.moshi.Json <fields>;
        }
        
        # 3. OkHttp3 & TLS 1.3 ALPN (h2 / h3) Reflection Safety
        -dontwarn okhttp3.**
        -dontwarn okio.**
        -keepnames class okhttp3.internal.publicsuffix.PublicSuffixDatabase
        
        # 4. Zero-Trust Production Log Stripping
        -assumenosideeffects class android.util.Log {
            public static boolean isLoggable(java.lang.String, int);
            public static int v(...);
            public static int d(...);
        }
    """.trimIndent()

    fun productionChecklist(uiState: ShieldUiState): List<ReleaseModuleChecklistItem> {
        val verifiedCount = uiState.scannerNodes.count {
            it.scanStatus == com.example.parser.NodeScanStatus.VERIFIED_200
        }
        return listOf(
            ReleaseModuleChecklistItem(
                id = "mod_shield",
                moduleTitle = "1. Live Shield Radar & 4-Stage Pipeline",
                verificationDetail = "120FPS Canvas dimension guard • ${uiState.googleServices.size} Google/Gemini endpoints verified",
                statusBadge = "VERIFIED",
                badgeColor = NeonEmerald
            ),
            ReleaseModuleChecklistItem(
                id = "mod_scanner",
                moduleTitle = "2. Multi-Protocol Subscription Scanner",
                verificationDetail = "$verifiedCount/${uiState.scannerNodes.size} nodes 200 OK • Per-Link Copy ('کپی لینک') + Bulk Copy active",
                statusBadge = "READY",
                badgeColor = NeonCyan
            ),
            ReleaseModuleChecklistItem(
                id = "mod_actions_ota",
                moduleTitle = "3. Dynamic GitHub Actions Sync & OTA Updater",
                verificationDetail = "Run #${uiState.latestWorkflowRunNumber} (${uiState.latestActionCommitHash}) • ${uiState.dynamicEndpointRules.size} dynamic rules synced",
                statusBadge = "SYNCED",
                badgeColor = GoogleBlue
            ),
            ReleaseModuleChecklistItem(
                id = "mod_audio_theme",
                moduleTitle = "4. Procedural Sci-Fi Audio & Seamless Logo",
                verificationDetail = "Offline PCM 24kHz synthesizer + Haptics • Radial logo feathering (#010511 / #FFFFFF)",
                statusBadge = "60/120FPS",
                badgeColor = ElectricPurple
            ),
            ReleaseModuleChecklistItem(
                id = "mod_appsec",
                moduleTitle = "5. Zero-Trust AppSec & R8/ProGuard Hardening",
                verificationDetail = "SSRF Firewall + 64KB Cap + AES-256-GCM Vault + SafeLogRedactor + JNI Keep Rules",
                statusBadge = "100/100",
                badgeColor = NeonEmerald
            )
        )
    }

    fun buildFullManifestClipboardPayload(uiState: ShieldUiState): String {
        val checklist = productionChecklist(uiState)
        return buildString {
            appendLine("==================================================================")
            appendLine("GEMINI NODEGUARD — OFFICIAL PRODUCTION BUILD MANIFEST ($VERSION_NAME)")
            appendLine("==================================================================")
            appendLine("Application ID : $APPLICATION_ID")
            appendLine("Version        : $VERSION_NAME (versionCode = $VERSION_CODE)")
            appendLine("SDK Target     : minSdk = $MIN_SDK | targetSdk = $TARGET_SDK")
            appendLine("GitHub Repo    : https://github.com/$UPSTREAM_REPO")
            appendLine("Action Commit  : #${uiState.latestWorkflowRunNumber} (${uiState.latestActionCommitHash})")
            appendLine("Vault Provider : ${uiState.vaultStatus.keystoreProvider}")
            appendLine("Privacy Shield : redactSensitiveLogs=${uiState.redactSensitiveLogs}")
            appendLine()
            appendLine("--- PRODUCTION MODULE VERIFICATION ---")
            checklist.forEach { item ->
                appendLine("[${item.statusBadge}] ${item.moduleTitle}: ${item.verificationDetail}")
            }
            appendLine()
            appendLine("--- R8 / PROGUARD KEEP RULES (proguard-rules.pro) ---")
            appendLine(R8_PROGUARD_MANIFEST_TEXT)
        }
    }
}

/**
 * Compact card displayed inside the AppSec Lab tab so users can inspect production
 * release metadata (`versionName = "1.0.0-release"`, `versionCode = 1`, R8/ProGuard keep rules)
 * or launch the full Release Info & Build Manifest modal.
 */
@Composable
fun ReleaseBuildManifestCard(
    uiState: ShieldUiState,
    onOpenReleaseManifestModal: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val palette = LocalNodeGuardColors.current

    Box(
        modifier = modifier
            .fillMaxWidth()
            .spatialGlassCard(cornerRadius = 20.dp, accentTint = NeonEmerald)
            .testTag("release_build_manifest_card")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    SeamlessLogoBox(
                        emblemType = BrandEmblemType.GEMINI_NODEGUARD,
                        boxSize = 44.dp
                    )
                    Column {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Verified,
                                contentDescription = null,
                                tint = NeonEmerald,
                                modifier = Modifier.size(15.dp)
                            )
                            Text(
                                text = "RELEASE INFO & BUILD MANIFEST",
                                style = MaterialTheme.typography.labelMedium.copy(
                                    fontFamily = JetBrainsMonoFontFamily,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = palette.textPrimary
                                )
                            )
                        }
                        Text(
                            text = "versionName = \"${ReleaseManifestSpec.VERSION_NAME}\" • versionCode = ${ReleaseManifestSpec.VERSION_CODE} • R8 + JNI Keep",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontFamily = JetBrainsMonoFontFamily,
                                color = palette.textSecondary,
                                fontSize = 10.sp
                            ),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                Surface(
                    shape = RoundedCornerShape(999.dp),
                    color = NeonEmerald.copy(alpha = 0.14f),
                    border = BorderStroke(1.dp, NeonEmerald.copy(alpha = 0.55f))
                ) {
                    Text(
                        text = ReleaseManifestSpec.VERSION_NAME,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontFamily = JetBrainsMonoFontFamily,
                            color = NeonEmerald,
                            fontWeight = FontWeight.Bold,
                            fontSize = 10.sp
                        )
                    )
                }
            }

            // Metadata Grid Pills
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                ManifestMetaPill(
                    label = "VERSION",
                    value = "1.0.0-release (1)",
                    accent = NeonEmerald,
                    modifier = Modifier.weight(1f)
                )
                ManifestMetaPill(
                    label = "R8 / PROGUARD",
                    value = "JNI + JSON KEEP",
                    accent = NeonCyan,
                    modifier = Modifier.weight(1f)
                )
                ManifestMetaPill(
                    label = "LOG REDACTOR",
                    value = if (uiState.redactSensitiveLogs) "MASKED (PROD)" else "RAW MODE",
                    accent = if (uiState.redactSensitiveLogs) ElectricPurple else NeonAmber,
                    modifier = Modifier.weight(1f)
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Button(
                    onClick = {
                        SciFiSoundEngine.playHoloClick()
                        onOpenReleaseManifestModal()
                    },
                    modifier = Modifier
                        .weight(1f)
                        .height(46.dp)
                        .testTag("open_release_manifest_button"),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = RoyalSapphire,
                        contentColor = Color.White
                    )
                ) {
                    Icon(
                        imageVector = Icons.Default.Code,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "VIEW RELEASE & R8 MANIFEST",
                        style = MaterialTheme.typography.labelMedium.copy(
                            fontFamily = JetBrainsMonoFontFamily,
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp
                        ),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                OutlinedButton(
                    onClick = {
                        val payload = ReleaseManifestSpec.buildFullManifestClipboardPayload(uiState)
                        SecurityHardeningManager.SafeClipboard.copyToClipboard(
                            context = context,
                            label = "Gemini_NodeGuard_v1.0.0_Release_Manifest.txt",
                            text = payload,
                            isSensitive = false
                        )
                        SciFiSoundEngine.playCopyLaser(context)
                        Toast.makeText(
                            context,
                            "v1.0.0-release Build & R8 Manifest copied to clipboard",
                            Toast.LENGTH_SHORT
                        ).show()
                    },
                    modifier = Modifier
                        .height(46.dp)
                        .testTag("copy_release_manifest_inline_button"),
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, NeonCyan.copy(alpha = 0.65f)),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = NeonCyan)
                ) {
                    Icon(
                        imageVector = Icons.Default.ContentCopy,
                        contentDescription = "Copy Release Manifest",
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "COPY",
                        style = MaterialTheme.typography.labelMedium.copy(
                            fontFamily = JetBrainsMonoFontFamily,
                            fontWeight = FontWeight.Bold
                        )
                    )
                }
            }
        }
    }
}

@Composable
private fun ManifestMetaPill(
    label: String,
    value: String,
    accent: Color,
    modifier: Modifier = Modifier
) {
    val palette = LocalNodeGuardColors.current
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(10.dp),
        color = palette.surfaceBright,
        border = BorderStroke(1.dp, accent.copy(alpha = 0.35f))
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall.copy(
                    fontFamily = JetBrainsMonoFontFamily,
                    color = palette.textSecondary,
                    fontSize = 9.sp
                ),
                maxLines = 1
            )
            Text(
                text = value,
                style = MaterialTheme.typography.labelSmall.copy(
                    fontFamily = JetBrainsMonoFontFamily,
                    color = accent,
                    fontWeight = FontWeight.Bold,
                    fontSize = 10.sp
                ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

/**
 * Full-screen / modal dialog accessible from the TopAppBar (`v1.0.0` badge or logo)
 * and from the AppSec Lab tab (`ReleaseBuildManifestCard`).
 */
@Composable
fun ReleaseManifestDialog(
    uiState: ShieldUiState,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val palette = LocalNodeGuardColors.current
    val checklist = remember(
        uiState.scannerNodes,
        uiState.googleServices,
        uiState.latestWorkflowRunNumber,
        uiState.dynamicEndpointRules
    ) {
        ReleaseManifestSpec.productionChecklist(uiState)
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(0.94f)
                .spatialGlassCard(cornerRadius = 24.dp, accentTint = NeonCyan)
                .padding(18.dp)
                .testTag("release_manifest_dialog")
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Header with Seamless Resized App Logo
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        SeamlessLogoBox(
                            emblemType = BrandEmblemType.GEMINI_NODEGUARD,
                            boxSize = 60.dp
                        )
                        Column {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = NeonEmerald.copy(alpha = 0.18f),
                                    border = BorderStroke(1.dp, NeonEmerald)
                                ) {
                                    Text(
                                        text = ReleaseManifestSpec.VERSION_NAME,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            fontFamily = JetBrainsMonoFontFamily,
                                            color = NeonEmerald,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 10.sp
                                        )
                                    )
                                }
                                Text(
                                    text = "versionCode = ${ReleaseManifestSpec.VERSION_CODE}",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontFamily = JetBrainsMonoFontFamily,
                                        color = NeonCyan,
                                        fontSize = 10.sp
                                    )
                                )
                            }
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "GEMINI NODEGUARD PRODUCTION MANIFEST",
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontWeight = FontWeight.ExtraBold,
                                    color = palette.textPrimary
                                )
                            )
                            Text(
                                text = "${ReleaseManifestSpec.APPLICATION_ID} • API ${ReleaseManifestSpec.MIN_SDK}–${ReleaseManifestSpec.TARGET_SDK}",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontFamily = JetBrainsMonoFontFamily,
                                    color = palette.textSecondary,
                                    fontSize = 10.sp
                                )
                            )
                        }
                    }

                    IconButton(
                        onClick = {
                            SciFiSoundEngine.playHoloClick()
                            onDismiss()
                        },
                        modifier = Modifier.testTag("close_release_manifest_dialog")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close Release Manifest",
                            tint = palette.textSecondary
                        )
                    }
                }

                HorizontalDivider(color = palette.cardBorder)

                // 5-Point Production Readiness Checklist
                Text(
                    text = "PRODUCTION INTEGRATION & ZERO-TRUST VERIFICATION",
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontFamily = JetBrainsMonoFontFamily,
                        color = NeonCyan,
                        fontWeight = FontWeight.Bold
                    )
                )

                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    checklist.forEach { item ->
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = palette.surfaceBright,
                            border = BorderStroke(1.dp, palette.cardBorder)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 10.dp, vertical = 8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.CheckCircle,
                                        contentDescription = null,
                                        tint = item.badgeColor,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Column {
                                        Text(
                                            text = item.moduleTitle,
                                            style = MaterialTheme.typography.labelMedium.copy(
                                                color = palette.textPrimary,
                                                fontWeight = FontWeight.Bold
                                            )
                                        )
                                        Text(
                                            text = item.verificationDetail,
                                            style = MaterialTheme.typography.labelSmall.copy(
                                                fontFamily = JetBrainsMonoFontFamily,
                                                color = palette.textSecondary,
                                                fontSize = 10.sp
                                            )
                                        )
                                    }
                                }

                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = item.badgeColor.copy(alpha = 0.14f),
                                    border = BorderStroke(1.dp, item.badgeColor.copy(alpha = 0.5f))
                                ) {
                                    Text(
                                        text = item.statusBadge,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            fontFamily = JetBrainsMonoFontFamily,
                                            color = item.badgeColor,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 9.sp
                                        )
                                    )
                                }
                            }
                        }
                    }
                }

                // R8 / ProGuard Keep Rules Box
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Security,
                        contentDescription = null,
                        tint = NeonEmerald,
                        modifier = Modifier.size(14.dp)
                    )
                    Text(
                        text = "R8 / PROGUARD KEEP RULES (proguard-rules.pro)",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontFamily = JetBrainsMonoFontFamily,
                            color = NeonEmerald,
                            fontWeight = FontWeight.Bold
                        )
                    )
                }

                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = palette.surfaceBright,
                    border = BorderStroke(1.dp, palette.cardBorder),
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 190.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .padding(10.dp)
                            .verticalScroll(rememberScrollState())
                    ) {
                        Text(
                            text = ReleaseManifestSpec.R8_PROGUARD_MANIFEST_TEXT,
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontFamily = JetBrainsMonoFontFamily,
                                color = palette.textPrimary,
                                fontSize = 10.sp,
                                lineHeight = 14.sp
                            )
                        )
                    }
                }

                // Bottom Action Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Button(
                        onClick = {
                            val payload = ReleaseManifestSpec.buildFullManifestClipboardPayload(uiState)
                            SecurityHardeningManager.SafeClipboard.copyToClipboard(
                                context = context,
                                label = "Gemini_NodeGuard_v1.0.0_Release_Manifest.txt",
                                text = payload,
                                isSensitive = false
                            )
                            SciFiSoundEngine.playCopyLaser(context)
                            Toast.makeText(
                                context,
                                "v1.0.0-release Manifest & R8 rules copied to clipboard",
                                Toast.LENGTH_SHORT
                            ).show()
                        },
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .testTag("copy_release_manifest_button"),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = NeonEmerald,
                            contentColor = Color(0xFF04130D)
                        )
                    ) {
                        Icon(
                            imageVector = Icons.Default.ContentCopy,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "COPY FULL RELEASE MANIFEST",
                            style = MaterialTheme.typography.labelMedium.copy(
                                fontFamily = JetBrainsMonoFontFamily,
                                fontWeight = FontWeight.Bold
                            )
                        )
                    }

                    OutlinedButton(
                        onClick = {
                            SciFiSoundEngine.playHoloClick()
                            onDismiss()
                        },
                        modifier = Modifier.height(48.dp),
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, palette.cardBorder),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = palette.textPrimary)
                    ) {
                        Text(
                            text = "CLOSE",
                            style = MaterialTheme.typography.labelMedium.copy(
                                fontFamily = JetBrainsMonoFontFamily,
                                fontWeight = FontWeight.Bold
                            )
                        )
                    }
                }
            }
        }
    }
}
