package com.gridee.parking.config

import com.gridee.parking.BuildConfig

/**
 * API configuration for the backend server.
 *
 * The URL is no longer edited here. It comes from `BuildConfig`, which is set per build type in
 * `app/build.gradle`:
 *
 *  - **release** is pinned to production and cannot be overridden by any Gradle property, so the
 *    Play Store build can never be pointed at a staging host by accident.
 *  - **debug** honours `-PgrideeBaseUrl=...` (or a `grideeBaseUrl` entry in `gradle.properties`)
 *    and falls back to production when unset.
 *
 * Point a debug build at another backend with:
 * ```
 * ./gradlew :app:installDebug -PgrideeBaseUrl=http://192.168.1.42:8080/
 * ```
 *
 * Note: the base URL must NOT include `/api` — paths in `ApiService` already start with `api/...`.
 */
object ApiConfig {

    val BASE_URL: String = BuildConfig.GRIDEE_BASE_URL

    /**
     * Retry host for [com.gridee.parking.data.api.ApiClient] when a request fails outright.
     * When it resolves to the same host as [BASE_URL] the retry is a no-op by design — that is the
     * current production behaviour. Set `grideeFallbackBaseUrl` on a debug build to exercise it.
     */
    val FALLBACK_BASE_URL: String = BuildConfig.GRIDEE_FALLBACK_BASE_URL

    // Authentication is handled via JWT tokens (no basic auth required)
    val REQUIRES_AUTH = false

    fun getAuthHeader(): String? {
        return null  // JWT tokens are handled by JwtAuthInterceptor
    }

    // Convenience helper
    fun isSSLRequired(): Boolean = BASE_URL.startsWith("https://")

    /**
     * Human-readable environment label for debug logging. Derived from the URL actually in use, so
     * it cannot drift out of sync with [BASE_URL] the way the old hardcoded string did.
     */
    fun getEnvironmentInfo(): String {
        val host = BASE_URL
            .substringAfter("://")
            .substringBefore('/')
            .substringBefore(':')
        val label = when {
            host.endsWith("onrender.com") -> "GRIDEE ONRENDER"
            host == "10.0.2.2" -> "LOCAL (emulator)"
            host == "localhost" || host == "127.0.0.1" -> "LOCAL (adb reverse)"
            else -> "CUSTOM ($host)"
        }
        return if (BuildConfig.DEBUG) "$label [debug]" else label
    }
}
