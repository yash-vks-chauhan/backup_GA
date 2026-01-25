package com.gridee.parking.ui.adapters

import android.view.LayoutInflater
import android.view.ViewGroup
import android.view.View
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.gridee.parking.R
import com.gridee.parking.databinding.ItemBookingBinding
import com.gridee.parking.databinding.ItemBookingActivePassBinding
import java.io.Serializable
import java.util.Locale

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

class BookingsAdapter(
    private var bookings: List<Booking>,
    private val onBookingClick: (Booking) -> Unit,
    private val onExtendClick: (Booking) -> Unit,
    private val useCompactHistory: Boolean = false
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    companion object {
        const val TYPE_ACTIVE_PASS = 1
        const val TYPE_DEFAULT = 2
        const val TYPE_HISTORY = 3
        const val PAYLOAD_TIMER_UPDATE = "payload_timer_update"
    }

    override fun getItemViewType(position: Int): Int {
        val status = bookings[position].status
        return if (status == BookingStatus.ACTIVE) {
            TYPE_ACTIVE_PASS
        } else if (useCompactHistory) {
            TYPE_HISTORY
        } else {
            TYPE_DEFAULT
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
        val booking = bookings[position]
        when (holder) {
            is ActivePassViewHolder -> holder.bind(booking)
            is HistoryBookingViewHolder -> holder.bind(booking)
            is StandardBookingViewHolder -> holder.bind(booking)
        }
    }
    
    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int, payloads: MutableList<Any>) {
        if (payloads.any { it == PAYLOAD_TIMER_UPDATE }) {
            if (holder is ActivePassViewHolder) {
                holder.updateTimer(bookings[position])
            } else if (holder is StandardBookingViewHolder) {
                // No-op for standard cards or verify if they need updates
            }
        } else {
            super.onBindViewHolder(holder, position, payloads)
        }
    }

    override fun getItemCount(): Int = bookings.size

    fun updateBookings(newBookings: List<Booking>) {
        bookings = newBookings
        notifyDataSetChanged()
    }

    inner class ActivePassViewHolder(
        private val binding: ItemBookingActivePassBinding
    ) : RecyclerView.ViewHolder(binding.root) {
        
        fun bind(booking: Booking) {
            binding.apply {
                root.setOnClickListener { onBookingClick(booking) }
                btnExtend.setOnClickListener { onExtendClick(booking) }
                
                tvParkingSpot.text = booking.spotName
                tvLocationSub.text = booking.locationName
                
                // Show vehicle if available
                if (booking.vehicleNumber.isNotEmpty()) {
                    tvVehicleNumber.text = booking.vehicleNumber
                } else {
                    tvVehicleNumber.text = "Unknown Vehicle"
                }

                // Times
                tvCheckInTime.text = booking.startTime
                tvCheckOutTime.text = booking.endTime
                
                // Date & Amount & Duration
                tvBookingDate.text = booking.bookingDate
                tvAmount.text = booking.amount
                // Duration removed from layout

                
                updateTimer(booking)
                
                // --- Complex Radar Animation ---
                
                // 1. The Core Breathing (Subtle Heartbeat)
                val coreScaleX = android.animation.PropertyValuesHolder.ofFloat(android.view.View.SCALE_X, 0.9f, 1.1f)
                val coreScaleY = android.animation.PropertyValuesHolder.ofFloat(android.view.View.SCALE_Y, 0.9f, 1.1f)
                val coreAnim = android.animation.ObjectAnimator.ofPropertyValuesHolder(binding.viewLiveCore, coreScaleX, coreScaleY)
                coreAnim.duration = 1000
                coreAnim.repeatCount = android.animation.ValueAnimator.INFINITE
                coreAnim.repeatMode = android.animation.ValueAnimator.REVERSE
                coreAnim.start()
                
                // 2. The Ripple Shockwave (Expands and Fades)
                // Reset ripple first
                binding.viewLiveRipple.alpha = 0.5f
                binding.viewLiveRipple.scaleX = 0.8f
                binding.viewLiveRipple.scaleY = 0.8f
                
                val rippleScaleX = android.animation.PropertyValuesHolder.ofFloat(android.view.View.SCALE_X, 0.8f, 1.5f)
                val rippleScaleY = android.animation.PropertyValuesHolder.ofFloat(android.view.View.SCALE_Y, 0.8f, 1.5f)
                val rippleAlpha = android.animation.PropertyValuesHolder.ofFloat(android.view.View.ALPHA, 0.5f, 0f)
                
                val rippleAnim = android.animation.ObjectAnimator.ofPropertyValuesHolder(binding.viewLiveRipple, rippleScaleX, rippleScaleY, rippleAlpha)
                rippleAnim.duration = 2000
                rippleAnim.repeatCount = android.animation.ValueAnimator.INFINITE
                rippleAnim.repeatMode = android.animation.ValueAnimator.RESTART // Restart to create "waves"
                rippleAnim.interpolator = android.view.animation.DecelerateInterpolator()
                rippleAnim.start()
            }
        }
        
        fun updateTimer(booking: Booking) {
            // Simple timer logic visualization
            val now = System.currentTimeMillis()
            val start = booking.checkInTimestamp
            val end = booking.checkOutTimestamp
            
            if (end > start) {
                val totalDuration = end - start
                val elapsed = now - start
                val progress = ((elapsed.toDouble() / totalDuration.toDouble()) * 100).toInt().coerceIn(0, 100)
                
                val currentProgress = binding.progressTime.progress
                if (currentProgress != progress) {
                    val animator = android.animation.ObjectAnimator.ofInt(binding.progressTime, "progress", currentProgress, progress)
                    animator.duration = 500
                    animator.interpolator = android.view.animation.DecelerateInterpolator()
                    animator.start()
                }
                
                // Calculate remaining
                val remainingMillis = end - now
                if (remainingMillis > 0) {
                     binding.tvTimeRemaining.text = formatRemainingTime(remainingMillis)
                } else {
                    binding.tvTimeRemaining.text = "Session Ended"
                    binding.progressTime.progress = 100
                }
            } else {
                binding.progressTime.progress = 0
                binding.tvTimeRemaining.text = "--"
            }
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
                cardBooking.setCardBackgroundColor(android.graphics.Color.WHITE)
                // Using View.GONE instead of android.view.View.GONE
                progressActiveTimer.visibility = View.GONE
                progressActiveTimer.isIndeterminate = false
                progressActiveTimer.progress = 0

                when (booking.status) {
                    BookingStatus.ACTIVE -> {
                        // Should be handled by generic view type, but fallback just in case
                        tvStatus.text = booking.statusLabelOverride ?: "Active"
                        layoutStatusPill.setBackgroundResource(R.drawable.status_soft_active)
                        tvStatus.setTextColor(android.graphics.Color.parseColor("#059669"))
                        viewStatusDot.visibility = View.VISIBLE
                        viewStatusDot.setBackgroundResource(R.drawable.shape_status_dot_active)
                    }
                    BookingStatus.PENDING -> {
                        tvStatus.text = booking.statusLabelOverride ?: "Pending"
                        layoutStatusPill.setBackgroundResource(R.drawable.status_soft_pending)
                        tvStatus.setTextColor(android.graphics.Color.parseColor("#D97706"))
                        viewStatusDot.visibility = View.GONE
                    }
                    BookingStatus.COMPLETED -> {
                        tvStatus.text = booking.statusLabelOverride ?: "Completed"
                        layoutStatusPill.setBackgroundResource(R.drawable.status_soft_completed)
                        tvStatus.setTextColor(android.graphics.Color.parseColor("#374151"))
                        viewStatusDot.visibility = View.GONE
                    }
                    BookingStatus.CANCELLED -> {
                        tvStatus.text = booking.statusLabelOverride ?: "Cancelled"
                        layoutStatusPill.setBackgroundResource(R.drawable.status_soft_cancelled)
                        tvStatus.setTextColor(android.graphics.Color.parseColor("#DC2626"))
                        viewStatusDot.visibility = View.GONE
                    }
                    BookingStatus.NO_SHOW -> {
                        tvStatus.text = booking.statusLabelOverride ?: "No Show"
                        layoutStatusPill.setBackgroundResource(R.drawable.status_soft_noshow)
                        tvStatus.setTextColor(android.graphics.Color.parseColor("#1E40AF"))
                        viewStatusDot.visibility = View.GONE
                    }
                }

                cardBooking.setOnClickListener {
                    onBookingClick(booking)
                }
            }
        }
    }

    inner class HistoryBookingViewHolder(
        private val binding: com.gridee.parking.databinding.ItemBookingHistoryBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(booking: Booking) {
            binding.apply {
                tvParkingSpot.text = booking.spotName
                
                // Date and Price separated
                tvDate.text = booking.bookingDate
                tvPrice.text = booking.amount

                // Status Styling
                when (booking.status) {
                    BookingStatus.COMPLETED -> {
                        tvStatus.text = booking.statusLabelOverride ?: "Completed"
                        layoutStatusPill.setBackgroundResource(R.drawable.status_soft_completed)
                        tvStatus.setTextColor(android.graphics.Color.parseColor("#374151"))
                    }
                    BookingStatus.CANCELLED -> {
                        tvStatus.text = booking.statusLabelOverride ?: "Cancelled"
                        layoutStatusPill.setBackgroundResource(R.drawable.status_soft_cancelled)
                        tvStatus.setTextColor(android.graphics.Color.parseColor("#DC2626"))
                    }
                    BookingStatus.NO_SHOW -> {
                        tvStatus.text = booking.statusLabelOverride ?: "No Show"
                        layoutStatusPill.setBackgroundResource(R.drawable.status_soft_noshow)
                        tvStatus.setTextColor(android.graphics.Color.parseColor("#1E40AF"))
                    }
                    BookingStatus.ACTIVE -> {
                        tvStatus.text = booking.statusLabelOverride ?: "Active"
                        layoutStatusPill.setBackgroundResource(R.drawable.status_soft_active)
                        tvStatus.setTextColor(android.graphics.Color.parseColor("#059669"))
                    }
                    else -> {
                        // Pending / Other
                        tvStatus.text = booking.statusLabelOverride ?: "Pending"
                        layoutStatusPill.setBackgroundResource(R.drawable.status_soft_pending)
                        tvStatus.setTextColor(android.graphics.Color.parseColor("#D97706"))
                    }
                }

                root.setOnClickListener {
                    onBookingClick(booking)
                }
            }
        }
    }

    private fun formatRemainingTime(remainingMillis: Long): String {
        val totalSeconds = remainingMillis.coerceAtLeast(0L) / 1000
        val hours = totalSeconds / 3600
        val minutes = (totalSeconds % 3600) / 60
        val seconds = totalSeconds % 60

        return if (hours > 0) {
            String.format(Locale.getDefault(), "%d:%02d:%02d remaining", hours, minutes, seconds)
        } else {
            String.format(Locale.getDefault(), "%d:%02d remaining", minutes, seconds)
        }
    }
}
