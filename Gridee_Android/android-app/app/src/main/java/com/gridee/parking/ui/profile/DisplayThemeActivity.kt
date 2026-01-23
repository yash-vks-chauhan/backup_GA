package com.gridee.parking.ui.profile

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.graphics.Color
import android.os.Bundle
import android.view.HapticFeedbackConstants
import android.view.View
import android.view.ViewGroup
import android.view.animation.DecelerateInterpolator
import android.view.animation.PathInterpolator
import androidx.core.view.WindowInsetsControllerCompat
import com.gridee.parking.databinding.ActivityDisplayThemeBinding
import com.gridee.parking.ui.base.BaseActivity

class DisplayThemeActivity : BaseActivity<ActivityDisplayThemeBinding>() {

    private var isExpanded = true // Default open
    private var isAnimating = false

    private var isThemeExpanded = true // Default open
    private var isThemeAnimating = false
    private var initialHeroGradientKey: String = "obsidian_dip"

    override fun getViewBinding(): ActivityDisplayThemeBinding {
        return ActivityDisplayThemeBinding.inflate(layoutInflater)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        window.statusBarColor = Color.parseColor("#F5F5F5")
        WindowInsetsControllerCompat(window, window.decorView).isAppearanceLightStatusBars = true
        
        setupRadioGroup()
        setupChangeListeners()
        setupAccordion()
        setupThemeAccordion()

        binding.btnBackToProfile.setOnClickListener {
            saveSelection()
            android.widget.Toast.makeText(this, "Changes saved", android.widget.Toast.LENGTH_SHORT).show()
            finish()
        }

        binding.toolbar.setNavigationOnClickListener {
            finish()
        }
    }
    
