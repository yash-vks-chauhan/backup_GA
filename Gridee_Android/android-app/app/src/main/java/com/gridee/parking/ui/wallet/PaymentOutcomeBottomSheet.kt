package com.gridee.parking.ui.wallet

import android.app.Dialog
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Bundle
import android.os.SystemClock
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.PathInterpolator
import android.widget.FrameLayout
import androidx.core.view.isVisible
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.gridee.parking.R
import com.gridee.parking.databinding.BottomSheetPaymentOutcomeBinding
import com.gridee.parking.ui.views.PaymentStatusGlyphView
import com.gridee.parking.ui.views.PaymentTrailView
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.DecimalFormat

/**
 * Explains how a top-up ended, in place of a toast.
 *
 * A toast is the wrong surface for money: it vanishes before it is read and cannot answer the
 * one question the user actually has — *was I charged?* This sheet answers that first, with the
 * [PaymentTrailView] showing where the money physically stopped, and only then offers actions.
 *
 * The pending case is the interesting one. Rather than telling the user to come back later, the
 * sheet re-checks the order against the backend on demand and rewrites itself if the payment
 * has since settled, so a slow UPI confirmation resolves without leaving the screen.
 */
class PaymentOutcomeBottomSheet : BottomSheetDialogFragment() {

    /** What the backend concluded about the order. */
    enum class Outcome { CANCELLED, PENDING, UNCONFIRMED, PAID }

    private var _binding: BottomSheetPaymentOutcomeBinding? = null
    private val binding get() = _binding!!

    private var outcome: Outcome = Outcome.CANCELLED
    private var amount: Double = 0.0
    private var orderId: String = ""

    /** Re-queries the order. Supplied by the host so this sheet owns no networking. */
    var statusChecker: (suspend (String) -> PaymentStatusCoordinator.Verification)? = null

    /** User wants to start a fresh checkout for the same amount. */
    var onRetry: ((Double) -> Unit)? = null

    /** Sheet closed. [settled] is true when the payment turned out to be paid after all. */
    var onFinished: ((settled: Boolean) -> Unit)? = null

    private var settled = false
    private var checking = false
    private var retryRequested = false
    private var finishDelivered = false
    private var manualCooldownJob: Job? = null
    private var lastManualCheckAtElapsedMs = NO_MANUAL_CHECK
    private val resetCopyHintRunnable = Runnable {
        _binding?.let { currentBinding ->
            currentBinding.tvCopyHint.text = currentBinding.root.context.getString(
                R.string.payment_copy_reference,
            )
        }
    }

