package com.gridee.parking.utils

import android.content.Context

data class PendingProfileUpdate(
    val email: String,
    val name: String,
    val phone: String,
    val parkingLotName: String?
)

class PendingProfileUpdateStore(context: Context) {

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun save(update: PendingProfileUpdate) {
        prefs.edit()
            .putString(KEY_EMAIL, update.email)
            .putString(KEY_NAME, update.name)
            .putString(KEY_PHONE, update.phone)
            .putString(KEY_PARKING_LOT, update.parkingLotName)
            .apply()
    }

    fun get(): PendingProfileUpdate? {
        val email = prefs.getString(KEY_EMAIL, null)?.trim().orEmpty()
        val name = prefs.getString(KEY_NAME, null)?.trim().orEmpty()
        val phone = prefs.getString(KEY_PHONE, null)?.trim().orEmpty()
        val parkingLot = prefs.getString(KEY_PARKING_LOT, null)?.trim()

        if (email.isBlank() || (name.isBlank() && phone.isBlank() && parkingLot.isNullOrBlank())) {
            return null
        }

        return PendingProfileUpdate(
            email = email,
            name = name,
            phone = phone,
            parkingLotName = parkingLot?.takeIf { it.isNotBlank() }
        )
    }

    fun clear() {
        prefs.edit()
            .remove(KEY_EMAIL)
            .remove(KEY_NAME)
            .remove(KEY_PHONE)
            .remove(KEY_PARKING_LOT)
            .apply()
    }

    companion object {
        private const val PREFS_NAME = "gridee_pending_profile_update"
        private const val KEY_EMAIL = "pending_email"
        private const val KEY_NAME = "pending_name"
        private const val KEY_PHONE = "pending_phone"
        private const val KEY_PARKING_LOT = "pending_parking_lot"
    }
}
