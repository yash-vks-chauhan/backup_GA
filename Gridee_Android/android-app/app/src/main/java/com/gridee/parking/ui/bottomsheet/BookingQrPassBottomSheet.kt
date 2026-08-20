package com.gridee.parking.ui.bottomsheet

import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.animation.PropertyValuesHolder
import android.animation.ValueAnimator
import android.app.Dialog
import android.content.DialogInterface
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.RenderEffect
import android.graphics.Shader
import android.graphics.drawable.ColorDrawable
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.view.animation.PathInterpolator
import android.window.OnBackInvokedCallback
import android.window.OnBackInvokedDispatcher
import androidx.core.content.ContextCompat
import androidx.core.os.bundleOf
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.doOnPreDraw
import androidx.core.view.isInvisible
import androidx.core.view.isVisible
import androidx.core.view.updateLayoutParams
import androidx.core.view.updatePadding
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.setFragmentResult
import androidx.lifecycle.lifecycleScope
import com.gridee.parking.R
import com.gridee.parking.data.model.CustomAd
import com.gridee.parking.data.repository.CustomAdsRepository
import com.gridee.parking.databinding.BottomSheetBookingQrPassBinding
import com.gridee.parking.ui.adapters.Booking
import com.gridee.parking.ui.adapters.BookingStatus
import com.gridee.parking.ui.ads.BookingQrCampaignPolicy
import androidx.viewpager2.widget.ViewPager2
import com.gridee.parking.ui.ads.BookingQrNativeAdView
import com.gridee.parking.ui.ads.CustomAdPlacement
import com.gridee.parking.ui.ads.EventDeckAdapter
import com.gridee.parking.ui.bookings.BookingPassText
import com.gridee.parking.ui.views.SkeletonShimmer
import com.gridee.parking.utils.AdRevenueAnalytics
import com.gridee.parking.utils.BookingQrCodeGenerator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * The booking pass: the code, the booking it belongs to, and a rail of everything sold.
 *
 * One surface, one screen, nothing below the fold. The code sits at the top and the ad zone
 * takes the rest of the page below it, separated by space rather than by a panel. The screen
 * used to need 917 dp of vertical space and had 736 dp to put it in, so the AdMob card ran
 * off the bottom on almost every open and served a partial view of a creative nobody read.
 *
 * The ad zone is one full-width rail carrying both kinds of paid surface — the QR placement's
 * AdMob native card first, partner campaigns stacked behind it — and it is never empty:
 *  - [TrayState.RAIL]  at least one creative, AdMob and/or campaigns, one page at a time
 *  - [TrayState.HOUSE] nothing at all, so a Gridee promo takes a single row
 *
 * The two used to sit side by side, which split the width into a 120 dp AdMob column and a
 * 235 dp poster. Neither read well and the poster took every look; full width one at a time
 * is worth more to both and costs no extra height.
 *
 * Two rules the rail exists to keep:
 *
 *  - **The AdMob page is only ever laid out when it can be seen.** It loads from the first
 *    frame inside a zero-size GONE host, joins the rail only once it has a creative, and is
 *    withheld entirely if the user has already swiped past page 0 by then. Every page of the
 *    rail is held in memory at once — the stack behind the front card is the whole point — so
 *    a page added out of view would log an impression nobody saw.
 *  - **Labels ride with their creative.** There is no section heading anywhere in this screen,
 *    so a rail with nothing to show is simply not laid out, rather than leaving a heading
 *    stranded over a void the way "PARTNER SPOTLIGHT" used to. Every page wears the same "Ad"
 *    chip in the same corner, so which page is up never changes what the disclosure says.
 */
class BookingQrPassBottomSheet : DialogFragment() {

    private var _binding: BottomSheetBookingQrPassBinding? = null
    private val binding get() = requireNotNull(_binding)

    private enum class TrayState { LOADING, RAIL, HOUSE }

    private val customAdsRepository by lazy { CustomAdsRepository(requireContext()) }
    private lateinit var deckAdapter: EventDeckAdapter

    private var nativeAdView: BookingQrNativeAdView? = null
    private var campaigns: List<CustomAd> = emptyList()
    private val failedCampaignIds = mutableSetOf<String>()
    private val reportedCampaignIds = mutableSetOf<String>()
    private var shownRailKeys: List<String> = emptyList()
    private var trayState = TrayState.LOADING
    private var campaignsResolved = false

    /** The AdMob unit has answered — with a creative, with no fill, or not at all in time. */
    private var nativeResolved = false
    private var nativeLoaded = false
    private var railCommitted = false

    /** Once true, every render commits whatever is live rather than waiting for both sides. */
    private var deadlinePassed = false

    /** Set once the user leaves page 0 under their own steam. See the class note. */
    private var userMovedRail = false
    private var skeletonAnimator: ValueAnimator? = null
    private var blurAnimator: ValueAnimator? = null
    private var currentBlurRadius = 0f
    private var plateSizePending = false
    private var deckSizedForWidth = 0
    private var deckTrayMinPx = 0

