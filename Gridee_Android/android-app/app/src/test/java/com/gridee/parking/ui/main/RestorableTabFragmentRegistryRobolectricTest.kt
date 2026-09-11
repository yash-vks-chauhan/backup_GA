package com.gridee.parking.ui.main

import android.content.res.Configuration
import android.os.Bundle
import android.os.Parcel
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.Lifecycle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.android.controller.ActivityController

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [35])
class RestorableTabFragmentRegistryRobolectricTest {

    @Before
    fun resetFactoryCounts() {
        RegistryFactoryProbe.reset()
        RegistryLifecycleProbe.reset()
        UnknownLifecycleProbe.reset()
    }

    @Test
    fun `fresh host creates tabs lazily and adds them with stable tags`() {
        val controller = launchHost()
        val activity = controller.get()

        assertEquals(setOf(HOME), activity.reconciliation.createdTabIds)
        assertEquals(emptySet<Int>(), activity.reconciliation.restoredTabIds)
        assertEquals(1, RegistryFactoryProbe.count(HOME))
        assertEquals(1, rootTabs(activity).size)
        assertCanonicalState(activity, selectedTab = HOME, addedTabs = setOf(HOME))

        val firstBookings = activity.registry.getOrCreate(BOOKINGS)
        val secondBookings = activity.registry.getOrCreate(BOOKINGS)
        assertSame(firstBookings, secondBookings)
        assertEquals(1, RegistryFactoryProbe.count(BOOKINGS))
        assertFalse(firstBookings.isAdded)

        activity.select(BOOKINGS)

        assertEquals(BOOKINGS_TAG, firstBookings.tag)
        assertCanonicalState(
            activity,
            selectedTab = BOOKINGS,
            addedTabs = setOf(HOME, BOOKINGS),
        )
        ALL_TABS.forEach { tabId ->
            assertEquals(TAG_BY_TAB.getValue(tabId), activity.registry.stableTagFor(tabId))
        }

        controller.pause().stop().destroy()
    }

    @Test
    fun `full parcelled restoration rebinds every fragment without registry factories`() {
        val originalController = launchHost()
        val original = originalController.get()
        addEveryTabAndSelect(original, selectedTab = WALLET)
        val originalByTab = ALL_TABS.associateWith { original.registry.existing(it)!! }
        val savedState = saveParcelAndDestroy(originalController)

        RegistryFactoryProbe.reset()
        RegistryLifecycleProbe.reset()
        val restoredController = launchHost(savedState)
        val restored = restoredController.get()

        assertEquals(ALL_TABS, restored.reconciliation.restoredTabIds)
        assertTrue(restored.reconciliation.createdTabIds.isEmpty())
        assertEquals(0, restored.reconciliation.removedDuplicateCount)
        assertEquals(0, RegistryFactoryProbe.total())
        ALL_TABS.forEach { tabId ->
            val fragment = restored.registry.existing(tabId)!!
            assertNotSame(originalByTab.getValue(tabId), fragment)
            assertSame(fragment, restored.supportFragmentManager.findFragmentByTag(TAG_BY_TAB.getValue(tabId)))
        }
        assertCanonicalState(restored, selectedTab = WALLET, addedTabs = ALL_TABS)

        restoredController.pause().stop().destroy()
    }

    @Test
    fun `process restoration never resumes an inactive or duplicate root tab`() {
        val originalController = launchHost()
        addEveryTabAndSelect(originalController.get(), selectedTab = BOOKINGS)
        val savedState = saveParcelAndDestroy(originalController)

        RegistryFactoryProbe.reset()
        RegistryLifecycleProbe.reset()
        val restoredController = launchHost(savedState)
        val restored = restoredController.get()

        assertEquals(1, RegistryLifecycleProbe.resumeCount(BOOKINGS))
        assertEquals(0, RegistryLifecycleProbe.resumeCount(HOME))
        assertEquals(0, RegistryLifecycleProbe.resumeCount(WALLET))
        assertEquals(0, RegistryLifecycleProbe.resumeCount(PROFILE))
        ALL_TABS.forEach { tabId ->
            assertEquals(1, RegistryLifecycleProbe.viewCreateCount(tabId))
        }
        assertCanonicalState(restored, selectedTab = BOOKINGS, addedTabs = ALL_TABS)

        restoredController.pause().stop().destroy()
    }

