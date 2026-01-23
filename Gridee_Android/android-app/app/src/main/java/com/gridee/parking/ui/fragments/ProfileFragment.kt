package com.gridee.parking.ui.fragments

import android.animation.ValueAnimator
import android.content.Intent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.DecelerateInterpolator
import android.view.animation.AccelerateInterpolator
import androidx.core.animation.doOnEnd
import androidx.interpolator.view.animation.FastOutSlowInInterpolator
import android.view.HapticFeedbackConstants
import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.view.animation.PathInterpolator
import androidx.lifecycle.ViewModelProvider
import com.gridee.parking.databinding.FragmentProfileBinding
import com.gridee.parking.ui.base.BaseTabFragment
import com.gridee.parking.ui.bottomsheet.AddVehicleBottomSheet
import com.gridee.parking.ui.bottomsheet.EditVehicleBottomSheet
import com.gridee.parking.ui.profile.AccountSettingsActivity
import com.gridee.parking.ui.profile.DisplayThemeActivity
import com.gridee.parking.ui.profile.HelpSupportActivity
import com.gridee.parking.ui.profile.NotificationsActivity
import com.gridee.parking.ui.profile.ProfileViewModel
import com.gridee.parking.utils.AuthSession
import com.gridee.parking.utils.NotificationHelper

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
        
        // Setup collapsing toolbar with smooth parallax
        setupCollapsingToolbar()
        
        setupObservers()
        setupClickListeners()
        loadUserData()
        
        childFragmentManager.setFragmentResultListener("profile_updated", viewLifecycleOwner) { _, _ ->
            loadUserData()
        }

        // Fog Effect
        binding.scrollContent.setOnScrollChangeListener { _, _, scrollY, _, _ ->
            val maxAlphaOffset = 60f.dpToPx()
            val alpha = (scrollY / maxAlphaOffset).coerceIn(0f, 1f)
            binding.viewFogOverlay.alpha = alpha
        }
    }
    
    private fun setupCollapsingToolbar() {
        // Title is now handled by custom TextView in Toolbar XML
        
        // Add offset change listener for smooth transitions
        binding.appBarLayout.addOnOffsetChangedListener { appBarLayout, verticalOffset ->
            val totalScrollRange = appBarLayout.totalScrollRange.toFloat()
            val scrollPercentage = Math.abs(verticalOffset / totalScrollRange)
            
            // Smooth alpha transition for better visual feedback
            binding.collapsingToolbar.alpha = 1f - (scrollPercentage * 0.1f)
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
        viewModel.userProfile.observe(this) { user ->
            user?.let {
                // Set user initials in the avatar
                val initials = it.name.split(" ").mapNotNull { name -> 
                    name.firstOrNull()?.uppercaseChar() 
                }.take(2).joinToString("")
                binding.tvUserInitials.text = if (initials.isNotEmpty()) initials else "U"
                
                // Set user info
                binding.tvUserName.text = it.name
                
                // Update vehicle count 
                val vehicleCount = it.vehicleNumbers.size
                binding.tvVehicleCount.text = if (vehicleCount == 1) {
                    "$vehicleCount vehicle"
                } else {
                    "$vehicleCount vehicles"
                }
                
                // Populate vehicles in accordion if it's already expanded
                if (isVehiclesExpanded) {
                    populateVehicles(it.vehicleNumbers)
                }
            }
        }

        viewModel.isLoading.observe(this) { isLoading ->
            // Handle loading state if needed
        }

        viewModel.errorMessage.observe(this) { error ->
            error?.let {
                showToast(it)
                viewModel.clearError()
            }
        }

        viewModel.logoutSuccess.observe(this) { success ->
            if (success) {
                AuthSession.clearSession(requireContext())
                navigateToLogin()
            }
        }
    }

    private fun setupClickListeners() {
        // Account section click
        val editProfileListener = View.OnClickListener {
            com.gridee.parking.ui.bottomsheet.ProfilePageBottomSheet.newInstance()
                .show(childFragmentManager, com.gridee.parking.ui.bottomsheet.ProfilePageBottomSheet.TAG)
        }

        binding.btnEditProfile.setOnClickListener(editProfileListener)
        binding.tvUserName.setOnClickListener(editProfileListener)
        binding.tvUserInitials.setOnClickListener(editProfileListener)

        // Account & Profile category
        binding.btnAccountSettings.setOnClickListener {
            startActivity(Intent(requireContext(), AccountSettingsActivity::class.java))
        }



        binding.btnNotifications.setOnClickListener {
            startActivity(Intent(requireContext(), NotificationsActivity::class.java))
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

        binding.btnParkingPreferences.setOnClickListener {
            showToast("Parking Preferences - Coming Soon!")
        }

        // App Preferences category
        binding.btnDisplayTheme.setOnClickListener {
            startActivity(Intent(requireContext(), DisplayThemeActivity::class.java))
        }

        binding.btnSoundsVibration.setOnClickListener {
            showToast("Sounds & Vibration - Coming Soon!")
        }

        binding.btnLanguageRegion.setOnClickListener {
            showToast("Language & Region - Coming Soon!")
        }

        // Support & Legal category
        binding.btnHelpSupport.setOnClickListener {
            startActivity(Intent(requireContext(), HelpSupportActivity::class.java))
        }

        binding.btnPrivacyPolicy.setOnClickListener {
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
            try {
                val dataSafetyUrl = "https://docs.gridee.in/#/data-safety"
                val intent = Intent(Intent.ACTION_VIEW, android.net.Uri.parse(dataSafetyUrl))
                startActivity(intent)
            } catch (e: Exception) {
                showToast("Unable to open Data Safety page. Please try again.")
            }
        }

        binding.btnAbout.setOnClickListener {
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
            showLogoutConfirmation()
        }
    }

    private fun loadUserData() {
        val sharedPref = requireActivity().getSharedPreferences("gridee_prefs", android.content.Context.MODE_PRIVATE)
        val userId = sharedPref.getString("user_id", null)
        val isLoggedIn = sharedPref.getBoolean("is_logged_in", false)
        
        if (userId != null && isLoggedIn) {
            viewModel.loadUserProfile(userId)
        } else if (isLoggedIn) {
            // User is logged in but we don't have user ID, ask them to log in again
            showToast("Please log in again to access your profile")
            navigateToLogin()
        } else {
            showToast("User session expired")
            navigateToLogin()
        }
    }

    private fun showVehicleOptionsDialog(vehicleNumber: String) {
        val isDefault = viewModel.userProfile.value?.defaultVehicle == vehicleNumber
        
        val bottomSheet = com.gridee.parking.ui.bottomsheet.VehicleOptionsBottomSheet(
            vehicleNumber = vehicleNumber,
            isDefault = isDefault,
            onEdit = { 
                editVehicle(it) 
            },
            onMakeDefault = { 
                setDefaultVehicle(it) 
            },
            onDelete = { 
                removeVehicle(it) 
            }
        )
        
        bottomSheet.show(childFragmentManager, com.gridee.parking.ui.bottomsheet.VehicleOptionsBottomSheet.TAG)
    }

    private fun editVehicle(vehicleNumber: String) {
        // Show the edit vehicle bottom sheet with spring animation
        val bottomSheet = EditVehicleBottomSheet(vehicleNumber) { newVehicleNumber ->
            // Update the vehicle in the backend
            viewModel.editVehicle(vehicleNumber, newVehicleNumber.uppercase())
            
            // Show professional success notification
            NotificationHelper.showSuccess(
                parent = binding.root,
                title = "Success",
                message = "Vehicle number saved successfully",
                duration = 3000L
            )
        }
        
        bottomSheet.show(childFragmentManager, EditVehicleBottomSheet.TAG)
    }

    private fun setDefaultVehicle(vehicleNumber: String) {
        viewModel.setDefaultVehicle(vehicleNumber)
    }

    private fun removeVehicle(vehicleNumber: String) {
        val builder = androidx.appcompat.app.AlertDialog.Builder(requireContext())
        builder.setTitle("Remove Vehicle")
        builder.setMessage("Are you sure you want to remove vehicle $vehicleNumber?")
        builder.setPositiveButton("Remove") { _, _ ->
            viewModel.removeVehicle(vehicleNumber)
        }
        builder.setNegativeButton("Cancel", null)
        builder.show()
    }

    private fun showAddVehicleDialog() {
        // Show the add vehicle bottom sheet with spring animation
        val bottomSheet = AddVehicleBottomSheet { newVehicleNumber ->
            // Add the vehicle to the backend
            viewModel.addVehicle(newVehicleNumber.uppercase())
            
            // Show professional success notification
            NotificationHelper.showSuccess(
                parent = binding.root,
                title = "Success",
                message = "Vehicle added successfully",
                duration = 3000L
            )
        }
        
        bottomSheet.show(childFragmentManager, AddVehicleBottomSheet.TAG)
    }

    private fun showLogoutConfirmation() {
        val builder = androidx.appcompat.app.AlertDialog.Builder(requireContext())
        builder.setTitle("Logout")
        builder.setMessage("Are you sure you want to logout?")
        builder.setPositiveButton("Logout") { _, _ ->
            viewModel.logout()
        }
        builder.setNegativeButton("Cancel", null)
        builder.show()
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
        

        // Define Apple-style physics for content
        val contentInterpolator = PathInterpolator(0.4f, 0f, 0.2f, 1f)
        
        vehicles.forEachIndexed { index, vehicleNumber ->
            val vehicleView = layoutInflater.inflate(
                com.gridee.parking.R.layout.item_vehicle,
                container,
                false
            )
            
            val tvVehicleNumber = vehicleView.findViewById<android.widget.TextView>(com.gridee.parking.R.id.tv_vehicle_number)
            val tvVehicleLabel = vehicleView.findViewById<android.widget.TextView>(com.gridee.parking.R.id.tv_vehicle_label)
            val ivVehicleMenu = vehicleView.findViewById<android.widget.ImageView>(com.gridee.parking.R.id.iv_vehicle_menu)
            val ivDefaultIndicator = vehicleView.findViewById<android.widget.ImageView>(com.gridee.parking.R.id.iv_default_indicator)
            
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
            
            // Add margin if not the first item
            if (index > 0) {
                val params = android.widget.LinearLayout.LayoutParams(
                    android.widget.LinearLayout.LayoutParams.MATCH_PARENT,
                    android.widget.LinearLayout.LayoutParams.WRAP_CONTENT
                )
                params.topMargin = (8 * resources.displayMetrics.density).toInt()
                vehicleView.layoutParams = params
            }
            
            // Add click listener for the entire vehicle item
            vehicleView.setOnClickListener {
                // Optional: Can be used for selection or other actions
            }
            
            // Add click listener for the three-dot menu
            ivVehicleMenu.setOnClickListener {
                showVehicleOptionsDialog(vehicleNumber)
            }
            
            container.addView(vehicleView)
            
            // EXTRAORDINARY 3D "DECK" REVEAL
            vehicleView.alpha = 0f
            vehicleView.translationY = -50f.dpToPx() // Hyper-extended slide (-50dp)
            vehicleView.rotationX = -20f // 3D Hinge Effect (Tilted back)
            vehicleView.scaleX = 0.92f // Depth Scale (Coming from background)
            vehicleView.scaleY = 0.92f
            vehicleView.pivotY = 0f // Hinge point at the top
            
            vehicleView.animate()
                .alpha(1f)
                .translationY(0f)
                .rotationX(0f)
                .scaleX(1f)
                .scaleY(1f)
                .setDuration(450) // Long, luxurious duration
                .setStartDelay(60L + (index * 50L)) // Increased stagger (50ms) for distinctive arrival
                .setInterpolator(contentInterpolator)
                .start()
        }
        
        // Add "Add New Vehicle" button at the end
        val addVehicleView = layoutInflater.inflate(
            com.gridee.parking.R.layout.item_add_vehicle,
            container,
            false
        )
        
        // Add margin for spacing
        val params = android.widget.LinearLayout.LayoutParams(
            android.widget.LinearLayout.LayoutParams.MATCH_PARENT,
            android.widget.LinearLayout.LayoutParams.WRAP_CONTENT
        )
        params.topMargin = (16 * resources.displayMetrics.density).toInt()
        addVehicleView.layoutParams = params
        
        // Add click listener
        addVehicleView.setOnClickListener {
            showAddVehicleDialog()
        }
        
        container.addView(addVehicleView)
        
        // ADD BUTTON ENTRANCE (Slighter slower/later for CTA distinction)
        addVehicleView.alpha = 0f
        addVehicleView.translationY = -20f.dpToPx()
        addVehicleView.scaleX = 0.95f // Subtle scale up entrance
        addVehicleView.scaleY = 0.95f
        
        addVehicleView.animate()
            .alpha(1f)
            .translationY(0f)
            .scaleX(1f)
            .scaleY(1f)
            .setDuration(400) // Slower duration
            .setStartDelay(60L + (vehicles.size * 30L) + 20L) // Wait for list + extra beat
            .setInterpolator(contentInterpolator)
            .start()
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
            val vehicles = viewModel.userProfile.value?.vehicleNumbers ?: emptyList()
            populateVehicles(vehicles)
            
            expandedLayout.visibility = View.VISIBLE
            expandedLayout.alpha = 0f
            expandedLayout.translationY = -20f.dpToPx() // Subtle slide from top
            
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
            
            // 2. 3D "FOLD BACK" EXIT
            // We animate the whole container retreating into the background
            expandedLayout.pivotY = 0f // Pivot at the top hinge
            
            expandedLayout.animate()
                .alpha(0f)                // Fade out
                .translationY(-30f.dpToPx()) // Slide up deeper
                .rotationX(15f)           // TILT BACK (Folding effect)
                .scaleX(0.95f)            // Shrink slightly (Depth retreat)
                .scaleY(0.95f)
                .setDuration(250)         // Fast but visible
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

    companion object {
        fun newInstance(): ProfileFragment {
            return ProfileFragment()
        }
    }
}
