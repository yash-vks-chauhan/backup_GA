package com.gridee.parking.ui.bottomsheet

import android.app.Dialog
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.AnimationUtils
import androidx.core.view.WindowCompat
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.gridee.parking.R
import com.gridee.parking.data.model.Vehicle
import com.gridee.parking.databinding.BottomSheetSelectVehicleBinding
import com.gridee.parking.ui.booking.VehicleSelectionAdapter
import java.util.UUID

/**
 * Vehicle picker owned by [ParkingSpotBottomSheet]. All state needed to recreate the sheet is
 * stored in [arguments]. Selection and add actions use Fragment results, so neither callback is
 * held in a constructor field and an undelivered result can survive parent recreation.
 */
class SelectVehicleBottomSheet : BottomSheetDialogFragment() {

    private var _binding: BottomSheetSelectVehicleBinding? = null
    private val currentVehicles: MutableList<Vehicle> = mutableListOf()

    private var selectedVehicleId: String? = null
    private var selectionRequestToken: String? = null
    private var currentPage = SelectVehicleRestorationPolicy.Page.SELECT
    private var selectionDelivered = false
    private var activeAddRequestToken: String? = null
    private var activeAddVehicleNumber: String? = null
    private var completedAddRequestToken: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setStyle(STYLE_NORMAL, R.style.BottomSheetDialogTheme)

        val savedVehicleIds = savedInstanceState?.getStringArrayList(STATE_VEHICLE_IDS)
        val savedVehicleNumbers = savedInstanceState?.getStringArrayList(STATE_VEHICLE_NUMBERS)
        val hasSavedVehicleSnapshot =
            savedInstanceState?.containsKey(STATE_VEHICLE_IDS) == true ||
                savedInstanceState?.containsKey(STATE_VEHICLE_NUMBERS) == true
        val vehicleIds = if (hasSavedVehicleSnapshot) {
            savedVehicleIds
        } else {
            arguments?.getStringArrayList(ARG_VEHICLE_IDS)
        }
        val vehicleNumbers = if (hasSavedVehicleSnapshot) {
            savedVehicleNumbers
        } else {
            arguments?.getStringArrayList(ARG_VEHICLE_NUMBERS)
        }
        selectedVehicleId = if (savedInstanceState?.containsKey(STATE_SELECTED_VEHICLE_ID) == true) {
            savedInstanceState.getString(STATE_SELECTED_VEHICLE_ID)
        } else {
            arguments?.getString(ARG_SELECTED_VEHICLE_ID)
        }
        selectionRequestToken = if (
            savedInstanceState?.containsKey(STATE_SELECTION_REQUEST_TOKEN) == true
        ) {
            savedInstanceState.getString(STATE_SELECTION_REQUEST_TOKEN)
        } else {
            arguments?.getString(ARG_SELECTION_REQUEST_TOKEN)
        }?.takeIf { it.isNotBlank() }
        selectionDelivered = savedInstanceState?.getBoolean(STATE_SELECTION_DELIVERED) ?: false
        activeAddRequestToken = savedInstanceState?.getString(STATE_ACTIVE_ADD_REQUEST_TOKEN)
            ?.takeIf { it.isNotBlank() }
        activeAddVehicleNumber = savedInstanceState?.getString(STATE_ACTIVE_ADD_VEHICLE_NUMBER)
            ?.takeIf { it.isNotBlank() }
        completedAddRequestToken = savedInstanceState?.getString(STATE_COMPLETED_ADD_REQUEST_TOKEN)
            ?.takeIf { it.isNotBlank() }
        if (activeAddRequestToken == null || activeAddVehicleNumber == null) {
            activeAddRequestToken = null
            activeAddVehicleNumber = null
        }
        currentVehicles.clear()
        SelectVehicleRestorationPolicy.pairSnapshots(
            ids = vehicleIds,
            numbers = vehicleNumbers,
        ).forEachIndexed { index, (id, number) ->
            currentVehicles += Vehicle(
                id = id,
                number = number,
                type = "Car",
                brand = "User",
                model = "Vehicle",
                isDefault = index == 0,
            )
        }

        if (savedInstanceState != null) {
            currentPage = SelectVehicleRestorationPolicy.restorePage(
                savedInstanceState.getString(STATE_PAGE),
            )
        }

