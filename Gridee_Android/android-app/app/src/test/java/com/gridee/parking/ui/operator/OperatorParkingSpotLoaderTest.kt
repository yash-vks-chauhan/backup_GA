package com.gridee.parking.ui.operator

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OperatorParkingSpotLoaderTest {

    @Test
    fun successfulEmptyResponseMeansAssignedLotHasNoActiveSpots() {
        val reason = OperatorParkingSpotLoader.classifyEmptyResult(
            attempts = listOf(
                OperatorParkingSpotLoader.AttemptSummary(successful = true)
            ),
            hasLotContext = true
        )

        assertEquals(OperatorParkingSpotLoader.EmptyReason.NO_ACTIVE_SPOTS, reason)
    }

    @Test
    fun forbiddenResponseWithoutLotContextMeansOperatorNeedsAssignment() {
        val reason = OperatorParkingSpotLoader.classifyEmptyResult(
            attempts = listOf(
                OperatorParkingSpotLoader.AttemptSummary(
                    successful = false,
                    code = 403,
                    message = "Operator is not assigned to any parking lot."
                )
            ),
            hasLotContext = false
        )

        assertEquals(OperatorParkingSpotLoader.EmptyReason.NO_LOT_ASSIGNMENT, reason)
    }

    @Test
    fun forbiddenResponseWithLotContextMeansAssignmentIsStaleOrDenied() {
        val reason = OperatorParkingSpotLoader.classifyEmptyResult(
            attempts = listOf(
                OperatorParkingSpotLoader.AttemptSummary(
                    successful = false,
                    code = 403
                )
            ),
            hasLotContext = true
        )

        assertEquals(OperatorParkingSpotLoader.EmptyReason.ACCESS_DENIED, reason)
    }

    @Test
    fun networkFailureWithLotContextIsRetryableLoadFailure() {
        val reason = OperatorParkingSpotLoader.classifyEmptyResult(
            attempts = listOf(
                OperatorParkingSpotLoader.AttemptSummary(
                    successful = false,
                    failedWithException = true
                )
            ),
            hasLotContext = true
        )

        assertEquals(OperatorParkingSpotLoader.EmptyReason.LOAD_FAILED, reason)
    }

    @Test
    fun alternateRouteIsUsedOnlyWhenCurrentRouteIsUnsupported() {
        listOf(404, 405, 501).forEach { code ->
            assertTrue(
                OperatorParkingSpotLoader.canTryAlternateRoute(
                    OperatorParkingSpotLoader.AttemptSummary(successful = false, code = code)
                )
            )
        }
    }

    @Test
    fun overloadAndServerErrorsDoNotFanOutToAlternateRoutes() {
        listOf(429, 500, 502, 503, 504).forEach { code ->
            assertFalse(
                OperatorParkingSpotLoader.canTryAlternateRoute(
                    OperatorParkingSpotLoader.AttemptSummary(successful = false, code = code)
                )
            )
        }
        assertFalse(
            OperatorParkingSpotLoader.canTryAlternateRoute(
                OperatorParkingSpotLoader.AttemptSummary(
                    successful = false,
                    failedWithException = true
                )
            )
        )
    }

    @Test
    fun staleDataIsAllowedForTransientFailuresButNeverAccessDenial() {
        assertTrue(
            OperatorParkingSpotLoader.canUseStaleFallback(
                listOf(
                    OperatorParkingSpotLoader.AttemptSummary(
                        successful = false,
                        code = 503
                    )
                )
            )
        )
        assertTrue(
            OperatorParkingSpotLoader.canUseStaleFallback(
                listOf(
                    OperatorParkingSpotLoader.AttemptSummary(
                        successful = false,
                        failedWithException = true
                    )
                )
            )
        )
        assertFalse(
            OperatorParkingSpotLoader.canUseStaleFallback(
                listOf(
                    OperatorParkingSpotLoader.AttemptSummary(
                        successful = false,
                        code = 403
                    )
                )
            )
        )
    }
}
