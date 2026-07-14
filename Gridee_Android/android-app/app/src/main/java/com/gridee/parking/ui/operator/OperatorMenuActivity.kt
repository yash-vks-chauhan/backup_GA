package com.gridee.parking.ui.operator

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.gridee.parking.R
import com.gridee.parking.ui.auth.LoginActivity
import com.gridee.parking.ui.bottomsheet.LogoutConfirmationBottomSheet
import com.gridee.parking.utils.AppLocaleManager
import com.gridee.parking.utils.AuthSession
import com.gridee.parking.utils.NotificationHelper

class OperatorMenuActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        AppLocaleManager.applySavedLocale(this)
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_operator_menu)
        
        // --- UI SETUP ---
        setupUserProfile()
        setupClickListeners()
        setupBackNavigation()
    }

    private fun setupBackNavigation() {
        onBackPressedDispatcher.addCallback(this, object : androidx.activity.OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                finish()
                overridePendingTransition(R.anim.fade_in, R.anim.slide_out_left)
            }
        })
    }

    private fun setupUserProfile() {
        // Set Profile Name
        val sharedPref = getSharedPreferences("gridee_prefs", MODE_PRIVATE)
        val operatorName = sharedPref.getString("user_name", "Operator")
        findViewById<TextView>(R.id.tv_menu_name)?.text = operatorName

        // Set Profile Initials
        val initials = if (operatorName.isNullOrBlank()) "O" else operatorName.first().toString().uppercase()
        findViewById<TextView>(R.id.tv_menu_initials)?.text = initials

        // Set Current Language
        val langCode = sharedPref.getString("app_language", "en") ?: "en"
        val langName = when (langCode) {
            "hi" -> getString(R.string.language_hindi)
            "ta" -> getString(R.string.language_tamil)
            else -> getString(R.string.language_english)
        }
        findViewById<TextView>(R.id.tv_current_lang)?.text = langName
    }

    private fun setupClickListeners() {
        // Close Button
        findViewById<View>(R.id.btn_close_menu).setOnClickListener {
            finish()
            overridePendingTransition(R.anim.fade_in, R.anim.slide_out_left) 
        }

        // History
        findViewById<View>(R.id.btn_menu_history).setOnClickListener {
            showNotification(getString(R.string.op_history_soon), NotificationType.INFO)
        }

        // Settings
        findViewById<View>(R.id.btn_menu_settings).setOnClickListener {
            showNotification(getString(R.string.op_settings_soon), NotificationType.INFO)
        }

        // Language
        findViewById<View>(R.id.btn_menu_language).setOnClickListener {
            showLanguageSelectionDialog()
        }

        // Help
        findViewById<View>(R.id.btn_menu_help).setOnClickListener {
            showNotification(getString(R.string.op_help_soon), NotificationType.INFO)
        }

        // Logout
        findViewById<View>(R.id.btn_menu_logout).setOnClickListener {
            showLogoutConfirmation()
        }
    }

    private fun showLanguageSelectionDialog() {
        val dialog = com.google.android.material.bottomsheet.BottomSheetDialog(this, R.style.BottomSheetDialogTheme)
        
        dialog.setOnShowListener {
            val bottomSheet = dialog.findViewById<View>(com.google.android.material.R.id.design_bottom_sheet) as? FrameLayout
            bottomSheet?.setBackgroundResource(R.drawable.bg_bottom_sheet_universal)
        }
        
        val view = layoutInflater.inflate(R.layout.bottom_sheet_language_selector, null)
        dialog.setContentView(view)

        view.findViewById<View>(R.id.btn_close)?.setOnClickListener {
            dialog.dismiss()
        }
        
        view.findViewById<View>(R.id.btn_english)?.setOnClickListener {
            setAppLocale("en")
            dialog.dismiss()
        }
        view.findViewById<View>(R.id.btn_hindi)?.setOnClickListener {
            setAppLocale("hi")
            dialog.dismiss()
        }
        view.findViewById<View>(R.id.btn_tamil)?.setOnClickListener {
            setAppLocale("ta")
            dialog.dismiss()
        }
        
        dialog.show()
    }

    private fun setAppLocale(languageCode: String) {
        AppLocaleManager.setLocale(this, languageCode)

        // Restart App (Go to Dashboard)
        val intent = Intent(this, OperatorDashboardActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        finish()
    }

    private fun showLogoutConfirmation() {
        LogoutConfirmationBottomSheet.newInstance()
            .setOnLogoutConfirmed { logout() }
            .show(supportFragmentManager, LogoutConfirmationBottomSheet.TAG)
    }

    private fun logout() {
        AuthSession.clearSession(this)

        // Navigate to login
        val intent = Intent(this, LoginActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        finish()
    }

    // --- NOTIFICATION HELPERS ---
    enum class NotificationType {
        SUCCESS, ERROR, INFO
    }

    private fun showNotification(message: String, type: NotificationType) {
        when (type) {
            NotificationType.SUCCESS -> {
                NotificationHelper.showSuccess(
                    parent = findViewById(android.R.id.content),
                    message = message,
                    duration = 3000L
                )
            }
            NotificationType.ERROR -> {
                NotificationHelper.showError(
                    parent = findViewById(android.R.id.content),
                    message = message,
                    duration = 3000L
                )
            }
            NotificationType.INFO -> {
                NotificationHelper.showInfo(
                    parent = findViewById(android.R.id.content),
                    message = message,
                    duration = 3000L
                )
            }
        }
    }
}
