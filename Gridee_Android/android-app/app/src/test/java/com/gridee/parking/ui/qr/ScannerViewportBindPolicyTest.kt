package com.gridee.parking.ui.qr

import org.junit.Assert.assertEquals
import org.junit.Test

class ScannerViewportBindPolicyTest {

    @Test
    fun `available viewport binds immediately`() {
        assertEquals(
            ScannerViewportBindAction.BIND,
            ScannerViewportBindPolicy.decide(
                viewportAvailable = true,
                shouldContinue = true,
                retryAttempt = 0,
            ),
        )
    }

    @Test
    fun `missing viewport retries only within the bounded window`() {
        assertEquals(
            ScannerViewportBindAction.RETRY,
            ScannerViewportBindPolicy.decide(
                viewportAvailable = false,
                shouldContinue = true,
                retryAttempt = ScannerViewportBindPolicy.MAX_RETRY_ATTEMPTS - 1,
            ),
        )
        assertEquals(
            ScannerViewportBindAction.FAIL,
            ScannerViewportBindPolicy.decide(
                viewportAvailable = false,
                shouldContinue = true,
                retryAttempt = ScannerViewportBindPolicy.MAX_RETRY_ATTEMPTS,
            ),
        )
    }

    @Test
    fun `stopped lifecycle cancels instead of binding or reporting failure`() {
        assertEquals(
            ScannerViewportBindAction.CANCEL,
            ScannerViewportBindPolicy.decide(
                viewportAvailable = true,
                shouldContinue = false,
                retryAttempt = 0,
            ),
        )
        assertEquals(
            ScannerViewportBindAction.CANCEL,
            ScannerViewportBindPolicy.decide(
                viewportAvailable = false,
                shouldContinue = false,
                retryAttempt = ScannerViewportBindPolicy.MAX_RETRY_ATTEMPTS,
            ),
        )
    }
}
