package com.gridee.parking.ui.bottomsheet

import android.os.Bundle
import android.view.HapticFeedbackConstants
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.annotation.StringRes
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import com.gridee.parking.R
import com.gridee.parking.data.api.ApiClient
import com.gridee.parking.data.model.CreateSupportTicketRequest
import com.gridee.parking.databinding.BottomSheetPartnerReferralBinding
import com.gridee.parking.utils.AuthSession
import com.gridee.parking.utils.NotificationHelper
import kotlinx.coroutines.launch

/**
 * "Refer a Parking Space" — four lines of copy and a form.
 *
 * The screen deliberately does not sell. It states what can be referred, what
 * happens if the referral is verified, and who is not eligible to refer at all;
 * it names no amount, ranks no tier, and hands the reader no line to take to a
 * decision-maker. A referral here is an introduction, and an introduction is all
 * the page asks for.
 *
 * Opens as a full page on the same Lift & Settle motion as the parking-spot sheet
 * (see [FullPageBottomSheetFragment]).
 *
 * A referral is filed through the existing support-ticket API rather than an
 * endpoint of its own — that is what the sheet promises the user ("expect a reply
 * through your support tickets"), and it means the team already has a thread to
 * reply in.
 */
class PartnerReferralBottomSheet : FullPageBottomSheetFragment() {

    private var _binding: BottomSheetPartnerReferralBinding? = null
    private val binding get() = _binding!!