    private var originalScreenBrightness: Float? = null
    private var entranceAnimator: AnimatorSet? = null
    private var exitAnimationRunning = false
    private var bypassExitAnimation = false
    private var backInvokedCallback: OnBackInvokedCallback? = null

    private val ticker = Handler(Looper.getMainLooper())
    private val tickRunnable = object : Runnable {
        override fun run() {
            renderStatusLine()
            ticker.postDelayed(this, TICK_INTERVAL_MS)
        }
    }

    /**
     * A slow campaign response or a slow ad request must not hold the rail on a skeleton.
     * When the deadline passes we commit to whichever side has answered. The other can still
     * join afterwards — a campaign list always, the AdMob page only while the user is still
     * looking at page 0.
     */
    private val trayDeadline = Runnable {
        deadlinePassed = true
        commitRail()
    }

    private val booking: Booking?
        get() = @Suppress("DEPRECATION") (arguments?.getSerializable(ARG_BOOKING) as? Booking)

    val bookingId: String
        get() = booking?.id.orEmpty()

    private val isPreviewMode: Boolean
        get() = arguments?.getBoolean(ARG_PREVIEW_MODE, false) == true

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setStyle(STYLE_NO_TITLE, R.style.Theme_Gridee_NoActionBar)
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        return Dialog(requireContext(), theme).apply {
            setCanceledOnTouchOutside(false)
            setOnKeyListener { _, keyCode, event ->
                if (keyCode == KeyEvent.KEYCODE_BACK && event.action == KeyEvent.ACTION_UP) {
                    this@BookingQrPassBottomSheet.dismiss()
                    true
                } else {
                    false
                }
            }
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = BottomSheetBookingQrPassBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        configureInsets()
        observeOverlayDialogs()
        bindPassContent()
        setupCampaignDeck()
        sizePlateToFit()
        showSkeleton()
        // Both sides start now and race the same deadline. The ad request used to be fired
        // only after the campaign list came back, which serialised two waits the user sat
        // through one after the other.
        startNativeAd()
        loadCampaigns()
    }

