package com.gridee.parking.ui.bookings

import android.animation.ValueAnimator
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.view.HapticFeedbackConstants
import android.view.View
import android.view.animation.PathInterpolator
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.lifecycleScope
import com.gridee.parking.R
import com.gridee.parking.data.api.ApiClient
import com.gridee.parking.data.model.Booking
import com.gridee.parking.data.model.BookingPayloadParser
import com.gridee.parking.data.repository.ParkingRepository
import com.gridee.parking.databinding.ActivityBookingDetailsBinding
import com.gridee.parking.ui.base.BaseActivity
import com.gridee.parking.utils.AuthSession
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

class BookingDetailsActivity : BaseActivity<ActivityBookingDetailsBinding>() {

    private val timeFormat = SimpleDateFormat("h:mm a", Locale.getDefault())
    private val dateFormat = SimpleDateFormat("EEE, d MMM", Locale.getDefault())
    private val bookedOnFormat = SimpleDateFormat("EEE, d MMM · h:mm a", Locale.getDefault())
    private val bookedDateFormat = SimpleDateFormat("EEE, d MMM", Locale.getDefault())
    private val bookedTimeFormat = SimpleDateFormat("h:mm a", Locale.getDefault())
    private val deadZonePx by lazy { 16f * resources.displayMetrics.density }
    private val frostRangePx by lazy { 120f * resources.displayMetrics.density }
    private val titleRangePx by lazy { 80f * resources.displayMetrics.density }
    private val parkingLotCache = mutableMapOf<String, String>()
    private val parkingSpotCache = mutableMapOf<String, String>()
    private val parkingRepository = ParkingRepository()
    private var isCacheLoaded = false
    private var bookingId: String? = null
    private var entryAnimated = false
    private var pendingFillRatio: Float = 0f

    private val easeOut = PathInterpolator(0.16f, 1f, 0.3f, 1f)
    private val easeInOut = PathInterpolator(0.4f, 0f, 0.2f, 1f)
    private var activePulseAnimator: ValueAnimator? = null

    override fun getViewBinding(): ActivityBookingDetailsBinding {
        return ActivityBookingDetailsBinding.inflate(layoutInflater)
    }