    @Test
    fun `partial restoration reuses present tabs and creates only a missing tab on demand`() {
        val originalController = launchHost()
        val original = originalController.get()
        original.select(BOOKINGS)
        val savedState = saveParcelAndDestroy(originalController)

        RegistryFactoryProbe.reset()
        val restoredController = launchHost(savedState)
        val restored = restoredController.get()

        assertEquals(setOf(HOME, BOOKINGS), restored.reconciliation.restoredTabIds)
        assertTrue(restored.reconciliation.createdTabIds.isEmpty())
        assertEquals(0, RegistryFactoryProbe.total())
        assertCanonicalState(
            restored,
            selectedTab = BOOKINGS,
            addedTabs = setOf(HOME, BOOKINGS),
        )

        restored.select(WALLET)

        assertEquals(1, RegistryFactoryProbe.count(WALLET))
        assertEquals(WALLET_TAG, restored.registry.existing(WALLET)!!.tag)
        assertCanonicalState(
            restored,
            selectedTab = WALLET,
            addedTabs = setOf(HOME, BOOKINGS, WALLET),
        )

        restoredController.pause().stop().destroy()
    }

    @Test
    fun `missing saved selection is created while every available restored tab is reused`() {
        val originalController = launchHost()
        originalController.get().forceSavedSelection(PROFILE)
        val savedState = saveParcelAndDestroy(originalController)

        RegistryFactoryProbe.reset()
        RegistryLifecycleProbe.reset()
        val restoredController = launchHost(savedState)
        val restored = restoredController.get()

        assertEquals(setOf(HOME), restored.reconciliation.restoredTabIds)
        assertEquals(setOf(PROFILE), restored.reconciliation.createdTabIds)
        assertEquals(1, RegistryFactoryProbe.count(PROFILE))
        assertEquals(0, RegistryFactoryProbe.count(HOME))
        assertCanonicalState(
            restored,
            selectedTab = PROFILE,
            addedTabs = setOf(HOME, PROFILE),
        )

        restoredController.pause().stop().destroy()
    }

    @Test
    fun `legacy tagless restoration is rebound without replacement`() {
        val originalController = launchHost()
        val original = originalController.get()
        val legacyHome = RegistryHomeFragment()
        val legacyBookings = RegistryBookingsFragment()
        original.replaceWithLegacyTabs(
            fragmentsByTab = linkedMapOf(HOME to legacyHome, BOOKINGS to legacyBookings),
            selectedTab = BOOKINGS,
        )
        val savedState = saveParcelAndDestroy(originalController)

        RegistryFactoryProbe.reset()
        val restoredController = launchHost(savedState)
        val restored = restoredController.get()

        assertEquals(setOf(HOME, BOOKINGS), restored.reconciliation.restoredTabIds)
        assertTrue(restored.reconciliation.createdTabIds.isEmpty())
        assertEquals(0, RegistryFactoryProbe.total())
        assertEquals(null, restored.registry.existing(HOME)!!.tag)
        assertEquals(null, restored.registry.existing(BOOKINGS)!!.tag)
        assertCanonicalState(
            restored,
            selectedTab = BOOKINGS,
            addedTabs = setOf(HOME, BOOKINGS),
        )

        restoredController.pause().stop().destroy()
    }

    @Test
    fun `stable tagged fragment wins and restored legacy duplicate is removed`() {
        val originalController = launchHost()
        val original = originalController.get()
        val canonical = original.registry.existing(HOME)!!
        original.addLegacyDuplicate(tabId = HOME, selected = true)
        val savedState = saveParcelAndDestroy(originalController)

        RegistryFactoryProbe.reset()
        RegistryLifecycleProbe.reset()
        val restoredController = launchHost(savedState)
        val restored = restoredController.get()
        val restoredHome = restored.registry.existing(HOME)!!

        assertEquals(setOf(HOME), restored.reconciliation.restoredTabIds)
        assertTrue(restored.reconciliation.createdTabIds.isEmpty())
        assertEquals(1, restored.reconciliation.removedDuplicateCount)
        assertEquals(0, RegistryFactoryProbe.total())
        assertEquals(HOME_TAG, restoredHome.tag)
        assertNotSame(canonical, restoredHome)
        assertEquals(
            1,
            rootTabs(restored).count { it.javaClass == RegistryHomeFragment::class.java },
        )
        // Reconciliation happens before START/view creation, where production tabs register
        // observers and launch initial data loads. The removed duplicate never reaches that work.
        assertEquals(1, RegistryLifecycleProbe.viewCreateCount(HOME))
        assertEquals(1, RegistryLifecycleProbe.resumeCount(HOME))
        assertCanonicalState(restored, selectedTab = HOME, addedTabs = setOf(HOME))

        restoredController.pause().stop().destroy()
    }