    override fun onStart() {
        super.onStart()
        dialog?.window?.let { window ->
            window.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
            window.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            window.clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
            WindowCompat.setDecorFitsSystemWindows(window, false)
            window.statusBarColor = Color.TRANSPARENT
            window.navigationBarColor = Color.TRANSPARENT
            window.isNavigationBarContrastEnforced = false

            val isNightMode = (resources.configuration.uiMode and
                Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
            WindowCompat.getInsetsController(window, window.decorView).apply {
                isAppearanceLightStatusBars = !isNightMode
                isAppearanceLightNavigationBars = !isNightMode
            }

            if (originalScreenBrightness == null) {
                originalScreenBrightness = window.attributes.screenBrightness
            }
            window.attributes = window.attributes.apply {
                screenBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_FULL
            }
            // Brightness alone was not enough: a driver queueing 40 s at a barrier watched
            // the display sleep with the code still on it.
            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
        registerCinematicBackCallback()
        playDepthEntrance()
    }

    override fun onResume() {
        super.onResume()
        startTicking()
    }

    override fun onPause() {
        ticker.removeCallbacks(tickRunnable)
        super.onPause()
    }

    override fun onStop() {
        unregisterCinematicBackCallback()
        super.onStop()
    }

    // ───────────────────────── the pass ─────────────────────────

    private fun bindPassContent() {
        val current = booking
        binding.btnCloseQrPass.setOnClickListener { dismiss() }
        binding.btnQrPassDetails.setOnClickListener { openPassDetails() }

        // A pass with no booking behind it can only happen if the argument failed to
        // deserialise. The code still scans, so show it rather than an empty status row.
        binding.qrPassStatusLine.isVisible = current != null
        // The details sheet reads every one of its rows off the booking and dismisses itself
        // without one, so the ⓘ goes with the status line rather than opening onto nothing.
        binding.btnQrPassDetails.isVisible = current != null
        renderStatusLine()

        val instruction = if (current?.status == BookingStatus.ACTIVE) {
            R.string.pass_hold_up_exit
        } else {
            R.string.pass_hold_up_entry
        }
        binding.tvQrPassInstruction.setText(instruction)

        // The spot, the lot and the vehicle used to be printed under the code. They are the
        // booking's details, not the scan's, and the details sheet behind the ⓘ carries all
        // three — along with the booking ID support asks for. That sheet is the only place
        // any of it appears, so the ⓘ has to stay wired for the trade to hold.

        lifecycleScope.launch {
            val bitmap = withContext(Dispatchers.Default) {
                BookingQrCodeGenerator.generate(bookingId, QR_BITMAP_SIZE_PX)
            }
            _binding?.ivQrPassCode?.setImageBitmap(bitmap)
        }
    }

    /**
     * The booking behind the code: the ID, the times, the amount, the spot and the lot.
     *
     * None of it prints on the pass — the pass is for the scan — so this sheet is the only
     * way to any of it, and the ID is the one an operator or support will ask for out loud.
     * Guarded the same way the spotlight is: nothing opens over a saved state, and a second
     * tap while it is already up is the same sheet, not a second copy of it.
     */
    private fun openPassDetails() {
        val current = booking ?: return
        if (childFragmentManager.isStateSaved) return
        if (childFragmentManager.findFragmentByTag(BookingPassDetailsDialog.TAG) != null) return
        setPassBlurred(true)
        BookingPassDetailsDialog.newInstance(current)
            .show(childFragmentManager, BookingPassDetailsDialog.TAG)
    }

    /**
     * "Live · 1h 24m left · until 10:51 pm" or "Booked · Starts in 44 min", in the app's own
     * words and the app's own status colours. Re-rendered on the 1 s tick so the countdown
     * stays true while the pass is open.
     */
    private fun renderStatusLine() {
        val view = _binding ?: return
        val current = booking ?: return
        val isActive = current.status == BookingStatus.ACTIVE

        val colorRes = if (isActive) R.color.status_text_active else R.color.status_text_pending
        val color = ContextCompat.getColor(requireContext(), colorRes)
        view.tvQrPassState.setTextColor(color)
        view.qrPassStatusDot.backgroundTintList = android.content.res.ColorStateList.valueOf(color)
        view.tvQrPassState.setText(
            if (isActive) R.string.pass_state_live else R.string.pass_state_booked
        )

        val when0 = if (isActive) {
            val remaining = current.checkOutTimestamp - System.currentTimeMillis()
            val left = BookingPassText.remaining(remaining)
            if (current.endTime.isBlank()) {
                getString(R.string.pass_time_left, left)
            } else {
                getString(R.string.pass_time_left_until, left, current.endTime)
            }
        } else {
            BookingPassText.countdown(current.checkInTimestamp, current.startTime)
        }
        view.tvQrPassWhen.text = when0
    }

    private fun startTicking() {
        ticker.removeCallbacks(tickRunnable)
        // Only an active session counts down every second; a booked one changes by the
        // minute, but the same tick keeps both honest without a second code path.
        ticker.post(tickRunnable)
    }

    // ───────────────────────── the ad zone ─────────────────────────

    /**
     * Blurs the pass while a dialog sits over it.
     *
     * This deliberately does not use `FLAG_BLUR_BEHIND`: cross-window blur is a
     * SurfaceFlinger capability that plenty of shipping devices — Samsung's One UI among
     * them — simply do not support, and on those the flag is silently ignored, which is
     * exactly what happened here. A [RenderEffect] on our own view hierarchy is a view-level
     * effect, so it renders the same everywhere from Android 12 up.
     */
    /**
     * Every dialog that sits over the pass reports when it closes, so the blur always comes
     * back off. A dialog that blurs on the way in and has no listener here would leave the
     * pass blurred behind it for the rest of the sheet's life.
     */
    private fun observeOverlayDialogs() {
        listOf(
            EventSpotlightDialog.RESULT_KEY_CLOSED,
            BookingPassDetailsDialog.RESULT_KEY_CLOSED
        ).forEach { key ->
            childFragmentManager.setFragmentResultListener(key, viewLifecycleOwner) { _, _ ->
                setPassBlurred(false)
            }
        }
    }

    private fun setPassBlurred(blurred: Boolean) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return
        val root = _binding?.root ?: return
        blurAnimator?.cancel()
        val from = currentBlurRadius
        val to = if (blurred) BLUR_RADIUS_PX else 0f
        if (from == to) return
        blurAnimator = ValueAnimator.ofFloat(from, to).apply {
            duration = BLUR_RAMP_MS
            addUpdateListener { animation ->
                val radius = animation.animatedValue as Float
                currentBlurRadius = radius
                val target = _binding?.root ?: return@addUpdateListener
                target.setRenderEffect(
                    if (radius <= 0.01f) {
                        null
                    } else {
                        RenderEffect.createBlurEffect(radius, radius, Shader.TileMode.CLAMP)
                    }
                )
            }
            start()
        }
    }

    /**
     * Sizes the plate to whatever the tray leaves behind.
     *
     * The plate wants to be [PLATE_MAX_DP] and will take less rather than clip: a tall status
     * bar, a large system font scale or a shorter device all eat into the pass block, and a
     * fixed size would just push the bottom lines off screen. It never goes below
     * [PLATE_MIN_DP] — at that point the code is still ~33 mm across, comfortably scannable —
     * because a code that is too small to scan defeats the whole screen.
     */
    private fun sizePlateToFit() {
        val view = _binding ?: return
        val block = view.qrPassBlock
        // A permanent listener rather than a one-shot: the tray changes height when it
        // commits a state — and again when the AdMob row turns out to have no fill — and each
        // time the pass block gets more or less room. A one-shot measured the layout as it was
        // *before* the change and left the plate stuck at the smaller size.
        block.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ -> requestPlateSize() }
        requestPlateSize()
    }

    /**
     * Resizing has to happen *after* the layout pass, not during it. Setting layoutParams from
     * inside an OnLayoutChangeListener is a `requestLayout()` while a layout is in flight, and
     * the platform drops it — which is why the plate stayed stuck at its measured-once size
     * instead of growing into the space the tray freed.
     */
    private fun requestPlateSize() {
        val root = _binding?.root ?: return
        if (plateSizePending) return
        plateSizePending = true
        root.post {
            plateSizePending = false
            applyPlateSize()
        }
    }

