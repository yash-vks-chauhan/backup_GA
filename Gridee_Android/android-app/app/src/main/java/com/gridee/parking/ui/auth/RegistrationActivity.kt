package com.gridee.parking.ui.auth

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.gridee.parking.R
import com.gridee.parking.config.RemoteConfigManager
import com.gridee.parking.databinding.ActivityRegistrationBinding
import com.gridee.parking.ui.lot.ChooseCategoryActivity
import com.gridee.parking.ui.main.MainContainerActivity
import com.gridee.parking.utils.NotificationHelper

class RegistrationActivity : AppCompatActivity() {

    private lateinit var binding: ActivityRegistrationBinding
    private val viewModel: RegistrationViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityRegistrationBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Theme-aware status bar
        window.statusBarColor = ContextCompat.getColor(this, R.color.background_primary)
        androidx.core.view.WindowInsetsControllerCompat(window, window.decorView).isAppearanceLightStatusBars =
            !com.gridee.parking.utils.ThemeManager.isDarkMode(this)

        setupUI()
        observeViewModel()
    }

    private fun setupUI() {
        // Password visibility toggle with custom animation
        com.gridee.parking.ui.utils.PasswordBlurAnimator(this, binding.tilPassword, binding.etPassword)

        binding.btnRegister.setOnClickListener {
            if (!RemoteConfigManager.isEmailSignInEnabled()) {
                NotificationHelper.showWarning(binding.rootContainer, message = "Email registration is temporarily unavailable.")
                return@setOnClickListener
            }
            registerUser()
        }

        binding.tvLoginLink.setOnClickListener {
            it.performHapticFeedback(android.view.HapticFeedbackConstants.CONTEXT_CLICK)
            // Navigate to login activity
            startActivity(Intent(this, LoginActivity::class.java))
            finish()
        }

        applyFeatureSwitches()
    }

    private fun applyFeatureSwitches() {
        RemoteConfigManager.loadCached(this)
        val emailEnabled = RemoteConfigManager.isEmailSignInEnabled()
        binding.btnRegister.isEnabled = emailEnabled
        binding.btnRegister.alpha = if (emailEnabled) 1f else 0.45f
        if (!emailEnabled) {
            NotificationHelper.showWarning(
                binding.rootContainer,
                message = getString(R.string.email_registration_is_temporarily_unavailable)
            )
        }
    }

    private fun registerUser() {
        val name = binding.etName.text.toString()
        val email = binding.etEmail.text.toString()
        val phone = binding.etPhone.text.toString()
        val password = binding.etPassword.text.toString()

        viewModel.registerUser(
            context = this,
            name = name,
            email = email,
            phone = phone,
            password = password
        )
    }

    private fun observeViewModel() {
        viewModel.registrationState.observe(this) { state ->
            when (state) {
                is RegistrationState.Loading -> {
                    showLoading(true)
                }
                is RegistrationState.Success -> {
                    showLoading(false)

                    // Save user data to SharedPreferences. The parking lot is chosen
                    // right after this via the selection gate, so it is not stored here.
                    val sharedPref = getSharedPreferences("gridee_prefs", MODE_PRIVATE)
                    sharedPref.edit()
                        .putString("user_id", state.user.id)
                        .putString("user_name", state.user.name)
                        .putString("user_email", state.user.email)
                        .putString("user_phone", state.user.phone)
                        .putBoolean("is_logged_in", true)
                        .apply()

                    NotificationHelper.showSuccess(binding.rootContainer, message = "Registration successful! Welcome to Gridee!")

                    // Continue directly into the same category + lot screens used by
                    // Profile. Home opens only after the selected lot is persisted.
                    val homeExtras = Bundle().apply {
                        putString("USER_NAME", state.user.name)
                        putBoolean(MainContainerActivity.EXTRA_SHOW_SIGNUP_GIFT, true)
                    }
                    val intent = ChooseCategoryActivity.onboardingIntent(this, homeExtras)
                    intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                    startActivity(intent)
                    finish()
                }
                is RegistrationState.VerificationSent -> {
                    showLoading(false)
                    val intent = Intent(this, EmailVerificationActivity::class.java)
                    intent.putExtra(EmailVerificationActivity.EXTRA_EMAIL, state.email)
                    intent.putExtra(EmailVerificationActivity.EXTRA_NEW_ACCOUNT, true)
                    startActivity(intent)
                    finish()
                }
                is RegistrationState.Error -> {
                    showLoading(false)
                    if (state.isRetryable) {
                        NotificationHelper.showWarning(
                            binding.rootContainer,
                            title = state.title,
                            message = state.message,
                            onClick = { registerUser() },
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
            clearErrors()
            errors.forEach { (field, message) ->
                when (field) {
                    "name" -> binding.tilName.error = message
                    "email" -> binding.tilEmail.error = message
                    "phone" -> binding.tilPhone.error = message
                    "password" -> binding.tilPassword.error = message
                }
            }
        }
    }

    private fun showLoading(show: Boolean) {
        binding.progressBar.visibility = if (show) View.VISIBLE else View.GONE
        binding.btnRegister.isEnabled = !show
        binding.btnRegister.text = if (show) "" else "Register"
    }

    private fun clearErrors() {
        binding.tilName.error = null
        binding.tilEmail.error = null
        binding.tilPhone.error = null
        binding.tilPassword.error = null
    }
}
