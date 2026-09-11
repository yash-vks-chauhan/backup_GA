package com.gridee.parking.ui.bottomsheet

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Guards the production Bookings host around the separately tested pass snapshot. */
class BookingPassHostLifecycleContractTest {

    @Test
    fun `bookings admits one pass only from a live resumed view`() {
        val source = bookingsSource()
        val launch = source
            .substringAfter("private fun showBookingQrPass")
            .substringBefore("private fun dismissBookingQrPass")

        assertTrue(launch.contains("Lifecycle.State.RESUMED"))
        assertTrue(launch.contains("fragmentManager.isDestroyed"))
        assertTrue(launch.contains("fragmentManager.isStateSaved"))
        assertTrue(launch.contains("findFragmentByTag(BookingQrPassBottomSheet.TAG)"))
        assertTrue(
            launch.indexOf("findFragmentByTag(BookingQrPassBottomSheet.TAG)") <
                launch.indexOf("BookingQrPassBottomSheet.newInstance"),
        )
        assertTrue(launch.contains("current != null && !current.isRemoving"))
        assertTrue(launch.contains("showNow(fragmentManager, BookingQrPassBottomSheet.TAG)"))
        assertFalse(launch.contains(".show(fragmentManager"))
    }

    @Test
    fun `bookings never discards pass dismissal after fragment state save`() {
        val source = bookingsSource()
        val dismissal = source
            .substringAfter("private fun dismissBookingQrPass")
            .substringBefore("private fun requestBookingStatusRefresh")

        assertTrue(dismissal.contains("fragmentManager.isDestroyed"))
        assertTrue(dismissal.contains("fragmentManager.isStateSaved"))
        assertTrue(dismissal.contains("sheet.dismiss()"))
        assertFalse(dismissal.contains("dismissAllowingStateLoss"))
    }

    private fun bookingsSource(): String = findAppModuleRoot()
        .resolve("src/main/java/com/gridee/parking/ui/fragments/BookingsFragmentNew.kt")
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