    private fun applyPlateSize() {
        val view = _binding ?: return
        val block = view.qrPassBlock
        val plate = view.ivQrPassCode
        if (view.root.height <= 0 || block.width <= 0) return

        var occupied = 0
        for (index in 0 until block.childCount) {
            val child = block.getChildAt(index)
            if (child === plate || !child.isVisible) continue
            val params = child.layoutParams as? ViewGroup.MarginLayoutParams
            occupied += child.height + (params?.topMargin ?: 0) + (params?.bottomMargin ?: 0)
        }
        val plateMargin = (plate.layoutParams as? ViewGroup.MarginLayoutParams)?.topMargin ?: 0
        // The pass block now wraps its content, so it can no longer be the budget. The budget
        // is what is left of the screen once the nav and the tray's minimum have been paid
        // for — otherwise a 300 dp plate on a short device would push the deck off the bottom.
        val budget = view.root.height - view.qrPassNav.height -
            maxOf(dp(TRAY_MIN_DP), deckTrayMinPx)
        val byHeight = budget - occupied - plateMargin
        val byWidth = block.width - block.paddingStart - block.paddingEnd
        val target = minOf(dp(PLATE_MAX_DP), byHeight, byWidth)
            .coerceAtLeast(dp(PLATE_MIN_DP))

        if (plate.layoutParams.height == target && plate.layoutParams.width == target) return
        plate.layoutParams = plate.layoutParams.apply {
            width = target
            height = target
        }
    }

    private fun setupCampaignDeck() {
        deckAdapter = EventDeckAdapter(
            onCreativeShown = ::reportCampaignShown,
            onCreativeFailed = { ad ->
                failedCampaignIds += ad.id
                _binding?.root?.post { renderTray() }
            },
            onCardClick = ::openSpotlight,
            onNativePageBound = ::moveNativeAdIntoRail
        )
        binding.deckEventCampaigns.apply {
            adapter = deckAdapter
            // Every card is drawn at once, or the ones stacked behind would be recycled away
            // and the deck would look like a single card. commitRail raises this to the page
            // count, which also keeps the loaded AdMob page from being recycled out from
            // under itself on a long rail.
            offscreenPageLimit = DECK_OFFSCREEN_PAGES
            setPageTransformer(EventDeckAdapter.stackTransformer(resources.displayMetrics.density))
            registerOnPageChangeCallback(deckPageCallback)
            // The deck sits inside the tray's own padding, so its RecyclerView must not clip.
            (getChildAt(0) as? androidx.recyclerview.widget.RecyclerView)?.apply {
                clipToPadding = false
                clipChildren = false
            }
            // Sizing runs off the measured width rather than a dp constant, which covers a
            // fold, multi-window and rotation for free.
            addOnLayoutChangeListener { _, left, _, right, _, _, _, _, _ ->
                if (right - left != deckSizedForWidth) post { sizeDeckToWidth() }
            }
        }
    }

    /**
     * Gives the deck a 16:9 frame measured off the page width, and the pager enough height to
     * hold the stack peeking out below it.
     *
     * The card used to be a fixed 124 dp, which is a different aspect on every phone — 3:1 on
     * a 411 dp-wide screen — so centre-crop reduced landscape artwork to a strip through the
     * middle. Sizing off the width is the only way the frame matches the creative everywhere.
     */
    private fun sizeDeckToWidth() {
        val view = _binding ?: return
        val pager = view.deckEventCampaigns
        val pagerWidth = pager.width
        val cardWidth = pagerWidth - dp(DECK_SIDE_PADDING_DP) * 2
        if (cardWidth <= 0) return
        deckSizedForWidth = pagerWidth

        val cardHeight = EventDeckAdapter.cardHeightForWidth(cardWidth)
        val deckHeight = EventDeckAdapter.deckHeightForCard(cardHeight, resources.displayMetrics.density)
        deckAdapter.cardHeightPx = cardHeight
        if (pager.layoutParams.height != deckHeight) {
            pager.updateLayoutParams { height = deckHeight }
        }
        // The skeleton stands in for the whole rail, so it reserves the pager *and* the dot
        // row; the poster inside it is the front card alone. Matching only the card left the
        // skeleton short by the deck's peek and resized the plate on reveal.
        if (view.skeletonDeckPoster.layoutParams.height != cardHeight) {
            view.skeletonDeckPoster.updateLayoutParams { height = cardHeight }
        }
        val skeletonHeight = deckHeight + dp(DECK_DOTS_BLOCK_DP)
        if (view.traySkeleton.layoutParams.height != skeletonHeight) {
            view.traySkeleton.updateLayoutParams { height = skeletonHeight }
        }

        // What the ad zone needs before the plate may claim anything: a taller rail has to
        // shrink the code on a short device rather than run off the bottom of it.
        deckTrayMinPx = dp(TRAY_TOP_PADDING_DP) + deckHeight + dp(DECK_DOTS_BLOCK_DP) +
            view.qrPassTray.paddingBottom
        requestPlateSize()
    }

