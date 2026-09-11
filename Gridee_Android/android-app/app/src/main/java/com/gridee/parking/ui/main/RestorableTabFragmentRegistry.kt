package com.gridee.parking.ui.main

import androidx.annotation.IdRes
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import androidx.fragment.app.FragmentTransaction
import androidx.lifecycle.Lifecycle

/**
 * Stable identity and a lazy factory for one root tab.
 *
 * [fragmentClass] is deliberately matched exactly. Root-tab subclasses are separate navigation
 * destinations; silently accepting a subclass would make a renamed or incorrectly tagged screen
 * look valid while restoring the wrong state.
 */
internal data class RestorableTabSpec(
    val tabId: Int,
    val stableTag: String,
    val fragmentClass: Class<out Fragment>,
    val factory: () -> Fragment,
)

internal data class RestoredTabCandidate<T>(
    val tabId: Int,
    val hasStableTag: Boolean,
    val isVisible: Boolean,
    val insertionOrder: Int,
    val value: T,
)

internal data class RestoredTabResolution<T>(
    val retainedByTab: Map<Int, T>,
    val duplicates: List<T>,
)

/** Pure selection policy kept separate so legacy/duplicate recovery can be exhaustively tested. */
internal object RestoredTabSelectionPolicy {

    fun <T> resolve(
        candidates: List<RestoredTabCandidate<T>>,
        selectedTabId: Int,
    ): RestoredTabResolution<T> {
        val retained = linkedMapOf<Int, T>()
        val duplicates = mutableListOf<T>()

        candidates.groupBy { it.tabId }.forEach { (tabId, tabCandidates) ->
            // FragmentManager searches its added list from newest to oldest. Preserve that
            // behavior for deployed, tagless saved states, while making a stable tag authoritative
            // as soon as one exists. For the selected legacy tab, prefer its visible instance.
            val newestFirst = tabCandidates.sortedByDescending { it.insertionOrder }
            val tagged = newestFirst.filter { it.hasStableTag }
            val canonical = if (tagged.isNotEmpty()) {
                if (tabId == selectedTabId) {
                    tagged.firstOrNull { it.isVisible } ?: tagged.first()
                } else {
                    tagged.first()
                }
            } else if (tabId == selectedTabId) {
                newestFirst.firstOrNull { it.isVisible } ?: newestFirst.first()
            } else {
                newestFirst.first()
            }

            retained[tabId] = canonical.value
            tabCandidates
                .asSequence()
                .filterNot { it === canonical }
                .mapTo(duplicates) { it.value }
        }

        return RestoredTabResolution(
            retainedByTab = retained,
            duplicates = duplicates,
        )
    }
}

internal data class TabFragmentReconciliation(
    val selectedFragment: Fragment,
    val restoredTabIds: Set<Int>,
    val createdTabIds: Set<Int>,
    val removedDuplicateCount: Int,
)

/**
 * Owns root-tab object identity across FragmentManager save/restore.
 *
 * Android restores fragments during `super.onCreate()`. A host must call
 * [reconcileRestoredState] before asking for any tab so those restored objects are rebound before
 * a factory can create replacements. Existing tagless fragments are accepted for one lifecycle
 * to preserve state from versions shipped before stable tags were introduced. Every new add uses
 * [addTo], which always supplies the stable tag.
 */
