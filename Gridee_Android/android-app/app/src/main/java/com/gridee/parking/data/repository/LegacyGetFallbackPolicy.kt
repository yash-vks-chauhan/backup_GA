package com.gridee.parking.data.repository

/** Legacy GET routes are compatibility-only and must never amplify peak/server failures. */
internal object LegacyGetFallbackPolicy {
    private val unsupportedRouteCodes = setOf(404, 405, 501)

    fun shouldFallback(statusCode: Int): Boolean = statusCode in unsupportedRouteCodes
}
