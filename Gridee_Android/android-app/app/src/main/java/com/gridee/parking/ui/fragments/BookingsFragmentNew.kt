package com.gridee.parking.ui.fragments

import android.graphics.Color
import android.graphics.RenderEffect
import android.graphics.Shader
import android.graphics.drawable.ColorDrawable
import android.os.Build
import android.os.Handler
import android.os.Looper
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
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.gridee.parking.R
import com.gridee.parking.data.api.ApiClient
import com.gridee.parking.data.model.Booking as BackendBooking
import com.gridee.parking.databinding.FragmentBookingsNewBinding
import com.gridee.parking.ui.adapters.Booking
import com.gridee.parking.ui.adapters.BookingStatus
import com.gridee.parking.ui.adapters.BookingsAdapter
import com.gridee.parking.ui.base.BaseTabFragment
import com.gridee.parking.databinding.BottomSheetBookingOverviewBinding
import android.view.WindowManager
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.gridee.parking.data.repository.BookingRepository
import com.gridee.parking.data.repository.ParkingRepository
import com.gridee.parking.notifications.BookingActiveNotificationManager
import com.gridee.parking.notifications.BookingStatusEvents
import com.gridee.parking.ui.bookings.BookingAdStatus
import com.gridee.parking.ui.bookings.BookingAdTarget
import com.gridee.parking.ui.bookings.BookingAdTransition
import com.gridee.parking.ui.bookings.BookingAdTransitionDetector
import com.gridee.parking.ui.bottomsheet.BookingQrPassBottomSheet
import com.gridee.parking.utils.BookingAdTransitionStore
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

data class Quad<A, B, C, D>(val first: A, val second: B, val third: C, val fourth: D) : java.io.Serializable
class BookingsFragmentNew : BaseTabFragment<FragmentBookingsNewBinding>() {

    private lateinit var bookingsAdapter: BookingsAdapter
    private var userBookings = mutableListOf<BackendBooking>()
    private var currentTab = BookingStatus.ACTIVE

    /**
     * Set once the tab is somebody's deliberate choice — a tap, a drag, or a caller navigating
     * here with a destination in mind. While it is set, [autoSelectTabWithBookings] keeps its
     * hands off, so a user who taps an empty tab to check it is not bounced straight back out.
     * It is cleared each time the screen returns to the foreground.
     */
    private var tabPinnedByUser = false
    private var blurOverlayView: View? = null
    private var currentBlurAnimator: ValueAnimator? = null
    private var isSliderDragging = false
    private var sliderDragOffset = 0f

    /**
     * Slider work has to be deferred until the segmented control is laid out, which in practice is
     * after the destination tab is already known. Every deferred hop re-reads [currentTab] rather
     * than carrying a captured status, and a stale hop is dropped by comparing the generation it
     * was scheduled under — otherwise a callback queued for the default tab can settle last and
     * park the pill on a tab the list is not showing.
     */
    private var sliderGeneration = 0
    private var sliderSpring: SpringAnimation? = null
    private var sliderWidthAnimator: ValueAnimator? = null
    private var lastActiveBookingId: String? = null
    private var bookingsLoadJob: Job? = null
    private var refreshAfterCurrentLoad = false
    private var observedAdStatuses: Map<String, BookingAdStatus>? = null
    private val pendingAdTransitions = ArrayDeque<PendingAdTransition>()
    private var activeAdTransition: PendingAdTransition? = null
    private var visibleQrBookingId: String? = null
    private var bookingQrPassSheet: BookingQrPassBottomSheet? = null
    private var qrRefreshGraceUntilMs = 0L

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
    private var isCacheLoaded = false
    private data class NavigationRequest(
        val showPending: Boolean,
        val highlightBookingId: String?,
        val openBookingId: String?
    )
    private var pendingNavigationRequest: NavigationRequest? = null
    private var pendingHighlightBookingId: String? = null
    private var pendingOpenBookingId: String? = null
    private val activeTimerHandler = Handler(Looper.getMainLooper())
    private val activeTimerRunnable = object : Runnable {
        override fun run() {
            if (!hasViewBinding() || view == null) return
            if (::bookingsAdapter.isInitialized && (currentTab == BookingStatus.ACTIVE || currentTab == BookingStatus.PENDING)) {
                val itemCount = bookingsAdapter.itemCount
                if (itemCount > 0) {
                    bookingsAdapter.notifyItemRangeChanged(
                        0,
                        itemCount,
                        BookingsAdapter.PAYLOAD_TIMER_UPDATE
                    )
                }
            }
            if (hasViewBinding() && view != null) {
                activeTimerHandler.postDelayed(this, ACTIVE_TIMER_REFRESH_MS)
            }
        }
    }
    private val bookingStatusRefreshHandler = Handler(Looper.getMainLooper())
    private val bookingStatusRefreshRunnable = object : Runnable {
        override fun run() {
            if (!shouldAutoRefreshBookingStatus()) return
            requestBookingStatusRefresh()
            scheduleBookingStatusRefresh()
        }
    }

