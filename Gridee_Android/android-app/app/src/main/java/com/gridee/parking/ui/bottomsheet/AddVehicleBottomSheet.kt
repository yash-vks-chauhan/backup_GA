package com.gridee.parking.ui.bottomsheet

import android.os.Bundle
import android.util.TypedValue
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.constraintlayout.widget.ConstraintSet
import androidx.core.content.ContextCompat
import androidx.dynamicanimation.animation.DynamicAnimation
import androidx.dynamicanimation.animation.SpringAnimation
import androidx.dynamicanimation.animation.SpringForce
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.card.MaterialCardView
import com.gridee.parking.R
import com.gridee.parking.databinding.BottomSheetAddVehicleBinding

class AddVehicleBottomSheet(
    private val onVehicleAdded: (String) -> Unit
) : BottomSheetDialogFragment() {

    private var _binding: BottomSheetAddVehicleBinding? = null
    private val binding get() = _binding!!


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setStyle(STYLE_NORMAL, com.gridee.parking.R.style.BottomSheetDialogTheme)
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): android.app.Dialog {
        val dialog = super.onCreateDialog(savedInstanceState)
        dialog.setOnShowListener { dialogInterface ->
            val bottomSheetDialog = dialogInterface as com.google.android.material.bottomsheet.BottomSheetDialog
            
            // Fix for Edge-to-Edge: Ensure the container extends behind nav bar
            val bottomSheet = bottomSheetDialog.findViewById<View>(com.google.android.material.R.id.design_bottom_sheet)
            bottomSheet?.let { sheet ->
                // Remove default background to use ours
                sheet.background = null 
                
                // Force the sheet to extend to the edge
                sheet.fitsSystemWindows = false
                
                // Remove any margins that might lift the sheet up
                val params = sheet.layoutParams as? ViewGroup.MarginLayoutParams
                params?.setMargins(0, 0, 0, 0)
                sheet.layoutParams = params
                
                // Prevent system from padding the sheet automatically
                androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener(sheet) { view, insets ->
                    view.setPadding(0, 0, 0, 0)
                    insets
                }
            }
            
            // Ensure behavior ignores gesture insets
            bottomSheetDialog.behavior.isGestureInsetBottomIgnored = true
            
            // Force fully expanded state
            bottomSheetDialog.behavior.state = com.google.android.material.bottomsheet.BottomSheetBehavior.STATE_EXPANDED
            bottomSheetDialog.behavior.skipCollapsed = true
            
            bottomSheetDialog.window?.let { window ->
                androidx.core.view.WindowCompat.setDecorFitsSystemWindows(window, false)
                val navBarColor = androidx.core.content.ContextCompat.getColor(requireContext(), com.gridee.parking.R.color.white)
                window.navigationBarColor = navBarColor
                window.isNavigationBarContrastEnforced = false
                
                // Ensure light nav bar (dark icons) since background is white
                val wic = androidx.core.view.WindowCompat.getInsetsController(window, window.decorView)
                wic.isAppearanceLightNavigationBars = true
                
                // Remove the black divider line above the nav bar (Android 9+)
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
                    window.navigationBarDividerColor = android.graphics.Color.TRANSPARENT
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                        window.isNavigationBarContrastEnforced = false
                    }
                }

                // Glassmorphism: Blur the screen behind the sheet (Android 12+)
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                    window.attributes.blurBehindRadius = 50 // 50px blur for frosted glass effect
                    window.attributes = window.attributes // Apply changes
                }
            }
        }
        return dialog
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = BottomSheetAddVehicleBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        setupUI()
        setupClickListeners()
        animateEntry()
        
        // Ambient Shadow (Android 9+)
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
            binding.root.outlineAmbientShadowColor = android.graphics.Color.parseColor("#40000000")
            binding.root.outlineSpotShadowColor = android.graphics.Color.parseColor("#40000000")
        }
    }

    private fun setupUI() {
        // Don't auto-focus or show keyboard
        // Let the user tap on the input field to bring up the keyboard
        // This allows the modal to slide up smoothly first
    }

    private fun setupClickListeners() {
        binding.btnAddVehicle.setOnClickListener {
            addVehicle()
        }

        binding.btnClose.setOnClickListener {
            dismiss()
        }
    }

    private fun addVehicle() {
        val vehicleNumber = binding.etVehicleNumber.text.toString().trim().uppercase()
        
        when {
            vehicleNumber.isEmpty() -> {
                binding.tilVehicleNumber.error = "Please enter a vehicle number"
                return
            }
            vehicleNumber.length < 6 -> {
                binding.tilVehicleNumber.error = "Vehicle number is too short"
                return
            }
            else -> {
                binding.tilVehicleNumber.error = null
            }
        }

        // Animate button press
        animateButtonPress {
            // Hide keyboard
            val imm = requireContext().getSystemService(android.content.Context.INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager
            imm.hideSoftInputFromWindow(binding.etVehicleNumber.windowToken, 0)
            
            // Callback with the new vehicle number
            onVehicleAdded(vehicleNumber)
            
            // Dismiss the bottom sheet
            dismiss()
        }
    }

    private fun animateEntry() {
        // Scale animation for smooth entry
        binding.root.scaleY = 0.95f
        binding.root.alpha = 0f
        
        val scaleY = SpringAnimation(binding.root, DynamicAnimation.SCALE_Y, 1f).apply {
            spring.stiffness = SpringForce.STIFFNESS_MEDIUM
            spring.dampingRatio = SpringForce.DAMPING_RATIO_MEDIUM_BOUNCY
            start()
        }
        
        val alpha = SpringAnimation(binding.root, DynamicAnimation.ALPHA, 1f).apply {
            spring.stiffness = SpringForce.STIFFNESS_MEDIUM
            spring.dampingRatio = SpringForce.DAMPING_RATIO_LOW_BOUNCY
            start()
        }
    }

    private fun animateButtonPress(onComplete: () -> Unit) {
        val button = binding.btnAddVehicle
        
        // Scale down animation
        button.animate()
            .scaleX(0.95f)
            .scaleY(0.95f)
            .setDuration(100)
            .withEndAction {
                // Scale back up
                button.animate()
                    .scaleX(1f)
                    .scaleY(1f)
                    .setDuration(100)
                    .withEndAction {
                        onComplete()
                    }
                    .start()
            }
            .start()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        const val TAG = "AddVehicleBottomSheet"
    }
}
