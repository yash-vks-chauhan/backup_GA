package com.gridee.parking.ui.qr

import org.junit.Assert.assertEquals
import org.junit.Test

class PlateCandidateFrameRankerTest {
    private val scanRegion = ScannerBounds(100f, 100f, 500f, 300f)

    @Test
    fun `prefers a centred contained line over off-frame text`() {
        val ranker = PlateCandidateFrameRanker()
        ranker.observe(
            candidate = NormalizedPlateCandidate("DL01CD5678", 0),
            formatScore = 50,
            source = PlateCandidateSource.LINE,
            bounds = ScannerBounds(450f, 250f, 650f, 350f),
            scanRegion = scanRegion,
        )
        ranker.observe(
            candidate = NormalizedPlateCandidate("MH12AB1234", 0),
            formatScore = 50,
            source = PlateCandidateSource.LINE,
            bounds = ScannerBounds(170f, 145f, 430f, 245f),
            scanRegion = scanRegion,
        )

        assertEquals("MH12AB1234", ranker.best()?.value)
    }

    @Test
    fun `prefers line evidence and repeated agreement`() {
        val ranker = PlateCandidateFrameRanker()
        repeat(2) {
            ranker.observe(
                candidate = NormalizedPlateCandidate("MH12AB1234", 0),
                formatScore = 50,
                source = PlateCandidateSource.LINE,
                bounds = ScannerBounds(170f, 145f, 430f, 245f),
                scanRegion = scanRegion,
            )
        }
        ranker.observe(
            candidate = NormalizedPlateCandidate("DL01CD5678", 0),
            formatScore = 50,
            source = PlateCandidateSource.FULL_TEXT,
            bounds = null,
            scanRegion = scanRegion,
        )

        assertEquals("MH12AB1234", ranker.best()?.value)
    }

    @Test
    fun `bounded ranker retains a stronger late candidate`() {
        val ranker = PlateCandidateFrameRanker(maximumUniqueCandidates = 2)
        ranker.observe(NormalizedPlateCandidate("TN011234", 0), 10, PlateCandidateSource.FULL_TEXT, null, null)
        ranker.observe(NormalizedPlateCandidate("KA011234", 0), 11, PlateCandidateSource.FULL_TEXT, null, null)
        ranker.observe(NormalizedPlateCandidate("MH12AB1234", 0), 80, PlateCandidateSource.LINE, scanRegion, scanRegion)

        assertEquals("MH12AB1234", ranker.best()?.value)
    }
}
