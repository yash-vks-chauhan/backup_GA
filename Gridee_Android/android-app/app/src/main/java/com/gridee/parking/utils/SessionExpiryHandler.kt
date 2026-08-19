package com.gridee.parking.utils

import android.content.Context
import android.content.Intent
import android.os.SystemClock
import android.widget.Toast
import com.gridee.parking.GrideeApplication
import com.gridee.parking.ui.auth.LoginActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Central handler for a dead session: an authenticated request came back HTTP 401,
 * i.e. the stored JWT is invalid/expired. Clears the session and routes to login.
 *
 * Invoked only by [com.gridee.parking.data.api.UnauthorizedInterceptor], and only
 * for requests that actually carried an Authorization header — so a wrong-password
 * 401 on a public login call never triggers a logout. Deliberately does NOT handle
 * 403 (authenticated but not permitted — wrong role / parking lot); those surface
 * as in-screen errors so the user is not logged out for a permission problem.
 */
object SessionExpiryHandler {

    // A burst of in-flight authenticated requests can all 401 at once; only act once.
    private const val DEBOUNCE_MS = 5_000L
    // Do not redirect if the user is already inside the auth flow (login/register/splash).
    private const val AUTH_PACKAGE = ".ui.auth."

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @Volatile
    private var lastHandledAtMs = 0L

    fun onUnauthorized(appContext: Context) {
        synchronized(this) {
            val now = SystemClock.elapsedRealtime()
            if (now - lastHandledAtMs < DEBOUNCE_MS) return
            lastHandledAtMs = now
        }

        val application = appContext.applicationContext
        scope.launch {
            // clearSession may fire a token-unregister network call; keep it off the main thread.
            runCatching { AuthSession.clearSession(application) }
            withContext(Dispatchers.Main) { routeToLogin() }
        }
    }

    private fun routeToLogin() {
        // No foreground activity (app backgrounded): session is already cleared, so the
        // next app open routes to login via the normal Splash auth check. Nothing to do.
        val activity = GrideeApplication.currentActivity ?: return
        if (activity.isFinishing || activity.isDestroyed) return
        if (activity.javaClass.name.contains(AUTH_PACKAGE)) return

        runCatching {
            Toast.makeText(activity, "Session expired. Please log in again.", Toast.LENGTH_LONG).show()
            val intent = Intent(activity, LoginActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
            }
            activity.startActivity(intent)
            activity.finish()
        }
    }
}
