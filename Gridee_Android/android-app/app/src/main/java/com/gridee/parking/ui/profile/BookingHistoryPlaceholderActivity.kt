package com.gridee.parking.ui.profile

import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.gridee.parking.R
import com.gridee.parking.data.api.ApiClient
import com.gridee.parking.data.repository.BookingRepository
import com.gridee.parking.data.repository.ParkingRepository
import com.gridee.parking.databinding.ActivityBookingHistoryPlaceholderBinding
import com.gridee.parking.ui.adapters.Booking as UiBooking
import com.gridee.parking.ui.adapters.BookingStatus
import com.gridee.parking.ui.adapters.BookingsAdapter
import com.gridee.parking.ui.base.BaseActivity
import com.gridee.parking.ui.bookings.BookingDetailsActivity
import com.gridee.parking.data.model.Booking as BackendBooking
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone

class BookingHistoryPlaceholderActivity : BaseActivity<ActivityBookingHistoryPlaceholderBinding>() {

    private lateinit var bookingsAdapter: BookingsAdapter
    private val bookingRepository by lazy { BookingRepository() }
    private val parkingRepository by lazy { ParkingRepository() }
    private val istTimeZone: TimeZone = TimeZone.getTimeZone("Asia/Kolkata")
    private val weekdayShortDateFormat = SimpleDateFormat("dd MMM", Locale.getDefault())
    private val longDateFormat = SimpleDateFormat("dd MMM yyyy", Locale.getDefault())
    private val timeFormat = SimpleDateFormat("h:mm a", Locale.getDefault())
    private val bookingSectionTitles = mutableMapOf<String, String>()
    private val parkingLotCache = mutableMapOf<String, String>()
    private val parkingSpotCache = mutableMapOf<String, String>()
    private var isCacheLoaded = false

    override fun getViewBinding(): ActivityBookingHistoryPlaceholderBinding {
        return ActivityBookingHistoryPlaceholderBinding.inflate(layoutInflater)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        window.statusBarColor = ContextCompat.getColor(this, R.color.background_primary)
        val isNightMode = resources.configuration.uiMode and
            android.content.res.Configuration.UI_MODE_NIGHT_MASK ==
            android.content.res.Configuration.UI_MODE_NIGHT_YES
        WindowInsetsControllerCompat(window, window.decorView).isAppearanceLightStatusBars = !isNightMode

        binding.btnBack.setOnClickListener { finish() }

        setupFrostedToolbar()
        setupRecyclerView()
        loadBookingHistory()
    }

    private fun setupFrostedToolbar() {
        val frostView = binding.viewToolbarFrost
        val titleView = binding.tvHeaderTitle

        // Start state — fully clear, title slightly soft
        frostView.alpha = 0f
        titleView.alpha = 0.88f

        // Content top padding — push below the floating header
        binding.layoutHeader.post {
            binding.contentContainer.setPadding(0, binding.layoutHeader.height + (4f.dpToPx()).toInt(), 0, 0)
        }

        binding.scrollContent.setOnScrollChangeListener { _, _, scrollY, _, _ ->
            updateFrostedHeader(scrollY)
        }
    }

    private fun setupRecyclerView() {
        bookingsAdapter = BookingsAdapter(
            emptyList(),
            onBookingClick = { booking ->
                openBookingDetails(booking)
            },
            onExtendClick = { _ ->
                // Extend is not applicable for history items
            },
            useCompactHistory = true,
            historySectionProvider = { booking ->
                bookingSectionTitles[booking.id] ?: ""
            }
        )

        binding.rvBookingHistory.apply {
            layoutManager = LinearLayoutManager(this@BookingHistoryPlaceholderActivity)
            adapter = bookingsAdapter
        }
    }

    private fun openBookingDetails(booking: UiBooking) {
        if (booking.id.isBlank() || booking.id == "Unknown") {
            Toast.makeText(this, "Booking ID not available", Toast.LENGTH_SHORT).show()
            return
        }
        val intent = android.content.Intent(this, BookingDetailsActivity::class.java)
        intent.putExtra(BookingDetailsActivity.EXTRA_BOOKING_ID, booking.id)
        startActivity(intent)
    }

