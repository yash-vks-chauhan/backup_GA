package com.gridee.parking.ui.fragments

import android.content.Intent
import android.text.TextWatcher
import android.text.Editable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.gridee.parking.R
import com.gridee.parking.data.api.ApiClient
import com.gridee.parking.data.model.WalletTransaction
import com.gridee.parking.databinding.FragmentWalletNewBinding
import com.gridee.parking.databinding.BottomSheetTopUpBinding
import com.gridee.parking.ui.activities.TransactionHistoryActivity
import com.gridee.parking.ui.adapters.Transaction
import com.gridee.parking.ui.adapters.TransactionType
import com.gridee.parking.ui.adapters.WalletTransactionGrouping
import com.gridee.parking.ui.adapters.WalletTransactionsAdapter
import com.gridee.parking.ui.base.BaseTabFragment
import com.gridee.parking.ui.compose.DotLottieAnimation
import com.gridee.parking.ui.compose.DotLottieSource
import com.gridee.parking.ui.compose.Mode
import com.gridee.parking.utils.AuthSession
import com.gridee.parking.utils.BackendTimestampParser
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import androidx.dynamicanimation.animation.DynamicAnimation
import androidx.dynamicanimation.animation.SpringAnimation
import androidx.dynamicanimation.animation.SpringForce
import kotlinx.coroutines.launch
import java.util.*

class WalletFragmentNew : BaseTabFragment<FragmentWalletNewBinding>() {

    private lateinit var transactionsAdapter: WalletTransactionsAdapter
    private var currentBalance = 0.0
    private var userTransactions = mutableListOf<Transaction>()

    override fun getViewBinding(inflater: LayoutInflater, container: ViewGroup?): FragmentWalletNewBinding {
        return FragmentWalletNewBinding.inflate(inflater, container, false)
    }

    override fun getScrollableView(): View? {
        return try {
            binding.scrollContent
        } catch (e: IllegalStateException) {
            null
        }
    }

    override fun setupUI() {
        setupRecyclerView()
        setupEmptyStateAnimation()
        setupPullToRefresh()
        setupClickListeners()
        loadWalletData()
    }

    override fun onResume() {
        super.onResume()
        // Refresh wallet data in case a top-up just completed
        loadWalletData()
    }

