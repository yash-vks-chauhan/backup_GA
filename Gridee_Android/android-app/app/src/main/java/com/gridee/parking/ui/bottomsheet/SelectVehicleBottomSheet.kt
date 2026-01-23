package com.gridee.parking.ui.bottomsheet

import android.app.Dialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
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
    private val onAddVehicle: () -> Unit
) : BottomSheetDialogFragment() {

    private var _binding: BottomSheetSelectVehicleBinding? = null
    private val binding get() = _binding!!

    private var selectedVehicle: Vehicle? = null

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
                sheet.background = null
                sheet.fitsSystemWindows = false

                val params = sheet.layoutParams as? ViewGroup.MarginLayoutParams
                params?.setMargins(0, 0, 0, 0)
                sheet.layoutParams = params

                ViewCompat.setOnApplyWindowInsetsListener(sheet) { view, insets ->
                    view.setPadding(0, 0, 0, 0)
                    insets
                }
            }

            bottomSheetDialog.behavior.isGestureInsetBottomIgnored = true
            bottomSheetDialog.behavior.state =
                com.google.android.material.bottomsheet.BottomSheetBehavior.STATE_EXPANDED
            bottomSheetDialog.behavior.skipCollapsed = true

            bottomSheetDialog.window?.let { window ->
                WindowCompat.setDecorFitsSystemWindows(window, false)
                val navBarColor = ContextCompat.getColor(requireContext(), R.color.white)
                window.navigationBarColor = navBarColor
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
        setupVehicleList()
        setupClickListeners()

        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
            binding.root.outlineAmbientShadowColor = android.graphics.Color.parseColor("#40000000")
            binding.root.outlineSpotShadowColor = android.graphics.Color.parseColor("#40000000")
        }
    }

    private fun setupVehicleList() {
        val adapter = VehicleSelectionAdapter(vehicles) { vehicle ->
            selectedVehicle = vehicle
            onVehicleSelected(vehicle)
            dismiss()
        }

        binding.rvVehicles.layoutManager = LinearLayoutManager(requireContext())
        binding.rvVehicles.adapter = adapter

        selectedVehicleId?.let { currentId ->
            val position = vehicles.indexOfFirst { it.id == currentId }
            if (position >= 0) {
                adapter.setSelectedPosition(position)
                selectedVehicle = vehicles[position]
            }
        }
    }

    private fun setupClickListeners() {
        binding.btnClose.setOnClickListener { dismiss() }
        // specific button listeners removed as buttons are removed from layout
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        const val TAG = "SelectVehicleBottomSheet"
    }
}
