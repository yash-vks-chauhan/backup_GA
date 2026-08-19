package com.gridee.parking.ui.ads

import com.gridee.parking.data.model.CustomAd

/**
 * Keeps the QR pass predictable even when the campaign API returns duplicates or too many ads.
 *
 * The pass shows a rail of posters rather than a rotating banner, so there is no rotation to
 * schedule here any more — the user swipes, and every campaign in the rail is a real
 * impression instead of only whichever one happened to be showing.
 */
object BookingQrCampaignPolicy {
    const val MAX_CAMPAIGNS = 5

    fun select(primary: List<CustomAd>, fallback: List<CustomAd> = emptyList()): List<CustomAd> {
        return primary.ifEmpty { fallback }
            .sortedByDescending { it.priority }
            .distinctBy { it.id }
            .take(MAX_CAMPAIGNS)
    }
}
