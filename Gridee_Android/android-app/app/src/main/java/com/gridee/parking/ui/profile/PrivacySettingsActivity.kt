package com.gridee.parking.ui.profile

import android.os.Bundle
import com.gridee.parking.databinding.ActivityPrivacySettingsBinding
import com.gridee.parking.ui.base.BaseActivity
import com.gridee.parking.utils.AuthSession

class PrivacySettingsActivity : BaseActivity<ActivityPrivacySettingsBinding>() {

    override fun getViewBinding(): ActivityPrivacySettingsBinding {
        return ActivityPrivacySettingsBinding.inflate(layoutInflater)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setupClickListeners()
        loadSettings()
    }

    private fun setupClickListeners() {
        binding.btnBack.setOnClickListener {
            finish()
        }

        binding.switchDataCollection.setOnCheckedChangeListener { _, isChecked ->
            saveDataCollectionSetting(isChecked)
            showToast(if (isChecked) "Data collection enabled" else "Data collection disabled")
        }

        binding.switchLocationTracking.setOnCheckedChangeListener { _, isChecked ->
            saveLocationTrackingSetting(isChecked)
            showToast(if (isChecked) "Location tracking enabled" else "Location tracking disabled")
        }

        binding.switchAnalytics.setOnCheckedChangeListener { _, isChecked ->
            saveAnalyticsSetting(isChecked)
            showToast(if (isChecked) "Analytics enabled" else "Analytics disabled")
        }

        binding.switchMarketingEmails.setOnCheckedChangeListener { _, isChecked ->
            saveMarketingEmailsSetting(isChecked)
            showToast(if (isChecked) "Marketing emails enabled" else "Marketing emails disabled")
        }

        binding.btnDeleteAccount.setOnClickListener {
            showDeleteAccountConfirmation()
        }

        binding.btnExportData.setOnClickListener {
            showToast("Data export request submitted. You will receive an email shortly.")
        }
    }

    private fun loadSettings() {
        val sharedPref = getSharedPreferences("gridee_privacy_prefs", MODE_PRIVATE)
        
        binding.switchDataCollection.isChecked = sharedPref.getBoolean("data_collection", true)
        binding.switchLocationTracking.isChecked = sharedPref.getBoolean("location_tracking", true)
        binding.switchAnalytics.isChecked = sharedPref.getBoolean("analytics", true)
        binding.switchMarketingEmails.isChecked = sharedPref.getBoolean("marketing_emails", false)
    }

    private fun saveDataCollectionSetting(enabled: Boolean) {
        val sharedPref = getSharedPreferences("gridee_privacy_prefs", MODE_PRIVATE)
        sharedPref.edit().putBoolean("data_collection", enabled).apply()
    }

    private fun saveLocationTrackingSetting(enabled: Boolean) {
        val sharedPref = getSharedPreferences("gridee_privacy_prefs", MODE_PRIVATE)
        sharedPref.edit().putBoolean("location_tracking", enabled).apply()
    }

    private fun saveAnalyticsSetting(enabled: Boolean) {
        val sharedPref = getSharedPreferences("gridee_privacy_prefs", MODE_PRIVATE)
        sharedPref.edit().putBoolean("analytics", enabled).apply()
    }

    private fun saveMarketingEmailsSetting(enabled: Boolean) {
        val sharedPref = getSharedPreferences("gridee_privacy_prefs", MODE_PRIVATE)
        sharedPref.edit().putBoolean("marketing_emails", enabled).apply()
    }

    private fun showDeleteAccountConfirmation() {
        val builder = androidx.appcompat.app.AlertDialog.Builder(this)
        builder.setTitle("Delete Account")
        builder.setMessage("Are you sure you want to delete your account? This action cannot be undone. All your data including bookings, vehicles, and preferences will be permanently deleted.")
        
        builder.setPositiveButton("Delete Account") { _, _ ->
            showFinalDeleteConfirmation()
        }
        
        builder.setNegativeButton("Cancel", null)
        builder.show()
    }

    private fun showFinalDeleteConfirmation() {
        val builder = androidx.appcompat.app.AlertDialog.Builder(this)
        builder.setTitle("Final Confirmation")
        builder.setMessage("This is your final warning. Deleting your account will:\n\n• Remove all your personal data\n• Cancel active bookings\n• Delete payment methods\n• Remove vehicle information\n\nType 'DELETE' to confirm:")
        
        val input = android.widget.EditText(this)
        input.hint = "Type DELETE to confirm"
        builder.setView(input)
        
        builder.setPositiveButton("Delete Forever") { _, _ ->
            val confirmation = input.text.toString().trim()
            if (confirmation == "DELETE") {
                deleteAccount()
            } else {
                showToast("Confirmation text doesn't match. Account deletion cancelled.")
            }
        }
        
        builder.setNegativeButton("Cancel", null)
        builder.show()
    }

    private fun deleteAccount() {
        // TODO: Implement actual account deletion API call
        showToast("Account deletion request submitted. You will receive a confirmation email.")
        
        // For now, just clear local data
        AuthSession.clearSession(this)
        val grideePrefs = getSharedPreferences("gridee_prefs", MODE_PRIVATE)
        val privacyPrefs = getSharedPreferences("gridee_privacy_prefs", MODE_PRIVATE)
        
        grideePrefs.edit().clear().apply()
        privacyPrefs.edit().clear().apply()
        
        // Navigate to login screen
        val intent = android.content.Intent(this, com.gridee.parking.ui.auth.LoginActivity::class.java)
        intent.flags = android.content.Intent.FLAG_ACTIVITY_NEW_TASK or android.content.Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        finish()
    }

    private fun showToast(message: String) {
        android.widget.Toast.makeText(this, message, android.widget.Toast.LENGTH_SHORT).show()
    }
}
