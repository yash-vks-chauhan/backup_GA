package com.gridee.parking.ui.operator

import android.Manifest
import android.animation.ValueAnimator
import android.app.Dialog
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
import com.google.android.material.button.MaterialButton
import com.gridee.parking.R
import com.gridee.parking.data.model.ParkingSpot
import com.gridee.parking.data.repository.ParkingRepository
import com.gridee.parking.databinding.ActivityOperatorDashboardBinding
import com.gridee.parking.databinding.BottomSheetOperatorSpotSelectionBinding
import com.gridee.parking.ui.auth.LoginActivity
import com.gridee.parking.ui.booking.ParkingSpotSelectionAdapter
import com.gridee.parking.ui.qr.QrScannerActivity
import com.gridee.parking.utils.AuthSession
import com.gridee.parking.utils.InAppUpdateController
import com.gridee.parking.utils.NotificationHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

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
    }

    enum class OperatorMode {
        CHECK_IN, CHECK_OUT
    }

    private val vehicleScannerLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
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
                when (currentMode) {
                    OperatorMode.CHECK_IN -> viewModel.checkInByVehicleNumber(vehicleNumber, spotId)
                    OperatorMode.CHECK_OUT -> viewModel.checkOutByVehicleNumber(vehicleNumber, spotId)
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

        loadFonts()
        setupUI()
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
            typefaceMedium = ResourcesCompat.getFont(this, R.font.inter_medium)
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

    override fun onDestroy() {
        inAppUpdateController?.onDestroy()
        inAppUpdateController = null
        spotSelectionDialog?.dismiss()
        spotSelectionDialog = null
        super.onDestroy()
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

        // Manual Entry Link
        binding.linkManualEntry.setOnClickListener {
            it.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
            switchToManualEntry()
        }

        // Back to Scan
        binding.btnBackToScan.setOnClickListener {
            it.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
            switchToScanMode()
        }

        // Submit Manual Button
        binding.btnSubmitManual.setOnClickListener {
            it.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
            if (!ensureSpotSelected()) return@setOnClickListener
            val vehicleNumber = binding.etVehicleNumberClean.text.toString().trim()
            val normalizedVehicle = normalizeVehicleNumber(vehicleNumber)
            val spotId = selectedSpotId
            
            when {
                normalizedVehicle.isBlank() -> {
                    binding.etVehicleNumberClean.error = getString(R.string.op_enter_vehicle_error)
                    binding.etVehicleNumberClean.requestFocus()
                }
                normalizedVehicle.length < 4 || normalizedVehicle.length > 15 -> {
                    binding.etVehicleNumberClean.error = getString(R.string.op_invalid_vehicle_error)
                    binding.etVehicleNumberClean.requestFocus()
                }
                else -> {
                    when (currentMode) {
                        OperatorMode.CHECK_IN -> viewModel.checkInByVehicleNumber(normalizedVehicle, spotId)
                        OperatorMode.CHECK_OUT -> viewModel.checkOutByVehicleNumber(normalizedVehicle, spotId)
                    }
                }
            }
        }

        // Selected spot field opens the selection sheet
        binding.etSpotId.apply {
            isFocusable = false
            isFocusableInTouchMode = false
            isCursorVisible = false
            setOnClickListener { showSpotSelectionSheet() }
        }
        binding.tvSpotIdLabel.setOnClickListener { showSpotSelectionSheet() }

        // Auto-format vehicle number
        binding.etVehicleNumberClean.addTextChangedListener(object : TextWatcher {
            private var isFormatting = false
            
            override fun afterTextChanged(s: Editable?) {
                if (isFormatting) return
                
                isFormatting = true
                val cleaned = s.toString().replace(" ", "").uppercase()
                
                val formatted = when {
                    cleaned.length <= 2 -> cleaned
                    cleaned.length <= 4 -> "${cleaned.substring(0, 2)} ${cleaned.substring(2)}"
                    cleaned.length <= 6 -> "${cleaned.substring(0, 2)} ${cleaned.substring(2, 4)} ${cleaned.substring(4)}"
                    else -> "${cleaned.substring(0, 2)} ${cleaned.substring(2, 4)} ${cleaned.substring(4, 6)} ${cleaned.substring(6).take(4)}"
                }
                
                if (formatted != s.toString()) {
                    s?.replace(0, s.length, formatted)
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
            bottomSheet?.setBackgroundResource(android.R.color.transparent)
            dialog.behavior.isDraggable = false
            dialog.behavior.state = com.google.android.material.bottomsheet.BottomSheetBehavior.STATE_EXPANDED
        }
        dialog.setOnDismissListener { spotSelectionDialog = null }

        var tempSelection: ParkingSpot? = selectedSpot
        val logBuffer = StringBuilder()
        fun pushLog(message: String) {
            if (logBuffer.isNotEmpty()) logBuffer.append('\n')
            logBuffer.append(message)
            sheetBinding.tvLog.text = logBuffer.toString()
            sheetBinding.tvLog.visibility = View.VISIBLE
        }

        val adapter = ParkingSpotSelectionAdapter(
            onItemClick = { spot ->
                tempSelection = spot
                sheetBinding.btnSelectSpot.isEnabled = true
            },
            allowUnavailableSelection = true
        )

        sheetBinding.rvSpots.layoutManager = LinearLayoutManager(this)
        sheetBinding.rvSpots.adapter = adapter
        if (!selectedSpotId.isNullOrBlank()) {
            adapter.setSelectedSpot(selectedSpotId)
            sheetBinding.btnSelectSpot.isEnabled = true
        }

        sheetBinding.btnSelectSpot.setOnClickListener {
            val chosenSpot = tempSelection
            if (chosenSpot == null) {
                showToast(getString(R.string.op_select_spot_required))
                return@setOnClickListener
            }
            applySelectedSpot(chosenSpot)
            dialog.dismiss()
        }

        sheetBinding.progressBar.visibility = View.VISIBLE
        sheetBinding.tvEmptyState.visibility = View.GONE
        sheetBinding.rvSpots.visibility = View.GONE
        pushLog("Loading parking spots…")

        lifecycleScope.launch {
            val spots = loadAllParkingSpots(::pushLog)
            if (spotSelectionDialog !== dialog || !dialog.isShowing) return@launch
            sheetBinding.progressBar.visibility = View.GONE

            if (spots.isEmpty()) {
                sheetBinding.tvEmptyState.visibility = View.VISIBLE
                sheetBinding.rvSpots.visibility = View.GONE
                sheetBinding.btnSelectSpot.isEnabled = tempSelection != null
            } else {
                sheetBinding.tvEmptyState.visibility = View.GONE
                sheetBinding.rvSpots.visibility = View.VISIBLE
                adapter.submitList(spots)
                val previewNames = spots.take(5).joinToString { getSpotDisplayName(it) }
                pushLog("Spots loaded: ${spots.size}. First: $previewNames")
                if (!selectedSpotId.isNullOrBlank()) {
                    adapter.setSelectedSpot(selectedSpotId)
                }
            }
        }

        dialog.show()
    }

    private fun applySelectedSpot(spot: ParkingSpot) {
        selectedSpot = spot
        selectedSpotId = spot.id
        binding.etSpotId.setText(getSpotDisplayName(spot))
    }

    private suspend fun loadAllParkingSpots(
        log: ((String) -> Unit)? = null
    ): List<ParkingSpot> = withContext(Dispatchers.IO) {
        try {
            val prefs = getSharedPreferences("gridee_prefs", MODE_PRIVATE)
            val assignedLotId = prefs.getString("parking_lot_id", null)?.trim().orEmpty()
            val assignedLotName = prefs.getString("parking_lot_name", null)?.trim().orEmpty()

            suspend fun logOnMain(message: String) {
                log ?: return
                withContext(Dispatchers.Main) { log(message) }
            }

            if (assignedLotId.isNotEmpty()) {
                logOnMain("Using assigned lot id: $assignedLotId")
                val spotsResp = parkingRepository.getParkingSpotsByLot(assignedLotId)
                if (spotsResp.isSuccessful) {
                    val spots = spotsResp.body().orEmpty()
                    logOnMain("Loaded ${spots.size} spots from assigned lot.")
                    if (spots.isNotEmpty()) {
                        return@withContext spots
                    }
                } else {
                    logOnMain("Failed to load spots for assigned lot (HTTP ${spotsResp.code()}).")
                }
            }

            if (assignedLotName.isNotEmpty()) {
                logOnMain("Searching lot by name: $assignedLotName")
                val lotByNameResponse = parkingRepository.getParkingLotByName(assignedLotName)
                val lot = if (lotByNameResponse.isSuccessful) lotByNameResponse.body() else null
                val lotId = lot?.id?.trim().orEmpty()
                if (lotId.isNotEmpty()) {
                    logOnMain("Resolved lot id: $lotId")
                    val spotsResp = parkingRepository.getParkingSpotsByLot(lotId)
                    if (spotsResp.isSuccessful) {
                        val spots = spotsResp.body().orEmpty()
                        logOnMain("Loaded ${spots.size} spots from resolved lot.")
                        if (spots.isNotEmpty()) {
                            return@withContext spots
                        }
                    } else {
                        logOnMain("Failed to load spots for resolved lot (HTTP ${spotsResp.code()}).")
                    }
                } else {
                    logOnMain("Lot name not found on server.")
                }
            }

            logOnMain("Falling back to all lots…")
            val lotsResponse = parkingRepository.getParkingLots()
            if (!lotsResponse.isSuccessful) {
                logOnMain("Failed to load parking lots (HTTP ${lotsResponse.code()}).")
                return@withContext emptyList()
            }

            val lots = lotsResponse.body() ?: emptyList()
            val combined = mutableListOf<ParkingSpot>()
            for (lot in lots) {
                val lotId = lot.id.trim()
                if (lotId.isEmpty()) continue
                val spotsResp = parkingRepository.getParkingSpotsByLot(lotId)
                if (spotsResp.isSuccessful) {
                    combined.addAll(spotsResp.body().orEmpty())
                }
            }

            val byId = LinkedHashMap<String, ParkingSpot>()
            for (spot in combined) {
                if (spot.id.isNotBlank()) {
                    byId[spot.id] = spot
                }
            }

            logOnMain("Total spots loaded from all lots: ${byId.size}")
            return@withContext byId.values.sortedBy { getSpotDisplayName(it).lowercase() }
        } catch (_: Exception) {
            if (log != null) {
                withContext(Dispatchers.Main) { log("Error loading parking spots.") }
            }
            return@withContext emptyList()
        }
    }

    private fun getSpotDisplayName(spot: ParkingSpot): String {
        return spot.name
            ?: spot.zoneName
            ?: spot.spotCode
            ?: spot.id
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

    private fun normalizeVehicleNumber(raw: String): String {
        return raw.trim()
            .uppercase(java.util.Locale.ROOT)
            .replace(Regex("[^A-Z0-9]"), "")
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
            binding.textHeaderTitle.text = getString(R.string.op_scanner_tab)

            // Animate Tabs
            activateScannerTab(animate)
            deactivateDashboardTab(animate)
        } else {
            // Show Dashboard content
            binding.layoutContentScanner.visibility = View.GONE
            binding.layoutContentDashboard.visibility = View.VISIBLE
            binding.textHeaderTitle.text = getString(R.string.op_dashboard_tab)

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
                
                // Focus on input
                binding.etVehicleNumberClean.requestFocus()
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

    private fun updateUIForMode(mode: OperatorMode, animate: Boolean) {
        // Update scan label
        val scanLabel = when (mode) {
            OperatorMode.CHECK_IN -> getString(R.string.op_tap_to_scan)
            OperatorMode.CHECK_OUT -> getString(R.string.op_tap_to_scan)
        }
        binding.tvScanLabel.text = scanLabel
        
        // Update submit button text
        val submitText = when (mode) {
            OperatorMode.CHECK_IN -> getString(R.string.op_btn_check_in)
            OperatorMode.CHECK_OUT -> getString(R.string.op_btn_check_out)
        }
        binding.btnSubmitManual.text = submitText
        
        // If in manual mode, clear input
        if (isManualEntryMode) {
            binding.etVehicleNumberClean.text?.clear()
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
        val dialog = com.google.android.material.bottomsheet.BottomSheetDialog(this)
        
        // Fix: Make background transparent to show custom rounded corners
        dialog.setOnShowListener {
            val bottomSheet = dialog.findViewById<View>(com.google.android.material.R.id.design_bottom_sheet) as? FrameLayout
            bottomSheet?.setBackgroundResource(android.R.color.transparent)
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
        val locale = java.util.Locale(languageCode)
        java.util.Locale.setDefault(locale)
        val config = resources.configuration
        config.setLocale(locale)
        createConfigurationContext(config)
        resources.updateConfiguration(config, resources.displayMetrics)

        // Save to prefs
        getSharedPreferences("gridee_prefs", MODE_PRIVATE).edit().putString("app_language", languageCode).apply()

        // Restart activity to apply changes
        val intent = intent
        finish()
        startActivity(intent)
    }

    private fun loadLocale() {
        val sharedPref = getSharedPreferences("gridee_prefs", MODE_PRIVATE)
        val languageCode = sharedPref.getString("app_language", "en") ?: "en"
        val locale = java.util.Locale(languageCode)
        java.util.Locale.setDefault(locale)
        val config = resources.configuration
        config.setLocale(locale)
        resources.updateConfiguration(config, resources.displayMetrics)
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
    
    private fun showSessionInfo() {
        val sharedPref = getSharedPreferences("gridee_prefs", MODE_PRIVATE)
        val operatorName = sharedPref.getString("user_name", "Operator")
        val parkingLotName = sharedPref.getString("parking_lot_name", "Parking Lot")
        
        val message = getString(R.string.op_session_info, operatorName, parkingLotName)
        showNotification(message, NotificationType.INFO)
    }
    
    private fun showLogoutConfirmation() {
        val dialog = Dialog(this)
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        dialog.setContentView(R.layout.dialog_logout_confirmation)
        
        val btnCancel = dialog.findViewById<MaterialButton>(R.id.btn_cancel)
        val btnLogout = dialog.findViewById<MaterialButton>(R.id.btn_logout)
        
        btnCancel.setOnClickListener {
            dialog.dismiss()
        }
        
        btnLogout.setOnClickListener {
            dialog.dismiss()
            logout()
        }
        
        dialog.show()
    }

    private fun openVehicleScanner() {
        val intent = Intent(this, QrScannerActivity::class.java).apply {
            putExtra(
                QrScannerActivity.EXTRA_SCAN_TYPE,
                when (currentMode) {
                    OperatorMode.CHECK_IN -> "VEHICLE_CHECK_IN"
                    OperatorMode.CHECK_OUT -> "VEHICLE_CHECK_OUT"
                }
            )
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
                    "✅ Check-In Successful\nVehicle: ${booking.vehicleNumber}",
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
                    "❌ Check-In Failed\n${state.message}",
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
                    "✅ Check-Out Successful\nVehicle: ${booking.vehicleNumber}",
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
                    "❌ Check-Out Failed\n${state.message}",
                    NotificationType.ERROR
                )

                viewModel.resetCheckOutState()
            }
        }
    }

    enum class NotificationType {
        SUCCESS, ERROR, INFO
    }

    private fun showNotification(message: String, type: NotificationType) {
        when (type) {
            NotificationType.SUCCESS -> {
                NotificationHelper.showSuccess(
                    parent = binding.root as ViewGroup,
                    message = message,
                    duration = 3000L
                )
            }
            NotificationType.ERROR -> {
                NotificationHelper.showError(
                    parent = binding.root as ViewGroup,
                    message = message,
                    duration = 3000L
                )
            }
            NotificationType.INFO -> {
                NotificationHelper.showInfo(
                    parent = binding.root as ViewGroup,
                    message = message,
                    duration = 3000L
                )
            }
        }
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
                Toast.makeText(this, "Camera permission granted", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(
                    this,
                    "Camera permission is required to scan vehicles",
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
