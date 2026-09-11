package com.gridee.parking.ui.wallet

import android.content.Context
import android.content.Intent
import com.gridee.parking.R
import com.gridee.parking.config.RemoteConfigManager
import com.gridee.parking.data.api.ApiClient
import com.gridee.parking.data.model.PaymentInitiateRequest
import com.gridee.parking.utils.AuthSession
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.DecimalFormat
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Single entry point for starting a wallet top-up.
 *
 * Four screens can open a top-up (the wallet tab, the add-money screen, the booking flow and
 * the spot bottom sheet). They all need the same three steps — create the order on the backend,
 * check it is launchable, hand the session to [WalletTopUpActivity] — so the steps live here
 * once rather than drifting apart in four places.
 */
object WalletTopUpLauncher {

    sealed class Result {
        /** Order created; start this intent to open checkout. */
        data class Ready(val intent: Intent) : Result()

        /** Nothing was started; [message] is safe to show to the user. */
        data class Failed(val message: String) : Result()
    }

    /**
     * Creates the Cashfree order for [amount] and returns the intent that opens checkout.
     *
     * Runs the network call on IO; callers can invoke it straight from a UI coroutine.
     */
    suspend fun createTopUp(
        context: Context,
        amount: Double,
        parkingLotId: String? = null,
        organizationId: String? = null,
        locationId: String? = null
    ): Result {
        val appContext = context.applicationContext

        // Check the limits before spending a Cashfree order on an amount the checkout screen
        // would reject anyway. Skipping this left a dead order behind, and the backend records a
        // pending transaction the moment one is created — which is how an amount that was never
        // paid ended up in the user's history.
        amountRejection(appContext, amount)?.let { return Result.Failed(it) }

        // Identity is always read from the authenticated session at order-creation time. Accepting
        // a user id supplied by a screen would allow a stale activity to initiate an order for a
        // user who is no longer logged in.
        val userId = AuthSession.getUserId(appContext)
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
            ?: return Result.Failed(
                appContext.getString(R.string.session_expired_please_log_in_again)
            )

        val sessionParkingLotId = AuthSession.getParkingLotId(appContext).normalizedOrNull()
        val requestedParkingLotId = parkingLotId.normalizedOrNull()
        val resolvedParkingLotId = requestedParkingLotId ?: sessionParkingLotId

        // Organization/location are optional. Reuse the saved tenant only when it belongs to the
        // same lot; otherwise omit it so stale tenant data cannot be paired with a newly selected
        // parking lot. When no local lot exists, let the authenticated backend resolve all three
        // values from the user's authoritative parking context.
        val selectedSessionLot = resolvedParkingLotId != null &&
            resolvedParkingLotId == sessionParkingLotId
        val resolvedOrganizationId = organizationId.normalizedOrNull()
            ?: AuthSession.getOrganizationId(appContext).takeIf { selectedSessionLot }
        val resolvedLocationId = locationId.normalizedOrNull()
            ?: AuthSession.getLocationId(appContext).takeIf { selectedSessionLot }

        // Initiation is a money mutation and cannot be transparently repeated. Reject a second
        // tap while the first POST is unresolved instead of creating two Cashfree orders.
        if (!initiationInFlight.compareAndSet(false, true)) {
            return Result.Failed(
                appContext.getString(R.string.payment_request_already_in_progress)
            )
        }

        return try {
            val response = withContext(Dispatchers.IO) {
                runCatching {
                    ApiClient.apiService.initiatePayment(
                        PaymentInitiateRequest(
                            userId = userId,
                            amount = amount,
                            parkingLotId = resolvedParkingLotId,
                            organizationId = resolvedOrganizationId,
                            locationId = resolvedLocationId
                        )
                    )
                }.getOrNull()
            } ?: return Result.Failed(
                appContext.getString(R.string.payment_could_not_be_started)
            )

            if (!response.isSuccessful) {
                return Result.Failed(errorMessage(appContext, response.code()))
            }

            val body = response.body()
            if (body == null || !body.isLaunchable) {
                // This also rejects an unknown gateway or environment. In particular, a production
                // payment session is never opened by silently defaulting the SDK to sandbox.
                return Result.Failed(
                    appContext.getString(R.string.payment_could_not_be_started)
                )
            }

            val intent = Intent(appContext, WalletTopUpActivity::class.java).apply {
                putExtra(WalletTopUpActivity.EXTRA_USER_ID, userId)
                putExtra(WalletTopUpActivity.EXTRA_AMOUNT, amount)
                putExtra(WalletTopUpActivity.EXTRA_ORDER_ID, body.normalizedOrderId)
                putExtra(WalletTopUpActivity.EXTRA_PAYMENT_SESSION_ID, body.normalizedPaymentSessionId)
                putExtra(WalletTopUpActivity.EXTRA_ENVIRONMENT, body.paymentEnvironment?.name)
                putExtra(WalletTopUpActivity.EXTRA_GATEWAY, body.gateway?.trim()?.uppercase())
            }
            Result.Ready(intent)
        } finally {
            initiationInFlight.set(false)
        }
    }

    /** Backend-aligned minimum for a wallet top-up. */
    fun minAmount(context: Context): Double {
        RemoteConfigManager.loadCached(context.applicationContext)
        return RemoteConfigManager.currentConfig.financial.minWalletTopUpAmount
    }

    fun maxAmount(context: Context): Double {
        RemoteConfigManager.loadCached(context.applicationContext)
        return RemoteConfigManager.currentConfig.financial.maxWalletTopUpAmount
    }

    /** Null when [amount] is acceptable, otherwise the reason to show the user. */
    private fun amountRejection(context: Context, amount: Double): String? {
        val min = minAmount(context)
        val max = maxAmount(context)
        val formatter = DecimalFormat("#,##0")

        return when {
            amount <= 0 -> context.getString(R.string.please_enter_a_valid_amount)
            amount < min -> context.getString(R.string.top_up_below_minimum, formatter.format(min))
            amount > max -> context.getString(R.string.top_up_above_maximum, formatter.format(max))
            else -> null
        }
    }

    private fun errorMessage(context: Context, code: Int): String = when (code) {
        401 -> context.getString(R.string.session_expired_please_log_in_again)
        403 -> context.getString(R.string.wallet_top_up_is_temporarily_unavailable)
        else -> context.getString(R.string.payment_could_not_be_started)
    }

    private fun String?.normalizedOrNull(): String? =
        this?.trim()?.takeIf { it.isNotEmpty() }

    private val initiationInFlight = AtomicBoolean(false)
}
