package com.gridee.parking.ui.bottomsheet

import android.animation.ValueAnimator
import android.app.Dialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.DecelerateInterpolator
import android.widget.FrameLayout
import androidx.dynamicanimation.animation.DynamicAnimation
import androidx.dynamicanimation.animation.SpringAnimation
import androidx.dynamicanimation.animation.SpringForce
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.gridee.parking.R
import com.gridee.parking.databinding.BottomSheetVehicleOptionsBinding

class VehicleOptionsBottomSheet(
    private val vehicleNumber: String,
    private val isDefault: Boolean,
    private val onEdit: (String) -> Unit,
    private val onMakeDefault: (String) -> Unit,
    private val onDelete: (String) -> Unit
) : BottomSheetDialogFragment() {

    private var _binding: BottomSheetVehicleOptionsBinding? = null
    private val binding get() = _binding!!

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setStyle(STYLE_NORMAL, R.style.BottomSheetDialogTheme)
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val dialog = super.onCreateDialog(savedInstanceState) as BottomSheetDialog
        
        dialog.setOnShowListener { dialogInterface ->
            val bottomSheet = (dialogInterface as BottomSheetDialog)
                .findViewById<FrameLayout>(com.google.android.material.R.id.design_bottom_sheet)
            
            bottomSheet?.let { sheet ->
                val behavior = BottomSheetBehavior.from(sheet)
                behavior.state = BottomSheetBehavior.STATE_EXPANDED
                behavior.skipCollapsed = true
                behavior.isDraggable = true
                behavior.isHideable = true
                
                // Apply spring animation to the bottom sheet
                applySpringAnimation(sheet)
            }
        }
        
        return dialog
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = BottomSheetVehicleOptionsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        setupUI()
        setupListeners()
        applyBlurAndOpacityAnimation()
    }

    private fun setupUI() {
        binding.tvSubtitle.text = vehicleNumber

        if (isDefault) {
            // Visual indication that this is already the default vehicle
            binding.ivDefaultCheck.visibility = View.VISIBLE
            binding.tvDefaultText.text = "Default Vehicle"
            
            // Subtle change to indicate inactive state, but keep it high contrast/readable
            binding.btnMakeDefault.alpha = 0.6f
            binding.btnMakeDefault.isEnabled = false
        }
    }

    private fun setupListeners() {
        binding.btnClose.setOnClickListener {
            dismissWithAnimation()
        }

        binding.btnEdit.setOnClickListener {
            // Dismiss then perform action
            dismissWithAnimation {
                onEdit(vehicleNumber)
            }
        }

        binding.btnMakeDefault.setOnClickListener {
            dismissWithAnimation {
                onMakeDefault(vehicleNumber)
            }
        }

        binding.btnDelete.setOnClickListener {
            dismissWithAnimation {
                onDelete(vehicleNumber)
            }
        }
    }

    private fun applySpringAnimation(view: View) {
        val springAnim = SpringAnimation(view, DynamicAnimation.TRANSLATION_Y, 0f).apply {
            spring.stiffness = SpringForce.STIFFNESS_MEDIUM
            spring.dampingRatio = SpringForce.DAMPING_RATIO_LOW_BOUNCY
            
            // Start from below the screen
            view.translationY = view.height.toFloat()
            start()
        }
    }

    private fun applyBlurAndOpacityAnimation() {
        // Get the dim background view
        dialog?.window?.let { window ->
            val decorView = window.decorView
            val rootView = decorView.findViewById<ViewGroup>(android.R.id.content)
            
            // Animate opacity
            ValueAnimator.ofFloat(0f, 1f).apply {
                duration = 300
                interpolator = DecelerateInterpolator()
                addUpdateListener { animator ->
                    val alpha = animator.animatedValue as Float
                    window.setDimAmount(0.6f * alpha)
                }
                start()
            }
        }
        
        // Animate the bottom sheet content
        binding.root.alpha = 0f
        binding.root.animate()
            .alpha(1f)
            .setDuration(300)
            .setInterpolator(DecelerateInterpolator())
            .start()
    }

    private fun dismissWithAnimation(onDismissComplete: (() -> Unit)? = null) {
        // Animate the bottom sheet sliding down
        val springAnim = SpringAnimation(binding.root, DynamicAnimation.TRANSLATION_Y, binding.root.height.toFloat()).apply {
            spring.stiffness = SpringForce.STIFFNESS_MEDIUM
            spring.dampingRatio = SpringForce.DAMPING_RATIO_MEDIUM_BOUNCY
            
            addEndListener { _, _, _, _ ->
                dismiss()
                onDismissComplete?.invoke()
            }
            
            start()
        }
        
        // Animate opacity
        dialog?.window?.let { window ->
            ValueAnimator.ofFloat(1f, 0f).apply {
                duration = 250
                addUpdateListener { animator ->
                    val alpha = animator.animatedValue as Float
                    window.setDimAmount(0.6f * alpha)
                }
                start()
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        const val TAG = "VehicleOptionsBottomSheet"
    }
}