    override fun onDestroy() {
        activePulseAnimator?.cancel()
        activePulseAnimator = null
        super.onDestroy()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        window.statusBarColor = ContextCompat.getColor(this, R.color.background_primary)
        val isNightMode = resources.configuration.uiMode and
            android.content.res.Configuration.UI_MODE_NIGHT_MASK ==
            android.content.res.Configuration.UI_MODE_NIGHT_YES
        WindowInsetsControllerCompat(window, window.decorView).isAppearanceLightStatusBars = !isNightMode

        binding.btnBack.setOnClickListener { v ->
            v.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
            playBackPress(v) { finish() }
        }
        binding.btnRetry.setOnClickListener { v ->
            v.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
            bookingId?.let { loadBookingDetails(it) }
        }

        binding.viewToolbarFrost.alpha = 0f
        binding.tvHeaderTitle.alpha = 0f

        binding.layoutHeader.post {
            val headerHeight = binding.layoutHeader.height
            val currentPadding = binding.contentContainer.paddingStart
            binding.contentContainer.setPadding(
                currentPadding,
                headerHeight + (24f * resources.displayMetrics.density).toInt(),
                currentPadding,
                0
            )
        }

        binding.scrollContent.setOnScrollChangeListener { _, _, scrollY, _, _ ->
            updateFrostedHeader(scrollY)
        }

        prepareEntrance()

        bookingId = intent.getStringExtra(EXTRA_BOOKING_ID)
        if (bookingId.isNullOrBlank()) {
            Toast.makeText(this, "Missing booking ID", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        loadBookingDetails(bookingId!!)
    }

    private fun prepareEntrance() {
        val initialOffset = 10f * resources.displayMetrics.density
        listOf(
            binding.sectionHero,
            binding.sectionSchedule,
            binding.sectionDetails
        ).forEach { v ->
            v.alpha = 0f
            v.translationY = initialOffset
        }
        binding.scheduleFill.scaleY = 0f

        // Wipe-in elements collapse from the left
        listOf(binding.heroRule, binding.scheduleHairline, binding.detailsHairline).forEach { v ->
            v.scaleX = 0f
            v.pivotX = 0f
        }

        // Status pill pops in
        binding.pillStatus.alpha = 0f
        binding.pillStatus.scaleX = 0.92f
        binding.pillStatus.scaleY = 0.92f

        // Schedule dots quietly pop on settle
        binding.dotCheckIn.scaleX = 0.6f
        binding.dotCheckIn.scaleY = 0.6f
        binding.dotCheckIn.alpha = 0f
        binding.dotCheckOut.scaleX = 0.6f
        binding.dotCheckOut.scaleY = 0.6f
        binding.dotCheckOut.alpha = 0f
    }

    private fun playEntrance() {
        if (entryAnimated) return
        entryAnimated = true

        val sections = listOf(
            binding.sectionHero,
            binding.sectionSchedule,
            binding.sectionDetails
        )

        // Sections fade-up with editorial cascade
        sections.forEachIndexed { index, view ->
            view.animate()
                .alpha(1f)
                .translationY(0f)
                .setStartDelay(90L * index)
                .setDuration(460L)
                .setInterpolator(easeOut)
                .start()
        }

        // Status pill scales into place during the hero fade
        binding.pillStatus.animate()
            .alpha(1f).scaleX(1f).scaleY(1f)
            .setStartDelay(80L)
            .setDuration(380L)
            .setInterpolator(easeOut)
            .start()

        // Hero rule wipes in left → right
        binding.heroRule.animate()
            .scaleX(1f)
            .setStartDelay(220L)
            .setDuration(560L)
            .setInterpolator(easeOut)
            .start()

        // Schedule hairline draws across the section header as it lands
        binding.scheduleHairline.animate()
            .scaleX(1f)
            .setStartDelay(180L)
            .setDuration(640L)
            .setInterpolator(easeOut)
            .start()

        // Schedule dots settle in at their endpoints
        binding.dotCheckIn.animate()
            .alpha(1f).scaleX(1f).scaleY(1f)
            .setStartDelay(220L)
            .setDuration(380L)
            .setInterpolator(easeOut)
            .start()
        binding.dotCheckOut.animate()
            .alpha(1f).scaleX(1f).scaleY(1f)
            .setStartDelay(280L)
            .setDuration(380L)
            .setInterpolator(easeOut)
            .start()

        // Schedule fill draws between the dots after they land
        binding.scheduleFill.animate()
            .scaleY(pendingFillRatio)
            .setStartDelay(360L)
            .setDuration(780L)
            .setInterpolator(easeOut)
            .start()

        // Details hairline draws as the section lands
        binding.detailsHairline.animate()
            .scaleX(1f)
            .setStartDelay(260L)
            .setDuration(680L)
            .setInterpolator(easeOut)
            .start()
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
                        if (!isCacheLoaded) loadParkingDataCache()
                        renderBooking(booking)
                        showLoading(false)
                        showContent(true)
                    } else {
                        showLoading(false)
                        showError(true, "Booking not found")
                    }
                } else if (response.code() == 404) {
                    val historyResponse = ApiClient.apiService.getUserBookingHistory(userId)
                    if (historyResponse.isSuccessful) {
                        val history = BookingPayloadParser.parseBookings(historyResponse.body())
                        val booking = history.firstOrNull { it.id == id }
                        if (booking != null) {
                            if (!isCacheLoaded) loadParkingDataCache()
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
        bindHero(booking)
        bindSchedule(booking)
        bindDetails(booking)
    }

    // ===== HERO =====

    private fun bindHero(booking: Booking) {
        binding.tvSpotSummary.text = resolveSpotName(booking)
        binding.tvLotSummary.text = resolveLotName(booking)
        applyStatusPill(booking.status)
        binding.tvSmartSummary.text = composeSmartSummary(booking)
    }

    private fun statusColorRes(status: String?): Int {
        val normalized = status?.trim()?.lowercase(Locale.getDefault()).orEmpty()
        return when {
            normalized in ACTIVE_STATES -> R.color.booking_status_active_text
            normalized == "cancelled" || normalized == "canceled" -> R.color.error
            normalized in setOf("no_show", "no-show", "noshow") -> R.color.brand_primary
            normalized in setOf(
                "pending", "booked", "reserved", "confirmed", "scheduled",
                "awaiting_payment", "awaiting-payment",
                "awaiting_checkin", "awaiting-checkin", "initiated"
            ) -> R.color.booking_status_pending_text
            else -> R.color.text_primary
        }
    }

    private fun applyStatusPill(status: String?) {
        val normalized = status?.trim()?.lowercase(Locale.getDefault()).orEmpty()
        val (label, tintRes) = when {
            normalized in ACTIVE_STATES -> "Active" to R.color.booking_status_active_text
            normalized in COMPLETED_STATES -> "Completed" to R.color.text_primary
            normalized == "cancelled" || normalized == "canceled" -> "Cancelled" to R.color.error
            normalized in setOf("no_show", "no-show", "noshow") ->
                "No-show" to R.color.text_secondary
            normalized in setOf(
                "pending", "booked", "reserved", "confirmed", "scheduled",
                "awaiting_payment", "awaiting-payment",
                "awaiting_checkin", "awaiting-checkin", "initiated"
            ) -> "Reserved" to R.color.booking_status_pending_text
            else -> formatStatusText(status) to R.color.text_primary
        }
        binding.tvStatusLabel.text = label

        val tint = ContextCompat.getColor(this, tintRes)
        val pillBg = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = 100f * resources.displayMetrics.density
            setColor(Color.argb(28, Color.red(tint), Color.green(tint), Color.blue(tint)))
        }
        binding.pillStatus.background = pillBg

        binding.dotStatus.backgroundTintList = android.content.res.ColorStateList.valueOf(tint)
        binding.tvStatusLabel.setTextColor(tint)
        binding.heroRule.setBackgroundColor(
            Color.argb(120, Color.red(tint), Color.green(tint), Color.blue(tint))
        )

        // Active bookings get a slow breathing pulse on the status dot.
        activePulseAnimator?.cancel()
        activePulseAnimator = null
        binding.dotStatus.scaleX = 1f
        binding.dotStatus.scaleY = 1f
        binding.dotStatus.alpha = 1f
        if (normalized in ACTIVE_STATES) {
            activePulseAnimator = createActivePulse(binding.dotStatus).also { it.start() }
        }
    }

    private fun playBackPress(view: View, onComplete: () -> Unit) {
        view.animate()
            .scaleX(0.9f).scaleY(0.9f)
            .setDuration(110L).setInterpolator(easeInOut)
            .withEndAction {
                view.animate()
                    .scaleX(1f).scaleY(1f)
                    .setDuration(140L).setInterpolator(easeOut)
                    .withEndAction { onComplete() }
                    .start()
            }.start()
    }

    private fun createActivePulse(target: View): ValueAnimator {
        val pulse = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 1800L
            interpolator = easeInOut
            repeatCount = ValueAnimator.INFINITE
            repeatMode = ValueAnimator.RESTART
        }
        pulse.addUpdateListener { anim ->
            val t = anim.animatedValue as Float
            // Smooth sin-like breathing curve: 0 → up at 0.5 → back at 1
            val curve = kotlin.math.sin(t * Math.PI).toFloat()
            val scale = 1f + 0.35f * curve
            target.scaleX = scale
            target.scaleY = scale
            target.alpha = 1f - 0.4f * curve
        }
        return pulse
    }

    private fun composeSmartSummary(booking: Booking): CharSequence {
        val status = booking.status.trim().lowercase(Locale.getDefault())
        val now = System.currentTimeMillis()
        val actualIn = booking.actualCheckInTime
        val actualOut = booking.actualCheckOutTime
        val plannedIn = booking.checkInTime
        val plannedOut = booking.checkOutTime

        return when {
            status in ACTIVE_STATES -> {
                if (actualIn != null) {
                    "Checked in ${humanDuration(now - actualIn.time)} ago"
                } else "Currently parked"
            }
            status in COMPLETED_STATES -> {
                if (actualIn != null && actualOut != null) {
                    "Parked for ${humanDuration(actualOut.time - actualIn.time)}"
                } else if (plannedIn != null && plannedOut != null) {
                    "Reserved for ${humanDuration(plannedOut.time - plannedIn.time)}"
                } else "Booking completed"
            }
            status == "cancelled" || status == "canceled" -> {
                val cancelledAt = booking.cancelledAt ?: booking.updatedAt
                if (cancelledAt != null) "Cancelled ${humanRelativeAgo(now - cancelledAt.time)}"
                else "Cancelled"
            }
            status in setOf("no_show", "no-show", "noshow") -> {
                if (plannedIn != null && plannedOut != null) {
                    "Slot held for ${humanDuration(plannedOut.time - plannedIn.time)}"
                } else "Slot was held but never used"
            }
            status in setOf("pending", "booked", "reserved", "confirmed", "scheduled",
                "awaiting_payment", "awaiting-payment", "awaiting_checkin", "awaiting-checkin",
                "initiated") -> {
                plannedIn?.let { "Scheduled for ${humanRelativeDateTime(it)}" } ?: "Reserved"
            }
            else -> formatStatusText(booking.status)
        }
    }

    // ===== SCHEDULE =====

    private fun bindSchedule(booking: Booking) {
        binding.tvCheckinTime.text = booking.checkInTime?.let { timeFormat.format(it) } ?: "—"
        binding.tvCheckinDate.text = booking.checkInTime?.let { dateFormat.format(it) } ?: ""
        binding.tvCheckoutTime.text = booking.checkOutTime?.let { timeFormat.format(it) } ?: "—"
        binding.tvCheckoutDate.text = booking.checkOutTime?.let { dateFormat.format(it) } ?: ""

        val checkinActual = composeActualLine(
            booking.checkInTime, booking.actualCheckInTime, "Arrived"
        )
        binding.tvCheckinActual.text = checkinActual ?: ""
        binding.tvCheckinActual.visibility = if (checkinActual != null) View.VISIBLE else View.GONE

        val checkoutActual = composeActualLine(
            booking.checkOutTime, booking.actualCheckOutTime, "Left"
        )
        binding.tvCheckoutActual.text = checkoutActual ?: ""
        binding.tvCheckoutActual.visibility = if (checkoutActual != null) View.VISIBLE else View.GONE

        val connectorText = composeScheduleConnectorText(booking)
        binding.tvScheduleDuration.text = connectorText
        binding.tvScheduleDuration.visibility =
            if (connectorText.isBlank()) View.GONE else View.VISIBLE

        val normalized = booking.status.trim().lowercase(Locale.getDefault())
        val checkinReached = booking.actualCheckInTime != null ||
            normalized in ACTIVE_STATES || normalized in COMPLETED_STATES
        val checkoutReached = booking.actualCheckOutTime != null ||
            normalized in COMPLETED_STATES

        binding.dotCheckIn.background = ContextCompat.getDrawable(
            this,
            if (checkinReached) R.drawable.shape_booking_dot_solid
            else R.drawable.shape_booking_dot_ring
        )
        binding.dotCheckOut.background = ContextCompat.getDrawable(
            this,
            if (checkoutReached) R.drawable.shape_booking_dot_solid
            else R.drawable.shape_booking_dot_ring
        )

        // Track stays as a quiet hairline; muted further when the booking didn't happen.
        val isMuted = normalized in MUTED_STATES
        binding.scheduleTrack.alpha = if (isMuted) 0.55f else 1f

        // Fill represents the booking's lived progress. Status-aware:
        //  - completed: full fill (the booking happened end-to-end)
        //  - active   : partial fill up to "now" between scheduled in/out
        //  - others   : zero (track stays as a hairline)
        val timelineTint = ContextCompat.getColor(this, statusColorRes(booking.status))
        binding.scheduleFill.backgroundTintList =
            android.content.res.ColorStateList.valueOf(timelineTint)
        pendingFillRatio = computeFillRatio(booking)

        // If the entrance has already played (e.g. orientation change), apply directly.
        if (entryAnimated) {
            binding.scheduleFill.scaleY = pendingFillRatio
        }
    }

    private fun computeFillRatio(booking: Booking): Float {
        val plannedIn = booking.checkInTime?.time ?: return 0f
        val plannedOut = booking.checkOutTime?.time ?: return 0f
        val total = plannedOut - plannedIn
        if (total <= 0L) return 0f

        val status = booking.status.trim().lowercase(Locale.getDefault())
        return when {
            status in COMPLETED_STATES -> 1f
            status in ACTIVE_STATES -> {
                val now = System.currentTimeMillis()
                ((now - plannedIn).toFloat() / total).coerceIn(0f, 1f)
            }
            else -> 0f
        }
    }

    private fun composeActualLine(scheduled: Date?, actual: Date?, verb: String): CharSequence? {
        if (actual == null) return null
        val timeText = timeFormat.format(actual)
        if (scheduled == null) return "$verb $timeText"

        val deltaMs = actual.time - scheduled.time
        val deltaMin = (kotlin.math.abs(deltaMs) / 60_000L).toInt()
        val (deltaPhrase, deltaColor) = when {
            deltaMin == 0 -> "on time" to R.color.text_secondary
            deltaMs > 0 -> "${humanShortMin(deltaMin)} late" to R.color.booking_details_delta_late
            else -> "${humanShortMin(deltaMin)} early" to R.color.booking_details_delta_early
        }
        val head = "$verb $timeText · "
        val sb = SpannableStringBuilder(head).append(deltaPhrase)
        sb.setSpan(
            ForegroundColorSpan(ContextCompat.getColor(this, deltaColor)),
            head.length, head.length + deltaPhrase.length,
            Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
        )
        return sb
    }

    private fun composeScheduleConnectorText(booking: Booking): String {
        val now = System.currentTimeMillis()
        val actualIn = booking.actualCheckInTime
        val actualOut = booking.actualCheckOutTime
        val plannedIn = booking.checkInTime
        val plannedOut = booking.checkOutTime
        val status = booking.status.trim().lowercase(Locale.getDefault())

        return when {
            actualIn != null && actualOut != null ->
                "${humanDuration(actualOut.time - actualIn.time)} parked"
            actualIn != null && (status == "active" || status.contains("ongoing") ||
                status.contains("in_progress") || status.contains("checked_in")) ->
                "${humanDuration(now - actualIn.time)} so far"
            status == "cancelled" || status == "canceled" -> "Never started"
            status.contains("no_show") || status.contains("no-show") || status.contains("noshow") ->
                "Slot was never used"
            plannedIn != null && plannedOut != null ->
                "${humanDuration(plannedOut.time - plannedIn.time)} reserved"
            else -> ""
        }
    }

    // ===== DETAILS =====

    private fun bindDetails(booking: Booking) {
        binding.tvVehicleValue.text =
            booking.vehicleNumber?.takeUnless { it.isBlank() }?.let { formatPlate(it) } ?: "—"

        val isNegative = booking.amount < 0
        val amountText = String.format(
            Locale.getDefault(), "%.2f", kotlin.math.abs(booking.amount)
        )
        binding.tvAmountSummary.text = if (isNegative) "−$amountText" else amountText

        val createdAt = booking.createdAt
        if (createdAt != null) {
            binding.tvBookedOnValue.text = bookedDateFormat.format(createdAt)
            binding.tvBookedOnTime.text = bookedTimeFormat.format(createdAt)
            binding.tvBookedOnTime.visibility = View.VISIBLE
        } else {
            binding.tvBookedOnValue.text = "—"
            binding.tvBookedOnTime.visibility = View.GONE
        }

        val rawId = booking.id?.takeUnless { it.isBlank() }
        binding.tvBookingIdValue.text = rawId ?: "—"
        if (rawId != null) {
            binding.rowBookingId.setOnClickListener { v -> performCopy(rawId, v) }
            binding.rowBookingId.isClickable = true
            binding.ivCopyId.visibility = View.VISIBLE
        } else {
            binding.rowBookingId.setOnClickListener(null)
            binding.rowBookingId.isClickable = false
            binding.ivCopyId.visibility = View.GONE
        }
    }

    /**
     * Formats Indian-style plates by inserting spaces at letter↔digit boundaries.
     * "MH04CV4888" -> "MH 04 CV 4888". Already-spaced input is normalized first.
     */
    private fun formatPlate(raw: String): String {
        val cleaned = raw.replace(Regex("\\s+"), "").uppercase(Locale.getDefault())
        if (cleaned.isEmpty()) return raw
        val sb = StringBuilder()
        for (i in cleaned.indices) {
            val c = cleaned[i]
            if (i > 0) {
                val prev = cleaned[i - 1]
                if (prev.isLetter() != c.isLetter()) sb.append(' ')
            }
            sb.append(c)
        }
        return sb.toString()
    }

    private fun performCopy(value: String, source: View) {
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
        clipboard?.setPrimaryClip(ClipData.newPlainText("Booking reference", value))
        source.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)

        val icon = binding.ivCopyId
        val referenceText = binding.tvBookingIdValue
        icon.animate().cancel()
        icon.setImageResource(R.drawable.ic_copy_booking_reference_done)
        icon.imageTintList = ContextCompat.getColorStateList(this, R.color.text_primary)
        referenceText.text = "Copied to clipboard"
        referenceText.setTextColor(ContextCompat.getColor(this, R.color.text_primary))
        icon.scaleX = 0.6f; icon.scaleY = 0.6f; icon.alpha = 0f
        icon.animate()
            .alpha(1f).scaleX(1f).scaleY(1f)
            .setDuration(180L).setInterpolator(easeOut)
            .withEndAction {
                icon.postDelayed({
                    referenceText.text = value
                    referenceText.setTextColor(
                        ContextCompat.getColor(this, R.color.booking_details_support_text)
                    )
                    icon.animate()
                        .alpha(0.4f).scaleX(0.85f).scaleY(0.85f)
                        .setDuration(160L)
                        .withEndAction {
                            icon.setImageResource(R.drawable.ic_copy_booking_reference)
                            icon.imageTintList =
                                ContextCompat.getColorStateList(
                                    this,
                                    R.color.booking_details_support_text
                                )
                            icon.animate()
                                .alpha(1f).scaleX(1f).scaleY(1f)
                                .setDuration(160L).start()
                        }.start()
                }, 1100L)
            }.start()
    }

