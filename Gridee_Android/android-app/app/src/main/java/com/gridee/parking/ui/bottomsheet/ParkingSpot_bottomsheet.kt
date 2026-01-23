package com.gridee.parking.ui.bottomsheet

import android.app.DatePickerDialog
import android.app.Dialog
import android.app.TimePickerDialog
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.dynamicanimation.animation.DynamicAnimation
import androidx.dynamicanimation.animation.SpringAnimation
import androidx.dynamicanimation.animation.SpringForce
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.gridee.parking.R
import com.gridee.parking.data.api.ApiClient
import com.gridee.parking.data.model.ParkingSpot
import com.gridee.parking.utils.AuthSession
import com.gridee.parking.databinding.BottomSheetParkingSpotBinding
import com.gridee.parking.databinding.BottomSheetTopUpBinding
import com.gridee.parking.ui.booking.BookingConfirmationActivity
import com.gridee.parking.ui.booking.BookingViewModel
import com.gridee.parking.ui.booking.ParkingSpotSelectionAdapter
import com.gridee.parking.ui.wallet.WalletTopUpActivity
import kotlinx.coroutines.launch
import retrofit2.Response
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

class ParkingSpotBottomSheet : BottomSheetDialogFragment() {

    private var _binding: BottomSheetParkingSpotBinding? = null
    private val binding get() = _binding!!

    private lateinit var viewModel: BookingViewModel

    private var parkingSpot: ParkingSpot? = null
    private var selectedLotId: String = ""
    private var selectedLotName: String = ""
    private var selectedSpotId: String = ""

