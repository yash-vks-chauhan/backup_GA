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
import androidx.lifecycle.Lifecycle
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.gridee.parking.R
import com.gridee.parking.databinding.BottomSheetVehicleOptionsBinding
import com.gridee.parking.utils.VehicleNumberValidator

class VehicleOptionsBottomSheet : BottomSheetDialogFragment() {

    private var _binding: BottomSheetVehicleOptionsBinding? = null
    private val binding get() = _binding!!

    private val vehicleNumber: String
        get() = requireArguments().getString(ARG_VEHICLE_NUMBER).orEmpty()
    private val existingVehicleNumbers: List<String>
        get() = arguments?.getStringArrayList(ARG_EXISTING_VEHICLES).orEmpty()
    private val isDefault: Boolean
        get() = arguments?.getBoolean(ARG_IS_DEFAULT) ?: false

    private var isAnimating = false

    // Tracks which page is currently visible: "options", "edit", "delete"
    private var currentPage = PAGE_OPTIONS
    private var actionDelivered = false
    private var pendingAction: String? = null
    private var pendingNewVehicleNumber: String? = null
    private var pageAnimator: AnimatorSet? = null
    private val resumeDeliveryRunnable = Runnable {
        if (actionDelivered) {
            dismissIfStateCanBeSaved()
        } else {
            deliverPendingActionIfPossible()
        }
    }

