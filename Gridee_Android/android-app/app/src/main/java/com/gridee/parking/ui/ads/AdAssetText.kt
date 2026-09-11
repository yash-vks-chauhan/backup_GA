package com.gridee.parking.ui.ads

import java.util.Locale

/**
 * Networks hand back call-to-action and advertiser assets already shouting — "INSTALL",
 * "SHOP NOW" — and a card that passes that straight through reads as an advertiser's button
 * dropped into the page. Nothing in this app is set in full caps.
 *
 * Only strings with no lowercase at all are touched; anything the network already cased itself
 * is left exactly as it arrived. This is a rendering choice, the same one `textAllCaps` makes
 * in the other direction — the asset's words are unchanged, which is what native policy cares
 * about.
 *
 * Shared by both native renderers. It used to be a private copy inside the Home card, which
 * meant the booking pass shipped shouting CTAs while Home did not.
 */
internal fun String.unshout(): String {
    if (none { it.isLowerCase() } && any { it.isLetter() }) {
        // Every character is already uppercase, so the leading one needs no titlecasing —
        // only the tail comes down. split(" ") can yield empty tokens on a double space,
        // hence the isEmpty guard rather than an unchecked first().
        return split(" ").joinToString(" ") { word ->
            if (word.isEmpty()) word
            else word.first() + word.drop(1).lowercase(Locale.getDefault())
        }
    }
    return this
}

/**
 * Short all-caps names are almost always the brand itself — BMW, ASOS, H&M — so they keep
 * their own casing; only a longer shout gets normalised.
 */
internal fun String.unshoutAdvertiser(): String =
    if (length > 4) unshout() else this
