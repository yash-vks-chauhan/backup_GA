package com.gridee.parking.ui.profile

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Bundle
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.annotation.ColorRes
import androidx.core.content.ContextCompat
import androidx.core.content.res.ResourcesCompat
import androidx.core.view.isVisible
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.gridee.parking.R
import com.gridee.parking.databinding.BottomSheetTicketDetailsBinding
import com.gridee.parking.ui.adapters.SupportConversationBuilder
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Everything about a support request that isn't the conversation.
 *
 * The chat header can only carry the subject and a status word; this holds the facts
 * underneath it — the reference to quote elsewhere, when it was raised and last
 * touched, where it happened, and how far along it is.
 *
 * Deliberately takes plain fields rather than a [com.gridee.parking.data.model.SupportTicket]:
 * the model isn't Parcelable, and a sheet that survives process death has to rebuild
 * from its arguments. Priority is not among them on purpose — it is an internal triage
 * field, and showing it invites the user to argue with it.
 */
class TicketDetailsBottomSheet : BottomSheetDialogFragment() {

    private var _binding: BottomSheetTicketDetailsBinding? = null
    private val binding get() = _binding!!

    companion object {
        const val TAG = "TicketDetailsSheet"

        private const val ARG_TICKET_ID = "ticket_id"
        private const val ARG_SUBJECT = "subject"
        private const val ARG_STATUS = "status"
        private const val ARG_CREATED_AT = "created_at"
        private const val ARG_UPDATED_AT = "updated_at"
        private const val ARG_LOCATION = "location"

        /** Sentinel for "this ticket has no such date", since Bundle longs aren't nullable. */
        private const val NO_DATE = -1L

        fun newInstance(
            ticketId: String?,
            subject: String,
            status: String,
            createdAt: Date?,
            updatedAt: Date?,
            location: String?
        ): TicketDetailsBottomSheet {
            return TicketDetailsBottomSheet().apply {
                arguments = Bundle().apply {
                    putString(ARG_TICKET_ID, ticketId)
                    putString(ARG_SUBJECT, subject)
                    putString(ARG_STATUS, status)
                    putLong(ARG_CREATED_AT, createdAt?.time ?: NO_DATE)
                    putLong(ARG_UPDATED_AT, updatedAt?.time ?: NO_DATE)
                    putString(ARG_LOCATION, location)
                }
            }
        }
    }

    private val dateFormat by lazy { SimpleDateFormat("d MMM yyyy, h:mm a", Locale.getDefault()) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setStyle(STYLE_NORMAL, R.style.BottomSheetDialogTheme)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = BottomSheetTicketDetailsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val args = arguments ?: return
        val subject = args.getString(ARG_SUBJECT).orEmpty()
        val status = args.getString(ARG_STATUS).orEmpty()
        val reference = SupportConversationBuilder.shortReference(args.getString(ARG_TICKET_ID))

        binding.tvDetailsSubject.isVisible = subject.isNotBlank()
        binding.tvDetailsSubject.text = subject

        buildStatusTimeline(stageOf(status))

        // A row with nothing to say is removed rather than shown empty — an em dash
        // in a value column reads as a loading failure, not as "not applicable".
        bindRow(binding.rowReference, binding.tvDetailsReference, reference?.let { "#$it" })
        bindRow(binding.rowOpened, binding.tvDetailsOpened, formatDate(args.getLong(ARG_CREATED_AT)))
        bindRow(binding.rowUpdated, binding.tvDetailsUpdated, formatDate(args.getLong(ARG_UPDATED_AT)))
        bindRow(binding.rowLocation, binding.tvDetailsLocation, args.getString(ARG_LOCATION)?.trim())

        binding.rowReference.setOnClickListener { row ->
            val value = reference ?: return@setOnClickListener
            row.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
            copyReference(value)
        }
    }

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }

    private fun bindRow(row: View, value: TextView, text: String?) {
        row.isVisible = !text.isNullOrBlank()
        value.text = text.orEmpty()
    }

    private fun formatDate(millis: Long): String? {
        return if (millis == NO_DATE) null else dateFormat.format(Date(millis))
    }

    private fun copyReference(reference: String) {
        val context = context ?: return
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("Support reference", reference))
        // Android 13+ shows its own clipboard confirmation overlay.
        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.S_V2) {
            Toast.makeText(context, R.string.copied, Toast.LENGTH_SHORT).show()
        }
    }

    // -----------------------------------------------------------------------
    // Status timeline
    // -----------------------------------------------------------------------

    /** 0 = Open, 1 = In progress, 2 = Resolved. Mirrors the chat's status handling. */
    private fun stageOf(status: String): Int {
        return when (status.trim().uppercase(Locale.getDefault())) {
            "RESOLVED", "CLOSED" -> 2
            "IN_PROGRESS" -> 1
            else -> 0
        }
    }

    private fun buildStatusTimeline(stage: Int) {
        val context = context ?: return
        val container = binding.layoutStatusTimeline
        container.removeAllViews()

        val dots = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            // The row is decorative: the labels underneath already name every stage,
            // so letting TalkBack walk three unlabelled dots adds nothing.
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
        }
        for (index in 0..2) {
            dots.addView(stepDot(reached = index < stage, isCurrent = index == stage))
            if (index < 2) dots.addView(stepLine(reached = stage > index))
        }

        val labels = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dp(9) }
        }
        val stageLabels = listOf(
            R.string.support_details_stage_open,
            R.string.support_details_stage_in_progress,
            R.string.support_details_stage_resolved
        )
        stageLabels.forEachIndexed { index, labelRes ->
            val isCurrent = index == stage
            labels.addView(TextView(context).apply {
                setText(labelRes)
                textSize = 11.5f
                includeFontPadding = false
                typeface = ResourcesCompat.getFont(
                    context, if (isCurrent) R.font.inter_semibold else R.font.inter_medium
                )
                setTextColor(
                    color(
                        when {
                            isCurrent -> R.color.brand_primary
                            index < stage -> R.color.text_secondary
                            else -> R.color.text_tertiary
                        }
                    )
                )
                gravity = when (index) {
                    0 -> Gravity.START
                    1 -> Gravity.CENTER
                    else -> Gravity.END
                }
                layoutParams =
                    LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            })
        }

        container.addView(dots)
        container.addView(labels)
    }

    private fun stepDot(reached: Boolean, isCurrent: Boolean): View {
        val context = requireContext()
        val size = if (isCurrent) dp(12) else dp(9)
        val shape = GradientDrawable().apply {
            this.shape = GradientDrawable.OVAL
            if (reached || isCurrent) {
                setColor(color(R.color.brand_primary))
            } else {
                // Hollow, not filled-grey: an upcoming stage hasn't happened, and a
                // solid dot reads as one that has.
                setColor(Color.TRANSPARENT)
                setStroke(dp(2), color(R.color.text_tertiary))
            }
        }
        return View(context).apply {
            background = shape
            layoutParams = LinearLayout.LayoutParams(size, size)
        }
    }

    private fun stepLine(reached: Boolean): View {
        return View(requireContext()).apply {
            setBackgroundColor(color(if (reached) R.color.brand_primary else R.color.divider))
            layoutParams = LinearLayout.LayoutParams(0, dp(2), 1f).apply {
                marginStart = dp(6)
                marginEnd = dp(6)
            }
        }
    }

    private fun color(@ColorRes resId: Int) = ContextCompat.getColor(requireContext(), resId)

    private fun dp(value: Int): Int {
        return (value * resources.displayMetrics.density).toInt()
    }
}
