package com.gridee.parking.utils

/**
 * Per-session ad state: which creatives the user closed and which have already reported an
 * impression. Deliberately in-memory only — a dismissed ad comes back on the next app launch,
 * and impressions are counted once per launch rather than once per scroll.
 */
object CustomAdSession {

    private val dismissedAdIds = mutableSetOf<String>()
    private val impressionsSent = mutableSetOf<String>()
    private val displayedAdIds = mutableSetOf<String>()

    @Synchronized
    fun dismiss(adId: String) {
        dismissedAdIds.add(adId)
    }

    @Synchronized
    fun isDismissed(adId: String): Boolean = dismissedAdIds.contains(adId)

    /** Returns true only for the first call per ad, so the caller can fire tracking exactly once. */
    @Synchronized
    fun markImpressionSent(adId: String): Boolean = impressionsSent.add(adId)

    /**
     * Records that the creative reached the screen. Backs `displayFrequency` — an ad marked
     * ONCE_PER_SESSION is not offered again once this has been called for it.
     */
    @Synchronized
    fun markDisplayed(adId: String) {
        displayedAdIds.add(adId)
    }

    @Synchronized
    fun hasBeenDisplayed(adId: String): Boolean = displayedAdIds.contains(adId)

    @Synchronized
    fun reset() {
        dismissedAdIds.clear()
        impressionsSent.clear()
        displayedAdIds.clear()
    }
}
