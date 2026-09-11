package com.gridee.parking.ui.bottomsheet

import android.os.Bundle
import android.os.Parcel
import com.gridee.parking.data.model.Vehicle
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [35])
class ParkingSpotBookingDraftRestorationTest {

    @Test
    fun `configuration snapshot retains vehicle identity times and default-selection intent`() {
        val snapshot = draft(
            vehicleId = "user_vehicle_2",
            vehicleNumber = "MH 12 AB 1234",
            start = 1_800_000L,
            end = 5_400_000L,
            allowAutomaticSelection = false,
        )

        assertEquals(snapshot, ParkingSpotBookingDraftSnapshot.fromBundle(snapshot.toBundle()))
    }

    @Test
    fun `parcel round trip retains only process-restorable primitive draft state`() {
        val snapshot = draft(
            vehicleId = "user_vehicle_1",
            vehicleNumber = "TN01AB1234",
            start = 10_000L,
            end = 20_000L,
            allowAutomaticSelection = false,
        )
        val parcel = Parcel.obtain()
        try {
            parcel.writeBundle(Bundle().apply { putBundle("draft", snapshot.toBundle()) })
            parcel.setDataPosition(0)

            val restoredContainer = requireNotNull(
                parcel.readBundle(ParkingSpotBookingDraftSnapshot::class.java.classLoader),
            )
            val restored = ParkingSpotBookingDraftSnapshot.fromBundle(
                requireNotNull(restoredContainer.getBundle("draft")),
            )

            assertEquals(snapshot, restored)
        } finally {
            parcel.recycle()
        }
    }

    @Test
    fun `restored plate survives profile reorder without trusting a reused synthetic id`() {
        val saved = draft(
            vehicleId = "user_vehicle_0",
            vehicleNumber = "MH12AB1234",
            allowAutomaticSelection = false,
        )
        val vehicles = listOf(
            vehicle("user_vehicle_0", "TN01AB1234"),
            vehicle("user_vehicle_1", "MH 12 AB 1234"),
        )

        assertEquals(
            vehicles[1],
            ParkingSpotBookingDraftPolicy.resolveVehicle(saved, vehicles),
        )
    }

    @Test
    fun `missing restored vehicle never silently changes to first vehicle`() {
        val saved = draft(
            vehicleId = "user_vehicle_3",
            vehicleNumber = "KA01AB1234",
            allowAutomaticSelection = false,
        )

        assertNull(
            ParkingSpotBookingDraftPolicy.resolveVehicle(
                saved,
                listOf(vehicle("user_vehicle_0", "TN01AB1234")),
            ),
        )
    }

    @Test
    fun `fresh draft may use first vehicle but restored explicit-empty draft does not`() {
        val vehicles = listOf(vehicle("user_vehicle_0", "TN01AB1234"))

        assertEquals(
            vehicles.first(),
            ParkingSpotBookingDraftPolicy.resolveVehicle(
                ParkingSpotBookingDraftSnapshot.empty(allowAutomaticVehicleSelection = true),
                vehicles,
            ),
        )
        assertNull(
            ParkingSpotBookingDraftPolicy.resolveVehicle(
                ParkingSpotBookingDraftSnapshot.empty(allowAutomaticVehicleSelection = false),
                vehicles,
            ),
        )
    }

    @Test
    fun `stale process-restored window resets to current session bounds`() {
        val validated = ParkingSpotBookingDraftPolicy.validateTimes(
            requestedStartTimeMillis = 1_000L,
            requestedEndTimeMillis = 2_000L,
            minimumStartTimeMillis = 100_000L,
            maximumEndTimeMillis = 500_000L,
        )

        assertEquals(100_000L, validated.startTimeMillis)
        assertEquals(500_000L, validated.endTimeMillis)
        assertTrue(validated.adjusted)
    }

