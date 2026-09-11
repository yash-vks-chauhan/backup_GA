package com.gridee.parking.ui.fragments

import android.graphics.Color
import android.graphics.RenderEffect
import android.graphics.Shader
import android.graphics.drawable.ColorDrawable
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.widget.TextView
import android.view.HapticFeedbackConstants
import android.util.TypedValue
import androidx.core.animation.doOnEnd
import androidx.core.content.ContextCompat
import androidx.core.view.doOnLayout
import androidx.core.view.doOnNextLayout
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.gridee.parking.R
import com.gridee.parking.ui.components.CustomBottomNavigation
import com.gridee.parking.ui.main.MainContainerActivity
import android.content.Intent
import com.gridee.parking.GrideeApplication
import com.gridee.parking.data.model.Booking as BackendBooking
import com.gridee.parking.data.model.BookingPolicyResolver
import com.gridee.parking.databinding.FragmentBookingsNewBinding
import com.gridee.parking.ui.adapters.Booking
import com.gridee.parking.ui.adapters.BookingStatus
import com.gridee.parking.ui.adapters.BookingRailAdapter
import com.gridee.parking.ui.adapters.BookingsAdapter
import com.gridee.parking.ui.base.BaseTabFragment
import com.gridee.parking.databinding.BottomSheetCancelBookingBinding
import android.view.WindowManager
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.gridee.parking.debug.DebugSampleBookings
import com.gridee.parking.data.repository.BookingRepository
import com.gridee.parking.data.repository.BookingMutationRefreshCoordinator
import com.gridee.parking.data.repository.ParkingRepository
import com.gridee.parking.notifications.BookingActiveNotificationManager
import com.gridee.parking.notifications.BookingStatusEvents
import com.gridee.parking.ui.bookings.BookingAdStatus
import com.gridee.parking.ui.bookings.BookingAdTarget
import com.gridee.parking.ui.bookings.BookingAdTransition
import com.gridee.parking.ui.bookings.BookingAdTransitionDetector
import com.gridee.parking.ui.bookings.BookingDetailsActivity
import com.gridee.parking.ui.bottomsheet.BookingQrPassBottomSheet
import com.gridee.parking.ui.wallet.WalletRefreshSource
import com.gridee.parking.utils.AuthSession
import com.gridee.parking.utils.BookingAdTransitionStore
import com.gridee.parking.utils.AppForegroundTracker
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*
import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.view.animation.DecelerateInterpolator
import kotlin.math.abs
import androidx.dynamicanimation.animation.SpringAnimation
import androidx.dynamicanimation.animation.SpringForce
import androidx.dynamicanimation.animation.DynamicAnimation
import com.gridee.parking.ui.bookings.BookingsSkeletonHint
import com.gridee.parking.ui.views.SkeletonShimmer
import com.gridee.parking.ui.views.StageRailView
import androidx.core.view.isVisible

class BookingsFragmentNew : BaseTabFragment<FragmentBookingsNewBinding>() {

    private lateinit var railAdapter: BookingRailAdapter
    private var userBookings = mutableListOf<BackendBooking>()
    /** Receipts the user has already moved on from; in-memory, so a fresh launch forgets. */

    /** Header height the refresh spinner is currently offset for; -1 until first layout. */
    private var blurOverlayView: View? = null
    private var currentBlurAnimator: ValueAnimator? = null
    private var lastActiveBookingId: String? = null
    private var bookingsLoadJob: Job? = null
    private var refreshAfterCurrentLoad = false
    private var forceActiveRefreshAfterCurrentLoad = false
    private var forceHistoryRefreshAfterCurrentLoad = false
    private var includeHistoryAfterCurrentLoad = false
    private var observedAdStatuses: Map<String, BookingAdStatus>? = null
    private val pendingAdTransitions = ArrayDeque<PendingAdTransition>()
    private var activeAdTransition: PendingAdTransition? = null
    private var visibleQrBookingId: String? = null
    private var skeletonAnimator: android.animation.ValueAnimator? = null
    private var skeletonShowing = false
    private var bookingQrPassSheet: BookingQrPassBottomSheet? = null
    private var handledForegroundGeneration = Long.MIN_VALUE
    private var loadedParkingLotId: String? = null
    private var hasLoadedBookings = false
    private var pendingPushRefresh = false
    private var pendingPushRequiresActiveNetwork = false
    private var pendingPushRequiresHistoryNetwork = false
    private var lastManualRefreshElapsedMs = Long.MIN_VALUE
    private val cancellingBookingIds = mutableSetOf<String>()

    private data class PendingAdTransition(
        val bookingId: String,
        val target: BookingAdTarget,
        val queuedAtMs: Long
    ) {
        val eventKey: String = "$bookingId:${target.name}"
    }

    // Cache for parking lot and spot names
    private val parkingLotCache = mutableMapOf<String, String>() // lotId -> name
    private val parkingSpotCache = mutableMapOf<String, String>() // spotId -> name
    private val loadedSpotLotIds = mutableSetOf<String>()
    private data class NavigationRequest(
        val showPending: Boolean,
        val highlightBookingId: String?,
        val openBookingId: String?
    )
    private var pendingNavigationRequest: NavigationRequest? = null
    private var pendingHighlightBookingId: String? = null
    private var pendingOpenBookingId: String? = null
    /**
     * Watches for the operator acting on a booking, wherever the user happens to be.
     *
     * The check-in choreography — pass closes, interstitial, status flips — hangs off
     * detectBookingAdTransitions, which only runs inside a fetch. Nothing else notices a scan:
     * there is no socket, the backend sends no push on check-in, and pull-to-refresh is gone.
     *
     * It is not tied to the QR pass, because the pass is not the only way in — an operator can
     * check a car in off its number plate with the phone on any other tab. So it runs whenever a
     * booking exists that can still move, and stops when none does.
     */
    private val transitionWatchHandler = Handler(Looper.getMainLooper())
    private val transitionWatchRunnable = object : Runnable {
        override fun run() {
            if (!hasViewBinding() || view == null) return
            if (!hasWatchableBooking()) return
            // A live booking is watched for check-OUT, which lands it in history — so that read
            // has to include history or the booking simply vanishes from the fetch and the
            // transition is never seen. A booked one is watched for check-IN, which stays in the
            // active list, so it can skip the second request.
            val awaitingCheckOut = userBookings.any {
                mapBackendStatus(it.status) == BookingStatus.ACTIVE
            }
            loadUserBookings(
                forceActiveRefresh = true,
                includeHistory = awaitingCheckOut,
                queueIfBusy = false,
            )
            transitionWatchHandler.postDelayed(this, watchIntervalMs())
        }
    }

    private fun hasWatchableBooking(): Boolean = userBookings.any {
        val status = mapBackendStatus(it.status)
        status == BookingStatus.PENDING || status == BookingStatus.ACTIVE
    }

    /**
     * How hard to look, by how close the user is to the moment. Standing at the barrier with the
     * pass open is the one place a delay reads as a broken app; another tab is watched slowly on
     * purpose, because a parking session runs for hours.
     */
    private fun watchIntervalMs(): Long = when {
        visibleQrBookingId != null -> WATCH_AT_BARRIER_MS
        !isHidden -> WATCH_TAB_IN_FRONT_MS
        else -> WATCH_OTHER_TAB_MS
    }

    private fun syncTransitionWatch() {
        syncTransitionAdWarmUp()
        transitionWatchHandler.removeCallbacks(transitionWatchRunnable)
        if (!isResumed || !hasViewBinding() || view == null) return
        if (!hasWatchableBooking()) return
        transitionWatchHandler.postDelayed(transitionWatchRunnable, watchIntervalMs())
    }

