package com.gridee.parking.ui.fragments

import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.RenderEffect
import android.graphics.Shader
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.RippleDrawable
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.widget.TextView
import android.widget.DatePicker
import android.widget.TimePicker
import android.widget.Button
import android.app.Dialog
import android.view.HapticFeedbackConstants
import android.util.TypedValue
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.ColorInt
import androidx.core.animation.doOnEnd
import androidx.core.content.ContextCompat
import androidx.core.content.res.ResourcesCompat
import androidx.core.view.doOnLayout
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.gridee.parking.R
import com.gridee.parking.data.api.ApiClient
import com.gridee.parking.data.model.Booking as BackendBooking
import com.gridee.parking.data.model.UIBooking
import com.gridee.parking.databinding.FragmentBookingsNewBinding
import com.gridee.parking.ui.adapters.Booking
import com.gridee.parking.ui.adapters.BookingStatus
import com.gridee.parking.ui.adapters.BookingsAdapter
import com.gridee.parking.ui.base.BaseTabFragment
import com.gridee.parking.databinding.BottomSheetBookingFiltersBinding
import com.gridee.parking.databinding.BottomSheetBookingOverviewBinding
import android.view.WindowManager
import android.graphics.Typeface
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.chip.Chip
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import android.content.Intent
import com.gridee.parking.ui.qr.QrScannerActivity
import com.gridee.parking.data.repository.BookingRepository
import com.gridee.parking.data.repository.ParkingRepository
import com.gridee.parking.notifications.BookingActiveNotificationManager
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*
import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.view.animation.DecelerateInterpolator
import android.view.animation.OvershootInterpolator
import com.google.android.material.ripple.RippleUtils
import com.google.android.material.shape.MaterialShapeDrawable
import com.google.android.material.shape.ShapeAppearanceModel
import kotlin.math.abs
import kotlin.math.roundToInt
import androidx.dynamicanimation.animation.SpringAnimation
import androidx.dynamicanimation.animation.SpringForce
import androidx.dynamicanimation.animation.DynamicAnimation

data class Quad<A, B, C, D>(val first: A, val second: B, val third: C, val fourth: D) : java.io.Serializable
class BookingsFragmentNew : BaseTabFragment<FragmentBookingsNewBinding>() {

    private lateinit var bookingsAdapter: BookingsAdapter
    private var userBookings = mutableListOf<BackendBooking>()
    private var currentTab = BookingStatus.ACTIVE
    private var blurOverlayView: View? = null
    private var currentBlurAnimator: ValueAnimator? = null
    private var isSliderDragging = false
    private var sliderDragOffset = 0f
    private val selectedLabelColor by lazy { ContextCompat.getColor(requireContext(), R.color.text_primary) }
    private val unselectedLabelColor by lazy { ContextCompat.getColor(requireContext(), R.color.segment_button_text_unchecked) }
    private val typefaceBold by lazy { ResourcesCompat.getFont(requireContext(), R.font.inter_bold) }
    private val typefaceMedium by lazy { ResourcesCompat.getFont(requireContext(), R.font.inter_medium) }
    private var currentSortOption = BookingSortOption.NEWEST_FIRST
    private var sortBottomSheetDialog: BottomSheetDialog? = null
    private var selectedSpotFilter: String? = null
    private var lastActiveBookingId: String? = null
    
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

    private val bookingRepository by lazy { BookingRepository(requireContext()) }

    private enum class ScanType { CHECK_IN, CHECK_OUT }
    private enum class BookingSortOption {
        NEWEST_FIRST,
        OLDEST_FIRST
    }

    private data class BookingFilterDraft(
        var sortOption: BookingSortOption,
        var startDate: Long?,
        var endDate: Long?,
        var spotFilter: String?
    )
    
    // Date filter fields
    private var filterStartDate: Long? = null
    private var filterEndDate: Long? = null
    
    private var pendingScanBookingId: String? = null
    private var pendingScanType: ScanType? = null

