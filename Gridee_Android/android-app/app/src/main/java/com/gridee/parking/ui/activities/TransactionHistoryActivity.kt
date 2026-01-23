package com.gridee.parking.ui.activities

import android.animation.Animator
import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.content.res.ColorStateList
import android.os.Bundle
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.animation.OvershootInterpolator
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.core.view.doOnLayout
import androidx.core.view.updatePadding
import androidx.dynamicanimation.animation.SpringAnimation
import androidx.dynamicanimation.animation.SpringForce
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.gridee.parking.R
import com.gridee.parking.data.api.ApiClient
import com.gridee.parking.data.model.WalletTransaction
import com.gridee.parking.databinding.ActivityTransactionHistoryBinding
import com.gridee.parking.ui.adapters.Transaction
import com.gridee.parking.ui.adapters.TransactionType
import com.gridee.parking.ui.adapters.WalletTransactionGrouping
import com.gridee.parking.ui.adapters.WalletTransactionsAdapter
import com.gridee.parking.utils.AuthSession
import com.gridee.parking.utils.BackendTimestampParser

import kotlinx.coroutines.launch
import java.util.*

class TransactionHistoryActivity : AppCompatActivity() {

    private lateinit var binding: ActivityTransactionHistoryBinding
    private lateinit var transactionsAdapter: WalletTransactionsAdapter
    private var allTransactions = mutableListOf<Transaction>()
    private val fullTransactionList = mutableListOf<Transaction>()
    private var displayedTransactions = mutableListOf<Transaction>()
    private var currentPage = 0
    private var isLoading = false
    private var isLastPage = false
    private val pageSize = 20

    private val headerCollapseRange by lazy {
        resources.getDimensionPixelSize(R.dimen.transaction_header_collapse_range)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityTransactionHistoryBinding.inflate(layoutInflater)
        setContentView(binding.root)

        window.statusBarColor = ContextCompat.getColor(this, R.color.background_primary)
        WindowInsetsControllerCompat(window, window.decorView).isAppearanceLightStatusBars = true
        
        setupToolbar()
        setupRecyclerView()
        setupFilterButtons()
        fetchAllTransactions()
    }

    private fun setupToolbar() {
        binding.btnBack.setOnClickListener {
            onBackPressedDispatcher.onBackPressed()
        }
    }

    private fun setupRecyclerView() {
        transactionsAdapter = WalletTransactionsAdapter(emptyList())
        
        binding.rvAllTransactions.apply {
            layoutManager = LinearLayoutManager(this@TransactionHistoryActivity)
            adapter = transactionsAdapter
            addItemDecoration(com.gridee.parking.ui.adapters.StickyHeaderItemDecoration(transactionsAdapter))
            
            addOnScrollListener(object : RecyclerView.OnScrollListener() {
                override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                    super.onScrolled(recyclerView, dx, dy)
                    
                    val visibleItemCount = layoutManager?.childCount ?: 0
                    val totalItemCount = layoutManager?.itemCount ?: 0
                    val firstVisibleItemPosition = (layoutManager as LinearLayoutManager).findFirstVisibleItemPosition()
                    
                    if (!isLoading && !isLastPage) {
                        if ((visibleItemCount + firstVisibleItemPosition) >= totalItemCount && firstVisibleItemPosition >= 0 && totalItemCount >= pageSize) {
                            loadMoreTransactions()
                        }
                    }
                }
            })
        }