    // ===== Helpers =====

    private fun resolveLotName(booking: Booking): String {
        if (!booking.lotName.isNullOrBlank()) return booking.lotName
        if (booking.lotId.isBlank()) return "Parking Lot"
        return parkingLotCache[booking.lotId] ?: booking.lotId
    }

    private fun resolveSpotName(booking: Booking): String {
        if (booking.spotId.isBlank()) return "Parking Spot"
        return parkingSpotCache[booking.spotId] ?: booking.spotId
    }

    private fun formatStatusText(status: String?): String {
        if (status.isNullOrBlank()) return "Unknown"
        val normalized = status.trim().lowercase(Locale.getDefault())
        if (normalized == "pending") return "Booked"
        return status
            .trim()
            .replace("_", " ")
            .replace("-", " ")
            .split(Regex("\\s+"))
            .filter { it.isNotBlank() }
            .joinToString(" ") { word ->
                word.lowercase(Locale.getDefault()).replaceFirstChar { c ->
                    if (c.isLowerCase()) c.titlecase(Locale.getDefault()) else c.toString()
                }
            }
    }

    private fun humanDuration(durationMillis: Long): String {
        val safe = durationMillis.coerceAtLeast(0L)
        val totalMinutes = safe / 60_000L
        val hours = totalMinutes / 60
        val minutes = totalMinutes % 60
        return when {
            hours == 0L && minutes <= 1L -> "1 minute"
            hours == 0L -> "$minutes minutes"
            minutes == 0L && hours == 1L -> "1 hour"
            minutes == 0L -> "$hours hours"
            hours == 1L -> "1 hr ${minutes}m"
            else -> "${hours}h ${minutes}m"
        }
    }

