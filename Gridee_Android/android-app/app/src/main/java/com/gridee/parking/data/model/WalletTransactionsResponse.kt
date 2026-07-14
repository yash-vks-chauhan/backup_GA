package com.gridee.parking.data.model

import com.google.gson.annotations.SerializedName

data class WalletTransactionsResponse(
    @SerializedName("content")
    val content: List<WalletTransaction> = emptyList(),
    @SerializedName("totalElements")
    val totalElements: Long? = null,
    @SerializedName("totalPages")
    val totalPages: Int? = null,
    @SerializedName("number")
    val pageNumber: Int? = null,
    @SerializedName("last")
    val last: Boolean? = null
) {
    val transactions: List<WalletTransaction>
        get() = content
}
