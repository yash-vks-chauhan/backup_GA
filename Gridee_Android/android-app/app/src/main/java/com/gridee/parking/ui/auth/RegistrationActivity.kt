package com.gridee.parking.ui.auth

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.PopupWindow
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.gridee.parking.R
import com.gridee.parking.config.RemoteConfigManager
import com.gridee.parking.databinding.ActivityRegistrationBinding
import com.gridee.parking.ui.main.MainContainerActivity
import com.gridee.parking.utils.NotificationHelper

class RegistrationActivity : AppCompatActivity() {
    
    private lateinit var binding: ActivityRegistrationBinding
    private val viewModel: RegistrationViewModel by viewModels()
    private val parkingLotData = mutableListOf<String>()
    private var activePopupWindow: PopupWindow? = null
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityRegistrationBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        // Theme-aware status bar
        window.statusBarColor = ContextCompat.getColor(this, R.color.background_primary)
        androidx.core.view.WindowInsetsControllerCompat(window, window.decorView).isAppearanceLightStatusBars =
            !com.gridee.parking.utils.ThemeManager.isDarkMode(this)
        
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

        binding.etParkingLot.setOnClickListener { toggleParkingDropdown() }
        binding.tilParkingLot.setEndIconOnClickListener { toggleParkingDropdown() }



        binding.btnRegister.setOnClickListener {
            if (!RemoteConfigManager.isEmailSignInEnabled()) {
                NotificationHelper.showWarning(binding.rootContainer, message = "Email registration is temporarily unavailable.")
                return@setOnClickListener
            }
            registerUser()
        }
        
        binding.tvLoginLink.setOnClickListener {
            it.performHapticFeedback(android.view.HapticFeedbackConstants.CONTEXT_CLICK)
            // Navigate to login activity
            startActivity(Intent(this, LoginActivity::class.java))
            finish()
        }

