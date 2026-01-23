package com.gridee.parking.ui.bottomsheet

import android.content.DialogInterface
import android.graphics.RenderEffect
import android.graphics.Shader
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.annotation.StringRes
import androidx.dynamicanimation.animation.DynamicAnimation
import androidx.dynamicanimation.animation.SpringAnimation
import androidx.dynamicanimation.animation.SpringForce
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.gridee.parking.R
import com.gridee.parking.databinding.BottomSheetPaymentMethodFilterBinding

enum class PaymentMethodOption(@StringRes val labelRes: Int) {
    ALL(R.string.payment_filter_option_all),
    UPI(R.string.payment_filter_option_upi),
    CARD(R.string.payment_filter_option_card)
}

class PaymentMethodFilterBottomSheet(
    private val initialSelection: PaymentMethodOption,
    private val onApply: (PaymentMethodOption) -> Unit
) : BottomSheetDialogFragment() {

    private var _binding: BottomSheetPaymentMethodFilterBinding? = null
    private val binding get() = _binding!!

    private var currentSelection: PaymentMethodOption = initialSelection
    private var hostContentView: ViewGroup? = null
    private var originalHostAlpha: Float = 1f

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setStyle(STYLE_NORMAL, R.style.BottomSheetDialogTheme)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = BottomSheetPaymentMethodFilterBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupWindowInsets()
        setupSelection()
        setupListeners()
        animateEntry()
    }

    override fun onStart() {
        super.onStart()
        dialog?.window?.setDimAmount(0f)
        applyHostBlur()
        (dialog as? BottomSheetDialog)?.behavior?.apply {
            state = BottomSheetBehavior.STATE_EXPANDED
            skipCollapsed = true
        }
    }

    override fun onDismiss(dialog: DialogInterface) {
        clearHostBlur()
        super.onDismiss(dialog)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private fun setupWindowInsets() {
        binding.root.clipToOutline = true
    }

    private fun setupSelection() {
        val targetId = optionToRadioId(initialSelection)
        binding.groupPaymentMethods.check(targetId)
    }

    private fun setupListeners() {
        binding.groupPaymentMethods.setOnCheckedChangeListener { _, checkedId ->
            currentSelection = when (checkedId) {
                binding.rbPaymentAll.id -> PaymentMethodOption.ALL
                binding.rbPaymentUpi.id -> PaymentMethodOption.UPI
                binding.rbPaymentCard.id -> PaymentMethodOption.CARD
                else -> PaymentMethodOption.ALL
            }
        }

        binding.btnApplyFilter.setOnClickListener {
            animateButtonPress(it) {
                onApply.invoke(currentSelection)
                dismiss()
            }
        }

        binding.btnCancelFilter.setOnClickListener {
            animateButtonPress(it) {
                dismiss()
            }
        }
    }

    private fun animateEntry() {
        binding.root.scaleX = 0.95f
        binding.root.scaleY = 0.95f
        binding.root.alpha = 0f

        SpringAnimation(binding.root, DynamicAnimation.SCALE_X, 1f).apply {
            spring.dampingRatio = SpringForce.DAMPING_RATIO_MEDIUM_BOUNCY
            spring.stiffness = SpringForce.STIFFNESS_MEDIUM
            start()
        }

        SpringAnimation(binding.root, DynamicAnimation.SCALE_Y, 1f).apply {
            spring.dampingRatio = SpringForce.DAMPING_RATIO_MEDIUM_BOUNCY
            spring.stiffness = SpringForce.STIFFNESS_MEDIUM
            start()
        }

        SpringAnimation(binding.root, DynamicAnimation.ALPHA, 1f).apply {
            spring.dampingRatio = SpringForce.DAMPING_RATIO_LOW_BOUNCY
            spring.stiffness = SpringForce.STIFFNESS_LOW
            start()
        }
    }

    private fun animateButtonPress(target: View, onComplete: () -> Unit) {
        target.animate()
            .scaleX(0.96f)
            .scaleY(0.96f)
            .setDuration(80)
            .withEndAction {
                target.animate()
                    .scaleX(1f)
                    .scaleY(1f)
                    .setDuration(120)
                    .withEndAction { onComplete() }
                    .start()
            }
            .start()
    }

    private fun applyHostBlur() {
        val activityRoot = activity?.findViewById<ViewGroup>(android.R.id.content) ?: return
        hostContentView = activityRoot
        originalHostAlpha = activityRoot.alpha

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val blurEffect = RenderEffect.createBlurEffect(28f, 28f, Shader.TileMode.CLAMP)
            activityRoot.setRenderEffect(blurEffect)
        }

        activityRoot.animate()
            .alpha(0.85f)
            .setDuration(180)
            .start()
    }

    private fun clearHostBlur() {
        val activityRoot = hostContentView ?: return

        activityRoot.animate()
            .alpha(originalHostAlpha)
            .setDuration(160)
            .withEndAction {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    activityRoot.setRenderEffect(null)
                }
            }
            .start()
    }

    private fun optionToRadioId(option: PaymentMethodOption): Int {
        return when (option) {
            PaymentMethodOption.ALL -> R.id.rbPaymentAll
            PaymentMethodOption.UPI -> R.id.rbPaymentUpi
            PaymentMethodOption.CARD -> R.id.rbPaymentCard
        }
    }

    companion object {
        const val TAG = "PaymentMethodFilterBottomSheet"
    }
}
