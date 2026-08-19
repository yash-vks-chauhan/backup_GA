package com.gridee.parking.data.api

import android.content.Context
import com.gridee.parking.utils.SessionExpiryHandler
import okhttp3.Interceptor
import okhttp3.Response

/**
 * Detects an expired/invalid session and hands off to [SessionExpiryHandler].
 *
 * Triggers only when a request that carried an Authorization header comes back
 * HTTP 401 — that is a rejected token, i.e. the session is dead. A 401 on a public
 * request (e.g. a wrong-password login, which has no Authorization header) is left
 * alone, and 403 (authenticated but not permitted — wrong role or parking lot) is
 * intentionally NOT treated as a session problem.
 *
 * Must be registered AFTER JwtAuthInterceptor so the request it inspects already
 * has the Authorization header attached.
 */
class UnauthorizedInterceptor(context: Context) : Interceptor {

    private val appContext = context.applicationContext

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val response = chain.proceed(request)

        if (isSessionDead(response.code, request.header("Authorization") != null)) {
            SessionExpiryHandler.onUnauthorized(appContext)
        }

        return response
    }

    companion object {
        /**
         * A dead session is specifically a rejected token: HTTP 401 on a request that
         * actually carried an Authorization header. 403 (authenticated but not permitted)
         * and unauthenticated 401s (e.g. a wrong-password login, which has no auth header)
         * are deliberately excluded so we never log a user out for a permission problem
         * or a failed login attempt.
         */
        internal fun isSessionDead(statusCode: Int, hadAuthHeader: Boolean): Boolean =
            statusCode == 401 && hadAuthHeader
    }
}
