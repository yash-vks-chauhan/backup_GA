package com.gridee.parking.benchmark

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.gridee.parking.utils.AuthSession
import com.gridee.parking.utils.JwtTokenManager

/**
 * Benchmark-only session setup for the PERF-024 main-tab journey.
 *
 * The benchmark manifest removes INTERNET permission and pins both API hosts to loopback, so this
 * synthetic identity cannot authenticate against or mutate any backend. Keeping the receiver in
 * the benchmark source set also guarantees that it is absent from debug and release APKs.
 */
class Perf024BenchmarkSeedReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_SEED_SESSION) return

        JwtTokenManager(context).saveAuthToken(
            token = "perf024-local-benchmark-token",
            userId = "perf024-local-user",
            userName = "PERF-024 Benchmark",
            userRole = "USER",
        )
        AuthSession.syncLegacyPrefsFromJwt(context)
    }

    companion object {
        const val ACTION_SEED_SESSION =
            "com.gridee.parking.benchmark.PERF024_SEED_SESSION"
    }
}
