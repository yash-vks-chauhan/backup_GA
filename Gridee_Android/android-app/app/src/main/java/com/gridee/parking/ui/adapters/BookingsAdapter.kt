package com.gridee.parking.ui.adapters

import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.animation.PropertyValuesHolder
import android.animation.ValueAnimator
import android.app.Dialog
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Build
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.VelocityTracker
import android.view.View
import android.view.ViewConfiguration
import android.view.ViewGroup
import android.view.Window
import android.view.WindowManager
import android.view.animation.PathInterpolator
import android.widget.ImageView
import android.widget.TextView
import androidx.dynamicanimation.animation.DynamicAnimation
import androidx.dynamicanimation.animation.SpringAnimation
import androidx.dynamicanimation.animation.SpringForce
import java.util.concurrent.atomic.AtomicBoolean
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.gridee.parking.R
import com.gridee.parking.databinding.ItemBookingBinding
import com.gridee.parking.databinding.ItemBookingActivePassBinding
import com.gridee.parking.utils.BookingQrCodeGenerator
import com.gridee.parking.utils.VehicleNumberValidator
import java.io.Serializable
import java.util.Locale
import kotlin.math.min
import kotlin.math.roundToInt

data class Booking(
    val id: String,
    val vehicleNumber: String,
    val spotId: String,
    val spotName: String,
    val locationName: String,
    val locationAddress: String,
    val startTime: String,
    val endTime: String,
    val duration: String,
    val amount: String,
    val status: BookingStatus,
    val bookingDate: String,
    val checkInTimestamp: Long = 0,
    val checkOutTimestamp: Long = 0,
    val statusLabelOverride: String? = null
) : Serializable

enum class BookingStatus : Serializable {
    ACTIVE,
    PENDING,
    COMPLETED,
    CANCELLED,
    NO_SHOW
}

// How close to expiry the active session is. Drives both the colour
// of the hero "remaining" number and the progress bar's fill drawable.
private enum class Urgency { GREEN, AMBER, RED }

