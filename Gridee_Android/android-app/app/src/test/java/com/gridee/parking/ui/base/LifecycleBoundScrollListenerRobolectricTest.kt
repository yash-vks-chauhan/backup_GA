package com.gridee.parking.ui.base

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver
import android.widget.FrameLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.core.widget.NestedScrollView
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.android.controller.ActivityController
import org.robolectric.annotation.Config
import org.robolectric.annotation.LooperMode

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [35])
@LooperMode(LooperMode.Mode.PAUSED)
class LifecycleBoundScrollListenerRobolectricTest {
    private val controllers = mutableListOf<ActivityController<ScrollBindingHostActivity>>()

    @After
    fun tearDown() {
        controllers.forEach { it.pause().stop().destroy() }
    }

    @Test
    fun `legacy additive setup grows to 101 callbacks while rebinding stays at one`() {
        val host = launch().get()
        val scroll = host.scroll
        assertTrue("scroll=${scroll.width}x${scroll.height} child=${scroll.getChildAt(0).width}x${scroll.getChildAt(0).height} measured=${scroll.getChildAt(0).measuredHeight}",
            scroll.getChildAt(0).height > scroll.height)
        var legacyCallbacks = 0
        val legacyListeners = List(101) {
            ViewTreeObserver.OnScrollChangedListener { legacyCallbacks++ }.also {
                scroll.viewTreeObserver.addOnScrollChangedListener(it)
            }
        }
        repeat(100) { host.bindSelected() }
        host.callbacks.clear()

        scroll.scrollTo(0, 150)
        scroll.viewTreeObserver.dispatchOnScrollChanged()

        assertEquals(101, legacyCallbacks)
        assertEquals(listOf(0 to 150), host.callbacks)
        legacyListeners.forEach { scroll.viewTreeObserver.removeOnScrollChangedListener(it) }
    }

    @Test
    fun `100 real fragment tab switches preserve one callback and ignore hidden offsets`() {
        val host = launch().get()
        repeat(100) { index ->
            host.select((index + 1) % 4)
            host.bindSelected() // A posted setup and transition completion may both run.
            val hidden = host.tab((host.selected + 1) % 4)
            hidden?.view?.scrollTo(0, 80 + index)
            host.callbacks.clear()

            val y = 200 + index
            host.scroll.scrollTo(0, y)
            host.scroll.viewTreeObserver.dispatchOnScrollChanged()

            assertEquals("switch ${index + 1}", listOf(host.selected to y), host.callbacks)
        }
        assertEquals(4, host.supportFragmentManager.fragments.size)
    }

    @Test
    fun `window notifications from another scrollable do not repeat work`() {
        val host = launch().get()
        host.scroll.scrollTo(0, 120)
        host.scroll.viewTreeObserver.dispatchOnScrollChanged()
        host.callbacks.clear()

        repeat(5) { host.scroll.viewTreeObserver.dispatchOnScrollChanged() }

        assertTrue(host.callbacks.isEmpty())
    }

    @Test
    fun `screen owned scroll listener survives binding and unbinding`() {
        val host = launch().get()
        var screenCallbacks = 0
        host.scroll.setOnScrollChangeListener(NestedScrollView.OnScrollChangeListener { _, _, _, _, _ ->
            screenCallbacks++
        })
        host.bindSelected()
        host.scroll.scrollTo(0, 120)
        host.scroll.viewTreeObserver.dispatchOnScrollChanged()
        host.binding.clear()
        host.scroll.scrollTo(0, 180)
        host.scroll.viewTreeObserver.dispatchOnScrollChanged()

        assertEquals(2, screenCallbacks)
        assertEquals(listOf(0 to 120), host.callbacks)
    }

    @Test
    fun `pause suppresses delivery and resume synchronizes the current offset`() {
        val controller = launch()
        val host = controller.get()
        controller.pause()
        host.scroll.scrollTo(0, 250)
        host.scroll.viewTreeObserver.dispatchOnScrollChanged()
        assertTrue(host.callbacks.isEmpty())

        host.activations.clear()
        controller.resume()
        assertEquals(listOf(0 to 250), host.activations)
        host.scroll.scrollTo(0, 280)
        host.scroll.viewTreeObserver.dispatchOnScrollChanged()
        assertEquals(listOf(0 to 280), host.callbacks)
    }