        applyFeatureSwitches()
    }

    private fun applyFeatureSwitches() {
        RemoteConfigManager.loadCached(this)
        val emailEnabled = RemoteConfigManager.isEmailSignInEnabled()
        binding.btnRegister.isEnabled = emailEnabled
        binding.btnRegister.alpha = if (emailEnabled) 1f else 0.45f
        if (!emailEnabled) {
            NotificationHelper.showWarning(
                binding.rootContainer,
                message = "Email registration is temporarily unavailable."
            )
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
                NotificationHelper.showWarning(binding.rootContainer, message = it)
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
                    
                    NotificationHelper.showSuccess(binding.rootContainer, message = "Registration successful! Welcome to Gridee!")
                    
                    // Navigate directly to main container (same as login)
                    val intent = Intent(this, MainContainerActivity::class.java)
                    intent.putExtra("USER_NAME", state.user.name)
                    intent.putExtra(MainContainerActivity.EXTRA_SHOW_SIGNUP_GIFT, true)
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
                    if (state.isRetryable) {
                        NotificationHelper.showWarning(
                            binding.rootContainer,
                            title = state.title,
                            message = state.message,
                            onClick = { registerUser() },
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

    private fun toggleParkingDropdown() {
        if (activePopupWindow?.isShowing == true) {
            activePopupWindow?.dismiss()
        } else {
            if (parkingLotData.isNotEmpty()) {
                showParkingDropdown(binding.tilParkingLot, parkingLotData)
            } else if (viewModel.parkingLotLoading.value != true) {
                viewModel.loadParkingLotNames()
            }
        }
    }

    private fun showParkingDropdown(anchor: View, items: List<String>) {
        val popupView = layoutInflater.inflate(R.layout.window_parking_dropdown, null)
        
        // Calculate padding for shadow breathing room (24dp from XML)
        val density = resources.displayMetrics.density
        val paddingPx = (24 * density).toInt() // Must match XML padding
        
        // Popup needs to be wider than anchor to accommodate the transparent padding
        val width = anchor.width + (paddingPx * 2)
        
        val popupWindow = PopupWindow(
            popupView, 
            width, 
            ViewGroup.LayoutParams.WRAP_CONTENT, 
            true
        )
        
        // Remove window shadow, rely on CardView shadow inside
        popupWindow.elevation = 0f 
        activePopupWindow = popupWindow
        popupWindow.isOutsideTouchable = true
        popupWindow.setBackgroundDrawable(ContextCompat.getDrawable(this, android.R.color.transparent))

        
        // Mechanical "Click" Rotation (Precision Snap)
        val endIcon = binding.tilParkingLot.findViewById<View>(com.google.android.material.R.id.text_input_end_icon)
        endIcon?.animate()
            ?.rotation(180f)
            ?.setDuration(300)
            ?.setInterpolator(android.view.animation.OvershootInterpolator(1.2f)) 
            ?.start()
        
        // Tactile Feedback
        anchor.performHapticFeedback(android.view.HapticFeedbackConstants.CONTEXT_CLICK)

        popupWindow.setOnDismissListener { 
            activePopupWindow = null
            endIcon?.animate()?.rotation(0f)?.setDuration(250)?.setInterpolator(androidx.interpolator.view.animation.FastOutSlowInInterpolator())?.start()
        }

        
        val recyclerView = popupView.findViewById<androidx.recyclerview.widget.RecyclerView>(R.id.recyclerView)
        recyclerView.layoutManager = androidx.recyclerview.widget.LinearLayoutManager(this)
        
        recyclerView.adapter = object : androidx.recyclerview.widget.RecyclerView.Adapter<ParkingViewHolder>() {
            override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ParkingViewHolder {
                val view = layoutInflater.inflate(R.layout.item_parking_option, parent, false)
                return ParkingViewHolder(view)
            }

            override fun onBindViewHolder(holder: ParkingViewHolder, position: Int) {
                val item = items[position]
                holder.tvName.text = item
                holder.itemView.setOnClickListener {
                    binding.etParkingLot.setText(item)
                    binding.tilParkingLot.error = null
                    
                    // Fluid Press (Subtle)
                    holder.itemView.animate()
                        .scaleX(0.98f).scaleY(0.98f)
                        .setDuration(80)
                        .start()

                    closeDropdown(popupWindow, popupView, endIcon)
                }
            }

            override fun getItemCount() = items.size
        }
        
        // ... (Existing setup code remains here) ...
        
        // Initial State for "Liquid Glass" telescope
        popupView.alpha = 0f
        popupView.scaleY = 0.9f 
        popupView.translationY = -24f 
        
        popupView.pivotX = width / 2f
        popupView.pivotY = paddingPx.toFloat() 

        val yOffset = -paddingPx + (8 * density).toInt() 
        
        popupWindow.showAsDropDown(anchor, -paddingPx, yOffset)

        // Dim Background
        try {
            val container = popupWindow.contentView.rootView
            val wm = getSystemService(android.content.Context.WINDOW_SERVICE) as android.view.WindowManager
            val p = container.layoutParams as android.view.WindowManager.LayoutParams
            p.flags = p.flags or android.view.WindowManager.LayoutParams.FLAG_DIM_BEHIND
            p.dimAmount = 0.15f 
            wm.updateViewLayout(container, p)
        } catch (e: Exception) {}

        // 1. Alpha
        popupView.animate()
            .alpha(1f)
            .setDuration(100)
            .setInterpolator(androidx.interpolator.view.animation.LinearOutSlowInInterpolator())
            .start()

        // 2. Spring Scale
        val springY = androidx.dynamicanimation.animation.SpringAnimation(popupView, androidx.dynamicanimation.animation.DynamicAnimation.SCALE_Y, 1f)
        springY.spring.dampingRatio = 0.82f 
        springY.spring.stiffness = 450f 
        springY.start()
        
        // 3. Spring Translation
        val springTransY = androidx.dynamicanimation.animation.SpringAnimation(popupView, androidx.dynamicanimation.animation.DynamicAnimation.TRANSLATION_Y, 0f)
        springTransY.spring.dampingRatio = 0.85f 
        springTransY.spring.stiffness = 500f
        springTransY.start()
    }

    private fun closeDropdown(window: PopupWindow, view: View, arrow: View?) {
        // Prevent double-close
        window.setOnDismissListener(null)

        // 1. Retract Arrow (Snappy sync)
        arrow?.animate()
            ?.rotation(0f)
            ?.setDuration(200)
            ?.setInterpolator(androidx.interpolator.view.animation.FastOutSlowInInterpolator())
            ?.start()

        // 2. Fade Out Dim (Snappy sync)
        val container = window.contentView.rootView
        val wm = getSystemService(android.content.Context.WINDOW_SERVICE) as android.view.WindowManager
        val p = container.layoutParams as android.view.WindowManager.LayoutParams
        
        val dimAnimator = android.animation.ValueAnimator.ofFloat(0.15f, 0f)
        dimAnimator.duration = 200
        dimAnimator.addUpdateListener {
            try {
                p.dimAmount = it.animatedValue as Float
                wm.updateViewLayout(container, p)
            } catch (e: Exception) {}
        }
        dimAnimator.start()

        // 3. Physics Retraction ("Apple Friction" - High Damping, Snappy Stiffness)
        
        // Alpha: Accelerate (Fade out faster at the end, staying visible during initial shrink)
        view.animate()
            .alpha(0f)
            .setDuration(200)
            .setInterpolator(android.view.animation.AccelerateInterpolator())
            .start()

        // Scale: Shrink slightly to 0.95f (Apple standard)
        val springScale = androidx.dynamicanimation.animation.SpringAnimation(view, androidx.dynamicanimation.animation.DynamicAnimation.SCALE_Y, 0.95f)
        springScale.spring.dampingRatio = 1f // Critical Damping (No bounce, pure friction)
        springScale.spring.stiffness = 400f // Stiffer (Snappier response)
        springScale.start()

        // Translation: Slide back up to -24f (Origin)
        val springTrans = androidx.dynamicanimation.animation.SpringAnimation(view, androidx.dynamicanimation.animation.DynamicAnimation.TRANSLATION_Y, -24f)
        springTrans.spring.dampingRatio = 1f 
        springTrans.spring.stiffness = 400f 
        
        // Dismiss when physics settle
        springTrans.addEndListener { _, _, _, _ -> 
             window.dismiss()
        }
        
        // Safety Fallback (Reduced to match new speed)
        view.postDelayed({
            if (window.isShowing) window.dismiss()
        }, 250)
        
        springTrans.start()
    }
    
    class ParkingViewHolder(view: View) : androidx.recyclerview.widget.RecyclerView.ViewHolder(view) {
        val tvName: android.widget.TextView = view.findViewById(R.id.tvParkingName)
    }

}
