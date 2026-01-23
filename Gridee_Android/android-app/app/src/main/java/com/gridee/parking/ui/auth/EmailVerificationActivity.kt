package com.gridee.parking.ui.auth

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.gridee.parking.databinding.ActivityEmailVerificationBinding
import com.gridee.parking.ui.main.MainContainerActivity
import com.gridee.parking.ui.operator.OperatorDashboardActivity
import com.gridee.parking.utils.AuthSession
import java.util.Locale

class EmailVerificationActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_EMAIL = "extra_email"
        private const val POLL_INTERVAL_MS = 4000L
    }

    private lateinit var binding: ActivityEmailVerificationBinding
    private val viewModel: EmailVerificationViewModel by viewModels()
    private val firebaseAuth: FirebaseAuth = FirebaseAuth.getInstance()
    private val handler = Handler(Looper.getMainLooper())
    private var isChecking = false
    private var isExchanging = false
    private var isCompleted = false

    private val pollRunnable = object : Runnable {
        override fun run() {
            checkVerificationStatus()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityEmailVerificationBinding.inflate(layoutInflater)
        setContentView(binding.root)

        window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
        window.statusBarColor = android.graphics.Color.parseColor("#F5F5F5")

        val email = intent.getStringExtra(EXTRA_EMAIL).orEmpty()
        if (email.isNotBlank()) {
            binding.tvEmail.text = email
        } else {
            binding.tvEmail.text = firebaseAuth.currentUser?.email.orEmpty()
        }

        setupUi()
        observeViewModel()
    }

    override fun onStart() {
        super.onStart()
        startPolling()
    }

    override fun onStop() {
        super.onStop()
        stopPolling()
    }

    private fun setupUi() {
        binding.btnOpenEmailApp.setOnClickListener {
            it.performHapticFeedback(android.view.HapticFeedbackConstants.VIRTUAL_KEY)
            openEmailApp()
        }

        binding.btnResendEmail.setOnClickListener {
            it.performHapticFeedback(android.view.HapticFeedbackConstants.VIRTUAL_KEY)
            resendVerificationEmail()
        }

        binding.btnIHaveVerified.setOnClickListener {
            it.performHapticFeedback(android.view.HapticFeedbackConstants.VIRTUAL_KEY)
            checkVerificationStatus()
        }
    }

    private fun observeViewModel() {
        viewModel.state.observe(this) { state ->
            when (state) {
                is EmailVerificationState.Loading -> showLoading(true)
                is EmailVerificationState.Success -> {
                    isCompleted = true
                    showLoading(false)
                    persistLegacySession(state.user)
                    handlePostVerification(state.user)
                }
                is EmailVerificationState.Error -> {
                    isExchanging = false
                    showLoading(false)
                    Toast.makeText(this, state.message, Toast.LENGTH_LONG).show()
                    if (!isCompleted) {
                        binding.tvStatus.text = "Waiting for verification..."
                        scheduleNextPoll()
                    }
                }
            }
        }
    }

    private fun startPolling() {
        if (isCompleted || isExchanging) return
        handler.post(pollRunnable)
    }

    private fun stopPolling() {
        handler.removeCallbacks(pollRunnable)
    }

    private fun scheduleNextPoll() {
        if (!isCompleted && !isExchanging) {
            handler.postDelayed(pollRunnable, POLL_INTERVAL_MS)
        }
    }

    private fun checkVerificationStatus() {
        if (isChecking || isCompleted || isExchanging) return

        val user = firebaseAuth.currentUser
        if (user == null) {
            Toast.makeText(this, "Please sign in again to verify your email", Toast.LENGTH_LONG).show()
            startActivity(Intent(this, LoginActivity::class.java))
            finish()
            return
        }

        isChecking = true
        binding.tvStatus.text = "Checking verification status..."

        user.reload()
            .addOnCompleteListener { task ->
                isChecking = false
                if (!task.isSuccessful) {
                    binding.tvStatus.text = "Waiting for verification..."
                    scheduleNextPoll()
                    return@addOnCompleteListener
                }

                if (user.isEmailVerified) {
                    onVerified(user)
                } else {
                    binding.tvStatus.text = "Waiting for verification..."
                    scheduleNextPoll()
                }
            }
    }

    private fun onVerified(user: FirebaseUser) {
        isExchanging = true
        stopPolling()
        binding.tvStatus.text = "Email verified. Signing you in..."
        showLoading(true)

        user.getIdToken(true)
            .addOnCompleteListener { tokenTask ->
                if (!tokenTask.isSuccessful) {
                    showLoading(false)
                    isExchanging = false
                    Toast.makeText(this, "Failed to complete verification. Try again.", Toast.LENGTH_LONG).show()
                    scheduleNextPoll()
                    return@addOnCompleteListener
                }

                val idToken = tokenTask.result?.token
                if (idToken.isNullOrBlank()) {
                    showLoading(false)
                    isExchanging = false
                    Toast.makeText(this, "Missing verification token. Try again.", Toast.LENGTH_LONG).show()
                    scheduleNextPoll()
                    return@addOnCompleteListener
                }

                viewModel.exchangeFirebaseToken(this, idToken)
            }
    }

    private fun resendVerificationEmail() {
        val user = firebaseAuth.currentUser
        if (user == null) {
            Toast.makeText(this, "Please sign in again to resend the email", Toast.LENGTH_LONG).show()
            return
        }

        user.sendEmailVerification()
            .addOnSuccessListener {
                Toast.makeText(this, "Verification email sent", Toast.LENGTH_LONG).show()
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "Failed to send email: ${e.message}", Toast.LENGTH_LONG).show()
            }
    }

    private fun openEmailApp() {
        val intent = Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_APP_EMAIL)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }

        if (intent.resolveActivity(packageManager) != null) {
            startActivity(intent)
        } else {
            Toast.makeText(this, "No email app found", Toast.LENGTH_SHORT).show()
        }
    }

    private fun showLoading(show: Boolean) {
        binding.progressBar.visibility = if (show) View.VISIBLE else View.GONE
        binding.btnOpenEmailApp.isEnabled = !show
        binding.btnResendEmail.isEnabled = !show
        binding.btnIHaveVerified.isEnabled = !show
    }

    private fun navigateToHome(user: com.gridee.parking.data.model.User) {
        val normalizedRole = user.role?.uppercase(Locale.ROOT) ?: "USER"
        when (normalizedRole) {
            "OPERATOR" -> {
                val intent = Intent(this, OperatorDashboardActivity::class.java)
                intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                startActivity(intent)
            }
            else -> {
                val intent = Intent(this, MainContainerActivity::class.java)
                intent.putExtra("USER_NAME", user.name)
                intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                startActivity(intent)
            }
        }
        finish()
    }

    private fun handlePostVerification(user: com.gridee.parking.data.model.User) {
        val normalizedRole = user.role?.uppercase(Locale.ROOT) ?: "USER"
        if (normalizedRole == "OPERATOR") {
            navigateToHome(user)
            return
        }
        if (user.vehicleNumbers.isEmpty()) {
            val intent = Intent(this, AddVehicleActivity::class.java)
            intent.putExtra(AddVehicleActivity.EXTRA_USER_ID, user.id)
            intent.putExtra(AddVehicleActivity.EXTRA_USER_NAME, user.name)
            intent.putExtra(AddVehicleActivity.EXTRA_USER_ROLE, user.role)
            startActivity(intent)
            finish()
        } else {
            navigateToHome(user)
        }
    }

    private fun persistLegacySession(user: com.gridee.parking.data.model.User) {
        val normalizedRole = user.role?.uppercase(Locale.ROOT) ?: "USER"
        val resolvedUserId = user.id ?: AuthSession.getUserId(this)

        val sharedPref = getSharedPreferences("gridee_prefs", MODE_PRIVATE)
        sharedPref.edit()
            .putString("user_id", resolvedUserId)
            .putString("user_name", user.name)
            .putString("user_email", user.email)
            .putString("user_phone", user.phone)
            .putString("user_role", normalizedRole)
            .putString("parking_lot_name", user.parkingLotName)
            .putBoolean("is_logged_in", true)
            .apply()
    }
}
