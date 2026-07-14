package com.gridee.parking.config

import com.gridee.parking.data.model.RemoteAppVersions
import kotlin.math.max

object AndroidVersionPolicy {

    fun isBelowMinimum(
        currentVersionCode: Int?,
        currentVersionName: String?,
        versions: RemoteAppVersions,
    ): Boolean {
        if (versions.minAndroidVersionCode > 1 && currentVersionCode != null && currentVersionCode > 0) {
            return currentVersionCode < versions.minAndroidVersionCode
        }
        return isVersionNameBelow(currentVersionName, versions.minAndroidVersion)
    }

    fun isBelowLatest(
        currentVersionCode: Int?,
        currentVersionName: String?,
        versions: RemoteAppVersions,
    ): Boolean {
        if (versions.latestAndroidVersionCode > versions.minAndroidVersionCode &&
            currentVersionCode != null &&
            currentVersionCode > 0
        ) {
            return currentVersionCode < versions.latestAndroidVersionCode
        }
        return isVersionNameBelow(currentVersionName, versions.latestAndroidVersion)
    }

    private fun isVersionNameBelow(currentVersion: String?, requiredVersion: String?): Boolean {
        if (currentVersion.isNullOrBlank() || requiredVersion.isNullOrBlank()) return false

        val currentParts = versionComponents(currentVersion)
        val requiredParts = versionComponents(requiredVersion)
        val count = max(currentParts.size, requiredParts.size)

        for (index in 0 until count) {
            val current = currentParts.getOrElse(index) { 0 }
            val required = requiredParts.getOrElse(index) { 0 }
            if (current < required) return true
            if (current > required) return false
        }

        return false
    }

    private fun versionComponents(version: String): List<Int> {
        return Regex("\\d+")
            .findAll(version.trim())
            .map { it.value.toIntOrNull() ?: 0 }
            .toList()
            .ifEmpty { listOf(0) }
    }
}
