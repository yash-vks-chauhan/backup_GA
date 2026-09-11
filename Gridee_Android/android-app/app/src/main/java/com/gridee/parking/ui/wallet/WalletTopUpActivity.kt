package com.gridee.parking.ui.wallet

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ProgressBar
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AlertDialog
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import com.cashfree.pg.api.CFPaymentGatewayService
import com.cashfree.pg.core.api.CFSession
import com.cashfree.pg.core.api.callback.CFCheckoutResponseCallback
import com.cashfree.pg.core.api.utils.CFErrorResponse
import com.cashfree.pg.core.api.webcheckout.CFWebCheckoutPayment
import com.cashfree.pg.core.api.webcheckout.CFWebCheckoutTheme
import com.gridee.parking.BuildConfig
import com.gridee.parking.R
import com.gridee.parking.config.RemoteConfigManager
import com.gridee.parking.data.repository.WalletRepository
import com.gridee.parking.ui.main.MainContainerActivity
import com.gridee.parking.utils.AuthSession
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Hosts the Cashfree web checkout for a wallet top-up.
 *
 * The order is created by the backend before this screen opens; all this activity receives is
 * the `paymentSessionId` that identifies it. Crucially, the SDK callback is treated only as a
 * signal that checkout finished — never as proof of payment. The wallet balance is decided by
 * `GET /api/payments/status/{orderId}`, and the balance itself is always re-read from the
 * backend by the wallet screen. Nothing here credits the wallet locally.
 */
class WalletTopUpActivity : AppCompatActivity(), CFCheckoutResponseCallback {

    private var userId: String = ""
    private var amount: Double = 0.0
    private var orderId: String = ""
    private var paymentSessionId: String = ""
    private var environment: String = ""
    private var gatewayName: String = ""

    /** Guards against a second doPayment() when the activity is recreated or resumed. */
    private var checkoutLaunched = false
    private var reconciling = false
    private val paymentSuccessHandled = AtomicBoolean(false)
    private var paymentGateway: CFPaymentGatewayService? = null
    private var terminalMessage: String? = null
    private var terminalDialog: AlertDialog? = null

    private lateinit var progress: ProgressBar

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(buildProgressView())

        userId = resolveUserId(intent.getStringExtra(EXTRA_USER_ID).orEmpty())
        amount = intent.getDoubleExtra(EXTRA_AMOUNT, 0.0)
        orderId = intent.getStringExtra(EXTRA_ORDER_ID).orEmpty().trim()
        paymentSessionId = intent.getStringExtra(EXTRA_PAYMENT_SESSION_ID).orEmpty().trim()
        environment = intent.getStringExtra(EXTRA_ENVIRONMENT).orEmpty().trim()
        gatewayName = intent.getStringExtra(EXTRA_GATEWAY).orEmpty().trim()
        checkoutLaunched = savedInstanceState?.getBoolean(STATE_CHECKOUT_LAUNCHED) ?: false
        terminalMessage = savedInstanceState?.getString(STATE_TERMINAL_MESSAGE)

        // A callback failure may have arrived immediately before a configuration change. Restore
        // its actionable message instead of leaving the recreated host on an endless spinner.
        if (terminalMessage != null) return

        RemoteConfigManager.loadCached(this)

        if (!RemoteConfigManager.isWalletEnabled()) {
            failFast(getString(R.string.wallet_top_up_is_temporarily_unavailable))
            return
        }

        if (userId.isBlank() || !isAmountAllowed(amount) || orderId.isBlank()) {
            failFast(getString(R.string.invalid_payment_data))
            return
        }

        if (paymentSessionId.isBlank() ||
            !gatewayName.equals(CASHFREE_GATEWAY, ignoreCase = true) ||
            cfEnvironment() == null
        ) {
            // Without a session id there is no Cashfree order to open. This is a backend/config
            // problem, not a user error, so say so rather than dropping them on a blank screen.
            failFast(getString(R.string.payment_configuration_missing))
            return
        }

