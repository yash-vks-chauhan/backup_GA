package com.gridee.parking.data.model

import com.google.gson.annotations.SerializedName
import java.util.Date

data class CreateSupportTicketRequest(
    @SerializedName("subject") val subject: String,
    @SerializedName("description") val description: String,
    @SerializedName("priority") val priority: String = "MEDIUM",
    @SerializedName("parkingLotId") val parkingLotId: String? = null,
    @SerializedName("parkingLotName") val parkingLotName: String? = null
)

data class AddSupportTicketMessageRequest(
    @SerializedName("message") val message: String
)

data class SupportTicket(
    @SerializedName("id") val id: String? = null,
    @SerializedName("userId") val userId: String? = null,
    @SerializedName("userEmail") val userEmail: String? = null,
    @SerializedName("userName") val userName: String? = null,
    @SerializedName("subject") val subject: String = "",
    @SerializedName("description") val description: String = "",
    @SerializedName("status") val status: String = "OPEN",
    @SerializedName("priority") val priority: String = "MEDIUM",
    @SerializedName("parkingLotId") val parkingLotId: String? = null,
    @SerializedName("parkingLotName") val parkingLotName: String? = null,
    @SerializedName("messages") val messages: List<SupportTicketMessage> = emptyList(),
    @SerializedName("createdAt") val createdAt: Date? = null,
    @SerializedName("updatedAt") val updatedAt: Date? = null,
    @SerializedName("resolvedAt") val resolvedAt: Date? = null,
    @SerializedName("resolvedBy") val resolvedBy: String? = null
)

data class SupportTicketMessage(
    @SerializedName("messageId") val messageId: String? = null,
    @SerializedName("senderId") val senderId: String? = null,
    @SerializedName("senderRole") val senderRole: String? = null,
    @SerializedName("message") val message: String = "",
    @SerializedName("sentAt") val sentAt: Date? = null
)
