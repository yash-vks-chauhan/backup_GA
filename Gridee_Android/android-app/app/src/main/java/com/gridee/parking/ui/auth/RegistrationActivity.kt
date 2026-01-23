package com.gridee.parking.ui.auth

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.gridee.parking.R
import com.gridee.parking.databinding.ActivityRegistrationBinding
import com.gridee.parking.ui.main.MainContainerActivity

class RegistrationActivity : AppCompatActivity() {
    
    private lateinit var binding: ActivityRegistrationBinding
    private val viewModel: RegistrationViewModel by viewModels()
    private val parkingLotData = mutableListOf<String>()
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityRegistrationBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        // Set light status bar with dark icons for white background
        window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
        window.statusBarColor = android.graphics.Color.parseColor("#F5F5F5")
        
        setupUI()
        observeViewModel()
        viewModel.loadParkingLotNames()
    }
    
    private fun setupUI() {
        // Password visibility toggle with custom animation
        com.gridee.parking.ui.utils.PasswordBlurAnimator(this, binding.tilPassword, binding.etPassword)

        // Disable default keyboard/focus for custom dropdown
        binding.etParkingLot.showSoftInputOnFocus = false
        binding.etParkingLot.inputType = 0
        binding.etParkingLot.keyListener = null

        val showDropdownAction = {
            if (parkingLotData.isNotEmpty()) {
                showParkingDropdown(binding.tilParkingLot, parkingLotData)
            } else {
                if (viewModel.parkingLotLoading.value != true) {
                    viewModel.loadParkingLotNames()
                }
            }
        }

        binding.etParkingLot.setOnClickListener { showDropdownAction() }
        binding.etParkingLot.setOnFocusChangeListener { _, hasFocus ->
            if (hasFocus) {
                showDropdownAction()
            }
        }
        binding.tilParkingLot.setEndIconOnClickListener { showDropdownAction() }
        binding.tilParkingLot.setOnClickListener { showDropdownAction() }

        binding.btnRegister.setOnClickListener {
            registerUser()
        }
        
        binding.tvLoginLink.setOnClickListener {
            it.performHapticFeedback(android.view.HapticFeedbackConstants.CONTEXT_CLICK)
            // Navigate to login activity
            startActivity(Intent(this, LoginActivity::class.java))
            finish()
        }
    }
    
    private fun registerUser() {
        val name = binding.etName.text.toString()
        val email = binding.etEmail.text.toString()
        val phone = binding.etPhone.text.toString()
        val password = binding.etPassword.text.toString()
        val parkingLotName = binding.etParkingLot.text?.toString()?.trim() ?: ""
        
        viewModel.registerUser(
            context = this,
            name = name,
            email = email,
            phone = phone,
            password = password,
            parkingLotName = parkingLotName
        )
    }
    
    private fun observeViewModel() {
        viewModel.parkingLotLoading.observe(this) { isLoading ->
            binding.parkingLotProgress.visibility = if (isLoading) View.VISIBLE else View.GONE
            binding.etParkingLot.isEnabled = !isLoading
            binding.tilParkingLot.isEnabled = !isLoading
        }

        viewModel.parkingLotNames.observe(this) { names ->
            parkingLotData.clear()
            parkingLotData.addAll(names)
        }

        viewModel.parkingLotError.observe(this) { error ->
            error?.let {
                Toast.makeText(this, it, Toast.LENGTH_LONG).show()
            }
        }

        viewModel.registrationState.observe(this) { state ->
            when (state) {
                is RegistrationState.Loading -> {
                    showLoading(true)
                }
                is RegistrationState.Success -> {
                    showLoading(false)
                    
                    // Save user data to SharedPreferences
                    val sharedPref = getSharedPreferences("gridee_prefs", MODE_PRIVATE)
                    sharedPref.edit()
                        .putString("user_id", state.user.id)
                        .putString("user_name", state.user.name)
                        .putString("user_email", state.user.email)
                        .putString("user_phone", state.user.phone)
                        .putString("parking_lot_name", state.user.parkingLotName)
                        .putBoolean("is_logged_in", true)
                        .apply()
                    
                    Toast.makeText(this, "Registration successful! Welcome to Gridee!", Toast.LENGTH_LONG).show()
                    
                    // Navigate directly to main container (same as login)
                    val intent = Intent(this, MainContainerActivity::class.java)
                    intent.putExtra("USER_NAME", state.user.name)
                    intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                    startActivity(intent)
                    finish()
                }
                is RegistrationState.VerificationSent -> {
                    showLoading(false)
                    val intent = Intent(this, EmailVerificationActivity::class.java)
                    intent.putExtra(EmailVerificationActivity.EXTRA_EMAIL, state.email)
                    startActivity(intent)
                    finish()
                }
                is RegistrationState.Error -> {
                    showLoading(false)
                    Toast.makeText(this, state.message, Toast.LENGTH_LONG).show()
                }
            }
        }
        
        viewModel.validationErrors.observe(this) { errors ->
            clearErrors()
            errors.forEach { (field, message) ->
                when (field) {
                    "name" -> binding.tilName.error = message
                    "email" -> binding.tilEmail.error = message
                    "phone" -> binding.tilPhone.error = message
                    "password" -> binding.tilPassword.error = message
                    "parkingLot" -> binding.tilParkingLot.error = message
                }
            }
        }
    }
    
    private fun showLoading(show: Boolean) {
        binding.progressBar.visibility = if (show) View.VISIBLE else View.GONE
        binding.btnRegister.isEnabled = !show
        binding.btnRegister.text = if (show) "" else "Register"
    }
    
    private fun clearErrors() {
        binding.tilName.error = null
        binding.tilEmail.error = null
        binding.tilPhone.error = null
        binding.tilPassword.error = null
        binding.tilParkingLot.error = null
    }

    private fun showParkingDropdown(anchor: View, items: List<String>) {
        val popupView = layoutInflater.inflate(R.layout.window_parking_dropdown, null)
        
        val width = anchor.width
        val popupWindow = android.widget.PopupWindow(
            popupView, 
            width, 
            android.view.ViewGroup.LayoutParams.WRAP_CONTENT, 
            true
        )
        
        popupWindow.elevation = 16f
        popupWindow.isOutsideTouchable = true
        popupWindow.setBackgroundDrawable(ContextCompat.getDrawable(this, android.R.color.transparent))
        
        // Toggle arrow icon
        binding.tilParkingLot.setEndIconDrawable(R.drawable.ic_arrow_up_filled_rounded)
        popupWindow.setOnDismissListener { 
            binding.tilParkingLot.setEndIconDrawable(R.drawable.ic_arrow_down_filled_rounded)
        }
        
        val recyclerView = popupView.findViewById<androidx.recyclerview.widget.RecyclerView>(R.id.recyclerView)
        recyclerView.layoutManager = androidx.recyclerview.widget.LinearLayoutManager(this)
        
        recyclerView.adapter = object : androidx.recyclerview.widget.RecyclerView.Adapter<ParkingViewHolder>() {
            override fun onCreateViewHolder(parent: android.view.ViewGroup, viewType: Int): ParkingViewHolder {
                val view = layoutInflater.inflate(R.layout.item_parking_option, parent, false)
                return ParkingViewHolder(view)
            }

            override fun onBindViewHolder(holder: ParkingViewHolder, position: Int) {
                val item = items[position]
                holder.tvName.text = item
                holder.itemView.setOnClickListener {
                    binding.etParkingLot.setText(item)
                    binding.tilParkingLot.error = null
                    popupWindow.dismiss()
                }
            }

            override fun getItemCount() = items.size
        }
        
        val controller = android.view.animation.AnimationUtils.loadLayoutAnimation(this, R.anim.layout_animation_waterfall)
        recyclerView.layoutAnimation = controller
        recyclerView.scheduleLayoutAnimation()
        
        // Show with a slight offset
        popupWindow.showAsDropDown(anchor, 0, 8)
    }
    
    class ParkingViewHolder(view: View) : androidx.recyclerview.widget.RecyclerView.ViewHolder(view) {
        val tvName: android.widget.TextView = view.findViewById(R.id.tvParkingName)
    }

}
