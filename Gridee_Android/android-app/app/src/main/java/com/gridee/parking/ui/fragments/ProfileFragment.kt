package com.gridee.parking.ui.fragments

import android.animation.ValueAnimator
import android.content.Intent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.DecelerateInterpolator
import android.view.HapticFeedbackConstants
import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.view.animation.PathInterpolator
import androidx.core.content.ContextCompat
import androidx.core.content.res.ResourcesCompat
import androidx.lifecycle.ViewModelProvider
import com.gridee.parking.R
import com.gridee.parking.databinding.FragmentProfileBinding
import com.gridee.parking.ui.base.BaseTabFragment
import com.gridee.parking.ui.bottomsheet.AddVehicleBottomSheet
import com.gridee.parking.ui.bottomsheet.LogoutConfirmationBottomSheet
import com.gridee.parking.ui.profile.AccountSettingsActivity
import com.gridee.parking.ui.profile.DisplayThemeActivity
import com.gridee.parking.ui.profile.HelpSupportActivity
import com.gridee.parking.ui.profile.NotificationsActivity
import com.gridee.parking.ui.profile.ProfileViewModel
import com.gridee.parking.utils.AuthSession
import com.gridee.parking.utils.NotificationHelper
import com.gridee.parking.utils.ThemeManager

class ProfileFragment : BaseTabFragment<FragmentProfileBinding>() {

    private lateinit var viewModel: ProfileViewModel
    private var isVehiclesExpanded = false
    private var isAnimating = false // Prevent double-tap glitches

    override fun getViewBinding(inflater: LayoutInflater, container: ViewGroup?): FragmentProfileBinding {
        return FragmentProfileBinding.inflate(inflater, container, false)
    }

    override fun getScrollableView(): View? {
        return try {
            binding.scrollContent
        } catch (e: IllegalStateException) {
            null
        }
    }

    override fun setupUI() {
        viewModel = ViewModelProvider(this)[ProfileViewModel::class.java]
        renderProfileHeader(AuthSession.getUserName(requireContext()))
        
        // Setup frosted glass toolbar scroll effect
        setupFrostedToolbar()

        setupObservers()
        setupClickListeners()
        loadUserData()
        updateThemeModeLabel()

        childFragmentManager.setFragmentResultListener("profile_updated", viewLifecycleOwner) { _, _ ->
            loadUserData()
            
            val parentView = requireActivity().findViewById<android.view.ViewGroup>(R.id.fragment_container)
                ?: requireActivity().window.decorView as? android.view.ViewGroup 
                ?: binding.root
            
            com.gridee.parking.utils.NotificationHelper.showInfoNoIcon(
                parent = parentView,
                title = "Profile Update",
                message = "Profile successfully updated",
                duration = 3000L
            )
        }
    }

    private fun setupFrostedToolbar() {
        val frostView = binding.viewToolbarFrost
        val titleView = binding.tvToolbarTitle

        // Start state — fully clear, title slightly soft
        frostView.alpha = 0f
        titleView.alpha = 0.88f

        // Content top padding — push below the floating title. We can't rely on
        // titleView.post because when the fragment is pre-warmed (hidden), the title
        // never gets measured (visibility=GONE skips layout) and the post fires with
        // height=0 — leaving content overlapping the toolbar. A layout listener fires
        // when the title actually has dimensions (when the fragment becomes visible).
        titleView.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ ->
            val targetPadding = titleView.height + 4f.dpToPx().toInt()
            if (titleView.height > 0 && binding.contentContainer.paddingTop != targetPadding) {
                binding.contentContainer.setPadding(0, targetPadding, 0, 0)
            }
        }

