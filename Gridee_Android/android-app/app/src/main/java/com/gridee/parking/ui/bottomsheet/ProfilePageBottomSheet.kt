package com.gridee.parking.ui.bottomsheet

import android.app.Dialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import androidx.lifecycle.ViewModelProvider
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.gridee.parking.R
import com.gridee.parking.databinding.BottomSheetProfilePageBinding
import com.gridee.parking.ui.profile.EditProfileViewModel
import com.gridee.parking.utils.AuthSession

class ProfilePageBottomSheet : BottomSheetDialogFragment() {

    private var _binding: BottomSheetProfilePageBinding? = null
    private val binding get() = _binding!!

    private lateinit var viewModel: EditProfileViewModel

    private var selectedPhotoUri: android.net.Uri? = null
    private var previousActivityNavBarColor: Int? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setStyle(STYLE_NORMAL, com.gridee.parking.R.style.BottomSheetDialogTheme)
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val dialog = super.onCreateDialog(savedInstanceState)
        dialog.setOnShowListener { dialogInterface ->
            val bottomSheetDialog = dialogInterface as com.google.android.material.bottomsheet.BottomSheetDialog
            
            // Fix for Edge-to-Edge: Ensure the container extends behind nav bar
            val bottomSheet = bottomSheetDialog.findViewById<View>(com.google.android.material.R.id.design_bottom_sheet)
            bottomSheet?.let { sheet ->
                // Keep the sheet surface opaque so nav-area never appears transparent
                sheet.setBackgroundResource(com.gridee.parking.R.drawable.bg_bottom_sheet_universal)
                
                // Force the sheet to extend to the edge
                sheet.fitsSystemWindows = false
                
                // Remove any margins that might lift the sheet up
                val params = sheet.layoutParams as? ViewGroup.MarginLayoutParams
                params?.setMargins(0, 0, 0, 0)
                sheet.layoutParams = params
            }
            
            // Ensure behavior ignores gesture insets
            bottomSheetDialog.behavior.apply {
                isGestureInsetBottomIgnored = true
                skipCollapsed = true
                state = com.google.android.material.bottomsheet.BottomSheetBehavior.STATE_EXPANDED
            }
            
            bottomSheetDialog.window?.let { window ->
                WindowCompat.setDecorFitsSystemWindows(window, false)
                window.addFlags(android.view.WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS)
                window.clearFlags(android.view.WindowManager.LayoutParams.FLAG_TRANSLUCENT_NAVIGATION)
                window.navigationBarColor = android.graphics.Color.TRANSPARENT
                window.isNavigationBarContrastEnforced = false
                
                // Ensure light nav bar (dark icons) since background is white
                val wic = WindowCompat.getInsetsController(window, window.decorView)
                wic.isAppearanceLightNavigationBars = true
                
                // Remove the black divider line above the nav bar (Android 9+)
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
                    window.navigationBarDividerColor = android.graphics.Color.TRANSPARENT
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                        window.isNavigationBarContrastEnforced = false
                    }
                }

