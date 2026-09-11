package com.gridee.parking.ui.bottomsheet

import androidx.lifecycle.SavedStateHandle
import java.io.File
import java.lang.reflect.Modifier
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [35])
class SelectVehicleRestorationPolicyTest {

    @Test
    fun `complete id and number pairs retain their order`() {
        assertEquals(
            listOf("vehicle-2" to "MH12AB1234", "vehicle-1" to "TN011234"),
            SelectVehicleRestorationPolicy.pairSnapshots(
                ids = listOf("vehicle-2", "vehicle-1"),
                numbers = listOf("MH12AB1234", "TN011234"),
            ),
        )
    }

    @Test
    fun `incomplete restored snapshots never create a mismatched vehicle`() {
        assertEquals(
            listOf("vehicle-1" to "TN011234"),
            SelectVehicleRestorationPolicy.pairSnapshots(
                ids = listOf("vehicle-1", "orphan-id"),
                numbers = listOf("TN011234"),
            ),
        )
        assertTrue(SelectVehicleRestorationPolicy.pairSnapshots(null, null).isEmpty())
    }

    @Test
    fun `add page restores and unknown state falls back to select page`() {
        assertEquals(
            SelectVehicleRestorationPolicy.Page.ADD,
            SelectVehicleRestorationPolicy.restorePage("ADD"),
        )
        assertEquals(
            SelectVehicleRestorationPolicy.Page.SELECT,
            SelectVehicleRestorationPolicy.restorePage("unknown"),
        )
        assertEquals(
            SelectVehicleRestorationPolicy.Page.SELECT,
            SelectVehicleRestorationPolicy.restorePage(null),
        )
    }

    @Test
    fun `add result is accepted only for the active token and equivalent plate`() {
        assertTrue(
            SelectVehicleRestorationPolicy.shouldAcceptAddResult(
                activeRequestToken = "request-1",
                activeVehicleNumber = "mh 12 ab 1234",
                completedRequestToken = null,
                resultRequestToken = "request-1",
                resultVehicleNumber = "MH12AB1234",
            ),
        )
        assertFalse(
            SelectVehicleRestorationPolicy.shouldAcceptAddResult(
                activeRequestToken = "request-2",
                activeVehicleNumber = "MH12AB1234",
                completedRequestToken = null,
                resultRequestToken = "stale-request",
                resultVehicleNumber = "MH12AB1234",
            ),
        )
        assertFalse(
            SelectVehicleRestorationPolicy.shouldAcceptAddResult(
                activeRequestToken = "request-2",
                activeVehicleNumber = "MH12AB1234",
                completedRequestToken = "request-2",
                resultRequestToken = "request-2",
                resultVehicleNumber = "MH12AB1234",
            ),
        )
    }

    @Test
    fun `coordinator joins the same request and rejects stale or concurrent completions`() {
        val coordinator = SelectVehicleAddRequestCoordinator(SavedStateHandle())
        var callback: ((Boolean) -> Unit)? = null
        var starts = 0

        assertEquals(
            SelectVehicleAddRequestCoordinator.Admission.STARTED,
            coordinator.submit("request-1", "MH12AB1234") {
                starts++
                callback = it
            },
        )
        assertEquals(
            SelectVehicleAddRequestCoordinator.Admission.JOINED,
            coordinator.submit("request-1", "MH 12 AB 1234") { starts++ },
        )
        assertEquals(
            SelectVehicleAddRequestCoordinator.Admission.BUSY,
            coordinator.submit("request-2", "TN01AB1234") { starts++ },
        )
        assertEquals(1, starts)
        assertFalse(coordinator.complete("stale-request", "MH12AB1234", success = true))

        requireNotNull(callback).invoke(true)
        assertEquals(
            SelectVehicleAddCompletion("request-1", "MH12AB1234", success = true),
            coordinator.currentCompletion(),
        )
        assertFalse(coordinator.complete("request-1", "MH12AB1234", success = false))
        assertEquals(
            SelectVehicleAddRequestCoordinator.Admission.REPLAYED,
            coordinator.submit("request-1", "MH12AB1234") { starts++ },
        )
        assertEquals(1, starts)
    }

    @Test
    fun `saved active request becomes one retryable failure and rejects a stale callback`() {
        val savedState = SavedStateHandle()
        val original = SelectVehicleAddRequestCoordinator(savedState)
        var staleCallback: ((Boolean) -> Unit)? = null
        original.submit("request-process", "KA01AB1234") { staleCallback = it }

        val restored = SelectVehicleAddRequestCoordinator(savedState)

        assertEquals(
            SelectVehicleAddCompletion(
                requestToken = "request-process",
                vehicleNumber = "KA01AB1234",
                success = false,
            ),
            restored.currentCompletion(),
        )
        requireNotNull(staleCallback).invoke(true)
        assertEquals(false, restored.currentCompletion()?.success)
    }

