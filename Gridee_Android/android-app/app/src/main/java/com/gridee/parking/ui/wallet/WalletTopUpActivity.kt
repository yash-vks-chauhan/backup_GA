package com.gridee.parking.ui.wallet

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.util.Log
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ProgressBar
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
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
import com.gridee.parking.data.api.ApiClient
import com.gridee.parking.data.model.PaymentStatusResponse
import com.gridee.parking.ui.main.MainContainerActivity
import com.gridee.parking.utils.AuthSession
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Locale

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
    private var parkingLotId: String = ""
    private var organizationId: String? = null
    private var locationId: String? = null

    /** Guards against a second doPayment() when the activity is recreated mid-checkout. */
    private var checkoutLaunched = false
    private var checkoutLeftHost = false
    private var reconciling = false

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
        parkingLotId = intent.getStringExtra(EXTRA_PARKING_LOT_ID).orEmpty().trim()
        organizationId = intent.getStringExtra(EXTRA_ORGANIZATION_ID).normalizedOrNull()
        locationId = intent.getStringExtra(EXTRA_LOCATION_ID).normalizedOrNull()
        checkoutLaunched = savedInstanceState?.getBoolean(STATE_CHECKOUT_LAUNCHED) ?: false
        checkoutLeftHost = savedInstanceState?.getBoolean(STATE_CHECKOUT_LEFT_HOST) ?: false

        RemoteConfigManager.loadCached(this)

        if (!RemoteConfigManager.isWalletEnabled()) {
            failFast(getString(R.string.wallet_top_up_is_temporarily_unavailable))
            return
        }

        if (userId.isBlank() || !isAmountAllowed(amount) || orderId.isBlank() || parkingLotId.isBlank()) {
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
        val gateway = runCatching { CFPaymentGatewayService.getInstance() }.getOrNull()
        if (gateway == null) {
            failFast(getString(R.string.payment_configuration_missing))
            return
        }
        gateway.setCheckoutCallback(this)
        (supportFragmentManager.findFragmentByTag(PaymentOutcomeBottomSheet.TAG)
            as? PaymentOutcomeBottomSheet)?.let(::configureOutcomeSheet)

        if (!checkoutLaunched) {
            startCheckout(gateway)
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putBoolean(STATE_CHECKOUT_LAUNCHED, checkoutLaunched)
        outState.putBoolean(STATE_CHECKOUT_LEFT_HOST, checkoutLeftHost)
    }

    override fun onPause() {
        if (checkoutLaunched) checkoutLeftHost = true
        super.onPause()
    }

    override fun onResume() {
        super.onResume()
        if (!checkoutLeftHost || !checkoutLaunched || reconciling) return

        // Cashfree normally invokes one of its callbacks. This delayed resume check covers an
        // external UPI app returning without a callback and races safely with the normal callback
        // through the reconciling guard.
        lifecycleScope.launch {
            delay(RETURN_STATUS_CHECK_DELAY_MS)
            if (!reconciling && checkoutLeftHost && checkoutLaunched) {
                reconcile(orderId, userCancelled = false)
            }
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
        }.getOrElse { error ->
            Log.w(TAG, "Could not build Cashfree session for order $orderId", error)
            failFast(getString(R.string.payment_configuration_missing))
            return
        }

        val payment = runCatching {
            CFWebCheckoutPayment.CFWebCheckoutPaymentBuilder()
                .setSession(session)
                .setCFWebCheckoutUITheme(checkoutTheme())
                .build()
        }.getOrElse { error ->
            Log.w(TAG, "Could not build Cashfree checkout for order $orderId", error)
            failFast(getString(R.string.payment_configuration_missing))
            return
        }

        if (BuildConfig.DEBUG) {
            // Says out loud whether the backend put us in sandbox or against real money, so a
            // QA top-up can be checked before anyone taps Pay.
            Log.i(TAG, "Opening $sdkEnvironment checkout — order=$orderId amount=$amount")
        }

        checkoutLaunched = true
        runCatching { gateway.doPayment(this, payment) }
            .onFailure { error ->
                Log.w(TAG, "Cashfree checkout failed to open for order $orderId", error)
                checkoutLaunched = false
                failFast(getString(R.string.payment_could_not_be_started))
            }
    }

    /**
     * Checkout finished and Cashfree wants the order verified. The SDK never tells us the
     * amount or whether the wallet moved — only the backend can answer that.
     */
    override fun onPaymentVerify(orderID: String?) {
        reconcile(resolveCallbackOrderId(orderID), userCancelled = false)
    }

    /**
     * Cashfree reported a failure or cancellation. We still reconcile: a payment that actually
     * went through but failed to confirm on the device must not be silently lost.
     */
    override fun onPaymentFailure(error: CFErrorResponse?, orderID: String?) {
        Log.d(TAG, "Checkout reported failure for $orderID: ${error?.code} ${error?.message}")
        reconcile(
            resolveCallbackOrderId(orderID),
            userCancelled = error.isUserCancellation()
        )
    }

    /**
     * Cashfree reports a user backing out and a genuine payment failure through the same
     * callback, so the code/type is the only way to tell "I changed my mind" apart from
     * "your card was declined" — and they deserve very different screens.
     */
    private fun CFErrorResponse?.isUserCancellation(): Boolean {
        if (this == null) return false
        val haystack = listOf(code, type, message)
            .joinToString(" ") { it.orEmpty() }
            .lowercase(Locale.ROOT)
        return CANCELLATION_MARKERS.any { haystack.contains(it) }
    }

    /** Never query an order id different from the one returned by our own backend initiation. */
    private fun resolveCallbackOrderId(callbackOrderId: String?): String {
        val candidate = callbackOrderId.normalizedOrNull()
        if (candidate != null && candidate != orderId) {
            Log.w(TAG, "Ignoring mismatched Cashfree callback order id")
        }
        return orderId
    }

    private fun reconcile(reconcileOrderId: String, userCancelled: Boolean) {
        if (reconciling) return
        reconciling = true

        // The SDK does not promise which thread it calls back on, so every view touch below
        // happens inside the coroutine, which lifecycleScope dispatches to main.
        lifecycleScope.launch {
            progress.visibility = View.VISIBLE
            val status = fetchPaymentStatus(reconcileOrderId)

            progress.visibility = View.GONE

            when {
                // Money moving is the only thing that outranks what the user just did.
                status?.isPaid == true -> onTopUpConfirmed()

                // Backend status is authoritative even if the user just closed the SDK. Some UPI
                // payments remain in flight after checkout returns.
                status?.isPending == true -> showOutcome(PaymentOutcomeBottomSheet.Outcome.PENDING)

                // The user closing checkout is a fact; the order still reading "pending" only
                // matters only when the backend has already returned a terminal non-paid state.
                userCancelled && status != null ->
                    showOutcome(PaymentOutcomeBottomSheet.Outcome.CANCELLED)

                else -> showOutcome(PaymentOutcomeBottomSheet.Outcome.UNCONFIRMED)
            }
        }
    }

    /**
     * Explains the outcome on a sheet instead of a toast.
     *
     * A toast disappears before it can answer "was I charged?", which is the only thing the user
     * wants to know here. The sheet owns the rest of this screen's life: the activity finishes
     * when it closes, so the user never lands back on a blank checkout host.
     */
    private fun showOutcome(outcome: PaymentOutcomeBottomSheet.Outcome) {
        if (isFinishing || isDestroyed) return
        val existing = supportFragmentManager.findFragmentByTag(PaymentOutcomeBottomSheet.TAG)
            as? PaymentOutcomeBottomSheet
        val sheet = existing ?: PaymentOutcomeBottomSheet.newInstance(outcome, amount, orderId)
        configureOutcomeSheet(sheet)
        if (existing == null) {
            sheet.show(supportFragmentManager, PaymentOutcomeBottomSheet.TAG)
        } else {
            sheet.renderOutcome(outcome)
        }
    }

    /** Reattaches non-persistable callbacks when Android recreates the outcome fragment. */
    private fun configureOutcomeSheet(sheet: PaymentOutcomeBottomSheet) {
        // Lets the sheet re-query the order without owning any networking itself.
        sheet.statusChecker = { id ->
            runCatching {
                ApiClient.apiService.getPaymentStatus(id).takeIf { it.isSuccessful }?.body()
            }.getOrNull()
        }

        sheet.onRetry = { retryAmount ->
            retryTopUp(retryAmount)
        }

        sheet.onFinished = { paid ->
            // A re-check that found the payment settled should land the user on the credited
            // wallet, exactly as a first-time success would.
            if (paid) onTopUpConfirmed() else finish()
        }
    }

    /** Starts a fresh order for the same amount, so "Try again" costs one tap. */
    private fun retryTopUp(retryAmount: Double) {
        progress.visibility = View.VISIBLE
        lifecycleScope.launch {
            when (
                val result = WalletTopUpLauncher.createTopUp(
                    this@WalletTopUpActivity,
                    retryAmount,
                    parkingLotId = parkingLotId,
                    organizationId = organizationId,
                    locationId = locationId
                )
            ) {
                is WalletTopUpLauncher.Result.Ready -> {
                    startActivity(result.intent)
                    finish()
                }

                is WalletTopUpLauncher.Result.Failed -> finishWith(result.message)
            }
        }
    }

    /** The status endpoint is the only post-checkout source of truth. */
    private suspend fun fetchPaymentStatus(reconcileOrderId: String): PaymentStatusResponse? =
        runCatching {
            ApiClient.apiService.getPaymentStatus(reconcileOrderId)
                .takeIf { it.isSuccessful }
                ?.body()
        }.getOrNull()

    /**
     * The backend confirmed the credit. We only navigate — the wallet screen re-reads the
     * balance and the transaction list from the backend when it resumes.
     */
    private fun onTopUpConfirmed() {
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

    private fun String?.normalizedOrNull(): String? =
        this?.trim()?.takeIf { it.isNotEmpty() }

    private fun failFast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
        finish()
    }

    private fun finishWith(message: String) {
        if (!isFinishing && !isDestroyed) {
            Toast.makeText(this, message, Toast.LENGTH_LONG).show()
        }
        finish()
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
            visibility = View.GONE
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
        const val EXTRA_PARKING_LOT_ID = "PARKING_LOT_ID"
        const val EXTRA_ORGANIZATION_ID = "ORGANIZATION_ID"
        const val EXTRA_LOCATION_ID = "LOCATION_ID"

        private const val STATE_CHECKOUT_LAUNCHED = "checkout_launched"
        private const val STATE_CHECKOUT_LEFT_HOST = "checkout_left_host"
        private const val TAG = "WalletTopUp"
        private const val CASHFREE_GATEWAY = "CASHFREE"
        private const val RETURN_STATUS_CHECK_DELAY_MS = 750L

        /** Substrings Cashfree uses for a user-initiated exit, e.g. "action_cancelled". */
        private val CANCELLATION_MARKERS = listOf("cancel", "user_dropped", "aborted")

        private const val HEX_BLACK = "#000000"
        private const val HEX_WHITE = "#FFFFFF"
    }
}
