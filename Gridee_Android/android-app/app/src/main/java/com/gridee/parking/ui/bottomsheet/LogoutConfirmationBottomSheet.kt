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
import com.gridee.parking.databinding.BottomSheetLogoutConfirmationBinding

class LogoutConfirmationBottomSheet : BottomSheetDialogFragment() {

    private var _binding: BottomSheetLogoutConfirmationBinding? = null
    private val binding get() = _binding!!

    private var onLogoutConfirmed: (() -> Unit)? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setStyle(STYLE_NORMAL, R.style.BottomSheetDialogTheme)
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        return (super.onCreateDialog(savedInstanceState) as BottomSheetDialog).apply {
            setOnShowListener { dialogInterface ->
                val dialog = dialogInterface as BottomSheetDialog
                dialog.findViewById<FrameLayout>(com.google.android.material.R.id.design_bottom_sheet)
                    ?.let { sheet ->
                        // Match other bottom sheets: keep the nav-area surface opaque.
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
        _binding = BottomSheetLogoutConfirmationBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.btnCancel.setOnClickListener {
            dismissAllowingStateLoss()
        }

        binding.btnLogout.setOnClickListener {
            onLogoutConfirmed?.invoke()
            dismissAllowingStateLoss()
        }
    }

    fun setOnLogoutConfirmed(listener: () -> Unit): LogoutConfirmationBottomSheet {
        onLogoutConfirmed = listener
        return this
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        const val TAG = "LogoutConfirmationBottomSheet"

        fun newInstance(): LogoutConfirmationBottomSheet = LogoutConfirmationBottomSheet()
    }
}
