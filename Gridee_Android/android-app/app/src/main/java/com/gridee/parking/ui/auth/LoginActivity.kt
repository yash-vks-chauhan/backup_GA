package com.gridee.parking.ui.auth

import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.view.animation.PathInterpolator
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import com.gridee.parking.R
import com.gridee.parking.config.RemoteConfigManager
import com.gridee.parking.databinding.ActivityLoginBinding
import com.gridee.parking.ui.main.MainContainerActivity
import com.gridee.parking.ui.operator.OperatorDashboardActivity
import com.gridee.parking.utils.AuthSession
import com.gridee.parking.utils.GoogleSignInManager
import com.gridee.parking.utils.GoogleSignInResult
import com.gridee.parking.utils.NotificationHelper
import java.util.Locale

class LoginActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_FORCE_LOGIN = "extra_force_login"
    }
    
    private lateinit var binding: ActivityLoginBinding
    private val viewModel: LoginViewModel by viewModels()


    
    private lateinit var googleSignInManager: GoogleSignInManager
    
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
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // If a valid JWT is already stored, skip login and go directly to the correct home screen.
        // This prevents users from being sent back to the login page every time the app is reopened/updated.
        val forceLogin = intent?.getBooleanExtra(EXTRA_FORCE_LOGIN, false) ?: false
        if (!forceLogin && AuthSession.isAuthenticated(this)) {
            AuthSession.syncLegacyPrefsFromJwt(this)
            navigateToHomeFromSession()
            return
        }

        binding = ActivityLoginBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        // Theme-aware status bar
        window.statusBarColor = androidx.core.content.ContextCompat.getColor(this, R.color.background_primary)
        androidx.core.view.WindowInsetsControllerCompat(window, window.decorView).isAppearanceLightStatusBars =
            !com.gridee.parking.utils.ThemeManager.isDarkMode(this)
        
        // Initialize sign-in managers
        googleSignInManager = GoogleSignInManager(this)
        
        setupUI()
        observeViewModel()
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
    
    private fun setupUI() {
        // Sign In button click
        binding.btnSignIn.setOnClickListener {
            if (!RemoteConfigManager.isEmailSignInEnabled()) {
                NotificationHelper.showWarning(binding.rootContainer, message = "Email sign-in is temporarily unavailable.")
                return@setOnClickListener
            }
            it.performHapticFeedback(android.view.HapticFeedbackConstants.VIRTUAL_KEY)
            val emailOrPhone = binding.etEmailPhone.text.toString()
            val password = binding.etPassword.text.toString()
            viewModel.loginUser(this, emailOrPhone, password)
        }
        
        // Password visibility toggle with custom animation
        com.gridee.parking.ui.utils.PasswordBlurAnimator(this, binding.tilPassword, binding.etPassword)
        
        // Forgot password click
        binding.tvForgotPassword.setOnClickListener {
            it.performHapticFeedback(android.view.HapticFeedbackConstants.VIRTUAL_KEY)
            startActivity(Intent(this, ForgotPasswordActivity::class.java))
        }
        
        // Google Sign In
        binding.btnSignInWithGoogle.setOnClickListener {
            if (!RemoteConfigManager.isGoogleSignInEnabled()) {
                NotificationHelper.showWarning(binding.rootContainer, message = "Google sign-in is temporarily unavailable.")
                return@setOnClickListener
            }
            it.performHapticFeedback(android.view.HapticFeedbackConstants.VIRTUAL_KEY)
            googleSignInManager.launchSignIn(googleSignInLauncher)
        }
        
        // Sign Up link
        binding.tvSignUpLink.setOnClickListener {
            it.performHapticFeedback(android.view.HapticFeedbackConstants.VIRTUAL_KEY)
            startActivity(Intent(this, RegistrationActivity::class.java))
        }
        
        // Clear errors when user starts typing
        binding.etEmailPhone.setOnFocusChangeListener { _, _ ->
            viewModel.clearErrors()
        }
        
        binding.etPassword.setOnFocusChangeListener { _, _ ->
            viewModel.clearErrors()
        }

        applyFeatureSwitches()
    }

    private fun applyFeatureSwitches() {
        RemoteConfigManager.loadCached(this)
        val emailEnabled = RemoteConfigManager.isEmailSignInEnabled()
        val googleEnabled = RemoteConfigManager.isGoogleSignInEnabled()

        binding.btnSignIn.isEnabled = emailEnabled
        binding.btnSignIn.alpha = if (emailEnabled) 1f else 0.45f
        binding.etEmailPhone.isEnabled = emailEnabled
        binding.etPassword.isEnabled = emailEnabled
        binding.tvForgotPassword.isEnabled = emailEnabled
        binding.tvForgotPassword.alpha = if (emailEnabled) 1f else 0.45f
        binding.tvSignUpLink.isEnabled = emailEnabled
        binding.tvSignUpLink.alpha = if (emailEnabled) 1f else 0.45f
        binding.btnSignInWithGoogle.visibility = if (googleEnabled) View.VISIBLE else View.GONE
    }
    

    
    private fun observeViewModel() {
        viewModel.loginState.observe(this) { state ->
            when (state) {
                is LoginState.Loading -> {
                    showLoading(true)
                }
                is LoginState.Success -> {
                    showLoading(false)
                    
                    val normalizedRole = state.user.role?.uppercase(Locale.ROOT) ?: "USER"
                    val resolvedUserId = state.user.id ?: AuthSession.getUserId(this)
                    
                    // Save user data to SharedPreferences
                    val sharedPref = getSharedPreferences("gridee_prefs", MODE_PRIVATE)
                    sharedPref.edit()
                        .putString("user_id", resolvedUserId)
                        .putString("user_name", state.user.name)
                        .putString("user_email", state.user.email)
                        .putString("user_phone", state.user.phone)
                        .putString("user_role", normalizedRole)
                        .putString("parking_lot_id", state.user.parkingLotId)
                        .putString("parking_lot_name", state.user.parkingLotName)
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
                            val requiresPhone = state.user.phone.isBlank()
                            val requiresVehicle = state.user.vehicleNumbers.isEmpty()

                            if (requiresPhone) {
                                val intent = Intent(this, AddPhoneActivity::class.java)
                                intent.putExtra(AddPhoneActivity.EXTRA_USER_ID, resolvedUserId)
                                intent.putExtra(AddPhoneActivity.EXTRA_USER_NAME, state.user.name)
                                intent.putExtra(AddPhoneActivity.EXTRA_USER_ROLE, normalizedRole)
                                intent.putExtra(AddPhoneActivity.EXTRA_REQUIRE_VEHICLE, requiresVehicle)
                                startActivity(intent)
                                finish()
                            } else if (requiresVehicle) {
                                val intent = Intent(this, AddVehicleActivity::class.java)
                                intent.putExtra(AddVehicleActivity.EXTRA_USER_ID, resolvedUserId)
                                intent.putExtra(AddVehicleActivity.EXTRA_USER_NAME, state.user.name)
                                intent.putExtra(AddVehicleActivity.EXTRA_USER_ROLE, normalizedRole)
                                startActivity(intent)
                                finish()
                            } else {
                                val intent = Intent(this, MainContainerActivity::class.java)
                                intent.putExtra("USER_NAME", state.user.name)
                                intent.putExtra(MainContainerActivity.EXTRA_SHOW_LOGIN_WELCOME, true)
                                intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                                startActivity(intent)
                                finish()
                            }
                        }
                    }
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
                            onClick = { binding.btnSignIn.performClick() },
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
        
        viewModel.validationErrors.observe(this) { errors ->
            clearErrors()
            errors.forEach { (field, message) ->
                when (field) {
                    "emailPhone" -> binding.tilEmailPhone.error = message
                    "password" -> binding.tilPassword.error = message
                }
            }
        }
    }
    
    private var isLoadingVisible = false

    private fun showLoading(show: Boolean) {
        if (show == isLoadingVisible) return
        isLoadingVisible = show

        val easeOut = PathInterpolator(0.22f, 1.0f, 0.36f, 1.0f)

        if (show) {
            binding.btnSignIn.isEnabled = false
            binding.btnSignInWithGoogle.isEnabled = false

            binding.loadingScrim.visibility = View.VISIBLE
            binding.loadingContent.visibility = View.VISIBLE

            val scrimFade = ObjectAnimator.ofFloat(binding.loadingScrim, View.ALPHA, 0f, 0.85f)
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
                        binding.btnSignIn.isEnabled = true
                        binding.btnSignIn.text = "Sign In"
                        binding.btnSignInWithGoogle.isEnabled = true
                    }
                })
                start()
            }
        }
    }
    
    private fun clearErrors() {
        binding.tilEmailPhone.error = null
        binding.tilPassword.error = null
    }

}