    /** Allows the host to restore the latest backend outcome after activity recreation. */
    fun renderOutcome(newOutcome: Outcome) {
        outcome = newOutcome
        if (newOutcome == Outcome.PAID) settled = true
        if (_binding != null) applyOutcome(newOutcome, animateIn = false)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setStyle(STYLE_NORMAL, R.style.BottomSheetDialogTheme)

        arguments?.let { args ->
            outcome = parseOutcome(args.getString(ARG_OUTCOME))
            amount = args.getDouble(ARG_AMOUNT, 0.0)
            orderId = args.getString(ARG_ORDER_ID).orEmpty()
        }
        savedInstanceState?.let { state ->
            outcome = parseOutcome(state.getString(STATE_OUTCOME), fallback = outcome)
            settled = state.getBoolean(STATE_SETTLED, outcome == Outcome.PAID)
            retryRequested = state.getBoolean(STATE_RETRY_REQUESTED, false)
            finishDelivered = state.getBoolean(STATE_FINISH_DELIVERED, false)
            lastManualCheckAtElapsedMs = state.getLong(
                STATE_LAST_MANUAL_CHECK_AT,
                NO_MANUAL_CHECK,
            )
        } ?: run {
            settled = outcome == Outcome.PAID
            lastManualCheckAtElapsedMs = NO_MANUAL_CHECK
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putString(STATE_OUTCOME, outcome.name)
        outState.putBoolean(STATE_SETTLED, settled)
        outState.putBoolean(STATE_RETRY_REQUESTED, retryRequested)
        outState.putBoolean(STATE_FINISH_DELIVERED, finishDelivered)
        outState.putLong(STATE_LAST_MANUAL_CHECK_AT, lastManualCheckAtElapsedMs)
        super.onSaveInstanceState(outState)
    }

    private fun parseOutcome(value: String?, fallback: Outcome = Outcome.CANCELLED): Outcome =
        runCatching { Outcome.valueOf(value.orEmpty()) }.getOrDefault(fallback)

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        return (super.onCreateDialog(savedInstanceState) as BottomSheetDialog).apply {
            setOnShowListener { dialogInterface ->
                val dialog = dialogInterface as BottomSheetDialog
                dialog.findViewById<FrameLayout>(com.google.android.material.R.id.design_bottom_sheet)
                    ?.let { sheet ->
                        sheet.setBackgroundResource(R.drawable.bg_bottom_sheet_universal)
                        sheet.fitsSystemWindows = false
                        (sheet.layoutParams as? ViewGroup.MarginLayoutParams)?.let { params ->
                            params.setMargins(0, 0, 0, 0)
                            sheet.layoutParams = params
                        }
                        BottomSheetBehavior.from(sheet).apply {
                            state = BottomSheetBehavior.STATE_EXPANDED
                            skipCollapsed = true
                            isDraggable = true
                            isHideable = true
                            isGestureInsetBottomIgnored = true
                        }
                    }
            }
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = BottomSheetPaymentOutcomeBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // An in-flight checker belongs to the old viewLifecycleOwner and is cancelled with it.
        // Restore the timestamp-backed cooldown instead of leaving the new button enabled early.
        checking = false
        applyOutcome(outcome, animateIn = true)

        binding.rowReference.setOnClickListener { copyReference() }

        binding.btnCheckStatus.setOnClickListener { recheckStatus() }

        binding.btnSecondary.setOnClickListener { dismissIfStateCanBeSaved() }
    }

    override fun onViewStateRestored(savedInstanceState: Bundle?) {
        super.onViewStateRestored(savedInstanceState)
        // View hierarchy restoration happens after onViewCreated and can restore the old enabled
        // state/spinner. Reassert the Fragment's authoritative saved outcome and cooldown last.
        checking = false
        applyOutcome(outcome, animateIn = false)
        binding.pbChecking.isVisible = false
        if (binding.btnCheckStatus.isVisible) {
            binding.btnCheckStatus.text = getString(R.string.payment_check_again)
        }
        startManualCooldown(manualCooldownRemainingMs())
    }

    /** Rewrites every part of the sheet for [outcome]. Called again when a re-check changes it. */
    private fun applyOutcome(outcome: Outcome, animateIn: Boolean) {
        this.outcome = outcome
        val context = requireContext()

        when (outcome) {
            Outcome.CANCELLED -> {
                binding.glyphStatus.render(PaymentStatusGlyphView.State.CANCELLED)
                binding.trailPayment.render(PaymentTrailView.Stage.AT_BANK)
                binding.tvTitle.text = getString(R.string.payment_outcome_cancelled_title)
                binding.tvMessage.text = getString(R.string.payment_outcome_cancelled_message)
                binding.tvReassurance.text = getString(R.string.payment_outcome_not_charged)
                binding.btnCheckStatus.isVisible = false
                binding.btnPrimary.text = getString(R.string.payment_try_again)
                binding.btnSecondary.isVisible = true
                binding.btnSecondary.text = getString(R.string.payment_back_to_wallet)
            }

            Outcome.PENDING -> {
                binding.glyphStatus.render(PaymentStatusGlyphView.State.PENDING)
                binding.trailPayment.render(PaymentTrailView.Stage.IN_TRANSIT)
                binding.tvTitle.text = getString(R.string.payment_outcome_pending_title)
                binding.tvMessage.text = getString(R.string.payment_outcome_pending_message)
                binding.tvReassurance.text = getString(R.string.payment_outcome_pending_reassurance)
                binding.btnCheckStatus.isVisible = true
                binding.btnPrimary.text = getString(R.string.payment_back_to_wallet)
                // "Check status" is already the second action; a third button that also just
                // closes would only make the user stop and choose between identical outcomes.
                binding.btnSecondary.isVisible = false
            }

            Outcome.UNCONFIRMED -> {
                binding.glyphStatus.render(PaymentStatusGlyphView.State.UNCONFIRMED)
                binding.trailPayment.render(PaymentTrailView.Stage.IN_TRANSIT)
                binding.tvTitle.text = getString(R.string.payment_outcome_unconfirmed_title)
                binding.tvMessage.text = getString(R.string.payment_outcome_unconfirmed_message)
                binding.tvReassurance.text = getString(R.string.payment_outcome_unconfirmed_reassurance)
                binding.btnCheckStatus.isVisible = true
                binding.btnPrimary.text = getString(R.string.payment_try_again)
                binding.btnSecondary.isVisible = true
                binding.btnSecondary.text = getString(R.string.payment_back_to_wallet)
            }

            Outcome.PAID -> {
                settled = true
                binding.glyphStatus.render(PaymentStatusGlyphView.State.PAID)
                binding.trailPayment.render(PaymentTrailView.Stage.IN_WALLET)
                binding.tvTitle.text = getString(R.string.payment_outcome_paid_title)
                binding.tvMessage.text = getString(
                    R.string.payment_outcome_paid_message,
                    amountFormatter.format(amount)
                )
                binding.tvReassurance.text = getString(R.string.payment_outcome_paid_reassurance)
                binding.btnCheckStatus.isVisible = false
                binding.btnPrimary.text = getString(R.string.payment_back_to_wallet)
                binding.btnSecondary.isVisible = false
            }
        }

        binding.tvAmountLabel.text = context.getString(
            R.string.payment_amount_and_reference,
            amountFormatter.format(amount),
            shortReference()
        )

        binding.btnPrimary.setOnClickListener {
            when (outcome) {
                // "Try again" restarts checkout for the same amount — the host owns that.
                Outcome.CANCELLED, Outcome.UNCONFIRMED -> {
                    if (retryRequested) return@setOnClickListener
                    val retry = onRetry ?: return@setOnClickListener
                    // Dismissing normally tells the host to finish. A retry must keep that host
                    // alive long enough for its lifecycle coroutine to create and open the next
                    // Cashfree order.
                    retryRequested = true
                    retry(amount)
                    dismissIfStateCanBeSaved()
                }

                Outcome.PENDING, Outcome.PAID -> dismissIfStateCanBeSaved()
            }
        }

        if (animateIn) playEntrance()
    }

    /**
     * Staggered rise-in, matching the reveal used elsewhere in the app. Explicitly not a
     * crossfade: content that fades in reads as uncertain, which is the opposite of the
     * reassurance this sheet exists to give.
     */
    private fun playEntrance() {
        val rise = 14f * resources.displayMetrics.density
        val targets = listOf(
            binding.tvTitle,
            binding.tvMessage,
            binding.cardTrail,
            binding.btnCheckStatus,
            binding.btnPrimary,
            binding.btnSecondary
        )

        targets.forEachIndexed { index, view ->
            if (!view.isVisible) return@forEachIndexed
            view.alpha = 0f
            view.translationY = rise
            view.animate()
                .alpha(1f)
                .translationY(0f)
                .setStartDelay(60L + index * 45L)
                .setDuration(360L)
                .setInterpolator(PathInterpolator(0.05f, 0.7f, 0.1f, 1f))
                .start()
        }
    }

    /** Asks the backend again, and rewrites the sheet if the answer has changed. */
    private fun recheckStatus() {
        val checker = statusChecker ?: return
        if (checking || orderId.isBlank()) return

        val cooldownRemainingMs = manualCooldownRemainingMs()
        if (cooldownRemainingMs > 0L) {
            startManualCooldown(cooldownRemainingMs)
            nudge(binding.btnCheckStatus)
            return
        }

        checking = true
        lastManualCheckAtElapsedMs = SystemClock.elapsedRealtime()
        binding.btnCheckStatus.text = ""
        binding.btnCheckStatus.isEnabled = false
        binding.pbChecking.isVisible = true

        viewLifecycleOwner.lifecycleScope.launch {
            // This awaits the exact same per-order coordinator used by the SDK callback and
            // activity resume. It never starts an independent polling loop.
            val verification = runCatching { checker(orderId) }.getOrNull()

            if (_binding == null) return@launch

            checking = false
            binding.pbChecking.isVisible = false
            binding.btnCheckStatus.text = getString(R.string.payment_check_again)

            when (verification?.outcome) {
                PaymentStatusCoordinator.Outcome.PAID ->
                    applyOutcome(Outcome.PAID, animateIn = true)

                PaymentStatusCoordinator.Outcome.PENDING_OR_UNKNOWN -> {
                    // The coordinator has exhausted its four-check budget. Keep the final
                    // processing copy visible and never start another automatic poll.
                    binding.tvMessage.text = getString(R.string.payment_outcome_pending_message)
                    nudge(binding.cardTrail)
                }

                PaymentStatusCoordinator.Outcome.TERMINAL_UNPAID ->
                    applyOutcome(Outcome.UNCONFIRMED, animateIn = true)

                null -> {
                    binding.tvMessage.text = getString(R.string.payment_check_failed)
                    nudge(binding.cardTrail)
                }
            }

            if (verification?.outcome != PaymentStatusCoordinator.Outcome.PAID) {
                startManualCooldown(manualCooldownRemainingMs())
            }
        }
    }

    private fun manualCooldownRemainingMs(): Long {
        if (lastManualCheckAtElapsedMs == NO_MANUAL_CHECK) return 0L
        val elapsedMs = (SystemClock.elapsedRealtime() - lastManualCheckAtElapsedMs)
            .coerceAtLeast(0L)
        return (MANUAL_CHECK_COOLDOWN_MS - elapsedMs)
            .coerceIn(0L, MANUAL_CHECK_COOLDOWN_MS)
    }

    private fun startManualCooldown(remainingMs: Long) {
        manualCooldownJob?.cancel()
        if (_binding == null || !binding.btnCheckStatus.isVisible || remainingMs <= 0L) {
            if (_binding != null && binding.btnCheckStatus.isVisible) {
                binding.btnCheckStatus.isEnabled = true
            }
            return
        }

        binding.btnCheckStatus.isEnabled = false
        manualCooldownJob = viewLifecycleOwner.lifecycleScope.launch {
            delay(remainingMs)
            if (_binding != null && binding.btnCheckStatus.isVisible && !checking) {
                binding.btnCheckStatus.isEnabled = true
            }
        }
    }

    /** Small settle so a re-check that changed nothing still feels acknowledged. */
    private fun nudge(view: View) {
        view.animate()
            .scaleX(1.015f)
            .scaleY(1.015f)
            .setDuration(120L)
            .withEndAction {
                view.animate().scaleX(1f).scaleY(1f).setDuration(180L).start()
            }
            .start()
    }

    private fun copyReference() {
        if (orderId.isBlank()) return
        val clipboard = requireContext().getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
            ?: return
        clipboard.setPrimaryClip(ClipData.newPlainText("Gridee payment reference", orderId))

        binding.tvCopyHint.text = getString(R.string.payment_reference_copied)
        binding.tvCopyHint.removeCallbacks(resetCopyHintRunnable)
        binding.tvCopyHint.postDelayed(resetCopyHintRunnable, 1_800L)
    }

    /** Last 10 characters — enough for support to find the order, short enough to read out. */
    private fun shortReference(): String =
        if (orderId.length <= REFERENCE_LENGTH) orderId else orderId.takeLast(REFERENCE_LENGTH)

    private fun dismissIfStateCanBeSaved() {
        val fragmentManager = runCatching { parentFragmentManager }.getOrNull() ?: return
        if (!isAdded || !lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED) ||
            fragmentManager.isDestroyed || fragmentManager.isStateSaved
        ) return
        dismiss()
    }

