package com.gridee.parking.ui.fragments

import android.content.Intent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.core.app.ActivityOptionsCompat
import androidx.core.content.ContextCompat
import androidx.core.util.Pair
import androidx.core.view.ViewCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.gridee.parking.R
import com.gridee.parking.config.RemoteConfigManager
import com.gridee.parking.data.api.ApiClient
import com.gridee.parking.data.model.WalletTransaction
import com.gridee.parking.databinding.FragmentWalletNewBinding
import com.gridee.parking.ui.activities.TransactionHistoryActivity
import com.gridee.parking.ui.adapters.Transaction
import com.gridee.parking.ui.adapters.TransactionType
import com.gridee.parking.ui.adapters.WalletTransactionGrouping
import com.gridee.parking.ui.adapters.WalletTransactionsAdapter
import com.gridee.parking.ui.base.BaseTabFragment
import com.gridee.parking.ui.compose.DotLottieAnimation
import com.gridee.parking.ui.compose.DotLottieSource
import com.gridee.parking.ui.compose.Mode
import com.gridee.parking.ui.wallet.WalletAddMoneyActivity
import com.gridee.parking.utils.AuthSession
import com.gridee.parking.utils.BackendTimestampParser
import androidx.dynamicanimation.animation.DynamicAnimation
import androidx.dynamicanimation.animation.SpringAnimation
import androidx.dynamicanimation.animation.SpringForce
import com.google.android.material.transition.platform.MaterialContainerTransformSharedElementCallback
import kotlinx.coroutines.launch
import java.util.*

class WalletFragmentNew : BaseTabFragment<FragmentWalletNewBinding>() {

