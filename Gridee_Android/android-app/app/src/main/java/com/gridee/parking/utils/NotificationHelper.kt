package com.gridee.parking.utils

import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.coordinatorlayout.widget.CoordinatorLayout
import androidx.core.view.doOnPreDraw
import androidx.dynamicanimation.animation.DynamicAnimation
import androidx.dynamicanimation.animation.SpringAnimation
import androidx.dynamicanimation.animation.SpringForce
import com.gridee.parking.R
import com.gridee.parking.databinding.CustomNotificationBinding

object NotificationHelper {

    private var currentNotification: View? = null
    private var currentHostParent: ViewGroup? = null
    private var dismissRunnable: Runnable? = null
    private var snapRunnable: Runnable? = null
    private var isDismissing = false
    private val activeSprings = mutableListOf<SpringAnimation>()

    fun showSuccess(
        parent: ViewGroup,
        title: String = "Success",
        message: String,
        duration: Long = 4500L,
        onClick: (() -> Unit)? = null
    ) {
        show(parent, title, message, NotificationType.SUCCESS, duration, onClick)
    }

    fun showError(
        parent: ViewGroup,
        title: String = "Error",
        message: String,
        duration: Long = 4500L,
        onClick: (() -> Unit)? = null
    ) {
        show(parent, title, message, NotificationType.ERROR, duration, onClick)
    }

    fun showInfo(
        parent: ViewGroup,
        title: String = "Info",
        message: String,
        duration: Long = 4500L,
        onClick: (() -> Unit)? = null
    ) {
        show(parent, title, message, NotificationType.INFO, duration, onClick)
    }

    fun showWalletTransaction(
        parent: ViewGroup,
        title: String,
        amountText: String,
        isCredit: Boolean,
        duration: Long = 4500L,
        onClick: (() -> Unit)? = null,
        actionButtonText: String? = null
    ) {
        show(parent, title, amountText, if (isCredit) NotificationType.TRANSACTION_CREDIT else NotificationType.TRANSACTION_DEBIT, duration, onClick, actionButtonText)
    }

    fun showWarning(
        parent: ViewGroup,
        title: String = "Warning",
        message: String,
        duration: Long = 5000L,
        onClick: (() -> Unit)? = null,
        actionButtonText: String? = null
    ) {
        show(parent, title, message, NotificationType.WARNING, duration, onClick, actionButtonText)
    }

    fun showInfoNoIcon(
        parent: ViewGroup,
        title: String,
        message: String,
        duration: Long = 4500L,
        onClick: (() -> Unit)? = null
    ) {
        show(parent, title, message, NotificationType.INFO_NO_ICON, duration, onClick)
    }