    @Test
    fun `sheet restoration contract remains constructor and transaction safe`() {
        val constructor = SelectVehicleBottomSheet::class.java.getDeclaredConstructor()
        assertTrue(Modifier.isPublic(constructor.modifiers))

        val moduleRoot = findAppModuleRoot()
        val sheetSource = moduleRoot.resolve(
            "src/main/java/com/gridee/parking/ui/bottomsheet/SelectVehicleBottomSheet.kt",
        ).readText()
        val hostSource = moduleRoot.resolve(
            "src/main/java/com/gridee/parking/ui/bottomsheet/ParkingSpot_bottomsheet.kt",
        ).readText()
        val homeSource = moduleRoot.resolve(
            "src/main/java/com/gridee/parking/ui/fragments/HomeFragment.kt",
        ).readText()
        val showContract = hostSource
            .substringAfter("private fun showVehicleSelectionBottomSheet()")
            .substringBefore("private fun updateStartTimeDisplay")
        val parkingSpotShowContract = homeSource
            .substringAfter("private fun openParkingSpotBottomSheet")
            .substringBefore("private fun resolveSpotDisplayName")

        assertTrue(sheetSource.contains("putStringArrayList(ARG_VEHICLE_IDS"))
        assertTrue(sheetSource.contains("putStringArrayList(ARG_VEHICLE_NUMBERS"))
        assertTrue(sheetSource.contains("putString(ARG_SELECTED_VEHICLE_ID"))
        assertFalse(sheetSource.contains("putSerializable"))
        assertTrue(sheetSource.contains("override fun onSaveInstanceState"))
        assertTrue(sheetSource.contains("putBoolean(STATE_SELECTION_DELIVERED"))
        assertTrue(sheetSource.contains("if (selectionDelivered)"))
        assertTrue(sheetSource.contains("STATE_ACTIVE_ADD_REQUEST_TOKEN"))
        assertTrue(sheetSource.contains("STATE_COMPLETED_ADD_REQUEST_TOKEN"))
        assertTrue(sheetSource.contains("STATE_SELECTION_REQUEST_TOKEN"))
        assertTrue(sheetSource.contains("RESULT_KEY_VEHICLE_SELECTION"))
        assertTrue(sheetSource.contains("fragmentManager.isStateSaved"))
        assertTrue(sheetSource.contains("setFragmentResult(\n            REQUEST_KEY_ADD_VEHICLE"))
        assertTrue(sheetSource.contains("RESULT_KEY_ADD_VEHICLE"))
        assertFalse(sheetSource.contains("fun onAddVehicleRequested"))
        assertFalse(hostSource.contains("SelectVehicleBottomSheet.Host"))
        assertTrue(hostSource.contains("SelectVehicleAddRequestCoordinator"))
        assertTrue(
            hostSource.contains(
                "setFragmentResultListener(\n            " +
                    "SelectVehicleBottomSheet.RESULT_KEY_VEHICLE_SELECTION",
            ),
        )
        assertTrue(hostSource.contains("STATE_ACTIVE_VEHICLE_SELECTION_REQUEST_TOKEN"))
        assertTrue(hostSource.contains("setFragmentResultListener(\n            SelectVehicleBottomSheet.REQUEST_KEY_ADD_VEHICLE"))
        assertTrue(showContract.contains("childFragmentManager"))
        assertTrue(showContract.contains("Lifecycle.State.RESUMED"))
        assertTrue(showContract.contains("fragmentManager.isDestroyed"))
        assertTrue(showContract.contains("fragmentManager.isStateSaved"))
        assertTrue(showContract.contains("findFragmentByTag(SelectVehicleBottomSheet.TAG)"))
        assertTrue(showContract.contains("showNow(fragmentManager, SelectVehicleBottomSheet.TAG)"))
        assertTrue(showContract.contains("activeVehicleSelectionRequestToken = null"))
        assertFalse(showContract.contains(").show(fragmentManager"))
        assertFalse(showContract.contains("show(parentFragmentManager"))
        assertTrue(parkingSpotShowContract.contains("fragmentManager.isDestroyed"))
        assertTrue(parkingSpotShowContract.contains("fragmentManager.isStateSaved"))
        assertTrue(parkingSpotShowContract.contains("findFragmentByTag(ParkingSpotBottomSheet.TAG)"))
        assertTrue(
            parkingSpotShowContract.indexOf("findFragmentByTag(ParkingSpotBottomSheet.TAG)") <
                parkingSpotShowContract.indexOf("ParkingSpotBottomSheet.newInstance"),
        )
    }

    private fun findAppModuleRoot(): File {
        val workingDirectory = System.getProperty("user.dir") ?: "."
        var cursor: File? = File(workingDirectory).absoluteFile
        while (true) {
            val current = cursor ?: break
            val candidates = listOf(
                current,
                current.resolve("app"),
                current.resolve("Gridee_Android/android-app/app"),
            )
            candidates.firstOrNull {
                it.resolve("src/main/java").isDirectory &&
                    (it.resolve("build.gradle").isFile || it.resolve("build.gradle.kts").isFile)
            }?.let { return it }
            cursor = current.parentFile
        }
        error("Unable to locate the Android app module from $workingDirectory")
    }
}
