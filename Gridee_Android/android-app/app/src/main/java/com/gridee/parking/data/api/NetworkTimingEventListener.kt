package com.gridee.parking.data.api

import android.content.Context
import android.os.Bundle
import com.gridee.parking.utils.AppLog
import com.google.firebase.analytics.FirebaseAnalytics
import com.gridee.parking.BuildConfig
import java.io.IOException
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Proxy
import java.util.UUID
import okhttp3.Call
import okhttp3.EventListener
import okhttp3.Handshake
import okhttp3.Protocol

internal data class NetworkPhaseTiming(
    val traceId: String,
    val category: String,
    val method: String,
    val result: String,
    val totalMs: Long,
    val dnsMs: Long,
    val connectMs: Long,
    val tlsMs: Long,
    val uploadMs: Long,
    val serverWaitMs: Long,
    val downloadMs: Long,
)

internal fun interface NetworkTimingSink {
    fun emit(timing: NetworkPhaseTiming)
}

/** Nanosecond phase accumulator kept independent of OkHttp for deterministic tests. */
internal class NetworkPhaseAccumulator(
    private val clockNs: () -> Long = System::nanoTime,
) {
    private var callStartNs: Long? = null
    private var dnsStartNs: Long? = null
    private var dnsMs = NOT_RECORDED
    private var connectStartNs: Long? = null
    private var connectMs = NOT_RECORDED
    private var tlsStartNs: Long? = null
    private var tlsMs = NOT_RECORDED
    private var uploadStartNs: Long? = null
    private var uploadEndNs: Long? = null
    private var responseHeadersStartNs: Long? = null
    private var downloadStartNs: Long? = null
    private var downloadMs = NOT_RECORDED

    fun callStarted() {
        callStartNs = clockNs()
    }

    fun dnsStarted() {
        dnsStartNs = clockNs()
    }

    fun dnsEnded() {
        dnsMs = elapsedMs(dnsStartNs, clockNs())
    }

    fun connectStarted() {
        connectStartNs = clockNs()
    }

    fun connectEnded() {
        closeConnectAttempt(clockNs())
    }

    fun tlsStarted() {
        val nowNs = clockNs()
        // On HTTPS OkHttp emits secureConnectStart before connectEnd. Closing the TCP phase here
        // prevents connect_ms from including (and therefore double-counting) the TLS handshake.
        closeConnectAttempt(nowNs)
        tlsStartNs = nowNs
    }

    fun tlsEnded() {
        closeTlsAttempt(clockNs())
    }

    fun connectionFailed() {
        val nowNs = clockNs()
        closeConnectAttempt(nowNs)
        closeTlsAttempt(nowNs)
    }

    fun uploadStarted() {
        if (uploadStartNs == null) uploadStartNs = clockNs()
    }

    fun uploadEnded() {
        uploadEndNs = clockNs()
    }

    fun responseHeadersStarted() {
        responseHeadersStartNs = clockNs()
    }

    fun downloadStarted() {
        downloadStartNs = clockNs()
    }

    fun downloadEnded() {
        downloadMs = elapsedMs(downloadStartNs, clockNs())
    }

    fun finish(
        method: String,
        result: String,
        category: String = "other",
        traceId: String = "",
    ): NetworkPhaseTiming {
        val finishedAtNs = clockNs()
        val requestFinishedAtNs = uploadEndNs ?: uploadStartNs
        return NetworkPhaseTiming(
            traceId = traceId.take(32),
            category = category.take(24),
            method = method.take(12),
            result = result.take(20),
            totalMs = elapsedMs(callStartNs, finishedAtNs),
            dnsMs = dnsMs,
            connectMs = connectMs,
            tlsMs = tlsMs,
            uploadMs = elapsedMs(uploadStartNs, uploadEndNs),
            serverWaitMs = elapsedMs(requestFinishedAtNs, responseHeadersStartNs),
            downloadMs = downloadMs,
        )
    }

    private fun elapsedMs(startNs: Long?, endNs: Long?): Long {
        if (startNs == null || endNs == null) return NOT_RECORDED
        return ((endNs - startNs).coerceAtLeast(0L) / NANOS_PER_MS)
    }

    private fun closeConnectAttempt(endNs: Long) {
        val startNs = connectStartNs ?: return
        connectMs = addDuration(connectMs, elapsedMs(startNs, endNs))
        connectStartNs = null
    }

    private fun closeTlsAttempt(endNs: Long) {
        val startNs = tlsStartNs ?: return
        tlsMs = addDuration(tlsMs, elapsedMs(startNs, endNs))
        tlsStartNs = null
    }

    private fun addDuration(existingMs: Long, durationMs: Long): Long {
        if (durationMs == NOT_RECORDED) return existingMs
        return if (existingMs == NOT_RECORDED) durationMs else existingMs + durationMs
    }

    private companion object {
        const val NOT_RECORDED = -1L
        const val NANOS_PER_MS = 1_000_000L
    }
}

