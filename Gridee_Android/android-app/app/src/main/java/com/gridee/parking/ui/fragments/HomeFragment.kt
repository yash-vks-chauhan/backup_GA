package com.gridee.parking.ui.fragments

import com.gridee.parking.R

import android.animation.ValueAnimator
import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.PagerSnapHelper
import androidx.dynamicanimation.animation.DynamicAnimation
import androidx.dynamicanimation.animation.SpringAnimation
import androidx.dynamicanimation.animation.SpringForce
import androidx.core.view.doOnLayout
import com.gridee.parking.config.RemoteConfigManager
import com.gridee.parking.data.model.ParkingSpot
import com.gridee.parking.data.repository.ParkingRepository
import com.gridee.parking.databinding.FragmentHomeBinding
import com.gridee.parking.ui.MainViewModel
import com.gridee.parking.ui.adapters.ParkingSpotPageAdapter
import com.gridee.parking.ui.ads.AdMobNativeAdCardView
import com.gridee.parking.ui.ads.CustomAdPlacement
import com.gridee.parking.ui.ads.CustomAdViewModel
import com.gridee.parking.ui.base.BaseTabFragment
import com.gridee.parking.ui.bottomsheet.ParkingSpotBottomSheet
import com.gridee.parking.ui.main.MainContainerActivity
import com.gridee.parking.ui.views.SkeletonShimmer
import com.gridee.parking.utils.AuthErrorMapper
import com.gridee.parking.utils.AdRevenueAnalytics
import com.gridee.parking.utils.ParkingSpotSchedulePolicy
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.Locale

class HomeFragment : BaseTabFragment<FragmentHomeBinding>() {

    private enum class SlotFilterMode {
        MORNING,
        EVENING
    }

    private enum class MorningParkingMode {
        STANDARD,
        QUICK
    }

    private fun getDefaultSlotFilterMode(): SlotFilterMode {
        val availability = ParkingSpotSchedulePolicy.homeFilterAvailability()
        return if (availability.afternoonEnabled && !availability.morningEnabled) {
            SlotFilterMode.EVENING
        } else {
            SlotFilterMode.MORNING
        }
    }

    private lateinit var viewModel: MainViewModel
    private lateinit var customAdViewModel: CustomAdViewModel
    private var nativeHomeAdView: AdMobNativeAdCardView? = null
    private var forceCustomAdRefreshOnNativeFailure = false
    private val nativeAdRefreshHandler = Handler(Looper.getMainLooper())
    private var nativeAdVisibleSinceElapsedMs: Long? = null
    private var nativeAdAccumulatedVisibleMs = 0L
    private val nativeAdRefreshRunnable = Runnable { refreshNativeAdIfEligible() }
    private val parkingRepository = ParkingRepository()
    private lateinit var parkingSpotPageAdapter: ParkingSpotPageAdapter
    private var loadJob: Job? = null
    private var allParkingSpots: List<ParkingSpot> = emptyList()
    private var currentSearchQuery: String? = null
    private var defaultSearchHint: String = "Find a parking spot..."
    private var autoRefreshJob: Job? = null
    private val autoRefreshIntervalMs: Long = 10_000L
    private var isShowingBookingClosedState = false
    private var slotFilterMode: SlotFilterMode = getDefaultSlotFilterMode()
    private var morningParkingMode: MorningParkingMode = MorningParkingMode.STANDARD
    private var showSlotFilters: Boolean = true

    private var currentPage = 0
    private var filteredSpots: List<ParkingSpot> = emptyList()
    private val pagerSnapHelper = PagerSnapHelper()
    private var skeletonBreath: ValueAnimator? = null
    
    // Physics-based Apple transition state trackers
    private var isSlidingRight: Boolean? = null
    private var springX: SpringAnimation? = null
    private var springY: SpringAnimation? = null
    private var springScaleX: SpringAnimation? = null
    private var springScaleY: SpringAnimation? = null

    private fun cancelPhysicsAnimations() {
        springX?.cancel()
        springY?.cancel()
        springScaleX?.cancel()
        springScaleY?.cancel()
    }

