package com.gridee.parking.data.model

import com.google.gson.annotations.SerializedName
import java.util.Date

data class Booking(
    @SerializedName(value = "id", alternate = ["_id", "bookingId"])
    val id: String? = null,
    
    @SerializedName("userId")
    val userId: String,
    
    @SerializedName("lotId")
    val lotId: String,
    
    @SerializedName("spotId")
    val spotId: String,
    
    @SerializedName("status")
    val status: String, // "pending", "active", "cancelled", "completed"
    
    @SerializedName("amount")
    val amount: Double = 0.0,
    
    @SerializedName("qrCode")
    val qrCode: String? = null,
    
    @SerializedName("checkInTime")
    val checkInTime: Date? = null,
    
    @SerializedName("checkOutTime")
    val checkOutTime: Date? = null,
    
    @SerializedName("createdAt")
    val createdAt: Date? = null,

    @SerializedName("updatedAt")
    val updatedAt: Date? = null,

    @SerializedName("cancelledAt")
    val cancelledAt: Date? = null,
    
    @SerializedName("vehicleNumber")
    val vehicleNumber: String? = null,

    // QR/check-in fields
    @SerializedName("qrCodeScanned")
    val qrCodeScanned: Boolean = false,

    @SerializedName("actualCheckInTime")
    val actualCheckInTime: Date? = null,

    @SerializedName("actualCheckOutTime")
    val actualCheckOutTime: Date? = null,

    @SerializedName("autoCompleted")
    val autoCompleted: Boolean? = false,

    @SerializedName("checkInOperatorId")
    val checkInOperatorId: String? = null,

    @SerializedName("checkOutOperatorId")
    val checkOutOperatorId: String? = null,

    @SerializedName("lotName")
    val lotName: String? = null,

    @SerializedName("endingReminderSent")
    val endingReminderSent: Boolean? = null,

    @SerializedName("endingReminderSentAt")
    val endingReminderSentAt: Date? = null,

    @SerializedName("balanceSettled")
    val balanceSettled: Boolean? = null,

    @SerializedName("archivedAt")
    val archivedAt: Date? = null
)
