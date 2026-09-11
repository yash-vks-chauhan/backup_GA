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
import androidx.lifecycle.Lifecycle
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.card.MaterialCardView
import com.gridee.parking.R
import com.gridee.parking.databinding.BottomSheetAddVehicleBinding

class AddVehicleBottomSheet : BottomSheetDialogFragment() {

    private var _binding: BottomSheetAddVehicleBinding? = null
    private val binding get() = _binding!!
    private val existingVehicleNumbers: List<String>
        get() = arguments?.getStringArrayList(ARG_EXISTING_VEHICLES).orEmpty()
    private var actionDelivered = false
    private var pendingVehicleNumber: String? = null
    private val entrySprings = mutableListOf<SpringAnimation>()
    private var buttonAnimationGeneration = 0
    private val resumeDeliveryRunnable = Runnable {
        if (actionDelivered) {
            dismissIfStateCanBeSaved()
        } else {
            deliverPendingActionIfPossible()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        actionDelivered = savedInstanceState?.getBoolean(STATE_ACTION_DELIVERED) ?: false
        pendingVehicleNumber = savedInstanceState?.getString(STATE_PENDING_VEHICLE_NUMBER)
            ?.takeIf { it.isNotBlank() }
        setStyle(STYLE_NORMAL, com.gridee.parking.R.style.BottomSheetDialogTheme)
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): android.app.Dialog {
        val dialog = super.onCreateDialog(savedInstanceState)
        dialog.setOnShowListener { dialogInterface ->
            val bottomSheetDialog = dialogInterface as com.google.android.material.bottomsheet.BottomSheetDialog
            
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
            }
            
            // Ensure behavior ignores gesture insets
            bottomSheetDialog.behavior.isGestureInsetBottomIgnored = true
            
            // Force fully expanded state
            bottomSheetDialog.behavior.state = com.google.android.material.bottomsheet.BottomSheetBehavior.STATE_EXPANDED
            bottomSheetDialog.behavior.skipCollapsed = true
            
            bottomSheetDialog.window?.let { window ->
                androidx.core.view.WindowCompat.setDecorFitsSystemWindows(window, false)
                
                // Ensure light nav bar (dark icons) since background is white
                val wic = androidx.core.view.WindowCompat.getInsetsController(window, window.decorView)
                wic.isAppearanceLightNavigationBars = true
                
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                    window.isNavigationBarContrastEnforced = false
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
        binding.btnAddVehicle.isEnabled = !actionDelivered && pendingVehicleNumber == null
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
        binding.etVehicleNumber.setOnFocusChangeListener { _, hasFocus ->
            if (hasFocus) {
                binding.tilVehicleNumber.error = null
            }
        }
    }

    private fun setupClickListeners() {
        binding.btnAddVehicle.setOnClickListener {
            addVehicle()
        }

        binding.btnClose.setOnClickListener {
            dismissIfStateCanBeSaved()
        }
    }

    private fun addVehicle() {
        val vehicleNumber = com.gridee.parking.utils.VehicleNumberValidator.normalize(
            binding.etVehicleNumber.text.toString()
        )

        val error = com.gridee.parking.utils.VehicleNumberValidator.getError(vehicleNumber)
        if (error != null) {
            binding.tilVehicleNumber.error = error
            return
        }

        if (com.gridee.parking.utils.VehicleNumberValidator.containsEquivalent(existingVehicleNumbers, vehicleNumber)) {
            binding.tilVehicleNumber.error = "Vehicle number already exists"
            return
        }
        binding.tilVehicleNumber.error = null

        if (actionDelivered || pendingVehicleNumber != null) return
        pendingVehicleNumber = vehicleNumber
        binding.btnAddVehicle.isEnabled = false

        // Animate button press
        animateButtonPress {
            val currentBinding = _binding ?: return@animateButtonPress
            // Hide keyboard
            val imm = requireContext().getSystemService(android.content.Context.INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager
            imm.hideSoftInputFromWindow(currentBinding.etVehicleNumber.windowToken, 0)

            deliverPendingActionIfPossible()
        }
    }

    override fun onResume() {
        super.onResume()
        // FragmentManager can still be executing the transaction that resumed this fragment.
        // Post delivery to the next main-loop turn so a restored pending action never tries to
        // dismiss from inside executeOpsTogether().
        _binding?.root?.removeCallbacks(resumeDeliveryRunnable)
        _binding?.root?.post(resumeDeliveryRunnable)
    }

    private fun deliverPendingActionIfPossible() {
        val vehicleNumber = pendingVehicleNumber ?: return
        val fragmentManager = runCatching { parentFragmentManager }.getOrNull() ?: return
        if (!isAdded || !lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED) ||
            fragmentManager.isDestroyed || fragmentManager.isStateSaved
        ) return

        // Mark before publishing so a synchronous result listener cannot re-enter and emit the
        // same mutation twice. Roll back if FragmentManager unexpectedly rejects the result.
        actionDelivered = true
        pendingVehicleNumber = null
        val published = runCatching {
            fragmentManager.setFragmentResult(
                RESULT_KEY,
                Bundle().apply { putString(RESULT_VEHICLE_NUMBER, vehicleNumber) },
            )
        }.isSuccess
        if (!published) {
            actionDelivered = false
            pendingVehicleNumber = vehicleNumber
            return
        }
        dismissIfStateCanBeSaved()
    }

    private fun dismissIfStateCanBeSaved() {
        val fragmentManager = runCatching { parentFragmentManager }.getOrNull() ?: return
        if (!isAdded || !lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED) ||
            fragmentManager.isDestroyed || fragmentManager.isStateSaved
        ) return
        dismiss()
    }

    private fun animateEntry() {
        // Scale animation for smooth entry
        binding.root.scaleY = 0.95f
        binding.root.alpha = 0f
        
        entrySprings.forEach { it.cancel() }
        entrySprings.clear()
        val scaleY = SpringAnimation(binding.root, DynamicAnimation.SCALE_Y, 1f).apply {
            spring.stiffness = SpringForce.STIFFNESS_MEDIUM
            spring.dampingRatio = SpringForce.DAMPING_RATIO_MEDIUM_BOUNCY
        }
        
        val alpha = SpringAnimation(binding.root, DynamicAnimation.ALPHA, 1f).apply {
            spring.stiffness = SpringForce.STIFFNESS_MEDIUM
            spring.dampingRatio = SpringForce.DAMPING_RATIO_LOW_BOUNCY
        }
        entrySprings += scaleY
        entrySprings += alpha
        entrySprings.forEach { it.start() }
    }

    private fun animateButtonPress(onComplete: () -> Unit) {
        val button = binding.btnAddVehicle
        val generation = ++buttonAnimationGeneration
        
        // Scale down animation
        button.animate()
            .scaleX(0.95f)
            .scaleY(0.95f)
            .setDuration(100)
            .withEndAction {
                // Scale back up
                if (generation == buttonAnimationGeneration && _binding != null) {
                    button.animate()
                        .scaleX(1f)
                        .scaleY(1f)
                        .setDuration(100)
                        .withEndAction {
                            if (generation == buttonAnimationGeneration && _binding != null) {
                                onComplete()
                            }
                        }
                        .start()
                }
            }
            .start()
    }

    override fun onDestroyView() {
        buttonAnimationGeneration += 1
        entrySprings.forEach { it.cancel() }
        entrySprings.clear()
        _binding?.root?.removeCallbacks(resumeDeliveryRunnable)
        _binding?.btnAddVehicle?.animate()
            ?.setListener(null)
            ?.withStartAction(null)
            ?.withEndAction(null)
            ?.cancel()
        super.onDestroyView()
        _binding = null
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putBoolean(STATE_ACTION_DELIVERED, actionDelivered)
        pendingVehicleNumber?.let {
            outState.putString(STATE_PENDING_VEHICLE_NUMBER, it)
        }
        super.onSaveInstanceState(outState)
    }

    companion object {
        const val TAG = "AddVehicleBottomSheet"
        const val RESULT_KEY = "add_vehicle_action"
        const val RESULT_VEHICLE_NUMBER = "vehicle_number"

        private const val ARG_EXISTING_VEHICLES = "existing_vehicle_numbers"
        private const val STATE_ACTION_DELIVERED = "action_delivered"
        private const val STATE_PENDING_VEHICLE_NUMBER = "pending_vehicle_number"

        fun newInstance(existingVehicleNumbers: List<String>): AddVehicleBottomSheet =
            AddVehicleBottomSheet().apply {
                arguments = Bundle().apply {
                    putStringArrayList(
                        ARG_EXISTING_VEHICLES,
                        ArrayList(existingVehicleNumbers),
                    )
                }
            }
    }
}
