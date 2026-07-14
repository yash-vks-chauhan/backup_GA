package com.gridee.parking.ui.maintenance

import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Bundle
import android.os.SystemClock
import android.view.HapticFeedbackConstants
import android.view.View
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.core.view.updatePadding
import androidx.lifecycle.lifecycleScope
import com.gridee.parking.R
import com.gridee.parking.config.RemoteConfigManager
import com.gridee.parking.databinding.ActivityMaintenanceBinding
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Full-screen maintenance state — shown by [com.gridee.parking.ui.auth.SplashActivity]
 * when the backend reports maintenance mode. Replaces the old bare AlertDialog.
 *
 * Themed to the brand (mono surface, one gold accent) and built around a parking
 * metaphor: a lowered **boom gate** ([GrideeGateView]) bars entry, a gold lamp
 * breathes so it feels alive, and the moment maintenance clears the gate **lifts**
 * as the transition into the app. It quietly re-checks on a slow cadence and a
 * live "last checked" ticker keeps the screen provably alive even at rest. A
 * subtle tilt-parallax gives the flat screen physical depth.
 */
class MaintenanceActivity : AppCompatActivity(), SensorEventListener {

    private lateinit var binding: ActivityMaintenanceBinding

    private var checking = false
    private var proceeding = false
    private var loopJob: Job? = null

    /** elapsedRealtime of the last completed config check; drives the ticker. */
    private var lastCheckedAt = 0L

    private var sensorManager: SensorManager? = null
    private var accelerometer: Sensor? = null
    private val gravity = FloatArray(2)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMaintenanceBinding.inflate(layoutInflater)
        setContentView(binding.root)

        lastCheckedAt = SystemClock.elapsedRealtime() // Splash refreshed just before launching us
        configureSystemBars()
        applyInsets()
        bindContent()

