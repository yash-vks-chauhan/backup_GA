package com.gridee.parking.ui.qr

internal data class ScannerSpotState(
    val spotId: String?,
    val spotName: String?,
    val lotId: String?,
)

/** Restores a scanner-selected spot only inside the operator's current authenticated lot. */
internal object ScannerSpotStatePolicy {
    fun resolve(
        assignedLotId: String?,
        restored: ScannerSpotState?,
        launched: ScannerSpotState,
    ): ScannerSpotState {
        val authoritativeLotId = normalizeId(assignedLotId)
            ?: return ScannerSpotState(spotId = null, spotName = null, lotId = null)
        val restoredCandidate = restored?.takeIf { normalizeId(it.spotId) != null }
        val candidate = restoredCandidate ?: launched
        if (normalizeId(candidate.lotId) != authoritativeLotId) {
            return ScannerSpotState(
                spotId = null,
                spotName = null,
                lotId = authoritativeLotId,
            )
        }
        return ScannerSpotState(
            spotId = normalizeId(candidate.spotId),
            spotName = candidate.spotName?.trim()?.takeIf(String::isNotEmpty),
            lotId = authoritativeLotId,
        )
    }

    fun normalizeId(raw: String?): String? = raw?.trim()?.takeIf {
        it.isNotEmpty() &&
            !it.equals("Parking Lot", ignoreCase = true) &&
            !it.equals("null", ignoreCase = true) &&
            !it.equals("nil", ignoreCase = true)
    }
}
