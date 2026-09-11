package com.gridee.parking.ui.qr

import java.util.Locale

/**
 * Fail-safe Android scanner controls backed by the app config's existing free-form settings.
 * Invalid or absent production values always fall back to the locally tested defaults.
 */
internal data class ScannerFeatureControls(
    val automaticRecognitionEnabled: Boolean = true,
    val successResultHoldMs: Long = 850L,
    val errorResultHoldMs: Long = 1_800L,
    val cleanPlateConsensusMatches: Int = 2,
    val correctedPlateConsensusMatches: Int = 3,
    val plateConsensusMaxGapMs: Long = 900L,
    val analysisWidth: Int = 1_280,
    val analysisHeight: Int = 720,
    val plateInitialZoomRatio: Float = 1.12f,
    val qrAutoZoomEnabled: Boolean = true,
    val reflectivePlateExposureCompensationEnabled: Boolean = true,
    val forceManualPlateConfirmation: Boolean = false,
) {
    /**
     * Returns true when accepting a result that started under [previous] could bypass the newly
     * selected recognition policy. Presentation-only hold durations intentionally do not restart
     * or invalidate the camera pipeline.
     */
    fun invalidatesRecognitionWorkComparedTo(previous: ScannerFeatureControls): Boolean =
        cleanPlateConsensusMatches != previous.cleanPlateConsensusMatches ||
            correctedPlateConsensusMatches != previous.correctedPlateConsensusMatches ||
            plateConsensusMaxGapMs != previous.plateConsensusMaxGapMs ||
            analysisWidth != previous.analysisWidth ||
            analysisHeight != previous.analysisHeight ||
            plateInitialZoomRatio != previous.plateInitialZoomRatio ||
            qrAutoZoomEnabled != previous.qrAutoZoomEnabled ||
            reflectivePlateExposureCompensationEnabled !=
                previous.reflectivePlateExposureCompensationEnabled ||
            forceManualPlateConfirmation != previous.forceManualPlateConfirmation

    companion object {
        private const val SETTINGS_GROUP = "operatorScanner"

        fun from(
            customSettings: Map<String, Any> = emptyMap(),
            featureToggles: Map<String, Boolean> = emptyMap(),
        ): ScannerFeatureControls {
            val settings = flattenScannerSettings(customSettings)
            val toggles = featureToggles.entries.associate { normalizeToggleKey(it.key) to it.value }

            fun value(vararg keys: String): Any? = keys
                .asSequence()
                .map(::normalizeKey)
                .mapNotNull(settings::get)
                .firstOrNull()

            fun Number.isFiniteConfigNumber(): Boolean = toDouble().isFinite()

            fun longValue(default: Long, range: LongRange, vararg keys: String): Long {
                val parsed = when (val raw = value(*keys)) {
                    is Number -> raw.takeIf { it.isFiniteConfigNumber() }?.toLong()
                    is String -> raw.trim().toLongOrNull()
                    else -> null
                } ?: default
                return parsed.coerceIn(range)
            }

            fun intValue(default: Int, range: IntRange, vararg keys: String): Int {
                val parsed = when (val raw = value(*keys)) {
                    is Number -> raw.takeIf { it.isFiniteConfigNumber() }?.toInt()
                    is String -> raw.trim().toIntOrNull()
                    else -> null
                } ?: default
                return parsed.coerceIn(range)
            }

            fun booleanValue(default: Boolean, vararg keys: String): Boolean {
                return when (val raw = value(*keys)) {
                    is Boolean -> raw
                    is Number -> raw.takeIf { it.isFiniteConfigNumber() }
                        ?.let { it.toInt() != 0 } ?: default
                    is String -> raw.trim().lowercase(Locale.ROOT).let {
                        when (it) {
                            "true", "1", "yes", "on" -> true
                            "false", "0", "no", "off" -> false
                            else -> default
                        }
                    }
                    else -> default
                }
            }

            fun floatValue(default: Float, range: ClosedFloatingPointRange<Float>, vararg keys: String): Float {
                val parsed = when (val raw = value(*keys)) {
                    is Number -> raw.takeIf { it.isFiniteConfigNumber() }?.toFloat()
                    is String -> raw.trim().toFloatOrNull()
                    else -> null
                }?.takeIf(Float::isFinite) ?: default
                return parsed.coerceIn(range.start, range.endInclusive)
            }

            fun featureToggle(name: String): Boolean? = toggles[normalizeToggleKey(name)]

            val requestedResolution = value(
                "operatorScannerAnalysisResolution",
                "analysisResolution",
            )?.toString()?.trim()?.lowercase(Locale.ROOT)
            val (analysisWidth, analysisHeight) = when (requestedResolution) {
                "480p", "640x480", "minimum" -> 640 to 480
                "540p", "960x540", "low" -> 960 to 540
                else -> 1_280 to 720
            }

            val qrAutoZoom = featureToggle("operatorScannerQrAutoZoom")
                ?: booleanValue(
                    true,
                    "operatorScannerQrAutoZoomEnabled",
                    "qrAutoZoomEnabled",
                )
            val automaticRecognition = featureToggle(AUTOMATIC_RECOGNITION_FEATURE_KEY)
                ?: booleanValue(
                    true,
                    "operatorScannerAutomaticRecognitionEnabled",
                    "automaticRecognitionEnabled",
                )
            val plateAutoSubmit = featureToggle("operatorScannerPlateAutoSubmit")
                ?: booleanValue(
                    true,
                    "operatorScannerPlateAutoSubmitEnabled",
                    "plateAutoSubmitEnabled",
                )
            val forceManualConfirmation = booleanValue(
                false,
                "operatorScannerForceManualPlateConfirmation",
                "forceManualPlateConfirmation",
            ) || !plateAutoSubmit
            val reflectiveExposureCompensation =
                featureToggle("operatorScannerReflectivePlateExposureCompensation")
                    ?: booleanValue(
                        true,
                        "operatorScannerReflectivePlateExposureCompensationEnabled",
                        "reflectivePlateExposureCompensationEnabled",
                    )

            return ScannerFeatureControls(
                automaticRecognitionEnabled = automaticRecognition,
                successResultHoldMs = longValue(
                    850L,
                    600L..1_500L,
                    "operatorScannerSuccessResultHoldMs",
                    "successResultHoldMs",
                ),
                errorResultHoldMs = longValue(
                    1_800L,
                    1_000L..5_000L,
                    "operatorScannerErrorResultHoldMs",
                    "errorResultHoldMs",
                ),
                cleanPlateConsensusMatches = intValue(
                    2,
                    2..3,
                    "operatorScannerCleanPlateConsensusMatches",
                    "cleanPlateConsensusMatches",
                ),
                correctedPlateConsensusMatches = intValue(
                    3,
                    3..5,
                    "operatorScannerCorrectedPlateConsensusMatches",
                    "correctedPlateConsensusMatches",
                ),
                plateConsensusMaxGapMs = longValue(
                    900L,
                    500L..1_500L,
                    "operatorScannerPlateConsensusMaxGapMs",
                    "plateConsensusMaxGapMs",
                ),
                analysisWidth = analysisWidth,
                analysisHeight = analysisHeight,
                plateInitialZoomRatio = floatValue(
                    1.12f,
                    1.0f..1.35f,
                    "operatorScannerPlateInitialZoomRatio",
                    "plateInitialZoomRatio",
                ),
                qrAutoZoomEnabled = qrAutoZoom,
                reflectivePlateExposureCompensationEnabled = reflectiveExposureCompensation,
                forceManualPlateConfirmation = forceManualConfirmation,
            )
        }

        private fun flattenScannerSettings(source: Map<String, Any>): Map<String, Any> {
            val flattened = linkedMapOf<String, Any>()
            source.forEach { (key, value) ->
                flattened[normalizeKey(key)] = value
                if (normalizeKey(key) == normalizeKey(SETTINGS_GROUP) && value is Map<*, *>) {
                    value.forEach { (nestedKey, nestedValue) ->
                        if (nestedKey != null && nestedValue != null) {
                            flattened[normalizeKey(nestedKey.toString())] = nestedValue
                        }
                    }
                }
            }
            return flattened
        }

        private fun normalizeKey(raw: String): String = raw
            .trim()
            .replace("-", "")
            .replace("_", "")
            .replace(".", "")
            .lowercase(Locale.ROOT)

        private fun normalizeToggleKey(raw: String): String {
            val normalized = normalizeKey(raw)
            return when {
                normalized.endsWith("featureenabled") -> normalized.removeSuffix("featureenabled")
                normalized.endsWith("enabled") -> normalized.removeSuffix("enabled")
                else -> normalized
            }
        }

        /**
         * Optional free-form app-config key. It deliberately defaults to enabled so a missing,
         * stale, or malformed config cannot silently remove the production scanner. Turning it
         * off is an Android-side emergency stop for new automatic Plate and QR recognition only;
         * manual plate entry remains available.
         */
        internal const val AUTOMATIC_RECOGNITION_FEATURE_KEY =
            "operatorScannerAutomaticRecognition"
    }
}
