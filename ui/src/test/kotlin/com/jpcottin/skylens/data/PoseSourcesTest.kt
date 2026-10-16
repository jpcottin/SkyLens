package com.jpcottin.skylens.data

import app.cash.turbine.test
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PoseSourcesTest {
    private val glasses = PoseReading(DevicePose.fromAngles(90.0, 10.0), CompassAccuracy.HIGH)
    private val phone = PoseReading(DevicePose.fromAngles(180.0, 0.0), CompassAccuracy.MEDIUM)

    @Test
    fun primaryThatReports_isUsed_andKeepsReporting() = runTest {
        val primary = flow { emit(glasses); delay(100); emit(glasses.copy(accuracy = CompassAccuracy.LOW)) }
        val combined = PoseSources.withFallback(primary, flowOf(phone), timeoutMillis = 1_000)!!
        combined.test {
            assertEquals(glasses, awaitItem())
            // The primary is re-subscribed after its first reading, so it replays from the start.
            assertEquals(glasses, awaitItem())
            assertEquals(CompassAccuracy.LOW, awaitItem().accuracy)
            awaitComplete()
        }
    }

    @Test
    fun silentPrimary_fallsBackAfterTheTimeout() = runTest {
        val silent = flow<PoseReading> { delay(60_000) }
        val combined = PoseSources.withFallback(silent, flowOf(phone), timeoutMillis = 3_000)!!
        assertEquals(listOf(phone), combined.toList())
    }

    @Test
    fun emptyPrimary_fallsBack() = runTest {
        assertEquals(listOf(phone), PoseSources.withFallback(emptyFlow(), flowOf(phone), 1_000)!!.toList())
    }

    @Test
    fun missingSources() {
        val only = flowOf(phone)
        assertSame(only, PoseSources.withFallback(null, only))
        assertSame(only, PoseSources.withFallback(only, null))
        assertNull(PoseSources.withFallback(null, null))
    }
}