    @Test
    fun `three process-like recreations never accumulate tabs or invoke registry factories`() {
        var controller = launchHost()
        addEveryTabAndSelect(controller.get(), selectedTab = PROFILE)

        repeat(3) {
            val savedState = saveParcelAndDestroy(controller)
            RegistryFactoryProbe.reset()
            controller = launchHost(savedState)
            val restored = controller.get()

            assertEquals(0, RegistryFactoryProbe.total())
            assertEquals(4, rootTabs(restored).size)
            assertEquals(4, rootTabs(restored).map { it.tag }.toSet().size)
            assertCanonicalState(restored, selectedTab = PROFILE, addedTabs = ALL_TABS)
        }

        controller.pause().stop().destroy()
    }

    @Test
    fun `configuration recreation retains one canonical instance per tab and saved selection`() {
        val controller = launchHost()
        val original = controller.get()
        addEveryTabAndSelect(original, selectedTab = BOOKINGS)

        RegistryFactoryProbe.reset()
        val changedConfiguration = Configuration(original.resources.configuration).apply {
            orientation = if (orientation == Configuration.ORIENTATION_LANDSCAPE) {
                Configuration.ORIENTATION_PORTRAIT
            } else {
                Configuration.ORIENTATION_LANDSCAPE
            }
        }
        controller.configurationChange(changedConfiguration)
        val recreated = controller.get()

        assertNotSame(original, recreated)
        assertEquals(0, RegistryFactoryProbe.total())
        assertEquals(ALL_TABS, recreated.reconciliation.restoredTabIds)
        assertCanonicalState(recreated, selectedTab = BOOKINGS, addedTabs = ALL_TABS)

        controller.pause().stop().destroy()
    }