        // Registered on every creation, including after process death, so a checkout that
        // outlived this activity still reports back into the reconcile path below.
        paymentGateway = runCatching { CFPaymentGatewayService.getInstance() }.getOrNull()
        if (paymentGateway == null) {
            failFast(getString(R.string.payment_configuration_missing))
            return
        }
        paymentGateway?.setCheckoutCallback(this)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putBoolean(STATE_CHECKOUT_LAUNCHED, checkoutLaunched)
        terminalMessage?.let { outState.putString(STATE_TERMINAL_MESSAGE, it) }
    }

    /**
     * Cashfree may need a fully foreground Activity while it prepares its WebView. Starting it
     * from onCreate made the host's own lifecycle look like an early checkout return on some
     * devices. Launch once from onPostResume, after FragmentManager and the window are resumed.
     */
    override fun onPostResume() {
        super.onPostResume()
        terminalMessage?.let {
            showTerminalMessage(it)
            return
        }
        if (!checkoutLaunched) {
            paymentGateway?.let(::startCheckout)
                ?: failFast(getString(R.string.payment_configuration_missing))
        }
    }

    private fun startCheckout(gateway: CFPaymentGatewayService) {
        val sdkEnvironment = cfEnvironment()
        if (sdkEnvironment == null) {
            failFast(getString(R.string.payment_configuration_missing))
            return
        }

        val session = runCatching {
            CFSession.CFSessionBuilder()
                .setEnvironment(sdkEnvironment)
                .setOrderId(orderId)
                .setPaymentSessionID(paymentSessionId)
                .build()
        }.getOrElse {
            failFast(getString(R.string.payment_configuration_missing))
            return
        }

        val payment = runCatching {
            CFWebCheckoutPayment.CFWebCheckoutPaymentBuilder()
                .setSession(session)
                .setCFWebCheckoutUITheme(checkoutTheme())
                .build()
        }.getOrElse {
            failFast(getString(R.string.payment_configuration_missing))
            return
        }

        checkoutLaunched = true
        runCatching { gateway.doPayment(this, payment) }
            .onFailure {
                checkoutLaunched = false
                failFast(getString(R.string.payment_could_not_be_started))
            }
    }

    /**
     * Checkout finished and Cashfree wants the order verified. The SDK never tells us the
     * amount or whether the wallet moved — only the backend can answer that.
     */
    override fun onPaymentVerify(orderID: String?) {
        reconcile(
            reconcileOrderId = resolveCallbackOrderId(orderID),
            trigger = CompletionTrigger.VERIFY,
        )
    }

    /**
     * Cashfree reported a failure or cancellation. We still reconcile: a payment that actually
     * went through but failed to confirm on the device must not be silently lost.
     */
    override fun onPaymentFailure(error: CFErrorResponse?, orderID: String?) {
        val failureDiagnostic = error.failureDiagnostic()
        reconcile(
            reconcileOrderId = resolveCallbackOrderId(orderID),
            trigger = if (failureDiagnostic.kind == CashfreeCheckoutFailureClassifier.Kind.CANCELLED) {
                CompletionTrigger.CANCELLED
            } else {
                CompletionTrigger.SDK_FAILURE
            },
            failureDiagnostic = failureDiagnostic,
        )
    }

    /**
     * Cashfree reports a user backing out and a genuine payment failure through the same
     * callback, so the code/type is the only way to tell "I changed my mind" apart from
     * "your card was declined" — and they deserve very different screens.
     */
    private fun CFErrorResponse?.failureDiagnostic(): CashfreeCheckoutFailureClassifier.Diagnostic =
        CashfreeCheckoutFailureClassifier.diagnose(
            status = this?.status,
            code = this?.code,
            type = this?.type,
            message = this?.message,
        )

    /** Never query an order id different from the one returned by our own backend initiation. */
    @Suppress("UNUSED_PARAMETER")
    private fun resolveCallbackOrderId(callbackOrderId: String?): String = orderId

    private fun reconcile(
        reconcileOrderId: String,
        trigger: CompletionTrigger,
        failureDiagnostic: CashfreeCheckoutFailureClassifier.Diagnostic =
            CashfreeCheckoutFailureClassifier.Diagnostic(
                kind = CashfreeCheckoutFailureClassifier.Kind.OTHER,
                supportCode = "CF_OTHER",
            ),
    ) {
        // The SDK does not promise which thread it calls back on, so every view touch below
        // and the reconciliation gate itself live inside this main-thread coroutine.
        lifecycleScope.launch {
            if (reconciling) return@launch
            reconciling = true

            try {
                progress.visibility = View.VISIBLE
                val verification = when (trigger) {
                    // Cashfree explicitly requested verification, so allow the bounded settling
                    // window before deciding that confirmation is still pending.
                    CompletionTrigger.VERIFY ->
                        PaymentStatusCoordinator.verify(reconcileOrderId)

                    // A failure/cancel callback must not make the user stare at a 14-second
                    // spinner for an order that never reached checkout. One server check still
                    // protects a payment that settled immediately before the callback.
                    CompletionTrigger.CANCELLED, CompletionTrigger.SDK_FAILURE ->
                        withTimeoutOrNull(FAILURE_SAFETY_CHECK_TIMEOUT_MS) {
                            PaymentStatusCoordinator.checkOnce(reconcileOrderId)
                        } ?: PaymentStatusCoordinator.Verification(
                            outcome = PaymentStatusCoordinator.Outcome.PENDING_OR_UNKNOWN,
                            response = null,
                            attempts = 0,
                        )
                }

                if (verification.outcome == PaymentStatusCoordinator.Outcome.PAID) {
                    // The backend is the only authority allowed to claim that money was added.
                    onTopUpConfirmed()
                } else {
                    finishWith(
                        when (trigger) {
                            CompletionTrigger.CANCELLED ->
                                getString(R.string.payment_cancelled)

                            CompletionTrigger.SDK_FAILURE ->
                                checkoutFailureMessage(failureDiagnostic)

                            CompletionTrigger.VERIFY ->
                                getString(
                                    if (verification.outcome ==
                                        PaymentStatusCoordinator.Outcome.PENDING_OR_UNKNOWN
                                    ) {
                                        R.string.payment_pending_confirmation
                                    } else {
                                        R.string.payment_could_not_be_confirmed
                                    }
                                )
                        }
                    )
                }
            } finally {
                reconciling = false
                if (!isFinishing && !isDestroyed) progress.visibility = View.GONE
            }
        }
    }

    /** Provides a useful remedy without exposing Cashfree's raw callback text. */
    private fun checkoutFailureMessage(
        diagnostic: CashfreeCheckoutFailureClassifier.Diagnostic,
    ): String {
        val message = when (diagnostic.kind) {
            CashfreeCheckoutFailureClassifier.Kind.UNTRUSTED_INSTALLER -> getString(
                if (BuildConfig.DEBUG) {
                    R.string.payment_cashfree_debug_install_blocked
                } else {
                    R.string.payment_cashfree_play_install_required
                }
            )

            CashfreeCheckoutFailureClassifier.Kind.INVALID_SESSION ->
                getString(R.string.payment_cashfree_session_invalid)

            CashfreeCheckoutFailureClassifier.Kind.INVALID_CALLING_CONTEXT ->
                getString(R.string.payment_cashfree_calling_context_invalid)

            CashfreeCheckoutFailureClassifier.Kind.INACTIVE_ORDER ->
                getString(R.string.payment_cashfree_order_inactive)

            CashfreeCheckoutFailureClassifier.Kind.GATEWAY_UNAVAILABLE ->
                getString(R.string.payment_cashfree_gateway_unavailable)

            CashfreeCheckoutFailureClassifier.Kind.CANCELLED ->
                return getString(R.string.payment_cancelled)

            CashfreeCheckoutFailureClassifier.Kind.OTHER ->
                getString(R.string.payment_cashfree_checkout_incomplete)
        }
        return getString(
            R.string.payment_cashfree_error_with_support_code,
            message,
            diagnostic.supportCode,
        )
    }

    /**
     * The backend confirmed the credit. Invalidate once and publish one stable event; the wallet
     * screen then refreshes its balance and transaction list so the completed top-up is visible.
     */
    private fun onTopUpConfirmed() {
        if (!paymentSuccessHandled.compareAndSet(false, true)) return
        val firstProcessConfirmation = WalletRefreshEvents.publish(
            WalletRefreshEvent(
                eventId = "payment:$orderId",
                source = WalletRefreshSource.PAYMENT
            )
        )
        // The stable order event is process-wide while this Activity's atomic flag is only tied
        // to one instance. Invalidate only for the first publication so an Activity recreation or
        // duplicated SDK callback cannot discard the wallet snapshot after its one refresh.
        if (firstProcessConfirmation) {
            userId.trim().takeIf { it.isNotEmpty() }?.let(WalletRepository::invalidateWallet)
        }
        val intent = Intent(this, MainContainerActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra("extra_show_wallet_transaction", true)
            putExtra("extra_wallet_transaction_title", getString(R.string.wallet_top_up_title))
            putExtra(
                "extra_wallet_transaction_amount",
                "+" + String.format(Locale.getDefault(), "%.2f", amount)
            )
            putExtra("extra_wallet_transaction_is_credit", true)
        }
        startActivity(intent)
        finish()
    }

    private fun cfEnvironment(): CFSession.Environment? =
        when (environment.uppercase(Locale.ROOT)) {
            "PRODUCTION" -> CFSession.Environment.PRODUCTION
            "SANDBOX" -> CFSession.Environment.SANDBOX
            else -> null
        }

    private fun checkoutTheme(): CFWebCheckoutTheme =
        CFWebCheckoutTheme.CFWebCheckoutThemeBuilder()
            .setNavigationBarBackgroundColor(HEX_BLACK)
            .setNavigationBarTextColor(HEX_WHITE)
            .build()

    private fun isAmountAllowed(amount: Double): Boolean {
        return amount >= WalletTopUpLauncher.minAmount(this) &&
            amount <= WalletTopUpLauncher.maxAmount(this)
    }

    private fun resolveUserId(candidate: String): String {
        val sessionUserId = AuthSession.getUserId(this)?.trim()?.takeIf { it.isNotEmpty() }
            ?: return ""
        val normalizedCandidate = candidate.trim()
        return sessionUserId
            .takeIf { normalizedCandidate.isEmpty() || normalizedCandidate == sessionUserId }
            .orEmpty()
    }

    private fun failFast(message: String) {
        finishWith(message)
    }

    private fun finishWith(message: String) {
        if (isFinishing || isDestroyed) return
        terminalMessage = message
        progress.visibility = View.GONE
        if (lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
            showTerminalMessage(message)
        }
    }

    /** Keeps checkout failures readable and actionable instead of using a transient system toast. */
    private fun showTerminalMessage(message: String) {
        if (isFinishing || isDestroyed || terminalDialog?.isShowing == true) return
        progress.visibility = View.GONE
        terminalDialog = MaterialAlertDialogBuilder(this)
            .setTitle(R.string.payment_cashfree_dialog_title)
            .setMessage(message)
            .setPositiveButton(R.string.close) { _, _ -> finish() }
            .setOnCancelListener { finish() }
            .show()
    }

    override fun onDestroy() {
        terminalDialog?.dismiss()
        terminalDialog = null
        super.onDestroy()
    }

    /**
     * Checkout runs in the SDK's own activity, so this screen is only ever a backdrop and the
     * brief reconcile wait. A single spinner is all it needs — no layout file to keep in sync.
     */
    private fun buildProgressView(): ViewGroup {
        val container = FrameLayout(this).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            setBackgroundColor(resolveWindowBackground())
        }
        progress = ProgressBar(this).apply {
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.CENTER
            )
            visibility = View.VISIBLE
        }
        container.addView(progress)
        return container
    }

    private fun resolveWindowBackground(): Int {
        val typedValue = TypedValue()
        val resolved = theme.resolveAttribute(android.R.attr.colorBackground, typedValue, true)
        return if (resolved && typedValue.type >= TypedValue.TYPE_FIRST_COLOR_INT &&
            typedValue.type <= TypedValue.TYPE_LAST_COLOR_INT
        ) {
            typedValue.data
        } else {
            Color.TRANSPARENT
        }
    }

    companion object {
        const val EXTRA_USER_ID = "USER_ID"
        const val EXTRA_AMOUNT = "AMOUNT"
        const val EXTRA_ORDER_ID = "ORDER_ID"
        const val EXTRA_PAYMENT_SESSION_ID = "PAYMENT_SESSION_ID"
        const val EXTRA_ENVIRONMENT = "ENVIRONMENT"
        const val EXTRA_GATEWAY = "GATEWAY"

        private const val STATE_CHECKOUT_LAUNCHED = "checkout_launched"
        private const val STATE_TERMINAL_MESSAGE = "terminal_message"
        private const val CASHFREE_GATEWAY = "CASHFREE"
        private const val FAILURE_SAFETY_CHECK_TIMEOUT_MS = 5_000L

        private const val HEX_BLACK = "#000000"
        private const val HEX_WHITE = "#FFFFFF"
    }

    private enum class CompletionTrigger { VERIFY, CANCELLED, SDK_FAILURE }
}
