package com.gridee.parking.ui.auth

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.activity.OnBackPressedCallback
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import com.gridee.parking.databinding.ActivityAddPhoneBinding
import com.gridee.parking.utils.NotificationHelper
import com.gridee.parking.ui.main.MainContainerActivity
import com.gridee.parking.ui.operator.OperatorDashboardActivity
import com.gridee.parking.utils.AuthSession
import java.util.Locale

class AddPhoneActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_USER_ID = "extra_user_id"
        const val EXTRA_USER_NAME = "extra_user_name"
        const val EXTRA_USER_ROLE = "extra_user_role"
        const val EXTRA_REQUIRE_VEHICLE = "extra_require_vehicle"
    }

    private lateinit var binding: ActivityAddPhoneBinding
    private val viewModel: AddPhoneViewModel by viewModels()

    private var userId: String? = null
    private var userName: String? = null
    private var userRole: String? = null
    private var requireVehicle: Boolean = false
    private var showSignupGift: Boolean = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAddPhoneBinding.inflate(layoutInflater)
        setContentView(binding.root)

        window.statusBarColor = androidx.core.content.ContextCompat.getColor(this, com.gridee.parking.R.color.background_primary)
        androidx.core.view.WindowInsetsControllerCompat(window, window.decorView).isAppearanceLightStatusBars =
            !com.gridee.parking.utils.ThemeManager.isDarkMode(this)

        userId = intent.getStringExtra(EXTRA_USER_ID) ?: AuthSession.getUserId(this)
        userName = intent.getStringExtra(EXTRA_USER_NAME) ?: AuthSession.getUserName(this)
        userRole = intent.getStringExtra(EXTRA_USER_ROLE) ?: AuthSession.getUserRole(this)
        requireVehicle = intent.getBooleanExtra(EXTRA_REQUIRE_VEHICLE, false)
        showSignupGift = intent.getBooleanExtra(MainContainerActivity.EXTRA_SHOW_SIGNUP_GIFT, false)

        if (userId.isNullOrBlank()) {
            startActivity(Intent(this, LoginActivity::class.java))
            finish()
            return
        }

        setupUi()
        observeViewModel()
        disableBackNavigation()
    }

    private fun setupUi() {
        binding.btnContinue.setOnClickListener {
            it.performHapticFeedback(android.view.HapticFeedbackConstants.VIRTUAL_KEY)
            viewModel.clearErrors()
            val phoneNumber = binding.etPhone.text?.toString().orEmpty()
            viewModel.updatePhone(userId, phoneNumber)
        }

        binding.etPhone.setOnFocusChangeListener { _, _ ->
            viewModel.clearErrors()
        }
    }

    private fun observeViewModel() {
        viewModel.state.observe(this) { state ->
            when (state) {
                is AddPhoneState.Loading -> showLoading(true)
                is AddPhoneState.Success -> {
                    showLoading(false)
                    handlePhoneSaved(state.phone)
                }
                is AddPhoneState.Error -> {
                    showLoading(false)
                    if (state.isRetryable) {
                        NotificationHelper.showWarning(
                            binding.rootContainer,
                            title = state.title,
                            message = state.message,
                            onClick = { binding.btnContinue.performClick() },
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
            binding.tilPhone.error = errors["phone"]
            if (errors["user"] != null) {
                NotificationHelper.showError(binding.rootContainer, message = errors["user"] ?: "")
            }
        }
    }

    private fun showLoading(show: Boolean) {
        binding.progressBar.visibility = if (show) View.VISIBLE else View.GONE
        binding.btnContinue.isEnabled = !show
        binding.btnContinue.text = if (show) "" else "Continue"
    }

    private fun handlePhoneSaved(phone: String) {
        val sharedPref = getSharedPreferences("gridee_prefs", MODE_PRIVATE)
        sharedPref.edit()
            .putString("user_id", userId)
            .putString("user_phone", phone)
            .putBoolean("is_logged_in", true)
            .apply()

        if (requireVehicle) {
            val intent = Intent(this, AddVehicleActivity::class.java)
            intent.putExtra(AddVehicleActivity.EXTRA_USER_ID, userId)
            intent.putExtra(AddVehicleActivity.EXTRA_USER_NAME, userName)
            intent.putExtra(AddVehicleActivity.EXTRA_USER_ROLE, userRole)
            intent.putExtra(MainContainerActivity.EXTRA_SHOW_SIGNUP_GIFT, showSignupGift)
            startActivity(intent)
            finish()
            return
        }

        navigateToHome()
    }

    private fun navigateToHome() {
        val normalizedRole = userRole?.uppercase(Locale.ROOT) ?: "USER"
        when (normalizedRole) {
            "OPERATOR" -> {
                val intent = Intent(this, OperatorDashboardActivity::class.java)
                intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                startActivity(intent)
            }
            else -> {
                val intent = Intent(this, MainContainerActivity::class.java)
                userName?.let { intent.putExtra("USER_NAME", it) }
                intent.putExtra(MainContainerActivity.EXTRA_SHOW_SIGNUP_GIFT, showSignupGift)
                intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                startActivity(intent)
            }
        }
        finish()
    }

    private fun disableBackNavigation() {
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                // Phone entry is required before proceeding.
            }
        })
    }
}