    private val bookingRepository by lazy { BookingRepository(requireContext()) }

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
        setupPullToRefresh()
        setupSegmentedControl()
        observeBookingStatusEvents()
        loadUserBookings() // Use real API instead of sample data
    }

    private fun setupPullToRefresh() {
        binding.swipeRefresh.setProgressBackgroundColorSchemeResource(R.color.background_secondary)
        binding.swipeRefresh.setColorSchemeResources(R.color.text_primary)
        binding.swipeRefresh.setOnRefreshListener {
            loadUserBookings()
        }
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
        bookingsAdapter = BookingsAdapter(
            emptyList(),
            onBookingClick = { booking ->
                // Handle booking click (e.g., show details)
                showBookingDetails(booking)
            },
            onQrDialogVisibilityChanged = { bookingId, visible ->
                handleBookingQrVisibilityChanged(bookingId, visible)
            },
            onQrPassClick = { booking -> showBookingQrPass(booking) }
        )
        binding.rvBookings.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = bookingsAdapter

            addOnScrollListener(object : androidx.recyclerview.widget.RecyclerView.OnScrollListener() {
                override fun onScrolled(recyclerView: androidx.recyclerview.widget.RecyclerView, dx: Int, dy: Int) {
                    super.onScrolled(recyclerView, dx, dy)
                    syncStickyHeaderState()
                }
            })
        }
    }

    private fun handleSegmentSelection(newStatus: BookingStatus, userTriggered: Boolean = true) {
        android.util.Log.d("BookingsFragment", "handleSegmentSelection called with status: $newStatus")
        val changed = newStatus != currentTab
        if (userTriggered) {
            // Their choice now, not the screen's. Even landing on an empty tab is a choice.
            tabPinnedByUser = true
        }
        if (changed && userTriggered) {
            val rootView = binding.segmentedControlContainer.segmentContainer
            rootView?.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
        }

        // The tab is committed before any visual work: the deferred slider hops and the label
        // emphasis both resolve their target from currentTab, so updating visuals first would
        // let them read the tab being navigated away from.
        currentTab = newStatus
        updateSegmentVisualState()

        if (changed) {
            showBookingsForStatus(newStatus)
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
        // Coming back is a fresh look at the screen, so the landing tab is up for decision
        // again. It can still only move off a tab with nothing on it.
        tabPinnedByUser = false
        startActiveTimerUpdates()
        // Also catches a transition performed by an operator while this app was backgrounded.
        // The load guard below leaves the initial request untouched.
        loadUserBookings()
        // A transition detected while this tab was hidden waits here for the tab to come forward.
        processPendingBookingTransition()
        scheduleBookingStatusRefresh()
    }

    override fun onPause() {
        stopActiveTimerUpdates()
        stopBookingStatusRefresh()
        super.onPause()
    }

    override fun onHiddenChanged(hidden: Boolean) {
        super.onHiddenChanged(hidden)
        if (hidden) {
            stopActiveTimerUpdates()
            stopBookingStatusRefresh()
        } else {
            // The bottom nav shows and hides this fragment rather than recreating it, so this
            // is the "screen opened" moment onResume does not get to see.
            tabPinnedByUser = false
            startActiveTimerUpdates()
            processPendingBookingTransition()
            scheduleBookingStatusRefresh(immediate = true)
            autoSelectTabWithBookings()
        }
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
                if (!isResumed || isHidden) return@collect
                android.util.Log.d(
                    AD_FLOW_TAG,
                    "push refresh requested for ${event.bookingId ?: "unknown booking"} (${event.statusHint})"
                )
                requestBookingStatusRefresh()
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
        if (visible) {
            qrRefreshGraceUntilMs = 0L
            requestBookingStatusRefresh()
        } else if (wasVisibleBooking) {
            // Operators normally scan just before the user closes the pass. Keep the fast cadence
            // through the backend's commit window instead of dropping immediately to normal polling.
            qrRefreshGraceUntilMs = System.currentTimeMillis() + QR_DISMISS_REFRESH_GRACE_MS
        }
        scheduleBookingStatusRefresh()
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

        childFragmentManager.setFragmentResultListener(
            BookingQrPassBottomSheet.RESULT_KEY_HOUSE_PROMO,
            viewLifecycleOwner
        ) { _, _ -> showPartnerReferral() }

        bookingQrPassSheet = childFragmentManager.findFragmentByTag(
            BookingQrPassBottomSheet.TAG
        ) as? BookingQrPassBottomSheet
        bookingQrPassSheet?.bookingId?.takeIf { it.isNotBlank() }?.let { bookingId ->
            handleBookingQrVisibilityChanged(bookingId, true)
        }
    }

    private fun showBookingQrPass(booking: com.gridee.parking.ui.adapters.Booking) {
        val bookingId = booking.id.trim()
        if (bookingId.isBlank() || childFragmentManager.isStateSaved) return
        val current = bookingQrPassSheet
        if (current?.isAdded == true) return

        val sheet = BookingQrPassBottomSheet.newInstance(booking)
        bookingQrPassSheet = sheet
        handleBookingQrVisibilityChanged(bookingId, true)
        runCatching { sheet.show(childFragmentManager, BookingQrPassBottomSheet.TAG) }
            .onFailure {
                bookingQrPassSheet = null
                handleBookingQrVisibilityChanged(bookingId, false)
                android.util.Log.e(AD_FLOW_TAG, "Unable to open booking QR pass", it)
            }
    }

    /**
     * The pass's house-promo rung asks for the referral sheet. It is shown on the activity's
     * manager, exactly as MainContainerActivity does, so it is not nested inside the pass.
     */
    private fun showPartnerReferral() {
        val host = activity ?: return
        val manager = host.supportFragmentManager
        if (manager.isStateSaved) return
        if (manager.findFragmentByTag(
                com.gridee.parking.ui.bottomsheet.PartnerReferralBottomSheet.TAG
            ) != null
        ) {
            return
        }
        com.gridee.parking.ui.bottomsheet.PartnerReferralBottomSheet().show(
            manager,
            com.gridee.parking.ui.bottomsheet.PartnerReferralBottomSheet.TAG
        )
    }

    private fun dismissBookingQrPass(bookingId: String) {
        bookingQrPassSheet
            ?.takeIf { it.bookingId == bookingId }
            ?.dismissAllowingStateLoss()
    }

    private fun requestBookingStatusRefresh() {
        if (bookingsLoadJob?.isActive == true) {
            refreshAfterCurrentLoad = true
            return
        }
        loadUserBookings(showRefreshIndicator = false)
    }

    private fun shouldAutoRefreshBookingStatus(): Boolean {
        if (!hasViewBinding() || view == null || !isResumed || isHidden) return false
        return userBookings.any {
            val status = mapBackendStatus(it.status)
            status == BookingStatus.PENDING || status == BookingStatus.ACTIVE
        }
    }

    private fun scheduleBookingStatusRefresh(immediate: Boolean = false) {
        bookingStatusRefreshHandler.removeCallbacks(bookingStatusRefreshRunnable)
        if (!shouldAutoRefreshBookingStatus()) return
        val delay = when {
            immediate -> 0L
            visibleQrBookingId != null || System.currentTimeMillis() < qrRefreshGraceUntilMs -> {
                QR_VISIBLE_REFRESH_INTERVAL_MS
            }
            else -> BOOKING_STATUS_REFRESH_INTERVAL_MS
        }
        bookingStatusRefreshHandler.postDelayed(bookingStatusRefreshRunnable, delay)
    }

    private fun stopBookingStatusRefresh() {
        bookingStatusRefreshHandler.removeCallbacks(bookingStatusRefreshRunnable)
    }

    private fun setupSegmentedControl() {
        val container = binding.segmentedControlContainer
        val segments = listOf(
            container.segmentActive to BookingStatus.ACTIVE,
            container.segmentPending to BookingStatus.PENDING
        )

        segments.forEach { (segmentView, status) ->
            segmentView.setOnClickListener {
                handleSegmentSelection(status)
            }
        }

        setupSliderDragGesture()

        // The destination tab is settled before any pill work is scheduled. A caller-supplied
        // destination (Done -> Booked) arrives before this control has been laid out, so
        // scheduling for the default tab first would leave a stale hop racing this one.
        applyPendingNavigationIfReady()

        updateSegmentVisualState()
        container.segmentGroup.doOnLayout { positionSliderInstantly() }
    }

    private fun updateSegmentVisualState() {
        val container = binding.segmentedControlContainer
        val selectedStatus = currentTab
        val isActive = selectedStatus == BookingStatus.ACTIVE
        val isPending = selectedStatus == BookingStatus.PENDING

        // Animate the previously selected segment out
        val segments = listOf(
            container.segmentActive to isActive,
            container.segmentPending to isPending
        )

        segments.forEach { (segment, shouldBeSelected) ->
            if (segment.isSelected != shouldBeSelected) {
                // Animate selection change
                if (shouldBeSelected) {
                    // Animate in
                    animateSegmentIn(segment)
                } else {
                    // Animate out
                    animateSegmentOut(segment)
                }
                segment.isSelected = shouldBeSelected
            }
        }

        sliderGeneration++
        animateSegmentSlider()
    }

    private fun getSegmentView(status: BookingStatus): View {
        val container = binding.segmentedControlContainer
        return when (status) {
            BookingStatus.ACTIVE -> container.segmentActive
            BookingStatus.PENDING -> container.segmentPending
            else -> container.segmentActive
        }
    }

    // Position the slider within the track padding.
    private fun positionSliderInstantly(generation: Int = sliderGeneration) {
        if (generation != sliderGeneration) return
        if (!hasViewBinding() || view == null) return
        val container = binding.segmentedControlContainer
        val slider = container.segmentSlider ?: return
        val root = container.segmentContainer ?: return // FRAME LAYOUT ROOT
        val targetStatus = currentTab
        val targetSegment = getSegmentView(targetStatus)

        if (targetSegment.width == 0 || !root.isLaidOut) {
            // doOnNextLayout, not doOnLayout: the latter runs inline when the root is already
            // laid out, which recurses without end while a segment still measures to zero.
            root.doOnNextLayout { positionSliderInstantly(generation) }
            return
        }

        cancelSliderAnimations()

        // Match the slider to the segment width; container padding provides the inset.
        val params = slider.layoutParams
        params.width = targetSegment.width
        slider.layoutParams = params

        // Align the slider with the target segment inside the padded track.
        slider.translationX = calculateSliderTargetX(targetSegment)

        slider.visibility = View.VISIBLE
        updateSegmentLabelsForSlider(selectedStatusOverride = targetStatus)
    }

    private fun cancelSliderAnimations() {
        sliderSpring?.cancel()
        sliderSpring = null
        sliderWidthAnimator?.cancel()
        sliderWidthAnimator = null
    }

    private fun calculateSliderTargetX(targetSegment: View): Float {
        return targetSegment.left.toFloat()
    }

    private fun animateSegmentSlider(generation: Int = sliderGeneration) {
        if (generation != sliderGeneration) return
        if (isSliderDragging || !hasViewBinding() || view == null) return
        val container = binding.segmentedControlContainer
        val slider = container.segmentSlider ?: return
        val root = container.segmentContainer ?: return
        val targetStatus = currentTab
        val targetSegment = getSegmentView(targetStatus)

        if (!targetSegment.isLaidOut || !root.isLaidOut || !slider.isLaidOut) {
            root.doOnNextLayout { animateSegmentSlider(generation) }
            return
        }

        val targetWidth = targetSegment.width
        val targetX = calculateSliderTargetX(targetSegment)

        if (slider.visibility != View.VISIBLE) {
            positionSliderInstantly(generation)
            return
        }

        // A spring from an earlier selection can still be running; two live springs would both
        // drive translationX and the pill would settle wherever the slower one finished.
        cancelSliderAnimations()

        // Use Spring Animation for "Liquid" feel
        val springAnim = SpringAnimation(slider, DynamicAnimation.TRANSLATION_X, targetX).apply {
            spring = SpringForce(targetX).apply {
                dampingRatio = SpringForce.DAMPING_RATIO_NO_BOUNCY
                stiffness = SpringForce.STIFFNESS_LOW
            }
        }
        sliderSpring = springAnim

        // Also animate width if needed
        if (slider.width != targetWidth) {
            sliderWidthAnimator = ValueAnimator.ofInt(slider.width, targetWidth).apply {
                addUpdateListener { animator ->
                    val params = slider.layoutParams
                    params.width = animator.animatedValue as Int
                    slider.layoutParams = params
                }
                duration = 250
                start()
            }
        }

        springAnim.addUpdateListener { _, _, _ ->
            updateSegmentLabelsForSlider(
                selectedStatusOverride = targetStatus,
                allowPostLayout = false
            )
        }
        springAnim.addEndListener { _, _, _, _ ->
            if (sliderSpring === springAnim) sliderSpring = null
            // Emphasis is interpolated from the pill's pixel distance while it travels, so a
            // spring that is cancelled part-way would leave the wrong label bold. Settle the
            // labels on the committed tab once motion is over.
            if (generation == sliderGeneration && hasViewBinding() && view != null) {
                updateSegmentLabelsForSlider(selectedStatusOverride = currentTab)
            }
        }

        springAnim.start()
    }

    private fun updateSegmentLabelsForSlider(
        sliderCenterOverride: Float? = null,
        selectedStatusOverride: BookingStatus? = null,
        allowPostLayout: Boolean = true
    ) {
        val container = binding.segmentedControlContainer
        val slider = container.segmentSlider ?: return
        val root = container.segmentContainer ?: return

        val needsLayout = !root.isLaidOut ||
            slider.width == 0 ||
            container.segmentActive.width == 0 ||
            container.segmentPending.width == 0

        if (needsLayout) {
            if (allowPostLayout) {
                root.doOnLayout {
                    updateSegmentLabelsForSlider(
                        sliderCenterOverride,
                        selectedStatusOverride,
                        false
                    )
                }
            }
            return
        }

        val sliderCenter = sliderCenterOverride ?: (slider.translationX + slider.width / 2f)
        val selection = selectedStatusOverride ?: currentTab

        val segments = listOf(
            Quad(BookingStatus.ACTIVE, container.segmentActive, container.textActive, "textActiveBold"),
            Quad(BookingStatus.PENDING, container.segmentPending, container.textPending, "textPendingBold")
        )

        // Remove unused color defs if strict, but kept for safety

        segments.forEach { segmentTriple ->
            val status = segmentTriple.first
            val segment = segmentTriple.second
            val label = segmentTriple.third
            val width = segment.width
            if (width == 0) return@forEach

            val segmentLeft = segment.left.toFloat()
            val segmentCenter = segmentLeft + width / 2f

            val boldTag = segmentTriple.fourth
            val boldLabel = if (segment.tag is View) segment.tag as TextView else segment.findViewWithTag<TextView>(boldTag)?.also { segment.tag = it }

            val distance = abs(sliderCenter - segmentCenter)
            val influenceRadius = (width * 0.9f).coerceAtLeast(1f)
            val emphasis = (1f - (distance / influenceRadius)).coerceIn(0f, 1f)

            // Cross-fade Alpha
            // Medium Label (Unselected) fades OUT as emphasis increases
            label.alpha = 1f - emphasis

            // Bold Label (Selected) fades IN as emphasis increases
            boldLabel?.alpha = emphasis

            // Ensure visibility (optimization)
            if (label.alpha > 0) label.visibility = View.VISIBLE else label.visibility = View.INVISIBLE
            if ((boldLabel?.alpha ?: 0f) > 0) boldLabel?.visibility = View.VISIBLE else boldLabel?.visibility = View.INVISIBLE

            label.isSelected = status == selection
        }
    }

    private fun animateSegmentIn(segment: View) {
        val scaleX = ObjectAnimator.ofFloat(segment, "scaleX", 0.98f, 1.0f)
        val scaleY = ObjectAnimator.ofFloat(segment, "scaleY", 0.98f, 1.0f)
        val alpha = ObjectAnimator.ofFloat(segment, "alpha", 0.7f, 1.0f)

        val animatorSet = AnimatorSet()
        animatorSet.playTogether(scaleX, scaleY, alpha)
        animatorSet.duration = 200
        animatorSet.interpolator = DecelerateInterpolator()
        animatorSet.start()
    }

    private fun animateSegmentOut(segment: View) {
        val scaleX = ObjectAnimator.ofFloat(segment, "scaleX", 1.0f, 0.98f)
        val scaleY = ObjectAnimator.ofFloat(segment, "scaleY", 1.0f, 0.98f)
        val alpha = ObjectAnimator.ofFloat(segment, "alpha", 1.0f, 0.85f)

        val animatorSet = AnimatorSet()
        animatorSet.playTogether(scaleX, scaleY, alpha)
        animatorSet.duration = 200
        animatorSet.interpolator = DecelerateInterpolator()
        animatorSet.start()
    }

    private fun setupSliderDragGesture() {
        val container = binding.segmentedControlContainer
        container.segmentContainer.setOnTouchListener { view, event ->
            val slider = container.segmentSlider ?: return@setOnTouchListener false
            if (!slider.isShown || slider.width == 0) {
                return@setOnTouchListener false
            }
            val localX = event.x - view.paddingLeft
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    val sliderStart = slider.translationX
                    val sliderEnd = sliderStart + slider.width
                    val withinSlider = localX in sliderStart..sliderEnd
                    if (withinSlider) {
                        isSliderDragging = true
                        sliderDragOffset = localX - sliderStart
                        view.parent?.requestDisallowInterceptTouchEvent(true)
                        true
                    } else {
                        isSliderDragging = false
                        false
                    }
                }
                MotionEvent.ACTION_MOVE -> {
                    if (!isSliderDragging) return@setOnTouchListener false
                    val desiredTranslation = localX - sliderDragOffset
                    slider.translationX = clampSliderTranslation(desiredTranslation, view, slider)
                    updateSegmentLabelsForSlider()
                    true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    if (!isSliderDragging) return@setOnTouchListener false
                    isSliderDragging = false
                    view.parent?.requestDisallowInterceptTouchEvent(false)
                    val status = determineNearestStatus(slider)
                    handleSegmentSelection(status)
                    true
                }
                else -> false
            }
        }
    }

    private fun clampSliderTranslation(desiredTranslation: Float, root: View, slider: View): Float {
        val min = 0f
        val max = (root.width - root.paddingLeft - root.paddingRight - slider.width).toFloat()
        return desiredTranslation.coerceIn(min, max.coerceAtLeast(min))
    }

    private fun determineNearestStatus(slider: View): BookingStatus {
        val sliderCenter = slider.translationX + slider.width / 2f
        val container = binding.segmentedControlContainer
        val centers = listOf(
            BookingStatus.ACTIVE to container.segmentActive,
            BookingStatus.PENDING to container.segmentPending
        )
        return centers.minByOrNull { (_, view) ->
             // View left is relative to the segment group; sliderCenter is in the same local coordinates.
             abs(sliderCenter - (view.left + view.width / 2f))
        }?.first ?: currentTab
    }

    override fun onDestroyView() {
        if (::bookingsAdapter.isInitialized) {
            bookingsAdapter.dismissVisibleBookingQrDialog()
        }
        bookingQrPassSheet = null
        visibleQrBookingId = null
        stopActiveTimerUpdates()
        stopBookingStatusRefresh()
        cancelSliderAnimations()
        toggleBackgroundBlur(false)
        super.onDestroyView()
    }

    private fun showBookingsForStatus(status: BookingStatus) {
        // Reached from coroutine continuations that resume on Main.immediate during view
        // teardown (config change / process death). At that point the binding is already
        // null, so bail instead of dereferencing it — the list re-renders on the next
        // onViewCreated from the cached userBookings.
        if (!hasViewBinding()) return
        val sortedBookings = sortBookings(userBookings.filter { mapBackendStatus(it.status) == status })

        if (sortedBookings.isEmpty()) {
            // Show empty state
            binding.rvBookings.visibility = View.GONE
            binding.layoutEmptyState.visibility = View.VISIBLE

            // Update empty state text based on status
            when (status) {
                BookingStatus.ACTIVE -> {
                    binding.tvEmptyTitle.text = getString(R.string.no_active_bookings_2)
                    binding.tvEmptySubtitle.text = getString(R.string.your_active_parking_bookings_will_appear)
                }
                BookingStatus.PENDING -> {
                    binding.tvEmptyTitle.text = getString(R.string.no_booked_bookings_2)
                    binding.tvEmptySubtitle.text = getString(R.string.your_booked_parking_reservations_will_appear)
                }
                else -> {
                    binding.tvEmptyTitle.text = getString(R.string.no_bookings)
                    binding.tvEmptySubtitle.text = getString(R.string.your_bookings_will_appear_here)
                }
            }
        } else {
            // Show bookings list
            binding.rvBookings.visibility = View.VISIBLE
            binding.layoutEmptyState.visibility = View.GONE

            // Convert to UI bookings before updating adapter
            val uiBookings = sortedBookings.map { convertToBooking(it) }
            updateAdapterWithBookings(uiBookings)
        }
    }

    /**
     * Opens the screen on a tab that actually has something on it.
     *
     * The control was hard-wired to Active, so a user whose only booking was still Booked
     * landed on an empty list and had to work out for themselves that the other tab held it.
     *
     * The rule is deliberately one-directional: this only ever moves *off* an empty tab, so it
     * can never pull the screen away from a list somebody is reading, and it stands down
     * completely once [tabPinnedByUser] says the tab was chosen rather than defaulted to.
     * Active wins when both have bookings — a session already running is the more urgent of
     * the two.
     *
     * Returns true when the tab moved, so the caller can skip the render it was about to do
     * for the tab that is no longer showing.
     */
    private fun autoSelectTabWithBookings(): Boolean {
        if (tabPinnedByUser || !hasViewBinding()) return false
        if (visibleBookingCount(currentTab) > 0) return false

        val target = when {
            visibleBookingCount(BookingStatus.ACTIVE) > 0 -> BookingStatus.ACTIVE
            visibleBookingCount(BookingStatus.PENDING) > 0 -> BookingStatus.PENDING
            else -> return false
        }
        if (target == currentTab) return false

        android.util.Log.d("BookingsFragment", "auto-selecting $target: $currentTab has nothing to show")
        handleSegmentSelection(target, userTriggered = false)
        return true
    }

    /** What [showBookingsForStatus] would put on screen for [status], counted rather than built. */
    private fun visibleBookingCount(status: BookingStatus): Int {
        return userBookings.count { mapBackendStatus(it.status) == status }
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

    /**
     * Sync the sticky-header fog and segmented-control elevation to the
     * recyclerview's current scroll offset. Driven by [onScrolled] during
     * scrolls, and called manually after data updates / tab switches /
     * refreshes — those reset the list to top without firing [onScrolled],
     * so the fog would otherwise stay opaque over an already-at-top list.
     */
    private fun syncStickyHeaderState() {
        if (view == null) return
        val offset = binding.rvBookings.computeVerticalScrollOffset()
        val fogAlpha = (offset / dpToPx(60f)).coerceIn(0f, 1f)
        binding.viewFogOverlay.alpha = fogAlpha
        val elevation = dpToPx(4f) * (offset / dpToPx(40f)).coerceIn(0f, 1f)
        binding.segmentedControlContainer.segmentContainer?.elevation = elevation
    }

    private fun updateAdapterWithBookings(bookings: List<Booking>) {
        android.util.Log.d("BookingsFragment", "updateAdapterWithBookings called with ${bookings.size} bookings")
        bookingsAdapter.updateBookings(bookings)
        binding.rvBookings.post {
            if (hasViewBinding() && view != null) {
                syncStickyHeaderState()
            }
        }

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
                        showBookingDetails(booking)
                    }
                }
                pendingOpenBookingId = null
            }
        }
    }

    private fun loadUserBookings(showRefreshIndicator: Boolean = true) {
        if (!hasViewBinding() || view == null) return
        if (bookingsLoadJob?.isActive == true) {
            if (!showRefreshIndicator) refreshAfterCurrentLoad = true
            return
        }

        if (showRefreshIndicator) setRefreshing(true)

        val userId = getUserId()
        if (userId == null) {
            showToast(getString(R.string.please_login_to_view_your_bookings))
            userBookings.clear()
            updateActiveBookingNotification(emptyList())
            if (showRefreshIndicator) setRefreshing(false)
            showBookingsForStatus(currentTab)
            return
        }

        bookingsLoadJob = viewLifecycleOwner.lifecycleScope.launch {
            try {
                // Load parking lots and spots cache first
                if (!isCacheLoaded) {
                    loadParkingDataCache()
                }

                // Get active/pending + history bookings from backend using robust repository parsing.
                val primaryResult = bookingRepository.getUserBookings()
                val historyResult = bookingRepository.getUserBookingHistory()
                primaryResult.exceptionOrNull()?.let { error ->
                    android.util.Log.e("BookingsFragment", "Failed loading current bookings: ${error.message}")
                }
                historyResult.exceptionOrNull()?.let { error ->
                    android.util.Log.e("BookingsFragment", "Failed loading booking history: ${error.message}")
                }
                if (primaryResult.isFailure && historyResult.isFailure) {
                    throw primaryResult.exceptionOrNull()
                        ?: historyResult.exceptionOrNull()
                        ?: IllegalStateException("Booking refresh failed")
                }

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
                val detectedTransitions = detectBookingAdTransitions(mergedBookings)
                userBookings.clear()

                if (mergedBookings.isNotEmpty()) {
                    userBookings.addAll(mergedBookings)

                    mergedBookings.forEach { booking ->
                        android.util.Log.d(
                            "BookingsFragment",
                            "Raw booking status from backend: '${booking.status}' mapped to ${mapBackendStatus(booking.status)}"
                        )
                    }
                }

                updateActiveBookingNotification(mergedBookings)
                detectedTransitions.forEach(::queueBookingTransitionAd)
                if (!hasUnfinishedBookingTransition()) {
                    // This is the first point at which the tabs are known to be empty or not,
                    // so it is where the landing tab gets decided. The hop renders the list
                    // itself, hence the else.
                    if (!autoSelectTabWithBookings()) {
                        showBookingsForStatus(currentTab)
                    }
                }

            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                android.util.Log.e("BookingsFragment", "Error loading bookings", e)
                // Keep the last verified state during a transient garage/network failure. Clearing
                // it would hide the QR and stop the bounded retry loop at exactly the wrong time.
                showBookingsForStatus(currentTab)
            } finally {
                if (showRefreshIndicator) setRefreshing(false)
                bookingsLoadJob = null
                processPendingBookingTransition()

                val shouldRefreshAgain = refreshAfterCurrentLoad
                refreshAfterCurrentLoad = false
                if (shouldRefreshAgain && hasViewBinding() && view != null) {
                    binding.root.post { loadUserBookings(showRefreshIndicator = false) }
                } else {
                    scheduleBookingStatusRefresh()
                }
            }
        }
    }

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
            android.util.Log.d(AD_FLOW_TAG, "no bookings in this fetch; baseline left untouched")
            return emptyList()
        }

        val previous = observedAdStatuses ?: loadPersistedAdStatuses(context, userId)
        // Bookings absent from this fetch keep their last known status for the same reason.
        val merged = if (previous == null) current else previous + current
        observedAdStatuses = merged
        BookingAdTransitionStore.save(context, userId, merged.mapValues { it.value.name })
        android.util.Log.d(
            AD_FLOW_TAG,
            "statuses now=$current previous=${previous ?: "<none: first snapshot, no ad this pass>"}"
        )
        if (previous == null) return emptyList()

        return BookingAdTransitionDetector.detect(previous, current).also { transitions ->
            transitions.forEach { transition ->
                android.util.Log.d(
                    AD_FLOW_TAG,
                    "transition detected: ${transition.bookingId} -> ${transition.target.name}"
                )
            }
        }
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
        android.util.Log.d(AD_FLOW_TAG, "queued ${candidate.eventKey}")
        pendingAdTransitions.addLast(candidate)
        processPendingBookingTransition()
    }

    private fun hasUnfinishedBookingTransition(): Boolean {
        return activeAdTransition != null || pendingAdTransitions.isNotEmpty()
    }

    /**
     * The transition is held until this tab is actually in front. MainContainerActivity keeps the
     * fragment added and refreshing while it is hidden, and an interstitial fired from there would
     * land on top of whatever tab the user is really looking at.
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

        if (!isResumed || isHidden || !hasViewBinding()) {
            android.util.Log.d(
                AD_FLOW_TAG,
                "holding ${pending.eventKey}: tab not in front (resumed=$isResumed hidden=$isHidden)"
            )
            return
        }
        val host = activity ?: return
        pendingAdTransitions.removeFirst()
        activeAdTransition = pending

        // The operator has already consumed this pass. Remove it before presenting the full-screen
        // ad so the stale QR is never revealed again when the ad closes.
        if (::bookingsAdapter.isInitialized) {
            bookingsAdapter.dismissBookingQrDialog(pending.bookingId)
        }
        dismissBookingQrPass(pending.bookingId)

        android.util.Log.d(AD_FLOW_TAG, "handing ${pending.eventKey} to AdMobManager")
        com.gridee.parking.utils.AdMobManager.showBookingTransitionInterstitial(
            host,
            pending.bookingId,
            pending.target.name
        ) { outcome ->
            if (activeAdTransition?.eventKey != pending.eventKey) return@showBookingTransitionInterstitial
            android.util.Log.d(AD_FLOW_TAG, "${pending.eventKey} completed with $outcome")
            activeAdTransition = null
            applyBookingTransitionDestination(pending)
            processPendingBookingTransition()
        }
    }

    private fun applyBookingTransitionDestination(transition: PendingAdTransition) {
        qrRefreshGraceUntilMs = 0L
        when (transition.target) {
            BookingAdTarget.ACTIVE -> {
                currentTab = BookingStatus.ACTIVE
                // The booking just started; this destination is the point of the transition.
                tabPinnedByUser = true
                if (hasViewBinding()) {
                    updateSegmentVisualState()
                    showBookingsForStatus(BookingStatus.ACTIVE)
                    binding.rvBookings.post {
                        if (hasViewBinding() && bookingsAdapter.itemCount > 0) {
                            binding.rvBookings.scrollToPosition(0)
                        }
                    }
                }
            }
            BookingAdTarget.COMPLETED,
            BookingAdTarget.CANCELLED -> {
                if (hasViewBinding()) showBookingsForStatus(currentTab)
            }
        }
        scheduleBookingStatusRefresh()
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

        val targetStatus = if (request.showPending) BookingStatus.PENDING else BookingStatus.ACTIVE
        pendingHighlightBookingId = request.highlightBookingId
        pendingOpenBookingId = request.openBookingId

        handleSegmentSelection(targetStatus, userTriggered = false)
        // A caller that named a destination outranks the landing-tab guess, even if the tab it
        // asked for turns out to be empty — it usually knows about a booking not fetched yet.
        tabPinnedByUser = true
    }

    private suspend fun loadParkingDataCache() {
        try {
            // ✅ Load ALL parking spots first (works now after JsonNull fix!)
            try {
                val allSpotsResponse = ApiClient.apiService.getParkingSpots()
                if (allSpotsResponse.isSuccessful) {
                    allSpotsResponse.body()?.forEach { spot ->
                        val spotName = spot.name ?: spot.zoneName ?: "Spot ${spot.id}"
                        parkingSpotCache[spot.id] = spotName
                        android.util.Log.d("BookingsFragment", "Cached spot: ${spot.id} -> $spotName")
                    }
                    android.util.Log.d("BookingsFragment", "Loaded ${parkingSpotCache.size} spots from /api/parking-spots")
                }
            } catch (e: Exception) {
                android.util.Log.e("BookingsFragment", "Error loading all parking spots: ${e.message}")
            }

            // Load parking lots
            val lotsResponse = ApiClient.apiService.getParkingLots()
            if (lotsResponse.isSuccessful) {
                lotsResponse.body()?.forEach { lot ->
                    parkingLotCache[lot.id] = lot.name
                    android.util.Log.d("BookingsFragment", "Cached lot: ${lot.id} -> ${lot.name}")

                    // Also try to load spots for this lot (as fallback)
                    try {
                        val spotsForLot = ParkingRepository().getParkingSpotsByLot(lot.id)
                        if (spotsForLot.isSuccessful) {
                            spotsForLot.body()?.forEach { spot ->
                                // Only cache if not already cached from /api/parking-spots
                                if (!parkingSpotCache.containsKey(spot.id)) {
                                    val spotName = spot.name ?: spot.zoneName ?: "Spot ${spot.id}"
                                    parkingSpotCache[spot.id] = spotName
                                    android.util.Log.d("BookingsFragment", "Cached spot from lot: ${spot.id} -> ${spotName}")
                                }
                            }
                        }
                    } catch (e: Exception) {
                        android.util.Log.e("BookingsFragment", "Error loading spots for lot ${lot.id}: ${e.message}")
                    }
                }
            }

            // Avoid admin-only all-spots endpoint; rely on by-lot cache above

            isCacheLoaded = true
            android.util.Log.d("BookingsFragment", "Cache loaded: ${parkingLotCache.size} lots, ${parkingSpotCache.size} spots")
        } catch (e: Exception) {
            android.util.Log.e("BookingsFragment", "Error loading parking data cache: ${e.message}")
            // Continue without cache - will use fallback names
        }
    }

    private fun convertToBooking(backendBooking: BackendBooking): Booking {
        val parkingLocation = "SRM University Parking Lot"

        // Get spot name from cache with debug logging
        val spotId = backendBooking.spotId ?: "Unknown"
        android.util.Log.d("BookingsFragment", "Looking up spot: $spotId in cache (${parkingSpotCache.size} entries)")

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

            else -> {
                android.util.Log.w("BookingsFragment", "Unknown booking status '$backendStatus', defaulting to Pending")
                BookingStatus.PENDING
            }
        }
    }

    companion object {
        private const val ACTIVE_TIMER_REFRESH_MS = 1000L
        private const val QR_VISIBLE_REFRESH_INTERVAL_MS = 2_000L
        private const val QR_DISMISS_REFRESH_GRACE_MS = 15_000L
        private const val BOOKING_STATUS_REFRESH_INTERVAL_MS = 10_000L
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

    private fun showBookingDetails(booking: Booking) {
        val dialog = BottomSheetDialog(requireContext(), R.style.BottomSheetDialogTheme)
        val sheetBinding = BottomSheetBookingOverviewBinding.inflate(layoutInflater)
        dialog.setContentView(sheetBinding.root)
        dialog.window?.let { window ->
            androidx.core.view.WindowCompat.setDecorFitsSystemWindows(window, false)
            window.navigationBarColor = android.graphics.Color.TRANSPARENT
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                window.isNavigationBarContrastEnforced = false
            }
            window.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            when {
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
                    window.clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
                    window.addFlags(WindowManager.LayoutParams.FLAG_BLUR_BEHIND)
                    window.setBackgroundBlurRadius(80)
                    val attrs = window.attributes
                    attrs.blurBehindRadius = 80
                    window.attributes = attrs
                    window.setDimAmount(0f)
                }
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.R -> {
                    window.clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
                    window.addFlags(WindowManager.LayoutParams.FLAG_BLUR_BEHIND)
                    window.setDimAmount(0f)
                }
                else -> {
                    window.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
                    window.setDimAmount(0.25f)
                }
            }
        }

        toggleBackgroundBlur(true)

        sheetBinding.apply {
            textLocationName.text = booking.spotName

            textSpotName.text = booking.locationName
            textVehicleNumber.text = booking.vehicleNumber
            textBookingId.text = "ID: #${booking.id}"
            textBookingId.setOnClickListener {
                val clipboard = requireContext().getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                val clip = android.content.ClipData.newPlainText("Booking ID", booking.id)
                clipboard.setPrimaryClip(clip)
                if (android.os.Build.VERSION.SDK_INT <= android.os.Build.VERSION_CODES.S_V2) {
                    showToast(getString(R.string.booking_id_copied))
                }
            }

            textCheckIn.text = getStringFormattedTime(booking.bookingDate, booking.startTime).split(" · ").lastOrNull() ?: booking.startTime
            textCheckOut.text = getStringFormattedTime(booking.bookingDate, booking.endTime).split(" · ").lastOrNull() ?: booking.endTime
            textBookingDate.text = booking.bookingDate

            textDuration.text = if (booking.status == BookingStatus.ACTIVE && booking.checkInTimestamp > 0L) {
                formatDuration(System.currentTimeMillis() - booking.checkInTimestamp)
            } else {
                booking.duration
            }
            textAmount.text = booking.amount

            // Use Soft Status Styling to match Cards e.g. status_soft_active
            val (statusLabel, statusBackgroundRes, statusTextColor, statusDotVisible) = when (booking.status) {
                BookingStatus.ACTIVE -> Quad("Active", R.drawable.status_soft_active, androidx.core.content.ContextCompat.getColor(requireContext(), R.color.status_text_active), true)
                BookingStatus.PENDING -> Quad("Booked", R.drawable.status_soft_pending, androidx.core.content.ContextCompat.getColor(requireContext(), R.color.status_text_pending), false)
                BookingStatus.COMPLETED -> Quad("Completed", R.drawable.status_soft_completed, androidx.core.content.ContextCompat.getColor(requireContext(), R.color.status_text_completed), false)
                BookingStatus.CANCELLED -> Quad("Cancelled", R.drawable.status_soft_cancelled, androidx.core.content.ContextCompat.getColor(requireContext(), R.color.status_text_cancelled), false)
                BookingStatus.NO_SHOW -> Quad("No Show", R.drawable.status_soft_noshow, androidx.core.content.ContextCompat.getColor(requireContext(), R.color.status_text_noshow), false)
            }

            textStatusChip.text = statusLabel
            textStatusChip.setTextColor(statusTextColor)
            textStatusChip.background = ContextCompat.getDrawable(requireContext(), statusBackgroundRes)
        }

        val showCancel = booking.status == BookingStatus.PENDING
        sheetBinding.actionCancel.visibility = if (showCancel) View.VISIBLE else View.GONE


        sheetBinding.buttonCloseSheet.setOnClickListener { dialog.dismiss() }
        sheetBinding.actionCancel.setOnClickListener {
            when (booking.status) {
                BookingStatus.PENDING -> {
                    // Confirm cancellation
                    MaterialAlertDialogBuilder(requireContext())
                        .setTitle("Cancel Booking")
                        .setMessage("Are you sure you want to cancel this booking? Any holds or promotions may be released.")
                        .setNegativeButton("No", null)
                        .setPositiveButton("Yes, Cancel") { _, _ ->
                            // Disable action to prevent double taps
                            sheetBinding.actionCancel.isEnabled = false
                            sheetBinding.actionCancel.alpha = 0.6f

                            lifecycleScope.launch {
                                try {
                                    val result = bookingRepository.cancelBooking(booking.id)
                                    if (result.isSuccess) {
                                        showToast(getString(R.string.booking_cancelled))
                                        dialog.dismiss()
                                        userBookings
                                            .firstOrNull { it.id == booking.id }
                                            ?.copy(status = "cancelled")
                                            ?.let { applyUpdatedBooking(it, render = false) }
                                        queueBookingTransitionAd(booking.id, BookingAdTarget.CANCELLED)
                                        loadUserBookings(showRefreshIndicator = false)
                                    } else {
                                        showToast(result.exceptionOrNull()?.message ?: "Failed to cancel booking")
                                        sheetBinding.actionCancel.isEnabled = true
                                        sheetBinding.actionCancel.alpha = 1f
                                    }
                                } catch (e: Exception) {
                                    showToast(e.message ?: "Failed to cancel booking")
                                    sheetBinding.actionCancel.isEnabled = true
                                    sheetBinding.actionCancel.alpha = 1f
                                }
                            }
                        }
                        .show()
                }
                BookingStatus.ACTIVE -> {
                    showToast(getString(R.string.active_bookings_cannot_be_cancelled_please))
                }
                BookingStatus.COMPLETED, BookingStatus.CANCELLED, BookingStatus.NO_SHOW -> {
                    showToast(getString(R.string.this_booking_is_already_finished))
                }
            }
        }

        dialog.setOnShowListener {
            val bottomSheet = dialog.findViewById<View>(com.google.android.material.R.id.design_bottom_sheet)
            bottomSheet?.let {
                it.setBackgroundColor(Color.TRANSPARENT)
                val behavior = BottomSheetBehavior.from(it)
                behavior.state = BottomSheetBehavior.STATE_EXPANDED
                behavior.skipCollapsed = true
                behavior.isFitToContents = true
            }
        }
        dialog.behavior.isDraggable = true
        dialog.show()

        dialog.setOnDismissListener {
            toggleBackgroundBlur(false)
        }
        dialog.setOnCancelListener {
            toggleBackgroundBlur(false)
        }
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
        if (render) showBookingsForStatus(currentTab)
    }

    private fun setRefreshing(show: Boolean) {
        val currentBinding = bindingOrNull ?: return

        if (show) {
            if (!currentBinding.swipeRefresh.isRefreshing) {
                currentBinding.swipeRefresh.post {
                    bindingOrNull?.let { activeBinding ->
                        if (view != null) {
                            activeBinding.swipeRefresh.isRefreshing = true
                        }
                    }
                }
            }
        } else {
            currentBinding.swipeRefresh.isRefreshing = false
        }
    }

    private fun formatDuration(durationMillis: Long): String {
        val safeDuration = durationMillis.coerceAtLeast(0L)
        val hours = safeDuration / (1000 * 60 * 60)
        val minutes = (safeDuration % (1000 * 60 * 60)) / (1000 * 60)
        return if (hours > 0) "${hours}h ${minutes}m" else "${minutes}m"
    }

    private fun getStringFormattedTime(date: String, time: String): String {
        return if (date == "TBD" || time == "TBD") {
            "TBD"
        } else {
            "$date · $time"
        }
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