    @Test
    fun `destroying and recreating a fragment view disconnects the obsolete observer`() {
        val host = launch().get()
        val fragment = host.tab(0)!!
        val oldView = host.scroll
        val oldObserver = oldView.viewTreeObserver
        val oldOwner = fragment.viewLifecycleOwner

        host.supportFragmentManager.beginTransaction().detach(fragment).commitNow()
        assertEquals(Lifecycle.State.DESTROYED, oldOwner.lifecycle.currentState)
        host.callbacks.clear()
        oldView.scrollTo(0, 180)
        if (oldObserver.isAlive) oldObserver.dispatchOnScrollChanged()
        assertTrue(host.callbacks.isEmpty())

        host.supportFragmentManager.beginTransaction().attach(fragment).commitNow()
        host.layout()
        host.bindSelected()
        assertNotSame(oldView, host.scroll)
        assertNotSame(oldOwner, fragment.viewLifecycleOwner)
        host.callbacks.clear()
        host.scroll.scrollTo(0, 220)
        host.scroll.viewTreeObserver.dispatchOnScrollChanged()
        assertEquals(listOf(0 to 220), host.callbacks)
    }

    @Test
    fun `detach and reattach registers on the live observer exactly once`() {
        val host = launch().get()
        val view = host.scroll
        val parent = view.parent as ViewGroup
        val observerBeforeDetach = view.viewTreeObserver
        parent.removeView(view)
        host.callbacks.clear()
        view.scrollTo(0, 110)
        if (observerBeforeDetach.isAlive) observerBeforeDetach.dispatchOnScrollChanged()
        assertTrue(host.callbacks.isEmpty())

        parent.addView(view)
        host.layout()
        host.callbacks.clear()
        host.bindSelected()
        view.scrollTo(0, 240)
        view.viewTreeObserver.dispatchOnScrollChanged()
        assertEquals(listOf(0 to 240), host.callbacks)
    }

    @Test
    fun `a detached view waits for attachment and a stopped owner waits for resume`() {
        val host = launch().get()
        host.binding.clear()
        val owner = ScrollTestOwner(Lifecycle.State.STARTED)
        val view = createScrollView(host)
        val binding = LifecycleBoundScrollListener()
        val activations = mutableListOf<Int>()
        val callbacks = mutableListOf<Int>()
        binding.bind(view, owner, callbacks::add, activations::add)
        owner.registry.currentState = Lifecycle.State.RESUMED
        assertTrue(activations.isEmpty())

        host.container.addView(view)
        host.layout()
        assertEquals(listOf(0), activations)
        view.scrollTo(0, 130)
        view.viewTreeObserver.dispatchOnScrollChanged()
        assertEquals(listOf(130), callbacks)
        owner.registry.currentState = Lifecycle.State.DESTROYED
    }

    @Test
    fun `replacing a binding detaches both the old lifecycle and old window listener`() {
        val host = launch().get()
        host.binding.clear()
        val firstOwner = ScrollTestOwner(Lifecycle.State.RESUMED)
        val secondOwner = ScrollTestOwner(Lifecycle.State.RESUMED)
        val binding = LifecycleBoundScrollListener()
        val firstCallbacks = mutableListOf<Int>()
        val secondCallbacks = mutableListOf<Int>()
        binding.bind(host.scroll, firstOwner, firstCallbacks::add, {})
        assertEquals(1, firstOwner.registry.observerCount)
        binding.bind(host.scroll, secondOwner, secondCallbacks::add, {})
        assertEquals(0, firstOwner.registry.observerCount)
        firstOwner.registry.currentState = Lifecycle.State.DESTROYED

        host.scroll.scrollTo(0, 160)
        host.scroll.viewTreeObserver.dispatchOnScrollChanged()
        assertTrue(firstCallbacks.isEmpty())
        assertEquals(listOf(160), secondCallbacks)
        binding.clear()
        binding.clear()
        assertEquals(0, secondOwner.registry.observerCount)
    }

    @Test
    fun `destroyed owners and unsupported views do not keep an earlier subscription`() {
        val host = launch().get()
        val destroyed = ScrollTestOwner(Lifecycle.State.CREATED)
        destroyed.registry.currentState = Lifecycle.State.DESTROYED
        host.binding.bind(host.scroll, destroyed, { error("destroyed owner delivered") })
        host.scroll.scrollTo(0, 160)
        host.scroll.viewTreeObserver.dispatchOnScrollChanged()
        assertTrue(host.callbacks.isEmpty())

        host.bindSelected()
        host.binding.bind(View(host), host, { error("unsupported view delivered") })
        host.scroll.scrollTo(0, 240)
        host.scroll.viewTreeObserver.dispatchOnScrollChanged()
        assertTrue(host.callbacks.isEmpty())
    }