    @Test
    fun `valid restored window is unchanged and elapsed start is clamped`() {
        val unchanged = ParkingSpotBookingDraftPolicy.validateTimes(
            requestedStartTimeMillis = 200_000L,
            requestedEndTimeMillis = 400_000L,
            minimumStartTimeMillis = 100_000L,
            maximumEndTimeMillis = 500_000L,
        )
        val elapsed = ParkingSpotBookingDraftPolicy.validateTimes(
            requestedStartTimeMillis = 100_000L,
            requestedEndTimeMillis = 500_000L,
            minimumStartTimeMillis = 200_000L,
            maximumEndTimeMillis = 500_000L,
        )

        assertEquals(200_000L, unchanged.startTimeMillis)
        assertEquals(400_000L, unchanged.endTimeMillis)
        assertFalse(unchanged.adjusted)
        assertEquals(200_000L, elapsed.startTimeMillis)
        assertEquals(500_000L, elapsed.endTimeMillis)
        assertTrue(elapsed.adjusted)
    }

    @Test
    fun `selection result is exact-once and bound to active parent request`() {
        assertTrue(
            ParkingSpotBookingDraftPolicy.shouldAcceptVehicleSelectionResult(
                activeRequestToken = "request-1",
                completedRequestToken = null,
                resultRequestToken = "request-1",
                vehicleId = "user_vehicle_1",
                vehicleNumber = "MH12AB1234",
            ),
        )
        assertFalse(
            ParkingSpotBookingDraftPolicy.shouldAcceptVehicleSelectionResult(
                activeRequestToken = "request-2",
                completedRequestToken = null,
                resultRequestToken = "stale-request",
                vehicleId = "user_vehicle_1",
                vehicleNumber = "MH12AB1234",
            ),
        )
        assertFalse(
            ParkingSpotBookingDraftPolicy.shouldAcceptVehicleSelectionResult(
                activeRequestToken = "request-1",
                completedRequestToken = "request-1",
                resultRequestToken = "request-1",
                vehicleId = "user_vehicle_1",
                vehicleNumber = "MH12AB1234",
            ),
        )
    }

    @Test
    fun `confirm cannot bypass restored vehicle or time validation`() {
        val source = parkingSpotSource()
        val createBooking = source
            .substringAfter("private fun createBooking()")
            .substringBefore("private fun createDefaultParkingSpot")

        assertTrue(
            createBooking.contains(
                "restoredVehicleValidationPending || restoredTimesValidationPending",
            ),
        )
        assertTrue(createBooking.indexOf("restoredVehicleValidationPending") <
            createBooking.indexOf("viewModel.createBackendBooking()"))
        assertTrue(createBooking.contains("return"))
    }

    private fun draft(
        vehicleId: String? = null,
        vehicleNumber: String? = null,
        start: Long? = null,
        end: Long? = null,
        allowAutomaticSelection: Boolean,
    ) = ParkingSpotBookingDraftSnapshot(
        selectedVehicleId = vehicleId,
        selectedVehicleNumber = vehicleNumber,
        startTimeMillis = start,
        endTimeMillis = end,
        allowAutomaticVehicleSelection = allowAutomaticSelection,
    )

    private fun vehicle(id: String, number: String) = Vehicle(
        id = id,
        number = number,
        type = "Car",
        brand = "User",
        model = "Vehicle",
    )

    private fun parkingSpotSource(): String = findAppModuleRoot()
        .resolve("src/main/java/com/gridee/parking/ui/bottomsheet/ParkingSpot_bottomsheet.kt")
        .readText()

    private fun findAppModuleRoot(): File {
        val workingDirectory = System.getProperty("user.dir") ?: "."
        var cursor: File? = File(workingDirectory).absoluteFile
        while (true) {
            val current = cursor ?: break
            listOf(current, current.resolve("app"), current.resolve("Gridee_Android/android-app/app"))
                .firstOrNull {
                    it.resolve("src/main/java").isDirectory &&
                        (it.resolve("build.gradle").isFile || it.resolve("build.gradle.kts").isFile)
                }
                ?.let { return it }
            cursor = current.parentFile
        }
        error("Unable to locate the Android app module from $workingDirectory")
    }
}
