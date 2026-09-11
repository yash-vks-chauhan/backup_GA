package com.gridee.parking.debug

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver
import android.view.MotionEvent
import android.content.Context
import android.widget.FrameLayout
import androidx.core.widget.NestedScrollView
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.Lifecycle
import com.gridee.parking.ui.base.LifecycleBoundScrollListener
import java.lang.ref.WeakReference
import java.lang.ref.ReferenceQueue

/** Standalone, network-free instrumentation fixture; never included in the production app. */
class ScrollListenerProbeActivity : FragmentActivity() {
    private val scrollBinding = LifecycleBoundScrollListener()
    private val referralBinding = LifecycleBoundScrollListener()
    private lateinit var container: FrameLayout
    var selected = 0
        private set
    var scrollCallbacks = 0
        private set
    var referralCallbacks = 0
        private set
    var changedWindowEvents = 0
        private set
    private var observedY = 0
    private var witnessObserver: ViewTreeObserver? = null
    private val witness = ViewTreeObserver.OnScrollChangedListener {
        val y = scroll.scrollY
        if (y != observedY) {
            observedY = y
            changedWindowEvents++
        }
    }
    val scroll: ProbeScrollView
        get() = supportFragmentManager.findFragmentByTag("probe-$selected")!!.requireView() as ProbeScrollView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        container = FrameLayout(this).apply { id = CONTAINER_ID }
        container.addOnAttachStateChangeListener(object : View.OnAttachStateChangeListener {
            override fun onViewAttachedToWindow(v: View) {
                // The window observer can be replaced across a stop/resume cycle.
                witnessObserver = v.viewTreeObserver.also { it.addOnScrollChangedListener(witness) }
            }

            override fun onViewDetachedFromWindow(v: View) = removeWitness()
        })
        setContentView(container)
        select(savedInstanceState?.getInt("selected") ?: 0)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putInt("selected", selected)
    }

    fun select(id: Int) {
        val fragment = supportFragmentManager.findFragmentByTag("probe-$id") ?: ScrollListenerProbeFragment()
        val tx = supportFragmentManager.beginTransaction()
        supportFragmentManager.fragments.filter { it !== fragment }.forEach {
            tx.hide(it).setMaxLifecycle(it, Lifecycle.State.STARTED)
        }
        if (!fragment.isAdded) tx.add(CONTAINER_ID, fragment, "probe-$id")
        tx.show(fragment).setMaxLifecycle(fragment, Lifecycle.State.RESUMED)
            .setPrimaryNavigationFragment(fragment).commitNow()
        selected = id
        bindSelected()
        observedY = fragment.view?.scrollY ?: 0
    }

    fun bindSelected() {
        val fragment = supportFragmentManager.findFragmentByTag("probe-$selected") ?: return
        val view = fragment.view ?: return
        scrollBinding.bind(view, fragment.viewLifecycleOwner,
            onScroll = { scrollCallbacks++ }, onActivated = {})
        if (selected == 0) {
            referralBinding.bind(view, fragment.viewLifecycleOwner,
                onScroll = { referralCallbacks++ }, onActivated = {})
        } else referralBinding.clear()
    }

    fun resetMeasurements() {
        scrollCallbacks = 0
        referralCallbacks = 0
        changedWindowEvents = 0
        observedY = scroll.scrollY
        scroll.touchEvents = 0
    }

    /** Keep the Activity, helper instances and detached Fragment alive while watching its old tree. */
    fun detachAndWatch(queue: ReferenceQueue<Any>): List<WeakReference<Any>> {
        val fragment = supportFragmentManager.findFragmentByTag("probe-$selected")!!
        val view = fragment.requireView() as ViewGroup
        val references = listOf(
            WeakReference<Any>(view, queue),
            WeakReference<Any>(view.getChildAt(0), queue),
            WeakReference<Any>(fragment.viewLifecycleOwner, queue),
        )
        removeWitness()
        supportFragmentManager.beginTransaction().detach(fragment).commitNow()
        return references
    }

    override fun onDestroy() {
        scrollBinding.clear()
        referralBinding.clear()
        removeWitness()
        super.onDestroy()
    }

    private fun removeWitness() {
        witnessObserver?.takeIf { it.isAlive }?.removeOnScrollChangedListener(witness)
        witnessObserver = null
    }

    companion object {
        private const val CONTAINER_ID = 0x417240
        const val SCROLL_ID = 0x417241
    }
}

class ScrollListenerProbeFragment : Fragment() {
    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View =
        ProbeScrollView(requireContext()).apply {
            id = ScrollListenerProbeActivity.SCROLL_ID
            isFillViewport = true
            addView(View(context).apply {
                minimumHeight = 12000
                setBackgroundColor(0xffdedede.toInt())
            }, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        }
}

class ProbeScrollView(context: Context) : NestedScrollView(context) {
    var touchEvents = 0
    override fun onTouchEvent(event: MotionEvent): Boolean {
        touchEvents++
        return super.onTouchEvent(event)
    }
}
