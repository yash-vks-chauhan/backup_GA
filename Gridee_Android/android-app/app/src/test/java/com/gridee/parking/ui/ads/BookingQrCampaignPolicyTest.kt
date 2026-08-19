package com.gridee.parking.ui.ads

import com.gridee.parking.data.model.CustomAd
import org.junit.Assert.assertEquals
import org.junit.Test

class BookingQrCampaignPolicyTest {

    @Test
    fun `qr campaigns are capped at five and deduplicated`() {
        val selected = BookingQrCampaignPolicy.select(
            listOf(
                ad("1"), ad("2"), ad("2"), ad("3"), ad("4"), ad("5"), ad("6")
            )
        )

        assertEquals(listOf("1", "2", "3", "4", "5"), selected.map { it.id })
    }

    @Test
    fun `home campaigns are used only when qr placement is empty`() {
        assertEquals(
            listOf("home"),
            BookingQrCampaignPolicy.select(emptyList(), listOf(ad("home"))).map { it.id }
        )
        assertEquals(
            listOf("qr"),
            BookingQrCampaignPolicy.select(listOf(ad("qr")), listOf(ad("home"))).map { it.id }
        )
    }

    @Test
    fun `higher priority campaigns appear first`() {
        assertEquals(
            listOf("high", "low"),
            BookingQrCampaignPolicy.select(
                listOf(ad("low", priority = 1), ad("high", priority = 10))
            ).map { it.id }
        )
    }


    private fun ad(id: String, priority: Int = 0) = CustomAd(
        id = id,
        placement = CustomAdPlacement.BOOKING_QR_PASS,
        imageUrl = "https://example.com/$id.jpg",
        priority = priority
    )
}