        binding.scrollContent.setOnScrollChangeListener { _, _, scrollY, _, _ ->
            // Dead zone: first 16dp of scroll — no reaction, feels weightless
            val deadZone = 16f.dpToPx()
            val activeScroll = (scrollY - deadZone).coerceAtLeast(0f)

            // Frost alpha: ramps over 120dp of active scroll with ease-out cubic
            val frostRange = 120f.dpToPx()
            val rawFrost = (activeScroll / frostRange).coerceIn(0f, 1f)
            val t = 1f - rawFrost
            val easedFrost = 1f - (t * t * t)
            frostView.alpha = easedFrost

            // Title "wakes up": 0.88 → 1.0 over the first 80dp of active scroll
            val titleRange = 80f.dpToPx()
            val rawTitle = (activeScroll / titleRange).coerceIn(0f, 1f)
            titleView.alpha = 0.88f + (0.12f * rawTitle)

            // Overscroll safety — snap everything to rest state if fully scrolled back
            if (scrollY <= 0) {
                frostView.alpha = 0f
                titleView.alpha = 0.88f
            }
        }
    }

    private fun showVehicleManagementDialog() {
        val vehicles = viewModel.userProfile.value?.vehicleNumbers ?: emptyList()
        val options = if (vehicles.isEmpty()) {
            arrayOf("Add Vehicle")
        } else {
            arrayOf("View Vehicles", "Add Vehicle")
        }
        
        val builder = androidx.appcompat.app.AlertDialog.Builder(requireContext())
        builder.setTitle("Vehicle Management")
        builder.setItems(options) { _, which ->
            when {
                vehicles.isEmpty() && which == 0 -> showAddVehicleDialog()
                vehicles.isNotEmpty() && which == 0 -> showVehicleListDialog()
                vehicles.isNotEmpty() && which == 1 -> showAddVehicleDialog()
            }
        }
        builder.show()
    }

    private fun showVehicleListDialog() {
        val vehicles = viewModel.userProfile.value?.vehicleNumbers ?: emptyList()
        val builder = androidx.appcompat.app.AlertDialog.Builder(requireContext())
        builder.setTitle("My Vehicles")
        builder.setItems(vehicles.toTypedArray()) { _, which ->
            // Use the root view as anchor since this is from a dialog
            showVehicleOptionsDialog(vehicles[which])
        }
        builder.setPositiveButton("Add Vehicle") { _, _ ->
            showAddVehicleDialog()
        }
        builder.setNegativeButton("Close", null)
        builder.show()
    }

    private fun setupObservers() {
        viewModel.userProfile.observe(viewLifecycleOwner) { user ->
            user?.let {
                AuthSession.updateCachedUserProfile(requireContext(), it)
                renderProfileHeader(it.name)
            }

            renderVehicleAccordionContent()
        }

        viewModel.isLoading.observe(viewLifecycleOwner) { isLoading ->
            if (isLoading && viewModel.userProfile.value == null) {
                binding.tvVehicleCount.text = getString(R.string.profile_vehicle_count_loading)
            }

            renderVehicleAccordionContent()
        }

        viewModel.errorMessage.observe(viewLifecycleOwner) { error ->
            error?.let {
                showToast(it)
                viewModel.clearError()
            }
        }

        viewModel.logoutSuccess.observe(viewLifecycleOwner) { success ->
            if (success) {
                AuthSession.clearSession(requireContext())
                navigateToLogin()
            }
        }
    }

    private fun setupClickListeners() {
        // Account section click
        val editProfileListener = View.OnClickListener { view ->
            view.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
            com.gridee.parking.ui.bottomsheet.ProfilePageBottomSheet.newInstance()
                .show(childFragmentManager, com.gridee.parking.ui.bottomsheet.ProfilePageBottomSheet.TAG)
        }

        binding.btnEditProfile.setOnClickListener(editProfileListener)
        binding.tvUserName.setOnClickListener(editProfileListener)
        binding.tvUserInitials.setOnClickListener(editProfileListener)
        binding.cardProfileAvatar.setOnClickListener(editProfileListener)

        // Account & Profile category
        binding.btnAccountSettings.setOnClickListener {
            it.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
            startActivity(Intent(requireContext(), AccountSettingsActivity::class.java))
        }



        binding.btnNotifications.setOnClickListener {
            it.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
            startActivity(Intent(requireContext(), NotificationsActivity::class.java))
        }

        binding.btnBookingHistory.setOnClickListener {
            it.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
             startActivity(Intent(requireContext(), com.gridee.parking.ui.profile.BookingHistoryPlaceholderActivity::class.java))
        }

        // Vehicle Management category - Accordion Animation
        binding.btnMyVehicles.setOnClickListener {
            if (isAnimating) return@setOnClickListener
            
            // 1. Haptic Feedback (Mechanical feel)
            it.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
            
            // 2. "Squish" Micro-interaction (Weight/Inertia)
            it.animate()
                .scaleX(0.95f)
                .scaleY(0.95f)
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
                
            toggleVehiclesAccordion()
        }

        // App Preferences category
        binding.btnDisplayTheme.setOnClickListener {
            it.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
            startActivity(Intent(requireContext(), DisplayThemeActivity::class.java))
        }



        binding.btnLanguageRegion.setOnClickListener { view ->
            view.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
            val parentView = requireActivity().findViewById<android.view.ViewGroup>(R.id.fragment_container)
                ?: requireActivity().window.decorView as? android.view.ViewGroup
                ?: binding.root
            NotificationHelper.showInfo(
                parent = parentView,
                title = "Language & Region",
                message = "Coming soon",
                duration = 3000L
            )
        }

        // Support & Legal category
        binding.btnHelpSupport.setOnClickListener {
            it.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
            startActivity(Intent(requireContext(), HelpSupportActivity::class.java))
        }

        binding.btnPrivacyPolicy.setOnClickListener {
            it.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
            try {
                // Open privacy policy in default browser
                val privacyPolicyUrl = "https://docs.gridee.in/#/privacy"
                val intent = Intent(Intent.ACTION_VIEW, android.net.Uri.parse(privacyPolicyUrl))
                startActivity(intent)
            } catch (e: Exception) {
                showToast("Unable to open Privacy Policy. Please try again.")
            }
        }

        binding.btnDataSafety.setOnClickListener {
            it.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
            try {
                val dataSafetyUrl = "https://docs.gridee.in/#/data-safety"
                val intent = Intent(Intent.ACTION_VIEW, android.net.Uri.parse(dataSafetyUrl))
                startActivity(intent)
            } catch (e: Exception) {
                showToast("Unable to open Data Safety page. Please try again.")
            }
        }

        binding.btnAbout.setOnClickListener {
            it.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
            try {
                val aboutUrl = "https://docs.gridee.in/#/about"
                val intent = Intent(Intent.ACTION_VIEW, android.net.Uri.parse(aboutUrl))
                startActivity(intent)
            } catch (e: Exception) {
                showToast("Unable to open About page. Please try again.")
            }
        }

        // Logout
        binding.btnLogout.setOnClickListener {
            it.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
            showLogoutConfirmation()
        }
    }

    private fun loadUserData() {
        val context = requireContext()
        val sharedPref = requireActivity().getSharedPreferences("gridee_prefs", android.content.Context.MODE_PRIVATE)
        val userId = AuthSession.getUserId(context) ?: sharedPref.getString("user_id", null)
        val isLoggedIn = AuthSession.isAuthenticated(context) || sharedPref.getBoolean("is_logged_in", false)
        
        if (userId != null && isLoggedIn) {
            viewModel.loadUserProfile(context, userId)
        } else if (isLoggedIn) {
            // User is logged in but we don't have user ID, ask them to log in again
            showToast("Please log in again to access your profile")
            navigateToLogin()
        } else {
            showToast("User session expired")
            navigateToLogin()
        }
    }

    private fun formatProfileHeaderName(name: String): String {
        val words = name
            .trim()
            .split(Regex("\\s+"))
            .filter { it.isNotBlank() }

        return words.take(2).joinToString(" ")
    }

    private fun renderProfileHeader(name: String?) {
        val normalizedName = name?.trim().orEmpty()
        binding.tvUserName.text = if (normalizedName.isNotEmpty()) {
            formatProfileHeaderName(normalizedName)
        } else {
            ""
        }
        binding.tvUserInitials.text = buildInitials(normalizedName)
    }

    private fun updateThemeModeLabel() {
        val label = when (ThemeManager.getSavedThemeMode(requireContext())) {
            ThemeManager.MODE_DARK -> "Dark"
            ThemeManager.MODE_SYSTEM -> "System"
            else -> "Light"
        }
        binding.tvDisplayThemeValue.text = label
    }

    override fun onResume() {
        super.onResume()
        if (view != null) {
            updateThemeModeLabel()
        }
    }

    private fun buildInitials(name: String): String {
        if (name.isBlank()) return ""

        return name
            .split(Regex("\\s+"))
            .mapNotNull { word -> word.firstOrNull()?.uppercaseChar() }
            .take(2)
            .joinToString("")
    }

    private fun showVehicleOptionsDialog(vehicleNumber: String) {
        val isDefault = viewModel.userProfile.value?.defaultVehicle == vehicleNumber
        val existingVehicles = viewModel.userProfile.value?.vehicleNumbers ?: emptyList()
        
        val bottomSheet = com.gridee.parking.ui.bottomsheet.VehicleOptionsBottomSheet(
            vehicleNumber = vehicleNumber,
            existingVehicleNumbers = existingVehicles,
            isDefault = isDefault,
            onEditSave = { newVehicleNumber ->
                // Edit is handled inside the sheet with slide animation.
                // This callback fires when user saves the new number.
                viewModel.editVehicle(vehicleNumber, newVehicleNumber)
                
                // Show professional success notification globally
                val parentView = requireActivity().findViewById<android.view.ViewGroup>(R.id.fragment_container)
                    ?: requireActivity().window.decorView as? android.view.ViewGroup 
                    ?: binding.root
                NotificationHelper.showSuccess(
                    parent = parentView,
                    title = "Success",
                    message = "Vehicle number saved successfully",
                    duration = 3000L
                )
            },
            onMakeDefault = { 
                setDefaultVehicle(it) 
            },
            onDeleteConfirm = { 
                // Delete confirmation is handled inside the sheet with slide animation.
                // This callback fires when user confirms deletion.
                viewModel.removeVehicle(it)
                
                // Show professional success notification globally
                val parentView = requireActivity().findViewById<android.view.ViewGroup>(R.id.fragment_container)
                    ?: requireActivity().window.decorView as? android.view.ViewGroup 
                    ?: binding.root
                NotificationHelper.showSuccess(
                    parent = parentView,
                    title = "Success",
                    message = "Vehicle removed successfully",
                    duration = 3000L
                )
            }
        )
        
        bottomSheet.show(childFragmentManager, com.gridee.parking.ui.bottomsheet.VehicleOptionsBottomSheet.TAG)
    }

    private fun setDefaultVehicle(vehicleNumber: String) {
        viewModel.setDefaultVehicle(vehicleNumber)
    }

    private fun showAddVehicleDialog() {
        // Show the add vehicle bottom sheet with spring animation
        val existingVehicles = viewModel.userProfile.value?.vehicleNumbers ?: emptyList()
        val bottomSheet = AddVehicleBottomSheet(existingVehicles) { newVehicleNumber ->
            // Add the vehicle to the backend
            viewModel.addVehicle(newVehicleNumber)
            
            // Show professional success notification globally
            val parentView = requireActivity().findViewById<android.view.ViewGroup>(R.id.fragment_container)
                ?: requireActivity().window.decorView as? android.view.ViewGroup 
                ?: binding.root
            NotificationHelper.showSuccess(
                parent = parentView,
                title = "Success",
                message = "Vehicle added successfully",
                duration = 3000L
            )
        }
        
        bottomSheet.show(childFragmentManager, AddVehicleBottomSheet.TAG)
    }

    private fun showLogoutConfirmation() {
        LogoutConfirmationBottomSheet.newInstance()
            .setOnLogoutConfirmed { viewModel.logout() }
            .show(childFragmentManager, LogoutConfirmationBottomSheet.TAG)
    }

    private fun navigateToLogin() {
        try {
            val intent = Intent(requireContext(), Class.forName("com.gridee.parking.ui.auth.LoginActivity"))
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            startActivity(intent)
            requireActivity().finish()
        } catch (e: Exception) {
            showToast("Unable to logout at this time")
        }
    }

    private fun populateVehicles(vehicles: List<String>) {
        val container = binding.vehiclesContainer
        container.removeAllViews()

        // M3 Emphasized Decelerate — elements entering screen
        val enterInterpolator = PathInterpolator(0.05f, 0.7f, 0.1f, 1.0f)
        val density = resources.displayMetrics.density

        vehicles.forEachIndexed { index, vehicleNumber ->
            val vehicleView = layoutInflater.inflate(
                R.layout.item_vehicle,
                container,
                false
            )

            val tvVehicleNumber = vehicleView.findViewById<android.widget.TextView>(R.id.tv_vehicle_number)
            val tvVehicleLabel = vehicleView.findViewById<android.widget.TextView>(R.id.tv_vehicle_label)
            val ivVehicleMenu = vehicleView.findViewById<android.widget.ImageView>(R.id.iv_vehicle_menu)
            val ivDefaultIndicator = vehicleView.findViewById<android.widget.ImageView>(R.id.iv_default_indicator)

            tvVehicleNumber.text = vehicleNumber
            tvVehicleLabel.text = "Vehicle ${index + 1}"

            // Show default indicator if this is the default vehicle
            val defaultVehicle = viewModel.userProfile.value?.defaultVehicle
            if (vehicleNumber == defaultVehicle) {
                ivDefaultIndicator.visibility = View.VISIBLE
                tvVehicleLabel.text = "Default Vehicle"
            } else {
                ivDefaultIndicator.visibility = View.GONE
            }

            // Add margin between items
            if (index > 0) {
                val params = android.widget.LinearLayout.LayoutParams(
                    android.widget.LinearLayout.LayoutParams.MATCH_PARENT,
                    android.widget.LinearLayout.LayoutParams.WRAP_CONTENT
                )
                params.topMargin = (8 * density).toInt()
                vehicleView.layoutParams = params
            }

            ivVehicleMenu.setOnClickListener {
                it.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
                showVehicleOptionsDialog(vehicleNumber)
            }

            container.addView(vehicleView)

            // Clean subtle entrance — fade + slide + scale
            vehicleView.alpha = 0f
            vehicleView.translationY = 12f * density
            vehicleView.scaleX = 0.97f
            vehicleView.scaleY = 0.97f

            vehicleView.animate()
                .alpha(1f)
                .translationY(0f)
                .scaleX(1f)
                .scaleY(1f)
                .setDuration(300L)
                .setStartDelay(40L * index.toLong())
                .setInterpolator(enterInterpolator)
                .start()
        }

        // Add "Add New Vehicle" button at the end
        val addVehicleView = layoutInflater.inflate(
            R.layout.item_add_vehicle,
            container,
            false
        )

        val params = android.widget.LinearLayout.LayoutParams(
            android.widget.LinearLayout.LayoutParams.MATCH_PARENT,
            android.widget.LinearLayout.LayoutParams.WRAP_CONTENT
        )
        params.topMargin = (12 * density).toInt()
        addVehicleView.layoutParams = params

        addVehicleView.setOnClickListener {
            showAddVehicleDialog()
        }

        container.addView(addVehicleView)

        // Add button entrance — slightly delayed after last card
        addVehicleView.alpha = 0f
        addVehicleView.translationY = 8f * density
        addVehicleView.scaleX = 0.98f
        addVehicleView.scaleY = 0.98f

        addVehicleView.animate()
            .alpha(1f)
            .translationY(0f)
            .scaleX(1f)
            .scaleY(1f)
            .setDuration(280L)
            .setStartDelay(40L * vehicles.size.toLong() + 30L)
            .setInterpolator(enterInterpolator)
            .start()
    }

    private fun populateVehicleLoadingState() {
        val container = binding.vehiclesContainer
        container.removeAllViews()

        repeat(2) { index ->
            val loadingView = layoutInflater.inflate(R.layout.item_vehicle_loading, container, false)

            if (index > 0) {
                loadingView.layoutParams = createVehicleItemLayoutParams(8)
            }

            container.addView(loadingView)
        }

        val loadingMessage = android.widget.TextView(requireContext()).apply {
            text = getString(R.string.profile_vehicle_loading_message)
            setTextColor(ContextCompat.getColor(context, R.color.text_secondary))
            textSize = 13f
            typeface = ResourcesCompat.getFont(context, R.font.inter_regular)
        }
        loadingMessage.layoutParams = createVehicleItemLayoutParams(12)
        container.addView(loadingMessage)
    }

    private fun populateVehicleEmptyState() {
        populateVehicleMessageState(
            title = getString(R.string.profile_vehicle_empty_title),
            message = getString(R.string.profile_vehicle_empty_message),
            action = getString(R.string.profile_vehicle_add_action)
        ) {
            it.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
            showAddVehicleDialog()
        }
    }

    private fun populateVehicleUnavailableState() {
        populateVehicleMessageState(
            title = getString(R.string.profile_vehicle_unavailable_title),
            message = getString(R.string.profile_vehicle_unavailable_message),
            action = getString(R.string.profile_vehicle_retry_action)
        ) {
            it.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
            loadUserData()
        }
    }

    private fun populateVehicleMessageState(
        title: String,
        message: String,
        action: String,
        onActionClick: (View) -> Unit
    ) {
        val container = binding.vehiclesContainer
        container.removeAllViews()

        val emptyView = layoutInflater.inflate(R.layout.item_vehicle_empty_state, container, false)
        emptyView.findViewById<android.widget.TextView>(R.id.tv_vehicle_state_title).text = title
        emptyView.findViewById<android.widget.TextView>(R.id.tv_vehicle_state_message).text = message
        emptyView.findViewById<android.widget.TextView>(R.id.tv_vehicle_state_action).text = action
        emptyView.findViewById<View>(R.id.btn_empty_add_vehicle).setOnClickListener(onActionClick)

        container.addView(emptyView)
    }

    private fun renderVehicleAccordionContent() {
        val user = viewModel.userProfile.value
        val isLoading = viewModel.isLoading.value == true

        when {
            isLoading && user == null -> binding.tvVehicleCount.text =
                getString(R.string.profile_vehicle_count_loading)
            user == null -> updateVehicleCount(0)
            else -> updateVehicleCount(user.vehicleNumbers.size)
        }

        if (!isVehiclesExpanded) return

        when {
            isLoading && user == null -> populateVehicleLoadingState()
            user == null -> populateVehicleUnavailableState()
            user.vehicleNumbers.isEmpty() -> populateVehicleEmptyState()
            else -> populateVehicles(user.vehicleNumbers)
        }
    }

    private fun updateVehicleCount(vehicleCount: Int) {
        binding.tvVehicleCount.text = resources.getQuantityString(
            R.plurals.profile_vehicle_count,
            vehicleCount,
            vehicleCount
        )
    }

    private fun createVehicleItemLayoutParams(topMarginDp: Int): android.widget.LinearLayout.LayoutParams {
        return android.widget.LinearLayout.LayoutParams(
            android.widget.LinearLayout.LayoutParams.MATCH_PARENT,
            android.widget.LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply {
            topMargin = topMarginDp.dpToPx().toInt()
        }
    }
    
    private fun toggleVehiclesAccordion() {
        val expandedLayout = binding.layoutVehiclesExpanded
        val arrowIcon = binding.ivVehiclesExpand
        
        isVehiclesExpanded = !isVehiclesExpanded
        isAnimating = true
        
        // Use Hardware Acceleration for butter-smooth rendering during animation
        expandedLayout.setLayerType(View.LAYER_TYPE_HARDWARE, null)

        // Apple-style Cubic Bezier Curves
        // Opening: Rapid start, gentle deceleration (0.4, 0.0, 0.2, 1.0)
        val openInterpolator = PathInterpolator(0.4f, 0f, 0.2f, 1f)
        // Closing: Gentle acceleration, crisp finish (0.4, 0.0, 1.0, 1.0)
        val closeInterpolator = PathInterpolator(0.4f, 0f, 1f, 1f)
        
        if (isVehiclesExpanded) {
            // OPENING
            renderVehicleAccordionContent()
            
            expandedLayout.visibility = View.VISIBLE
            expandedLayout.alpha = 0f
            expandedLayout.translationY = -8f.dpToPx()
            
            // Measure precise target height
            expandedLayout.measure(
                View.MeasureSpec.makeMeasureSpec(binding.root.width, View.MeasureSpec.AT_MOST),
                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
            )
            val targetHeight = expandedLayout.measuredHeight
            
            // 1. Height Expansion (350ms)
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
            
            // 2. Fade In (Delayed slightly for "solid" feel)
            expandedLayout.animate()
                .alpha(1f)
                .setDuration(300)
                .setStartDelay(50) // Prevent ghosting
                .setInterpolator(openInterpolator)
                .start()
            
            // 3. Slide Down
            expandedLayout.animate()
                .translationY(0f)
                .setDuration(350)
                .setInterpolator(openInterpolator)
                .start()
            
            // 4. Icon Rotation
            arrowIcon.animate()
                .rotation(180f)
                .setDuration(350)
                .setInterpolator(openInterpolator)
                .start()
                
        } else {
            // CLOSING
            val currentHeight = expandedLayout.height
            
            // 1. Height Collapse (300ms)
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
                        // Reset properties for next open
                        expandedLayout.rotationX = 0f
                        expandedLayout.scaleX = 1f
                        expandedLayout.scaleY = 1f
                        isAnimating = false
                    }
                })
                start()
            }
            
            // 2. Clean fade + slide exit
            expandedLayout.animate()
                .alpha(0f)
                .translationY(-8f.dpToPx())
                .setDuration(220)
                .setStartDelay(0)
                .setInterpolator(closeInterpolator)
                .start()
            
            // 3. Icon Rotation
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

    private fun Int.dpToPx(): Float {
        return this.toFloat().dpToPx()
    }

    companion object {
        fun newInstance(): ProfileFragment {
            return ProfileFragment()
        }
    }
}
