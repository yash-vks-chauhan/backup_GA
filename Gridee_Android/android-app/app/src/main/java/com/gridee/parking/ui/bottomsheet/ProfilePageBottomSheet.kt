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
import com.gridee.parking.databinding.BottomSheetProfilePageBinding
import com.gridee.parking.ui.profile.EditProfileViewModel

class ProfilePageBottomSheet : BottomSheetDialogFragment() {

    private var _binding: BottomSheetProfilePageBinding? = null
    private val binding get() = _binding!!

    private lateinit var viewModel: EditProfileViewModel

    private var selectedPhotoUri: android.net.Uri? = null

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
                // Remove default background to use ours
                sheet.background = null 
                
                // Force the sheet to extend to the edge
                sheet.fitsSystemWindows = false
                
                // Remove any margins that might lift the sheet up
                val params = sheet.layoutParams as? ViewGroup.MarginLayoutParams
                params?.setMargins(0, 0, 0, 0)
                sheet.layoutParams = params
                
                // Prevent system from padding the sheet automatically
                ViewCompat.setOnApplyWindowInsetsListener(sheet) { view, insets ->
                    view.setPadding(0, 0, 0, 0)
                    insets
                }
            }
            
            // Ensure behavior ignores gesture insets
            bottomSheetDialog.behavior.isGestureInsetBottomIgnored = true
            
            // Force fully expanded state
            bottomSheetDialog.behavior.state = com.google.android.material.bottomsheet.BottomSheetBehavior.STATE_EXPANDED
            bottomSheetDialog.behavior.skipCollapsed = true
            
            bottomSheetDialog.window?.let { window ->
                WindowCompat.setDecorFitsSystemWindows(window, false)
                val navBarColor = ContextCompat.getColor(requireContext(), com.gridee.parking.R.color.white)
                window.navigationBarColor = navBarColor
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
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            val initialPadding = (32 * view.context.resources.displayMetrics.density).toInt()
            view.setPadding(view.paddingLeft, view.paddingTop, view.paddingRight, initialPadding + bars.bottom)
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
                viewModel.updateProfile(name, email, phone)
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
                // Set user initials
                val initials = it.name.split(" ").mapNotNull { name -> 
                    name.firstOrNull()?.uppercaseChar() 
                }.take(2).joinToString("")
                binding.tvUserInitials.text = if (initials.isNotEmpty()) initials else "U"
                
                // Set user info
                binding.etName.setText(it.name)
                binding.etEmail.setText(it.email)
                binding.etPhone.setText(it.phone)
            }
        }

        viewModel.isLoading.observe(viewLifecycleOwner) { isLoading ->
            val hasData = viewModel.userProfile.value != null
            val isInitialLoad = isLoading && !hasData

            binding.progressLoader.isVisible = isInitialLoad
            
            if (mode == Mode.EDIT_PROFILE) {
                binding.layoutEditProfileForm.isVisible = !isInitialLoad
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
                Toast.makeText(requireContext(), "Profile updated successfully", Toast.LENGTH_SHORT).show()
                dismiss()
            }
        }
    }
    
    private fun loadUserData() {
        val sharedPref = requireActivity().getSharedPreferences("gridee_prefs", android.content.Context.MODE_PRIVATE)
        val userId = sharedPref.getString("user_id", null)
        val isLoggedIn = sharedPref.getBoolean("is_logged_in", false)
        
        if (userId != null && isLoggedIn) {
            viewModel.loadUserProfile(userId)
        } else {
            Toast.makeText(requireContext(), "User session expired", Toast.LENGTH_SHORT).show()
            dismiss()
        }
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