        // Parallax setup removed

    }

    private fun setupFilterButtons() {
        // Enable smooth scrolling for filter buttons
        binding.filterScrollView.isHorizontalScrollBarEnabled = false
        binding.filterScrollView.isSmoothScrollingEnabled = true
        
        binding.btnFilterDate.setOnClickListener {
            showDateFilterModal()
        }
        
        binding.btnFilterAmount.setOnClickListener {
            showAmountFilterModal()
        }
        
        binding.btnFilterPaymentMethod.setOnClickListener {
            showPaymentFilterModal()
        }
        
        setupDateFilter()
    }

    private fun setupDateFilter() {
        binding.btnApplyDateFilter.setOnClickListener {
            applyDateFilter()
            hideDateFilterModal()
        }
        
        binding.btnClearDateFilter.setOnClickListener {
            clearDateFilter()
            hideDateFilterModal()
        }
    }
    
    private fun applyDateFilter() {
        // Find selected radio button
        val selectedId = binding.rgDateFilter.checkedRadioButtonId
        if (selectedId == -1) {
             return
        }
        
        val calendar = Calendar.getInstance()
        val now = calendar.time
        
        // Reset calendar for start calculation
        val startDate: Date? = when (selectedId) {
            R.id.rb_this_month -> {
                calendar.set(Calendar.DAY_OF_MONTH, 1)
                calendar.set(Calendar.HOUR_OF_DAY, 0)
                calendar.set(Calendar.MINUTE, 0)
                calendar.set(Calendar.SECOND, 0)
                calendar.set(Calendar.MILLISECOND, 0)
                calendar.time
            }
            R.id.rb_last_30_days -> {
                calendar.add(Calendar.DAY_OF_YEAR, -30)
                calendar.time
            }
            R.id.rb_last_90_days -> {
                calendar.add(Calendar.DAY_OF_YEAR, -90)
                calendar.time
            }
            else -> null
        }
        
        if (startDate != null) {
            // Include today by ensuring 'before' covers it (adding buffer)
            val bufferEnd = Date(now.time + 10000) 
            
            filteredTransactionList = fullTransactionList.filter { transaction ->
                val txTime = transaction.timestamp
                txTime.after(startDate) && txTime.before(bufferEnd)
            }.toMutableList()
            
            // Reload with filters
            currentPage = 0
            isLastPage = false
            isLoading = false
            loadPage(0)
            
            Toast.makeText(this, "Showing ${filteredTransactionList?.size} transactions", Toast.LENGTH_SHORT).show()
        }
    }
    
    private fun clearDateFilter() {
        binding.rgDateFilter.clearCheck()
        filteredTransactionList = null
        
        currentPage = 0
        isLastPage = false
        isLoading = false
        loadPage(0)
        
        Toast.makeText(this, "Filters cleared", Toast.LENGTH_SHORT).show()
    }



    // Parallax effect removed for cleaner, more standard UI


    private fun applyFilters() {
        renderTransactions(allTransactions)
    }

    private fun renderTransactions(transactions: List<Transaction>, isLoadingMore: Boolean = false) {
        if (transactions.isEmpty() && !isLoadingMore) {
            binding.rvAllTransactions.visibility = View.GONE
            binding.layoutEmptyState.visibility = View.VISIBLE
            binding.tvTransactionCount.visibility = View.GONE
            transactionsAdapter.updateItems(emptyList())
        } else {
            binding.rvAllTransactions.visibility = View.VISIBLE
            binding.layoutEmptyState.visibility = View.GONE
            binding.tvTransactionCount.visibility = View.VISIBLE
            binding.tvTransactionCount.text = "${transactions.size} transactions"
            val groupedItems = WalletTransactionGrouping.buildGroupedItems(transactions).toMutableList()
            
            if (isLoadingMore) {
                groupedItems.add(com.gridee.parking.ui.adapters.WalletTransactionListItem.Loading)
            }
            
            transactionsAdapter.updateItems(groupedItems)
        }
    }



    private fun loadMoreTransactions() {
        if (isLoading || isLastPage) return
        
        isLoading = true
        currentPage++
        
        // Show loading indicator at bottom
        renderTransactions(displayedTransactions, isLoadingMore = true)
        
        // Simulate network delay for "loading more" feeling
        binding.rvAllTransactions.postDelayed({
            loadPage(currentPage)
            isLoading = false
        }, 1500) // Increased delay slightly to show off the fancy loader
    }
    
    private fun fetchAllTransactions() {
        val userId = getUserId()
        if (userId == null) {
            Toast.makeText(this, "Please login to view transaction history", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        showLoading(true)
        currentPage = 0
        isLastPage = false
        filteredTransactionList = null // Reset filters on reload

        lifecycleScope.launch {
            try {
                // Fetch ALL transactions from backend
                val response = ApiClient.apiService.getWalletTransactions(userId)
                if (response.isSuccessful) {
                    val rawTransactions = response.body() ?: emptyList()
                    
                    // PROCESS IN BACKGROUND:
                    // Since we fetch ALL data at once, processing 1000+ items on the main thread
                    // would freeze the UI. We move this work to the Default dispatcher.
                    val processedTransactions = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) {
                        rawTransactions.mapNotNull { transaction ->
                            try {
                                convertToUITransaction(transaction)
                            } catch (e: Exception) {
                                null // Skip
                            }
                        }.sortedByDescending { it.timestamp }
                    }
                    
                    fullTransactionList.clear()
                    fullTransactionList.addAll(processedTransactions)
                    
                    if (fullTransactionList.isEmpty()) {
                        showEmptyState()
                    } else {
                        // Load first page
                        loadPage(0)
                    }
                } else {
                    Toast.makeText(this@TransactionHistoryActivity, "Failed to load transactions: ${response.code()}", Toast.LENGTH_SHORT).show()
                    showEmptyState()
                }
            } catch (e: Exception) {
                Toast.makeText(this@TransactionHistoryActivity, "Error loading transactions: ${e.message}", Toast.LENGTH_SHORT).show()
                showEmptyState()
            } finally {
                showLoading(false)
            }
        }
    }
    
    private var filteredTransactionList: MutableList<Transaction>? = null
    
    private fun loadPage(page: Int) {
        // Use filtered list if active, otherwise full list
        val sourceList = filteredTransactionList ?: fullTransactionList
        
        val start = page * pageSize
        if (start >= sourceList.size) {
            isLastPage = true
            return
        }
        
        var end = start + pageSize
        if (end >= sourceList.size) {
            end = sourceList.size
            isLastPage = true
        }
        
        val chunk = sourceList.subList(start, end)
        
        if (page == 0) {
            displayedTransactions.clear()
            allTransactions.clear() 
        }
        
        displayedTransactions.addAll(chunk)
        allTransactions.addAll(chunk) // Sync lists
        
        renderTransactions(displayedTransactions)
    }

    private fun convertToUITransaction(walletTransaction: WalletTransaction): Transaction {
        val id = walletTransaction.id?.trim()
        val timestamp = walletTransaction.timestamp?.trim()
        
        if (id.isNullOrBlank() || timestamp.isNullOrBlank()) {
            throw IllegalArgumentException("Transaction missing required fields")
        }
        
        val parsedTimestamp = BackendTimestampParser.parse(timestamp)

        val rawDescription = walletTransaction.description?.trim()
        val normalizedDescription = rawDescription?.ifBlank { null }
        val descriptionLower = normalizedDescription?.lowercase(Locale.getDefault())
        val typeNorm = walletTransaction.type?.trim()?.lowercase(Locale.getDefault())
        val backendType = walletTransaction.type?.trim()?.uppercase(Locale.getDefault())
        val statusNorm = walletTransaction.status?.trim()?.lowercase(Locale.getDefault())
        val amountValue = walletTransaction.amount ?: 0.0

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
            id = id,
            type = transactionType,
            amount = displayAmount,
            description = description,
            timestamp = parsedTimestamp,
            balanceAfter = walletTransaction.balanceAfter ?: 0.0,
            paymentMethod = null,
            status = walletTransaction.status
        )
    }

    companion object {
        private const val REWARD_AMOUNT_RUPEES = 20.0
    }



    private fun showEmptyState() {
        allTransactions.clear()
        renderTransactions(emptyList())
    }

    private fun showLoading(show: Boolean) {
        binding.progressBar.visibility = if (show) View.VISIBLE else View.GONE
        if (show) {
            binding.rvAllTransactions.visibility = View.GONE
            binding.layoutEmptyState.visibility = View.GONE
        } else if (binding.layoutEmptyState.visibility != View.VISIBLE) {
            binding.rvAllTransactions.visibility = View.VISIBLE
        }
    }

    private fun getUserId(): String? {
        return AuthSession.getUserId(this)
    }

    private fun showDateFilterModal() {
        val modal = binding.dateFilterModal
        val overlay = binding.modalOverlay
        val closeButton = modal.findViewById<View>(R.id.btn_close_date)
        
        // Get screen height for consistent positioning
        val displayMetrics = resources.displayMetrics
        val screenHeight = displayMetrics.heightPixels.toFloat()
        val slideDistance = screenHeight * 0.8f // Start 80% of screen height below
        
        // Set initial position off screen
        modal.translationY = slideDistance
        modal.alpha = 0f
        overlay.alpha = 0f
        
        // Make modal and overlay visible
        modal.visibility = View.VISIBLE
        overlay.visibility = View.VISIBLE
        
        // Animate overlay fade in with blur effect
        val overlayAnimator = ObjectAnimator.ofFloat(overlay, "alpha", 0f, 1f).apply {
            duration = 350
            interpolator = AccelerateDecelerateInterpolator()
        }
        
        // Animate modal slide up with spring animation
        val slideUpAnimation = SpringAnimation(modal, SpringAnimation.TRANSLATION_Y, 0f).apply {
            spring = SpringForce(0f).apply {
                dampingRatio = SpringForce.DAMPING_RATIO_MEDIUM_BOUNCY
                stiffness = SpringForce.STIFFNESS_LOW
            }
        }
        
        // Animate modal fade in
        val modalAlphaAnimator = ObjectAnimator.ofFloat(modal, "alpha", 0f, 1f).apply {
            duration = 400
            interpolator = OvershootInterpolator(0.6f) // Slightly less overshoot for smoother feel
        }
        
        // Start animations
        overlayAnimator.start()
        slideUpAnimation.start()
        modalAlphaAnimator.start()
        
        // Animate arrow up
        animateArrow(binding.ivArrowDate, true)
        
        // Set up close modal functionality
        overlay.setOnClickListener {
            hideDateFilterModal()
        }
        closeButton.setOnClickListener {
            hideDateFilterModal()
        }
    }
    
    private fun hideDateFilterModal() {
        val modal = binding.dateFilterModal
        val overlay = binding.modalOverlay
        
        // Animate overlay fade out with slower, smoother timing and blur effect
        val overlayAnimator = ObjectAnimator.ofFloat(overlay, "alpha", 1f, 0f).apply {
            duration = 800 // Much slower fade out for smooth effect
            interpolator = AccelerateDecelerateInterpolator()
        }
        
        // Get screen height for proper slide down distance
        val displayMetrics = resources.displayMetrics
        val screenHeight = displayMetrics.heightPixels.toFloat()
        val slideDistance = screenHeight * 0.9f // Slide further down for smoother exit
        
        // Animate modal slide down with smoother spring animation
        val slideDownAnimation = SpringAnimation(modal, SpringAnimation.TRANSLATION_Y, slideDistance).apply {
            spring = SpringForce(slideDistance).apply {
                dampingRatio = SpringForce.DAMPING_RATIO_MEDIUM_BOUNCY // Smoother bounce
                stiffness = SpringForce.STIFFNESS_LOW // Slower, more graceful movement
            }
        }
        
        // Animate modal fade out with smoother curve
        val modalAlphaAnimator = ObjectAnimator.ofFloat(modal, "alpha", 1f, 0f).apply {
            duration = 400 // Longer duration for smoother fade
            interpolator = AccelerateDecelerateInterpolator()
            startDelay = 50 // Small delay to let slide start first
        }
        
        // Hide modal and overlay after animations complete
        slideDownAnimation.addEndListener { _, _, _, _ ->
            modal.visibility = View.GONE
            overlay.visibility = View.GONE
            // Reset positions for next time
            modal.translationY = slideDistance
            modal.alpha = 0f
            overlay.alpha = 0f
        }
        
        // Start animations simultaneously for smooth effect
        overlayAnimator.start()
        slideDownAnimation.start()
        modalAlphaAnimator.start()
        
        // Animate arrow down
        animateArrow(binding.ivArrowDate, false)
    }

    private fun showAmountFilterModal() {
        val modal = binding.amountFilterModal
        val overlay = binding.modalOverlay
        val closeButton = modal.findViewById<View>(R.id.btn_close_amount)
        
        // Get screen height for consistent positioning
        val displayMetrics = resources.displayMetrics
        val screenHeight = displayMetrics.heightPixels.toFloat()
        val slideDistance = screenHeight * 0.8f // Start 80% of screen height below
        
        // Set initial position off screen
        modal.translationY = slideDistance
        modal.alpha = 0f
        overlay.alpha = 0f
        
        // Make modal and overlay visible
        modal.visibility = View.VISIBLE
        overlay.visibility = View.VISIBLE
        
        // Animate overlay fade in with blur effect
        val overlayAnimator = ObjectAnimator.ofFloat(overlay, "alpha", 0f, 1f).apply {
            duration = 350
            interpolator = AccelerateDecelerateInterpolator()
        }
        
        // Animate modal slide up with spring animation
        val slideUpAnimation = SpringAnimation(modal, SpringAnimation.TRANSLATION_Y, 0f).apply {
            spring = SpringForce(0f).apply {
                dampingRatio = SpringForce.DAMPING_RATIO_MEDIUM_BOUNCY
                stiffness = SpringForce.STIFFNESS_LOW
            }
        }
        
        // Animate modal fade in
        val modalAlphaAnimator = ObjectAnimator.ofFloat(modal, "alpha", 0f, 1f).apply {
            duration = 400
            interpolator = OvershootInterpolator(0.6f) // Slightly less overshoot for smoother feel
        }
        
        // Start animations
        overlayAnimator.start()
        slideUpAnimation.start()
        modalAlphaAnimator.start()
        
        // Animate arrow up
        animateArrow(binding.ivArrowAmount, true)
        
        // Set up close modal functionality
        overlay.setOnClickListener {
            hideAmountFilterModal()
        }
        closeButton.setOnClickListener {
            hideAmountFilterModal()
        }
    }
    
    private fun hideAmountFilterModal() {
        val modal = binding.amountFilterModal
        val overlay = binding.modalOverlay
        
        // Animate overlay fade out with faster timing
        val overlayAnimator = ObjectAnimator.ofFloat(overlay, "alpha", 1f, 0f).apply {
            duration = 300
            interpolator = AccelerateDecelerateInterpolator()
        }
        
        // Get screen height for proper slide down distance
        val displayMetrics = resources.displayMetrics
        val screenHeight = displayMetrics.heightPixels.toFloat()
        val slideDistance = screenHeight * 0.8f // Slide down 80% of screen height
        
        // Animate modal slide down with spring animation - more bouncy and natural
        val slideDownAnimation = SpringAnimation(modal, SpringAnimation.TRANSLATION_Y, slideDistance).apply {
            spring = SpringForce(slideDistance).apply {
                dampingRatio = SpringForce.DAMPING_RATIO_LOW_BOUNCY // More spring bounce
                stiffness = SpringForce.STIFFNESS_MEDIUM // Good responsiveness
            }
        }
        
        // Animate modal fade out with smoother curve
        val modalAlphaAnimator = ObjectAnimator.ofFloat(modal, "alpha", 1f, 0f).apply {
            duration = 400 // Longer duration for smoother fade
            interpolator = AccelerateDecelerateInterpolator()
            startDelay = 50 // Small delay to let slide start first
        }
        
        // Hide modal and overlay after animations complete
        slideDownAnimation.addEndListener { _, _, _, _ ->
            modal.visibility = View.GONE
            overlay.visibility = View.GONE
            // Reset positions for next time
            modal.translationY = slideDistance
            modal.alpha = 0f
            overlay.alpha = 0f
        }
        
        // Start animations simultaneously for smooth effect
        overlayAnimator.start()
        slideDownAnimation.start()
        modalAlphaAnimator.start()
        
        // Animate arrow down
        animateArrow(binding.ivArrowAmount, false)
    }

    private fun showPaymentFilterModal() {
        val modal = binding.paymentFilterModal
        val overlay = binding.modalOverlay
        val closeButton = modal.findViewById<View>(R.id.btn_close_payment)
        
        // Get screen height for consistent positioning
        val displayMetrics = resources.displayMetrics
        val screenHeight = displayMetrics.heightPixels.toFloat()
        val slideDistance = screenHeight * 0.8f // Start 80% of screen height below
        
        // Set initial position off screen
        modal.translationY = slideDistance
        modal.alpha = 0f
        overlay.alpha = 0f
        
        // Make modal and overlay visible
        modal.visibility = View.VISIBLE
        overlay.visibility = View.VISIBLE
        
        // Animate overlay fade in with blur effect
        val overlayAnimator = ObjectAnimator.ofFloat(overlay, "alpha", 0f, 1f).apply {
            duration = 350
            interpolator = AccelerateDecelerateInterpolator()
        }
        
        // Animate modal slide up with spring animation
        val slideUpAnimation = SpringAnimation(modal, SpringAnimation.TRANSLATION_Y, 0f).apply {
            spring = SpringForce(0f).apply {
                dampingRatio = SpringForce.DAMPING_RATIO_MEDIUM_BOUNCY
                stiffness = SpringForce.STIFFNESS_LOW
            }
        }
        
        // Animate modal fade in
        val modalAlphaAnimator = ObjectAnimator.ofFloat(modal, "alpha", 0f, 1f).apply {
            duration = 400
            interpolator = OvershootInterpolator(0.6f) // Slightly less overshoot for smoother feel
        }
        
        // Start animations
        overlayAnimator.start()
        slideUpAnimation.start()
        modalAlphaAnimator.start()
        
        // Animate arrow up
        animateArrow(binding.ivArrowPayment, true)
        
        // Set up close modal functionality
        overlay.setOnClickListener {
            hidePaymentFilterModal()
        }
        closeButton.setOnClickListener {
            hidePaymentFilterModal()
        }
    }
    
    private fun hidePaymentFilterModal() {
        val modal = binding.paymentFilterModal
        val overlay = binding.modalOverlay
        
        // Animate overlay fade out with faster timing
        val overlayAnimator = ObjectAnimator.ofFloat(overlay, "alpha", 1f, 0f).apply {
            duration = 300
            interpolator = AccelerateDecelerateInterpolator()
        }
        
        // Get screen height for proper slide down distance
        val displayMetrics = resources.displayMetrics
        val screenHeight = displayMetrics.heightPixels.toFloat()
        val slideDistance = screenHeight * 0.8f // Slide down 80% of screen height
        
        // Animate modal slide down with spring animation - more bouncy and natural
        val slideDownAnimation = SpringAnimation(modal, SpringAnimation.TRANSLATION_Y, slideDistance).apply {
            spring = SpringForce(slideDistance).apply {
                dampingRatio = SpringForce.DAMPING_RATIO_LOW_BOUNCY // More spring bounce
                stiffness = SpringForce.STIFFNESS_MEDIUM // Good responsiveness
            }
        }
        
        // Animate modal fade out with smoother curve
        val modalAlphaAnimator = ObjectAnimator.ofFloat(modal, "alpha", 1f, 0f).apply {
            duration = 400 // Longer duration for smoother fade
            interpolator = AccelerateDecelerateInterpolator()
            startDelay = 50 // Small delay to let slide start first
        }
        
        // Hide modal and overlay after animations complete
        slideDownAnimation.addEndListener { _, _, _, _ ->
            modal.visibility = View.GONE
            overlay.visibility = View.GONE
            // Reset positions for next time
            modal.translationY = slideDistance
            modal.alpha = 0f
            overlay.alpha = 0f
        }
        
        // Start animations simultaneously for smooth effect
        overlayAnimator.start()
        slideDownAnimation.start()
        modalAlphaAnimator.start()
        
        // Animate arrow down
        animateArrow(binding.ivArrowPayment, false)
    }

    private fun animateArrow(view: View, rotateUp: Boolean) {
        val rotation = if (rotateUp) 180f else 0f
        ObjectAnimator.ofFloat(view, "rotation", rotation).apply {
            duration = 300
            interpolator = AccelerateDecelerateInterpolator()
            start()
        }
    }
}