    override fun onDismiss(dialog: android.content.DialogInterface) {
        super.onDismiss(dialog)
        if (retryRequested || finishDelivered) return

        // DialogFragment tears down its Dialog during Activity recreation too. That is not a
        // user dismissal: invoking the transient callback would finish or navigate from the old
        // Activity just as Android is restoring the same sheet into the replacement instance.
        val host = activity ?: return
        val managerStateSaved = runCatching { parentFragmentManager.isStateSaved }
            .getOrDefault(true)
        if (host.isChangingConfigurations || host.isFinishing || host.isDestroyed ||
            managerStateSaved
        ) return

        val finish = onFinished ?: return
        finishDelivered = true
        finish(settled)
    }

    override fun onDestroyView() {
        manualCooldownJob?.cancel()
        manualCooldownJob = null
        checking = false
        _binding?.let { currentBinding ->
            currentBinding.tvCopyHint.removeCallbacks(resetCopyHintRunnable)
            cancelViewPropertyAnimations(currentBinding.root)
        }
        super.onDestroyView()
        _binding = null
    }

    private fun cancelViewPropertyAnimations(view: View) {
        view.animate()
            .setListener(null)
            .withStartAction(null)
            .withEndAction(null)
            .cancel()
        if (view is ViewGroup) {
            for (index in 0 until view.childCount) {
                cancelViewPropertyAnimations(view.getChildAt(index))
            }
        }
    }

    companion object {
        const val TAG = "PaymentOutcomeBottomSheet"
        private const val ARG_OUTCOME = "arg_outcome"
        private const val ARG_AMOUNT = "arg_amount"
        private const val ARG_ORDER_ID = "arg_order_id"
        private const val STATE_OUTCOME = "current_outcome"
        private const val STATE_SETTLED = "settled"
        private const val STATE_RETRY_REQUESTED = "retry_requested"
        private const val STATE_FINISH_DELIVERED = "finish_delivered"
        private const val STATE_LAST_MANUAL_CHECK_AT = "last_manual_check_at"
        private const val REFERENCE_LENGTH = 10
        private const val MANUAL_CHECK_COOLDOWN_MS = 20_000L
        private const val NO_MANUAL_CHECK = Long.MIN_VALUE

        private val amountFormatter = DecimalFormat("#,##0")

        fun newInstance(outcome: Outcome, amount: Double, orderId: String) =
            PaymentOutcomeBottomSheet().apply {
                arguments = Bundle().apply {
                    putString(ARG_OUTCOME, outcome.name)
                    putDouble(ARG_AMOUNT, amount)
                    putString(ARG_ORDER_ID, orderId)
                }
            }
    }
}
