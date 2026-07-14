package com.gridee.parking.ui.bottomsheet

import android.app.Dialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.gridee.parking.R
import com.gridee.parking.databinding.BottomSheetDeleteVehicleConfirmationBinding

class DeleteVehicleConfirmationBottomSheet : BottomSheetDialogFragment() {

    private var _binding: BottomSheetDeleteVehicleConfirmationBinding? = null
    private val binding get() = _binding!!

    private var onDeleteConfirmed: ((String) -> Unit)? = null
    private var vehicleNumber: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setStyle(STYLE_NORMAL, R.style.BottomSheetDialogTheme)
        
        arguments?.getString(ARG_VEHICLE_NUMBER)?.let {
            vehicleNumber = it
        }
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        return (super.onCreateDialog(savedInstanceState) as BottomSheetDialog).apply {
            setOnShowListener { dialogInterface ->
                val dialog = dialogInterface as BottomSheetDialog
                dialog.findViewById<FrameLayout>(com.google.android.material.R.id.design_bottom_sheet)
                    ?.let { sheet ->
                        sheet.setBackgroundResource(R.drawable.bg_bottom_sheet_universal)
                        sheet.fitsSystemWindows = false
                        val params = sheet.layoutParams as? ViewGroup.MarginLayoutParams
                        params?.setMargins(0, 0, 0, 0)
                        sheet.layoutParams = params

                        val behavior = BottomSheetBehavior.from(sheet)
                        behavior.state = BottomSheetBehavior.STATE_EXPANDED
                        behavior.skipCollapsed = true
                        behavior.isDraggable = true
                        behavior.isHideable = true
                        behavior.isGestureInsetBottomIgnored = true
                    }
            }
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = BottomSheetDeleteVehicleConfirmationBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.tvMessage.text = "Are you sure you want to remove vehicle $vehicleNumber?"

        binding.btnCancel.setOnClickListener {
            dismissAllowingStateLoss()
        }

        binding.btnDelete.setOnClickListener {
            onDeleteConfirmed?.invoke(vehicleNumber)
            dismissAllowingStateLoss()
        }
    }

    fun setOnDeleteConfirmed(listener: (String) -> Unit): DeleteVehicleConfirmationBottomSheet {
        onDeleteConfirmed = listener
        return this
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        const val TAG = "DeleteVehicleConfirmationBottomSheet"
        private const val ARG_VEHICLE_NUMBER = "arg_vehicle_number"

        fun newInstance(vehicleNumber: String): DeleteVehicleConfirmationBottomSheet {
            val fragment = DeleteVehicleConfirmationBottomSheet()
            val args = Bundle()
            args.putString(ARG_VEHICLE_NUMBER, vehicleNumber)
            fragment.arguments = args
            return fragment
        }
    }
}
