package com.gridee.parking.ui.booking

import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.content.Intent
import android.graphics.drawable.AnimatedVectorDrawable
import android.os.Bundle
import android.view.View
import android.view.animation.DecelerateInterpolator
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModelProvider
import com.gridee.parking.R
import com.gridee.parking.databinding.ActivityBookingConfirmationBinding
import com.gridee.parking.ui.components.CustomBottomNavigation
import com.gridee.parking.ui.main.MainContainerActivity
import com.gridee.parking.utils.InAppReviewManager
import java.text.SimpleDateFormat
import java.util.*

class BookingConfirmationActivity : AppCompatActivity() {

    private lateinit var binding: ActivityBookingConfirmationBinding
    private lateinit var viewModel: BookingConfirmationViewModel
    private var feeNotificationShown = false
    private var reviewRequestScheduled = false
    private var entranceAnimationEndMs = 0L
    // True when this activity was launched mid-way through the confirm-button
    // success morph — the reveal overlay carries the success colour across
    // the activity boundary so the user reads it as one motion.
    private var startedFromSuccessReveal = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityBookingConfirmationBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Resolve the reveal flag BEFORE the first frame so the overlay is
        // already covering the activity by the time it becomes visible.
        startedFromSuccessReveal = intent.getBooleanExtra("REVEAL_FROM_SUCCESS", false)
        if (startedFromSuccessReveal) {
            binding.revealOverlay.alpha = 1f
            binding.revealOverlay.visibility = View.VISIBLE
            // Keep the 80dp success check invisible until the overlay fades,
            // otherwise its outline would draw under the green and pop in.
            binding.ivSuccessCheck.alpha = 0f
        }

        viewModel = ViewModelProvider(this)[BookingConfirmationViewModel::class.java]

