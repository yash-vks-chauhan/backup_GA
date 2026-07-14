package com.gridee.parking.ui.auth

import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.content.Intent
import android.os.Bundle
import android.view.HapticFeedbackConstants
import android.view.View
import android.view.animation.PathInterpolator
import android.Manifest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowInsetsControllerCompat
import com.gridee.parking.config.RemoteConfigManager
import com.gridee.parking.data.model.User
import com.gridee.parking.databinding.ActivityWelcomeBinding
import com.gridee.parking.ui.main.MainContainerActivity
import com.gridee.parking.ui.operator.OperatorDashboardActivity
import com.gridee.parking.utils.AuthSession
import com.gridee.parking.utils.GoogleSignInManager
import com.gridee.parking.utils.GoogleSignInResult
import com.gridee.parking.utils.NotificationHelper
import com.gridee.parking.utils.NotificationPermissionHelper
import com.gridee.parking.R
import java.util.Locale

class WelcomeActivity : AppCompatActivity() {

    private lateinit var binding: ActivityWelcomeBinding
    private val viewModel: LoginViewModel by viewModels()
    private lateinit var googleSignInManager: GoogleSignInManager
    private var pendingSessionNavigation = false

    private val googleSignInLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val signInResult = googleSignInManager.handleSignInResult(result.data)
        when (signInResult) {
            is GoogleSignInResult.Success -> {
                viewModel.handleGoogleSignInSuccess(this, signInResult.account)
            }
            is GoogleSignInResult.Error -> {
                viewModel.handleSignInError(signInResult.message)
            }
            is GoogleSignInResult.Cancelled -> {
                // User cancelled Google sign-in; no UI message needed.
            }
        }
    }

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) {
        if (pendingSessionNavigation) {
            pendingSessionNavigation = false
            navigateToHomeFromSession()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (AuthSession.isAuthenticated(this)) {
            AuthSession.syncLegacyPrefsFromJwt(this)
            if (requestNotificationPermissionIfNeeded(navigateAfter = true)) {
                return
            }
            navigateToHomeFromSession()
            return
        }

        binding = ActivityWelcomeBinding.inflate(layoutInflater)
        setContentView(binding.root)

        configureSystemBars()

        googleSignInManager = GoogleSignInManager(this)

        requestNotificationPermissionIfNeeded(navigateAfter = false)

        setupUI()
        observeViewModel()
    }

    private fun configureSystemBars() {
        // Set transparent status bar with dark icons for light background
        window.statusBarColor = android.graphics.Color.TRANSPARENT
        window.navigationBarColor = androidx.core.content.ContextCompat.getColor(this, R.color.background_primary)
        val isDark = com.gridee.parking.utils.ThemeManager.isDarkMode(this)
        WindowInsetsControllerCompat(window, window.decorView).isAppearanceLightStatusBars = !isDark
        WindowInsetsControllerCompat(window, window.decorView).isAppearanceLightNavigationBars = !isDark
    }

    private fun setupUI() {
        binding.btnContinueWithGoogle.setOnClickListener {
            it.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
            googleSignInManager.launchSignIn(googleSignInLauncher)
        }

        binding.btnSignUpEmail.setOnClickListener {
            it.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
            startActivity(Intent(this, RegistrationActivity::class.java))
        }

        binding.btnLogin.setOnClickListener {
            it.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
            startActivity(Intent(this, LoginActivity::class.java))
        }

        setupTermsText()
        applyFeatureSwitches()
    }

    private fun applyFeatureSwitches() {
        RemoteConfigManager.loadCached(this)
        val emailEnabled = RemoteConfigManager.isEmailSignInEnabled()
        val googleEnabled = RemoteConfigManager.isGoogleSignInEnabled()

        binding.btnSignUpEmail.visibility = if (emailEnabled) View.VISIBLE else View.GONE
        binding.btnLogin.isEnabled = emailEnabled
        binding.btnLogin.alpha = if (emailEnabled) 1f else 0.45f
        binding.btnContinueWithGoogle.visibility = if (googleEnabled) View.VISIBLE else View.GONE

        if (!emailEnabled && !googleEnabled) {
            NotificationHelper.showWarning(
                parent = binding.rootContainer,
                title = "Sign-in unavailable",
                message = "Account sign-in is temporarily unavailable."
            )
        }
    }

    private fun requestNotificationPermissionIfNeeded(navigateAfter: Boolean): Boolean {
        if (!NotificationPermissionHelper.shouldRequest(this)) return false
        NotificationPermissionHelper.markRequested(this)
        if (navigateAfter) {
            pendingSessionNavigation = true
        }
        notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        return true
    }

    private fun setupTermsText() {
        val text = "By signing up, you agree to our Terms, Privacy Policy, and Data Safety."
        val spannableString = android.text.SpannableString(text)
        val blackColor = androidx.core.content.ContextCompat.getColor(this, R.color.text_primary)

        val termsClickable = object : android.text.style.ClickableSpan() {
            override fun onClick(widget: View) {
                openUrl("https://docs.gridee.in/#/terms")
            }

            override fun updateDrawState(ds: android.text.TextPaint) {
                super.updateDrawState(ds)
                ds.isUnderlineText = false
                ds.color = blackColor
                ds.typeface = android.graphics.Typeface.DEFAULT_BOLD
            }
        }

        val privacyClickable = object : android.text.style.ClickableSpan() {
            override fun onClick(widget: View) {
                openUrl("https://docs.gridee.in/#/privacy")
            }

            override fun updateDrawState(ds: android.text.TextPaint) {
                super.updateDrawState(ds)
                ds.isUnderlineText = false
                ds.color = blackColor
                ds.typeface = android.graphics.Typeface.DEFAULT_BOLD
            }
        }

        val dataSafetyClickable = object : android.text.style.ClickableSpan() {
            override fun onClick(widget: View) {
                openUrl("https://docs.gridee.in/#/data-safety")
            }

            override fun updateDrawState(ds: android.text.TextPaint) {
                super.updateDrawState(ds)
                ds.isUnderlineText = false
                ds.color = blackColor
                ds.typeface = android.graphics.Typeface.DEFAULT_BOLD
            }
        }

        // Terms
        val termsStart = text.indexOf("Terms")
        spannableString.setSpan(termsClickable, termsStart, termsStart + 5, android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)

        // Privacy Policy
        val privacyStart = text.indexOf("Privacy Policy")
        spannableString.setSpan(privacyClickable, privacyStart, privacyStart + 14, android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)

        // Data Safety
        val dataSafetyStart = text.indexOf("Data Safety")
        spannableString.setSpan(dataSafetyClickable, dataSafetyStart, dataSafetyStart + 11, android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)

        binding.tvTerms.text = spannableString
        binding.tvTerms.movementMethod = android.text.method.LinkMovementMethod.getInstance()
        binding.tvTerms.highlightColor = android.graphics.Color.TRANSPARENT
    }

    private fun openUrl(url: String) {
        try {
            val intent = Intent(Intent.ACTION_VIEW, android.net.Uri.parse(url))
            startActivity(intent)
        } catch (e: Exception) {
            NotificationHelper.showError(binding.rootContainer, message = "Unable to open link")
        }
    }

    private fun observeViewModel() {
        viewModel.loginState.observe(this) { state ->
            when (state) {
                is LoginState.Loading -> {
                    showLoading(true)
                }
                is LoginState.Success -> {
                    showLoading(false)
                    handleLoginSuccess(state.user)
                }
                is LoginState.VerificationRequired -> {
                    showLoading(false)
                    val intent = Intent(this, EmailVerificationActivity::class.java)
                    intent.putExtra(EmailVerificationActivity.EXTRA_EMAIL, state.email)
                    startActivity(intent)
                }
                is LoginState.Error -> {
                    showLoading(false)
                    if (state.isRetryable) {
                        NotificationHelper.showWarning(
                            binding.rootContainer,
                            title = state.title,
                            message = state.message,
                            onClick = { binding.btnContinueWithGoogle.performClick() },
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

        viewModel.statusMessage.observe(this) { message ->
            if (!message.isNullOrBlank()) {
                NotificationHelper.showInfo(binding.rootContainer, message = message)
            }
        }
    }

    private var isLoadingVisible = false

    private fun showLoading(show: Boolean) {
        if (show == isLoadingVisible) return
        isLoadingVisible = show

        // Apple-style deceleration curve
        val easeOut = PathInterpolator(0.22f, 1.0f, 0.36f, 1.0f)

        if (show) {
            // Disable all interaction
            binding.btnContinueWithGoogle.isEnabled = false
            binding.btnSignUpEmail.isEnabled = false
            binding.btnLogin.isEnabled = false

            // Show overlay views
            binding.loadingScrim.visibility = View.VISIBLE
            binding.loadingContent.visibility = View.VISIBLE

            // Animate scrim fade in (background dims)
            val scrimFade = ObjectAnimator.ofFloat(binding.loadingScrim, View.ALPHA, 0f, 0.85f)

            // Animate content: fade in + slight scale up from 0.9
            val contentFade = ObjectAnimator.ofFloat(binding.loadingContent, View.ALPHA, 0f, 1f)
            val contentScaleX = ObjectAnimator.ofFloat(binding.loadingContent, View.SCALE_X, 0.9f, 1f)
            val contentScaleY = ObjectAnimator.ofFloat(binding.loadingContent, View.SCALE_Y, 0.9f, 1f)

            AnimatorSet().apply {
                playTogether(scrimFade, contentFade, contentScaleX, contentScaleY)
                duration = 350L
                interpolator = easeOut
                start()
            }
        } else {
            // Animate out
            val scrimFade = ObjectAnimator.ofFloat(binding.loadingScrim, View.ALPHA, binding.loadingScrim.alpha, 0f)
            val contentFade = ObjectAnimator.ofFloat(binding.loadingContent, View.ALPHA, 1f, 0f)
            val contentScaleX = ObjectAnimator.ofFloat(binding.loadingContent, View.SCALE_X, 1f, 0.95f)
            val contentScaleY = ObjectAnimator.ofFloat(binding.loadingContent, View.SCALE_Y, 1f, 0.95f)

            AnimatorSet().apply {
                playTogether(scrimFade, contentFade, contentScaleX, contentScaleY)
                duration = 250L
                interpolator = PathInterpolator(0.55f, 0f, 1f, 0.45f)
                addListener(object : android.animation.AnimatorListenerAdapter() {
                    override fun onAnimationEnd(animation: android.animation.Animator) {
                        binding.loadingScrim.visibility = View.GONE
                        binding.loadingContent.visibility = View.GONE
                        // Restore interaction
                        binding.btnContinueWithGoogle.isEnabled = true
                        binding.btnSignUpEmail.isEnabled = true
                        binding.btnLogin.isEnabled = true
                    }
                })
                start()
            }
        }
    }

    private fun navigateToHomeFromSession() {
        val normalizedRole = AuthSession.getUserRole(this)?.uppercase(Locale.ROOT) ?: "USER"
        val userName = AuthSession.getUserName(this)

        when (normalizedRole) {
            "OPERATOR" -> {
                val intent = Intent(this, OperatorDashboardActivity::class.java)
                intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                startActivity(intent)
            }
            else -> {
                val intent = Intent(this, MainContainerActivity::class.java)
                userName?.let { intent.putExtra("USER_NAME", it) }
                intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                startActivity(intent)
            }
        }
        finish()
    }

    private fun handleLoginSuccess(user: User) {
        val normalizedRole = user.role?.uppercase(Locale.ROOT) ?: "USER"
        val resolvedUserId = user.id ?: AuthSession.getUserId(this)

        val sharedPref = getSharedPreferences("gridee_prefs", MODE_PRIVATE)
        sharedPref.edit()
            .putString("user_id", resolvedUserId)
            .putString("user_name", user.name)
            .putString("user_email", user.email)
            .putString("user_phone", user.phone)
            .putString("user_role", normalizedRole)
            .putString("parking_lot_id", user.parkingLotId)
            .putString("parking_lot_name", user.parkingLotName)
            .putBoolean("is_logged_in", true)
            .apply()

        when (normalizedRole) {
            "OPERATOR" -> {
                val intent = Intent(this, OperatorDashboardActivity::class.java)
                intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                startActivity(intent)
                finish()
            }
            else -> {
                val requiresPhone = user.phone.isBlank()
                val requiresVehicle = user.vehicleNumbers.isEmpty()

                // New Google users need phone/vehicle — thread signup gift flag through
                val isNewUser = requiresPhone || requiresVehicle

                if (requiresPhone) {
                    val intent = Intent(this, AddPhoneActivity::class.java)
                    intent.putExtra(AddPhoneActivity.EXTRA_USER_ID, resolvedUserId)
                    intent.putExtra(AddPhoneActivity.EXTRA_USER_NAME, user.name)
                    intent.putExtra(AddPhoneActivity.EXTRA_USER_ROLE, normalizedRole)
                    intent.putExtra(AddPhoneActivity.EXTRA_REQUIRE_VEHICLE, requiresVehicle)
                    intent.putExtra(MainContainerActivity.EXTRA_SHOW_SIGNUP_GIFT, true)
                    startActivity(intent)
                    finish()
                } else if (requiresVehicle) {
                    val intent = Intent(this, AddVehicleActivity::class.java)
                    intent.putExtra(AddVehicleActivity.EXTRA_USER_ID, resolvedUserId)
                    intent.putExtra(AddVehicleActivity.EXTRA_USER_NAME, user.name)
                    intent.putExtra(AddVehicleActivity.EXTRA_USER_ROLE, normalizedRole)
                    intent.putExtra(MainContainerActivity.EXTRA_SHOW_SIGNUP_GIFT, true)
                    startActivity(intent)
                    finish()
                } else if (isNewUser) {
                    val intent = Intent(this, MainContainerActivity::class.java)
                    intent.putExtra("USER_NAME", user.name)
                    intent.putExtra(MainContainerActivity.EXTRA_SHOW_SIGNUP_GIFT, true)
                    intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                    startActivity(intent)
                    finish()
                } else {
                    val intent = Intent(this, MainContainerActivity::class.java)
                    intent.putExtra("USER_NAME", user.name)
                    intent.putExtra(MainContainerActivity.EXTRA_SHOW_LOGIN_WELCOME, true)
                    intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                    startActivity(intent)
                    finish()
                }
            }
        }
    }
}
