package com.gridee.parking.ui.bottomsheet

import android.animation.ValueAnimator
import android.app.Dialog
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.DecelerateInterpolator
import android.widget.FrameLayout
import androidx.activity.result.contract.ActivityResultContracts
import androidx.dynamicanimation.animation.DynamicAnimation
import androidx.dynamicanimation.animation.SpringAnimation
import androidx.dynamicanimation.animation.SpringForce
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.gridee.parking.databinding.BottomSheetEditPhotoBinding

class EditPhotoBottomSheet(
    private val onPhotoSelected: (Uri) -> Unit
) : BottomSheetDialogFragment() {

    private var _binding: BottomSheetEditPhotoBinding? = null
    private val binding get() = _binding!!
    private var selectedPhotoUri: Uri? = null

    // Gallery launcher
    private val galleryLauncher = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            handlePhotoSelected(it)
        }
    }

    // Camera launcher
    private val cameraLauncher = registerForActivityResult(
        ActivityResultContracts.TakePicturePreview()
    ) { bitmap: Bitmap? ->
        bitmap?.let {
            // Convert bitmap to URI (you may want to save it to cache)
            val uri = saveBitmapToCache(it)
            uri?.let { savedUri ->
                handlePhotoSelected(savedUri)
            }
        }
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val dialog = super.onCreateDialog(savedInstanceState) as BottomSheetDialog
        
        dialog.setOnShowListener { dialogInterface ->
            val bottomSheet = (dialogInterface as BottomSheetDialog)
                .findViewById<FrameLayout>(com.google.android.material.R.id.design_bottom_sheet)
            
            bottomSheet?.let { sheet ->
                val behavior = BottomSheetBehavior.from(sheet)
                behavior.state = BottomSheetBehavior.STATE_COLLAPSED
                behavior.skipCollapsed = false
                behavior.isDraggable = true
                
                // Apply spring animation to the bottom sheet
                applySpringAnimation(sheet)
            }
        }
        
        return dialog
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = BottomSheetEditPhotoBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        setupListeners()
        applyBlurAndOpacityAnimation()
    }

    private fun setupListeners() {
        // Gallery button
        binding.btnGallery.setOnClickListener {
            openGallery()
        }
        
        // Camera button
        binding.btnCamera.setOnClickListener {
            openCamera()
        }
        
        // Save button
        binding.btnSave.setOnClickListener {
            selectedPhotoUri?.let { uri ->
                onPhotoSelected(uri)
                dismissWithAnimation()
            }
        }
        
        // Cancel button
        binding.btnCancel.setOnClickListener {
            dismissWithAnimation()
        }
    }

    private fun openGallery() {
        galleryLauncher.launch("image/*")
    }

    private fun openCamera() {
        cameraLauncher.launch(null)
    }

    private fun handlePhotoSelected(uri: Uri) {
        selectedPhotoUri = uri
        
        // Show the photo preview with animation
        binding.ivPhotoPreview.setImageURI(uri)
        
        // Animate the preview card appearing
        binding.cardPhotoPreview.visibility = View.VISIBLE
        binding.cardPhotoPreview.alpha = 0f
        binding.cardPhotoPreview.scaleX = 0.9f
        binding.cardPhotoPreview.scaleY = 0.9f
        
        // Spring animation for scale
        SpringAnimation(binding.cardPhotoPreview, DynamicAnimation.SCALE_X, 1f).apply {
            spring.stiffness = SpringForce.STIFFNESS_MEDIUM
            spring.dampingRatio = SpringForce.DAMPING_RATIO_LOW_BOUNCY
            start()
        }
        
        SpringAnimation(binding.cardPhotoPreview, DynamicAnimation.SCALE_Y, 1f).apply {
            spring.stiffness = SpringForce.STIFFNESS_MEDIUM
            spring.dampingRatio = SpringForce.DAMPING_RATIO_LOW_BOUNCY
            start()
        }
        
        // Alpha animation
        binding.cardPhotoPreview.animate()
            .alpha(1f)
            .setDuration(300)
            .setInterpolator(DecelerateInterpolator())
            .start()
        
        // Show save button with animation
        binding.btnSave.visibility = View.VISIBLE
        binding.btnSave.alpha = 0f
        binding.btnSave.translationY = 20f
        
        binding.btnSave.animate()
            .alpha(1f)
            .translationY(0f)
            .setDuration(300)
            .setInterpolator(DecelerateInterpolator())
            .start()
        
        // Update subtitle
        binding.tvSubtitle.text = "Preview your photo and tap save to confirm"
    }

    private fun saveBitmapToCache(bitmap: Bitmap): Uri? {
        return try {
            val filename = "profile_photo_${System.currentTimeMillis()}.jpg"
            val contentValues = android.content.ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, filename)
                put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
            }
            
            val uri = requireContext().contentResolver.insert(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                contentValues
            )
            
            uri?.let {
                requireContext().contentResolver.openOutputStream(it)?.use { outputStream ->
                    bitmap.compress(Bitmap.CompressFormat.JPEG, 95, outputStream)
                }
            }
            
            uri
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    private fun applySpringAnimation(view: View) {
        val springAnim = SpringAnimation(view, DynamicAnimation.TRANSLATION_Y, 0f).apply {
            spring.stiffness = SpringForce.STIFFNESS_MEDIUM
            spring.dampingRatio = SpringForce.DAMPING_RATIO_LOW_BOUNCY
            
            // Start from below the screen
            view.translationY = view.height.toFloat()
            start()
        }
    }

    private fun applyBlurAndOpacityAnimation() {
        // Get the dim background view and animate opacity with blur effect
        dialog?.window?.let { window ->
            // Animate dim amount (opacity)
            ValueAnimator.ofFloat(0f, 1f).apply {
                duration = 400
                interpolator = DecelerateInterpolator()
                addUpdateListener { animator ->
                    val alpha = animator.animatedValue as Float
                    window.setDimAmount(0.65f * alpha)
                }
                start()
            }
        }
        
        // Animate the bottom sheet content with fade in
        binding.root.alpha = 0f
        binding.root.animate()
            .alpha(1f)
            .setDuration(350)
            .setInterpolator(DecelerateInterpolator())
            .start()
        
        // Stagger animation for option cards
        binding.btnGallery.alpha = 0f
        binding.btnGallery.translationY = 30f
        binding.btnGallery.animate()
            .alpha(1f)
            .translationY(0f)
            .setDuration(300)
            .setStartDelay(50)
            .setInterpolator(DecelerateInterpolator())
            .start()
        
        binding.btnCamera.alpha = 0f
        binding.btnCamera.translationY = 30f
        binding.btnCamera.animate()
            .alpha(1f)
            .translationY(0f)
            .setDuration(300)
            .setStartDelay(100)
            .setInterpolator(DecelerateInterpolator())
            .start()
        
        binding.btnCancel.alpha = 0f
        binding.btnCancel.translationY = 30f
        binding.btnCancel.animate()
            .alpha(1f)
            .translationY(0f)
            .setDuration(300)
            .setStartDelay(150)
            .setInterpolator(DecelerateInterpolator())
            .start()
    }

    private fun dismissWithAnimation() {
        // Animate the bottom sheet sliding down
        val springAnim = SpringAnimation(binding.root, DynamicAnimation.TRANSLATION_Y, binding.root.height.toFloat()).apply {
            spring.stiffness = SpringForce.STIFFNESS_MEDIUM
            spring.dampingRatio = SpringForce.DAMPING_RATIO_MEDIUM_BOUNCY
            
            addEndListener { _, _, _, _ ->
                dismiss()
            }
            
            start()
        }
        
        // Animate opacity fade out
        binding.root.animate()
            .alpha(0f)
            .setDuration(250)
            .setInterpolator(DecelerateInterpolator())
            .start()
        
        // Animate dim background
        dialog?.window?.let { window ->
            ValueAnimator.ofFloat(1f, 0f).apply {
                duration = 250
                addUpdateListener { animator ->
                    val alpha = animator.animatedValue as Float
                    window.setDimAmount(0.65f * alpha)
                }
                start()
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        const val TAG = "EditPhotoBottomSheet"
    }
}
