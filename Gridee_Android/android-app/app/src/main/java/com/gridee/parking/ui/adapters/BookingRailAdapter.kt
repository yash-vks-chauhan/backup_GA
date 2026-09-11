package com.gridee.parking.ui.adapters

import android.content.res.ColorStateList
import android.graphics.Bitmap
import android.view.HapticFeedbackConstants
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.gridee.parking.R
import com.gridee.parking.databinding.ItemBookingRailBinding
import com.gridee.parking.databinding.StageCardActiveBinding
import com.gridee.parking.databinding.StageCardPendingBinding
import com.gridee.parking.databinding.StageRowBinding
import com.gridee.parking.ui.bookings.BookingPassText
import com.gridee.parking.ui.views.StageRailView
import com.gridee.parking.utils.BookingQrCodeGenerator
import kotlin.math.roundToInt

/**
 * The bookings tab, as a stage rail: booked -> checked in -> checked out, with only the
 * stop you are currently on expanded into a card.
 *
 * This replaces the tab's use of [BookingsAdapter], which stays as it is because the profile's
 * booking-history screen still binds it in compact mode.
 *
 * One booking is one rail. The list is still a RecyclerView rather than three static views
 * because remote config can turn multipleBookings on, and two bookings should read as two
 * rails rather than an impossible merged one.
 */
