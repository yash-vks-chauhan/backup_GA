package com.gridee.parking.ui.wallet

import android.content.DialogInterface
import android.os.Parcel
import android.os.SystemClock
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.Lifecycle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [35])
class PaymentOutcomeBottomSheetRestorationTest {

    @Test
    fun `latest paid outcome survives a parcelled saved-state restoration`() {
        val original = PaymentOutcomeBottomSheet.newInstance(
            outcome = PaymentOutcomeBottomSheet.Outcome.PENDING,
            amount = 750.0,
            orderId = "order-restore-1",
        )
        val originalHost = attachAtCreated(original)
        original.renderOutcome(PaymentOutcomeBottomSheet.Outcome.PAID)

        val savedState = requireNotNull(
            originalHost.supportFragmentManager.saveFragmentInstanceState(original),
        )

        val recreated = PaymentOutcomeBottomSheet.newInstance(
            outcome = PaymentOutcomeBottomSheet.Outcome.PENDING,
            amount = 750.0,
            orderId = "order-restore-1",
        )
        recreated.setInitialSavedState(parcelRoundTrip(savedState))
        attachAtCreated(recreated)

        assertEquals(PaymentOutcomeBottomSheet.Outcome.PAID, recreated.privateField("outcome"))
        assertTrue(recreated.privateField("settled"))
    }

    @Test
    fun `a newly created paid outcome is settled before its view is inflated`() {
        val sheet = PaymentOutcomeBottomSheet.newInstance(
            outcome = PaymentOutcomeBottomSheet.Outcome.PAID,
            amount = 125.0,
            orderId = "order-paid-1",
        )

        attachAtCreated(sheet)

        assertEquals(PaymentOutcomeBottomSheet.Outcome.PAID, sheet.privateField("outcome"))
        assertTrue(sheet.privateField("settled"))
    }

    @Test
    fun `reported configuration teardown does not finish through obsolete host callback`() {
        val controller = Robolectric.buildActivity(PaymentOutcomeConfigHostActivity::class.java)
            .setup()
        val originalHost = controller.get()
        var obsoleteCallbackCount = 0
        val sheet = PaymentOutcomeBottomSheet.newInstance(
            outcome = PaymentOutcomeBottomSheet.Outcome.PENDING,
            amount = 320.0,
            orderId = "order-config-1",
        ).apply {
            onFinished = { obsoleteCallbackCount += 1 }
        }
        originalHost.supportFragmentManager.beginTransaction()
            .add(sheet, PaymentOutcomeBottomSheet.TAG)
            .setMaxLifecycle(sheet, Lifecycle.State.CREATED)
            .commitNow()
        originalHost.changingConfigurationsForTest = true
        sheet.onDismiss(object : DialogInterface {
            override fun cancel() = Unit
            override fun dismiss() = Unit
        })

        assertEquals(0, obsoleteCallbackCount)
        controller.destroy()
    }

    @Test
    fun `one shot finish delivery flag survives parcelled saved state`() {
        val original = PaymentOutcomeBottomSheet.newInstance(
            outcome = PaymentOutcomeBottomSheet.Outcome.CANCELLED,
            amount = 320.0,
            orderId = "order-finished-1",
        )
        val originalHost = attachAtCreated(original)
        original.setPrivateField("finishDelivered", true)
        val savedState = requireNotNull(
            originalHost.supportFragmentManager.saveFragmentInstanceState(original),
        )

        val recreated = PaymentOutcomeBottomSheet.newInstance(
            outcome = PaymentOutcomeBottomSheet.Outcome.CANCELLED,
            amount = 320.0,
            orderId = "order-finished-1",
        )
        recreated.setInitialSavedState(parcelRoundTrip(savedState))
        attachAtCreated(recreated)

        assertTrue(recreated.privateField("finishDelivered"))
    }

    @Test
    fun `manual recheck cooldown timestamp survives parcelled saved state`() {
        val original = PaymentOutcomeBottomSheet.newInstance(
            outcome = PaymentOutcomeBottomSheet.Outcome.PENDING,
            amount = 320.0,
            orderId = "order-cooldown-1",
        )
        val originalHost = attachAtCreated(original)
        val checkedAt = SystemClock.elapsedRealtime()
        original.setPrivateField("lastManualCheckAtElapsedMs", checkedAt)
        val savedState = requireNotNull(
            originalHost.supportFragmentManager.saveFragmentInstanceState(original),
        )

        val recreated = PaymentOutcomeBottomSheet.newInstance(
            outcome = PaymentOutcomeBottomSheet.Outcome.PENDING,
            amount = 320.0,
            orderId = "order-cooldown-1",
        )
        recreated.setInitialSavedState(parcelRoundTrip(savedState))
        attachAtCreated(recreated)

        assertEquals(checkedAt, recreated.privateField<Long>("lastManualCheckAtElapsedMs"))
    }

    @Suppress("UNCHECKED_CAST")
    private fun <T> PaymentOutcomeBottomSheet.privateField(name: String): T =
        javaClass.getDeclaredField(name).run {
            isAccessible = true
            get(this@privateField) as T
        }

    private fun PaymentOutcomeBottomSheet.setPrivateField(name: String, value: Any) {
        javaClass.getDeclaredField(name).run {
            isAccessible = true
            set(this@setPrivateField, value)
        }
    }

    private fun attachAtCreated(sheet: PaymentOutcomeBottomSheet): FragmentActivity {
        val activity = Robolectric.buildActivity(FragmentActivity::class.java).setup().get()
        activity.supportFragmentManager.beginTransaction()
            .add(sheet, PaymentOutcomeBottomSheet.TAG)
            .setMaxLifecycle(sheet, Lifecycle.State.CREATED)
            .commitNow()
        return activity
    }

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
}

class PaymentOutcomeConfigHostActivity : FragmentActivity() {
    var changingConfigurationsForTest = false

    override fun isChangingConfigurations(): Boolean = changingConfigurationsForTest
}