internal class RestorableTabFragmentRegistry(
    specs: List<RestorableTabSpec>,
) {
    private val specsByTab: LinkedHashMap<Int, RestorableTabSpec>
    private val specsByTag: Map<String, RestorableTabSpec>
    private val boundByTab = linkedMapOf<Int, Fragment>()

    init {
        require(specs.isNotEmpty()) { "At least one tab specification is required" }
        require(specs.map { it.tabId }.distinct().size == specs.size) {
            "Tab IDs must be unique"
        }
        require(specs.all { it.stableTag.isNotBlank() }) {
            "Stable fragment tags must not be blank"
        }
        require(specs.map { it.stableTag }.distinct().size == specs.size) {
            "Stable fragment tags must be unique"
        }
        require(specs.map { it.fragmentClass }.distinct().size == specs.size) {
            "Root tab fragment classes must be unique"
        }

        specsByTab = LinkedHashMap<Int, RestorableTabSpec>().apply {
            specs.forEach { put(it.tabId, it) }
        }
        specsByTag = specs.associateBy { it.stableTag }
    }

    fun reconcileRestoredState(
        fragmentManager: FragmentManager,
        @IdRes containerId: Int,
        selectedTabId: Int,
    ): TabFragmentReconciliation {
        check(!fragmentManager.isStateSaved) {
            "Root tabs must be reconciled before FragmentManager state is saved"
        }
        specForTab(selectedTabId)

        val reservedTagConflicts = linkedSetOf<Fragment>()
        val unregisteredContainerFragments = linkedSetOf<Fragment>()
        val candidates = fragmentManager.fragments.mapIndexedNotNull { order, fragment ->
            if (!fragment.isAdded || fragment.isRemoving) return@mapIndexedNotNull null
            if (fragment.id != containerId) return@mapIndexedNotNull null

            val spec = specForFragmentOrNull(fragment)
            val reservedTagOwner = fragment.tag?.let(specsByTag::get)
            if (reservedTagOwner != null && reservedTagOwner != spec) {
                reservedTagConflicts += fragment
                return@mapIndexedNotNull null
            }
            if (spec == null) {
                // This registry exclusively owns the activity's root-tab container. A fragment
                // from an older/renamed navigation implementation must not remain visible or
                // RESUMED beside the selected tab after an upgrade restoration. Dialogs have
                // container id 0 and fragments in any foreign container were filtered above.
                unregisteredContainerFragments += fragment
                return@mapIndexedNotNull null
            }

            RestoredTabCandidate(
                tabId = spec.tabId,
                hasStableTag = fragment.tag == spec.stableTag,
                isVisible = !fragment.isHidden,
                insertionOrder = order,
                value = fragment,
            )
        }

        val resolution = RestoredTabSelectionPolicy.resolve(candidates, selectedTabId)
        boundByTab.clear()
        boundByTab.putAll(resolution.retainedByTab)

        val restoredTabIds = boundByTab.keys.toSet()
        val createdTabIds = linkedSetOf<Int>()
        val selected = boundByTab[selectedTabId] ?: create(selectedTabId).also {
            createdTabIds += selectedTabId
        }
        val duplicates = linkedSetOf<Fragment>().apply {
            addAll(resolution.duplicates)
            addAll(reservedTagConflicts)
            addAll(unregisteredContainerFragments)
        }

        val transaction = fragmentManager.beginTransaction().setReorderingAllowed(true)
        duplicates.forEach { duplicate ->
            if (duplicate.isAdded) transaction.remove(duplicate)
        }

        boundByTab.forEach { (tabId, fragment) ->
            if (!fragment.isAdded) {
                addTo(transaction, containerId, fragment)
            }
            if (tabId == selectedTabId) {
                transaction.show(fragment)
                transaction.setMaxLifecycle(fragment, Lifecycle.State.RESUMED)
            } else {
                transaction.hide(fragment)
                transaction.setMaxLifecycle(fragment, Lifecycle.State.STARTED)
            }
        }
        transaction.setPrimaryNavigationFragment(selected)
        transaction.commitNow()

        return TabFragmentReconciliation(
            selectedFragment = selected,
            restoredTabIds = restoredTabIds,
            createdTabIds = createdTabIds,
            removedDuplicateCount = duplicates.size,
        )
    }

    fun getOrCreate(tabId: Int): Fragment = boundByTab[tabId] ?: create(tabId)

    fun existing(tabId: Int): Fragment? = boundByTab[tabId]

    fun allBound(): List<Pair<Int, Fragment>> = specsByTab.keys.mapNotNull { tabId ->
        boundByTab[tabId]?.let { tabId to it }
    }

    fun tabIdFor(fragment: Fragment): Int? = specForFragmentOrNull(fragment)?.tabId

    fun stableTagFor(tabId: Int): String = specForTab(tabId).stableTag

    fun stableTagFor(fragment: Fragment): String = specForFragment(fragment).stableTag

    fun addTo(
        transaction: FragmentTransaction,
        @IdRes containerId: Int,
        fragment: Fragment,
    ): FragmentTransaction = transaction.add(containerId, fragment, stableTagFor(fragment))

    private fun create(tabId: Int): Fragment {
        val spec = specForTab(tabId)
        val fragment = spec.factory()
        check(fragment.javaClass == spec.fragmentClass) {
            "Factory for tab $tabId created ${fragment.javaClass.name}; expected ${spec.fragmentClass.name}"
        }
        boundByTab[tabId] = fragment
        return fragment
    }

    private fun specForTab(tabId: Int): RestorableTabSpec = specsByTab[tabId]
        ?: error("Unknown root tab ID: $tabId")

    private fun specForFragment(fragment: Fragment): RestorableTabSpec =
        specForFragmentOrNull(fragment)
            ?: error("${fragment.javaClass.name} is not a registered root tab")

    private fun specForFragmentOrNull(fragment: Fragment): RestorableTabSpec? =
        specsByTab.values.firstOrNull { it.fragmentClass == fragment.javaClass }
}
