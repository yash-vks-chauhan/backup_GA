package com.gridee.parking.ui.auth

import android.content.Intent
import android.os.Bundle
import android.view.HapticFeedbackConstants
import android.view.View
import android.widget.Toast
import android.Manifest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowInsetsControllerCompat
import com.gridee.parking.data.model.User
import com.gridee.parking.databinding.ActivityWelcomeBinding
import com.gridee.parking.ui.main.MainContainerActivity
import com.gridee.parking.ui.operator.OperatorDashboardActivity
import com.gridee.parking.utils.AuthSession
import com.gridee.parking.utils.GoogleSignInManager
import com.gridee.parking.utils.GoogleSignInResult
import com.gridee.parking.utils.NotificationPermissionHelper
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
        window.navigationBarColor = android.graphics.Color.parseColor("#F5F5F5")
        WindowInsetsControllerCompat(window, window.decorView).isAppearanceLightStatusBars = true
        WindowInsetsControllerCompat(window, window.decorView).isAppearanceLightNavigationBars = true
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
        val blackColor = android.graphics.Color.parseColor("#111827")

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
            Toast.makeText(this, "Unable to open link", Toast.LENGTH_SHORT).show()
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
                    Toast.makeText(this, state.message, Toast.LENGTH_LONG).show()
                }
            }
        }

        viewModel.statusMessage.observe(this) { message ->
            if (!message.isNullOrBlank()) {
                Toast.makeText(this, message, Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun showLoading(show: Boolean) {
        binding.progressGoogle.visibility = if (show) View.VISIBLE else View.GONE
        binding.btnContinueWithGoogle.isEnabled = !show
        binding.btnSignUpEmail.isEnabled = !show
        binding.btnLogin.isEnabled = !show
        binding.btnContinueWithGoogle.text = if (show) "" else "Continue with Google"
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
        Toast.makeText(this, "Welcome back, ${user.name}!", Toast.LENGTH_LONG).show()

        val normalizedRole = user.role?.uppercase(Locale.ROOT) ?: "USER"
        val resolvedUserId = user.id ?: AuthSession.getUserId(this)

        val sharedPref = getSharedPreferences("gridee_prefs", MODE_PRIVATE)
        sharedPref.edit()
            .putString("user_id", resolvedUserId)
            .putString("user_name", user.name)
            .putString("user_email", user.email)
            .putString("user_phone", user.phone)
            .putString("user_role", normalizedRole)
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

                if (requiresPhone) {
                    val intent = Intent(this, AddPhoneActivity::class.java)
                    intent.putExtra(AddPhoneActivity.EXTRA_USER_ID, resolvedUserId)
                    intent.putExtra(AddPhoneActivity.EXTRA_USER_NAME, user.name)
                    intent.putExtra(AddPhoneActivity.EXTRA_USER_ROLE, normalizedRole)
                    intent.putExtra(AddPhoneActivity.EXTRA_REQUIRE_VEHICLE, requiresVehicle)
                    startActivity(intent)
                    finish()
                } else if (requiresVehicle) {
                    val intent = Intent(this, AddVehicleActivity::class.java)
                    intent.putExtra(AddVehicleActivity.EXTRA_USER_ID, resolvedUserId)
                    intent.putExtra(AddVehicleActivity.EXTRA_USER_NAME, user.name)
                    intent.putExtra(AddVehicleActivity.EXTRA_USER_ROLE, normalizedRole)
                    startActivity(intent)
                    finish()
                } else {
                    val intent = Intent(this, MainContainerActivity::class.java)
                    intent.putExtra("USER_NAME", user.name)
                    intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                    startActivity(intent)
                    finish()
                }
            }
        }
    }
}
