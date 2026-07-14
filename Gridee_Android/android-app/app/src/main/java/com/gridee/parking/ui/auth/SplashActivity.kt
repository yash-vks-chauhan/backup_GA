package com.gridee.parking.ui.auth

import android.animation.Animator
import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.app.AlertDialog
import android.content.Intent
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.RenderEffect
import android.graphics.Shader
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import android.view.View
import android.view.animation.Interpolator
import androidx.appcompat.app.AppCompatActivity
import androidx.core.animation.doOnEnd
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.core.view.WindowInsetsControllerCompat
import androidx.core.view.animation.PathInterpolatorCompat
import androidx.lifecycle.lifecycleScope
import com.gridee.parking.R
import com.gridee.parking.config.RemoteConfigManager
import com.gridee.parking.data.model.AppRemoteConfig
import com.gridee.parking.databinding.ActivitySplashBinding
import com.gridee.parking.ui.maintenance.MaintenanceActivity
import com.gridee.parking.ui.main.MainContainerActivity
import com.gridee.parking.ui.operator.OperatorDashboardActivity
import com.gridee.parking.utils.AuthSession
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.util.Locale

class SplashActivity : AppCompatActivity() {

    private companion object {
        val SHINE_DARK = Color.parseColor("#3A3A3A")

        // Hard cap on how long routing waits for a fresh config once the splash is
        // done animating. Past this we route on the cached config so a slow or dead
        // network can never leave the user on a black screen. Sized so the branded
        // loading sequence can play out gracefully on the slowest connections.
        const val CONFIG_WAIT_BUDGET_MS = 3000L

        // Grace window before the loader appears: if config lands within this we
        // route straight through and the loader never shows, so it can't flash.
        const val LOADER_REVEAL_DELAY_MS = 400L

        // Once shown, hold the loader at least this long so a config that arrives
        // just after reveal can't make it blink in and straight back out.
        const val MIN_LOADER_VISIBLE_MS = 700L

        // Cadence for stepping the copy forward while waiting, so it reads as
        // progress. Slow enough to read each line; stops on the last, never loops.
        const val MESSAGE_ADVANCE_DELAY_MS = 1000L

        // Shown in order while waiting; the first line is already set in the layout.
        // Advancing stops at the last and is cancelled the moment config arrives.
        val LOADING_MESSAGES = intArrayOf(
            R.string.splash_loading_message,
            R.string.splash_loading_message_2,
            R.string.splash_loading_message_3,
        )

        // Process-scoped flag: full cinematic plays once per process lifetime.
        // Reset automatically when the OS reclaims the process.
        @Volatile
        private var hasShownCinematic = false
    }

    private lateinit var binding: ActivitySplashBinding
    private var routingStarted = false

    // Config refresh kicked off in onCreate so it runs *during* the cinematic
    // instead of blocking after it; routing later awaits this with a timeout.
    private var configRefresh: Job? = null

