package com.gridee.parking.ui.base

/**
 * Implemented by activities that park persistent floating controls in the band directly
 * above the tab bar — on Home that is the portrait ad dock and the partner referral pill.
 *
 * A transient in-app banner claims the same band. Drawing over those controls buried the
 * ad's headline and call-to-action and, because the banner outranks them on elevation, ate
 * the taps meant for the ad too. So the banner reports its footprint instead and the host
 * lifts the dock clear of it for as long as it is on screen.
 */
interface BottomOverlayHost {

    /**
     * @param overlayHeightAboveNavPx how far up from the tab bar's top edge the transient
     *   overlay reaches — its measured height plus its own bottom gap. `0` once nothing is
     *   showing, which returns the dock to rest.
     */
    fun onBottomOverlayHeightChanged(overlayHeightAboveNavPx: Int)
}
