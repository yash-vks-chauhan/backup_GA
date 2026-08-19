package com.gridee.parking.ui.ads

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.gridee.parking.data.model.CustomAd
import com.gridee.parking.data.repository.CustomAdsRepository
import com.gridee.parking.utils.CustomAdSession
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * Owns the ad shown in a single placement.
 *
 * The only two states are "show this creative" and "show nothing" — there is no loading or
 * error state on purpose: an ad that is still loading, failed to load, or does not exist all
 * look identical to the user, so the host screen never has to reason about it.
 */
class CustomAdViewModel(application: Application) : AndroidViewModel(application) {

    sealed class AdState {
        object Hidden : AdState()
        data class Visible(val ad: CustomAd) : AdState()
    }

    private val repository = CustomAdsRepository(application)

    private val _adState = MutableLiveData<AdState>(AdState.Hidden)
    val adState: LiveData<AdState> = _adState

    private var candidates: List<CustomAd> = emptyList()
    private var loadJob: Job? = null

    /**
     * Paints from cache immediately, then refreshes in the background.
     *
     * @param forceRefresh bypasses the cache TTL — pass true when the app returns to the
     *   foreground, false for ordinary screen entry.
     */
    fun load(placement: String, forceRefresh: Boolean = false) {
        // Cheap synchronous cache read so a returning user sees the banner on the first frame.
        publish(repository.getCachedAds(placement))

        if (loadJob?.isActive == true && !forceRefresh) return
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            publish(repository.getAds(placement, forceRefresh))
        }
    }

    /** User closed the banner: gone for the rest of this app session, for every screen. */
    fun dismiss(ad: CustomAd) {
        CustomAdSession.dismiss(ad.id)
        publish(candidates)
    }

    /**
     * The creative failed to load. Treated exactly like a dismissal so the slot collapses
     * silently and, if the backend sent more than one ad, the next one gets its turn.
     */
    fun onImageFailed(ad: CustomAd) {
        CustomAdSession.dismiss(ad.id)
        publish(candidates)
    }

    /** Called once the creative is actually on screen, not merely fetched. */
    fun onAdDisplayed(ad: CustomAd) {
        repository.trackImpression(ad)
        // Spends this ad's once-per-session turn. It stays on screen for now (see [publish]);
        // the rule only takes effect the next time we pick an ad.
        repository.markDisplayed(ad)
    }

    fun onAdClicked(ad: CustomAd) {
        repository.trackClick(ad)
    }

    private fun publish(ads: List<CustomAd>) {
        candidates = ads
        val showing = (_adState.value as? AdState.Visible)?.ad?.id
        val next = ads.firstOrNull { isEligible(it, showing) }
        val newState = if (next == null) AdState.Hidden else AdState.Visible(next)

        // Avoid re-emitting the same creative: it would restart the image load and re-run the
        // reveal animation every time Home resumes.
        val current = _adState.value
        if (current is AdState.Visible && newState is AdState.Visible && current.ad.id == newState.ad.id) return
        if (current is AdState.Hidden && newState is AdState.Hidden) return

        _adState.value = newState
    }

    /**
     * Whether [ad] may take the slot.
     *
     * [showing] is exempt from the once-per-session rule on purpose: the ad spends its turn the
     * moment it becomes visible, and without this exemption the very next refresh would yank the
     * banner out from under a user who is still looking at it.
     */
    private fun isEligible(ad: CustomAd, showing: String?): Boolean {
        if (CustomAdSession.isDismissed(ad.id)) return false
        if (!ad.isLiveAt()) return false
        if (!ad.isForAndroid()) return false
        if (ad.id == showing) return true
        return !(ad.isOncePerSession() && CustomAdSession.hasBeenDisplayed(ad.id))
    }
}
