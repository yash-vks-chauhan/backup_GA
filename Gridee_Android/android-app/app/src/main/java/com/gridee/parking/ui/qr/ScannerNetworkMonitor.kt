package com.gridee.parking.ui.qr

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities

internal enum class ScannerNetworkReadiness {
    READY,
    DEGRADED,
    OFFLINE,
}

internal data class ScannerNetworkSnapshot(
    val readiness: ScannerNetworkReadiness,
    val transport: String,
) {
    val canSubmitMutation: Boolean
        get() = readiness != ScannerNetworkReadiness.OFFLINE
}

internal object ScannerNetworkClassifier {
    fun classify(
        hasActiveNetwork: Boolean,
        hasInternetCapability: Boolean,
        isValidated: Boolean,
        downstreamKbps: Int,
        transport: String,
    ): ScannerNetworkSnapshot {
        if (!hasActiveNetwork || !hasInternetCapability || !isValidated) {
            return ScannerNetworkSnapshot(ScannerNetworkReadiness.OFFLINE, transport)
        }
        val readiness = if (downstreamKbps in 1..256) {
            ScannerNetworkReadiness.DEGRADED
        } else {
            ScannerNetworkReadiness.READY
        }
        return ScannerNetworkSnapshot(readiness, transport)
    }
}

/** Best-effort Android network preflight. The backend remains the final authority. */
internal class ScannerNetworkMonitor(context: Context) {
    private val connectivityManager = context.applicationContext
        .getSystemService(ConnectivityManager::class.java)

    fun snapshot(): ScannerNetworkSnapshot {
        val manager = connectivityManager
            ?: return ScannerNetworkSnapshot(ScannerNetworkReadiness.OFFLINE, "unknown")
        val network = manager.activeNetwork
            ?: return ScannerNetworkSnapshot(ScannerNetworkReadiness.OFFLINE, "offline")
        val capabilities = manager.getNetworkCapabilities(network)
            ?: return ScannerNetworkSnapshot(ScannerNetworkReadiness.OFFLINE, "unknown")
        val transport = when {
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_VPN) -> "vpn"
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "wifi"
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> "cellular"
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> "ethernet"
            else -> "other"
        }
        return ScannerNetworkClassifier.classify(
            hasActiveNetwork = true,
            hasInternetCapability = capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET),
            isValidated = capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED),
            downstreamKbps = capabilities.linkDownstreamBandwidthKbps,
            transport = transport,
        )
    }
}
