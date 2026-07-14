package com.gridee.parking.ui.profile

import android.animation.ObjectAnimator
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.core.content.res.ResourcesCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.lifecycleScope
import com.gridee.parking.R
import com.gridee.parking.data.api.ApiClient
import com.gridee.parking.data.model.CreateSupportTicketRequest
import com.gridee.parking.databinding.ActivityNewSupportRequestBinding
import com.gridee.parking.ui.base.BaseActivity
import com.gridee.parking.utils.AuthSession
import com.gridee.parking.utils.ThemeManager
import kotlinx.coroutines.launch

class NewSupportRequestActivity : BaseActivity<ActivityNewSupportRequestBinding>() {

    private val priorityOrder = listOf("LOW", "MEDIUM", "HIGH")
    private var selectedPriority: String = "MEDIUM"
    private var isSubmitting = false
    private var sliderReady = false

    override fun getViewBinding(): ActivityNewSupportRequestBinding {
        return ActivityNewSupportRequestBinding.inflate(layoutInflater)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        window.statusBarColor = ContextCompat.getColor(this, R.color.background_primary)
        WindowInsetsControllerCompat(window, window.decorView).isAppearanceLightStatusBars =
            !ThemeManager.isDarkMode(this)

        binding.btnBack.setOnClickListener { finish() }
        binding.btnSubmitTicket.setOnClickListener { submitTicket() }

        setupPrioritySegments()
        binding.etSubject.requestFocus()
    }

    private fun setupPrioritySegments() {
        priorityFrames.forEachIndexed { index, frame ->
            frame.setOnClickListener { selectPriority(index, animate = true) }
        }
        // Position the pill once the track has been measured.
        binding.priorityTrack.post {
            selectPriority(priorityOrder.indexOf(selectedPriority).coerceAtLeast(0), animate = false)
        }
    }

    private val priorityFrames get() = listOf(binding.segLow, binding.segMedium, binding.segHigh)
    private val priorityLabels get() = listOf(binding.tvSegLow, binding.tvSegMedium, binding.tvSegHigh)

    private fun selectPriority(index: Int, animate: Boolean) {
        selectedPriority = priorityOrder[index]

        val selectedColor = ContextCompat.getColor(this, R.color.segment_text_selected)
        val unselectedColor = ContextCompat.getColor(this, R.color.segment_text_unselected)
        priorityLabels.forEachIndexed { i, tv ->
            val isSelected = i == index
            tv.setTextColor(if (isSelected) selectedColor else unselectedColor)
            tv.typeface = ResourcesCompat.getFont(
                this, if (isSelected) R.font.inter_bold else R.font.inter_medium
            )
        }

        // Snap the sliding pill exactly to the chosen segment's measured bounds.
        val target = priorityFrames[index]
        if (target.width == 0) return // not laid out yet; the post() pass handles it

        val slider = binding.prioritySlider
        if (slider.width != target.width) {
            slider.layoutParams = slider.layoutParams.apply { width = target.width }
        }
        slider.visibility = View.VISIBLE
        val targetX = target.left.toFloat()
        if (animate && slider.translationX != targetX) {
            ObjectAnimator.ofFloat(slider, View.TRANSLATION_X, slider.translationX, targetX).apply {
                duration = 240L
                interpolator = AccelerateDecelerateInterpolator()
                start()
            }
        } else {
            slider.translationX = targetX
        }
    }

    private fun submitTicket() {
        if (isSubmitting) return

        val subject = binding.etSubject.text.toString().trim()
        val description = binding.etDescription.text.toString().trim()

        when {
            subject.length < 3 -> {
                binding.etSubject.requestFocus()
                showToast("Please enter a clear subject")
                return
            }
            description.length < 10 -> {
                binding.etDescription.requestFocus()
                showToast("Please describe the issue in detail")
                return
            }
        }

        isSubmitting = true
        binding.btnSubmitTicket.isEnabled = false
        binding.btnSubmitTicket.text = "Submitting…"

        lifecycleScope.launch {
            try {
                val response = ApiClient.apiService.createSupportTicket(
                    CreateSupportTicketRequest(
                        subject = subject,
                        description = description,
                        priority = selectedPriority,
                        parkingLotId = AuthSession.getParkingLotId(this@NewSupportRequestActivity),
                        parkingLotName = AuthSession.getParkingLotName(this@NewSupportRequestActivity)
                    )
                )

                if (response.isSuccessful && response.body() != null) {
                    val createdTicket = response.body()!!
                    val ticketId = createdTicket.id
                    showToast("Support ticket created")
                    if (!ticketId.isNullOrBlank()) {
                        startActivity(
                            Intent(this@NewSupportRequestActivity, SupportTicketChatActivity::class.java)
                                .putExtra(SupportTicketChatActivity.EXTRA_TICKET_ID, ticketId)
                                .putExtra(SupportTicketChatActivity.EXTRA_TICKET_TITLE, createdTicket.subject)
                        )
                    }
                    setResult(RESULT_OK)
                    finish()
                } else {
                    showToast("Unable to create ticket (${response.code()})")
                }
            } catch (e: Exception) {
                showToast(e.message ?: "Unable to create ticket")
            } finally {
                isSubmitting = false
                binding.btnSubmitTicket.isEnabled = true
                binding.btnSubmitTicket.text = "Submit request"
            }
        }
    }

    private fun showToast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }
}