    private fun setupAccordion() {
        binding.btnHeroGradientAccordion.setOnClickListener {
            if (isAnimating) return@setOnClickListener

            // Haptic Feedback
            it.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)

            // "Squish" Micro-interaction
            it.animate()
                .scaleX(0.98f)
                .scaleY(0.98f)
                .setDuration(100)
                .setInterpolator(DecelerateInterpolator())
                .withEndAction {
                    it.animate()
                        .scaleX(1f)
                        .scaleY(1f)
                        .setDuration(100)
                        .setInterpolator(DecelerateInterpolator())
                        .start()
                }
                .start()

            toggleAccordion()
        }
    }

    private fun toggleAccordion() {
        val expandedLayout = binding.layoutHeroGradientExpanded
        val arrowIcon = binding.ivAccordionArrow
        
        isExpanded = !isExpanded
        isAnimating = true
        
        // Use Hardware Acceleration
        expandedLayout.setLayerType(View.LAYER_TYPE_HARDWARE, null)

        // Apple-style Cubic Bezier Curves
        val openInterpolator = PathInterpolator(0.4f, 0f, 0.2f, 1f)
        val closeInterpolator = PathInterpolator(0.4f, 0f, 1f, 1f)
        
        if (isExpanded) {
            // OPENING
            expandedLayout.visibility = View.VISIBLE
            expandedLayout.alpha = 0f
            expandedLayout.translationY = -20f.dpToPx()
            
            expandedLayout.measure(
                View.MeasureSpec.makeMeasureSpec(binding.root.width, View.MeasureSpec.AT_MOST),
                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
            )
            val targetHeight = expandedLayout.measuredHeight
            
            ValueAnimator.ofInt(0, targetHeight).apply {
                duration = 350
                interpolator = openInterpolator
                addUpdateListener { animation ->
                    expandedLayout.layoutParams.height = animation.animatedValue as Int
                    expandedLayout.requestLayout()
                }
                addListener(object : AnimatorListenerAdapter() {
                    override fun onAnimationEnd(animation: Animator) {
                        expandedLayout.layoutParams.height = ViewGroup.LayoutParams.WRAP_CONTENT
                        expandedLayout.setLayerType(View.LAYER_TYPE_NONE, null)
                        isAnimating = false
                    }
                })
                start()
            }
            
            expandedLayout.animate()
                .alpha(1f)
                .setDuration(300)
                .setStartDelay(50)
                .setInterpolator(openInterpolator)
                .start()
            
            expandedLayout.animate()
                .translationY(0f)
                .setDuration(350)
                .setInterpolator(openInterpolator)
                .start()
            
            arrowIcon.animate()
                .rotation(180f)
                .setDuration(350)
                .setInterpolator(openInterpolator)
                .start()
                
        } else {
            // CLOSING
            val currentHeight = expandedLayout.height
            
            ValueAnimator.ofInt(currentHeight, 0).apply {
                duration = 300
                interpolator = closeInterpolator
                addUpdateListener { animation ->
                    expandedLayout.layoutParams.height = animation.animatedValue as Int
                    expandedLayout.requestLayout()
                }
                addListener(object : AnimatorListenerAdapter() {
                    override fun onAnimationEnd(animation: Animator) {
                        expandedLayout.visibility = View.GONE
                        expandedLayout.setLayerType(View.LAYER_TYPE_NONE, null)
                        expandedLayout.rotationX = 0f
                        expandedLayout.scaleX = 1f
                        expandedLayout.scaleY = 1f
                        isAnimating = false
                    }
                })
                start()
            }
            
            // 3D "Fold Back" Exit
            expandedLayout.pivotY = 0f
            expandedLayout.animate()
                .alpha(0f)
                .translationY(-30f.dpToPx())
                .rotationX(15f)
                .scaleX(0.95f)
                .scaleY(0.95f)
                .setDuration(250)
                .setInterpolator(closeInterpolator)
                .start()
            
            arrowIcon.animate()
                .rotation(0f)
                .setDuration(300)
                .setInterpolator(closeInterpolator)
                .start()
        }
    }
    
    private fun setupThemeAccordion() {
        binding.btnThemeAccordion.setOnClickListener {
            if (isThemeAnimating) return@setOnClickListener

            // Haptic Feedback
            it.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)

            // "Squish" Micro-interaction
            it.animate()
                .scaleX(0.98f)
                .scaleY(0.98f)
                .setDuration(100)
                .setInterpolator(DecelerateInterpolator())
                .withEndAction {
                    it.animate()
                        .scaleX(1f)
                        .scaleY(1f)
                        .setDuration(100)
                        .setInterpolator(DecelerateInterpolator())
                        .start()
                }
                .start()

            toggleThemeAccordion()
        }
    }

    private fun toggleThemeAccordion() {
        val expandedLayout = binding.layoutThemeExpanded
        val arrowIcon = binding.ivThemeArrow
        
        isThemeExpanded = !isThemeExpanded
        isThemeAnimating = true
        
        // Use Hardware Acceleration
        expandedLayout.setLayerType(View.LAYER_TYPE_HARDWARE, null)

        // Apple-style Cubic Bezier Curves
        val openInterpolator = PathInterpolator(0.4f, 0f, 0.2f, 1f)
        val closeInterpolator = PathInterpolator(0.4f, 0f, 1f, 1f)
        
        if (isThemeExpanded) {
            // OPENING
            expandedLayout.visibility = View.VISIBLE
            expandedLayout.alpha = 0f
            expandedLayout.translationY = -20f.dpToPx()
            
            expandedLayout.measure(
                View.MeasureSpec.makeMeasureSpec(binding.root.width, View.MeasureSpec.AT_MOST),
                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
            )
            val targetHeight = expandedLayout.measuredHeight
            
            ValueAnimator.ofInt(0, targetHeight).apply {
                duration = 350
                interpolator = openInterpolator
                addUpdateListener { animation ->
                    expandedLayout.layoutParams.height = animation.animatedValue as Int
                    expandedLayout.requestLayout()
                }
                addListener(object : AnimatorListenerAdapter() {
                    override fun onAnimationEnd(animation: Animator) {
                        expandedLayout.layoutParams.height = ViewGroup.LayoutParams.WRAP_CONTENT
                        expandedLayout.setLayerType(View.LAYER_TYPE_NONE, null)
                        isThemeAnimating = false
                    }
                })
                start()
            }
            
            expandedLayout.animate()
                .alpha(1f)
                .setDuration(300)
                .setStartDelay(50)
                .setInterpolator(openInterpolator)
                .start()
            
            expandedLayout.animate()
                .translationY(0f)
                .setDuration(350)
                .setInterpolator(openInterpolator)
                .start()
            
            arrowIcon.animate()
                .rotation(180f)
                .setDuration(350)
                .setInterpolator(openInterpolator)
                .start()
                
        } else {
            // CLOSING
            val currentHeight = expandedLayout.height
            
            ValueAnimator.ofInt(currentHeight, 0).apply {
                duration = 300
                interpolator = closeInterpolator
                addUpdateListener { animation ->
                    expandedLayout.layoutParams.height = animation.animatedValue as Int
                    expandedLayout.requestLayout()
                }
                addListener(object : AnimatorListenerAdapter() {
                    override fun onAnimationEnd(animation: Animator) {
                        expandedLayout.visibility = View.GONE
                        expandedLayout.setLayerType(View.LAYER_TYPE_NONE, null)
                        expandedLayout.rotationX = 0f
                        expandedLayout.scaleX = 1f
                        expandedLayout.scaleY = 1f
                        isThemeAnimating = false
                    }
                })
                start()
            }
            
            // 3D "Fold Back" Exit
            expandedLayout.pivotY = 0f
            expandedLayout.animate()
                .alpha(0f)
                .translationY(-30f.dpToPx())
                .rotationX(15f)
                .scaleX(0.95f)
                .scaleY(0.95f)
                .setDuration(250)
                .setInterpolator(closeInterpolator)
                .start()
            
            arrowIcon.animate()
                .rotation(0f)
                .setDuration(300)
                .setInterpolator(closeInterpolator)
                .start()
        }
    }
    
    private fun Float.dpToPx(): Float {
        return this * resources.displayMetrics.density
    }
    
    private fun setupRadioGroup() {
        val sharedPref = getSharedPreferences("gridee_prefs", android.content.Context.MODE_PRIVATE)
        initialHeroGradientKey = sharedPref.getString("hero_gradient_key", "obsidian_dip") ?: "obsidian_dip"
        
        when(initialHeroGradientKey) {
            "midnight_slate" -> binding.rbMidnightSlate.isChecked = true
            "obsidian_dip" -> binding.rbObsidianDip.isChecked = true
            "carbon_mist" -> binding.rbCarbonMist.isChecked = true
            "lavender_whisper" -> binding.rbLavenderWhisper.isChecked = true
            else -> binding.rbObsidianDip.isChecked = true
        }

        updateSaveButtonVisibility(hasChanges = false)
    }

    private fun setupChangeListeners() {
        binding.radioGroupGradients.setOnCheckedChangeListener { _, _ ->
            updateSaveButtonVisibility(currentHeroGradientKey() != initialHeroGradientKey)
        }
    }
    
    private fun saveSelection() {
        val selected = currentHeroGradientKey()
        
        val sharedPref = getSharedPreferences("gridee_prefs", android.content.Context.MODE_PRIVATE)
        with(sharedPref.edit()) {
            putString("hero_gradient_key", selected)
            apply()
        }

        initialHeroGradientKey = selected
        updateSaveButtonVisibility(hasChanges = false)
    }

    private fun currentHeroGradientKey(): String {
        return when(binding.radioGroupGradients.checkedRadioButtonId) {
            binding.rbMidnightSlate.id -> "midnight_slate"
            binding.rbObsidianDip.id -> "obsidian_dip"
            binding.rbCarbonMist.id -> "carbon_mist"
            binding.rbLavenderWhisper.id -> "lavender_whisper"
            else -> "midnight_slate"
        }
    }

    private fun updateSaveButtonVisibility(hasChanges: Boolean) {
        binding.layoutSaveContainer.visibility = if (hasChanges) View.VISIBLE else View.GONE
    }
}