    private val deckPageCallback = object : ViewPager2.OnPageChangeCallback() {
        override fun onPageSelected(position: Int) {
            if (position != 0) userMovedRail = true
            renderDots(position)
        }
    }

    /**
     * Which page of the rail is up, as markers rather than a "1 / 3" counter — that read like
     * a spreadsheet and said nothing about the rail being swipeable, which on a rail nobody
     * swipes means every page after the first is worth nothing.
     *
     * The active marker stretches into a pill instead of only changing colour, the same
     * active-state idiom the tab bar uses.
     */
    private fun renderDots(selected: Int) {
        val view = _binding ?: return
        val dots = view.deckDots
        val total = deckAdapter.itemCount

        // A single page has nothing to page between, and a row of one dot under it just looks
        // like a smudge.
        dots.isVisible = total > 1
        if (total <= 1) {
            dots.removeAllViews()
            return
        }

        while (dots.childCount > total) dots.removeViewAt(dots.childCount - 1)
        while (dots.childCount < total) {
            val dot = View(requireContext()).apply {
                background = ContextCompat.getDrawable(requireContext(), R.drawable.shape_deck_dot)
            }
            dots.addView(
                dot,
                LinearLayout.LayoutParams(dp(DOT_SIZE_DP), dp(DOT_SIZE_DP)).apply {
                    marginStart = if (dots.childCount == 0) 0 else dp(DOT_GAP_DP)
                }
            )
        }

        val activeColor = ContextCompat.getColor(requireContext(), R.color.text_primary)
        val idleColor = ContextCompat.getColor(requireContext(), R.color.text_tertiary)
        for (index in 0 until dots.childCount) {
            val dot = dots.getChildAt(index)
            val active = index == selected
            dot.backgroundTintList = android.content.res.ColorStateList.valueOf(
                if (active) activeColor else idleColor
            )
            dot.alpha = if (active) 1f else DOT_IDLE_ALPHA
            val targetWidth = dp(if (active) DOT_ACTIVE_WIDTH_DP else DOT_SIZE_DP)
            if (dot.layoutParams.width != targetWidth) {
                dot.updateLayoutParams { width = targetWidth }
            }
        }
    }

    private fun showSkeleton() {
        binding.traySkeleton.isVisible = true
        // INVISIBLE, not GONE, and the reason the ad zone is a FrameLayout: everything that
        // sizes the rail — the card's 16:9 height, the skeleton's own height, and the minimum
        // the plate has to leave for the zone — is measured off the pager's laid-out width. A
        // GONE pager is never laid out, so all three stayed at their first-frame dp constants
        // and the reveal resized the tray under the code. Laid out but not drawn also means
        // the SDK cannot log an impression for a card behind the skeleton.
        binding.campaignDeckContainer.isInvisible = true
        binding.housePromoRow.isVisible = false
        skeletonAnimator = SkeletonShimmer.start(binding.traySkeleton)
        binding.root.postDelayed(trayDeadline, TRAY_DEADLINE_MS)
    }

    private fun loadCampaigns() {
        lifecycleScope.launch {
            // includePreviouslyDisplayed is on for every read here, and the deck deliberately
            // never calls markDisplayed. CustomAd.isOncePerSession() defaults to TRUE when the
            // backend leaves displayFrequency unset, so a deck that reported all of its cards
            // as displayed would be filtered down to nothing by its own next read — the deck
            // collapsed to a single card, then to none. Once-per-session is a rule for a
            // single rotating banner; a deck the user deliberately opened should show every
            // live campaign, every time.
            val cachedPrimary = customAdsRepository.getCachedAds(
                CustomAdPlacement.BOOKING_QR_PASS,
                includePreviouslyDisplayed = true
            )
            val cachedFallback = if (cachedPrimary.isEmpty()) {
                // Existing admin builds could only create HOME campaigns; the pass reuses
                // those only when it has none of its own.
                customAdsRepository.getCachedAds(
                    CustomAdPlacement.HOME,
                    includePreviouslyDisplayed = true
                )
            } else {
                emptyList()
            }
            val cached = BookingQrCampaignPolicy.select(cachedPrimary, cachedFallback)
            if (cached.isNotEmpty()) {
                campaignsResolved = true
                applyCampaigns(cached)
            }

            // Always revalidate: a campaign activated minutes ago has to appear the next time
            // the pass is opened, and the pass is opened rarely enough that one request per
            // open costs nothing.
            val freshPrimary = customAdsRepository.getAds(
                CustomAdPlacement.BOOKING_QR_PASS,
                forceRefresh = true,
                includePreviouslyDisplayed = true
            )
            val freshFallback = if (freshPrimary.isEmpty()) {
                customAdsRepository.getAds(
                    CustomAdPlacement.HOME,
                    forceRefresh = true,
                    includePreviouslyDisplayed = true
                )
            } else {
                emptyList()
            }
            campaignsResolved = true
            applyCampaigns(BookingQrCampaignPolicy.select(freshPrimary, freshFallback))
        }
    }

    private fun applyCampaigns(next: List<CustomAd>) {
        if (_binding == null) return
        Log.d(TAG, "campaigns: ${next.size} live, ${failedCampaignIds.size} dropped as broken")
        campaigns = next.filterNot { it.id in failedCampaignIds }
        renderTray()
    }

