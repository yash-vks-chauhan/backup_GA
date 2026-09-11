package com.gridee.parking.ui.main

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RestoredTabSelectionPolicyTest {

    @Test
    fun `stable tag wins over newer legacy duplicate`() {
        val result = resolve(
            candidate(tab = HOME, value = "tagged", tagged = true, order = 0),
            candidate(tab = HOME, value = "legacy-newer", visible = true, order = 1),
            selectedTab = HOME,
        )

        assertEquals("tagged", result.retainedByTab[HOME])
        assertEquals(listOf("legacy-newer"), result.duplicates)
    }

    @Test
    fun `selected legacy tab keeps newest visible restored instance`() {
        val result = resolve(
            candidate(tab = BOOKINGS, value = "old-visible", visible = true, order = 0),
            candidate(tab = BOOKINGS, value = "new-hidden", visible = false, order = 1),
            candidate(tab = BOOKINGS, value = "new-visible", visible = true, order = 2),
            selectedTab = BOOKINGS,
        )

        assertEquals("new-visible", result.retainedByTab[BOOKINGS])
        assertEquals(listOf("old-visible", "new-hidden"), result.duplicates)
    }

    @Test
    fun `inactive legacy tab keeps newest restored instance`() {
        val result = resolve(
            candidate(tab = WALLET, value = "old", visible = true, order = 0),
            candidate(tab = WALLET, value = "new", visible = false, order = 3),
            selectedTab = HOME,
        )

        assertEquals("new", result.retainedByTab[WALLET])
        assertEquals(listOf("old"), result.duplicates)
    }

    @Test
    fun `each tab resolves independently and every noncanonical instance is removed`() {
        val result = resolve(
            candidate(tab = PROFILE, value = "profile-legacy", order = 0),
            candidate(tab = HOME, value = "home-old", order = 1),
            candidate(tab = PROFILE, value = "profile-tagged", tagged = true, order = 2),
            candidate(tab = HOME, value = "home-new", visible = true, order = 3),
            selectedTab = HOME,
        )

        assertEquals(
            mapOf(PROFILE to "profile-tagged", HOME to "home-new"),
            result.retainedByTab,
        )
        assertEquals(listOf("profile-legacy", "home-old"), result.duplicates)
    }

    @Test
    fun `fragment insertion order cannot override saved selected tab`() {
        val result = resolve(
            candidate(tab = HOME, value = "home", visible = false, order = 9),
            candidate(tab = BOOKINGS, value = "bookings", visible = true, order = 1),
            candidate(tab = PROFILE, value = "profile", visible = true, order = 99),
            selectedTab = BOOKINGS,
        )

        assertEquals("bookings", result.retainedByTab[BOOKINGS])
        assertEquals(3, result.retainedByTab.size)
        assertTrue(result.duplicates.isEmpty())
    }

    @Test
    fun `empty restoration creates no synthetic policy candidates`() {
        val result = RestoredTabSelectionPolicy.resolve<String>(
            candidates = emptyList(),
            selectedTabId = HOME,
        )

        assertTrue(result.retainedByTab.isEmpty())
        assertTrue(result.duplicates.isEmpty())
    }

    private fun resolve(
        vararg candidates: RestoredTabCandidate<String>,
        selectedTab: Int,
    ): RestoredTabResolution<String> = RestoredTabSelectionPolicy.resolve(
        candidates = candidates.toList(),
        selectedTabId = selectedTab,
    )

    private fun candidate(
        tab: Int,
        value: String,
        tagged: Boolean = false,
        visible: Boolean = false,
        order: Int,
    ) = RestoredTabCandidate(
        tabId = tab,
        hasStableTag = tagged,
        isVisible = visible,
        insertionOrder = order,
        value = value,
    )

    private companion object {
        const val HOME = 0
        const val BOOKINGS = 1
        const val WALLET = 2
        const val PROFILE = 3
    }
}