    private fun show(
        parent: ViewGroup,
        title: String,
        message: String,
        type: NotificationType,
        duration: Long,
        onClick: (() -> Unit)? = null,
        actionButtonText: String? = null
    ) {
        // Resolve the actual host: if the caller passed a view inside an activity with a
        // floating navbar, attach to the navbar's parent so the card draws above it.
        val host = resolveHost(parent)
        val navbar = host.findViewById<View?>(R.id.bottom_navigation)

        // Remove any existing notification immediately (no animation overlap).
        currentNotification?.let { existing ->
            dismissRunnable?.let { existing.removeCallbacks(it) }
            snapRunnable?.let { existing.removeCallbacks(it); snapRunnable = null }
            cancelActiveSprings()
            (currentHostParent ?: host).removeView(existing)
            currentNotification = null
            currentHostParent = null
            isDismissing = false
        }

        val binding = CustomNotificationBinding.inflate(
            LayoutInflater.from(host.context),
            host,
            false
        )

        binding.tvNotificationTitle.text = title
        binding.tvNotificationMessage.text = message
        binding.tvNotificationMessage.visibility = if (message.isBlank()) View.GONE else View.VISIBLE

        // Reset reusable state.
        binding.ivNotificationIcon.background = null
        binding.ivNotificationIcon.colorFilter = null
        binding.ivNotificationIcon.visibility = View.VISIBLE
        binding.ivGrideeCoin.visibility = View.GONE
        binding.tvTransactionSign.visibility = View.GONE
        binding.tvNotificationTitle.setTextColor(
            androidx.core.content.ContextCompat.getColor(host.context, R.color.text_primary)
        )
        binding.tvNotificationMessage.setTextColor(
            androidx.core.content.ContextCompat.getColor(host.context, R.color.text_secondary)
        )

        when (type) {
            NotificationType.SUCCESS -> {
                binding.ivNotificationIcon.setImageResource(R.drawable.ic_notification_check_circle)
                binding.ivNotificationIcon.setColorFilter(
                    androidx.core.content.ContextCompat.getColor(host.context, R.color.success_green)
                )
            }
            NotificationType.ERROR -> {
                binding.ivNotificationIcon.setImageResource(R.drawable.ic_close)
                val errorBackground = host.context.getDrawable(R.drawable.notification_icon_background)
                binding.ivNotificationIcon.background = errorBackground?.apply {
                    setTint(android.graphics.Color.parseColor("#FF453A"))
                }
                binding.ivNotificationIcon.setColorFilter(android.graphics.Color.WHITE)
            }
            NotificationType.INFO -> {
                binding.ivNotificationIcon.setImageResource(R.drawable.ic_info)
                val infoBackground = host.context.getDrawable(R.drawable.notification_icon_background)
                binding.ivNotificationIcon.background = infoBackground?.apply {
                    setTint(android.graphics.Color.parseColor("#0A84FF"))
                }
                binding.ivNotificationIcon.setColorFilter(android.graphics.Color.WHITE)
            }
            NotificationType.WARNING -> {
                binding.ivNotificationIcon.setImageResource(R.drawable.ic_info)
                val warningBackground = host.context.getDrawable(R.drawable.notification_icon_background)
                binding.ivNotificationIcon.background = warningBackground?.apply {
                    setTint(android.graphics.Color.parseColor("#FF9F0A"))
                }
                binding.ivNotificationIcon.setColorFilter(android.graphics.Color.WHITE)
            }
            NotificationType.TRANSACTION_CREDIT -> {
                binding.ivNotificationIcon.visibility = View.GONE
                binding.tvTransactionSign.visibility = View.VISIBLE
                binding.tvTransactionSign.text = "+"
                binding.tvTransactionSign.setTextColor(
                    androidx.core.content.ContextCompat.getColor(host.context, R.color.status_text_credit)
                )
                binding.ivGrideeCoin.visibility = View.VISIBLE
                binding.tvNotificationMessage.setTextColor(
                    androidx.core.content.ContextCompat.getColor(host.context, R.color.status_text_credit)
                )
            }
            NotificationType.TRANSACTION_DEBIT -> {
                binding.ivNotificationIcon.visibility = View.GONE
                binding.tvTransactionSign.visibility = View.VISIBLE
                binding.tvTransactionSign.text = "-"
                binding.tvTransactionSign.setTextColor(
                    androidx.core.content.ContextCompat.getColor(host.context, R.color.status_text_debit)
                )
                binding.ivGrideeCoin.visibility = View.VISIBLE
                binding.tvNotificationMessage.setTextColor(
                    androidx.core.content.ContextCompat.getColor(host.context, R.color.status_text_debit)
                )
            }
            NotificationType.INFO_NO_ICON -> {
                binding.ivNotificationIcon.visibility = View.GONE
                binding.ivGrideeCoin.visibility = View.GONE
            }
        }

        // Hide the message row entirely when there's nothing to show.
        val hasMessageRowContent =
            binding.tvNotificationMessage.visibility == View.VISIBLE ||
                binding.tvTransactionSign.visibility == View.VISIBLE ||
                binding.ivGrideeCoin.visibility == View.VISIBLE
        binding.llMessageContainer.visibility = if (hasMessageRowContent) View.VISIBLE else View.GONE

        val density = host.context.resources.displayMetrics.density
        val sideMargin = (20 * density).toInt()
        val gapAboveNav = (12 * density).toInt()
        val gapAboveGesture = (16 * density).toInt()

        binding.root.layoutParams = buildLayoutParams(host, navbar, sideMargin, gapAboveNav, gapAboveGesture)

        host.addView(binding.root)

        // Inset listener — for hosts WITHOUT a floating navbar, we still need to clear
        // the gesture-nav bar. With a navbar present, the navbar already owns that space.
        if (navbar == null) {
            androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener(binding.root) { v, insets ->
                val navInsets = insets.getInsets(androidx.core.view.WindowInsetsCompat.Type.systemBars())
                val lp = v.layoutParams as ViewGroup.MarginLayoutParams
                lp.bottomMargin = gapAboveGesture + navInsets.bottom
                v.layoutParams = lp
                insets
            }
        }

        currentNotification = binding.root
        currentHostParent = host
        isDismissing = false

        binding.btnNotificationClose.setOnClickListener {
            it.performHapticFeedback(android.view.HapticFeedbackConstants.CLOCK_TICK)
            dismissNotification(binding.root, host)
        }

        if (onClick != null) {
            binding.root.setOnClickListener {
                it.performHapticFeedback(android.view.HapticFeedbackConstants.CLOCK_TICK)
                dismissNotification(binding.root, host)
                onClick.invoke()
            }
        }

        if (actionButtonText != null && onClick != null) {
            binding.btnNotificationAction.visibility = View.VISIBLE
            binding.btnNotificationAction.text = actionButtonText
            binding.btnNotificationAction.setOnClickListener {
                it.performHapticFeedback(android.view.HapticFeedbackConstants.CLOCK_TICK)
                dismissNotification(binding.root, host)
                onClick.invoke()
            }
        } else {
            binding.btnNotificationAction.visibility = View.GONE
        }

        animateIn(binding.root)

        dismissRunnable = Runnable { dismissNotification(binding.root, host) }
        binding.root.postDelayed(dismissRunnable, duration)
    }

