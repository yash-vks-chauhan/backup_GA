package com.gridee.parking.ui.main

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Guards the activity wiring around the separately exercised registry implementation. */
class MainContainerRestorationContractTest {

    @Test
    fun `all root tabs have unique stable tags and restoration runs before routing`() {
        val source = mainContainerSource()
        val tags = Regex("""internal const val TAB_TAG_[A-Z_]+ = \"([^\"]+)\"""")
            .findAll(source)
            .map { it.groupValues[1] }
            .toList()

        assertEquals(4, tags.size)
        assertEquals(4, tags.distinct().size)
        listOf(
            "HomeFragment::class.java",
            "BookingsFragmentNew::class.java",
            "WalletFragmentNew::class.java",
            "ProfileFragment::class.java",
        ).forEach { fragmentClass -> assertTrue(source.contains(fragmentClass)) }

        val reconciliation = source.indexOf(
            "currentFragment = tabFragments.reconcileRestoredState(",
        )
        val intentRouting = source.indexOf("handleNavigationIntent(intent, currentTabId)")
        assertTrue(reconciliation >= 0)
        assertTrue(intentRouting > reconciliation)
        assertTrue(source.contains("savedInstanceState.getInt(STATE_CURRENT_TAB"))
        assertTrue(source.contains("outState.putInt(STATE_CURRENT_TAB, currentTabId)"))
    }

    @Test
    fun `tabs are created only by restoration or direct user navigation`() {
        val source = mainContainerSource()

        assertFalse(source.contains("private val homeFragment by lazy"))
        assertFalse(source.contains("private val bookingsFragment by lazy"))
        assertFalse(source.contains("private val walletFragment by lazy"))
        assertFalse(source.contains("private val profileFragment by lazy"))
        assertFalse(source.contains("findFragmentById(R.id.fragment_container)"))
        assertFalse(source.contains("commitNowAllowingStateLoss"))
        assertFalse(source.contains("commitAllowingStateLoss"))
        assertFalse(source.contains("prewarmInactiveFragment"))
        assertFalse(source.contains("delayed prewarming"))

        val startupPath = source
            .substringAfter("override fun onCreate(savedInstanceState: Bundle?)")
            .substringBefore("override fun onResume()")
        assertEquals(1, Regex("tabFragments\\.reconcileRestoredState\\(").findAll(startupPath).count())
        assertFalse(startupPath.contains("getFragmentForTab(CustomBottomNavigation.TAB_BOOKINGS)"))
        assertFalse(startupPath.contains("getFragmentForTab(CustomBottomNavigation.TAB_WALLET)"))
        assertFalse(startupPath.contains("getFragmentForTab(CustomBottomNavigation.TAB_PROFILE)"))

        val switchPath = source
            .substringAfter("private fun switchToFragment")
            .substringBefore("// ── Partner referral chip")
        assertTrue(switchPath.contains("supportFragmentManager.isStateSaved"))
        assertTrue(switchPath.contains("tabFragments.addTo"))
        assertTrue(switchPath.contains("setPrimaryNavigationFragment"))
        assertTrue(switchPath.contains("Lifecycle.State.RESUMED"))
        assertTrue(switchPath.contains("Lifecycle.State.STARTED"))

        val swipePath = source
            .substringAfter("override fun onSwipeBegin(forward: Boolean)")
            .substringBefore("override fun onSwipeProgress(progress: Float)")
        assertTrue(swipePath.contains("getFragmentForTab(targetTabId)"))
        assertTrue(swipePath.contains("tabFragments.addTo"))
    }

    private fun mainContainerSource(): String = findAppModuleRoot()
        .resolve("src/main/java/com/gridee/parking/ui/main/MainContainerActivity.kt")
        .readText()

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