    /**
     * Keeps the transition interstitial warm on the same signal the watch runs on: a booking that
     * can still move.
     *
     * The ad used to be requested once per activity resume, for everyone, whether or not they
     * held a booking at all - and it only ever displays on a check-in, check-out or cancellation,
     * so the great majority of those requests loaded an ad nobody was ever shown. This is the
     * same question asked the other way round: request only while there is a transition that
     * could plausibly arrive, and stop the moment there isn't.
     *
     * Deliberately not gated on the tab being in front. An operator can check a car in off its
     * number plate while the user is on Home, which is exactly the transition this has to have an
     * ad ready for.
     */
    private fun syncTransitionAdWarmUp() {
        if (!isAdded) return
        val host = activity ?: return
        // A transition already owed counts as well as a booking that can still move. The last
        // two of the three moments — check-out and cancellation — leave nothing watchable behind
        // them, and the ad for that moment has not necessarily been shown yet: it waits here
        // whenever the operator worked the barrier while the app was not in front.
        if (hasWatchableBooking() || hasUnfinishedBookingTransition()) {
            com.gridee.parking.utils.AdMobManager.warmUpBookingInterstitial(host)
        } else {
            com.gridee.parking.utils.AdMobManager.stopBookingInterstitialWarmUp()
        }
    }

    private fun stopTransitionWatch() {
        transitionWatchHandler.removeCallbacks(transitionWatchRunnable)
    }

    private val activeTimerHandler = Handler(Looper.getMainLooper())
    private val activeTimerRunnable = object : Runnable {
        override fun run() {
            if (!hasViewBinding() || view == null) return
            if (::railAdapter.isInitialized && railAdapter.hasLiveTimer()) {
                railAdapter.tickLiveTimers()
            }
            if (hasViewBinding() && view != null) {
                activeTimerHandler.postDelayed(this, ACTIVE_TIMER_REFRESH_MS)
            }
        }
    }
    private val bookingRepository by lazy {
        GrideeApplication.instance.repositories.bookingRepository
    }
    private val parkingRepository by lazy {
        GrideeApplication.instance.repositories.parkingRepository
    }

    override fun getViewBinding(inflater: LayoutInflater, container: ViewGroup?): FragmentBookingsNewBinding {
        return FragmentBookingsNewBinding.inflate(inflater, container, false)
    }

    override fun getScrollableView(): View? {
        return try {
            binding.rvBookings
        } catch (e: IllegalStateException) {
            null
        }
    }

    override fun setupUI() {
        setupBookingQrPassResults()
        setupRecyclerView()
        applyStatusBarInset()
        showSkeletonIfExpected()
        setupEmptyState()
        observeBookingStatusEvents()
    }

    /**
     * A booking leaves this tab the moment it ends — cancelled, checked out or missed, it goes
     * to Booking History and the tab is empty again. Booking again starts on Home, so the empty
     * state hands the user straight there rather than leaving them to work it out.
     */
    /**
     * Pays the status-bar inset here rather than waiting for the activity to do it.
     *
     * MainContainerActivity.applyTopInsetToFragmentView re-pads every added fragment, but only
     * when the window dispatches insets. A fragment whose view is created after the last
     * dispatch — a tab opened for the first time, which is exactly this screen — never gets
     * that call, so its root kept paddingTop 0 and the top of the booking card was sliced off
     * by the status bar. Owning it here makes it deterministic; the activity setting the same
     * value later is a no-op.
     */
    private fun applyStatusBarInset() {
        val root = binding.root
        ViewCompat.setOnApplyWindowInsetsListener(root) { view, insets ->
            view.updatePadding(top = insets.getInsets(WindowInsetsCompat.Type.systemBars()).top)
            insets
        }
        ViewCompat.requestApplyInsets(root)
    }

    /**
     * Raises the loading state for the first paint, and only when there is something to load.
     *
     * Gated on [BookingsSkeletonHint] rather than shown unconditionally: a skeleton is a promise
     * that content is coming, and showing a card-shaped ghost to someone with no booking is a
     * promise the screen immediately breaks. It is also skipped once bookings are in hand — a
     * tab switch back to a fragment that already has data has nothing to wait for.
     */
    private fun showSkeletonIfExpected() {
        if (hasLoadedBookings || userBookings.isNotEmpty()) return
        val userId = getUserId() ?: return
        val expected = BookingsSkeletonHint.expected(requireContext(), userId)
        if (expected == BookingsSkeletonHint.Expected.NOTHING) return
        showSkeleton(live = expected == BookingsSkeletonHint.Expected.LIVE)
    }

    /**
     * Lays the ghost out as whichever card is coming.
     *
     * Booked and Live are not the same silhouette: a Booked card is the first thing on the rail
     * with two stops ahead of it and a cancel row at the bottom; a Live one has a stop behind
     * it, one ahead, and no cancel — the backend refuses cancellation once a car is checked in.
     * Drawing the wrong one is ~50 dp of height that visibly drops when the real card lands.
     */
    private fun showSkeleton(live: Boolean) {
        if (skeletonShowing) return
        skeletonShowing = true
        val skeleton = binding.layoutSkeleton
        // Muted rails: the gutter is the one part of the card that carries status colour, and
        // the skeleton has not read a status yet. Same geometry, no claim.
        val ink = ContextCompat.getColor(requireContext(), R.color.stage_rail_ink)

        skeleton.skeletonStopLead.isVisible = live
        skeleton.skeletonStopTwo.isVisible = !live
        skeleton.skeletonCancelRow.isVisible = !live
        skeleton.skeletonCancelDivider.isVisible = !live
        // The stub loses its closing row on a Live card, so it pays that space back as padding
        // rather than letting the badge sit against the card's bottom edge.
        skeleton.skeletonPassStub.updatePadding(bottom = if (live) dpToPx(18f).toInt() else 0)

        if (live) {
            railStub(skeleton.skeletonRailLead, StageRailView.Marker.DONE, StageRailView.Line.NONE, StageRailView.Line.SOLID, ink)
            railStub(skeleton.skeletonRailCard, StageRailView.Marker.CURRENT, StageRailView.Line.SOLID, StageRailView.Line.DASHED, ink)
            railStub(skeleton.skeletonRailOne, StageRailView.Marker.FUTURE, StageRailView.Line.DASHED, StageRailView.Line.NONE, ink)
        } else {
            railStub(skeleton.skeletonRailCard, StageRailView.Marker.CURRENT, StageRailView.Line.NONE, StageRailView.Line.DASHED, ink)
            railStub(skeleton.skeletonRailOne, StageRailView.Marker.FUTURE, StageRailView.Line.DASHED, StageRailView.Line.DASHED, ink)
            railStub(skeleton.skeletonRailTwo, StageRailView.Marker.FUTURE, StageRailView.Line.DASHED, StageRailView.Line.NONE, ink)
        }

        binding.rvBookings.isVisible = false
        binding.layoutEmptyState.isVisible = false
        skeleton.layoutBookingsSkeleton.isVisible = true
        skeletonAnimator = SkeletonShimmer.start(skeleton.layoutBookingsSkeleton)
    }

    /**
     * Matches BookingRailAdapter.rail so the ghost gutter lines up with the real one — same
     * marker centre, same dash pattern. A skeleton whose rail sits a few dp off its replacement
     * is the tell that gives the whole thing away.
     */
    private fun railStub(
        view: StageRailView,
        marker: StageRailView.Marker,
        lead: StageRailView.Line,
        tail: StageRailView.Line,
        ink: Int,
    ) {
        view.marker = marker
        view.leadLine = lead
        view.tailLine = tail
        view.markerCenterY = 20f * resources.displayMetrics.density
        view.accentColor = ink
        view.haloColor = 0
    }

