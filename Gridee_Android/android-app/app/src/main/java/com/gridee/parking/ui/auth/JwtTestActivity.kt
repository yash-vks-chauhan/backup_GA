package com.gridee.parking.ui.auth

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import com.gridee.parking.R
import com.gridee.parking.data.repository.UserRepository
import com.gridee.parking.ui.main.MainContainerActivity
import com.gridee.parking.utils.JwtTokenManager

/**
 * Dedicated test activity for JWT authentication
 * 
 * This activity is designed specifically for testing the JWT authentication
 * implementation without interfering with your existing login flow.
 * 
 * Features:
 * - Test login with JWT
 * - View stored token
 * - Check authentication status
 * - Test logout
 * - View all auth data
 * 
 * To use:
 * 1. Add to AndroidManifest.xml as LAUNCHER (temporarily)
 * 2. Build and install app
 * 3. Test all features
 * 4. Remove LAUNCHER when done testing
 */
class JwtTestActivity : AppCompatActivity() {
    
    private val TAG = "JwtTestActivity"
    private val viewModel: JwtLoginViewModel by viewModels()
    
    // UI Elements
    private lateinit var etEmail: EditText
    private lateinit var etPassword: EditText
    private lateinit var btnLogin: Button
    private lateinit var btnCheckAuth: Button
    private lateinit var btnViewToken: Button
    private lateinit var btnViewUserInfo: Button
    private lateinit var btnFetchOAuth2User: Button
    private lateinit var btnLogout: Button
    private lateinit var btnClearLogs: Button
    private lateinit var progressBar: ProgressBar
    private lateinit var tvStatus: TextView
    private lateinit var statusDot: View
    private lateinit var tvLogs: TextView
    private lateinit var btnBack: android.widget.ImageButton
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_jwt_test)
        
        // Set light status bar
        window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR

        initViews()
        setupClickListeners()
        observeViewModel()
        
        // Check initial auth status
        checkAuthenticationStatus()
        
        addLog("✅ Console initialized")
        addLog("🔗 Endpoint: ${com.gridee.parking.config.ApiConfig.BASE_URL}")
    }
    
    private fun initViews() {
        etEmail = findViewById(R.id.etTestEmail)
        etPassword = findViewById(R.id.etTestPassword)
        btnLogin = findViewById(R.id.btnTestLogin)
        btnCheckAuth = findViewById(R.id.btnCheckAuth)
        btnViewToken = findViewById(R.id.btnViewToken)
        btnViewUserInfo = findViewById(R.id.btnViewUserInfo)
        btnFetchOAuth2User = findViewById(R.id.btnFetchOAuth2User)
        btnLogout = findViewById(R.id.btnTestLogout)
        btnClearLogs = findViewById(R.id.btnClearLogs)
        progressBar = findViewById(R.id.progressBarTest)
        tvStatus = findViewById(R.id.tvStatus)
        statusDot = findViewById(R.id.statusDot)
        tvLogs = findViewById(R.id.tvLogs)
        btnBack = findViewById(R.id.btnBack)
    }
    
    private fun setupClickListeners() {
        btnBack.setOnClickListener {
            finish()
        }
        
        btnLogin.setOnClickListener {
            testLogin()
        }
        
        btnCheckAuth.setOnClickListener {
            checkAuthenticationStatus()
        }
        
        btnViewToken.setOnClickListener {
            viewToken()
        }
        
        btnViewUserInfo.setOnClickListener {
            viewUserInfo()
        }

        btnFetchOAuth2User.setOnClickListener {
            fetchOAuth2User()
        }
        
        btnLogout.setOnClickListener {
            testLogout()
        }
        
        btnClearLogs.setOnClickListener {
            clearLogs()
        }
    }

    private fun fetchOAuth2User() {
        addLog("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
        addLog("🌐 Fetching OAuth2 user from /api/oauth2/user...")

        progressBar.visibility = View.VISIBLE

        lifecycleScope.launch {
            try {
                val response = UserRepository().getOAuth2User()
                progressBar.visibility = View.GONE

                if (response.isSuccessful) {
                    val body = response.body()
                    addLog("✅ OAuth2 user info fetched successfully")
                    addLog("📦 Response: ${body}")

                    val message = body?.entries?.joinToString("\n") { (k, v) -> "$k: $v" } ?: "<empty>"
                    
                    com.gridee.parking.ui.bottomsheet.UniversalBottomSheet.newInstance(
                        title = "OAuth2 User Info",
                        message = message,
                        buttonText = "Close",
                        isRewardMode = false
                    ).show(supportFragmentManager, "oauth2_info")
                    
                } else {
                    val err = response.errorBody()?.string()
                    addLog("❌ OAuth2 user fetch failed: ${response.code()} ${response.message()}")
                    addLog("📝 Error Body: ${err}")
                    Toast.makeText(this@JwtTestActivity, "Error: ${response.code()} ${response.message()}", Toast.LENGTH_LONG).show()
                }
            } catch (e: Exception) {
                progressBar.visibility = View.GONE
                addLog("❌ Network error while fetching OAuth2 user: ${e.message}")
                Toast.makeText(this@JwtTestActivity, "Network error: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun observeViewModel() {
        viewModel.authState.observe(this) { state ->
            when (state) {
                is JwtAuthState.Idle -> {
                    progressBar.visibility = View.GONE
                    updateStatus("⚪ Idle", android.R.color.darker_gray)
                }
                
                is JwtAuthState.Loading -> {
                    progressBar.visibility = View.VISIBLE
                    updateStatus("🔄 Loading...", android.R.color.holo_blue_light)
                    addLog("🔄 Login in progress...")
                }
                
                is JwtAuthState.Success -> {
                    progressBar.visibility = View.GONE
                    updateStatus("✅ Authenticated", android.R.color.holo_green_light)
                    
                    val auth = state.authResponse
                    addLog("✅ LOGIN SUCCESS!")
                    addLog("📝 Token: ${auth.token.take(20)}...")
                    addLog("👤 User ID: ${auth.id}")
                    addLog("👤 Name: ${auth.name}")
                    addLog("🎭 Role: ${auth.role}")
                    
                    Toast.makeText(this, "Login successful!", Toast.LENGTH_SHORT).show()
                    
                    // Show success dialog
                    showSuccessDialog(auth.name)
                }
                
                is JwtAuthState.Error -> {
                    progressBar.visibility = View.GONE
                    updateStatus("❌ Error", android.R.color.holo_red_light)
                    addLog("❌ LOGIN FAILED: ${state.message}")
                    
                    Toast.makeText(this, "Error: ${state.message}", Toast.LENGTH_LONG).show()
                }
                
                is JwtAuthState.LoggedOut -> {
                    progressBar.visibility = View.GONE
                    updateStatus("🚪 Logged Out", android.R.color.darker_gray)
                    addLog("🚪 User logged out successfully")
                    
                    Toast.makeText(this, "Logged out", Toast.LENGTH_SHORT).show()
                }
            }
        }
        
        viewModel.validationErrors.observe(this) { errors ->
            if (errors.isNotEmpty()) {
                errors.forEach { (field, message) ->
                    addLog("⚠️ Validation Error - $field: $message")
                }
            }
        }
    }
    
    private fun testLogin() {
        val email = etEmail.text.toString()
        val password = etPassword.text.toString()
        
        addLog("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
        addLog("🔐 Starting JWT Login Test")
        addLog("📧 Email: $email")
        addLog("🔑 Password: ${password.take(3)}***")
        
        viewModel.loginWithJwt(this, email, password)
    }
    
    private fun checkAuthenticationStatus() {
        addLog("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
        addLog("🔍 Checking authentication status...")
        
        val isAuth = viewModel.checkAuthentication(this)
        
        if (isAuth) {
            updateStatus("✅ Authenticated", android.R.color.holo_green_light)
            addLog("✅ User IS authenticated")
            
            val jwtManager = JwtTokenManager(this)
            addLog("👤 User ID: ${jwtManager.getUserId()}")
            addLog("👤 Name: ${jwtManager.getUserName()}")
            addLog("🎭 Role: ${jwtManager.getUserRole()}")
        } else {
            updateStatus("❌ Not Authenticated", android.R.color.holo_red_light)
            addLog("❌ User is NOT authenticated")
        }
    }
    
    private fun viewToken() {
        addLog("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
        addLog("🔑 Viewing JWT Token...")
        
        val jwtManager = JwtTokenManager(this)
        val token = jwtManager.getAuthToken()
        
        if (token != null) {
            addLog("✅ Token found!")
            addLog("📝 Token: ${token.take(50)}...")
            addLog("📝 Bearer: ${jwtManager.getBearerToken()?.take(50)}...")
            addLog("📏 Length: ${token.length} characters")
            
            // Show full token in Bottom Sheet
            val bottomSheet = com.gridee.parking.ui.bottomsheet.UniversalBottomSheet.newInstance(
                title = "JWT Token",
                message = token,
                buttonText = "Copy Token",
                isRewardMode = false
            )
            
            bottomSheet.setPrimaryButton("Copy Token") {
                copyToClipboard(token)
            }
            
            bottomSheet.show(supportFragmentManager, "view_token")
            
        } else {
            addLog("❌ No token found")
            Toast.makeText(this, "No token available", Toast.LENGTH_SHORT).show()
        }
    }
    
    private fun viewUserInfo() {
        addLog("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
        addLog("👤 Viewing User Info...")
        
        val jwtManager = JwtTokenManager(this)
        val authData = jwtManager.getAuthData()
        
        val info = StringBuilder()
        authData.forEach { (key, value) ->
            val displayValue = if (key == "token" && value != null) {
                "${value.take(30)}..."
            } else {
                value ?: "null"
            }
            addLog("📋 $key: $displayValue")
            info.append("$key: $displayValue\n")
        }
        
        // Show in Bottom Sheet
        com.gridee.parking.ui.bottomsheet.UniversalBottomSheet.newInstance(
            title = "User Details",
            message = info.toString(),
            buttonText = "Close",
            isRewardMode = false
        ).show(supportFragmentManager, "user_info")
    }
    
    private fun testLogout() {
        addLog("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
        addLog("🚪 Testing Logout...")
        
        val bottomSheet = com.gridee.parking.ui.bottomsheet.UniversalBottomSheet.newInstance(
            title = "Confirm Logout",
            message = "Are you sure you want to terminate this session? This will clear all stored JWT tokens.",
            buttonText = "Logout",
            isRewardMode = false
        )
        
        bottomSheet.setPrimaryButton("Logout") {
            viewModel.logout(this)
            clearInputFields()
        }
        
        bottomSheet.show(supportFragmentManager, "logout_confirm")
    }
    
    private fun clearLogs() {
        tvLogs.text = ""
        addLog("🧹 Logs cleared")
    }
    
    private fun clearInputFields() {
        etEmail.text.clear()
        etPassword.text.clear()
    }
    
    private fun updateStatus(status: String, colorResId: Int) {
        tvStatus.text = status
        
        // Map the legacy color IDs to our monochrome/status colors if needed, 
        // or just use color tint for the dot
        val color = try {
            if (colorResId == android.R.color.holo_green_light) {
                 android.graphics.Color.parseColor("#111111") // Active = Black
            } else if (colorResId == android.R.color.holo_red_light) {
                 android.graphics.Color.parseColor("#757575") // Error = Grey
            } else if (colorResId == android.R.color.holo_blue_light) {
                 android.graphics.Color.parseColor("#424242") // Loading = Dark Grey
            } else {
                 android.graphics.Color.parseColor("#BDBDBD") // Idle = Light Grey
            }
        } catch (e: Exception) {
            android.graphics.Color.parseColor("#BDBDBD")
        }
        
        statusDot.backgroundTintList = android.content.res.ColorStateList.valueOf(color)
        
        // Keep text color always dark grey/black for consistency
        tvStatus.setTextColor(android.graphics.Color.parseColor("#212121"))
    }
    
    private fun addLog(message: String) {
        val timestamp = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault())
            .format(java.util.Date())
        val logMessage = "[$timestamp] $message\n"
        
        Log.d(TAG, message)
        
        runOnUiThread {
            tvLogs.append(logMessage)
            // Auto-scroll to bottom
            val scrollView = findViewById<androidx.core.widget.NestedScrollView>(R.id.scrollViewLogs)
            scrollView.post {
                scrollView.fullScroll(View.FOCUS_DOWN)
            }
        }
    }
    
    private fun copyToClipboard(text: String) {
        val clipboard = getSystemService(CLIPBOARD_SERVICE) as android.content.ClipboardManager
        val clip = android.content.ClipData.newPlainText("JWT Token", text)
        clipboard.setPrimaryClip(clip)
        Toast.makeText(this, "Token copied to clipboard", Toast.LENGTH_SHORT).show()
    }
    
    private fun showSuccessDialog(userName: String) {
        val bottomSheet = com.gridee.parking.ui.bottomsheet.UniversalBottomSheet.newInstance(
            title = "Welcome, $userName!",
            message = "Authentication was successful. You now have a valid JWT token.",
            buttonText = "Go to Main App",
            lottieFileName = "success_check.json", // Optional: if you have this file
            isRewardMode = false
        )
        
        bottomSheet.setPrimaryButton("Go to Main App") {
            navigateToMainApp()
        }
        
        bottomSheet.show(supportFragmentManager, "login_success")
    }
    
    private fun navigateToMainApp() {
        val intent = Intent(this, MainContainerActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        finish()
    }
}
