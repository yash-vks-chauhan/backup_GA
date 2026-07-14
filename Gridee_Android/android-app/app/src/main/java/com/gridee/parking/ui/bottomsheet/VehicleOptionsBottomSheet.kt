package com.gridee.parking.ui.bottomsheet

import android.animation.AnimatorSet
import android.animation.ValueAnimator
import android.app.Dialog
import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.PathInterpolator
import android.view.inputmethod.InputMethodManager
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.gridee.parking.R
import com.gridee.parking.databinding.BottomSheetVehicleOptionsBinding
import com.gridee.parking.utils.VehicleNumberValidator

class VehicleOptionsBottomSheet(
    private val vehicleNumber: String,
    private val existingVehicleNumbers: List<String>,
    private val isDefault: Boolean,
    private val onEditSave: (newVehicleNumber: String) -> Unit,
    private val onMakeDefault: (String) -> Unit,
    private val onDeleteConfirm: (String) -> Unit
) : BottomSheetDialogFragment() {

    private var _binding: BottomSheetVehicleOptionsBinding? = null
    private val binding get() = _binding!!

    private var isAnimating = false

    // Tracks which page is currently visible: "options", "edit", "delete"
    private var currentPage = "options"

    // Apple-style spring interpolator
    private val pushInterpolator = PathInterpolator(0.32f, 0.72f, 0f, 1f)
    private val popInterpolator = PathInterpolator(0.32f, 0.72f, 0f, 1f)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setStyle(STYLE_NORMAL, R.style.BottomSheetDialogTheme)
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val dialog = super.onCreateDialog(savedInstanceState) as BottomSheetDialog

        dialog.setOnShowListener { dialogInterface ->
            val bottomSheet = (dialogInterface as BottomSheetDialog)
                .findViewById<android.widget.FrameLayout>(com.google.android.material.R.id.design_bottom_sheet)

            bottomSheet?.let { sheet ->
                val behavior = BottomSheetBehavior.from(sheet)
                behavior.state = BottomSheetBehavior.STATE_EXPANDED
                behavior.skipCollapsed = true
                behavior.isDraggable = true
                behavior.isHideable = true

                // Allow child views to render outside bounds during slide animation
                (sheet as? ViewGroup)?.clipChildren = false
                (sheet as? ViewGroup)?.clipToPadding = false
                (sheet.parent as? ViewGroup)?.clipChildren = false
                (sheet.parent as? ViewGroup)?.clipToPadding = false
            }
        }

        return dialog
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = BottomSheetVehicleOptionsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupUI()
        setupOptionsListeners()
        setupEditListeners()
        setupDeleteListeners()
    }

    private fun setupUI() {
        binding.tvSubtitle.text = vehicleNumber

        if (isDefault) {
            binding.ivDefaultCheck.visibility = View.VISIBLE
            binding.tvDefaultText.text = "Default Vehicle"
            binding.btnMakeDefault.alpha = 0.6f
            binding.btnMakeDefault.isEnabled = false
        }

        // Pre-fill edit page input
        binding.etVehicleNumber.setText(vehicleNumber)
        binding.etVehicleNumber.setSelection(vehicleNumber.length)

        // Set delete confirmation message with vehicle number
        binding.tvDeleteMessage.text = "Are you sure you want to remove vehicle $vehicleNumber? This action cannot be undone."
    }

    // ─────────────────────────────────────────────────────────
    // Options Page Listeners
    // ─────────────────────────────────────────────────────────
    private fun setupOptionsListeners() {
        binding.btnClose.setOnClickListener {
            dismiss()
        }

        binding.btnEdit.setOnClickListener {
            if (!isAnimating) {
                navigateToPage(binding.layoutOptionsPage, binding.layoutEditPage)
                currentPage = "edit"
            }
        }

        binding.btnMakeDefault.setOnClickListener {
            dismiss()
            onMakeDefault(vehicleNumber)
        }

        binding.btnDelete.setOnClickListener {
            if (!isAnimating) {
                navigateToPage(binding.layoutOptionsPage, binding.layoutDeletePage)
                currentPage = "delete"
            }
        }
    }

    // ─────────────────────────────────────────────────────────
    // Edit Page Listeners
    // ─────────────────────────────────────────────────────────
    private fun setupEditListeners() {
        binding.btnEditBack.setOnClickListener {
            if (!isAnimating) {
                hideKeyboard()
                navigateBackToPage(binding.layoutEditPage, binding.layoutOptionsPage)
                currentPage = "options"
            }
        }

        binding.btnSave.setOnClickListener {
            val newVehicleNumber = VehicleNumberValidator.normalize(
                binding.etVehicleNumber.text.toString()
            )
            val currentVehicleNumber = VehicleNumberValidator.normalize(vehicleNumber)

            val error = VehicleNumberValidator.getError(newVehicleNumber)
            if (error != null) {
                binding.tilVehicleNumber.error = error
                return@setOnClickListener
            }

            if (
                newVehicleNumber != currentVehicleNumber &&
                existingVehicleNumbers.any {
                    !VehicleNumberValidator.areEquivalent(it, vehicleNumber) &&
                        VehicleNumberValidator.areEquivalent(it, newVehicleNumber)
                }
            ) {
                binding.tilVehicleNumber.error = "Vehicle number already exists"
                return@setOnClickListener
            }

            if (newVehicleNumber == currentVehicleNumber) {
                hideKeyboard()
                dismiss()
                return@setOnClickListener
            }

            hideKeyboard()
            onEditSave(newVehicleNumber)
            dismiss()
        }

        // Clear error when user types
        binding.etVehicleNumber.setOnFocusChangeListener { _, hasFocus ->
            if (hasFocus) {
                binding.tilVehicleNumber.error = null
            }
        }
    }

    // ─────────────────────────────────────────────────────────
    // Delete Page Listeners
    // ─────────────────────────────────────────────────────────
    private fun setupDeleteListeners() {
        binding.btnDeleteBack.setOnClickListener {
            if (!isAnimating) {
                navigateBackToPage(binding.layoutDeletePage, binding.layoutOptionsPage)
                currentPage = "options"
            }
        }

        binding.btnDeleteCancel.setOnClickListener {
            if (!isAnimating) {
                navigateBackToPage(binding.layoutDeletePage, binding.layoutOptionsPage)
                currentPage = "options"
            }
        }

        binding.btnDeleteConfirm.setOnClickListener {
            onDeleteConfirm(vehicleNumber)
            dismiss()
        }
    }

    // ─────────────────────────────────────────────────────────
    // Generic Page Navigation Animations
    // ─────────────────────────────────────────────────────────

    /**
     * Push navigation: slides [fromPage] out to the left and [toPage] in from the right.
     */
    private fun navigateToPage(fromPage: View, toPage: View) {
        isAnimating = true

        val rootContainer = binding.rootContainer
        val containerWidth = rootContainer.width.toFloat()

        // Disable dragging on sub-pages
        (dialog as? BottomSheetDialog)?.behavior?.isDraggable = false

        // Measure the target page
        toPage.visibility = View.INVISIBLE
        toPage.measure(
            View.MeasureSpec.makeMeasureSpec(rootContainer.width, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
        )
        val toPageHeight = toPage.measuredHeight
        val fromPageHeight = fromPage.height

        // Position target page off-screen to the right
        toPage.translationX = containerWidth
        toPage.alpha = 1f
        toPage.visibility = View.VISIBLE

        // Fix container height
        rootContainer.layoutParams.height = fromPageHeight
        rootContainer.requestLayout()

        // Hardware layers
        fromPage.setLayerType(View.LAYER_TYPE_HARDWARE, null)
        toPage.setLayerType(View.LAYER_TYPE_HARDWARE, null)

        val animDuration = 380L

        val fromSlide = ValueAnimator.ofFloat(0f, -containerWidth * 0.3f).apply {
            addUpdateListener { fromPage.translationX = it.animatedValue as Float }
        }
        val fromFade = ValueAnimator.ofFloat(1f, 0.4f).apply {
            addUpdateListener { fromPage.alpha = it.animatedValue as Float }
        }
        val toSlide = ValueAnimator.ofFloat(containerWidth, 0f).apply {
            addUpdateListener { toPage.translationX = it.animatedValue as Float }
        }
        val heightAnim = ValueAnimator.ofInt(fromPageHeight, toPageHeight).apply {
            addUpdateListener {
                rootContainer.layoutParams.height = it.animatedValue as Int
                rootContainer.requestLayout()
            }
        }

        AnimatorSet().apply {
            playTogether(fromSlide, fromFade, toSlide, heightAnim)
            duration = animDuration
            interpolator = pushInterpolator

            addListener(object : android.animation.AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: android.animation.Animator) {
                    fromPage.visibility = View.GONE
                    fromPage.translationX = 0f
                    fromPage.alpha = 1f
                    fromPage.setLayerType(View.LAYER_TYPE_NONE, null)
                    toPage.setLayerType(View.LAYER_TYPE_NONE, null)
                    rootContainer.layoutParams.height = ViewGroup.LayoutParams.WRAP_CONTENT
                    rootContainer.requestLayout()
                    isAnimating = false
                }
            })

            start()
        }
    }

    /**
     * Pop navigation: slides [fromPage] out to the right and [toPage] back in from the left.
     */
    private fun navigateBackToPage(fromPage: View, toPage: View) {
        isAnimating = true

        val rootContainer = binding.rootContainer
        val containerWidth = rootContainer.width.toFloat()

        // Re-enable dragging when back on options page
        (dialog as? BottomSheetDialog)?.behavior?.isDraggable = true

        // Measure the target page
        toPage.visibility = View.INVISIBLE
        toPage.measure(
            View.MeasureSpec.makeMeasureSpec(rootContainer.width, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
        )
        val toPageHeight = toPage.measuredHeight
        val fromPageHeight = fromPage.height

        // Position target page at its parallax offset
        toPage.translationX = -containerWidth * 0.3f
        toPage.alpha = 0.4f
        toPage.visibility = View.VISIBLE

        // Fix container height
        rootContainer.layoutParams.height = fromPageHeight
        rootContainer.requestLayout()

        // Hardware layers
        fromPage.setLayerType(View.LAYER_TYPE_HARDWARE, null)
        toPage.setLayerType(View.LAYER_TYPE_HARDWARE, null)

        val animDuration = 350L

        val toSlide = ValueAnimator.ofFloat(-containerWidth * 0.3f, 0f).apply {
            addUpdateListener { toPage.translationX = it.animatedValue as Float }
        }
        val toFade = ValueAnimator.ofFloat(0.4f, 1f).apply {
            addUpdateListener { toPage.alpha = it.animatedValue as Float }
        }
        val fromSlide = ValueAnimator.ofFloat(0f, containerWidth).apply {
            addUpdateListener { fromPage.translationX = it.animatedValue as Float }
        }
        val heightAnim = ValueAnimator.ofInt(fromPageHeight, toPageHeight).apply {
            addUpdateListener {
                rootContainer.layoutParams.height = it.animatedValue as Int
                rootContainer.requestLayout()
            }
        }

        AnimatorSet().apply {
            playTogether(toSlide, toFade, fromSlide, heightAnim)
            duration = animDuration
            interpolator = popInterpolator

            addListener(object : android.animation.AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: android.animation.Animator) {
                    fromPage.visibility = View.GONE
                    fromPage.translationX = 0f
                    fromPage.setLayerType(View.LAYER_TYPE_NONE, null)
                    toPage.setLayerType(View.LAYER_TYPE_NONE, null)
                    rootContainer.layoutParams.height = ViewGroup.LayoutParams.WRAP_CONTENT
                    rootContainer.requestLayout()
                    isAnimating = false
                }
            })

            start()
        }
    }

    // ─────────────────────────────────────────────────────────
    // Utilities
    // ─────────────────────────────────────────────────────────

    private fun hideKeyboard() {
        val imm = requireContext().getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        val currentFocus = dialog?.currentFocus ?: binding.root
        imm.hideSoftInputFromWindow(currentFocus.windowToken, 0)
        binding.etVehicleNumber.clearFocus()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        const val TAG = "VehicleOptionsBottomSheet"
    }
}