    /**
     * Hands over to the real content.
     *
     * The skeleton is removed outright and the incoming list carries the reveal by rising into
     * place — the app's shared idiom. No crossfade: two versions of the same card dissolving
     * through each other reads as a glitch rather than as an arrival.
     */
    private fun hideSkeleton() {
        skeletonAnimator?.cancel()
        skeletonAnimator = null
        if (!skeletonShowing) return
        skeletonShowing = false
        bindingOrNull?.let { view ->
            view.layoutSkeleton.layoutBookingsSkeleton.isVisible = false
            if (view.rvBookings.isVisible) SkeletonShimmer.revealView(view.rvBookings)
            else if (view.layoutEmptyState.isVisible) SkeletonShimmer.revealView(view.layoutEmptyState)
        }
    }

    private fun setupEmptyState() {
        binding.buttonFindParking.setOnClickListener { goToHome() }
    }

    /**
     * Back to Home, where booking starts. Routed through the container's intent rather than
     * onTabSelected because onNewIntent syncs the nav pill and the fragment together.
     */
    private fun goToHome() {
        val host = activity ?: return
        startActivity(
            Intent(host, MainContainerActivity::class.java).apply {
                putExtra(MainContainerActivity.EXTRA_TARGET_TAB, CustomBottomNavigation.TAB_HOME)
                addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            }
        )
    }