    private fun setupRecyclerView() {
        transactionsAdapter = WalletTransactionsAdapter(emptyList())
        
        binding.rvTransactions.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = transactionsAdapter
        }
    }

    private fun setupEmptyStateAnimation() {
        binding.emptyStateAnimation.setViewCompositionStrategy(
            ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed
        )
        binding.emptyStateAnimation.setContent {
            DotLottieAnimation(
                source = DotLottieSource.Url(EMPTY_STATE_DOTLOTTIE_URL),
                autoplay = true,
                loop = true,
                speed = 3f,
                useFrameInterpolation = false,
                playMode = Mode.Forward,
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.LightGray),
            )
        }
    }

    private fun setupPullToRefresh() {
        binding.swipeRefresh.setColorSchemeResources(R.color.brand_primary)
        binding.swipeRefresh.setOnRefreshListener {
            loadWalletData()
        }
    }

    private fun setupClickListeners() {
        binding.tvViewAll.setOnClickListener {
            val intent = Intent(requireContext(), TransactionHistoryActivity::class.java)
            startActivity(intent)
        }
        
        binding.btnAddMoney.setOnClickListener {
            android.util.Log.d("WalletFragmentNew", "Add money button clicked")
            showTopUpDialog()
        }
        
        // Quick Add chip buttons - with bounce animation
        binding.btnQuickAdd10.setOnClickListener { view ->
            animateChipBounce(view) {
                startRazorpayCheckout(50.0)
            }
        }
        
        binding.btnQuickAdd20.setOnClickListener { view ->
            animateChipBounce(view) {
                startRazorpayCheckout(100.0)
            }
        }
        
        binding.btnQuickAdd100.setOnClickListener { view ->
            animateChipBounce(view) {
                startRazorpayCheckout(200.0)
            }
        }
    }
    
    /**
     * Animates a chip with a satisfying press-down and spring-back effect.
     * Uses spring physics for premium feel.
     */
    private fun animateChipBounce(view: View, onComplete: () -> Unit) {
        // Haptic feedback for tactile response
        view.performHapticFeedback(android.view.HapticFeedbackConstants.KEYBOARD_TAP)
        
        // Phase 1: Press down (scale to 0.92)
        val scaleDownX = SpringAnimation(view, DynamicAnimation.SCALE_X, 0.92f).apply {
            spring = SpringForce(0.92f).apply {
                dampingRatio = SpringForce.DAMPING_RATIO_NO_BOUNCY
                stiffness = SpringForce.STIFFNESS_HIGH
            }
        }
        val scaleDownY = SpringAnimation(view, DynamicAnimation.SCALE_Y, 0.92f).apply {
            spring = SpringForce(0.92f).apply {
                dampingRatio = SpringForce.DAMPING_RATIO_NO_BOUNCY
                stiffness = SpringForce.STIFFNESS_HIGH
            }
        }
        
        // Phase 2: Spring back with bounce (scale to 1.0 with overshoot)
        val scaleUpX = SpringAnimation(view, DynamicAnimation.SCALE_X, 1f).apply {
            spring = SpringForce(1f).apply {
                dampingRatio = SpringForce.DAMPING_RATIO_MEDIUM_BOUNCY
                stiffness = SpringForce.STIFFNESS_MEDIUM
            }
        }
        val scaleUpY = SpringAnimation(view, DynamicAnimation.SCALE_Y, 1f).apply {
            spring = SpringForce(1f).apply {
                dampingRatio = SpringForce.DAMPING_RATIO_MEDIUM_BOUNCY
                stiffness = SpringForce.STIFFNESS_MEDIUM
            }
        }
        
        // Slight elevation drop during animation (press effect)
        val originalElevation = view.elevation
        // mitigate negative elevation
        val newElevation = if (originalElevation > 4f.dpToPx()) originalElevation - 4f.dpToPx() else 0f
        view.elevation = newElevation
        
        // Chain animations
        scaleDownX.start()
        scaleDownY.start()
        
        // After press down, spring back and trigger action
        scaleDownX.addEndListener { _, _, _, _ ->
            view.elevation = originalElevation
            scaleUpX.start()
            scaleUpY.start()
            
            // Trigger the action slightly before animation completes for responsiveness
            view.postDelayed({
                onComplete()
            }, 50)
        }
    }
    
    private fun Float.dpToPx(): Float {
        return this * resources.displayMetrics.density
    }

    private fun loadWalletData() {
        setRefreshing(true)
        
        val userId = getUserId()
        if (userId == null) {
            showToast("Please login to view wallet")
            setRefreshing(false)
            return
        }

        lifecycleScope.launch {
            try {
                android.util.Log.d("WalletFragmentNew", "Loading wallet balance for user: $userId")
                loadWalletBalance(userId)
                android.util.Log.d("WalletFragmentNew", "Loading wallet transactions for user: $userId")
                loadWalletTransactions(userId)
            } catch (e: Exception) {
                android.util.Log.e("WalletFragmentNew", "Unexpected error loading wallet data", e)
                showToast("Error loading wallet data: ${e.message}")
            } finally {
                setRefreshing(false)
            }
        }
    }

    private suspend fun loadWalletBalance(userId: String) {
        try {
            val response = ApiClient.apiService.getWalletDetails(userId)
            android.util.Log.d("WalletFragmentNew", "Wallet balance API response: ${response.code()}")
            if (response.isSuccessful) {
                val walletDetails = response.body()
                currentBalance = walletDetails?.balance ?: 0.0
                updateBalanceDisplay()
            } else {
                if (response.code() == 401) {
                    handleUnauthorized()
                } else if (response.code() == 404) {
                    // Wallet not found — treat as zero balance for resilience
                    currentBalance = 0.0
                    updateBalanceDisplay()
                    android.util.Log.w("WalletFragmentNew", "Wallet not found (404); showing zero balance")
                } else {
                    showToast("Unable to load wallet balance (${response.code()})")
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("WalletFragmentNew", "Error loading wallet balance", e)
            showToast("Error loading balance: ${e.message}")
        }
    }

    private suspend fun loadWalletTransactions(userId: String) {
        try {
            val transactionResponse = ApiClient.apiService.getWalletTransactions(userId)
            android.util.Log.d("WalletFragmentNew", "Wallet transactions API response: ${transactionResponse.code()}")
            if (transactionResponse.isSuccessful) {
                val backendTransactions = transactionResponse.body().orEmpty()
                userTransactions.clear()
                
                val convertedTransactions = backendTransactions.map { convertToUITransaction(it) }
                userTransactions.addAll(convertedTransactions.sortedByDescending { it.timestamp })
                reconcileBalanceFromTransactions(backendTransactions)
                
                android.util.Log.d("WalletFragmentNew", "Loaded ${userTransactions.size} transactions from API")
                updateTransactionsDisplay()
            } else {
                if (transactionResponse.code() == 401) {
                    handleUnauthorized()
                } else {
                    userTransactions.clear()
                    updateTransactionsDisplay()
                    showToast("Unable to load transactions (${transactionResponse.code()})")
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("WalletFragmentNew", "Error loading wallet transactions", e)
            userTransactions.clear()
            updateTransactionsDisplay()
            showToast("Error loading transactions: ${e.message}")
        }
    }

    private fun reconcileBalanceFromTransactions(transactions: List<WalletTransaction>) {
        if (transactions.isEmpty()) return

        val latestTransaction = transactions.maxByOrNull {
            BackendTimestampParser.parseToMillis(it.timestamp, 0L)
        } ?: return

        val latestBalance = latestTransaction.balanceAfter
        if (latestBalance != null && kotlin.math.abs(latestBalance - currentBalance) > BALANCE_EPSILON) {
            currentBalance = latestBalance
            updateBalanceDisplay()
            return
        }

        if (currentBalance == 0.0) {
            val fallbackBalance = transactions
                .mapNotNull { txn ->
                    val balance = txn.balanceAfter ?: return@mapNotNull null
                    val timestampMillis = BackendTimestampParser.parseToMillis(txn.timestamp, 0L)
                    balance to timestampMillis
                }
                .maxByOrNull { it.second }
                ?.first

            if (fallbackBalance != null && kotlin.math.abs(fallbackBalance - currentBalance) > BALANCE_EPSILON) {
                currentBalance = fallbackBalance
                updateBalanceDisplay()
            }
        }
    }

    private fun handleUnauthorized() {
        showToast("Session expired. Please log in again.")
        AuthSession.clearSession(requireContext())
        // Navigate to login
        val intent = android.content.Intent(requireContext(), com.gridee.parking.ui.auth.LoginActivity::class.java)
        intent.flags = android.content.Intent.FLAG_ACTIVITY_NEW_TASK or android.content.Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        requireActivity().finish()
    }

    private fun updateBalanceDisplay() {
        binding.tvBalanceAmount.text = "₹${String.format("%.2f", currentBalance)}"
    }

    private fun updateTransactionsDisplay() {
        if (userTransactions.isEmpty()) {
            binding.rvTransactions.visibility = View.GONE
            binding.layoutEmptyState.visibility = View.VISIBLE
            transactionsAdapter.updateItems(emptyList())
        } else {
            binding.rvTransactions.visibility = View.VISIBLE
            binding.layoutEmptyState.visibility = View.GONE
            
            val groupedItems = WalletTransactionGrouping.buildGroupedItems(
                userTransactions,
                MAX_RECENT_TRANSACTIONS
            )
            android.util.Log.d("WalletFragmentNew", "Rendering ${groupedItems.size} grouped transaction items")
            transactionsAdapter.updateItems(groupedItems)
        }
    }

    private fun loadSampleData() {
        // This method is kept for testing purposes only
        // In production, this should not be called - real data should always be used
        android.util.Log.w("WalletFragmentNew", "Using sample data - this should not happen in production!")
        
        currentBalance = 0.0
        updateBalanceDisplay()
        userTransactions.clear()
        updateTransactionsDisplay()
        showToast("No real wallet data available")
    }

    private fun convertToUITransaction(backendTransaction: WalletTransaction): Transaction {
        val timestamp = BackendTimestampParser.parse(backendTransaction.timestamp)
        
        // Normalize fields
        val rawDescription = backendTransaction.description?.trim()
        val normalizedDescription = rawDescription?.ifBlank { null }
        val descriptionLower = normalizedDescription?.lowercase(Locale.getDefault())
        val typeNorm = backendTransaction.type?.trim()?.lowercase(Locale.getDefault())
        val backendType = backendTransaction.type?.trim()?.uppercase(Locale.getDefault())
        val statusNorm = backendTransaction.status?.trim()?.lowercase(Locale.getDefault())
        val amountValue = backendTransaction.amount ?: 0.0

        val backendUiType = when (backendType) {
            "BOOKING_FEE" -> TransactionType.PARKING_PAYMENT
            "BOOKING_REFUND" -> TransactionType.REFUND
            "WALLET_TOP_UP", "AD_TOP_UP" -> TransactionType.TOP_UP
            "REFUND" -> TransactionType.REFUND
            "PENALTY_FEE", "LATE_CHECK_IN_PENALTY", "LATE_CHECK_OUT_PENALTY" -> TransactionType.PARKING_PAYMENT
            else -> null
        }
        val backendBaseDescription = when (backendType) {
            "BOOKING_FEE" -> "Booking Fee"
            "BOOKING_REFUND" -> "Booking Refund"
            "WALLET_TOP_UP" -> "Wallet Top-up"
            "AD_TOP_UP" -> "Ad Top-up"
            "REFUND" -> "Refund"
            "PENALTY_FEE" -> "Penalty Fee"
            "LATE_CHECK_IN_PENALTY" -> "Late Check-in Penalty"
            "LATE_CHECK_OUT_PENALTY" -> "Late Check-out Penalty"
            else -> null
        }
        val backendIsCredit = when (backendType) {
            "BOOKING_REFUND", "WALLET_TOP_UP", "AD_TOP_UP", "REFUND" -> true
            "BOOKING_FEE", "PENALTY_FEE", "LATE_CHECK_IN_PENALTY", "LATE_CHECK_OUT_PENALTY" -> false
            else -> null
        }
        val backendIsBookingRelated = backendType in setOf(
            "BOOKING_FEE",
            "BOOKING_REFUND",
            "PENALTY_FEE",
            "LATE_CHECK_IN_PENALTY",
            "LATE_CHECK_OUT_PENALTY"
        )

        // Map transaction type and handle amount correctly
        val isRewardByText = descriptionLower?.contains("reward") == true
        val isRewardByAmount = amountValue > 0 &&
            kotlin.math.abs(amountValue - REWARD_AMOUNT_RUPEES) < 0.01
        val isReward = isRewardByText || isRewardByAmount || typeNorm == "bonus"

        val isTopUpByType = typeNorm in listOf(
            "top_up",
            "topup",
            "wallet_topup",
            "wallet_recharge",
            "recharge",
            "top-up"
        )
        val isTopUpByText = descriptionLower?.let {
            it.contains("top up") ||
                it.contains("topup") ||
                it.contains("top-up") ||
                it.contains("recharge") ||
                it.contains("add money") ||
                it.contains("wallet top")
        } == true
        val isTopUp = isTopUpByType || isTopUpByText

        val isRefundByType = typeNorm?.contains("refund") == true ||
            typeNorm?.contains("reversal") == true ||
            typeNorm?.contains("cancel") == true ||
            typeNorm?.contains("return") == true ||
            typeNorm?.contains("adjust") == true
        val isRefundByText = descriptionLower?.let {
            it.contains("refund") ||
                it.contains("reversal") ||
                it.contains("cancel") ||
                it.contains("checkout") ||
                it.contains("check out") ||
                it.contains("check-out") ||
                it.contains("early") ||
                it.contains("unused") ||
                it.contains("overpaid") ||
                it.contains("adjust")
        } == true
        val isBookingRelated = descriptionLower?.let {
            it.contains("booking") ||
                it.contains("parking") ||
                it.contains("session") ||
                it.contains("check in") ||
                it.contains("check-in") ||
                it.contains("checkout") ||
                it.contains("check out") ||
                it.contains("check-out") ||
                it.contains("reservation") ||
                it.contains("slot")
        } == true || typeNorm?.contains("booking") == true || typeNorm?.contains("parking") == true

        val isDebit = typeNorm in listOf("debit", "payment", "penalty_deduction", "penalty", "charge") ||
            typeNorm?.contains("debit") == true
        val isCredit = typeNorm == "credit" || typeNorm?.contains("credit") == true

        val isRefund = isRefundByType || isRefundByText ||
            (!isReward && !isTopUp && isCredit) ||
            (isBookingRelated && !isReward && !isDebit && !isTopUpByText)
        val isBookingCharge = isBookingRelated && !isRefund
        val transactionType = backendUiType ?: when {
            isReward -> TransactionType.BONUS
            isRefund -> TransactionType.REFUND
            isTopUp -> TransactionType.TOP_UP
            isBookingCharge || isDebit -> TransactionType.PARKING_PAYMENT
            else -> TransactionType.TOP_UP
        }
        
        // Ensure proper amount handling:
        // CREDIT transactions should be positive
        // DEBIT transactions should be negative
        val displayAmount = if (backendUiType != null && backendIsCredit != null) {
            val absolute = kotlin.math.abs(amountValue)
            if (backendIsCredit) absolute else -absolute
        } else {
            when (transactionType) {
                TransactionType.PARKING_PAYMENT ->
                    if (amountValue > 0) -amountValue else amountValue
                else ->
                    if (amountValue < 0) -amountValue else amountValue
            }
        }
        
        android.util.Log.d("WalletFragmentNew", "Converting transaction: type=${backendTransaction.type}, originalAmount=$amountValue, displayAmount=$displayAmount")
        
        // Build a user-friendly description, respecting status
        val resolvedBookingRelated = if (backendUiType != null) backendIsBookingRelated else isBookingRelated
        val baseDescription = backendBaseDescription ?: when {
            isReward -> "Reward Added"
            transactionType == TransactionType.TOP_UP -> "Wallet Top-up"
            transactionType == TransactionType.PARKING_PAYMENT -> "Booking Charge"
            transactionType == TransactionType.REFUND && resolvedBookingRelated -> "Booking Refund"
            transactionType == TransactionType.REFUND -> "Refund"
            else -> "Wallet Top-up"
        }
        val description = when (statusNorm) {
            "failed" -> "$baseDescription Failed"
            "cancelled", "canceled" -> "$baseDescription Cancelled"
            else -> when {
                isReward -> baseDescription
                transactionType == TransactionType.REFUND ->
                    if (descriptionLower?.contains("refund") == true) normalizedDescription ?: baseDescription else baseDescription
                else -> normalizedDescription ?: baseDescription
            }
        }

        return Transaction(
            id = backendTransaction.id ?: "Unknown",
            type = transactionType,
            amount = displayAmount,
            description = description,
            timestamp = timestamp,
            balanceAfter = backendTransaction.balanceAfter ?: 0.0,
            status = backendTransaction.status
        )
    }

    private fun showTopUpDialog() {
        try {
            android.util.Log.d("WalletFragmentNew", "showTopUpDialog called")
            
            val bottomSheetDialog = BottomSheetDialog(requireContext(), R.style.BottomSheetDialogTheme)
            val bottomSheetBinding = BottomSheetTopUpBinding.inflate(layoutInflater)
            bottomSheetDialog.setContentView(bottomSheetBinding.root)

            bottomSheetDialog.window?.apply {
                setWindowAnimations(R.style.BottomSheetSpringAnimation)
                setDimAmount(0.45f)
                
                // Glassmorphism: Blur the screen behind the sheet (Android 12+)
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                    attributes.blurBehindRadius = 50 // 50px blur for frosted glass effect
                    attributes = attributes // Apply changes
                }
            }

            bottomSheetDialog.behavior.apply {
                skipCollapsed = true
                state = BottomSheetBehavior.STATE_EXPANDED
                isFitToContents = true
            }

            bottomSheetDialog.setOnShowListener {
                val bottomSheet = bottomSheetDialog.findViewById<FrameLayout>(com.google.android.material.R.id.design_bottom_sheet)
                bottomSheet?.post {
                    bottomSheet.translationY = 120f
                    bottomSheet.alpha = 0f
                    val spring = SpringAnimation(bottomSheet, DynamicAnimation.TRANSLATION_Y, 0f).apply {
                        spring = SpringForce(0f).apply {
                            dampingRatio = SpringForce.DAMPING_RATIO_MEDIUM_BOUNCY
                            stiffness = SpringForce.STIFFNESS_LOW
                        }
                    }
                    bottomSheet.animate()
                        .alpha(1f)
                        .setDuration(220)
                        .start()
                    spring.start()
                }
            }
            
            android.util.Log.d("WalletFragmentNew", "Bottom sheet dialog created")
            
            // Set current balance
            bottomSheetBinding.tvCurrentBalance.text = "₹${String.format("%.2f", currentBalance)}"
            
            // Setup click listeners for quick amount buttons
            bottomSheetBinding.btnAmount50.setOnClickListener {
                bottomSheetBinding.etAmount.setText("50")
                updateAddButtonState(bottomSheetBinding)
            }
            
            bottomSheetBinding.btnAmount100.setOnClickListener {
                bottomSheetBinding.etAmount.setText("100")
                updateAddButtonState(bottomSheetBinding)
            }
            
            bottomSheetBinding.btnAmount200.setOnClickListener {
                bottomSheetBinding.etAmount.setText("200")
                updateAddButtonState(bottomSheetBinding)
            }

            bottomSheetBinding.btnAmount500.setOnClickListener {
                bottomSheetBinding.etAmount.setText("500")
                updateAddButtonState(bottomSheetBinding)
            }
            
            // Payment method is fixed to Razorpay; no selection needed
            
            // Setup text change listener for amount input
            bottomSheetBinding.etAmount.addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
                override fun afterTextChanged(s: Editable?) {
                    updateAddButtonState(bottomSheetBinding)
                }
            })
            
            // Close button
            bottomSheetBinding.btnClose.setOnClickListener {
                animateAndDismiss(bottomSheetDialog)
            }
            
            // Add money button
            bottomSheetBinding.btnAddMoneyConfirm.setOnClickListener {
                val amountText = bottomSheetBinding.etAmount.text.toString()
                if (amountText.isNotEmpty()) {
                    val amount = amountText.toDoubleOrNull()
                    if (amount != null && amount > 0) {
                        showToast("Redirecting to Razorpay checkout...")
                        startRazorpayCheckout(amount)
                        animateAndDismiss(bottomSheetDialog)
                    } else {
                        showToast("Please enter a valid amount")
                    }
                }
            }
            
            // No selection state to initialize
            
            android.util.Log.d("WalletFragmentNew", "About to show bottom sheet")
            bottomSheetDialog.show()
            android.util.Log.d("WalletFragmentNew", "Bottom sheet shown")
            
        } catch (e: Exception) {
            android.util.Log.e("WalletFragmentNew", "Error showing bottom sheet", e)
            showToast("Error opening top-up dialog: ${e.message}")
        }
    }
    
    // No payment method selection anymore
    
    private fun updateAddButtonState(binding: BottomSheetTopUpBinding) {
        val amountText = binding.etAmount.text.toString()
        val amount = amountText.toDoubleOrNull()
        val isValidAmount = amount != null && amount > 0
        
        binding.btnAddMoneyConfirm.isEnabled = isValidAmount
        binding.btnAddMoneyConfirm.text = if (isValidAmount) {
            "Add ₹${amount?.toInt()}"
        } else {
            "Add Money"
        }
    }
    
    private fun animateAndDismiss(dialog: BottomSheetDialog) {
        val bottomSheet = dialog.findViewById<FrameLayout>(com.google.android.material.R.id.design_bottom_sheet)
        if (bottomSheet == null) {
            dialog.dismiss()
            return
        }
        bottomSheet.animate()
            .translationY(bottomSheet.height * 0.25f)
            .alpha(0f)
            .setDuration(200)
            .withEndAction {
                dialog.dismiss()
                bottomSheet.translationY = 0f
                bottomSheet.alpha = 1f
            }
            .start()
    }
    
    private fun startRazorpayCheckout(amount: Double) {
        val userId = getUserId()
        if (userId == null) {
            showToast("Please login to add money")
            return
        }

        setRefreshing(true)

        lifecycleScope.launch {
            try {
                // 1) Create Razorpay order via backend
                val initResp = ApiClient.apiService.initiatePayment(
                    com.gridee.parking.data.model.PaymentInitiateRequest(
                        userId = userId,
                        amount = amount
                    )
                )

                if (!initResp.isSuccessful) {
                    showToast("Failed to initiate payment: ${initResp.code()}")
                    return@launch
                }

                val body = initResp.body()
                val orderId = body?.orderId
                val keyId = body?.keyId
                if (orderId.isNullOrBlank()) {
                    showToast("Invalid payment order from server")
                    return@launch
                }

                // 2) Open Razorpay Checkout in a dedicated activity
                val intent = android.content.Intent(requireContext(), com.gridee.parking.ui.wallet.WalletTopUpActivity::class.java)
                intent.putExtra("USER_ID", userId)
                intent.putExtra("AMOUNT", amount)
                intent.putExtra("ORDER_ID", orderId)
                keyId?.let { intent.putExtra("KEY_ID", it) }
                startActivity(intent)
            } catch (e: Exception) {
                showToast("Error: ${e.message}")
            } finally {
                setRefreshing(false)
            }
        }
    }

    override fun scrollToTop() {
        try {
            binding.scrollContent.smoothScrollTo(0, 0)
        } catch (e: Exception) {
            // Handle any exceptions
        }
    }

    private fun getUserId(): String? {
        return AuthSession.getUserId(requireContext())
    }

    private fun setRefreshing(show: Boolean) {
        if (show) {
            if (!binding.swipeRefresh.isRefreshing) {
                binding.swipeRefresh.post { binding.swipeRefresh.isRefreshing = true }
            }
        } else {
            binding.swipeRefresh.isRefreshing = false
        }
    }
    private companion object {
        private const val EMPTY_STATE_DOTLOTTIE_URL =
            "https://lottie.host/501bcbee-2a36-496c-a127-bedca0ef0ce8/D2DCOt93Jv.lottie"
        private const val MAX_RECENT_TRANSACTIONS = 5
        private const val REWARD_AMOUNT_RUPEES = 20.0
        private const val BALANCE_EPSILON = 0.01
    }
}