    /**
     * Walk up from the caller-supplied parent to find the activity-level container.
     * If we find `main_container_root` (the layout that holds both fragment_container
     * AND bottom_navigation), use it so the banner draws above the floating navbar.
     * Otherwise fall back to the supplied parent.
     */
    private fun resolveHost(parent: ViewGroup): ViewGroup {
        var view: View = parent
        while (true) {
            if (view.id == R.id.main_container_root && view is ViewGroup) return view
            val next = view.parent
            if (next !is View) return parent
            view = next
        }
        @Suppress("UNREACHABLE_CODE")
        return parent
    }

    private fun buildLayoutParams(
        host: ViewGroup,
        navbar: View?,
        sideMargin: Int,
        gapAboveNav: Int,
        gapAboveGesture: Int
    ): ViewGroup.LayoutParams {
        val widthMatch = ViewGroup.LayoutParams.MATCH_PARENT
        val heightWrap = ViewGroup.LayoutParams.WRAP_CONTENT

        return when {
            host is androidx.constraintlayout.widget.ConstraintLayout -> {
                androidx.constraintlayout.widget.ConstraintLayout.LayoutParams(widthMatch, heightWrap).apply {
                    if (navbar != null && navbar.parent === host) {
                        bottomToTop = navbar.id
                        bottomMargin = gapAboveNav
                    } else {
                        bottomToBottom = androidx.constraintlayout.widget.ConstraintLayout.LayoutParams.PARENT_ID
                        bottomMargin = gapAboveGesture
                    }
                    startToStart = androidx.constraintlayout.widget.ConstraintLayout.LayoutParams.PARENT_ID
                    endToEnd = androidx.constraintlayout.widget.ConstraintLayout.LayoutParams.PARENT_ID
                    leftMargin = sideMargin
                    rightMargin = sideMargin
                }
            }
            host is CoordinatorLayout -> {
                CoordinatorLayout.LayoutParams(widthMatch, heightWrap).apply {
                    gravity = Gravity.BOTTOM
                    setMargins(sideMargin, 0, sideMargin, gapAboveGesture)
                }
            }
            host is FrameLayout -> {
                FrameLayout.LayoutParams(widthMatch, heightWrap).apply {
                    gravity = Gravity.BOTTOM
                    setMargins(sideMargin, 0, sideMargin, gapAboveGesture)
                }
            }
            else -> {
                ViewGroup.MarginLayoutParams(widthMatch, heightWrap).apply {
                    setMargins(sideMargin, 0, sideMargin, gapAboveGesture)
                }
            }
        }
    }