        parentFragmentManager.setFragmentResultListener(
            RESULT_KEY_ADD_VEHICLE,
            this,
        ) { _, result -> handleAddVehicleResult(result) }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putString(STATE_PAGE, currentPage.name)
        outState.putStringArrayList(
            STATE_VEHICLE_IDS,
            ArrayList(currentVehicles.map(Vehicle::id)),
        )
        outState.putStringArrayList(
            STATE_VEHICLE_NUMBERS,
            ArrayList(currentVehicles.map(Vehicle::number)),
        )
        outState.putString(STATE_SELECTED_VEHICLE_ID, selectedVehicleId)
        outState.putString(STATE_SELECTION_REQUEST_TOKEN, selectionRequestToken)
        outState.putBoolean(STATE_SELECTION_DELIVERED, selectionDelivered)
        outState.putString(STATE_ACTIVE_ADD_REQUEST_TOKEN, activeAddRequestToken)
        outState.putString(STATE_ACTIVE_ADD_VEHICLE_NUMBER, activeAddVehicleNumber)
        outState.putString(STATE_COMPLETED_ADD_REQUEST_TOKEN, completedAddRequestToken)
        super.onSaveInstanceState(outState)
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val dialog = super.onCreateDialog(savedInstanceState)
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
            }

            bottomSheetDialog.behavior.isGestureInsetBottomIgnored = true
            bottomSheetDialog.behavior.state =
                com.google.android.material.bottomsheet.BottomSheetBehavior.STATE_EXPANDED
            bottomSheetDialog.behavior.skipCollapsed = true

            bottomSheetDialog.window?.let { window ->
                WindowCompat.setDecorFitsSystemWindows(window, false)

                val wic = WindowCompat.getInsetsController(window, window.decorView)
                wic.isAppearanceLightNavigationBars = true

                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                    window.isNavigationBarContrastEnforced = false
                }

                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                    window.attributes.blurBehindRadius = 50
                    window.attributes = window.attributes
                }
            }
        }
        return dialog
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        val viewBinding = BottomSheetSelectVehicleBinding.inflate(inflater, container, false)
        _binding = viewBinding
        return viewBinding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val viewBinding = _binding ?: return
        if (selectionDelivered) {
            dismissSafely()
            return
        }

        setupVehicleList(viewBinding)
        setupClickListeners(viewBinding)
        renderPage(viewBinding)
        renderAddLoadingState(viewBinding, isLoading = activeAddRequestToken != null)

        viewBinding.etVehicleNumber.setOnFocusChangeListener { _, hasFocus ->
            if (hasFocus) {
                _binding?.tilVehicleNumber?.error = null
            }
        }

        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
            viewBinding.root.outlineAmbientShadowColor = android.graphics.Color.parseColor("#40000000")
            viewBinding.root.outlineSpotShadowColor = android.graphics.Color.parseColor("#40000000")
        }
    }

    private fun setupVehicleList(viewBinding: BottomSheetSelectVehicleBinding) {
        if (currentVehicles.isEmpty()) {
            viewBinding.emptyStateContainer.visibility = View.VISIBLE
            viewBinding.rvVehicles.visibility = View.GONE
        } else {
            viewBinding.emptyStateContainer.visibility = View.GONE
            viewBinding.rvVehicles.visibility = View.VISIBLE
        }

        val adapter = VehicleSelectionAdapter(currentVehicles) { vehicle ->
            deliverVehicleSelection(vehicle)
        }

        viewBinding.rvVehicles.layoutManager = LinearLayoutManager(viewBinding.root.context)
        viewBinding.rvVehicles.adapter = adapter

        selectedVehicleId?.let { currentId ->
            val position = currentVehicles.indexOfFirst { it.id == currentId }
            if (position >= 0) {
                adapter.setSelectedPosition(position)
            }
        }
    }

    private fun deliverVehicleSelection(vehicle: Vehicle) {
        if (selectionDelivered) return
        val requestToken = selectionRequestToken ?: return
        val fragmentManager = parentFragmentManager
        if (fragmentManager.isDestroyed || fragmentManager.isStateSaved) return

        selectionDelivered = true
        fragmentManager.setFragmentResult(
            RESULT_KEY_VEHICLE_SELECTION,
            Bundle().apply {
                putString(BUNDLE_SELECTION_REQUEST_TOKEN, requestToken)
                putString(BUNDLE_SELECTED_VEHICLE_ID, vehicle.id)
                putString(BUNDLE_SELECTED_VEHICLE_NUMBER, vehicle.number)
            },
        )
        dismissSafely()
    }

    private fun setupClickListeners(viewBinding: BottomSheetSelectVehicleBinding) {
        viewBinding.btnClose.setOnClickListener { dismissSafely() }

        viewBinding.btnAddNewVehicle.setOnClickListener {
            it.performHapticFeedback(android.view.HapticFeedbackConstants.VIRTUAL_KEY)
            navigateToAddPage()
        }

        viewBinding.btnBack.setOnClickListener {
            it.performHapticFeedback(android.view.HapticFeedbackConstants.VIRTUAL_KEY)
            navigateToSelectPage()
        }

        viewBinding.btnAddVehicle.setOnClickListener {
            it.performHapticFeedback(android.view.HapticFeedbackConstants.VIRTUAL_KEY)
            attemptAddVehicle()
        }
    }

    // --- Page navigation ---

    private fun renderPage(viewBinding: BottomSheetSelectVehicleBinding) {
        viewBinding.btnClose.animate().cancel()
        viewBinding.btnBack.animate().cancel()
        viewBinding.viewFlipper.inAnimation = null
        viewBinding.viewFlipper.outAnimation = null

        val showingAddPage = currentPage == SelectVehicleRestorationPolicy.Page.ADD
        viewBinding.viewFlipper.displayedChild = if (showingAddPage) ADD_PAGE_INDEX else SELECT_PAGE_INDEX
        viewBinding.btnClose.visibility = if (showingAddPage) View.GONE else View.VISIBLE
        viewBinding.btnClose.alpha = if (showingAddPage) 0f else 1f
        viewBinding.btnBack.visibility = if (showingAddPage) View.VISIBLE else View.GONE
        viewBinding.btnBack.alpha = if (showingAddPage) 1f else 0f
    }

    private fun navigateToAddPage() {
        currentPage = SelectVehicleRestorationPolicy.Page.ADD
        val viewBinding = _binding ?: return

        viewBinding.etVehicleNumber.text?.clear()
        viewBinding.tilVehicleNumber.error = null

        // Swap header buttons: hide close, show back
        viewBinding.btnClose.animate().alpha(0f).setDuration(150).withEndAction {
            if (_binding === viewBinding) {
                viewBinding.btnClose.visibility = View.GONE
            }
        }.start()
        viewBinding.btnBack.visibility = View.VISIBLE
        viewBinding.btnBack.alpha = 0f
        viewBinding.btnBack.animate().alpha(1f).setDuration(200).setStartDelay(80).start()

        // Slide forward
        viewBinding.viewFlipper.inAnimation =
            AnimationUtils.loadAnimation(viewBinding.root.context, R.anim.sheet_page_in_right)
        viewBinding.viewFlipper.outAnimation =
            AnimationUtils.loadAnimation(viewBinding.root.context, R.anim.sheet_page_out_left)
        viewBinding.viewFlipper.displayedChild = ADD_PAGE_INDEX
    }

    private fun navigateToSelectPage() {
        currentPage = SelectVehicleRestorationPolicy.Page.SELECT
        val viewBinding = _binding ?: return
        hideKeyboard(viewBinding)

        // Swap header buttons: show close, hide back
        viewBinding.btnBack.animate().alpha(0f).setDuration(150).withEndAction {
            if (_binding === viewBinding) {
                viewBinding.btnBack.visibility = View.GONE
            }
        }.start()
        viewBinding.btnClose.visibility = View.VISIBLE
        viewBinding.btnClose.alpha = 0f
        viewBinding.btnClose.animate().alpha(1f).setDuration(200).setStartDelay(80).start()

        // Slide back
        viewBinding.viewFlipper.inAnimation =
            AnimationUtils.loadAnimation(viewBinding.root.context, R.anim.sheet_page_in_left)
        viewBinding.viewFlipper.outAnimation =
            AnimationUtils.loadAnimation(viewBinding.root.context, R.anim.sheet_page_out_right)
        viewBinding.viewFlipper.displayedChild = SELECT_PAGE_INDEX
    }

    // --- Add vehicle ---

    private fun attemptAddVehicle() {
        if (activeAddRequestToken != null) return
        val viewBinding = _binding ?: return
        val vehicleNumber = com.gridee.parking.utils.VehicleNumberValidator.normalize(
            viewBinding.etVehicleNumber.text.toString(),
        )

        val error = com.gridee.parking.utils.VehicleNumberValidator.getError(vehicleNumber)
        if (error != null) {
            viewBinding.tilVehicleNumber.error = error
            return
        }

        if (
            com.gridee.parking.utils.VehicleNumberValidator.containsEquivalent(
                currentVehicles.map { it.number },
                vehicleNumber,
            )
        ) {
            viewBinding.tilVehicleNumber.error = "Vehicle number already exists"
            return
        }
        viewBinding.tilVehicleNumber.error = null

        if (!isAdded || parentFragmentManager.isDestroyed) {
            viewBinding.tilVehicleNumber.error = "Unable to add vehicle. Please try again."
            return
        }

        val requestToken = UUID.randomUUID().toString()
        activeAddRequestToken = requestToken
        activeAddVehicleNumber = vehicleNumber
        renderAddLoadingState(viewBinding, isLoading = true)

        parentFragmentManager.setFragmentResult(
            REQUEST_KEY_ADD_VEHICLE,
            Bundle().apply {
                putString(BUNDLE_REQUEST_TOKEN, requestToken)
                putString(BUNDLE_VEHICLE_NUMBER, vehicleNumber)
            },
        )
    }

    private fun handleAddVehicleResult(result: Bundle) {
        val requestToken = result.getString(BUNDLE_REQUEST_TOKEN).orEmpty()
        val vehicleNumber = result.getString(BUNDLE_VEHICLE_NUMBER).orEmpty()
        val success = result.getBoolean(BUNDLE_SUCCESS)
        if (!SelectVehicleRestorationPolicy.shouldAcceptAddResult(
                activeRequestToken = activeAddRequestToken,
                activeVehicleNumber = activeAddVehicleNumber,
                completedRequestToken = completedAddRequestToken,
                resultRequestToken = requestToken,
                resultVehicleNumber = vehicleNumber,
            )
        ) {
            return
        }

        if (Looper.myLooper() == Looper.getMainLooper()) {
            completeAddRequest(requestToken, vehicleNumber, success)
        } else {
            Handler(Looper.getMainLooper()).post {
                completeAddRequest(requestToken, vehicleNumber, success)
            }
        }
    }

    private fun completeAddRequest(requestToken: String, vehicleNumber: String, success: Boolean) {
        // Re-check on the main thread: a newer result may have completed while this one was queued.
        if (!SelectVehicleRestorationPolicy.shouldAcceptAddResult(
                activeRequestToken = activeAddRequestToken,
                activeVehicleNumber = activeAddVehicleNumber,
                completedRequestToken = completedAddRequestToken,
                resultRequestToken = requestToken,
                resultVehicleNumber = vehicleNumber,
            )
        ) {
            return
        }
        activeAddRequestToken = null
        activeAddVehicleNumber = null
        completedAddRequestToken = requestToken

        if (success) {
            if (
                !com.gridee.parking.utils.VehicleNumberValidator.containsEquivalent(
                    currentVehicles.map { it.number },
                    vehicleNumber,
                )
            ) {
                currentVehicles += Vehicle(
                    id = "user_vehicle_${currentVehicles.size}",
                    number = vehicleNumber,
                    type = "Car",
                    brand = "User",
                    model = "Vehicle",
                    isDefault = currentVehicles.isEmpty(),
                )
            }
            currentPage = SelectVehicleRestorationPolicy.Page.SELECT
        }

        // The request may finish while the dialog's view is being recreated or after dismissal.
        // State above remains valid, but no stale view binding is ever dereferenced.
        val viewBinding = _binding ?: return
        renderAddLoadingState(viewBinding, isLoading = false)

        if (success) {
            hideKeyboard(viewBinding)
            showAddSuccessNotification(viewBinding, vehicleNumber)
            setupVehicleList(viewBinding)
            navigateToSelectPage()
        } else {
            viewBinding.tilVehicleNumber.error = "Failed to add vehicle. Please try again."
        }
    }

    private fun renderAddLoadingState(
        viewBinding: BottomSheetSelectVehicleBinding,
        isLoading: Boolean,
    ) {
        viewBinding.btnAddVehicle.isEnabled = !isLoading
        viewBinding.btnAddVehicle.text = if (isLoading) "" else getString(R.string.add_vehicle)
        viewBinding.btnBack.isEnabled = !isLoading
    }

    private fun showAddSuccessNotification(
        viewBinding: BottomSheetSelectVehicleBinding,
        vehicleNumber: String,
    ) {
        val hostActivity = activity ?: return
        val parentView = hostActivity.findViewById<ViewGroup>(R.id.fragment_container)
            ?: hostActivity.window.decorView as? ViewGroup
            ?: viewBinding.root
        com.gridee.parking.utils.NotificationHelper.showSuccess(
            parent = parentView,
            message = "Vehicle $vehicleNumber added successfully",
            duration = 3000L,
        )
    }

    private fun hideKeyboard(viewBinding: BottomSheetSelectVehicleBinding) {
        val currentContext = context ?: return
        val imm = currentContext.getSystemService(android.content.Context.INPUT_METHOD_SERVICE)
            as android.view.inputmethod.InputMethodManager
        viewBinding.etVehicleNumber.clearFocus()
        imm.hideSoftInputFromWindow(viewBinding.etVehicleNumber.windowToken, 0)
    }

    private fun dismissSafely() {
        if (!isAdded) return
        if (parentFragmentManager.isStateSaved) {
            dismissAllowingStateLoss()
        } else {
            dismiss()
        }
    }

    override fun onDestroyView() {
        val oldBinding = _binding
        _binding = null
        oldBinding?.btnClose?.animate()?.cancel()
        oldBinding?.btnBack?.animate()?.cancel()
        oldBinding?.etVehicleNumber?.onFocusChangeListener = null
        oldBinding?.rvVehicles?.adapter = null
        super.onDestroyView()
    }

    companion object {
        const val TAG = "SelectVehicleBottomSheet"

        private const val ARG_VEHICLE_IDS = "select_vehicle.ids"
        private const val ARG_VEHICLE_NUMBERS = "select_vehicle.numbers"
        private const val ARG_SELECTED_VEHICLE_ID = "select_vehicle.selected_id"
        private const val ARG_SELECTION_REQUEST_TOKEN = "select_vehicle.selection.request_token"
        private const val STATE_PAGE = "select_vehicle.page"
        private const val STATE_VEHICLE_IDS = "select_vehicle.state.ids"
        private const val STATE_VEHICLE_NUMBERS = "select_vehicle.state.numbers"
        private const val STATE_SELECTED_VEHICLE_ID = "select_vehicle.state.selected_id"
        private const val STATE_SELECTION_REQUEST_TOKEN =
            "select_vehicle.state.selection.request_token"
        private const val STATE_SELECTION_DELIVERED = "select_vehicle.state.selection_delivered"
        private const val STATE_ACTIVE_ADD_REQUEST_TOKEN = "select_vehicle.state.add.token"
        private const val STATE_ACTIVE_ADD_VEHICLE_NUMBER = "select_vehicle.state.add.vehicle_number"
        private const val STATE_COMPLETED_ADD_REQUEST_TOKEN = "select_vehicle.state.add.completed_token"
        private const val SELECT_PAGE_INDEX = 0
        private const val ADD_PAGE_INDEX = 1

        const val REQUEST_KEY_ADD_VEHICLE = "select_vehicle.add.request"
        const val RESULT_KEY_ADD_VEHICLE = "select_vehicle.add.result"
        const val BUNDLE_REQUEST_TOKEN = "select_vehicle.add.token"
        const val BUNDLE_VEHICLE_NUMBER = "select_vehicle.add.vehicle_number"
        const val BUNDLE_SUCCESS = "select_vehicle.add.success"

        const val RESULT_KEY_VEHICLE_SELECTION = "select_vehicle.selection.result"
        const val BUNDLE_SELECTION_REQUEST_TOKEN = "select_vehicle.selection.request_token"
        const val BUNDLE_SELECTED_VEHICLE_ID = "select_vehicle.selection.vehicle_id"
        const val BUNDLE_SELECTED_VEHICLE_NUMBER = "select_vehicle.selection.vehicle_number"

        fun newInstance(
            vehicles: List<Vehicle>,
            selectedVehicleId: String?,
            selectionRequestToken: String,
        ): SelectVehicleBottomSheet = SelectVehicleBottomSheet().apply {
            arguments = Bundle().apply {
                putStringArrayList(ARG_VEHICLE_IDS, ArrayList(vehicles.map(Vehicle::id)))
                putStringArrayList(ARG_VEHICLE_NUMBERS, ArrayList(vehicles.map(Vehicle::number)))
                putString(ARG_SELECTED_VEHICLE_ID, selectedVehicleId)
                putString(ARG_SELECTION_REQUEST_TOKEN, selectionRequestToken)
            }
        }
    }
}

internal object SelectVehicleRestorationPolicy {
    enum class Page {
        SELECT,
        ADD,
    }

    fun restorePage(savedPage: String?): Page =
        Page.values().firstOrNull { it.name == savedPage } ?: Page.SELECT

    fun pairSnapshots(
        ids: List<String>?,
        numbers: List<String>?,
    ): List<Pair<String, String>> = ids.orEmpty().zip(numbers.orEmpty())

    fun shouldAcceptAddResult(
        activeRequestToken: String?,
        activeVehicleNumber: String?,
        completedRequestToken: String?,
        resultRequestToken: String?,
        resultVehicleNumber: String?,
    ): Boolean =
        !resultRequestToken.isNullOrBlank() &&
            resultRequestToken == activeRequestToken &&
            resultRequestToken != completedRequestToken &&
            !resultVehicleNumber.isNullOrBlank() &&
            com.gridee.parking.utils.VehicleNumberValidator.areEquivalent(
                activeVehicleNumber.orEmpty(),
                resultVehicleNumber,
            )
}
