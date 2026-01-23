package com.gridee.parking.data.model

data class Vehicle(
    val id: String,
    val number: String,
    val type: String,
    val brand: String,
    val model: String,
    val isDefault: Boolean = false
) {
    fun getDisplayName(): String {
        return "$brand $model"
    }
    
    fun getDisplayType(): String {
        return "$type • $brand $model"
    }
}
