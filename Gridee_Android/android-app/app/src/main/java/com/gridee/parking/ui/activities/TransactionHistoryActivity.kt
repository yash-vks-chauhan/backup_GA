package com.gridee.parking.ui.activities

import android.animation.ObjectAnimator
import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.view.Window
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.animation.OvershootInterpolator
import android.view.animation.PathInterpolator
import android.widget.Toast
import androidx.activity.addCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
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
import com.gridee.parking.ui.adapters.WalletTransactionListItem
import com.gridee.parking.ui.adapters.WalletTransactionsAdapter
import com.gridee.parking.utils.AuthSession
import com.gridee.parking.utils.BackendTimestampParser
import com.gridee.parking.utils.NotificationHelper
import com.google.android.material.shape.ShapeAppearanceModel
import com.google.android.material.transition.platform.MaterialContainerTransform
import com.google.android.material.transition.platform.MaterialContainerTransformSharedElementCallback

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

    private var currentSubtitleText: String? = null
    private var isSubtitleAnimating = false
    private var isClosing = false

    override fun onCreate(savedInstanceState: Bundle?) {
        window.requestFeature(Window.FEATURE_ACTIVITY_TRANSITIONS)
        setEnterSharedElementCallback(MaterialContainerTransformSharedElementCallback())
        configureSharedElementTransitions()

        super.onCreate(savedInstanceState)
        binding = ActivityTransactionHistoryBinding.inflate(layoutInflater)
        setContentView(binding.root)
        ViewCompat.setTransitionName(binding.root, VIEW_ALL_TRANSITION_NAME)
        runCatching { window.sharedElementsUseOverlay = false }

        window.statusBarColor = ContextCompat.getColor(this, R.color.background_primary)
        val isDarkMode = (resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK) == android.content.res.Configuration.UI_MODE_NIGHT_YES
        WindowInsetsControllerCompat(window, window.decorView).isAppearanceLightStatusBars = !isDarkMode

        onBackPressedDispatcher.addCallback(this) {
            closeWithAnimation()
        }

        setupToolbar()
        setupRecyclerView()
        setupFrostedToolbar()
        setupFilterButtons()
        fetchAllTransactions()
    }

    private fun setupToolbar() {
        binding.btnBack.setOnClickListener {
            closeWithAnimation()
        }
    }

    private fun configureSharedElementTransitions() {
        val pillShape = ShapeAppearanceModel.builder()
            .setAllCornerSizes(16f.dpToPx())
            .build()
        val fullShape = ShapeAppearanceModel.builder()
            .setAllCornerSizes(0f)
            .build()

        window.sharedElementEnterTransition = MaterialContainerTransform().apply {
            addTarget(VIEW_ALL_TRANSITION_NAME)
            duration = 500
            scrimColor = Color.TRANSPARENT
            setAllContainerColors(Color.TRANSPARENT)
            drawingViewId = android.R.id.content
            isElevationShadowEnabled = false
            startShapeAppearanceModel = pillShape
            endShapeAppearanceModel = fullShape
            interpolator = PathInterpolator(0.05f, 0.7f, 0.1f, 1.0f)
            fadeMode = MaterialContainerTransform.FADE_MODE_CROSS
        }

        window.sharedElementReturnTransition = MaterialContainerTransform().apply {
            addTarget(VIEW_ALL_TRANSITION_NAME)
            duration = 450
            scrimColor = Color.TRANSPARENT
            setAllContainerColors(Color.TRANSPARENT)
            drawingViewId = android.R.id.content
            isElevationShadowEnabled = false
            startShapeAppearanceModel = fullShape
            endShapeAppearanceModel = pillShape
            interpolator = PathInterpolator(0.05f, 0.7f, 0.1f, 1.0f)
            fadeMode = MaterialContainerTransform.FADE_MODE_CROSS
        }
    }

    private fun closeWithAnimation() {
        if (isClosing) return
        isClosing = true
        finishAfterTransition()
    }

    private fun setupRecyclerView() {
        transactionsAdapter = WalletTransactionsAdapter(emptyList())

        binding.rvAllTransactions.apply {
            layoutManager = LinearLayoutManager(this@TransactionHistoryActivity)
            adapter = transactionsAdapter
            // No sticky header decoration — using frosted header + dynamic subtitle instead
        }

        // Set RecyclerView top padding dynamically after header is measured
        binding.layoutHeader.doOnLayout { header ->
            binding.rvAllTransactions.updatePadding(top = header.height + 4f.dpToPx().toInt())
        }
    }

    private fun setupFrostedToolbar() {
        val frostView = binding.viewToolbarFrost
        val subtitleView = binding.tvSectionSubtitle

        // Start state — fully clear, subtitle invisible
        frostView.alpha = 0f
        subtitleView.alpha = 0f

        binding.rvAllTransactions.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                super.onScrolled(recyclerView, dx, dy)

                val layoutManager = recyclerView.layoutManager as? LinearLayoutManager ?: return
                val firstVisiblePosition = layoutManager.findFirstVisibleItemPosition()
                val totalScroll = recyclerView.computeVerticalScrollOffset()

                // ── Frost alpha: ramps over 120dp with ease-out cubic ──
                val deadZone = 16f.dpToPx()
                val activeScroll = (totalScroll - deadZone).coerceAtLeast(0f)

                val frostRange = 150f.dpToPx()
                val rawFrost = (activeScroll / frostRange).coerceIn(0f, 1f)
                val t = 1f - rawFrost
                val easedFrost = 1f - (t * t * t)
                frostView.alpha = easedFrost

                // ── Dynamic subtitle: detect current visible section ──
                val subtitleRange = 100f.dpToPx()
                val rawSubtitle = (activeScroll / subtitleRange).coerceIn(0f, 1f)

                if (totalScroll <= 0) {
                    // Fully scrolled to top — reset everything
                    frostView.alpha = 0f
                    subtitleView.animate().cancel()
                    subtitleView.alpha = 0f
                    subtitleView.translationY = 0f
                    currentSubtitleText = null
                    isSubtitleAnimating = false
                    return
                }

                // Find the section header for the first visible item
                val sectionTitle = findCurrentSectionTitle(firstVisiblePosition)

                // Subtitle alpha: ease in over first 60dp then lock at full
                val subtitleAlpha = if (rawSubtitle >= 1f) 1f else {
                    // Smooth ease-in curve
                    val s = rawSubtitle
                    s * s * (3f - 2f * s) // smoothstep
                }

                if (sectionTitle != null) {
                    if (sectionTitle != currentSubtitleText) {
                        // Crossfade to new section title with slide
                        crossfadeSubtitle(subtitleView, sectionTitle, subtitleAlpha)
                        currentSubtitleText = sectionTitle
                    } else if (!isSubtitleAnimating) {
                        // Steady state — just track alpha
                        subtitleView.alpha = subtitleAlpha
                    }
                } else {
                    subtitleView.alpha = 0f
                    subtitleView.translationY = 0f
                    currentSubtitleText = null
                }

                // ── Pagination check ──
                val visibleItemCount = layoutManager.childCount
                val totalItemCount = layoutManager.itemCount
                if (!isLoading && !isLastPage) {
                    if ((visibleItemCount + firstVisiblePosition) >= totalItemCount && firstVisiblePosition >= 0 && totalItemCount >= pageSize) {
                        loadMoreTransactions()
                    }
                }
            }
        })
    }

    private fun findCurrentSectionTitle(firstVisiblePosition: Int): String? {
        if (firstVisiblePosition == RecyclerView.NO_POSITION) return null
        val items = transactionsAdapter.getItems()
        // Walk backwards from the first visible position to find the nearest header
        for (i in firstVisiblePosition downTo 0) {
            if (i < items.size) {
                val item = items[i]
                if (item is WalletTransactionListItem.Header) {
                    return item.title
                }
            }
        }
        return null
    }

    private fun crossfadeSubtitle(view: android.widget.TextView, newText: String, targetAlpha: Float) {
        if (isSubtitleAnimating) return

        isSubtitleAnimating = true
        val slideDistance = 6f.dpToPx()

        // Phase 1: Fade out + slide up current text
        view.animate().cancel()
        view.animate()
            .alpha(0f)
            .translationY(-slideDistance)
            .setDuration(200)
            .setInterpolator(AccelerateDecelerateInterpolator())
            .withEndAction {
                // Swap text while invisible
                view.text = newText
                // Reset position below for slide-up entrance
                view.translationY = slideDistance

                // Phase 2: Fade in + slide up into position
                view.animate()
                    .alpha(targetAlpha.coerceAtLeast(0.9f))
                    .translationY(0f)
                    .setDuration(280)
                    .setInterpolator(android.view.animation.DecelerateInterpolator(1.5f))
                    .withEndAction {
                        isSubtitleAnimating = false
                    }
                    .start()
            }
            .start()
    }

    private fun setupFilterButtons() {
        binding.filterScrollView.isHorizontalScrollBarEnabled = false
        binding.filterScrollView.isSmoothScrollingEnabled = true

        binding.btnFilterDate.setOnClickListener {
            it.performHapticFeedback(android.view.HapticFeedbackConstants.VIRTUAL_KEY)
            showFilterComingSoon()
        }

        binding.btnFilterAmount.setOnClickListener {
            it.performHapticFeedback(android.view.HapticFeedbackConstants.VIRTUAL_KEY)
            showFilterComingSoon()
        }

        binding.btnFilterPaymentMethod.setOnClickListener {
            it.performHapticFeedback(android.view.HapticFeedbackConstants.VIRTUAL_KEY)
            showFilterComingSoon()
        }
    }

    private fun showFilterComingSoon() {
        NotificationHelper.showInfoNoIcon(
            parent = binding.root,
            title = "Coming Soon",
            message = ""
        )
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
        val selectedId = binding.rgDateFilter.checkedRadioButtonId
        if (selectedId == -1) {
             return
        }

        val calendar = Calendar.getInstance()
        val now = calendar.time

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
            val bufferEnd = Date(now.time + 10000)

            filteredTransactionList = fullTransactionList.filter { transaction ->
                val txTime = transaction.timestamp
                txTime.after(startDate) && txTime.before(bufferEnd)
            }.toMutableList()

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
                groupedItems.add(WalletTransactionListItem.Loading)
            }

            transactionsAdapter.updateItems(groupedItems)
        }

        // Reset subtitle state when data changes
        currentSubtitleText = null
        isSubtitleAnimating = false
        binding.tvSectionSubtitle.animate().cancel()
        binding.tvSectionSubtitle.alpha = 0f
        binding.tvSectionSubtitle.translationY = 0f
    }

    private fun loadMoreTransactions() {
        if (isLoading || isLastPage) return

        isLoading = true
        currentPage++

        renderTransactions(displayedTransactions, isLoadingMore = true)

        binding.rvAllTransactions.postDelayed({
            loadPage(currentPage)
            isLoading = false
        }, 1500)
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
        filteredTransactionList = null

        lifecycleScope.launch {
            try {
                val response = ApiClient.apiService.getWalletTransactions(
                    userId = userId,
                    page = 0,
                    size = 1000,
                    sort = listOf("timestamp", "desc")
                )
                if (response.isSuccessful) {
                    val rawTransactions = response.body()?.content.orEmpty()

                    val processedTransactions = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) {
                        rawTransactions.mapNotNull { transaction ->
                            try {
                                convertToUITransaction(transaction)
                            } catch (e: Exception) {
                                null
                            }
                        }.sortedByDescending { it.timestamp }
                    }

                    fullTransactionList.clear()
                    fullTransactionList.addAll(processedTransactions)

                    if (fullTransactionList.isEmpty()) {
                        showEmptyState()
                    } else {
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
        allTransactions.addAll(chunk)

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
        val locationLabel = listOfNotNull(
            walletTransaction.lotName?.trim()?.takeIf { it.isNotEmpty() }
                ?: walletTransaction.lotId?.trim()?.takeIf { it.isNotEmpty() },
            walletTransaction.spotId?.trim()?.takeIf { it.isNotEmpty() }?.let { "Spot $it" }
        ).joinToString(" • ").takeIf { it.isNotEmpty() }
        val displayDescription = if (locationLabel != null && resolvedBookingRelated) {
            "$description • $locationLabel"
        } else {
            description
        }

        return Transaction(
            id = id,
            type = transactionType,
            amount = displayAmount,
            description = displayDescription,
            timestamp = parsedTimestamp,
            balanceAfter = walletTransaction.balanceAfter ?: 0.0,
            paymentMethod = null,
            status = walletTransaction.status
        )
    }

    companion object {
        private const val REWARD_AMOUNT_RUPEES = 20.0
        const val VIEW_ALL_TRANSITION_NAME = "wallet_view_all_transition"
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

        val displayMetrics = resources.displayMetrics
        val screenHeight = displayMetrics.heightPixels.toFloat()
        val slideDistance = screenHeight * 0.8f

        modal.translationY = slideDistance
        modal.alpha = 0f
        overlay.alpha = 0f

        modal.visibility = View.VISIBLE
        overlay.visibility = View.VISIBLE

        val overlayAnimator = ObjectAnimator.ofFloat(overlay, "alpha", 0f, 1f).apply {
            duration = 350
            interpolator = AccelerateDecelerateInterpolator()
        }

        val slideUpAnimation = SpringAnimation(modal, SpringAnimation.TRANSLATION_Y, 0f).apply {
            spring = SpringForce(0f).apply {
                dampingRatio = SpringForce.DAMPING_RATIO_MEDIUM_BOUNCY
                stiffness = SpringForce.STIFFNESS_LOW
            }
        }

        val modalAlphaAnimator = ObjectAnimator.ofFloat(modal, "alpha", 0f, 1f).apply {
            duration = 400
            interpolator = OvershootInterpolator(0.6f)
        }

        overlayAnimator.start()
        slideUpAnimation.start()
        modalAlphaAnimator.start()

        animateArrow(binding.ivArrowDate, true)

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

        val overlayAnimator = ObjectAnimator.ofFloat(overlay, "alpha", 1f, 0f).apply {
            duration = 800
            interpolator = AccelerateDecelerateInterpolator()
        }

        val displayMetrics = resources.displayMetrics
        val screenHeight = displayMetrics.heightPixels.toFloat()
        val slideDistance = screenHeight * 0.9f

        val slideDownAnimation = SpringAnimation(modal, SpringAnimation.TRANSLATION_Y, slideDistance).apply {
            spring = SpringForce(slideDistance).apply {
                dampingRatio = SpringForce.DAMPING_RATIO_MEDIUM_BOUNCY
                stiffness = SpringForce.STIFFNESS_LOW
            }
        }

        val modalAlphaAnimator = ObjectAnimator.ofFloat(modal, "alpha", 1f, 0f).apply {
            duration = 400
            interpolator = AccelerateDecelerateInterpolator()
            startDelay = 50
        }

        slideDownAnimation.addEndListener { _, _, _, _ ->
            modal.visibility = View.GONE
            overlay.visibility = View.GONE
            modal.translationY = slideDistance
            modal.alpha = 0f
            overlay.alpha = 0f
        }

        overlayAnimator.start()
        slideDownAnimation.start()
        modalAlphaAnimator.start()

        animateArrow(binding.ivArrowDate, false)
    }

    private fun showAmountFilterModal() {
        val modal = binding.amountFilterModal
        val overlay = binding.modalOverlay
        val closeButton = modal.findViewById<View>(R.id.btn_close_amount)

        val displayMetrics = resources.displayMetrics
        val screenHeight = displayMetrics.heightPixels.toFloat()
        val slideDistance = screenHeight * 0.8f

        modal.translationY = slideDistance
        modal.alpha = 0f
        overlay.alpha = 0f

        modal.visibility = View.VISIBLE
        overlay.visibility = View.VISIBLE

        val overlayAnimator = ObjectAnimator.ofFloat(overlay, "alpha", 0f, 1f).apply {
            duration = 350
            interpolator = AccelerateDecelerateInterpolator()
        }

        val slideUpAnimation = SpringAnimation(modal, SpringAnimation.TRANSLATION_Y, 0f).apply {
            spring = SpringForce(0f).apply {
                dampingRatio = SpringForce.DAMPING_RATIO_MEDIUM_BOUNCY
                stiffness = SpringForce.STIFFNESS_LOW
            }
        }

        val modalAlphaAnimator = ObjectAnimator.ofFloat(modal, "alpha", 0f, 1f).apply {
            duration = 400
            interpolator = OvershootInterpolator(0.6f)
        }

        overlayAnimator.start()
        slideUpAnimation.start()
        modalAlphaAnimator.start()

        animateArrow(binding.ivArrowAmount, true)

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

        val overlayAnimator = ObjectAnimator.ofFloat(overlay, "alpha", 1f, 0f).apply {
            duration = 300
            interpolator = AccelerateDecelerateInterpolator()
        }

        val displayMetrics = resources.displayMetrics
        val screenHeight = displayMetrics.heightPixels.toFloat()
        val slideDistance = screenHeight * 0.8f

        val slideDownAnimation = SpringAnimation(modal, SpringAnimation.TRANSLATION_Y, slideDistance).apply {
            spring = SpringForce(slideDistance).apply {
                dampingRatio = SpringForce.DAMPING_RATIO_LOW_BOUNCY
                stiffness = SpringForce.STIFFNESS_MEDIUM
            }
        }

        val modalAlphaAnimator = ObjectAnimator.ofFloat(modal, "alpha", 1f, 0f).apply {
            duration = 400
            interpolator = AccelerateDecelerateInterpolator()
            startDelay = 50
        }

        slideDownAnimation.addEndListener { _, _, _, _ ->
            modal.visibility = View.GONE
            overlay.visibility = View.GONE
            modal.translationY = slideDistance
            modal.alpha = 0f
            overlay.alpha = 0f
        }

        overlayAnimator.start()
        slideDownAnimation.start()
        modalAlphaAnimator.start()

        animateArrow(binding.ivArrowAmount, false)
    }

    private fun showPaymentFilterModal() {
        val modal = binding.paymentFilterModal
        val overlay = binding.modalOverlay
        val closeButton = modal.findViewById<View>(R.id.btn_close_payment)

        val displayMetrics = resources.displayMetrics
        val screenHeight = displayMetrics.heightPixels.toFloat()
        val slideDistance = screenHeight * 0.8f

        modal.translationY = slideDistance
        modal.alpha = 0f
        overlay.alpha = 0f

        modal.visibility = View.VISIBLE
        overlay.visibility = View.VISIBLE

        val overlayAnimator = ObjectAnimator.ofFloat(overlay, "alpha", 0f, 1f).apply {
            duration = 350
            interpolator = AccelerateDecelerateInterpolator()
        }

        val slideUpAnimation = SpringAnimation(modal, SpringAnimation.TRANSLATION_Y, 0f).apply {
            spring = SpringForce(0f).apply {
                dampingRatio = SpringForce.DAMPING_RATIO_MEDIUM_BOUNCY
                stiffness = SpringForce.STIFFNESS_LOW
            }
        }

        val modalAlphaAnimator = ObjectAnimator.ofFloat(modal, "alpha", 0f, 1f).apply {
            duration = 400
            interpolator = OvershootInterpolator(0.6f)
        }

        overlayAnimator.start()
        slideUpAnimation.start()
        modalAlphaAnimator.start()

        animateArrow(binding.ivArrowPayment, true)

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

        val overlayAnimator = ObjectAnimator.ofFloat(overlay, "alpha", 1f, 0f).apply {
            duration = 300
            interpolator = AccelerateDecelerateInterpolator()
        }

        val displayMetrics = resources.displayMetrics
        val screenHeight = displayMetrics.heightPixels.toFloat()
        val slideDistance = screenHeight * 0.8f

        val slideDownAnimation = SpringAnimation(modal, SpringAnimation.TRANSLATION_Y, slideDistance).apply {
            spring = SpringForce(slideDistance).apply {
                dampingRatio = SpringForce.DAMPING_RATIO_LOW_BOUNCY
                stiffness = SpringForce.STIFFNESS_MEDIUM
            }
        }

        val modalAlphaAnimator = ObjectAnimator.ofFloat(modal, "alpha", 1f, 0f).apply {
            duration = 400
            interpolator = AccelerateDecelerateInterpolator()
            startDelay = 50
        }

        slideDownAnimation.addEndListener { _, _, _, _ ->
            modal.visibility = View.GONE
            overlay.visibility = View.GONE
            modal.translationY = slideDistance
            modal.alpha = 0f
            overlay.alpha = 0f
        }

        overlayAnimator.start()
        slideDownAnimation.start()
        modalAlphaAnimator.start()

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

    private fun Float.dpToPx(): Float {
        return this * resources.displayMetrics.density
    }
}
