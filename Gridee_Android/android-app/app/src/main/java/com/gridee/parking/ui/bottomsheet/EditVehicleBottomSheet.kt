package com.gridee.parking.ui.bottomsheet

import android.app.Dialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.gridee.parking.R
import com.gridee.parking.databinding.BottomSheetEditVehicleBinding

class EditVehicleBottomSheet(
    private val vehicleNumber: String,
    private val onSave: (String) -> Unit
) : BottomSheetDialogFragment() {

    private var _binding: BottomSheetEditVehicleBinding? = null
    private val binding get() = _binding!!

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setStyle(STYLE_NORMAL, R.style.BottomSheetDialogTheme)
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val dialog = super.onCreateDialog(savedInstanceState)
        dialog.setOnShowListener { dialogInterface ->
            val bottomSheetDialog = dialogInterface as BottomSheetDialog
            
            // Fix for Edge-to-Edge: Ensure the container extends behind nav bar
            val bottomSheet = bottomSheetDialog.findViewById<View>(com.google.android.material.R.id.design_bottom_sheet)
            bottomSheet?.let { sheet ->
                // Keep the sheet surface opaque so nav-area never appears transparent
                sheet.setBackgroundResource(R.drawable.bg_bottom_sheet_universal)
                
                // Force the sheet to extend to the edge
                sheet.fitsSystemWindows = false
                
                // Remove any margins that might lift the sheet up
                val params = sheet.layoutParams as? ViewGroup.MarginLayoutParams
                params?.setMargins(0, 0, 0, 0)
                sheet.layoutParams = params
                
                // Apply spring animation if needed, or rely on default behavior
                // Keeping original behavior implies we might want to call applySpringAnimation(sheet) here if distinct
                // For consistency with AddVehicle, we usually rely on standard behavior + internal view animation
           }
            
            // Ensure behavior ignores gesture insets
            bottomSheetDialog.behavior.isGestureInsetBottomIgnored = true
            
            // Force fully expanded state
            bottomSheetDialog.behavior.state = BottomSheetBehavior.STATE_EXPANDED
            bottomSheetDialog.behavior.skipCollapsed = true
            
            bottomSheetDialog.window?.let { window ->
                androidx.core.view.WindowCompat.setDecorFitsSystemWindows(window, false)
                window.navigationBarColor = android.graphics.Color.TRANSPARENT
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
        _binding = BottomSheetEditVehicleBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        setupUI()
        setupListeners()
        
        // Ambient Shadow (Android 9+)
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
            binding.root.outlineAmbientShadowColor = android.graphics.Color.parseColor("#40000000")
            binding.root.outlineSpotShadowColor = android.graphics.Color.parseColor("#40000000")
        }
    }

    private fun setupUI() {
        // Pre-fill the current vehicle number
        binding.etVehicleNumber.setText(vehicleNumber)
        binding.etVehicleNumber.setSelection(vehicleNumber.length)
        
        // Don't auto-focus or show keyboard
        // Let the user tap on the input field to bring up the keyboard
        // This allows the modal to slide up smoothly first
    }

    private fun setupListeners() {
        binding.btnSave.setOnClickListener {
            val newVehicleNumber = com.gridee.parking.utils.VehicleNumberValidator.normalize(
                binding.etVehicleNumber.text.toString()
            )
            val currentVehicleNumber = com.gridee.parking.utils.VehicleNumberValidator.normalize(vehicleNumber)

            if (newVehicleNumber == currentVehicleNumber) {
                dismiss()
                return@setOnClickListener
            }

            val error = com.gridee.parking.utils.VehicleNumberValidator.getError(newVehicleNumber)
            if (error != null) {
                binding.tilVehicleNumber.error = error
                return@setOnClickListener
            }

            onSave(newVehicleNumber)
            dismiss()
        }
        
        binding.btnClose.setOnClickListener {
            dismiss()
        }
        
        // Clear error when user types
        binding.etVehicleNumber.setOnFocusChangeListener { _, hasFocus ->
            if (hasFocus) {
                binding.tilVehicleNumber.error = null
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        const val TAG = "EditVehicleBottomSheet"
    }
}
