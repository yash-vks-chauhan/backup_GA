package com.gridee.parking.ui.bookings

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.lifecycleScope
import com.gridee.parking.R
import com.gridee.parking.data.api.ApiClient
import com.gridee.parking.data.model.Booking
import com.gridee.parking.databinding.ActivityBookingDetailsBinding
import com.gridee.parking.ui.base.BaseActivity
import com.gridee.parking.utils.AuthSession
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class BookingDetailsActivity : BaseActivity<ActivityBookingDetailsBinding>() {

    private val dateTimeFormat = SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault())
    private val parkingLotCache = mutableMapOf<String, String>()
    private val parkingSpotCache = mutableMapOf<String, String>()
    private var isCacheLoaded = false
    private var bookingId: String? = null

    override fun getViewBinding(): ActivityBookingDetailsBinding {
        return ActivityBookingDetailsBinding.inflate(layoutInflater)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        window.statusBarColor = ContextCompat.getColor(this, R.color.background_primary)
        WindowInsetsControllerCompat(window, window.decorView).isAppearanceLightStatusBars = true

        binding.btnBack.setOnClickListener { finish() }
        binding.btnRetry.setOnClickListener { bookingId?.let { loadBookingDetails(it) } }

        bookingId = intent.getStringExtra(EXTRA_BOOKING_ID)
        if (bookingId.isNullOrBlank()) {
            Toast.makeText(this, "Missing booking ID", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        loadBookingDetails(bookingId!!)
    }

    private fun loadBookingDetails(id: String) {
        showLoading(true)
        showError(false)

        lifecycleScope.launch {
            val userId = AuthSession.getUserId(this@BookingDetailsActivity)
            if (userId.isNullOrBlank()) {
                showLoading(false)
                showError(true, "Please login to view booking details")
                return@launch
            }

            try {
                val response = ApiClient.apiService.getBookingById(userId, id)
                if (response.isSuccessful) {
                    val booking = response.body()
                    if (booking != null) {
                        if (!isCacheLoaded) {
                            loadParkingDataCache()
                        }
                        renderBooking(booking)
                        showLoading(false)
                        showContent(true)
                    } else {
                        showLoading(false)
                        showError(true, "Booking not found")
                    }
                } else if (response.code() == 404) {
                    // Fallback: try booking history collection
                    val historyResponse = ApiClient.apiService.getUserBookingHistory(userId)
                    if (historyResponse.isSuccessful) {
                        val history = historyResponse.body().orEmpty()
                        val booking = history.firstOrNull { it.id == id }
                        if (booking != null) {
                            if (!isCacheLoaded) {
                                loadParkingDataCache()
                            }
                            renderBooking(booking)
                            showLoading(false)
                            showContent(true)
                        } else {
                            showLoading(false)
                            showError(true, "Booking not found in history")
                        }
                    } else {
                        showLoading(false)
                        showError(true, "Failed to load booking history (" + historyResponse.code() + ")")
                    }
                } else {
                    showLoading(false)
                    showError(true, "Failed to load booking (" + response.code() + ")")
                }
            } catch (e: Exception) {
                showLoading(false)
                showError(true, e.message ?: "Failed to load booking")
            }
        }
    }

    private fun renderBooking(booking: Booking) {
        val lotName = resolveLotName(booking)
        val spotName = resolveSpotName(booking)
        val items = mutableListOf<DetailItem>()

        items.add(DetailItem("Booking ID", booking.id ?: "—"))
        items.add(DetailItem("Status", booking.status))
        items.add(DetailItem("Amount", formatAmount(booking.amount)))
        items.add(DetailItem("User ID", booking.userId))
        items.add(DetailItem("Lot Name", lotName))
        items.add(DetailItem("Lot ID", booking.lotId))
        items.add(DetailItem("Spot Name", spotName))
        items.add(DetailItem("Spot ID", booking.spotId))
        items.add(DetailItem("Vehicle Number", booking.vehicleNumber ?: "—"))
        items.add(DetailItem("QR Code", booking.qrCode ?: "—"))
        items.add(DetailItem("QR Scanned", booking.qrCodeScanned.toString()))
        items.add(DetailItem("Check-In Time", formatDate(booking.checkInTime)))
        items.add(DetailItem("Check-Out Time", formatDate(booking.checkOutTime)))
        items.add(DetailItem("Actual Check-In Time", formatDate(booking.actualCheckInTime)))
        items.add(DetailItem("Actual Check-Out Time", formatDate(booking.actualCheckOutTime)))
        items.add(DetailItem("Created At", formatDate(booking.createdAt)))
        items.add(DetailItem("Updated At", formatDate(booking.updatedAt)))
        items.add(DetailItem("Cancelled At", formatDate(booking.cancelledAt)))
        items.add(DetailItem("Archived At", formatDate(booking.archivedAt)))
        items.add(DetailItem("Auto Completed", booking.autoCompleted?.toString() ?: "—"))
        items.add(DetailItem("Check-In Operator ID", booking.checkInOperatorId ?: "—"))
        items.add(DetailItem("Check-Out Operator ID", booking.checkOutOperatorId ?: "—"))
        items.add(DetailItem("Lot Name (Backend)", booking.lotName ?: "—"))
        items.add(DetailItem("Ending Reminder Sent", booking.endingReminderSent?.toString() ?: "—"))
        items.add(DetailItem("Ending Reminder Sent At", formatDate(booking.endingReminderSentAt)))
        items.add(DetailItem("Balance Settled", booking.balanceSettled?.toString() ?: "—"))

        val container = binding.detailsContainer
        container.removeAllViews()
        val inflater = LayoutInflater.from(this)

        items.forEachIndexed { index, item ->
            val row = inflater.inflate(R.layout.item_booking_detail_row, container, false)
            row.findViewById<TextView>(R.id.tv_label).text = item.label
            row.findViewById<TextView>(R.id.tv_value).text = item.value
            row.findViewById<View>(R.id.view_divider).visibility =
                if (index == items.lastIndex) View.GONE else View.VISIBLE
            container.addView(row)
        }
    }

    private fun resolveLotName(booking: Booking): String {
        if (!booking.lotName.isNullOrBlank()) return booking.lotName
        if (booking.lotId.isBlank()) return "—"
        return parkingLotCache[booking.lotId] ?: booking.lotId
    }

    private fun resolveSpotName(booking: Booking): String {
        if (booking.spotId.isBlank()) return "—"
        return parkingSpotCache[booking.spotId] ?: booking.spotId
    }

    private suspend fun loadParkingDataCache() {
        try {
            try {
                val allSpotsResponse = ApiClient.apiService.getParkingSpots()
                if (allSpotsResponse.isSuccessful) {
                    allSpotsResponse.body()?.forEach { spot ->
                        val spotName = spot.name ?: spot.zoneName ?: "Spot ${spot.id}"
                        parkingSpotCache[spot.id] = spotName
                    }
                }
            } catch (_: Exception) {
                // Ignore admin endpoint failures
            }

            val lotsResponse = ApiClient.apiService.getParkingLots()
            if (lotsResponse.isSuccessful) {
                lotsResponse.body()?.forEach { lot ->
                    parkingLotCache[lot.id] = lot.name

                    try {
                        val spotsForLot = ApiClient.apiService.getParkingSpotsByLot(lot.id)
                        if (spotsForLot.isSuccessful) {
                            spotsForLot.body()?.forEach { spot ->
                                if (!parkingSpotCache.containsKey(spot.id)) {
                                    val spotName = spot.name ?: spot.zoneName ?: "Spot ${spot.id}"
                                    parkingSpotCache[spot.id] = spotName
                                }
                            }
                        }
                    } catch (_: Exception) {
                        // Ignore per-lot failures
                    }
                }
            }

            isCacheLoaded = true
        } catch (_: Exception) {
            // Continue without cache
        }
    }

    private fun formatDate(date: Date?): String {
        return date?.let { dateTimeFormat.format(it) } ?: "—"
    }

    private fun formatAmount(amount: Double): String {
        return "₹${String.format(Locale.getDefault(), "%.2f", amount)}"
    }

    private fun showLoading(show: Boolean) {
        binding.progressLoading.visibility = if (show) View.VISIBLE else View.GONE
        if (show) {
            binding.scrollContent.visibility = View.GONE
            binding.layoutErrorState.visibility = View.GONE
        }
    }

    private fun showContent(show: Boolean) {
        binding.scrollContent.visibility = if (show) View.VISIBLE else View.GONE
    }

    private fun showError(show: Boolean, message: String? = null) {
        binding.layoutErrorState.visibility = if (show) View.VISIBLE else View.GONE
        if (show) {
            binding.scrollContent.visibility = View.GONE
            binding.tvErrorMessage.text = message ?: "Please try again."
        }
    }

    private data class DetailItem(val label: String, val value: String)

    companion object {
        const val EXTRA_BOOKING_ID = "extra_booking_id"
    }
}
