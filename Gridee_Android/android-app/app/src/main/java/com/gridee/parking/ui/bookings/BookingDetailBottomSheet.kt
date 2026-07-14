package com.gridee.parking.ui.bookings

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.Build
import android.content.Context
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.gridee.parking.R
import com.gridee.parking.data.model.Booking
import com.gridee.parking.databinding.BottomSheetBookingDetailBinding
import com.gridee.parking.ui.qr.QrScannerActivity
import com.gridee.parking.data.repository.BookingRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

class BookingDetailBottomSheet : BottomSheetDialogFragment() {

    private var _binding: BottomSheetBookingDetailBinding? = null
    private val binding get() = _binding!!

    private lateinit var viewModel: BookingsViewModel
    private var booking: Booking? = null
    private var penaltyUpdateJob: Job? = null
    private var lastScannedQr: String? = null
    private var countdownJob: Job? = null
    private val bookingRepository by lazy { BookingRepository(requireContext()) }

    private val scanLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == QrScannerActivity.RESULT_QR_SCANNED) {
            val qr = result.data?.getStringExtra(QrScannerActivity.EXTRA_QR_CODE) ?: return@registerForActivityResult
            lastScannedQr = qr
            val bId = booking?.id ?: return@registerForActivityResult
            when (booking?.status?.lowercase(Locale.ROOT)) {
                "pending" -> viewModel.validateCheckInQr(bId, qr)
                "active" -> viewModel.validateCheckOutQr(bId, qr)
            }
        }
    }

    companion object {
        private const val ARG_BOOKING_ID = "booking_id"

        fun newInstance(bookingId: String): BookingDetailBottomSheet {
            return BookingDetailBottomSheet().apply {
                arguments = Bundle().apply { putString(ARG_BOOKING_ID, bookingId) }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setStyle(STYLE_NORMAL, R.style.BottomSheetDialogTheme)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = BottomSheetBookingDetailBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        viewModel = ViewModelProvider(requireActivity())[BookingsViewModel::class.java]

        val bookingId = arguments?.getString(ARG_BOOKING_ID) ?: return

        setupObservers()
        setupClickListeners(bookingId)
        viewModel.refreshBooking(bookingId)
    }

    private fun setupObservers() {
        viewModel.isLoading.observe(viewLifecycleOwner) { loading ->
            binding.progressBar.visibility = if (loading) View.VISIBLE else View.GONE
            // Disable scan buttons during any network activity, but don't show spinners
            setScanButtonsEnabled(!loading)
        }
        viewModel.errorMessage.observe(viewLifecycleOwner) { err ->
            err?.let {
                val friendly = mapErrorMessage(it)
                Toast.makeText(requireContext(), friendly ?: it, Toast.LENGTH_SHORT).show()
            }
            // stop any button progress
            setCheckInLoading(false)
            setCheckOutLoading(false)
        }
        viewModel.selectedBooking.observe(viewLifecycleOwner) { b ->
            booking = b
            if (b != null) updateUI(b)
            if (b?.status?.equals("active", true) == true) {
                startPenaltyTracking(b.id ?: return@observe)
                startCountdown(b)
            } else {
                stopPenaltyTracking()
                stopCountdown()
                binding.tvPenaltyWarning.visibility = View.GONE
            }
        }
        viewModel.penalty.observe(viewLifecycleOwner) { p ->
            if (p > 0) {
                binding.tvPenaltyWarning.visibility = View.VISIBLE
                binding.tvPenaltyWarning.text = "⚠️ Overtime Penalty: ₹${String.format("%.2f", p)}"
            } else {
                binding.tvPenaltyWarning.visibility = View.GONE
            }
        }
        viewModel.qrValidation.observe(viewLifecycleOwner) { validation ->
            val b = booking ?: return@observe
            if (validation == null) return@observe
            val qr = lastScannedQr
            if (qr != null) {
                if (b.status.equals("pending", true)) {
                    // stop validating spinner
                    setCheckInLoading(false)
                    showConfirmDialog("Check-In", validation.message) {
                        setCheckInLoading(true, processing = true)
                        viewModel.checkIn(b.id ?: return@showConfirmDialog, qr)
                    }
                } else if (b.status.equals("active", true)) {
                    setCheckOutLoading(false)
                    showConfirmDialog("Check-Out", validation.message) {
                        setCheckOutLoading(true, processing = true)
                        viewModel.checkOut(b.id ?: return@showConfirmDialog, qr)
                    }
                }
            }
            // Clear after consumption
            viewModel.clearQrValidation()
        }
        viewModel.checkInSuccess.observe(viewLifecycleOwner) { ok ->
            ok?.let {
                Toast.makeText(requireContext(), if (it) "Checked in" else "Check-in failed", Toast.LENGTH_SHORT).show()
                viewModel.clearCheckInSuccess()
                setCheckInLoading(false)
                if (it) dismissAllowingStateLoss()
            }
        }
        viewModel.checkOutSuccess.observe(viewLifecycleOwner) { ok ->
            ok?.let {
                Toast.makeText(requireContext(), if (it) "Checked out" else "Check-out failed", Toast.LENGTH_SHORT).show()
                viewModel.clearCheckOutSuccess()
                setCheckOutLoading(false)
                if (it) dismissAllowingStateLoss()
            }
        }
    }

    private fun setupClickListeners(bookingId: String) {
        binding.btnClose.setOnClickListener { dismissAllowingStateLoss() }
        binding.btnRefresh.setOnClickListener { viewModel.refreshBooking(bookingId) }
        // Show price breakup when tapping on amount
        binding.tvAmount.setOnClickListener {
            val b = booking ?: return@setOnClickListener
            lifecycleScope.launch {
                try {
                    val result = bookingRepository.getPriceBreakup(b.id ?: "")
                    if (result.isSuccess) {
                        val map = result.getOrNull() ?: emptyMap()
                        val message = buildString {
                            map.forEach { (k, v) ->
                                append("• ").append(k).append(": ").append(v.toString()).append('\n')
                            }
                        }
                        MaterialAlertDialogBuilder(requireContext())
                            .setTitle("Price Breakup")
                            .setMessage(message.trim())
                            .setPositiveButton("OK", null)
                            .show()
                    } else {
                        Toast.makeText(requireContext(), result.exceptionOrNull()?.message ?: "Failed to load price breakup", Toast.LENGTH_SHORT).show()
                    }
                } catch (e: Exception) {
                    Toast.makeText(requireContext(), e.message ?: "Failed to load price breakup", Toast.LENGTH_SHORT).show()
                }
            }
        }
        binding.btnCheckIn.setOnClickListener {
            val intent = Intent(requireContext(), QrScannerActivity::class.java)
            setCheckInLoading(true, validating = true)
            scanLauncher.launch(intent)
        }
        binding.btnCheckOut.setOnClickListener {
            val intent = Intent(requireContext(), QrScannerActivity::class.java)
            setCheckOutLoading(true, validating = true)
            scanLauncher.launch(intent)
        }
        binding.btnExtendBooking.setOnClickListener {
            showExtendDialog()
        }
        binding.btnCancelBooking.setOnClickListener {
            val b = booking ?: return@setOnClickListener
            if (!b.status.equals("pending", true)) {
                Toast.makeText(requireContext(), "Only booked bookings can be cancelled", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            MaterialAlertDialogBuilder(requireContext())
                .setTitle("Cancel Booking")
                .setMessage("Are you sure you want to cancel this booking?")
                .setNegativeButton("No", null)
                .setPositiveButton("Yes, Cancel") { _, _ ->
                    binding.btnCancelBooking.isEnabled = false
                    lifecycleScope.launch {
                        try {
                            val result = bookingRepository.cancelBooking(b.id ?: "")
                            if (result.isSuccess) {
                                // Use MainContainerActivity's robust intent receiver instead of fragile view posts
                                val mainActivity = requireActivity()
                                val refundIntent = Intent(mainActivity, com.gridee.parking.ui.main.MainContainerActivity::class.java).apply {
                                    flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                                    putExtra(com.gridee.parking.ui.main.MainContainerActivity.EXTRA_SHOW_WALLET_TRANSACTION, true)
                                    putExtra(com.gridee.parking.ui.main.MainContainerActivity.EXTRA_WALLET_TRANSACTION_TITLE, "Booking Refund")
                                    putExtra(com.gridee.parking.ui.main.MainContainerActivity.EXTRA_WALLET_TRANSACTION_AMOUNT, "+₹${String.format(java.util.Locale.getDefault(), "%.2f", b.amount)}")
                                    putExtra(com.gridee.parking.ui.main.MainContainerActivity.EXTRA_WALLET_TRANSACTION_IS_CREDIT, true)
                                }
                                mainActivity.startActivity(refundIntent)
                                
                                dismissAllowingStateLoss()
                                viewModel.loadUserBookings()
                            } else {
                                Toast.makeText(requireContext(), result.exceptionOrNull()?.message ?: "Failed to cancel", Toast.LENGTH_SHORT).show()
                                binding.btnCancelBooking.isEnabled = true
                            }
                        } catch (e: Exception) {
                            Toast.makeText(requireContext(), e.message ?: "Failed to cancel", Toast.LENGTH_SHORT).show()
                            binding.btnCancelBooking.isEnabled = true
                        }
                    }
                }
                .show()
        }
    }

    private fun updateUI(booking: Booking) {
        // Status styling chip using existing drawables and colors
        val statusNorm = booking.status.lowercase(Locale.getDefault())
        binding.tvStatus.text = if (statusNorm == "pending") "BOOKED" else statusNorm.uppercase(Locale.getDefault())
        when (statusNorm) {
            "active" -> {
                binding.tvStatus.background = resources.getDrawable(R.drawable.status_outlined_active, null)
                binding.tvStatus.setTextColor(resources.getColor(R.color.booking_status_active_text, null))
            }
            "pending" -> {
                binding.tvStatus.background = resources.getDrawable(R.drawable.status_outlined_pending, null)
                binding.tvStatus.setTextColor(resources.getColor(R.color.booking_status_pending_text, null))
            }
            else -> {
                binding.tvStatus.background = resources.getDrawable(R.drawable.status_outlined_completed, null)
                binding.tvStatus.setTextColor(resources.getColor(R.color.booking_status_completed_text, null))
            }
        }
        // Show appropriate action
        when (booking.status.lowercase(Locale.getDefault())) {
            "pending" -> {
                binding.btnCheckIn.visibility = View.GONE
                binding.btnCheckOut.visibility = View.GONE
                binding.btnExtendBooking.visibility = View.VISIBLE
                binding.btnCancelBooking.visibility = View.VISIBLE
                binding.progressCheckIn.visibility = View.GONE
            }
            "active" -> {
                binding.btnCheckIn.visibility = View.GONE
                binding.btnCheckOut.visibility = View.VISIBLE
                binding.btnExtendBooking.visibility = View.VISIBLE
                binding.btnCancelBooking.visibility = View.GONE
            }
            else -> {
                binding.btnCheckIn.visibility = View.GONE
                binding.btnCheckOut.visibility = View.GONE
                binding.btnExtendBooking.visibility = View.GONE
                binding.btnCancelBooking.visibility = View.GONE
            }
        }

        // Fill details
        binding.tvSpotName.text = "Spot: ${booking.spotId}"
        binding.tvLotId.text = "Lot ID: ${booking.lotId}"
        binding.tvVehicleNumber.text = "Vehicle: ${booking.vehicleNumber ?: "-"}"
        val df = SimpleDateFormat("MMM dd, yyyy 'at' hh:mm a", Locale.getDefault())
        booking.checkInTime?.let { binding.tvCheckInTime.text = "Check-in: ${df.format(it)}" }
        booking.checkOutTime?.let { binding.tvCheckOutTime.text = "Check-out: ${df.format(it)}" }
        binding.tvAmount.text = String.format("%.2f", booking.amount)
    }

    private fun showExtendDialog() {
        val b = booking ?: return
        val dialogView = layoutInflater.inflate(R.layout.dialog_extend_booking, null)
        val tvCurrent = dialogView.findViewById<android.widget.TextView>(R.id.tv_current_end_time)
        val datePicker = dialogView.findViewById<android.widget.DatePicker>(R.id.date_picker)
        val timePicker = dialogView.findViewById<android.widget.TimePicker>(R.id.time_picker)
        val btnCancel = dialogView.findViewById<android.widget.Button>(R.id.btn_cancel)
        val btnExtend = dialogView.findViewById<android.widget.Button>(R.id.btn_extend)

        val ctx = requireContext()
        val fmt = java.text.SimpleDateFormat("EEE, MMM d, yyyy h:mm a", java.util.Locale.getDefault())
        val currentEnd = b.checkOutTime ?: java.util.Date()
        tvCurrent.text = "Current end: ${fmt.format(currentEnd)}"

        // Initialize pickers with current end time
        val cal = java.util.Calendar.getInstance().apply { time = currentEnd }
        datePicker.updateDate(cal.get(java.util.Calendar.YEAR), cal.get(java.util.Calendar.MONTH), cal.get(java.util.Calendar.DAY_OF_MONTH))
        if (android.os.Build.VERSION.SDK_INT >= 23) {
            timePicker.hour = cal.get(java.util.Calendar.HOUR_OF_DAY)
            timePicker.minute = cal.get(java.util.Calendar.MINUTE)
        } else {
            @Suppress("DEPRECATION")
            run {
                timePicker.currentHour = cal.get(java.util.Calendar.HOUR_OF_DAY)
                timePicker.currentMinute = cal.get(java.util.Calendar.MINUTE)
            }
        }

        val dialog = androidx.appcompat.app.AlertDialog.Builder(ctx)
            .setView(dialogView)
            .create()

        btnCancel.setOnClickListener { dialog.dismiss() }
        btnExtend.setOnClickListener {
            val newCal = java.util.Calendar.getInstance()
            newCal.set(datePicker.year, datePicker.month, datePicker.dayOfMonth)
            val selHour: Int = if (android.os.Build.VERSION.SDK_INT >= 23) timePicker.hour else @Suppress("DEPRECATION") { timePicker.currentHour }
            val selMinute: Int = if (android.os.Build.VERSION.SDK_INT >= 23) timePicker.minute else @Suppress("DEPRECATION") { timePicker.currentMinute }
            newCal.set(java.util.Calendar.HOUR_OF_DAY, selHour)
            newCal.set(java.util.Calendar.MINUTE, selMinute)
            newCal.set(java.util.Calendar.SECOND, 0)
            val newDate = newCal.time

            if (newDate <= currentEnd) {
                android.widget.Toast.makeText(ctx, "Select a time after current end", android.widget.Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            // Call VM to extend
            viewModel.extendBooking(b.id ?: return@setOnClickListener, newDate)
            dialog.dismiss()
        }

        dialog.show()
    }

    private fun setCheckInLoading(loading: Boolean, validating: Boolean = false, processing: Boolean = false) {
        binding.btnCheckIn.isEnabled = !loading
        binding.progressCheckIn.visibility = if (loading) View.VISIBLE else View.GONE
        binding.btnCheckIn.text = when {
            processing -> getString(R.string.processing_check_in)
            validating -> getString(R.string.validating_qr)
            else -> getString(R.string.scan_to_check_in)
        }
    }

    private fun setCheckOutLoading(loading: Boolean, validating: Boolean = false, processing: Boolean = false) {
        binding.btnCheckOut.isEnabled = !loading
        binding.progressCheckOut.visibility = if (loading) View.VISIBLE else View.GONE
        binding.btnCheckOut.text = when {
            processing -> getString(R.string.processing_check_out)
            validating -> getString(R.string.validating_qr)
            else -> getString(R.string.scan_to_check_out)
        }
    }

    private fun startPenaltyTracking(bookingId: String) {
        penaltyUpdateJob?.cancel()
        penaltyUpdateJob = viewLifecycleOwner.lifecycleScope.launch {
            while (isActive) {
                viewModel.loadPenaltyInfo(bookingId)
                delay(30_000L)
            }
        }
    }

    private fun stopPenaltyTracking() {
        penaltyUpdateJob?.cancel()
        penaltyUpdateJob = null
    }

    private fun showConfirmDialog(title: String, message: String, onConfirm: () -> Unit) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(title)
            .setMessage(message)
            .setPositiveButton("Proceed") { _, _ -> onConfirm() }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun mapErrorMessage(raw: String?): String? {
        if (raw == null) return null
        val key = raw.uppercase(Locale.getDefault())
        return when {
            key.contains("INVALID_QR") -> "This QR code doesn't match your booking"
            key.contains("WRONG_STATUS") -> "This booking cannot be checked in/out"
            key.contains("ALREADY_ACTIVE") -> "Your earlier booking is still active. Queued back-to-back bookings will activate automatically."
            key.contains("INSUFFICIENT_FUNDS") || key.contains("PAYMENT") -> "Please top up your wallet to pay penalties"
            key.contains("NOT_FOUND") -> "Booking not found"
            else -> raw
        }
    }

    private fun vibrateShort() {
        try {
            val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vm = requireContext().getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as android.os.VibratorManager
                vm.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                requireContext().getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator.vibrate(VibrationEffect.createOneShot(40, VibrationEffect.DEFAULT_AMPLITUDE))
            } else {
                @Suppress("DEPRECATION")
                vibrator.vibrate(40)
            }
        } catch (_: Exception) {}
    }

    private fun startCountdown(b: Booking) {
        stopCountdown()
        val end = b.checkOutTime ?: return
        countdownJob = viewLifecycleOwner.lifecycleScope.launch {
            while (isActive) {
                val now = System.currentTimeMillis()
                val graceMs = 10 * 60 * 1000L
                val target = end.time + graceMs
                val remaining = target - now
                if (remaining >= 0) {
                    binding.tvCountdown.setTextColor(resources.getColor(R.color.booking_sheet_secondary_text, null))
                    binding.tvCountdown.text = "Time left: ${formatHms(remaining)}"
                } else {
                    binding.tvCountdown.setTextColor(0xFFC62828.toInt())
                    binding.tvCountdown.text = "Overtime: ${formatHms(-remaining)}"
                }
                delay(1000L)
            }
        }
    }

    private fun stopCountdown() {
        countdownJob?.cancel()
        countdownJob = null
    }

    private fun formatHms(ms: Long): String {
        var seconds = ms / 1000
        val h = seconds / 3600
        seconds %= 3600
        val m = seconds / 60
        val s = seconds % 60
        return String.format(Locale.getDefault(), "%02d:%02d:%02d", h, m, s)
    }

    private fun setScanButtonsEnabled(enabled: Boolean) {
        binding.btnCheckIn.isEnabled = enabled
        binding.btnCheckOut.isEnabled = enabled
    }

    override fun onDestroyView() {
        super.onDestroyView()
        stopPenaltyTracking()
        _binding = null
    }
}
