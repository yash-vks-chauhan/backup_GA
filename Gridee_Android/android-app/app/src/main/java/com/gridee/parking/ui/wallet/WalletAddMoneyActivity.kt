package com.gridee.parking.ui.wallet

import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.text.SpannableString
import android.text.Spanned
import android.text.TextPaint
import android.text.style.ImageSpan
import android.util.TypedValue
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.view.animation.PathInterpolator
import android.view.inputmethod.EditorInfo
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.os.ConfigurationCompat
import androidx.core.view.ViewCompat
import androidx.core.view.AccessibilityDelegateCompat
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsAnimationCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.doOnLayout
import androidx.lifecycle.lifecycleScope
import com.gridee.parking.R
import com.gridee.parking.config.RemoteConfigManager
import com.gridee.parking.databinding.ActivityWalletAddMoneyBinding
import com.gridee.parking.ui.motion.AnimatorSettingsCompat
import com.gridee.parking.utils.AuthSession
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import java.text.NumberFormat
import java.util.Locale
import kotlin.math.ceil
import kotlin.math.floor

class WalletAddMoneyActivity : AppCompatActivity() {
    private lateinit var binding: ActivityWalletAddMoneyBinding
    private lateinit var amountFormatter: WalletAmountFormatter
    private var parkingLotId: String? = null
    private var isSubmitting = false
    private var checkoutActive = false
    private var minAmount = 10.0
    private var maxAmount = 50_000.0
    private var buttonColorAnimator: ValueAnimator? = null
    private var buttonActive: Boolean? = null

