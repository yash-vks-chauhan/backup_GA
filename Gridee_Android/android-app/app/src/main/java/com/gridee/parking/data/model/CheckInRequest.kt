package com.gridee.parking.data.model

import com.google.gson.annotations.SerializedName

/**
 * Request model for check-in/check-out operations
 * Supports multiple authentication modes: QR_CODE, VEHICLE_NUMBER, PIN
 */
data class CheckInRequest(
    @SerializedName("mode")
    val mode: CheckInMode,
    
    @SerializedName("qrCode")
    val qrCode: String? = null,
    
    @SerializedName("vehicleNumber")
    val vehicleNumber: String? = null,
    
    @SerializedName("pin")
    val pin: String? = null
) {
    init {
        // Validate that appropriate field is provided for the mode
        when (mode) {
            CheckInMode.QR_CODE -> require(!qrCode.isNullOrBlank()) { 
                "QR code is required when mode is QR_CODE" 
            }
            CheckInMode.VEHICLE_NUMBER -> require(!vehicleNumber.isNullOrBlank()) { 
                "Vehicle number is required when mode is VEHICLE_NUMBER" 
            }
            CheckInMode.PIN -> require(!pin.isNullOrBlank()) { 
                "PIN is required when mode is PIN" 
            }
        }
    }
}

