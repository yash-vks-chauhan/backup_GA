package com.gridee.parking.ui.auth

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import com.gridee.parking.databinding.ActivityAddVehicleBinding
import com.gridee.parking.ui.main.MainContainerActivity
import com.gridee.parking.ui.operator.OperatorDashboardActivity
import com.gridee.parking.utils.AuthSession
import java.util.Locale

class AddVehicleActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_USER_ID = "extra_user_id"
        const val EXTRA_USER_NAME = "extra_user_name"
        const val EXTRA_USER_ROLE = "extra_user_role"
    }

    private lateinit var binding: ActivityAddVehicleBinding
    private val viewModel: AddVehicleViewModel by viewModels()
    private var userId: String? = null
    private var userName: String? = null
    private var userRole: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAddVehicleBinding.inflate(layoutInflater)
        setContentView(binding.root)

        window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
        window.statusBarColor = android.graphics.Color.parseColor("#F5F5F5")

        userId = intent.getStringExtra(EXTRA_USER_ID) ?: AuthSession.getUserId(this)
        userName = intent.getStringExtra(EXTRA_USER_NAME) ?: AuthSession.getUserName(this)
        userRole = intent.getStringExtra(EXTRA_USER_ROLE) ?: AuthSession.getUserRole(this)

        if (userId.isNullOrBlank()) {
            Toast.makeText(this, "Session expired. Please sign in again.", Toast.LENGTH_LONG).show()
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
            val vehicleNumber = binding.etVehicleNumber.text?.toString().orEmpty()
            viewModel.addVehicle(userId, vehicleNumber)
        }

        binding.etVehicleNumber.setOnFocusChangeListener { _, _ ->
            viewModel.clearErrors()
        }
    }

    private fun observeViewModel() {
        viewModel.state.observe(this) { state ->
            when (state) {
                is AddVehicleState.Loading -> showLoading(true)
                is AddVehicleState.Success -> {
                    showLoading(false)
                    navigateToHome(state.user.name, state.user.role)
                }
                is AddVehicleState.Error -> {
                    showLoading(false)
                    Toast.makeText(this, state.message, Toast.LENGTH_LONG).show()
                }
            }
        }

        viewModel.validationErrors.observe(this) { errors ->
            binding.tilVehicleNumber.error = errors["vehicle"]
            if (errors["user"] != null) {
                Toast.makeText(this, errors["user"], Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun showLoading(show: Boolean) {
        binding.progressBar.visibility = if (show) View.VISIBLE else View.GONE
        binding.btnContinue.isEnabled = !show
        binding.btnContinue.text = if (show) "" else "Continue"
    }

    private fun navigateToHome(name: String?, role: String?) {
        val resolvedName = name?.takeIf { it.isNotBlank() } ?: userName
        val normalizedRole = (role ?: userRole)?.uppercase(Locale.ROOT) ?: "USER"
        when (normalizedRole) {
            "OPERATOR" -> {
                val intent = Intent(this, OperatorDashboardActivity::class.java)
                intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                startActivity(intent)
            }
            else -> {
                val intent = Intent(this, MainContainerActivity::class.java)
                resolvedName?.let { intent.putExtra("USER_NAME", it) }
                intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                startActivity(intent)
            }
        }
        finish()
    }

    private fun disableBackNavigation() {
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                // Vehicle entry is required before proceeding.
            }
        })
    }
}
