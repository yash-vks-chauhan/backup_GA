package com.gridee.parking.data.model

data class ParkingSpot(
    val id: String,
    val lotId: String = "",
    val lotName: String? = null, // Legacy backend sometimes returns lotName instead of lotId
    val spotCode: String? = null, // Friendly spot identifier from backend (spotId)
    val slotId: Int? = null, // New backend field: logical slot identifier
    val slotName: String? = null, // Backend field: "morning"/"afternoon" slot type (legacy: "evening")
    val name: String? = null,  // The actual spot name like "TP Avenue", "Medical College"
    val zoneName: String? = null,  // Keep for backwards compatibility
    val capacity: Int = 0,
    val available: Int = 0,
    val status: String,
    val bookingRate: Double = 0.0
)
