package com.gridee.parking.utils

import android.content.Context
import com.google.gson.Gson
import com.gridee.parking.data.model.User

object UserProfileCache {

    private const val PREFS_NAME = "gridee_profile_cache"
    private const val KEY_USER_ID = "cached_user_id"
    private const val KEY_USER_JSON = "cached_user_json"

    private val gson = Gson()

    fun get(context: Context, userId: String): User? {
        val normalizedUserId = userId.trim()
        if (normalizedUserId.isEmpty()) return null

        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val cachedUserId = prefs.getString(KEY_USER_ID, null)?.trim()
        val cachedUserJson = prefs.getString(KEY_USER_JSON, null)

        if (cachedUserId.isNullOrEmpty() || cachedUserId != normalizedUserId || cachedUserJson.isNullOrEmpty()) {
            return null
        }

        return runCatching { gson.fromJson(cachedUserJson, User::class.java) }.getOrNull()
    }

    fun save(context: Context, user: User) {
        val userId = user.id?.trim().orEmpty()
        if (userId.isEmpty()) return

        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit()
            .putString(KEY_USER_ID, userId)
            .putString(KEY_USER_JSON, gson.toJson(user))
            .apply()
    }

    fun clear(context: Context) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .clear()
            .apply()
    }
}