        getBookingDataFromIntent()
        setupUI()
        setupClickListeners()
        setupObservers()
    }

    private fun getBookingDataFromIntent() {
        val bookingId = intent.getStringExtra("BOOKING_ID").orEmpty()
        val transactionId = intent.getStringExtra("TRANSACTION_ID").orEmpty()
        val parkingName = intent.getStringExtra("PARKING_NAME").orEmpty()
        val parkingAddress = intent.getStringExtra("PARKING_ADDRESS").orEmpty()
        val selectedSpot = intent.getStringExtra("SELECTED_SPOT")
        val vehicleNumber = intent.getStringExtra("VEHICLE_NUMBER")
        val startTime = intent.getLongExtra("START_TIME", System.currentTimeMillis())
        val endTime = intent.getLongExtra("END_TIME", startTime + 60 * 60 * 1000)
        val totalAmount = intent.getDoubleExtra("TOTAL_AMOUNT", 0.0)
        val paymentMethod = intent.getStringExtra("PAYMENT_METHOD") ?: "Wallet"
        val paymentStatus = intent.getStringExtra("PAYMENT_STATUS") ?: "Pending"
        val timestamp = intent.getLongExtra("BOOKING_TIMESTAMP", System.currentTimeMillis())

        viewModel.setBookingDetails(
            BookingConfirmationDetails(
                bookingId = bookingId.ifBlank { "--" },
                transactionId = transactionId,
                parkingSpotName = parkingName.ifBlank { "Parking Location" },
                parkingAddress = parkingAddress.ifBlank { "Not available" },
                selectedSpot = selectedSpot,
                vehicleNumber = vehicleNumber,
                startTime = startTime,
                endTime = endTime,
                totalAmount = totalAmount,
                paymentMethodDisplay = paymentMethod,
                paymentStatus = paymentStatus,
                timestamp = timestamp
            )
        )
    }

    private fun setupUI() {
        binding.tvTitle.text = "Booking Confirmed"

        // Push the header, content, and footer past the system bars. Insets
        // are applied to the chrome (header + frost) and to the scroll's top
        // padding so the hero check sits clear of the notch but the frosted
        // gradient still extends edge-to-edge under it.
        androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener(binding.root) { _, insets ->
            val sysBars = insets.getInsets(androidx.core.view.WindowInsetsCompat.Type.systemBars())
            val density = resources.displayMetrics.density

            (binding.header.layoutParams as androidx.constraintlayout.widget.ConstraintLayout.LayoutParams).also {
                it.topMargin = sysBars.top
                binding.header.layoutParams = it
            }
            (binding.headerFrost.layoutParams as androidx.constraintlayout.widget.ConstraintLayout.LayoutParams).also {
                // Frost extends *under* the status bar — covers the system
                // chrome cleanly when content scrolls behind it.
                it.height = ((96 * density).toInt() + sysBars.top)
                binding.headerFrost.layoutParams = it
            }
            binding.contentScroll.setPadding(
                binding.contentScroll.paddingLeft,
                sysBars.top,
                binding.contentScroll.paddingRight,
                sysBars.bottom,
            )
            (binding.actionButtons.layoutParams as androidx.constraintlayout.widget.ConstraintLayout.LayoutParams).also {
                it.bottomMargin = sysBars.bottom
                binding.actionButtons.layoutParams = it
            }
            insets
        }

        playEntranceAnimation()
    }

    private fun playEntranceAnimation() {
        val emphasized = androidx.core.view.animation.PathInterpolatorCompat
            .create(0.2f, 0f, 0f, 1f)

        // Two entrance paths share the staggered card animation but differ on
        // how the success check + overlay arrive. The reveal path doesn't hold
        // a wall of green — the green *contracts into* the check icon while
        // the cards emerge naturally from the screen edges inward.
        if (startedFromSuccessReveal) {
            playRevealCollapseEntrance(emphasized)
        } else {
            playStandardEntrance()
        }
    }

    // ── Entrance: arriving from the confirm-button success reveal ──
    //
    // The dialog-side reveal handed us a fullscreen green overlay. Instead of
    // fading it out as a separate beat, we run a circular reveal in REVERSE on
    // the overlay — shrinking it from fullscreen down to the 80dp success
    // check icon's position. As the green pulls in, the screen edges (which
    // sit furthest from the focal point) are exposed first, so the content
    // cards animate in *under* the contracting green rather than after it.
    // To the user it reads as: button → grows up → contracts into the check
    // icon at the top of the next screen. One continuous motion, no wall.
    private fun playRevealCollapseEntrance(interpolator: android.view.animation.Interpolator) {
        val overlay = binding.revealOverlay
        val check = binding.ivSuccessCheck

        // The check icon needs at least one layout pass before we can resolve
        // its centre; post on the overlay so we run after measure/layout.
        overlay.post {
            if (!overlay.isAttachedToWindow) return@post

            val checkLoc = IntArray(2).also { check.getLocationOnScreen(it) }
            val overlayLoc = IntArray(2).also { overlay.getLocationOnScreen(it) }
            val cx = checkLoc[0] - overlayLoc[0] + check.width / 2
            val cy = checkLoc[1] - overlayLoc[1] + check.height / 2

            val w = overlay.width
            val h = overlay.height
            val startRadius = kotlin.math.hypot(
                maxOf(cx, w - cx).toDouble(),
                maxOf(cy, h - cy).toDouble()
            ).toFloat()

            val shrink = android.view.ViewAnimationUtils.createCircularReveal(
                overlay, cx, cy, startRadius, 0f
            ).apply {
                duration = 620L
                this.interpolator = interpolator
            }
            shrink.addListener(object : android.animation.AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: android.animation.Animator) {
                    overlay.visibility = View.GONE
                }
            })

            // The check sits *behind* the overlay — keep it at alpha 1 from
            // the start; it becomes visible naturally as the green contracts
            // around it. Starting the AVD now means its circle stroke draws
            // out and its check stroke lands right as the green closes in.
            check.alpha = 1f
            (check.drawable as? AnimatedVectorDrawable)?.start()

            shrink.start()
        }

        // Run the card stagger in parallel with the shrink so the cards are
        // already mid-fade by the time the green has retracted past them.
        // Shorter base delay + tighter stagger keeps this from feeling stale.
        animateContentEntrance(
            baseDelay = 60L,
            stagger = 55L,
            slideDuration = 380L,
            interpolator = interpolator,
        )
    }

    // ── Entrance: standard launch path (no reveal handoff) ─────────
    private fun playStandardEntrance() {
        val avd = binding.ivSuccessCheck.drawable as? AnimatedVectorDrawable
        avd?.start()

        animateContentEntrance(
            baseDelay = 550L,
            stagger = 80L,
            slideDuration = 400L,
            interpolator = DecelerateInterpolator(1.8f),
        )
    }

    private fun animateContentEntrance(
        baseDelay: Long,
        stagger: Long,
        slideDuration: Long,
        interpolator: android.view.animation.Interpolator,
    ) {
        // Five logical groups: hero title + subtitle, main ticket, supporting
        // reference card, and the footer. Keeps the choreography compact —
        // a long stagger would stretch past the moment the contracting
        // overlay has already settled.
        val animTargets = listOf(
            binding.tvTitle,
            binding.tvSubtitle,
            binding.ticketCard,
            binding.referenceCard,
            binding.actionButtons
        )

        val animatorSet = AnimatorSet()
        val animators = animTargets.mapIndexed { index, view ->
            val delay = baseDelay + (index * stagger)

            val fadeIn = ObjectAnimator.ofFloat(view, View.ALPHA, 0f, 1f).apply {
                duration = slideDuration
                startDelay = delay
                this.interpolator = interpolator
            }

            val slideUp = ObjectAnimator.ofFloat(view, View.TRANSLATION_Y, 24f, 0f).apply {
                duration = slideDuration
                startDelay = delay
                this.interpolator = interpolator
            }

            listOf(fadeIn, slideUp)
        }.flatten()

        animatorSet.playTogether(animators)
        animatorSet.start()

        // Store when the last element finishes so other UI (e.g. notifications) can wait
        val lastElementDelay = baseDelay + ((animTargets.size - 1) * stagger)
        entranceAnimationEndMs = lastElementDelay + slideDuration
    }

    private fun setupClickListeners() {
        binding.btnBack.setOnClickListener {
            navigateToBookings(showPending = true)
        }

        binding.btnDone.setOnClickListener {
            navigateToBookings(showPending = true)
        }

        binding.btnViewBooking.setOnClickListener {
            val bookingId = viewModel.bookingDetails.value?.bookingId
                ?.takeIf { it.isNotBlank() && it != "--" }
            navigateToBookings(showPending = true, bookingId = bookingId)
        }

        binding.btnShareReceipt.setOnClickListener {
            shareBookingReceipt()
        }
    }

    private fun setupObservers() {
        viewModel.bookingDetails.observe(this) { details ->
            updateBookingDisplay(details)
        }

        viewModel.isLoading.observe(this) { isLoading ->
            // Handle loading state if needed
        }
    }

    private fun updateBookingDisplay(details: BookingConfirmationDetails) {
        // Update confirmation details
        binding.tvBookingId.text = "Booking ID: ${details.bookingId}"
        if (details.transactionId.isNotBlank()) {
            binding.tvTransactionId.visibility = View.VISIBLE
            binding.tvTransactionId.text = "Transaction ID: ${details.transactionId}"
        } else {
            binding.tvTransactionId.visibility = View.GONE
        }
        
        // Update parking details
        binding.tvParkingSpotName.text = details.parkingSpotName
        if (details.parkingAddress.isBlank() || details.parkingAddress.equals("Not available", true)) {
            binding.tvParkingAddress.visibility = View.GONE
        } else {
            binding.tvParkingAddress.visibility = View.VISIBLE
            binding.tvParkingAddress.text = details.parkingAddress
        }
        binding.tvSelectedSpot.text = details.selectedSpot ?: "Any available spot"
        if (!details.vehicleNumber.isNullOrBlank()) {
            binding.rowVehicle.visibility = View.VISIBLE
            binding.tvVehicleNumber.text = details.vehicleNumber
        } else {
            binding.rowVehicle.visibility = View.GONE
        }
        
        // Update time details
        val dateFormat = SimpleDateFormat("MMM dd, yyyy", Locale.getDefault())
        val timeFormat = SimpleDateFormat("hh:mm a", Locale.getDefault())

        if (details.startTime > 0) {
            val startDate = Date(details.startTime)
            binding.tvParkingDate.text = dateFormat.format(startDate)
            binding.tvStartTime.text = timeFormat.format(startDate)
        } else {
            binding.tvParkingDate.text = "--"
            binding.tvStartTime.text = "--"
        }

        if (details.endTime > 0) {
            binding.tvEndTime.text = timeFormat.format(Date(details.endTime))
        } else {
            binding.tvEndTime.text = "--"
        }
        
        // Calculate and display duration
        val durationMillis = (details.endTime - details.startTime).coerceAtLeast(0)
        val durationHours = durationMillis / (1000 * 60 * 60)
        val durationMinutes = (durationMillis % (1000 * 60 * 60)) / (1000 * 60)

        if (durationHours > 0) {
            binding.tvDuration.text = "${durationHours}h ${durationMinutes}m"
        } else {
            binding.tvDuration.text = "${durationMinutes}m"
        }
        
        // Update payment details
        binding.tvTotalAmount.text = String.format(Locale.getDefault(), "%.2f", details.totalAmount)
        binding.tvPaymentMethod.text = details.paymentMethodDisplay
        
        var statusDisplay = details.paymentStatus.ifBlank { "Pending" }
            .replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() }

        // Any booking reaching this screen via Wallet implies a successful deduction.
        if (details.paymentMethodDisplay.contains("Wallet", ignoreCase = true)) {
            // Unify all valid creation states to "Success" for the receipt UI
            if (statusDisplay.equals("Paid", true) || 
                statusDisplay.equals("Confirmed", true) ||
                statusDisplay.equals("Pending", true) ||
                statusDisplay.equals("Active", true)) {
                statusDisplay = "Success"
            }
            
            if (!feeNotificationShown) {
                feeNotificationShown = true
                // Show notification after the entrance animation sequence completes
                val notificationDelay = entranceAnimationEndMs + 300L
                binding.actionButtons.postDelayed({
                    val notificationAnchor = binding.notificationAnchor
                    com.gridee.parking.utils.NotificationHelper.showWalletTransaction(
                        parent = notificationAnchor,
                        title = "Booking Fee",
                        amountText = String.format(java.util.Locale.getDefault(), "%.2f", details.totalAmount),
                        isCredit = false,
                        duration = 5000L,
                        onClick = {
                            startActivity(android.content.Intent(this, com.gridee.parking.ui.activities.TransactionHistoryActivity::class.java))
                        }
                    )
                }, notificationDelay)
            }

            scheduleInAppReviewRequest()
        }

        binding.tvPaymentStatus.text = statusDisplay
        val statusColor = if (statusDisplay.equals("Paid", true) ||
            statusDisplay.equals("Confirmed", true) ||
            statusDisplay.equals("Success", true)
        ) {
            ContextCompat.getColor(this, R.color.success_green)
        } else {
            ContextCompat.getColor(this, R.color.brand_primary)
        }
        binding.tvPaymentStatus.setTextColor(statusColor)
        
        // Update timestamp
        if (details.timestamp > 0) {
            val timestampFormat = SimpleDateFormat("MMM dd, yyyy 'at' hh:mm a", Locale.getDefault())
            binding.tvBookingTime.visibility = View.VISIBLE
            binding.tvBookingTime.text = "Booked on ${timestampFormat.format(Date(details.timestamp))}"
        } else {
            binding.tvBookingTime.visibility = View.GONE
        }
    }

    private fun scheduleInAppReviewRequest() {
        if (reviewRequestScheduled) return
        reviewRequestScheduled = true

        val reviewDelay = entranceAnimationEndMs + 6_000L
        binding.actionButtons.postDelayed({
            if (!isFinishing && !isDestroyed) {
                InAppReviewManager.onBookingConfirmed(this)
            }
        }, reviewDelay)
    }

    private fun shareBookingReceipt() {
        val details = viewModel.bookingDetails.value ?: return
        
        val statusDisplay = details.paymentStatus.ifBlank { "Pending" }
            .replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() }

        val shareText = buildString {
            appendLine("🅿️ Parking Booking Confirmed")
            appendLine()
            appendLine("Booking ID: ${details.bookingId}")
            appendLine("Location: ${details.parkingSpotName}")
            appendLine("Address: ${details.parkingAddress}")
            if (!details.vehicleNumber.isNullOrBlank()) {
                appendLine("Vehicle: ${details.vehicleNumber}")
            }
            appendLine()
            val dateFormat = SimpleDateFormat("MMM dd, yyyy 'at' hh:mm a", Locale.getDefault())
            appendLine("Start: ${dateFormat.format(Date(details.startTime))}")
            appendLine("End: ${dateFormat.format(Date(details.endTime))}")
            appendLine()
            appendLine("Amount: ${String.format(Locale.getDefault(), "%.2f", details.totalAmount)}")
            appendLine("Payment: ${details.paymentMethodDisplay}")
            appendLine("Status: $statusDisplay")
            appendLine()
            appendLine("Powered by Gridee Parking")
        }
        
        val shareIntent = Intent().apply {
            action = Intent.ACTION_SEND
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, shareText)
            putExtra(Intent.EXTRA_SUBJECT, "Parking Booking Receipt")
        }
        
        startActivity(Intent.createChooser(shareIntent, "Share booking receipt"))
    }

    private fun navigateToBookings(showPending: Boolean, bookingId: String? = null) {
        val intent = Intent(this, MainContainerActivity::class.java).apply {
            putExtra(MainContainerActivity.EXTRA_TARGET_TAB, CustomBottomNavigation.TAB_BOOKINGS)
            putExtra(MainContainerActivity.EXTRA_SHOW_PENDING, showPending)
            bookingId?.let { putExtra(MainContainerActivity.EXTRA_HIGHLIGHT_BOOKING_ID, it) }
            addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        }
        startActivity(intent)
        finish()
    }
}