class BookingsAdapter(
    private var bookings: List<Booking>,
    private val onBookingClick: (Booking) -> Unit,
    private val onExtendClick: (Booking) -> Unit,
    private val useCompactHistory: Boolean = false,
    private val historySectionProvider: ((Booking) -> String)? = null
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    private val qrBitmapCache = mutableMapOf<String, Bitmap>()

    private sealed class Row {
        data class Card(val booking: Booking, val isHero: Boolean = false) : Row()
        data class Queue(val nextBooking: Booking, val isHeader: Boolean) : Row()
        data class Section(val title: String) : Row()
    }

    private var rows: List<Row> = buildRows(bookings)

    companion object {
        const val TYPE_ACTIVE_PASS = 1
        const val TYPE_DEFAULT = 2
        const val TYPE_HISTORY = 3
        const val TYPE_QUEUE_CONNECTOR = 4
        const val TYPE_HISTORY_SECTION = 5
        const val TYPE_HERO_BOOKING = 6
        const val PAYLOAD_TIMER_UPDATE = "payload_timer_update"
    }

    private fun buildRows(source: List<Booking>): List<Row> {
        val out = mutableListOf<Row>()
        var lastSection: String? = null
        var heroAssigned = false
        source.forEachIndexed { _, b ->
            if (useCompactHistory && historySectionProvider != null) {
                val section = historySectionProvider.invoke(b)
                if (section != lastSection) {
                    out.add(Row.Section(section))
                    lastSection = section
                }
            }
            val isHero = !useCompactHistory && !heroAssigned && b.status == BookingStatus.PENDING
            if (isHero) heroAssigned = true
            out.add(Row.Card(b, isHero = isHero))
        }
        return out
    }


    private fun formatCountdown(targetMillis: Long, fallbackTime: String): String {
        val safeFallback = fallbackTime.trim().ifEmpty { "--" }
        if (targetMillis <= 0L) return "Starts at $safeFallback"
        val diff = targetMillis - System.currentTimeMillis()
        return when {
            diff <= -60_000L -> "Started at $safeFallback"
            diff <= 0L -> "Starting now"
            diff < 60_000L -> "Starts in less than a minute"
            diff < 60L * 60_000L -> {
                val mins = (diff / 60_000L).toInt().coerceAtLeast(1)
                "Starts in $mins min"
            }
            diff < 12L * 60L * 60_000L -> {
                val hours = diff / (60L * 60_000L)
                val mins = (diff % (60L * 60_000L)) / 60_000L
                if (mins == 0L) "Starts in ${hours}h" else "Starts in ${hours}h ${mins}m"
            }
            else -> "Starts at $safeFallback"
        }
    }

    override fun getItemViewType(position: Int): Int {
        return when (val row = rows[position]) {
            is Row.Queue -> TYPE_QUEUE_CONNECTOR
            is Row.Section -> TYPE_HISTORY_SECTION
            is Row.Card -> {
                val status = row.booking.status
                when {
                    status == BookingStatus.ACTIVE -> TYPE_ACTIVE_PASS
                    row.isHero -> TYPE_HERO_BOOKING
                    useCompactHistory -> TYPE_HISTORY
                    else -> TYPE_DEFAULT
                }
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return when (viewType) {
            TYPE_ACTIVE_PASS -> {
                val binding = ItemBookingActivePassBinding.inflate(
                    LayoutInflater.from(parent.context),
                    parent,
                    false
                )
                ActivePassViewHolder(binding)
            }
            TYPE_HISTORY -> {
                val binding = com.gridee.parking.databinding.ItemBookingHistoryBinding.inflate(
                    LayoutInflater.from(parent.context),
                    parent,
                    false
                )
                HistoryBookingViewHolder(binding)
            }
            TYPE_QUEUE_CONNECTOR -> {
                val binding = com.gridee.parking.databinding.ItemBookingQueueConnectorBinding.inflate(
                    LayoutInflater.from(parent.context),
                    parent,
                    false
                )
                QueueConnectorViewHolder(binding)
            }
            TYPE_HISTORY_SECTION -> {
                val binding = com.gridee.parking.databinding.ItemBookingHistorySectionBinding.inflate(
                    LayoutInflater.from(parent.context),
                    parent,
                    false
                )
                HistorySectionViewHolder(binding)
            }
            TYPE_HERO_BOOKING -> {
                val binding = com.gridee.parking.databinding.ItemBookingHeroBinding.inflate(
                    LayoutInflater.from(parent.context),
                    parent,
                    false
                )
                HeroBookingViewHolder(binding)
            }
            else -> {
                val binding = ItemBookingBinding.inflate(
                    LayoutInflater.from(parent.context),
                    parent,
                    false
                )
                StandardBookingViewHolder(binding)
            }
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (val row = rows[position]) {
            is Row.Card -> {
                val booking = row.booking
                when (holder) {
                    is ActivePassViewHolder -> holder.bind(booking)
                    is HeroBookingViewHolder -> holder.bind(booking)
                    is HistoryBookingViewHolder -> {
                        val next = rows.getOrNull(position + 1)
                        val isLastInSection = next == null || next is Row.Section
                        holder.bind(booking, isLastInSection)
                    }
                    is StandardBookingViewHolder -> holder.bind(booking)
                }
            }
            is Row.Queue -> {
                if (holder is QueueConnectorViewHolder) holder.bind(row.nextBooking, row.isHeader)
            }
            is Row.Section -> {
                if (holder is HistorySectionViewHolder) holder.bind(row.title, position == 0)
            }
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int, payloads: MutableList<Any>) {
        if (payloads.any { it == PAYLOAD_TIMER_UPDATE }) {
            when (val row = rows.getOrNull(position)) {
                is Row.Card -> {
                    if (holder is ActivePassViewHolder) holder.updateTimer(row.booking)
                }
                is Row.Queue -> {
                    if (holder is QueueConnectorViewHolder) holder.bind(row.nextBooking, row.isHeader)
                }
                else -> { /* no-op */ }
            }
        } else {
            super.onBindViewHolder(holder, position, payloads)
        }
    }

    override fun getItemCount(): Int = rows.size

    fun updateBookings(newBookings: List<Booking>) {
        bookings = newBookings
        rows = buildRows(newBookings)
        notifyDataSetChanged()
    }

    inner class ActivePassViewHolder(
        private val binding: ItemBookingActivePassBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        // Cached state for micro-interactions — comparing prev vs next
        // each tick is what lets us animate transitions instead of hard
        // swaps. Reset in bind() so a recycled holder doesn't leak its
        // previous booking's state into the next.
        private var lastRemainingText: String? = null
        private var lastUrgency: Urgency? = null
        private var currentTextColor: Int = 0
        private var currentHaloColor: Int = 0
        private var boundBookingId: String? = null

        fun bind(booking: Booking) {
            binding.apply {
                val ctx = root.context

                // Reset transition state on a fresh bind so the first
                // updateTimer call doesn't try to "animate" from stale
                // values left over from another booking.
                if (boundBookingId != booking.id) {
                    lastRemainingText = null
                    lastUrgency = null
                    currentTextColor = ContextCompat.getColor(ctx, R.color.status_text_active)
                    currentHaloColor = (currentTextColor and 0x00FFFFFF) or 0x33000000
                    boundBookingId = booking.id
                }

                root.setOnClickListener { onBookingClick(booking) }

                tvParkingSpot.text = booking.spotName

                val sub = booking.locationName.trim()
                val hasSub = sub.isNotEmpty() && !sub.equals("unknown lot", ignoreCase = true)
                tvLocationSub.visibility = if (hasSub) View.VISIBLE else View.GONE
                if (hasSub) tvLocationSub.text = sub

                tvVehicleNumber.text = if (booking.vehicleNumber.isNotEmpty())
                    booking.vehicleNumber else "Unknown Vehicle"

                tvCheckInTime.text = booking.startTime
                tvCheckOutTime.text = booking.endTime
                tvAmount.text = booking.amount

                // LIVE pill — single soft-pulsing green dot. The timeline's
                // halo carries the main "alive" signal; this is just the
                // status badge confirming the session is open.
                viewLiveDot.animate().cancel()
                viewLiveDot.scaleX = 1f
                viewLiveDot.scaleY = 1f
                android.animation.ObjectAnimator.ofPropertyValuesHolder(
                    viewLiveDot,
                    android.animation.PropertyValuesHolder.ofFloat(View.SCALE_X, 0.85f, 1.15f),
                    android.animation.PropertyValuesHolder.ofFloat(View.SCALE_Y, 0.85f, 1.15f)
                ).apply {
                    duration = 1400
                    repeatCount = android.animation.ValueAnimator.INFINITE
                    repeatMode = android.animation.ValueAnimator.REVERSE
                    interpolator = android.view.animation.AccelerateDecelerateInterpolator()
                    start()
                }

                // Now-marker halo: slow-breathing radar on the leading
                // edge of the progress fill — the real "alive" signal,
                // anchored exactly where the user is on their journey.
                viewNowMarkerHalo.animate().cancel()
                viewNowMarkerHalo.scaleX = 1f
                viewNowMarkerHalo.scaleY = 1f
                viewNowMarkerHalo.alpha = 0.85f
                android.animation.ObjectAnimator.ofPropertyValuesHolder(
                    viewNowMarkerHalo,
                    android.animation.PropertyValuesHolder.ofFloat(View.SCALE_X, 0.65f, 1.4f),
                    android.animation.PropertyValuesHolder.ofFloat(View.SCALE_Y, 0.65f, 1.4f),
                    android.animation.PropertyValuesHolder.ofFloat(View.ALPHA, 0.85f, 0f)
                ).apply {
                    duration = 2200
                    repeatCount = android.animation.ValueAnimator.INFINITE
                    repeatMode = android.animation.ValueAnimator.RESTART
                    interpolator = android.view.animation.DecelerateInterpolator()
                    start()
                }

                updateTimer(booking)

                // Bind the QR stub directly (the layout is structurally
                // different from the booking-card QR — eyebrow above, ID
                // chip below — so we don't reuse bindBookingQr here).
                bindActiveQrStub(booking)

                attachPressScale(root)
            }
        }

        // Bind the bottom-stub QR. Whole stub is tappable to open the
        // focus modal; QR press scale gives the user a tactile tap cue
        // independent of the card's own press scale on root.
        private fun bindActiveQrStub(booking: Booking) {
            binding.apply {
                val ctx = root.context
                val bookingId = booking.id.trim()
                if (bookingId.isBlank() || bookingId.equals("unknown", ignoreCase = true)) {
                    layoutBookingQr.visibility = View.GONE
                    ivBookingQr.setImageDrawable(null)
                    layoutBookingQr.setOnClickListener(null)
                    return
                }

                layoutBookingQr.visibility = View.VISIBLE
                tvBookingQrTitle.text = "SHOW AT EXIT"
                tvBookingQrSubtitle.text = "ID · $bookingId"

                // 118dp on screen — over-sample (×2) so the bitmap stays
                // crisp on hi-DPI screens. Cache key in getQrBitmap is
                // sizePx-scoped so this doesn't collide with the modal's
                // larger render.
                val sizePx = (118f * 2f * ctx.resources.displayMetrics.density)
                    .roundToInt()
                getQrBitmap(bookingId, sizePx)?.let { ivBookingQr.setImageBitmap(it) }

                layoutBookingQr.isClickable = true
                layoutBookingQr.isFocusable = true
                attachPressScale(layoutBookingQr)
                layoutBookingQr.setOnClickListener { v ->
                    v.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                    showBookingQrDialog(
                        v,
                        bookingId,
                        title = "Show at exit",
                        subtitle = "Hold up to the exit scanner"
                    )
                }
            }
        }

        // Timer update: positions the progress fill + now-marker, recolors
        // the hero "remaining" number and the fill based on how close we
        // are to the end. Called from bind() and from the timer payload
        // path so the card stays live without rebinding.
        fun updateTimer(booking: Booking) {
            binding.apply {
                val now = System.currentTimeMillis()
                val start = booking.checkInTimestamp
                val end = booking.checkOutTimestamp
                val ctx = root.context

                if (end <= start) {
                    setRemainingText("—")
                    tvTimeUntil.visibility = View.GONE
                    setProgress(0f)
                    return
                }

                val total = (end - start).toFloat()
                val elapsed = (now - start).toFloat().coerceAtLeast(0f)
                val progress = (elapsed / total).coerceIn(0f, 1f)
                val remainingMillis = (end - now).coerceAtLeast(0L)
                val remainingFraction = 1f - progress

                if (remainingMillis <= 0L) {
                    setRemainingText("Session Ended")
                    tvTimeUntil.visibility = View.GONE
                } else {
                    setRemainingText(formatRemainingHero(remainingMillis))
                    tvTimeUntil.visibility = View.VISIBLE
                    tvTimeUntil.text = "until ${booking.endTime}"
                }

                // Three urgency thresholds give ambient awareness of how
                // much rope is left without having to read the number.
                //   green  — > 25% remaining
                //   amber  — 10–25% remaining
                //   red    — < 10% remaining OR < 5 absolute minutes
                val urgency: Urgency = when {
                    remainingMillis in 1L..(5L * 60_000L) -> Urgency.RED
                    remainingFraction < 0.10f -> Urgency.RED
                    remainingFraction < 0.25f -> Urgency.AMBER
                    else -> Urgency.GREEN
                }
                applyUrgencyAnimated(ctx, urgency)
                setProgress(progress)
            }
        }

        // Hero number set with a subtle "tick" transition on change. When
        // the displayed minute count flips, the new number fades + slides
        // in a few pixels rather than hard-swapping. Tiny detail, but it
        // makes the card visibly feel like time is passing.
        private fun setRemainingText(newText: String) {
            val view = binding.tvTimeRemaining
            if (lastRemainingText == newText) {
                if (view.text != newText) view.text = newText
                return
            }
            lastRemainingText = newText
            val density = view.context.resources.displayMetrics.density
            view.animate().cancel()
            view.alpha = 0.35f
            view.translationY = -3f * density
            view.text = newText
            view.animate()
                .alpha(1f)
                .translationY(0f)
                .setDuration(220)
                .setInterpolator(android.view.animation.DecelerateInterpolator())
                .start()
        }

        // Apply urgency styling, animating the colour transitions so the
        // card never visibly jolts when crossing a threshold (e.g. green
        // → amber). Fill drawable is swapped instantly because animating
        // a gradient drawable swap doesn't read as a smooth change.
        private fun applyUrgencyAnimated(ctx: android.content.Context, urgency: Urgency) {
            binding.apply {
                val (textColorRes, fillRes) = when (urgency) {
                    Urgency.GREEN -> R.color.status_text_active to R.drawable.bg_active_progress_fill_green
                    Urgency.AMBER -> R.color.status_text_pending to R.drawable.bg_active_progress_fill_amber
                    Urgency.RED -> R.color.status_text_cancelled to R.drawable.bg_active_progress_fill_red
                }
                val targetTextColor = ContextCompat.getColor(ctx, textColorRes)
                val targetHaloColor = (targetTextColor and 0x00FFFFFF) or 0x33000000

                if (lastUrgency == urgency) {
                    // Same tier — make sure colours are correct (in case
                    // of recycle) but don't replay the transition.
                    tvTimeRemaining.setTextColor(targetTextColor)
                    viewNowMarkerHalo.backgroundTintList =
                        android.content.res.ColorStateList.valueOf(targetHaloColor)
                    viewProgressFill.setBackgroundResource(fillRes)
                    setMarkerCoreStroke(ctx, targetTextColor)
                    currentTextColor = targetTextColor
                    currentHaloColor = targetHaloColor
                    return
                }

                // Smooth ARGB transition for text + halo so the urgency
                // shift (green → amber → red) feels like the card is
                // gradually warming up, not a state machine snapping.
                val argbEvaluator = android.animation.ArgbEvaluator()
                android.animation.ValueAnimator.ofObject(
                    argbEvaluator, currentTextColor, targetTextColor
                ).apply {
                    duration = 360
                    interpolator = android.view.animation.DecelerateInterpolator()
                    addUpdateListener { v ->
                        tvTimeRemaining.setTextColor(v.animatedValue as Int)
                    }
                    start()
                }
                android.animation.ValueAnimator.ofObject(
                    argbEvaluator, currentHaloColor, targetHaloColor
                ).apply {
                    duration = 360
                    interpolator = android.view.animation.DecelerateInterpolator()
                    addUpdateListener { v ->
                        viewNowMarkerHalo.backgroundTintList =
                            android.content.res.ColorStateList.valueOf(v.animatedValue as Int)
                    }
                    start()
                }
                viewProgressFill.setBackgroundResource(fillRes)
                setMarkerCoreStroke(ctx, targetTextColor)

                currentTextColor = targetTextColor
                currentHaloColor = targetHaloColor
                lastUrgency = urgency
            }
        }

        // Marker core's stroke matches urgency so the white bead at the
        // leading edge picks up the alarm colour at the boundary.
        private fun setMarkerCoreStroke(ctx: android.content.Context, color: Int) {
            val coreDrawable = ContextCompat.getDrawable(
                ctx, R.drawable.shape_now_marker_core
            )?.mutate()
            if (coreDrawable is android.graphics.drawable.GradientDrawable) {
                val strokePx = (2f * ctx.resources.displayMetrics.density).toInt()
                coreDrawable.setStroke(strokePx, color)
                binding.viewNowMarkerCore.background = coreDrawable
            }
        }

        // Animate fill width + marker x-translation so the bead always
        // rides the leading edge of the fill cleanly.
        private fun setProgress(fraction: Float) {
            binding.apply {
                val track = viewProgressTrack
                track.post {
                    val trackWidth = track.width.toFloat()
                    if (trackWidth <= 0f) return@post
                    val targetFillWidth = (trackWidth * fraction).toInt()
                    val markerX = (trackWidth * fraction)

                    val currentFillWidth = viewProgressFill.width
                    android.animation.ValueAnimator.ofInt(
                        currentFillWidth, targetFillWidth
                    ).apply {
                        duration = 500
                        interpolator = android.view.animation.DecelerateInterpolator()
                        addUpdateListener { v ->
                            val params = viewProgressFill.layoutParams
                            params.width = v.animatedValue as Int
                            viewProgressFill.layoutParams = params
                        }
                        start()
                    }

                    val haloOffset = markerX - viewNowMarkerHalo.width / 2f
                    val coreOffset = markerX - viewNowMarkerCore.width / 2f
                    viewNowMarkerHalo.animate()
                        .translationX(haloOffset)
                        .setDuration(500)
                        .setInterpolator(android.view.animation.DecelerateInterpolator())
                        .start()
                    viewNowMarkerCore.animate()
                        .translationX(coreOffset)
                        .setDuration(500)
                        .setInterpolator(android.view.animation.DecelerateInterpolator())
                        .start()
                }
            }
        }
    }

    // Hero formatting: big chunky "1h 23m" / "12m" / "32s".
    // Matches the type-driven hierarchy of the redesigned active card —
    // we don't need seconds visible at hour-scale, but we surface them
    // at minute-scale so the card doesn't appear frozen at "1m" forever.
    private fun formatRemainingHero(remainingMillis: Long): String {
        val totalSeconds = remainingMillis.coerceAtLeast(0L) / 1000
        val hours = totalSeconds / 3600
        val minutes = (totalSeconds % 3600) / 60
        val seconds = totalSeconds % 60
        return when {
            hours > 0 -> "${hours}h ${minutes}m"
            minutes > 0 -> "${minutes}m"
            else -> "${seconds}s"
        }
    }

    inner class StandardBookingViewHolder(
        private val binding: ItemBookingBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(booking: Booking) {
            binding.apply {
                tvParkingSpot.text = booking.spotName
                tvBookingDate.text = booking.bookingDate
                tvCheckInTime.text = booking.startTime
                tvCheckOutTime.text = booking.endTime
                tvAmount.text = booking.amount
                tvVehicleNumber.text = booking.vehicleNumber

                // Reset common view properties
                viewStatusDot.animate().cancel()
                viewStatusDot.alpha = 1f
                cardBooking.setCardBackgroundColor(androidx.core.content.ContextCompat.getColor(itemView.context, R.color.background_secondary))
                // Using View.GONE instead of android.view.View.GONE
                progressActiveTimer.visibility = View.GONE
                progressActiveTimer.isIndeterminate = false
                progressActiveTimer.progress = 0

                val ctx = itemView.context
                when (booking.status) {
                    BookingStatus.ACTIVE -> {
                        tvStatus.text = booking.statusLabelOverride ?: "Active"
                        layoutStatusPill.setBackgroundResource(R.drawable.status_soft_active)
                        tvStatus.setTextColor(androidx.core.content.ContextCompat.getColor(ctx, R.color.status_text_active))
                        viewStatusDot.visibility = View.VISIBLE
                        viewStatusDot.setBackgroundResource(R.drawable.shape_status_dot_active)
                    }
                    BookingStatus.PENDING -> {
                        tvStatus.text = booking.statusLabelOverride ?: "Booked"
                        layoutStatusPill.setBackgroundResource(R.drawable.status_soft_pending)
                        tvStatus.setTextColor(androidx.core.content.ContextCompat.getColor(ctx, R.color.status_text_pending))
                        viewStatusDot.visibility = View.GONE
                    }
                    BookingStatus.COMPLETED -> {
                        tvStatus.text = booking.statusLabelOverride ?: "Completed"
                        layoutStatusPill.setBackgroundResource(R.drawable.status_soft_completed)
                        tvStatus.setTextColor(androidx.core.content.ContextCompat.getColor(ctx, R.color.status_text_completed))
                        viewStatusDot.visibility = View.GONE
                    }
                    BookingStatus.CANCELLED -> {
                        tvStatus.text = booking.statusLabelOverride ?: "Cancelled"
                        layoutStatusPill.setBackgroundResource(R.drawable.status_soft_cancelled)
                        tvStatus.setTextColor(androidx.core.content.ContextCompat.getColor(ctx, R.color.status_text_cancelled))
                        viewStatusDot.visibility = View.GONE
                    }
                    BookingStatus.NO_SHOW -> {
                        tvStatus.text = booking.statusLabelOverride ?: "No Show"
                        layoutStatusPill.setBackgroundResource(R.drawable.status_soft_noshow)
                        tvStatus.setTextColor(androidx.core.content.ContextCompat.getColor(ctx, R.color.status_text_noshow))
                        viewStatusDot.visibility = View.GONE
                    }
                }

                applyJourneyTimeline(
                    status = booking.status,
                    durationText = resolveDurationText(booking),
                    rail = viewJourneyRail,
                    halo = viewJourneyDotHalo,
                    brandDot = viewJourneyDotBrand,
                    capsule = tvDurationCapsule
                )

                if (booking.status == BookingStatus.PENDING) {
                    bindBookingQr(
                        container = layoutBookingQr,
                        qrImage = ivBookingQr,
                        titleView = tvBookingQrTitle,
                        subtitleView = tvBookingQrSubtitle,
                        booking = booking,
                        title = "Show at entry",
                        subtitle = "Tap to enlarge"
                    )
                } else {
                    clearBookingQr(layoutBookingQr, ivBookingQr)
                }

                attachPressScale(cardBooking)
                cardBooking.setOnClickListener {
                    onBookingClick(booking)
                }
            }
        }
    }

    inner class HeroBookingViewHolder(
        private val binding: com.gridee.parking.databinding.ItemBookingHeroBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(booking: Booking) {
            binding.apply {
                cardBooking.setOnClickListener { onBookingClick(booking) }

                tvParkingSpot.text = booking.spotName

                val sub = booking.locationName.trim()
                val hasSub = sub.isNotEmpty() && !sub.equals("unknown lot", ignoreCase = true)
                tvLocationSub.visibility = if (hasSub) View.VISIBLE else View.GONE
                if (hasSub) tvLocationSub.text = sub

                tvCheckInTime.text = booking.startTime
                tvCheckOutTime.text = booking.endTime

                tvVehicleNumber.text = if (booking.vehicleNumber.isNotEmpty()) booking.vehicleNumber else "—"
                tvAmount.text = booking.amount

                // Hero is reserved for PENDING; status pill always reads "Booked".
                val ctx = itemView.context
                tvStatus.text = booking.statusLabelOverride ?: "Booked"
                tvStatus.setTextColor(ContextCompat.getColor(ctx, R.color.status_text_pending))
                layoutStatusPill.setBackgroundResource(R.drawable.status_soft_pending)

                // Live eyebrow: within 12h → amber countdown ("STARTS IN 32 MIN"),
                // farther out → grey date. The eyebrow becomes the most useful
                // line on the card when check-in is imminent.
                val timeUntil = booking.checkInTimestamp - System.currentTimeMillis()
                val withinCountdown = booking.checkInTimestamp > 0L &&
                    timeUntil in -60_000L..(12L * 60L * 60_000L)
                if (withinCountdown) {
                    tvBookingDate.text = formatCountdown(booking.checkInTimestamp, booking.startTime)
                        .uppercase(Locale.getDefault())
                    tvBookingDate.setTextColor(ContextCompat.getColor(ctx, R.color.status_text_pending))
                } else {
                    tvBookingDate.text = booking.bookingDate
                    tvBookingDate.setTextColor(ContextCompat.getColor(ctx, R.color.text_secondary))
                }

                // Pulsing amber dot in the status pill — a slow heartbeat that
                // signals the pass is alive, mirroring the active pass's radar
                // but at a calmer cadence appropriate for a "still booked" state.
                viewStatusDot.visibility = View.VISIBLE
                viewStatusDot.backgroundTintList = android.content.res.ColorStateList.valueOf(
                    ContextCompat.getColor(ctx, R.color.status_text_pending)
                )
                viewStatusDot.animate().cancel()
                viewStatusDot.scaleX = 1f
                viewStatusDot.scaleY = 1f
                val pulseX = android.animation.PropertyValuesHolder.ofFloat(View.SCALE_X, 0.85f, 1.2f)
                val pulseY = android.animation.PropertyValuesHolder.ofFloat(View.SCALE_Y, 0.85f, 1.2f)
                val pulse = android.animation.ObjectAnimator.ofPropertyValuesHolder(viewStatusDot, pulseX, pulseY)
                pulse.duration = 1400
                pulse.repeatCount = android.animation.ValueAnimator.INFINITE
                pulse.repeatMode = android.animation.ValueAnimator.REVERSE
                pulse.start()

                // Journey timeline: amber rail, duration capsule, and a slow
                // halo pulse on the brand check-in dot. The halo is the calmer
                // cousin of the active pass radar — same vocabulary, gentler
                // cadence so the "next stop" reads without shouting.
                applyJourneyTimeline(
                    status = BookingStatus.PENDING,
                    durationText = resolveDurationText(booking),
                    rail = viewJourneyRail,
                    halo = viewJourneyDotHalo,
                    brandDot = viewJourneyDotBrand,
                    capsule = tvDurationCapsule
                )

                bindBookingQr(
                    container = layoutBookingQr,
                    qrImage = ivBookingQr,
                    titleView = tvBookingQrTitle,
                    subtitleView = tvBookingQrSubtitle,
                    booking = booking,
                    title = "Show at entry",
                    subtitle = "Tap to enlarge"
                )

                attachPressScale(cardBooking)
            }
        }
    }

    inner class QueueConnectorViewHolder(
        private val binding: com.gridee.parking.databinding.ItemBookingQueueConnectorBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(nextBooking: Booking, isHeader: Boolean) {
            val time = nextBooking.startTime.trim()
            val text = if (isHeader) {
                formatCountdown(nextBooking.checkInTimestamp, time)
            } else {
                if (time.isNotEmpty()) "Then at $time" else "Then"
            }
            binding.tvQueueLabel.text = text.uppercase(Locale.getDefault())
        }
    }

    inner class HistoryBookingViewHolder(
        private val binding: com.gridee.parking.databinding.ItemBookingHistoryBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(booking: Booking, isLast: Boolean = false) {
            binding.apply {
                val ctx = itemView.context

                // Title: the spot name (e.g. "PL1") — this is what the user recognizes
                // as the parking spot. Fall back to the lot name only if the spot is
                // missing or looks like a raw UUID.
                val spotTrimmed = booking.spotName.trim()
                val lotTrimmed = booking.locationName.trim()
                val vehicleTrimmed = booking.vehicleNumber.trim()
                val hasSpotName = spotTrimmed.isNotEmpty() &&
                    !spotTrimmed.equals("unknown spot", ignoreCase = true) &&
                    !spotTrimmed.looksLikeRawId()
                val hasLot = lotTrimmed.isNotEmpty() &&
                    !lotTrimmed.equals("unknown lot", ignoreCase = true) &&
                    !lotTrimmed.looksLikeRawId()
                val hasVehicle = vehicleTrimmed.isNotEmpty() &&
                    !vehicleTrimmed.equals("unknown vehicle", ignoreCase = true)
                tvParkingSpot.text = when {
                    hasSpotName -> spotTrimmed
                    hasLot -> lotTrimmed
                    else -> "Parking"
                }


                // Tier 2 — vehicle ("which car was this"). Primary identity hook.
                if (hasVehicle) {
                    chipVehicle.visibility = View.VISIBLE
                    tvMeta.text = VehicleNumberValidator.formatForDisplay(vehicleTrimmed)
                } else {
                    chipVehicle.visibility = View.GONE
                }

                // Tier 3 — timing: date · duration. Kept short so it never visually
                // clashes with the right-hand amount/status column.
                // Duration is skipped for cancelled / no-show bookings — it's meaningless
                // when the session never actually happened.
                val dropDuration = booking.status == BookingStatus.CANCELLED ||
                    booking.status == BookingStatus.NO_SHOW
                val metaParts = mutableListOf<String>()
                if (booking.bookingDate.isNotBlank() && booking.bookingDate != "TBD") {
                    metaParts += booking.bookingDate
                }
                if (!dropDuration) {
                    if (booking.duration.isNotBlank() && booking.duration != "TBD") {
                        metaParts += booking.duration
                    } else if (booking.startTime.isNotBlank() && booking.endTime.isNotBlank() &&
                        booking.startTime != "TBD" && booking.endTime != "TBD") {
                        metaParts += "${booking.startTime} – ${booking.endTime}"
                    }
                }
                if (metaParts.isEmpty()) {
                    tvMetaSecondary.visibility = View.GONE
                } else {
                    tvMetaSecondary.visibility = View.VISIBLE
                    tvMetaSecondary.text = metaParts.joinToString("  ·  ")
                }

                tvPrice.text = booking.amount

                dividerLine.visibility = if (isLast) View.INVISIBLE else View.VISIBLE

                // Status word + leading tile colour. The tile's soft-tinted background
                // and the icon's tint both carry the status colour, giving one clean
                // visual signal on the left that mirrors the status word on the right.
                data class StatusStyle(
                    val label: String,
                    val textColor: Int,
                    val tileBackground: Int
                )
                val style = when (booking.status) {
                    BookingStatus.COMPLETED -> StatusStyle(
                        booking.statusLabelOverride ?: "Completed",
                        R.color.status_text_active,
                        R.drawable.bg_history_tile_completed
                    )
                    BookingStatus.CANCELLED -> StatusStyle(
                        booking.statusLabelOverride ?: "Cancelled",
                        R.color.status_text_cancelled,
                        R.drawable.bg_history_tile_cancelled
                    )
                    BookingStatus.NO_SHOW -> StatusStyle(
                        booking.statusLabelOverride ?: "No Show",
                        R.color.status_text_noshow,
                        R.drawable.bg_history_tile_noshow
                    )
                    BookingStatus.ACTIVE -> StatusStyle(
                        booking.statusLabelOverride ?: "Active",
                        R.color.status_text_active,
                        R.drawable.bg_history_tile_active
                    )
                    else -> StatusStyle(
                        booking.statusLabelOverride ?: "Booked",
                        R.color.status_text_pending,
                        R.drawable.bg_history_tile_pending
                    )
                }
                tvStatus.text = style.label
                tvStatus.setTextColor(ContextCompat.getColor(ctx, style.textColor))
                ivIconTile.setBackgroundResource(style.tileBackground)
                ivIconTile.setColorFilter(ContextCompat.getColor(ctx, style.textColor))

                root.setOnClickListener {
                    root.performHapticFeedback(android.view.HapticFeedbackConstants.VIRTUAL_KEY)
                    onBookingClick(booking)
                }
            }
        }
    }

    inner class HistorySectionViewHolder(
        private val binding: com.gridee.parking.databinding.ItemBookingHistorySectionBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(title: String, isFirst: Boolean) {
            binding.tvSectionTitle.text = title
            val density = itemView.resources.displayMetrics.density
            val topPad = (if (isFirst) 8f else 24f) * density
            binding.sectionRoot.setPadding(
                binding.sectionRoot.paddingStart,
                topPad.toInt(),
                binding.sectionRoot.paddingEnd,
                binding.sectionRoot.paddingBottom
            )
        }
    }

    private fun String.looksLikeRawId(): Boolean {
        // UUID-ish: 20+ chars, no spaces, mostly hex/hyphens — we don't want to
        // surface these to the user as a "lot name" on fallback.
        if (length < 20 || contains(' ')) return false
        return all { it.isLetterOrDigit() || it == '-' || it == '_' }
    }

    // ───────── Journey timeline (rail / halo / capsule) ─────────
    //
    // The booking card uses a "Mobility Pass" timeline: an amber gradient
    // rail connecting two stops, a translucent halo that pulses around the
    // live check-in dot, and a small duration capsule between the times.
    // These helpers paint that timeline status-aware (pending = amber,
    // completed/cancelled = neutral) and own the halo animator lifecycle.

    private fun resolveDurationText(booking: Booking): String? {
        val raw = booking.duration.trim()
        if (raw.isNotEmpty() && !raw.equals("TBD", ignoreCase = true)) return raw
        if (booking.checkInTimestamp > 0L && booking.checkOutTimestamp > booking.checkInTimestamp) {
            val diff = booking.checkOutTimestamp - booking.checkInTimestamp
            val hours = diff / (60L * 60L * 1000L)
            val mins = (diff % (60L * 60L * 1000L)) / (60L * 1000L)
            return if (hours > 0) "${hours}h ${mins}m" else "${mins}m"
        }
        return null
    }

    private fun applyJourneyTimeline(
        status: BookingStatus,
        durationText: String?,
        rail: View,
        halo: View,
        brandDot: View,
        capsule: TextView
    ) {
        val ctx = rail.context
        val isLive = status == BookingStatus.PENDING || status == BookingStatus.ACTIVE

        // Status-aware drawables: amber for live, neutral for done.
        if (isLive) {
            rail.setBackgroundResource(R.drawable.bg_journey_rail_pending)
            capsule.setBackgroundResource(R.drawable.bg_duration_capsule_pending)
            capsule.setTextColor(ContextCompat.getColor(ctx, R.color.status_text_pending))
            brandDot.setBackgroundResource(R.drawable.shape_journey_dot_brand)
        } else {
            rail.setBackgroundResource(R.drawable.bg_journey_rail_default)
            capsule.setBackgroundResource(R.drawable.bg_duration_capsule_default)
            capsule.setTextColor(ContextCompat.getColor(ctx, R.color.text_secondary))
            // Tint the brand-shape dot to a quiet neutral so the timeline
            // reads "completed" without needing a separate drawable.
            brandDot.setBackgroundResource(R.drawable.shape_journey_dot_brand)
            brandDot.backgroundTintList = android.content.res.ColorStateList.valueOf(
                ContextCompat.getColor(ctx, R.color.text_tertiary)
            )
        }
        if (isLive) brandDot.backgroundTintList = null

        // Duration capsule: hide the chip (and its margin) when we have no
        // value to show, rather than displaying an empty pill.
        if (durationText.isNullOrBlank()) {
            capsule.visibility = View.GONE
        } else {
            capsule.visibility = View.VISIBLE
            capsule.text = durationText
        }

        // Halo only pulses for live bookings. For completed/cancelled the
        // timeline is a static record — no need to draw the eye.
        halo.animate().cancel()
        halo.scaleX = 1f
        halo.scaleY = 1f
        halo.alpha = 1f
        if (isLive) {
            halo.visibility = View.VISIBLE
            val haloScaleX = android.animation.PropertyValuesHolder.ofFloat(View.SCALE_X, 0.6f, 1.25f)
            val haloScaleY = android.animation.PropertyValuesHolder.ofFloat(View.SCALE_Y, 0.6f, 1.25f)
            val haloAlpha = android.animation.PropertyValuesHolder.ofFloat(View.ALPHA, 0.85f, 0f)
            val haloAnim = android.animation.ObjectAnimator.ofPropertyValuesHolder(
                halo, haloScaleX, haloScaleY, haloAlpha
            )
            haloAnim.duration = 1900
            haloAnim.repeatCount = android.animation.ValueAnimator.INFINITE
            haloAnim.repeatMode = android.animation.ValueAnimator.RESTART
            haloAnim.interpolator = android.view.animation.DecelerateInterpolator()
            haloAnim.start()
            halo.tag = haloAnim
        } else {
            halo.visibility = View.GONE
        }
    }

    // Subtle scale-down on press. Cheap, tactile, no overdraw — gives the
    // big card the same "pressable" feel as a button without an overlay.
    private fun attachPressScale(target: View) {
        target.setOnTouchListener { v, event ->
            when (event.actionMasked) {
                android.view.MotionEvent.ACTION_DOWN -> {
                    v.animate().scaleX(0.985f).scaleY(0.985f).setDuration(120).start()
                }
                android.view.MotionEvent.ACTION_UP,
                android.view.MotionEvent.ACTION_CANCEL -> {
                    v.animate().scaleX(1f).scaleY(1f).setDuration(160).start()
                }
            }
            false
        }
    }

    private fun bindBookingQr(
        container: View,
        qrImage: ImageView,
        titleView: TextView,
        subtitleView: TextView,
        booking: Booking,
        title: String,
        subtitle: String
    ) {
        val bookingId = booking.id.trim()
        if (bookingId.isBlank() || bookingId.equals("unknown", ignoreCase = true)) {
            clearBookingQr(container, qrImage)
            return
        }

        val bitmap = getQrBitmap(bookingId)

        if (bitmap == null) {
            clearBookingQr(container, qrImage)
            return
        }

        container.visibility = View.VISIBLE
        qrImage.setImageBitmap(bitmap)
        subtitleView.text = subtitle
        container.isClickable = true
        container.isFocusable = true
        // Card preview's "Tap to enlarge" is wrong copy once the modal is
        // open — the action has already happened. Swap to a contextual
        // scanner instruction derived from the title (entry vs exit).
        val dialogSubtitle = when {
            title.contains("entry", ignoreCase = true) -> "Hold up to the entry scanner"
            title.contains("exit", ignoreCase = true) -> "Hold up to the exit scanner"
            else -> "Hold the screen up to the scanner"
        }
        container.setOnClickListener { view ->
            view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
            showBookingQrDialog(view, bookingId, title, dialogSubtitle)
        }

        // Time-aware caption: for a booked session whose check-in is imminent
        // (within an hour, or up to 10 min past), the caption swaps to "Ready to
        // scan" in the active green. Quietly tells the user the pass is hot.
        val withinScanWindow = booking.status == BookingStatus.PENDING &&
            booking.checkInTimestamp > 0L &&
            (booking.checkInTimestamp - System.currentTimeMillis()) in -10 * 60_000L..60 * 60_000L
        if (withinScanWindow) {
            titleView.text = "Ready to scan"
            titleView.setTextColor(ContextCompat.getColor(titleView.context, R.color.status_text_active))
        } else {
            titleView.text = title
            titleView.setTextColor(ContextCompat.getColor(titleView.context, R.color.text_primary))
        }
    }

    private fun clearBookingQr(container: View, qrImage: ImageView) {
        container.visibility = View.GONE
        qrImage.setImageDrawable(null)
        container.setOnClickListener(null)
        container.isClickable = false
        container.isFocusable = false
    }

    private fun getQrBitmap(bookingId: String, sizePx: Int = 256): Bitmap? {
        val cacheKey = "$bookingId@$sizePx"
        return qrBitmapCache[cacheKey] ?: BookingQrCodeGenerator.generate(bookingId, sizePx)?.also {
            qrBitmapCache[cacheKey] = it
        }
    }

    private fun showBookingQrDialog(anchor: View, bookingId: String, title: String, subtitle: String) {
        val context = anchor.context
        val dialog = Dialog(context)
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)

        val content = LayoutInflater.from(context).inflate(R.layout.dialog_booking_qr, null, false)
        val backdrop = content.findViewById<View>(R.id.dialog_qr_backdrop)
        val card = content.findViewById<View>(R.id.dialog_qr_card)
        val handle = content.findViewById<View>(R.id.dialog_qr_drag_handle)
        val titleBlock = content.findViewById<View>(R.id.dialog_qr_title_block)
        val qrImage = content.findViewById<ImageView>(R.id.iv_dialog_booking_qr)
        val titleView = content.findViewById<TextView>(R.id.tv_dialog_booking_qr_title)
        val subtitleView = content.findViewById<TextView>(R.id.tv_dialog_booking_qr_subtitle)
        val idView = content.findViewById<TextView>(R.id.tv_dialog_booking_qr_id)
        val closeButton = content.findViewById<View>(R.id.iv_close_qr_dialog)

        val density = context.resources.displayMetrics.density
        val availableWidth = context.resources.displayMetrics.widthPixels - (48f * density).roundToInt()
        val maxCardWidth = (380f * density).roundToInt()
        card.layoutParams = card.layoutParams.apply {
            width = min(availableWidth, maxCardWidth).coerceAtLeast((280f * density).roundToInt())
        }

        val qrSizePx = (300f * density).roundToInt()
        getQrBitmap(bookingId, qrSizePx)?.let { qrImage.setImageBitmap(it) }
        titleView.text = title
        subtitleView.text = subtitle
        // Compact, ledger-style ID — the leading "ID ·" anchors meaning
        // without forcing the full "Booking ID:" prefix into the chip.
        idView.text = "ID · $bookingId"

        // Suppress any lingering window-level transition so the only motion
        // the user sees is our staged view-level choreography.
        dialog.setContentView(content)
        dialog.setCanceledOnTouchOutside(true)

        dialog.window?.let { window ->
            window.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            window.setGravity(Gravity.CENTER)
            window.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
            window.attributes = window.attributes.apply {
                windowAnimations = 0
            }
            val attrs = window.attributes
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                window.addFlags(WindowManager.LayoutParams.FLAG_BLUR_BEHIND or WindowManager.LayoutParams.FLAG_DIM_BEHIND)
                window.setBackgroundBlurRadius(80)
                attrs.blurBehindRadius = 80
                attrs.dimAmount = 0.28f
            } else {
                window.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
                window.setDimAmount(0.55f)
            }
            // Boost screen brightness so the QR scans reliably under bright outdoor light.
            attrs.screenBrightness = 1.0f
            window.attributes = attrs
        }

        attachQrDialogChoreography(
            dialog = dialog,
            backdrop = backdrop,
            card = card,
            handle = handle,
            titleBlock = titleBlock,
            qrImage = qrImage,
            idView = idView,
            closeButton = closeButton
        )

        dialog.show()
    }

    // ───────── QR modal: open/close choreography + drag-to-dismiss ─────────
    //
    // Tuned to the project's ANIMATION_TRANSITIONS_GUIDE.md: Apple "snappy"
    // spring (damping 0.8 / stiffness 400) on the card itself, M3 emphasized
    // bezier curves on everything else. Total open ≈ 400ms, close ≈ 240ms,
    // exit always faster than entry. Animations are interruptible — drag,
    // backdrop tap, or close-button tap can catch the open mid-flight and
    // reverse cleanly.
    //
    // Drag-to-dismiss: vertical drag past 140dp (or velocity ≥ 1500dp/s)
    // dismisses the dialog; otherwise the card springs back to rest.

    // M3 cubic-bezier curves — see guide §"Easing Curves & Timing Functions".
    private val emphasizedDecelerate = PathInterpolator(0.05f, 0.7f, 0.1f, 1f)
    private val emphasizedAccelerate = PathInterpolator(0.3f, 0f, 0.8f, 0.15f)

    private fun attachQrDialogChoreography(
        dialog: Dialog,
        backdrop: View,
        card: View,
        handle: View,
        titleBlock: View,
        qrImage: View,
        idView: View,
        closeButton: View
    ) {
        val ctx = card.context
        val density = ctx.resources.displayMetrics.density

        val isClosing = AtomicBoolean(false)
        var openSet: AnimatorSet? = null
        val activeSprings = mutableListOf<SpringAnimation>()

        fun setInitialClosedState() {
            backdrop.alpha = 0f
            card.alpha = 0f
            card.translationY = 36f * density
            card.scaleX = 0.92f
            card.scaleY = 0.92f
            qrImage.alpha = 0f
            qrImage.scaleX = 0.92f
            qrImage.scaleY = 0.92f
            titleBlock.alpha = 0f
            titleBlock.translationY = 8f * density
            idView.alpha = 0f
        }
        setInitialClosedState()

        fun cancelOpenAnimations() {
            openSet?.cancel()
            activeSprings.forEach { it.cancel() }
            activeSprings.clear()
        }

        fun playOpen() {
            // Backdrop / title / QR / ID — ObjectAnimator with M3 Emphasized
            // Decelerate. "Quick start, smooth landing." (See guide.)
            val backdropFade = ObjectAnimator.ofFloat(backdrop, View.ALPHA, 0f, 1f).apply {
                duration = 200
                interpolator = emphasizedDecelerate
            }
            val titleSettle = ObjectAnimator.ofPropertyValuesHolder(
                titleBlock,
                PropertyValuesHolder.ofFloat(View.TRANSLATION_Y, 8f * density, 0f),
                PropertyValuesHolder.ofFloat(View.ALPHA, 0f, 1f)
            ).apply {
                startDelay = 140
                duration = 240
                interpolator = emphasizedDecelerate
            }
            val qrFocus = ObjectAnimator.ofPropertyValuesHolder(
                qrImage,
                PropertyValuesHolder.ofFloat(View.SCALE_X, 0.92f, 1f),
                PropertyValuesHolder.ofFloat(View.SCALE_Y, 0.92f, 1f),
                PropertyValuesHolder.ofFloat(View.ALPHA, 0f, 1f)
            ).apply {
                startDelay = 180
                duration = 260
                interpolator = emphasizedDecelerate
            }
            val idFade = ObjectAnimator.ofFloat(idView, View.ALPHA, 0f, 1f).apply {
                startDelay = 220
                duration = 200
                interpolator = emphasizedDecelerate
            }

            openSet = AnimatorSet().apply {
                playTogether(backdropFade, titleSettle, qrFocus, idFade)
                start()
            }

            // Card itself — Apple "snappy" spring. Damping 0.8 / stiffness
            // 400 = the SwiftUI `.snappy` preset: quick rise, near-imperceptible
            // overshoot, organic landing. Springs run independently of the
            // ObjectAnimator set so they can settle on their own physics
            // timeline (~280–360ms perceptible).
            val springConfig = { spring: SpringForce ->
                spring.dampingRatio = 0.8f
                spring.stiffness = 400f
            }
            // Slight delay so the backdrop fade starts a beat before the
            // card lifts in — gives the modal a layered "presented" feel.
            card.postDelayed({
                if (isClosing.get()) return@postDelayed
                val translateSpring = SpringAnimation(card, DynamicAnimation.TRANSLATION_Y, 0f).apply {
                    spring.also(springConfig)
                    setStartValue(card.translationY)
                    setStartVelocity(0f)
                }
                val scaleXSpring = SpringAnimation(card, DynamicAnimation.SCALE_X, 1f).apply {
                    spring.also(springConfig)
                    setStartValue(card.scaleX)
                }
                val scaleYSpring = SpringAnimation(card, DynamicAnimation.SCALE_Y, 1f).apply {
                    spring.also(springConfig)
                    setStartValue(card.scaleY)
                }
                // Card alpha rides a fast critically-damped spring so it
                // doesn't bounce — alpha bouncing reads as a flicker.
                val alphaSpring = SpringAnimation(card, DynamicAnimation.ALPHA, 1f).apply {
                    spring.dampingRatio = 1f
                    spring.stiffness = 800f
                    setStartValue(0f)
                }
                activeSprings += listOf(translateSpring, scaleXSpring, scaleYSpring, alphaSpring)
                translateSpring.start()
                scaleXSpring.start()
                scaleYSpring.start()
                alphaSpring.start()
            }, 60)

            // Land haptic — fired at the spring's perceptual settle point
            // (~260ms after spring start, or ~320ms after open begins).
            // Light enough to feel like a click, not a buzz.
            card.postDelayed({
                if (!isClosing.get()) {
                    card.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                }
            }, 320)
        }

        fun playCloseAndDismiss() {
            if (isClosing.getAndSet(true)) return
            cancelOpenAnimations()

            // Reverse stagger with M3 Emphasized Accelerate ("smooth start,
            // quick exit"). All ObjectAnimator — exits should be predictable
            // accelerates, never bouncy springs.
            val idFade = ObjectAnimator.ofFloat(idView, View.ALPHA, idView.alpha, 0f).apply {
                duration = 100
                interpolator = emphasizedAccelerate
            }
            val titleHide = ObjectAnimator.ofPropertyValuesHolder(
                titleBlock,
                PropertyValuesHolder.ofFloat(View.TRANSLATION_Y, titleBlock.translationY, 6f * density),
                PropertyValuesHolder.ofFloat(View.ALPHA, titleBlock.alpha, 0f)
            ).apply {
                startDelay = 20
                duration = 120
                interpolator = emphasizedAccelerate
            }
            val qrHide = ObjectAnimator.ofPropertyValuesHolder(
                qrImage,
                PropertyValuesHolder.ofFloat(View.SCALE_X, qrImage.scaleX, 0.95f),
                PropertyValuesHolder.ofFloat(View.SCALE_Y, qrImage.scaleY, 0.95f),
                PropertyValuesHolder.ofFloat(View.ALPHA, qrImage.alpha, 0f)
            ).apply {
                startDelay = 40
                duration = 140
                interpolator = emphasizedAccelerate
            }
            val cardHide = ObjectAnimator.ofPropertyValuesHolder(
                card,
                PropertyValuesHolder.ofFloat(View.TRANSLATION_Y, card.translationY, card.translationY + 24f * density),
                PropertyValuesHolder.ofFloat(View.SCALE_X, card.scaleX, 0.96f),
                PropertyValuesHolder.ofFloat(View.SCALE_Y, card.scaleY, 0.96f),
                PropertyValuesHolder.ofFloat(View.ALPHA, card.alpha, 0f)
            ).apply {
                startDelay = 60
                duration = 200
                interpolator = emphasizedAccelerate
            }
            val backdropHide = ObjectAnimator.ofFloat(backdrop, View.ALPHA, backdrop.alpha, 0f).apply {
                startDelay = 80
                duration = 200
                interpolator = emphasizedAccelerate
            }

            AnimatorSet().apply {
                playTogether(idFade, titleHide, qrHide, cardHide, backdropHide)
                addListener(object : android.animation.AnimatorListenerAdapter() {
                    override fun onAnimationEnd(animation: android.animation.Animator) {
                        try {
                            dialog.dismiss()
                        } catch (_: IllegalArgumentException) {
                            // Window may already be detached if the host
                            // activity is finishing. Safe to ignore.
                        }
                    }
                })
                start()
            }
        }

        // Tapping the card itself should NOT dismiss — only the backdrop /
        // close button / drag / system back. Swallow taps that land on the
        // card so the backdrop's listener doesn't fire.
        card.setOnClickListener { /* consume */ }
        backdrop.setOnClickListener {
            backdrop.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
            playCloseAndDismiss()
        }
        closeButton.setOnClickListener {
            it.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
            playCloseAndDismiss()
        }
        dialog.setOnKeyListener { _, keyCode, event ->
            if (keyCode == KeyEvent.KEYCODE_BACK && event.action == KeyEvent.ACTION_UP) {
                playCloseAndDismiss()
                true
            } else {
                false
            }
        }
        // Override the default cancel path (e.g. setCanceledOnTouchOutside)
        // so it routes through our close animation instead of a hard cut.
        dialog.setOnCancelListener {
            if (!isClosing.get()) playCloseAndDismiss()
        }

        attachQrDragToDismiss(card, handle, backdrop, density, ::cancelOpenAnimations) {
            playCloseAndDismiss()
        }

        dialog.setOnShowListener { playOpen() }
    }

    // Drag the card downward to dismiss. Past 140dp (or velocity ≥ 1500dp/s
    // downward) the card flies off and the dialog dismisses; otherwise it
    // springs back to rest using the same Apple "snappy" spring as the
    // open animation so the gesture feels like the same physical material.
    //
    // Touching the card during the open animation also cancels in-flight
    // springs so the drag has clean ownership of the card's transforms —
    // this is the "interruptibility" the guide calls out.
    private fun attachQrDragToDismiss(
        card: View,
        handle: View,
        backdrop: View,
        density: Float,
        cancelOpen: () -> Unit,
        onDismiss: () -> Unit
    ) {
        val touchSlop = ViewConfiguration.get(card.context).scaledTouchSlop
        val dismissDistance = 140f * density
        val dismissVelocity = 1500f * density
        val maxFadeDistance = 320f * density

        var downY = 0f
        var startTranslation = 0f
        var dragging = false
        var velocityTracker: VelocityTracker? = null
        var hapticTriggered = false

        val touchListener = View.OnTouchListener { _, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    downY = event.rawY
                    startTranslation = 0f
                    dragging = false
                    hapticTriggered = false
                    velocityTracker?.recycle()
                    velocityTracker = VelocityTracker.obtain().also { it.addMovement(event) }
                    false
                }
                MotionEvent.ACTION_MOVE -> {
                    velocityTracker?.addMovement(event)
                    val dy = event.rawY - downY
                    if (!dragging && dy > touchSlop) {
                        dragging = true
                        // Steal control from any in-flight open springs so
                        // 1:1 finger tracking starts from a stable state.
                        cancelOpen()
                        startTranslation = card.translationY
                    }
                    if (dragging) {
                        val translated = (startTranslation + dy).coerceAtLeast(startTranslation)
                        card.translationY = translated
                        val fade = (1f - (translated - startTranslation) / maxFadeDistance)
                            .coerceIn(0.35f, 1f)
                        card.alpha = fade
                        backdrop.alpha = fade.coerceAtLeast(0.5f)
                        // Light haptic the first time we cross the dismiss
                        // threshold — tells the user that release will commit.
                        if (!hapticTriggered && (translated - startTranslation) > dismissDistance) {
                            hapticTriggered = true
                            card.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                        } else if (hapticTriggered && (translated - startTranslation) < dismissDistance) {
                            hapticTriggered = false
                        }
                    }
                    dragging
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    val wasDragging = dragging
                    val tracker = velocityTracker
                    tracker?.addMovement(event)
                    tracker?.computeCurrentVelocity(1000)
                    val vy = tracker?.yVelocity ?: 0f
                    velocityTracker?.recycle()
                    velocityTracker = null
                    dragging = false

                    if (!wasDragging) return@OnTouchListener false

                    val dragged = card.translationY - startTranslation
                    val shouldDismiss = dragged > dismissDistance || vy > dismissVelocity
                    if (shouldDismiss) {
                        // Fly the card the rest of the way down, then dismiss.
                        ObjectAnimator.ofPropertyValuesHolder(
                            card,
                            PropertyValuesHolder.ofFloat(View.TRANSLATION_Y, card.translationY, card.translationY + 160f * density),
                            PropertyValuesHolder.ofFloat(View.ALPHA, card.alpha, 0f)
                        ).apply {
                            duration = 200
                            interpolator = emphasizedAccelerate
                            addListener(object : android.animation.AnimatorListenerAdapter() {
                                override fun onAnimationEnd(animation: android.animation.Animator) {
                                    onDismiss()
                                }
                            })
                            start()
                        }
                        ObjectAnimator.ofFloat(backdrop, View.ALPHA, backdrop.alpha, 0f).apply {
                            duration = 220
                            interpolator = emphasizedAccelerate
                            start()
                        }
                    } else {
                        // Spring back to rest. Same physics signature as
                        // the open spring (damping 0.8 / stiffness 400) so
                        // gesture and entry feel like the same material.
                        SpringAnimation(card, DynamicAnimation.TRANSLATION_Y, startTranslation).apply {
                            spring.dampingRatio = 0.8f
                            spring.stiffness = 400f
                            // Inject the release velocity so a flick that
                            // didn't quite cross threshold still has a
                            // little kinetic followthrough on the way back.
                            setStartVelocity(-vy.coerceAtLeast(0f))
                            start()
                        }
                        SpringAnimation(card, DynamicAnimation.ALPHA, 1f).apply {
                            spring.dampingRatio = 1f
                            spring.stiffness = 800f
                            start()
                        }
                        ObjectAnimator.ofFloat(backdrop, View.ALPHA, backdrop.alpha, 1f).apply {
                            duration = 220
                            interpolator = emphasizedDecelerate
                            start()
                        }
                    }
                    true
                }
                else -> false
            }
        }

        card.setOnTouchListener(touchListener)
        handle.setOnTouchListener(touchListener)
    }
}