    @Test
    fun `scroll view and recycler view report their actual vertical offsets`() {
        val host = launch().get()
        host.binding.clear()
        val binding = LifecycleBoundScrollListener()
        val callbacks = mutableListOf<Int>()
        val scrollView = ScrollView(host).apply {
            addView(View(host).apply { minimumHeight = 8000 }, ViewGroup.LayoutParams(600, 8000))
        }
        host.container.addView(scrollView)
        host.layout()
        binding.bind(scrollView, host, callbacks::add, {})
        scrollView.scrollTo(0, 180)
        scrollView.viewTreeObserver.dispatchOnScrollChanged()
        assertEquals(listOf(180), callbacks)

        val recycler = RecyclerView(host).apply {
            layoutManager = LinearLayoutManager(host)
            adapter = object : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
                override fun getItemCount() = 100
                override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
                    object : RecyclerView.ViewHolder(TextView(parent.context).apply {
                        layoutParams = RecyclerView.LayoutParams(600, 100)
                    }) {}
                override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) = Unit
            }
        }
        host.container.addView(recycler)
        host.layout()
        callbacks.clear()
        binding.bind(recycler, host, callbacks::add, {})
        recycler.scrollBy(0, 180)
        recycler.viewTreeObserver.dispatchOnScrollChanged()
        assertTrue(recycler.computeVerticalScrollOffset() > 0)
        assertEquals(listOf(recycler.computeVerticalScrollOffset()), callbacks)
        binding.clear()
    }

    @Test
    fun `activity recreation starts with a fresh binding and a restored view owner`() {
        val controller = launch()
        val original = controller.get()
        original.select(2)
        val previousOwner = original.tab(2)!!.viewLifecycleOwner
        controller.recreate()
        val restored = controller.get()
        restored.layout()
        restored.bindSelected()

        assertNotSame(original, restored)
        assertEquals(Lifecycle.State.DESTROYED, previousOwner.lifecycle.currentState)
        assertEquals(2, restored.selected)
        restored.callbacks.clear()
        restored.scroll.scrollTo(0, 200)
        restored.scroll.viewTreeObserver.dispatchOnScrollChanged()
        assertEquals(listOf(2 to 200), restored.callbacks)
    }

    private fun launch(): ActivityController<ScrollBindingHostActivity> {
        val controller = Robolectric.buildActivity(ScrollBindingHostActivity::class.java)
            .setup().visible()
        controllers += controller
        controller.get().layout()
        controller.get().bindSelected()
        controller.get().callbacks.clear()
        return controller
    }
}

class ScrollBindingHostActivity : FragmentActivity() {
    internal val binding = LifecycleBoundScrollListener()
    val callbacks = mutableListOf<Pair<Int, Int>>()
    val activations = mutableListOf<Pair<Int, Int>>()
    lateinit var container: FrameLayout
    var selected = 0
        private set
    val scroll: NestedScrollView get() = tab(selected)!!.requireView() as NestedScrollView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        container = FrameLayout(this).apply { id = 0x41723 }
        setContentView(container)
        select(savedInstanceState?.getInt("selected") ?: 0)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putInt("selected", selected)
    }

    fun tab(id: Int) = supportFragmentManager.findFragmentByTag("tab-$id")

    fun select(id: Int) {
        val target = tab(id) ?: ScrollBindingTestFragment()
        val transaction = supportFragmentManager.beginTransaction()
        if (!target.isAdded) transaction.add(container.id, target, "tab-$id")
        supportFragmentManager.fragments.filter { it !== target }.forEach {
            transaction.hide(it).setMaxLifecycle(it, Lifecycle.State.STARTED)
        }
        transaction.show(target).setMaxLifecycle(target, Lifecycle.State.RESUMED)
            .setPrimaryNavigationFragment(target).commitNow()
        selected = id
        layout()
        bindSelected()
    }

    fun bindSelected() {
        val fragment = tab(selected) ?: return
        val view = fragment.view ?: return
        val id = selected
        binding.bind(view, fragment.viewLifecycleOwner,
            onScroll = { callbacks += id to it },
            onActivated = { activations += id to it })
    }

    fun layout() {
        // These resource-free hosts have no app theme/window sizing. Size the actual content.
        container.measure(View.MeasureSpec.makeMeasureSpec(600, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(900, View.MeasureSpec.EXACTLY))
        container.layout(0, 0, 600, 900)
    }

    override fun onDestroy() {
        binding.clear()
        super.onDestroy()
    }
}

class ScrollBindingTestFragment : Fragment() {
    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View =
        createScrollView(requireActivity())
}

private fun createScrollView(activity: FragmentActivity) = NestedScrollView(activity).apply {
    addView(View(activity).apply { minimumHeight = 8000 }, ViewGroup.LayoutParams(600, 8000))
}

private class ScrollTestOwner(state: Lifecycle.State) : LifecycleOwner {
    val registry = LifecycleRegistry(this).apply { currentState = state }
    override val lifecycle: Lifecycle get() = registry
}

/** Framework dispatch is hidden from the SDK stubs, but runs unchanged in Robolectric. */
private fun ViewTreeObserver.dispatchOnScrollChanged() {
    javaClass.getDeclaredMethod("dispatchOnScrollChanged").apply { isAccessible = true }.invoke(this)
}
