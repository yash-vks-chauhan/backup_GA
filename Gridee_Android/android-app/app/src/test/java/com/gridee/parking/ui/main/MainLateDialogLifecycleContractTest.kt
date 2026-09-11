package com.gridee.parking.ui.main

import com.gridee.parking.ui.components.CustomBottomNavigation
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MainLateDialogLifecycleContractTest {

    @Test
    fun `unknown restored and intent tab ids normalize to home`() {
        val validTabs = listOf(
            CustomBottomNavigation.TAB_HOME,
            CustomBottomNavigation.TAB_BOOKINGS,
            CustomBottomNavigation.TAB_WALLET,
            CustomBottomNavigation.TAB_PROFILE,
        )
        validTabs.forEach { assertEquals(it, normalizeMainTabId(it)) }

        listOf(-1, Int.MIN_VALUE, Int.MAX_VALUE, 99).forEach { corruptTabId ->
            assertEquals(CustomBottomNavigation.TAB_HOME, normalizeMainTabId(corruptTabId))
        }
    }

    @Test
    fun `signup gift survives the delay and is admitted only while resumed`() {
        val source = mainContainerSource()

        assertTrue(source.contains("savedInstanceState?.getBoolean(STATE_SIGNUP_GIFT_PENDING)"))
        assertTrue(source.contains("outState.putBoolean(STATE_SIGNUP_GIFT_PENDING, signupGiftPending)"))
        assertTrue(source.contains("captureSignupGiftRequest(intent)"))
        assertTrue(source.contains("source.removeExtra(EXTRA_SHOW_SIGNUP_GIFT)"))

        val launch = source
            .substringAfter("private fun launchPendingSignupGiftIfSafe()")
            .substringBefore("private fun requestNotificationPermissionIfNeeded")
        assertTrue(launch.contains("Lifecycle.State.RESUMED"))
        assertTrue(launch.contains("fragmentManager.isDestroyed"))
        assertTrue(launch.contains("fragmentManager.isStateSaved"))
        assertTrue(launch.contains("findFragmentByTag(WelcomeGiftBottomSheet.TAG)"))
        assertTrue(launch.contains("showNow(fragmentManager, WelcomeGiftBottomSheet.TAG)"))
        assertFalse(launch.contains("commitAllowingStateLoss"))

        val postResume = source
            .substringAfter("override fun onPostResume()")
            .substringBefore("override fun onPause()")
        assertTrue(postResume.contains("scheduleSignupGiftIfPossible()"))

        val pause = source
            .substringAfter("override fun onPause()")
            .substringBefore("private fun rememberRenderedTheme")
        assertTrue(pause.contains("removeCallbacks(signupGiftLaunchRunnable)"))
    }

    @Test
    fun `partner referral rejects saved state and rapid duplicate launches`() {
        val source = mainContainerSource()
        val launch = source
            .substringAfter("private fun setupPartnerReferralButton()")
            .substringBefore("private fun setupSwipeGesture()")

        assertTrue(launch.contains("Lifecycle.State.RESUMED"))
        assertTrue(launch.contains("fragmentManager.isDestroyed"))
        assertTrue(launch.contains("fragmentManager.isStateSaved"))
        assertTrue(launch.contains("findFragmentByTag(PartnerReferralBottomSheet.TAG)"))
        assertTrue(launch.contains("showNow("))
        assertFalse(launch.contains(".show("))
    }

    private fun mainContainerSource(): String = findAppModuleRoot()
        .resolve("src/main/java/com/gridee/parking/ui/main/MainContainerActivity.kt")
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
