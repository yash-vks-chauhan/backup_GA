package com.gridee.parking.ui.booking

import com.gridee.parking.R

import android.content.Intent
import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import android.view.View
import com.gridee.parking.data.model.ParkingSpot
import com.gridee.parking.data.model.BookingPolicyResolver
import com.gridee.parking.data.model.ResolvedBookingPolicy
import com.gridee.parking.utils.ParkingSpotSchedulePolicy
import com.gridee.parking.databinding.ActivityBookingFlowBinding
import com.gridee.parking.ui.wallet.WalletAddMoneyActivity
import java.text.SimpleDateFormat
import java.util.*

class BookingFlowActivity : AppCompatActivity() {

    private lateinit var binding: ActivityBookingFlowBinding
    private lateinit var viewModel: BookingViewModel
    private var parkingSpot: ParkingSpot? = null
    private var selectedLotId: String = ""
    private var selectedLotName: String = ""
    private var selectedSpotId: String = ""
    private var bookingPolicy: ResolvedBookingPolicy? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityBookingFlowBinding.inflate(layoutInflater)
        setContentView(binding.root)

        viewModel = ViewModelProvider(this)[BookingViewModel::class.java]

        // Get parking spot data from intent
        selectedSpotId = intent.getStringExtra("PARKING_SPOT_ID") ?: ""
        selectedLotId = intent.getStringExtra("PARKING_LOT_ID") ?: ""
        selectedLotName = intent.getStringExtra("PARKING_LOT_NAME") ?: ""
        
