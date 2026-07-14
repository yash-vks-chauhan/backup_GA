package com.gridee.parking.ui.bottomsheet

import android.app.Dialog
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.dynamicanimation.animation.DynamicAnimation
import androidx.dynamicanimation.animation.SpringAnimation
import androidx.dynamicanimation.animation.SpringForce
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.gridee.parking.R
import com.gridee.parking.data.api.ApiClient
import com.gridee.parking.data.model.ParkingSpot
import com.gridee.parking.utils.AuthSession
import com.gridee.parking.utils.ParkingSpotSchedulePolicy
import com.gridee.parking.databinding.BottomSheetParkingSpotBinding
import com.gridee.parking.databinding.BottomSheetTopUpBinding
import com.gridee.parking.ui.booking.BookingConfirmationActivity
import com.gridee.parking.ui.booking.BookingViewModel
import com.gridee.parking.ui.booking.ParkingSpotSelectionAdapter
import com.gridee.parking.ui.wallet.WalletTopUpActivity
import kotlinx.coroutines.launch
import retrofit2.Response
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

class ParkingSpotBottomSheet : BottomSheetDialogFragment() {

    private var _binding: BottomSheetParkingSpotBinding? = null
    private val binding get() = _binding!!

    private lateinit var viewModel: BookingViewModel

    private var parkingSpot: ParkingSpot? = null
    private var selectedLotId: String = ""
    private var selectedLotName: String = ""
    private var selectedSpotId: String = ""
    private var selectedSpotNameArg: String? = null
    private var selectedSpotZoneNameArg: String? = null
    private var selectedSpotCodeArg: String? = null
    private var selectedSpotSlotNameArg: String? = null



    private var isBookingInProgress = false
    private var btnFullWidth = 0
    private var glowAnimator: android.animation.ValueAnimator? = null
    private var pendingConfirmationIntent: Intent? = null

    // Lift & Settle transition state
    private var backdropAnimator: android.animation.ValueAnimator? = null
    private var sheetSpring: SpringAnimation? = null
    private var currentBackdropProgress: Float = 0f
    private var isProgrammaticAnimation: Boolean = false
    // Transparent overlay parked inside the sheet during the entrance so a fast
    // tap can't activate a card before the animation has finished placing it.
    private var touchShield: View? = null
    // Reaction animations for content that changes after the sheet has settled —
    // keep references so we can cancel cleanly on rapid updates and teardown.
    private var noticeAnimator: android.animation.ValueAnimator? = null

    // ── Skeleton placeholders ───────────────────────────────────────
    // While the sheet's content loads (parking spot, vehicles, wallet, price),
    // rounded skeleton pills sit in place of each async field and pulse gently.
    // A single animator drives all visible skeletons so they breathe in unison.
    private var skeletonBreathAnimator: android.animation.ValueAnimator? = null
    // Skeletons mid-fade-out are excluded from the breath pulse so the two
    // alpha animations don't fight on the same view.
    private val resolvingSkeletons = mutableSetOf<View>()

    // ── Reveal cascade coordinator ──────────────────────────────────
    // Network calls return at their own pace, so several fields can resolve in
    // the same frame. Rather than snap them all in at once (or let them pop
    // field-by-field), reveals are batched per frame and fired top-to-bottom
    // with a small stagger — content settles down the sheet in reading order.
    // A lone reveal fires with no delay, so this never adds latency when only
    // one field lands.
    private data class PendingReveal(val order: Int, val start: () -> Unit)
    private val pendingReveals = mutableListOf<PendingReveal>()
    // Skeletons queued for reveal this frame but not yet animating. They keep
    // breathing until their staggered turn, so there's no frozen-pill gap.
    private val scheduledReveals = mutableSetOf<View>()
    private var revealFlushPosted = false
    private val revealStaggerMs = 45L
    // Material 3 emphasized-decelerate — the real value rides in on this so it
    // settles into place rather than blinking on. Cached so reveals don't each
    // allocate a fresh interpolator.
    private val arrivalInterpolator by lazy {
        androidx.core.view.animation.PathInterpolatorCompat.create(0.2f, 0f, 0f, 1f)
    }
    // How far the arriving value drifts up as it fades in (dp → px). Small on
    // purpose: enough to read as "landing," not enough to look like a slide.
    private val arrivalRisePx: Float by lazy { 7f * resources.displayMetrics.density }
    // Per-field flags: a skeleton resolves exactly once, on the first emission
    // that carries real data — subsequent emissions just update text in place.
    private var parkingSpotResolvedForSkeleton = false
    private var vehicleSkeletonResolved = false
    private var walletSkeletonResolved = false
    private var totalPriceSkeletonResolved = false
    private var durationSkeletonResolved = false
    private var nameSkeletonResolved = false
    private var metaSkeletonResolved = false

    // The sheet's drawable uses 40dp top corners; the host activity behind it
    // gets 32dp — slightly smaller so the receding page sits one step "deeper"
    // in the visual stack while still clearly belonging to the same family.
    private val hostCornerRadiusPx: Float by lazy {
        32f * resources.displayMetrics.density
    }
    private val hostScaleOutline = object : android.view.ViewOutlineProvider() {
        override fun getOutline(view: View, outline: android.graphics.Outline) {
            val radius = hostCornerRadiusPx * currentBackdropProgress
            outline.setRoundRect(0, 0, view.width, view.height, radius)
        }
    }

    // Cached so applyBackdropProgress doesn't walk the host's view tree on
    // every animation frame. Refreshed once when the dialog is shown.
    private var hostContentView: View? = null
    // Set once at show time so the per-frame path doesn't have to query the
    // WindowManager service to decide whether to push blur radius updates.
    private var crossWindowBlurSupported: Boolean = false

    private var bottomSheetBehavior: BottomSheetBehavior<*>? = null
    private val bottomSheetCallback = object : BottomSheetBehavior.BottomSheetCallback() {
        override fun onStateChanged(bottomSheet: View, newState: Int) {
            if (newState == BottomSheetBehavior.STATE_EXPANDED ||
                newState == BottomSheetBehavior.STATE_HIDDEN
            ) {
                bottomSheet.performHapticFeedback(android.view.HapticFeedbackConstants.CONTEXT_CLICK)
            }

            if (newState == BottomSheetBehavior.STATE_COLLAPSED ||
                newState == BottomSheetBehavior.STATE_EXPANDED
            ) {
                animateHandle(32)
            }
        }

        override fun onSlide(bottomSheet: View, slideOffset: Float) {
            animateHandle(48)
            if (isProgrammaticAnimation) return
            // While the user is dragging, the backdrop tracks the sheet's position
            // so the world behind responds to the gesture in real time.
            val progress = slideOffset.coerceIn(0f, 1f)
            applyBackdropProgress(progress)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setStyle(STYLE_NORMAL, R.style.BottomSheetDialogTheme)

        arguments?.let { args ->
            selectedSpotId = args.getString(ARG_PARKING_SPOT_ID).orEmpty()
            selectedLotId = args.getString(ARG_PARKING_LOT_ID).orEmpty()
            selectedLotName = args.getString(ARG_PARKING_LOT_NAME).orEmpty()
            selectedSpotNameArg = args.getString(ARG_PARKING_SPOT_NAME)
            selectedSpotZoneNameArg = args.getString(ARG_PARKING_SPOT_ZONE_NAME)
            selectedSpotCodeArg = args.getString(ARG_PARKING_SPOT_CODE)
            selectedSpotSlotNameArg = args.getString(ARG_PARKING_SPOT_SLOT_NAME)
        }
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val dialog = super.onCreateDialog(savedInstanceState)
        // Start with no dim — the entrance animator drives it up so the dim arrives
        // with the sheet rather than snapping on before the motion begins.
        dialog.window?.setDimAmount(0f)
        // Cache the host activity's content view once so the per-frame backdrop
        // path doesn't walk the view tree on every tick of the animation.
        hostContentView = activity?.window?.decorView?.findViewById(android.R.id.content)
        // Resolve cross-window blur support up front. FLAG_BLUR_BEHIND alone
        // isn't enough — on devices where the system has disabled blur (battery
        // saver, low-end GPU, OEM setting), the radius is silently ignored and
        // every per-frame WindowManager update would be wasted IPC.
        crossWindowBlurSupported =
            android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S &&
            (requireContext().getSystemService(Context.WINDOW_SERVICE) as? android.view.WindowManager)
                ?.isCrossWindowBlurEnabled == true
        dialog.setOnShowListener { dialogInterface ->
            val bottomSheetDialog = dialogInterface as BottomSheetDialog

            val bottomSheet =
                bottomSheetDialog.findViewById<View>(com.google.android.material.R.id.design_bottom_sheet)
            bottomSheet?.let { sheet ->
                sheet.setBackgroundResource(R.drawable.bg_bottom_sheet_universal)
                sheet.fitsSystemWindows = false

                val params = sheet.layoutParams as? ViewGroup.MarginLayoutParams
                params?.setMargins(0, 0, 0, 0)
                sheet.layoutParams = params

                // Park the sheet off-screen immediately so Material's default slide
                // doesn't flash a partial reveal before our spring takes over.
                sheet.alpha = 1f
                sheet.scaleX = 1f
                sheet.scaleY = 1f
                sheet.translationY = resources.displayMetrics.heightPixels.toFloat()

                sheet.post { animateLiftEntrance(sheet) }
            }

            bottomSheetDialog.behavior.isGestureInsetBottomIgnored = true

            bottomSheetDialog.window?.let { window ->
                WindowCompat.setDecorFitsSystemWindows(window, false)
                window.navigationBarColor = android.graphics.Color.TRANSPARENT
                window.isNavigationBarContrastEnforced = false

                val isLightMode = (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) != Configuration.UI_MODE_NIGHT_YES
                val wic = WindowCompat.getInsetsController(window, window.decorView)
                wic.isAppearanceLightNavigationBars = isLightMode
                wic.isAppearanceLightStatusBars = isLightMode

                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
                    window.navigationBarDividerColor = android.graphics.Color.TRANSPARENT
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                        window.isNavigationBarContrastEnforced = false
                    }
                }

                if (crossWindowBlurSupported) {
                    // FLAG_BLUR_BEHIND is required for blurBehindRadius to have any
                    // effect — without it the value is silently ignored.
                    window.addFlags(android.view.WindowManager.LayoutParams.FLAG_BLUR_BEHIND)
                    val attrs = window.attributes
                    attrs.blurBehindRadius = 0
                    window.attributes = attrs
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
        _binding = BottomSheetParkingSpotBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        viewModel = ViewModelProvider(
            this,
            ViewModelProvider.AndroidViewModelFactory.getInstance(requireActivity().application)
        )[BookingViewModel::class.java]

        setupInsets()
        setupBehaviors()
        setupUI()
        // Mask the async fields before the observers are wired — a LiveData
        // value can be delivered synchronously the moment we start observing,
        // and we never want it to paint at full alpha for a frame.
        initializeSkeletons()
        // The parking name arrives as an argument, so it's known on the first
        // frame. Show it solid now (no skeleton) so the sheet's most prominent
        // line lifts in with the content instead of popping after the network.
        prefillKnownFieldsFromArgs()
        setupClickListeners()
        setupObservers()

        loadParkingSpot(selectedSpotId)

        // The content rides up with the sheet as a single object — no independent
        // per-child stagger. One coordinated motion reads as cinematic and confident.
    }

    override fun onStart() {
        super.onStart()
        (dialog as? BottomSheetDialog)?.let { bottomSheetDialog ->
            val bottomSheet =
                bottomSheetDialog.findViewById<View>(com.google.android.material.R.id.design_bottom_sheet)
            bottomSheet?.layoutParams = bottomSheet?.layoutParams?.apply {
                height = ViewGroup.LayoutParams.MATCH_PARENT
            }
            bottomSheet?.requestLayout()
            bottomSheetDialog.behavior.state = BottomSheetBehavior.STATE_EXPANDED
            bottomSheetDialog.behavior.skipCollapsed = true
        }
    }

    override fun onResume() {
        super.onResume()
        viewModel.loadUserVehicles()
        viewModel.loadWalletBalance()
    }

    override fun dismiss() {
        animateLiftExit { super.dismiss() }
    }

    override fun dismissAllowingStateLoss() {
        animateLiftExit { super.dismissAllowingStateLoss() }
    }

    // ── Lift & Settle: unified entrance ─────────────────────────────
    // The sheet rises on a critically-damped spring while the world behind it
    // responds in sync — page scales down a hair, dim deepens, blur ramps up,
    // and the host activity picks up matching rounded corners so it reads as
    // a card receding behind the sheet rather than a flat shrink.
    //
    // Touch is gated for the duration of the entrance: a transparent shield
    // sits over the content (blocks taps) and the sheet behavior's drag is
    // disabled (blocks pulls). Both release the moment the motion settles.
    private fun animateLiftEntrance(sheet: View) {
        val sheetHeight = if (sheet.height > 0) sheet.height.toFloat()
            else resources.displayMetrics.heightPixels.toFloat()

        // Accessibility: if the system has reduced motion (animations off or
        // duration scale 0), skip the choreography and place the sheet at rest.
        if (shouldReduceMotion()) {
            sheet.translationY = 0f
            applyBackdropProgress(1f)
            isProgrammaticAnimation = false
            return
        }

        isProgrammaticAnimation = true
        bottomSheetBehavior?.isDraggable = false
        engageTouchShield()
        sheet.translationY = sheetHeight

        sheetSpring?.cancel()
        sheetSpring = SpringAnimation(sheet, DynamicAnimation.TRANSLATION_Y, 0f).apply {
            // Stiffness 300 + critical damping settles in ~460ms — the same window
            // the backdrop animator runs on, so motion and depth resolve together.
            spring = SpringForce(0f).apply {
                dampingRatio = 1.0f
                stiffness = 300f
            }
            addEndListener { _, canceled, _, _ ->
                // Settle haptic — one soft tick the moment the sheet arrives.
                // Skipped if the spring was interrupted (e.g. user dragged early).
                if (!canceled && _binding != null) {
                    sheet.performHapticFeedback(
                        android.view.HapticFeedbackConstants.CLOCK_TICK
                    )
                }
            }
            start()
        }

        backdropAnimator?.cancel()
        backdropAnimator = android.animation.ValueAnimator.ofFloat(currentBackdropProgress, 1f).apply {
            duration = 460
            interpolator = android.view.animation.PathInterpolator(0.2f, 0.0f, 0.0f, 1.0f)
            addUpdateListener { anim -> applyBackdropProgress(anim.animatedValue as Float) }
            addListener(object : android.animation.AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: android.animation.Animator) {
                    finishEntranceGate()
                }
                override fun onAnimationCancel(animation: android.animation.Animator) {
                    finishEntranceGate()
                }
            })
            start()
        }
    }