    // Keep this activity underneath checkout: failures/cancellations return to the same amount.
    // A server-confirmed success still clears back to the wallet in WalletTopUpActivity.
    private val checkout = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        checkoutActive = false
        isSubmitting = false
        if (::binding.isInitialized) {
            refreshLimits()
            updateAddButtonState()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        configurePageTransitions()
        window.setSoftInputMode(
            WindowManager.LayoutParams.SOFT_INPUT_STATE_HIDDEN or
                WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE
        )
        WindowCompat.setDecorFitsSystemWindows(window, false)
        binding = ActivityWalletAddMoneyBinding.inflate(layoutInflater)
        setContentView(binding.root)
        val locale = ConfigurationCompat.getLocales(resources.configuration)[0] ?: Locale.getDefault()
        amountFormatter = WalletAmountFormatter(locale)
        checkoutActive = savedInstanceState?.getBoolean(STATE_CHECKOUT_ACTIVE) ?: false
        isSubmitting = checkoutActive
        refreshLimits()
        if (!RemoteConfigManager.isWalletEnabled()) {
            showToast(getString(R.string.wallet_top_up_is_temporarily_unavailable))
            finish()
            return
        }

        parkingLotId = intent.getStringExtra(EXTRA_PARKING_LOT_ID)?.trim()?.takeIf(String::isNotEmpty)
        val balance = NumberFormat.getNumberInstance(locale).apply {
            minimumFractionDigits = 2
            maximumFractionDigits = 2
        }.format(intent.getDoubleExtra(EXTRA_CURRENT_BALANCE, 0.0))
        val balanceText = SpannableString(getString(R.string.current_balance) + "  \uFFFC " + balance)
        ContextCompat.getDrawable(this, R.drawable.ic_wallet_coin_outline)?.mutate()?.let { icon ->
            val size = dp(16f).toInt()
            icon.setBounds(0, 0, size, size)
            icon.setTint(ContextCompat.getColor(this, R.color.wallet_supporting_text))
            val index = balanceText.indexOf('\uFFFC')
            balanceText.setSpan(ImageSpan(icon, ImageSpan.ALIGN_BASELINE), index, index + 1,
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
        binding.tvCurrentBalance.text = balanceText
        binding.tvCurrentBalance.contentDescription = getString(R.string.wallet_balance_accessibility, balance)
        setupInsets()
        setupListeners()
        binding.amountStage.doOnLayout { fitAmountToAvailableWidth() }
        binding.amountStage.addOnLayoutChangeListener { _, l, _, r, _, oldL, _, oldR, _ ->
            if (r - l != oldR - oldL) fitAmountToAvailableWidth()
        }
        updateAddButtonState()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putBoolean(STATE_CHECKOUT_ACTIVE, checkoutActive)
        super.onSaveInstanceState(outState)
    }

    @Suppress("DEPRECATION") // API 24–33 need the legacy transition API.
    private fun configurePageTransitions() {
        val animate = AnimatorSettingsCompat.areEnabled(this)
        val enter = if (animate) android.R.anim.fade_in else 0
        val exit = if (animate) android.R.anim.fade_out else 0
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            overrideActivityTransition(OVERRIDE_TRANSITION_OPEN, enter, exit)
            overrideActivityTransition(OVERRIDE_TRANSITION_CLOSE, enter, exit)
        } else {
            overridePendingTransition(enter, exit)
        }
    }

    @Suppress("DEPRECATION") // Keep the same close on Android versions before API 34.
    override fun finish() {
        val animate = AnimatorSettingsCompat.areEnabled(this)
        val enter = if (animate) android.R.anim.fade_in else 0
        val exit = if (animate) android.R.anim.fade_out else 0
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            // Refresh in case reduced-motion settings changed while this page was open.
            overrideActivityTransition(OVERRIDE_TRANSITION_CLOSE, enter, exit)
        }
        super.finish()
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            overridePendingTransition(enter, exit)
        }
    }

    private fun refreshLimits() {
        RemoteConfigManager.loadCached(this)
        minAmount = ceil(WalletTopUpLauncher.minAmount(this))
        maxAmount = floor(WalletTopUpLauncher.maxAmount(this))
    }

    @SuppressLint("ClickableViewAccessibility") // Touch listener returns false: native button dispatches clicks.
    private fun setupListeners() {
        // Edge gestures, system back keys and the toolbar arrow all commit this same close.
        // An unfinished/cancelled predictive gesture never calls handleOnBackPressed.
        onBackPressedDispatcher.addCallback(this, WalletAddMoneyBackCallback(
            isKeyboardVisible = {
                ViewCompat.getRootWindowInsets(binding.root)?.isVisible(WindowInsetsCompat.Type.ime()) == true
            },
            dismissKeyboard = {
                WindowCompat.getInsetsController(window, binding.root).hide(WindowInsetsCompat.Type.ime())
                binding.etAmount.clearFocus()
            },
            closePage = {
                binding.etAmount.stopGlyphMotion()
                finish()
            },
        ))
        binding.btnBack.setOnClickListener { onBackPressedDispatcher.onBackPressed() }
        val touch = View.OnTouchListener { view, event ->
            if (view.isEnabled && AnimatorSettingsCompat.areEnabled(this)) {
                when (event.actionMasked) {
                    MotionEvent.ACTION_DOWN -> view.animate().scaleX(0.98f).scaleY(0.98f).setDuration(90).start()
                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL ->
                        view.animate().scaleX(1f).scaleY(1f).setDuration(150)
                            .setInterpolator(PathInterpolator(0.2f, 0f, 0f, 1f)).start()
                }
            }
            false
        }
        binding.btnAddMoneyConfirm.setOnTouchListener(touch)
        binding.etAmount.onAmountChanged = { updateAddButtonState() }
        binding.etAmount.onInputTooLong = { showToast(getString(R.string.wallet_amount_too_long)) }
        ViewCompat.setAccessibilityDelegate(binding.etAmount, object : AccessibilityDelegateCompat() {
            override fun onInitializeAccessibilityNodeInfo(host: View, info: AccessibilityNodeInfoCompat) {
                super.onInitializeAccessibilityNodeInfo(host, info)
                // A contentDescription would mask the editable value. Supply its label as a hint.
                info.hintText = getString(R.string.wallet_amount_label)
                info.isShowingHintText = binding.etAmount.text.isNullOrEmpty()
                if (info.isShowingHintText) info.text = info.hintText
                val result = binding.etAmount.amountResult
                val invalid = result.text.isNotEmpty() &&
                    (result.error != null || result.amount?.toDouble()?.let(::isAmountAllowed) != true)
                info.isContentInvalid = invalid
                if (invalid) info.error = binding.tvMinimumAmount.text
            }
        })
        binding.etAmount.setOnEditorActionListener { _, action, _ ->
            if (action == EditorInfo.IME_ACTION_DONE) {
                WindowCompat.getInsetsController(window, binding.root).hide(WindowInsetsCompat.Type.ime())
                binding.etAmount.clearFocus()
                true // Done dismisses the keyboard; it never submits a payment.
            } else false
        }
        binding.llAmountContainer.addOnLayoutChangeListener { _, left, _, _, _, oldLeft, _, _, _ ->
            if (left != oldLeft && oldLeft != 0 && binding.etAmount.isFocused &&
                AnimatorSettingsCompat.areEnabled(this)
            ) {
                val coin = binding.ivAmountCoin
                val start = coin.translationX + oldLeft - left
                coin.animate().cancel()
                coin.translationX = start
                coin.animate().translationX(0f).setDuration(180)
                    .setInterpolator(PathInterpolator(0.2f, 0f, 0f, 1f)).start()
            }
        }
        binding.btnAddMoneyConfirm.setOnClickListener {
            val amount = binding.etAmount.amountResult.amount?.toDouble()
            if (!isSubmitting && amount != null && isAmountAllowed(amount)) {
                it.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                startCheckout(amount)
            }
        }
    }

    /** One size for the entire configured range, shrinking only when the viewport requires it. */
    private fun fitAmountToAvailableWidth() {
        val available = binding.amountStage.width - binding.amountStage.paddingLeft -
            binding.amountStage.paddingRight - dp(42f)
        if (available <= 0) return
        val basePx = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, 54f, resources.displayMetrics)
        val measuringPaint = TextPaint(binding.etAmount.paint).apply { textSize = basePx }
        val widestAmount = amountFormatter.display(maxAmount.coerceAtLeast(0.0))
        val required = measuringPaint.measureText(widestAmount) + dp(6f)
        val size = basePx * minOf(1f, available / required)
        if (binding.etAmount.textSize != size) {
            binding.etAmount.stopGlyphMotion()
            binding.etAmount.setTextSize(TypedValue.COMPLEX_UNIT_PX, size)
        }
        binding.etAmount.maxWidth = available.toInt()
    }

    private fun setupInsets() {
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { view, insets ->
            val padding = insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.ime())
            view.setPadding(padding.left, padding.top, padding.right, padding.bottom)
            insets
        }
        val movingViews = listOf(binding.tvCurrentBalance, binding.llAmountContainer,
            binding.tvMinimumAmount, binding.layoutActionFooter)
        val positions = FloatArray(movingViews.size)
        val offsets = FloatArray(movingViews.size)
        val location = IntArray(2)
        ViewCompat.setWindowInsetsAnimationCallback(binding.root,
            object : WindowInsetsAnimationCompat.Callback(DISPATCH_MODE_CONTINUE_ON_SUBTREE) {
                override fun onPrepare(animation: WindowInsetsAnimationCompat) {
                    if (animation.typeMask and WindowInsetsCompat.Type.ime() == 0) return
                    binding.etAmount.stopGlyphMotion()
                    movingViews.forEachIndexed { index, view ->
                        view.getLocationInWindow(location)
                        positions[index] = location[1].toFloat()
                    }
                }
                override fun onStart(animation: WindowInsetsAnimationCompat,
                    bounds: WindowInsetsAnimationCompat.BoundsCompat): WindowInsetsAnimationCompat.BoundsCompat {
                    if (animation.typeMask and WindowInsetsCompat.Type.ime() != 0) {
                        movingViews.forEachIndexed { index, view ->
                            view.translationY = 0f
                            view.getLocationInWindow(location)
                            offsets[index] = if (AnimatorSettingsCompat.areEnabled(this@WalletAddMoneyActivity))
                                positions[index] - location[1] else 0f
                            view.translationY = offsets[index]
                        }
                    }
                    return bounds
                }
                override fun onProgress(insets: WindowInsetsCompat,
                    runningAnimations: MutableList<WindowInsetsAnimationCompat>): WindowInsetsCompat {
                    val ime = runningAnimations.lastOrNull {
                        it.typeMask and WindowInsetsCompat.Type.ime() != 0
                    } ?: return insets
                    movingViews.forEachIndexed { index, view ->
                        view.translationY = offsets[index] * (1f - ime.interpolatedFraction)
                    }
                    return insets
                }
                override fun onEnd(animation: WindowInsetsAnimationCompat) {
                    if (animation.typeMask and WindowInsetsCompat.Type.ime() != 0) {
                        movingViews.forEach { it.translationY = 0f }
                    }
                }
            })
        ViewCompat.requestApplyInsets(binding.root)
    }

    private fun updateAddButtonState() {
        val result = binding.etAmount.amountResult
        val amount = result.amount?.toDouble()
        val valid = amount != null && isAmountAllowed(amount) && result.error == null
        val helper = when {
            result.error == WalletAmountFormatter.Error.DECIMAL -> getString(R.string.wallet_whole_rupees)
            result.error == WalletAmountFormatter.Error.INVALID -> getString(R.string.wallet_invalid_amount)
            result.error == WalletAmountFormatter.Error.TOO_LARGE || (amount != null && amount > maxAmount) ->
                getString(R.string.wallet_maximum_amount, amountFormatter.display(maxAmount))
            else -> getString(R.string.wallet_minimum_amount, amountFormatter.display(minAmount))
        }
        val invalid = result.text.isNotEmpty() && !valid
        if (binding.tvMinimumAmount.text.toString() != helper) binding.tvMinimumAmount.text = helper
        binding.tvMinimumAmount.setTextColor(ContextCompat.getColor(this,
            if (invalid) R.color.error else R.color.wallet_supporting_text))
        binding.btnAddMoneyConfirm.isEnabled = valid && !isSubmitting
        binding.etAmount.isEnabled = !isSubmitting
        binding.btnAddMoneyConfirm.text = when {
            isSubmitting -> getString(R.string.wallet_opening_checkout)
            valid -> getString(R.string.wallet_continue_amount, amountFormatter.display(requireNotNull(amount)))
            else -> getString(R.string.add_money)
        }
        binding.loadingContent.visibility = if (isSubmitting) View.VISIBLE else View.GONE
        setButtonColors(valid || isSubmitting)
    }

    private fun setButtonColors(active: Boolean) {
        val background = ContextCompat.getColor(this,
            if (active) R.color.brand_primary else R.color.wallet_button_disabled)
        val foreground = ContextCompat.getColor(this,
            if (active) R.color.md3_on_primary else R.color.wallet_button_disabled_text)
        val button = binding.btnAddMoneyConfirm
        // Keep the native button label for accessibility/layout; the loading row paints it once.
        button.setTextColor(if (isSubmitting) Color.TRANSPARENT else foreground)
        if (buttonActive == active) return
        val initial = button.backgroundTintList?.defaultColor ?: background
        buttonColorAnimator?.cancel()
        if (buttonActive != null && AnimatorSettingsCompat.areEnabled(this)) {
            buttonColorAnimator = ValueAnimator.ofArgb(initial, background).apply {
                duration = 150L
                addUpdateListener { button.backgroundTintList = ColorStateList.valueOf(it.animatedValue as Int) }
                start()
            }
        } else button.backgroundTintList = ColorStateList.valueOf(background)
        buttonActive = active
    }

    private fun startCheckout(amount: Double) {
        refreshLimits()
        if (!RemoteConfigManager.isWalletEnabled()) {
            showToast(getString(R.string.wallet_top_up_is_temporarily_unavailable))
            return
        }
        if (!isAmountAllowed(amount)) {
            updateAddButtonState()
            showToast(getString(R.string.wallet_amount_range,
                amountFormatter.display(minAmount), amountFormatter.display(maxAmount)))
            return
        }
        if (AuthSession.getUserId(this).isNullOrBlank()) {
            showToast(getString(R.string.please_login_to_add_money))
            return
        }
        isSubmitting = true
        binding.etAmount.stopGlyphMotion()
        WindowCompat.getInsetsController(window, binding.root).hide(WindowInsetsCompat.Type.ime())
        updateAddButtonState()
        lifecycleScope.launch {
            try {
                when (val result = WalletTopUpLauncher.createTopUp(this@WalletAddMoneyActivity, amount, parkingLotId)) {
                    is WalletTopUpLauncher.Result.Ready -> {
                        checkoutActive = true
                        checkout.launch(result.intent)
                    }
                    is WalletTopUpLauncher.Result.Failed -> showToast(result.message)
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                checkoutActive = false
                showToast(getString(R.string.payment_could_not_be_started))
            } finally {
                isSubmitting = checkoutActive
                if (!isFinishing && !isDestroyed) updateAddButtonState()
            }
        }
    }

    private fun isAmountAllowed(amount: Double) = amount >= minAmount && amount <= maxAmount

    private fun showToast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
    }

    private fun dp(value: Float) = value * resources.displayMetrics.density

    override fun onDestroy() {
        buttonColorAnimator?.cancel()
        super.onDestroy()
    }

    companion object {
        const val EXTRA_CURRENT_BALANCE = "EXTRA_CURRENT_BALANCE"
        private const val EXTRA_PARKING_LOT_ID = "EXTRA_PARKING_LOT_ID"
        private const val STATE_CHECKOUT_ACTIVE = "checkout_active"

        fun createIntent(context: Context, currentBalance: Double, parkingLotId: String? = null): Intent =
            Intent(context, WalletAddMoneyActivity::class.java).apply {
                putExtra(EXTRA_CURRENT_BALANCE, currentBalance)
                parkingLotId?.trim()?.takeIf(String::isNotEmpty)?.let { putExtra(EXTRA_PARKING_LOT_ID, it) }
            }
    }
}