    private lateinit var transactionsAdapter: WalletTransactionsAdapter
    private var currentBalance = 0.0
    private var displayedBalance = 0.0
    private var balanceAnimator: android.animation.ValueAnimator? = null
    private var balanceColorAnimator: android.animation.ValueAnimator? = null
    private var shimmerAnimator: android.animation.ValueAnimator? = null
    private var hasLoadedBalance = false
    private var hasAnimatedCardIn = false
    private var userTransactions = mutableListOf<Transaction>()
    private val indianLocale = java.util.Locale("en", "IN")
    private val balanceFormatter = (java.text.NumberFormat.getNumberInstance(indianLocale)
            as java.text.DecimalFormat).apply {
        applyPattern("#,##,##0.00")
        roundingMode = java.math.RoundingMode.HALF_UP
    }
    private val integerFormatter = (java.text.NumberFormat.getNumberInstance(indianLocale)
            as java.text.DecimalFormat).apply {
        applyPattern("#,##,##0")
        roundingMode = java.math.RoundingMode.DOWN
    }

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
        animateCardEntrance()
        startBalanceShimmer()
        loadWalletData()
    }

    private fun animateCardEntrance() {
        if (hasAnimatedCardIn) return
        hasAnimatedCardIn = true
        val slideFromPx = 12f.dpToPx()
        binding.cardWalletBalance.alpha = 0f
        binding.cardWalletBalance.translationY = slideFromPx
        binding.cardWalletBalance.animate()
            .alpha(1f)
            .translationY(0f)
            .setDuration(380)
            .setInterpolator(android.view.animation.PathInterpolator(0.05f, 0.7f, 0.1f, 1f))
            .start()
    }

    override fun onResume() {
        super.onResume()
        // Refresh wallet data in case a top-up just completed
        loadWalletData()
    }

    override fun onDestroyView() {
        balanceAnimator?.cancel()
        balanceAnimator = null
        balanceColorAnimator?.cancel()
        balanceColorAnimator = null
        shimmerAnimator?.cancel()
        shimmerAnimator = null
        super.onDestroyView()
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
        binding.swipeRefresh.setProgressBackgroundColorSchemeResource(R.color.background_secondary)
        binding.swipeRefresh.setColorSchemeResources(R.color.text_primary)
        binding.swipeRefresh.setOnRefreshListener {
            loadWalletData()
        }
    }

    private fun setupClickListeners() {
        applyFeatureSwitches()

        ViewCompat.setTransitionName(
            binding.tvViewAll,
            TransactionHistoryActivity.VIEW_ALL_TRANSITION_NAME
        )

        // View All Activity (Apple Press UX)
        binding.tvViewAll.setOnClickListener { view ->
            view.performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS)
            view.animate()
                .scaleX(0.95f)
                .scaleY(0.95f)
                .setDuration(80)
                .withEndAction {
                    view.animate()
                        .scaleX(1f)
                        .scaleY(1f)
                        .setDuration(120)
                        .setInterpolator(android.view.animation.OvershootInterpolator(1.2f))
                        .withEndAction {
                            launchTransactionHistory(view)
                        }
                        .start()
                }
                .start()
        }
        
        binding.btnAddMoney.setOnClickListener { view ->
            if (!RemoteConfigManager.isWalletEnabled()) {
                showToast("Wallet is temporarily unavailable.")
                return@setOnClickListener
            }
            view.performHapticFeedback(android.view.HapticFeedbackConstants.VIRTUAL_KEY)
            android.util.Log.d("WalletFragmentNew", "Add money button clicked")
            val intent = Intent(requireContext(), WalletAddMoneyActivity::class.java).apply {
                putExtra(WalletAddMoneyActivity.EXTRA_CURRENT_BALANCE, currentBalance)
            }
            startActivity(intent)
        }

        // Watch Ad (Earn Coins) — hero CTA pill
        binding.btnWatchAd.setOnClickListener { view ->
            animateChipBounce(view) {
                openRewardedAdSheet()
            }
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

    private fun launchTransactionHistory(sourceView: View) {
        requireActivity().setExitSharedElementCallback(
            MaterialContainerTransformSharedElementCallback()
        )
        requireActivity().window.sharedElementsUseOverlay = false

        val intent = Intent(requireContext(), TransactionHistoryActivity::class.java)
        val options = ActivityOptionsCompat.makeSceneTransitionAnimation(
            requireActivity(),
            Pair.create(sourceView, TransactionHistoryActivity.VIEW_ALL_TRANSITION_NAME)
        )
        startActivity(intent, options.toBundle())
    }

    private fun loadWalletData() {
        RemoteConfigManager.loadCached(requireContext())
        if (!RemoteConfigManager.isWalletEnabled()) {
            currentBalance = 0.0
            updateBalanceDisplay()
            transactionsAdapter.updateItems(emptyList())
            showToast("Wallet is temporarily unavailable.")
            setRefreshing(false)
            applyFeatureSwitches()
            return
        }

        setRefreshing(true)
        
        val userId = getUserId()
        if (userId == null) {
            showToast("Please login to view wallet")
            setRefreshing(false)
            return
        }

        // Bind to the view lifecycle, not the fragment lifecycle: this coroutine touches
        // the binding (balance display, shimmer, refresh spinner), so it must be cancelled
        // at onDestroyView rather than living on until onDestroy and resuming into a dead view.
        viewLifecycleOwner.lifecycleScope.launch {
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
                    stopBalanceShimmer()
                    if (!hasLoadedBalance) renderBalance(currentBalance, isAnimating = false)
                    showToast(walletErrorMessage(response.code(), "Unable to load wallet balance (${response.code()})"))
                }
            }
        } catch (e: Exception) {
            stopBalanceShimmer()
            if (!hasLoadedBalance) renderBalance(currentBalance, isAnimating = false)
            android.util.Log.e("WalletFragmentNew", "Error loading wallet balance", e)
            showToast("Error loading balance: ${e.message}")
        }
    }

    private suspend fun loadWalletTransactions(userId: String) {
        try {
            val transactionResponse = ApiClient.apiService.getWalletTransactions(
                userId = userId,
                page = 0,
                size = 200,
                sort = listOf("timestamp", "desc")
            )
            android.util.Log.d("WalletFragmentNew", "Wallet transactions API response: ${transactionResponse.code()}")
            if (transactionResponse.isSuccessful) {
                val backendTransactions = transactionResponse.body()?.content.orEmpty()
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
                showToast(walletErrorMessage(transactionResponse.code(), "Unable to load transactions (${transactionResponse.code()})"))
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
        animateBalanceTo(currentBalance)
    }

    private fun animateBalanceTo(target: Double) {
        balanceAnimator?.cancel()

        val isInitialLoad = !hasLoadedBalance
        hasLoadedBalance = true
        stopBalanceShimmer()

        val from = displayedBalance
        if (kotlin.math.abs(from - target) < 0.005) {
            renderBalance(target, isAnimating = false)
            displayedBalance = target
            return
        }

        val delta = kotlin.math.abs(target - from)
        val durationMs = when {
            delta < 10.0 -> 350L
            delta < 100.0 -> 550L
            delta < 1000.0 -> 750L
            else -> 950L
        }

        if (!isInitialLoad) {
            flashBalanceColor(isIncrease = target > from)
        }

        val animator = android.animation.ValueAnimator.ofFloat(from.toFloat(), target.toFloat()).apply {
            duration = durationMs
            // Material 3 emphasized decelerate — snappy start, soft settle
            interpolator = android.view.animation.PathInterpolator(0.05f, 0.7f, 0.1f, 1f)
            addUpdateListener { a ->
                val v = (a.animatedValue as Float).toDouble()
                renderBalance(v, isAnimating = true)
            }
            addListener(object : android.animation.AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: android.animation.Animator) {
                    displayedBalance = target
                    renderBalance(target, isAnimating = false)
                    if (!isInitialLoad) {
                        binding.tvBalanceAmount.performHapticFeedback(
                            android.view.HapticFeedbackConstants.CLOCK_TICK
                        )
                    }
                }
            })
        }
        balanceAnimator = animator
        animator.start()
    }

    private fun flashBalanceColor(isIncrease: Boolean) {
        balanceColorAnimator?.cancel()
        val flashColor = if (isIncrease) 0xFF34D399.toInt() else 0xFFF87171.toInt()
        val baseColor = 0xFFFFFFFF.toInt()
        val animator = android.animation.ValueAnimator.ofArgb(flashColor, baseColor).apply {
            duration = 420
            interpolator = android.view.animation.PathInterpolator(0.05f, 0.7f, 0.1f, 1f)
            addUpdateListener { a ->
                binding.tvBalanceAmount.setTextColor(a.animatedValue as Int)
            }
            addListener(object : android.animation.AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: android.animation.Animator) {
                    binding.tvBalanceAmount.setTextColor(baseColor)
                }
            })
        }
        balanceColorAnimator = animator
        animator.start()
    }

    private fun startBalanceShimmer() {
        if (hasLoadedBalance) return
        shimmerAnimator?.cancel()
        binding.tvBalanceAmount.text = "—"
        binding.tvBalanceAmount.contentDescription = "Loading balance"
        shimmerAnimator = android.animation.ValueAnimator.ofFloat(0.35f, 0.75f).apply {
            duration = 900
            repeatMode = android.animation.ValueAnimator.REVERSE
            repeatCount = android.animation.ValueAnimator.INFINITE
            interpolator = android.view.animation.AccelerateDecelerateInterpolator()
            addUpdateListener { a ->
                binding.tvBalanceAmount.alpha = a.animatedValue as Float
            }
            start()
        }
    }

    private fun stopBalanceShimmer() {
        shimmerAnimator?.cancel()
        shimmerAnimator = null
        binding.tvBalanceAmount.alpha = 1f
    }

    private fun renderBalance(value: Double, isAnimating: Boolean = false) {
        val safe = if (value.isNaN() || value < 0.0) 0.0 else value
        val ssb = android.text.SpannableStringBuilder()
        if (isAnimating) {
            // During count-up: show integer only + dim ".--" placeholder
            // to suppress decimal flicker without causing a layout jump on settle.
            ssb.append(integerFormatter.format(safe.toLong()))
            val decStart = ssb.length
            ssb.append(".--")
            ssb.setSpan(
                android.text.style.RelativeSizeSpan(0.72f),
                decStart, ssb.length,
                android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
            )
            ssb.setSpan(
                android.text.style.ForegroundColorSpan(0x99FFFFFF.toInt()),
                decStart, ssb.length,
                android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
            )
        } else {
            // Settled: integer at full weight + decimal slightly smaller/softer
            val full = balanceFormatter.format(safe)
            ssb.append(full)
            val dotIdx = full.lastIndexOf('.')
            if (dotIdx >= 0) {
                ssb.setSpan(
                    android.text.style.RelativeSizeSpan(0.72f),
                    dotIdx, full.length,
                    android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                )
                ssb.setSpan(
                    android.text.style.ForegroundColorSpan(0xE6FFFFFF.toInt()),
                    dotIdx, full.length,
                    android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                )
            }
            binding.tvBalanceAmount.contentDescription =
                "Balance ${balanceFormatter.format(safe)} Gridee coins"
        }
        binding.tvBalanceAmount.text = ssb
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
            "WALLET_TOP_UP" -> TransactionType.TOP_UP
            "AD_TOP_UP" -> TransactionType.BONUS
            "WELCOME_BONUS" -> TransactionType.BONUS
            "REFUND" -> TransactionType.REFUND
            "PENALTY_FEE", "LATE_CHECK_IN_PENALTY", "LATE_CHECK_OUT_PENALTY" -> TransactionType.PARKING_PAYMENT
            else -> null
        }
        val backendBaseDescription = when (backendType) {
            "BOOKING_FEE" -> "Booking Fee"
            "BOOKING_REFUND" -> "Booking Refund"
            "WALLET_TOP_UP" -> "Wallet Top-up"
            "AD_TOP_UP" -> "Ad Top-up"
            "WELCOME_BONUS" -> "Welcome Bonus"
            "REFUND" -> "Refund"
            "PENALTY_FEE" -> "Penalty Fee"
            "LATE_CHECK_IN_PENALTY" -> "Late Check-in Penalty"
            "LATE_CHECK_OUT_PENALTY" -> "Late Check-out Penalty"
            else -> null
        }
        val backendIsCredit = when (backendType) {
            "BOOKING_REFUND", "WALLET_TOP_UP", "AD_TOP_UP", "WELCOME_BONUS", "REFUND" -> true
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
            || typeNorm == "welcome_bonus"

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
        val locationLabel = listOfNotNull(
            backendTransaction.lotName?.trim()?.takeIf { it.isNotEmpty() }
                ?: backendTransaction.lotId?.trim()?.takeIf { it.isNotEmpty() },
            backendTransaction.spotId?.trim()?.takeIf { it.isNotEmpty() }?.let { "Spot $it" }
        ).joinToString(" • ").takeIf { it.isNotEmpty() }
        val displayDescription = if (locationLabel != null && resolvedBookingRelated) {
            "$description • $locationLabel"
        } else {
            description
        }

        return Transaction(
            id = backendTransaction.id ?: "Unknown",
            type = transactionType,
            amount = displayAmount,
            description = displayDescription,
            timestamp = timestamp,
            balanceAfter = backendTransaction.balanceAfter ?: 0.0,
            status = backendTransaction.status
        )
    }

    private fun openRewardedAdSheet() {
        // No source coin here, so the sheet reveals its medallion with a scale/fade.
        val bottomSheet = com.gridee.parking.ui.bottomsheet.RewardBottomSheet.newInstance()
        bottomSheet.show(parentFragmentManager, com.gridee.parking.ui.bottomsheet.RewardBottomSheet.TAG)
    }

    private fun startRazorpayCheckout(amount: Double) {
        RemoteConfigManager.loadCached(requireContext())
        if (!RemoteConfigManager.isWalletEnabled()) {
            showToast("Wallet top-up is temporarily unavailable.")
            return
        }

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
                    showToast(walletErrorMessage(initResp.code(), "Add money temporarily unavailable during payment integration."))
                    return@launch
                }

                val body = initResp.body()
                val orderId = body?.orderId
                val keyId = body?.keyId
                if (orderId.isNullOrBlank()) {
                    showToast("Add money temporarily unavailable during payment integration.")
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
                showToast("Add money temporarily unavailable during payment integration.")
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
        // Runs from a coroutine `finally`, which also fires on cancellation while the
        // view is being torn down (config change / process death). At that point the
        // binding may already be null, so touch it defensively instead of via binding!!.
        val swipe = bindingOrNull?.swipeRefresh ?: return
        if (show) {
            if (!swipe.isRefreshing) {
                swipe.post { swipe.isRefreshing = true }
            }
        } else {
            swipe.isRefreshing = false
        }
    }

    private fun applyFeatureSwitches() {
        RemoteConfigManager.loadCached(requireContext())
        val walletEnabled = RemoteConfigManager.isWalletEnabled()
        val rewardsEnabled = RemoteConfigManager.isFeatureEnabled("rewards")

        listOf(binding.btnAddMoney, binding.btnQuickAdd10, binding.btnQuickAdd20, binding.btnQuickAdd100).forEach {
            it.isEnabled = walletEnabled
            it.alpha = if (walletEnabled) 1f else 0.45f
        }

        binding.btnWatchAd.isEnabled = rewardsEnabled
        binding.btnWatchAd.alpha = if (rewardsEnabled) 1f else 0.45f
    }

    private fun walletErrorMessage(code: Int, fallback: String): String {
        return when (code) {
            401 -> "Session expired. Please log in again."
            429 -> "Too many requests. Please wait a moment before trying again."
            503 -> "Wallet is temporarily unavailable."
            else -> fallback
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