        sensorManager = getSystemService(SENSOR_SERVICE) as? SensorManager
        accelerometer = sensorManager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)

        binding.btnRetry.setOnClickListener { runCheck(manual = true) }
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            // Don't trap the user, but don't let them slip past the gate either —
            // back simply sends the app to the background.
            override fun handleOnBackPressed() {
                moveTaskToBack(true)
            }
        })

        playEntryAnimation()
    }

    private fun configureSystemBars() {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        val light = !isNightMode()
        WindowInsetsControllerCompat(window, window.decorView).apply {
            isAppearanceLightStatusBars = light
            isAppearanceLightNavigationBars = light
        }
    }

    private fun applyInsets() {
        ViewCompat.setOnApplyWindowInsetsListener(binding.maintenanceRoot) { v, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.updatePadding(top = bars.top, bottom = bars.bottom)
            insets
        }
    }

    private fun bindContent() {
        val config = RemoteConfigManager.currentConfig.features
        binding.tvMaintenanceTitle.text =
            intent.getStringExtra(EXTRA_TITLE)?.takeIf { it.isNotBlank() }
                ?: config.maintenanceTitle.ifBlank { getString(R.string.maintenance_title_default) }
        binding.tvMaintenanceMessage.text =
            intent.getStringExtra(EXTRA_MESSAGE)?.takeIf { it.isNotBlank() }
                ?: config.maintenanceMessage.ifBlank { getString(R.string.maintenance_message_default) }
        updateTicker()
    }

    private fun playEntryAnimation() {
        val views = listOf(
            binding.heroContainer,
            binding.tvMaintenanceTitle,
            binding.tvMaintenanceMessage,
            binding.retryContainer,
            binding.tvMaintenanceCaption
        )
        views.forEachIndexed { i, view ->
            view.alpha = 0f
            view.translationY = 18f * resources.displayMetrics.density
            view.animate()
                .alpha(1f)
                .translationY(0f)
                .setStartDelay(80L + i * 70L)
                .setDuration(520L)
                .start()
        }
    }

    // ── Re-check loop + live ticker ────────────────────────────────────────────

    override fun onStart() {
        super.onStart()
        startLoop()
    }

    override fun onStop() {
        super.onStop()
        loopJob?.cancel()
        loopJob = null
    }

    override fun onResume() {
        super.onResume()
        accelerometer?.let { sensorManager?.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME) }
    }

    override fun onPause() {
        super.onPause()
        sensorManager?.unregisterListener(this)
    }

    private fun startLoop() {
        if (loopJob?.isActive == true) return
        loopJob = lifecycleScope.launch {
            while (isActive) {
                if (!checking && !proceeding) {
                    updateTicker()
                    if (secondsSinceChecked() >= AUTO_POLL_SECONDS) runCheck(manual = false)
                }
                delay(1000)
            }
        }
    }

    private fun secondsSinceChecked(): Int =
        ((SystemClock.elapsedRealtime() - lastCheckedAt) / 1000L).toInt().coerceAtLeast(0)

    private fun updateTicker() {
        if (proceeding || checking) return
        val secs = secondsSinceChecked()
        binding.tvMaintenanceCaption.text =
            if (secs < 3) getString(R.string.maintenance_caption_just_now)
            else getString(R.string.maintenance_caption_seconds, secs)
    }

    /**
     * Refresh remote config and either open the gate (maintenance cleared) or
     * settle back. [manual] taps show the in-button spinner; auto-polls are silent.
     */
    private fun runCheck(manual: Boolean) {
        if (checking || proceeding) return
        checking = true

        if (manual) {
            binding.btnRetry.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
            setButtonLoading(true)
            binding.gateView.beginAttempt() // gate strains up while we check
            binding.tvMaintenanceCaption.setText(R.string.maintenance_caption_checking)
        }

        lifecycleScope.launch {
            val startedAt = System.currentTimeMillis()
            RemoteConfigManager.refresh(this@MaintenanceActivity)

            // Keep a manual spinner visible long enough to read as deliberate.
            if (manual) {
                val elapsed = System.currentTimeMillis() - startedAt
                if (elapsed < MIN_SPINNER_MS) delay(MIN_SPINNER_MS - elapsed)
            }

            lastCheckedAt = SystemClock.elapsedRealtime()

            if (!RemoteConfigManager.isMaintenanceMode()) {
                openGateAndProceed()
                return@launch
            }

            if (manual) {
                setButtonLoading(false)
                binding.gateView.failAttempt() // still locked — drop the gate back closed
            }
            checking = false
            updateTicker()
        }
    }

    private fun setButtonLoading(loading: Boolean) {
        binding.btnRetry.isEnabled = !loading
        // MaterialButton has no "label visibility" — blank the text so the
        // centred spinner reads cleanly, then restore it.
        binding.btnRetry.text = if (loading) "" else getString(R.string.maintenance_retry)
        binding.retrySpinner.visibility = if (loading) View.VISIBLE else View.GONE
    }

    private fun openGateAndProceed() {
        if (proceeding) return
        proceeding = true
        loopJob?.cancel()

        // Supporting copy clears so the gate's full open is the hero beat; the
        // window fade-out on finish() dissolves the rest into the app.
        listOf(
            binding.tvMaintenanceTitle,
            binding.tvMaintenanceMessage,
            binding.retryContainer,
            binding.tvMaintenanceCaption
        ).forEach { it.animate().alpha(0f).setDuration(280L).start() }

        binding.btnRetry.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
        binding.gateView.raise {
            // Splash sits beneath us and re-routes on resume now the config is clear.
            finish()
            overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
        }
    }

    // ── Tilt parallax ──────────────────────────────────────────────────────────

    override fun onSensorChanged(event: SensorEvent) {
        if (event.sensor.type != Sensor.TYPE_ACCELEROMETER || proceeding) return
        // Low-pass the gravity vector so the parallax glides instead of jitters.
        gravity[0] = gravity[0] + 0.12f * (event.values[0] - gravity[0])
        gravity[1] = gravity[1] + 0.12f * (event.values[1] - gravity[1])

        val maxShift = 8f * resources.displayMetrics.density
        val tx = (-gravity[0] / 9.8f).coerceIn(-1f, 1f) * maxShift
        val ty = ((gravity[1] - 9.2f) / 9.8f).coerceIn(-1f, 1f) * maxShift * 0.6f

        // Foreground (wordmark + gate) shifts more than the glow → depth.
        binding.heroGroup.translationX = tx
        binding.heroGroup.translationY = ty
        binding.heroGlow.translationX = tx * 0.4f
        binding.heroGlow.translationY = ty * 0.4f
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit

    private fun isNightMode(): Boolean =
        (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
            Configuration.UI_MODE_NIGHT_YES

    companion object {
        private const val EXTRA_TITLE = "extra_maintenance_title"
        private const val EXTRA_MESSAGE = "extra_maintenance_message"
        private const val AUTO_POLL_SECONDS = 25
        private const val MIN_SPINNER_MS = 650L

        fun newIntent(context: Context, title: String?, message: String?): Intent =
            Intent(context, MaintenanceActivity::class.java).apply {
                putExtra(EXTRA_TITLE, title)
                putExtra(EXTRA_MESSAGE, message)
            }
    }
}
