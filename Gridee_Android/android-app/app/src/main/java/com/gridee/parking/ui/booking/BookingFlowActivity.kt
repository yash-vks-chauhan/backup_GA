package com.gridee.parking.ui.booking

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import android.app.AlertDialog
import android.view.View
import android.widget.EditText
import com.gridee.parking.data.model.ParkingSpot
import com.gridee.parking.utils.AuthSession
import com.gridee.parking.databinding.ActivityBookingFlowBinding
import java.text.SimpleDateFormat
import java.util.*

class BookingFlowActivity : AppCompatActivity() {

    private lateinit var binding: ActivityBookingFlowBinding
    private lateinit var viewModel: BookingViewModel
    private var parkingSpot: ParkingSpot? = null
    private var selectedLotId: String = ""
    private var selectedLotName: String = ""
    private var selectedSpotId: String = ""

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
        loadParkingSpot(selectedSpotId)
    }
    
    override fun onResume() {
        super.onResume()
        // Refresh vehicles when returning to this activity (e.g., from profile)
        viewModel.loadUserVehicles()
        // Refresh wallet balance when returning (e.g., after adding money)
        viewModel.loadWalletBalance()
    }

    private fun setupUI() {
        binding.tvTitle.text = "Book Parking"
        
        // Set default times based on current time and backend business rules
        val calendar = Calendar.getInstance()
        val currentHour = calendar.get(Calendar.HOUR_OF_DAY)
        
        // Backend rule: "Bookings after 8 pm are only allowed for tomorrow"
        // This means: 8 PM - 11:59 PM = must book for next day
        // After midnight (12 AM onwards) = can book for current day again
        if (currentHour >= 20) { // 8 PM to 11:59 PM
            // Set start time to tomorrow at 9 AM
            calendar.add(Calendar.DAY_OF_MONTH, 1)
            calendar.set(Calendar.HOUR_OF_DAY, 9)
            calendar.set(Calendar.MINUTE, 0)
            calendar.set(Calendar.SECOND, 0)
            viewModel.setStartTime(calendar.time)
            
            // Set end time to 2 hours later
            calendar.add(Calendar.HOUR_OF_DAY, 2)
            viewModel.setEndTime(calendar.time)
        } else {
            // Before 8 PM or after midnight (12 AM - 7:59 AM): can book for current day
            // For very early hours (12 AM - 6 AM), set a reasonable start time
            if (currentHour < 6) {
                calendar.set(Calendar.HOUR_OF_DAY, 9)
                calendar.set(Calendar.MINUTE, 0)
                calendar.set(Calendar.SECOND, 0)
            }
            // Otherwise use current time
            
            viewModel.setStartTime(calendar.time)
            calendar.add(Calendar.HOUR_OF_DAY, 2)
            viewModel.setEndTime(calendar.time)
        }
    }

    private fun setupClickListeners() {
        binding.btnBack.setOnClickListener {
            finish()
        }

        binding.cardStartTime.setOnClickListener {
            showDateTimePicker(true) // true for start time
        }

        binding.cardEndTime.setOnClickListener {
            showDateTimePicker(false) // false for end time
        }

        binding.btnSelectSpot.setOnClickListener {
            showSpotSelectionDialog()
        }
        
        binding.cardVehicleSelection.setOnClickListener {
            showVehicleSelectionDialog()
        }

        binding.btnContinueToPayment.setOnClickListener {
            createBooking()
        }
        
        binding.btnAddMoney.setOnClickListener {
            showAddMoneyDialog()
        }
    }

    private fun setupObservers() {
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
            binding.tvTotalPrice.text = "₹${String.format(Locale.getDefault(), "%.2f", price)}"
        }

        viewModel.duration.observe(this) { duration ->
            binding.tvDuration.text = duration
        }
        
        viewModel.selectedVehicle.observe(this) { vehicle ->
            binding.tvSelectedVehicle.text = vehicle?.number ?: "Select your vehicle"
        }
        
        viewModel.walletBalance.observe(this) { balance ->
            binding.tvWalletBalance.text = "₹${String.format(Locale.getDefault(), "%.2f", balance)}"
        }
        
        // Backend integration observers
        viewModel.isLoading.observe(this) { isLoading ->
            binding.btnContinueToPayment.isEnabled = !isLoading
            // Note: Since btnContinueToPayment is a CardView with TextView, 
            // we can't directly set text. The button text stays as "Continue to Payment"
        }
        
        viewModel.bookingCreated.observe(this) { booking ->
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
                    putExtra("PAYMENT_METHOD", "Wallet")
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
                if (spot != null) {
                    parkingSpot = spot
                } else {
                    // Fallback: create spot with the data we have
                    parkingSpot = ParkingSpot(
                        id = spotId,
                        lotId = selectedLotId,
                        spotCode = spotId,
                        name = "Selected Spot",
                        zoneName = "Unknown Spot",
                        capacity = 0,
                        available = 0,
                        status = "unknown"
                    )
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
            calculatePricing()
            viewModel.loadUserVehicles()
        }
    }

    private fun updateSelectedSpotDisplay(spot: String?) {
        // Display the selected spot name or default to "Any available spot"
        binding.tvSelectedSpot.text = spot ?: "Any available spot"
        println("BookingFlowActivity: updateSelectedSpotDisplay called with: '$spot', set text to: '${binding.tvSelectedSpot.text}'")
    }

    private fun showDateTimePicker(isStartTime: Boolean) {
        val calendar = Calendar.getInstance()
        val currentTime = if (isStartTime) viewModel.startTime.value else viewModel.endTime.value
        currentTime?.let { calendar.time = it }

        DatePickerDialog(
            this,
            { _, year, month, dayOfMonth ->
                calendar.set(year, month, dayOfMonth)
                
                TimePickerDialog(
                    this,
                    { _, hourOfDay, minute ->
                        calendar.set(Calendar.HOUR_OF_DAY, hourOfDay)
                        calendar.set(Calendar.MINUTE, minute)
                        
                        if (isStartTime) {
                            viewModel.setStartTime(calendar.time)
                        } else {
                            viewModel.setEndTime(calendar.time)
                        }
                    },
                    calendar.get(Calendar.HOUR_OF_DAY),
                    calendar.get(Calendar.MINUTE),
                    false
                ).show()
            },
            calendar.get(Calendar.YEAR),
            calendar.get(Calendar.MONTH),
            calendar.get(Calendar.DAY_OF_MONTH)
        ).show()
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
        val spotAdapter = ParkingSpotSelectionAdapter { spot ->
            selectedSpot = spot
            isAnySpotSelected = false
            updateSpotSelection(ivAnySpotSelected, false)
            showToast("Selected spot: ${spot.name ?: spot.zoneName ?: spot.spotCode ?: spot.id}")
        }
        
        recyclerView.layoutManager = androidx.recyclerview.widget.LinearLayoutManager(this)
        recyclerView.adapter = spotAdapter
        
        // Load parking spots for current lot
        showProgress(progressBar, recyclerView, emptyState, true)
        
        // Debug: Check what lot ID we're using
        println("BookingFlowActivity: Loading spots for lot ID: '$selectedLotId'")
        showToast("Loading spots for lot: $selectedLotId")
        
        // Use the repository directly like the discovery screen does
        lifecycleScope.launch {
            try {
                val parkingRepository = com.gridee.parking.data.repository.ParkingRepository()
                val start = viewModel.startTime.value ?: java.util.Calendar.getInstance().time
                val end = viewModel.endTime.value ?: java.util.Calendar.getInstance().apply { add(java.util.Calendar.HOUR_OF_DAY, 2) }.time
                val df = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX", java.util.Locale.getDefault())
                val startStr = df.format(start)
                val endStr = df.format(end)
                val spotsResponse = if (selectedLotId.isNotEmpty()) {
                    parkingRepository.getAvailableSpots(selectedLotId, startStr, endStr)
                } else {
                    retrofit2.Response.success(emptyList<com.gridee.parking.data.model.ParkingSpot>())
                }
                
                runOnUiThread {
                    if (spotsResponse.isSuccessful) {
                        val filteredSpots = spotsResponse.body()?.filter { it.available > 0 } ?: emptyList()
                        
                        showToast("Filtered spots for lot '$selectedLotId': ${filteredSpots.size}")
                        println("BookingFlowActivity: Received ${filteredSpots.size} spots for lot $selectedLotId")
                        
                        showProgress(progressBar, recyclerView, emptyState, false)
                        
                        if (filteredSpots.isNotEmpty()) {
                            showToast("Setting ${filteredSpots.size} spots to adapter")
                            spotAdapter.submitList(filteredSpots)
                            recyclerView.visibility = android.view.View.VISIBLE
                            emptyState.visibility = android.view.View.GONE
                            
                            // Force layout update
                            recyclerView.requestLayout()
                            
                            // Debug adapter state
                            android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                                showToast("Adapter item count: ${spotAdapter.itemCount}")
                            }, 500)
                            
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
            showToast("Selected: Any available spot")
        }
        
        // Cancel button
        btnCancel.setOnClickListener {
            dialog.dismiss()
        }
        
        // Select button
        btnSelect.setOnClickListener {
            println("BookingFlowActivity: Select button clicked. isAnySpotSelected: $isAnySpotSelected, selectedSpot: ${selectedSpot?.id}")
            
            if (isAnySpotSelected) {
                // User chose "Any available spot"
                showToast("Applying: Any available spot")
                binding.tvSelectedSpot.text = "Any available spot"
                viewModel.setSelectedSpot(null)
            } else {
                selectedSpot?.let { spot ->
                    // User chose a specific spot
                    val spotName = spot.name ?: spot.zoneName ?: "Selected Spot"
                    showToast("Applying: $spotName")
                    
                    // Debug: Check what we're setting
                    println("BookingFlowActivity: Setting spot text to: '$spotName'")
                    binding.tvSelectedSpot.text = spotName
                    
                    // Force UI update
                    binding.tvSelectedSpot.requestLayout()
                    binding.tvSelectedSpot.invalidate()
                    
                    // Debug: Check what was actually set
                    println("BookingFlowActivity: Spot text after setting: '${binding.tvSelectedSpot.text}'")
                    
                    // Debug: Check text after a delay to see if something overwrites it
                    android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                        println("BookingFlowActivity: Spot text after 500ms: '${binding.tvSelectedSpot.text}'")
                    }, 500)
                    
                    viewModel.setSelectedSpot(spotName)
                } ?: run {
                    showToast("No specific spot selected, using Any available spot")
                    println("BookingFlowActivity: No spot selected, falling back to Any available spot")
                    binding.tvSelectedSpot.text = "Any available spot"
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
                println("BookingFlowActivity: Attempting to add vehicle: $newVehicleNumber")
                // After adding vehicle, reload the vehicles list and reopen selection dialog
                viewModel.addVehicleToProfile(newVehicleNumber) { success ->
                    println("BookingFlowActivity: Add vehicle result: $success")
                    runOnUiThread {
                        if (success) {
                            showToast("Vehicle added successfully!")
                            viewModel.loadUserVehicles() // Refresh the list
                            // Reopen the selection dialog after a short delay
                            android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                                showVehicleSelectionDialog()
                            }, 500)
                        } else {
                            showToast("Failed to add vehicle. Please check your connection.")
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
                showToast("Please select a vehicle")
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
                showToast("Please enter a vehicle number")
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
        val selectedVehicle = viewModel.selectedVehicle.value
        
        if (selectedVehicle == null) {
            showToast("Please select a vehicle")
            return
        }
        
        viewModel.setVehicleNumber(selectedVehicle.number)
        viewModel.createBackendBooking()
    }
    
    private fun showAddMoneyDialog() {
        val dialogView = layoutInflater.inflate(android.R.layout.select_dialog_item, null)
        val input = EditText(this)
        input.hint = "Enter amount (e.g., 500)"
        input.inputType = android.text.InputType.TYPE_CLASS_NUMBER or android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL
        
        val builder = AlertDialog.Builder(this)
        builder.setTitle("Add Money to Wallet")
        builder.setMessage("Enter the amount you want to add to your wallet:")
        builder.setView(input)
        
        builder.setPositiveButton("Add") { _, _ ->
            val amountText = input.text.toString().trim()
            if (amountText.isNotEmpty()) {
                try {
                    val amount = amountText.toDouble()
                    if (amount > 0) {
                        initiateWalletTopUp(amount)
                    } else {
                        showToast("Please enter a valid amount")
                    }
                } catch (e: NumberFormatException) {
                    showToast("Invalid amount format")
                }
            } else {
                showToast("Please enter an amount")
            }
        }
        
        builder.setNegativeButton("Cancel", null)
        builder.show()
    }
    
    private fun initiateWalletTopUp(amount: Double) {
        val userId = AuthSession.getUserId(this)
        
        if (userId == null) {
            showToast("Please log in to add money")
            return
        }
        
        lifecycleScope.launch {
            try {
                // Create a Razorpay order via the backend
                val response = com.gridee.parking.data.api.ApiClient.apiService.initiatePayment(
                    com.gridee.parking.data.model.PaymentInitiateRequest(
                        userId = userId,
                        amount = amount
                    )
                )
                
                if (response.isSuccessful) {
                    val result = response.body()
                    if (result != null) {
                        // Navigate to WalletTopUpActivity with order details
                        val intent = Intent(this@BookingFlowActivity, com.gridee.parking.ui.wallet.WalletTopUpActivity::class.java).apply {
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
        android.widget.Toast.makeText(this, message, android.widget.Toast.LENGTH_SHORT).show()
    }
}
