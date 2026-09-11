package com.gridee.parking.ui.fragments

import android.content.Intent
import android.os.SystemClock
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
import com.gridee.parking.GrideeApplication
import com.gridee.parking.config.RemoteConfigManager
import com.gridee.parking.data.model.WalletTransaction
import com.gridee.parking.data.repository.WalletRepository
import com.gridee.parking.databinding.FragmentWalletNewBinding
import com.gridee.parking.ui.activities.TransactionHistoryActivity
import com.gridee.parking.ui.adapters.Transaction
import com.gridee.parking.ui.adapters.TransactionType
import com.gridee.parking.ui.adapters.WalletTransactionGrouping
import com.gridee.parking.ui.adapters.WalletTransactionText
import com.gridee.parking.ui.adapters.WalletTransactionVisibility
import com.gridee.parking.ui.adapters.WalletTransactionsAdapter
import com.gridee.parking.ui.base.BaseTabFragment
import com.gridee.parking.ui.compose.DotLottieAnimation
import com.gridee.parking.ui.compose.DotLottieSource
import com.gridee.parking.ui.compose.Mode
import com.gridee.parking.ui.views.SkeletonShimmer
import com.gridee.parking.ui.wallet.WalletAddMoneyActivity
import com.gridee.parking.ui.wallet.WalletRefreshEvent
import com.gridee.parking.ui.wallet.WalletRefreshEvents
import com.gridee.parking.ui.wallet.WalletRefreshSource
import com.gridee.parking.ui.wallet.WalletTopUpLauncher
import com.gridee.parking.utils.AppForegroundTracker
import com.gridee.parking.utils.AuthSession
import com.gridee.parking.utils.BackendTimestampParser
import com.gridee.parking.utils.WalletCache
import androidx.dynamicanimation.animation.DynamicAnimation
import androidx.dynamicanimation.animation.SpringAnimation
import androidx.dynamicanimation.animation.SpringForce
import com.google.android.material.transition.platform.MaterialContainerTransformSharedElementCallback
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.*

class WalletFragmentNew : BaseTabFragment<FragmentWalletNewBinding>() {

    private lateinit var transactionsAdapter: WalletTransactionsAdapter
    private var currentBalance = 0.0
    private var displayedBalance = 0.0
    private var balanceAnimator: android.animation.ValueAnimator? = null
    private var balanceColorAnimator: android.animation.ValueAnimator? = null
    private var shimmerAnimator: android.animation.ValueAnimator? = null
    private var skeletonAnimator: android.animation.ValueAnimator? = null
    private var hasLoadedBalance = false
    private var hasLoadedTransactions = false
    private var hasAnimatedCardIn = false
    private var walletLoadJob: Job? = null
    private var handledForegroundGeneration = Long.MIN_VALUE
    private var hasRequestedBalance = false
    private var hasRequestedTransactions = false
    private var lastManualRefreshElapsedMs = Long.MIN_VALUE
    private var refreshAfterCurrentLoad = false
    private var forceBalanceRefreshAfterCurrentLoad = false
    private var forceTransactionsRefreshAfterCurrentLoad = false
    private var includeTransactionsAfterCurrentLoad = false
    private val walletRepository by lazy {
        GrideeApplication.instance.repositories.walletRepository
    }
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
        SkeletonShimmer.populate(binding.layoutTransactionSkeleton, WALLET_SKELETON_ROWS)