    private var bottomSheetBehavior: BottomSheetBehavior<*>? = null
    private val bottomSheetCallback = object : BottomSheetBehavior.BottomSheetCallback() {
        override fun onStateChanged(bottomSheet: View, newState: Int) {
            if (newState == BottomSheetBehavior.STATE_EXPANDED ||
                newState == BottomSheetBehavior.STATE_HIDDEN
            ) {
                bottomSheet.performHapticFeedback(android.view.HapticFeedbackConstants.CONTEXT_CLICK)
            }

            if (newState == BottomSheetBehavior.STATE_COLLAPSED ||
                newState == BottomSheetBehavior.STATE_EXPANDED
            ) {
                animateHandle(32)
            }
        }

        override fun onSlide(bottomSheet: View, slideOffset: Float) {
            animateHandle(48)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setStyle(STYLE_NORMAL, R.style.BottomSheetDialogTheme)

        arguments?.let { args ->
            selectedSpotId = args.getString(ARG_PARKING_SPOT_ID).orEmpty()
            selectedLotId = args.getString(ARG_PARKING_LOT_ID).orEmpty()
            selectedLotName = args.getString(ARG_PARKING_LOT_NAME).orEmpty()
        }
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val dialog = super.onCreateDialog(savedInstanceState)
        dialog.setOnShowListener { dialogInterface ->
            val bottomSheetDialog = dialogInterface as BottomSheetDialog

            val bottomSheet =
                bottomSheetDialog.findViewById<View>(com.google.android.material.R.id.design_bottom_sheet)
            bottomSheet?.let { sheet ->
                sheet.background = null
                sheet.fitsSystemWindows = false

                val params = sheet.layoutParams as? ViewGroup.MarginLayoutParams
                params?.setMargins(0, 0, 0, 0)
                sheet.layoutParams = params

                ViewCompat.setOnApplyWindowInsetsListener(sheet) { view, insets ->
                    view.setPadding(0, 0, 0, 0)
                    insets
                }
            }

            bottomSheetDialog.behavior.isGestureInsetBottomIgnored = true

            bottomSheetDialog.window?.let { window ->
                WindowCompat.setDecorFitsSystemWindows(window, false)
                val navBarColor =
                    ContextCompat.getColor(requireContext(), R.color.background_primary)
                window.navigationBarColor = navBarColor
                window.isNavigationBarContrastEnforced = false

                val wic = WindowCompat.getInsetsController(window, window.decorView)
                wic.isAppearanceLightNavigationBars = true

                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
                    window.navigationBarDividerColor = android.graphics.Color.TRANSPARENT
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                        window.isNavigationBarContrastEnforced = false
                    }
                }

                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                    window.attributes.blurBehindRadius = 50
                    window.attributes = window.attributes
                }
            }
        }
        return dialog
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = BottomSheetParkingSpotBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        viewModel = ViewModelProvider(
            this,
            ViewModelProvider.AndroidViewModelFactory.getInstance(requireActivity().application)
        )[BookingViewModel::class.java]

        setupInsets()
        setupBehaviors()
        setupUI()
        setupClickListeners()
        setupObservers()

        loadParkingSpot(selectedSpotId)
    }

    override fun onStart() {
        super.onStart()
        (dialog as? BottomSheetDialog)?.let { bottomSheetDialog ->
            val bottomSheet =
                bottomSheetDialog.findViewById<View>(com.google.android.material.R.id.design_bottom_sheet)
            bottomSheet?.layoutParams = bottomSheet?.layoutParams?.apply {
                height = ViewGroup.LayoutParams.MATCH_PARENT
            }
            bottomSheet?.requestLayout()
            bottomSheetDialog.behavior.state = BottomSheetBehavior.STATE_EXPANDED
            bottomSheetDialog.behavior.skipCollapsed = true
        }
    }

    override fun onResume() {
        super.onResume()
        viewModel.loadUserVehicles()
        viewModel.loadWalletBalance()
    }

    private fun setupInsets() {
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(view.paddingLeft, view.paddingTop, view.paddingRight, bars.bottom)
            insets
        }
    }

    private fun setupBehaviors() {
        bottomSheetBehavior = (dialog as? BottomSheetDialog)?.behavior
        bottomSheetBehavior?.addBottomSheetCallback(bottomSheetCallback)
    }

    private fun animateHandle(targetWidthDp: Int) {
        val binding = _binding ?: return
        val targetWidthPx = (targetWidthDp * resources.displayMetrics.density).toInt()
        if (binding.dragHandle.layoutParams.width != targetWidthPx) {
            val params = binding.dragHandle.layoutParams
            params.width = targetWidthPx
            binding.dragHandle.layoutParams = params
        }
    }

    private fun setupUI() {


        val now = Calendar.getInstance()
        val start = now.clone() as Calendar

        start.set(Calendar.SECOND, 0)
        start.set(Calendar.MILLISECOND, 0)

        if (now.get(Calendar.HOUR_OF_DAY) >= BOOKING_CUTOFF_HOUR) {
            start.add(Calendar.DAY_OF_MONTH, 1)
            start.set(Calendar.HOUR_OF_DAY, BOOKING_OPEN_HOUR)
            start.set(Calendar.MINUTE, 0)
            start.set(Calendar.SECOND, 0)
            start.set(Calendar.MILLISECOND, 0)
        } else if (now.get(Calendar.HOUR_OF_DAY) < BOOKING_OPEN_HOUR) {
            start.set(Calendar.HOUR_OF_DAY, BOOKING_OPEN_HOUR)
            start.set(Calendar.MINUTE, 0)
            start.set(Calendar.SECOND, 0)
            start.set(Calendar.MILLISECOND, 0)
        }

        val end = start.clone() as Calendar
        end.add(Calendar.MINUTE, DEFAULT_DURATION_MINUTES)

        val normalized = normalizeBookingTimes(start, end)
        viewModel.setStartTime(normalized.start.time)
        viewModel.setEndTime(normalized.end.time)
        showBookingNotice(normalized.message ?: initialBookingNotice())
    }

    private fun setupClickListeners() {
        binding.btnClose.setOnClickListener { dismiss() }

        binding.cardStartTime.setOnClickListener { showDateTimePicker(true) }
        binding.cardVehicleSelection.setOnClickListener { showVehicleSelectionBottomSheet() }
        binding.sbSlideToBook.setOnSeekBarChangeListener(object : android.widget.SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: android.widget.SeekBar?, progress: Int, fromUser: Boolean) {
                binding.tvSlideToBook.alpha = 1f - (progress / 100f)
            }

            override fun onStartTrackingTouch(seekBar: android.widget.SeekBar?) {}

            override fun onStopTrackingTouch(seekBar: android.widget.SeekBar?) {
                seekBar?.let {
                    if (it.progress >= 85) {
                        it.progress = 100
                        createBooking()
                    } else {
                        val anim = android.animation.ObjectAnimator.ofInt(it, "progress", it.progress, 0)
                        anim.duration = 300
                        anim.interpolator = android.view.animation.OvershootInterpolator()
                        anim.start()
                    }
                }
            }
        })
        binding.btnAddMoney.setOnClickListener { showTopUpDialog() }
    }

    private fun setupObservers() {
        viewModel.startTime.observe(viewLifecycleOwner) { time ->
            updateStartTimeDisplay(time)
            calculatePricing()
        }

        viewModel.endTime.observe(viewLifecycleOwner) { time ->
            updateEndTimeDisplay(time)
            calculatePricing()
        }

        viewModel.selectedSpot.observe(viewLifecycleOwner) { spot ->
            updateSelectedSpotDisplay(spot)
        }

        viewModel.totalPrice.observe(viewLifecycleOwner) { price ->
            binding.tvTotalPrice.text = "₹${String.format(Locale.getDefault(), "%.2f", price)}"
        }

        viewModel.duration.observe(viewLifecycleOwner) { duration ->
            binding.tvDuration.text = duration
        }

        viewModel.selectedVehicle.observe(viewLifecycleOwner) { vehicle ->
            binding.tvSelectedVehicle.text = vehicle?.number ?: "Select your vehicle"
        }

        viewModel.walletBalance.observe(viewLifecycleOwner) { balance ->
            binding.tvWalletBalance.text = "₹${String.format(Locale.getDefault(), "%.2f", balance)}"
        }

        viewModel.isLoading.observe(viewLifecycleOwner) { isLoading ->
            binding.sbSlideToBook.isEnabled = !isLoading
            binding.sbSlideToBook.alpha = if (isLoading) 0.5f else 1f
            if (isLoading) {
                 binding.tvSlideToBook.text = "Processing..."
                 binding.tvSlideToBook.alpha = 1f
            } else {
                 binding.tvSlideToBook.text = "Slide to Book"
                 if (binding.sbSlideToBook.progress < 100) {
                     binding.tvSlideToBook.alpha = 1f - (binding.sbSlideToBook.progress / 100f)
                 }
            }
        }

        viewModel.bookingCreated.observe(viewLifecycleOwner) { booking ->
            booking?.let {
                val startMillis = viewModel.startTime.value?.time ?: System.currentTimeMillis()
                val endMillis = viewModel.endTime.value?.time ?: (startMillis + 60 * 60 * 1000)
                val totalAmount = viewModel.totalPrice.value ?: 0.0
                val selectedSpotName = viewModel.selectedSpot.value
                    ?: parkingSpot?.name
                    ?: parkingSpot?.zoneName
                val parkingName = selectedLotName.ifEmpty { binding.tvParkingName.text.toString() }
                val parkingAddress = binding.tvParkingAddress.text.toString()
                val vehicleNumber = viewModel.selectedVehicle.value?.number

                val confirmationIntent = Intent(requireContext(), BookingConfirmationActivity::class.java).apply {
                    putExtra("BOOKING_ID", it.id ?: "")
                    putExtra("TRANSACTION_ID", it.qrCode ?: "")
                    putExtra("PARKING_NAME", parkingName)
                    putExtra("PARKING_ADDRESS", parkingAddress)
                    putExtra("SELECTED_SPOT", selectedSpotName)
                    putExtra("PARKING_SPOT_ID", it.spotId)
                    putExtra("VEHICLE_NUMBER", vehicleNumber)
                    putExtra("START_TIME", startMillis)
                    putExtra("END_TIME", endMillis)
                    putExtra("TOTAL_AMOUNT", totalAmount)
                    putExtra("PAYMENT_METHOD", "Wallet")
                    putExtra("PAYMENT_STATUS", it.status ?: "Pending")
                    putExtra("BOOKING_TIMESTAMP", it.createdAt?.time ?: System.currentTimeMillis())
                }

                startActivity(confirmationIntent)
                viewModel.clearBookingCreated()
                dismissAllowingStateLoss()
            }
        }

        viewModel.errorMessage.observe(viewLifecycleOwner) { error ->
            error?.let {
                showBookingNotice(it)
                viewModel.clearError()
            }
        }
    }

    private fun loadParkingSpot(spotId: String) {
        if (spotId == "quick_book") {
            parkingSpot = createDefaultParkingSpot()
            updateParkingSpotDisplay()
            return
        }

        if (spotId.isNotEmpty()) {
            viewModel.loadParkingSpotById(spotId) { spot ->
                if (!isAdded || _binding == null) return@loadParkingSpotById
                parkingSpot = spot ?: ParkingSpot(
                    id = spotId,
                    lotId = selectedLotId,
                    spotCode = spotId,
                    name = "Selected Spot",
                    zoneName = "Unknown Spot",
                    capacity = 0,
                    available = 0,
                    status = "unknown"
                )
                updateParkingSpotDisplay()
            }
            return
        }

        parkingSpot = ParkingSpot(
            id = "unknown",
            lotId = selectedLotId,
            spotCode = null,
            name = null,
            zoneName = null,
            capacity = 0,
            available = 0,
            status = "unknown"
        )
        updateParkingSpotDisplay()
    }

    private fun updateParkingSpotDisplay() {
        parkingSpot?.let { spot ->
            if (selectedLotId.isBlank() && spot.lotId.isNotBlank()) {
                selectedLotId = spot.lotId
            }

            binding.tvParkingName.text =
                if (selectedLotName.isNotEmpty()) selectedLotName else "Unknown Location"

            val addressText = when {
                !spot.lotName.isNullOrBlank() && spot.lotName != spot.name -> spot.lotName
                !spot.zoneName.isNullOrBlank() && spot.zoneName != spot.name -> spot.zoneName
                selectedLotId.isNotEmpty() -> "Lot ID: $selectedLotId"
                else -> "Address unavailable"
            }
            // Keep legacy hidden field populated for booking logic usage
            binding.tvParkingAddress.text = addressText

            val hourlyRate = spot.bookingRate
            val rateText = if (hourlyRate > 0.0) {
                binding.tvHourlyRate.text =
                    "₹${String.format(Locale.getDefault(), "%.2f", hourlyRate)}/hour"
                " • ₹${String.format(Locale.getDefault(), "%.0f", hourlyRate)}/hr"
            } else {
                binding.tvHourlyRate.text = ""
                ""
            }

            // Set the new combined subtitle
            binding.tvParkingMeta.text = "$addressText$rateText"

            val spotName = spot.name ?: spot.zoneName ?: spot.spotCode ?: "Any available spot"
            viewModel.setSelectedSpot(spotName)

            viewModel.setParkingSpot(spot)
            calculatePricing()
            viewModel.loadUserVehicles()
        }
    }

    private fun updateSelectedSpotDisplay(spot: String?) {
        // Spot selection UI removed
    }

    private fun showDateTimePicker(isStartTime: Boolean) {
        val calendar = Calendar.getInstance()
        val currentTime = if (isStartTime) viewModel.startTime.value else viewModel.endTime.value
        currentTime?.let { calendar.time = it }

        val datePickerDialog = DatePickerDialog(
            requireContext(),
            { _, year, month, dayOfMonth ->
                calendar.set(year, month, dayOfMonth)

                TimePickerDialog(
                    requireContext(),
                    { _, hourOfDay, minute ->
                        calendar.set(Calendar.HOUR_OF_DAY, hourOfDay)
                        calendar.set(Calendar.MINUTE, minute)
                        calendar.set(Calendar.SECOND, 0)
                        calendar.set(Calendar.MILLISECOND, 0)

                        applySelectedTime(isStartTime, calendar)
                    },
                    calendar.get(Calendar.HOUR_OF_DAY),
                    calendar.get(Calendar.MINUTE),
                    false
                ).show()
            },
            calendar.get(Calendar.YEAR),
            calendar.get(Calendar.MONTH),
            calendar.get(Calendar.DAY_OF_MONTH)
        )

        val (minDate, maxDate) = getDatePickerBounds(isStartTime)
        datePickerDialog.datePicker.minDate = minDate
        maxDate?.let { datePickerDialog.datePicker.maxDate = it }
        datePickerDialog.show()
    }



    private fun showProgress(
        progressBar: android.widget.ProgressBar,
        content: View,
        emptyState: android.widget.TextView,
        show: Boolean
    ) {
        if (show) {
            progressBar.visibility = View.VISIBLE
            content.visibility = View.GONE
            emptyState.visibility = View.GONE
        } else {
            progressBar.visibility = View.GONE
        }
    }

    private fun showVehicleSelectionBottomSheet() {
        val vehicles = viewModel.userVehicles.value ?: emptyList()
        val selectedVehicleId = viewModel.selectedVehicle.value?.id

        val bottomSheet = SelectVehicleBottomSheet(
            vehicles = vehicles,
            selectedVehicleId = selectedVehicleId,
            onVehicleSelected = { vehicle ->
                viewModel.setSelectedVehicle(vehicle)
            },
            onAddVehicle = {
                showAddVehicleBottomSheet()
            }
        )
        bottomSheet.show(parentFragmentManager, SelectVehicleBottomSheet.TAG)
    }

    private fun showAddVehicleBottomSheet() {
        val bottomSheet = AddVehicleBottomSheet { newVehicleNumber ->
            viewModel.addVehicleToProfile(newVehicleNumber) { success ->
                if (!isAdded) return@addVehicleToProfile
                if (success) {
                    showToast("Vehicle added successfully!")
                    viewModel.loadUserVehicles()
                    Handler(Looper.getMainLooper()).postDelayed({
                        if (isAdded) showVehicleSelectionBottomSheet()
                    }, 500)
                } else {
                    showToast("Failed to add vehicle. Please check your connection.")
                }
            }
        }
        bottomSheet.show(parentFragmentManager, AddVehicleBottomSheet.TAG)
    }

    private fun updateStartTimeDisplay(time: Date) {
        val formatter = SimpleDateFormat("MMM dd, yyyy\nhh:mm a", Locale.getDefault())
        binding.tvStartTime.text = formatter.format(time)
    }

    private fun updateEndTimeDisplay(time: Date) {
        val formatter = SimpleDateFormat("MMM dd, yyyy\nhh:mm a", Locale.getDefault())
        binding.tvEndTime.text = formatter.format(time)
    }

    private fun calculatePricing() {
        viewModel.calculatePricing()
    }

    private data class NormalizedTimes(
        val start: Calendar,
        val end: Calendar,
        val message: String?,
        val adjusted: Boolean
    )

    private fun applySelectedTime(isStartTime: Boolean, selectedCalendar: Calendar) {
        val currentStart = viewModel.startTime.value?.let { calendarFromDate(it) } ?: selectedCalendar
        val currentEnd = viewModel.endTime.value?.let { calendarFromDate(it) } ?: selectedCalendar

        val startCandidate = if (isStartTime) selectedCalendar.clone() as Calendar else currentStart
        val endCandidate = if (isStartTime) currentEnd else selectedCalendar.clone() as Calendar

        val normalized = normalizeBookingTimes(startCandidate, endCandidate)
        viewModel.setStartTime(normalized.start.time)
        viewModel.setEndTime(normalized.end.time)
        showBookingNotice(normalized.message)
    }

    private fun validateCurrentTimesForBooking(): Boolean {
        val start = viewModel.startTime.value
        val end = viewModel.endTime.value

        if (start == null || end == null) {
            showBookingNotice("Please select start and end times.")
            return false
        }

        val normalized = normalizeBookingTimes(calendarFromDate(start), calendarFromDate(end))
        return if (normalized.adjusted) {
            viewModel.setStartTime(normalized.start.time)
            viewModel.setEndTime(normalized.end.time)
            showBookingNotice(normalized.message)
            false
        } else {
            showBookingNotice(null)
            true
        }
    }

    private fun getDatePickerBounds(isStartTime: Boolean): Pair<Long, Long?> {
        val now = Calendar.getInstance()
        val bookingDay = getBookingDay(now)

        return if (isStartTime) {
            val maxDate = getEndOfDay(bookingDay)
            bookingDay.timeInMillis to maxDate.timeInMillis
        } else {
            val selectedStart = viewModel.startTime.value?.let { calendarFromDate(it) }
            val selectedStartDay = selectedStart?.let { getStartOfDay(it) } ?: bookingDay
            val minDate = if (selectedStartDay.before(bookingDay)) bookingDay else selectedStartDay
            val maxDate = getEndOfDay(bookingDay)
            minDate.timeInMillis to maxDate.timeInMillis
        }
    }

    private fun showBookingNotice(message: String?) {
        if (message.isNullOrBlank()) {
            binding.tvBookingNotice.visibility = View.GONE
        } else {
            binding.tvBookingNotice.text = message
            binding.tvBookingNotice.visibility = View.VISIBLE
        }
    }

    private fun normalizeBookingTimes(start: Calendar, end: Calendar): NormalizedTimes {
        val now = Calendar.getInstance()
        val bookingDay = getBookingDay(now)
        val cutoff = getCutoffCalendar(bookingDay)
        val opening = getOpeningCalendar(bookingDay)
        val isBeforeCutoff = now.before(getCutoffCalendar(now))

        val adjustedStart = start.clone() as Calendar
        val adjustedEnd = end.clone() as Calendar
        val messages = mutableListOf<String>()
        var adjusted = false

        if (!isSameDate(adjustedStart, bookingDay)) {
            alignDate(adjustedStart, bookingDay)
            adjusted = true
            messages.add(if (isBeforeCutoff) "Before 8 PM, bookings must be for today." else "After 8 PM, bookings must be for tomorrow.")
        }

        if (!isSameDate(adjustedEnd, bookingDay)) {
            alignDate(adjustedEnd, bookingDay)
            adjusted = true
            messages.add("Bookings must start and end on the same day.")
        }

        if (adjustedStart.before(opening)) {
            adjustedStart.timeInMillis = opening.timeInMillis
            adjusted = true
            messages.add("Start time must be at or after 8 AM. Updated to 8 AM (earliest available).")
        }

        if (adjustedEnd.after(cutoff)) {
            adjustedEnd.timeInMillis = cutoff.timeInMillis
            adjusted = true
            messages.add("Bookings must end by 8 PM. End time adjusted to 8 PM.")
        }

        if (!adjustedEnd.after(adjustedStart)) {
            val fallbackEnd = adjustedStart.clone() as Calendar
            fallbackEnd.add(Calendar.MINUTE, DEFAULT_DURATION_MINUTES)
            if (fallbackEnd.after(cutoff)) fallbackEnd.timeInMillis = cutoff.timeInMillis
            adjustedEnd.timeInMillis = fallbackEnd.timeInMillis
            adjusted = true
            messages.add("End time updated to be after start time.")

            if (!adjustedEnd.after(adjustedStart)) {
                val fallbackStart = adjustedEnd.clone() as Calendar
                fallbackStart.add(Calendar.MINUTE, -1)
                adjustedStart.timeInMillis = fallbackStart.timeInMillis
                adjusted = true
                messages.add("Start time adjusted to fit within opening hours.")
            }
        }

        val message = messages.distinct().joinToString(" ").ifBlank { null }
        return NormalizedTimes(adjustedStart, adjustedEnd, message, adjusted)
    }

    private fun getBookingDay(now: Calendar): Calendar {
        val today = getStartOfDay(now)
        return if (now.before(getCutoffCalendar(now))) today else (today.clone() as Calendar).apply {
            add(Calendar.DAY_OF_MONTH, 1)
        }
    }

    private fun initialBookingNotice(): String? {
        val now = Calendar.getInstance()
        return when {
            now.get(Calendar.HOUR_OF_DAY) >= BOOKING_CUTOFF_HOUR -> "After 8 PM, bookings must be for tomorrow."
            now.get(Calendar.HOUR_OF_DAY) < BOOKING_OPEN_HOUR -> "Bookings start at 8 AM. You can book now for 8 AM or later."
            else -> null
        }
    }

    private fun getCutoffCalendar(reference: Calendar): Calendar {
        return (reference.clone() as Calendar).apply {
            set(Calendar.HOUR_OF_DAY, BOOKING_CUTOFF_HOUR)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
    }

    private fun getOpeningCalendar(reference: Calendar): Calendar {
        return (reference.clone() as Calendar).apply {
            set(Calendar.HOUR_OF_DAY, BOOKING_OPEN_HOUR)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
    }

    private fun getStartOfDay(reference: Calendar): Calendar {
        return (reference.clone() as Calendar).apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
    }

    private fun getEndOfDay(reference: Calendar): Calendar {
        return (reference.clone() as Calendar).apply {
            set(Calendar.HOUR_OF_DAY, 23)
            set(Calendar.MINUTE, 59)
            set(Calendar.SECOND, 59)
            set(Calendar.MILLISECOND, 999)
        }
    }

    private fun isSameDate(first: Calendar, second: Calendar): Boolean {
        return first.get(Calendar.YEAR) == second.get(Calendar.YEAR) &&
            first.get(Calendar.DAY_OF_YEAR) == second.get(Calendar.DAY_OF_YEAR)
    }

    private fun alignDate(target: Calendar, reference: Calendar) {
        target.set(Calendar.YEAR, reference.get(Calendar.YEAR))
        target.set(Calendar.MONTH, reference.get(Calendar.MONTH))
        target.set(Calendar.DAY_OF_MONTH, reference.get(Calendar.DAY_OF_MONTH))
    }

    private fun calendarFromDate(date: Date): Calendar {
        return Calendar.getInstance().apply { time = date }
    }

    private fun createBooking() {
        val selectedVehicle = viewModel.selectedVehicle.value

        if (selectedVehicle == null) {
            showBookingNotice("Please select a vehicle.")
            return
        }

        if (!validateCurrentTimesForBooking()) {
            return
        }

        viewModel.setVehicleNumber(selectedVehicle.number)
        viewModel.createBackendBooking()
    }

    private fun showTopUpDialog() {
        val bottomSheetDialog = BottomSheetDialog(requireContext(), R.style.BottomSheetDialogTheme)
        val bottomSheetBinding = BottomSheetTopUpBinding.inflate(layoutInflater)
        bottomSheetDialog.setContentView(bottomSheetBinding.root)

        bottomSheetDialog.window?.apply {
            setWindowAnimations(R.style.BottomSheetSpringAnimation)
            setDimAmount(0.45f)

            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                attributes.blurBehindRadius = 50
                attributes = attributes
            }
        }

        bottomSheetDialog.behavior.apply {
            skipCollapsed = true
            state = BottomSheetBehavior.STATE_EXPANDED
            isFitToContents = true
        }

        bottomSheetDialog.setOnShowListener {
            val bottomSheet = bottomSheetDialog.findViewById<FrameLayout>(
                com.google.android.material.R.id.design_bottom_sheet
            )
            bottomSheet?.post {
                bottomSheet.translationY = 120f
                bottomSheet.alpha = 0f
                val spring = SpringAnimation(bottomSheet, DynamicAnimation.TRANSLATION_Y, 0f).apply {
                    spring = SpringForce(0f).apply {
                        dampingRatio = SpringForce.DAMPING_RATIO_MEDIUM_BOUNCY
                        stiffness = SpringForce.STIFFNESS_LOW
                    }
                }
                bottomSheet.animate()
                    .alpha(1f)
                    .setDuration(220)
                    .start()
                spring.start()
            }
        }

        val currentBalance = viewModel.walletBalance.value ?: 0.0
        bottomSheetBinding.tvCurrentBalance.text =
            "₹${String.format(Locale.getDefault(), "%.2f", currentBalance)}"

        bottomSheetBinding.btnAmount50.setOnClickListener {
            bottomSheetBinding.etAmount.setText("50")
            updateAddButtonState(bottomSheetBinding)
        }

        bottomSheetBinding.btnAmount100.setOnClickListener {
            bottomSheetBinding.etAmount.setText("100")
            updateAddButtonState(bottomSheetBinding)
        }

        bottomSheetBinding.btnAmount200.setOnClickListener {
            bottomSheetBinding.etAmount.setText("200")
            updateAddButtonState(bottomSheetBinding)
        }

        bottomSheetBinding.btnAmount500.setOnClickListener {
            bottomSheetBinding.etAmount.setText("500")
            updateAddButtonState(bottomSheetBinding)
        }

        bottomSheetBinding.etAmount.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                updateAddButtonState(bottomSheetBinding)
            }
        })

        bottomSheetBinding.btnClose.setOnClickListener {
            animateAndDismiss(bottomSheetDialog)
        }

        bottomSheetBinding.btnAddMoneyConfirm.setOnClickListener {
            val amountText = bottomSheetBinding.etAmount.text.toString()
            val amount = amountText.toDoubleOrNull()
            if (amount != null && amount > 0) {
                showToast("Redirecting to Razorpay checkout...")
                initiateWalletTopUp(amount)
                animateAndDismiss(bottomSheetDialog)
            } else {
                showToast("Please enter a valid amount")
            }
        }

        updateAddButtonState(bottomSheetBinding)
        bottomSheetDialog.show()
    }

    private fun updateAddButtonState(binding: BottomSheetTopUpBinding) {
        val amountText = binding.etAmount.text.toString()
        val amount = amountText.toDoubleOrNull()
        val isValidAmount = amount != null && amount > 0

        binding.btnAddMoneyConfirm.isEnabled = isValidAmount
        binding.btnAddMoneyConfirm.text = if (isValidAmount) {
            "Add ₹${amount?.toInt()}"
        } else {
            "Add Money"
        }
    }

    private fun animateAndDismiss(dialog: BottomSheetDialog) {
        val bottomSheet = dialog.findViewById<FrameLayout>(com.google.android.material.R.id.design_bottom_sheet)
        if (bottomSheet == null) {
            dialog.dismiss()
            return
        }
        bottomSheet.animate()
            .translationY(bottomSheet.height * 0.25f)
            .alpha(0f)
            .setDuration(200)
            .withEndAction {
                dialog.dismiss()
                bottomSheet.translationY = 0f
                bottomSheet.alpha = 1f
            }
            .start()
    }

    private fun initiateWalletTopUp(amount: Double) {
        val userId = AuthSession.getUserId(requireContext())

        if (userId == null) {
            showToast("Please log in to add money")
            return
        }

        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val response = ApiClient.apiService.initiatePayment(
                    com.gridee.parking.data.model.PaymentInitiateRequest(
                        userId = userId,
                        amount = amount
                    )
                )

                if (response.isSuccessful) {
                    val result = response.body()
                    if (result != null) {
                        val intent = Intent(requireContext(), WalletTopUpActivity::class.java).apply {
                            putExtra("USER_ID", userId)
                            putExtra("AMOUNT", amount)
                            putExtra("ORDER_ID", result.orderId)
                            putExtra("KEY_ID", result.keyId)
                        }
                        startActivity(intent)
                    } else {
                        showToast("Failed to initiate payment")
                    }
                } else {
                    showToast("Error: ${response.message()}")
                }
            } catch (e: Exception) {
                showToast("Error: ${e.message}")
                e.printStackTrace()
            }
        }
    }

    private fun createDefaultParkingSpot(): ParkingSpot {
        return ParkingSpot(
            id = "default_spot",
            lotId = selectedLotId,
            name = null,
            zoneName = null,
            capacity = 0,
            available = 0,
            status = "unknown"
        )
    }

    private fun showToast(message: String) {
        val context = context ?: return
        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
    }

    override fun onDestroyView() {
        bottomSheetBehavior?.removeBottomSheetCallback(bottomSheetCallback)
        bottomSheetBehavior = null
        super.onDestroyView()
        _binding = null
    }

    companion object {
        const val TAG = "ParkingSpotBottomSheet"

        private const val BOOKING_OPEN_HOUR = 8
        private const val BOOKING_CUTOFF_HOUR = 20
        private const val DEFAULT_DURATION_MINUTES = 120

        private const val ARG_PARKING_SPOT_ID = "PARKING_SPOT_ID"
        private const val ARG_PARKING_LOT_ID = "PARKING_LOT_ID"
        private const val ARG_PARKING_LOT_NAME = "PARKING_LOT_NAME"

        fun newInstance(
            parkingSpotId: String,
            parkingLotId: String,
            parkingLotName: String
        ): ParkingSpotBottomSheet {
            return ParkingSpotBottomSheet().apply {
                arguments = Bundle().apply {
                    putString(ARG_PARKING_SPOT_ID, parkingSpotId)
                    putString(ARG_PARKING_LOT_ID, parkingLotId)
                    putString(ARG_PARKING_LOT_NAME, parkingLotName)
                }
            }
        }
    }
}
