package com.gridee.parking.data.model

import com.google.gson.JsonParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Covers the fields the backend's `CustomAdResponseDto` sends and the client-side rules built on
 * them: platform targeting, scheduling windows and `displayFrequency`.
 */
class CustomAdPayloadParserTest {

    private fun parse(json: String, placement: String = "HOME"): List<CustomAd> =
        CustomAdPayloadParser.parseAds(JsonParser.parseString(json), placement)

    @Test
    fun parsesFullBackendPayload() {
        val ads = parse(
            """
            [{
              "id": "ad_1",
              "title": "Campus offer",
              "subtitle": "Half price parking",
              "clickUrl": "https://gridee.in/offer",
              "imageUrl": "https://cdn.gridee.in/a.png",
              "placement": "HOME",
              "platform": "ANDROID",
              "parkingLotId": "lot_9",
              "organizationId": "org_3",
              "locationId": "loc_5",
              "priority": 7,
              "dismissible": true,
              "displayFrequency": "ONCE_PER_SESSION",
              "impressions": 120,
              "clicks": 8
            }]
            """.trimIndent()
        )

        assertEquals(1, ads.size)
        val ad = ads.first()
        assertEquals("ad_1", ad.id)
        assertEquals("Campus offer", ad.title)
        assertEquals("Half price parking", ad.subtitle)
        assertEquals("https://gridee.in/offer", ad.clickUrl)
        assertEquals("ANDROID", ad.platform)
        assertEquals("lot_9", ad.parkingLotId)
        assertEquals("org_3", ad.organizationId)
        assertEquals("loc_5", ad.locationId)
        assertEquals(7, ad.priority)
        assertEquals("ONCE_PER_SESSION", ad.displayFrequency)
        assertEquals(120L, ad.impressions)
        assertEquals(8L, ad.clicks)
        assertTrue(ad.dismissible)
    }

    @Test
    fun sortsByPriorityDescendingSoHighestPriorityAdWins() {
        val ads = parse(
            """
            [
              {"id": "low",  "imageUrl": "https://cdn.gridee.in/l.png", "priority": 1},
              {"id": "high", "imageUrl": "https://cdn.gridee.in/h.png", "priority": 9},
              {"id": "mid",  "imageUrl": "https://cdn.gridee.in/m.png", "priority": 5}
            ]
            """.trimIndent()
        )

        assertEquals(listOf("high", "mid", "low"), ads.map { it.id })
    }

    @Test
    fun androidAndAllPlatformAdsAreEligibleButIosOnlyIsNot() {
        val ads = parse(
            """
            [
              {"id": "android", "imageUrl": "https://cdn.gridee.in/a.png", "platform": "ANDROID"},
              {"id": "all",     "imageUrl": "https://cdn.gridee.in/b.png", "platform": "ALL"},
              {"id": "ios",     "imageUrl": "https://cdn.gridee.in/c.png", "platform": "IOS"},
              {"id": "unset",   "imageUrl": "https://cdn.gridee.in/d.png"}
            ]
            """.trimIndent()
        ).associateBy { it.id }

        assertTrue(ads.getValue("android").isForAndroid())
        assertTrue(ads.getValue("all").isForAndroid())
        assertFalse(ads.getValue("ios").isForAndroid())
        // No platform stated is not a reason to hide an ad the backend already selected.
        assertTrue(ads.getValue("unset").isForAndroid())
    }

    @Test
    fun scheduleWindowHidesFutureAndExpiredAds() {
        val now = 1_700_000_000_000L
        val ads = parse(
            """
            [
              {"id": "future",  "imageUrl": "https://cdn.gridee.in/a.png",
               "startAt": ${now + 60_000}, "endAt": ${now + 120_000}},
              {"id": "active",  "imageUrl": "https://cdn.gridee.in/b.png",
               "startAt": ${now - 60_000}, "endAt": ${now + 60_000}},
              {"id": "expired", "imageUrl": "https://cdn.gridee.in/c.png",
               "startAt": ${now - 120_000}, "endAt": ${now - 60_000}}
            ]
            """.trimIndent()
        ).associateBy { it.id }

        assertFalse(ads.getValue("future").isLiveAt(now))
        assertTrue(ads.getValue("active").isLiveAt(now))
        assertFalse(ads.getValue("expired").isLiveAt(now))
    }

    @Test
    fun displayFrequencyDefaultsToOncePerSession() {
        val ads = parse(
            """
            [
              {"id": "once",    "imageUrl": "https://cdn.gridee.in/a.png", "displayFrequency": "ONCE_PER_SESSION"},
              {"id": "always",  "imageUrl": "https://cdn.gridee.in/b.png", "displayFrequency": "ALWAYS"},
              {"id": "unset",   "imageUrl": "https://cdn.gridee.in/c.png"},
              {"id": "spelling", "imageUrl": "https://cdn.gridee.in/d.png", "displayFrequency": "once-per-session"}
            ]
            """.trimIndent()
        ).associateBy { it.id }

        assertTrue(ads.getValue("once").isOncePerSession())
        assertFalse(ads.getValue("always").isOncePerSession())
        // Matches the backend default, so an ad that omits the field is not shown repeatedly.
        assertTrue(ads.getValue("unset").isOncePerSession())
        assertTrue(ads.getValue("spelling").isOncePerSession())
    }

    @Test
    fun emptyPayloadYieldsNoAds() {
        assertTrue(parse("[]").isEmpty())
        assertTrue(parse("""{"data": []}""").isEmpty())
        assertTrue(CustomAdPayloadParser.parseAds(null, "HOME").isEmpty())
    }

    @Test
    fun dropsAdsWithoutALoadableCreativeOrMarkedInactive() {
        val ads = parse(
            """
            [
              {"id": "no_image"},
              {"id": "local_scheme", "imageUrl": "file:///data/local.png"},
              {"id": "inactive", "imageUrl": "https://cdn.gridee.in/a.png", "active": false},
              {"id": "good", "imageUrl": "https://cdn.gridee.in/b.png"}
            ]
            """.trimIndent()
        )

        assertEquals(listOf("good"), ads.map { it.id })
    }

    @Test
    fun rejectsNonHttpClickTargets() {
        val ad = parse(
            """[{"id": "a", "imageUrl": "https://cdn.gridee.in/a.png", "clickUrl": "intent://evil"}]"""
        ).single()

        assertNull(ad.clickUrl)
    }
}