    private fun renderTray() = commitRail()

    /**
     * Builds the rail out of whatever is live, without either paid surface suppressing the
     * other.
     *
     * It holds the skeleton until both the campaign list and the ad request have answered —
     * committing on the first of the two and then growing the rail under the user a moment
     * later is worse than a skeleton for a few hundred milliseconds. Past [trayDeadline] that
     * gate is gone for the rest of the sheet's life, so a side that answers late still lands:
     * a campaign list can pull the zone back out of the house promo it fell to, and the AdMob
     * page can still join while the user is on page 0.
     */
    private fun commitRail() {
        val view = _binding ?: return
        if (!deadlinePassed && !railCommitted && !(campaignsResolved && nativeResolved)) return

        val live = campaigns.filterNot { it.id in failedCampaignIds }
        // The AdMob page joins late only while the user is still on page 0. Past that, every
        // page of the rail is held off-screen, so inserting it would log an impression for a
        // card behind the one they are actually reading.
        val includeNative = nativeLoaded && (deckAdapter.hasNativePage() || !userMovedRail)
        val items = buildList {
            if (includeNative) add(EventDeckAdapter.Item.Native)
            live.forEach { add(EventDeckAdapter.Item.Campaign(it)) }
        }

        view.root.removeCallbacks(trayDeadline)

        if (items.isEmpty()) {
            showHousePromo()
            return
        }

        val wasLoading = trayState != TrayState.RAIL
        trayState = TrayState.RAIL
        stopSkeleton()
        view.housePromoRow.isVisible = false
        view.campaignDeckContainer.isVisible = true

        // Hold every page at once: the stack peeking out from under the front card is the
        // whole idiom, and a recycled page would take the loaded AdMob card with it.
        view.deckEventCampaigns.offscreenPageLimit = maxOf(DECK_OFFSCREEN_PAGES, items.size - 1)

        // The cached read often lands first with fewer campaigns than the network read that
        // follows. Growing the rail under the user must not leave them parked on card 3 of 4
        // before they have touched it — but once they have, their page is theirs to keep.
        val keys = items.map(::railKey)
        if (keys != shownRailKeys) {
            shownRailKeys = keys
            deckAdapter.submit(items)
            if (!userMovedRail) view.deckEventCampaigns.setCurrentItem(0, false)
        }

        renderDots(view.deckEventCampaigns.currentItem)
        if (wasLoading) SkeletonShimmer.revealView(view.campaignDeckContainer)
        railCommitted = true
        requestPlateSize()
    }

    private fun railKey(item: EventDeckAdapter.Item): String = when (item) {
        is EventDeckAdapter.Item.Native -> "admob"
        is EventDeckAdapter.Item.Campaign -> item.ad.id
    }

    /** Last rung: no campaign and no fill. The zone shrinks to one row rather than a hole. */
    private fun showHousePromo() {
        val view = _binding ?: return
        if (trayState == TrayState.HOUSE) return
        trayState = TrayState.HOUSE
        stopSkeleton()
        view.root.removeCallbacks(trayDeadline)
        view.campaignDeckContainer.isVisible = false
        view.housePromoRow.isVisible = true
        SkeletonShimmer.revealView(view.housePromoRow)
        requestPlateSize()
        view.housePromoRow.setOnClickListener {
            setFragmentResult(RESULT_KEY_HOUSE_PROMO, bundleOf())
            dismiss()
        }
        view.btnHousePromo.setOnClickListener { view.housePromoRow.performClick() }
    }

    private fun stopSkeleton() {
        skeletonAnimator?.cancel()
        skeletonAnimator = null
        _binding?.traySkeleton?.isVisible = false
    }

    /**
     * Starts the ad request on the first frame, from a zero-size GONE host.
     *
     * The host exists because the load path guards its callbacks on `isAttachedToWindow` — a
     * GONE view is attached, so the request completes normally, while having no area at all
     * means the SDK cannot log an impression for a card that is not on the rail yet. The card
     * moves into its page in [moveNativeAdIntoRail] once it has something to show.
     */
    private fun startNativeAd() {
        val view = _binding ?: return
        nativeAdView?.destroy()
        view.nativeAdHost.removeAllViews()

        val native = BookingQrNativeAdView(requireContext()).apply {
            onAdLoaded = {
                nativeLoaded = true
                nativeResolved = true
                renderTray()
            }
            onAdUnavailable = {
                nativeLoaded = false
                nativeResolved = true
                renderTray()
            }
            onLoadEvent = { if (!isPreviewMode) AdRevenueAnalytics.logBookingQrLoad(context, it) }
            onAdImpression = { if (!isPreviewMode) AdRevenueAnalytics.logBookingQrImpression(context) }
            onAdClicked = { if (!isPreviewMode) AdRevenueAnalytics.logBookingQrClick(context) }
            onPaidEvent = { if (!isPreviewMode) AdRevenueAnalytics.logBookingQrPaidEvent(context, it) }
            onVideoEvent = { action, muted ->
                if (!isPreviewMode) AdRevenueAnalytics.logBookingQrVideoEvent(context, action, muted)
            }
        }
        nativeAdView = native
        view.nativeAdHost.addView(
            native,
            ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        )
        native.post { native.load() }
    }

