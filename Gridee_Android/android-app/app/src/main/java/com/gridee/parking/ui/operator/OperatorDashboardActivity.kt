package com.gridee.parking.ui.operator

import android.Manifest
import android.animation.ValueAnimator
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.graphics.Typeface
import android.os.Build
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.util.TypedValue
import android.view.HapticFeedbackConstants
import android.view.View
import android.view.ViewGroup
import android.view.animation.DecelerateInterpolator
import android.view.animation.OvershootInterpolator
import android.view.inputmethod.InputMethodManager
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.content.getSystemService
import androidx.core.content.res.ResourcesCompat
import androidx.core.view.GravityCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.dynamicanimation.animation.DynamicAnimation
import androidx.dynamicanimation.animation.SpringAnimation
import androidx.dynamicanimation.animation.SpringForce
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.gridee.parking.R
import com.gridee.parking.data.model.ParkingSpot
import com.gridee.parking.data.repository.ParkingRepository
import com.gridee.parking.databinding.ActivityOperatorDashboardBinding
import com.gridee.parking.databinding.BottomSheetOperatorSpotSelectionBinding
import com.gridee.parking.ui.auth.LoginActivity
import com.gridee.parking.ui.bottomsheet.LogoutConfirmationBottomSheet
import com.gridee.parking.utils.AppLocaleManager
import com.gridee.parking.ui.booking.ParkingSpotSelectionAdapter
import com.gridee.parking.ui.qr.QrScannerActivity
import com.gridee.parking.utils.AuthSession
import com.gridee.parking.utils.InAppUpdateController
import com.gridee.parking.utils.NotificationHelper
import com.gridee.parking.utils.VehicleNumberValidator
import kotlinx.coroutines.launch

/**
 * Dashboard for parking lot operators
 * Ultra-clean minimal design with progressive disclosure
 * Features single-action scan mode + hidden manual entry
 */
class OperatorDashboardActivity : AppCompatActivity() {

    private lateinit var binding: ActivityOperatorDashboardBinding
    private val viewModel: OperatorViewModel by viewModels()
    private var currentMode = OperatorMode.CHECK_IN
    private var isManualEntryMode = false
    private var inAppUpdateController: InAppUpdateController? = null
    private val parkingRepository = ParkingRepository()
    private var selectedSpot: ParkingSpot? = null
    private var selectedSpotId: String? = null
    private var selectedParkingLotId: String? = null
    private var spotSelectionDialog: BottomSheetDialog? = null

    // Animation properties
    private var typefaceBold: Typeface? = null
    private var typefaceMedium: Typeface? = null
    private val vibrator: Vibrator? by lazy { getSystemService<Vibrator>() }
    
    // Tab state tracking
    private var isScannerTabActive = true

    companion object {
        private const val CAMERA_PERMISSION_REQUEST = 100
        private const val ANIM_DURATION = 200L
        private const val DEFAULT_OPERATOR_PLATE_PREFIX = "TN"
        private const val STATE_SELECTED_SPOT_ID = "state_selected_spot_id"
        private const val STATE_SELECTED_SPOT_NAME = "state_selected_spot_name"
        private const val PREF_LAST_SELECTED_SPOT_ID = "operator_last_selected_spot_id"
        private const val PREF_LAST_SELECTED_SPOT_NAME = "operator_last_selected_spot_name"
    }

    enum class OperatorMode {
        CHECK_IN, CHECK_OUT
    }

    private val vehicleScannerLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        syncSelectedSpotFromScanner(
            spotId = result.data?.getStringExtra(QrScannerActivity.EXTRA_PARKING_SPOT_ID),
            spotDisplayName = result.data?.getStringExtra(QrScannerActivity.EXTRA_PARKING_SPOT_NAME),
            parkingLotId = result.data?.getStringExtra(QrScannerActivity.EXTRA_PARKING_LOT_ID)
        )

        val returnedMode = mapScanTypeToOperatorMode(
            result.data?.getStringExtra(QrScannerActivity.EXTRA_SCAN_TYPE)
        )
        returnedMode?.let(::syncModeFromScanner)

