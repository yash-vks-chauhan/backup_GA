package com.gridee.parking.ui.operator

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OperatorScanCoordinatorTest {

    private val vehicleCheckIn = OperatorOperationKey(
        operation = "CHECK_IN",
        inputMode = "VEHICLE_NUMBER",
        identifier = "TN01AB1234",
        parkingLotId = "lot-1",
        parkingSpotId = "spot-1"
    )

    @Test
    fun oneGuardSerializesCheckInAndCheckOut() {
        val coordinator = OperatorScanCoordinator(cooldownMs = 7_000L)
        val checkOut = vehicleCheckIn.copy(operation = "CHECK_OUT")

        assertTrue(
            coordinator.tryStart(vehicleCheckIn, requestId = 1L, elapsedRealtimeMs = 100L)
                is OperatorOperationStart.Started
        )
        val blocked = coordinator.tryStart(checkOut, requestId = 2L, elapsedRealtimeMs = 101L)

        assertEquals(OperatorOperationStart.Busy(activeRequestId = 1L), blocked)
        assertTrue(coordinator.hasActiveOperation())
        assertEquals(1L, coordinator.currentRequestId())
    }

    @Test
    fun onlyOwningRequestCanReleaseGuard() {
        val coordinator = OperatorScanCoordinator(cooldownMs = 7_000L)
        coordinator.tryStart(vehicleCheckIn, requestId = 10L, elapsedRealtimeMs = 100L)

        coordinator.finish(requestId = 11L)
        assertTrue(coordinator.hasActiveOperation())

        coordinator.finish(requestId = 10L)
        assertFalse(coordinator.hasActiveOperation())
    }

    @Test
    fun sameOperationKeyIsDebouncedForSevenSecondsUsingElapsedTime() {
        val coordinator = OperatorScanCoordinator(cooldownMs = 7_000L)
        coordinator.tryStart(vehicleCheckIn, requestId = 1L, elapsedRealtimeMs = 1_000L)
        coordinator.finish(1L)

        val coolingDown = coordinator.tryStart(
            vehicleCheckIn,
            requestId = 2L,
            elapsedRealtimeMs = 7_999L
        )
        assertEquals(OperatorOperationStart.CoolingDown(remainingMs = 1L), coolingDown)

        val accepted = coordinator.tryStart(
            vehicleCheckIn,
            requestId = 3L,
            elapsedRealtimeMs = 8_000L
        )
        assertTrue(accepted is OperatorOperationStart.Started)
    }

    @Test
    fun sameScanIsDebouncedEvenWhenOperationOrSelectedSpotChanges() {
        val coordinator = OperatorScanCoordinator(cooldownMs = 7_000L)
        coordinator.tryStart(vehicleCheckIn, requestId = 1L, elapsedRealtimeMs = 10L)
        coordinator.finish(1L)

        val changedOperationAndSpot = vehicleCheckIn.copy(
            operation = "CHECK_OUT",
            parkingSpotId = "spot-2",
        )
        assertEquals(
            OperatorOperationStart.CoolingDown(remainingMs = 6_999L),
            coordinator.tryStart(
                changedOperationAndSpot,
                requestId = 2L,
                elapsedRealtimeMs = 11L,
            ),
        )
    }

    @Test
    fun differentLotHasIndependentIdentityButRememberedBookingAliasesBlockModeSwitch() {
        val coordinator = OperatorScanCoordinator(cooldownMs = 7_000L)
        coordinator.tryStart(vehicleCheckIn, requestId = 1L, elapsedRealtimeMs = 10L)
        coordinator.finish(1L)

        val differentLot = vehicleCheckIn.copy(parkingLotId = "lot-2")
        assertTrue(
            coordinator.tryStart(differentLot, requestId = 2L, elapsedRealtimeMs = 11L)
                is OperatorOperationStart.Started
        )
        coordinator.finish(2L)

        coordinator.rememberBookingAliases(
            parkingLotId = "lot-1",
            vehicleNumber = "TN 01 AB 1234",
            qrAliases = listOf("booking-id-1", "booking-qr-1"),
            elapsedRealtimeMs = 12L,
        )
        listOf("booking-id-1", "booking-qr-1").forEachIndexed { index, alias ->
            val qrInput = vehicleCheckIn.copy(
                inputMode = "QR_CODE",
                identifier = alias,
            )
            assertEquals(
                OperatorOperationStart.CoolingDown(remainingMs = 6_999L),
                coordinator.tryStart(qrInput, requestId = 3L + index, elapsedRealtimeMs = 13L),
            )
        }
    }

    @Test
    fun vehicleResponseMustMatchAssignedLotAndSelectedSpot() {
        assertEquals(
            null,
            OperatorBookingScopeValidator.validate(
                returnedLotId = "lot-1",
                returnedSpotId = "spot-1",
                expectedLotId = "lot-1",
                expectedSpotId = "spot-1"
            )
        )
        assertEquals(
            "Operator response did not match the assigned parking lot",
            OperatorBookingScopeValidator.validate("lot-2", "spot-1", "lot-1", "spot-1")
        )
        assertEquals(
            "Operator response did not match the selected parking spot",
            OperatorBookingScopeValidator.validate("lot-1", "spot-2", "lot-1", "spot-1")
        )
    }

    @Test
    fun qrResponseMustMatchAssignedLotAndSelectedPhysicalSpot() {
        assertEquals(
            null,
            OperatorBookingScopeValidator.validate(
                returnedLotId = "lot-1",
                returnedSpotId = "spot-1",
                expectedLotId = "lot-1",
                expectedSpotId = "spot-1"
            )
        )
        assertEquals(
            "Operator response did not match the selected parking spot",
            OperatorBookingScopeValidator.validate("lot-1", "spot-2", "lot-1", "spot-1")
        )
    }
}