    // Apple-style spring interpolator
    private val pushInterpolator = PathInterpolator(0.32f, 0.72f, 0f, 1f)
    private val popInterpolator = PathInterpolator(0.32f, 0.72f, 0f, 1f)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        currentPage = savedInstanceState?.getString(STATE_CURRENT_PAGE)
            ?.takeIf { it in VALID_PAGES }
            ?: PAGE_OPTIONS
        actionDelivered = savedInstanceState?.getBoolean(STATE_ACTION_DELIVERED) ?: false
        pendingAction = savedInstanceState?.getString(STATE_PENDING_ACTION)
            ?.takeIf { it in VALID_ACTIONS }
        pendingNewVehicleNumber = savedInstanceState?.getString(STATE_PENDING_NEW_VEHICLE_NUMBER)
            ?.takeIf { it.isNotBlank() }
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
                behavior.isDraggable = currentPage == PAGE_OPTIONS
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
        renderCurrentPage()
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
            dismissIfStateCanBeSaved()
        }

        binding.btnEdit.setOnClickListener {
            if (!isAnimating) {
                navigateToPage(binding.layoutOptionsPage, binding.layoutEditPage)
                currentPage = PAGE_EDIT
            }
        }

        binding.btnMakeDefault.setOnClickListener {
            publishAction(ACTION_MAKE_DEFAULT)
        }

        binding.btnDelete.setOnClickListener {
            if (!isAnimating) {
                navigateToPage(binding.layoutOptionsPage, binding.layoutDeletePage)
                currentPage = PAGE_DELETE
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
                currentPage = PAGE_OPTIONS
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
                dismissIfStateCanBeSaved()
                return@setOnClickListener
            }

            hideKeyboard()
            publishAction(ACTION_EDIT, newVehicleNumber)
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
                currentPage = PAGE_OPTIONS
            }
        }

        binding.btnDeleteCancel.setOnClickListener {
            if (!isAnimating) {
                navigateBackToPage(binding.layoutDeletePage, binding.layoutOptionsPage)
                currentPage = PAGE_OPTIONS
            }
        }

        binding.btnDeleteConfirm.setOnClickListener {
            publishAction(ACTION_DELETE)
        }
    }

    private fun renderCurrentPage() {
        binding.layoutOptionsPage.visibility = if (currentPage == PAGE_OPTIONS) View.VISIBLE else View.GONE
        binding.layoutEditPage.visibility = if (currentPage == PAGE_EDIT) View.VISIBLE else View.GONE
        binding.layoutDeletePage.visibility = if (currentPage == PAGE_DELETE) View.VISIBLE else View.GONE
        binding.layoutOptionsPage.translationX = 0f
        binding.layoutEditPage.translationX = 0f
        binding.layoutDeletePage.translationX = 0f
        binding.layoutOptionsPage.alpha = 1f
        binding.layoutEditPage.alpha = 1f
        binding.layoutDeletePage.alpha = 1f
        (dialog as? BottomSheetDialog)?.behavior?.isDraggable = currentPage == PAGE_OPTIONS
    }

    private fun publishAction(action: String, newVehicleNumber: String? = null) {
        if (actionDelivered || pendingAction != null || action !in VALID_ACTIONS) return
        pendingAction = action
        pendingNewVehicleNumber = newVehicleNumber
        deliverPendingActionIfPossible()
    }

    override fun onResume() {
        super.onResume()
        _binding?.root?.removeCallbacks(resumeDeliveryRunnable)
        _binding?.root?.post(resumeDeliveryRunnable)
    }

    private fun deliverPendingActionIfPossible() {
        val action = pendingAction ?: return
        val newVehicleNumber = pendingNewVehicleNumber
        val fragmentManager = runCatching { parentFragmentManager }.getOrNull() ?: return
        if (!isAdded || !lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED) ||
            fragmentManager.isDestroyed || fragmentManager.isStateSaved
        ) return

        actionDelivered = true
        pendingAction = null
        pendingNewVehicleNumber = null
        val published = runCatching {
            fragmentManager.setFragmentResult(
                RESULT_KEY,
                Bundle().apply {
                    putString(RESULT_ACTION, action)
                    putString(RESULT_ORIGINAL_VEHICLE_NUMBER, vehicleNumber)
                    newVehicleNumber?.let { putString(RESULT_NEW_VEHICLE_NUMBER, it) }
                },
            )
        }.isSuccess
        if (!published) {
            actionDelivered = false
            pendingAction = action
            pendingNewVehicleNumber = newVehicleNumber
            return
        }
        dismissIfStateCanBeSaved()
    }

    private fun dismissIfStateCanBeSaved() {
        val fragmentManager = runCatching { parentFragmentManager }.getOrNull() ?: return
        if (!isAdded || !lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED) ||
            fragmentManager.isDestroyed || fragmentManager.isStateSaved
        ) return
        dismiss()
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
                    if (pageAnimator !== animation || _binding == null) return
                    pageAnimator = null
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
            pageAnimator = this
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
                    if (pageAnimator !== animation || _binding == null) return
                    pageAnimator = null
                    fromPage.visibility = View.GONE
                    fromPage.translationX = 0f
                    fromPage.setLayerType(View.LAYER_TYPE_NONE, null)
                    toPage.setLayerType(View.LAYER_TYPE_NONE, null)
                    rootContainer.layoutParams.height = ViewGroup.LayoutParams.WRAP_CONTENT
                    rootContainer.requestLayout()
                    isAnimating = false
                }
            })
            pageAnimator = this
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
        pageAnimator?.removeAllListeners()
        pageAnimator?.cancel()
        pageAnimator = null
        isAnimating = false
        _binding?.root?.removeCallbacks(resumeDeliveryRunnable)
        _binding?.let { currentBinding ->
            listOf(
                currentBinding.layoutOptionsPage,
                currentBinding.layoutEditPage,
                currentBinding.layoutDeletePage,
            ).forEach { page ->
                page.animate()
                    .setListener(null)
                    .withStartAction(null)
                    .withEndAction(null)
                    .cancel()
                page.setLayerType(View.LAYER_TYPE_NONE, null)
            }
        }
        super.onDestroyView()
        _binding = null
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putString(STATE_CURRENT_PAGE, currentPage)
        outState.putBoolean(STATE_ACTION_DELIVERED, actionDelivered)
        pendingAction?.let { outState.putString(STATE_PENDING_ACTION, it) }
        pendingNewVehicleNumber?.let {
            outState.putString(STATE_PENDING_NEW_VEHICLE_NUMBER, it)
        }
        super.onSaveInstanceState(outState)
    }

    companion object {
        const val TAG = "VehicleOptionsBottomSheet"
        const val RESULT_KEY = "vehicle_options_action"
        const val RESULT_ACTION = "action"
        const val RESULT_ORIGINAL_VEHICLE_NUMBER = "original_vehicle_number"
        const val RESULT_NEW_VEHICLE_NUMBER = "new_vehicle_number"
        const val ACTION_EDIT = "edit"
        const val ACTION_MAKE_DEFAULT = "make_default"
        const val ACTION_DELETE = "delete"

        private const val ARG_VEHICLE_NUMBER = "vehicle_number"
        private const val ARG_EXISTING_VEHICLES = "existing_vehicle_numbers"
        private const val ARG_IS_DEFAULT = "is_default"
        private const val STATE_CURRENT_PAGE = "current_page"
        private const val STATE_ACTION_DELIVERED = "action_delivered"
        private const val STATE_PENDING_ACTION = "pending_action"
        private const val STATE_PENDING_NEW_VEHICLE_NUMBER = "pending_new_vehicle_number"
        private const val PAGE_OPTIONS = "options"
        private const val PAGE_EDIT = "edit"
        private const val PAGE_DELETE = "delete"
        private val VALID_PAGES = setOf(PAGE_OPTIONS, PAGE_EDIT, PAGE_DELETE)
        private val VALID_ACTIONS = setOf(ACTION_EDIT, ACTION_MAKE_DEFAULT, ACTION_DELETE)

        fun newInstance(
            vehicleNumber: String,
            existingVehicleNumbers: List<String>,
            isDefault: Boolean,
        ): VehicleOptionsBottomSheet = VehicleOptionsBottomSheet().apply {
            arguments = Bundle().apply {
                putString(ARG_VEHICLE_NUMBER, vehicleNumber)
                putStringArrayList(ARG_EXISTING_VEHICLES, ArrayList(existingVehicleNumbers))
                putBoolean(ARG_IS_DEFAULT, isDefault)
            }
        }
    }
}