    @Test
    fun `night mode recreation retains one canonical instance per tab and saved selection`() {
        val controller = launchHost()
        val original = controller.get()
        addEveryTabAndSelect(original, selectedTab = WALLET)

        RegistryFactoryProbe.reset()
        val changedConfiguration = Configuration(original.resources.configuration).apply {
            val currentNightMode = uiMode and Configuration.UI_MODE_NIGHT_MASK
            val nextNightMode = if (currentNightMode == Configuration.UI_MODE_NIGHT_YES) {
                Configuration.UI_MODE_NIGHT_NO
            } else {
                Configuration.UI_MODE_NIGHT_YES
            }
            uiMode = (uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or nextNightMode
        }
        controller.configurationChange(changedConfiguration)
        val recreated = controller.get()

        assertNotSame(original, recreated)
        assertEquals(0, RegistryFactoryProbe.total())
        assertEquals(ALL_TABS, recreated.reconciliation.restoredTabIds)
        assertCanonicalState(recreated, selectedTab = WALLET, addedTabs = ALL_TABS)

        controller.pause().stop().destroy()
    }

    @Test
    fun `low memory followed by process recreation retains canonical tabs without new factories`() {
        val originalController = launchHost()
        val original = originalController.get()
        addEveryTabAndSelect(original, selectedTab = PROFILE)
        original.onLowMemory()
        val savedState = saveParcelAndDestroy(originalController)

        RegistryFactoryProbe.reset()
        val restoredController = launchHost(savedState)
        val restored = restoredController.get()

        assertEquals(0, RegistryFactoryProbe.total())
        assertEquals(ALL_TABS, restored.reconciliation.restoredTabIds)
        assertCanonicalState(restored, selectedTab = PROFILE, addedTabs = ALL_TABS)

        restoredController.pause().stop().destroy()
    }

    @Test
    fun `same root-tab class in a foreign container is never rebound or removed`() {
        val originalController = launchHost()
        val original = originalController.get()
        original.addForeignHomeFragment()
        val savedState = saveParcelAndDestroy(originalController)

        RegistryFactoryProbe.reset()
        val restoredController = launchHost(savedState)
        val restored = restoredController.get()

        assertEquals(0, RegistryFactoryProbe.total())
        assertCanonicalState(restored, selectedTab = HOME, addedTabs = setOf(HOME))
        val foreign = restored.supportFragmentManager.findFragmentByTag(FOREIGN_HOME_TAG)
        assertTrue(foreign is RegistryHomeFragment)
        assertTrue(foreign!!.isAdded)
        assertEquals(RegistryHostActivity.FOREIGN_CONTAINER_ID, foreign.id)

        restoredController.pause().stop().destroy()
    }

    @Test
    fun `unregistered restored fragment in the owned root container is removed before view work`() {
        val originalController = launchHost()
        originalController.get().addUnknownRootFragmentAsVisible()
        val savedState = saveParcelAndDestroy(originalController)

        RegistryFactoryProbe.reset()
        RegistryLifecycleProbe.reset()
        UnknownLifecycleProbe.reset()
        val restoredController = launchHost(savedState)
        val restored = restoredController.get()

        assertEquals(1, restored.reconciliation.removedDuplicateCount)
        assertEquals(0, RegistryFactoryProbe.total())
        assertEquals(null, restored.supportFragmentManager.findFragmentByTag(UNKNOWN_ROOT_TAG))
        assertEquals(0, UnknownLifecycleProbe.viewCreateCount)
        assertEquals(0, UnknownLifecycleProbe.resumeCount)
        assertCanonicalState(restored, selectedTab = HOME, addedTabs = setOf(HOME))

        restoredController.pause().stop().destroy()
    }

    private fun launchHost(
        savedState: Bundle? = null,
    ): ActivityController<RegistryHostActivity> =
        Robolectric.buildActivity(RegistryHostActivity::class.java)
            .create(savedState)
            .start()
            .resume()
            .visible()

    private fun saveParcelAndDestroy(
        controller: ActivityController<RegistryHostActivity>,
    ): Bundle {
        controller.pause()
        val savedState = Bundle()
        controller.saveInstanceState(savedState)
        controller.stop().destroy()
        return parcelRoundTrip(savedState)
    }

    private fun parcelRoundTrip(savedState: Bundle): Bundle {
        val parcel = Parcel.obtain()
        return try {
            parcel.writeBundle(savedState)
            parcel.setDataPosition(0)
            requireNotNull(parcel.readBundle(RegistryHostActivity::class.java.classLoader)).also {
                it.classLoader = RegistryHostActivity::class.java.classLoader
            }
        } finally {
            parcel.recycle()
        }
    }

    private fun addEveryTabAndSelect(activity: RegistryHostActivity, selectedTab: Int) {
        ALL_TABS.forEach(activity::select)
        activity.select(selectedTab)
        assertCanonicalState(activity, selectedTab = selectedTab, addedTabs = ALL_TABS)
    }

    private fun assertCanonicalState(
        activity: RegistryHostActivity,
        selectedTab: Int,
        addedTabs: Set<Int>,
    ) {
        val fragments = rootTabs(activity)
        assertEquals(addedTabs.size, fragments.size)

        addedTabs.forEach { tabId ->
            val fragment = requireNotNull(activity.registry.existing(tabId))
            assertTrue(fragment.isAdded)
            assertSame(fragment, fragments.single { it.javaClass == CLASS_BY_TAB.getValue(tabId) })

            if (tabId == selectedTab) {
                assertFalse(fragment.isHidden)
                assertEquals(Lifecycle.State.RESUMED, fragment.lifecycle.currentState)
                assertSame(fragment, activity.supportFragmentManager.primaryNavigationFragment)
            } else {
                assertTrue(fragment.isHidden)
                assertEquals(Lifecycle.State.STARTED, fragment.lifecycle.currentState)
            }
        }
    }

    private fun rootTabs(activity: RegistryHostActivity): List<Fragment> =
        activity.supportFragmentManager.fragments.filter { fragment ->
            fragment.isAdded &&
                fragment.id == RegistryHostActivity.CONTAINER_ID &&
                CLASS_BY_TAB.values.any { fragmentClass -> fragmentClass == fragment.javaClass }
        }
}

class RegistryHostActivity : FragmentActivity() {
    internal lateinit var registry: RestorableTabFragmentRegistry
        private set
    internal lateinit var reconciliation: TabFragmentReconciliation
        private set
    private var selectedTab = HOME

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(FrameLayout(this).apply {
            addView(FrameLayout(context).apply { id = CONTAINER_ID })
            addView(FrameLayout(context).apply { id = FOREIGN_CONTAINER_ID })
        })