    private val voiceInputLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode != Activity.RESULT_OK) return@registerForActivityResult
        val spokenText = result.data
            ?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
            ?.firstOrNull()
            ?.trim()

        if (spokenText.isNullOrBlank()) {
            showToast(getString(R.string.no_speech_detected))
            return@registerForActivityResult
        }

        applySearchQuery(spokenText)
    }

    override fun getViewBinding(inflater: LayoutInflater, container: ViewGroup?): FragmentHomeBinding {
        return FragmentHomeBinding.inflate(inflater, container, false)
    }

    override fun getScrollableView(): View? {
        return try {
            binding.scrollContent
        } catch (e: IllegalStateException) {
            null
        }
    }

    override fun setupUI() {
        viewModel = ViewModelProvider(this)[MainViewModel::class.java]
        showSlotFilters = loadHomeSlotFilterSetting()

        defaultSearchHint = binding.etSearchInput.hint?.toString()?.ifBlank { defaultSearchHint }
            ?: defaultSearchHint
        setupClickListeners()
        setupStepBanner()
        setupTimeFilterCards()
        setupMorningModeToggle()
        applyHomeSlotFilterVisibility(animated = false)
        setupParkingSpots()
        setupHomeAds()
        setupPullToRefresh()
        refreshParkingSpots(showBlockingLoading = true)

        applyHeroGradient()
    }

    override fun onStart() {
        super.onStart()
        if (!isHidden) {
            startAutoRefresh()
            loadNativeHomeAd()
        }
    }

    override fun onStop() {
        onHomeNativeAdDockVisibilityChanged(false)
        super.onStop()
        stopAutoRefresh()
    }

    override fun onHiddenChanged(hidden: Boolean) {
        super.onHiddenChanged(hidden)
        if (hidden) {
            onHomeNativeAdDockVisibilityChanged(false)
            stopAutoRefresh()
        } else {
            startAutoRefresh()
            loadNativeHomeAd()
        }
    }
    
    override fun onResume() {
        super.onResume()
        applyHeroGradient()
        // Show the daily reward dot until the user opens the reward today.
        binding.heroRewardCoin.setRewardAvailable(
            com.gridee.parking.utils.DailyRewardState.shouldShowDailyDot(requireContext())
        )
        maybePlayRewardIntro()
        val previousShowSlotFilters = showSlotFilters
        showSlotFilters = loadHomeSlotFilterSetting()
        applyHomeSlotFilterVisibility(animated = previousShowSlotFilters != showSlotFilters)
        if (previousShowSlotFilters != showSlotFilters) {
            currentPage = 0
            renderParkingSpotResults(animateLayout = true)
        }
    }

    /**
     * Play the reward medallion's "mint reveal".
     *
     * TEMP (tuning): replays every time Home resumes so it's easy to review — just
     * switch tabs and come back. Before shipping, gate this with
     * DailyRewardState.shouldPlayIntro() / markIntroPlayed() so it only ever plays
     * on the true first launch.
     */
    private fun maybePlayRewardIntro() {
        // Delay so Home has fully settled — the reveal then reads as its own
        // distinct beat instead of blending into the app-open.
        binding.heroRewardCoin.postDelayed({
            if (hasViewBinding()) binding.heroRewardCoin.playReveal()
        }, 800L)
    }

    private fun loadHomeSlotFilterSetting(): Boolean {
        RemoteConfigManager.loadCached(requireContext())
        return SHOW_SLOT_FILTERS && RemoteConfigManager.shouldShowHomeSlotFilters()
    }

    private fun applyHeroGradient() {
        try {
            val sharedPref = requireActivity().getSharedPreferences("gridee_prefs", android.content.Context.MODE_PRIVATE)
            val selected = sharedPref.getString("hero_gradient_key", "obsidian_dip")
            
            val (drawableRes, isLightTheme) = when(selected) {
                "midnight_slate" -> com.gridee.parking.R.drawable.bg_home_hero_midnight_slate to false
                "obsidian_dip" -> com.gridee.parking.R.drawable.bg_home_hero_obsidian_dip to false
                "carbon_mist" -> com.gridee.parking.R.drawable.bg_home_hero_carbon_mist to false
                "lavender_whisper" -> com.gridee.parking.R.drawable.bg_home_hero_lavender_whisper to true
                else -> com.gridee.parking.R.drawable.bg_home_hero_obsidian_dip to false
            }
            
            binding.heroBackground.setBackgroundResource(drawableRes)
            
            // Adapt Gridee Logo text color for peak contrast
            val titleColor = if (isLightTheme && !com.gridee.parking.utils.ThemeManager.isDarkMode(requireContext())) {
                androidx.core.content.ContextCompat.getColor(requireContext(), com.gridee.parking.R.color.text_primary)
            } else {
                android.graphics.Color.WHITE
            }
            binding.brandFollowSwitcher.setBrandColor(titleColor)
        } catch (e: Exception) {
            // Fallback safely
        }
    }

    private fun startAutoRefresh() {
        if (autoRefreshJob?.isActive == true) return
        autoRefreshJob = viewLifecycleOwner.lifecycleScope.launch {
            refreshParkingSpots(showBlockingLoading = false)
            while (isActive) {
                delay(autoRefreshIntervalMs)
                refreshParkingSpots(showBlockingLoading = false)
            }
        }
    }

    private fun stopAutoRefresh() {
        autoRefreshJob?.cancel()
        autoRefreshJob = null
    }

    private fun setupParkingSpots() {
        parkingSpotPageAdapter = ParkingSpotPageAdapter(::handleParkingSpotSelection)
        val layoutManager = LinearLayoutManager(requireContext(), LinearLayoutManager.HORIZONTAL, false)
        binding.recyclerParkingSpots.apply {
            this.layoutManager = layoutManager
            adapter = parkingSpotPageAdapter
            setHasFixedSize(false)
            itemAnimator = null
            isNestedScrollingEnabled = false
            overScrollMode = View.OVER_SCROLL_NEVER

            if (onFlingListener == null) {
                pagerSnapHelper.attachToRecyclerView(this)
            }

            addOnItemTouchListener(object : androidx.recyclerview.widget.RecyclerView.OnItemTouchListener {
                private var startX = 0f
                private var startY = 0f

                override fun onInterceptTouchEvent(
                    rv: androidx.recyclerview.widget.RecyclerView,
                    e: android.view.MotionEvent
                ): Boolean {
                    when (e.actionMasked) {
                        android.view.MotionEvent.ACTION_DOWN -> {
                            startX = e.x
                            startY = e.y
                            rv.parent?.requestDisallowInterceptTouchEvent(false)
                        }
                        android.view.MotionEvent.ACTION_MOVE -> {
                            val dx = kotlin.math.abs(e.x - startX)
                            val dy = kotlin.math.abs(e.y - startY)
                            rv.parent?.requestDisallowInterceptTouchEvent(dx > dy)
                        }
                        android.view.MotionEvent.ACTION_UP,
                        android.view.MotionEvent.ACTION_CANCEL -> {
                            rv.parent?.requestDisallowInterceptTouchEvent(false)
                        }
                    }
                    return false
                }

                override fun onTouchEvent(
                    rv: androidx.recyclerview.widget.RecyclerView,
                    e: android.view.MotionEvent
                ) = Unit

                override fun onRequestDisallowInterceptTouchEvent(disallowIntercept: Boolean) {}
            })

            addOnScrollListener(object : androidx.recyclerview.widget.RecyclerView.OnScrollListener() {
                override fun onScrollStateChanged(
                    recyclerView: androidx.recyclerview.widget.RecyclerView,
                    newState: Int
                ) {
                    super.onScrollStateChanged(recyclerView, newState)
                    if (newState != androidx.recyclerview.widget.RecyclerView.SCROLL_STATE_IDLE) return

                    val snappedView = pagerSnapHelper.findSnapView(layoutManager) ?: return
                    val snappedPosition = layoutManager.getPosition(snappedView)
                    if (snappedPosition != androidx.recyclerview.widget.RecyclerView.NO_POSITION &&
                        snappedPosition != currentPage
                    ) {
                        currentPage = snappedPosition
                        val totalPages = filteredSpots.chunked(PAGE_SIZE).size
                        renderDotIndicator(totalPages, currentPage)
                    }
                }
            })
        }
    }

    private fun setupPullToRefresh() {
        binding.swipeRefresh.setProgressBackgroundColorSchemeResource(com.gridee.parking.R.color.background_secondary)
        binding.swipeRefresh.setColorSchemeResources(com.gridee.parking.R.color.text_primary)
        binding.swipeRefresh.setOnRefreshListener {
            refreshParkingSpots(showBlockingLoading = false, showSwipeRefresh = true)
            // Keep a filled native ad stable. A manual content refresh must not manufacture
            // additional ad requests or reset the ad's visible-time clock.
            if (nativeHomeAdView?.hasAd != true) {
                forceCustomAdRefreshOnNativeFailure = true
                loadNativeHomeAd()
            }
        }
        binding.swipeRefresh.setOnChildScrollUpCallback { _, _ ->
            binding.scrollContent.canScrollVertically(-1)
        }
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { _, insets ->
            val topInset = insets.getInsets(WindowInsetsCompat.Type.systemBars()).top
            val startOffset = topInset + dpToPx(24)
            val endOffset = topInset + dpToPx(104)
            binding.swipeRefresh.setProgressViewOffset(false, startOffset, endOffset)
            insets
        }
        ViewCompat.requestApplyInsets(binding.root)
    }

    /** Uses the activity's floating portrait AdMob card and an in-content campaign fallback. */
    private fun setupHomeAds() {
        setupCustomAd()
        nativeHomeAdView = requireActivity().findViewById(R.id.native_ad_home)
        nativeHomeAdView?.onAdLoaded = {
            if (hasViewBinding()) {
                resetNativeAdRefreshClock()
                binding.customAdHome.clear()
                forceCustomAdRefreshOnNativeFailure = false
                (activity as? MainContainerActivity)?.setHomeNativeAdAvailable(true)
            }
        }
        nativeHomeAdView?.onAdUnavailable = {
            if (hasViewBinding()) {
                resetNativeAdRefreshClock()
                (activity as? MainContainerActivity)?.setHomeNativeAdAvailable(false)
                val forceRefresh = forceCustomAdRefreshOnNativeFailure
                forceCustomAdRefreshOnNativeFailure = false
                loadHomeAd(forceRefresh)
                // Mediation just gave the slot back; show the campaign we already have without
                // waiting for the refresh to return a different creative.
                applyCustomAdState()
            }
        }
        nativeHomeAdView?.onLoadEvent = { event ->
            context?.let { AdRevenueAnalytics.logLoad(it, event) }
        }
        nativeHomeAdView?.onAdImpression = {
            context?.let { AdRevenueAnalytics.logImpression(it) }
        }
        nativeHomeAdView?.onAdClicked = {
            context?.let { AdRevenueAnalytics.logClick(it) }
        }
        nativeHomeAdView?.onPaidEvent = { event ->
            context?.let { AdRevenueAnalytics.logPaidEvent(it, event) }
        }
        nativeHomeAdView?.onVideoEvent = { action, muted ->
            context?.let { AdRevenueAnalytics.logVideoEvent(it, action, muted) }
        }
        loadNativeHomeAd()
    }

    private fun setupCustomAd() {
        customAdViewModel = ViewModelProvider(this)[CustomAdViewModel::class.java]

        binding.customAdHome.apply {
            onAdDisplayed = { ad -> customAdViewModel.onAdDisplayed(ad) }
            onAdLoadFailed = { ad -> customAdViewModel.onImageFailed(ad) }
            onAdDismiss = { ad -> customAdViewModel.dismiss(ad) }
            onAdClick = { ad -> openAdClickUrl(ad) }
        }

        customAdViewModel.adState.observe(viewLifecycleOwner) { applyCustomAdState() }
    }

    /**
     * Paints whatever the campaign slot should be showing right now.
     *
     * Driven by two independent signals — the campaign state changing, and the AdMob card
     * taking or releasing the slot — so it reads the current state rather than an event
     * payload. That matters on the release path: the view model suppresses re-emitting the
     * same creative, so a mediation failure would otherwise never hand the space back to a
     * campaign that had been hidden behind a native ad.
     */
    private fun applyCustomAdState() {
        if (!hasViewBinding() || !::customAdViewModel.isInitialized) return

        val state = customAdViewModel.adState.value
        if (state is CustomAdViewModel.AdState.Visible && nativeHomeAdView?.hasAd != true) {
            binding.customAdHome.bind(state.ad)
        } else {
            binding.customAdHome.clear()
        }
    }

    private fun loadNativeHomeAd() {
        if (!hasViewBinding()) return
        nativeHomeAdView?.load()
    }

    /** Called after UMP has refreshed the current launch's consent state. */
    fun onAdsConsentReady() {
        if (hasViewBinding()) loadNativeHomeAd()
    }

    /** Clears an already loaded creative if the newest UMP state no longer permits ads. */
    fun onAdsConsentUnavailable() {
        if (!hasViewBinding()) return
        resetNativeAdRefreshClock()
        (activity as? MainContainerActivity)?.setHomeNativeAdAvailable(false)
        nativeHomeAdView?.destroy()
        loadHomeAd()
    }

    /**
     * Called by MainContainerActivity when the floating dock actually becomes visible or hidden.
     * Only visible Home time counts toward replacement, so a quick tab switch reuses the ad.
     */
    fun onHomeNativeAdDockVisibilityChanged(visible: Boolean) {
        nativeHomeAdView?.setPlacementVisible(visible)
        val now = SystemClock.elapsedRealtime()
        if (!visible) {
            nativeAdVisibleSinceElapsedMs?.let { startedAt ->
                nativeAdAccumulatedVisibleMs += (now - startedAt).coerceAtLeast(0L)
            }
            nativeAdVisibleSinceElapsedMs = null
            nativeAdRefreshHandler.removeCallbacks(nativeAdRefreshRunnable)
            return
        }

        if (nativeHomeAdView?.hasAd != true || nativeAdVisibleSinceElapsedMs != null) return
        nativeAdVisibleSinceElapsedMs = now
        scheduleNativeAdRefresh()
    }

    private fun scheduleNativeAdRefresh() {
        nativeAdRefreshHandler.removeCallbacks(nativeAdRefreshRunnable)
        val remaining = (NATIVE_AD_VISIBLE_REFRESH_MS - nativeAdAccumulatedVisibleMs)
            .coerceAtLeast(1L)
        nativeAdRefreshHandler.postDelayed(nativeAdRefreshRunnable, remaining)
    }

    private fun refreshNativeAdIfEligible() {
        val startedAt = nativeAdVisibleSinceElapsedMs ?: return
        if (!hasViewBinding() || nativeHomeAdView?.hasAd != true) return

        val now = SystemClock.elapsedRealtime()
        val totalVisibleMs = nativeAdAccumulatedVisibleMs + (now - startedAt).coerceAtLeast(0L)
        if (totalVisibleMs < NATIVE_AD_VISIBLE_REFRESH_MS) {
            nativeAdAccumulatedVisibleMs = totalVisibleMs
            nativeAdVisibleSinceElapsedMs = now
            scheduleNativeAdRefresh()
            return
        }

        resetNativeAdRefreshClock()
        (activity as? MainContainerActivity)?.setHomeNativeAdAvailable(false)
        nativeHomeAdView?.refresh()
    }

    private fun resetNativeAdRefreshClock() {
        nativeAdRefreshHandler.removeCallbacks(nativeAdRefreshRunnable)
        nativeAdVisibleSinceElapsedMs = null
        nativeAdAccumulatedVisibleMs = 0L
    }

    private fun loadHomeAd(forceRefresh: Boolean = false) {
        // onStart/onHiddenChanged can run before the view exists during tab restoration.
        if (!::customAdViewModel.isInitialized) return
        customAdViewModel.load(CustomAdPlacement.HOME, forceRefresh)
    }

    override fun onDestroyView() {
        skeletonBreath?.cancel()
        skeletonBreath = null
        resetNativeAdRefreshClock()
        (activity as? MainContainerActivity)?.setHomeNativeAdAvailable(false)
        nativeHomeAdView?.apply {
            onAdLoaded = null
            onAdUnavailable = null
            onLoadEvent = null
            onAdImpression = null
            onAdClicked = null
            onPaidEvent = null
            onVideoEvent = null
            destroy()
        }
        nativeHomeAdView = null
        super.onDestroyView()
    }

    /**
     * Ad taps only ever open a web page: the URL was scheme-checked when the payload was
     * parsed, and re-checked here because it may have come back from the cache since.
     */
    private fun openAdClickUrl(ad: com.gridee.parking.data.model.CustomAd) {
        val url = ad.clickUrl?.trim().orEmpty()
        if (url.isEmpty() ||
            !com.gridee.parking.data.model.CustomAdPayloadParser.isOpenableClickUrl(url)
        ) {
            return
        }
        customAdViewModel.onAdClicked(ad)
        openExternalUrl(url)
    }

    private fun handleParkingSpotSelection(spot: ParkingSpot) {
        openParkingSpotBottomSheet(spot)
    }

    private fun openParkingSpotBottomSheet(spot: ParkingSpot) {
        val lotName = resolveSpotDisplayName(spot)
        
        val sheet = ParkingSpotBottomSheet.newInstance(
            parkingSpotId = spot.id,
            parkingLotId = spot.lotId,
            parkingLotName = lotName,
            parkingSpotName = spot.name,
            parkingSpotZoneName = spot.zoneName,
            parkingSpotCode = spot.spotCode,
            parkingSpotSlotName = spot.slotName
        )
        sheet.show(parentFragmentManager, ParkingSpotBottomSheet.TAG)
    }

    private fun resolveSpotDisplayName(spot: ParkingSpot): String {
        return spot.name?.takeIf { it.isNotBlank() }
            ?: spot.zoneName?.takeIf { it.isNotBlank() }
            ?: spot.spotCode?.takeIf { it.isNotBlank() }
            ?: spot.id
    }

    private fun refreshParkingSpots(
        showBlockingLoading: Boolean,
        showSwipeRefresh: Boolean = false
    ) {
        if (ParkingSpotSchedulePolicy.isBookingClosed()) {
            loadJob?.cancel()
            loadJob = null
            binding.swipeRefresh.isRefreshing = false
            showParkingBookingsClosedState()
            return
        }

        // At 5:00 AM, restore yesterday's cached cards immediately while the first fresh
        // availability request runs. This keeps the scheduled transition independent of the
        // network and the normal refresh replaces the cached values as soon as it completes.
        if (isShowingBookingClosedState && allParkingSpots.isNotEmpty()) {
            renderParkingSpotResults()
        }

        // A manual action is one the user explicitly triggered (Try Again or pull-to-refresh),
        // as opposed to the silent 10s background sync.
        val isManual = showBlockingLoading || showSwipeRefresh

        // The silent background sync must never duplicate or interrupt a load that's already
        // running — just let the in-flight one finish.
        if (!isManual && loadJob?.isActive == true) {
            return
        }

        // A manual action supersedes any in-flight silent sync, so the user always gets
        // immediate feedback instead of a dead tap that's blocked behind a background request.
        if (isManual) {
            loadJob?.cancel()
        }

        setParkingSpotsRefreshing(
            isRefreshing = true,
            showBlockingLoading = showBlockingLoading,
            showSwipeRefresh = showSwipeRefresh
        )

        loadJob = viewLifecycleOwner.lifecycleScope.launch {
            try {
                val spots = fetchAllParkingSpots()
                println("DEBUG HomeFragment.refreshParkingSpots: Fetched spots size=${spots.size}")
                allParkingSpots = spots

                // Manual actions get the Depth Pop physics; a silent background sync stays
                // still so it doesn't interrupt the user mid-scroll.
                renderParkingSpotResults(animateLayout = isManual)
            } catch (e: CancellationException) {
                // Superseded by a newer load (e.g. the user tapped Try Again). Not a failure —
                // let the newer load own the UI.
                throw e
            } catch (e: Exception) {
                println("DEBUG HomeFragment.refreshParkingSpots: Exception - ${e.message}")
                when {
                    ParkingSpotSchedulePolicy.isBookingClosed() ->
                        showParkingBookingsClosedState()
                    // Cold load with nothing on screen → full-screen error state with Retry.
                    allParkingSpots.isEmpty() -> showParkingSpotsErrorState(e)
                    // Spots are already showing → never wipe them on a transient blip. Tell the
                    // user only if they actively asked to refresh; stay silent for background syncs.
                    isManual -> notifyRefreshFailed(e)
                }
            } finally {
                setParkingSpotsRefreshing(
                    isRefreshing = false,
                    showBlockingLoading = showBlockingLoading,
                    showSwipeRefresh = showSwipeRefresh
                )
            }
        }
    }

    /**
     * A manual refresh failed but we already have spots on screen — keep them and surface a
     * brief, dismissible banner instead of destroying the user's content.
     */
    private fun notifyRefreshFailed(e: Exception) {
        // Nothing replaced the ghosts on this path, so take them down here.
        hideSpotSkeleton()
        val error = if (e is retrofit2.HttpException) {
            AuthErrorMapper.fromHttpCode(e.code())
        } else {
            AuthErrorMapper.fromException(e)
        }
        val parent = activity?.findViewById<android.view.ViewGroup>(com.gridee.parking.R.id.fragment_container)
            ?: activity?.findViewById<android.view.ViewGroup>(android.R.id.content)
            ?: return
        com.gridee.parking.utils.NotificationHelper.showWarning(
            parent = parent,
            title = getString(R.string.couldnt_refresh),
            message = error.message,
            duration = 3000L
        )
    }

    private fun setParkingSpotsRefreshing(
        isRefreshing: Boolean,
        showBlockingLoading: Boolean,
        showSwipeRefresh: Boolean
    ) {
        if (!hasViewBinding()) return
        // Only the start of a blocking load is handled here. Taking the skeleton back
        // down belongs to whichever state replaces it — list, empty or error — so the
        // ghosts stay put until real content is laid out instead of blinking out a
        // frame early.
        if (showBlockingLoading && isRefreshing) {
            binding.recyclerParkingSpots.visibility = View.GONE
            binding.tvParkingSpotsEmpty.visibility = View.GONE
            binding.layoutParkingSpotsError.visibility = View.GONE
            binding.cardDotIndicator.visibility = View.GONE
            showSpotSkeleton()
        }

        if (showSwipeRefresh) {
            if (isRefreshing) {
                if (!binding.swipeRefresh.isRefreshing) {
                    binding.swipeRefresh.post { binding.swipeRefresh.isRefreshing = true }
                }
            } else {
                binding.swipeRefresh.isRefreshing = false
            }
        } else if (!isRefreshing) {
            binding.swipeRefresh.isRefreshing = false
        }
    }

    /**
     * Ghost cards while the spot list loads with nothing on screen. The block is sized
     * to the page this user saw last time, so on every open after the first the real
     * cards land exactly where the ghosts stood. Views are reused between shows and
     * only re-inflated when the count actually changes.
     */
    private fun showSpotSkeleton() {
        if (!hasViewBinding()) return
        val container = binding.layoutParkingSpotsSkeleton
        val cardCount = lastSpotPageSize()
        if (container.childCount != cardCount) {
            HomeSpotSkeleton.populate(container, cardCount)
        }
        container.visibility = View.VISIBLE
        if (skeletonBreath == null) {
            skeletonBreath = SkeletonShimmer.start(container)
        }
    }

    private fun hideSpotSkeleton() {
        skeletonBreath?.cancel()
        skeletonBreath = null
        if (!hasViewBinding()) return
        if (binding.layoutParkingSpotsSkeleton.visibility != View.GONE) {
            binding.layoutParkingSpotsSkeleton.visibility = View.GONE
        }
    }

    /** Cards on the first page last time spots rendered; a full page until we've seen one. */
    private fun lastSpotPageSize(): Int {
        val prefs = context?.getSharedPreferences(PREFS_NAME, android.content.Context.MODE_PRIVATE)
            ?: return PAGE_SIZE
        return prefs.getInt(KEY_LAST_SPOT_PAGE_SIZE, PAGE_SIZE).coerceIn(1, PAGE_SIZE)
    }

    private fun rememberSpotPageSize(size: Int) {
        val prefs = context?.getSharedPreferences(PREFS_NAME, android.content.Context.MODE_PRIVATE)
            ?: return
        val stored = size.coerceIn(1, PAGE_SIZE)
        if (prefs.getInt(KEY_LAST_SPOT_PAGE_SIZE, -1) != stored) {
            prefs.edit().putInt(KEY_LAST_SPOT_PAGE_SIZE, stored).apply()
        }
    }

    private fun startVoiceSearch() {
        if (!SpeechRecognizer.isRecognitionAvailable(requireContext())) {
            showToast(getString(R.string.voice_search_is_not_available_on))
            return
        }

        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
            putExtra(RecognizerIntent.EXTRA_PROMPT, "Search parking spots")
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
        }

        if (intent.resolveActivity(requireContext().packageManager) == null) {
            showToast(getString(R.string.voice_search_is_not_available))
            return
        }

        runCatching { voiceInputLauncher.launch(intent) }
            .onFailure { showToast(getString(R.string.unable_to_start_voice_search)) }
    }

    private fun applySearchQuery(query: String) {
        val trimmed = query.trim()
        currentSearchQuery = trimmed.ifBlank { null }
        
        if (binding.etSearchInput.text.toString() != trimmed) {
            binding.etSearchInput.setText(if (trimmed.isBlank()) "" else trimmed)
            binding.etSearchInput.setSelection(binding.etSearchInput.text?.length ?: 0)
        }
        updateSearchTrailing(trimmed)

        if (loadJob?.isActive == true && allParkingSpots.isEmpty()) {
            return
        }
        currentPage = 0 // Reset page on query
        renderParkingSpotResults(animateLayout = true) // Searching should animate newly matched items
    }

    /**
     * Show the clear (×) button once there's text to clear, otherwise the voice mic —
     * but only when the device actually supports speech recognition.
     */
    private fun updateSearchTrailing(query: String) {
        if (!hasViewBinding()) return
        val ctx = context ?: return
        val hasText = query.isNotEmpty()
        val voiceAvailable = SpeechRecognizer.isRecognitionAvailable(ctx)
        binding.ivSearchClear.visibility = if (hasText) View.VISIBLE else View.GONE
        binding.ivSearchMic.visibility = if (!hasText && voiceAvailable) View.VISIBLE else View.GONE
    }

    private fun renderParkingSpotResults(animateLayout: Boolean = false) {
        if (!hasViewBinding()) return
        if (ParkingSpotSchedulePolicy.isBookingClosed()) {
            showParkingBookingsClosedState()
            return
        }

        val query = currentSearchQuery?.trim().orEmpty()
        val availability = syncFilterStateWithSchedule()
        val visibleNow = applyActiveSpotFilter(allParkingSpots, availability)
        filteredSpots = filterParkingSpots(visibleNow, query)
        
        // Ensure currentPage is within bounds in case list shrunk
        val totalPages = filteredSpots.chunked(PAGE_SIZE).size
        if (currentPage >= totalPages && totalPages > 0) {
            currentPage = totalPages - 1
        }
        
        updateSpotPagination(animateLayout)
    }

    private fun updateSpotPagination(animateLayout: Boolean) {
        if (filteredSpots.isEmpty()) {
            showEmptyState(buildEmptyStateMessage())
        } else {
            val pages = filteredSpots.chunked(PAGE_SIZE)
            val totalPages = pages.size

            // Size the next cold open's ghost block to the list this user actually has.
            rememberSpotPageSize(pages.first().size)
            isShowingBookingClosedState = false
            binding.tvParkingSpotsEmpty.visibility = View.GONE
            binding.layoutParkingSpotsError.visibility = View.GONE

            // Define the action that applies the new list and animates the list entering
            val commitAndAnimateIn = Runnable {
                if (totalPages > 1) {
                    binding.cardDotIndicator.visibility = View.VISIBLE
                    renderDotIndicator(totalPages, currentPage)
                } else {
                    binding.cardDotIndicator.visibility = View.GONE
                }

                parkingSpotPageAdapter.submitList(pages) {
                    if (pages.isEmpty()) return@submitList
                    binding.recyclerParkingSpots.post {
                        binding.recyclerParkingSpots.scrollToPosition(currentPage.coerceIn(0, pages.lastIndex))

                        // Ghosts out and real cards in within the same layout pass, so the
                        // block never blinks empty between the two.
                        hideSpotSkeleton()

                        if (animateLayout || binding.recyclerParkingSpots.visibility != View.VISIBLE) {
                            binding.recyclerParkingSpots.visibility = View.VISIBLE
                            
                            // Apple Friction Entry: Alpha uses standard duration, position uses Spring Physics
                            binding.recyclerParkingSpots.animate().cancel()
                            cancelPhysicsAnimations()
                            
                            binding.recyclerParkingSpots.animate()
                                .alpha(1f)
                                .setDuration(250)
                                .setInterpolator(android.view.animation.DecelerateInterpolator(1.2f))
                                .withEndAction { isSlidingRight = null }
                                .start()

                            // Authentic Apple snappy spring (0.8 damping = magnetic friction, 350 stiffness = responsive)
                            springX = SpringAnimation(binding.recyclerParkingSpots, DynamicAnimation.TRANSLATION_X, 0f).apply {
                                spring = SpringForce(0f).apply { dampingRatio = 0.8f; stiffness = 350f }
                                start()
                            }
                            
                            springY = SpringAnimation(binding.recyclerParkingSpots, DynamicAnimation.TRANSLATION_Y, 0f).apply {
                                spring = SpringForce(0f).apply { dampingRatio = 0.8f; stiffness = 350f }
                                start()
                            }
                            
                            springScaleX = SpringAnimation(binding.recyclerParkingSpots, DynamicAnimation.SCALE_X, 1f).apply {
                                spring = SpringForce(1f).apply { dampingRatio = 0.8f; stiffness = 350f }
                                start()
                            }
                            
                            springScaleY = SpringAnimation(binding.recyclerParkingSpots, DynamicAnimation.SCALE_Y, 1f).apply {
                                spring = SpringForce(1f).apply { dampingRatio = 0.8f; stiffness = 350f }
                                start()
                            }
                        }
                    }
                }
            }

            if (!animateLayout && binding.recyclerParkingSpots.visibility == View.VISIBLE) {
                // Background refresh without jarring visual animations
                commitAndAnimateIn.run()
                return
            }

            // Cancel any ongoing animations so rapid clicking doesn't stack visual bugs
            binding.recyclerParkingSpots.animate().cancel()
            cancelPhysicsAnimations()
            
            if (binding.recyclerParkingSpots.visibility == View.VISIBLE && binding.recyclerParkingSpots.alpha > 0f) {
                // Sharply push outgoing list away to create high momentum 
                val exitTranslationX = when (isSlidingRight) {
                    true -> -dpToPx(80).toFloat() // Slide left out
                    false -> dpToPx(80).toFloat() // Slide right out
                    null -> 0f 
                }
                
                // For Swipe-To-Refresh: Items visually fall downwards with the pull
                val exitTranslationY = when (isSlidingRight) {
                    null -> dpToPx(60).toFloat()
                    else -> 0f
                }

                binding.recyclerParkingSpots.animate()
                    .alpha(0f)
                    .translationX(exitTranslationX)
                    .translationY(exitTranslationY)
                    .scaleX(1f)
                    .scaleY(1f)
                    .setDuration(160) // Fast Apple exit speed
                    .setInterpolator(android.view.animation.AccelerateInterpolator(1.8f))
                    .withEndAction(commitAndAnimateIn)
                    .start()
            } else {
                // Initialize out-of-bounds for the entrance animation
                val enterTranslationX = when (isSlidingRight) {
                    true -> dpToPx(80).toFloat() // Coming from right
                    false -> -dpToPx(80).toFloat() // Coming from left
                    null -> 0f
                }
                
                // For Swipe-To-Refresh: New items drop in from the top
                val enterTranslationY = when (isSlidingRight) {
                    null -> -dpToPx(60).toFloat()
                    else -> 0f
                }
                
                binding.recyclerParkingSpots.alpha = 0f
                binding.recyclerParkingSpots.translationX = enterTranslationX
                binding.recyclerParkingSpots.translationY = enterTranslationY
                binding.recyclerParkingSpots.scaleX = 1f
                binding.recyclerParkingSpots.scaleY = 1f
                commitAndAnimateIn.run()
            }
        }
    }

    private fun renderDotIndicator(totalPages: Int, current: Int) {
        binding.layoutDotIndicator.removeAllViews()
        val context = requireContext()
        val activeDrawable = androidx.core.content.ContextCompat.getDrawable(context, com.gridee.parking.R.drawable.bg_dot_active_pill)
        val inactiveDrawable = androidx.core.content.ContextCompat.getDrawable(context, com.gridee.parking.R.drawable.bg_dot_inactive)

        for (i in 0 until totalPages) {
            val dot = View(context)
            val lp = android.widget.LinearLayout.LayoutParams(
                if (i == current) dpToPx(34) else dpToPx(8),
                if (i == current) dpToPx(10) else dpToPx(8)
            )
            if (i > 0) {
                lp.marginStart = dpToPx(10)
            }
            dot.layoutParams = lp
            dot.background = if (i == current) activeDrawable else inactiveDrawable
            
            dot.setOnClickListener {
                currentPage = i
                renderDotIndicator(totalPages, currentPage)
                binding.recyclerParkingSpots.smoothScrollToPosition(i)
            }
            
            binding.layoutDotIndicator.addView(dot)
        }
    }
    
    private fun dpToPx(dp: Int): Int {
        return (dp * resources.displayMetrics.density).toInt()
    }

    private fun filterParkingSpots(spots: List<ParkingSpot>, query: String): List<ParkingSpot> {
        val normalized = query.trim().lowercase(Locale.getDefault())
        if (normalized.isEmpty()) return spots

        return spots.filter { spot ->
            val candidates = listOf(
                spot.name,
                spot.zoneName,
                spot.lotName,
                spot.spotCode,
                spot.lotId
            )
            candidates.any { it?.lowercase(Locale.getDefault())?.contains(normalized) == true }
        }
    }

    private fun setupStepBanner() {
        val banner = binding.layoutStepBanner
        if (!SHOW_STEP_BANNER) {
            banner.visibility = View.GONE
            return
        }
        banner.visibility = View.VISIBLE
        banner.bannerText = getString(com.gridee.parking.R.string.step_banner_line)

        // Entrance: the banner fades in and a gentle breeze gust ripples through it.
        banner.alpha = 0f
        banner.translationY = -dpToPx(8).toFloat()
        banner.animate()
            .alpha(1f)
            .translationY(0f)
            .setStartDelay(140)
            .setDuration(420)
            .setInterpolator(android.view.animation.DecelerateInterpolator())
            .withEndAction { if (hasViewBinding()) banner.gust() }
            .start()

        banner.setOnClickListener {
            it.performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS)
            binding.layoutStepBanner.gust()

            // Glide the bookable spots into view once they're loaded.
            if (binding.recyclerParkingSpots.visibility == View.VISIBLE) {
                binding.scrollContent.post {
                    binding.scrollContent.smoothScrollTo(0, binding.recyclerParkingSpots.top)
                }
            }
        }
    }

    private fun setupTimeFilterCards() {
        updateTimeFilterCardsUI()

        binding.cardFilterMorning.setOnClickListener {
            if (slotFilterMode != SlotFilterMode.MORNING) {
                isSlidingRight = false // Moving leftwards from Afternoon to Morning
                slotFilterMode = SlotFilterMode.MORNING
                morningParkingMode = MorningParkingMode.STANDARD
                currentPage = 0
                animateSegmentClick(it)
                updateTimeFilterCardsUI()
                updateMorningModeToggleUI()
                renderParkingSpotResults(animateLayout = true)
            }
        }

        binding.cardFilterEvening.setOnClickListener {
            if (slotFilterMode != SlotFilterMode.EVENING) {
                isSlidingRight = true // Moving rightwards from Morning to Afternoon
                slotFilterMode = SlotFilterMode.EVENING
                currentPage = 0
                animateSegmentClick(it)
                updateTimeFilterCardsUI()
                updateMorningModeToggleUI()
                renderParkingSpotResults(animateLayout = true)
            }
        }
    }

    private fun setupMorningModeToggle() {
        updateMorningModeToggleUI(animated = false)

        binding.cardMorningModeStandard.setOnClickListener {
            if (morningParkingMode != MorningParkingMode.STANDARD) {
                morningParkingMode = MorningParkingMode.STANDARD
                currentPage = 0
                animateSegmentClick(it)
                updateMorningModeToggleUI()
                renderParkingSpotResults(animateLayout = true)
            }
        }

        binding.cardMorningModeQuick.setOnClickListener {
            if (morningParkingMode != MorningParkingMode.QUICK) {
                morningParkingMode = MorningParkingMode.QUICK
                currentPage = 0
                animateSegmentClick(it)
                updateMorningModeToggleUI()
                renderParkingSpotResults(animateLayout = true)
            }
        }
    }

    private fun animateSegmentClick(view: View) {
        view.performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS)
        view.animate()
            .scaleX(0.95f)
            .scaleY(0.95f)
            .setDuration(100)
            .withEndAction {
                view.animate()
                    .scaleX(1f)
                    .scaleY(1f)
                    .setDuration(100)
                    .start()
            }
            .start()
    }

    private var isFirstFilterLayout = true
    private var isFirstMorningModeLayout = true

    private fun applyHomeSlotFilterVisibility(animated: Boolean = false) {
        if (showSlotFilters) {
            if (binding.layoutFilterCards.visibility != View.VISIBLE) {
                binding.layoutFilterCards.visibility = View.VISIBLE
                binding.layoutFilterCards.alpha = if (animated) 0f else 1f
                binding.layoutFilterCards.translationY = if (animated) -dpToPx(8).toFloat() else 0f
                if (animated) {
                    binding.layoutFilterCards.animate()
                        .alpha(1f)
                        .translationY(0f)
                        .setDuration(180)
                        .start()
                }
            }
            updateTimeFilterCardsUI()
            updateMorningModeToggleUI(animated = animated)
            return
        }

        binding.layoutFilterCards.animate().cancel()
        binding.layoutMorningModeToggle.animate().cancel()
        binding.layoutFilterCards.visibility = View.GONE
        binding.layoutFilterCards.alpha = 1f
        binding.layoutFilterCards.translationY = 0f
        binding.layoutMorningModeToggle.visibility = View.GONE
        binding.layoutMorningModeToggle.alpha = 1f
        binding.layoutMorningModeToggle.translationY = 0f
        isFirstFilterLayout = true
        isFirstMorningModeLayout = true
    }

    private fun updateTimeFilterCardsUI() {
        if (!showSlotFilters) {
            binding.layoutFilterCards.visibility = View.GONE
            return
        }

        binding.layoutFilterCards.visibility = View.VISIBLE
        val selectedContent = androidx.core.content.ContextCompat.getColor(requireContext(), com.gridee.parking.R.color.text_primary)
        val unselectedContent = androidx.core.content.ContextCompat.getColor(requireContext(), com.gridee.parking.R.color.text_secondary)
        
        val isMorning = slotFilterMode == SlotFilterMode.MORNING
        val targetSegment = if (isMorning) binding.cardFilterMorning else binding.cardFilterEvening

        // Reset visibility and weights just in case
        binding.tvFilterMorning.visibility = View.VISIBLE
        binding.tvFilterEvening.visibility = View.VISIBLE
        binding.tvFilterMorning.alpha = 1f
        binding.tvFilterEvening.alpha = 1f
        
        val morningParams = binding.cardFilterMorning.layoutParams as android.widget.LinearLayout.LayoutParams
        morningParams.weight = 1f
        binding.cardFilterMorning.layoutParams = morningParams
        
        val eveningParams = binding.cardFilterEvening.layoutParams as android.widget.LinearLayout.LayoutParams
        eveningParams.weight = 1f
        binding.cardFilterEvening.layoutParams = eveningParams
        
        binding.layoutFilterCards.doOnLayout {
            val slider = binding.filterSegmentSlider
            val targetX = targetSegment.left.toFloat()
            val targetWidth = targetSegment.width
            
            if (slider.width != targetWidth) {
                val params = slider.layoutParams
                params.width = targetWidth
                slider.requestLayout()
            }
            
            if (isFirstFilterLayout) {
                slider.translationX = targetX
                isFirstFilterLayout = false
            } else {
                SpringAnimation(slider, DynamicAnimation.TRANSLATION_X, targetX).apply {
                    spring = SpringForce(targetX).apply {
                        dampingRatio = SpringForce.DAMPING_RATIO_NO_BOUNCY
                        stiffness = SpringForce.STIFFNESS_LOW
                    }
                }.start()
            }
        }

        binding.cardFilterMorning.apply { strokeWidth = 0 }
        val morningColor = if (isMorning) selectedContent else unselectedContent
        binding.ivFilterMorning.setColorFilter(morningColor, android.graphics.PorterDuff.Mode.SRC_IN)
        binding.tvFilterMorning.setTextColor(morningColor)
        binding.cardFilterMorning.isEnabled = true
        binding.cardFilterMorning.isClickable = true
        binding.cardFilterMorning.isFocusable = true
        binding.cardFilterMorning.alpha = 1f

        binding.cardFilterEvening.apply { strokeWidth = 0 }
        val eveningColor = if (!isMorning) selectedContent else unselectedContent
        binding.ivFilterEvening.setColorFilter(eveningColor, android.graphics.PorterDuff.Mode.SRC_IN)
        binding.tvFilterEvening.setTextColor(eveningColor)
        binding.cardFilterEvening.isEnabled = true
        binding.cardFilterEvening.isClickable = true
        binding.cardFilterEvening.isFocusable = true
        binding.cardFilterEvening.alpha = 1f
    }

    private fun updateMorningModeToggleUI(animated: Boolean = true) {
        val shouldShow = showSlotFilters && slotFilterMode == SlotFilterMode.MORNING
        val container = binding.layoutMorningModeToggle

        if (!shouldShow) {
            if (container.visibility == View.VISIBLE) {
                if (animated) {
                    container.animate()
                        .alpha(0f)
                        .translationY(-dpToPx(8).toFloat())
                        .setDuration(160)
                        .withEndAction {
                            container.visibility = View.GONE
                            isFirstMorningModeLayout = true
                        }
                        .start()
                } else {
                    container.visibility = View.GONE
                    isFirstMorningModeLayout = true
                }
            }
            return
        }

        if (container.visibility != View.VISIBLE) {
            container.visibility = View.VISIBLE
            container.alpha = if (animated) 0f else 1f
            container.translationY = if (animated) -dpToPx(8).toFloat() else 0f
            if (animated) {
                container.animate()
                    .alpha(1f)
                    .translationY(0f)
                    .setDuration(180)
                    .start()
            }
        }

        val selectedContent = androidx.core.content.ContextCompat.getColor(
            requireContext(),
            com.gridee.parking.R.color.uber_toggle_selected_text
        )
        val unselectedContent = androidx.core.content.ContextCompat.getColor(
            requireContext(),
            com.gridee.parking.R.color.uber_toggle_unselected_text
        )
        val selectedBgColor = androidx.core.content.ContextCompat.getColor(
            requireContext(),
            com.gridee.parking.R.color.uber_toggle_selected_bg
        )
        val unselectedBgColor = android.graphics.Color.TRANSPARENT

        val isStandard = morningParkingMode == MorningParkingMode.STANDARD
        
        binding.cardMorningModeStandard.setBackgroundColor(if (isStandard) selectedBgColor else unselectedBgColor)
        binding.cardMorningModeQuick.setBackgroundColor(if (isStandard) unselectedBgColor else selectedBgColor)

        binding.tvMorningModeStandard.setTextColor(if (isStandard) selectedContent else unselectedContent)
        binding.tvMorningModeQuick.setTextColor(if (isStandard) unselectedContent else selectedContent)

        binding.cardMorningModeStandard.isEnabled = true
        binding.cardMorningModeStandard.isClickable = true
        binding.cardMorningModeStandard.isFocusable = true
        binding.cardMorningModeStandard.alpha = 1f

        binding.cardMorningModeQuick.isEnabled = true
        binding.cardMorningModeQuick.isClickable = true
        binding.cardMorningModeQuick.isFocusable = true
        binding.cardMorningModeQuick.alpha = 1f
    }

    private fun isQuickMorningMode(): Boolean {
        return slotFilterMode == SlotFilterMode.MORNING &&
            morningParkingMode == MorningParkingMode.QUICK
    }

    private fun applyActiveSpotFilter(
        spots: List<ParkingSpot>,
        availability: ParkingSpotSchedulePolicy.HomeFilterAvailability
    ): List<ParkingSpot> {
        if (!showSlotFilters) {
            return ParkingSpotSchedulePolicy.filterVisibleSpots(spots)
        }

        if (slotFilterMode == SlotFilterMode.MORNING && !availability.morningEnabled) {
            return emptyList()
        }

        if (slotFilterMode == SlotFilterMode.EVENING && !availability.afternoonEnabled) {
            return emptyList()
        }

        if (isQuickMorningMode()) {
            if (!availability.quickEnabled) {
                println("DEBUG QuickFilter: quickEnabled=false, returning empty")
                return emptyList()
            }

            println("DEBUG QuickFilter: Total spots=${spots.size}")
            spots.forEach { spot ->
                val isQuick = ParkingSpotSchedulePolicy.isQuickBookSpot(spot)
                val session = ParkingSpotSchedulePolicy.classifySlotSession(spot)
                if (isQuick) {
                    println("DEBUG QuickFilter: QUICK spot found - id=${spot.id}, name=${spot.name}, zone=${spot.zoneName}, slot=${spot.slotName}, session=$session, available=${spot.available}")
                }
            }

            val quickSpots = ParkingSpotSchedulePolicy.filterQuickBookSpots(spots)
            println("DEBUG QuickFilter: Quick spots after pattern match=${quickSpots.size}")

            // Quick spots may have slotName=null (UNKNOWN session) — treat them as morning quick spots
            val morningQuickSpots = quickSpots.filter { spot ->
                val session = ParkingSpotSchedulePolicy.classifySlotSession(spot)
                session == ParkingSpotSchedulePolicy.SlotSession.MORNING ||
                    session == ParkingSpotSchedulePolicy.SlotSession.UNKNOWN
            }
            println("DEBUG QuickFilter: Quick+Morning/Unknown spots=${morningQuickSpots.size}")

            val availableQuickSpots = morningQuickSpots.filter { spot -> spot.available > 0 }
            println("DEBUG QuickFilter: Quick+Morning+Available spots=${availableQuickSpots.size}")

            return availableQuickSpots
                .sortedWith(
                    compareByDescending<ParkingSpot> { spot -> spot.available }
                        .thenBy { spot -> resolveSpotDisplayName(spot).lowercase(Locale.getDefault()) }
                )
        }

        return applySlotFilterMode(spots)
    }

    private fun applySlotFilterMode(spots: List<ParkingSpot>): List<ParkingSpot> {
        return when (slotFilterMode) {
            SlotFilterMode.MORNING -> spots.filter { spot ->
                ParkingSpotSchedulePolicy.classifySlotSession(spot) ==
                    ParkingSpotSchedulePolicy.SlotSession.MORNING &&
                    !ParkingSpotSchedulePolicy.isQuickBookSpot(spot)
            }

            SlotFilterMode.EVENING -> spots.filter { spot ->
                ParkingSpotSchedulePolicy.classifySlotSession(spot) ==
                    ParkingSpotSchedulePolicy.SlotSession.EVENING
            }
        }
    }

    private suspend fun fetchAllParkingSpots(): List<com.gridee.parking.data.model.ParkingSpot> {
        // Multi-tenant: Home shows spots ONLY for the user's assigned parking lot. We use the
        // lot-scoped endpoint rather than the global /api/parking-spots list (which mixes every
        // lot and isn't lot-scoped on the stricter backend). The selection gate guarantees a
        // lot is set before Home is shown. On a network/server failure we THROW so the caller
        // shows a retryable error state — never fake or stale inventory a user could try to book.
        val lotId = com.gridee.parking.utils.AuthSession.getParkingLotId(requireContext())
        if (lotId.isNullOrBlank()) {
            println("DEBUG HomeFragment.fetchAllParkingSpots: no assigned lot; nothing to show")
            return emptyList()
        }

        val resp = parkingRepository.getParkingSpotsByLot(lotId)
        println("DEBUG HomeFragment.fetchAllParkingSpots: lot=$lotId status=${resp.code()}, success=${resp.isSuccessful}")

        if (resp.isSuccessful) {
            val spots = (resp.body() ?: emptyList())
                // Defensive: never surface another lot's spots even if the API returns extras.
                .filter { it.lotId.isBlank() || it.lotId == lotId }
            println("DEBUG HomeFragment.fetchAllParkingSpots: lot returned ${spots.size} spots")
            return spots
        }

        // Non-2xx — surface as a retryable error state, not a misleading "no spots available".
        println("DEBUG HomeFragment.fetchAllParkingSpots: endpoint failed - status=${resp.code()}")
        throw retrofit2.HttpException(resp)

        /* ========================================
         * 📦 OLD LOT-BASED AGGREGATION APPROACH
         * ========================================
         * This code fetches parking lots first, then aggregates spots from each lot.
         * Currently COMMENTED OUT because direct /api/parking-spots endpoint works.
         * Keeping this for reference in case we need lot-based filtering in future.
         * 
         * To re-enable: Uncomment this block and remove the "return emptyList()" above.
         */
        
        /*
        // Fallback: aggregate by lot
        println("DEBUG HomeFragment.fetchAllParkingSpots: Using lot-based aggregation fallback")
        return try {
            val lotsResp = parkingRepository.getParkingLots()
            if (!lotsResp.isSuccessful) {
                println("DEBUG HomeFragment.fetchAllParkingSpots: Failed to get lots - status=${lotsResp.code()}")
                return emptyList()
            }

            val lots = lotsResp.body() ?: emptyList()
            println("DEBUG HomeFragment.fetchAllParkingSpots: Got ${lots.size} parking lots")
            
            val combined = mutableListOf<com.gridee.parking.data.model.ParkingSpot>()

            for (lot in lots) {
                println("DEBUG HomeFragment.fetchAllParkingSpots: Processing lot: id=${lot.id}, name=${lot.name}")
                
                val attempts = listOf(lot.name, lot.id).filter { it.isNotBlank() }.distinct()
                var lotSpots: List<com.gridee.parking.data.model.ParkingSpot> = emptyList()

                for (key in attempts) {
                    try {
                        val resp = parkingRepository.getParkingSpotsByLot(key)
                        if (resp.isSuccessful) {
                            val body = resp.body() ?: emptyList()
                            println("DEBUG HomeFragment.fetchAllParkingSpots: Lot '$key' returned ${body.size} spots")
                            if (body.isNotEmpty()) {
                                lotSpots = body
                                break
                            }
                        } else {
                            val errorBody = resp.errorBody()?.string()
                            println("DEBUG HomeFragment.fetchAllParkingSpots: Lot '$key' API failed - status=${resp.code()}, error=$errorBody")
                        }
                    } catch (e: Exception) {
                        println("DEBUG HomeFragment.fetchAllParkingSpots: Lot '$key' exception - ${e.javaClass.simpleName}: ${e.message}")
                    }
                }

                combined.addAll(lotSpots)
            }

            println("DEBUG HomeFragment.fetchAllParkingSpots: Total aggregated spots: ${combined.size}")
            combined
        } catch (e: Exception) {
            println("DEBUG HomeFragment.fetchAllParkingSpots: Fallback exception - ${e.message}")
            e.printStackTrace()
            emptyList()
        }
        */
    }

    private fun buildEmptyStateMessage(): String {
        val query = currentSearchQuery?.trim().orEmpty()
        val availability = ParkingSpotSchedulePolicy.homeFilterAvailability()

        if (!showSlotFilters) {
            return if (query.isNotEmpty()) {
                "No parking spots match \"$query\""
            } else {
                "No parking spots available"
            }
        }

        if (isQuickMorningMode() && !availability.quickEnabled) {
            return "Quick slot booking is available from 8:00 AM to 11:30 AM on the same day of parking."
        }

        if (slotFilterMode == SlotFilterMode.MORNING && !availability.morningEnabled) {
            return "Morning slots open at 5:00 AM on the parking day and run from 7:30 AM to 12:30 PM."
        }

        if (slotFilterMode == SlotFilterMode.EVENING && !availability.afternoonEnabled) {
            return "Afternoon slots show and open at 8:00 AM. Parking runs from 12:30 PM to 5:30 PM."
        }

        return when {
            query.isNotEmpty() && isQuickMorningMode() -> "No quick parking spots match \"$query\""
            query.isNotEmpty() -> "No parking spots match \"$query\""
            isQuickMorningMode() -> "No quick parking spots available"
            else -> "No parking spots available"
        }
    }

    private fun syncFilterStateWithSchedule(): ParkingSpotSchedulePolicy.HomeFilterAvailability {
        val availability = ParkingSpotSchedulePolicy.homeFilterAvailability()
        showSlotFilters = SHOW_SLOT_FILTERS && RemoteConfigManager.shouldShowHomeSlotFilters()

        applyHomeSlotFilterVisibility(animated = false)
        return availability
    }

    private fun showEmptyState(message: String) {
        hideSpotSkeleton()
        isShowingBookingClosedState = false
        binding.layoutParkingSpotsError.visibility = View.GONE

        // Hide recycler smoothly if it was visible
        binding.recyclerParkingSpots.animate().cancel()
        binding.recyclerParkingSpots.visibility = View.GONE
        binding.cardDotIndicator.visibility = View.GONE

        // Soft fade-in for empty state
        binding.tvParkingSpotsEmpty.text = message
        binding.tvParkingSpotsEmpty.alpha = 0f
        binding.tvParkingSpotsEmpty.translationY = dpToPx(6).toFloat()
        binding.tvParkingSpotsEmpty.visibility = View.VISIBLE
        
        binding.tvParkingSpotsEmpty.animate()
            .alpha(1f)
            .translationY(0f)
            .setDuration(250)
            .setInterpolator(android.view.animation.DecelerateInterpolator(1.2f))
            .start()
    }

    /** Uses the same quiet status treatment as server errors, without a retry action. */
    private fun showParkingBookingsClosedState() {
        if (!hasViewBinding()) return
        val now = ParkingSpotSchedulePolicy.currentTime()
        val message = if (now.get(java.util.Calendar.HOUR_OF_DAY) >= 17) {
            getString(R.string.tomorrow_booking_opens_at_five)
        } else {
            getString(R.string.booking_opens_at_five_this_morning)
        }

        hideSpotSkeleton()
        isShowingBookingClosedState = true
        filteredSpots = emptyList()
        currentPage = 0
        binding.tvParkingSpotsEmpty.visibility = View.GONE
        binding.cardDotIndicator.visibility = View.GONE
        binding.recyclerParkingSpots.animate().cancel()
        binding.recyclerParkingSpots.visibility = View.GONE

        binding.ivParkingSpotsError.setImageResource(R.drawable.ic_time)
        binding.tvParkingSpotsErrorTitle.setText(R.string.parking_bookings_closed)
        binding.tvParkingSpotsErrorMessage.text = message
        binding.btnParkingSpotsRetry.visibility = View.GONE

        val container = binding.layoutParkingSpotsError
        if (container.visibility != View.VISIBLE) {
            container.alpha = 0f
            container.translationY = dpToPx(6).toFloat()
            container.visibility = View.VISIBLE
            container.animate()
                .alpha(1f)
                .translationY(0f)
                .setDuration(250)
                .setInterpolator(android.view.animation.DecelerateInterpolator(1.2f))
                .start()
        }
    }

    /**
     * Distinguish a connectivity/server failure from a genuinely empty result. Maps the
     * exception to a user-facing message via [AuthErrorMapper] and offers Retry when the
     * error is recoverable — never the misleading "No parking spots available".
     */
    private fun showParkingSpotsErrorState(e: Exception) {
        if (!hasViewBinding()) return
        val error = if (e is retrofit2.HttpException) {
            AuthErrorMapper.fromHttpCode(e.code())
        } else {
            AuthErrorMapper.fromException(e)
        }

        hideSpotSkeleton()
        isShowingBookingClosedState = false
        binding.tvParkingSpotsEmpty.visibility = View.GONE
        binding.cardDotIndicator.visibility = View.GONE
        binding.recyclerParkingSpots.animate().cancel()
        binding.recyclerParkingSpots.visibility = View.GONE

        binding.ivParkingSpotsError.setImageResource(R.drawable.ic_no_connection)
        binding.tvParkingSpotsErrorTitle.text = error.title
        binding.tvParkingSpotsErrorMessage.text = error.message
        binding.btnParkingSpotsRetry.visibility = if (error.isRetryable) View.VISIBLE else View.GONE

        val container = binding.layoutParkingSpotsError
        if (container.visibility != View.VISIBLE) {
            container.alpha = 0f
            container.translationY = dpToPx(6).toFloat()
            container.visibility = View.VISIBLE
            container.animate()
                .alpha(1f)
                .translationY(0f)
                .setDuration(250)
                .setInterpolator(android.view.animation.DecelerateInterpolator(1.2f))
                .start()
        }
    }

    private fun setupClickListeners() {
        binding.brandFollowSwitcher.setOnClickListener {
            it.performHapticFeedback(android.view.HapticFeedbackConstants.CONTEXT_CLICK)
            // Cast a spark burst, then open Instagram a beat later so the tap feels causal.
            binding.brandFollowSwitcher.castBurst()
            it.postDelayed({
                if (hasViewBinding()) openInstagram()
            }, 240)
        }

        // Reward coin — the coin flies off the header into the reward sheet's
        // minting chamber, so opening reads as one continuous gesture.
        binding.btnHeroReward.setOnClickListener {
            it.performHapticFeedback(android.view.HapticFeedbackConstants.CONTEXT_CLICK)

            // Capture the coin's on-screen rect [x, y, sizePx] for the flight.
            val coin = binding.heroRewardCoin
            val loc = IntArray(2)
            coin.getLocationOnScreen(loc)
            val startRect = intArrayOf(loc[0], loc[1], coin.width)

            // Opening the reward counts as engaging with today's daily reward.
            com.gridee.parking.utils.DailyRewardState.markSeenToday(requireContext())
            coin.setRewardAvailable(false)

            val bottomSheet =
                com.gridee.parking.ui.bottomsheet.RewardBottomSheet.newInstance(startRect)

            // Leave the coin fully in place until its bright copy is airborne in the
            // sheet, then recede it into a faint "empty socket" — so the lift-off is
            // a seamless hand-off and the header never shows a blank hole.
            bottomSheet.onCoinAirborne = {
                if (hasViewBinding()) {
                    binding.heroRewardCoin.animate()
                        .alpha(0.18f)
                        .setDuration(180)
                        .setInterpolator(android.view.animation.DecelerateInterpolator())
                        .start()
                }
            }
            // On close, the coin drops back into its socket with a small settle.
            bottomSheet.onDismissed = {
                if (hasViewBinding()) {
                    binding.heroRewardCoin.animate().cancel()
                    binding.heroRewardCoin.scaleX = 0.9f
                    binding.heroRewardCoin.scaleY = 0.9f
                    binding.heroRewardCoin.animate()
                        .alpha(1f).scaleX(1f).scaleY(1f)
                        .setInterpolator(android.view.animation.OvershootInterpolator(2.2f))
                        .setDuration(280)
                        .start()
                }
            }
            bottomSheet.show(parentFragmentManager, com.gridee.parking.ui.bottomsheet.RewardBottomSheet.TAG)
        }

        binding.etSearchInput.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                applySearchQuery(s?.toString() ?: "")
            }
            override fun afterTextChanged(s: android.text.Editable?) {}
        })

        // Trailing slot swaps between voice (empty) and clear (has text).
        updateSearchTrailing(binding.etSearchInput.text?.toString().orEmpty())

        binding.ivSearchMic.setOnClickListener {
            it.performHapticFeedback(android.view.HapticFeedbackConstants.CONTEXT_CLICK)
            startVoiceSearch()
        }

        binding.ivSearchClear.setOnClickListener {
            it.performHapticFeedback(android.view.HapticFeedbackConstants.CONTEXT_CLICK)
            binding.etSearchInput.setText("")
            binding.etSearchInput.requestFocus()
        }

        // The whole pill is one tap target: tapping the icon or padding focuses
        // the field and raises the keyboard, not just tapping the text itself.
        binding.searchBarContainer.setOnClickListener {
            binding.etSearchInput.requestFocus()
            val imm = it.context.getSystemService(android.content.Context.INPUT_METHOD_SERVICE)
                as? android.view.inputmethod.InputMethodManager
            imm?.showSoftInput(binding.etSearchInput, android.view.inputmethod.InputMethodManager.SHOW_IMPLICIT)
        }

        // Keyboard "Search" key confirms and dismisses the keyboard.
        binding.etSearchInput.setOnEditorActionListener { v, actionId, _ ->
            if (actionId == android.view.inputmethod.EditorInfo.IME_ACTION_SEARCH) {
                v.clearFocus()
                val imm = v.context.getSystemService(android.content.Context.INPUT_METHOD_SERVICE)
                    as? android.view.inputmethod.InputMethodManager
                imm?.hideSoftInputFromWindow(v.windowToken, 0)
                true
            } else {
                false
            }
        }

        binding.btnParkingSpotsRetry.setOnClickListener {
            it.performHapticFeedback(android.view.HapticFeedbackConstants.CONTEXT_CLICK)
            refreshParkingSpots(showBlockingLoading = true)
        }
    }

    private fun openExternalUrl(url: String) {
        runCatching {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        }.onFailure {
            showToast(getString(R.string.unable_to_open_link))
        }
    }

    /**
     * Open the Gridee profile inside the Instagram app, where Follow is one tap away. Falls
     * back to the browser only if Instagram isn't installed — that's where follows convert.
     */
    private fun openInstagram() {
        val appIntent = Intent(Intent.ACTION_VIEW, Uri.parse("http://instagram.com/_u/_gridee_"))
            .setPackage("com.instagram.android")
        runCatching { startActivity(appIntent) }
            .onFailure { openExternalUrl(INSTAGRAM_URL) }
    }

    companion object {
        private const val PAGE_SIZE = 4
        private const val PREFS_NAME = "gridee_prefs"
        private const val KEY_LAST_SPOT_PAGE_SIZE = "home_last_spot_page_size"
        private const val INSTAGRAM_URL = "https://www.instagram.com/_gridee_?igsh=bHhxZmJ6eGs4Y2tk"
        private const val NATIVE_AD_VISIBLE_REFRESH_MS = 90_000L

        // Keep these views available for a future campaign without showing them on Home today.
        private const val SHOW_STEP_BANNER = false
        private const val SHOW_SLOT_FILTERS = false
        private const val MENU_FILTER_AUTO = 1
        private const val MENU_FILTER_MORNING = 2
        private const val MENU_FILTER_EVENING = 3

        fun newInstance(userName: String? = null): HomeFragment {
            val fragment = HomeFragment()
            userName?.let {
                val args = android.os.Bundle()
                args.putString("USER_NAME", it)
                fragment.arguments = args
            }
            return fragment
        }
    }
}