    private fun loadBookingHistory() {
        showLoading(true)

        lifecycleScope.launch {
            if (!isCacheLoaded) {
                loadParkingDataCache()
            }
            val result = bookingRepository.getUserBookingHistory()
            result.onSuccess { bookings ->
                val sorted = bookings.sortedByDescending { getComparableTimestamp(it) }
                bookingSectionTitles.clear()
                val uiBookings = sorted.map { backend ->
                    val ui = convertToUiBooking(backend)
                    bookingSectionTitles[ui.id] = sectionTitleFor(backend)
                    ui
                }

                bookingsAdapter.updateBookings(uiBookings)
                showLoading(false)
                showEmptyState(uiBookings.isEmpty())
                if (uiBookings.isNotEmpty()) {
                    binding.rvBookingHistory.scheduleLayoutAnimation()
                }
            }.onFailure { error ->
                showLoading(false)
                showEmptyState(true)
                Toast.makeText(
                    this@BookingHistoryPlaceholderActivity,
                    error.message ?: "Failed to load booking history",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    private fun showLoading(show: Boolean) {
        binding.progressLoading.visibility = if (show) View.VISIBLE else View.GONE
        if (show) {
            binding.rvBookingHistory.visibility = View.GONE
            binding.layoutEmptyState.visibility = View.GONE
            updateFrostedHeader(0)
        }
    }

    private fun showEmptyState(show: Boolean) {
        binding.layoutEmptyState.visibility = if (show) View.VISIBLE else View.GONE
        binding.rvBookingHistory.visibility = if (show) View.GONE else View.VISIBLE
        if (show) {
            updateFrostedHeader(0)
        } else {
            updateFrostedHeader(binding.scrollContent.scrollY)
        }
    }

    private fun updateFrostedHeader(scrollOffsetPx: Int) {
        val deadZone = 16f.dpToPx()
        val activeScroll = (scrollOffsetPx - deadZone).coerceAtLeast(0f)

        val frostRange = 120f.dpToPx()
        val rawFrost = (activeScroll / frostRange).coerceIn(0f, 1f)
        val t = 1f - rawFrost
        binding.viewToolbarFrost.alpha = 1f - (t * t * t)

        val titleRange = 80f.dpToPx()
        val rawTitle = (activeScroll / titleRange).coerceIn(0f, 1f)
        binding.tvHeaderTitle.alpha = 0.88f + (0.12f * rawTitle)

        if (scrollOffsetPx <= 0) {
            binding.viewToolbarFrost.alpha = 0f
            binding.tvHeaderTitle.alpha = 0.88f
        }
    }

    private fun convertToUiBooking(backendBooking: BackendBooking): UiBooking {
        val status = mapBackendStatus(backendBooking.status)
        val statusLabelOverride = getStatusLabelOverride(backendBooking.status)
        val checkInTime = backendBooking.actualCheckInTime
            ?: backendBooking.checkInTime
            ?: backendBooking.createdAt
        val checkOutTime = backendBooking.checkOutTime

        val bookingDate = getHistoryDisplayDate(backendBooking)?.let { formatRelativeDate(it) } ?: "TBD"
        val startTime = checkInTime?.let { timeFormat.format(it) } ?: "TBD"
        val endTime = checkOutTime?.let { timeFormat.format(it) } ?: "TBD"

        val duration = when {
            checkInTime == null -> "TBD"
            status == BookingStatus.ACTIVE -> formatDuration(System.currentTimeMillis() - checkInTime.time)
            checkOutTime != null -> formatDuration(checkOutTime.time - checkInTime.time)
            else -> "TBD"
        }

        val rawSpotId: String? = backendBooking.spotId
        val rawLotId: String? = backendBooking.lotId
        val spotLabel = getSpotLabel(rawSpotId)
        // Prefer the lot name embedded in the booking response; fall back to cache.
        val lotLabel = backendBooking.lotName
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
            ?: getLotLabel(rawLotId)
        val amountText = String.format(Locale.getDefault(), "%.2f", backendBooking.amount)

        return UiBooking(
            id = backendBooking.id ?: "Unknown",
            vehicleNumber = backendBooking.vehicleNumber ?: "",
            spotId = rawSpotId ?: "",
            spotName = spotLabel,
            locationName = lotLabel,
            locationAddress = "",
            startTime = startTime,
            endTime = endTime,
            duration = duration,
            amount = amountText,
            status = status,
            bookingDate = bookingDate,
            checkInTimestamp = checkInTime?.time ?: 0L,
            checkOutTimestamp = checkOutTime?.time ?: 0L,
            statusLabelOverride = statusLabelOverride
        )
    }

    private fun getSpotLabel(spotId: String?): String {
        if (spotId.isNullOrBlank()) return "Unknown Spot"
        return parkingSpotCache[spotId] ?: spotId
    }

    private fun getLotLabel(lotId: String?): String {
        if (lotId.isNullOrBlank()) return "Unknown Lot"
        return parkingLotCache[lotId] ?: lotId
    }

    private suspend fun loadParkingDataCache() {
        try {
            // Try admin all-spots endpoint first (same approach as bookings screen)
            try {
                val allSpotsResponse = ApiClient.apiService.getParkingSpots()
                if (allSpotsResponse.isSuccessful) {
                    allSpotsResponse.body()?.forEach { spot ->
                        val spotName = spot.name ?: spot.zoneName ?: "Spot ${spot.id}"
                        parkingSpotCache[spot.id] = spotName
                    }
                }
            } catch (_: Exception) {
                // Ignore and fall back to per-lot spot loading
            }

            val lotsResponse = ApiClient.apiService.getParkingLots()
            if (lotsResponse.isSuccessful) {
                lotsResponse.body()?.forEach { lot ->
                    parkingLotCache[lot.id] = lot.name

                    try {
                        val spotsForLot = parkingRepository.getParkingSpotsByLot(lot.id)
                        if (spotsForLot.isSuccessful) {
                            spotsForLot.body()?.forEach { spot ->
                                if (!parkingSpotCache.containsKey(spot.id)) {
                                    val spotName = spot.name ?: spot.zoneName ?: "Spot ${spot.id}"
                                    parkingSpotCache[spot.id] = spotName
                                }
                            }
                        }
                    } catch (_: Exception) {
                        // Ignore per-lot spot failures
                    }
                }
            }

            isCacheLoaded = true
        } catch (_: Exception) {
            // Continue without cache
        }
    }

    private fun formatRelativeDate(date: java.util.Date): String {
        val now = java.util.Calendar.getInstance()
        val then = java.util.Calendar.getInstance().apply { time = date }

        // Normalize both to start-of-day to get calendar-day differences
        fun startOfDay(c: java.util.Calendar): java.util.Calendar {
            val copy = c.clone() as java.util.Calendar
            copy.set(java.util.Calendar.HOUR_OF_DAY, 0)
            copy.set(java.util.Calendar.MINUTE, 0)
            copy.set(java.util.Calendar.SECOND, 0)
            copy.set(java.util.Calendar.MILLISECOND, 0)
            return copy
        }

        val diffDays = ((startOfDay(now).timeInMillis - startOfDay(then).timeInMillis) /
            (1000L * 60 * 60 * 24)).toInt()

        val timeSuffix = "  ·  ${timeFormat.format(date)}"
        return when {
            diffDays == 0 -> "Today$timeSuffix"
            diffDays == 1 -> "Yesterday$timeSuffix"
            now.get(java.util.Calendar.YEAR) == then.get(java.util.Calendar.YEAR) ->
                "${weekdayShortDateFormat.format(date)}$timeSuffix"
            else -> "${longDateFormat.format(date)}$timeSuffix"
        }
    }

    private fun sectionTitleFor(booking: BackendBooking): String {
        val date = getHistoryDisplayDate(booking) ?: return "Earlier"

        val now = Calendar.getInstance(istTimeZone)
        val todayStart = startOfDay(now)
        val yesterdayStart = startOfDay(
            (now.clone() as Calendar).apply { add(Calendar.DAY_OF_YEAR, -1) }
        )
        val thisWeekStart = (now.clone() as Calendar).apply {
            add(Calendar.DAY_OF_YEAR, -7)
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }

        val bookingCal = Calendar.getInstance(istTimeZone).apply { time = date }

        return when {
            bookingCal.timeInMillis >= todayStart.timeInMillis -> "Today"
            bookingCal.timeInMillis >= yesterdayStart.timeInMillis -> "Yesterday"
            bookingCal.timeInMillis >= thisWeekStart.timeInMillis -> "This Week"
            else -> {
                val month = bookingCal.getDisplayName(Calendar.MONTH, Calendar.LONG, Locale.getDefault())
                val year = bookingCal.get(Calendar.YEAR)
                "$month $year"
            }
        }
    }

    private fun startOfDay(calendar: Calendar): Calendar {
        return (calendar.clone() as Calendar).apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
    }

    private fun getComparableTimestamp(booking: BackendBooking): Long {
        return getHistoryDisplayDate(booking)?.time ?: 0L
    }

    private fun getHistoryDisplayDate(booking: BackendBooking): java.util.Date? {
        return when (mapBackendStatus(booking.status)) {
            BookingStatus.CANCELLED -> booking.cancelledAt
                ?: booking.updatedAt
                ?: booking.archivedAt
                ?: booking.createdAt
                ?: booking.actualCheckInTime
                ?: booking.checkInTime
                ?: booking.checkOutTime

            BookingStatus.NO_SHOW -> booking.updatedAt
                ?: booking.archivedAt
                ?: booking.checkInTime
                ?: booking.createdAt
                ?: booking.checkOutTime

            BookingStatus.COMPLETED -> booking.actualCheckOutTime
                ?: booking.checkOutTime
                ?: booking.updatedAt
                ?: booking.archivedAt
                ?: booking.actualCheckInTime
                ?: booking.checkInTime
                ?: booking.createdAt

            BookingStatus.ACTIVE -> booking.actualCheckInTime
                ?: booking.checkInTime
                ?: booking.updatedAt
                ?: booking.createdAt

            BookingStatus.PENDING -> booking.checkInTime
                ?: booking.createdAt
                ?: booking.updatedAt
        }
    }

    private fun formatDuration(durationMillis: Long): String {
        val safeDuration = durationMillis.coerceAtLeast(0L)
        val hours = safeDuration / (1000 * 60 * 60)
        val minutes = (safeDuration % (1000 * 60 * 60)) / (1000 * 60)
        return if (hours > 0) "${hours}h ${minutes}m" else "${minutes}m"
    }

    private fun Float.dpToPx(): Float {
        return this * resources.displayMetrics.density
    }

    private fun mapBackendStatus(backendStatus: String?): BookingStatus {
        val normalized = backendStatus?.trim()?.lowercase(Locale.ROOT)?.replace(' ', '_')
            ?: return BookingStatus.PENDING

        return when {
            normalized.isEmpty() -> BookingStatus.PENDING

            // Explicit overrides for Cancelled/No Show must come first
            normalized.contains("cancel") -> BookingStatus.CANCELLED
            normalized.contains("no_show") || normalized.contains("noshow") -> BookingStatus.NO_SHOW

            normalized in ACTIVE_STATUSES -> BookingStatus.ACTIVE
            normalized in PENDING_STATUSES -> BookingStatus.PENDING
            normalized in COMPLETED_STATUSES -> BookingStatus.COMPLETED

            normalized.contains("check_out") || normalized.contains("checkout") ||
                normalized.contains("complete") || normalized.contains("finish") ||
                normalized.contains("expire") ||
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

    private fun getStatusLabelOverride(backendStatus: String?): String? {
        val normalized = backendStatus?.trim()?.lowercase(Locale.ROOT)?.replace(' ', '_') ?: return null
        return when {
            normalized.contains("cancel") -> "Cancelled"
            normalized.contains("no_show") || normalized.contains("noshow") -> "No Show"
            else -> null
        }
    }

    companion object {
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
}