    // Set when we hand off to the full-screen maintenance gate; on return we
    // re-route once the backend reports maintenance has cleared.
    private var awaitingMaintenanceClearance = false

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)

        binding = ActivitySplashBinding.inflate(layoutInflater)
        setContentView(binding.root)

        configureSystemBars()
        RemoteConfigManager.loadCached(this)
        startConfigRefresh()

        if (shouldSkipCinematic()) {
            // Warm relaunch within the same process, or user has disabled animations system-wide.
            binding.tvGrideeLogo.visibility = View.INVISIBLE
            openNextScreen()
            return
        }

        hasShownCinematic = true
        playLogoAnimation()
    }

    private fun shouldSkipCinematic(): Boolean {
        return hasShownCinematic || ValueAnimator.getDurationScale() == 0f
    }

    private fun configureSystemBars() {
        window.statusBarColor = Color.BLACK
        window.navigationBarColor = Color.BLACK
        WindowInsetsControllerCompat(window, window.decorView).apply {
            isAppearanceLightStatusBars = false
            isAppearanceLightNavigationBars = false
        }
    }

    private fun playLogoAnimation() {
        val density = resources.displayMetrics.density
        val startOffsetPx = 6f * density

        // Entry uses an emphasized curve (long graceful tail). Dive uses an ease-in
        // (slow approach, rapid pass-through) so the camera feels like it accelerates into the wordmark.
        val entryCurve: Interpolator = PathInterpolatorCompat.create(0.2f, 0.8f, 0.2f, 1f)
        val blurCurve: Interpolator = PathInterpolatorCompat.create(0.25f, 0.1f, 0.25f, 1f)
        val shineCurve: Interpolator = PathInterpolatorCompat.create(0.4f, 0f, 0.2f, 1f)
        val diveCurve: Interpolator = PathInterpolatorCompat.create(0.55f, 0f, 0.85f, 0.25f)

        binding.tvGrideeLogo.apply {
            alpha = 0f
            scaleX = 1.18f
            scaleY = 1.18f
            translationY = startOffsetPx
            letterSpacing = 0.10f
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                setRenderEffect(RenderEffect.createBlurEffect(20f, 20f, Shader.TileMode.CLAMP))
            }
        }

        binding.tvGrideeLogo.animate()
            .alpha(1f)
            .scaleX(1f)
            .scaleY(1f)
            .translationY(0f)
            .setDuration(1300L)
            .setStartDelay(120L)
            .setInterpolator(entryCurve)
            .withEndAction {
                binding.tvGrideeLogo.postDelayed({ playDiveExit(diveCurve) }, 280L)
            }
            .start()

        ValueAnimator.ofFloat(0.10f, -0.02f).apply {
            startDelay = 120L
            duration = 1300L
            interpolator = entryCurve
            addUpdateListener {
                binding.tvGrideeLogo.letterSpacing = it.animatedValue as Float
            }
            start()
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            ValueAnimator.ofFloat(20f, 0f).apply {
                startDelay = 120L
                duration = 950L
                interpolator = blurCurve
                addUpdateListener {
                    val r = it.animatedValue as Float
                    binding.tvGrideeLogo.setRenderEffect(
                        if (r > 0.1f) RenderEffect.createBlurEffect(r, r, Shader.TileMode.CLAMP) else null
                    )
                }
                start()
            }
        }

        // Shine wipes left-to-right, overlapping the entry fade-in so the dark base color
        // is masked by the alpha ramp instead of sitting visibly as a "dark grey logo" phase.
        binding.tvGrideeLogo.post {
            val viewWidth = binding.tvGrideeLogo.width.toFloat()
            val glowWidth = viewWidth * 0.38f
            val startX = -10f
            val endX = viewWidth + glowWidth + 10f

            val createShader = { x: Float ->
                LinearGradient(
                    x - glowWidth, 0f,
                    x, 0f,
                    intArrayOf(Color.WHITE, SHINE_DARK),
                    null,
                    Shader.TileMode.CLAMP
                )
            }

            binding.tvGrideeLogo.paint.shader = createShader(startX)
            binding.tvGrideeLogo.invalidate()

            ValueAnimator.ofFloat(startX, endX).apply {
                startDelay = 250L
                duration = 1100L
                interpolator = shineCurve
                addUpdateListener {
                    binding.tvGrideeLogo.paint.shader = createShader(it.animatedValue as Float)
                    binding.tvGrideeLogo.invalidate()
                }
                start()
            }
        }
    }

    private fun playDiveExit(curve: Interpolator) {
        val tv = binding.tvGrideeLogo
        tv.pivotX = tv.width / 2f
        tv.pivotY = tv.height / 2f

        // Clear the shine shader so the dive renders as flat white pixels
        // instead of a gradient stretched across the screen at 14x scale.
        tv.paint.shader = null
        tv.invalidate()

        val scaleTarget = 14f
        val scaleX = ObjectAnimator.ofFloat(tv, "scaleX", 1f, scaleTarget).apply {
            interpolator = curve
        }
        val scaleY = ObjectAnimator.ofFloat(tv, "scaleY", 1f, scaleTarget).apply {
            interpolator = curve
        }
        // Late fade — wordmark stays solid while it grows, then vanishes only as it engulfs the frame.
        val alpha = ObjectAnimator.ofFloat(tv, "alpha", 1f, 0f).apply {
            interpolator = PathInterpolatorCompat.create(0.7f, 0f, 1f, 1f)
        }

        val children = mutableListOf<Animator>(scaleX, scaleY, alpha)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            children += ValueAnimator.ofFloat(0f, 28f).apply {
                interpolator = curve
                addUpdateListener {
                    val r = it.animatedValue as Float
                    if (r > 0.1f) {
                        tv.setRenderEffect(RenderEffect.createBlurEffect(r, r, Shader.TileMode.CLAMP))
                    }
                }
            }
        }

        AnimatorSet().apply {
            playTogether(children)
            duration = 850L
            // Note: not calling setInterpolator() here — it would overwrite each child's curve.
            doOnEnd { openNextScreen() }
            start()
        }
    }

    private fun startConfigRefresh() {
        configRefresh = lifecycleScope.launch {
            // refresh() already swallows network errors and falls back to cache;
            // runCatching is a final guard so a failure here can never crash launch.
            runCatching { RemoteConfigManager.refresh(this@SplashActivity) }
        }
    }

    private fun openNextScreen() {
        if (isFinishing || isDestroyed) return
        if (routingStarted) return
        routingStarted = true

        lifecycleScope.launch {
            awaitConfigOrTimeout()
            if (isFinishing || isDestroyed) return@launch

            if (RemoteConfigManager.isMaintenanceMode()) {
                routingStarted = false
                showMaintenanceGate(RemoteConfigManager.currentConfig)
                return@launch
            }

            if (RemoteConfigManager.isForceUpdateRequired()) {
                showForceUpdateDialog()
                return@launch
            }

            if (RemoteConfigManager.isRecommendedUpdateAvailable()) {
                showRecommendedUpdateDialog()
                return@launch
            }

            openResolvedNextScreen()
        }
    }

    /**
     * Waits for the in-flight config refresh, capped at [CONFIG_WAIT_BUDGET_MS], so a
     * slow or dead network can never leave the user on a black screen. Fast
     * connections route straight through; slower ones get the branded loading screen,
     * held a minimum beat so it never flashes. Routing then proceeds with whatever
     * config we have — fresh if it arrived, cached otherwise.
     */
    private suspend fun awaitConfigOrTimeout() {
        val job = configRefresh ?: return

        // Fast path: give config a brief grace window and route straight through.
        // The loader never appears, so it can't blink in and out.
        if (withTimeoutOrNull(LOADER_REVEAL_DELAY_MS) { job.join() } != null) return

        // Slow connection: commit to the branded loading screen.
        showLoading()
        val shownAt = SystemClock.elapsedRealtime()
        val messages = lifecycleScope.launch { runLoadingMessages() }

        withTimeoutOrNull(CONFIG_WAIT_BUDGET_MS - LOADER_REVEAL_DELAY_MS) { job.join() }
        messages.cancel()

        // Never flash: hold the loader for a minimum once it has been shown.
        val visibleFor = SystemClock.elapsedRealtime() - shownAt
        if (visibleFor < MIN_LOADER_VISIBLE_MS) {
            delay(MIN_LOADER_VISIBLE_MS - visibleFor)
        }
        // Loader is left visible on purpose: the fade transition into the next
        // screen cross-dissolves it away, avoiding a one-frame black flash.
    }

    private suspend fun runLoadingMessages() {
        for (i in 1 until LOADING_MESSAGES.size) {
            delay(MESSAGE_ADVANCE_DELAY_MS)
            crossfadeLoadingMessage(LOADING_MESSAGES[i])
        }
    }

    private fun showLoading() {
        binding.loadingContainer.apply {
            visibility = View.VISIBLE
            animate().alpha(1f).setDuration(220L).start()
        }
    }

    private fun crossfadeLoadingMessage(messageRes: Int) {
        binding.tvLoadingMessage.animate()
            .alpha(0f)
            .setDuration(180L)
            .withEndAction {
                binding.tvLoadingMessage.setText(messageRes)
                binding.tvLoadingMessage.animate().alpha(1f).setDuration(220L).start()
            }
            .start()
    }

    private fun openResolvedNextScreen() {
        if (isFinishing || isDestroyed) return

        val targetIntent = if (AuthSession.isAuthenticated(this)) {
            AuthSession.syncLegacyPrefsFromJwt(this)
            val normalizedRole = AuthSession.getUserRole(this)?.uppercase(Locale.ROOT) ?: "USER"
            when (normalizedRole) {
                "OPERATOR" -> Intent(this, OperatorDashboardActivity::class.java)
                else -> Intent(this, MainContainerActivity::class.java).apply {
                    AuthSession.getUserName(this@SplashActivity)?.let { putExtra("USER_NAME", it) }
                }
            }.apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            }
        } else {
            Intent(this, WelcomeActivity::class.java)
        }

        startActivity(targetIntent)
        overridePendingTransition(android.R.anim.fade_in, 0)
        finish()
    }

    private fun showMaintenanceGate(config: AppRemoteConfig) {
        if (isFinishing || isDestroyed) return
        // Keep Splash alive beneath the gate; onResume re-routes when it clears.
        awaitingMaintenanceClearance = true
        startActivity(
            MaintenanceActivity.newIntent(
                this,
                config.features.maintenanceTitle,
                config.features.maintenanceMessage
            )
        )
        overridePendingTransition(android.R.anim.fade_in, 0)
    }

    override fun onResume() {
        super.onResume()
        if (awaitingMaintenanceClearance) {
            awaitingMaintenanceClearance = false
            // The maintenance gate only finishes once the backend reports it has
            // cleared, so resume the normal launch flow.
            routingStarted = false
            openNextScreen()
        }
    }

    private fun showForceUpdateDialog() {
        if (isFinishing || isDestroyed) return
        AlertDialog.Builder(this)
            .setTitle("Update required")
            .setMessage(RemoteConfigManager.getAndroidUpdateMessage())
            .setCancelable(false)
            .setPositiveButton("Update") { _, _ ->
                openPlayStore()
            }
            .show()
    }

    private fun showRecommendedUpdateDialog() {
        if (isFinishing || isDestroyed) return
        AlertDialog.Builder(this)
            .setTitle("Update available")
            .setMessage(RemoteConfigManager.getAndroidUpdateMessage())
            .setCancelable(true)
            .setNegativeButton("Later") { dialog, _ ->
                dialog.dismiss()
                openResolvedNextScreen()
            }
            .setPositiveButton("Update") { _, _ ->
                openPlayStore()
                openResolvedNextScreen()
            }
            .setOnCancelListener {
                openResolvedNextScreen()
            }
            .show()
    }

    private fun openPlayStore() {
        val url = RemoteConfigManager.getAndroidPlayStoreUrl()
        runCatching {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        }
    }
}