    private fun dpToPx(value: Float): Float {
        return TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, value, resources.displayMetrics)
    }

    fun handleExternalNavigation(
        showPending: Boolean,
        highlightBookingId: String?,
        openBookingId: String?
    ) {
        pendingNavigationRequest = NavigationRequest(showPending, highlightBookingId, openBookingId)
        applyPendingNavigationIfReady()
        if (isAdded && view != null) {
            loadUserBookings()
        }
    }

    private fun setupRecyclerView() {
        railAdapter = BookingRailAdapter(
            onPassClick = { booking -> showBookingQrPass(booking) },
            onCancelRequested = { booking -> showCancelConfirmation(booking) },
        )
        binding.rvBookings.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = railAdapter
        }
    }

    override fun scrollToTop() {
        try {
            binding.rvBookings.smoothScrollToPosition(0)
        } catch (e: IllegalStateException) {
            // Handle case where fragment is not attached
        }
    }

    override fun onResume() {
        super.onResume()
        requireActivity().title = "My Bookings"
        startActiveTimerUpdates()
        syncTransitionWatch()
        refreshForVisibleEntry()
        // A transition detected while this tab was hidden waits here for the tab to come forward.
        processPendingBookingTransition()
    }

    override fun onPause() {
        stopActiveTimerUpdates()
        stopTransitionWatch()
        // The warm buffer itself is kept. The commonest shape of this placement is the operator
        // checking a car in while the phone is in the user's pocket, and dropping the warm ad on
        // every pause would make that a cold load every time.
        com.gridee.parking.utils.AdMobManager.pauseBookingInterstitialWarmUp()
        super.onPause()
    }

    override fun onHiddenChanged(hidden: Boolean) {
        super.onHiddenChanged(hidden)
        if (hidden) {
            stopActiveTimerUpdates()
            // Deliberately NOT stopped: an operator can check a car in off its plate while the
            // user is on another tab, and this is what notices. It just slows down.
            syncTransitionWatch()
        } else {
            // The bottom nav shows and hides this fragment rather than recreating it, so this
            // is the "screen opened" moment onResume does not get to see.
            startActiveTimerUpdates()
            syncTransitionWatch()
            refreshForVisibleEntry()
            processPendingBookingTransition()
        }
    }

    private fun refreshForVisibleEntry() {
        if (!hasViewBinding() || view == null) return
        val generation = AppForegroundTracker.currentGeneration()
        val foregroundChanged = generation != handledForegroundGeneration
        val currentLotId = currentBookingsLotId()
        val lotChanged = currentLotId != loadedParkingLotId
        val mustRefresh = pendingPushRefresh ||
            !hasLoadedBookings ||
            foregroundChanged ||
            lotChanged
        if (!mustRefresh) return

        val pushRefresh = pendingPushRefresh
        val forceActiveRefresh = pushRefresh && pendingPushRequiresActiveNetwork
        val forceHistoryRefresh = pushRefresh && pendingPushRequiresHistoryNetwork
        pendingPushRefresh = false
        pendingPushRequiresActiveNetwork = false
        pendingPushRequiresHistoryNetwork = false
        handledForegroundGeneration = generation
        loadedParkingLotId = currentLotId
        loadUserBookings(
            forceActiveRefresh = forceActiveRefresh,
            forceHistoryRefresh = forceHistoryRefresh,
            includeHistory = !hasLoadedBookings || forceHistoryRefresh ||
                foregroundChanged || lotChanged,
            queueIfBusy = pushRefresh || lotChanged,
        )
    }

    private fun startActiveTimerUpdates() {
        activeTimerHandler.removeCallbacks(activeTimerRunnable)
        activeTimerHandler.post(activeTimerRunnable)
    }

    private fun stopActiveTimerUpdates() {
        activeTimerHandler.removeCallbacks(activeTimerRunnable)
    }

    private fun observeBookingStatusEvents() {
        viewLifecycleOwner.lifecycleScope.launch {
            BookingStatusEvents.events.collect { event ->
                val terminalEvent = event.statusHint.isTerminalBookingRefreshHint()
                val appliedLocally = applyBookingStatusHint(event)
                if (event.cacheAlreadyRefreshed && appliedLocally) {
                    // The coordinator already performed the one allowed active-bookings read.
                    // Preserve the backend notification's status locally instead of fetching
                    // history as a fourth post-mutation request.
                    return@collect
                }
                if (!isResumed || isHidden) {
                    pendingPushRefresh = true
                    pendingPushRequiresActiveNetwork = pendingPushRequiresActiveNetwork ||
                        !event.cacheAlreadyRefreshed
                    pendingPushRequiresHistoryNetwork = pendingPushRequiresHistoryNetwork ||
                        (!event.cacheAlreadyRefreshed && terminalEvent)
                } else {
                    requestBookingStatusRefresh(
                        forceActiveRefresh = !event.cacheAlreadyRefreshed,
                        forceHistoryRefresh = !event.cacheAlreadyRefreshed && terminalEvent,
                    )
                }
            }
        }
    }

    private fun handleBookingQrVisibilityChanged(bookingId: String, visible: Boolean) {
        val wasVisibleBooking = visibleQrBookingId == bookingId
        visibleQrBookingId = when {
            visible -> bookingId
            wasVisibleBooking -> null
            else -> visibleQrBookingId
        }
        // Opening or closing the pass changes how hard the watch looks, not whether it runs.
        syncTransitionWatch()
    }

    private fun setupBookingQrPassResults() {
        childFragmentManager.setFragmentResultListener(
            BookingQrPassBottomSheet.RESULT_KEY_DISMISSED,
            viewLifecycleOwner
        ) { _, result ->
            val bookingId = result.getString(BookingQrPassBottomSheet.RESULT_BOOKING_ID).orEmpty()
            if (bookingId.isNotBlank()) handleBookingQrVisibilityChanged(bookingId, false)
            if (bookingQrPassSheet?.bookingId == bookingId) bookingQrPassSheet = null
        }

        bookingQrPassSheet = childFragmentManager.findFragmentByTag(
            BookingQrPassBottomSheet.TAG
        ) as? BookingQrPassBottomSheet
        bookingQrPassSheet?.bookingId?.takeIf { it.isNotBlank() }?.let { bookingId ->
            handleBookingQrVisibilityChanged(bookingId, true)
        }
    }

    private fun showBookingQrPass(booking: com.gridee.parking.ui.adapters.Booking) {
        val bookingId = booking.id.trim()
        if (!isAdded || bookingId.isBlank()) return
        val currentViewLifecycle = viewLifecycleOwnerLiveData.value?.lifecycle ?: return
        val fragmentManager = childFragmentManager
        if (!currentViewLifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED) ||
            fragmentManager.isDestroyed || fragmentManager.isStateSaved
        ) return

        (fragmentManager.findFragmentByTag(BookingQrPassBottomSheet.TAG)
            as? BookingQrPassBottomSheet)?.let { restored ->
            bookingQrPassSheet = restored
            restored.bookingId.takeIf { it.isNotBlank() }?.let { restoredBookingId ->
                handleBookingQrVisibilityChanged(restoredBookingId, true)
            }
            return
        }
        val current = bookingQrPassSheet
        // Also covers a transaction admitted earlier on this main-loop turn but not yet observed
        // by FragmentManager. New launches use showNow, while this protects an in-memory upgrade
        // edge and any future asynchronous caller.
        if (current != null && !current.isRemoving) return

        val sheet = BookingQrPassBottomSheet.newInstance(booking)
        bookingQrPassSheet = sheet
        handleBookingQrVisibilityChanged(bookingId, true)
        runCatching { sheet.showNow(fragmentManager, BookingQrPassBottomSheet.TAG) }
            .onFailure {
                bookingQrPassSheet = null
                handleBookingQrVisibilityChanged(bookingId, false)
            }
    }

    private fun dismissBookingQrPass(bookingId: String) {
        val sheet = bookingQrPassSheet?.takeIf { it.bookingId == bookingId } ?: return
        val fragmentManager = childFragmentManager
        if (fragmentManager.isDestroyed || fragmentManager.isStateSaved || !sheet.isAdded) return
        sheet.dismiss()
    }

    private fun requestBookingStatusRefresh(
        forceActiveRefresh: Boolean = true,
        forceHistoryRefresh: Boolean = false,
    ) {
        loadUserBookings(
            forceActiveRefresh = forceActiveRefresh,
            forceHistoryRefresh = forceHistoryRefresh,
            includeHistory = forceHistoryRefresh,
            queueIfBusy = true,
        )
    }

    private fun String.isTerminalBookingRefreshHint(): Boolean {
        val normalized = trim().uppercase(Locale.ROOT)
        return normalized.contains("CHECKED_OUT") ||
            normalized.contains("CHECKOUT") ||
            normalized.contains("COMPLET") ||
            normalized.contains("CANCEL") ||
            normalized.contains("ENDED") ||
            normalized.contains("EXPIRED")
    }

    /** Applies a backend push hint only to an already-known booking; unknown rows are read from cache. */
    private fun applyBookingStatusHint(event: BookingStatusEvents.Event): Boolean {
        val bookingId = event.bookingId ?: return false
        val existing = userBookings.firstOrNull { it.id == bookingId } ?: return false
        val canonicalStatus = when {
            event.statusHint.contains("CANCEL", ignoreCase = true) -> "cancelled"
            event.statusHint.isTerminalBookingRefreshHint() -> "completed"
            event.statusHint.contains("ACTIVE", ignoreCase = true) ||
                event.statusHint.contains("CHECK_IN", ignoreCase = true) ||
                event.statusHint.contains("CHECKIN", ignoreCase = true) -> "active"
            else -> event.statusHint
        }
        val oldAdStatus = bookingAdStatus(existing.status)
        val updated = existing.copy(status = canonicalStatus)
        applyUpdatedBooking(updated)
        BookingAdTransitionDetector.detect(
            previous = mapOf(bookingId to oldAdStatus),
            current = mapOf(bookingId to bookingAdStatus(canonicalStatus)),
        ).forEach(::queueBookingTransitionAd)
        return true
    }

    override fun onDestroyView() {
        skeletonAnimator?.cancel()
        skeletonAnimator = null
        skeletonShowing = false
        stopTransitionWatch()
        // Nothing is left to notice a transition, so nothing should still be requesting ads for
        // one. Restarted by the first sync after the view comes back.
        com.gridee.parking.utils.AdMobManager.stopBookingInterstitialWarmUp()
        bookingQrPassSheet = null
        visibleQrBookingId = null
        stopActiveTimerUpdates()
        toggleBackgroundBlur(false)
        super.onDestroyView()
    }

    /**
     * The whole screen, rendered from one list. Active and Booked live on the same rail now,
     * so there is nothing to filter by and nothing to switch between.
     *
     * Reached from coroutine continuations that resume on Main.immediate during view teardown
     * (config change / process death). At that point the binding is already null, so bail
     * instead of dereferencing it — the list re-renders on the next onViewCreated from the
     * cached userBookings.
     */
    private fun renderBookings() {
        if (!hasViewBinding()) return
        val live = liveBookings()
        getUserId()?.let { userId ->
            BookingsSkeletonHint.remember(
                requireContext(),
                userId,
                when {
                    live.isEmpty() -> BookingsSkeletonHint.Expected.NOTHING
                    live.any { mapBackendStatus(it.status) == BookingStatus.ACTIVE } ->
                        BookingsSkeletonHint.Expected.LIVE
                    else -> BookingsSkeletonHint.Expected.BOOKED
                }
            )
        }

        if (live.isEmpty()) {
            binding.rvBookings.visibility = View.GONE
            binding.layoutEmptyState.visibility = View.VISIBLE
            binding.tvEmptyTitle.text = getString(R.string.empty_bookings_title)
            binding.tvEmptySubtitle.text = getString(R.string.empty_bookings_body)
            // Nothing left that can move, so the watch stops paying for itself.
            syncTransitionWatch()
            hideSkeleton()
            return
        }

        binding.rvBookings.visibility = View.VISIBLE
        binding.layoutEmptyState.visibility = View.GONE
        updateAdapterWithBookings(live.map { convertToBooking(it) })
        syncTransitionWatch()
        hideSkeleton()
    }

    /**
     * What the tab shows: the booking in progress, and nothing else.
     *
     * A booking leaves this tab the moment it ends. Cancelled, checked out or missed, it goes
     * to Booking History under Profile — that is where it lives and where the app already
     * renders it properly — and the tab falls back to its empty state, which points at Home.
     *
     * This briefly showed a receipt for a just-finished booking, with "View history" and "Book
     * again" on it. It was cut: the tab is for the booking you are in the middle of, and a
     * receipt with a 30-minute half-life is a second, weaker copy of a screen that already
     * exists.
     */
    private fun liveBookings(): List<BackendBooking> = sortBookings(
        displayBookings().filter { booking ->
            val status = mapBackendStatus(booking.status)
            status == BookingStatus.ACTIVE || status == BookingStatus.PENDING
        }
    )

    /**
     * The list as the screen renders it: the real bookings, plus [DebugSampleBookings] when the
     * debug samples are switched on. Everything that talks to the backend — the notification,
     * the ad-transition baseline, cancel — keeps reading [userBookings] directly, so the fakes
     * stay purely a rendering concern.
     */
    private fun displayBookings(): List<BackendBooking> {
        if (!DebugSampleBookings.isOn) return userBookings
        return userBookings + DebugSampleBookings.bookings()
    }

    /** Newest first — the only order this screen has ever shipped. */
    private fun sortBookings(bookings: List<BackendBooking>): List<BackendBooking> {
        return bookings.sortedByDescending { getComparableTimestamp(it) }
    }

    private fun getComparableTimestamp(booking: BackendBooking): Long {
        return booking.checkInTime?.time
            ?: booking.createdAt?.time
            ?: abs(booking.id?.hashCode() ?: 0).toLong()
    }

    private fun updateAdapterWithBookings(bookings: List<Booking>) {
        railAdapter.submit(bookings)

        pendingHighlightBookingId?.let { highlightId ->
            val index = bookings.indexOfFirst { it.id == highlightId }
            if (index >= 0) {
                binding.rvBookings.post {
                    if (hasViewBinding() && view != null) {
                        binding.rvBookings.smoothScrollToPosition(index)
                    }
                }
                showToast(getString(R.string.showing_your_latest_booking))
                pendingHighlightBookingId = null
            }
        }

        pendingOpenBookingId?.let { openId ->
            val booking = bookings.firstOrNull { it.id == openId }
            if (booking != null) {
                binding.rvBookings.post {
                    if (hasViewBinding() && view != null) {
                        showBookingQrPass(booking)
                    }
                }
                pendingOpenBookingId = null
            }
        }
    }

    private fun loadUserBookings(
        forceActiveRefresh: Boolean = false,
        forceHistoryRefresh: Boolean = false,
        includeHistory: Boolean = true,
        queueIfBusy: Boolean = false,
    ) {
        if (!hasViewBinding() || view == null) return
        if (bookingsLoadJob?.isActive == true) {
            if (queueIfBusy) {
                refreshAfterCurrentLoad = true
                forceActiveRefreshAfterCurrentLoad =
                    forceActiveRefreshAfterCurrentLoad || forceActiveRefresh
                forceHistoryRefreshAfterCurrentLoad =
                    forceHistoryRefreshAfterCurrentLoad || forceHistoryRefresh
                includeHistoryAfterCurrentLoad = includeHistoryAfterCurrentLoad || includeHistory
            }
            return
        }

        val userId = getUserId()
        if (userId == null) {
            showToast(getString(R.string.please_login_to_view_your_bookings))
            userBookings.clear()
            updateActiveBookingNotification(emptyList())
            renderBookings()
            return
        }
        val requestedLotId = currentBookingsLotId()

        bookingsLoadJob = viewLifecycleOwner.lifecycleScope.launch {
            try {
                // Get active/pending + history bookings from backend using robust repository parsing.
                val primaryResult = bookingRepository.getUserBookings(forceActiveRefresh)
                val historyResult = if (includeHistory) {
                    bookingRepository.getUserBookingHistory(forceHistoryRefresh)
                } else {
                    Result.success(
                        userBookings.filter { booking ->
                            mapBackendStatus(booking.status) !in setOf(
                                BookingStatus.ACTIVE,
                                BookingStatus.PENDING,
                            )
                        }
                    )
                }
                if (primaryResult.isFailure && historyResult.isFailure) {
                    throw primaryResult.exceptionOrNull()
                        ?: historyResult.exceptionOrNull()
                        ?: IllegalStateException("Booking refresh failed")
                }
                if (currentBookingsLotId() != requestedLotId) return@launch

                val fetchedBookings = mergeBookings(
                    primaryResult.getOrDefault(emptyList()),
                    historyResult.getOrDefault(emptyList())
                )
                // A partial response may omit the booking that just crossed endpoints (for example,
                // current -> history during checkout). Retain the last snapshot only for missing
                // ids; every successfully fetched record still wins over stale local data.
                val mergedBookings = if (primaryResult.isSuccess && historyResult.isSuccess) {
                    fetchedBookings
                } else {
                    mergeBookingSnapshots(userBookings, fetchedBookings)
                }
                loadParkingDataCache(mergedBookings)
                if (currentBookingsLotId() != requestedLotId) return@launch
                val detectedTransitions = detectBookingAdTransitions(mergedBookings)
                userBookings.clear()

                if (mergedBookings.isNotEmpty()) {
                    userBookings.addAll(mergedBookings)
                }

                updateActiveBookingNotification(mergedBookings)
                hasLoadedBookings = true
                detectedTransitions.forEach(::queueBookingTransitionAd)
                // After the queueing, never before it. Check-out and cancellation stop the
                // booking being watchable in the very same pass that queues its ad, so syncing
                // first would tear the warm buffer down microseconds before the ad that needs it.
                // The render that would otherwise sync this is held back below while one is owed.
                syncTransitionAdWarmUp()
                // Held back while a transition ad is still owed: the interstitial's terminal
                // callback re-renders, and painting the new state behind the ad would reveal it
                // twice.
                if (!hasUnfinishedBookingTransition()) {
                    renderBookings()
                }

            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                if (currentBookingsLotId() != requestedLotId) return@launch
                // Keep the last verified state during a transient garage/network failure. Clearing
                // it would hide the QR and stop the bounded retry loop at exactly the wrong time.
                renderBookings()
            } finally {
                bookingsLoadJob = null
                processPendingBookingTransition()

                val shouldRefreshAgain = refreshAfterCurrentLoad
                val shouldForceActiveRefreshAgain = forceActiveRefreshAfterCurrentLoad
                val shouldForceHistoryRefreshAgain = forceHistoryRefreshAfterCurrentLoad
                val shouldIncludeHistoryAgain = includeHistoryAfterCurrentLoad
                refreshAfterCurrentLoad = false
                forceActiveRefreshAfterCurrentLoad = false
                forceHistoryRefreshAfterCurrentLoad = false
                includeHistoryAfterCurrentLoad = false
                if (shouldRefreshAgain && hasViewBinding() && view != null) {
                    binding.root.post {
                        loadUserBookings(
                            forceActiveRefresh = shouldForceActiveRefreshAgain,
                            forceHistoryRefresh = shouldForceHistoryRefreshAgain,
                            includeHistory = shouldIncludeHistoryAgain,
                            queueIfBusy = false
                        )
                    }
                }
            }
        }
    }

    private fun currentBookingsLotId(): String? =
        AuthSession.getParkingLotId(requireContext())
            ?.trim()
            ?.takeIf { it.isNotEmpty() }

    /**
     * The previously seen statuses are the baseline. They are persisted, because the operator
     * performs the check-in while this app is in the user's pocket — the transition is almost
     * always first observed by the very first refresh of a launch, and an in-memory baseline
     * would silently absorb it instead of reporting it.
     */
    private fun detectBookingAdTransitions(bookings: List<BackendBooking>): List<BookingAdTransition> {
        if (!isAdded) return emptyList()
        val context = context ?: return emptyList()
        val userId = getUserId() ?: return emptyList()

        val current = bookings.mapNotNull { booking ->
            val id = booking.id?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
            id to bookingAdStatus(booking.status)
        }.toMap()
        // A failed or partial fetch must not be mistaken for "every booking disappeared", which
        // would wipe the baseline and swallow the next transition.
        if (current.isEmpty()) {
            return emptyList()
        }

        val previous = observedAdStatuses ?: loadPersistedAdStatuses(context, userId)
        // Bookings absent from this fetch keep their last known status for the same reason.
        val merged = if (previous == null) current else previous + current
        observedAdStatuses = merged
        BookingAdTransitionStore.save(context, userId, merged.mapValues { it.value.name })
        if (previous == null) return emptyList()

        return BookingAdTransitionDetector.detect(previous, current)
    }

    private fun loadPersistedAdStatuses(
        context: android.content.Context,
        userId: String
    ): Map<String, BookingAdStatus>? {
        val saved = BookingAdTransitionStore.load(context, userId) ?: return null
        return saved.mapNotNull { (id, name) ->
            val status = runCatching { BookingAdStatus.valueOf(name) }.getOrNull()
                ?: return@mapNotNull null
            id to status
        }.toMap().takeIf { it.isNotEmpty() }
    }

    private fun bookingAdStatus(rawStatus: String?): BookingAdStatus {
        val normalized = rawStatus
            ?.trim()
            ?.lowercase(Locale.ROOT)
            ?.replace(' ', '_')
            .orEmpty()
        return when {
            normalized in ACTIVE_STATUSES ||
                normalized.contains("check_in") || normalized.contains("checkin") -> {
                BookingAdStatus.ACTIVE
            }
            normalized in AD_COMPLETED_STATUSES ||
                normalized.contains("check_out") || normalized.contains("checkout") -> {
                BookingAdStatus.COMPLETED
            }
            normalized in AD_CANCELLED_STATUSES -> BookingAdStatus.CANCELLED
            else -> BookingAdStatus.OTHER
        }
    }

    private fun queueBookingTransitionAd(transition: BookingAdTransition) {
        queueBookingTransitionAd(transition.bookingId, transition.target)
    }

    private fun queueBookingTransitionAd(bookingId: String, target: BookingAdTarget) {
        val candidate = PendingAdTransition(bookingId, target, System.currentTimeMillis())
        if (activeAdTransition?.eventKey == candidate.eventKey ||
            pendingAdTransitions.any { it.eventKey == candidate.eventKey }
        ) {
            return
        }
        pendingAdTransitions.addLast(candidate)
        processPendingBookingTransition()
    }

    private fun hasUnfinishedBookingTransition(): Boolean {
        return activeAdTransition != null || pendingAdTransitions.isNotEmpty()
    }

    /**
     * The transition is held only while the app itself is not in front.
     *
     * It used to be held unless the bookings tab was the visible one, on the reasoning that an
     * interstitial fired from a hidden fragment lands on top of whatever tab the user is really
     * looking at. But the check-in is not something the user did on a tab — an operator can
     * check a car in off its number plate while the user is anywhere in the app — and holding
     * the ad meant the booking silently stayed Booked until they wandered back to the tab.
     * Landing on top of whatever they are looking at IS the behaviour: it is a response to
     * their car being let in, not to a tap.
     *
     * isResumed is the gate that still matters, and it is doing real work: it goes false when
     * MainContainerActivity is paused, so nothing fires over the top-up flow, support chat,
     * booking details, or a backgrounded app.
     *
     * Navigation is completed from AdMobManager's terminal callback. That callback also fires for
     * no-fill, disabled ads, load timeout, and show failure, so monetisation can never strand the
     * booking UI in its previous state.
     */
    private fun processPendingBookingTransition() {
        if (activeAdTransition != null) return

        val pending = pendingAdTransitions.firstOrNull() ?: return
        val isStale = System.currentTimeMillis() - pending.queuedAtMs > PENDING_AD_TRANSITION_MAX_AGE_MS
        val alreadyHandled = com.gridee.parking.utils.AdMobManager.hasShownBookingTransition(
            pending.bookingId,
            pending.target.name
        )
        if (isStale || alreadyHandled) {
            pendingAdTransitions.removeFirst()
            applyBookingTransitionDestination(pending)
            processPendingBookingTransition()
            return
        }

        if (!isResumed || !hasViewBinding()) {
            return
        }
        val host = activity ?: return
        pendingAdTransitions.removeFirst()
        activeAdTransition = pending

        // The operator has already consumed this pass. Remove it before presenting the full-screen
        // ad so the stale QR is never revealed again when the ad closes.
        dismissBookingQrPass(pending.bookingId)

        com.gridee.parking.utils.AdMobManager.showBookingTransitionInterstitial(
            host,
            pending.bookingId,
            pending.target.name
        ) {
            if (activeAdTransition?.eventKey != pending.eventKey) return@showBookingTransitionInterstitial
            activeAdTransition = null
            applyBookingTransitionDestination(pending)
            processPendingBookingTransition()
        }
    }

    /**
     * Where the interstitial hands back to. There is no tab to switch to any more — the rail
     * already shows every stop — so the destination is simply the re-render, scrolled to the
     * booking that moved.
     */
    private fun applyBookingTransitionDestination(transition: PendingAdTransition) {
        if (!hasViewBinding()) return
        renderBookings()
        if (transition.target == BookingAdTarget.ACTIVE) {
            binding.rvBookings.post {
                if (hasViewBinding() && railAdapter.itemCount > 0) {
                    binding.rvBookings.scrollToPosition(0)
                }
            }
        }
    }

    /**
     * The guard on the card's cancel row. It restates what is being cancelled — a confirmation
     * that names nothing is one people learn to tap through — and gives the way out the wider,
     * calmer button of the two.
     */
    private fun showCancelConfirmation(booking: Booking) {
        if (!isAdded) return
        if (booking.id in cancellingBookingIds) {
            showToast("Cancellation is already processing")
            return
        }
        val dialog = BottomSheetDialog(requireContext(), R.style.BottomSheetDialogTheme)
        val sheet = BottomSheetCancelBookingBinding.inflate(layoutInflater)
        dialog.setContentView(sheet.root)
        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))

        sheet.textCancelSpot.text = booking.spotName
        val from = booking.scheduledStartTime.ifBlank { booking.startTime }
        sheet.textCancelWhen.text = listOf(from, booking.endTime)
            .filter { it.isNotBlank() && !it.equals("TBD", ignoreCase = true) }
            .joinToString(" – ") { it.uppercase(Locale.getDefault()) }
        sheet.textCancelAmount.text = booking.amount
        sheet.coinCancelAmount.visibility =
            if (booking.amount.isBlank()) View.GONE else View.VISIBLE
        sheet.textCancelBody.text = getString(R.string.cancel_sheet_body_policy_loading)

        val lotId = userBookings.firstOrNull { it.id == booking.id }
            ?.lotId
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
        if (lotId == null) {
            sheet.textCancelBody.text = getString(R.string.cancel_sheet_body_policy_unavailable)
        } else {
            viewLifecycleOwner.lifecycleScope.launch {
                val response = runCatching { parkingRepository.getBookingPolicy(lotId) }.getOrNull()
                if (!dialog.isShowing || !isAdded) return@launch
                val policy = response?.body()
                    ?.takeIf { response.isSuccessful }
                    ?.let { runCatching { BookingPolicyResolver.resolve(it) }.getOrNull() }
                sheet.textCancelBody.text = when {
                    policy == null -> getString(R.string.cancel_sheet_body_policy_unavailable)
                    !policy.bookingChargeRequired -> getString(R.string.cancel_sheet_body_free_booking)
                    policy.isNoRefund -> getString(R.string.cancel_sheet_body_no_refund)
                    else -> getString(R.string.cancel_sheet_body)
                }
            }
        }

        sheet.buttonKeepBooking.setOnClickListener { dialog.dismiss() }
        sheet.buttonConfirmCancel.setOnClickListener {
            sheet.buttonConfirmCancel.isEnabled = false
            dialog.dismiss()
            cancelBookingFromCard(booking)
        }

        toggleBackgroundBlur(true)
        dialog.setOnDismissListener { toggleBackgroundBlur(false) }
        dialog.show()
    }

    /**
     * Runs once the confirmation sheet says yes.
     */
    private fun cancelBookingFromCard(booking: Booking) {
        if (DebugSampleBookings.isSample(booking.id)) {
            showToast("Debug sample booking")
            return
        }
        if (booking.status != BookingStatus.PENDING) {
            showToast(getString(R.string.active_bookings_cannot_be_cancelled_please))
            return
        }
        if (!cancellingBookingIds.add(booking.id)) return
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val result = bookingRepository.cancelBooking(booking.id)
                if (!isAdded) return@launch
                if (result.isSuccess) {
                    showToast(getString(R.string.booking_cancelled))
                    val cancelled = userBookings.firstOrNull { it.id == booking.id }
                    cancelled?.copy(status = "cancelled")?.let { applyUpdatedBooking(it, render = false) }
                    queueBookingTransitionAd(booking.id, BookingAdTarget.CANCELLED)
                    BookingMutationRefreshCoordinator.refreshAfterSuccess(
                        context = requireContext(),
                        parkingLotId = cancelled?.lotId,
                        bookingId = booking.id,
                        statusHint = "cancelled",
                        walletSource = WalletRefreshSource.BOOKING_CANCEL,
                        cachesAlreadyInvalidated = true,
                    )
                    renderBookings()
                } else {
                    showToast(result.exceptionOrNull()?.message ?: "Failed to cancel booking")
                    renderBookings()
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (isAdded) {
                    showToast(e.message ?: "Failed to cancel booking")
                    renderBookings()
                }
            } finally {
                cancellingBookingIds.remove(booking.id)
            }
        }
    }

    private fun updateActiveBookingNotification(bookings: List<BackendBooking>) {
        val context = context ?: return
        val activeBookings = bookings.filter { mapBackendStatus(it.status) == BookingStatus.ACTIVE }
        if (activeBookings.isEmpty()) {
            lastActiveBookingId?.let { BookingActiveNotificationManager.cancel(context, it) }
            lastActiveBookingId = null
            return
        }

        val now = System.currentTimeMillis()
        val activeWithEnd = activeBookings.mapNotNull { booking ->
            val id = booking.id ?: return@mapNotNull null
            val endTime = booking.checkOutTime?.time ?: return@mapNotNull null
            if (endTime <= now) return@mapNotNull null
            id to endTime
        }.minByOrNull { it.second }

        if (activeWithEnd != null) {
            val (bookingId, endTime) = activeWithEnd
            BookingActiveNotificationManager.showOrUpdate(context, bookingId, endTime)
            if (lastActiveBookingId != null && lastActiveBookingId != bookingId) {
                BookingActiveNotificationManager.cancel(context, lastActiveBookingId!!)
            }
            lastActiveBookingId = bookingId
        } else {
            lastActiveBookingId?.let { BookingActiveNotificationManager.cancel(context, it) }
            lastActiveBookingId = null
        }
    }

    private fun mergeBookings(
        primary: List<BackendBooking>,
        history: List<BackendBooking>
    ): List<BackendBooking> {
        if (primary.isEmpty()) return history
        if (history.isEmpty()) return primary

        val merged = LinkedHashMap<String, BackendBooking>()

        fun bookingKey(booking: BackendBooking): String {
            return booking.id
                ?: "${booking.spotId}:${booking.checkInTime?.time ?: booking.createdAt?.time ?: 0L}"
        }

        primary.forEach { booking ->
            merged[bookingKey(booking)] = booking
        }
        history.forEach { booking ->
            merged[bookingKey(booking)] = booking
        }

        return merged.values.toList()
    }

    private fun mergeBookingSnapshots(
        previous: List<BackendBooking>,
        latest: List<BackendBooking>
    ): List<BackendBooking> {
        val merged = LinkedHashMap<String, BackendBooking>()

        fun bookingKey(booking: BackendBooking): String {
            return booking.id
                ?: "${booking.spotId}:${booking.checkInTime?.time ?: booking.createdAt?.time ?: 0L}"
        }

        previous.forEach { booking -> merged[bookingKey(booking)] = booking }
        latest.forEach { booking -> merged[bookingKey(booking)] = booking }
        return merged.values.toList()
    }

    private fun applyPendingNavigationIfReady() {
        val request = pendingNavigationRequest ?: return
        if (!isAdded || view == null) return

        pendingNavigationRequest = null

        // request.showPending is kept in the contract because MainContainerActivity and the
        // confirmation screen still pass it, but it no longer selects anything: both states
        // are on screen together. The highlight and open ids still do their work.
        pendingHighlightBookingId = request.highlightBookingId
        pendingOpenBookingId = request.openBookingId
    }

    private suspend fun loadParkingDataCache(bookings: List<BackendBooking>) {
        // Booking payloads already contain their lot context. Resolve only lots referenced by
        // this user's bookings; the old global-spots + every-lot loop created an N+1 burst each
        // time the fragment was rebuilt.
        bookings.forEach { booking ->
            booking.lotName
                ?.takeIf { it.isNotBlank() }
                ?.let { parkingLotCache[booking.lotId] = it }
        }

        val missingLotIds = bookings.asSequence()
            .filter { booking ->
                mapBackendStatus(booking.status) == BookingStatus.ACTIVE ||
                    mapBackendStatus(booking.status) == BookingStatus.PENDING
            }
            .map { it.lotId.trim() }
            .filter { it.isNotEmpty() && it !in loadedSpotLotIds }
            .distinct()
            .toList()

        missingLotIds.forEach { lotId ->
            runCatching { parkingRepository.getParkingSpotsByLot(lotId) }
                .onSuccess { response ->
                    if (!response.isSuccessful) return@onSuccess
                    response.body().orEmpty().forEach { spot ->
                        parkingSpotCache[spot.id] = spot.name
                            ?: spot.zoneName
                            ?: spot.spotCode
                            ?: "Spot ${spot.id}"
                    }
                    loadedSpotLotIds += lotId
                }
        }
    }

    private fun convertToBooking(backendBooking: BackendBooking): Booking {
        val parkingLocation = backendBooking.lotName
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
            ?: backendBooking.locationName
                ?.trim()
                ?.takeIf { it.isNotEmpty() }
            ?: parkingLotCache[backendBooking.lotId]
            ?: "Parking lot"

        // Resolve the display name from the lot-scoped cache.
        val spotId = backendBooking.spotId ?: "Unknown"

        val spotName = parkingSpotCache[spotId]
            ?: spotId

        // Format date and time from backend data
        val dateFormat = SimpleDateFormat("dd MMM yyyy", Locale.getDefault())
        val timeFormat = SimpleDateFormat("hh:mm a", Locale.getDefault())

        val status = mapBackendStatus(backendBooking.status)
        val scheduledCheckInTime = backendBooking.checkInTime
            ?: backendBooking.actualCheckInTime
            ?: backendBooking.createdAt
        val checkInTime = backendBooking.actualCheckInTime
            ?: backendBooking.checkInTime
            ?: backendBooking.createdAt
        val checkOutTime = backendBooking.checkOutTime

        val bookingDate = scheduledCheckInTime?.let { dateFormat.format(it) } ?: "TBD"
        val startTime = checkInTime?.let { timeFormat.format(it) } ?: "TBD"
        val endTime = checkOutTime?.let { timeFormat.format(it) } ?: "TBD"

        val duration = when {
            checkInTime == null -> "TBD"
            status == BookingStatus.ACTIVE -> formatDuration(System.currentTimeMillis() - checkInTime.time)
            checkOutTime != null -> formatDuration(checkOutTime.time - checkInTime.time)
            else -> "TBD"
        }

        return Booking(
            id = backendBooking.id ?: "Unknown",
            locationName = parkingLocation,
            spotName = spotName,
            vehicleNumber = backendBooking.vehicleNumber ?: "",
            amount = if (backendBooking.amount != null) String.format("%.2f", backendBooking.amount) else "",
            status = status,
            spotId = backendBooking.spotId ?: "",
            locationAddress = "",
            startTime = startTime,
            endTime = endTime,
            scheduledStartTime = scheduledCheckInTime?.let { timeFormat.format(it) } ?: startTime,
            scannedAtTime = backendBooking.actualCheckInTime?.let { timeFormat.format(it) },
            endedAtTimestamp = (
                backendBooking.cancelledAt
                    ?: backendBooking.actualCheckOutTime
                    ?: backendBooking.checkOutTime
                    ?: backendBooking.updatedAt
                )?.time ?: 0L,
            duration = duration,
            bookingDate = bookingDate,
            checkInTimestamp = checkInTime?.time ?: 0L,
            checkOutTimestamp = checkOutTime?.time ?: 0L
        )
    }

    private fun mapBackendStatus(backendStatus: String?): BookingStatus {
        val normalized = backendStatus?.trim()?.lowercase(Locale.ROOT)?.replace(' ', '_') ?: return BookingStatus.PENDING

        return when {
            normalized.isEmpty() -> BookingStatus.PENDING
            normalized in ACTIVE_STATUSES -> BookingStatus.ACTIVE
            normalized in PENDING_STATUSES -> BookingStatus.PENDING
            normalized in COMPLETED_STATUSES -> BookingStatus.COMPLETED

            // Substring-based fallbacks for unexpected variants
            normalized.contains("check_out") || normalized.contains("checkout") ||
                normalized.contains("complete") || normalized.contains("finish") ||
                normalized.contains("cancel") || normalized.contains("expire") ||
                normalized.contains("no_show") || normalized.contains("noshow") ||
                normalized.contains("auto") -> BookingStatus.COMPLETED

            normalized.contains("check_in") || normalized.contains("checkin") ||
                normalized.contains("in_progress") || normalized.contains("inprogress") ||
                normalized.contains("ongoing") || normalized.contains("running") ||
                normalized.contains("active") -> BookingStatus.ACTIVE

            normalized.contains("pending") || normalized.contains("await") ||
                normalized.contains("reserve") || normalized.contains("schedule") ||
                normalized.contains("confirm") || normalized.contains("book") ||
                normalized.contains("init") || normalized.contains("hold") -> BookingStatus.PENDING

            else -> BookingStatus.PENDING
        }
    }

    companion object {
        private const val ACTIVE_TIMER_REFRESH_MS = 1000L

        /** Pass open, user at the barrier: the one place a delay reads as a broken app. */
        private const val WATCH_AT_BARRIER_MS = 1_500L

        /** Bookings tab in front — a plate check-in has to land here on its own. */
        private const val WATCH_TAB_IN_FRONT_MS = 2_500L

        /**
         * Any other tab. Slow on purpose: a parking session runs for hours, and watching it
         * every couple of seconds for that long costs more than the wait it saves.
         */
        private const val WATCH_OTHER_TAB_MS = 12_000L
        private const val MANUAL_REFRESH_COOLDOWN_MS = 20_000L
        private val PENDING_STATUSES = setOf(
            "pending",
            "created",
            "booked",
            "reserved",
            "scheduled",
            "pending_confirmation",
            "pending-confirmation",
            "pending_payment",
            "pending-payment",
            "awaiting_payment",
            "awaiting-payment",
            "awaiting_checkin",
            "awaiting-checkin",
            "initiated",
            "confirmed"
        )

        private val ACTIVE_STATUSES = setOf(
            "active",
            "in_progress",
            "in-progress",
            "ongoing",
            "ongoing_session",
            "live",
            "checked_in",
            "checked-in"
        )

        private val COMPLETED_STATUSES = setOf(
            "completed",
            "finished",
            "cancelled",
            "canceled",
            "expired",
            "checked_out",
            "checked-out",
            "no_show",
            "no-show",
            "auto_completed",
            "auto-completed"
        )

        /** A transition older than this belongs to a session the user has long since left. */
        private const val PENDING_AD_TRANSITION_MAX_AGE_MS = 10L * 60L * 1000L

        /** Capture with: adb logcat -s BookingAdFlow AdMobManager */
        private const val AD_FLOW_TAG = "BookingAdFlow"

        // Cancellation and no-show are terminal, but are not successful checkouts.
        private val AD_COMPLETED_STATUSES = setOf(
            "completed",
            "finished",
            "expired",
            "checked_out",
            "checked-out",
            "auto_completed",
            "auto-completed"
        )

        // Matched exactly rather than by substring, so a no-show or an expiry the user had no
        // hand in never gets treated as a cancellation they performed.
        private val AD_CANCELLED_STATUSES = setOf(
            "cancelled",
            "canceled",
            "user_cancelled",
            "user-cancelled",
            "cancelled_by_user",
            "booking_cancelled"
        )
    }

    private fun applyUpdatedBooking(updated: BackendBooking, render: Boolean = true) {
        val updatedId = updated.id ?: return
        val index = userBookings.indexOfFirst { it.id == updatedId }
        if (index >= 0) {
            userBookings[index] = updated
        } else {
            userBookings.add(updated)
        }
        updateActiveBookingNotification(userBookings)
        if (render) renderBookings()
    }

    private fun formatDuration(durationMillis: Long): String {
        val safeDuration = durationMillis.coerceAtLeast(0L)
        val hours = safeDuration / (1000 * 60 * 60)
        val minutes = (safeDuration % (1000 * 60 * 60)) / (1000 * 60)
        return if (hours > 0) "${hours}h ${minutes}m" else "${minutes}m"
    }

    private fun toggleBackgroundBlur(show: Boolean) {
        val decorView = requireActivity().window.decorView as? ViewGroup ?: return
        val activityRoot = requireActivity().findViewById<ViewGroup>(android.R.id.content) ?: return
        val contentRoot = activityRoot.getChildAt(0) ?: return

        // Cancel previous animation but preserve current state for continuity
        currentBlurAnimator?.cancel()
        val currentProgress = blurOverlayView?.alpha ?: 0f

        if (show) {
            // 1. Overlay Setup
            if (blurOverlayView == null) {
                blurOverlayView = View(requireContext()).apply {
                    layoutParams = ViewGroup.LayoutParams(MATCH_PARENT, MATCH_PARENT)
                    // Keep an iOS-like frosted backdrop: visible blur with a lighter tint above content.
                    setBackgroundColor(Color.parseColor("#40000000"))
                    alpha = 0f
                    isClickable = true
                    isFocusable = true
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                        elevation = 50f
                    }
                }
                decorView.addView(blurOverlayView)
            }
            blurOverlayView?.bringToFront()

            // 2. Animate In (From current state to 1f)
            val target = 1f
            // Scale duration based on remaining distance to keep velocity consistent
            val remaining = abs(target - currentProgress)
            val duration = (300 * remaining).toLong().coerceAtLeast(100)

            currentBlurAnimator = ValueAnimator.ofFloat(currentProgress, target).apply {
                this.duration = duration
                interpolator = DecelerateInterpolator(1.5f)
                addUpdateListener { anim ->
                    val progress = anim.animatedValue as Float
                    blurOverlayView?.alpha = progress
                    applyBlurEffect(contentRoot, progress)
                }
                start()
            }

        } else {
            // 3. Animate Out (From current state to 0f)
            val target = 0f
            val remaining = abs(target - currentProgress)
            val duration = (250 * remaining).toLong().coerceAtLeast(100)

             currentBlurAnimator = ValueAnimator.ofFloat(currentProgress, target).apply {
                this.duration = duration
                interpolator = DecelerateInterpolator(1.5f)
                addUpdateListener { anim ->
                    val progress = anim.animatedValue as Float
                    blurOverlayView?.alpha = progress
                    applyBlurEffect(contentRoot, progress)
                }
                doOnEnd {
                   if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                       contentRoot.setRenderEffect(null)
                   }
                   blurOverlayView?.let {
                       (it.parent as? ViewGroup)?.removeView(it)
                       blurOverlayView = null
                   }
                }
                start()
            }
        }
    }

    private fun applyBlurEffect(content: View, progress: Float) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val radius = progress * 50f
            // Stronger desaturation (down to 0.4) to make foreground pop
            val saturation = 1f - (progress * 0.6f)

            val safeRadius = radius.coerceAtLeast(0.01f)

            val blur = RenderEffect.createBlurEffect(
                safeRadius, safeRadius, Shader.TileMode.CLAMP
            )
            val colorMatrix = android.graphics.ColorMatrix().apply { setSaturation(saturation) }
            val effect = RenderEffect.createColorFilterEffect(
                android.graphics.ColorMatrixColorFilter(colorMatrix),
                blur
            )
            content.setRenderEffect(effect)
        }
    }

    private fun getUserId(): String? {
        // Primary: legacy prefs set by classic login/registration
        val sharedPref = requireActivity().getSharedPreferences("gridee_prefs", android.content.Context.MODE_PRIVATE)
        val legacyId = sharedPref.getString("user_id", null)
        if (!legacyId.isNullOrBlank()) return legacyId

        // Fallback: JWT-based auth storage
        return try {
            com.gridee.parking.utils.JwtTokenManager(requireContext()).getUserId()
        } catch (_: Exception) {
            null
        }
    }

}