/** Opaque, process-local request metadata attached with Retrofit @Tag; never sent over HTTP. */
data class ScannerNetworkTraceTag(val id: String) {
    companion object {
        fun create(scannerTraceId: String?): ScannerNetworkTraceTag {
            val safeScannerId = scannerTraceId
                ?.trim()
                ?.takeIf { it.matches(Regex("[A-Za-z0-9_-]{1,32}")) }
            return ScannerNetworkTraceTag(
                safeScannerId ?: UUID.randomUUID().toString().replace("-", "").take(16)
            )
        }
    }
}

/** Emits no URL, path, query, headers, body, booking ID, QR value, or vehicle number. */
internal class PrivacySafeNetworkTimingEventListener private constructor(
    private val call: Call,
    private val sink: NetworkTimingSink,
) : EventListener() {
    private val phases = NetworkPhaseAccumulator()
    private var emitted = false

    override fun callStart(call: Call) = phases.callStarted()
    override fun dnsStart(call: Call, domainName: String) = phases.dnsStarted()
    override fun dnsEnd(call: Call, domainName: String, inetAddressList: List<InetAddress>) = phases.dnsEnded()
    override fun connectStart(call: Call, inetSocketAddress: InetSocketAddress, proxy: Proxy) =
        phases.connectStarted()
    override fun secureConnectStart(call: Call) = phases.tlsStarted()
    override fun secureConnectEnd(call: Call, handshake: Handshake?) = phases.tlsEnded()
    override fun connectEnd(
        call: Call,
        inetSocketAddress: InetSocketAddress,
        proxy: Proxy,
        protocol: Protocol?,
    ) = phases.connectEnded()
    override fun connectFailed(
        call: Call,
        inetSocketAddress: InetSocketAddress,
        proxy: Proxy,
        protocol: Protocol?,
        ioe: IOException,
    ) = phases.connectionFailed()
    override fun requestHeadersStart(call: Call) = phases.uploadStarted()
    override fun requestHeadersEnd(call: Call, request: okhttp3.Request) = phases.uploadEnded()
    override fun requestBodyEnd(call: Call, byteCount: Long) = phases.uploadEnded()
    override fun responseHeadersStart(call: Call) = phases.responseHeadersStarted()
    override fun responseBodyStart(call: Call) = phases.downloadStarted()
    override fun responseBodyEnd(call: Call, byteCount: Long) = phases.downloadEnded()
    override fun callEnd(call: Call) = emit("success")
    override fun callFailed(call: Call, ioe: IOException) = emit("network_failure")

    private fun emit(result: String) {
        if (emitted) return
        emitted = true
        sink.emit(
            phases.finish(
                method = call.request().method,
                result = result,
                traceId = call.request().tag(ScannerNetworkTraceTag::class.java)?.id.orEmpty(),
                category = PrivacySafeNetworkCategory.classify(
                    call.request().url.encodedPath,
                ),
            )
        )
    }

    class Factory(context: Context) : EventListener.Factory {
        private val sink = FirebaseNetworkTimingSink(context.applicationContext)

        override fun create(call: Call): EventListener {
            return PrivacySafeNetworkTimingEventListener(call, sink)
        }
    }
}

private class FirebaseNetworkTimingSink(context: Context) : NetworkTimingSink {
    private val appContext = context.applicationContext
    private val analytics by lazy { FirebaseAnalytics.getInstance(appContext) }

    override fun emit(timing: NetworkPhaseTiming) {
        AppLog.d(TAG) {
            "api_network_phase category=${timing.category} method=${timing.method} " +
                "result=${timing.result} total_ms=${timing.totalMs} dns_ms=${timing.dnsMs} " +
                "connect_ms=${timing.connectMs} tls_ms=${timing.tlsMs} " +
                "server_wait_ms=${timing.serverWaitMs} download_ms=${timing.downloadMs}"
        }
        if (BuildConfig.DEBUG || !analyticsEnabled()) return
        analytics.logEvent(EVENT_NAME, Bundle().apply {
            putString("trace_id", timing.traceId)
            putString("category", timing.category)
            putString("method", timing.method)
            putString("result", timing.result)
            putLong("total_ms", timing.totalMs)
            putLong("dns_ms", timing.dnsMs)
            putLong("connect_ms", timing.connectMs)
            putLong("tls_ms", timing.tlsMs)
            putLong("upload_ms", timing.uploadMs)
            putLong("server_wait_ms", timing.serverWaitMs)
            putLong("download_ms", timing.downloadMs)
        })
    }

    private fun analyticsEnabled(): Boolean {
        return appContext
            .getSharedPreferences(PRIVACY_PREFS, Context.MODE_PRIVATE)
            .getBoolean(ANALYTICS_ENABLED, true)
    }

    private companion object {
        const val TAG = "NetworkTiming"
        const val EVENT_NAME = "api_network_phase"
        const val PRIVACY_PREFS = "gridee_privacy_prefs"
        const val ANALYTICS_ENABLED = "analytics"
    }
}

/** Converts a URL path to a fixed allowlisted label; the path itself is never logged or emitted. */
internal object PrivacySafeNetworkCategory {
    fun classify(encodedPath: String): String {
        val path = encodedPath.lowercase()
        if (!path.contains("/api/operator/")) return "other"
        return when {
            path.endsWith("/bookings/checkin") -> "operator_checkin"
            path.endsWith("/bookings/checkout") -> "operator_checkout"
            else -> "operator_other"
        }
    }
}
