package com.gridee.parking.ui.base

import android.os.SystemClock
import android.view.View
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.swipeUp
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.gridee.parking.debug.ScrollListenerProbeActivity
import java.lang.ref.WeakReference
import java.lang.ref.ReferenceQueue
import org.hamcrest.Matchers.allOf
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** The target has its own application ID and no Internet permission, services or account data. */
@RunWith(AndroidJUnit4::class)
class ScrollListenerDeviceTest {
    @Test
    fun gesturesStillDeliverOneCallbackPerChangedWindowEventAfter100TabSwitches() {
        ActivityScenario.launch(ScrollListenerProbeActivity::class.java).use { scenario ->
            scenario.onActivity { it.bindSelected() }
            assertGestureDelivery(scenario, home = true)
            repeat(100) { index ->
                scenario.onActivity {
                    it.select((index + 1) % 4)
                    it.bindSelected()
                }
            }
            for (tab in 0..3) {
                scenario.onActivity { it.select(tab) }
                assertGestureDelivery(scenario, home = tab == 0)
            }
        }
    }

    @Test
    fun destroyedViewTreeAndOwnerAreGarbageCollectedWhileHostAndBindingsStayAlive() {
        ActivityScenario.launch(ScrollListenerProbeActivity::class.java).use { scenario ->
            scenario.onActivity { it.bindSelected() }
            var watched: List<WeakReference<Any>> = emptyList()
            val queue = ReferenceQueue<Any>()
            scenario.onActivity { watched = it.detachAndWatch(queue) }
            InstrumentationRegistry.getInstrumentation().waitForIdleSync()
            val deadline = SystemClock.uptimeMillis() + 5000
            var collected = 0
            // Do not call WeakReference.get() before GC: an ART temporary register can itself
            // retain the referent while the test requests collection. Observe the queue instead.
            while (SystemClock.uptimeMillis() < deadline && collected < watched.size) {
                Runtime.getRuntime().gc()
                Runtime.getRuntime().runFinalization()
                SystemClock.sleep(100)
                while (queue.poll() != null) collected++
            }
            assertTrue("Obsolete root, child or view lifecycle owner retained: ${watched.map { it.get()?.javaClass?.simpleName }}",
                collected == watched.size)
            scenario.onActivity { assertEquals(Lifecycle.State.RESUMED, it.lifecycle.currentState) }
        }
    }

    @Test
    fun pauseResumeAndActivityRecreationPreserveScrollDelivery() {
        ActivityScenario.launch(ScrollListenerProbeActivity::class.java).use { scenario ->
            scenario.onActivity { it.select(2) }
            scenario.moveToState(Lifecycle.State.CREATED)
            scenario.moveToState(Lifecycle.State.RESUMED)
            assertGestureDelivery(scenario, home = false, phase = "after resume")
            var oldRoot: WeakReference<View>? = null
            scenario.onActivity { oldRoot = WeakReference(it.scroll) }
            scenario.recreate()
            scenario.onActivity {
                it.bindSelected()
                assertEquals(2, it.selected)
                assertNotSame(oldRoot?.get(), it.scroll)
            }
            assertGestureDelivery(scenario, home = false, phase = "after recreation")
        }
    }

    private fun assertGestureDelivery(scenario: ActivityScenario<ScrollListenerProbeActivity>, home: Boolean, phase: String = "tab switch") {
        scenario.onActivity {
            // A previous upward fling can end at the bottom and its offset survives recreation.
            // Start every measured gesture with a real scrollable range in the same direction.
            it.scroll.fling(0)
            it.scroll.scrollTo(0, 0)
            it.bindSelected()
        }
        InstrumentationRegistry.getInstrumentation().waitForIdleSync()
        var delivered = false
        for (attempt in 0..2) {
            scenario.onActivity { it.resetMeasurements() }
            onView(allOf(withId(ScrollListenerProbeActivity.SCROLL_ID), isDisplayed())).perform(swipeUp())
            InstrumentationRegistry.getInstrumentation().waitForIdleSync()
            scenario.onActivity { delivered = it.scroll.touchEvents > 0 }
            if (delivered) break
            // Samsung's activity transition can still intercept injected input after the
            // Activity is RESUMED and focused. Retry only when the view received NO touch;
            // a delivered gesture with missing/duplicate scroll callbacks must still fail.
            SystemClock.sleep(250)
        }
        assertTrue("Injected gesture never reached the selected view $phase", delivered)
        scenario.onActivity {
            assertTrue("No observed scroll $phase: tab=${it.selected}, y=${it.scroll.scrollY}, callbacks=${it.scrollCallbacks}, height=${it.scroll.height}, childHeight=${it.scroll.getChildAt(0).height}, touches=${it.scroll.touchEvents}, focus=${it.scroll.hasWindowFocus()}, layoutPending=${it.scroll.isLayoutRequested}",
                it.changedWindowEvents > 0)
            assertEquals(it.changedWindowEvents, it.scrollCallbacks)
            assertEquals(if (home) it.changedWindowEvents else 0, it.referralCallbacks)
        }
    }
}