        setupUI()
        setupClickListeners()
        setupObservers()
        viewModel.loadBookingPolicy(selectedLotId) { loaded ->
            if (loaded) loadParkingSpot(selectedSpotId)
        }
    }
    
    override fun onResume() {
        super.onResume()
        // Refresh vehicles when returning to this activity (e.g., from profile)
        viewModel.loadUserVehicles()
        // Refresh wallet balance when returning (e.g., after adding money)
        viewModel.loadWalletBalance()
    }

    private fun setupUI() {
        binding.tvTitle.text = getString(R.string.book_parking)
        binding.cardStartTime.visibility = View.INVISIBLE
        binding.cardEndTime.visibility = View.INVISIBLE
        binding.tvVehicleTitle.visibility = View.INVISIBLE
        binding.cardVehicleSelection.visibility = View.INVISIBLE
        binding.tvPaymentWalletTitle.visibility = View.INVISIBLE
        binding.cardWallet.visibility = View.INVISIBLE
        binding.tvPolicySummary.visibility = View.INVISIBLE
        binding.btnContinueToPayment.isEnabled = false
    }

    private fun setupClickListeners() {
        binding.btnBack.setOnClickListener {
            finish()
        }

        binding.btnSelectSpot.setOnClickListener {
            showSpotSelectionDialog()
        }

        binding.cardStartTime.setOnClickListener {
            if (bookingPolicy?.usesDynamicTimeSelection == true) showDateTimePicker(true)
        }
        binding.cardEndTime.setOnClickListener {
            if (bookingPolicy?.usesDynamicTimeSelection == true) showDateTimePicker(false)
        }
        
        binding.cardVehicleSelection.setOnClickListener {
            showVehicleSelectionDialog()
        }

        binding.btnContinueToPayment.setOnClickListener {
            createBooking()
        }
        
        binding.btnAddMoney.setOnClickListener {
            startActivity(
                WalletAddMoneyActivity.createIntent(
                    context = this,
                    currentBalance = viewModel.walletBalance.value ?: 0.0,
                    parkingLotId = selectedLotId,
                )
            )
        }
    }

    private fun setupObservers() {
        viewModel.bookingPolicy.observe(this) { policy ->
            bookingPolicy = policy
            if (policy != null) {
                binding.cardStartTime.visibility = View.VISIBLE
                binding.cardEndTime.visibility = View.VISIBLE
                binding.tvVehicleTitle.visibility =
                    if (policy.requiresVehicleRegistration) View.VISIBLE else View.GONE
                binding.cardVehicleSelection.visibility =
                    if (policy.requiresVehicleRegistration) View.VISIBLE else View.GONE
                binding.tvPaymentWalletTitle.visibility =
                    if (policy.bookingChargeRequired) View.VISIBLE else View.GONE
                binding.cardWallet.visibility = if (policy.bookingChargeRequired) View.VISIBLE else View.GONE
                binding.tvPolicySummary.text = when {
                    !policy.bookingChargeRequired -> getString(R.string.booking_policy_no_payment)
                    policy.isNoRefund -> getString(R.string.booking_policy_no_refund)
                    else -> getString(R.string.booking_policy_standard_refund)
                }
                binding.tvPolicySummary.visibility = View.VISIBLE
                initializePolicyTimes(policy)
                binding.btnContinueToPayment.isEnabled = true
            }
        }

        viewModel.policyError.observe(this) { error ->
            if (!error.isNullOrBlank()) showToast(error)
        }

        viewModel.startTime.observe(this) { time ->
            updateStartTimeDisplay(time)
            calculatePricing()
        }

        viewModel.endTime.observe(this) { time ->
            updateEndTimeDisplay(time)
            calculatePricing()
        }

        viewModel.selectedSpot.observe(this) { spot ->
            updateSelectedSpotDisplay(spot)
        }

        viewModel.totalPrice.observe(this) { price ->
            binding.tvTotalPrice.text = if (bookingPolicy?.bookingChargeRequired == false) {
                "Free"
            } else {
                "₹${String.format(Locale.getDefault(), "%.2f", price)}"
            }
        }

        viewModel.duration.observe(this) { duration ->
            binding.tvDuration.text = duration
        }
        
        viewModel.selectedVehicle.observe(this) { vehicle ->
            binding.tvSelectedVehicle.text = vehicle?.number ?: "Select your vehicle"
        }
        
        viewModel.walletBalance.observe(this) { balance ->
            if (bookingPolicy?.bookingChargeRequired != false) {
                binding.tvWalletBalance.text = "₹${String.format(Locale.getDefault(), "%.2f", balance)}"
            }
        }
        
        // Backend integration observers
        viewModel.isLoading.observe(this) { isLoading ->
            binding.btnContinueToPayment.isEnabled = !isLoading && bookingPolicy != null
            // Note: Since btnContinueToPayment is a CardView with TextView, 
            // we can't directly set text. The button text stays as "Continue to Payment"
        }
        
        viewModel.bookingCreated.observe(this) { booking ->
            booking?.let {
                val startMillis = viewModel.startTime.value?.time ?: System.currentTimeMillis()
                val endMillis = viewModel.endTime.value?.time ?: (startMillis + 60 * 60 * 1000)
                val totalAmount = it.amount
                val selectedSpotName = viewModel.selectedSpot.value
                    ?: parkingSpot?.name
                    ?: parkingSpot?.zoneName
                val parkingName = selectedLotName.ifEmpty { binding.tvParkingName.text.toString() }
                val parkingAddress = binding.tvParkingAddress.text.toString()
                val vehicleNumber = viewModel.selectedVehicle.value?.number

                val confirmationIntent = Intent(this, BookingConfirmationActivity::class.java).apply {
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
                    putExtra(
                        "PAYMENT_METHOD",
                        when {
                            bookingPolicy?.walletPaymentRequired == true -> "Gridee Coin Wallet"
                            bookingPolicy?.paymentRequired == true -> "Wallet"
                            else -> "No payment required"
                        }
                    )
                    putExtra("PAYMENT_STATUS", it.status ?: "Pending")
                    putExtra("BOOKING_TIMESTAMP", it.createdAt?.time ?: System.currentTimeMillis())
                }

                startActivity(confirmationIntent)
                viewModel.clearBookingCreated()
                finish()
            }
        }
        
        viewModel.errorMessage.observe(this) { error ->
            error?.let {
                showToast(it)
                viewModel.clearError()
            }
        }
    }

    private fun loadParkingSpot(spotId: String) {
        if (spotId == "quick_book") {
            // Quick booking mode - create a default parking spot
            parkingSpot = createDefaultParkingSpot()
            updateParkingSpotDisplay()
        } else if (spotId.isNotEmpty()) {
            // Load actual parking spot data from API
            viewModel.loadParkingSpotById(spotId) { spot ->
                if (spot != null && spot.lotId == selectedLotId) {
                    parkingSpot = spot
                } else {
                    showToast("This parking spot could not be verified for the selected lot.")
                    binding.btnContinueToPayment.isEnabled = false
                    return@loadParkingSpotById
                }
                updateParkingSpotDisplay()
            }
        } else {
            // Fallback for missing/unknown spot without injecting dummy location data
            parkingSpot = ParkingSpot(
                id = if (spotId.isNotEmpty()) spotId else "unknown",
                lotId = selectedLotId,
                spotCode = spotId,
                name = null,
                zoneName = null,
                capacity = 0,
                available = 0,
                status = "unknown"
            )
            updateParkingSpotDisplay()
        }
    }
    
    private fun updateParkingSpotDisplay() {
        parkingSpot?.let { spot ->
            // Display the selected parking lot name (main location)
            binding.tvParkingName.text = if (selectedLotName.isNotEmpty()) selectedLotName else "Unknown Location"
            
            // Display the parking lot address/info
            val addressText = when {
                !spot.zoneName.isNullOrBlank() && spot.zoneName != spot.name -> spot.zoneName
                selectedLotId.isNotEmpty() -> "Lot ID: $selectedLotId"
                else -> "Address unavailable"
            }
            binding.tvParkingAddress.text = addressText
            
            val hourlyRate = spot.bookingRate
            if (hourlyRate > 0.0) {
                binding.tvHourlyRate.text =
                    "₹${String.format(Locale.getDefault(), "%.2f", hourlyRate)}/hour"
                binding.tvHourlyRate.visibility = View.VISIBLE
            } else {
                binding.tvHourlyRate.visibility = View.GONE
            }
            
            // Update the selected spot display to show the actual selected spot
            val spotName = spot.name ?: spot.zoneName ?: spot.spotCode ?: "Any available spot"
            binding.tvSelectedSpot.text = spotName
            viewModel.setSelectedSpot(spotName)

            viewModel.setParkingSpot(spot)
            bookingPolicy?.takeIf { it.usesFixedDailySlots }?.let { policy ->
                val now = ParkingSpotSchedulePolicy.currentTime()
                val start = ParkingSpotSchedulePolicy.minimumAllowedStartTime(spot, now, policy)
                val end = ParkingSpotSchedulePolicy.sessionEndTime(spot, now, policy)
                if (start != null && end != null && start.before(end)) {
                    viewModel.setStartTime(start.time)
                    viewModel.setEndTime(end.time)
                }
            }
            calculatePricing()
            viewModel.loadUserVehicles()
        }
    }

    private fun updateSelectedSpotDisplay(spot: String?) {
        // Display the selected spot name or default to "Any available spot"
        binding.tvSelectedSpot.text = spot ?: "Any available spot"
    }

    private fun showSpotSelectionDialog() {
        val dialogView = layoutInflater.inflate(com.gridee.parking.R.layout.dialog_spot_selection, null)
        val dialog = androidx.appcompat.app.AlertDialog.Builder(this)
            .setView(dialogView)
            .create()
        
        val recyclerView = dialogView.findViewById<androidx.recyclerview.widget.RecyclerView>(com.gridee.parking.R.id.rv_parking_spots)
        val progressBar = dialogView.findViewById<android.widget.ProgressBar>(com.gridee.parking.R.id.progress_bar)
        val emptyState = dialogView.findViewById<android.widget.TextView>(com.gridee.parking.R.id.tv_empty_state)
        val cardAnySpot = dialogView.findViewById<androidx.cardview.widget.CardView>(com.gridee.parking.R.id.card_any_spot)
        val ivAnySpotSelected = dialogView.findViewById<android.widget.ImageView>(com.gridee.parking.R.id.iv_any_spot_selected)
        val btnCancel = dialogView.findViewById<android.widget.Button>(com.gridee.parking.R.id.btn_cancel)
        val btnSelect = dialogView.findViewById<android.widget.Button>(com.gridee.parking.R.id.btn_select)
        
        var selectedSpot: ParkingSpot? = null
        
        // Check current selection state - if a specific spot is already selected, don't default to "Any available spot"
        val currentSelectedSpotText = binding.tvSelectedSpot.text.toString()
        var isAnySpotSelected = currentSelectedSpotText == "Any available spot"
        
        // Set up RecyclerView
        val spotAdapter = ParkingSpotSelectionAdapter(
            onItemClick = { spot ->
                selectedSpot = spot
                isAnySpotSelected = false
                updateSpotSelection(ivAnySpotSelected, false)
                showToast("Selected spot: ${spot.name ?: spot.zoneName ?: spot.spotCode ?: spot.id}")
            }
        )
        
        recyclerView.layoutManager = androidx.recyclerview.widget.LinearLayoutManager(this)
        recyclerView.adapter = spotAdapter
        
        // Load parking spots for current lot
        showProgress(progressBar, recyclerView, emptyState, true)
        
        // Use the repository directly like the discovery screen does
        lifecycleScope.launch {
            try {
                val parkingRepository = com.gridee.parking.data.repository.ParkingRepository()
                val spotsResponse = if (selectedLotId.isNotEmpty()) {
                    parkingRepository.getParkingSpotsByLot(selectedLotId)
                } else {
                    retrofit2.Response.success(emptyList<com.gridee.parking.data.model.ParkingSpot>())
                }
                
                runOnUiThread {
                    if (spotsResponse.isSuccessful) {
                        val filteredSpots = ParkingSpotSchedulePolicy.filterVisibleSpots(
                            spotsResponse.body().orEmpty().mapNotNull { spot ->
                                when (spot.lotId.trim()) {
                                    "" -> spot.copy(lotId = selectedLotId)
                                    selectedLotId -> spot
                                    else -> null
                                }
                            }.filter { it.available > 0 },
                            policy = bookingPolicy,
                        )
                        
                        showProgress(progressBar, recyclerView, emptyState, false)
                        
                        if (filteredSpots.isNotEmpty()) {
                            spotAdapter.submitList(filteredSpots)
                            recyclerView.visibility = android.view.View.VISIBLE
                            emptyState.visibility = android.view.View.GONE
                            
                            // Force layout update
                            recyclerView.requestLayout()
                            
                            // Pre-select the current spot if it matches one in the list
                            if (!isAnySpotSelected) {
                                val currentSpot = filteredSpots.find { spot ->
                                    val spotName = spot.name ?: spot.zoneName ?: ""
                                    spotName == currentSelectedSpotText
                                }
                                if (currentSpot != null) {
                                    selectedSpot = currentSpot
                                    spotAdapter.setSelectedSpot(currentSpot.id)
                                }
                            }
                        } else {
                            recyclerView.visibility = android.view.View.GONE
                            emptyState.visibility = android.view.View.VISIBLE
                        }
                    } else {
                        showProgress(progressBar, recyclerView, emptyState, false)
                        showToast("API call failed: ${spotsResponse.code()}")
                        recyclerView.visibility = android.view.View.GONE
                        emptyState.visibility = android.view.View.VISIBLE
                    }
                }
            } catch (e: Exception) {
                runOnUiThread {
                    showProgress(progressBar, recyclerView, emptyState, false)
                    showToast("Error loading spots: ${e.message}")
                    recyclerView.visibility = android.view.View.GONE
                    emptyState.visibility = android.view.View.VISIBLE
                }
            }
        }
        
        // "Any available spot" selection
        cardAnySpot.setOnClickListener {
            selectedSpot = null
            isAnySpotSelected = true
            updateSpotSelection(ivAnySpotSelected, true)
            spotAdapter.setSelectedSpot(null) // Clear adapter selection
            showToast(getString(R.string.selected_any_available_spot))
        }
        
        // Cancel button
        btnCancel.setOnClickListener {
            dialog.dismiss()
        }
        
        // Select button
        btnSelect.setOnClickListener {
            
            if (isAnySpotSelected) {
                // User chose "Any available spot"
                showToast(getString(R.string.applying_any_available_spot))
                binding.tvSelectedSpot.text = getString(R.string.any_available_spot)
                viewModel.setSelectedSpot(null)
            } else {
                selectedSpot?.let { spot ->
                    // User chose a specific spot
                    val spotName = spot.name ?: spot.zoneName ?: "Selected Spot"
                    showToast("Applying: $spotName")
                    
                    // Debug: Check what we're setting
                    binding.tvSelectedSpot.text = spotName
                    
                    // Force UI update
                    binding.tvSelectedSpot.requestLayout()
                    binding.tvSelectedSpot.invalidate()
                    
                    // Debug: Check what was actually set
                    
                    // Debug: Check text after a delay to see if something overwrites it
                    android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                    }, 500)
                    
                    viewModel.setSelectedSpot(spotName)
                    parkingSpot = spot
                    if (spot.lotId.isNotBlank()) {
                        selectedLotId = spot.lotId
                    }
                    viewModel.setParkingSpot(spot)
                } ?: run {
                    showToast(getString(R.string.no_specific_spot_selected_using_any))
                    binding.tvSelectedSpot.text = getString(R.string.any_available_spot)
                    viewModel.setSelectedSpot(null)
                }
            }
            dialog.dismiss()
        }
        
        // Initialize selection state based on current selection
        updateSpotSelection(ivAnySpotSelected, isAnySpotSelected)
        
        dialog.show()
    }
    
    private fun updateSpotSelection(imageView: android.widget.ImageView, isSelected: Boolean) {
        if (isSelected) {
            imageView.setImageResource(com.gridee.parking.R.drawable.ic_radio_button_checked)
        } else {
            imageView.setImageResource(com.gridee.parking.R.drawable.ic_radio_button_unchecked)
        }
    }
    
    private fun showProgress(progressBar: android.widget.ProgressBar, content: android.view.View, emptyState: android.widget.TextView, show: Boolean) {
        if (show) {
            progressBar.visibility = android.view.View.VISIBLE
            content.visibility = android.view.View.GONE
            emptyState.visibility = android.view.View.GONE
        } else {
            progressBar.visibility = android.view.View.GONE
        }
    }
    
    private fun showVehicleSelectionDialog() {
        val dialogView = layoutInflater.inflate(com.gridee.parking.R.layout.dialog_vehicle_selection, null)
        val dialog = androidx.appcompat.app.AlertDialog.Builder(this)
            .setView(dialogView)
            .create()
        
        val recyclerView = dialogView.findViewById<androidx.recyclerview.widget.RecyclerView>(com.gridee.parking.R.id.rv_vehicles)
        val btnAddVehicle = dialogView.findViewById<androidx.cardview.widget.CardView>(com.gridee.parking.R.id.btn_add_vehicle)
        val btnCancel = dialogView.findViewById<android.widget.Button>(com.gridee.parking.R.id.btn_cancel)
        val btnSelect = dialogView.findViewById<android.widget.Button>(com.gridee.parking.R.id.btn_select)
        
        val vehicles = viewModel.userVehicles.value ?: emptyList()
        var selectedVehicle: com.gridee.parking.data.model.Vehicle? = null
        
        val adapter = VehicleSelectionAdapter(vehicles) { vehicle ->
            selectedVehicle = vehicle
        }
        
        recyclerView.layoutManager = androidx.recyclerview.widget.LinearLayoutManager(this)
        recyclerView.adapter = adapter
        
        // Set default selection if user has a selected vehicle
        viewModel.selectedVehicle.value?.let { currentVehicle ->
            val position = vehicles.indexOfFirst { it.id == currentVehicle.id }
            if (position >= 0) {
                adapter.setSelectedPosition(position)
                selectedVehicle = currentVehicle
            }
        }
        
        btnAddVehicle.setOnClickListener {
            dialog.dismiss()
            showAddVehicleDialog { newVehicleNumber ->
                // After adding vehicle, reload the vehicles list and reopen selection dialog
                viewModel.addVehicleToProfile(newVehicleNumber) { success ->
                    runOnUiThread {
                        if (success) {
                            showToast(getString(R.string.vehicle_added_successfully_2))
                            viewModel.loadUserVehicles() // Refresh the list
                            // Reopen the selection dialog after a short delay
                            android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                                showVehicleSelectionDialog()
                            }, 500)
                        } else {
                            showToast(getString(R.string.failed_to_add_vehicle_please_check))
                        }
                    }
                }
            }
        }
        
        btnCancel.setOnClickListener {
            dialog.dismiss()
        }
        
        btnSelect.setOnClickListener {
            selectedVehicle?.let { vehicle ->
                viewModel.setSelectedVehicle(vehicle)
                dialog.dismiss()
            } ?: run {
                showToast(getString(R.string.please_select_a_vehicle))
            }
        }
        
        dialog.show()
    }
    
    private fun showAddVehicleDialog(onVehicleAdded: (String) -> Unit) {
        val input = android.widget.EditText(this)
        input.hint = "Enter vehicle number (e.g., MH01AB1234)"
        
        val builder = androidx.appcompat.app.AlertDialog.Builder(this)
        builder.setTitle("Add Vehicle")
        builder.setView(input)
        builder.setPositiveButton("Add") { _, _ ->
            val vehicleNumber = input.text.toString().trim().uppercase()
            if (vehicleNumber.isNotEmpty()) {
                onVehicleAdded(vehicleNumber)
            } else {
                showToast(getString(R.string.please_enter_a_vehicle_number))
            }
        }
        builder.setNegativeButton("Cancel", null)
        builder.show()
    }

    private fun updateStartTimeDisplay(time: Date) {
        val formatter = SimpleDateFormat("MMM dd, yyyy\nhh:mm a", Locale.getDefault())
        binding.tvStartTime.text = formatter.format(time)
    }

    private fun updateEndTimeDisplay(time: Date) {
        val formatter = SimpleDateFormat("MMM dd, yyyy\nhh:mm a", Locale.getDefault())
        binding.tvEndTime.text = formatter.format(time)
    }

    private fun initializePolicyTimes(policy: ResolvedBookingPolicy) {
        if (viewModel.startTime.value != null && viewModel.endTime.value != null) return
        val now = Calendar.getInstance().apply {
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            val remainder = get(Calendar.MINUTE) % 15
            if (remainder != 0) add(Calendar.MINUTE, 15 - remainder)
        }
        var start = (now.clone() as Calendar)
        if (!start.before(policy.endOfBookingDay(start))) {
            val tomorrow = (now.clone() as Calendar).apply {
                add(Calendar.DAY_OF_MONTH, 1)
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
            }
            if (policy.advanceBookingDays >= 1 && policy.isFutureDateOpen(tomorrow, now)) {
                start = tomorrow
            }
        }
        val end = (start.clone() as Calendar).apply { add(Calendar.HOUR_OF_DAY, 2) }
        val cutoff = policy.endOfBookingDay(end)
        if (end.after(cutoff)) end.timeInMillis = cutoff.timeInMillis
        viewModel.setStartTime(start.time)
        viewModel.setEndTime(end.time)
    }

    private fun showDateTimePicker(selectingStart: Boolean) {
        val policy = bookingPolicy ?: return
        if (!policy.usesDynamicTimeSelection) return
        val now = Calendar.getInstance()
        val current = (if (selectingStart) viewModel.startTime.value else viewModel.endTime.value)
            ?.let { Calendar.getInstance().apply { time = it } }
            ?: now
        val latest = (now.clone() as Calendar).apply { add(Calendar.DAY_OF_MONTH, policy.advanceBookingDays) }
        val dialog = DatePickerDialog(
            this,
            { _, year, month, day ->
                val selected = (current.clone() as Calendar).apply {
                    set(year, month, day)
                }
                if (!policy.isDateWithinAdvanceWindow(selected, now) || !policy.isFutureDateOpen(selected, now)) {
                    val opens = policy.nextDayBookingOpenMinutes?.let(BookingPolicyResolver::formatTime)
                    showToast(opens?.let { "Future bookings open at $it." }
                        ?: "That date is not available for booking.")
                    return@DatePickerDialog
                }
                TimePickerDialog(
                    this,
                    { _, hour, minute ->
                        selected.set(Calendar.HOUR_OF_DAY, hour)
                        selected.set(Calendar.MINUTE, minute)
                        selected.set(Calendar.SECOND, 0)
                        selected.set(Calendar.MILLISECOND, 0)
                        val cutoff = policy.endOfBookingDay(selected)
                        if (selected.after(cutoff)) {
                            showToast("Bookings must end by ${BookingPolicyResolver.formatTime(policy.dailyBookingEndMinutes)}.")
                            return@TimePickerDialog
                        }
                        if (selectingStart) {
                            if (selected.before(now)) {
                                showToast("Start time cannot be in the past.")
                                return@TimePickerDialog
                            }
                            viewModel.setStartTime(selected.time)
                            val currentEnd = viewModel.endTime.value
                            if (currentEnd == null || currentEnd.time <= selected.timeInMillis) {
                                val end = (selected.clone() as Calendar).apply { add(Calendar.HOUR_OF_DAY, 2) }
                                val endCutoff = policy.endOfBookingDay(end)
                                if (end.after(endCutoff)) end.timeInMillis = endCutoff.timeInMillis
                                viewModel.setEndTime(end.time)
                            }
                        } else {
                            val start = viewModel.startTime.value
                            if (start == null || selected.timeInMillis <= start.time) {
                                showToast("Checkout must be after check-in.")
                                return@TimePickerDialog
                            }
                            if (!policy.allowOvernightBookings) {
                                val startDay = Calendar.getInstance().apply { time = start }
                                if (startDay.get(Calendar.YEAR) != selected.get(Calendar.YEAR) ||
                                    startDay.get(Calendar.DAY_OF_YEAR) != selected.get(Calendar.DAY_OF_YEAR)
                                ) {
                                    showToast("This parking lot does not allow overnight bookings.")
                                    return@TimePickerDialog
                                }
                            }
                            viewModel.setEndTime(selected.time)
                        }
                    },
                    current.get(Calendar.HOUR_OF_DAY),
                    current.get(Calendar.MINUTE),
                    false,
                ).show()
            },
            current.get(Calendar.YEAR),
            current.get(Calendar.MONTH),
            current.get(Calendar.DAY_OF_MONTH),
        )
        dialog.datePicker.minDate = (now.clone() as Calendar).apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
        dialog.datePicker.maxDate = latest.timeInMillis
        dialog.show()
    }

    private fun calculatePricing() {
        viewModel.calculatePricing()
    }

    private fun proceedToPayment() {
        val intent = Intent(this, PaymentActivity::class.java)
        intent.putExtra("PARKING_SPOT_ID", parkingSpot?.id)
        intent.putExtra("START_TIME", viewModel.startTime.value?.time)
        intent.putExtra("END_TIME", viewModel.endTime.value?.time)
        intent.putExtra("TOTAL_PRICE", viewModel.totalPrice.value)
        intent.putExtra("SELECTED_SPOT", viewModel.selectedSpot.value)
        startActivity(intent)
    }
    
    private fun createBooking() {
        val policy = bookingPolicy ?: run {
            showToast("Parking rules are unavailable. Please try again.")
            return
        }
        val selectedVehicle = viewModel.selectedVehicle.value
        
        if (policy.requiresVehicleRegistration && selectedVehicle == null) {
            showToast(getString(R.string.please_select_a_vehicle))
            return
        }
        
        viewModel.setVehicleNumber(selectedVehicle?.number.orEmpty())
        viewModel.createBackendBooking()
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
        android.widget.Toast.makeText(this, message, android.widget.Toast.LENGTH_SHORT).show()
    }
}
