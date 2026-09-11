package com.gridee.parking.ui.bottomsheet

import android.os.Parcel
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.Lifecycle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [35])
class VehicleActionBottomSheetRestorationTest {

    @Test
    fun `add vehicle pending action survives parcelled fragment state`() {
        val original = AddVehicleBottomSheet.newInstance(listOf("KA01AB1234"))
        val originalHost = attachAtCreated(original, AddVehicleBottomSheet.TAG)
        original.setPrivateField("pendingVehicleNumber", "MH12CD5678")

        val recreated = AddVehicleBottomSheet.newInstance(listOf("KA01AB1234"))
        recreated.setInitialSavedState(parcelRoundTrip(savedState(originalHost, original)))
        attachAtCreated(recreated, AddVehicleBottomSheet.TAG)

        assertEquals("MH12CD5678", recreated.privateField<String?>("pendingVehicleNumber"))
        assertFalse(recreated.privateField("actionDelivered"))
    }

    @Test
    fun `vehicle edit pending action and destination survive parcelled fragment state`() {
        val original = VehicleOptionsBottomSheet.newInstance(
            vehicleNumber = "KA01AB1234",
            existingVehicleNumbers = listOf("KA01AB1234"),
            isDefault = false,
        )
        val originalHost = attachAtCreated(original, VehicleOptionsBottomSheet.TAG)
        original.setPrivateField("pendingAction", VehicleOptionsBottomSheet.ACTION_EDIT)
        original.setPrivateField("pendingNewVehicleNumber", "MH12CD5678")

        val recreated = VehicleOptionsBottomSheet.newInstance(
            vehicleNumber = "KA01AB1234",
            existingVehicleNumbers = listOf("KA01AB1234"),
            isDefault = false,
        )
        recreated.setInitialSavedState(parcelRoundTrip(savedState(originalHost, original)))
        attachAtCreated(recreated, VehicleOptionsBottomSheet.TAG)

        assertEquals(
            VehicleOptionsBottomSheet.ACTION_EDIT,
            recreated.privateField<String?>("pendingAction"),
        )
        assertEquals(
            "MH12CD5678",
            recreated.privateField<String?>("pendingNewVehicleNumber"),
        )
        assertFalse(recreated.privateField("actionDelivered"))
    }

    private fun attachAtCreated(fragment: Fragment, tag: String): FragmentActivity {
        val activity = Robolectric.buildActivity(FragmentActivity::class.java).setup().get()
        activity.supportFragmentManager.beginTransaction()
            .add(fragment, tag)
            .setMaxLifecycle(fragment, Lifecycle.State.CREATED)
            .commitNow()
        return activity
    }

    private fun savedState(
        host: FragmentActivity,
        fragment: Fragment,
    ): Fragment.SavedState = requireNotNull(
        host.supportFragmentManager.saveFragmentInstanceState(fragment),
    )

    private fun parcelRoundTrip(savedState: Fragment.SavedState): Fragment.SavedState {
        val parcel = Parcel.obtain()
        return try {
            parcel.writeParcelable(savedState, 0)
            parcel.setDataPosition(0)
            @Suppress("DEPRECATION")
            requireNotNull(parcel.readParcelable(Fragment.SavedState::class.java.classLoader))
        } finally {
            parcel.recycle()
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun <T> Any.privateField(name: String): T =
        javaClass.getDeclaredField(name).run {
            isAccessible = true
            get(this@privateField) as T
        }

    private fun Any.setPrivateField(name: String, value: Any?) {
        javaClass.getDeclaredField(name).run {
            isAccessible = true
            set(this@setPrivateField, value)
        }
    }
}
