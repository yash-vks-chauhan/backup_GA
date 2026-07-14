package com.gridee.parking.ui.bookings

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.gridee.parking.R
import com.gridee.parking.ui.adapters.Booking
import com.gridee.parking.ui.adapters.BookingStatus
import com.gridee.parking.databinding.ItemBookingBinding
import com.gridee.parking.databinding.ItemBookingActivePassBinding
import java.text.SimpleDateFormat
import java.util.*
import android.view.View

class BookingAdapter(
    private val onBookingClick: (Booking) -> Unit
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    private var bookings = listOf<Booking>()
    
    companion object {
        const val TYPE_ACTIVE_PASS = 1
        const val TYPE_DEFAULT = 2
        const val PAYLOAD_TIMER_UPDATE = "PAYLOAD_TIMER_UPDATE"
    }

    override fun getItemViewType(position: Int): Int {
        return if (bookings[position].status == BookingStatus.ACTIVE) {
            TYPE_ACTIVE_PASS
        } else {
            TYPE_DEFAULT
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return if (viewType == TYPE_ACTIVE_PASS) {
            val binding = ItemBookingActivePassBinding.inflate(
                LayoutInflater.from(parent.context),
                parent,
                false
            )
            ActivePassViewHolder(binding)
        } else {
            val binding = ItemBookingBinding.inflate(
                LayoutInflater.from(parent.context),
                parent,
                false
            )
            StandardBookingViewHolder(binding)
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val booking = bookings[position]
        when (holder) {
            is ActivePassViewHolder -> holder.bind(booking)
            is StandardBookingViewHolder -> holder.bind(booking)
        }
    }
    
    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int, payloads: MutableList<Any>) {
        if (payloads.contains(PAYLOAD_TIMER_UPDATE)) {
            if (holder is ActivePassViewHolder) {
                holder.updateTimer(bookings[position])
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
                
                // Date & Amount
                tvBookingDate.text = booking.bookingDate
                tvAmount.text = booking.amount
                
                // Duration removed from layout
                
                updateTimer(booking)
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
                
                binding.progressTime.progress = progress
                
                // Calculate remaining
                val remainingMillis = end - now
                if (remainingMillis > 0) {
                     val hours = remainingMillis / (1000 * 60 * 60)
                     val minutes = (remainingMillis % (1000 * 60 * 60)) / (1000 * 60)
                     
                     val remainingText = if (hours > 0) {
                         "${hours}h ${minutes}m remaining"
                     } else {
                         "${minutes}m remaining"
                     }
                     binding.tvTimeRemaining.text = remainingText
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
                root.setOnClickListener { onBookingClick(booking) }
                
                tvParkingSpot.text = booking.spotName
                tvBookingDate.text = booking.bookingDate
                tvCheckInTime.text = booking.startTime
                tvCheckOutTime.text = booking.endTime
                tvAmount.text = booking.amount
                tvVehicleNumber.text = booking.vehicleNumber

                // Reset card background
                cardBooking.setCardBackgroundColor(androidx.core.content.ContextCompat.getColor(itemView.context, R.color.background_secondary))
                progressActiveTimer.visibility = View.GONE
                viewStatusDot.visibility = View.GONE

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
            }
        }
    }
}
