package com.gridee.parking.ui.qr

import kotlin.math.roundToInt

internal enum class PlateCandidateSource {
    LINE,
    BLOCK,
    FULL_TEXT,
}

internal data class RankedPlateCandidate(
    val value: String,
    val correctedCharacters: Int,
    val score: Int,
)

/**
 * Bounded per-frame candidate accumulator. It replaces the former element/block/line list,
 * groupBy, intermediate list, and maxBy pipeline while incorporating position and size.
 */
internal class PlateCandidateFrameRanker(
    private val maximumUniqueCandidates: Int = 12,
) {
    private data class Evidence(
        var correctedCharacters: Int,
        var strongestScore: Int,
        var observations: Int,
    )

    private val evidenceByValue = LinkedHashMap<String, Evidence>(maximumUniqueCandidates)

    init {
        require(maximumUniqueCandidates > 0)
    }

    fun reset() {
        evidenceByValue.clear()
    }

    fun observe(
        candidate: NormalizedPlateCandidate,
        formatScore: Int,
        source: PlateCandidateSource,
        bounds: ScannerBounds?,
        scanRegion: ScannerBounds?,
    ) {
        val score = scoreEvidence(candidate, formatScore, source, bounds, scanRegion)
        val existing = evidenceByValue[candidate.value]
        if (existing != null) {
            existing.correctedCharacters = minOf(existing.correctedCharacters, candidate.correctedCharacters)
            existing.strongestScore = maxOf(existing.strongestScore, score)
            existing.observations++
            return
        }

        if (evidenceByValue.size >= maximumUniqueCandidates) {
            val weakest = evidenceByValue.minByOrNull { it.value.strongestScore }
            if (weakest == null || score <= weakest.value.strongestScore) return
            evidenceByValue.remove(weakest.key)
        }
        evidenceByValue[candidate.value] = Evidence(
            correctedCharacters = candidate.correctedCharacters,
            strongestScore = score,
            observations = 1,
        )
    }

    fun best(): RankedPlateCandidate? {
        var bestValue: String? = null
        var bestEvidence: Evidence? = null
        var bestScore = Int.MIN_VALUE
        evidenceByValue.forEach { (value, evidence) ->
            val consistencyBonus = (evidence.observations - 1).coerceIn(0, 3) * 4
            val finalScore = evidence.strongestScore + consistencyBonus
            if (finalScore > bestScore) {
                bestValue = value
                bestEvidence = evidence
                bestScore = finalScore
            }
        }
        val value = bestValue ?: return null
        return RankedPlateCandidate(
            value = value,
            correctedCharacters = bestEvidence?.correctedCharacters ?: 0,
            score = bestScore,
        )
    }

    private fun scoreEvidence(
        candidate: NormalizedPlateCandidate,
        formatScore: Int,
        source: PlateCandidateSource,
        bounds: ScannerBounds?,
        scanRegion: ScannerBounds?,
    ): Int {
        var score = formatScore - (candidate.correctedCharacters * 10)
        score += when (source) {
            PlateCandidateSource.LINE -> 18
            PlateCandidateSource.BLOCK -> 5
            PlateCandidateSource.FULL_TEXT -> 0
        }

        if (bounds != null && scanRegion != null && bounds.isUsable && scanRegion.isUsable) {
            val containment = ScannerGeometry.containmentRatio(bounds, scanRegion)
            val centreProximity = 1f - ScannerGeometry.normalizedCenterDistance(bounds, scanRegion)
            val widthRatio = (bounds.width / scanRegion.width).coerceIn(0f, 1.5f)
            val heightRatio = (bounds.height / scanRegion.height).coerceIn(0f, 1.5f)
            val aspectRatio = bounds.width / bounds.height
            score += (containment * 22f).roundToInt()
            score += (centreProximity * 14f).roundToInt()
            if (widthRatio in 0.28f..1.10f) score += 6
            if (heightRatio in 0.10f..0.72f) score += 5
            if (aspectRatio in 1.4f..8.5f) score += 6
        }
        return score
    }
}