    /**
     * Moves the loaded card out of its hidden host and into the rail page the adapter just
     * created. [BookingQrNativeAdView.retainOnDetach] covers the moment it has no parent —
     * a plain detach destroys the ad, which is right for teardown and would here throw away
     * the creative at the instant it was ready to be seen.
     */
    private fun moveNativeAdIntoRail(slot: FrameLayout) {
        val native = nativeAdView ?: return
        if (native.parent === slot) return
        native.retainOnDetach = true
        try {
            (native.parent as? ViewGroup)?.removeView(native)
            slot.removeAllViews()
            slot.addView(
                native,
                ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
            )
        } finally {
            native.retainOnDetach = false
        }
    }

    /**
     * Every card the deck renders is an impression — the stacked ones are on screen too, just
     * partly covered — reported once each, when their creative actually lands.
     */
    private fun reportCampaignShown(ad: CustomAd) {
        if (isPreviewMode || !reportedCampaignIds.add(ad.id)) return
        // Impression yes, markDisplayed deliberately no — see loadCampaigns. Marking would
        // also rob the Home placement of a campaign this screen only borrowed.
        customAdsRepository.trackImpression(ad)
    }

    /**
     * Opening the spotlight *is* the click: it is the moment the user chose this campaign
     * over the other two. Tracking it here rather than on the CTA means interest is measured
     * even when they read the detail and decide not to buy.
     */
    private fun openSpotlight(ad: CustomAd) {
        if (childFragmentManager.isStateSaved) return
        if (childFragmentManager.findFragmentByTag(EventSpotlightDialog.TAG) != null) return
        if (!isPreviewMode) customAdsRepository.trackClick(ad)
        setPassBlurred(true)
        EventSpotlightDialog.newInstance(ad, isPreviewMode)
            .show(childFragmentManager, EventSpotlightDialog.TAG)
    }

    // ───────────────────────── window plumbing ─────────────────────────

    private fun configureInsets() {
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { _, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            // The header wraps its own content, so the status bar is paid for in padding
            // rather than by overwriting a fixed height with one.
            binding.qrPassNav.updatePadding(top = dp(NAV_VERTICAL_PADDING_DP) + systemBars.top)
            // The ad zone runs to the bottom edge, so its padding absorbs the gesture inset.
            binding.qrPassTray.updatePadding(bottom = dp(18) + systemBars.bottom)
            insets
        }
        ViewCompat.requestApplyInsets(binding.root)
    }

