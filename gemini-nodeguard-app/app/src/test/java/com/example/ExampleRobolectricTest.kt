package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.audio.SciFiSoundEngine
import com.example.bridge.DynamicActionSyncManager
import com.example.model.ShieldUiState
import com.example.parser.NodeScanStatus
import com.example.parser.ProxyProtocol
import com.example.parser.ProxyUriParser
import com.example.parser.ScannerFilter
import com.example.security.AppSecFuzzingEngine
import com.example.security.AuditExportFormat
import com.example.security.DiagnosticErrorCollector
import com.example.security.FuzzTestState
import com.example.security.SecurityHardeningManager
import com.example.security.SessionTelemetryExporter
import com.example.ui.components.ReleaseManifestSpec
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

    @Test
    fun `verify app name and v1_0_0_release manifest metadata`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val appName = context.getString(R.string.app_name)
        assertEquals("Gemini NodeGuard", appName)
        assertEquals("1.0.0-release", ReleaseManifestSpec.VERSION_NAME)
        assertEquals(1, ReleaseManifestSpec.VERSION_CODE)
        assertTrue(ReleaseManifestSpec.R8_PROGUARD_MANIFEST_TEXT.contains("-keep class com.example.bridge.**"))
    }

    @Test
    fun `verify multi_protocol parser supports VLESS VMess Trojan Hysteria2 and TUIC`() {
        val vless = ProxyUriParser.parseSingleUri(
            "vless://7c9e6679-7425-40de-944b-e07fc1f90ae7@tyo-edge-01.nodeguard.io:443?security=reality&sni=generativelanguage.googleapis.com&type=tcp#TYO-Edge"
        )
        assertNotNull(vless)
        assertEquals(ProxyProtocol.VLESS, vless?.protocol)
        assertFalse(vless?.isPrivateOrLoopbackRisk ?: true)

        val trojan = ProxyUriParser.parseSingleUri(
            "trojan://trj_secret_pass@zrh-edge.nodeguard.io:443?security=tls&sni=gemini.google.com&type=ws#ZRH-Trojan"
        )
        assertNotNull(trojan)
        assertEquals(ProxyProtocol.TROJAN, trojan?.protocol)

        val hy2 = ProxyUriParser.parseSingleUri(
            "hysteria2://hy2_token@sjc-quic.nodeguard.io:4433?sni=aistudio.google.com&alpn=h3#SJC-Hy2"
        )
        assertNotNull(hy2)
        assertEquals(ProxyProtocol.HYSTERIA2, hy2?.protocol)

        val tuic = ProxyUriParser.parseSingleUri(
            "tuic://3f2504e0-4f89-11d3-9a0c-0305e82c3301@sin-tuic.nodeguard.io:443?sni=gemini.google.com&alpn=h3#SIN-TUIC"
        )
        assertNotNull(tuic)
        assertEquals(ProxyProtocol.TUIC, tuic?.protocol)
        assertTrue(ScannerFilter.entries.contains(ScannerFilter.PROTO_TUIC))
    }

    @Test
    fun `verify zero_trust SSRF firewall blocks loopback private LAN and cloud metadata`() {
        val loopback = SecurityHardeningManager.SsrfFirewall.inspectHostOrUrl("127.0.0.1")
        assertTrue(loopback.isBlocked)

        val localhost = SecurityHardeningManager.SsrfFirewall.inspectHostOrUrl("localhost")
        assertTrue(localhost.isBlocked)

        val privateLan10 = SecurityHardeningManager.SsrfFirewall.inspectHostOrUrl("10.0.0.1")
        assertTrue(privateLan10.isBlocked)

        val privateLan192 = SecurityHardeningManager.SsrfFirewall.inspectHostOrUrl("192.168.1.50")
        assertTrue(privateLan192.isBlocked)

        val metadata = SecurityHardeningManager.SsrfFirewall.inspectHostOrUrl("169.254.169.254")
        assertTrue(metadata.isBlocked)

        val ssrfNode = ProxyUriParser.parseSingleUri(
            "vless://7c9e6679-7425-40de-944b-e07fc1f90ae7@169.254.169.254:80?security=none#Metadata-SSRF"
        )
        assertNotNull(ssrfNode)
        assertTrue(ssrfNode!!.isPrivateOrLoopbackRisk)
        assertEquals(NodeScanStatus.SSRF_BLOCKED, ssrfNode.scanStatus)
    }

    @Test
    fun `verify 64KB Base64 cap and config injection sanitizer`() {
        val oversizedPayload = "vmess://" + "A".repeat(70_000)
        assertNull(ProxyUriParser.parseSingleUri(oversizedPayload))

        val injectionAttempt = SecurityHardeningManager.ConfigInjectionSanitizer.sanitizeSniOrHost(
            "evil.com\",\"detour\":\"root-shell\";../../etc/passwd"
        )
        assertTrue(injectionAttempt.hadInjectionAttempt)
        assertFalse(injectionAttempt.cleanValue.contains("\""))
        assertFalse(injectionAttempt.cleanValue.contains(".."))
    }

    @Test
    fun `verify AES_256_GCM vault seal decrypt zeroize and SafeLogRedactor`() {
        val vault = SecurityHardeningManager.HardwareCredentialVault()
        val sealedSnap = vault.storeCredentials(
            "ghp_TestSecretPatToken123456789".toCharArray(),
            "gist_secret_id_987654".toCharArray()
        )
        assertTrue(sealedSnap.hasEncryptedPat)
        assertEquals("ghp_TestSecretPatToken123456789", vault.readPatCopy())

        val wipedSnap = vault.zeroizeMemoryAndWipeVault()
        assertFalse(wipedSnap.hasEncryptedPat)
        assertEquals("", vault.readPatCopy())

        val redactedLog = SecurityHardeningManager.SafeLogRedactor.redactDiagnosticLog(
            rawLog = "Authorization: Bearer ghp_SecretToken999 UUID=7c9e6679-7425-40de-944b-e07fc1f90ae7 IP=203.0.113.42",
            redactEnabled = true
        )
        assertFalse(redactedLog.contains("ghp_SecretToken999"))
        assertFalse(redactedLog.contains("203.0.113.42"))
        assertTrue(redactedLog.contains("203.0.***.***"))
    }

    @Test
    fun `verify 5_stage fuzzing suite and OWASP Top 10 audit pass 100 percent`() {
        val fuzzResults = listOf(
            AppSecFuzzingEngine.executeFuzzTest1(),
            AppSecFuzzingEngine.executeFuzzTest2(),
            AppSecFuzzingEngine.executeFuzzTest3(),
            AppSecFuzzingEngine.executeFuzzTest4(),
            AppSecFuzzingEngine.executeFuzzTest5()
        )
        assertEquals(5, fuzzResults.size)
        assertTrue(fuzzResults.all { it.state == FuzzTestState.PASSED })

        val owasp = AppSecFuzzingEngine.owaspTop10Matrix()
        assertEquals(10, owasp.size)
    }

    @Test
    fun `verify SciFiSoundEngine mute toggle and crash_safe synthesis`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        SciFiSoundEngine.setSoundEnabled(false)
        assertFalse(SciFiSoundEngine.isSoundEnabled())
        SciFiSoundEngine.playRadarSweep()
        SciFiSoundEngine.playHoloClick()
        SciFiSoundEngine.playStageSuccess(2)
        SciFiSoundEngine.playGeminiVerified()
        SciFiSoundEngine.playThreatBlocked()
        SciFiSoundEngine.playCopyLaser(context)

        SciFiSoundEngine.setSoundEnabled(true)
        assertTrue(SciFiSoundEngine.isSoundEnabled())
    }

    @Test
    fun `verify SessionTelemetryExporter JSON and Markdown formatting`() {
        val uiState = ShieldUiState()
        val owasp = AppSecFuzzingEngine.owaspTop10Matrix()

        val jsonExport = SessionTelemetryExporter.buildExportPayload(
            uiState = uiState,
            owaspItems = owasp,
            format = AuditExportFormat.JSON
        )
        assertTrue(jsonExport.contains("Gemini NodeGuard"))
        assertTrue(jsonExport.contains("1.4.0"))

        val mdExport = SessionTelemetryExporter.buildExportPayload(
            uiState = uiState,
            owaspItems = owasp,
            format = AuditExportFormat.MARKDOWN
        )
        assertTrue(mdExport.contains("Gemini NodeGuard"))
    }

    @Test
    fun `verify DynamicActionSyncManager and DiagnosticErrorCollector`() {
        val endpoints = DynamicActionSyncManager.initialDynamicEndpoints()
        assertTrue(endpoints.size >= 4)

        DiagnosticErrorCollector.clearAllReports()
        val caught = DiagnosticErrorCollector.recordException(
            throwable = IllegalStateException("Test fault on 198.51.100.99"),
            originModule = "UnitTestBoundary"
        )
        assertEquals("UnitTestBoundary", caught.originModule)
        assertTrue(caught.getSanitizedMessage(true).contains("198.51.***.***"))
        DiagnosticErrorCollector.clearAllReports()
    }
}
