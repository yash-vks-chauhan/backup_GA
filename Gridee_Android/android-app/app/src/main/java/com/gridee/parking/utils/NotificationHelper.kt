package com.gridee.parking.utils

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.coordinatorlayout.widget.CoordinatorLayout
import androidx.core.view.ViewCompat
import androidx.dynamicanimation.animation.DynamicAnimation
import androidx.dynamicanimation.animation.SpringAnimation
import androidx.dynamicanimation.animation.SpringForce
import com.gridee.parking.R
import com.gridee.parking.databinding.CustomNotificationBinding

object NotificationHelper {
    
    private var currentNotification: View? = null
    private var dismissRunnable: Runnable? = null
    
    fun showSuccess(
        parent: ViewGroup,
        title: String = "Success",
        message: String,
        duration: Long = 3000L
    ) {
        show(parent, title, message, NotificationType.SUCCESS, duration)
    }
    
    fun showError(
        parent: ViewGroup,
        title: String = "Error",
        message: String,
        duration: Long = 3000L
    ) {
        show(parent, title, message, NotificationType.ERROR, duration)
    }
    
    fun showInfo(
        parent: ViewGroup,
        title: String = "Info",
        message: String,
        duration: Long = 3000L
    ) {
        show(parent, title, message, NotificationType.INFO, duration)
    }
    
    private fun show(
        parent: ViewGroup,
        title: String,
        message: String,
        type: NotificationType,
        duration: Long
    ) {
        // Dismiss any existing notification
        currentNotification?.let { dismissNotification(it, parent) }
        
        val binding = CustomNotificationBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        
        // Set notification content
        binding.tvNotificationTitle.text = title
        binding.tvNotificationMessage.text = message
        
        // Set icon and color based on type
        when (type) {
            NotificationType.SUCCESS -> {
                binding.ivNotificationIcon.setImageResource(R.drawable.ic_check)
                binding.ivNotificationIcon.setBackgroundResource(R.drawable.notification_icon_background)
            }
            NotificationType.ERROR -> {
                binding.ivNotificationIcon.setImageResource(R.drawable.ic_close)
                val errorBackground = parent.context.getDrawable(R.drawable.notification_icon_background)
                binding.ivNotificationIcon.background = errorBackground?.apply {
                    setTint(android.graphics.Color.parseColor("#F44336"))
                }
            }
            NotificationType.INFO -> {
                binding.ivNotificationIcon.setImageResource(R.drawable.ic_info)
                val infoBackground = parent.context.getDrawable(R.drawable.notification_icon_background)
                binding.ivNotificationIcon.background = infoBackground?.apply {
                    setTint(android.graphics.Color.parseColor("#2196F3"))
                }
            }
        }
        
        // Add to parent with proper layout params
        val layoutParams = when (parent) {
            is CoordinatorLayout -> {
                CoordinatorLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                ).apply {
                    gravity = Gravity.TOP
                    setMargins(0, 0, 0, 0)
                }
            }
            is FrameLayout -> {
                FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                ).apply {
                    gravity = Gravity.TOP
                    setMargins(0, 0, 0, 0)
                }
            }
            else -> {
                ViewGroup.MarginLayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                ).apply {
                    setMargins(0, 0, 0, 0)
                }
            }
        }
        
        binding.root.layoutParams = layoutParams
        parent.addView(binding.root, 0) // Add at the top
        
        currentNotification = binding.root
        
        // Setup close button
        binding.ivClose.setOnClickListener {
            dismissNotification(binding.root, parent)
        }
        
        // Animate in
        animateIn(binding.root)
        
        // Auto dismiss after duration
        dismissRunnable = Runnable {
            dismissNotification(binding.root, parent)
        }
        binding.root.postDelayed(dismissRunnable, duration)
    }
    
    private fun animateIn(view: View) {
        // Start from above the screen
        view.translationY = -view.height.toFloat() - 100f
        view.alpha = 0f
        
        // Spring animation for translation
        val springAnim = SpringAnimation(view, DynamicAnimation.TRANSLATION_Y, 0f).apply {
            spring.stiffness = SpringForce.STIFFNESS_MEDIUM
            spring.dampingRatio = SpringForce.DAMPING_RATIO_MEDIUM_BOUNCY
            start()
        }
        
        // Fade in animation
        view.animate()
            .alpha(1f)
            .setDuration(300)
            .start()
    }
    
    private fun dismissNotification(view: View, parent: ViewGroup) {
        dismissRunnable?.let { view.removeCallbacks(it) }
        
        // Spring animation out
        val springAnim = SpringAnimation(view, DynamicAnimation.TRANSLATION_Y, -view.height.toFloat() - 100f).apply {
            spring.stiffness = SpringForce.STIFFNESS_MEDIUM
            spring.dampingRatio = SpringForce.DAMPING_RATIO_MEDIUM_BOUNCY
            
            addEndListener { _, _, _, _ ->
                parent.removeView(view)
                if (currentNotification == view) {
                    currentNotification = null
                }
            }
            
            start()
        }
        
        // Fade out animation
        view.animate()
            .alpha(0f)
            .setDuration(250)
            .start()
    }
    
    enum class NotificationType {
        SUCCESS, ERROR, INFO
    }
}