        if (result.resultCode == QrScannerActivity.RESULT_QR_SCANNED) {
            val scannedData = result.data?.getStringExtra(QrScannerActivity.EXTRA_QR_CODE)
            scannedData?.let { vehicleNumber ->
                val spotId = selectedSpotId
                if (spotId.isNullOrBlank()) {
                    showSpotSelectionSheet()
                    showToast(getString(R.string.op_select_spot_required))
                    return@let
                }
                // Auto-process based on current mode
                when (returnedMode ?: currentMode) {
                    OperatorMode.CHECK_IN -> viewModel.checkInByVehicleNumber(
                        vehicleNumber,
                        spotId,
                        parkingLotId = currentOperatorParkingLotId()
                    )
                    OperatorMode.CHECK_OUT -> viewModel.checkOutByVehicleNumber(
                        vehicleNumber,
                        spotId,
                        parkingLotId = currentOperatorParkingLotId()
                    )
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        loadLocale()
        super.onCreate(savedInstanceState)

        if (!AuthSession.isAuthenticated(this)) {
            val intent = Intent(this, LoginActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            intent.putExtra(LoginActivity.EXTRA_FORCE_LOGIN, true)
            startActivity(intent)
            finish()
            return
        }
        AuthSession.syncLegacyPrefsFromJwt(this)

        binding = ActivityOperatorDashboardBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // In-app update prompt for operator users as well.
        inAppUpdateController = InAppUpdateController(
            activity = this,
            snackbarAnchorView = binding.root,
        ).also { it.checkForUpdates() }

        // Set status bar to match background
        window.statusBarColor = ContextCompat.getColor(this, android.R.color.transparent)
        window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR

        restoreSelectedSpotState(savedInstanceState)
        loadFonts()
        setupUI()
        ensureManualVehiclePrefixDefault()
        setupBottomNavigation()
        setupSegmentedControl()
        observeViewModel()
        setupWindowInsets()
        showSpotSelectionSheet()

        // Initialize with Check-In mode
        updateUIForMode(OperatorMode.CHECK_IN, animate = false)
        
        // Initialize Tab State (Scanner default)
        // Use post to ensure views are measured for initial spring setup if needed
        binding.root.post {
            setActiveTab(isScanner = true, animate = false)
        }
    }

    private fun loadFonts() {
        try {
            typefaceBold = ResourcesCompat.getFont(this, R.font.inter_bold)
            typefaceMedium = ResourcesCompat.getFont(this, R.font.inter_semibold)
        } catch (e: Exception) {
            Log.e("OperatorDashboard", "Error loading fonts", e)
            // Fallback to system fonts if resources fail
            typefaceBold = Typeface.DEFAULT_BOLD
            typefaceMedium = Typeface.DEFAULT
        }
    }

    private fun setupWindowInsets() {
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { view, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            val navigationBars = insets.getInsets(WindowInsetsCompat.Type.navigationBars())
            
            // Apply top padding to Header Row for Status Bar
            binding.headerRow.setPadding(
                binding.headerRow.paddingLeft,
                systemBars.top,
                binding.headerRow.paddingRight,
                binding.headerRow.paddingBottom
            )
            
            // Apply bottom padding to Navigation Container for Gesture/Nav Bar
            val navContainer = binding.navContainer
            
            // Copied logic from CustomBottomNavigation.kt
            val bottomPadding = Math.max(navigationBars.bottom, 20)
            val topPadding = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 10f, resources.displayMetrics).toInt()
            
            navContainer.setPadding(
                navContainer.paddingLeft,
                topPadding,
                navContainer.paddingRight,
                bottomPadding + 4
            )
            
            insets
        }
    }

    override fun onResume() {
        super.onResume()
        inAppUpdateController?.onResume()
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        inAppUpdateController?.onActivityResult(requestCode, resultCode, data)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        val spotId = selectedSpotId?.trim()
        if (!spotId.isNullOrEmpty()) {
            outState.putString(STATE_SELECTED_SPOT_ID, spotId)
            outState.putString(STATE_SELECTED_SPOT_NAME, binding.etSpotId.text?.toString().orEmpty())
        }
    }

    override fun onDestroy() {
        inAppUpdateController?.onDestroy()
        inAppUpdateController = null
        spotSelectionDialog?.dismiss()
        spotSelectionDialog = null
        super.onDestroy()
    }

    private fun restoreSelectedSpotState(savedInstanceState: Bundle?) {
        val stateSpotId = savedInstanceState
            ?.getString(STATE_SELECTED_SPOT_ID)
            ?.trim()
            .orEmpty()
        val stateSpotName = savedInstanceState
            ?.getString(STATE_SELECTED_SPOT_NAME)
            ?.trim()
            .orEmpty()
        if (stateSpotId.isNotEmpty()) {
            selectedSpotId = stateSpotId
            updateSelectedSpotUi(if (stateSpotName.isNotEmpty()) stateSpotName else stateSpotId)
            return
        }

        val prefs = getSharedPreferences("gridee_prefs", MODE_PRIVATE)
        val persistedSpotId = prefs.getString(PREF_LAST_SELECTED_SPOT_ID, null)?.trim().orEmpty()
        if (persistedSpotId.isEmpty()) {
            updateSelectedSpotUi(null)
            return
        }

        val persistedSpotName = prefs.getString(PREF_LAST_SELECTED_SPOT_NAME, null)?.trim().orEmpty()
        selectedSpotId = persistedSpotId
        updateSelectedSpotUi(if (persistedSpotName.isNotEmpty()) persistedSpotName else persistedSpotId)
    }

    private fun setupUI() {
        // Menu Button (Opens Drawer)
        // Listener moved to setupNavigationDrawer() to handle toggle logic


        setupNavigationDrawer()

        // Circular Scan Button (Primary CTA)
        binding.btnScanCircular.setOnClickListener {
            it.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
            if (!ensureSpotSelected()) return@setOnClickListener
            animateButtonPress(it) {
                if (checkCameraPermission()) {
                    openVehicleScanner()
                } else {
                    requestCameraPermission()
                }
            }
        }

        binding.linkManualEntry.visibility = View.GONE

        // Back to Scan
        binding.btnBackToScan.setOnClickListener {
            it.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
            switchToScanMode()
        }

        // Submit Manual Button
        binding.btnSubmitManual.setOnClickListener {
            it.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
            if (!ensureSpotSelected()) return@setOnClickListener
            val vehicleNumber = binding.etVehicleNumberClean.text.toString()
            val normalizedVehicle = VehicleNumberValidator.normalize(vehicleNumber)
            val vehicleError = VehicleNumberValidator.getError(vehicleNumber)
            val spotId = selectedSpotId
            
            if (vehicleError != null) {
                binding.etVehicleNumberClean.error = vehicleError
                binding.etVehicleNumberClean.requestFocus()
            } else {
                binding.etVehicleNumberClean.setText(normalizedVehicle)
                binding.etVehicleNumberClean.setSelection(normalizedVehicle.length)
                when (currentMode) {
                    OperatorMode.CHECK_IN -> viewModel.checkInByVehicleNumber(
                        normalizedVehicle,
                        spotId,
                        parkingLotId = currentOperatorParkingLotId()
                    )
                    OperatorMode.CHECK_OUT -> viewModel.checkOutByVehicleNumber(
                        normalizedVehicle,
                        spotId,
                        parkingLotId = currentOperatorParkingLotId()
                    )
                }
            }
        }

        // Shared selected-spot control opens the selection sheet for both scan and manual flows
        binding.cardSelectedSpot.setOnClickListener { showSpotSelectionSheet() }
        binding.etSpotId.setOnClickListener { showSpotSelectionSheet() }

        // Auto-format vehicle number
        binding.etVehicleNumberClean.addTextChangedListener(object : TextWatcher {
            private var isFormatting = false
            
            override fun afterTextChanged(s: Editable?) {
                if (isFormatting) return
                
                isFormatting = true
                val cleaned = VehicleNumberValidator.normalize(s.toString())

                if (cleaned != s.toString()) {
                    s?.replace(0, s.length, cleaned)
                }
                
                isFormatting = false
            }
            
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                binding.etVehicleNumberClean.error = null
            }
        })

        // Pull-to-refresh
        binding.swipeRefresh.setOnRefreshListener {
            binding.swipeRefresh.isRefreshing = false
        }
        binding.swipeRefresh.setColorSchemeColors(
            ContextCompat.getColor(this, android.R.color.black)
        )
    }

    private fun ensureSpotSelected(): Boolean {
        if (!selectedSpotId.isNullOrBlank()) return true
        showSpotSelectionSheet()
        showToast(getString(R.string.op_select_spot_required))
        return false
    }

    private fun showSpotSelectionSheet() {
        if (spotSelectionDialog?.isShowing == true || isFinishing || isDestroyed) return

        val dialog = BottomSheetDialog(this, R.style.BottomSheetDialogTheme)
        spotSelectionDialog = dialog

        val sheetBinding = BottomSheetOperatorSpotSelectionBinding.inflate(layoutInflater)
        dialog.setContentView(sheetBinding.root)
        dialog.setCancelable(false)
        dialog.setCanceledOnTouchOutside(false)
        dialog.setOnShowListener {
            val bottomSheet = dialog.findViewById<FrameLayout>(com.google.android.material.R.id.design_bottom_sheet)
            bottomSheet?.let { sheet ->
                sheet.setBackgroundResource(R.drawable.bg_bottom_sheet_universal)
                val params = sheet.layoutParams as? ViewGroup.MarginLayoutParams
                params?.setMargins(0, 0, 0, 0)
                params?.height = ViewGroup.LayoutParams.MATCH_PARENT
                sheet.layoutParams = params
                sheet.minimumHeight = resources.displayMetrics.heightPixels
                sheet.requestLayout()
            }
            dialog.behavior.peekHeight = resources.displayMetrics.heightPixels
            dialog.behavior.isGestureInsetBottomIgnored = true
            dialog.behavior.skipCollapsed = true
            dialog.behavior.state = com.google.android.material.bottomsheet.BottomSheetBehavior.STATE_EXPANDED
            bottomSheet?.post {
                dialog.behavior.state = com.google.android.material.bottomsheet.BottomSheetBehavior.STATE_EXPANDED
            }
        }
        dialog.setOnDismissListener { spotSelectionDialog = null }

        sheetBinding.btnClose.setOnClickListener {
            dialog.dismiss()
        }

        val adapter = ParkingSpotSelectionAdapter(
            onItemClick = { spot ->
                applySelectedSpot(spot)
                dialog.dismiss()
            },
            allowUnavailableSelection = true
        )

        sheetBinding.rvSpots.layoutManager = LinearLayoutManager(this)
        sheetBinding.rvSpots.adapter = adapter
        if (!selectedSpotId.isNullOrBlank()) {
            adapter.setSelectedSpot(selectedSpotId)
        }

        sheetBinding.progressBar.visibility = View.VISIBLE
        sheetBinding.tvEmptyState.text = getString(R.string.op_select_spot_loading)
        sheetBinding.tvEmptyState.visibility = View.VISIBLE
        sheetBinding.rvSpots.visibility = View.GONE

        dialog.show()

        lifecycleScope.launch {
            val spots = loadAllParkingSpots()
            if (spotSelectionDialog !== dialog) return@launch
            Log.d("OperatorDashboard", "Showing ${spots.size} operator parking spots")
            sheetBinding.progressBar.visibility = View.GONE

            if (spots.isEmpty()) {
                sheetBinding.tvEmptyState.text = getString(R.string.op_select_spot_empty)
                sheetBinding.tvEmptyState.visibility = View.VISIBLE
                sheetBinding.rvSpots.visibility = View.GONE
            } else {
                sheetBinding.tvEmptyState.visibility = View.GONE
                sheetBinding.rvSpots.visibility = View.VISIBLE
                adapter.submitList(spots) {
                    sheetBinding.rvSpots.requestLayout()
                }
                if (!selectedSpotId.isNullOrBlank()) {
                    adapter.setSelectedSpot(selectedSpotId)
                }
            }
        }
    }

    private fun applySelectedSpot(spot: ParkingSpot) {
        selectedSpot = spot
        selectedSpotId = spot.id
        selectedParkingLotId = normalizeLotId(spot.lotId) ?: AuthSession.getParkingLotId(this)
        val spotDisplayName = getSpotDisplayName(spot)
        updateSelectedSpotUi(spotDisplayName)
        getSharedPreferences("gridee_prefs", MODE_PRIVATE)
            .edit()
            .putString(PREF_LAST_SELECTED_SPOT_ID, spot.id)
            .putString(PREF_LAST_SELECTED_SPOT_NAME, spotDisplayName)
            .apply()
    }

    private fun syncSelectedSpotFromScanner(
        spotId: String?,
        spotDisplayName: String?,
        parkingLotId: String?
    ) {
        val normalizedSpotId = spotId?.trim().orEmpty()
        if (normalizedSpotId.isEmpty()) return
        selectedParkingLotId = normalizeLotId(parkingLotId) ?: selectedParkingLotId
        if (selectedSpotId == normalizedSpotId && !spotDisplayName.isNullOrBlank()) {
            updateSelectedSpotUi(spotDisplayName)
            return
        }

        selectedSpotId = normalizedSpotId
        selectedSpot = selectedSpot?.takeIf { it.id == normalizedSpotId }
        val resolvedDisplayName = spotDisplayName?.trim()?.takeIf { it.isNotEmpty() } ?: normalizedSpotId
        updateSelectedSpotUi(resolvedDisplayName)
        getSharedPreferences("gridee_prefs", MODE_PRIVATE)
            .edit()
            .putString(PREF_LAST_SELECTED_SPOT_ID, normalizedSpotId)
            .putString(PREF_LAST_SELECTED_SPOT_NAME, resolvedDisplayName)
            .apply()
    }

    private suspend fun loadAllParkingSpots(): List<ParkingSpot> {
        return OperatorParkingSpotLoader.load(this, parkingRepository, "OperatorDashboard")
    }

    private fun currentOperatorParkingLotId(): String? {
        return normalizeLotId(selectedParkingLotId)
            ?: normalizeLotId(selectedSpot?.lotId)
            ?: AuthSession.getParkingLotId(this)
    }

    private fun normalizeLotId(raw: String?): String? {
        return raw?.trim()?.takeIf { it.isNotEmpty() }
    }

    private fun getSpotDisplayName(spot: ParkingSpot): String {
        return OperatorParkingSpotLoader.getSpotDisplayName(spot)
    }

    private fun updateSelectedSpotUi(spotDisplayName: String?) {
        val hasSelection = !spotDisplayName.isNullOrBlank()
        binding.etSpotId.text = if (hasSelection) {
            spotDisplayName
        } else {
            getString(R.string.op_spot_id_hint)
        }
        binding.etSpotId.setTextColor(
            ContextCompat.getColor(
                this,
                if (hasSelection) R.color.text_primary else R.color.text_secondary
            )
        )
    }

    private fun setupBottomNavigation() {
        binding.tabScanner.setOnClickListener {
            if (!isScannerTabActive) {
                performHapticFeedback()
                setActiveTab(isScanner = true, animate = true)
            }
        }

        binding.tabDashboard.setOnClickListener {
            if (isScannerTabActive) {
                performHapticFeedback()
                setActiveTab(isScanner = false, animate = true)
            }
        }
    }

    private fun performHapticFeedback(heavy: Boolean = false) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            // "Intensity" achieved via distinct constants
            val constant = if (heavy) HapticFeedbackConstants.CONFIRM else HapticFeedbackConstants.CLOCK_TICK
            binding.root.performHapticFeedback(constant)
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            // Manually craft vibration patterns
            val ms = if (heavy) 30L else 10L
            val amp = if (heavy) VibrationEffect.DEFAULT_AMPLITUDE else 60
            vibrator?.vibrate(VibrationEffect.createOneShot(ms, amp))
        } else {
            @Suppress("DEPRECATION")
            vibrator?.vibrate(if (heavy) 30L else 10L)
        }
    }

    private fun setActiveTab(isScanner: Boolean, animate: Boolean) {
        this.isScannerTabActive = isScanner

        if (isScanner) {
            // Show Scanner content
            binding.layoutContentScanner.visibility = View.VISIBLE
            binding.layoutContentDashboard.visibility = View.GONE

            // Animate Tabs
            activateScannerTab(animate)
            deactivateDashboardTab(animate)
        } else {
            // Show Dashboard content
            binding.layoutContentScanner.visibility = View.GONE
            binding.layoutContentDashboard.visibility = View.VISIBLE

            // Animate Tabs
            deactivateScannerTab(animate)
            activateDashboardTab(animate)
        }
    }

    private fun activateScannerTab(animate: Boolean) {
        binding.ivScanner.setImageResource(R.drawable.ic_scanner_filled)
        activateTab(binding.scannerActiveBg, binding.ivScanner, binding.tvScanner, animate)
    }

    private fun deactivateScannerTab(animate: Boolean) {
        binding.ivScanner.setImageResource(R.drawable.ic_scanner_nav)
        deactivateTab(binding.scannerActiveBg, binding.ivScanner, binding.tvScanner, animate)
    }

    private fun activateDashboardTab(animate: Boolean) {
        binding.ivDashboard.setImageResource(R.drawable.ic_dashboard_filled)
        activateTab(binding.dashboardActiveBg, binding.ivDashboard, binding.tvDashboard, animate)
    }

    private fun deactivateDashboardTab(animate: Boolean) {
        binding.ivDashboard.setImageResource(R.drawable.ic_dashboard_nav)
        deactivateTab(binding.dashboardActiveBg, binding.ivDashboard, binding.tvDashboard, animate)
    }

    private fun activateTab(backgroundView: View, imageView: ImageView, textView: TextView, animate: Boolean) {
        if (!animate) {
            // Immediate state set without animation
            backgroundView.visibility = View.VISIBLE
            backgroundView.alpha = 1f
            backgroundView.scaleX = 1f
            backgroundView.scaleY = 1f
            
            val activeColor = ContextCompat.getColor(this, R.color.brand_primary)
            imageView.imageTintList = ColorStateList.valueOf(activeColor)
            
            textView.setTextColor(activeColor)
            textView.typeface = typefaceBold ?: Typeface.DEFAULT_BOLD
            textView.visibility = View.VISIBLE
            return
        }

        // Show background pill with Apple-style Spring Physics
        // "Alive" state: Start physically smaller (0.8x) to allow for the expansion
        backgroundView.visibility = View.VISIBLE
        backgroundView.alpha = 0f
        backgroundView.scaleX = 0.8f
        backgroundView.scaleY = 0.8f
        
        // Background: "Hydraulic" smooth fill (Slow & No Bounce)
        // It grows politely behind the icon
        applySpring(backgroundView, SpringAnimation.ALPHA, 1f, SpringForce.STIFFNESS_VERY_LOW, SpringForce.DAMPING_RATIO_NO_BOUNCY)
        applySpring(backgroundView, SpringAnimation.SCALE_X, 1f, SpringForce.STIFFNESS_VERY_LOW, SpringForce.DAMPING_RATIO_NO_BOUNCY)
        applySpring(backgroundView, SpringAnimation.SCALE_Y, 1f, SpringForce.STIFFNESS_VERY_LOW, SpringForce.DAMPING_RATIO_NO_BOUNCY)
        
        // Switch to filled icons (tint only for now as distinct filled resource unavailable)
        val activeColor = ContextCompat.getColor(this, R.color.brand_primary)
        imageView.imageTintList = ColorStateList.valueOf(activeColor)
        
        // Text is black (active) and Bold
        textView.setTextColor(activeColor)
        textView.typeface = typefaceBold ?: Typeface.DEFAULT_BOLD
        textView.visibility = View.VISIBLE
        
        // Add smooth bounce animation
        animateTabActivation(imageView, textView)
    }

    private fun deactivateTab(backgroundView: View, imageView: ImageView, textView: TextView, animate: Boolean) {
        val inactiveColor = ContextCompat.getColor(this, R.color.charcoal_black)
        
        if (!animate) {
            backgroundView.visibility = View.GONE
            imageView.imageTintList = ColorStateList.valueOf(inactiveColor)
            textView.setTextColor(inactiveColor)
            textView.typeface = typefaceMedium ?: Typeface.DEFAULT
            return
        }

        // Hide background with physics
        applySpring(backgroundView, SpringAnimation.ALPHA, 0f)
        applySpring(backgroundView, SpringAnimation.SCALE_X, 0.9f)
        applySpring(backgroundView, SpringAnimation.SCALE_Y, 0.9f)
        
        // Switch to inactive tint
        imageView.imageTintList = ColorStateList.valueOf(inactiveColor)
        
        textView.setTextColor(inactiveColor)
        textView.typeface = typefaceMedium ?: Typeface.DEFAULT
        textView.visibility = View.VISIBLE
        
        // Add smooth deactivation animation
        animateTabDeactivation(imageView, textView)
    }

    private fun animateTabActivation(imageView: ImageView, textView: TextView) {
        // Icon: Stronger "Heartbeat" Pop
        // Start smaller (0.7f) for more dramatic range -> Pop to 1.15x -> Settle at 1.0f
        imageView.scaleX = 0.7f
        imageView.scaleY = 0.7f
        imageView.alpha = 1.0f
        
        applySpring(imageView, SpringAnimation.SCALE_X, 1f, SpringForce.STIFFNESS_LOW, SpringForce.DAMPING_RATIO_MEDIUM_BOUNCY)
        applySpring(imageView, SpringAnimation.SCALE_Y, 1f, SpringForce.STIFFNESS_LOW, SpringForce.DAMPING_RATIO_MEDIUM_BOUNCY)
        
        // Text: "Sympathetic Resonance"
        textView.scaleX = 0.85f
        textView.scaleY = 0.85f
        textView.alpha = 1.0f
        
        applySpring(textView, SpringAnimation.SCALE_X, 1f, SpringForce.STIFFNESS_MEDIUM, 0.7f) // Custom "Low Bounce"
        applySpring(textView, SpringAnimation.SCALE_Y, 1f, SpringForce.STIFFNESS_MEDIUM, 0.7f)
    }
    
    private fun animateTabDeactivation(imageView: ImageView, textView: TextView) {
        // Relax back to natural state
        applySpring(imageView, SpringAnimation.SCALE_X, 1f)
        applySpring(imageView, SpringAnimation.SCALE_Y, 1f)
        applySpring(imageView, SpringAnimation.ALPHA, 1f)
        
        applySpring(textView, SpringAnimation.SCALE_X, 1f)
        applySpring(textView, SpringAnimation.SCALE_Y, 1f) 
        applySpring(textView, SpringAnimation.ALPHA, 1f)
    }

    private fun applySpring(
        view: View, 
        property: DynamicAnimation.ViewProperty, 
        targetValue: Float,
        stiffness: Float = SpringForce.STIFFNESS_VERY_LOW,
        dampingRatio: Float = SpringForce.DAMPING_RATIO_NO_BOUNCY
    ) {
        val animation = SpringAnimation(view, property)
        animation.spring = SpringForce(targetValue).apply {
            this.stiffness = stiffness
            this.dampingRatio = dampingRatio
        }
        animation.start()
    }

    private fun switchToManualEntry() {
        isManualEntryMode = true
        
        // Fade transition
        binding.layoutScanMode.animate()
            .alpha(0f)
            .setDuration(ANIM_DURATION)
            .withEndAction {
                binding.layoutScanMode.visibility = View.GONE
                binding.layoutManualMode.visibility = View.VISIBLE
                binding.layoutManualMode.alpha = 0f
                binding.layoutManualMode.animate()
                    .alpha(1f)
                    .setDuration(ANIM_DURATION)
                    .start()
                
                ensureManualVehiclePrefixDefault()
                // Focus on input
                binding.etVehicleNumberClean.requestFocus()
                binding.etVehicleNumberClean.setSelection(binding.etVehicleNumberClean.text?.length ?: 0)
                val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
                imm.showSoftInput(binding.etVehicleNumberClean, InputMethodManager.SHOW_IMPLICIT)
            }
            .start()
    }

    private fun switchToScanMode() {
        isManualEntryMode = false
        
        // Hide keyboard
        val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.hideSoftInputFromWindow(binding.etVehicleNumberClean.windowToken, 0)
        
        // Clear input
        binding.etVehicleNumberClean.text?.clear()
        
        // Fade transition
        binding.layoutManualMode.animate()
            .alpha(0f)
            .setDuration(ANIM_DURATION)
            .withEndAction {
                binding.layoutManualMode.visibility = View.GONE
                binding.layoutScanMode.visibility = View.VISIBLE
                binding.layoutScanMode.alpha = 0f
                binding.layoutScanMode.animate()
                    .alpha(1f)
                    .setDuration(ANIM_DURATION)
                    .start()
            }
            .start()
    }

    private fun setupSegmentedControl() {
        // Initialize visuals perfectly
        updateSegmentVisuals(currentMode)

        // Make slider visible after layout
        binding.segmentSlider.post {
            binding.segmentSlider.visibility = View.VISIBLE
            
            // Set initial position for Check-In (left segment)
            val params = binding.segmentSlider.layoutParams
            params.width = binding.segmentCheckin.width
            binding.segmentSlider.layoutParams = params
            binding.segmentSlider.translationX = 0f
        }

        // Check-In Segment Click
        binding.segmentCheckin.setOnClickListener {
            it.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
            if (currentMode != OperatorMode.CHECK_IN) {
                switchToMode(OperatorMode.CHECK_IN)
            }
        }

        // Check-Out Segment Click
        binding.segmentCheckout.setOnClickListener {
            it.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
            if (currentMode != OperatorMode.CHECK_OUT) {
                switchToMode(OperatorMode.CHECK_OUT)
            }
        }
    }

    private fun switchToMode(mode: OperatorMode) {
        if (currentMode == mode) return
        currentMode = mode
        
        // 1. Audio Cue: Professional System Click
        binding.root.playSoundEffect(android.view.SoundEffectConstants.CLICK)
        
        // 2. Light Haptic: "Initiate"
        performHapticFeedback(heavy = false)
        
        val targetSegment = when (mode) {
            OperatorMode.CHECK_IN -> binding.segmentCheckin
            OperatorMode.CHECK_OUT -> binding.segmentCheckout
        }

        val targetX = targetSegment.left.toFloat()
        
        val slider = binding.segmentSlider
        val animation = SpringAnimation(slider, DynamicAnimation.TRANSLATION_X, targetX).apply {
            spring = SpringForce(targetX).apply {
                stiffness = SpringForce.STIFFNESS_LOW
                dampingRatio = SpringForce.DAMPING_RATIO_NO_BOUNCY
            }
            // 3. Heavy Haptic: "Settle/Confirm" (Intensity)
            addEndListener { _, _, _, _ ->
                performHapticFeedback(heavy = true)
            }
        }
        animation.start()
        
        // 4. Update Visuals (Text Color + Weight)
        updateSegmentVisuals(mode)
        
        updateUIForMode(mode, animate = true)
    }

    private fun syncModeFromScanner(mode: OperatorMode) {
        if (currentMode == mode) return
        currentMode = mode

        val targetSegment = when (mode) {
            OperatorMode.CHECK_IN -> binding.segmentCheckin
            OperatorMode.CHECK_OUT -> binding.segmentCheckout
        }

        binding.segmentSlider.post {
            val slider = binding.segmentSlider
            val params = slider.layoutParams
            params.width = targetSegment.width
            slider.layoutParams = params
            slider.visibility = View.VISIBLE
            slider.translationX = targetSegment.left.toFloat()
        }

        updateSegmentVisuals(mode)
        updateUIForMode(mode, animate = false)
    }

    private fun updateUIForMode(mode: OperatorMode, animate: Boolean) {
        
        // Update submit button text
        val submitText = when (mode) {
            OperatorMode.CHECK_IN -> getString(R.string.op_btn_check_in)
            OperatorMode.CHECK_OUT -> getString(R.string.op_btn_check_out)
        }
        binding.btnSubmitManual.text = submitText


        
        // If in manual mode, clear input
        if (isManualEntryMode) {
            binding.etVehicleNumberClean.text?.clear()
            ensureManualVehiclePrefixDefault()
        }
    }

    private fun ensureManualVehiclePrefixDefault() {
        val currentValue = binding.etVehicleNumberClean.text?.toString().orEmpty()
        if (currentValue.isBlank()) {
            binding.etVehicleNumberClean.setText(DEFAULT_OPERATOR_PLATE_PREFIX)
            binding.etVehicleNumberClean.setSelection(DEFAULT_OPERATOR_PLATE_PREFIX.length)
        }
    }

    private fun animateButtonPress(view: View, action: () -> Unit) {
        view.animate()
            .scaleX(0.92f)
            .scaleY(0.92f)
            .setDuration(100)
            .withEndAction {
                view.animate()
                    .scaleX(1f)
                    .scaleY(1f)
                    .setDuration(100)
                    .withEndAction { action() }
                    .start()
            }
            .start()
    }

    private fun setupNavigationDrawer() {
        // Set Header Profile Initials
        val sharedPref = getSharedPreferences("gridee_prefs", MODE_PRIVATE)
        val operatorName = sharedPref.getString("user_name", "Operator")
        
        val initials = if (operatorName.isNullOrBlank()) "O" else operatorName.first().toString().uppercase()
        binding.root.findViewById<TextView>(R.id.tv_header_initials)?.text = initials

        // Launch Menu Activity on Click
        binding.buttonMenu.setOnClickListener {
            it.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
            val intent = Intent(this, OperatorMenuActivity::class.java)
            startActivity(intent)
            // Slide in from left animation
            overridePendingTransition(R.anim.slide_in_left, R.anim.fade_out)
        }
        
        // Disable Drawer Swipe (since we are not using it anymore)
        binding.drawerLayout.setDrawerLockMode(androidx.drawerlayout.widget.DrawerLayout.LOCK_MODE_LOCKED_CLOSED)
    }

    private fun showLanguageSelectionDialog() {
        val dialog = com.google.android.material.bottomsheet.BottomSheetDialog(this, R.style.BottomSheetDialogTheme)
        
        // Fix: Make background transparent to show custom rounded corners
        dialog.setOnShowListener {
            val bottomSheet = dialog.findViewById<View>(com.google.android.material.R.id.design_bottom_sheet) as? FrameLayout
            bottomSheet?.setBackgroundResource(R.drawable.bg_bottom_sheet_universal)
        }
        
        val view = layoutInflater.inflate(R.layout.bottom_sheet_language_selector, null)
        dialog.setContentView(view)

        // Find views (assuming layout exists or we create it dynamically.
        // For simplicity, I'll create a simple View structure programmatically if XML doesn't exist, 
        // BUT strict instruction is to use XML. I'll create the XML in next step.
        // For now, I will bind listeners.
        
        view.findViewById<View>(R.id.btn_close)?.setOnClickListener {
            dialog.dismiss()
        }
        
        view.findViewById<View>(R.id.btn_english)?.setOnClickListener {
            setAppLocale("en")
            dialog.dismiss()
        }
        view.findViewById<View>(R.id.btn_hindi)?.setOnClickListener {
            setAppLocale("hi")
            dialog.dismiss()
        }
        view.findViewById<View>(R.id.btn_tamil)?.setOnClickListener {
            setAppLocale("ta")
            dialog.dismiss()
        }
        
        dialog.show()
    }

    private fun setAppLocale(languageCode: String) {
        AppLocaleManager.setLocale(this, languageCode)
    }

    private fun loadLocale() {
        AppLocaleManager.applySavedLocale(this)
    }
    
    private fun updateSegmentVisuals(mode: OperatorMode) {
        val selectedColor = ContextCompat.getColor(this, R.color.segment_text_selected)
        val unselectedColor = ContextCompat.getColor(this, R.color.segment_text_unselected)
        
        when (mode) {
            OperatorMode.CHECK_IN -> {
                // Active: Bold & Dark
                binding.textCheckin.setTextColor(selectedColor)
                binding.textCheckin.typeface = typefaceBold
                
                // Inactive: Medium & Gray
                binding.textCheckout.setTextColor(unselectedColor)
                binding.textCheckout.typeface = typefaceMedium
            }
            OperatorMode.CHECK_OUT -> {
                // Active: Bold & Dark
                binding.textCheckout.setTextColor(selectedColor)
                binding.textCheckout.typeface = typefaceBold
                
                // Inactive: Medium & Gray
                binding.textCheckin.setTextColor(unselectedColor)
                binding.textCheckin.typeface = typefaceMedium
            }
        }
    }

    private fun mapScanTypeToOperatorMode(scanType: String?): OperatorMode? {
        val normalizedType = scanType.orEmpty().uppercase()
        return when {
            normalizedType.contains("CHECK_OUT") -> OperatorMode.CHECK_OUT
            normalizedType.contains("CHECK_IN") || normalizedType.contains("VEHICLE") -> OperatorMode.CHECK_IN
            else -> null
        }
    }
    
    private fun showSessionInfo() {
        val sharedPref = getSharedPreferences("gridee_prefs", MODE_PRIVATE)
        val operatorName = sharedPref.getString("user_name", "Operator")
        val parkingLotName = sharedPref.getString("parking_lot_name", "Parking Lot")
        
        val message = getString(R.string.op_session_info, operatorName, parkingLotName)
        showNotification(
            title = "Session Info",
            message = message,
            type = NotificationType.INFO
        )
    }
    
    private fun showLogoutConfirmation() {
        LogoutConfirmationBottomSheet.newInstance()
            .setOnLogoutConfirmed { logout() }
            .show(supportFragmentManager, LogoutConfirmationBottomSheet.TAG)
    }

    private fun openVehicleScanner() {
        val spotName = binding.etSpotId.text?.toString()?.trim()
            ?.takeIf { it.isNotEmpty() && it != getString(R.string.op_spot_id_hint) }
        val intent = Intent(this, QrScannerActivity::class.java).apply {
            putExtra(
                QrScannerActivity.EXTRA_SCAN_TYPE,
                when (currentMode) {
                    OperatorMode.CHECK_IN -> "VEHICLE_CHECK_IN"
                    OperatorMode.CHECK_OUT -> "VEHICLE_CHECK_OUT"
                }
            )
            putExtra(QrScannerActivity.EXTRA_PARKING_SPOT_ID, selectedSpotId)
            putExtra(QrScannerActivity.EXTRA_PARKING_SPOT_NAME, spotName)
            currentOperatorParkingLotId()?.let { putExtra(QrScannerActivity.EXTRA_PARKING_LOT_ID, it) }
        }
        vehicleScannerLauncher.launch(intent)
    }

    private fun observeViewModel() {
        // Observe check-in state
        viewModel.checkInState.observe(this) { state ->
            handleCheckInState(state)
        }

        // Observe check-out state
        viewModel.checkOutState.observe(this) { state ->
            handleCheckOutState(state)
        }
    }

    private fun handleCheckInState(state: CheckInState) {
        when (state) {
            is CheckInState.Idle -> {
                binding.progressLoading.visibility = View.GONE
            }
            is CheckInState.Loading -> {
                binding.progressLoading.visibility = View.VISIBLE
                binding.btnScanCircular.isEnabled = false
                binding.btnSubmitManual.isEnabled = false
            }
            is CheckInState.Success -> {
                binding.progressLoading.visibility = View.GONE
                binding.btnScanCircular.isEnabled = true
                binding.btnSubmitManual.isEnabled = true

                val booking = state.booking
                showNotification(
                    title = "Check-In Complete",
                    message = booking.vehicleNumber?.let { "Vehicle $it" } ?: "Vehicle checked in successfully",
                    NotificationType.SUCCESS
                )

                // Return to scan mode
                if (isManualEntryMode) {
                    binding.etVehicleNumberClean.text?.clear()
                    switchToScanMode()
                }
                
                viewModel.resetCheckInState()
            }
            is CheckInState.Error -> {
                binding.progressLoading.visibility = View.GONE
                binding.btnScanCircular.isEnabled = true
                binding.btnSubmitManual.isEnabled = true

                showNotification(
                    title = "Check-In Failed",
                    message = state.message,
                    NotificationType.ERROR
                )

                viewModel.resetCheckInState()
            }
        }
    }

    private fun handleCheckOutState(state: CheckInState) {
        when (state) {
            is CheckInState.Idle -> {
                binding.progressLoading.visibility = View.GONE
            }
            is CheckInState.Loading -> {
                binding.progressLoading.visibility = View.VISIBLE
                binding.btnScanCircular.isEnabled = false
                binding.btnSubmitManual.isEnabled = false
            }
            is CheckInState.Success -> {
                binding.progressLoading.visibility = View.GONE
                binding.btnScanCircular.isEnabled = true
                binding.btnSubmitManual.isEnabled = true

                val booking = state.booking
                showNotification(
                    title = "Check-Out Complete",
                    message = booking.vehicleNumber?.let { "Vehicle $it" } ?: "Vehicle checked out successfully",
                    NotificationType.SUCCESS
                )

                // Return to scan mode
                if (isManualEntryMode) {
                    binding.etVehicleNumberClean.text?.clear()
                    switchToScanMode()
                }
                
                viewModel.resetCheckOutState()
            }
            is CheckInState.Error -> {
                binding.progressLoading.visibility = View.GONE
                binding.btnScanCircular.isEnabled = true
                binding.btnSubmitManual.isEnabled = true

                showNotification(
                    title = "Check-Out Failed",
                    message = state.message,
                    NotificationType.ERROR
                )

                viewModel.resetCheckOutState()
            }
        }
    }

    enum class NotificationType {
        SUCCESS, ERROR, INFO
    }

    private fun showNotification(title: String, message: String, type: NotificationType) {
        val parent = currentNotificationHost()
        val normalizedMessage = normalizeNotificationMessage(message)
        when (type) {
            NotificationType.SUCCESS -> {
                NotificationHelper.showSuccess(
                    parent = parent,
                    title = title,
                    message = normalizedMessage,
                    duration = 3000L
                )
            }
            NotificationType.ERROR -> {
                NotificationHelper.showError(
                    parent = parent,
                    title = title,
                    message = normalizedMessage,
                    duration = 3000L
                )
            }
            NotificationType.INFO -> {
                NotificationHelper.showInfo(
                    parent = parent,
                    title = title,
                    message = normalizedMessage,
                    duration = 3000L
                )
            }
        }
    }

    private fun currentNotificationHost(): ViewGroup {
        return if (binding.layoutContentScanner.visibility == View.VISIBLE) {
            binding.layoutContentScanner
        } else {
            binding.layoutContentDashboard
        }
    }

    private fun normalizeNotificationMessage(message: String): String {
        return message
            .lineSequence()
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .joinToString(" ")
            .replace(Regex("\\s+"), " ")
            .trim()
    }

    private fun showToast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }

    private fun checkCameraPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.CAMERA
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun requestCameraPermission() {
        ActivityCompat.requestPermissions(
            this,
            arrayOf(Manifest.permission.CAMERA),
            CAMERA_PERMISSION_REQUEST
        )
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == CAMERA_PERMISSION_REQUEST) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(this, getString(R.string.camera_permission_granted), Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(
                    this,
                    getString(R.string.camera_permission_required),
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    private fun logout() {
        AuthSession.clearSession(this)

        // Navigate to login
        val intent = Intent(this, LoginActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        finish()
    }
}
