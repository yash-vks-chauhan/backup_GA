package com.gridee.parking.data.api

import org.junit.Assert.assertEquals
import org.junit.Test

class NetworkPhaseAccumulatorTest {

    @Test
    fun `records privacy-safe request phases`() {
        var nowNs = 0L
        val phases = NetworkPhaseAccumulator { nowNs }

        phases.callStarted()
        nowNs += 2_000_000L
        phases.dnsStarted()
        nowNs += 3_000_000L
        phases.dnsEnded()
        phases.connectStarted()
        nowNs += 4_000_000L
        phases.tlsStarted()
        nowNs += 5_000_000L
        phases.tlsEnded()
        phases.connectEnded()
        phases.uploadStarted()
        nowNs += 6_000_000L
        phases.uploadEnded()
        nowNs += 7_000_000L
        phases.responseHeadersStarted()
        phases.downloadStarted()
        nowNs += 8_000_000L
        phases.downloadEnded()
        val timing = phases.finish(
            "POST",
            "success",
            category = "operator_checkin",
            traceId = "scantrace123",
        )

        assertEquals(35L, timing.totalMs)
        assertEquals(3L, timing.dnsMs)
        assertEquals(4L, timing.connectMs)
        assertEquals(5L, timing.tlsMs)
        assertEquals(6L, timing.uploadMs)
        assertEquals(7L, timing.serverWaitMs)
        assertEquals(8L, timing.downloadMs)
        assertEquals("POST", timing.method)
        assertEquals("operator_checkin", timing.category)
        assertEquals("scantrace123", timing.traceId)
    }

    @Test
    fun `HTTPS connect duration excludes TLS handshake`() {
        var nowNs = 0L
        val phases = NetworkPhaseAccumulator { nowNs }

        phases.callStarted()
        phases.connectStarted()
        nowNs += 12_000_000L
        phases.tlsStarted()
        nowNs += 30_000_000L
        phases.tlsEnded()
        phases.connectEnded()
        val timing = phases.finish("POST", "success")

        assertEquals(12L, timing.connectMs)
        assertEquals(30L, timing.tlsMs)
        assertEquals(42L, timing.totalMs)
    }

    @Test
    fun `failed connection attempts are accumulated without overlap`() {
        var nowNs = 0L
        val phases = NetworkPhaseAccumulator { nowNs }

        phases.callStarted()
        phases.connectStarted()
        nowNs += 7_000_000L
        phases.connectionFailed()
        phases.connectStarted()
        nowNs += 5_000_000L
        phases.connectEnded()
        val timing = phases.finish("POST", "network_failure")

        assertEquals(12L, timing.connectMs)
        assertEquals(12L, timing.totalMs)
    }

    @Test
    fun `network category emits fixed labels without path identifiers`() {
        assertEquals(
            "operator_checkin",
            PrivacySafeNetworkCategory.classify(
                "/api/operator/parking-lots/private-lot-id/bookings/checkin"
            ),
        )
        assertEquals(
            "operator_checkout",
            PrivacySafeNetworkCategory.classify("/api/operator/bookings/checkout"),
        )
        assertEquals("operator_other", PrivacySafeNetworkCategory.classify("/api/operator/spots"))
        assertEquals("other", PrivacySafeNetworkCategory.classify("/api/bookings/private-id"))
    }

    @Test
    fun `scanner network tag accepts only bounded opaque identifiers`() {
        assertEquals("safe_trace-123", ScannerNetworkTraceTag.create("safe_trace-123").id)
        val generated = ScannerNetworkTraceTag.create("vehicle TN01AB1234").id
        assertEquals(16, generated.length)
        assertEquals(true, generated.all(Char::isLetterOrDigit))
    }
}
