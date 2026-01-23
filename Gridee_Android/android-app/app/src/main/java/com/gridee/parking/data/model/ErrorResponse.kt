package com.gridee.parking.data.model

import com.google.gson.annotations.SerializedName

/**
 * Standard error response from the backend API
 */
data class ErrorResponse(
    @SerializedName("timestamp")
    val timestamp: String? = null,
    
    @SerializedName("status")
    val status: Int? = null,
    
    @SerializedName("error")
    val error: String? = null,
    
    @SerializedName("errorCode")
    val errorCode: String? = null,
    
    @SerializedName("message")
    val message: String? = null,
    
    @SerializedName("path")
    val path: String? = null,
    
    @SerializedName("traceId")
    val traceId: String? = null,
    
    @SerializedName("requestId")
    val requestId: String? = null,
    
    @SerializedName("validationErrors")
    val validationErrors: Map<String, String>? = null
)
