package com.gridee.parking.ui.bottomsheet

import android.app.Dialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.AnimationUtils
import androidx.core.view.WindowCompat
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.gridee.parking.R
import com.gridee.parking.data.model.Vehicle
import com.gridee.parking.databinding.BottomSheetSelectVehicleBinding
import com.gridee.parking.ui.booking.VehicleSelectionAdapter

class SelectVehicleBottomSheet(
    private val vehicles: List<Vehicle>,
    private val selectedVehicleId: String?,
    private val onVehicleSelected: (Vehicle) -> Unit,
    private val onAddVehicle: (vehicleNumber: String, callback: (Boolean) -> Unit) -> Unit
) : BottomSheetDialogFragment() {

    private var _binding: BottomSheetSelectVehicleBinding? = null
    private val binding get() = _binding!!

    private val currentVehicles: MutableList<Vehicle> = mutableListOf()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setStyle(STYLE_NORMAL, R.style.BottomSheetDialogTheme)
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val dialog = super.onCreateDialog(savedInstanceState)
        dialog.setOnShowListener { dialogInterface ->
            val bottomSheetDialog = dialogInterface as BottomSheetDialog

            val bottomSheet =
                bottomSheetDialog.findViewById<View>(com.google.android.material.R.id.design_bottom_sheet)
            bottomSheet?.let { sheet ->
                sheet.setBackgroundResource(R.drawable.bg_bottom_sheet_universal)
                sheet.fitsSystemWindows = false

                val params = sheet.layoutParams as? ViewGroup.MarginLayoutParams
                params?.setMargins(0, 0, 0, 0)
                sheet.layoutParams = params
            }

            bottomSheetDialog.behavior.isGestureInsetBottomIgnored = true
            bottomSheetDialog.behavior.state =
                com.google.android.material.bottomsheet.BottomSheetBehavior.STATE_EXPANDED
            bottomSheetDialog.behavior.skipCollapsed = true

            bottomSheetDialog.window?.let { window ->
                WindowCompat.setDecorFitsSystemWindows(window, false)
                window.navigationBarColor = android.graphics.Color.TRANSPARENT
                window.isNavigationBarContrastEnforced = false

                val wic = WindowCompat.getInsetsController(window, window.decorView)
                wic.isAppearanceLightNavigationBars = true

                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
                    window.navigationBarDividerColor = android.graphics.Color.TRANSPARENT
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                        window.isNavigationBarContrastEnforced = false
                    }
                }

                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                    window.attributes.blurBehindRadius = 50
                    window.attributes = window.attributes
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
        _binding = BottomSheetSelectVehicleBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        currentVehicles.clear()
        currentVehicles.addAll(vehicles)

        setupVehicleList()
        setupClickListeners()
        binding.etVehicleNumber.setOnFocusChangeListener { _, hasFocus ->
            if (hasFocus) {
                binding.tilVehicleNumber.error = null
            }
        }

        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
            binding.root.outlineAmbientShadowColor = android.graphics.Color.parseColor("#40000000")
            binding.root.outlineSpotShadowColor = android.graphics.Color.parseColor("#40000000")
        }
    }

    private fun setupVehicleList() {
        if (currentVehicles.isEmpty()) {
            binding.emptyStateContainer.visibility = View.VISIBLE
            binding.rvVehicles.visibility = View.GONE
        } else {
            binding.emptyStateContainer.visibility = View.GONE
            binding.rvVehicles.visibility = View.VISIBLE
        }

        val adapter = VehicleSelectionAdapter(currentVehicles) { vehicle ->
            onVehicleSelected(vehicle)
            dismiss()
        }

        binding.rvVehicles.layoutManager = LinearLayoutManager(requireContext())
        binding.rvVehicles.adapter = adapter

        selectedVehicleId?.let { currentId ->
            val position = currentVehicles.indexOfFirst { it.id == currentId }
            if (position >= 0) {
                adapter.setSelectedPosition(position)
            }
        }
    }

    private fun setupClickListeners() {
        binding.btnClose.setOnClickListener { dismiss() }

        binding.btnAddNewVehicle.setOnClickListener {
            it.performHapticFeedback(android.view.HapticFeedbackConstants.VIRTUAL_KEY)
            navigateToAddPage()
        }

        binding.btnBack.setOnClickListener {
            it.performHapticFeedback(android.view.HapticFeedbackConstants.VIRTUAL_KEY)
            navigateToSelectPage()
        }

        binding.btnAddVehicle.setOnClickListener {
            it.performHapticFeedback(android.view.HapticFeedbackConstants.VIRTUAL_KEY)
            attemptAddVehicle()
        }
    }

    // --- Page navigation ---

    private fun navigateToAddPage() {
        binding.etVehicleNumber.text?.clear()
        binding.tilVehicleNumber.error = null

        // Swap header buttons: hide close, show back
        binding.btnClose.animate().alpha(0f).setDuration(150).withEndAction {
            binding.btnClose.visibility = View.GONE
        }.start()
        binding.btnBack.visibility = View.VISIBLE
        binding.btnBack.alpha = 0f
        binding.btnBack.animate().alpha(1f).setDuration(200).setStartDelay(80).start()

        // Slide forward
        binding.viewFlipper.inAnimation = AnimationUtils.loadAnimation(requireContext(), R.anim.sheet_page_in_right)
        binding.viewFlipper.outAnimation = AnimationUtils.loadAnimation(requireContext(), R.anim.sheet_page_out_left)
        binding.viewFlipper.displayedChild = 1
    }

    private fun navigateToSelectPage() {
        hideKeyboard()

        // Swap header buttons: show close, hide back
        binding.btnBack.animate().alpha(0f).setDuration(150).withEndAction {
            binding.btnBack.visibility = View.GONE
        }.start()
        binding.btnClose.visibility = View.VISIBLE
        binding.btnClose.alpha = 0f
        binding.btnClose.animate().alpha(1f).setDuration(200).setStartDelay(80).start()

        // Slide back
        binding.viewFlipper.inAnimation = AnimationUtils.loadAnimation(requireContext(), R.anim.sheet_page_in_left)
        binding.viewFlipper.outAnimation = AnimationUtils.loadAnimation(requireContext(), R.anim.sheet_page_out_right)
        binding.viewFlipper.displayedChild = 0
    }

    // --- Add vehicle ---

    private fun attemptAddVehicle() {
        val vehicleNumber = com.gridee.parking.utils.VehicleNumberValidator.normalize(
            binding.etVehicleNumber.text.toString()
        )

        val error = com.gridee.parking.utils.VehicleNumberValidator.getError(vehicleNumber)
        if (error != null) {
            binding.tilVehicleNumber.error = error
            return
        }

        if (
            com.gridee.parking.utils.VehicleNumberValidator.containsEquivalent(
                currentVehicles.map { it.number },
                vehicleNumber
            )
        ) {
            binding.tilVehicleNumber.error = "Vehicle number already exists"
            return
        }
        binding.tilVehicleNumber.error = null

        // Loading state
        binding.btnAddVehicle.isEnabled = false
        binding.btnAddVehicle.text = ""
        binding.btnBack.isEnabled = false

        onAddVehicle(vehicleNumber) { success ->
            if (!isAdded) return@onAddVehicle

            requireActivity().runOnUiThread {
                binding.btnAddVehicle.isEnabled = true
                binding.btnAddVehicle.text = "Add Vehicle"
                binding.btnBack.isEnabled = true

                if (success) {
                    hideKeyboard()

                    // Show success notification
                    val parentView = requireActivity().findViewById<ViewGroup>(R.id.fragment_container)
                        ?: requireActivity().window.decorView as? ViewGroup
                        ?: binding.root
                    com.gridee.parking.utils.NotificationHelper.showSuccess(
                        parent = parentView,
                        message = "Vehicle $vehicleNumber added successfully",
                        duration = 3000L
                    )

                    // Add the new vehicle to local list immediately so it shows instantly
                    currentVehicles.add(
                        Vehicle(
                            id = "user_vehicle_${currentVehicles.size}",
                            number = vehicleNumber,
                            type = "Car",
                            brand = "User",
                            model = "Vehicle",
                            isDefault = currentVehicles.isEmpty()
                        )
                    )

                    // Rebuild adapter with updated list
                    setupVehicleList()

                    // Navigate back to select page
                    navigateToSelectPage()
                } else {
                    binding.tilVehicleNumber.error = "Failed to add vehicle. Please try again."
                }
            }
        }
    }

    private fun hideKeyboard() {
        val imm = requireContext().getSystemService(android.content.Context.INPUT_METHOD_SERVICE)
                as android.view.inputmethod.InputMethodManager
        binding.etVehicleNumber.clearFocus()
        imm.hideSoftInputFromWindow(binding.etVehicleNumber.windowToken, 0)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        const val TAG = "SelectVehicleBottomSheet"
    }
}