    private fun humanShortMin(minutes: Int): String {
        val n = minutes.coerceAtLeast(1)
        return if (n == 1) "1 min" else "$n min"
    }

    private fun humanRelativeAgo(diffMillis: Long): String {
        val safe = diffMillis.coerceAtLeast(0L)
        val minutes = safe / 60_000L
        val hours = minutes / 60
        val days = hours / 24
        return when {
            minutes < 1L -> "just now"
            minutes < 60L -> "$minutes min ago"
            hours < 24L -> if (hours == 1L) "1 hour ago" else "$hours hours ago"
            days < 7L -> if (days == 1L) "1 day ago" else "$days days ago"
            else -> "${days / 7} weeks ago"
        }
    }

    private fun humanRelativeDateTime(date: Date): String {
        val now = Calendar.getInstance()
        val target = Calendar.getInstance().apply { time = date }
        fun startOfDay(c: Calendar) = (c.clone() as Calendar).apply {
            set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }
        val diffDays = ((startOfDay(target).timeInMillis - startOfDay(now).timeInMillis) /
            (1000L * 60 * 60 * 24)).toInt()
        val timeText = timeFormat.format(date)
        return when (diffDays) {
            0 -> "today at $timeText"
            1 -> "tomorrow at $timeText"
            -1 -> "yesterday at $timeText"
            else -> bookedOnFormat.format(date)
        }
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
                    }
                }
            }

            isCacheLoaded = true
        } catch (_: Exception) {
        }
    }

    private fun showLoading(show: Boolean) {
        binding.progressLoading.visibility = if (show) View.VISIBLE else View.GONE
        if (show) {
            binding.scrollContent.visibility = View.VISIBLE
            binding.sectionHero.visibility = View.GONE
            binding.sectionSchedule.visibility = View.GONE
            binding.sectionDetails.visibility = View.GONE
            binding.layoutErrorState.visibility = View.GONE
            updateFrostedHeader(0)
        }
    }

    private fun showContent(show: Boolean) {
        binding.scrollContent.visibility = if (show) View.VISIBLE else View.GONE
        binding.sectionHero.visibility = if (show) View.VISIBLE else View.GONE
        binding.sectionSchedule.visibility = if (show) View.VISIBLE else View.GONE
        binding.sectionDetails.visibility = if (show) View.VISIBLE else View.GONE
        if (show) {
            updateFrostedHeader(binding.scrollContent.scrollY)
            binding.scrollContent.post { playEntrance() }
        } else {
            updateFrostedHeader(0)
        }
    }

    private fun showError(show: Boolean, message: String? = null) {
        binding.layoutErrorState.visibility = if (show) View.VISIBLE else View.GONE
        if (show) {
            binding.scrollContent.visibility = View.GONE
            binding.tvErrorMessage.text = message ?: "Please try again."
            updateFrostedHeader(0)
        }
    }

    private fun updateFrostedHeader(scrollOffsetPx: Int) {
        val activeScroll = (scrollOffsetPx - deadZonePx).coerceAtLeast(0f)

        val rawFrost = (activeScroll / frostRangePx).coerceIn(0f, 1f)
        val t = 1f - rawFrost
        binding.viewToolbarFrost.alpha = 1f - (t * t * t)

        val rawTitle = (activeScroll / titleRangePx).coerceIn(0f, 1f)
        // Title is invisible at top, fades in only as the hero scrolls behind the toolbar.
        binding.tvHeaderTitle.alpha = rawTitle * rawTitle

        if (scrollOffsetPx <= 0) {
            binding.viewToolbarFrost.alpha = 0f
            binding.tvHeaderTitle.alpha = 0f
        }
    }

    companion object {
        const val EXTRA_BOOKING_ID = "extra_booking_id"

        private val COMPLETED_STATES = setOf(
            "completed", "checked_out", "checked-out",
            "auto_completed", "auto-completed", "finished", "expired"
        )
        private val ACTIVE_STATES = setOf(
            "active", "in_progress", "in-progress", "ongoing",
            "checked_in", "checked-in"
        )
        private val MUTED_STATES = setOf(
            "cancelled", "canceled", "no_show", "no-show", "noshow",
            "pending", "booked", "reserved", "confirmed", "scheduled",
            "awaiting_payment", "awaiting-payment",
            "awaiting_checkin", "awaiting-checkin", "initiated"
        )
    }
}