    private var isSubmitting = false

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = BottomSheetPartnerReferralBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupInsets()
        setupCollapsingHeader()
        setupActions()
    }

    // ── Actions ──────────────────────────────────────────────────────────────

    private fun setupActions() {
        binding.btnClose.setOnClickListener { dismiss() }

        binding.btnSendReferral.setOnClickListener {
            it.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
            submitReferral()
        }
    }

    // ── Collapsing header ────────────────────────────────────────────────────

    /**
     * iOS-style large-title collapse.
     *
     * The whole effect is driven by one number: where the large title physically is
     * relative to the bar's lower edge. Nothing keys off a fixed scroll distance, so
     * it stays correct at any font scale, locale or keyboard state — and because the
     * progress is continuous there is no threshold for the swap to pop at.
     *
     * The choreography deliberately isn't a crossfade of two equals:
     *  1. The title holds full opacity while it travels up the page. It is passing
     *     *behind* glass, not evaporating in open space — the frost's gradient does
     *     the dissolving, which is what makes the material read as material.
     *  2. It only gives up its alpha across its own height, as the bar covers it.
     *  3. The inline copy arrives on the back half of that same window, rising the
     *     last few dp. So the two titles never share the screen at equal weight;
     *     one hands off to the other.
     */
    private fun setupCollapsingHeader() {
        binding.scrollContent.setOnScrollChangeListener { _, _, scrollY, _, _ ->
            applyHeaderCollapse(scrollY)
        }
        // The title's measured position drives everything, so re-settle whenever it
        // gets one — first layout, font-scale change, keyboard resize.
        binding.tvTitleLarge.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ ->
            _binding?.let { applyHeaderCollapse(it.scrollContent.scrollY) }
        }
        applyHeaderCollapse(0)
    }

    /** Smoothstep — removes the linear ramp's visible start and stop. */
    private fun ease(t: Float): Float = t * t * (3f - 2f * t)

    private fun applyHeaderCollapse(scrollY: Int) {
        val binding = _binding ?: return
        val density = resources.displayMetrics.density
        val barBottom = HEADER_BAR_BOTTOM_DP * density

        // Frost: 6dp dead zone, then a 64dp ramp on ease-out cubic (the profile
        // toolbar's curve, shortened). Tuned against the handoff below — the bar has
        // to be nearly solid by the time the inline title lands on it, or the title
        // appears to float on nothing.
        val active = (scrollY - 6f * density).coerceAtLeast(0f)
        val rawFrost = (active / (64f * density)).coerceIn(0f, 1f)
        val inverse = 1f - rawFrost
        binding.headerFrost.alpha = 1f - (inverse * inverse * inverse)

        val largeTitle = binding.tvTitleLarge
        val inlineTitle = binding.tvTitleInline
        if (largeTitle.height == 0) {
            // Not laid out yet — hold the rest state rather than compute a handoff
            // from a zero-height title, which would read as fully collapsed.
            largeTitle.alpha = 1f
            inlineTitle.alpha = 0f
            return
        }

        val contentTop = (largeTitle.parent as? View)?.top ?: 0
        val titleTop = (contentTop + largeTitle.top - scrollY).toFloat()

        // 0 the instant the title's top edge touches the bar; 1 once it has travelled
        // its own height past it — i.e. exactly while the bar is swallowing it.
        val travel = largeTitle.height.toFloat()
        val handoff = ((barBottom - titleTop) / travel).coerceIn(0f, 1f)

        largeTitle.alpha = 1f - ease(handoff)

        // Back half only, so the inline copy is arriving as the large one leaves
        // rather than alongside it.
        val arrival = ease(((handoff - 0.5f) / 0.5f).coerceIn(0f, 1f))
        inlineTitle.alpha = arrival
        inlineTitle.translationY = (1f - arrival) * 5f * density
    }
    /**
     * The sheet runs edge-to-edge at full height, so the form owns its own bottom
     * inset: the scroll view is padded by whichever is taller — the gesture bar or
     * the keyboard — which is what lets a focused field near the bottom scroll clear
     * of the IME instead of sitting behind it.
     *
     * Posted so the view is attached to `design_bottom_sheet` first. The sheet's top
     * padding is set from the status-bar inset explicitly rather than inheriting
     * whatever Material happened to apply before we replaced its listener — the
     * header sits in that band, so it can't depend on listener ordering.
     */
    private fun setupInsets() {
        binding.root.post {
            val sheet = (_binding?.root?.parent as? View) ?: return@post

            ViewCompat.setOnApplyWindowInsetsListener(sheet) { v, insets ->
                val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
                val ime = insets.getInsets(WindowInsetsCompat.Type.ime())
                // Bottom only — the scroll view's top padding is the header's
                // clearance and is owned by the layout, not by insets.
                _binding?.scrollContent?.let {
                    it.setPadding(
                        it.paddingLeft,
                        it.paddingTop,
                        it.paddingRight,
                        maxOf(bars.bottom, ime.bottom)
                    )
                }

                v.setPadding(v.paddingLeft, bars.top, v.paddingRight, 0)

                // Consume the bottom inset so Material doesn't also shift the sheet.
                WindowInsetsCompat.Builder(insets)
                    .setInsets(
                        WindowInsetsCompat.Type.systemBars(),
                        androidx.core.graphics.Insets.of(bars.left, bars.top, bars.right, 0)
                    )
                    .build()
            }
            ViewCompat.requestApplyInsets(sheet)
        }
    }

    // ── Submit ───────────────────────────────────────────────────────────────

    private fun submitReferral() {
        if (isSubmitting) return

        val orgName = binding.etOrgName.text?.toString()?.trim().orEmpty()
        val contactPerson = binding.etContactPerson.text?.toString()?.trim().orEmpty()
        val contactNumber = binding.etContactNumber.text?.toString()?.trim().orEmpty()
        val orgType = binding.etOrgType.text?.toString()?.trim().orEmpty()
        val city = binding.etCity.text?.toString()?.trim().orEmpty()
        val notes = binding.etNotes.text?.toString()?.trim().orEmpty()

        // Organisation, a person to call, and a reachable number are the minimum
        // the partnerships team needs to act on a lead.
        when {
            orgName.length < 2 -> {
                focusField(binding.etOrgName, R.string.partner_error_org_name)
                return
            }
            contactPerson.length < 2 -> {
                focusField(binding.etContactPerson, R.string.partner_error_contact_person)
                return
            }
            contactNumber.filter { it.isDigit() }.length < 10 -> {
                focusField(binding.etContactNumber, R.string.partner_error_contact_number)
                return
            }
        }

        // A referral is filed as a support ticket rather than through an endpoint
        // of its own: that is what the sheet promises the reader ("a reply through
        // your support tickets"), and it means the lead lands in the same admin
        // queue the team already works instead of somewhere nobody is watching.
        val description = buildString {
            appendLine(getString(R.string.partner_ticket_intro))
            appendLine()
            appendLine("${getString(R.string.partner_field_org_name)}: $orgName")
            appendLine("${getString(R.string.partner_field_contact_person)}: $contactPerson")
            appendLine("${getString(R.string.partner_field_contact_number)}: $contactNumber")
            if (orgType.isNotEmpty()) {
                appendLine("${getString(R.string.partner_field_org_type)}: $orgType")
            }
            if (city.isNotEmpty()) {
                appendLine("${getString(R.string.partner_field_city)}: $city")
            }
            if (notes.isNotEmpty()) {
                appendLine()
                appendLine("${getString(R.string.partner_field_notes)}: $notes")
            }
        }

        setSubmitting(true)

        lifecycleScope.launch {
            try {
                val response = ApiClient.apiService.createSupportTicket(
                    CreateSupportTicketRequest(
                        subject = getString(R.string.partner_ticket_subject, orgName),
                        description = description,
                        priority = "MEDIUM",
                        parkingLotId = AuthSession.getParkingLotId(requireContext()),
                        parkingLotName = AuthSession.getParkingLotName(requireContext())
                    )
                )

                if (!isAdded) return@launch

                if (response.isSuccessful && response.body() != null) {
                    // Post the confirmation onto the host so it survives this
                    // sheet being dismissed.
                    val host = activity?.window?.decorView as? ViewGroup
                    dismiss()
                    host?.let {
                        NotificationHelper.showSuccess(
                            parent = it,
                            title = getString(R.string.partner_success_title),
                            message = getString(R.string.partner_success_message),
                            duration = 4000L
                        )
                    }
                } else {
                    setSubmitting(false)
                    notify(getString(R.string.partner_error_submit), isError = true)
                }
            } catch (e: Exception) {
                if (!isAdded) return@launch
                setSubmitting(false)
                notify(e.message ?: getString(R.string.partner_error_submit), isError = true)
            }
        }
    }

    /**
     * A validation error can still land on a field that is scrolled out of view on a
     * short screen with the keyboard up. Scroll to it before complaining about it.
     */
    private fun focusField(field: View, @StringRes message: Int) {
        binding.scrollContent.smoothScrollTo(0, (field.parent as? View)?.top?.minus(dp(80)) ?: 0)
        field.requestFocus()
        notify(getString(message), isError = true)
    }

    private fun setSubmitting(submitting: Boolean) {
        isSubmitting = submitting
        _binding?.btnSendReferral?.apply {
            isEnabled = !submitting
            text = getString(
                if (submitting) R.string.submitting else R.string.partner_referral_submit
            )
        }
    }

    private fun notify(message: String, isError: Boolean) {
        val parent = _binding?.root ?: return
        if (isError) {
            NotificationHelper.showError(parent = parent, message = message, duration = 3000L)
        } else {
            NotificationHelper.showInfo(parent = parent, message = message, duration = 3000L)
        }
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        const val TAG = "PartnerReferralBottomSheet"

        // Lower edge of the header bar's solid band, measured from the top of the
        // sheet's content. The close button sits 16dp down and is 32dp tall, so 52dp
        // clears it by 4dp — the line the large title disappears behind.
        private const val HEADER_BAR_BOTTOM_DP = 52f

    }
}
