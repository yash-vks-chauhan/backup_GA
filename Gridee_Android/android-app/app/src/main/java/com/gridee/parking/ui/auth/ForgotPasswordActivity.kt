package com.gridee.parking.ui.auth

import android.os.Bundle
import android.view.View
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.gridee.parking.R
import com.gridee.parking.databinding.ActivityForgotPasswordBinding
import com.gridee.parking.utils.NotificationHelper
import com.gridee.parking.utils.ThemeManager

class ForgotPasswordActivity : AppCompatActivity() {

    private lateinit var binding: ActivityForgotPasswordBinding
    private val viewModel: ForgotPasswordViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityForgotPasswordBinding.inflate(layoutInflater)
        setContentView(binding.root)

        window.statusBarColor = ContextCompat.getColor(this, R.color.background_primary)
        WindowInsetsControllerCompat(window, window.decorView).isAppearanceLightStatusBars = !ThemeManager.isDarkMode(this)

        binding.btnSendReset.setOnClickListener {
            it.performHapticFeedback(android.view.HapticFeedbackConstants.VIRTUAL_KEY)
            val email = binding.etEmail.text?.toString().orEmpty()
            viewModel.clearErrors()
            viewModel.sendResetEmail(email)
        }

        binding.btnBack.setOnClickListener {
            it.performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS)
            finish()
        }

        binding.btnOpenEmail.setOnClickListener {
            it.performHapticFeedback(android.view.HapticFeedbackConstants.VIRTUAL_KEY)
            val intent = android.content.Intent(android.content.Intent.ACTION_MAIN)
            intent.addCategory(android.content.Intent.CATEGORY_APP_EMAIL)
            intent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
            try {
                startActivity(android.content.Intent.createChooser(intent, "Open Email App"))
            } catch (e: android.content.ActivityNotFoundException) {
                NotificationHelper.showInfo(binding.rootContainer, message = "No email app found on this device")
            }
        }

        binding.etEmail.setOnFocusChangeListener { _, _ ->
            viewModel.clearErrors()
        }

        observeViewModel()
    }

    private fun observeViewModel() {
        viewModel.state.observe(this) { state ->
            when (state) {
                is ForgotPasswordState.Loading -> showLoading(true)
                is ForgotPasswordState.Success -> {
                    showLoading(false)
                    showSuccessState()
                }
                is ForgotPasswordState.Error -> {
                    showLoading(false)
                    if (state.isRetryable) {
                        NotificationHelper.showWarning(
                            binding.rootContainer,
                            title = state.title,
                            message = state.message,
                            onClick = { binding.btnSendReset.performClick() },
                            actionButtonText = "Try Again"
                        )
                    } else {
                        NotificationHelper.showError(
                            binding.rootContainer,
                            title = state.title,
                            message = state.message
                        )
                    }
                }
            }
        }

        viewModel.validationErrors.observe(this) { errors ->
            binding.tilEmail.error = errors["email"]
        }
    }

    private fun showSuccessState() {
        // 1. Animate Form OUT (Fade out + Slide Up + Scale Down)
        binding.layoutForm.animate()
            .alpha(0f)
            .translationY(-50f)
            .scaleX(0.95f)
            .scaleY(0.95f)
            .setDuration(300)
            .setInterpolator(android.view.animation.AccelerateInterpolator())
            .withEndAction {
                binding.layoutForm.visibility = View.GONE
                
                // 2. Prepare Success View (Scale down start pos)
                binding.layoutSuccess.visibility = View.VISIBLE
                binding.layoutSuccess.alpha = 0f
                binding.layoutSuccess.scaleX = 0.9f
                binding.layoutSuccess.scaleY = 0.9f
                binding.layoutSuccess.translationY = 50f

                // 3. Animate Success View IN (Fade in + Slide Up + Scale Up with bounce)
                binding.layoutSuccess.animate()
                    .alpha(1f)
                    .translationY(0f)
                    .scaleX(1f)
                    .scaleY(1f)
                    .setDuration(400)
                    .setInterpolator(android.view.animation.DecelerateInterpolator(1.5f))
                    .start()
            }
            .start()
    }

    private fun showLoading(show: Boolean) {
        binding.progressBar.visibility = if (show) View.VISIBLE else View.GONE
        binding.btnSendReset.isEnabled = !show
        binding.layoutForm.alpha = if (show) 0.5f else 1.0f
    }
}
