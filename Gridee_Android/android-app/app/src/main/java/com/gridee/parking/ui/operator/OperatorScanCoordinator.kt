package com.gridee.parking.ui.operator

import java.util.Locale

private data class OperatorScanIdentity(
    val inputMode: String,
    val identifier: String,
    val parkingLotId: String,
)

/**
 * Process-wide guard for operator scans.
 *
 * It serializes mutations and remembers recently submitted scan identities independently of the
 * Activity that presented them. This keeps a mode/spot change, Activity recreation, or rapid
 * Plate/QR switch from bypassing the active-operation lock or the seven-second cooldown.
 * Server-side operation reconciliation remains intentionally out of scope until the backend has
 * an idempotency contract.
 */
internal class OperatorScanCoordinator(
    private val cooldownMs: Long = DEFAULT_COOLDOWN_MS,
) {
    private var activeRequestId: Long? = null
    private val recentScans = LinkedHashMap<OperatorScanIdentity, Long>()

    @Synchronized
    fun tryStart(
        key: OperatorOperationKey,
        requestId: Long,
        elapsedRealtimeMs: Long,
    ): OperatorOperationStart {
        activeRequestId?.let { return OperatorOperationStart.Busy(it) }
        prune(elapsedRealtimeMs)

        // Debounce the scan itself, independently of which operation button or spot was selected.
        // The returned booking is still checked against the full operation scope separately.
        val scanIdentity = OperatorScanIdentity(
            inputMode = key.inputMode,
            identifier = key.identifier,
            parkingLotId = key.parkingLotId,
        )
        val lastStartedAt = recentScans[scanIdentity]
        if (lastStartedAt != null) {
            val remainingMs = cooldownMs - (elapsedRealtimeMs - lastStartedAt)
            if (remainingMs > 0L) return OperatorOperationStart.CoolingDown(remainingMs)
        }

        recentScans[scanIdentity] = elapsedRealtimeMs
        trimToSize()
        activeRequestId = requestId
        return OperatorOperationStart.Started
    }

    @Synchronized
    fun finish(requestId: Long) {
        if (activeRequestId == requestId) activeRequestId = null
    }

    /**
     * Records both identifiers returned for one successful booking. This makes a quick
     * Plate-to-QR (or QR-to-Plate) switch hit the same local cooldown without storing either
     * identifier outside process memory.
     */
    @Synchronized
    fun rememberBookingAliases(
        parkingLotId: String,
        vehicleNumber: String?,
        qrAliases: Iterable<String?>,
        elapsedRealtimeMs: Long,
    ) {
        prune(elapsedRealtimeMs)
        val normalizedLotId = parkingLotId.trim()
        vehicleNumber
            ?.filter(Char::isLetterOrDigit)
            ?.uppercase(Locale.ROOT)
            ?.takeIf(String::isNotEmpty)
            ?.let { vehicle ->
                recentScans[
                    OperatorScanIdentity(VEHICLE_INPUT_MODE, vehicle, normalizedLotId)
                ] = elapsedRealtimeMs
            }
        qrAliases.asSequence()
            .mapNotNull { it?.trim()?.takeIf(String::isNotEmpty) }
            .distinct()
            .forEach { qr ->
                recentScans[
                    OperatorScanIdentity(QR_INPUT_MODE, qr, normalizedLotId)
                ] = elapsedRealtimeMs
            }
        trimToSize()
    }

    @Synchronized
    fun hasActiveOperation(): Boolean = activeRequestId != null

    @Synchronized
    fun currentRequestId(): Long? = activeRequestId

    private fun prune(elapsedRealtimeMs: Long) {
        val iterator = recentScans.entries.iterator()
        while (iterator.hasNext()) {
            if (elapsedRealtimeMs - iterator.next().value >= cooldownMs) iterator.remove()
        }
    }

    private fun trimToSize() {
        while (recentScans.size > MAX_RECENT_SCANS) {
            recentScans.entries.iterator().let { iterator ->
                if (iterator.hasNext()) {
                    iterator.next()
                    iterator.remove()
                }
            }
        }
    }

    private companion object {
        const val DEFAULT_COOLDOWN_MS = 7_000L
        const val VEHICLE_INPUT_MODE = "VEHICLE_NUMBER"
        const val QR_INPUT_MODE = "QR_CODE"
        const val MAX_RECENT_SCANS = 64
    }
}