        registry = RegistryFactoryProbe.newRegistry()
        selectedTab = savedInstanceState?.getInt(STATE_SELECTED_TAB, HOME) ?: HOME
        reconciliation = registry.reconcileRestoredState(
            fragmentManager = supportFragmentManager,
            containerId = CONTAINER_ID,
            selectedTabId = selectedTab,
        )
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putInt(STATE_SELECTED_TAB, selectedTab)
        super.onSaveInstanceState(outState)
    }

    fun select(tabId: Int) {
        val selected = registry.getOrCreate(tabId)
        val transaction = supportFragmentManager.beginTransaction().setReorderingAllowed(true)

        registry.allBound().forEach { (boundTabId, fragment) ->
            if (!fragment.isAdded && fragment === selected) {
                registry.addTo(transaction, CONTAINER_ID, fragment)
            }
            if (!fragment.isAdded && fragment !== selected) return@forEach

            if (boundTabId == tabId) {
                transaction.show(fragment)
                transaction.setMaxLifecycle(fragment, Lifecycle.State.RESUMED)
            } else {
                transaction.hide(fragment)
                transaction.setMaxLifecycle(fragment, Lifecycle.State.STARTED)
            }
        }
        transaction.setPrimaryNavigationFragment(selected)
        transaction.commitNow()
        selectedTab = tabId
    }

    fun replaceWithLegacyTabs(
        fragmentsByTab: Map<Int, Fragment>,
        selectedTab: Int,
    ) {
        val transaction = supportFragmentManager.beginTransaction().setReorderingAllowed(true)
        supportFragmentManager.fragments.filter { it.isAdded }.forEach(transaction::remove)
        fragmentsByTab.forEach { (tabId, fragment) ->
            transaction.add(CONTAINER_ID, fragment)
            if (tabId == selectedTab) {
                transaction.show(fragment)
                transaction.setMaxLifecycle(fragment, Lifecycle.State.RESUMED)
            } else {
                transaction.hide(fragment)
                transaction.setMaxLifecycle(fragment, Lifecycle.State.STARTED)
            }
        }
        transaction.setPrimaryNavigationFragment(fragmentsByTab.getValue(selectedTab))
        transaction.commitNow()
        this.selectedTab = selectedTab
    }

    fun addLegacyDuplicate(tabId: Int, selected: Boolean) {
        val duplicate = RegistryFactoryProbe.createWithoutCounting(tabId)
        val transaction = supportFragmentManager.beginTransaction().setReorderingAllowed(true)
            .add(CONTAINER_ID, duplicate)

        if (selected) {
            registry.allBound().forEach { (_, fragment) ->
                if (fragment.isAdded) {
                    transaction.hide(fragment)
                    transaction.setMaxLifecycle(fragment, Lifecycle.State.STARTED)
                }
            }
            transaction.show(duplicate)
            transaction.setMaxLifecycle(duplicate, Lifecycle.State.RESUMED)
            transaction.setPrimaryNavigationFragment(duplicate)
            selectedTab = tabId
        }
        transaction.commitNow()
    }

    fun addForeignHomeFragment() {
        supportFragmentManager.beginTransaction()
            .add(FOREIGN_CONTAINER_ID, RegistryHomeFragment(), FOREIGN_HOME_TAG)
            .commitNow()
    }

    fun addUnknownRootFragmentAsVisible() {
        val unknown = RegistryUnknownRootFragment()
        val transaction = supportFragmentManager.beginTransaction().setReorderingAllowed(true)
        registry.allBound().forEach { (_, fragment) ->
            if (fragment.isAdded) {
                transaction.hide(fragment)
                transaction.setMaxLifecycle(fragment, Lifecycle.State.STARTED)
            }
        }
        transaction
            .add(CONTAINER_ID, unknown, UNKNOWN_ROOT_TAG)
            .setMaxLifecycle(unknown, Lifecycle.State.RESUMED)
            .setPrimaryNavigationFragment(unknown)
            .commitNow()
    }

    fun forceSavedSelection(tabId: Int) {
        selectedTab = tabId
    }

    companion object {
        const val CONTAINER_ID = 0x00c0ffee
        const val FOREIGN_CONTAINER_ID = 0x00c0ffef
        private const val STATE_SELECTED_TAB = "registry_test_selected_tab"
    }
}

