package com.example

import com.example.security.AppSecFuzzingEngine
import com.example.security.FuzzTestState
import com.example.security.PentestPresetPayload
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ExampleUnitTest {

    @Test
    fun fuzzSuite_all5SecurityTestsPass() {
        val t1 = AppSecFuzzingEngine.executeFuzzTest1()
        val t2 = AppSecFuzzingEngine.executeFuzzTest2()
        val t3 = AppSecFuzzingEngine.executeFuzzTest3()
        val t4 = AppSecFuzzingEngine.executeFuzzTest4()
        val t5 = AppSecFuzzingEngine.executeFuzzTest5()

        assertEquals(FuzzTestState.PASSED, t1.state)
        assertEquals(FuzzTestState.PASSED, t2.state)
        assertEquals(FuzzTestState.PASSED, t3.state)
        assertEquals(FuzzTestState.PASSED, t4.state)
        assertEquals(FuzzTestState.PASSED, t5.state)
    }

    @Test
    fun pentestSandbox_detectsSsrfAndInjectionPresets() {
        val ssrfResult = AppSecFuzzingEngine.analyzeSandboxPayload(
            PentestPresetPayload.SSRF_CLOUD_METADATA.rawPayload
        )
        assertTrue(ssrfResult.ssrfBlocked)
        assertTrue(ssrfResult.isThreatBlockedOrNeutralized)

        val breakoutResult = AppSecFuzzingEngine.analyzeSandboxPayload(
            PentestPresetPayload.JSON_BREAKOUT_ROOT_DETOUR.rawPayload
        )
        assertTrue(breakoutResult.injectionDetected)
        assertTrue(breakoutResult.isThreatBlockedOrNeutralized)
    }

    @Test
    fun owaspMatrix_hasAll10CategoriesAndBuildsReport() {
        val items = AppSecFuzzingEngine.owaspTop10Matrix()
        assertEquals(10, items.size)
        assertEquals("M1", items.first().code)
        assertEquals("M10", items.last().code)
    }
}
