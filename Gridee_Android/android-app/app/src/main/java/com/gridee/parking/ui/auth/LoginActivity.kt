package com.gridee.parking.ui.auth

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import com.gridee.parking.R
import com.gridee.parking.databinding.ActivityLoginBinding
import com.gridee.parking.ui.main.MainContainerActivity
import com.gridee.parking.ui.operator.OperatorDashboardActivity
import com.gridee.parking.utils.AuthSession
import com.gridee.parking.utils.GoogleSignInManager
import com.gridee.parking.utils.GoogleSignInResult
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
        
        // Set light status bar with dark icons for white background
        window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
        window.statusBarColor = android.graphics.Color.parseColor("#F5F5F5")
        
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
    }
    

    
    private fun observeViewModel() {
        viewModel.loginState.observe(this) { state ->
            when (state) {
                is LoginState.Loading -> {
                    showLoading(true)
                }
                is LoginState.Success -> {
                    showLoading(false)
                    Toast.makeText(this, "Welcome back, ${state.user.name}!", Toast.LENGTH_LONG).show()
                    
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
                    Toast.makeText(this, state.message, Toast.LENGTH_LONG).show()
                }
            }
        }

        viewModel.statusMessage.observe(this) { message ->
            if (!message.isNullOrBlank()) {
                Toast.makeText(this, message, Toast.LENGTH_LONG).show()
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
    
    private fun showLoading(show: Boolean) {
        binding.progressBar.visibility = if (show) View.VISIBLE else View.GONE
        binding.btnSignIn.isEnabled = !show
        binding.btnSignIn.text = if (show) "" else "Sign In"
        
        // Disable other buttons during loading
        binding.btnSignInWithGoogle.isEnabled = !show
    }
    
    private fun clearErrors() {
        binding.tilEmailPhone.error = null
        binding.tilPassword.error = null
    }

}
