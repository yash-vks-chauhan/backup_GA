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
                
                // Use safe optional access for vehicle number as it might not be in the binding's layout for all variants
                // (Depends on if user modified item_booking.xml to have ID tv_vehicle_number, which we saw in Step 9)
                 try {
                     // Check if view exists via findViewById if binding doesn't expose it directly or if it's optional?
                     // Actually binding should expose it if ID exists.
                     // In Step 9, item_booking.xml HAS tv_vehicle_number.
                     // So binding.tvVehicleNumber exists.
                     // But in previous Adapter code (Step 17), it wasn't used.
                     // I will assume it exists.
                     // Note: binding classes map IDs to camelCase. tv_vehicle_number -> tvVehicleNumber.
                     // Wait, Step 9: android:id="@+id/tv_vehicle_number"
                     // So usage: tvVehicleNumber.
                 } catch (e: Exception) {
                     // Ignore if field missing
                 }
                
                // Optional binding check for vehicle number (not in original adapter code but useful)
                // If binding has tvVehicleNumber property:
                // binding.tvVehicleNumber?.text = booking.vehicleNumber 
                // However, I can't check 'has property' easily in code text without reflection or knowing the generated class.
                // I will skip binding vehicle number here to match previous behavior for non-active cards, 
                // OR check if I can add it safely. 
                // I'll stick to reproducing previous logic + Status Pill.

                when (booking.status) {
                    BookingStatus.ACTIVE -> {
                        // Should not happen here given getItemViewType
                        tvStatus.text = "ACTIVE"
                        tvStatus.setBackgroundResource(R.drawable.status_outlined_active)
                        tvStatus.setTextColor(android.graphics.Color.parseColor("#2E7D32"))
                    }
                    BookingStatus.PENDING -> {
                        tvStatus.text = "PENDING"
                        tvStatus.setBackgroundResource(R.drawable.status_outlined_pending)
                        tvStatus.setTextColor(android.graphics.Color.parseColor("#E65100"))
                    }
                    BookingStatus.COMPLETED -> {
                        tvStatus.text = "COMPLETED"
                        tvStatus.setBackgroundResource(R.drawable.status_outlined_completed)
                        tvStatus.setTextColor(android.graphics.Color.parseColor("#616161"))
                    }
                }
            }
        }
    }
}