        // Stale-while-revalidate: if we have a cached snapshot, paint it instantly
        // (no shimmer, no skeleton) and let the network refresh reconcile in the
        // background. Only the true first-ever open falls back to the loading state.
        val restored = restoreFromCache()
        if (!restored) {
            startBalanceShimmer()
        }
        observeWalletRefreshEvents()
    }

    private fun restoreFromCache(): Boolean {
        val userId = getUserId() ?: return false
        val snapshot = WalletCache.get(requireContext(), userId) ?: return false

        // Balance — show immediately; a background refresh will count-up if it changed.
        currentBalance = snapshot.balance
        displayedBalance = snapshot.balance
        hasLoadedBalance = true
        renderBalance(snapshot.balance, isAnimating = false)

        // Transactions — render immediately, no skeleton.
        userTransactions.clear()
        userTransactions.addAll(
            snapshot.transactions
                // Snapshots written before this filter existed can still hold unsettled top-ups.
                .filter { WalletTransactionVisibility.isListable(it) }
                .map { convertToUITransaction(it) }
                .sortedByDescending { it.timestamp }
        )
        updateTransactionsDisplay()
        return true
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
        refreshForVisibleEntry()
    }

    override fun onHiddenChanged(hidden: Boolean) {
        super.onHiddenChanged(hidden)
        if (!hidden) refreshForVisibleEntry()
    }

    override fun onDestroyView() {
        balanceAnimator?.cancel()
        balanceAnimator = null
        balanceColorAnimator?.cancel()
        balanceColorAnimator = null
        shimmerAnimator?.cancel()
        shimmerAnimator = null
        skeletonAnimator?.cancel()
        skeletonAnimator = null
        walletLoadJob?.cancel()
        walletLoadJob = null
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
            val now = SystemClock.elapsedRealtime()
            if (lastManualRefreshElapsedMs != Long.MIN_VALUE &&
                now - lastManualRefreshElapsedMs < MANUAL_REFRESH_COOLDOWN_MS
            ) {
                binding.swipeRefresh.isRefreshing = false
                return@setOnRefreshListener
            }
            lastManualRefreshElapsedMs = now
            loadWalletData(
                userInitiated = true,
                forceBalanceRefresh = true,
                forceTransactionsRefresh = true,
                includeTransactions = true,
            )
        }
    }

    private fun refreshForVisibleEntry() {
        if (!hasViewBinding() || view == null) return
        val generation = AppForegroundTracker.currentGeneration()
        if (hasRequestedBalance && hasRequestedTransactions &&
            generation == handledForegroundGeneration
        ) {
            return
        }
        handledForegroundGeneration = generation
        // A replayed payment/reward event may already be loading balance while this pre-created
        // tab becomes visible. Queue the missing transaction read instead of dropping it.
        loadWalletData(queueIfBusy = true)
    }

    private fun observeWalletRefreshEvents() {
        viewLifecycleOwner.lifecycleScope.launch {
            WalletRefreshEvents.events.collect { event ->
                if (!markWalletEventHandled(event)) return@collect
                event.authoritativeBalance?.let { balance ->
                    currentBalance = balance
                    updateBalanceDisplay()
                }
                if (!event.cacheAlreadyRefreshed) {
                    val paymentCompleted = event.source == WalletRefreshSource.PAYMENT
                    loadWalletData(
                        // A Cashfree success creates a visible transaction as well as changing
                        // the balance. Refresh both even when this tab was already visited in the
                        // current foreground generation.
                        forceBalanceRefresh = paymentCompleted,
                        forceTransactionsRefresh = paymentCompleted,
                        includeTransactions = paymentCompleted,
                        queueIfBusy = true,
                    )
                } else if (event.authoritativeBalance == null) {
                    // The coordinator already populated the repository. Read that snapshot to
                    // update this view without another backend request.
                    loadWalletData(
                        includeTransactions = false,
                        queueIfBusy = true,
                    )
                }
            }
        }
    }

    private fun markWalletEventHandled(event: WalletRefreshEvent): Boolean {
        val userId = getUserId() ?: return false
        val preferences = requireContext().getSharedPreferences(
            WALLET_REFRESH_EVENT_PREFERENCES,
            android.content.Context.MODE_PRIVATE,
        )
        val key = "last_event:$userId"
        if (preferences.getString(key, null) == event.eventId) return false
        preferences.edit().putString(key, event.eventId).apply()
        return true
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
            RemoteConfigManager.loadCached(requireContext())
            if (!RemoteConfigManager.isWalletEnabled()) {
                showToast(getString(R.string.wallet_top_up_is_temporarily_unavailable))
                return@setOnClickListener
            }
            view.performHapticFeedback(android.view.HapticFeedbackConstants.VIRTUAL_KEY)
            startActivity(
                WalletAddMoneyActivity.createIntent(
                    context = requireContext(),
                    currentBalance = currentBalance,
                )
            )
        }

        // Watch Ad (Earn Coins) — hero CTA pill
        binding.btnWatchAd.setOnClickListener { view ->
            animateChipBounce(view) {
                openRewardedAdSheet()
            }
        }
        
        // Quick Add chip buttons - with bounce animation
        setupQuickTopUpChips()
    }

    /** Builds the fixed quick top-up chips that fall within the configured payment limits. */
    private fun setupQuickTopUpChips() {
        val min = WalletTopUpLauncher.minAmount(requireContext())
        val max = WalletTopUpLauncher.maxAmount(requireContext())
        val amounts = QUICK_TOP_UP_AMOUNTS.filter { it in min..max }

        val chips = listOf(
            binding.btnQuickAdd10 to binding.tvQuickAdd50,
            binding.btnQuickAdd20 to binding.tvQuickAdd100,
            binding.btnQuickAdd100 to binding.tvQuickAdd200
        )

        chips.forEachIndexed { index, (button, label) ->
            val amount = amounts.getOrNull(index)
            if (amount == null) {
                button.visibility = View.GONE
                return@forEachIndexed
            }
            button.visibility = View.VISIBLE
            label.text = integerFormatter.format(amount)
            button.setOnClickListener { view ->
                animateChipBounce(view) { startCheckout(amount) }
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

    private fun loadWalletData(
        userInitiated: Boolean = false,
        forceBalanceRefresh: Boolean = false,
        forceTransactionsRefresh: Boolean = false,
        includeTransactions: Boolean = true,
        queueIfBusy: Boolean = false,
    ) {
        RemoteConfigManager.loadCached(requireContext())
        if (!RemoteConfigManager.isWalletEnabled()) {
            currentBalance = 0.0
            updateBalanceDisplay()
            transactionsAdapter.updateItems(emptyList())
            showToast(getString(R.string.wallet_is_temporarily_unavailable))
            setRefreshing(false)
            applyFeatureSwitches()
            return
        }

        val userId = getUserId()
        if (userId == null) {
            showToast(getString(R.string.please_login_to_view_wallet))
            setRefreshing(false)
            return
        }

        if (walletLoadJob?.isActive == true) {
            if (queueIfBusy) {
                refreshAfterCurrentLoad = true
                forceBalanceRefreshAfterCurrentLoad =
                    forceBalanceRefreshAfterCurrentLoad || forceBalanceRefresh
                forceTransactionsRefreshAfterCurrentLoad =
                    forceTransactionsRefreshAfterCurrentLoad || forceTransactionsRefresh
                includeTransactionsAfterCurrentLoad =
                    includeTransactionsAfterCurrentLoad || includeTransactions
            }
            if (userInitiated) setRefreshing(false)
            return
        }
        hasRequestedBalance = true
        if (includeTransactions) hasRequestedTransactions = true

        // First-ever load (no data / no cache) shows the skeleton. A user-initiated
        // pull-to-refresh shows the swipe spinner. Everything else — onResume, a
        // cached open — refreshes silently in the background so the data on screen
        // never flickers.
        if (!hasLoadedTransactions) {
            showTransactionSkeleton()
        } else if (userInitiated) {
            setRefreshing(true)
        }

        // Bind to the view lifecycle, not the fragment lifecycle: this coroutine touches
        // the binding (balance display, shimmer, refresh spinner), so it must be cancelled
        // at onDestroyView rather than living on until onDestroy and resuming into a dead view.
        walletLoadJob = viewLifecycleOwner.lifecycleScope.launch {
            try {
                loadWalletBalance(forceBalanceRefresh)
                if (includeTransactions) {
                    loadWalletTransactions(forceTransactionsRefresh)
                }
            } catch (cancelled: CancellationException) {
                // Destroying the fragment view cancels this job. Cancellation is lifecycle
                // control flow, not a wallet failure, and must never fall through to getString()
                // or other Fragment APIs after the Fragment has detached.
                throw cancelled
            } catch (e: Exception) {
                showWalletLoadErrorIfVisible()
            } finally {
                setRefreshing(false)
                walletLoadJob = null
                val shouldRefreshAgain = refreshAfterCurrentLoad
                val shouldForceBalanceRefreshAgain = forceBalanceRefreshAfterCurrentLoad
                val shouldForceTransactionsRefreshAgain = forceTransactionsRefreshAfterCurrentLoad
                val shouldIncludeTransactionsAgain = includeTransactionsAfterCurrentLoad
                refreshAfterCurrentLoad = false
                forceBalanceRefreshAfterCurrentLoad = false
                forceTransactionsRefreshAfterCurrentLoad = false
                includeTransactionsAfterCurrentLoad = false
                if (isActive && shouldRefreshAgain && hasViewBinding() && view != null) {
                    binding.root.post {
                        if (isAdded && hasViewBinding() && view != null) {
                            loadWalletData(
                                forceBalanceRefresh = shouldForceBalanceRefreshAgain,
                                forceTransactionsRefresh = shouldForceTransactionsRefreshAgain,
                                includeTransactions = shouldIncludeTransactionsAgain,
                                queueIfBusy = false,
                            )
                        }
                    }
                }
            }
        }
    }

    private suspend fun loadWalletBalance(forceRefresh: Boolean) {
        walletRepository.getWalletDetails(forceRefresh).fold(
            onSuccess = { walletDetails ->
                currentBalance = walletDetails.balance ?: 0.0
                updateBalanceDisplay()
            },
            onFailure = {
                stopBalanceShimmer()
                if (!hasLoadedBalance) renderBalance(currentBalance, isAnimating = false)
                if (userTransactions.isEmpty()) showWalletLoadErrorIfVisible()
            },
        )
    }

    private suspend fun loadWalletTransactions(forceRefresh: Boolean) {
        val userId = getUserId() ?: return
        walletRepository.getWalletTransactions(forceRefresh).fold(
            onSuccess = { transactions ->
                // Drop top-ups the gateway never settled. The backend records a pending row at
                // order-creation time, so an abandoned checkout would otherwise be listed as
                // money received.
                val backendTransactions = transactions
                    .filter { WalletTransactionVisibility.isListable(it) }
                userTransactions.clear()

                val convertedTransactions = backendTransactions.map { convertToUITransaction(it) }
                userTransactions.addAll(convertedTransactions.sortedByDescending { it.timestamp })
                reconcileBalanceFromTransactions(backendTransactions)

                updateTransactionsDisplay()

                // Persist the fresh snapshot for the next instant open.
                WalletCache.save(requireContext(), userId, currentBalance, backendTransactions)
            },
            onFailure = {
                if (userTransactions.isEmpty()) updateTransactionsDisplay()
                showWalletLoadErrorIfVisible()
            },
        )
    }

    private fun showWalletLoadErrorIfVisible() {
        if (!hasViewBinding()) return
        val message = context?.getString(R.string.wallet_load_error) ?: return
        showToast(message)
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
        showToast(getString(R.string.session_expired_please_log_in_again))
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
        binding.tvBalanceAmount.contentDescription = getString(R.string.loading_balance)
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
                getString(
                    R.string.wallet_balance_content_description,
                    balanceFormatter.format(safe)
                )
        }
        binding.tvBalanceAmount.text = ssb
    }

    private fun showTransactionSkeleton() {
        val skeleton = bindingOrNull?.layoutTransactionSkeleton ?: return
        binding.rvTransactions.visibility = View.GONE
        binding.layoutEmptyState.visibility = View.GONE
        skeleton.animate().cancel()
        skeleton.alpha = 1f
        skeleton.visibility = View.VISIBLE
        skeletonAnimator?.cancel()
        skeletonAnimator = SkeletonShimmer.start(skeleton)
    }

    private fun updateTransactionsDisplay() {
        // Instant hand-off: drop the skeleton the moment data lands and let the
        // incoming rows carry the reveal (staggered settle-in) — no crossfade.
        val skeletonWasVisible =
            bindingOrNull?.layoutTransactionSkeleton?.visibility == View.VISIBLE
        skeletonAnimator?.cancel()
        skeletonAnimator = null
        bindingOrNull?.layoutTransactionSkeleton?.visibility = View.GONE
        hasLoadedTransactions = true

        if (userTransactions.isEmpty()) {
            binding.rvTransactions.visibility = View.GONE
            binding.layoutEmptyState.visibility = View.VISIBLE
            transactionsAdapter.updateItems(emptyList())
            binding.layoutEmptyState.alpha = 1f
            if (skeletonWasVisible) SkeletonShimmer.revealView(binding.layoutEmptyState)
        } else {
            binding.rvTransactions.visibility = View.VISIBLE
            binding.layoutEmptyState.visibility = View.GONE

            val groupedItems = WalletTransactionGrouping.buildGroupedItems(
                requireContext(),
                userTransactions,
                MAX_RECENT_TRANSACTIONS
            )
            binding.rvTransactions.alpha = 1f
            transactionsAdapter.updateItems(groupedItems)
            if (skeletonWasVisible) SkeletonShimmer.revealStagger(binding.rvTransactions)
        }
    }

    private fun loadSampleData() {
        // This method is kept for testing purposes only
        // In production, this should not be called - real data should always be used
        
        currentBalance = 0.0
        updateBalanceDisplay()
        userTransactions.clear()
        updateTransactionsDisplay()
        showToast(getString(R.string.no_real_wallet_data_available))
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
            "DAILY_WALLET_RESET" -> TransactionType.BONUS
            "REFUND" -> TransactionType.REFUND
            "PENALTY_FEE", "LATE_CHECK_IN_PENALTY", "LATE_CHECK_OUT_PENALTY" -> TransactionType.PARKING_PAYMENT
            else -> null
        }
        val backendIsCredit = when (backendType) {
            "BOOKING_REFUND", "WALLET_TOP_UP", "AD_TOP_UP", "WELCOME_BONUS",
            "DAILY_WALLET_RESET", "REFUND" -> true
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
        
        
        val resolvedBookingRelated = if (backendUiType != null) backendIsBookingRelated else isBookingRelated
        val description = WalletTransactionText.resolve(
            context = requireContext(),
            backendType = backendType,
            transactionType = transactionType,
            isReward = isReward,
            isBookingRelated = resolvedBookingRelated,
            status = statusNorm
        )
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

    private fun openRewardedAdSheet() {
        // No source coin here, so the sheet reveals its medallion with a scale/fade.
        val bottomSheet = com.gridee.parking.ui.bottomsheet.RewardBottomSheet.newInstance(
            entryPoint = com.gridee.parking.ui.bottomsheet.RewardBottomSheet.ENTRY_POINT_WALLET
        )
        bottomSheet.showIfPossible(parentFragmentManager)
    }

    private fun startCheckout(amount: Double) {
        RemoteConfigManager.loadCached(requireContext())
        if (!RemoteConfigManager.isWalletEnabled()) {
            showToast(getString(R.string.wallet_top_up_is_temporarily_unavailable))
            return
        }

        if (getUserId() == null) {
            showToast(getString(R.string.please_login_to_add_money))
            return
        }

        setRefreshing(true)
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                when (val result = WalletTopUpLauncher.createTopUp(requireContext(), amount)) {
                    is WalletTopUpLauncher.Result.Ready -> startActivity(result.intent)
                    is WalletTopUpLauncher.Result.Failed -> showToast(result.message)
                }
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
            401 -> getString(R.string.session_expired_please_log_in_again)
            429 -> getString(R.string.too_many_requests)
            503 -> getString(R.string.wallet_is_temporarily_unavailable)
            else -> fallback
        }
    }
    private companion object {
        private const val EMPTY_STATE_DOTLOTTIE_URL =
            "https://lottie.host/501bcbee-2a36-496c-a127-bedca0ef0ce8/D2DCOt93Jv.lottie"
        private const val MAX_RECENT_TRANSACTIONS = 5
        private const val WALLET_SKELETON_ROWS = 5
        private const val MANUAL_REFRESH_COOLDOWN_MS = 30_000L
        private const val WALLET_REFRESH_EVENT_PREFERENCES = "wallet_refresh_events"

        private val QUICK_TOP_UP_AMOUNTS = listOf(100.0, 200.0, 500.0)
        private const val REWARD_AMOUNT_RUPEES = 20.0
        private const val BALANCE_EPSILON = 0.01
    }
}
