package com.gridee.parking.ui.bottomsheet

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VehicleActionSheetLifecycleContractTest {

    @Test
    fun `add vehicle queues one restorable result and cancels delayed animation work`() {
        val source = productionSource("ui/bottomsheet/AddVehicleBottomSheet.kt")

        assertTrue(source.contains("STATE_PENDING_VEHICLE_NUMBER"))
        assertTrue(source.contains("outState.putString(STATE_PENDING_VEHICLE_NUMBER"))
        assertTrue(source.contains("pendingVehicleNumber = vehicleNumber"))
        assertTrue(source.contains("actionDelivered || pendingVehicleNumber != null"))
        assertTrue(source.contains("Lifecycle.State.RESUMED"))
        assertTrue(source.contains("fragmentManager.isDestroyed || fragmentManager.isStateSaved"))
        assertTrue(source.contains("fragmentManager.setFragmentResult("))
        assertTrue(source.contains("entrySprings.forEach { it.cancel() }"))
        assertTrue(source.contains("buttonAnimationGeneration += 1"))
        assertTrue(source.contains("withEndAction(null)"))
        assertTrue(source.contains("removeCallbacks(resumeDeliveryRunnable)"))
        assertFalse(source.contains("dismissAllowingStateLoss"))
        assertEquals(1, Regex("""(?m)^\s*dismiss\(\)\s*$""").findAll(source).count())

        val safeDismiss = source
            .substringAfter("private fun dismissIfStateCanBeSaved")
            .substringBefore("private fun animateEntry")
        assertSafeDismissAdmission(safeDismiss)
    }

    @Test
    fun `vehicle options queues one restorable result and owns its page animator`() {
        val source = productionSource("ui/bottomsheet/VehicleOptionsBottomSheet.kt")

        assertTrue(source.contains("STATE_PENDING_ACTION"))
        assertTrue(source.contains("STATE_PENDING_NEW_VEHICLE_NUMBER"))
        assertTrue(source.contains("actionDelivered || pendingAction != null"))
        assertTrue(source.contains("Lifecycle.State.RESUMED"))
        assertTrue(source.contains("fragmentManager.isDestroyed || fragmentManager.isStateSaved"))
        assertTrue(source.contains("fragmentManager.setFragmentResult("))
        assertTrue(source.contains("pageAnimator?.removeAllListeners()"))
        assertTrue(source.contains("pageAnimator?.cancel()"))
        assertTrue(source.contains("removeCallbacks(resumeDeliveryRunnable)"))
        assertFalse(source.contains("dismissAllowingStateLoss"))
        assertEquals(1, Regex("""(?m)^\s*dismiss\(\)\s*$""").findAll(source).count())

        val safeDismiss = source
            .substringAfter("private fun dismissIfStateCanBeSaved")
            .substringBefore("Generic Page Navigation Animations")
        assertSafeDismissAdmission(safeDismiss)
    }

    @Test
    fun `profile admits each scoped child sheet once with a stable tag`() {
        val source = productionSource("ui/fragments/ProfileFragment.kt")
        val profilePageLaunch = source
            .substringAfter("val editProfileListener")
            .substringBefore("binding.btnEditProfile")
        val vehicleOptionsLaunch = source
            .substringAfter("private fun showVehicleOptionsDialog")
            .substringBefore("private fun setDefaultVehicle")
        val addVehicleLaunch = source
            .substringAfter("private fun showAddVehicleDialog")
            .substringBefore("private fun showLogoutConfirmation")
        val logoutLaunch = source
            .substringAfter("private fun showLogoutConfirmation")
            .substringBefore("private fun navigateToLogin")

        listOf(
            profilePageLaunch to "ProfilePageBottomSheet.TAG",
            vehicleOptionsLaunch to "VehicleOptionsBottomSheet.TAG",
            addVehicleLaunch to "AddVehicleBottomSheet.TAG",
            logoutLaunch to "LogoutConfirmationBottomSheet.TAG",
        ).forEach { (launch, stableTag) ->
            assertTrue(launch.contains("Lifecycle.State.RESUMED"))
            assertTrue(launch.contains("fragmentManager.isDestroyed"))
            assertTrue(launch.contains("fragmentManager.isStateSaved"))
            assertTrue(launch.contains("findFragmentByTag($stableTag)"))
            assertTrue(launch.contains("showNow(fragmentManager"))
            assertTrue(launch.contains(stableTag))
            assertFalse(launch.contains(".show("))
        }
    }

    private fun assertSafeDismissAdmission(source: String) {
        assertTrue(source.contains("!isAdded"))
        assertTrue(source.contains("Lifecycle.State.RESUMED"))
        assertTrue(source.contains("fragmentManager.isDestroyed"))
        assertTrue(source.contains("fragmentManager.isStateSaved"))
        assertEquals(1, Regex("""(?m)^\s*dismiss\(\)\s*$""").findAll(source).count())
    }

    private fun productionSource(relativePath: String): String = findAppModuleRoot()
        .resolve("src/main/java/com/gridee/parking/$relativePath")
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