class BookingRailAdapter(
    private val onPassClick: (Booking) -> Unit,
    private val onCancelRequested: (Booking) -> Unit,
) : RecyclerView.Adapter<BookingRailAdapter.RailViewHolder>() {

    private var bookings: List<Booking> = emptyList()
    private val qrCache = mutableMapOf<String, Bitmap>()

    /** What each live rail was last drawn as, so the tick knows when the state actually flips. */
    private val renderedOverdue = mutableMapOf<String, Boolean>()

    /**
     * Live bookings only.
     *
     * The filter is here as well as in the fragment on purpose: this adapter has a rail for a
     * booking in progress and nothing else, so a finished one reaching a view holder would be
     * a bug with no correct rendering. Dropping it here means there is no such state to get
     * wrong rather than a branch that has to guess.
     */
    fun submit(next: List<Booking>) {
        bookings = next.filter {
            it.status == BookingStatus.ACTIVE || it.status == BookingStatus.PENDING
        }
        notifyDataSetChanged()
    }

    /**
     * The one-second beat, aimed only where a clock is running.
     *
     * This used to notify every row once a second whether or not anything on it counted down,
     * which kept the whole list in a permanent relayout — enough that uiautomator could never
     * reach an idle state and touch handling suffered for it. A Booked rail has nothing that
     * changes between ticks.
     */
    fun tickLiveTimers() {
        bookings.forEachIndexed { index, booking ->
            if (booking.status != BookingStatus.ACTIVE) return@forEachIndexed
            val overdue = isOverdue(booking)
            if (renderedOverdue[booking.id] != overdue) {
                // Crossing the checkout time changes the rail itself — the last stop stops
                // being a future clock time — so this one needs a real rebind, not a payload.
                renderedOverdue[booking.id] = overdue
                notifyItemChanged(index)
            } else {
                notifyItemChanged(index, PAYLOAD_TICK)
            }
        }
    }

    /** Past its checkout time and still not scanned out. */
    private fun isOverdue(booking: Booking): Boolean =
        booking.checkOutTimestamp > 0L && System.currentTimeMillis() > booking.checkOutTimestamp

    fun hasLiveTimer(): Boolean = bookings.any { it.status == BookingStatus.ACTIVE }

    class RailViewHolder(val binding: ItemBookingRailBinding) :
        RecyclerView.ViewHolder(binding.root) {
        /** Held so the one-second tick can reach the live card without a rebind. */
        var activeCard: StageCardActiveBinding? = null
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RailViewHolder =
        RailViewHolder(
            ItemBookingRailBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        )

    override fun getItemCount(): Int = bookings.size

    override fun onBindViewHolder(holder: RailViewHolder, position: Int) {
        val booking = bookings[position]
        holder.activeCard = null
        holder.binding.railContainer.removeAllViews()
        // submit() admits nothing else.
        if (booking.status == BookingStatus.ACTIVE) {
            bindActiveRail(holder, booking)
        } else {
            bindPendingRail(holder, booking)
        }
    }

    override fun onBindViewHolder(
        holder: RailViewHolder,
        position: Int,
        payloads: MutableList<Any>,
    ) {
        if (payloads.contains(PAYLOAD_TICK)) {
            val booking = bookings.getOrNull(position) ?: return
            holder.activeCard?.let { updateLiveTimer(it, booking) }
            return
        }
        super.onBindViewHolder(holder, position, payloads)
    }

    // ── rails ────────────────────────────────────────────────────────────────

    /** Booked: you are on stop one, and both stops ahead are dashed. */
    private fun bindPendingRail(holder: RailViewHolder, booking: Booking) {
        val container = holder.binding.railContainer
        val inflater = LayoutInflater.from(container.context)

        val card = StageCardPendingBinding.inflate(inflater, container, false)
        bindPendingCard(card, booking)
        rail(card.stageRail, StageRailView.Marker.CURRENT, StageRailView.Line.NONE, StageRailView.Line.DASHED)
        card.stageRail.accentColor = color(container, R.color.status_text_pending)
        card.stageRail.haloColor = color(container, R.color.stage_head_pending)
        container.addView(card.root)

        container.addView(
            stageRow(
                inflater, container,
                label = container.context.getString(R.string.stage_checked_in),
                value = container.context.getString(R.string.stage_on_arrival),
                marker = StageRailView.Marker.FUTURE,
                lead = StageRailView.Line.DASHED,
                tail = StageRailView.Line.DASHED,
                spoken = R.string.stage_cd_future,
            )
        )
        container.addView(
            stageRow(
                inflater, container,
                label = container.context.getString(R.string.stage_checked_out),
                value = clock(booking.endTime.ifBlank { "—" }),
                marker = StageRailView.Marker.FUTURE,
                lead = StageRailView.Line.DASHED,
                tail = StageRailView.Line.NONE,
                spoken = R.string.stage_cd_future,
            )
        )
    }

    /** Active: stop one is behind you and solid, stop three is still dashed ahead. */
    private fun bindActiveRail(holder: RailViewHolder, booking: Booking) {
        val container = holder.binding.railContainer
        val inflater = LayoutInflater.from(container.context)

        container.addView(
            stageRow(
                inflater, container,
                label = container.context.getString(R.string.stage_booked),
                // When Booked *completed* — the operator's scan, not the slot it was booked for.
                value = clock(booking.scannedAtTime ?: booking.startTime.ifBlank { "—" }),
                marker = StageRailView.Marker.DONE,
                lead = StageRailView.Line.NONE,
                tail = StageRailView.Line.SOLID,
                spoken = R.string.stage_cd_done,
            )
        )

        val card = StageCardActiveBinding.inflate(inflater, container, false)
        bindActiveCard(card, booking)
        rail(card.stageRail, StageRailView.Marker.CURRENT, StageRailView.Line.SOLID, StageRailView.Line.DASHED)
        val live = if (isOverdue(booking)) R.color.status_text_cancelled else R.color.status_text_active
        card.stageRail.accentColor = color(container, live)
        card.stageRail.haloColor =
            color(container, if (isOverdue(booking)) R.color.stage_head_ended else R.color.stage_head_active)
        container.addView(card.root)
        holder.activeCard = card

        val overdue = isOverdue(booking)
        renderedOverdue[booking.id] = overdue
        container.addView(
            stageRow(
                inflater, container,
                label = container.context.getString(R.string.stage_checked_out),
                value = if (overdue) container.context.getString(R.string.stage_overdue)
                        else clock(booking.endTime.ifBlank { "—" }),
                marker = StageRailView.Marker.FUTURE,
                lead = StageRailView.Line.DASHED,
                tail = StageRailView.Line.NONE,
                spoken = R.string.stage_cd_future,
                valueColor = if (overdue) R.color.status_text_cancelled else null,
            )
        )
    }

    // ── stops ────────────────────────────────────────────────────────────────

    private fun stageRow(
        inflater: LayoutInflater,
        parent: ViewGroup,
        label: String,
        value: String,
        marker: StageRailView.Marker,
        lead: StageRailView.Line,
        tail: StageRailView.Line,
        spoken: Int,
        valueColor: Int? = null,
    ): View {
        val row = StageRowBinding.inflate(inflater, parent, false)
        row.textStageLabel.text = label
        row.textStageValue.text = value
        valueColor?.let { row.textStageValue.setTextColor(color(row.root, it)) }
        rail(row.stageRail, marker, lead, tail)
        // The dot style is the only thing distinguishing done from upcoming; say it out loud.
        row.stageRowRoot.contentDescription = parent.context.getString(spoken, label, value)
        return row.root
    }

    private fun rail(
        view: StageRailView,
        marker: StageRailView.Marker,
        lead: StageRailView.Line,
        tail: StageRailView.Line,
    ) {
        view.marker = marker
        view.leadLine = lead
        view.tailLine = tail
        // Both a card head's status row and a row's label sit ~20dp down; one value serves both.
        view.markerCenterY = 20f * view.resources.displayMetrics.density
    }

    // ── cards ────────────────────────────────────────────────────────────────

    private fun bindPendingCard(binding: StageCardPendingBinding, booking: Booking) {
        binding.textStageDate.text = headDate(booking)
        binding.textSpotName.text = booking.spotName
        binding.textLotName.text = booking.locationName
        binding.textCheckIn.text = clock(booking.scheduledStartTime.ifBlank { booking.startTime })
        binding.textCheckOut.text = clock(booking.endTime)
        binding.textVehicle.text = booking.vehicleNumber.ifBlank { "—" }
        binding.textAmount.text = amount(booking)
        binding.coinAmount.visibility =
            if (booking.amount.isBlank()) View.GONE else View.VISIBLE

        val span = compactDuration(booking)
        binding.textDurationCapsule.visibility = if (span == null) View.GONE else View.VISIBLE
        span?.let { binding.textDurationCapsule.text = it }

        bindPass(binding.imagePassCode, booking)
        binding.layoutPassStub.setOnClickListener { onPassClick(booking) }
        bindCancelRow(binding, booking)
    }

    private fun bindActiveCard(binding: StageCardActiveBinding, booking: Booking) {
        binding.textStageDate.text = headDate(booking)
        binding.textSpotName.text = booking.spotName
        binding.textLotName.text = booking.locationName
        binding.textCheckIn.text = clock(booking.scheduledStartTime.ifBlank { booking.startTime })
        binding.textCheckOut.text = clock(booking.endTime)
        binding.textVehicle.text = booking.vehicleNumber.ifBlank { "—" }
        binding.textAmount.text = amount(booking)
        binding.coinAmount.visibility =
            if (booking.amount.isBlank()) View.GONE else View.VISIBLE

        bindPass(binding.imagePassCode, booking)
        binding.layoutPassStub.setOnClickListener { onPassClick(booking) }
        updateLiveTimer(binding, booking)
    }

    /**
     * The one-second beat. Called on bind and from the tick payload, never through a rebind —
     * re-inflating three stops every second would drop the hold-to-cancel gesture mid-press.
     */
    fun updateLiveTimer(binding: StageCardActiveBinding, booking: Booking) {
        val start = booking.checkInTimestamp
        val end = booking.checkOutTimestamp
        val now = System.currentTimeMillis()
        if (end <= start) {
            binding.textTimeRemaining.text = "—"
            binding.textTimeUntil.visibility = View.GONE
            return
        }

        val ctx = binding.root.context
        val overdue = now > end
        applyOverstayPalette(binding, overdue)

        if (overdue) {
            // Counts UP. The card used to sit at "0s" past the checkout time, which reads as
            // "you are fine" at exactly the moment the user is not.
            binding.textRemainingLabel.setText(R.string.label_time_over)
            binding.textTimeRemaining.text = "+" + BookingPassText.remaining(now - end)
            binding.textTimeUntil.text = ctx.getString(R.string.stage_since, clock(booking.endTime))
        } else {
            binding.textRemainingLabel.setText(R.string.label_time_remaining)
            binding.textTimeRemaining.text = BookingPassText.remaining(end - now)
            binding.textTimeUntil.text =
                ctx.getString(R.string.pass_time_left_until_short, clock(booking.endTime))
        }
        binding.textTimeUntil.visibility = View.VISIBLE

        val progress = ((now - start).toFloat() / (end - start).toFloat()).coerceIn(0f, 1f)
        val track = binding.layoutProgressTrack
        if (track.width > 0) {
            applyProgress(binding, track.width, progress)
        } else {
            track.post { if (track.width > 0) applyProgress(binding, track.width, progress) }
        }
    }

    /**
     * The fill needs a real width change, but the marker rides on translationX — a property
     * that never triggers a layout pass. Only the fill is re-laid-out, and only when the whole
     * pixel it lands on actually moves.
     */
    /**
     * Re-skins the live card for a session that has run over: red head wash, red timer, red
     * fill and marker, and the checkout time in red because it is now a deadline that passed
     * rather than one still ahead.
     *
     * Applied from the tick, so a session that runs over while the screen is open changes in
     * place — the only stateful thing on this screen that can happen without an operator.
     */
    private fun applyOverstayPalette(binding: StageCardActiveBinding, overdue: Boolean) {
        val ctx = binding.root.context
        val accent = ContextCompat.getColor(
            ctx, if (overdue) R.color.status_text_cancelled else R.color.status_text_active
        )
        binding.layoutStageHead.setBackgroundResource(
            if (overdue) R.drawable.bg_stage_card_head_ended else R.drawable.bg_stage_card_head_active
        )
        binding.viewStageDot.backgroundTintList = ColorStateList.valueOf(accent)
        binding.textStageStatus.setTextColor(accent)
        binding.textStageStatus.setText(
            if (overdue) R.string.stage_overdue_caps else R.string.stage_live_caps
        )
        binding.textSpotName.setTextColor(ContextCompat.getColor(
            ctx, if (overdue) R.color.stage_head_ended_ink else R.color.stage_head_active_ink))
        val sub = ContextCompat.getColor(
            ctx, if (overdue) R.color.stage_head_ended_sub else R.color.stage_head_active_sub)
        binding.textLotName.setTextColor(sub)
        binding.textStageDate.setTextColor(sub)
        binding.textTimeRemaining.setTextColor(accent)
        binding.textCheckOut.setTextColor(
            if (overdue) accent else ContextCompat.getColor(ctx, R.color.text_secondary))
        binding.viewProgressFill.setBackgroundResource(
            if (overdue) R.drawable.bg_active_progress_fill_red
            else R.drawable.bg_active_progress_fill_green
        )
        binding.viewNowMarker.setBackgroundResource(
            if (overdue) R.drawable.shape_now_marker_core_red else R.drawable.shape_now_marker_core
        )
    }

    private fun applyProgress(binding: StageCardActiveBinding, trackWidth: Int, progress: Float) {
        val filled = (trackWidth * progress).roundToInt()
        val fill = binding.viewProgressFill
        if (fill.layoutParams.width != filled) {
            fill.layoutParams = fill.layoutParams.apply { width = filled }
            fill.requestLayout()
        }
        val marker = binding.viewNowMarker
        val markerWidth = if (marker.width > 0) marker.width else (12f * marker.resources.displayMetrics.density).roundToInt()
        marker.translationX =
            (filled - markerWidth / 2f).coerceIn(0f, (trackWidth - markerWidth).toFloat())
    }

    // ── the pass ─────────────────────────────────────────────────────────────

    /**
     * The generator draws pure black modules on a transparent canvas, so tinting the view is
     * all it takes to ink the code with the theme — dark on the light card, light on the dark
     * one. Nothing here has to scan: at 76dp the code is ~12mm against a ~33mm floor, and the
     * tab has neither the brightness override nor the keep-awake flag the pass sheet sets.
     */
    private fun bindPass(view: android.widget.ImageView, booking: Booking) {
        val id = booking.id.trim()
        if (id.isBlank() || id.equals("unknown", ignoreCase = true)) {
            view.setImageDrawable(null)
            return
        }
        val sizePx = (76f * 2f * view.resources.displayMetrics.density).roundToInt()
        val bitmap = qrCache.getOrPut("$id@$sizePx") {
            BookingQrCodeGenerator.generate(id, sizePx) ?: return view.setImageDrawable(null)
        }
        view.setImageBitmap(bitmap)
        view.imageTintList = ColorStateList.valueOf(color(view, R.color.pass_code_ink))
    }

    // ── hold to cancel ───────────────────────────────────────────────────────

    /**
     * The card's cancel is a plain row: it opens the confirmation sheet rather than acting.
     * A hold gesture was tried here first and dropped — it guards well but has to be
     * discovered, and a destructive action nobody can find is its own kind of failure.
     */
    private fun bindCancelRow(binding: StageCardPendingBinding, booking: Booking) {
        binding.buttonCancelBooking.setOnClickListener {
            it.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
            onCancelRequested(booking)
        }
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    /** "04:01 am" is what SimpleDateFormat gives on API 31+; the design sets these in caps. */
    private fun clock(value: String): String =
        value.uppercase(java.util.Locale.getDefault())

    /** "FRI, 03 APR" — day-of-week first, the way a ticket prints it. */
    private fun headDate(booking: Booking): String {
        val stamp = booking.checkInTimestamp
        if (stamp <= 0L) return booking.bookingDate
        return java.text.SimpleDateFormat("EEE, dd MMM", java.util.Locale.getDefault())
            .format(java.util.Date(stamp))
    }

    /** The coin badge beside it carries the unit, so the number is bare. */
    private fun amount(booking: Booking): String =
        if (booking.amount.isBlank()) "—" else booking.amount

    /** "5h" / "45m" / "1h 30m" — the capsule is a glance, not a readout. */
    private fun compactDuration(booking: Booking): String? {
        val span = booking.checkOutTimestamp - booking.checkInTimestamp
        if (booking.checkInTimestamp <= 0L || span <= 0L) return null
        val hours = span / 3_600_000L
        val minutes = (span % 3_600_000L) / 60_000L
        return when {
            hours > 0 && minutes > 0 -> "${hours}h ${minutes}m"
            hours > 0 -> "${hours}h"
            else -> "${minutes}m"
        }
    }

    private fun color(view: View, res: Int) = ContextCompat.getColor(view.context, res)

    companion object {
        const val PAYLOAD_TICK = "payload_stage_tick"
    }
}