    private fun registerCinematicBackCallback() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU || backInvokedCallback != null) return
        val callback = OnBackInvokedCallback { dismiss() }
        dialog?.onBackInvokedDispatcher?.registerOnBackInvokedCallback(
            OnBackInvokedDispatcher.PRIORITY_DEFAULT,
            callback
        )
        backInvokedCallback = callback
    }

    private fun unregisterCinematicBackCallback() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        backInvokedCallback?.let { callback ->
            dialog?.onBackInvokedDispatcher?.unregisterOnBackInvokedCallback(callback)
        }
        backInvokedCallback = null
    }

    /** Fast cinematic Z-axis entrance: emerge, pass the resting plane, then settle. */
    private fun playDepthEntrance() {
        val root = _binding?.root ?: return
        entranceAnimator?.cancel()
        root.alpha = 0f
        root.scaleX = ENTRANCE_START_SCALE
        root.scaleY = ENTRANCE_START_SCALE
        root.doOnPreDraw {
            if (_binding == null || exitAnimationRunning) return@doOnPreDraw
            root.cameraDistance = resources.displayMetrics.density * CAMERA_DISTANCE_DP

            val emerge = ObjectAnimator.ofPropertyValuesHolder(
                root,
                PropertyValuesHolder.ofFloat(View.ALPHA, 0f, 1f),
                PropertyValuesHolder.ofFloat(View.SCALE_X, ENTRANCE_START_SCALE, ENTRANCE_OVERSHOOT_SCALE),
                PropertyValuesHolder.ofFloat(View.SCALE_Y, ENTRANCE_START_SCALE, ENTRANCE_OVERSHOOT_SCALE)
            ).apply {
                duration = ENTRANCE_EMERGE_MS
                interpolator = PathInterpolator(0.18f, 0.82f, 0.18f, 1f)
            }
            val settle = ObjectAnimator.ofPropertyValuesHolder(
                root,
                PropertyValuesHolder.ofFloat(View.SCALE_X, ENTRANCE_OVERSHOOT_SCALE, 1f),
                PropertyValuesHolder.ofFloat(View.SCALE_Y, ENTRANCE_OVERSHOOT_SCALE, 1f)
            ).apply {
                duration = ENTRANCE_SETTLE_MS
                interpolator = PathInterpolator(0.2f, 0f, 0f, 1f)
            }
            entranceAnimator = AnimatorSet().apply {
                playSequentially(emerge, settle)
                start()
            }
        }
    }

    private fun animateDepthExit(onFinished: () -> Unit) {
        val root = _binding?.root
        if (root == null || !root.isAttachedToWindow) {
            bypassExitAnimation = true
            onFinished()
            return
        }
        if (exitAnimationRunning) return
        exitAnimationRunning = true
        entranceAnimator?.cancel()
        root.animate().cancel()
        root.animate()
            .alpha(0f)
            .scaleX(EXIT_END_SCALE)
            .scaleY(EXIT_END_SCALE)
            .setDuration(EXIT_DURATION_MS)
            .setInterpolator(PathInterpolator(0.55f, 0f, 1f, 0.45f))
            .withEndAction {
                bypassExitAnimation = true
                onFinished()
            }
            .start()
    }

    override fun dismiss() {
        if (bypassExitAnimation || _binding == null) {
            super.dismiss()
        } else {
            animateDepthExit { super.dismiss() }
        }
    }

    override fun dismissAllowingStateLoss() {
        if (bypassExitAnimation || _binding == null) {
            super.dismissAllowingStateLoss()
        } else {
            animateDepthExit { super.dismissAllowingStateLoss() }
        }
    }

    override fun onDismiss(dialog: DialogInterface) {
        setFragmentResult(RESULT_KEY_DISMISSED, bundleOf(RESULT_BOOKING_ID to bookingId))
        super.onDismiss(dialog)
    }

    override fun onDestroyView() {
        ticker.removeCallbacks(tickRunnable)
        blurAnimator?.cancel()
        blurAnimator = null
        currentBlurRadius = 0f
        _binding?.root?.removeCallbacks(trayDeadline)
        stopSkeleton()
        entranceAnimator?.cancel()
        entranceAnimator = null
        unregisterCinematicBackCallback()
        _binding?.root?.animate()?.cancel()
        _binding?.deckEventCampaigns?.unregisterOnPageChangeCallback(deckPageCallback)
        _binding?.deckEventCampaigns?.adapter = null
        nativeAdView?.destroy()
        nativeAdView = null
        dialog?.window?.let { window ->
            window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            originalScreenBrightness?.let { original ->
                window.attributes = window.attributes.apply { screenBrightness = original }
            }
        }
        originalScreenBrightness = null
        exitAnimationRunning = false
        bypassExitAnimation = false
        _binding = null
        super.onDestroyView()
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    companion object {
        const val TAG = "BookingQrPassBottomSheet"
        const val RESULT_KEY_DISMISSED = "booking_qr_pass_dismissed"
        const val RESULT_KEY_HOUSE_PROMO = "booking_qr_pass_house_promo"
        const val RESULT_BOOKING_ID = "booking_id"

        private const val ARG_BOOKING = "booking"
        private const val ARG_PREVIEW_MODE = "preview_mode"
        private const val QR_BITMAP_SIZE_PX = 768
        /**
         * The plate takes whatever the header and the ad zone leave it, up to this. Raising it
         * to 340 to close the gap between the instruction and the rail was tried and reverted —
         * the code read as oversized. The slack under the instruction stays.
         */
        private const val PLATE_MAX_DP = 300
        private const val PLATE_MIN_DP = 212
        private const val DECK_OFFSCREEN_PAGES = 3

        /** Must match the deck page's own paddingHorizontal in item_event_deck_card.xml. */
        private const val DECK_SIDE_PADDING_DP = 2

        /** Must match the header's own paddingVertical in the layout. */
        private const val NAV_VERTICAL_PADDING_DP = 10

        /** Ad-zone paddingTop, and the dot row plus its margin under the rail. */
        private const val TRAY_TOP_PADDING_DP = 16
        private const val DECK_DOTS_BLOCK_DP = 24

        /**
         * First-frame estimate of what the ad zone needs, replaced by the measured
         * `deckTrayMinPx` on the first layout pass. Sized for a narrow phone so the plate
         * settles by growing rather than by shrinking away from the edge it just claimed.
         */
        private const val TRAY_MIN_DP = 260

        private const val DOT_SIZE_DP = 6
        private const val DOT_ACTIVE_WIDTH_DP = 16
        private const val DOT_GAP_DP = 5
        private const val DOT_IDLE_ALPHA = 0.45f
        private const val TICK_INTERVAL_MS = 1_000L
        private const val TRAY_DEADLINE_MS = 1_200L
        private const val BLUR_RADIUS_PX = 26f
        private const val BLUR_RAMP_MS = 180L
        private const val CAMERA_DISTANCE_DP = 8_000f
        private const val ENTRANCE_START_SCALE = 0.93f
        private const val ENTRANCE_OVERSHOOT_SCALE = 1.012f
        private const val EXIT_END_SCALE = 0.93f
        private const val ENTRANCE_EMERGE_MS = 185L
        private const val ENTRANCE_SETTLE_MS = 55L
        private const val EXIT_DURATION_MS = 145L

        fun newInstance(booking: Booking, previewMode: Boolean = false) =
            BookingQrPassBottomSheet().apply {
                arguments = bundleOf(
                    ARG_BOOKING to booking,
                    ARG_PREVIEW_MODE to previewMode
                )
            }
    }
}
