package com.gridee.parking.ui.lot

import com.gridee.parking.data.model.ParkingLot

/**
 * A place category (backend organizationType) with a friendly label, lot count and
 * the live spot totals aggregated across its lots (what the row's dial shows).
 */
data class LotCategory(
    val type: String,
    val label: String,
    val count: Int,
    val totalSpots: Int = 0,
    val availableSpots: Int = 0
)

object LotCategories {

    // Display order, most common first. Unknown types sort last as "Other".
    private val ORDER = listOf("COLLEGE", "PUBLIC_PLACE", "SOCIETY", "CORPORATE", "EVENT", "CUSTOM")

    fun label(type: String): String = when (type.uppercase()) {
        "COLLEGE" -> "College / Campus"
        "PUBLIC_PLACE" -> "Public Place"
        "SOCIETY" -> "Society"
        "CORPORATE" -> "Corporate"
        "EVENT" -> "Event"
        else -> "Other"
    }

    /** Groups lots into the categories that actually have lots, ordered for display. */
    fun fromLots(lots: List<ParkingLot>): List<LotCategory> {
        val grouped = LinkedHashMap<String, MutableList<ParkingLot>>()
        for (lot in lots) {
            val type = lot.organizationType?.trim()?.uppercase()
                ?.takeIf { it.isNotEmpty() } ?: "CUSTOM"
            grouped.getOrPut(type) { mutableListOf() }.add(lot)
        }
        return grouped.entries
            .map { (type, group) ->
                LotCategory(
                    type = type,
                    label = label(type),
                    count = group.size,
                    totalSpots = group.sumOf { it.totalSpots.coerceAtLeast(0) },
                    availableSpots = group.sumOf { it.availableSpots.coerceAtLeast(0) }
                )
            }
            .sortedBy { ORDER.indexOf(it.type).let { i -> if (i < 0) Int.MAX_VALUE else i } }
    }
}
