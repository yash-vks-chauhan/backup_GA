package com.gridee.parking.ui.operator

import android.content.res.ColorStateList
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.gridee.parking.R
import com.gridee.parking.data.model.ParkingSpot
import com.gridee.parking.databinding.ItemOperatorLotHeaderBinding
import com.gridee.parking.databinding.ItemSpotSelectionBinding

/** Operator spot selector with an explicit parking-lot boundary before every group. */
class OperatorGroupedSpotAdapter(
    private val onSpotSelected: (ParkingSpot) -> Unit,
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    private sealed class Row {
        data class Header(val lotId: String, val title: String) : Row()
        data class Spot(val value: ParkingSpot) : Row()
    }

    private var rows: List<Row> = emptyList()
    private var selectedSpotKey: OperatorSpotGrouping.SpotKey? = null

    init {
        setHasStableIds(true)
    }

    fun submitSpots(spots: List<ParkingSpot>, onCommitted: (() -> Unit)? = null) {
        rows = OperatorSpotGrouping.group(spots).flatMap { group ->
                listOf<Row>(Row.Header(group.lotId, group.title)) + group.spots.map(Row::Spot)
            }
        notifyDataSetChanged()
        onCommitted?.invoke()
    }

    fun setSelectedSpot(spotId: String?, lotId: String? = null) {
        val normalizedSpotId = spotId?.trim()?.takeIf(String::isNotEmpty)
        val normalizedLotId = lotId?.trim()?.takeIf(String::isNotEmpty)
        val nextKey = when {
            normalizedSpotId == null -> null
            normalizedLotId != null -> OperatorSpotGrouping.SpotKey(normalizedLotId, normalizedSpotId)
            else -> rows.filterIsInstance<Row.Spot>()
                .map { OperatorSpotGrouping.key(it.value) }
                .filter { it.spotId == normalizedSpotId }
                .distinct()
                .singleOrNull()
        }
        if (selectedSpotKey == nextKey) return
        selectedSpotKey = nextKey
        notifyDataSetChanged()
    }

    override fun getItemId(position: Int): Long = when (val row = rows[position]) {
        is Row.Header -> ("header:${row.lotId}").hashCode().toLong()
        is Row.Spot -> ("spot:${row.value.lotId}:${row.value.id}").hashCode().toLong()
    }

    override fun getItemViewType(position: Int): Int = when (rows[position]) {
        is Row.Header -> TYPE_HEADER
        is Row.Spot -> TYPE_SPOT
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return if (viewType == TYPE_HEADER) {
            HeaderHolder(ItemOperatorLotHeaderBinding.inflate(inflater, parent, false))
        } else {
            SpotHolder(ItemSpotSelectionBinding.inflate(inflater, parent, false))
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (val row = rows[position]) {
            is Row.Header -> (holder as HeaderHolder).bind(row)
            is Row.Spot -> (holder as SpotHolder).bind(row.value)
        }
    }

    override fun getItemCount(): Int = rows.size

    private class HeaderHolder(
        private val binding: ItemOperatorLotHeaderBinding,
    ) : RecyclerView.ViewHolder(binding.root) {
        fun bind(header: Row.Header) {
            binding.tvLotHeader.text = header.title
        }
    }

    private inner class SpotHolder(
        private val binding: ItemSpotSelectionBinding,
    ) : RecyclerView.ViewHolder(binding.root) {
        fun bind(spot: ParkingSpot) = with(binding) {
            val context = root.context
            tvSpotName.text = OperatorParkingSpotLoader.getSpotDisplayName(spot)
            tvAvailability.text = "${spot.available}/${spot.capacity} available"
            val hasAvailability = spot.available > 0
            val statusColor = ContextCompat.getColor(
                context,
                if (hasAvailability) R.color.parking_spot_available else R.color.error,
            )
            tvAvailability.setTextColor(statusColor)
            layoutAvailability.background = ContextCompat.getDrawable(
                context,
                if (hasAvailability) R.drawable.status_soft_active else R.drawable.rounded_background_error,
            )
            viewStatusDot.backgroundTintList = ColorStateList.valueOf(statusColor)
            rbSelectSpot.isChecked = selectedSpotKey == OperatorSpotGrouping.key(spot)
            root.isEnabled = true
            root.alpha = 1f
            root.setOnClickListener {
                setSelectedSpot(spot.id, spot.lotId)
                onSpotSelected(spot)
            }
        }
    }

    private companion object {
        const val TYPE_HEADER = 0
        const val TYPE_SPOT = 1
    }
}

/** Pure lot grouping used by both the selector and its contract tests. */
internal object OperatorSpotGrouping {
    data class SpotKey(val lotId: String, val spotId: String)

    data class Group(
        val lotId: String,
        val title: String,
        val spots: List<ParkingSpot>,
    )

    fun key(spot: ParkingSpot): SpotKey = SpotKey(spot.lotId.trim(), spot.id.trim())

    fun group(spots: List<ParkingSpot>): List<Group> = spots
        .filter { it.id.isNotBlank() && it.lotId.isNotBlank() }
        .groupBy { it.lotId.trim() }
        .toSortedMap()
        .map { (lotId, lotSpots) ->
            Group(
                lotId = lotId,
                title = lotSpots.firstNotNullOfOrNull {
                    it.lotName?.trim()?.takeIf(String::isNotEmpty)
                } ?: "Parking lot $lotId",
                spots = lotSpots.sortedBy(OperatorParkingSpotLoader::getSpotDisplayName),
            )
        }
}
