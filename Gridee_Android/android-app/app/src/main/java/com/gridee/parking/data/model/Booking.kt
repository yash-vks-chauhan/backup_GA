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

    /** Booking mode the lot's policy was in when this was created: DAILY or FLEXIBLE. */
    @SerializedName("bookingType")
    val bookingType: String? = null,

    /**
     * Who was meant to pay — see `ParkingLotBookingPolicy.PAYMENT_*`. Recorded by the backend at
     * creation time, but note the backend deducts from the user's wallet regardless of this value.
     */
    @SerializedName("paymentModel")
    val paymentModel: String? = null,

    // Tenant context stamped on the booking by the backend.
    @SerializedName("organizationId")
    val organizationId: String? = null,

    @SerializedName("organizationName")
    val organizationName: String? = null,

    @SerializedName("organizationType")
    val organizationType: String? = null,

    @SerializedName("locationId")
    val locationId: String? = null,

    @SerializedName("locationName")
    val locationName: String? = null,

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