    private val qrScanLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == QrScannerActivity.RESULT_QR_SCANNED) {
            val qrCode = result.data?.getStringExtra(QrScannerActivity.EXTRA_QR_CODE)
            val bookingId = pendingScanBookingId
            val type = pendingScanType
            if (qrCode.isNullOrBlank() || bookingId.isNullOrBlank() || type == null) {
                showToast(getString(R.string.qr_invalid))
                return@registerForActivityResult
            }

            lifecycleScope.launch {
                when (type) {
                    ScanType.CHECK_IN -> handleCheckInFlow(bookingId, qrCode)
                    ScanType.CHECK_OUT -> handleCheckOutFlow(bookingId, qrCode)
                }
            }
        }
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
        setupRecyclerView()
        setupPullToRefresh()
        // setupFilterButton() removed
        setupSegmentedControl()
        loadUserBookings() // Use real API instead of sample data
    }

    private fun setupPullToRefresh() {
        binding.swipeRefresh.setProgressBackgroundColorSchemeResource(R.color.background_secondary)
        binding.swipeRefresh.setColorSchemeResources(R.color.text_primary)
        binding.swipeRefresh.setOnRefreshListener {
            loadUserBookings()
        }
    }

    // Filter button setup removed

    private fun showSortBottomSheet() {
        if (sortBottomSheetDialog?.isShowing == true) return
        val sheetBinding = BottomSheetBookingFiltersBinding.inflate(layoutInflater)

        var draft = BookingFilterDraft(
            sortOption = currentSortOption,
            startDate = filterStartDate,
            endDate = filterEndDate,
            spotFilter = selectedSpotFilter
        )

        fun updateSortSelection(option: BookingSortOption) {
            sheetBinding.radioNewestFirst.isChecked = option == BookingSortOption.NEWEST_FIRST
            sheetBinding.radioOldestFirst.isChecked = option == BookingSortOption.OLDEST_FIRST
        }

        updateSortSelection(draft.sortOption)

        // Setup date filter UI
        setupDateFilter(sheetBinding, draft)

        populateSpotFilterChips(sheetBinding, draft.spotFilter) { selection ->
            draft.spotFilter = selection
        }

        // Use the custom theme to ensure consistency
        val dialog = BottomSheetDialog(requireContext(), R.style.BottomSheetDialogTheme)
        dialog.setContentView(sheetBinding.root)

        // Remove the default white background to show our custom rounded background
        dialog.setOnShowListener { dialogInterface ->
             val bottomSheetDialog = dialogInterface as BottomSheetDialog
             val bottomSheet = bottomSheetDialog.findViewById<View>(com.google.android.material.R.id.design_bottom_sheet)
             bottomSheet?.background = null
             
             // Ensure it expands fully if needed
             bottomSheet?.let { sheet ->
                 val behavior = BottomSheetBehavior.from(sheet)
                 behavior.state = BottomSheetBehavior.STATE_EXPANDED
                 behavior.skipCollapsed = true
             }
        }
        
        // Handle Close Button
        sheetBinding.btnClose.setOnClickListener {
            dialog.dismiss()
        }

        sheetBinding.containerNewest.setOnClickListener {
            it.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
            if (draft.sortOption != BookingSortOption.NEWEST_FIRST) {
                draft.sortOption = BookingSortOption.NEWEST_FIRST
                updateSortSelection(draft.sortOption)
            }
        }
        sheetBinding.containerOldest.setOnClickListener {
            it.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
            if (draft.sortOption != BookingSortOption.OLDEST_FIRST) {
                draft.sortOption = BookingSortOption.OLDEST_FIRST
                updateSortSelection(draft.sortOption)
            }
        }
        
        // Reset All button
        sheetBinding.buttonResetAll.setOnClickListener {
            it.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
            draft = BookingFilterDraft(
                sortOption = BookingSortOption.NEWEST_FIRST,
                startDate = null,
                endDate = null,
                spotFilter = null
            )
            updateSortSelection(draft.sortOption)
            updateDateDisplay(sheetBinding, draft.startDate, draft.endDate)
            populateSpotFilterChips(sheetBinding, draft.spotFilter) { selection ->
                draft.spotFilter = selection
            }
        }
        
        // Apply Filters button
        sheetBinding.buttonApplyFilters.setOnClickListener {
            it.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
            applyFilterDraft(draft)
            dialog.dismiss()
        }

        dialog.setOnDismissListener { sortBottomSheetDialog = null }
        dialog.show()
        sortBottomSheetDialog = dialog
    }

    private fun applyFilterDraft(draft: BookingFilterDraft) {
        currentSortOption = draft.sortOption
        filterStartDate = draft.startDate
        filterEndDate = draft.endDate
        selectedSpotFilter = draft.spotFilter
        showBookingsForStatus(currentTab)
    }

    private fun populateSpotFilterChips(
        sheetBinding: BottomSheetBookingFiltersBinding,
        selectedSpot: String?,
        onSpotSelected: (String?) -> Unit
    ) {
        val chipGroup = sheetBinding.chipGroupSpots
        chipGroup.setOnCheckedStateChangeListener(null)
        chipGroup.removeAllViews()

        var currentSelection = selectedSpot
        val availableSpots = userBookings
            .map { getSpotLabel(it).trim() }
            .filter { it.isNotEmpty() }
            .distinctBy { it.lowercase(Locale.ROOT) }
            .sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it })

        sheetBinding.textNoSpots.visibility = if (availableSpots.isEmpty()) View.VISIBLE else View.GONE

        val normalizedSelection = selectedSpot?.lowercase(Locale.ROOT)
        val allChip = createSpotChip(getString(R.string.spot_filter_all), true).apply {
            id = View.generateViewId()
            tag = ALL_SPOTS_TAG
        }
        chipGroup.addView(allChip)

        availableSpots.forEach { spotName ->
            val chip = createSpotChip(spotName).apply {
                id = View.generateViewId()
                tag = spotName
            }
            chipGroup.addView(chip)
            if (normalizedSelection != null && spotName.lowercase(Locale.ROOT) == normalizedSelection) {
                chipGroup.check(chip.id)
            }
        }

        if (normalizedSelection == null || chipGroup.checkedChipId == View.NO_ID) {
            chipGroup.check(allChip.id)
            if (currentSelection != null) {
                currentSelection = null
                onSpotSelected(null)
            }
        }

        chipGroup.setOnCheckedStateChangeListener { group, checkedIds ->
            val checkedId = checkedIds.firstOrNull() ?: View.NO_ID
            val selectedChip = if (checkedId != View.NO_ID) group.findViewById<Chip>(checkedId) else null
            val tag = selectedChip?.tag as? String
            val newSelection = when (tag) {
                null, ALL_SPOTS_TAG -> null
                else -> tag
            }
            val currentNormalized = currentSelection?.lowercase(Locale.ROOT)
            val newNormalized = newSelection?.lowercase(Locale.ROOT)
            if (currentNormalized != newNormalized) {
                currentSelection = newSelection
                onSpotSelected(newSelection)
            }
        }
    }

    private fun createSpotChip(label: String, isAllChip: Boolean = false): Chip {
        val chip = Chip(requireContext())
        chip.text = label
        chip.isCheckable = true
        chip.isCheckedIconVisible = false
        chip.isClickable = true
        chip.setEnsureMinTouchTargetSize(false)
        chip.textSize = 14f
        chip.typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        chip.chipStrokeWidth = dpToPx(1f)
        chip.chipStrokeColor = ColorStateList.valueOf(ContextCompat.getColor(requireContext(), R.color.segment_shell_border))
        chip.chipCornerRadius = dpToPx(20f)
        chip.rippleColor = ColorStateList.valueOf(ContextCompat.getColor(requireContext(), R.color.filter_button_ripple))
        val checkedBackground = ContextCompat.getColor(requireContext(), R.color.text_primary)
        val uncheckedBackground = ContextCompat.getColor(requireContext(), R.color.segment_shell_surface)
        val backgroundColors = ColorStateList(
            arrayOf(intArrayOf(android.R.attr.state_checked), intArrayOf()),
            intArrayOf(checkedBackground, uncheckedBackground)
        )
        chip.chipBackgroundColor = backgroundColors
        val textColors = ColorStateList(
            arrayOf(intArrayOf(android.R.attr.state_checked), intArrayOf()),
            intArrayOf(ContextCompat.getColor(requireContext(), R.color.white), ContextCompat.getColor(requireContext(), R.color.text_primary))
        )
        chip.setTextColor(textColors)
        chip.chipStartPadding = dpToPx(12f)
        chip.chipEndPadding = dpToPx(12f)
        chip.tag = if (isAllChip) ALL_SPOTS_TAG else label
        return chip
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
        val statuses = listOf(BookingStatus.ACTIVE, BookingStatus.PENDING)
        currentTab = statuses.first()

        bookingsAdapter = BookingsAdapter(
            emptyList(),
            onBookingClick = { booking ->
                // Handle booking click (e.g., show details)
                showBookingDetails(booking)
            },
            onExtendClick = { booking ->
                val backendBooking = userBookings.firstOrNull { it.id == booking.id }
                if (backendBooking != null) {
                    showExtendBookingDialog(backendBooking)
                } else {
                    showToast("Booking details not found")
                }
            }
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

    private fun getSegmentTitle(status: BookingStatus): String {
        return when (status) {
            BookingStatus.ACTIVE -> binding.segmentedControlContainer.textActive.text.toString()
            BookingStatus.PENDING -> binding.segmentedControlContainer.textPending.text.toString()
            else -> "Unknown" // Handle other cases
        }
    }

    private fun updateEmptyStateText(status: BookingStatus) {
        val title: String
        val subtitle: String
        
        when (status) {
            BookingStatus.ACTIVE -> {
                title = "No active bookings"
                subtitle = "When you book a parking spot, you will see your active sessions here."
            }
            BookingStatus.PENDING -> {
                title = "No booked bookings"
                subtitle = "Your booked parking reservations will appear here."
            }
            else -> {
                title = "No bookings found"
                subtitle = "You haven't made any bookings yet."
            }
        }
        
        binding.tvEmptyTitle.text = title
        binding.tvEmptySubtitle.text = subtitle
    }

    private fun filterBookingsByStatus(status: BookingStatus) {
        android.util.Log.d("BookingsFragment", "filterBookingsByStatus: status=$status, total bookings=${userBookings.size}")
        val filteredBookings: List<BackendBooking> = userBookings.filter {
            mapBackendStatus(it.status) == status
        }
        val spotFiltered = applySpotFilter(filteredBookings)
        val sortedBookings = sortBookings(spotFiltered)
        android.util.Log.d("BookingsFragment", "Filtered bookings count: ${filteredBookings.size}")
        
        // Convert to UI bookings before updating adapter
        val uiBookings = sortedBookings.map { convertToBooking(it) }
        updateAdapterWithBookings(uiBookings)

        // Update visibility
        binding.rvBookings.visibility = if (spotFiltered.isEmpty()) View.GONE else View.VISIBLE
        binding.layoutEmptyState.visibility = if (spotFiltered.isEmpty()) View.VISIBLE else View.GONE
        
        if (spotFiltered.isEmpty()) {
            updateEmptyStateText(status)
        }
        android.util.Log.d("BookingsFragment", "RecyclerView visibility: ${binding.rvBookings.visibility}, EmptyState visibility: ${binding.layoutEmptyState.visibility}")
    }

    private fun handleSegmentSelection(newStatus: BookingStatus, userTriggered: Boolean = true) {
        android.util.Log.d("BookingsFragment", "handleSegmentSelection called with status: $newStatus")
        updateSegmentVisualState(newStatus)

        if (newStatus != currentTab) {
            if (userTriggered) {
                val rootView = binding.segmentedControlContainer.segmentContainer
                rootView?.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
            }
            currentTab = newStatus
            showBookingsForStatus(newStatus)
        }
    }

    private fun loadBookingsFromAPI() {
        android.util.Log.d("BookingsFragment", "loadBookingsFromAPI called")
        // Use real API; do not inject sample data
        loadUserBookings()
    }

    private fun onBookingClicked(booking: Booking) {
        showBookingDetails(booking)
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
    }

    override fun onPause() {
        stopActiveTimerUpdates()
        super.onPause()
    }

    override fun onHiddenChanged(hidden: Boolean) {
        super.onHiddenChanged(hidden)
        if (hidden) {
            stopActiveTimerUpdates()
        } else {
            startActiveTimerUpdates()
        }
    }

    private fun startActiveTimerUpdates() {
        activeTimerHandler.removeCallbacks(activeTimerRunnable)
        activeTimerHandler.post(activeTimerRunnable)
    }

    private fun stopActiveTimerUpdates() {
        activeTimerHandler.removeCallbacks(activeTimerRunnable)
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

        container.segmentGroup.doOnLayout {
            positionSliderInstantly(getSegmentView(currentTab), currentTab)
        }

        setupSliderDragGesture()

        updateSegmentVisualState(currentTab)
        applyPendingNavigationIfReady()
    }

    private fun updateSegmentVisualState(selectedStatus: BookingStatus) {
        val container = binding.segmentedControlContainer
        val isActive = selectedStatus == BookingStatus.ACTIVE
        val isPending = selectedStatus == BookingStatus.PENDING
        val isCompleted = selectedStatus == BookingStatus.COMPLETED

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

        animateSegmentSlider(getSegmentView(selectedStatus), selectedStatus)
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
    private fun positionSliderInstantly(targetSegment: View, targetStatus: BookingStatus) {
        val container = binding.segmentedControlContainer
        val slider = container.segmentSlider ?: return
        val root = container.segmentContainer ?: return // FRAME LAYOUT ROOT

        if (targetSegment.width == 0 || !root.isLaidOut) {
            root.doOnLayout {
                if (hasViewBinding() && view != null) {
                    positionSliderInstantly(targetSegment, targetStatus)
                }
            }
            return
        }

        // Match the slider to the segment width; container padding provides the inset.
        val params = slider.layoutParams
        params.width = targetSegment.width
        slider.layoutParams = params

        // Align the slider with the target segment inside the padded track.
        slider.translationX = calculateSliderTargetX(targetSegment)

        slider.visibility = View.VISIBLE
        updateSegmentLabelsForSlider(selectedStatusOverride = targetStatus)
    }

    private fun calculateSliderTargetX(targetSegment: View): Float {
        return targetSegment.left.toFloat()
    }

    private fun animateSegmentSlider(targetSegment: View, targetStatus: BookingStatus) {
        if (isSliderDragging || !hasViewBinding() || view == null) return
        val container = binding.segmentedControlContainer
        val slider = container.segmentSlider ?: return
        val root = container.segmentContainer ?: return

        if (!targetSegment.isLaidOut || !root.isLaidOut || !slider.isLaidOut) {
            root.post {
                if (hasViewBinding() && view != null) {
                    animateSegmentSlider(targetSegment, targetStatus)
                }
            }
            return
        }

        val targetWidth = targetSegment.width
        val targetX = calculateSliderTargetX(targetSegment)

        if (slider.visibility != View.VISIBLE) {
            positionSliderInstantly(targetSegment, targetStatus)
            return
        }

        // Use Spring Animation for "Liquid" feel
        val springAnim = SpringAnimation(slider, DynamicAnimation.TRANSLATION_X, targetX).apply {
            spring = SpringForce(targetX).apply {
                dampingRatio = SpringForce.DAMPING_RATIO_NO_BOUNCY
                stiffness = SpringForce.STIFFNESS_LOW
            }
        }

        // Also animate width if needed
        if (slider.width != targetWidth) {
            val widthAnimator = ValueAnimator.ofInt(slider.width, targetWidth).apply {
                addUpdateListener { animator ->
                    val params = slider.layoutParams
                    params.width = animator.animatedValue as Int
                    slider.layoutParams = params
                }
                duration = 250
            }
            widthAnimator.start()
        }

        springAnim.addUpdateListener { _, _, _ ->
            updateSegmentLabelsForSlider(
                selectedStatusOverride = targetStatus,
                allowPostLayout = false
            )
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

    private fun applySegmentRipple(segment: View) {
        val rippleColor = RippleUtils.convertToRippleDrawableColor(
            ColorStateList.valueOf(ContextCompat.getColor(requireContext(), R.color.segment_ripple_active))
        )
        val cornerRadius = resources.getDimension(R.dimen.segmented_control_height) / 2f
        val shapeAppearance = ShapeAppearanceModel.builder()
            .setAllCornerSizes(cornerRadius)
            .build()
        val mask = MaterialShapeDrawable(shapeAppearance).apply {
            fillColor = ColorStateList.valueOf(Color.WHITE)
        }
        val ripple = RippleDrawable(rippleColor, ColorDrawable(Color.TRANSPARENT), mask)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            segment.foreground = ripple
        } else {
            segment.background = ripple
        }
    }
    
    /**
     * Handle segment selection from tap
     */
    override fun onDestroyView() {
        sortBottomSheetDialog?.dismiss()
        sortBottomSheetDialog = null
        stopActiveTimerUpdates()
        toggleBackgroundBlur(false)
        super.onDestroyView()
    }

    private fun showBookingsForStatus(status: BookingStatus) {
        // Reached from coroutine continuations that resume on Main.immediate during view
        // teardown (config change / process death). At that point the binding is already
        // null, so bail instead of dereferencing it — the list re-renders on the next
        // onViewCreated from the cached userBookings.
        if (!hasViewBinding()) return
        updateSegmentBadges()
        val filteredBookings = userBookings.filter { mapBackendStatus(it.status) == status }
        val spotFiltered = applySpotFilter(filteredBookings)
        val dateFiltered = applyDateFilter(spotFiltered)
        val sortedBookings = sortBookings(dateFiltered)
        
        if (sortedBookings.isEmpty()) {
            // Show empty state
            binding.rvBookings.visibility = View.GONE
            binding.layoutEmptyState.visibility = View.VISIBLE
            
            // Update empty state text based on status
            when (status) {
                BookingStatus.ACTIVE -> {
                    binding.tvEmptyTitle.text = "No Active Bookings"
                    binding.tvEmptySubtitle.text = "Your active parking bookings will appear here"
                }
                BookingStatus.PENDING -> {
                    binding.tvEmptyTitle.text = "No Booked Bookings"
                    binding.tvEmptySubtitle.text = "Your booked parking reservations will appear here"
                }
                else -> {
                    binding.tvEmptyTitle.text = "No Bookings"
                    binding.tvEmptySubtitle.text = "Your bookings will appear here"
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

    private fun updateSegmentBadges() {
        val active = userBookings.count { mapBackendStatus(it.status) == BookingStatus.ACTIVE }
        val pending = userBookings.count { mapBackendStatus(it.status) == BookingStatus.PENDING }

        val container = binding.segmentedControlContainer
        setBadgeState(container.badgeActive, active)
        setBadgeState(container.badgePending, pending)
    }

    private fun sortBookings(bookings: List<BackendBooking>): List<BackendBooking> {
        return when (currentSortOption) {
            BookingSortOption.NEWEST_FIRST -> bookings.sortedByDescending { getComparableTimestamp(it) }
            BookingSortOption.OLDEST_FIRST -> bookings.sortedBy { getComparableTimestamp(it) }
        }
    }

    private fun applySpotFilter(bookings: List<BackendBooking>): List<BackendBooking> {
        val selection = selectedSpotFilter?.takeUnless { it.isBlank() } ?: return bookings
        val normalized = selection.lowercase(Locale.ROOT)
        return bookings.filter {
            val label = getSpotLabel(it).trim().lowercase(Locale.ROOT)
            label == normalized
        }
    }

    private fun applyDateFilter(bookings: List<BackendBooking>): List<BackendBooking> {
        val startDate = filterStartDate
        val endDate = filterEndDate
        
        if (startDate == null && endDate == null) return bookings
        
        return bookings.filter { booking ->
            val bookingTime = booking.checkInTime?.time ?: booking.createdAt?.time ?: return@filter true
            
            when {
                startDate != null && endDate != null -> {
                    bookingTime >= startDate && bookingTime <= endDate + 86400000L // Add 24h to include end date
                }
                startDate != null -> bookingTime >= startDate
                endDate != null -> bookingTime <= endDate + 86400000L
                else -> true
            }
        }
    }

    private fun setupDateFilter(sheetBinding: BottomSheetBookingFiltersBinding, draft: BookingFilterDraft) {
        // Update date display
        updateDateDisplay(sheetBinding, draft.startDate, draft.endDate)
        
        // Start date picker with haptic feedback and scale animation
        sheetBinding.cardStartDate.setOnClickListener { view ->
            view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
            animateCardPress(view)
            showMinimalDatePicker(
                title = "Select Start Date",
                selectedDate = draft.startDate,
                maxDate = draft.endDate
            ) { selectedDate ->
                view.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
                draft.startDate = selectedDate
                updateDateDisplay(sheetBinding, draft.startDate, draft.endDate)
                animateDateSelected(sheetBinding.textStartDate)
            }
        }
        
        // End date picker with haptic feedback and scale animation
        sheetBinding.cardEndDate.setOnClickListener { view ->
            view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
            animateCardPress(view)
            showMinimalDatePicker(
                title = "Select End Date",
                selectedDate = draft.endDate,
                minDate = draft.startDate
            ) { selectedDate ->
                view.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
                draft.endDate = selectedDate
                updateDateDisplay(sheetBinding, draft.startDate, draft.endDate)
                animateDateSelected(sheetBinding.textEndDate)
            }
        }
        
        // Clear date filter with haptic feedback
        sheetBinding.buttonClearDateFilter.setOnClickListener { view ->
            view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
            draft.startDate = null
            draft.endDate = null
            updateDateDisplay(sheetBinding, draft.startDate, draft.endDate)
            animateClearFilter(sheetBinding)
        }
    }

    private fun animateCardPress(view: View) {
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

    private fun animateDateSelected(textView: TextView) {
        textView.alpha = 0f
        textView.animate()
            .alpha(1f)
            .setDuration(300)
            .start()
    }

    private fun animateClearFilter(sheetBinding: BottomSheetBookingFiltersBinding) {
        listOf(sheetBinding.textStartDate, sheetBinding.textEndDate).forEach { textView ->
            textView.animate()
                .alpha(0.5f)
                .setDuration(150)
                .withEndAction {
                    textView.animate()
                        .alpha(1f)
                        .setDuration(150)
                        .start()
                }
                .start()
        }
    }

    private fun updateDateDisplay(
        sheetBinding: BottomSheetBookingFiltersBinding,
        startDate: Long?,
        endDate: Long?
    ) {
        val dateFormat = java.text.SimpleDateFormat("MMM dd, yyyy", Locale.getDefault())
        
        sheetBinding.textStartDate.text = startDate?.let { 
            dateFormat.format(java.util.Date(it)) 
        } ?: "Select Date"
        
        sheetBinding.textEndDate.text = endDate?.let { 
            dateFormat.format(java.util.Date(it)) 
        } ?: "Select Date"
        
        // Show/hide clear button with animation
        val shouldShow = startDate != null || endDate != null
        if (shouldShow && sheetBinding.buttonClearDateFilter.visibility != View.VISIBLE) {
            sheetBinding.buttonClearDateFilter.alpha = 0f
            sheetBinding.buttonClearDateFilter.visibility = View.VISIBLE
            sheetBinding.buttonClearDateFilter.animate()
                .alpha(1f)
                .setDuration(200)
                .start()
        } else if (!shouldShow && sheetBinding.buttonClearDateFilter.visibility == View.VISIBLE) {
            sheetBinding.buttonClearDateFilter.animate()
                .alpha(0f)
                .setDuration(200)
                .withEndAction {
                    sheetBinding.buttonClearDateFilter.visibility = View.GONE
                }
                .start()
        }
    }

    private fun showMinimalDatePicker(
        title: String,
        selectedDate: Long? = null,
        minDate: Long? = null,
        maxDate: Long? = null,
        onDateSelected: (Long) -> Unit
    ) {
        val calendar = java.util.Calendar.getInstance()
        selectedDate?.let { calendar.timeInMillis = it }
        
        // Build constraints
        val constraintsBuilder = com.google.android.material.datepicker.CalendarConstraints.Builder()
        
        minDate?.let { 
            constraintsBuilder.setStart(it)
        }
        maxDate?.let { 
            constraintsBuilder.setEnd(it)
        }
        
        // Create date picker with custom theme, smooth animations, and haptic feedback
        val datePickerDialog = com.google.android.material.datepicker.MaterialDatePicker.Builder.datePicker()
            .setTitleText(title)
            .setSelection(selectedDate ?: calendar.timeInMillis)
            .setTheme(R.style.CustomDatePickerTheme)
            .setCalendarConstraints(constraintsBuilder.build())
            .build()
        
        // Add haptic feedback on positive button (Confirm)
        datePickerDialog.addOnPositiveButtonClickListener { selection ->
            view?.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
            onDateSelected(selection)
        }
        
        // Add subtle haptic feedback on negative button (Cancel)
        datePickerDialog.addOnNegativeButtonClickListener {
            view?.performHapticFeedback(HapticFeedbackConstants.REJECT)
        }
        
        // Add subtle haptic feedback on dismiss
        datePickerDialog.addOnDismissListener {
            view?.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
        }
        
        datePickerDialog.show(parentFragmentManager, "DATE_PICKER")
        
        // Apply smooth backdrop animation and setup date selection haptics
        Handler(Looper.getMainLooper()).postDelayed({
            datePickerDialog.dialog?.window?.apply {
                setDimAmount(0.5f)
                attributes?.windowAnimations = R.style.DatePickerDialogAnimation
            }

            // Add haptic feedback for date cell selections
            setupDatePickerHaptics(datePickerDialog)
        }, 50)
    }
    
    private fun setupDatePickerHaptics(datePickerDialog: com.google.android.material.datepicker.MaterialDatePicker<Long>) {
        try {
            // Find the calendar view and add touch listeners for haptic feedback
            datePickerDialog.dialog?.findViewById<View>(com.google.android.material.R.id.month_grid)?.let { monthGrid ->
                if (monthGrid is ViewGroup) {
                    addHapticToDateCells(monthGrid)
                }
            }
        } catch (e: Exception) {
            android.util.Log.d("BookingsFragment", "Could not add haptics to date cells: ${e.message}")
        }
    }
    
    private fun addHapticToDateCells(viewGroup: ViewGroup) {
        for (i in 0 until viewGroup.childCount) {
            val child = viewGroup.getChildAt(i)
            if (child is ViewGroup) {
                addHapticToDateCells(child)
            } else {
                // Add subtle haptic feedback and scale animation on date cell touch
                child.setOnTouchListener { v, event ->
                    when (event.action) {
                        MotionEvent.ACTION_DOWN -> {
                            // Haptic feedback
                            v.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                            
                            // Subtle scale animation
                            v.animate()
                                .scaleX(1.08f)
                                .scaleY(1.08f)
                                .setDuration(75)
                                .setInterpolator(android.view.animation.DecelerateInterpolator())
                                .start()
                        }
                        MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                            // Scale back to normal
                            v.animate()
                                .scaleX(1.0f)
                                .scaleY(1.0f)
                                .setDuration(75)
                                .setInterpolator(android.view.animation.AccelerateInterpolator())
                                .start()
                        }
                    }
                    false // Don't consume the event
                }
            }
        }
    }

    private fun getComparableTimestamp(booking: BackendBooking): Long {
        return booking.checkInTime?.time
            ?: booking.createdAt?.time
            ?: abs(booking.id?.hashCode() ?: 0).toLong()
    }

    private fun getSpotLabel(booking: BackendBooking): String {
        val spotId = booking.spotId?.takeIf { it.isNotBlank() } ?: return ""
        return parkingSpotCache[spotId] ?: spotId
    }

    private fun ensureSpotFilterIsValid() {
        val currentSelection = selectedSpotFilter?.lowercase(Locale.ROOT) ?: return
        val hasMatch = userBookings.any {
            val label = getSpotLabel(it).trim()
            label.isNotEmpty() && label.lowercase(Locale.ROOT) == currentSelection
        }
        if (!hasMatch) {
            selectedSpotFilter = null
        }
    }

    private fun setBadgeState(badge: TextView, @Suppress("UNUSED_PARAMETER") count: Int) {
        // Hide badge counts per latest design; leave the view gone regardless of data
        badge.visibility = View.GONE
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
        
        // Show bookings count in UI for now
        if (bookings.isNotEmpty()) {
            showToast("Found ${bookings.size} bookings")
        }

        pendingHighlightBookingId?.let { highlightId ->
            val index = bookings.indexOfFirst { it.id == highlightId }
            if (index >= 0) {
                binding.rvBookings.post {
                    if (hasViewBinding() && view != null) {
                        binding.rvBookings.smoothScrollToPosition(index)
                    }
                }
                showToast("Showing your latest booking")
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

    private fun loadUserBookings() {
        if (!hasViewBinding() || view == null) return

        setRefreshing(true)
        
        val userId = getUserId()
        if (userId == null) {
            showToast("Please login to view your bookings")
            userBookings.clear()
            selectedSpotFilter = null
            updateActiveBookingNotification(emptyList())
            setRefreshing(false)
            showBookingsForStatus(currentTab)
            return
        }
        
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                // Load parking lots and spots cache first
                if (!isCacheLoaded) {
                    loadParkingDataCache()
                }
                
                // Get active/pending + history bookings from backend using robust repository parsing.
                val primaryBookings = bookingRepository.getUserBookings().getOrElse { error ->
                    android.util.Log.e("BookingsFragment", "Failed loading current bookings: ${error.message}")
                    emptyList()
                }
                val historyBookings = bookingRepository.getUserBookingHistory().getOrElse { error ->
                    android.util.Log.e("BookingsFragment", "Failed loading booking history: ${error.message}")
                    emptyList()
                }

                val mergedBookings = mergeBookings(primaryBookings, historyBookings)
                userBookings.clear()

                if (mergedBookings.isNotEmpty()) {
                    userBookings.addAll(mergedBookings)
                    ensureSpotFilterIsValid()

                    mergedBookings.forEach { booking ->
                        android.util.Log.d(
                            "BookingsFragment",
                            "Raw booking status from backend: '${booking.status}' mapped to ${mapBackendStatus(booking.status)}"
                        )
                    }
                } else {
                    selectedSpotFilter = null
                }

                updateActiveBookingNotification(mergedBookings)
                showBookingsForStatus(currentTab)
                
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                android.util.Log.e("BookingsFragment", "Error loading bookings", e)
                userBookings.clear()
                selectedSpotFilter = null
                updateActiveBookingNotification(emptyList())
                showBookingsForStatus(currentTab)
            } finally {
                setRefreshing(false)
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

    private fun applyPendingNavigationIfReady() {
        val request = pendingNavigationRequest ?: return
        if (!isAdded || view == null) return

        pendingNavigationRequest = null

        val targetStatus = if (request.showPending) BookingStatus.PENDING else BookingStatus.ACTIVE
        pendingHighlightBookingId = request.highlightBookingId
        pendingOpenBookingId = request.openBookingId

        handleSegmentSelection(targetStatus, userTriggered = false)
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
        private const val ALL_SPOTS_TAG = "__ALL_SPOTS__"
        private const val ACTIVE_TIMER_REFRESH_MS = 1000L
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
            textLocationInitial.text = booking.spotName.firstOrNull()?.uppercaseChar()?.toString() ?: "S"

            textSpotName.text = booking.locationName
            textVehicleNumber.text = booking.vehicleNumber
            textBookingId.text = "ID: #${booking.id}"
            textBookingId.setOnClickListener {
                val clipboard = requireContext().getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                val clip = android.content.ClipData.newPlainText("Booking ID", booking.id)
                clipboard.setPrimaryClip(clip)
                if (android.os.Build.VERSION.SDK_INT <= android.os.Build.VERSION_CODES.S_V2) {
                    showToast("Booking ID copied")
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
            
            // Apply background to the parent container of the status chip (which we will add in XML)
            // But since we are binding to textStatusChip currently, we might need to adjust.
            // For now, let's assume the textStatusChip IS the container or we bind the container.
            // Actually, in the new XML, we'll likely have a container. 
            // Let's rely on the container ID if possible, or apply to textStatusChip if it's the pill.
            // Re-using textStatusChip as the pill for simplicity in migration, 
            // but we need to handle the DOT.
            
            // To be safe with ViewBinding, let's apply the background to textStatusChip 
            // assuming it acts as the pill for now, or we can use `statusContainer` if avail.
            textStatusChip.background = ContextCompat.getDrawable(requireContext(), statusBackgroundRes)
            
            // We removed locationBadgeContainer references

        }

        // Extend booking is not available from the details sheet.
        sheetBinding.btnExtendBooking.visibility = View.GONE
        sheetBinding.btnExtendBooking.setOnClickListener(null)

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
                                        showToast("Booking cancelled")
                                        dialog.dismiss()
                                        loadUserBookings()
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
                    showToast("Active bookings cannot be cancelled. Please check out.")
                }
                BookingStatus.COMPLETED, BookingStatus.CANCELLED, BookingStatus.NO_SHOW -> {
                    showToast("This booking is already finished.")
                }
            }
        }

        // Configure primary QR scan action based on status
        when (booking.status) {
            BookingStatus.PENDING -> {
                sheetBinding.buttonScanQr.visibility = View.GONE
                sheetBinding.buttonScanQr.setOnClickListener(null)
            }
            BookingStatus.ACTIVE -> {
                sheetBinding.buttonScanQr.visibility = View.GONE
                sheetBinding.buttonScanQr.setOnClickListener(null)
            }
            BookingStatus.COMPLETED, BookingStatus.CANCELLED, BookingStatus.NO_SHOW -> {
                sheetBinding.buttonScanQr.visibility = View.GONE
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

    private fun showExtendBookingDialog(booking: BackendBooking) {
        val dialogView = layoutInflater.inflate(R.layout.dialog_extend_booking, null)
        val dialog = MaterialAlertDialogBuilder(requireContext())
            .setView(dialogView)
            .create()

        val tvCurrentEndTime = dialogView.findViewById<TextView>(R.id.tv_current_end_time)
        val datePicker = dialogView.findViewById<DatePicker>(R.id.date_picker)
        val timePicker = dialogView.findViewById<TimePicker>(R.id.time_picker)
        val tvAdditionalCharges = dialogView.findViewById<TextView>(R.id.tv_additional_charges)
        val btnCancel = dialogView.findViewById<Button>(R.id.btn_cancel)
        val btnExtend = dialogView.findViewById<Button>(R.id.btn_extend)

        val currentEndTime = booking.checkOutTime ?: Date()
        val dateFormat = SimpleDateFormat("MMM dd, yyyy hh:mm a", Locale.getDefault())
        tvCurrentEndTime.text = getString(R.string.current_end_time, dateFormat.format(currentEndTime))

        tvAdditionalCharges.text = getString(R.string.additional_charges, "--")
        lifecycleScope.launch {
            val bookingId = booking.id ?: return@launch
            val result = bookingRepository.getPriceBreakup(bookingId)
            result.fold(
                onSuccess = { breakup ->
                    val bookingCharge = (breakup["bookingCharge"] as? Number)?.toDouble()
                        ?: (breakup["subtotal"] as? Number)?.toDouble()
                        ?: (breakup["totalDeducted"] as? Number)?.toDouble()
                    if (bookingCharge != null) {
                        val formatted = String.format(Locale.getDefault(), "%.2f", bookingCharge)
                        tvAdditionalCharges.text = getString(R.string.additional_charges, formatted)
                    } else {
                        tvAdditionalCharges.text = getString(R.string.additional_charges, "--")
                    }
                },
                onFailure = {
                    tvAdditionalCharges.text = getString(R.string.additional_charges, "--")
                }
            )
        }

        val calendar = Calendar.getInstance().apply { time = currentEndTime }
        datePicker.minDate = currentEndTime.time
        datePicker.updateDate(
            calendar.get(Calendar.YEAR),
            calendar.get(Calendar.MONTH),
            calendar.get(Calendar.DAY_OF_MONTH)
        )
        try {
            timePicker.hour = calendar.get(Calendar.HOUR_OF_DAY)
            timePicker.minute = calendar.get(Calendar.MINUTE)
        } catch (_: Throwable) {
            // ignore for older APIs
        }

        // Charges are calculated on the backend; display current charges from the breakup API.

        btnCancel.setOnClickListener { dialog.dismiss() }
        btnExtend.setOnClickListener {
            val newCal = Calendar.getInstance()
            val selHour = try { timePicker.hour } catch (_: Throwable) { calendar.get(Calendar.HOUR_OF_DAY) }
            val selMin = try { timePicker.minute } catch (_: Throwable) { calendar.get(Calendar.MINUTE) }
            newCal.set(datePicker.year, datePicker.month, datePicker.dayOfMonth, selHour, selMin)

            if (newCal.timeInMillis <= currentEndTime.time) {
                showToast(getString(R.string.new_time_must_be_later))
                return@setOnClickListener
            }

            val isoFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX", Locale.getDefault())
            val newCheckOutTime = isoFormat.format(Date(newCal.timeInMillis))

            showExtendConfirmation(booking.id ?: return@setOnClickListener, newCheckOutTime, dialog)
        }

        dialog.show()
    }

    private fun showExtendConfirmation(bookingId: String, newCheckOutTime: String, extendDialog: Dialog) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(getString(R.string.confirm_extend_title))
            .setMessage(getString(R.string.confirm_extend_message))
            .setPositiveButton(getString(R.string.extend_booking)) { _, _ ->
                lifecycleScope.launch {
                    extendBooking(bookingId, newCheckOutTime)
                    extendDialog.dismiss()
                }
            }
            .setNegativeButton(getString(R.string.cancel), null)
            .show()
    }

    private suspend fun extendBooking(bookingId: String, newCheckOutTime: String) {
        showLoading(true)
        val result = bookingRepository.extendBooking(bookingId, newCheckOutTime)
        result.fold(
            onSuccess = { updated ->
                showLoading(false)
                if (updated.id != null) {
                    applyUpdatedBooking(updated)
                }
                showToast(getString(R.string.booking_extended_success))
                refreshCurrentTab()
            },
            onFailure = { error ->
                showLoading(false)
                val message = error.message ?: getString(R.string.extend_failed_default)
                when {
                    message.contains("Insufficient wallet balance", ignoreCase = true) -> {
                        showToast(getString(R.string.insufficient_wallet_balance))
                    }
                    message.contains("not available", ignoreCase = true) -> {
                        showToast(getString(R.string.spot_not_available_extended))
                    }
                    else -> showToast(message)
                }
            }
        )
    }

    private fun applyUpdatedBooking(updated: BackendBooking) {
        val updatedId = updated.id ?: return
        val index = userBookings.indexOfFirst { it.id == updatedId }
        if (index >= 0) {
            userBookings[index] = updated
        } else {
            userBookings.add(updated)
        }
        ensureSpotFilterIsValid()
        updateActiveBookingNotification(userBookings)
        showBookingsForStatus(currentTab)
    }

    private fun showLoading(show: Boolean) {
        setRefreshing(show)
    }

    private fun refreshCurrentTab() {
        loadUserBookings()
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

    private suspend fun handleCheckInFlow(bookingId: String, qrCode: String) {
        try {
            showToast(getString(R.string.validating_qr))
            val validation = bookingRepository.validateCheckInQr(bookingId, qrCode)
            if (validation.isFailure) {
                showToast(validation.exceptionOrNull()?.message ?: getString(R.string.qr_invalid))
                return
            }
            val result = validation.getOrNull()
            if (result != null) {
                // If penalty applies, prompt user; simplified proceed
                if (result.penalty > 0) {
                    MaterialAlertDialogBuilder(requireContext())
                        .setTitle("Penalty on Check-In")
                        .setMessage(result.message)
                        .setPositiveButton("Proceed") { _, _ ->
                            lifecycleScope.launch { finalizeCheckIn(bookingId, qrCode) }
                        }
                        .setNegativeButton("Cancel", null)
                        .show()
                } else {
                    finalizeCheckIn(bookingId, qrCode)
                }
            }
        } catch (e: Exception) {
            showToast(e.message ?: "Check-in error")
        }
    }

    private suspend fun finalizeCheckIn(bookingId: String, qrCode: String) {
        showToast(getString(R.string.processing_check_in))
        val checkInRes = bookingRepository.checkIn(bookingId, qrCode)
        if (checkInRes.isSuccess) {
            showToast("Checked in successfully")
            loadUserBookings()
        } else {
            showToast(checkInRes.exceptionOrNull()?.message ?: "Check-in failed")
        }
    }

    private suspend fun handleCheckOutFlow(bookingId: String, qrCode: String) {
        try {
            showToast(getString(R.string.validating_qr))
            val validation = bookingRepository.validateCheckOutQr(bookingId, qrCode)
            if (validation.isFailure) {
                showToast(validation.exceptionOrNull()?.message ?: getString(R.string.qr_invalid))
                return
            }
            val result = validation.getOrNull()
            if (result != null) {
                MaterialAlertDialogBuilder(requireContext())
                    .setTitle("Check-Out Charges")
                    .setMessage(result.message)
                    .setPositiveButton("Pay & Check-Out") { _, _ ->
                        lifecycleScope.launch { finalizeCheckOut(bookingId, qrCode) }
                    }
                    .setNegativeButton("Cancel", null)
                    .show()
            }
        } catch (e: Exception) {
            showToast(e.message ?: "Check-out error")
        }
    }

    private suspend fun finalizeCheckOut(bookingId: String, qrCode: String) {
        showToast(getString(R.string.processing_check_out))
        val checkOutRes = bookingRepository.checkOut(bookingId, qrCode)
        if (checkOutRes.isSuccess) {
            showToast("Checked out successfully")
            loadUserBookings()
        } else {
            showToast(checkOutRes.exceptionOrNull()?.message ?: "Check-out failed")
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

    private fun announceForAccessibility(message: String) {
        binding.root.announceForAccessibility(message)
    }
}