                // Glassmorphism: Blur the screen behind the sheet (Android 12+)
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                    window.attributes.blurBehindRadius = 50 // 50px blur for frosted glass effect
                    window.attributes = window.attributes // Apply changes
                }
            }
        }
        return dialog
    }

    override fun onStart() {
        super.onStart()
        // Hard fallback: also force the host activity nav bar to white while this sheet is visible.
        activity?.window?.let { hostWindow ->
            previousActivityNavBarColor = hostWindow.navigationBarColor
            hostWindow.navigationBarColor = ContextCompat.getColor(requireContext(), R.color.white)
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                hostWindow.isNavigationBarContrastEnforced = false
            }
            WindowCompat.getInsetsController(hostWindow, hostWindow.decorView)
                .isAppearanceLightNavigationBars = true
        }
    }

    override fun onStop() {
        super.onStop()
        val previousColor = previousActivityNavBarColor
        if (previousColor != null) {
            activity?.window?.navigationBarColor = previousColor
        }
        previousActivityNavBarColor = null
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = BottomSheetProfilePageBinding.inflate(inflater, container, false)
        return binding.root
    }

    private enum class Mode { EDIT_PROFILE, INFO }

    private var mode: Mode = Mode.EDIT_PROFILE
    private var infoLottieFile: String? = null
    private var infoTitle: String? = null
    private var infoMessage: String? = null

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        viewModel = ViewModelProvider(this)[EditProfileViewModel::class.java]
        renderProfileIdentity(AuthSession.getUserName(requireContext()))
        
        setupUI()
        setupBehaviors()
        setupInsets()
        
        startModeSpecificSetup()
        
        // Ambient Shadow (Android 9+)
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
            binding.root.outlineAmbientShadowColor = android.graphics.Color.parseColor("#40000000")
            binding.root.outlineSpotShadowColor = android.graphics.Color.parseColor("#40000000")
        }
    }

    private fun startModeSpecificSetup() {
        if (mode == Mode.EDIT_PROFILE) {
            binding.layoutEditProfileForm.isVisible = true
            binding.layoutInfoView.isVisible = false
            setupObservers()
            loadUserData()
        } else {
            binding.layoutEditProfileForm.isVisible = false
            binding.layoutInfoView.isVisible = true
            
            infoLottieFile?.let { 
                binding.lottieAnimation.setAnimation(it)
                binding.lottieAnimation.playAnimation()
            }
            binding.tvInfoTitle.text = infoTitle
            binding.tvInfoMessage.text = infoMessage
        }
    }

    private fun setupInsets() {
        // Reduced bottom padding to avoid excessive space (common in Apple-like design)
        val density = binding.root.context.resources.displayMetrics.density
        val baseBottomPadding = binding.root.paddingBottom // Get XML padding
        
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            val extraPadding = (8 * density).toInt() // Keep it clear of nav gestures without over-spacing
            view.setPadding(view.paddingLeft, view.paddingTop, view.paddingRight, baseBottomPadding + extraPadding + bars.bottom)
            insets
        }
    }

    private fun setupBehaviors() {
        val bottomSheetBehavior = (dialog as? com.google.android.material.bottomsheet.BottomSheetDialog)?.behavior
        bottomSheetBehavior?.addBottomSheetCallback(object : com.google.android.material.bottomsheet.BottomSheetBehavior.BottomSheetCallback() {
            override fun onStateChanged(bottomSheet: View, newState: Int) {
                if (newState == com.google.android.material.bottomsheet.BottomSheetBehavior.STATE_EXPANDED ||
                    newState == com.google.android.material.bottomsheet.BottomSheetBehavior.STATE_HIDDEN
                ) {
                    bottomSheet.performHapticFeedback(android.view.HapticFeedbackConstants.CONTEXT_CLICK)
                }
                
                if (newState == com.google.android.material.bottomsheet.BottomSheetBehavior.STATE_COLLAPSED ||
                    newState == com.google.android.material.bottomsheet.BottomSheetBehavior.STATE_EXPANDED) {
                    animateHandle(32)
                }
            }

            override fun onSlide(bottomSheet: View, slideOffset: Float) {
                animateHandle(48)
            }
        })
    }

    private fun animateHandle(targetWidthDp: Int) {
        val targetWidthPx = (targetWidthDp * resources.displayMetrics.density).toInt()
        if (binding.dragHandle.layoutParams.width != targetWidthPx) {
            val params = binding.dragHandle.layoutParams
            params.width = targetWidthPx
            binding.dragHandle.layoutParams = params
        }
    }

    private fun setupUI() {
        // Close Button
        binding.btnClose.setOnClickListener {
            dismiss()
        }
        
        // Ensure drag handle is visible
        binding.dragHandle.isVisible = true
        
        // Background views removed from XML for cleaner UI, so no need to hide them here.
        
        // Save Button
        binding.btnSave.setOnClickListener {
            val name = binding.etName.text.toString().trim()
            val email = binding.etEmail.text.toString().trim()
            val phone = binding.etPhone.text.toString().trim()

            if (validateInput(name, email, phone)) {
                viewModel.updateProfile(requireContext(), name, email, phone)
            }
        }
        

        
        // Edit photo
        binding.tvChangePhoto.setOnClickListener {
            it.performHapticFeedback(android.view.HapticFeedbackConstants.CONTEXT_CLICK)
            
            // Temporary: Open generic info sheet instead of edit photo sheet
            val infoSheet = newInstanceForInfo(
                "central_icons_brush.json",
                "Change Photo",
                "This feature is currently under development."
            )
            infoSheet.show(parentFragmentManager, "ChangePhotoInfo")
        }        
    }

    private fun setupObservers() {
        viewModel.userProfile.observe(viewLifecycleOwner) { user ->
            user?.let {
                AuthSession.updateCachedUserProfile(requireContext(), it)
                renderProfileIdentity(it.name)
                
                // Set user info
                binding.etName.setText(it.name)
                binding.etEmail.setText(it.email)
                binding.etEmail.isEnabled = false // Disable editing since it's used for login
                binding.etEmail.alpha = 0.6f
                binding.etPhone.setText(it.phone)
            }
        }

        viewModel.isLoading.observe(viewLifecycleOwner) { isLoading ->
            val hasData = viewModel.userProfile.value != null
            val isInitialLoad = isLoading && !hasData

            binding.progressLoader.isVisible = isInitialLoad
            
            if (mode == Mode.EDIT_PROFILE) {
                // Keep form in layout during first load to avoid bottom-sheet height jump on open animation.
                binding.layoutEditProfileForm.alpha = if (isInitialLoad) 0.45f else 1f
                binding.etName.isEnabled = !isLoading
                binding.tilName.isEnabled = !isLoading
                binding.tvChangePhoto.isEnabled = !isLoading
                binding.tvChangePhoto.isClickable = !isLoading
            }

            // Button state for "Saving..."
            binding.btnSave.isEnabled = !isLoading
            binding.btnSave.text = if (isLoading && hasData) "Saving..." else "Save Changes"
            binding.btnSave.alpha = if (isLoading) 0.7f else 1.0f
        }

        viewModel.errorMessage.observe(viewLifecycleOwner) { error ->
            error?.let {
                Toast.makeText(requireContext(), it, Toast.LENGTH_SHORT).show()
                viewModel.clearError()
                
                // If we have no data (load failed), dismiss the sheet
                if (viewModel.userProfile.value == null) {
                    dismiss()
                }
            }
        }

        viewModel.updateSuccess.observe(viewLifecycleOwner) { success ->
            if (success) {
                parentFragmentManager.setFragmentResult("profile_updated", android.os.Bundle())
                dismiss()
            }
        }
    }
    
    private fun loadUserData() {
        val context = requireContext()
        val sharedPref = requireActivity().getSharedPreferences("gridee_prefs", android.content.Context.MODE_PRIVATE)
        val userId = AuthSession.getUserId(context) ?: sharedPref.getString("user_id", null)
        val isLoggedIn = AuthSession.isAuthenticated(context) || sharedPref.getBoolean("is_logged_in", false)
        
        if (userId != null && isLoggedIn) {
            viewModel.loadUserProfile(context, userId)
        } else {
            Toast.makeText(requireContext(), "User session expired", Toast.LENGTH_SHORT).show()
            dismiss()
        }
    }

    private fun renderProfileIdentity(name: String?) {
        val normalizedName = name?.trim().orEmpty()
        binding.tvUserInitials.text = buildInitials(normalizedName)
    }

    private fun buildInitials(name: String): String {
        if (name.isBlank()) return ""

        return name
            .split(Regex("\\s+"))
            .mapNotNull { word -> word.firstOrNull()?.uppercaseChar() }
            .take(2)
            .joinToString("")
    }
    
    private fun validateInput(name: String, email: String, phone: String): Boolean {
        val sanitizedPhone = phone.filter { it.isDigit() }
        when {
            name.isEmpty() -> {
                binding.tilName.error = "Name is required"
                return false
            }
            name.length < 2 -> {
                binding.tilName.error = "Name must be at least 2 characters"
                return false
            }
            email.isEmpty() -> {
                binding.tilEmail.error = "Email is required"
                return false
            }
            !android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches() -> {
                binding.tilEmail.error = "Invalid email format"
                return false
            }
            sanitizedPhone.isEmpty() -> {
                binding.tilPhone.error = "Phone number is required"
                return false
            }
            sanitizedPhone.length < 10 -> {
                binding.tilPhone.error = "Phone number must be at least 10 digits"
                return false
            }
            else -> {
                binding.tilName.error = null
                binding.tilEmail.error = null
                binding.tilPhone.error = null
                return true
            }
        }
    }
    

    
    private fun showEditPhotoBottomSheet() {
        val bottomSheet = EditPhotoBottomSheet { uri ->
            selectedPhotoUri = uri
            binding.tvUserInitials.visibility = View.GONE
            Toast.makeText(requireContext(), "Photo selected! Save to apply.", Toast.LENGTH_SHORT).show()
        }
        
        bottomSheet.show(childFragmentManager, EditPhotoBottomSheet.TAG)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        const val TAG = "ProfilePageBottomSheet"

        fun newInstance(): ProfilePageBottomSheet {
            return ProfilePageBottomSheet()
        }
        
        fun newInstanceForInfo(
            lottieFile: String,
            title: String,
            message: String
        ): ProfilePageBottomSheet {
            val fragment = ProfilePageBottomSheet()
            fragment.mode = Mode.INFO
            fragment.infoLottieFile = lottieFile
            fragment.infoTitle = title
            fragment.infoMessage = message
            return fragment
        }
    }
}
