package com.gridee.parking.ui.wallet

import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.gridee.parking.data.api.ApiClient
import com.gridee.parking.utils.AuthSession
import com.razorpay.Checkout
import com.razorpay.PaymentData
import com.razorpay.PaymentResultWithDataListener
import org.json.JSONObject
import kotlinx.coroutines.launch

class WalletTopUpActivity : AppCompatActivity(), PaymentResultWithDataListener {

    private var userId: String = ""
    private var amount: Double = 0.0
    private var orderId: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        userId = intent.getStringExtra("USER_ID") ?: ""
        amount = intent.getDoubleExtra("AMOUNT", 0.0)
        orderId = intent.getStringExtra("ORDER_ID") ?: ""

        userId = resolveUserId(userId)

        if (userId.isBlank() || amount <= 0.0 || orderId.isBlank()) {
            Toast.makeText(this, "Invalid payment data", Toast.LENGTH_LONG).show()
            finish()
            return
        }

        startCheckout()
    }

    private fun startCheckout() {
        try {
            // Preload checkout for faster launch
            Checkout.preload(applicationContext)

            val checkout = Checkout()
            // Optional: set your key id if you want on client; otherwise Razorpay reads from options
            // For explicitness, pass key id from backend config if available (public key)
            // Note: We don't fetch it dynamically here; server validates payment.

            val options = JSONObject()
            options.put("name", "Gridee")
            options.put("description", "Wallet Top-up")
            options.put("currency", "INR")
            options.put("order_id", orderId)
            options.put("amount", (amount * 100).toInt())

            // Optionally set key explicitly if backend included it in the intent
            val keyFromServer = intent.getStringExtra("KEY_ID")?.trim()
            val fallbackKey = getString(com.gridee.parking.R.string.razorpay_key_id).trim()
            val keyToUse = when {
                !keyFromServer.isNullOrBlank() -> keyFromServer
                fallbackKey.startsWith("rzp_") -> fallbackKey
                else -> ""
            }
            if (keyToUse.isBlank()) {
                Toast.makeText(this, "Payment configuration missing", Toast.LENGTH_LONG).show()
                finish()
                return
            }
            try { checkout.setKeyID(keyToUse) } catch (_: Exception) {}
            // Also include key in options for compatibility with older SDKs
            options.put("key", keyToUse)

            // Prefill details (if available)
            val shared = getSharedPreferences("gridee_prefs", MODE_PRIVATE)
            val email = shared.getString("user_email", null)
            val phone = shared.getString("user_phone", null)

            val prefill = JSONObject()
            email?.let { prefill.put("email", it) }
            phone?.let { prefill.put("contact", it) }
            options.put("prefill", prefill)

            checkout.open(this, options)
        } catch (e: Exception) {
            Toast.makeText(this, "Checkout error: ${e.message}", Toast.LENGTH_LONG).show()
            finish()
        }
    }

    override fun onPaymentSuccess(razorpayPaymentId: String?, paymentData: PaymentData?) {
        val paymentId = paymentData?.paymentId ?: (razorpayPaymentId ?: "")
        val orderIdFromCallback = paymentData?.orderId
        val signature = paymentData?.signature
        val resolvedOrderId = if (!orderIdFromCallback.isNullOrBlank()) orderIdFromCallback else orderId
        // Inform backend to credit wallet
        lifecycleScope.launch {
            try {
                val resp = ApiClient.apiService.paymentCallback(
                    com.gridee.parking.data.model.PaymentCallbackRequest(
                        orderId = resolvedOrderId,
                        paymentId = paymentId,
                        signature = signature,
                        razorpaySignature = signature,
                        success = true,
                        userId = userId,
                        amount = amount
                    )
                )
                if (!resp.isSuccessful) {
                    Toast.makeText(this@WalletTopUpActivity, "Payment success, update failed", Toast.LENGTH_LONG).show()
                } else {
                    Toast.makeText(this@WalletTopUpActivity, "Wallet recharged successfully", Toast.LENGTH_LONG).show()
                }
            } catch (e: Exception) {
                Toast.makeText(this@WalletTopUpActivity, "Callback error: ${e.message}", Toast.LENGTH_LONG).show()
            } finally {
                finish()
            }
        }
    }

    override fun onPaymentError(code: Int, response: String?, paymentData: PaymentData?) {
        val paymentId = paymentData?.paymentId ?: ""
        val orderIdFromCallback = paymentData?.orderId
        val signature = paymentData?.signature
        val resolvedOrderId = if (!orderIdFromCallback.isNullOrBlank()) orderIdFromCallback else orderId
        // Inform backend of failed/cancelled payment
        lifecycleScope.launch {
            try {
                val status = when {
                    // Heuristic: Razorpay uses code 2 for user-cancelled; also check message text
                    code == 2 -> "cancelled"
                    response?.contains("cancel", ignoreCase = true) == true -> "cancelled"
                    else -> "failed"
                }
                ApiClient.apiService.paymentCallback(
                    com.gridee.parking.data.model.PaymentCallbackRequest(
                        orderId = resolvedOrderId,
                        paymentId = paymentId,
                        signature = signature,
                        razorpaySignature = signature,
                        success = false,
                        userId = userId,
                        amount = amount,
                        status = status
                    )
                )
            } catch (_: Exception) {
            } finally {
                val msg = if (code == 2 || (response?.contains("cancel", true) == true)) {
                    "Payment cancelled"
                } else {
                    "Payment failed"
                }
                Toast.makeText(this@WalletTopUpActivity, msg, Toast.LENGTH_LONG).show()
                finish()
            }
        }
    }

    private fun resolveUserId(candidate: String): String {
        val sessionUserId = AuthSession.getUserId(this)
        if (!sessionUserId.isNullOrBlank()) {
            if (candidate.isBlank() || candidate != sessionUserId) {
                return sessionUserId
            }
        }
        return candidate
    }
}
