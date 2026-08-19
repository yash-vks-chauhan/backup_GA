package com.gridee.parking.ui.bottomsheet

import android.app.Dialog
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.DialogInterface
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.os.bundleOf
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.setFragmentResult
import com.gridee.parking.R
import com.gridee.parking.databinding.DialogBookingPassDetailsBinding
import com.gridee.parking.ui.adapters.Booking

/**
 * Where the booking ID lives now.
 *
 * It used to occupy the line directly under the code — the most valuable real estate on the
 * pass — while telling the user nothing they could act on. Support still needs it, so it is
 * one tap away behind the pass's Details button, with a Copy action and the times and amount
 * that are actually useful alongside it.
 */
class BookingPassDetailsDialog : DialogFragment() {

    private var _binding: DialogBookingPassDetailsBinding? = null
    private val binding get() = requireNotNull(_binding)

    private val booking: Booking?
        get() = @Suppress("DEPRECATION") (arguments?.getSerializable(ARG_BOOKING) as? Booking)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setStyle(STYLE_NO_TITLE, R.style.Theme_Gridee_NoActionBar)
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        return Dialog(requireContext(), theme).apply { setCanceledOnTouchOutside(true) }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = DialogBookingPassDetailsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val current = booking ?: run { dismiss(); return }

        binding.tvDetailsId.text = current.id
        binding.tvDetailsStart.text = current.startTime.ifBlank { "—" }
        binding.tvDetailsEnd.text = current.endTime.ifBlank { "—" }
        binding.tvDetailsAmount.text = current.amount.ifBlank { "—" }

        val lot = current.locationName.trim()
        binding.tvDetailsSpot.text = if (lot.isEmpty()) {
            current.spotName
        } else {
            getString(R.string.pass_where_format, current.spotName, lot)
        }

        binding.btnDetailsCopy.setOnClickListener { copyId(current.id) }
        binding.btnDetailsDone.setOnClickListener { dismiss() }
        binding.detailsBackdrop.setOnClickListener { dismiss() }
    }

    override fun onStart() {
        super.onStart()
        dialog?.window?.let { window ->
            window.setLayout(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            window.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            window.attributes = window.attributes.apply { windowAnimations = 0 }
        }
    }

    private fun copyId(id: String) {
        val clipboard = requireContext().getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
            ?: return
        clipboard.setPrimaryClip(ClipData.newPlainText(getString(R.string.booking_details_sheet_id), id))
        // Android 13+ shows its own copy confirmation; a second toast would double it up.
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            Toast.makeText(
                requireContext(),
                R.string.booking_details_sheet_copied,
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    override fun onDismiss(dialog: DialogInterface) {
        setFragmentResult(RESULT_KEY_CLOSED, bundleOf())
        super.onDismiss(dialog)
    }

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }

    companion object {
        const val TAG = "BookingPassDetailsDialog"
        const val RESULT_KEY_CLOSED = "booking_pass_details_closed"
        private const val ARG_BOOKING = "booking"

        fun newInstance(booking: Booking) = BookingPassDetailsDialog().apply {
            arguments = bundleOf(ARG_BOOKING to booking)
        }
    }
}
