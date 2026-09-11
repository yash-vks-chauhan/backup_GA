package com.gridee.parking.ui.bottomsheet

import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.animation.PropertyValuesHolder
import android.animation.ValueAnimator
import android.app.Dialog
import android.content.DialogInterface
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Bundle
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.view.animation.PathInterpolator
import android.window.OnBackInvokedCallback
import android.window.OnBackInvokedDispatcher
import androidx.annotation.ColorInt
import androidx.core.content.ContextCompat
import androidx.core.graphics.ColorUtils
import androidx.core.os.bundleOf
import androidx.core.view.OneShotPreDrawListener
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.doOnPreDraw
import androidx.core.view.isVisible
import androidx.core.view.updateLayoutParams
import androidx.core.view.updatePadding
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.setFragmentResult
import androidx.lifecycle.lifecycleScope
import com.gridee.parking.R
import com.gridee.parking.databinding.BottomSheetBookingQrPassBinding
import com.gridee.parking.ui.adapters.Booking
import com.gridee.parking.ui.adapters.BookingStatus
import com.gridee.parking.ui.views.SkeletonShimmer
import com.gridee.parking.utils.AdRevenueAnalytics
import com.gridee.parking.utils.BookingQrCodeGenerator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * The booking pass: the lot, the state, the code, and one full-bleed creative.
 *
 * The page carries exactly ONE horizontal edge, and it belongs to the ad. Everything above it
 * is a single surface: the top of the screen simply *is* the booking's state — the same
 * amber/green wash the stage cards wear on the bookings tab — dissolving into the page colour
 * a little way above the fold, with the code drawn straight onto it. There is no card, no
 * plate and no panel, so there is no seam to align and nothing to look empty.
 *
 * Below that edge the creative runs to all four sides. That is the point: an ad zone with a
 * frame of its own can look like an empty frame, and a bled one cannot, because the surface
 * *is* the creative.
 *
 * The band is the AdMob native placement or it is nothing. There is no house promo, no partner
 * campaign and no fallback of any kind, so when the request comes back empty the band
 * collapses to zero height and the code takes the whole page: an ad zone with no ad in it is
 * not a zone, it is a hole.
 *  - [TrayState.AD]    the AdMob native card, ~47 % of the page
 *  - [TrayState.EMPTY] no fill, so there is no band at all and the code grows into it
 *
 * What used to be here was a swipeable rail — the AdMob card as page 0, partner campaigns
 * stacked behind it, dots underneath, and a Gridee promo as the last rung. This screen now
 * carries the AdMob placement and nothing else, so the deck adapter, the campaign selection
 * policy and the spotlight dialog it opened were deleted with it; this was their only caller.
 *
 * Two rules survive the rewrite:
 *
 *  - **The ad is only ever laid out when it can be seen.** [BookingQrNativeAdView] starts
 *    GONE and shows itself only once it has a creative, so a card behind the skeleton has no
 *    area and the SDK cannot log an impression nobody saw.
 *  - **Labels ride with their creative.** There is no section heading anywhere on this
 *    screen. Every band wears its own chip in the same corner, so what is on the band never
 *    changes what the disclosure says.
 */
class BookingQrPassBottomSheet : DialogFragment() {

    private var _binding: BottomSheetBookingQrPassBinding? = null
    private val binding get() = requireNotNull(_binding)

    private enum class TrayState { LOADING, AD, EMPTY }

    private var trayState = TrayState.LOADING

    /** The AdMob unit has answered — with a creative, with no fill, or not at all in time. */
    private var nativeResolved = false
    private var nativeLoaded = false

    /** Once true, the band gives up rather than holding the skeleton for the rest of the sheet. */
    private var deadlinePassed = false

    private var skeletonAnimator: ValueAnimator? = null
    private var plateSizePending = false
    private var bandSizePending = false
    private var sizedForRootHeight = 0

    /** No creative, so no band: the code takes the page the ad would have had. */
    private var trayCollapsed = false

    private var originalScreenBrightness: Float? = null
    private var entranceAnimator: AnimatorSet? = null
    private var exitAnimationRunning = false
    private var bypassExitAnimation = false
    private var backInvokedCallback: OnBackInvokedCallback? = null
    private var dismissedResultDelivered = false

    /**
     * A slow ad request must not hold the band on a skeleton for the life of the sheet. When
     * the deadline passes the band collapses; a creative that lands afterwards still opens it
     * back up, because giving away a filled impression costs more than one reflow does.
     */
    private val trayDeadline = Runnable {
        deadlinePassed = true
        renderTray()
    }

