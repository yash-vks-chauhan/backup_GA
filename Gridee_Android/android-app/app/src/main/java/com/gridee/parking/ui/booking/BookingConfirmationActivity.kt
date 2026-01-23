package com.gridee.parking.ui.booking

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModelProvider
import com.gridee.parking.R
import com.gridee.parking.databinding.ActivityBookingConfirmationBinding
import com.gridee.parking.ui.components.CustomBottomNavigation
import com.gridee.parking.ui.main.MainContainerActivity
import java.text.SimpleDateFormat
import java.util.*

class BookingConfirmationActivity : AppCompatActivity() {

    private lateinit var binding: ActivityBookingConfirmationBinding
    private lateinit var viewModel: BookingConfirmationViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityBookingConfirmationBinding.inflate(layoutInflater)
        setContentView(binding.root)

        viewModel = ViewModelProvider(this)[BookingConfirmationViewModel::class.java]

        getBookingDataFromIntent()
        setupUI()
        setupClickListeners()
        setupObservers()
    }

    private fun getBookingDataFromIntent() {
        val bookingId = intent.getStringExtra("BOOKING_ID").orEmpty()
        val transactionId = intent.getStringExtra("TRANSACTION_ID").orEmpty()
        val parkingName = intent.getStringExtra("PARKING_NAME").orEmpty()
        val parkingAddress = intent.getStringExtra("PARKING_ADDRESS").orEmpty()
        val selectedSpot = intent.getStringExtra("SELECTED_SPOT")
        val vehicleNumber = intent.getStringExtra("VEHICLE_NUMBER")
        val startTime = intent.getLongExtra("START_TIME", System.currentTimeMillis())
        val endTime = intent.getLongExtra("END_TIME", startTime + 60 * 60 * 1000)
        val totalAmount = intent.getDoubleExtra("TOTAL_AMOUNT", 0.0)
        val paymentMethod = intent.getStringExtra("PAYMENT_METHOD") ?: "Wallet"
        val paymentStatus = intent.getStringExtra("PAYMENT_STATUS") ?: "Pending"
        val timestamp = intent.getLongExtra("BOOKING_TIMESTAMP", System.currentTimeMillis())

        viewModel.setBookingDetails(
            BookingConfirmationDetails(
                bookingId = bookingId.ifBlank { "--" },
                transactionId = transactionId,
                parkingSpotName = parkingName.ifBlank { "Parking Location" },
                parkingAddress = parkingAddress.ifBlank { "Not available" },
                selectedSpot = selectedSpot,
                vehicleNumber = vehicleNumber,
                startTime = startTime,
                endTime = endTime,
                totalAmount = totalAmount,
                paymentMethodDisplay = paymentMethod,
                paymentStatus = paymentStatus,
                timestamp = timestamp
            )
        )
    }

    private fun setupUI() {
        binding.tvTitle.text = "Booking Confirmed"
    }

    private fun setupClickListeners() {
        binding.btnBack.setOnClickListener {
            navigateToBookings(showPending = true)
        }

        binding.btnDone.setOnClickListener {
            navigateToBookings(showPending = true)
        }

        binding.btnViewBooking.setOnClickListener {
            val bookingId = viewModel.bookingDetails.value?.bookingId
                ?.takeIf { it.isNotBlank() && it != "--" }
            navigateToBookings(showPending = true, bookingId = bookingId)
        }

        binding.btnShareReceipt.setOnClickListener {
            shareBookingReceipt()
        }
    }

    private fun setupObservers() {
        viewModel.bookingDetails.observe(this) { details ->
            updateBookingDisplay(details)
        }

        viewModel.isLoading.observe(this) { isLoading ->
            // Handle loading state if needed
        }
    }

    private fun updateBookingDisplay(details: BookingConfirmationDetails) {
        // Update confirmation details
        binding.tvBookingId.text = "Booking ID: ${details.bookingId}"
        if (details.transactionId.isNotBlank()) {
            binding.tvTransactionId.visibility = View.VISIBLE
            binding.tvTransactionId.text = "Transaction ID: ${details.transactionId}"
        } else {
            binding.tvTransactionId.visibility = View.GONE
        }
        
        // Update parking details
        binding.tvParkingSpotName.text = details.parkingSpotName
        if (details.parkingAddress.isBlank() || details.parkingAddress.equals("Not available", true)) {
            binding.tvParkingAddress.visibility = View.GONE
        } else {
            binding.tvParkingAddress.visibility = View.VISIBLE
            binding.tvParkingAddress.text = details.parkingAddress
        }
        binding.tvSelectedSpot.text = details.selectedSpot ?: "Any available spot"
        if (!details.vehicleNumber.isNullOrBlank()) {
            binding.rowVehicle.visibility = View.VISIBLE
            binding.tvVehicleNumber.text = details.vehicleNumber
        } else {
            binding.rowVehicle.visibility = View.GONE
        }
        
        // Update time details
        val dateFormat = SimpleDateFormat("MMM dd, yyyy", Locale.getDefault())
        val timeFormat = SimpleDateFormat("hh:mm a", Locale.getDefault())

        if (details.startTime > 0) {
            val startDate = Date(details.startTime)
            binding.tvParkingDate.text = dateFormat.format(startDate)
            binding.tvStartTime.text = timeFormat.format(startDate)
        } else {
            binding.tvParkingDate.text = "--"
            binding.tvStartTime.text = "--"
        }

        if (details.endTime > 0) {
            binding.tvEndTime.text = timeFormat.format(Date(details.endTime))
        } else {
            binding.tvEndTime.text = "--"
        }
        
        // Calculate and display duration
        val durationMillis = (details.endTime - details.startTime).coerceAtLeast(0)
        val durationHours = durationMillis / (1000 * 60 * 60)
        val durationMinutes = (durationMillis % (1000 * 60 * 60)) / (1000 * 60)

        if (durationHours > 0) {
            binding.tvDuration.text = "${durationHours}h ${durationMinutes}m"
        } else {
            binding.tvDuration.text = "${durationMinutes}m"
        }
        
        // Update payment details
        binding.tvTotalAmount.text = "₹${String.format(Locale.getDefault(), "%.2f", details.totalAmount)}"
        binding.tvPaymentMethod.text = details.paymentMethodDisplay
        
        var statusDisplay = details.paymentStatus.ifBlank { "Pending" }
            .replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() }

        // Logic: specific to user request "for payment in wallet shows success"
        if (details.paymentMethodDisplay.contains("Wallet", ignoreCase = true) && 
           (statusDisplay.equals("Paid", ignoreCase = true) || statusDisplay.equals("Confirmed", ignoreCase = true))) {
            statusDisplay = "Success"
        }

        binding.tvPaymentStatus.text = statusDisplay
        val statusColor = if (statusDisplay.equals("Paid", true) ||
            statusDisplay.equals("Confirmed", true) ||
            statusDisplay.equals("Success", true)
        ) {
            ContextCompat.getColor(this, R.color.success_green)
        } else {
            ContextCompat.getColor(this, R.color.brand_primary)
        }
        binding.tvPaymentStatus.setTextColor(statusColor)
        
        // Update timestamp
        if (details.timestamp > 0) {
            val timestampFormat = SimpleDateFormat("MMM dd, yyyy 'at' hh:mm a", Locale.getDefault())
            binding.tvBookingTime.visibility = View.VISIBLE
            binding.tvBookingTime.text = "Booked on ${timestampFormat.format(Date(details.timestamp))}"
        } else {
            binding.tvBookingTime.visibility = View.GONE
        }
    }

    private fun shareBookingReceipt() {
        val details = viewModel.bookingDetails.value ?: return
        
        val statusDisplay = details.paymentStatus.ifBlank { "Pending" }
            .replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() }

        val shareText = buildString {
            appendLine("🅿️ Parking Booking Confirmed")
            appendLine()
            appendLine("Booking ID: ${details.bookingId}")
            appendLine("Location: ${details.parkingSpotName}")
            appendLine("Address: ${details.parkingAddress}")
            if (!details.vehicleNumber.isNullOrBlank()) {
                appendLine("Vehicle: ${details.vehicleNumber}")
            }
            appendLine()
            val dateFormat = SimpleDateFormat("MMM dd, yyyy 'at' hh:mm a", Locale.getDefault())
            appendLine("Start: ${dateFormat.format(Date(details.startTime))}")
            appendLine("End: ${dateFormat.format(Date(details.endTime))}")
            appendLine()
            appendLine("Amount: ₹${String.format(Locale.getDefault(), "%.2f", details.totalAmount)}")
            appendLine("Payment: ${details.paymentMethodDisplay}")
            appendLine("Status: $statusDisplay")
            appendLine()
            appendLine("Powered by Gridee Parking")
        }
        
        val shareIntent = Intent().apply {
            action = Intent.ACTION_SEND
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, shareText)
            putExtra(Intent.EXTRA_SUBJECT, "Parking Booking Receipt")
        }
        
        startActivity(Intent.createChooser(shareIntent, "Share booking receipt"))
    }

    private fun navigateToBookings(showPending: Boolean, bookingId: String? = null) {
        val intent = Intent(this, MainContainerActivity::class.java).apply {
            putExtra(MainContainerActivity.EXTRA_TARGET_TAB, CustomBottomNavigation.TAB_BOOKINGS)
            putExtra(MainContainerActivity.EXTRA_SHOW_PENDING, showPending)
            bookingId?.let { putExtra(MainContainerActivity.EXTRA_HIGHLIGHT_BOOKING_ID, it) }
            addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        }
        startActivity(intent)
        finish()
    }
}