abstract class RegistryProbeFragment : Fragment() {
    protected abstract val tabId: Int

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View = FrameLayout(requireContext())

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        RegistryLifecycleProbe.recordViewCreate(tabId)
    }

    override fun onResume() {
        super.onResume()
        RegistryLifecycleProbe.recordResume(tabId)
    }
}

class RegistryHomeFragment : RegistryProbeFragment() {
    override val tabId: Int = HOME
}

class RegistryBookingsFragment : RegistryProbeFragment() {
    override val tabId: Int = BOOKINGS
}

class RegistryWalletFragment : RegistryProbeFragment() {
    override val tabId: Int = WALLET
}

class RegistryProfileFragment : RegistryProbeFragment() {
    override val tabId: Int = PROFILE
}

class RegistryUnknownRootFragment : Fragment() {
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View = FrameLayout(requireContext())

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        UnknownLifecycleProbe.viewCreateCount++
    }

    override fun onResume() {
        super.onResume()
        UnknownLifecycleProbe.resumeCount++
    }
}

private object UnknownLifecycleProbe {
    var viewCreateCount = 0
    var resumeCount = 0

    fun reset() {
        viewCreateCount = 0
        resumeCount = 0
    }
}

private object RegistryLifecycleProbe {
    private val resumeCounts = mutableMapOf<Int, Int>()
    private val viewCreateCounts = mutableMapOf<Int, Int>()

    fun reset() {
        resumeCounts.clear()
        viewCreateCounts.clear()
    }

    fun recordResume(tabId: Int) {
        resumeCounts[tabId] = resumeCount(tabId) + 1
    }

    fun resumeCount(tabId: Int): Int = resumeCounts[tabId] ?: 0

    fun recordViewCreate(tabId: Int) {
        viewCreateCounts[tabId] = viewCreateCount(tabId) + 1
    }

    fun viewCreateCount(tabId: Int): Int = viewCreateCounts[tabId] ?: 0
}

private object RegistryFactoryProbe {
    private val counts = mutableMapOf<Int, Int>()

    fun reset() {
        counts.clear()
    }

    fun count(tabId: Int): Int = counts[tabId] ?: 0

    fun total(): Int = counts.values.sum()

    fun newRegistry(): RestorableTabFragmentRegistry = RestorableTabFragmentRegistry(
        listOf(
            spec(HOME, HOME_TAG, RegistryHomeFragment::class.java),
            spec(BOOKINGS, BOOKINGS_TAG, RegistryBookingsFragment::class.java),
            spec(WALLET, WALLET_TAG, RegistryWalletFragment::class.java),
            spec(PROFILE, PROFILE_TAG, RegistryProfileFragment::class.java),
        )
    )

    fun createWithoutCounting(tabId: Int): Fragment = when (tabId) {
        HOME -> RegistryHomeFragment()
        BOOKINGS -> RegistryBookingsFragment()
        WALLET -> RegistryWalletFragment()
        PROFILE -> RegistryProfileFragment()
        else -> error("Unknown test tab: $tabId")
    }

    private fun spec(
        tabId: Int,
        stableTag: String,
        fragmentClass: Class<out Fragment>,
    ): RestorableTabSpec = RestorableTabSpec(
        tabId = tabId,
        stableTag = stableTag,
        fragmentClass = fragmentClass,
    ) {
        counts[tabId] = count(tabId) + 1
        createWithoutCounting(tabId)
    }
}

private const val HOME = 10
private const val BOOKINGS = 20
private const val WALLET = 30
private const val PROFILE = 40

private const val HOME_TAG = "test.tab.home"
private const val BOOKINGS_TAG = "test.tab.bookings"
private const val WALLET_TAG = "test.tab.wallet"
private const val PROFILE_TAG = "test.tab.profile"
private const val FOREIGN_HOME_TAG = "test.foreign.home"
private const val UNKNOWN_ROOT_TAG = "test.legacy.unknown_root"

private val ALL_TABS = linkedSetOf(HOME, BOOKINGS, WALLET, PROFILE)
private val TAG_BY_TAB = mapOf(
    HOME to HOME_TAG,
    BOOKINGS to BOOKINGS_TAG,
    WALLET to WALLET_TAG,
    PROFILE to PROFILE_TAG,
)
private val CLASS_BY_TAB = mapOf(
    HOME to RegistryHomeFragment::class.java,
    BOOKINGS to RegistryBookingsFragment::class.java,
    WALLET to RegistryWalletFragment::class.java,
    PROFILE to RegistryProfileFragment::class.java,
)