    private var restoredBooking: BookingPassSnapshot? = null
    private val booking: BookingPassSnapshot?
        get() = restoredBooking
            ?: restoreBookingPassSnapshot(arguments, null)?.also { restoredBooking = it }

    val bookingId: String
        get() = booking?.id.orEmpty()

    private val isPreviewMode: Boolean
        get() = runCatching { arguments?.getBoolean(ARG_PREVIEW_MODE, false) == true }
            .getOrDefault(false)

    private val isActiveBooking: Boolean
        get() = booking?.status == BookingStatus.ACTIVE

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setStyle(STYLE_NO_TITLE, R.style.Theme_Gridee_NoActionBar)
        restoredBooking = restoreBookingPassSnapshot(arguments, savedInstanceState)
        migrateBookingPassArguments(arguments, restoredBooking)
        dismissedResultDelivered = savedInstanceState?.getBoolean(
            STATE_DISMISSED_RESULT_DELIVERED,
            false,
        ) ?: false
    }

    override fun onSaveInstanceState(outState: Bundle) {
        booking?.let { outState.putBundle(STATE_BOOKING_PASS_SNAPSHOT, it.toBundle()) }
        outState.putBoolean(STATE_DISMISSED_RESULT_DELIVERED, dismissedResultDelivered)
        super.onSaveInstanceState(outState)
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        return Dialog(requireContext(), theme).apply {
            setCanceledOnTouchOutside(false)
            setOnKeyListener { _, keyCode, event ->
                if (keyCode == KeyEvent.KEYCODE_BACK && event.action == KeyEvent.ACTION_UP) {
                    this@BookingQrPassBottomSheet.dismiss()
                    true
                } else {
                    false
                }
            }
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = BottomSheetBookingQrPassBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        configureInsets()
        applyStatePalette()
        bindPassContent()
        trackBandSizes()
        showSkeleton()
        startNativeAd()
    }

    override fun onStart() {
        super.onStart()
        dialog?.window?.let { window ->
            window.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
            window.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            window.clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
            WindowCompat.setDecorFitsSystemWindows(window, false)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                window.isNavigationBarContrastEnforced = false
            }

            val isNightMode = (resources.configuration.uiMode and
                Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
            WindowCompat.getInsetsController(window, window.decorView).apply {
                isAppearanceLightStatusBars = !isNightMode
            }
            renderNavBarTone()

            if (originalScreenBrightness == null) {
                originalScreenBrightness = window.attributes.screenBrightness
            }
            window.attributes = window.attributes.apply {
                screenBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_FULL
            }
            // Brightness alone was not enough: a driver queueing 40 s at a barrier watched
            // the display sleep with the code still on it.
            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
        registerCinematicBackCallback()
        playDepthEntrance()
    }

    override fun onStop() {
        unregisterCinematicBackCallback()
        super.onStop()
    }

    // ───────────────────────── the pass ─────────────────────────

    /**
     * Paints everything the booking's state owns, from the bookings tab's own tokens.
     *
     * The wash and the header's ink both come from the `stage_head_*` family the stage cards
     * use, so the pass is recognisably the card the user tapped rather than a separate screen
     * that happens to be the same two colours. With the status line gone the wash is the only
     * thing that says Booked or Live, which is what the layout was drawn around.
     */
    private fun applyStatePalette() {
        val view = _binding ?: return
        val context = requireContext()
        val active = isActiveBooking

        val wash = ContextCompat.getColor(
            context,
            if (active) R.color.stage_head_active else R.color.stage_head_pending
        )
        val ink = ContextCompat.getColor(
            context,
            if (active) R.color.stage_head_active_ink else R.color.stage_head_pending_ink
        )
        view.qrPassWash.background = washGradient(wash)
        view.tvQrPassLot.setTextColor(ink)
    }

    /**
     * Solid for the top third, then a straight fade to nothing.
     *
     * Four evenly spaced stops rather than [GradientDrawable.setColors] with offsets, which is
     * API 29: solid, solid, half, clear puts the knee at 33 % and a true linear ramp after it,
     * which is the shape the design specifies and works back to the app's minSdk.
     *
     * Both ends are the same RGB — only the alpha moves — so the ramp never picks up the grey
     * cast a fade to a bare transparent black would leave.
     */
    private fun washGradient(@ColorInt wash: Int) = GradientDrawable(
        GradientDrawable.Orientation.TOP_BOTTOM,
        intArrayOf(
            wash,
            wash,
            ColorUtils.setAlphaComponent(wash, 128),
            ColorUtils.setAlphaComponent(wash, 0)
        )
    )

    private fun bindPassContent() {
        val current = booking
        binding.btnCloseQrPass.setOnClickListener { dismiss() }

        // The spot replaces "Booking Pass" in the header. The old title said nothing the rest
        // of the screen was not already saying, and the spot is the one thing an operator
        // asks for. A pass whose booking failed to deserialise still scans, so it falls back
        // to the screen name rather than showing an empty header.
        binding.tvQrPassLot.text = current?.spotName?.takeIf { it.isNotBlank() }
            ?: getString(R.string.booking_pass_title)

        binding.tvQrPassInstruction.setText(
            if (current?.status == BookingStatus.ACTIVE) {
                R.string.pass_hold_up_exit
            } else {
                R.string.pass_hold_up_entry
            }
        )

        lifecycleScope.launch {
            val bitmap = withContext(Dispatchers.Default) {
                BookingQrCodeGenerator.generate(bookingId, QR_BITMAP_SIZE_PX)
            }
            _binding?.ivQrPassCode?.setImageBitmap(bitmap)
        }
    }

    // ───────────────────────── the bands ─────────────────────────

    /**
     * Splits the screen the way the design does, in proportion rather than in constants.
     *
     * The creative takes [TRAY_FRACTION] of the page and the code takes the rest. A dp
     * constant would be the design's own 390 × 844 frame baked in: the same 396 dp band is
     * 47 % of a Pixel and 62 % of a compact phone in multi-window.
     */
    private fun trackBandSizes() {
        val root = binding.root
        root.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ ->
            if (root.height != sizedForRootHeight) requestBandSizes()
        }
        // A permanent listener on the block as well, not a one-shot: the bands only move when
        // the root height does, but what the code has to fit around moves on its own — a large
        // system font scale, or an instruction that wraps to two lines. applyPlateSize is a
        // no-op once the target stops changing, so this settles rather than loops.
        binding.qrPassBlock.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ ->
            requestPlateSize()
        }
        requestBandSizes()
    }

    private fun requestBandSizes() {
        val root = _binding?.root ?: return
        if (bandSizePending) return
        bandSizePending = true
        root.post {
            bandSizePending = false
            applyBandSizes()
        }
    }

    private fun applyBandSizes() {
        val view = _binding ?: return
        val height = view.root.height
        if (height <= 0) return
        sizedForRootHeight = height

        val tray = if (trayCollapsed) {
            0
        } else {
            (height * TRAY_FRACTION).toInt().coerceIn(dp(TRAY_MIN_DP), dp(TRAY_MAX_DP))
        }
        if (view.qrPassTray.layoutParams.height != tray) {
            view.qrPassTray.updateLayoutParams { this.height = tray }
        }

        // Stop the wash short of the creative's edge rather than on it. Landing the fade
        // exactly there is the one way this composition fails — any rounding leaves a faint
        // coloured band above the image — so the page colour holds the last few dp. With the
        // band collapsed the same arithmetic simply runs the wash down the whole page.
        val wash = (height - tray - dp(WASH_CLEARANCE_DP)).coerceAtLeast(dp(WASH_MIN_DP))
        if (view.qrPassWash.layoutParams.height != wash) {
            view.qrPassWash.updateLayoutParams { this.height = wash }
        }
        requestPlateSize()
    }

    /**
     * Sizes the code to whatever the header and the creative leave behind.
     *
     * It wants to be [PLATE_MAX_DP] and will take less rather than clip: a tall status bar, a
     * large system font scale or a shorter device all eat into the block, and a fixed size
     * would push the instruction off the screen. It never goes below [PLATE_MIN_DP] — at that
     * point the code is still ~33 mm across, comfortably scannable — because a code too small
     * to scan defeats the whole screen.
     */
    private fun requestPlateSize() {
        val root = _binding?.root ?: return
        if (plateSizePending) return
        plateSizePending = true
        // A pre-draw callback, NOT root.post — and this is the whole reason the instruction
        // line used to be sliced in half by the creative's top edge.
        //
        // Sizing has to happen after the layout pass: setting layoutParams from inside a
        // layout listener is a requestLayout() while one is in flight, and the platform drops
        // it. But a plain post only *usually* lands after layout. When the band opened — the
        // ad arriving and the tray going from 0 to ~47 % — the post ran BEFORE the traversal
        // that shrank the block, so applyPlateSize measured the old, taller budget and gave
        // the code a size that no longer fitted. The block's own layout listener would have
        // corrected it, except plateSizePending was still true and swallowed that request.
        //
        // A pre-draw listener is guaranteed to run after measure and layout, so the budget it
        // reads is always the current one, and applyPlateSize is a no-op once the target stops
        // changing, so this settles in one or two frames rather than looping.
        OneShotPreDrawListener.add(root) {
            plateSizePending = false
            applyPlateSize()
        }
    }

    private fun applyPlateSize() {
        val view = _binding ?: return
        val block = view.qrPassBlock
        val plate = view.ivQrPassCode
        if (block.height <= 0 || block.width <= 0) return

        var occupied = 0
        for (index in 0 until block.childCount) {
            val child = block.getChildAt(index)
            if (child === plate || !child.isVisible) continue
            val params = child.layoutParams as? ViewGroup.MarginLayoutParams
            occupied += child.height + (params?.topMargin ?: 0) + (params?.bottomMargin ?: 0)
        }
        val plateMargin = (plate.layoutParams as? ViewGroup.MarginLayoutParams)?.topMargin ?: 0

        // The block is weight=1, so its measured height is already the budget: the header and
        // the creative have both been paid for by the time it is laid out.
        val budget = block.height - block.paddingTop - block.paddingBottom
        val byHeight = budget - occupied - plateMargin
        val byWidth = block.width - block.paddingStart - block.paddingEnd
        val target = minOf(dp(PLATE_MAX_DP), byHeight, byWidth)
            .coerceAtLeast(dp(PLATE_MIN_DP))

        if (plate.layoutParams.height == target && plate.layoutParams.width == target) return
        plate.layoutParams = plate.layoutParams.apply {
            width = target
            height = target
        }
    }

    // ───────────────────────── the creative ─────────────────────────

    private fun showSkeleton() {
        val view = binding
        trayState = TrayState.LOADING
        view.traySkeleton.isVisible = true
        skeletonAnimator = SkeletonShimmer.start(view.traySkeleton)
        view.root.postDelayed(trayDeadline, TRAY_DEADLINE_MS)
    }

    /**
     * Commits the band to whatever is live.
     *
     * The skeleton holds only until the ad request answers or [trayDeadline] fires. A
     * creative that arrives after the house promo has taken the band still replaces it: there
     * is no scroll position and no page to disturb, so the cost is one cross-fade and the
     * alternative is giving away a filled impression.
     */
    private fun renderTray() {
        val view = _binding ?: return
        val next = when {
            nativeLoaded -> TrayState.AD
            nativeResolved || deadlinePassed -> TrayState.EMPTY
            else -> TrayState.LOADING
        }
        if (next == trayState) return
        trayState = next
        if (next == TrayState.LOADING) return

        view.root.removeCallbacks(trayDeadline)
        stopSkeleton()

        // Collapsing and re-opening both go through the band arithmetic rather than touching
        // heights here, so the wash and the code follow the band in one pass either way.
        trayCollapsed = next == TrayState.EMPTY
        view.qrPassTray.isVisible = !trayCollapsed
        requestBandSizes()

        renderNavBarTone()
        if (next == TrayState.AD) SkeletonShimmer.revealView(view.nativeAd)
    }

    /**
     * What sits behind the gesture pill changes with the band.
     *
     * A creative owns the bottom of the screen and is dark whatever the app's theme, so the
     * pill has to go light over it. With no fill the band collapses and the page shows through
     * instead, which is light in day mode — leaving the pill light there would make it
     * invisible.
     */
    private fun renderNavBarTone() {
        val window = dialog?.window ?: return
        val night = (resources.configuration.uiMode and
            Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
        val overCreative = trayState == TrayState.AD
        WindowCompat.getInsetsController(window, window.decorView)
            .isAppearanceLightNavigationBars = !overCreative && !night
    }

    private fun stopSkeleton() {
        skeletonAnimator?.cancel()
        skeletonAnimator = null
        _binding?.traySkeleton?.isVisible = false
    }

    /**
     * Starts the ad request on the first frame.
     *
     * [BookingQrNativeAdView] is GONE until it has a creative, so it is attached — which is
     * all the load path needs — while having no area at all, which is what stops the SDK
     * logging an impression for a card sitting behind the skeleton.
     */
    private fun startNativeAd() {
        val native = binding.nativeAd
        // Captured once: an ad callback can land while the sheet is being torn down, and
        // requireContext() throws the moment the fragment detaches.
        val appContext = requireContext().applicationContext
        native.onAdLoaded = {
            nativeLoaded = true
            nativeResolved = true
            renderTray()
        }
        native.onAdUnavailable = {
            nativeLoaded = false
            nativeResolved = true
            renderTray()
        }
        native.onLoadEvent = { if (!isPreviewMode) AdRevenueAnalytics.logBookingQrLoad(appContext, it) }
        native.onAdImpression = { if (!isPreviewMode) AdRevenueAnalytics.logBookingQrImpression(appContext) }
        native.onAdClicked = { if (!isPreviewMode) AdRevenueAnalytics.logBookingQrClick(appContext) }
        native.onPaidEvent = { if (!isPreviewMode) AdRevenueAnalytics.logBookingQrPaidEvent(appContext, it) }
        native.onVideoEvent = { action, muted ->
            if (!isPreviewMode) AdRevenueAnalytics.logBookingQrVideoEvent(appContext, action, muted)
        }
        native.post { native.load() }
    }

    // ───────────────────────── window plumbing ─────────────────────────

    private fun configureInsets() {
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { _, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            // The header wraps its own content, so the status bar is paid for in padding
            // rather than by overwriting a fixed height with one.
            binding.qrPassNav.updatePadding(top = dp(NAV_VERTICAL_PADDING_DP) + systemBars.top)
            // The creative runs under the navigation bar on purpose — cropping it there would
            // leave a band of page colour under an edge-to-edge image — so the inset is paid
            // by its copy's padding instead.
            binding.nativeAd.applyBottomInset(systemBars.bottom)
            insets
        }
        ViewCompat.requestApplyInsets(binding.root)
    }

    private fun registerCinematicBackCallback() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU || backInvokedCallback != null) return
        val callback = OnBackInvokedCallback { dismiss() }
        dialog?.onBackInvokedDispatcher?.registerOnBackInvokedCallback(
            OnBackInvokedDispatcher.PRIORITY_DEFAULT,
            callback
        )
        backInvokedCallback = callback
    }

    private fun unregisterCinematicBackCallback() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        backInvokedCallback?.let { callback ->
            dialog?.onBackInvokedDispatcher?.unregisterOnBackInvokedCallback(callback)
        }
        backInvokedCallback = null
    }

    /** Fast cinematic Z-axis entrance: emerge, pass the resting plane, then settle. */
    private fun playDepthEntrance() {
        val root = _binding?.root ?: return
        entranceAnimator?.cancel()
        root.alpha = 0f
        root.scaleX = ENTRANCE_START_SCALE
        root.scaleY = ENTRANCE_START_SCALE
        root.doOnPreDraw {
            if (_binding == null || exitAnimationRunning) return@doOnPreDraw
            root.cameraDistance = resources.displayMetrics.density * CAMERA_DISTANCE_DP

            val emerge = ObjectAnimator.ofPropertyValuesHolder(
                root,
                PropertyValuesHolder.ofFloat(View.ALPHA, 0f, 1f),
                PropertyValuesHolder.ofFloat(View.SCALE_X, ENTRANCE_START_SCALE, ENTRANCE_OVERSHOOT_SCALE),
                PropertyValuesHolder.ofFloat(View.SCALE_Y, ENTRANCE_START_SCALE, ENTRANCE_OVERSHOOT_SCALE)
            ).apply {
                duration = ENTRANCE_EMERGE_MS
                interpolator = PathInterpolator(0.18f, 0.82f, 0.18f, 1f)
            }
            val settle = ObjectAnimator.ofPropertyValuesHolder(
                root,
                PropertyValuesHolder.ofFloat(View.SCALE_X, ENTRANCE_OVERSHOOT_SCALE, 1f),
                PropertyValuesHolder.ofFloat(View.SCALE_Y, ENTRANCE_OVERSHOOT_SCALE, 1f)
            ).apply {
                duration = ENTRANCE_SETTLE_MS
                interpolator = PathInterpolator(0.2f, 0f, 0f, 1f)
            }
            entranceAnimator = AnimatorSet().apply {
                playSequentially(emerge, settle)
                start()
            }
        }
    }

    private fun animateDepthExit(onFinished: () -> Unit) {
        val root = _binding?.root
        if (root == null || !root.isAttachedToWindow) {
            bypassExitAnimation = true
            onFinished()
            return
        }
        if (exitAnimationRunning) return
        exitAnimationRunning = true
        entranceAnimator?.cancel()
        root.animate().cancel()
        root.animate()
            .alpha(0f)
            .scaleX(EXIT_END_SCALE)
            .scaleY(EXIT_END_SCALE)
            .setDuration(EXIT_DURATION_MS)
            .setInterpolator(PathInterpolator(0.55f, 0f, 1f, 0.45f))
            .withEndAction {
                bypassExitAnimation = true
                onFinished()
            }
            .start()
    }

    override fun dismiss() {
        if (bypassExitAnimation || _binding == null) {
            super.dismiss()
        } else {
            animateDepthExit { super.dismiss() }
        }
    }

    override fun dismissAllowingStateLoss() {
        if (bypassExitAnimation || _binding == null) {
            super.dismissAllowingStateLoss()
        } else {
            animateDepthExit { super.dismissAllowingStateLoss() }
        }
    }

    override fun onDismiss(dialog: DialogInterface) {
        // Recreating the Dialog for rotation/theme changes is not a user dismissal. Preserve the
        // open-pass contract and deliver a real close at most once to the restored Bookings view.
        if (activity?.isChangingConfigurations != true && !dismissedResultDelivered && isAdded) {
            dismissedResultDelivered = true
            setFragmentResult(RESULT_KEY_DISMISSED, bundleOf(RESULT_BOOKING_ID to bookingId))
        }
        super.onDismiss(dialog)
    }

    override fun onDestroyView() {
        _binding?.root?.removeCallbacks(trayDeadline)
        stopSkeleton()
        entranceAnimator?.cancel()
        entranceAnimator = null
        unregisterCinematicBackCallback()
        _binding?.root?.animate()?.cancel()
        _binding?.nativeAd?.destroy()
        dialog?.window?.let { window ->
            window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            originalScreenBrightness?.let { original ->
                window.attributes = window.attributes.apply { screenBrightness = original }
            }
        }
        originalScreenBrightness = null
        exitAnimationRunning = false
        bypassExitAnimation = false
        _binding = null
        super.onDestroyView()
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    companion object {
        const val TAG = "BookingQrPassBottomSheet"
        const val RESULT_KEY_DISMISSED = "booking_qr_pass_dismissed"
        const val RESULT_BOOKING_ID = "booking_id"

        private const val ARG_PREVIEW_MODE = "preview_mode"
        private const val STATE_DISMISSED_RESULT_DELIVERED = "dismissed_result_delivered"
        private const val QR_BITMAP_SIZE_PX = 768

        /**
         * The code takes whatever the header and the creative leave it, up to this. Raising it
         * to 340 to close the gap under the instruction was tried and reverted — the code read
         * as oversized. The slack stays.
         */
        private const val PLATE_MAX_DP = 300

        /**
         * The floor, measured on the view rather than on the code: 212 dp is 33.6 mm, and the
         * 16 dp of padding inside it puts the modules themselves at 180 dp / 28.6 mm with the
         * generator's own 2-module quiet zone around them. Unchanged from what shipped — this
         * is a scanning dimension and not one to move without a barrier to test against.
         */
        private const val PLATE_MIN_DP = 212

        /**
         * The creative's share of the page: 396 of the design's 844 pt frame. Expressed as a
         * fraction so the split survives a taller phone, a shorter one and multi-window, with
         * dp bounds either side so it can neither swallow the code nor become a strip.
         */
        private const val TRAY_FRACTION = 0.47f
        private const val TRAY_MIN_DP = 300
        private const val TRAY_MAX_DP = 460

        /** How far above the creative's edge the wash has to be fully gone. */
        private const val WASH_CLEARANCE_DP = 28
        private const val WASH_MIN_DP = 160

        /** Must match the header's own paddingVertical in the layout. */
        private const val NAV_VERTICAL_PADDING_DP = 10

        private const val TRAY_DEADLINE_MS = 1_200L
        private const val CAMERA_DISTANCE_DP = 8_000f
        private const val ENTRANCE_START_SCALE = 0.93f
        private const val ENTRANCE_OVERSHOOT_SCALE = 1.012f
        private const val EXIT_END_SCALE = 0.93f
        private const val ENTRANCE_EMERGE_MS = 185L
        private const val ENTRANCE_SETTLE_MS = 55L
        private const val EXIT_DURATION_MS = 145L

        fun newInstance(booking: Booking, previewMode: Boolean = false) =
            BookingQrPassBottomSheet().apply {
                arguments = bundleOf(
                    ARG_BOOKING_PASS_SNAPSHOT to BookingPassSnapshot.from(booking).toBundle(),
                    ARG_PREVIEW_MODE to previewMode
                )
            }
    }
}
