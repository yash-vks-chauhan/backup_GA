package com.gridee.parking.ui.wallet

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.util.TypedValue
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.view.animation.OvershootInterpolator
import android.view.animation.PathInterpolator
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.dynamicanimation.animation.DynamicAnimation
import androidx.dynamicanimation.animation.SpringAnimation
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import com.gridee.parking.config.RemoteConfigManager
import com.gridee.parking.data.api.ApiClient
import com.gridee.parking.data.model.PaymentInitiateRequest
import com.gridee.parking.databinding.ActivityWalletAddMoneyBinding
import com.gridee.parking.utils.AuthSession
import kotlinx.coroutines.launch
import java.text.DecimalFormat

class WalletAddMoneyActivity : AppCompatActivity() {

    private lateinit var binding: ActivityWalletAddMoneyBinding
    private var currentBalance = 0.0
    private var isSubmitting = false
    private var isClosing = false
    private val decimalFormat = DecimalFormat("#,##0")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        overridePendingTransition(com.gridee.parking.R.anim.slide_up_entrance, com.gridee.parking.R.anim.scale_down_fade)

        window.apply {
            clearFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS)
            addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS)
            statusBarColor = Color.TRANSPARENT
            navigationBarColor = Color.TRANSPARENT
            setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_HIDDEN)
        }
        WindowCompat.setDecorFitsSystemWindows(window, false)

        binding = ActivityWalletAddMoneyBinding.inflate(layoutInflater)
        setContentView(binding.root)

        RemoteConfigManager.loadCached(this)
        if (!RemoteConfigManager.isWalletEnabled()) {
            Toast.makeText(this, "Wallet top-up is temporarily unavailable.", Toast.LENGTH_LONG).show()
            finish()
            return
        }

        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { v, insets ->
            val systemInsets = insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.ime())
            v.setPadding(systemInsets.left, systemInsets.top, systemInsets.right, systemInsets.bottom)
            insets
        }

        currentBalance = intent.getDoubleExtra(EXTRA_CURRENT_BALANCE, 0.0)

        binding.tvCurrentBalance.text = DecimalFormat("#,##0.00").format(currentBalance)
        setupListeners()
        disableAddMoney()
        
        // Prepare for animation immediately so no flashing occurs
        binding.layoutHeader.alpha = 0f
        binding.layoutNoticeBanner.alpha = 0f
        binding.cvBalancePill.alpha = 0f
        binding.llAmountContainer.alpha = 0f
        binding.layoutActionFooter.alpha = 0f
        
        binding.layoutNoticeBanner.translationY = 40f
        binding.cvBalancePill.translationY = 80f
        binding.llAmountContainer.translationY = 80f
        binding.layoutActionFooter.translationY = 120f
        
        binding.root.post {
            playEntranceAnimation()
        }
    }

    override fun finish() {
        super.finish()
        overridePendingTransition(com.gridee.parking.R.anim.scale_up_fade, com.gridee.parking.R.anim.slide_down_exit)
    }

    private fun playExitAnimation() {
        if (isClosing) return
        isClosing = true

        val pathInterpolator = PathInterpolator(0.3f, 0f, 0.8f, 0.15f)
        val alphaDuration = 150L

        // Parallax breakdown during exit (Micro interaction)
        binding.layoutActionFooter.animate()
            .translationY(60f)
            .alpha(0f)
            .setDuration(alphaDuration)
            .setInterpolator(pathInterpolator)
            .start()

        binding.llAmountContainer.animate()
            .translationY(40f)
            .alpha(0f)
            .setDuration(alphaDuration)
            .setInterpolator(pathInterpolator)
            .setStartDelay(20)
            .start()

        binding.cvBalancePill.animate()
            .translationY(20f)
            .alpha(0f)
            .setDuration(alphaDuration)
            .setInterpolator(pathInterpolator)
            .setStartDelay(40)
            .start()

        binding.layoutNoticeBanner.animate()
            .translationY(10f)
            .alpha(0f)
            .setDuration(alphaDuration)
            .setInterpolator(pathInterpolator)
            .setStartDelay(50)
            .start()

        binding.layoutHeader.animate()
            .alpha(0f)
            .setDuration(alphaDuration)
            .setStartDelay(60)
            .start()

        // Wait a tiny imperceptible frame so internal elements begin dropping first, then slide window
        binding.root.postDelayed({
            finish()
        }, 10)
    }

    private fun setupListeners() {
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                playExitAnimation()
            }
        })

        val touchListener = View.OnTouchListener { v, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    v.animate().scaleX(0.95f).scaleY(0.95f)
                        .setDuration(150)
                        .setInterpolator(PathInterpolator(0.2f, 0f, 0f, 1.0f))
                        .start()
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    SpringAnimation(v, DynamicAnimation.SCALE_X, 1f).apply {
                        spring.dampingRatio = 0.7f
                        spring.stiffness = 300f
                        start()
                    }
                    SpringAnimation(v, DynamicAnimation.SCALE_Y, 1f).apply {
                        spring.dampingRatio = 0.7f
                        spring.stiffness = 300f
                        start()
                    }
                }
            }
            false
        }

        binding.btnBack.setOnTouchListener(touchListener)
        binding.btnBack.setOnClickListener {
            it.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
            onBackPressedDispatcher.onBackPressed()
        }

        binding.etAmount.addTextChangedListener(object : TextWatcher {
            private var current = ""
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit

            override fun afterTextChanged(s: Editable?) {
                if (s.toString() != current) {
                    binding.etAmount.removeTextChangedListener(this)

                    val cleanString = s.toString().replace(Regex("[^0-9]"), "")
                    
                    if (cleanString.isNotEmpty()) {
                        try {
                            val parsed = cleanString.toDouble()
                            val formatted = decimalFormat.format(parsed)

                            current = formatted
                            binding.etAmount.setText(formatted)
                            binding.etAmount.setSelection(formatted.length)
                            
                            binding.etAmount.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)

                            val length = formatted.length
                            val textSize = when {
                                length > 9 -> 32f
                                length > 7 -> 40f
                                length > 5 -> 48f
                                else -> 54f
                            }
                            binding.etAmount.setTextSize(TypedValue.COMPLEX_UNIT_SP, textSize)
                            
                        } catch (e: NumberFormatException) {
                            // ignore potential issues
                        }
                    } else {
                        current = ""
                        binding.etAmount.setTextSize(TypedValue.COMPLEX_UNIT_SP, 54f)
                    }

                    binding.etAmount.addTextChangedListener(this)
                }
                updateAddButtonState()
            }
        })

        binding.btnAddMoneyConfirm.setOnTouchListener(touchListener)
        binding.btnAddMoneyConfirm.setOnClickListener {
            it.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
            val cleanString = binding.etAmount.text?.toString()?.replace(Regex("[^0-9]"), "")
            val amount = cleanString?.toDoubleOrNull()
            if (amount == null || !isAmountAllowed(amount)) {
                // Subtle shake animation for empty/invalid
                SpringAnimation(binding.etAmount, DynamicAnimation.TRANSLATION_X, 0f).apply {
                    setStartValue(40f) // start from right slightly
                    spring.dampingRatio = 0.2f // very bouncy
                    spring.stiffness = 1500f // fast shake
                    start()
                }
                binding.etAmount.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                showToast(topUpRangeMessage())
                return@setOnClickListener
            }
            startRazorpayCheckout(amount)
        }
    }

    private fun disableAddMoney() {
        // Permanently disable the Add Money button
        binding.btnAddMoneyConfirm.isEnabled = false
        binding.btnAddMoneyConfirm.alpha = 0.5f
        binding.btnAddMoneyConfirm.text = "Add Money"

        // Disable the amount input
        binding.etAmount.isEnabled = false
        binding.etAmount.isFocusable = false
    }

    private fun playEntranceAnimation() {
        binding.layoutHeader.alpha = 0f
        binding.layoutNoticeBanner.alpha = 0f
        binding.layoutNoticeBanner.translationY = 40f
        binding.cvBalancePill.alpha = 0f
        binding.cvBalancePill.translationY = 80f
        binding.llAmountContainer.alpha = 0f
        binding.llAmountContainer.translationY = 80f
        binding.layoutActionFooter.alpha = 0f
        binding.layoutActionFooter.translationY = 120f

        val pathInterpolator = PathInterpolator(0.05f, 0.7f, 0.1f, 1.0f)
        val alphaDuration = 350L

        binding.layoutHeader.animate()
            .alpha(1f)
            .setDuration(alphaDuration)
            .setInterpolator(pathInterpolator)
            .start()

        // Notice banner fades in after header
        binding.layoutNoticeBanner.animate()
            .alpha(1f)
            .setDuration(alphaDuration)
            .setInterpolator(pathInterpolator)
            .setStartDelay(30)
            .start()

        binding.layoutNoticeBanner.postDelayed({
            SpringAnimation(binding.layoutNoticeBanner, DynamicAnimation.TRANSLATION_Y, 0f).apply {
                spring.dampingRatio = 0.7f
                spring.stiffness = 300f
                start()
            }
        }, 30)

        binding.cvBalancePill.animate()
            .alpha(1f)
            .setDuration(alphaDuration)
            .setInterpolator(pathInterpolator)
            .setStartDelay(60)
            .start()

        binding.cvBalancePill.postDelayed({
            SpringAnimation(binding.cvBalancePill, DynamicAnimation.TRANSLATION_Y, 0f).apply {
                spring.dampingRatio = 0.7f
                spring.stiffness = 300f
                start()
            }
        }, 60)

        binding.llAmountContainer.animate()
            .alpha(0.35f)
            .setDuration(alphaDuration)
            .setInterpolator(pathInterpolator)
            .setStartDelay(80)
            .start()
            
        binding.llAmountContainer.postDelayed({
            SpringAnimation(binding.llAmountContainer, DynamicAnimation.TRANSLATION_Y, 0f).apply {
                spring.dampingRatio = 0.7f
                spring.stiffness = 300f
                start()
            }
        }, 80)

        binding.layoutActionFooter.animate()
            .alpha(1f)
            .setDuration(alphaDuration)
            .setInterpolator(pathInterpolator)
            .setStartDelay(120)
            .start()
            
        binding.layoutActionFooter.postDelayed({
            SpringAnimation(binding.layoutActionFooter, DynamicAnimation.TRANSLATION_Y, 0f).apply {
                spring.dampingRatio = 0.7f
                spring.stiffness = 300f
                start()
            }
        }, 120)
    }

    private fun updateAddButtonState() {
        val cleanString = binding.etAmount.text?.toString()?.replace(Regex("[^0-9]"), "")
        val amount = cleanString?.toDoubleOrNull()
        val isValidAmount = amount != null && isAmountAllowed(amount)
        
        val targetAlpha = if (isValidAmount && !isSubmitting) 1.0f else 0.5f
        if (binding.btnAddMoneyConfirm.alpha != targetAlpha) {
            binding.btnAddMoneyConfirm.animate()
                .alpha(targetAlpha)
                .setDuration(250)
                .setInterpolator(PathInterpolator(0.2f, 0f, 0f, 1.0f))
                .start()
                
            // Subtle bounce on state change
            if (isValidAmount) {
                SpringAnimation(binding.btnAddMoneyConfirm, DynamicAnimation.SCALE_X, 1f).apply {
                    setStartValue(0.95f)
                    spring.dampingRatio = 0.5f
                    spring.stiffness = 300f
                    start()
                }
                SpringAnimation(binding.btnAddMoneyConfirm, DynamicAnimation.SCALE_Y, 1f).apply {
                    setStartValue(0.95f)
                    spring.dampingRatio = 0.5f
                    spring.stiffness = 300f
                    start()
                }
            }
        }
        
        binding.btnAddMoneyConfirm.isEnabled = isValidAmount && !isSubmitting
        
        if (isSubmitting) {
            binding.btnAddMoneyConfirm.text = ""
            binding.pbLoading.visibility = View.VISIBLE
        } else {
            binding.btnAddMoneyConfirm.text = if (isValidAmount) "Add ${decimalFormat.format(amount)}" else "Add Money"
            binding.pbLoading.visibility = View.GONE
        }
    }

    private fun startRazorpayCheckout(amount: Double) {
        RemoteConfigManager.loadCached(this)
        if (!RemoteConfigManager.isWalletEnabled()) {
            showToast("Wallet top-up is temporarily unavailable.")
            return
        }
        if (!isAmountAllowed(amount)) {
            showToast(topUpRangeMessage())
            return
        }

        val userId = AuthSession.getUserId(this)
        if (userId.isNullOrBlank()) {
            showToast("Please login to add money")
            return
        }

        isSubmitting = true
        updateAddButtonState()

        lifecycleScope.launch {
            try {
                val initResp = ApiClient.apiService.initiatePayment(
                    PaymentInitiateRequest(
                        userId = userId,
                        amount = amount
                    )
                )

                if (!initResp.isSuccessful) {
                    showToast(walletErrorMessage(initResp.code()))
                    return@launch
                }

                val body = initResp.body()
                val orderId = body?.orderId
                val keyId = body?.keyId
                if (orderId.isNullOrBlank()) {
                    showToast("Add money temporarily unavailable during payment integration.")
                    return@launch
                }

                showToast("Redirecting to Razorpay checkout...")
                val intent = Intent(this@WalletAddMoneyActivity, WalletTopUpActivity::class.java).apply {
                    putExtra("USER_ID", userId)
                    putExtra("AMOUNT", amount)
                    putExtra("ORDER_ID", orderId)
                    keyId?.let { putExtra("KEY_ID", it) }
                }
                
                // Allow the toast to show briefly before routing
                kotlinx.coroutines.delay(600)
                startActivity(intent)
                finish()
            } catch (e: Exception) {
                showToast("Add money temporarily unavailable during payment integration.")
            } finally {
                isSubmitting = false
                if (!isFinishing && !isDestroyed) {
                    updateAddButtonState()
                }
            }
        }
    }

    private fun isAmountAllowed(amount: Double): Boolean {
        val financial = RemoteConfigManager.currentConfig.financial
        return amount >= financial.minWalletTopUpAmount && amount <= financial.maxWalletTopUpAmount
    }

    private fun topUpRangeMessage(): String {
        val financial = RemoteConfigManager.currentConfig.financial
        return "Enter an amount between ${decimalFormat.format(financial.minWalletTopUpAmount)} and ${decimalFormat.format(financial.maxWalletTopUpAmount)}."
    }

    private fun walletErrorMessage(code: Int): String {
        return when (code) {
            401 -> "Session expired. Please log in again."
            429 -> "Too many requests. Please wait a moment before trying again."
            503 -> "Wallet top-up is temporarily unavailable."
            else -> "Add money temporarily unavailable during payment integration."
        }
    }

    private fun showToast(message: String) {
        val root = findViewById<android.view.ViewGroup>(android.R.id.content)
        if (root != null) {
            com.gridee.parking.utils.NotificationHelper.showInfo(
                parent = root,
                title = "Wallet",
                message = message,
                duration = 3000L
            )
        } else {
            Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
        }
    }

    companion object {
        const val EXTRA_CURRENT_BALANCE = "EXTRA_CURRENT_BALANCE"
    }
}