    /**
     * Entry — "Silk Glide." One continuous frictioned motion. No pauses,
     * no rotation, no overshoot. The card materializes (alpha fades up)
     * while gliding into position (translation + scale). Every property
     * starts at frame 1 and reaches target together — that simultaneity
     * is what makes the motion read as silky rather than choreographed.
     *
     * Tuning (all critically damped, dampingRatio = 1.0):
     *  - translateY: stiffness 280  → primary motion, ~340ms perceived
     *  - scaleX/Y:   stiffness 320  → slightly faster — leads the translate
     *                                  by a hair so scale settles before
     *                                  translation does. Reads as the card
     *                                  "finding its size" then drifting in.
     *  - alpha:      stiffness 550  → fades in within ~220ms, well before
     *                                  motion ends → user watches the full
     *                                  deceleration of a fully-visible card.
     *  - Travel:     78dp  — enough to see the full deceleration curve
     *                         without feeling sluggish.
     *  - Initial scale: 0.94 — subtle (not cartoonish), tasteful.
     */
    private fun animateIn(view: View) {
        val density = view.context.resources.displayMetrics.density
        val travel = 78f * density

        view.alpha = 0f
        view.scaleX = 0.94f
        view.scaleY = 0.94f
        view.translationY = travel
        view.rotation = 0f

        view.doOnPreDraw {
            view.pivotX = view.width / 2f
            view.pivotY = view.height / 2f

            view.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)

            val translationSpring = SpringForce().apply {
                dampingRatio = SpringForce.DAMPING_RATIO_NO_BOUNCY
                stiffness = 280f
            }
            val scaleSpring = SpringForce().apply {
                dampingRatio = SpringForce.DAMPING_RATIO_NO_BOUNCY
                stiffness = 320f
            }
            val alphaSpring = SpringForce().apply {
                dampingRatio = SpringForce.DAMPING_RATIO_NO_BOUNCY
                stiffness = 550f
            }

            startSpring(view, DynamicAnimation.TRANSLATION_Y, 0f, translationSpring)
            startSpring(view, DynamicAnimation.SCALE_X, 1f, scaleSpring)
            startSpring(view, DynamicAnimation.SCALE_Y, 1f, scaleSpring)
            startSpring(view, DynamicAnimation.ALPHA, 1f, alphaSpring)
        }
    }

    /**
     * Dismiss — "Silk Recede." Mirror of the entry. Same physics philosophy,
     * just reversed direction with slightly higher stiffness for a quicker
     * exit. All critically damped, all properties moving together. Card
     * gently glides downward, shrinks subtly, and fades — no dramatic scale
     * collapse, no abrupt motion. ~220ms perceived.
     */
    private fun dismissNotification(view: View, host: ViewGroup) {
        if (isDismissing || currentNotification != view) return
        isDismissing = true

        dismissRunnable?.let { view.removeCallbacks(it) }
        snapRunnable?.let { view.removeCallbacks(it); snapRunnable = null }
        cancelActiveSprings()

        val density = view.context.resources.displayMetrics.density
        val recede = 30f * density

        val translationSpring = SpringForce().apply {
            dampingRatio = SpringForce.DAMPING_RATIO_NO_BOUNCY
            stiffness = 750f
        }
        val scaleSpring = SpringForce().apply {
            dampingRatio = SpringForce.DAMPING_RATIO_NO_BOUNCY
            stiffness = 800f
        }
        val alphaSpring = SpringForce().apply {
            dampingRatio = SpringForce.DAMPING_RATIO_NO_BOUNCY
            stiffness = 1300f
        }

        startSpring(view, DynamicAnimation.TRANSLATION_Y, recede, translationSpring)
        startSpring(view, DynamicAnimation.SCALE_X, 0.94f, scaleSpring)
        startSpring(view, DynamicAnimation.SCALE_Y, 0.94f, scaleSpring)
        val alphaAnim = startSpring(view, DynamicAnimation.ALPHA, 0f, alphaSpring)

        alphaAnim.addEndListener { _, _, _, _ ->
            host.removeView(view)
            if (currentNotification == view) {
                currentNotification = null
                currentHostParent = null
            }
            isDismissing = false
            activeSprings.clear()
        }
    }

    private fun startSpring(
        view: View,
        property: DynamicAnimation.ViewProperty,
        target: Float,
        force: SpringForce
    ): SpringAnimation {
        val anim = SpringAnimation(view, property).apply {
            spring = SpringForce(target).apply {
                dampingRatio = force.dampingRatio
                stiffness = force.stiffness
            }
        }
        activeSprings.add(anim)
        anim.start()
        return anim
    }

    private fun cancelActiveSprings() {
        for (spring in activeSprings) {
            if (spring.isRunning) spring.cancel()
        }
        activeSprings.clear()
    }

    enum class NotificationType {
        SUCCESS, ERROR, WARNING, INFO, TRANSACTION_CREDIT, TRANSACTION_DEBIT, INFO_NO_ICON
    }
}
