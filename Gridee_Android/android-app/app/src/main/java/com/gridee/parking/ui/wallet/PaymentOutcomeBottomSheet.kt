package com.gridee.parking.ui.wallet

import android.app.Dialog
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.PathInterpolator
import android.widget.FrameLayout
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.gridee.parking.R
import com.gridee.parking.data.model.PaymentStatusResponse
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
    var statusChecker: (suspend (String) -> PaymentStatusResponse?)? = null

    /** User wants to start a fresh checkout for the same amount. */
    var onRetry: ((Double) -> Unit)? = null

    /** Sheet closed. [settled] is true when the payment turned out to be paid after all. */
    var onFinished: ((settled: Boolean) -> Unit)? = null

    private var settled = false
    private var checking = false
    private var autoPollJob: Job? = null

    /** Allows the host to restore the latest backend outcome after activity recreation. */
    fun renderOutcome(newOutcome: Outcome) {
        outcome = newOutcome
        if (_binding != null) applyOutcome(newOutcome, animateIn = false)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setStyle(STYLE_NORMAL, R.style.BottomSheetDialogTheme)

        arguments?.let { args ->
            outcome = runCatching { Outcome.valueOf(args.getString(ARG_OUTCOME).orEmpty()) }
                .getOrDefault(Outcome.CANCELLED)
            amount = args.getDouble(ARG_AMOUNT, 0.0)
            orderId = args.getString(ARG_ORDER_ID).orEmpty()
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

        applyOutcome(outcome, animateIn = true)

        binding.rowReference.setOnClickListener { copyReference() }

        binding.btnCheckStatus.setOnClickListener { recheckStatus() }

        binding.btnSecondary.setOnClickListener { dismissAllowingStateLoss() }
    }

    /** Rewrites every part of the sheet for [outcome]. Called again when a re-check changes it. */
    private fun applyOutcome(outcome: Outcome, animateIn: Boolean) {
        this.outcome = outcome
        if (outcome != Outcome.PENDING) autoPollJob?.cancel()
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
                    onRetry?.invoke(amount)
                    dismissAllowingStateLoss()
                }

                Outcome.PENDING, Outcome.PAID -> dismissAllowingStateLoss()
            }
        }

        if (animateIn) playEntrance()
        if (outcome == Outcome.PENDING) startAutomaticPendingPolling()
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

        checking = true
        binding.btnCheckStatus.text = ""
        binding.btnCheckStatus.isEnabled = false
        binding.pbChecking.isVisible = true

        viewLifecycleOwner.lifecycleScope.launch {
            val status = runCatching { checker(orderId) }.getOrNull()

            if (_binding == null) return@launch

            checking = false
            binding.pbChecking.isVisible = false
            binding.btnCheckStatus.isEnabled = true
            binding.btnCheckStatus.text = getString(R.string.payment_check_again)

            when {
                status?.isPaid == true -> applyOutcome(Outcome.PAID, animateIn = true)

                status?.isPending == true -> {
                    // Keep the contract copy visible while the bounded background poll continues.
                    binding.tvMessage.text = getString(R.string.payment_outcome_pending_message)
                    nudge(binding.cardTrail)
                }

                status != null -> applyOutcome(Outcome.UNCONFIRMED, animateIn = true)

                else -> {
                    binding.tvMessage.text = getString(R.string.payment_check_failed)
                    nudge(binding.cardTrail)
                }
            }
        }
    }

    /**
     * A backend PENDING response is rechecked automatically after a few seconds. Polling is
     * lifecycle-bound and bounded, so leaving the sheet stops all requests and a long-running
     * payment does not spin forever. The manual Check status action remains available afterward.
     */
    private fun startAutomaticPendingPolling() {
        val checker = statusChecker ?: return
        if (autoPollJob?.isActive == true) return

        autoPollJob = viewLifecycleOwner.lifecycleScope.launch {
            repeat(AUTO_POLL_ATTEMPTS) {
                delay(AUTO_POLL_DELAY_MS)
                if (_binding == null || outcome != Outcome.PENDING || settled) return@launch

                val status = runCatching { checker(orderId) }.getOrNull()
                if (_binding == null || outcome != Outcome.PENDING || settled) return@launch

                when {
                    status?.isPaid == true -> {
                        applyOutcome(Outcome.PAID, animateIn = true)
                        return@launch
                    }

                    status?.isPending == true || status == null -> Unit

                    else -> {
                        applyOutcome(Outcome.UNCONFIRMED, animateIn = true)
                        return@launch
                    }
                }
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
        binding.tvCopyHint.postDelayed({
            _binding?.tvCopyHint?.text = getString(R.string.payment_copy_reference)
        }, 1_800L)
    }

    /** Last 10 characters — enough for support to find the order, short enough to read out. */
    private fun shortReference(): String =
        if (orderId.length <= REFERENCE_LENGTH) orderId else orderId.takeLast(REFERENCE_LENGTH)

    override fun onDismiss(dialog: android.content.DialogInterface) {
        super.onDismiss(dialog)
        onFinished?.invoke(settled)
    }

    override fun onDestroyView() {
        autoPollJob?.cancel()
        autoPollJob = null
        super.onDestroyView()
        _binding = null
    }

    companion object {
        const val TAG = "PaymentOutcomeBottomSheet"
        private const val ARG_OUTCOME = "arg_outcome"
        private const val ARG_AMOUNT = "arg_amount"
        private const val ARG_ORDER_ID = "arg_order_id"
        private const val REFERENCE_LENGTH = 10
        private const val AUTO_POLL_ATTEMPTS = 5
        private const val AUTO_POLL_DELAY_MS = 3_000L

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