    private fun finishEntranceGate() {
        isProgrammaticAnimation = false
        bottomSheetBehavior?.isDraggable = true
        releaseTouchShield()
    }

    private fun engageTouchShield() {
        val sheet = (dialog as? BottomSheetDialog)
            ?.findViewById<ViewGroup>(com.google.android.material.R.id.design_bottom_sheet)
            ?: return
        if (touchShield != null) return
        val shield = View(sheet.context).apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
            // setOnClickListener forces isClickable = true, which is what makes
            // this view absorb taps that would otherwise reach the cards below.
            setOnClickListener { /* swallow */ }
            isFocusable = false
            contentDescription = null
        }
        sheet.addView(shield)
        touchShield = shield
    }

    private fun releaseTouchShield() {
        val shield = touchShield ?: return
        (shield.parent as? ViewGroup)?.removeView(shield)
        touchShield = null
    }

    private fun shouldReduceMotion(): Boolean {
        // True when the user has disabled animations system-wide (Developer
        // Options → animation scale = 0, or Accessibility → Remove animations).
        return !android.animation.ValueAnimator.areAnimatorsEnabled()
    }

    // ── Lift & Settle: unified exit ─────────────────────────────────
    // Mirrors the entrance with a stiffer spring — crisper on the way out.
    // Has two paths:
    //   • Programmatic dismiss (tap close, back press): full spring exit.
    //   • Swipe dismiss: Material already moved the sheet to STATE_HIDDEN,
    //     so the spring would be invisible work that just delays the window
    //     teardown. We fast-path past it — release the backdrop and close.
    private fun animateLiftExit(onComplete: () -> Unit) {
        // Accessibility: snap out without animation if the system requests it.
        if (shouldReduceMotion()) {
            backdropAnimator?.cancel()
            sheetSpring?.cancel()
            resetBackdrop()
            onComplete()
            return
        }

        val bottomSheet = (dialog as? BottomSheetDialog)
            ?.findViewById<View>(com.google.android.material.R.id.design_bottom_sheet)
        val sheetAlreadyOffscreen =
            bottomSheetBehavior?.state == BottomSheetBehavior.STATE_HIDDEN
        val startProgress = currentBackdropProgress

        if (bottomSheet == null || sheetAlreadyOffscreen) {
            // Fast path — sheet is already gone visually. Just collapse the
            // remaining backdrop and dismiss without waiting on any spring.
            backdropAnimator?.cancel()
            sheetSpring?.cancel()
            if (startProgress < 0.02f) {
                resetBackdrop()
                onComplete()
                return
            }
            isProgrammaticAnimation = true
            backdropAnimator = android.animation.ValueAnimator.ofFloat(startProgress, 0f).apply {
                duration = (180 * startProgress).toLong().coerceAtLeast(90)
                interpolator = android.view.animation.PathInterpolator(0.4f, 0.0f, 1.0f, 1.0f)
                addUpdateListener { anim -> applyBackdropProgress(anim.animatedValue as Float) }
                addListener(object : android.animation.AnimatorListenerAdapter() {
                    override fun onAnimationEnd(animation: android.animation.Animator) {
                        resetBackdrop()
                        isProgrammaticAnimation = false
                        onComplete()
                    }
                })
                start()
            }
            return
        }

        // Full programmatic exit — spring sheet down + collapse backdrop in sync.
        isProgrammaticAnimation = true
        val durationScale = startProgress.coerceAtLeast(0.5f)

        backdropAnimator?.cancel()
        backdropAnimator = android.animation.ValueAnimator.ofFloat(startProgress, 0f).apply {
            duration = (260 * durationScale).toLong()
            interpolator = android.view.animation.PathInterpolator(0.4f, 0.0f, 1.0f, 1.0f)
            addUpdateListener { anim -> applyBackdropProgress(anim.animatedValue as Float) }
            addListener(object : android.animation.AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: android.animation.Animator) {
                    resetBackdrop()
                }
            })
            start()
        }

        val sheetHeight = if (bottomSheet.height > 0) bottomSheet.height.toFloat()
            else resources.displayMetrics.heightPixels.toFloat()

        sheetSpring?.cancel()
        sheetSpring = SpringAnimation(bottomSheet, DynamicAnimation.TRANSLATION_Y, sheetHeight).apply {
            // Exit stiffness 520 settles in ~300ms — about 65% of entrance time,
            // the standard "leave-faster-than-you-arrive" ratio.
            spring = SpringForce(sheetHeight).apply {
                dampingRatio = 1.0f
                stiffness = 520f
            }
            addEndListener { _, _, _, _ ->
                isProgrammaticAnimation = false
                onComplete()
            }
            start()
        }
    }

    private fun applyBackdropProgress(t: Float) {
        currentBackdropProgress = t
        hostContentView?.let {
            // 3% shrink — visible enough to read as depth, restrained enough to
            // not call attention to itself.
            val scale = 1f - (0.03f * t)
            it.scaleX = scale
            it.scaleY = scale
            // Round the receding page's corners in step with the sheet's so it
            // reads as a card lift rather than a flat zoom.
            if (it.outlineProvider !== hostScaleOutline) {
                it.outlineProvider = hostScaleOutline
                it.clipToOutline = true
            }
            it.invalidateOutline()
        }
        dialog?.window?.setDimAmount(0.35f * t)
        if (crossWindowBlurSupported) {
            dialog?.window?.let { window ->
                val attrs = window.attributes
                // 28px — iOS-tier blur intensity. Stronger reads as smudge.
                attrs.blurBehindRadius = (28f * t).toInt()
                window.attributes = attrs
            }
        }
    }

    private fun resetBackdrop() {
        currentBackdropProgress = 0f
        hostContentView?.let {
            it.scaleX = 1f
            it.scaleY = 1f
            it.clipToOutline = false
            it.outlineProvider = android.view.ViewOutlineProvider.BACKGROUND
        }
    }

    private fun setupInsets() {
        // Post to the view queue to guarantee the view is attached to `design_bottom_sheet`
        binding.root.post {
            val bottomSheet = binding.root.parent as? View
            bottomSheet?.let { sheet ->
                // Clear any incorrectly pre-applied padding from Material Components
                sheet.setPadding(sheet.paddingLeft, sheet.paddingTop, sheet.paddingRight, 0)
                
                ViewCompat.setOnApplyWindowInsetsListener(sheet) { v, insets ->
                    val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
                    
                    // Extend the white footer down dynamically
                    binding.bookingSummary.setPadding(
                        binding.bookingSummary.paddingLeft,
                        binding.bookingSummary.paddingTop,
                        binding.bookingSummary.paddingRight,
                        bars.bottom
                    )
                    
                    // Strip the Material bottom sheet of its default padding gap
                    v.setPadding(v.paddingLeft, v.paddingTop, v.paddingRight, 0)
                    
                    // Consume the bottom inset to suppress any further system shifts
                    androidx.core.view.WindowInsetsCompat.Builder(insets)
                        .setInsets(
                            androidx.core.view.WindowInsetsCompat.Type.systemBars(),
                            androidx.core.graphics.Insets.of(bars.left, bars.top, bars.right, 0)
                        )
                        .build()
                }
                ViewCompat.requestApplyInsets(sheet)
            }
        }
    }

    private fun setupBehaviors() {
        bottomSheetBehavior = (dialog as? BottomSheetDialog)?.behavior
        bottomSheetBehavior?.addBottomSheetCallback(bottomSheetCallback)
    }

    private fun animateHandle(targetWidthDp: Int) {
        val binding = _binding ?: return
        val targetWidthPx = (targetWidthDp * resources.displayMetrics.density).toInt()
        if (binding.dragHandle.layoutParams.width != targetWidthPx) {
            val params = binding.dragHandle.layoutParams
            params.width = targetWidthPx
            binding.dragHandle.layoutParams = params
        }
    }

    private fun setupUI() {


        val (start, end) = getDefaultBookingWindow()

        val normalized = normalizeBookingTimes(start, end)
        viewModel.setStartTime(normalized.start.time)
        viewModel.setEndTime(normalized.end.time)
        showBookingNotice(normalized.message ?: initialBookingNotice())
    }

    private fun setupClickListeners() {
        binding.btnClose.setOnClickListener { dismiss() }

        // Check-in / check-out times are fixed for the selected slot, so the
        // time cards are display-only. No date/time picker is opened on tap.
        binding.cardVehicleSelection.setOnClickListener {
            it.performHapticFeedback(android.view.HapticFeedbackConstants.CONTEXT_CLICK)
            showVehicleSelectionBottomSheet()
        }
        setupConfirmButton()

        binding.btnAddMoney.setOnClickListener {
            it.performHapticFeedback(android.view.HapticFeedbackConstants.CONTEXT_CLICK)
            showTopUpDialog()
        }
    }

    // ── Confirm Button: Setup ─────────────────────────────────────────

    @android.annotation.SuppressLint("ClickableViewAccessibility")
    private fun setupConfirmButton() {
        val btn = binding.btnConfirmContainer

        // Reset state from any previous usage
        isBookingInProgress = false
        btn.isClickable = true
        btn.alpha = 1f
        val lp = btn.layoutParams
        lp.width = android.view.ViewGroup.LayoutParams.MATCH_PARENT
        btn.layoutParams = lp
        binding.tvConfirmBooking.alpha = 1f
        binding.tvConfirmBooking.text = "Confirm Booking"
        binding.progressConfirm.visibility = View.GONE
        binding.ivConfirmCheck.visibility = View.GONE
        resetConfirmButtonBackground()

        // Capture full width after layout for morph calculations
        btn.post { btnFullWidth = btn.width }

        // Spring-based press animation + elevation lift + haptics
        btn.setOnTouchListener { v, event ->
            if (isBookingInProgress) return@setOnTouchListener false
            when (event.actionMasked) {
                android.view.MotionEvent.ACTION_DOWN -> {
                    v.performHapticFeedback(android.view.HapticFeedbackConstants.CONTEXT_CLICK)
                    springScale(v, 0.96f)
                    v.animate().translationZ(4f).setDuration(80).start()
                    false
                }
                android.view.MotionEvent.ACTION_UP,
                android.view.MotionEvent.ACTION_CANCEL -> {
                    springScale(v, 1f)
                    v.animate().translationZ(0f).setDuration(150).start()
                    false
                }
                else -> false
            }
        }

        btn.setOnClickListener {
            if (isBookingInProgress) return@setOnClickListener
            it.performHapticFeedback(android.view.HapticFeedbackConstants.CONFIRM)
            createBooking()
        }

        // Start idle breathing glow
        btn.post { startIdleGlow() }
    }

    private fun springScale(view: View, target: Float) {
        SpringAnimation(view, DynamicAnimation.SCALE_X, target).apply {
            spring = SpringForce(target).setDampingRatio(0.55f).setStiffness(800f)
        }.start()
        SpringAnimation(view, DynamicAnimation.SCALE_Y, target).apply {
            spring = SpringForce(target).setDampingRatio(0.55f).setStiffness(800f)
        }.start()
    }

    // ── Confirm Button: Idle Breathing Glow ─────────────────────────

    private fun startIdleGlow() {
        if (glowAnimator?.isRunning == true) return
        val btn = binding.btnConfirmContainer

        // Gentle breathing via elevation shadow — no layer type changes
        glowAnimator = android.animation.ValueAnimator.ofFloat(0f, 6f, 0f).apply {
            duration = 3000
            repeatCount = android.animation.ValueAnimator.INFINITE
            interpolator = android.view.animation.AccelerateDecelerateInterpolator()
            addUpdateListener { anim ->
                btn.translationZ = anim.animatedValue as Float
            }
        }
        glowAnimator?.start()
    }

    private fun stopIdleGlow() {
        glowAnimator?.cancel()
        glowAnimator = null
        if (_binding != null) {
            binding.btnConfirmContainer.translationZ = 0f
        }
    }

    // ── Confirm Button: Morph to Loading Circle ─────────────────────
    //
    // The pill collapses to a 56dp circle on Material 3 emphasized easing
    // (PathInterpolator 0.2,0,0,1.0) over 380ms. The label fades out on the
    // first half of the motion, the spinner fades in on the second — so the
    // user reads a single continuous gesture rather than two stacked fades.

    private fun morphToLoading() {
        isBookingInProgress = true
        stopIdleGlow()

        val btn = binding.btnConfirmContainer
        val label = binding.tvConfirmBooking
        val progress = binding.progressConfirm
        val check = binding.ivConfirmCheck
        val targetSize = btn.height // 56dp → becomes a circle

        // Haptic tick as morph starts
        btn.performHapticFeedback(android.view.HapticFeedbackConstants.CLOCK_TICK)

        val emphasized = androidx.core.view.animation.PathInterpolatorCompat
            .create(0.2f, 0f, 0f, 1f)

        // Fade out label, slight scale-down so it doesn't read as a hard cut
        label.animate()
            .alpha(0f)
            .scaleX(0.92f).scaleY(0.92f)
            .setDuration(160)
            .setInterpolator(emphasized)
            .start()

        // Morph width to a circle on emphasized easing
        val widthAnim = android.animation.ValueAnimator.ofInt(btn.width, targetSize).apply {
            duration = 380
            interpolator = emphasized
            addUpdateListener { anim ->
                val w = anim.animatedValue as Int
                val lp = btn.layoutParams
                lp.width = w
                btn.layoutParams = lp
            }
        }
        widthAnim.start()

        // Spinner fades up on the back half of the morph so it arrives just
        // as the circle settles, rather than after a hard pause.
        progress.alpha = 0f
        progress.scaleX = 0.7f
        progress.scaleY = 0.7f
        progress.visibility = View.VISIBLE
        check.visibility = View.GONE
        progress.animate()
            .alpha(1f).scaleX(1f).scaleY(1f)
            .setDuration(220)
            .setStartDelay(180)
            .setInterpolator(emphasized)
            .start()

        btn.isClickable = false
    }

    // ── Confirm Button: Success State ───────────────────────────────
    //
    // The circle's background crossfades from dark → success-green via
    // ArgbEvaluator (no hard color flip), the spinner crossfades out, and
    // the new check drawable stroke-draws itself on (no overshoot bounce).
    // Once the check has settled, the circular-reveal handoff to
    // BookingConfirmationActivity begins — the button is the seed of the
    // next screen, not a separate UI that disappears.

    private fun showSuccessState(onComplete: () -> Unit) {
        if (_binding == null) { onComplete(); return }

        val btn = binding.btnConfirmContainer
        val progress = binding.progressConfirm
        val check = binding.ivConfirmCheck
        val context = requireContext()
        val startColor = ContextCompat.getColor(context, R.color.confirm_btn_bg)
        val successColor = ContextCompat.getColor(context, R.color.confirm_btn_success_bg)

        // Haptic: rewarding double-pulse
        performSuccessHaptic()

        val emphasized = androidx.core.view.animation.PathInterpolatorCompat
            .create(0.2f, 0f, 0f, 1f)

        // Crossfade spinner out — straight alpha, no scale (the previous
        // 0.5x scale-down read as a glitch when the check appeared on top).
        progress.animate()
            .alpha(0f)
            .setDuration(160)
            .setInterpolator(emphasized)
            .withEndAction { progress.visibility = View.GONE }
            .start()

        // Check fades up cleanly at 1:1 scale; the AVD draws the stroke
        // itself for the satisfying part of the motion.
        check.visibility = View.VISIBLE
        check.alpha = 0f
        check.scaleX = 1f
        check.scaleY = 1f
        check.animate()
            .alpha(1f)
            .setDuration(180)
            .setInterpolator(emphasized)
            .withStartAction {
                (check.drawable as? android.graphics.drawable.Animatable)?.let { animatable ->
                    (animatable as? android.graphics.drawable.AnimatedVectorDrawable)?.reset()
                    animatable.start()
                }
            }
            .start()

        // Smooth ArgbEvaluator crossfade dark → green (~320ms)
        animateButtonBackgroundColor(btn, startColor, successColor, 320L, emphasized)

        // After the check has *mostly* finished drawing, kick off the reveal
        // handoff. We don't wait for the full 340ms stroke — the last 80ms is
        // a perceptual tail anyway, and starting the reveal early prevents the
        // success pill from sitting static on screen.
        btn.postDelayed({
            performRevealHandoff(successColor, onComplete)
        }, 280)
    }

    // Animates the button's background fill color smoothly using ArgbEvaluator,
    // handling both RippleDrawable-wrapped and bare GradientDrawable cases.
    private fun animateButtonBackgroundColor(
        view: View,
        fromColor: Int,
        toColor: Int,
        durationMs: Long,
        interpolator: android.view.animation.Interpolator,
    ) {
        view.background = view.background.mutate()
        val animator = android.animation.ValueAnimator.ofObject(
            android.animation.ArgbEvaluator(), fromColor, toColor
        ).apply {
            duration = durationMs
            this.interpolator = interpolator
            addUpdateListener { anim ->
                applyButtonFillColor(view, anim.animatedValue as Int)
            }
        }
        animator.start()
    }

    private fun applyButtonFillColor(view: View, color: Int) {
        val bgDrawable = view.background ?: return
        when (bgDrawable) {
            is android.graphics.drawable.RippleDrawable -> {
                val shape = bgDrawable.findDrawableByLayerId(0) as? android.graphics.drawable.GradientDrawable
                    ?: (bgDrawable.getDrawable(0) as? android.graphics.drawable.GradientDrawable)
                shape?.setColor(color)
            }
            is android.graphics.drawable.GradientDrawable -> bgDrawable.setColor(color)
        }
    }

    // ── Reveal handoff: button → fullscreen → next activity ────────
    //
    // After the check finishes drawing, a fullscreen green view is added to
    // the dialog window and a circular reveal expands it from the button's
    // center to the screen's furthest corner. The next activity is launched
    // mid-reveal so its own matching overlay carries the colour through
    // without a frame of flash; the activity then fades the overlay out to
    // reveal its content. To the user it reads as one continuous beat.

    private fun performRevealHandoff(color: Int, onComplete: () -> Unit) {
        val pendingIntent = pendingConfirmationIntent
        if (_binding == null || pendingIntent == null) { onComplete(); return }

        val btn = binding.btnConfirmContainer
        val dialogWindow = dialog?.window
        val dialogDecor = dialogWindow?.decorView as? ViewGroup
        if (dialogWindow == null || dialogDecor == null) { onComplete(); return }

        // Compute the button's center, both in window-local coords (for the
        // reveal animator on this overlay) and in screen coords (forwarded to
        // the activity so its overlay can spawn its content from the same
        // point if it wants to).
        val btnLocationOnScreen = IntArray(2).also { btn.getLocationOnScreen(it) }
        val decorLocationOnScreen = IntArray(2).also { dialogDecor.getLocationOnScreen(it) }
        val screenCenterX = btnLocationOnScreen[0] + btn.width / 2
        val screenCenterY = btnLocationOnScreen[1] + btn.height / 2
        val localCenterX = screenCenterX - decorLocationOnScreen[0]
        val localCenterY = screenCenterY - decorLocationOnScreen[1]

        // Build the overlay that will swallow the screen.
        val overlay = View(requireContext()).apply {
            setBackgroundColor(color)
            isClickable = true
            isFocusable = true
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        }
        dialogDecor.addView(overlay)

        val w = dialogDecor.width
        val h = dialogDecor.height
        val maxRadius = kotlin.math.hypot(
            maxOf(localCenterX, w - localCenterX).toDouble(),
            maxOf(localCenterY, h - localCenterY).toDouble()
        ).toFloat()

        val reveal = android.view.ViewAnimationUtils.createCircularReveal(
            overlay, localCenterX, localCenterY, btn.height / 2f, maxRadius
        ).apply {
            // Shorter dialog reveal: it only needs to cover the screen so the
            // activity-side contract can take over — any longer and the user
            // perceives it as a separate "wall of green" beat.
            duration = 320L
            interpolator = androidx.core.view.animation.PathInterpolatorCompat
                .create(0.2f, 0f, 0f, 1f)
        }

        // Forward reveal coords to the activity so its entrance animator can
        // anchor its own overlay/content to the same focal point.
        pendingIntent.putExtra("REVEAL_FROM_SUCCESS", true)
        pendingIntent.putExtra("REVEAL_CENTER_X", screenCenterX)
        pendingIntent.putExtra("REVEAL_CENTER_Y", screenCenterY)

        reveal.addListener(object : android.animation.AnimatorListenerAdapter() {
            override fun onAnimationEnd(animation: android.animation.Animator) {
                // Hand off: the overlay covers the screen at this point, so
                // the activity launch and dialog teardown are invisible.
                onComplete()
            }
        })
        reveal.start()
    }

    private fun performSuccessHaptic() {
        val btn = binding.btnConfirmContainer
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            val vibrator = requireContext().getSystemService(Context.VIBRATOR_SERVICE) as? android.os.Vibrator
            val effect = android.os.VibrationEffect.createWaveform(
                longArrayOf(0, 40, 60, 40), // two quick taps
                intArrayOf(0, 180, 0, 180),
                -1
            )
            vibrator?.vibrate(effect)
        } else {
            btn.performHapticFeedback(android.view.HapticFeedbackConstants.CONFIRM)
        }
    }

    // ── Confirm Button: Error State (shake + expand back) ───────────

    private fun showErrorState() {
        if (_binding == null) return

        val btn = binding.btnConfirmContainer
        val progress = binding.progressConfirm
        val label = binding.tvConfirmBooking

        // Reject haptic
        btn.performHapticFeedback(android.view.HapticFeedbackConstants.REJECT)

        // Shake animation
        val shake = android.animation.ObjectAnimator.ofFloat(btn, "translationX",
            0f, -10f, 10f, -8f, 8f, -4f, 4f, 0f
        ).apply {
            duration = 400
            interpolator = android.view.animation.DecelerateInterpolator()
        }
        shake.start()

        // Fade out spinner
        progress.animate().alpha(0f).setDuration(100).withEndAction {
            progress.visibility = View.GONE
        }.start()

        // Expand back to full width
        btn.postDelayed({
            if (_binding == null) return@postDelayed
            morphToFullWidth {
                label.animate().alpha(1f).setDuration(150).start()
                isBookingInProgress = false
                btn.isClickable = true
                startIdleGlow()
                updateConfirmButtonState()
            }
        }, 300)
    }

    // ── Confirm Button: Morph Back to Pill ──────────────────────────

    private fun morphToFullWidth(onComplete: (() -> Unit)? = null) {
        val btn = binding.btnConfirmContainer
        val targetWidth = if (btnFullWidth > 0) btnFullWidth
            else (binding.btnConfirmWrapper.width)

        val widthAnim = android.animation.ValueAnimator.ofInt(btn.width, targetWidth).apply {
            duration = 280
            interpolator = android.view.animation.DecelerateInterpolator(1.4f)
            addUpdateListener { anim ->
                val w = anim.animatedValue as Int
                val lp = btn.layoutParams
                lp.width = w
                btn.layoutParams = lp
            }
            addListener(object : android.animation.AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: android.animation.Animator) {
                    // Reset to match_parent so it stays responsive
                    val lp = btn.layoutParams
                    lp.width = ViewGroup.LayoutParams.MATCH_PARENT
                    btn.layoutParams = lp
                    onComplete?.invoke()
                }
            })
        }
        widthAnim.start()
    }

    // ── Confirm Button: Reset Background Color ──────────────────────

    private fun resetConfirmButtonBackground() {
        val btn = binding.btnConfirmContainer
        val normalColor = ContextCompat.getColor(requireContext(), R.color.confirm_btn_bg)
        btn.background = btn.background.mutate()
        val bgDrawable = btn.background
        if (bgDrawable is android.graphics.drawable.RippleDrawable) {
            val shape = bgDrawable.findDrawableByLayerId(0) as? android.graphics.drawable.GradientDrawable
                ?: (bgDrawable.getDrawable(0) as? android.graphics.drawable.GradientDrawable)
            shape?.setColor(normalColor)
        } else if (bgDrawable is android.graphics.drawable.GradientDrawable) {
            bgDrawable.setColor(normalColor)
        }
    }

    // ── Confirm Button: Disabled / Insufficient Balance ─────────────

    private fun updateConfirmButtonState() {
        if (_binding == null) return
        if (isBookingInProgress) return

        val balance = viewModel.walletBalance.value ?: 0.0
        val price = viewModel.totalPrice.value ?: 0.0
        val hasVehicle = viewModel.selectedVehicle.value != null
        val insufficient = price > 0.0 && balance < price

        val btn = binding.btnConfirmContainer
        val label = binding.tvConfirmBooking

        if (insufficient) {
            btn.isClickable = false
            btn.alpha = 0.5f
            label.text = "Insufficient Balance"
            stopIdleGlow()
        } else if (!hasVehicle) {
            btn.isClickable = true
            btn.alpha = 0.75f
            label.text = "Confirm Booking"
            stopIdleGlow()
        } else {
            btn.isClickable = true
            btn.alpha = 1f
            label.text = "Confirm Booking"
            startIdleGlow()
        }
    }

    private fun shakeDisabledButton() {
        val btn = binding.btnConfirmContainer
        btn.performHapticFeedback(android.view.HapticFeedbackConstants.CLOCK_TICK)
        android.animation.ObjectAnimator.ofFloat(btn, "translationX",
            0f, -6f, 6f, -4f, 4f, 0f
        ).apply {
            duration = 300
            interpolator = android.view.animation.DecelerateInterpolator()
        }.start()
    }

    private fun setupObservers() {
        viewModel.startTime.observe(viewLifecycleOwner) { time ->
            updateStartTimeDisplay(time)
            calculatePricing()
        }

        viewModel.endTime.observe(viewLifecycleOwner) { time ->
            updateEndTimeDisplay(time)
            calculatePricing()
        }

        viewModel.selectedSpot.observe(viewLifecycleOwner) { spot ->
            updateSelectedSpotDisplay(spot)
        }

        viewModel.totalPrice.observe(viewLifecycleOwner) { price ->
            val newText = String.format(Locale.getDefault(), "%.2f", price)
            // First emission after the spot data lands carries the real price —
            // resolve the skeleton then so the user never sees the pre-rate 0.00.
            if (parkingSpotResolvedForSkeleton && !totalPriceSkeletonResolved) {
                totalPriceSkeletonResolved = true
                binding.tvTotalPrice.text = newText
                revealText(REVEAL_ORDER_TOTAL, binding.skelTotalPrice, binding.tvTotalPrice)
            } else {
                setTextWithCrossfade(binding.tvTotalPrice, newText)
            }
            updateConfirmButtonState()
        }

        viewModel.duration.observe(viewLifecycleOwner) { duration ->
            // First emission after the spot lands resolves the duration skeleton
            // — paired with totalPrice so the footer reveals as one unit.
            if (parkingSpotResolvedForSkeleton && !durationSkeletonResolved) {
                durationSkeletonResolved = true
                binding.tvDuration.text = duration
                revealText(REVEAL_ORDER_DURATION, binding.skelDuration, binding.tvDuration)
            } else {
                setTextWithCrossfade(binding.tvDuration, duration)
            }
        }

        viewModel.selectedVehicle.observe(viewLifecycleOwner) { vehicle ->
            val vehicles = viewModel.userVehicles.value ?: emptyList()
            binding.tvSelectedVehicle.text = when {
                vehicle != null -> vehicle.number
                vehicles.isEmpty() -> "Add new vehicle"
                else -> "Select vehicle"
            }
            if (!vehicleSkeletonResolved) {
                vehicleSkeletonResolved = true
                revealText(REVEAL_ORDER_VEHICLE, binding.skelSelectedVehicle, binding.tvSelectedVehicle)
            }
            updateConfirmButtonState()
        }

        viewModel.userVehicles.observe(viewLifecycleOwner) { vehicles ->
            if (viewModel.selectedVehicle.value == null) {
                binding.tvSelectedVehicle.text = if (vehicles.isNullOrEmpty()) {
                    "Add new vehicle"
                } else {
                    "Select vehicle"
                }
            }
            if (!vehicleSkeletonResolved) {
                vehicleSkeletonResolved = true
                revealText(REVEAL_ORDER_VEHICLE, binding.skelSelectedVehicle, binding.tvSelectedVehicle)
            }
        }

        viewModel.walletBalance.observe(viewLifecycleOwner) { balance ->
            binding.tvWalletBalance.text = String.format(Locale.getDefault(), "%.2f", balance)
            if (!walletSkeletonResolved) {
                walletSkeletonResolved = true
                revealText(REVEAL_ORDER_WALLET, binding.skelWalletBalance, binding.tvWalletBalance)
            }
            updateConfirmButtonState()
        }

        viewModel.isLoading.observe(viewLifecycleOwner) { isLoading ->
            if (isLoading) {
                morphToLoading()
            }
            // Reset is handled by success/error flows
        }

        viewModel.bookingCreated.observe(viewLifecycleOwner) { booking ->
            booking?.let {
                val startMillis = viewModel.startTime.value?.time ?: System.currentTimeMillis()
                val endMillis = viewModel.endTime.value?.time ?: (startMillis + 60 * 60 * 1000)
                val totalAmount = viewModel.totalPrice.value ?: 0.0
                val selectedSpotName = viewModel.selectedSpot.value
                    ?: parkingSpot?.name
                    ?: parkingSpot?.zoneName
                val parkingName = selectedLotName.ifEmpty { binding.tvParkingName.text.toString() }
                val parkingAddress = binding.tvParkingAddress.text.toString()
                val vehicleNumber = viewModel.selectedVehicle.value?.number

                pendingConfirmationIntent = Intent(requireContext(), BookingConfirmationActivity::class.java).apply {
                    putExtra("BOOKING_ID", it.id ?: "")
                    putExtra("TRANSACTION_ID", it.qrCode ?: "")
                    putExtra("PARKING_NAME", parkingName)
                    putExtra("PARKING_ADDRESS", parkingAddress)
                    putExtra("SELECTED_SPOT", selectedSpotName)
                    putExtra("PARKING_SPOT_ID", it.spotId)
                    putExtra("VEHICLE_NUMBER", vehicleNumber)
                    putExtra("START_TIME", startMillis)
                    putExtra("END_TIME", endMillis)
                    putExtra("TOTAL_AMOUNT", totalAmount)
                    putExtra("PAYMENT_METHOD", "Wallet")
                    putExtra("PAYMENT_STATUS", it.status ?: "Pending")
                    putExtra("BOOKING_TIMESTAMP", it.createdAt?.time ?: System.currentTimeMillis())
                }

                viewModel.clearBookingCreated()

                // Show success animation, then navigate. The reveal overlay is
                // covering the screen by the time we land here, so we kill the
                // default activity transition — the next screen's matching
                // overlay continues the colour without a frame of flash.
                showSuccessState {
                    pendingConfirmationIntent?.let { intent ->
                        startActivity(intent)
                        activity?.overridePendingTransition(0, 0)
                        pendingConfirmationIntent = null
                    }
                    dismissAllowingStateLoss()
                }
            }
        }

        viewModel.errorMessage.observe(viewLifecycleOwner) { error ->
            error?.let {
                showErrorState()
                showBookingNotice(it)
                viewModel.clearError()
            }
        }
    }

    private fun loadParkingSpot(spotId: String) {
        if (spotId == "quick_book") {
            parkingSpot = createDefaultParkingSpot()
            updateParkingSpotDisplay()
            return
        }

        if (spotId.isNotEmpty()) {
            viewModel.loadParkingSpotById(spotId) { spot ->
                if (!isAdded || _binding == null) return@loadParkingSpotById
                parkingSpot = mergeSelectedSpotMetadata(
                    spot ?: ParkingSpot(
                        id = spotId,
                        lotId = selectedLotId,
                        spotCode = spotId,
                        name = "Selected Spot",
                        zoneName = "Unknown Spot",
                        capacity = 0,
                        available = 0,
                        status = "unknown"
                    )
                )
                updateParkingSpotDisplay()
            }
            return
        }

        parkingSpot = mergeSelectedSpotMetadata(
            ParkingSpot(
                id = "unknown",
                lotId = selectedLotId,
                spotCode = null,
                name = null,
                zoneName = null,
                capacity = 0,
                available = 0,
                status = "unknown"
            )
        )
        updateParkingSpotDisplay()
    }

    private fun mergeSelectedSpotMetadata(spot: ParkingSpot): ParkingSpot {
        return spot.copy(
            name = spot.name ?: selectedSpotNameArg,
            zoneName = spot.zoneName ?: selectedSpotZoneNameArg,
            spotCode = spot.spotCode ?: selectedSpotCodeArg,
            slotName = spot.slotName ?: selectedSpotSlotNameArg
        )
    }

    private fun updateParkingSpotDisplay() {
        parkingSpot?.let { spot ->
            if (selectedLotId.isBlank() && spot.lotId.isNotBlank()) {
                selectedLotId = spot.lotId
            }

            // The loader emits a sentinel spot when the API returns nothing —
            // detect it so we never replace skeletons with garbage values.
            // The user sees a sheet still in loading state, which is the
            // honest signal that something is wrong.
            val isFakeSpot = spot.name == "Selected Spot" && spot.zoneName == "Unknown Spot"

            // Name: comes from the args passed in by HomeFragment, so it's
            // already a real value the user tapped. Reveal as long as it
            // isn't itself a garbage string.
            val nameText = selectedLotName.takeIf { it.isNotBlank() && !isGarbageText(it) }
            if (nameText != null && !nameSkeletonResolved) {
                nameSkeletonResolved = true
                binding.tvParkingName.text = nameText
                revealText(REVEAL_ORDER_NAME, binding.skelParkingName, binding.tvParkingName)
            }

            // Address falls back through several spot fields; we skip any
            // garbage string and land on the first clean one. If everything
            // is garbage (full API failure with no lot id), skeleton stays.
            val addressText = sequenceOf(
                spot.lotName?.takeIf { it != spot.name },
                spot.zoneName?.takeIf { it != spot.name },
                "Lot ID: $selectedLotId".takeIf { selectedLotId.isNotEmpty() }
            ).filterNotNull().firstOrNull { !isGarbageText(it) }
            if (addressText != null && !metaSkeletonResolved) {
                metaSkeletonResolved = true
                binding.tvParkingAddress.text = addressText
                binding.tvParkingMeta.text = addressText
                revealText(REVEAL_ORDER_META, binding.skelParkingMeta, binding.tvParkingMeta)
            }

            // Rate badge — only resolve when we trust the rate. A fake spot's
            // rate=0 isn't "free parking," it's "API didn't return anything,"
            // so the skeleton must stay.
            val hourlyRate = spot.bookingRate
            if (hourlyRate > 0.0) {
                binding.tvHourlyRate.text =
                    "${String.format(Locale.getDefault(), "%.2f", hourlyRate)}/hour"
                binding.tvRateBadge.text = "${String.format(Locale.getDefault(), "%.0f", hourlyRate)}/hr"
                revealBadge(REVEAL_ORDER_RATE, binding.skelRateBadge, binding.layoutRateBadge, makeVisible = true)
            } else if (!isFakeSpot) {
                binding.tvHourlyRate.text = ""
                revealBadge(REVEAL_ORDER_RATE, binding.skelRateBadge, binding.layoutRateBadge, makeVisible = false)
            }

            // Availability badge — same rule, fake spot keeps skeleton.
            if (spot.available > 0) {
                binding.tvAvailability.text = "${spot.available} Available"
                binding.viewAvailabilityDot.backgroundTintList =
                    ContextCompat.getColorStateList(requireContext(), R.color.parking_spot_available)
                binding.tvAvailability.setTextColor(
                    ContextCompat.getColor(requireContext(), R.color.parking_spot_available)
                )
                revealBadge(REVEAL_ORDER_AVAILABILITY, binding.skelAvailability, binding.layoutAvailability, makeVisible = true)
            } else if (spot.capacity > 0) {
                binding.tvAvailability.text = "Full"
                binding.viewAvailabilityDot.backgroundTintList =
                    ContextCompat.getColorStateList(requireContext(), R.color.parking_spot_unavailable)
                binding.tvAvailability.setTextColor(
                    ContextCompat.getColor(requireContext(), R.color.parking_spot_unavailable)
                )
                revealBadge(REVEAL_ORDER_AVAILABILITY, binding.skelAvailability, binding.layoutAvailability, makeVisible = true)
            } else if (!isFakeSpot) {
                revealBadge(REVEAL_ORDER_AVAILABILITY, binding.skelAvailability, binding.layoutAvailability, makeVisible = false)
            }

            val spotName = spot.name ?: spot.zoneName ?: spot.spotCode ?: "Any available spot"
            viewModel.setSelectedSpot(spotName)

            viewModel.setParkingSpot(spot)
            applySlotSpecificScheduleDefaults()
            // Only mark the spot as resolved when we got real data — gates the
            // totalPrice / duration skeleton reveal away from the fake-rate 0.00.
            if (!isFakeSpot) {
                parkingSpotResolvedForSkeleton = true
            }
            calculatePricing()
            viewModel.loadUserVehicles()
        }
    }

    private fun updateSelectedSpotDisplay(spot: String?) {
        // Spot selection UI removed
    }

    private fun applySlotSpecificScheduleDefaults() {
        val currentStart = viewModel.startTime.value?.let { calendarFromDate(it) } ?: Calendar.getInstance()
        val currentEnd = viewModel.endTime.value?.let { calendarFromDate(it) }
            ?: (currentStart.clone() as Calendar).apply { add(Calendar.MINUTE, DEFAULT_DURATION_MINUTES) }

        if (isMorningSlotSelected() || isEveningSlotSelected()) {
            val spot = parkingSpot
            val now = ParkingSpotSchedulePolicy.currentTime()
            val startTime = spot?.let { ParkingSpotSchedulePolicy.minimumAllowedStartTime(it, now) }
            val endTime = spot?.let { ParkingSpotSchedulePolicy.sessionEndTime(it, now) }

            if (startTime != null && endTime != null) {
                if (!startTime.before(endTime)) {
                    startTime.timeInMillis = endTime.timeInMillis - (60 * 1000)
                }

                viewModel.setStartTime(startTime.time)
                viewModel.setEndTime(endTime.time)
            }
            showBookingNotice(null)
            return
        }

        val normalized = normalizeBookingTimes(currentStart, currentEnd)
        viewModel.setStartTime(normalized.start.time)
        viewModel.setEndTime(normalized.end.time)
        showBookingNotice(normalized.message ?: initialBookingNotice())
    }

    private fun showProgress(
        progressBar: android.widget.ProgressBar,
        content: View,
        emptyState: android.widget.TextView,
        show: Boolean
    ) {
        if (show) {
            progressBar.visibility = View.VISIBLE
            content.visibility = View.GONE
            emptyState.visibility = View.GONE
        } else {
            progressBar.visibility = View.GONE
        }
    }

    private fun showVehicleSelectionBottomSheet() {
        val vehicles = viewModel.userVehicles.value ?: emptyList()
        val selectedVehicleId = viewModel.selectedVehicle.value?.id

        val bottomSheet = SelectVehicleBottomSheet(
            vehicles = vehicles,
            selectedVehicleId = selectedVehicleId,
            onVehicleSelected = { vehicle ->
                viewModel.setSelectedVehicle(vehicle)
            },
            onAddVehicle = { vehicleNumber, callback ->
                viewModel.addVehicleToProfile(vehicleNumber) { success ->
                    if (success) {
                        viewModel.loadUserVehicles()
                    }
                    callback(success)
                }
            }
        )
        bottomSheet.show(parentFragmentManager, SelectVehicleBottomSheet.TAG)
    }

    private fun updateStartTimeDisplay(time: Date) {
        val (dayLabel, timeLabel) = formatSplitDateTime(time)
        binding.tvStartDate.text = dayLabel
        binding.tvStartTime.text = timeLabel
    }

    private fun updateEndTimeDisplay(time: Date) {
        val (dayLabel, timeLabel) = formatSplitDateTime(time)
        binding.tvEndDate.text = dayLabel
        binding.tvEndTime.text = timeLabel
    }

    private fun formatSplitDateTime(date: Date): Pair<String, String> {
        val cal = Calendar.getInstance().apply { this.time = date }
        val today = Calendar.getInstance()
        val tomorrow = (today.clone() as Calendar).apply { add(Calendar.DAY_OF_MONTH, 1) }
        val timeFormat = SimpleDateFormat("h:mm a", Locale.getDefault())
        val dayLabel = when {
            isSameDate(cal, today) -> "Today"
            isSameDate(cal, tomorrow) -> "Tomorrow"
            else -> SimpleDateFormat("MMM d, yyyy", Locale.getDefault()).format(date)
        }
        return dayLabel to timeFormat.format(date)
    }

    private fun calculatePricing() {
        viewModel.calculatePricing()
    }

    private data class NormalizedTimes(
        val start: Calendar,
        val end: Calendar,
        val message: String?,
        val adjusted: Boolean
    )

    private fun validateCurrentTimesForBooking(): Boolean {
        val start = viewModel.startTime.value
        val end = viewModel.endTime.value

        if (start == null || end == null) {
            showBookingNotice("Please select start and end times.")
            return false
        }

        if (isMorningSlotSelected() || isEveningSlotSelected()) return true

        val normalized = normalizeBookingTimes(calendarFromDate(start), calendarFromDate(end))
        return if (normalized.adjusted) {
            viewModel.setStartTime(normalized.start.time)
            viewModel.setEndTime(normalized.end.time)
            showBookingNotice(normalized.message)
            false
        } else {
            showBookingNotice(null)
            true
        }
    }

    // Notice band animates as a single piece: height + alpha grow together on
    // appear (alpha leads slightly so text reads solid before height settles),
    // collapse together on dismiss (alpha drops first so the band looks "empty"
    // as it folds away). Updates to the text while visible just swap in place.
    private fun showBookingNotice(message: String?) {
        val view = binding.tvBookingNotice

        if (message.isNullOrBlank()) {
            if (view.visibility != View.VISIBLE) return
            collapseNoticeBand(view)
            return
        }

        if (view.visibility == View.VISIBLE) {
            // Already on screen — just update the text. Animating mid-flight
            // would feel busy when the user is rapidly adjusting times.
            view.text = message
            return
        }

        expandNoticeBand(view, message)
    }

    private fun expandNoticeBand(view: android.widget.TextView, message: String) {
        noticeAnimator?.cancel()
        view.text = message
        view.visibility = View.VISIBLE
        view.alpha = 0f

        val parentWidth = (view.parent as? View)?.width
            ?: resources.displayMetrics.widthPixels
        val widthSpec = View.MeasureSpec.makeMeasureSpec(parentWidth, View.MeasureSpec.AT_MOST)
        val heightSpec = View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
        view.measure(widthSpec, heightSpec)
        val targetHeight = view.measuredHeight

        view.layoutParams = view.layoutParams.apply { height = 0 }
        noticeAnimator = android.animation.ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 220
            interpolator = android.view.animation.PathInterpolator(0.2f, 0f, 0f, 1f)
            addUpdateListener { anim ->
                if (_binding == null) return@addUpdateListener
                val t = anim.animatedValue as Float
                view.layoutParams = view.layoutParams.apply {
                    height = (targetHeight * t).toInt()
                }
                // Alpha leads height — fully visible by 70% so the text feels
                // settled even as the box finishes growing.
                view.alpha = (t / 0.7f).coerceAtMost(1f)
            }
            addListener(object : android.animation.AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: android.animation.Animator) {
                    if (_binding == null) return
                    view.layoutParams = view.layoutParams.apply {
                        height = ViewGroup.LayoutParams.WRAP_CONTENT
                    }
                    view.alpha = 1f
                }
            })
            start()
        }
    }

    private fun collapseNoticeBand(view: android.widget.TextView) {
        noticeAnimator?.cancel()
        val startHeight = if (view.height > 0) view.height else view.measuredHeight
        if (startHeight <= 0) {
            view.visibility = View.GONE
            return
        }
        noticeAnimator = android.animation.ValueAnimator.ofFloat(1f, 0f).apply {
            duration = 160
            interpolator = android.view.animation.PathInterpolator(0.4f, 0f, 1f, 1f)
            addUpdateListener { anim ->
                if (_binding == null) return@addUpdateListener
                val t = anim.animatedValue as Float
                view.layoutParams = view.layoutParams.apply {
                    height = (startHeight * t).toInt()
                }
                // Alpha drops first (gone by t=0.7) so the last sliver of
                // height collapses on an empty band — cleaner than text fading
                // and shrinking in lockstep.
                view.alpha = ((t - 0.7f) / 0.3f).coerceAtLeast(0f)
            }
            addListener(object : android.animation.AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: android.animation.Animator) {
                    if (_binding == null) return
                    view.visibility = View.GONE
                    view.layoutParams = view.layoutParams.apply {
                        height = ViewGroup.LayoutParams.WRAP_CONTENT
                    }
                    view.alpha = 1f
                }
            })
            start()
        }
    }

    // ── Skeleton helpers ────────────────────────────────────────────
    // initializeSkeletons hides the real values behind alpha=0 so the skeleton
    // pills (which stack with the values inside a FrameLayout) are the only
    // visible layer. A single ValueAnimator drives all skeletons in unison —
    // synchronized breath reads as calmer than per-element shimmer staggers.
    private fun initializeSkeletons() {
        val b = _binding ?: return
        // Hide real values so the skeleton stack shows alone.
        b.tvParkingName.alpha = 0f
        b.tvParkingMeta.alpha = 0f
        b.tvSelectedVehicle.alpha = 0f
        b.tvWalletBalance.alpha = 0f
        b.tvTotalPrice.alpha = 0f
        b.tvDuration.alpha = 0f
        // Rate/availability badges are already gone in XML — they'll fade in
        // when revealBadge promotes them.

        startSkeletonBreath()
    }

    // Fields the caller already handed us don't need to wait behind the network
    // skeleton. The parking name is always passed in (and updateParkingSpotDisplay
    // uses that same arg for the title anyway), so we settle it solid up front —
    // it then rides up with the sheet instead of popping in a beat later.
    private fun prefillKnownFieldsFromArgs() {
        val b = _binding ?: return
        val nameText = selectedLotName.takeIf { it.isNotBlank() && !isGarbageText(it) }
        if (nameText != null && !nameSkeletonResolved) {
            nameSkeletonResolved = true
            b.tvParkingName.text = nameText
            settleResolvedInstant(b.skelParkingName, b.tvParkingName)
        }
    }

    // Resolve a skeleton with no crossfade — used when the value is known before
    // the first frame, so there's nothing to animate away. The pill simply never
    // shows and the value is present from the start.
    private fun settleResolvedInstant(skel: View, real: View) {
        skel.animate().cancel()
        skel.visibility = View.GONE
        real.animate().cancel()
        real.translationY = 0f
        real.alpha = 1f
    }

    private fun visibleSkeletons(): List<View> {
        val b = _binding ?: return emptyList()
        return listOf(
            b.skelParkingName,
            b.skelParkingMeta,
            b.skelRateBadge,
            b.skelAvailability,
            b.skelSelectedVehicle,
            b.skelWalletBalance,
            b.skelTotalPrice,
            b.skelDuration
        ).filter { it.visibility == View.VISIBLE && it !in resolvingSkeletons }
    }

    // Recognises the fallback strings the spot loader emits when it has no real
    // data — these should never replace the skeleton, because showing them
    // would be worse than the user seeing "still loading."
    private fun isGarbageText(text: String?): Boolean {
        if (text.isNullOrBlank()) return true
        val lower = text.lowercase(Locale.getDefault())
        return lower.contains("unknown")
            || lower.contains("unavailable")
            || lower == "selected spot"
    }

    private fun startSkeletonBreath() {
        skeletonBreathAnimator?.cancel()
        // Reduced motion: no pulse. Show the pills at a calm static opacity so
        // the loading state still reads, without any animation.
        if (shouldReduceMotion()) {
            visibleSkeletons().forEach { it.alpha = 1f }
            return
        }
        // 700ms one-way × reverse = ~1.4s full breath cycle. Apple-tier pacing —
        // slow enough to read as ambient, not anxious.
        skeletonBreathAnimator = android.animation.ValueAnimator.ofFloat(0.55f, 1.0f).apply {
            duration = 700
            repeatCount = android.animation.ValueAnimator.INFINITE
            repeatMode = android.animation.ValueAnimator.REVERSE
            interpolator = android.view.animation.AccelerateDecelerateInterpolator()
            addUpdateListener { anim ->
                if (_binding == null) return@addUpdateListener
                val a = anim.animatedValue as Float
                visibleSkeletons().forEach { it.alpha = a }
            }
            start()
        }
    }

    private fun stopSkeletonBreath() {
        skeletonBreathAnimator?.cancel()
        skeletonBreathAnimator = null
    }

    // Once the last pill has crossfaded out there's nothing left to pulse, so
    // the infinite breath animator should retire rather than tick the main
    // thread forever for an empty list. Called from each reveal's end action.
    private fun stopBreathIfAllResolved() {
        if (_binding == null) return
        if (visibleSkeletons().isEmpty()) {
            stopSkeletonBreath()
        }
    }

    // Queue a reveal for this frame's cascade. The first reveal of a frame posts
    // a flush; everything queued before it runs is sorted top-to-bottom and
    // fired with an incremental stagger. The skeleton keeps breathing until its
    // turn, so there's no frozen gap before it fades.
    private fun scheduleReveal(order: Int, skel: View, start: () -> Unit) {
        pendingReveals.add(PendingReveal(order, start))
        scheduledReveals.add(skel)
        if (!revealFlushPosted) {
            revealFlushPosted = true
            binding.root.post { flushReveals() }
        }
    }

    private fun flushReveals() {
        revealFlushPosted = false
        val b = _binding ?: run { pendingReveals.clear(); scheduledReveals.clear(); return }
        if (pendingReveals.isEmpty()) return
        val batch = pendingReveals.sortedBy { it.order }
        pendingReveals.clear()
        batch.forEachIndexed { index, reveal ->
            val delay = index * revealStaggerMs
            if (delay == 0L) {
                reveal.start()
            } else {
                b.root.postDelayed({ if (_binding != null) reveal.start() }, delay)
            }
        }
    }

    // Crossfade a skeleton pill out and settle the real value in. The skeleton
    // recedes (fade + slight sink) while the value rides up into place a beat
    // later on emphasized easing — so it reads as content landing, not a blink.
    private fun revealText(order: Int, skel: View, real: View) {
        if (_binding == null) return
        if (skel.visibility != View.VISIBLE) return
        if (skel in resolvingSkeletons || skel in scheduledReveals) return
        // Reduced motion: place the value with no crossfade, sink, or cascade.
        if (shouldReduceMotion()) {
            settleResolvedInstant(skel, real)
            stopBreathIfAllResolved()
            return
        }
        scheduleReveal(order, skel) { animateRevealText(skel, real) }
    }

    private fun animateRevealText(skel: View, real: View) {
        if (_binding == null) return
        scheduledReveals.remove(skel)
        resolvingSkeletons.add(skel)
        skel.animate().cancel()
        skel.animate()
            .alpha(0f)
            // Skeleton sinks a hair as it leaves — the inverse of the value's
            // rise, so the handoff feels like one continuous vertical motion.
            .translationYBy(arrivalRisePx * 0.4f)
            .setDuration(160)
            .setInterpolator(arrivalInterpolator)
            .withEndAction {
                if (_binding == null) return@withEndAction
                skel.visibility = View.GONE
                skel.translationY = 0f
                resolvingSkeletons.remove(skel)
                stopBreathIfAllResolved()
            }
            .start()
        real.alpha = 0f
        real.translationY = arrivalRisePx
        real.animate().cancel()
        real.animate()
            .alpha(1f)
            .translationY(0f)
            .setStartDelay(70)
            .setDuration(280)
            .setInterpolator(arrivalInterpolator)
            .start()
    }

    // For badges that may or may not appear depending on data (rate=0 hides
    // the rate badge entirely). Either way, the skeleton goes away — the badge
    // only fades in when the data justifies it.
    private fun revealBadge(order: Int, skel: View, badge: View, makeVisible: Boolean) {
        if (_binding == null) return
        if (skel in resolvingSkeletons || skel in scheduledReveals) return
        if (shouldReduceMotion()) {
            skel.animate().cancel()
            skel.visibility = View.GONE
            if (makeVisible) {
                badge.animate().cancel()
                badge.translationY = 0f
                badge.alpha = 1f
                badge.visibility = View.VISIBLE
            } else {
                badge.visibility = View.GONE
            }
            stopBreathIfAllResolved()
            return
        }
        scheduleReveal(order, skel) { animateRevealBadge(skel, badge, makeVisible) }
    }

    private fun animateRevealBadge(skel: View, badge: View, makeVisible: Boolean) {
        if (_binding == null) return
        scheduledReveals.remove(skel)
        if (skel.visibility == View.VISIBLE && skel !in resolvingSkeletons) {
            resolvingSkeletons.add(skel)
            skel.animate().cancel()
            skel.animate()
                .alpha(0f)
                .translationYBy(arrivalRisePx * 0.4f)
                .setDuration(160)
                .setInterpolator(arrivalInterpolator)
                .withEndAction {
                    if (_binding == null) return@withEndAction
                    skel.visibility = View.GONE
                    skel.translationY = 0f
                    resolvingSkeletons.remove(skel)
                    stopBreathIfAllResolved()
                }
                .start()
        }
        if (makeVisible) {
            if (badge.visibility != View.VISIBLE) {
                badge.alpha = 0f
                badge.translationY = arrivalRisePx
                badge.visibility = View.VISIBLE
                badge.animate().cancel()
                badge.animate()
                    .alpha(1f)
                    .translationY(0f)
                    .setStartDelay(70)
                    .setDuration(280)
                    .setInterpolator(arrivalInterpolator)
                    .start()
            }
        } else {
            badge.visibility = View.GONE
        }
    }

    // Soft crossfade for numeric labels that change in response to user input
    // (price, duration). Skipped when the new text matches what's already shown
    // or when the field is still empty (first paint).
    private fun setTextWithCrossfade(view: android.widget.TextView, newText: String) {
        val current = view.text?.toString().orEmpty()
        if (current == newText) return
        if (current.isBlank()) {
            view.text = newText
            return
        }
        view.animate().cancel()
        view.animate()
            .alpha(0f)
            .setDuration(90)
            .setInterpolator(android.view.animation.AccelerateInterpolator())
            .withEndAction {
                if (_binding == null) return@withEndAction
                view.text = newText
                view.alpha = 0f
                view.animate()
                    .alpha(1f)
                    .setDuration(130)
                    .setInterpolator(android.view.animation.DecelerateInterpolator())
                    .start()
            }
            .start()
    }

    private fun normalizeBookingTimes(start: Calendar, end: Calendar): NormalizedTimes {
        val now = Calendar.getInstance()
        val bookingDay = getBookingDay(now)
        val cutoff = getCutoffCalendar(bookingDay)
        val opening = getEarliestStartCalendar(bookingDay, now)
        val isBeforeCutoff = now.before(getCutoffCalendar(now))
        val openingLabel = SimpleDateFormat("h:mm a", Locale.getDefault()).format(opening.time)

        val adjustedStart = start.clone() as Calendar
        val adjustedEnd = end.clone() as Calendar
        val messages = mutableListOf<String>()
        var adjusted = false

        if (!isSameDate(adjustedStart, bookingDay)) {
            alignDate(adjustedStart, bookingDay)
            adjusted = true
            messages.add(if (isBeforeCutoff) "Before 5 PM, bookings must be for today." else "After 5 PM, bookings must be for tomorrow.")
        }

        if (!isSameDate(adjustedEnd, bookingDay)) {
            alignDate(adjustedEnd, bookingDay)
            adjusted = true
            messages.add("Bookings must start and end on the same day.")
        }

        if (adjustedStart.before(opening)) {
            adjustedStart.timeInMillis = opening.timeInMillis
            adjusted = true
            messages.add("Start time must be at or after $openingLabel. Updated to $openingLabel (earliest available).")
        }

        if (adjustedEnd.after(cutoff)) {
            adjustedEnd.timeInMillis = cutoff.timeInMillis
            adjusted = true
            messages.add("Bookings must end by 5 PM. End time adjusted to 5 PM.")
        }

        if (!adjustedEnd.after(adjustedStart)) {
            val fallbackEnd = adjustedStart.clone() as Calendar
            fallbackEnd.add(Calendar.MINUTE, DEFAULT_DURATION_MINUTES)
            if (fallbackEnd.after(cutoff)) fallbackEnd.timeInMillis = cutoff.timeInMillis
            adjustedEnd.timeInMillis = fallbackEnd.timeInMillis
            adjusted = true
            messages.add("End time updated to be after start time.")

            if (!adjustedEnd.after(adjustedStart)) {
                val fallbackStart = adjustedEnd.clone() as Calendar
                fallbackStart.add(Calendar.MINUTE, -1)
                adjustedStart.timeInMillis = fallbackStart.timeInMillis
                adjusted = true
                messages.add("Start time adjusted to fit within opening hours.")
            }
        }

        val message = messages.distinct().joinToString(" ").ifBlank { null }
        return NormalizedTimes(adjustedStart, adjustedEnd, message, adjusted)
    }

    private fun getBookingDay(now: Calendar): Calendar {
        val today = getStartOfDay(now)
        return if (now.before(getCutoffCalendar(now))) today else (today.clone() as Calendar).apply {
            add(Calendar.DAY_OF_MONTH, 1)
        }
    }

    private fun initialBookingNotice(): String? {
        if (!SHOW_AUTOMATIC_SCHEDULE_NOTICE) return null

        val now = Calendar.getInstance()
        if (isEveningSlotSelected()) {
            return when {
                now.get(Calendar.HOUR_OF_DAY) >= BOOKING_CUTOFF_HOUR -> "After 5 PM, bookings must be for tomorrow."
                else -> "Afternoon slot booking opens at 8:00 AM. Parking runs from 12:30 PM to 5:30 PM."
            }
        }

        val isQuickSlotSelected = parkingSpot?.let(ParkingSpotSchedulePolicy::isQuickBookSpot) == true

        return when {
            now.get(Calendar.HOUR_OF_DAY) >= BOOKING_CUTOFF_HOUR -> "After 5 PM, bookings must be for tomorrow."
            isQuickSlotSelected && now.get(Calendar.HOUR_OF_DAY) < BOOKING_OPEN_HOUR ->
                "Quick slot booking is available from 8:00 AM to 11:30 AM on the same day of parking."
            now.get(Calendar.HOUR_OF_DAY) < BOOKING_OPEN_HOUR -> "Morning slot parking runs from 7:30 AM to 12:30 PM."
            else -> null
        }
    }

    private fun getCutoffCalendar(reference: Calendar): Calendar {
        return (reference.clone() as Calendar).apply {
            set(Calendar.HOUR_OF_DAY, BOOKING_CUTOFF_HOUR)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
    }

    private fun getDefaultBookingWindow(reference: Calendar = Calendar.getInstance()): Pair<Calendar, Calendar> {
        val bookingDay = getBookingDay(reference)
        val start = getEarliestStartCalendar(bookingDay, reference)
        val end = getCutoffCalendar(bookingDay)
        return start to end
    }

    private fun getEarliestStartCalendar(bookingDay: Calendar, reference: Calendar): Calendar {
        val opening = getOpeningCalendar(bookingDay)
        val currentMinute = (reference.clone() as Calendar).apply {
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }

        return if (isSameDate(currentMinute, bookingDay) && currentMinute.after(opening)) {
            currentMinute
        } else {
            opening
        }
    }

    private fun getOpeningCalendar(reference: Calendar): Calendar {
        val opening = parkingSpot?.let { spot ->
            ParkingSpotSchedulePolicy.minimumAllowedStartTime(spot, reference)
        }

        return (opening ?: (reference.clone() as Calendar).apply {
            set(Calendar.HOUR_OF_DAY, BOOKING_OPEN_HOUR)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        })
    }

    private fun isEveningSlotSelected(): Boolean {
        val spot = parkingSpot ?: return false
        return ParkingSpotSchedulePolicy.classifySlotSession(spot) == ParkingSpotSchedulePolicy.SlotSession.EVENING
    }

    private fun isMorningSlotSelected(): Boolean {
        val spot = parkingSpot ?: return false
        return ParkingSpotSchedulePolicy.classifySlotSession(spot) == ParkingSpotSchedulePolicy.SlotSession.MORNING ||
               ParkingSpotSchedulePolicy.isQuickBookSpot(spot)
    }

    private fun getStartOfDay(reference: Calendar): Calendar {
        return (reference.clone() as Calendar).apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
    }

    private fun getEndOfDay(reference: Calendar): Calendar {
        return (reference.clone() as Calendar).apply {
            set(Calendar.HOUR_OF_DAY, 23)
            set(Calendar.MINUTE, 59)
            set(Calendar.SECOND, 59)
            set(Calendar.MILLISECOND, 999)
        }
    }

    private fun isSameDate(first: Calendar, second: Calendar): Boolean {
        return first.get(Calendar.YEAR) == second.get(Calendar.YEAR) &&
            first.get(Calendar.DAY_OF_YEAR) == second.get(Calendar.DAY_OF_YEAR)
    }

    private fun alignDate(target: Calendar, reference: Calendar) {
        target.set(Calendar.YEAR, reference.get(Calendar.YEAR))
        target.set(Calendar.MONTH, reference.get(Calendar.MONTH))
        target.set(Calendar.DAY_OF_MONTH, reference.get(Calendar.DAY_OF_MONTH))
    }

    private fun calendarFromDate(date: Date): Calendar {
        return Calendar.getInstance().apply { time = date }
    }

    private fun createBooking() {
        // Check insufficient balance — shake the button
        val balance = viewModel.walletBalance.value ?: 0.0
        val price = viewModel.totalPrice.value ?: 0.0
        if (price > 0.0 && balance < price) {
            shakeDisabledButton()
            showBookingNotice("Insufficient wallet balance.")
            return
        }

        val selectedVehicle = viewModel.selectedVehicle.value

        if (selectedVehicle == null) {
            shakeDisabledButton()
            showBookingNotice("Please select a vehicle.")
            return
        }

        if (!validateCurrentTimesForBooking()) {
            return
        }

        viewModel.setVehicleNumber(selectedVehicle.number)
        viewModel.createBackendBooking()
    }

    private fun showTopUpDialog() {
        val bottomSheetDialog = BottomSheetDialog(requireContext(), R.style.BottomSheetDialogTheme)
        val bottomSheetBinding = BottomSheetTopUpBinding.inflate(layoutInflater)
        bottomSheetDialog.setContentView(bottomSheetBinding.root)

        bottomSheetDialog.window?.apply {
            setWindowAnimations(R.style.BottomSheetSpringAnimation)
            setDimAmount(0.45f)

            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                attributes.blurBehindRadius = 50
                attributes = attributes
            }
        }

        bottomSheetDialog.behavior.apply {
            skipCollapsed = true
            state = BottomSheetBehavior.STATE_EXPANDED
            isFitToContents = true
        }

        bottomSheetDialog.setOnShowListener {
            val bottomSheet = bottomSheetDialog.findViewById<FrameLayout>(
                com.google.android.material.R.id.design_bottom_sheet
            )
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

        val currentBalance = viewModel.walletBalance.value ?: 0.0
        bottomSheetBinding.tvCurrentBalance.text =
            String.format(Locale.getDefault(), "%.2f", currentBalance)

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

        bottomSheetBinding.etAmount.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                updateAddButtonState(bottomSheetBinding)
            }
        })

        bottomSheetBinding.btnClose.setOnClickListener {
            animateAndDismiss(bottomSheetDialog)
        }

        bottomSheetBinding.btnAddMoneyConfirm.setOnClickListener {
            val amountText = bottomSheetBinding.etAmount.text.toString()
            val amount = amountText.toDoubleOrNull()
            if (amount != null && amount > 0) {
                showToast("Redirecting to Razorpay checkout...")
                initiateWalletTopUp(amount)
                animateAndDismiss(bottomSheetDialog)
            } else {
                showToast("Please enter a valid amount")
            }
        }

        updateAddButtonState(bottomSheetBinding)
        bottomSheetDialog.show()
    }

    private fun updateAddButtonState(binding: BottomSheetTopUpBinding) {
        val amountText = binding.etAmount.text.toString()
        val amount = amountText.toDoubleOrNull()
        val isValidAmount = amount != null && amount > 0

        binding.btnAddMoneyConfirm.isEnabled = isValidAmount
        binding.btnAddMoneyConfirm.text = if (isValidAmount) {
            "Add ${amount?.toInt()} G"
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

    private fun initiateWalletTopUp(amount: Double) {
        val userId = AuthSession.getUserId(requireContext())

        if (userId == null) {
            showToast("Please log in to add money")
            return
        }

        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val response = ApiClient.apiService.initiatePayment(
                    com.gridee.parking.data.model.PaymentInitiateRequest(
                        userId = userId,
                        amount = amount
                    )
                )

                if (response.isSuccessful) {
                    val result = response.body()
                    if (result != null) {
                        val intent = Intent(requireContext(), WalletTopUpActivity::class.java).apply {
                            putExtra("USER_ID", userId)
                            putExtra("AMOUNT", amount)
                            putExtra("ORDER_ID", result.orderId)
                            putExtra("KEY_ID", result.keyId)
                        }
                        startActivity(intent)
                    } else {
                        showToast("Failed to initiate payment")
                    }
                } else {
                    showToast("Error: ${response.message()}")
                }
            } catch (e: Exception) {
                showToast("Error: ${e.message}")
                e.printStackTrace()
            }
        }
    }

    private fun createDefaultParkingSpot(): ParkingSpot {
        return ParkingSpot(
            id = "quick_book",
            lotId = selectedLotId,
            name = "Quick Book",
            zoneName = null,
            capacity = 0,
            available = 0,
            status = "unknown",
            slotName = "morning"
        )
    }

    private fun showToast(message: String) {
        val context = context ?: return
        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
    }

    override fun onDestroyView() {
        stopIdleGlow()
        stopSkeletonBreath()
        resolvingSkeletons.clear()
        pendingReveals.clear()
        scheduledReveals.clear()
        revealFlushPosted = false
        backdropAnimator?.cancel()
        backdropAnimator = null
        sheetSpring?.cancel()
        sheetSpring = null
        noticeAnimator?.cancel()
        noticeAnimator = null
        // Always release the host activity's scale so we never leave it
        // visually transformed if this fragment is torn down mid-animation.
        resetBackdrop()
        hostContentView = null
        releaseTouchShield()
        // Restore drag in case we died mid-entrance with it disabled.
        bottomSheetBehavior?.isDraggable = true
        pendingConfirmationIntent = null
        bottomSheetBehavior?.removeBottomSheetCallback(bottomSheetCallback)
        bottomSheetBehavior = null
        super.onDestroyView()
        _binding = null
    }

    companion object {
        const val TAG = "ParkingSpotBottomSheet"

        // Reveal cascade order — top to bottom down the sheet, so a batch of
        // fields that resolve in the same frame settles in reading order.
        private const val REVEAL_ORDER_NAME = 0
        private const val REVEAL_ORDER_META = 1
        private const val REVEAL_ORDER_RATE = 2
        private const val REVEAL_ORDER_AVAILABILITY = 3
        private const val REVEAL_ORDER_VEHICLE = 4
        private const val REVEAL_ORDER_WALLET = 5
        private const val REVEAL_ORDER_TOTAL = 6
        private const val REVEAL_ORDER_DURATION = 7

        private const val BOOKING_OPEN_HOUR = 8
        private const val BOOKING_CUTOFF_HOUR = 17
        private const val DEFAULT_DURATION_MINUTES = 120
        private const val SHOW_AUTOMATIC_SCHEDULE_NOTICE = false

        private const val ARG_PARKING_SPOT_ID = "PARKING_SPOT_ID"
        private const val ARG_PARKING_LOT_ID = "PARKING_LOT_ID"
        private const val ARG_PARKING_LOT_NAME = "PARKING_LOT_NAME"
        private const val ARG_PARKING_SPOT_NAME = "PARKING_SPOT_NAME"
        private const val ARG_PARKING_SPOT_ZONE_NAME = "PARKING_SPOT_ZONE_NAME"
        private const val ARG_PARKING_SPOT_CODE = "PARKING_SPOT_CODE"
        private const val ARG_PARKING_SPOT_SLOT_NAME = "PARKING_SPOT_SLOT_NAME"

        fun newInstance(
            parkingSpotId: String,
            parkingLotId: String,
            parkingLotName: String,
            parkingSpotName: String?,
            parkingSpotZoneName: String?,
            parkingSpotCode: String?,
            parkingSpotSlotName: String?
        ): ParkingSpotBottomSheet {
            return ParkingSpotBottomSheet().apply {
                arguments = Bundle().apply {
                    putString(ARG_PARKING_SPOT_ID, parkingSpotId)
                    putString(ARG_PARKING_LOT_ID, parkingLotId)
                    putString(ARG_PARKING_LOT_NAME, parkingLotName)
                    putString(ARG_PARKING_SPOT_NAME, parkingSpotName)
                    putString(ARG_PARKING_SPOT_ZONE_NAME, parkingSpotZoneName)
                    putString(ARG_PARKING_SPOT_CODE, parkingSpotCode)
                    putString(ARG_PARKING_SPOT_SLOT_NAME, parkingSpotSlotName)
                }
            }
        }
    }
}
