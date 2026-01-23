package com.gridee.parking.data.model

import com.google.gson.annotations.SerializedName

/**
 * Check-in authentication modes
 * Matches the backend CheckInMode enum
 */
enum class CheckInMode {
    @SerializedName("QR_CODE")
    QR_CODE,
    
    @SerializedName("VEHICLE_NUMBER")
    VEHICLE_NUMBER,
    
    @SerializedName("PIN")
    PIN;
    
    companion object {
        fun fromValue(value: String): CheckInMode {
            return values().find { 
                it.name.equals(value, ignoreCase = true) 
            } ?: throw IllegalArgumentException("Invalid CheckInMode: $value")
        }
    }
}
