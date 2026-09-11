package com.gridee.parking.ui.bottomsheet

import android.os.Bundle
import android.os.Parcel
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [35])
class BottomSheetLaunchConfigRobolectricTest {

    @Test
    fun `universal launch configuration survives a parcel recreation`() {
        val original = UniversalBottomSheet.newInstance(
            lottieFileName = "success.json",
            title = "Restored title",
            message = "Restored message",
            buttonText = "Continue",
            isRewardMode = true,
            primaryResultRequestKey = "host.primary",
        )

        val restored = UniversalBottomSheetLaunchConfig.from(
            parcelRoundTrip(requireNotNull(original.arguments)),
        )

        assertEquals("success.json", restored.lottieFileName)
        assertEquals("Restored title", restored.title)
        assertEquals("Restored message", restored.message)
        assertEquals("Continue", restored.buttonText)
        assertTrue(restored.isRewardMode)
        assertEquals("host.primary", restored.primaryResultRequestKey)
    }

    @Test
    fun `universal optional values and standard mode retain safe defaults`() {
        val restored = UniversalBottomSheetLaunchConfig.from(
            parcelRoundTrip(requireNotNull(UniversalBottomSheet.newInstance().arguments)),
        )

        assertNull(restored.lottieFileName)
        assertNull(restored.title)
        assertNull(restored.message)
        assertNull(restored.buttonText)
        assertFalse(restored.isRewardMode)
        assertNull(restored.primaryResultRequestKey)
    }

    @Test
    fun `universal imperative configuration updates its restoration snapshot`() {
        val sheet = UniversalBottomSheet.newInstance()

        sheet.setTitle("Changed title")
        sheet.setMessage("Changed message")
        sheet.setLottieFile("changed.json")
        sheet.setPrimaryButton("Apply", "host.apply")

        val restored = UniversalBottomSheetLaunchConfig.from(
            parcelRoundTrip(requireNotNull(sheet.arguments)),
        )
        assertEquals("Changed title", restored.title)
        assertEquals("Changed message", restored.message)
        assertEquals("changed.json", restored.lottieFileName)
        assertEquals("Apply", restored.buttonText)
        assertEquals("host.apply", restored.primaryResultRequestKey)
    }

    @Test
    fun `reward launch configuration snapshots and restores source geometry`() {
        val sourceRect = intArrayOf(120, 240, 64)
        val original = RewardBottomSheet.newInstance(
            startRect = sourceRect,
            entryPoint = RewardBottomSheet.ENTRY_POINT_HOME,
        )
        sourceRect.fill(-1)

        val restored = RewardBottomSheetLaunchConfig.from(
            parcelRoundTrip(requireNotNull(original.arguments)),
        )

        assertArrayEquals(intArrayOf(120, 240, 64), restored.startRect)
        assertEquals(RewardBottomSheet.ENTRY_POINT_HOME, restored.entryPoint)
    }

    @Test
    fun `reward restoration rejects unsafe geometry and blank analytics entry point`() {
        val malformed = RewardBottomSheetLaunchConfig(
            startRect = intArrayOf(10, 20),
            entryPoint = "",
        ).toBundle()

        val restored = RewardBottomSheetLaunchConfig.from(parcelRoundTrip(malformed))

        assertNull(restored.startRect)
        assertEquals(RewardBottomSheet.ENTRY_POINT_UNKNOWN, restored.entryPoint)
    }

    @Test
    fun `reward source geometry is consumed instead of replayed into a restored window`() {
        val launch = RewardBottomSheetLaunchConfig(
            startRect = intArrayOf(120, 240, 64),
            entryPoint = RewardBottomSheet.ENTRY_POINT_HOME,
        )

        assertArrayEquals(
            intArrayOf(120, 240, 64),
            launch.forFragmentCreation(restoringSavedInstance = false).startRect,
        )
        val restored = launch.forFragmentCreation(restoringSavedInstance = true)
        assertNull(restored.startRect)
        assertEquals(RewardBottomSheet.ENTRY_POINT_HOME, restored.entryPoint)
    }

    @Test
    fun `reward one-shot handoff flags survive saved-state parceling`() {
        val savedState = Bundle()
        RewardBottomSheetOneShotState(
            sourceGeometryConsumed = true,
            coinAirborneResultSent = true,
            dismissResultSent = false,
        ).writeTo(savedState)

        val restored = RewardBottomSheetOneShotState.from(parcelRoundTrip(savedState))

        assertTrue(restored.sourceGeometryConsumed)
        assertTrue(restored.coinAirborneResultSent)
        assertFalse(restored.dismissResultSent)
    }

    @Test
    fun `reward show admission rejects an existing stable tag`() {
        val activity = Robolectric.buildActivity(FragmentActivity::class.java).setup().get()
        activity.supportFragmentManager.beginTransaction()
            .add(Fragment(), RewardBottomSheet.TAG)
            .commitNow()

        assertFalse(
            RewardBottomSheet.newInstance().showIfPossible(activity.supportFragmentManager),
        )
        assertEquals(
            1,
            activity.supportFragmentManager.fragments.count { it.tag == RewardBottomSheet.TAG },
        )
    }

    @Test
    fun `universal show admission rejects a state-saved manager`() {
        val controller = Robolectric.buildActivity(FragmentActivity::class.java).setup()
        val activity = controller.get()
        controller.saveInstanceState(Bundle())

        assertTrue(activity.supportFragmentManager.isStateSaved)
        assertFalse(
            UniversalBottomSheet.newInstance().showIfPossible(activity.supportFragmentManager),
        )
        assertNull(activity.supportFragmentManager.findFragmentByTag(UniversalBottomSheet.TAG))
    }

    private fun parcelRoundTrip(source: Bundle): Bundle {
        val parcel = Parcel.obtain()
        return try {
            parcel.writeBundle(source)
            parcel.setDataPosition(0)
            requireNotNull(parcel.readBundle(javaClass.classLoader))
        } finally {
            parcel.recycle()
        }
    }
}
