package com.gridee.parking.ui.bottomsheet

import android.app.Dialog
import android.content.DialogInterface
import android.content.Intent
import android.content.res.Configuration
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.rewarded.RewardedAd
import com.google.android.gms.ads.rewarded.RewardedAdLoadCallback
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.progressindicator.CircularProgressIndicatorSpec
import com.google.android.material.progressindicator.IndeterminateDrawable
import com.gridee.parking.R
import com.gridee.parking.data.repository.WalletRepository
import com.gridee.parking.databinding.BottomSheetUniversalBinding
import com.gridee.parking.ui.components.CustomBottomNavigation
import com.gridee.parking.ui.main.MainContainerActivity
import com.gridee.parking.utils.AdMobManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class UniversalBottomSheet : BottomSheetDialogFragment() {

    private var _binding: BottomSheetUniversalBinding? = null
    private val binding get() = _binding!!

    private var lottieFileName: String? = null
    private var rewardedAd: RewardedAd? = null
    private var isLoadingRewardedAd: Boolean = false
    private var pendingShowRewardedAd: Boolean = false
    private var primaryButtonIdleLabel: CharSequence? = null
    private var isViewDestroyed: Boolean = false
    private var isRewardEarned: Boolean = false
    private val rewardAmount = 10.0
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setStyle(STYLE_NORMAL, R.style.BottomSheetDialogTheme)
        if (isRewardMode) preloadRewardedAd()
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val dialog = super.onCreateDialog(savedInstanceState)
        dialog.setOnShowListener { dialogInterface ->
            val bottomSheetDialog = dialogInterface as com.google.android.material.bottomsheet.BottomSheetDialog
            
            // Fix for Edge-to-Edge: Ensure the container extends behind nav bar
            val bottomSheet = bottomSheetDialog.findViewById<View>(com.google.android.material.R.id.design_bottom_sheet)
            bottomSheet?.let { sheet ->
                // Keep the sheet surface opaque so nav-area never appears transparent
                sheet.setBackgroundResource(R.drawable.bg_bottom_sheet_universal)
                
                // Force the sheet to extend to the edge
                sheet.fitsSystemWindows = false
                
                // Remove any margins that might lift the sheet up
                val params = sheet.layoutParams as? ViewGroup.MarginLayoutParams
                params?.setMargins(0, 0, 0, 0)
                sheet.layoutParams = params
            }
            
            // Ensure behavior ignores gesture insets
            bottomSheetDialog.behavior.isGestureInsetBottomIgnored = true
            
            bottomSheetDialog.window?.let { window ->
                WindowCompat.setDecorFitsSystemWindows(window, false)
                window.navigationBarColor = android.graphics.Color.TRANSPARENT
                window.isNavigationBarContrastEnforced = false
                
                // Adapt nav bar icons to current theme
                val wic = WindowCompat.getInsetsController(window, window.decorView)
                val isDarkMode = (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
                wic.isAppearanceLightNavigationBars = !isDarkMode
                
                // Remove the black divider line above the nav bar (Android 9+)
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
                    window.navigationBarDividerColor = android.graphics.Color.TRANSPARENT
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                        window.isNavigationBarContrastEnforced = false
                    }
                }

                // Glassmorphism: Blur the screen behind the sheet (Android 12+)
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                    window.attributes.blurBehindRadius = 50 // 50px blur for frosted glass effect
                    window.attributes = window.attributes // Apply changes
                }
            }
        }
        return dialog
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        isViewDestroyed = false
        _binding = BottomSheetUniversalBinding.inflate(inflater, container, false)
        return binding.root
    }



    private var title: String? = null
    private var message: String? = null
    private var primaryButtonText: String? = null
    private var onPrimaryClickListener: (() -> Unit)? = null
    private var isRewardMode: Boolean = false

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupUI()
        setupBehaviors()
        setupInsets()
        
        // Ambient Shadow (Android 9+)
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
            binding.root.outlineAmbientShadowColor = android.graphics.Color.parseColor("#40000000")
            binding.root.outlineSpotShadowColor = android.graphics.Color.parseColor("#40000000")
        }
    }

    private fun setupInsets() {
        // Reduced bottom padding to avoid excessive space (common in Apple-like design)
        val density = binding.root.context.resources.displayMetrics.density
        val baseBottomPadding = binding.root.paddingBottom // Get XML padding
        
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            val extraPadding = (8 * density).toInt() // Ultra-tight breathing room
            view.setPadding(view.paddingLeft, view.paddingTop, view.paddingRight, baseBottomPadding + extraPadding + bars.bottom)
            insets
        }
    }

    private fun setupBehaviors() {
        val bottomSheetBehavior = (dialog as? com.google.android.material.bottomsheet.BottomSheetDialog)?.behavior
        bottomSheetBehavior?.addBottomSheetCallback(object : com.google.android.material.bottomsheet.BottomSheetBehavior.BottomSheetCallback() {
            override fun onStateChanged(bottomSheet: View, newState: Int) {
                if (newState == com.google.android.material.bottomsheet.BottomSheetBehavior.STATE_EXPANDED ||
                    newState == com.google.android.material.bottomsheet.BottomSheetBehavior.STATE_HIDDEN
                ) {
                    bottomSheet.performHapticFeedback(android.view.HapticFeedbackConstants.CONTEXT_CLICK)
                }
                
                if (newState == com.google.android.material.bottomsheet.BottomSheetBehavior.STATE_COLLAPSED ||
                    newState == com.google.android.material.bottomsheet.BottomSheetBehavior.STATE_EXPANDED) {
                    animateHandle(32)
                }
            }

            override fun onSlide(bottomSheet: View, slideOffset: Float) {
                animateHandle(48)
            }
        })
    }

    private fun animateHandle(targetWidthDp: Int) {
        val targetWidthPx = (targetWidthDp * resources.displayMetrics.density).toInt()
        if (binding.dragHandle.layoutParams.width != targetWidthPx) {
            val params = binding.dragHandle.layoutParams
            params.width = targetWidthPx
            binding.dragHandle.layoutParams = params
        }
    }

    private fun setupUI() {
        // Close Button
        binding.btnClose.setOnClickListener {
            pendingShowRewardedAd = false
            setRewardedAdLoading(false)
            dismiss()
        }

        // Configure UI based on mode
        if (isRewardMode) {
            setupRewardUI()
        } else {
            setupStandardUI()
        }
    }

    private fun setupStandardUI() {
        // Hide reward-specific elements
        binding.viewRewardGlow.isVisible = false
        binding.rewardAmountContainer.isVisible = false
        binding.tvRewardNote.isVisible = false
        binding.playStoreReviewContainer.isVisible = false

        // Hide drag handle for cleaner look
        binding.dragHandle.isVisible = false

        // Lottie (Optional)
        if (lottieFileName != null) {
            binding.lottieIcon.setAnimation(lottieFileName)
            binding.lottieIcon.playAnimation()
            binding.lottieIcon.isVisible = true

            // Subtle pop animation
            binding.lottieIcon.scaleX = 0f
            binding.lottieIcon.scaleY = 0f
            binding.lottieIcon.animate()
                .scaleX(1f)
                .scaleY(1f)
                .setDuration(600)
                .setInterpolator(android.view.animation.OvershootInterpolator(1.2f))
                .start()
        } else {
            binding.lottieIcon.isVisible = false
        }

        // Texts
        binding.tvTitle.text = title
        binding.tvSubtitle.text = message
        binding.tvSubtitle.gravity = android.view.Gravity.START
        binding.tvSubtitle.textAlignment = View.TEXT_ALIGNMENT_TEXT_START

        // Button — standard brand styling
        binding.btnPrimary.icon = null
        if (primaryButtonText != null) {
            binding.btnPrimary.text = primaryButtonText
            binding.btnPrimary.isVisible = true
            binding.btnPrimary.setOnClickListener {
                onPrimaryClickListener?.invoke()
                dismiss()
            }
        } else {
            binding.btnPrimary.isVisible = false
        }
    }

    private fun setupRewardUI() {
        // Lottie animation
        if (lottieFileName != null) {
            binding.lottieIcon.setAnimation(lottieFileName)
            binding.lottieIcon.playAnimation()
            binding.lottieIcon.isVisible = true
        } else {
            binding.lottieIcon.isVisible = false
        }

        // Show reward-specific views
        binding.viewRewardGlow.isVisible = true
        binding.rewardAmountContainer.isVisible = true
        binding.tvRewardNote.isVisible = true
        binding.playStoreReviewContainer.isVisible = true

        // --- Entrance Animations ---

        // Lottie pop-in
        binding.lottieIcon.scaleX = 0f
        binding.lottieIcon.scaleY = 0f
        binding.lottieIcon.animate()
            .scaleX(1f)
            .scaleY(1f)
            .setDuration(600)
            .setInterpolator(android.view.animation.OvershootInterpolator(1.2f))
            .setStartDelay(200)
            .start()

        // Glow fade-in
        binding.viewRewardGlow.alpha = 0f
        binding.viewRewardGlow.scaleX = 0.8f
        binding.viewRewardGlow.scaleY = 0.8f
        binding.viewRewardGlow.animate()
            .alpha(1f)
            .scaleX(1f)
            .scaleY(1f)
            .setDuration(900)
            .setStartDelay(100)
            .start()

        // Subtle breathing pulse on glow
        val pulseX = android.animation.ObjectAnimator.ofFloat(binding.viewRewardGlow, "scaleX", 1f, 1.08f)
        pulseX.duration = 2500
        pulseX.repeatMode = android.animation.ObjectAnimator.REVERSE
        pulseX.repeatCount = android.animation.ObjectAnimator.INFINITE
        pulseX.interpolator = android.view.animation.AccelerateDecelerateInterpolator()

        val pulseY = android.animation.ObjectAnimator.ofFloat(binding.viewRewardGlow, "scaleY", 1f, 1.08f)
        pulseY.duration = 2500
        pulseY.repeatMode = android.animation.ObjectAnimator.REVERSE
        pulseY.repeatCount = android.animation.ObjectAnimator.INFINITE
        pulseY.interpolator = android.view.animation.AccelerateDecelerateInterpolator()

        val pulseSet = android.animation.AnimatorSet()
        pulseSet.playTogether(pulseX, pulseY)
        pulseSet.startDelay = 1000
        pulseSet.start()

        // Reward chip slide-up
        binding.rewardAmountContainer.alpha = 0f
        binding.rewardAmountContainer.translationY = 16f
        binding.rewardAmountContainer.animate()
            .alpha(1f)
            .translationY(0f)
            .setDuration(500)
            .setStartDelay(400)
            .start()

        // --- Gold button styling ---
        val goldColor = ContextCompat.getColor(requireContext(), R.color.reward_button)
        val goldTextColor = ContextCompat.getColor(requireContext(), R.color.reward_button_text)
        binding.btnPrimary.backgroundTintList = android.content.res.ColorStateList.valueOf(goldColor)
        binding.btnPrimary.setTextColor(goldTextColor)
        binding.btnPrimary.setIconResource(R.drawable.ic_reward_watch_claim)
        binding.btnPrimary.iconTint = null
        binding.btnPrimary.iconGravity = com.google.android.material.button.MaterialButton.ICON_GRAVITY_TEXT_START
        binding.btnPrimary.iconSize = (20 * resources.displayMetrics.density).toInt()
        binding.btnPrimary.iconPadding = (8 * resources.displayMetrics.density).toInt()

        // Set texts
        binding.tvTitle.text = title ?: "Daily Reward"
        binding.tvSubtitle.text = message ?: "Watch a short video to claim your daily reward"
        binding.btnPrimary.text = primaryButtonText ?: "Watch & Claim"
        primaryButtonIdleLabel = binding.btnPrimary.text

        // Reward amount display
        binding.tvRewardAmount.text = String.format("%.0f", rewardAmount)

        binding.btnPrimary.setOnClickListener {
            if (onPrimaryClickListener != null) {
                onPrimaryClickListener?.invoke()
            } else {
                showRewardVideo()
            }
        }

        binding.btnPlayStoreReview.setOnClickListener {
            openPlayStoreReview()
        }

        // Preload rewarded ad
        preloadRewardedAd()
    }

    private fun openPlayStoreReview() {
        val packageName = requireContext().packageName
        val marketIntent = Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=$packageName"))
        val webIntent = Intent(
            Intent.ACTION_VIEW,
            Uri.parse("https://play.google.com/store/apps/details?id=$packageName")
        )

        runCatching { startActivity(marketIntent) }
            .onFailure {
                runCatching { startActivity(webIntent) }
                    .onFailure {
                        Toast.makeText(requireContext(), "Unable to open Play Store", Toast.LENGTH_SHORT).show()
                    }
            }
    }

    // Public Configuration Methods =======================================================
    
    fun setTitle(text: String) {
        this.title = text
        if (_binding != null) binding.tvTitle.text = text
    }

    fun setMessage(text: String) {
        this.message = text
        if (_binding != null) binding.tvSubtitle.text = text
    }

    fun setPrimaryButton(text: String, onClick: () -> Unit) {
        this.primaryButtonText = text
        this.onPrimaryClickListener = onClick
        if (_binding != null) {
            binding.btnPrimary.text = text
            primaryButtonIdleLabel = text
            binding.btnPrimary.isVisible = true
            binding.btnPrimary.setOnClickListener {
                onClick()
                if (!isRewardMode) dismiss()
            }
        }
    }

    fun setLottieFile(fileName: String) {
        this.lottieFileName = fileName
        if (_binding != null) {
            binding.lottieIcon.setAnimation(fileName)
            binding.lottieIcon.playAnimation()
            binding.lottieIcon.isVisible = true
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        isViewDestroyed = true
        pendingShowRewardedAd = false
        isLoadingRewardedAd = false
        primaryButtonIdleLabel = null
        _binding = null
        rewardedAd = null
    }

    override fun onDismiss(dialog: DialogInterface) {
        pendingShowRewardedAd = false
        super.onDismiss(dialog)
    }

    companion object {
        const val TAG = "UniversalBottomSheet"

        fun newInstance(
            lottieFileName: String? = null,
            title: String? = null,
            message: String? = null,
            buttonText: String? = null,
            isRewardMode: Boolean = false
        ): UniversalBottomSheet {
            val fragment = UniversalBottomSheet()
            fragment.lottieFileName = lottieFileName
            fragment.title = title
            fragment.message = message
            fragment.primaryButtonText = buttonText
            fragment.isRewardMode = isRewardMode
            return fragment
        }
    }
    
    // =================================================================================

    private fun preloadRewardedAd() {
        if (isLoadingRewardedAd || rewardedAd != null) return

        val appContext = requireContext().applicationContext
        val adUnitId = AdMobManager.rewardedAdUnitId
        isLoadingRewardedAd = true

        val initialized = AdMobManager.initializeIfEnabled(requireContext()) {
            if (isViewDestroyed) {
                isLoadingRewardedAd = false
                return@initializeIfEnabled
            }

            val adRequest = AdRequest.Builder().build()

            RewardedAd.load(
                appContext,
                adUnitId,
                adRequest,
                object : RewardedAdLoadCallback() {
                    override fun onAdLoaded(ad: RewardedAd) {
                        isLoadingRewardedAd = false
                        rewardedAd = ad
                        maybeShowRewardedAdIfPending()
                    }

                    override fun onAdFailedToLoad(adError: LoadAdError) {
                        logRewardedAdLoadFailure(adError)
                        isLoadingRewardedAd = false
                        rewardedAd = null
                        handleRewardedAdLoadFailure(rewardedAdLoadFailureMessage(adError))
                    }
                }
            )
        }

        if (!initialized) {
            isLoadingRewardedAd = false
            handleRewardedAdLoadFailure("Rewards are temporarily unavailable. Please try again later.")
        }
    }

    private fun showRewardVideo() {
        val ad = rewardedAd
        if (ad == null) {
            pendingShowRewardedAd = true
            setRewardedAdLoading(true)
            preloadRewardedAd()
            return
        }

        pendingShowRewardedAd = false
        setRewardedAdLoading(false)
        showRewardedAd(ad)
    }

    private fun maybeShowRewardedAdIfPending() {
        if (!pendingShowRewardedAd) return
        val ad = rewardedAd ?: return
        if (isViewDestroyed || !isAdded) return

        pendingShowRewardedAd = false
        setRewardedAdLoading(false)
        showRewardedAd(ad)
    }

    private fun setRewardedAdLoading(isLoading: Boolean) {
        if (_binding == null) return
        if (primaryButtonIdleLabel == null) primaryButtonIdleLabel = binding.btnPrimary.text

        binding.btnPrimary.isEnabled = !isLoading
        binding.btnPrimary.alpha = if (isLoading) 0.7f else 1f

        if (isLoading) {
            // Animated spinner inside the button so the user can see the ad is being fetched.
            binding.btnPrimary.text = "Preparing video…"
            binding.btnPrimary.icon = buildButtonSpinner()
            binding.btnPrimary.iconTint = android.content.res.ColorStateList.valueOf(
                ContextCompat.getColor(requireContext(), R.color.reward_button_text)
            )
            binding.btnPrimary.iconGravity = MaterialButton.ICON_GRAVITY_TEXT_START
        } else {
            binding.btnPrimary.text = primaryButtonIdleLabel
            // Restore the idle watch/claim glyph only in reward mode (standard mode has no icon).
            if (isRewardMode) {
                binding.btnPrimary.setIconResource(R.drawable.ic_reward_watch_claim)
                binding.btnPrimary.iconTint = null
            } else {
                binding.btnPrimary.icon = null
            }
        }
    }

    /** Material indeterminate circular spinner sized to sit inside the primary button. */
    private fun buildButtonSpinner(): IndeterminateDrawable<CircularProgressIndicatorSpec> {
        val density = resources.displayMetrics.density
        val spec = CircularProgressIndicatorSpec(
            requireContext(),
            null,
            0,
            com.google.android.material.R.style.Widget_Material3_CircularProgressIndicator_ExtraSmall
        ).apply {
            indicatorInset = 0
            indicatorSize = (20 * density).toInt()
            trackThickness = (2 * density).toInt()
        }
        return IndeterminateDrawable.createCircularDrawable(requireContext(), spec)
    }

    private fun showRewardedAd(ad: RewardedAd) {
        if (!isAdded) return
        if (_binding != null) {
            binding.btnPrimary.isEnabled = false
            binding.btnPrimary.alpha = 0.6f
        }

        ad.fullScreenContentCallback = object : FullScreenContentCallback() {
            override fun onAdDismissedFullScreenContent() {
                rewardedAd = null
                // If they didn't earn the reward (e.g., closed early), dismiss the sheet immediately.
                // Otherwise, wait for the backend wallet top-up logic to finish processing.
                if (!isRewardEarned) {
                    dismissAllowingStateLoss()
                }
            }

            override fun onAdFailedToShowFullScreenContent(adError: com.google.android.gms.ads.AdError) {
                Log.w(
                    TAG,
                    "Rewarded ad failed to show: code=${adError.code}, " +
                        "domain=${adError.domain}, message=${adError.message}"
                )
                rewardedAd = null
                preloadRewardedAd()
                setRewardedAdLoading(false)
                Toast.makeText(
                    requireContext(),
                    "We could not open the reward video. Please try again in a moment.",
                    Toast.LENGTH_LONG
                ).show()
            }

            override fun onAdShowedFullScreenContent() {
                rewardedAd = null
            }
        }

        ad.show(requireActivity()) { rewardItem ->
            isRewardEarned = true
            creditRewardToWallet(rewardAmount)
            Toast.makeText(
                requireContext(),
                "Reward earned! Processing your wallet top-up...",
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    private fun handleRewardedAdLoadFailure(message: String) {
        if (!pendingShowRewardedAd) return
        pendingShowRewardedAd = false
        setRewardedAdLoading(false)
        if (isAdded) {
            Toast.makeText(requireContext(), message, Toast.LENGTH_LONG).show()
        }
    }

    private fun logRewardedAdLoadFailure(adError: LoadAdError) {
        Log.w(
            TAG,
            "Rewarded ad failed to load: code=${adError.code}, " +
                "domain=${adError.domain}, message=${adError.message}, " +
                "responseInfo=${adError.responseInfo}"
        )
    }

    private fun rewardedAdLoadFailureMessage(adError: LoadAdError): String {
        return when (adError.code) {
            AdRequest.ERROR_CODE_NO_FILL,
            AdRequest.ERROR_CODE_MEDIATION_NO_FILL ->
                "No reward video is available right now. Please try again in a few minutes."
            AdRequest.ERROR_CODE_NETWORK_ERROR ->
                "We could not load the video. Check your internet connection and try again."
            AdRequest.ERROR_CODE_INVALID_REQUEST,
            AdRequest.ERROR_CODE_APP_ID_MISSING,
            AdRequest.ERROR_CODE_INVALID_AD_STRING ->
                "Rewards are not available in this app version yet. Please update or try again later."
            else -> "We could not load the reward video. Please try again in a moment."
        }
    }

    private fun creditRewardToWallet(amount: Double) {
        if (!isAdded) return
        val ctx = requireContext()

        viewLifecycleOwner.lifecycleScope.launch {
            setRewardLoading(true)
            try {
                val result = withContext(Dispatchers.IO) {
                    WalletRepository(ctx).topUpWallet(amount)
                }
                result.fold(
                    onSuccess = { payload ->
                        val newBalance = (payload["balance"] as? Double)
                        showRewardDialog(amount, newBalance)
                    },
                    onFailure = { error ->
                        Toast.makeText(
                            ctx,
                            "Reward earned but could not be added: ${error.message ?: "Unknown error"}",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                )
            } catch (e: Exception) {
                Toast.makeText(
                    ctx,
                    "Reward earned but could not be added: ${e.message}",
                    Toast.LENGTH_LONG
                ).show()
                dismissAllowingStateLoss()
            } finally {
                setRewardLoading(false)
            }
        }
    }

    private fun setRewardLoading(isLoading: Boolean) {
        if (_binding == null) return
        binding.btnPrimary.isEnabled = !isLoading
        binding.btnPrimary.alpha = if (isLoading) 0.6f else 1f
    }

    private fun showRewardDialog(amount: Double, newBalance: Double?) {
        val activityContext = activity ?: return
        
        // Broadcast the reward completion directly into the MainContainerActivity global notification receiver
        val rewardIntent = Intent(activityContext, MainContainerActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra(MainContainerActivity.EXTRA_SHOW_WALLET_TRANSACTION, true)
            putExtra(MainContainerActivity.EXTRA_WALLET_TRANSACTION_TITLE, "Ad Top-Up")
            putExtra(MainContainerActivity.EXTRA_WALLET_TRANSACTION_AMOUNT, String.format(java.util.Locale.getDefault(), "%.0f", amount))
            putExtra(MainContainerActivity.EXTRA_WALLET_TRANSACTION_IS_CREDIT, true)
            // Signal MainContainerActivity to route the click to the wallet tab directly instead of history 
            putExtra(MainContainerActivity.EXTRA_WALLET_TRANSACTION_ROUTE_TO_WALLET, true)
        }
        activityContext.startActivity(rewardIntent)
        
        // Safely tear down this sheet AFTER offloading the broadcast UI instruction!
        dismissAllowingStateLoss()
    }

    private fun openWalletPage(ctx: android.content.Context) {
        val intent = Intent(ctx, MainContainerActivity::class.java).apply {
            putExtra(MainContainerActivity.EXTRA_TARGET_TAB, CustomBottomNavigation.TAB_WALLET)
            addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        }
        ctx.startActivity(intent)
    }
}
