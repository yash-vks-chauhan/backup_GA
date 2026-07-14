package com.gridee.parking.ui.profile

import android.animation.ValueAnimator
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.view.animation.LinearInterpolator
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.core.content.res.ResourcesCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import com.gridee.parking.R
import com.gridee.parking.data.api.ApiClient
import com.gridee.parking.data.model.SupportTicket
import com.gridee.parking.data.model.SupportTicketMessage
import com.gridee.parking.databinding.ActivityHelpSupportBinding
import com.gridee.parking.ui.base.BaseActivity
import com.gridee.parking.utils.ThemeManager
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone

class HelpSupportActivity : BaseActivity<ActivityHelpSupportBinding>() {

    private companion object {
        val ACTIVE_TICKET_STATUSES = setOf("OPEN", "IN_PROGRESS")
    }

    private var hasLoadedTickets = false
    private var currentTickets: List<SupportTicket> = emptyList()
    private var liveDotAnimator: ValueAnimator? = null
    private val ticketDateFormat by lazy { SimpleDateFormat("dd MMM, h:mm a", Locale.getDefault()) }
    private val istTimeZone: TimeZone by lazy { TimeZone.getTimeZone("Asia/Kolkata") }

    private val deadZonePx by lazy { 16f * resources.displayMetrics.density }
    private val frostRangePx by lazy { 120f * resources.displayMetrics.density }
    private val titleRangePx by lazy { 80f * resources.displayMetrics.density }

    override fun getViewBinding(): ActivityHelpSupportBinding {
        return ActivityHelpSupportBinding.inflate(layoutInflater)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        window.statusBarColor = ContextCompat.getColor(this, R.color.background_primary)
        WindowInsetsControllerCompat(window, window.decorView).isAppearanceLightStatusBars =
            !ThemeManager.isDarkMode(this)

        setupFrostedHeader()
        setupClickListeners()
        refreshTickets()
    }

    override fun onResume() {
        super.onResume()
        if (hasLoadedTickets) {
            refreshTickets(showInlineLoader = false)
        }
    }

    override fun onDestroy() {
        liveDotAnimator?.cancel()
        liveDotAnimator = null
        super.onDestroy()
    }

    private fun setupFrostedHeader() {
        binding.viewToolbarFrost.alpha = 0f
        binding.tvHeaderTitle.alpha = 0f
        binding.tvHeaderTitle.maxLines = 1
        binding.tvHeaderTitle.ellipsize = TextUtils.TruncateAt.END
        binding.tvHeaderTitle.maxWidth = resources.displayMetrics.widthPixels - dp(120)

        // Push content below the floating header once it has been measured.
        binding.layoutHeader.post {
            val headerHeight = binding.layoutHeader.height
            val sidePadding = binding.contentContainer.paddingStart
            binding.contentContainer.setPadding(
                sidePadding,
                headerHeight + (16f * resources.displayMetrics.density).toInt(),
                sidePadding,
                binding.contentContainer.paddingBottom
            )
        }

        binding.scrollContent.setOnScrollChangeListener { _, _, scrollY, _, _ ->
            updateFrostedHeader(scrollY)
        }
    }

    private fun updateFrostedHeader(scrollOffsetPx: Int) {
        val activeScroll = (scrollOffsetPx - deadZonePx).coerceAtLeast(0f)

        val rawFrost = (activeScroll / frostRangePx).coerceIn(0f, 1f)
        val t = 1f - rawFrost
        binding.viewToolbarFrost.alpha = 1f - (t * t * t)

        val rawTitle = (activeScroll / titleRangePx).coerceIn(0f, 1f)
        binding.tvHeaderTitle.alpha = rawTitle * rawTitle

        if (scrollOffsetPx <= 0) {
            binding.viewToolbarFrost.alpha = 0f
            binding.tvHeaderTitle.alpha = 0f
        }
    }

    private fun setupClickListeners() {
        binding.btnBack.setOnClickListener { finish() }
        binding.swipeRefresh.setOnRefreshListener { refreshTickets(showInlineLoader = false) }
        binding.cardStartRequest.setOnClickListener {
            startActivity(Intent(this, NewSupportRequestActivity::class.java))
        }
    }

    private fun refreshTickets(showInlineLoader: Boolean = true) {
        if (showInlineLoader) {
            binding.progressTickets.isVisible = binding.ticketList.childCount == 0
        }
        binding.tvTicketEmpty.isVisible = false

        lifecycleScope.launch {
            try {
                val response = ApiClient.apiService.getMySupportTickets()
                if (response.isSuccessful) {
                    renderTickets(response.body().orEmpty())
                } else {
                    showTicketsError("Unable to load tickets (${response.code()})")
                }
            } catch (e: Exception) {
                showTicketsError(e.message ?: "Unable to load tickets")
            } finally {
                binding.progressTickets.isVisible = false
                binding.swipeRefresh.isRefreshing = false
                hasLoadedTickets = true
            }
        }
    }

    private fun showTicketsError(message: String) {
        binding.ticketList.isVisible = false
        binding.tvTicketEmpty.text = message
        binding.tvTicketEmpty.isVisible = true
    }

    private fun renderTickets(tickets: List<SupportTicket>) {
        currentTickets = tickets.sortedByDescending { it.updatedAt ?: it.createdAt ?: Date(0) }

        val activeTicket = currentTickets.firstOrNull { canReplyToTicket(it) }
        bindActiveConversation(activeTicket)

        // The active ticket is pinned above, so the history list shows the rest.
        val pastTickets = currentTickets.filter { it.id != activeTicket?.id }.take(20)

        binding.ticketList.removeAllViews()

        if (pastTickets.isEmpty()) {
            binding.ticketList.isVisible = false
            // Only nudge the user when there are no tickets at all.
            binding.tvTicketEmpty.text = "No tickets yet — anything you raise will show up here."
            binding.tvTicketEmpty.isVisible = activeTicket == null
            return
        }

        binding.ticketList.isVisible = true
        binding.tvTicketEmpty.isVisible = false

        // Group by date section (Today / Yesterday / This Week / Month Year) like the history pages.
        val groups = LinkedHashMap<String, MutableList<SupportTicket>>()
        pastTickets.forEach { ticket ->
            val date = ticket.updatedAt ?: ticket.createdAt ?: Date(0)
            groups.getOrPut(sectionTitleForDate(date)) { mutableListOf() }.add(ticket)
        }
        groups.forEach { (title, list) ->
            binding.ticketList.addView(buildSectionHeader(title))
            list.forEach { binding.ticketList.addView(createTicketRow(it)) }
        }
    }

    private fun buildSectionHeader(title: String): TextView {
        return TextView(this).apply {
            text = title
            typeface = font(R.font.inter_bold)
            textSize = 15f
            includeFontPadding = false
            setTextColor(ContextCompat.getColor(this@HelpSupportActivity, R.color.text_primary))
            setPadding(dp(4), dp(20), dp(4), dp(8))
        }
    }

    private fun sectionTitleForDate(date: Date): String {
        val now = Calendar.getInstance(istTimeZone)
        val todayStart = startOfDay(now)
        val yesterdayStart = startOfDay((now.clone() as Calendar).apply { add(Calendar.DAY_OF_YEAR, -1) })
        val weekStart = startOfDay((now.clone() as Calendar).apply { add(Calendar.DAY_OF_YEAR, -7) })
        val cal = Calendar.getInstance(istTimeZone).apply { time = date }
        return when {
            cal.timeInMillis >= todayStart.timeInMillis -> "Today"
            cal.timeInMillis >= yesterdayStart.timeInMillis -> "Yesterday"
            cal.timeInMillis >= weekStart.timeInMillis -> "This Week"
            else -> {
                val month = cal.getDisplayName(Calendar.MONTH, Calendar.LONG, Locale.getDefault())
                "$month ${cal.get(Calendar.YEAR)}"
            }
        }
    }

    private fun startOfDay(calendar: Calendar): Calendar {
        return (calendar.clone() as Calendar).apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
    }

    private fun bindActiveConversation(activeTicket: SupportTicket?) {
        val hasActive = activeTicket != null
        binding.cardActiveConversation.isVisible = hasActive
        binding.cardStartRequest.isVisible = !hasActive

        if (activeTicket == null) {
            stopLiveDot()
            return
        }

        binding.tvActiveSubject.text = activeTicket.subject.ifBlank { "Support ticket" }
        binding.tvActiveSnippet.text = buildActiveSnippet(activeTicket)
        styleStatusPill(binding.tvActiveStatus, activeTicket.status)
        buildStatusJourney(statusStage(activeTicket.status))

        binding.cardActiveConversation.setOnClickListener { openTicketConversation(activeTicket) }

        // The breathing dot signals a live conversation, but only while it is in progress.
        if (normalizedStatus(activeTicket.status) == "IN_PROGRESS") {
            binding.viewActiveLiveDot.isVisible = true
            startLiveDot()
        } else {
            binding.viewActiveLiveDot.isVisible = false
            stopLiveDot()
        }
    }

    private fun buildActiveSnippet(ticket: SupportTicket): String {
        val latestMessage = ticket.messages
            .filter { it.message.isNotBlank() }
            .maxByOrNull { it.sentAt ?: Date(0) }
        return when {
            latestMessage != null ->
                "${if (isAdminMessage(latestMessage)) "Support" else "You"}: ${latestMessage.message}"
            ticket.description.isNotBlank() -> ticket.description
            else -> "Continue this conversation until our team closes the ticket."
        }
    }

    private fun startLiveDot() {
        if (liveDotAnimator?.isRunning == true) return
        liveDotAnimator = ValueAnimator.ofFloat(0.4f, 1f).apply {
            duration = 1100L
            interpolator = LinearInterpolator()
            repeatCount = ValueAnimator.INFINITE
            repeatMode = ValueAnimator.REVERSE
            addUpdateListener { binding.viewActiveLiveDot.alpha = it.animatedValue as Float }
            start()
        }
    }

    private fun stopLiveDot() {
        liveDotAnimator?.cancel()
        liveDotAnimator = null
        binding.viewActiveLiveDot.alpha = 1f
    }

    private fun createTicketRow(ticket: SupportTicket): View {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = ContextCompat.getDrawable(this@HelpSupportActivity, R.drawable.bg_ios_row_press)
            isClickable = true
            isFocusable = true
            setPadding(dp(4), dp(12), dp(4), dp(12))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            setOnClickListener { openTicketConversation(ticket) }
        }

        // Leading circular tile with the ticket glyph (matches booking/transaction history rows).
        row.addView(ImageView(this).apply {
            setImageResource(R.drawable.ic_booking_history_ticket)
            setColorFilter(ContextCompat.getColor(this@HelpSupportActivity, R.color.icon_secondary))
            background = ContextCompat.getDrawable(this@HelpSupportActivity, R.drawable.circle_background_grey_light)
            val pad = dp(12)
            setPadding(pad, pad, pad, pad)
            layoutParams = LinearLayout.LayoutParams(dp(44), dp(44)).apply { marginEnd = dp(14) }
        })

        val textColumn = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }

        textColumn.addView(TextView(this).apply {
            text = ticket.subject.ifBlank { "Support ticket" }
            setTextColor(ContextCompat.getColor(this@HelpSupportActivity, R.color.text_primary))
            textSize = 16f
            typeface = font(R.font.inter_semibold)
            includeFontPadding = false
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
        })

        textColumn.addView(TextView(this).apply {
            text = buildString {
                append(formatPriority(ticket.priority)).append(" priority")
                val date = ticket.updatedAt ?: ticket.createdAt
                if (date != null) append("  ·  ").append(ticketDateFormat.format(date))
            }
            setTextColor(ContextCompat.getColor(this@HelpSupportActivity, R.color.text_tertiary))
            textSize = 13f
            typeface = font(R.font.inter_medium)
            setPadding(0, dp(3), 0, 0)
        })

        row.addView(textColumn)
        row.addView(buildStatusPill(ticket.status))

        return row
    }

    private fun openTicketConversation(ticket: SupportTicket) {
        val ticketId = ticket.id
        if (ticketId.isNullOrBlank()) {
            showToast("Unable to open this ticket")
            return
        }
        startActivity(
            Intent(this, SupportTicketChatActivity::class.java)
                .putExtra(SupportTicketChatActivity.EXTRA_TICKET_ID, ticketId)
                .putExtra(SupportTicketChatActivity.EXTRA_TICKET_TITLE, ticket.subject)
        )
    }

    private fun statusStage(status: String): Int {
        return when (normalizedStatus(status)) {
            "RESOLVED", "CLOSED" -> 2
            "IN_PROGRESS" -> 1
            else -> 0 // OPEN
        }
    }

    private fun buildStatusJourney(stage: Int) {
        val container = binding.layoutStatusJourney
        container.removeAllViews()

        val dotsRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }
        for (i in 0..2) {
            dotsRow.addView(makeStepDot(state = if (i < stage) 0 else if (i == stage) 1 else 2))
            if (i < 2) dotsRow.addView(makeStepLine(reached = stage > i))
        }

        val labelsRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dp(8) }
        }
        listOf("Open", "In progress", "Resolved").forEachIndexed { i, label ->
            val isActive = i == stage
            val isReached = i <= stage
            labelsRow.addView(TextView(this).apply {
                text = label
                textSize = 11.5f
                includeFontPadding = false
                typeface = font(if (isActive) R.font.inter_semibold else R.font.inter_medium)
                setTextColor(
                    ContextCompat.getColor(
                        this@HelpSupportActivity,
                        when {
                            isActive -> R.color.brand_primary
                            isReached -> R.color.text_secondary
                            else -> R.color.text_tertiary
                        }
                    )
                )
                gravity = when (i) {
                    0 -> Gravity.START
                    1 -> Gravity.CENTER
                    else -> Gravity.END
                }
                layoutParams =
                    LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            })
        }

        container.addView(dotsRow)
        container.addView(labelsRow)
    }

    /** state: 0 = done, 1 = active (current), 2 = upcoming */
    private fun makeStepDot(state: Int): View {
        val brand = ContextCompat.getColor(this, R.color.brand_primary)
        val muted = ContextCompat.getColor(this, R.color.text_tertiary)
        val sizePx: Int
        val drawable = GradientDrawable().apply { shape = GradientDrawable.OVAL }
        when (state) {
            0 -> { sizePx = dp(8); drawable.setColor(brand) }
            1 -> { sizePx = dp(12); drawable.setColor(brand) }
            else -> { sizePx = dp(9); drawable.setColor(Color.TRANSPARENT); drawable.setStroke(dp(2), muted) }
        }
        return View(this).apply {
            background = drawable
            layoutParams = LinearLayout.LayoutParams(sizePx, sizePx)
        }
    }

    private fun makeStepLine(reached: Boolean): View {
        val color = ContextCompat.getColor(
            this, if (reached) R.color.brand_primary else R.color.divider
        )
        return View(this).apply {
            setBackgroundColor(color)
            layoutParams = LinearLayout.LayoutParams(0, dp(2), 1f).apply {
                marginStart = dp(6)
                marginEnd = dp(6)
            }
        }
    }

    private fun buildStatusPill(status: String): TextView {
        return TextView(this).apply {
            includeFontPadding = false
            textSize = 11.5f
            typeface = font(R.font.inter_medium)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { marginStart = dp(10) }
            styleStatusPill(this, status)
        }
    }

    private fun styleStatusPill(view: TextView, status: String) {
        val tint = statusColor(status)
        view.text = formatStatusLabel(status)
        view.setTextColor(tint)
        view.setPadding(dp(10), dp(5), dp(10), dp(5))
        view.background = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = 100f * resources.displayMetrics.density
            setColor(Color.argb(28, Color.red(tint), Color.green(tint), Color.blue(tint)))
        }
    }

    private fun statusColor(status: String): Int {
        return when (normalizedStatus(status)) {
            "RESOLVED", "CLOSED" -> ContextCompat.getColor(this, R.color.success_green)
            "IN_PROGRESS" -> ContextCompat.getColor(this, R.color.brand_primary)
            else -> ContextCompat.getColor(this, R.color.text_secondary)
        }
    }

    private fun canReplyToTicket(ticket: SupportTicket): Boolean {
        return normalizedStatus(ticket.status) in ACTIVE_TICKET_STATUSES
    }

    private fun normalizedStatus(status: String): String {
        return status.trim().uppercase(Locale.getDefault()).ifBlank { "OPEN" }
    }

    private fun isAdminMessage(message: SupportTicketMessage): Boolean {
        return message.senderRole?.trim()?.uppercase(Locale.getDefault()) == "ADMIN"
    }

    private fun formatStatusLabel(status: String): String {
        return status.trim()
            .replace('_', ' ')
            .split(Regex("\\s+"))
            .filter { it.isNotBlank() }
            .joinToString(" ") { word ->
                word.lowercase(Locale.getDefault())
                    .replaceFirstChar { it.titlecase(Locale.getDefault()) }
            }
            .ifBlank { "Open" }
    }

    private fun formatPriority(priority: String): String {
        return priority.trim().lowercase(Locale.getDefault())
            .replaceFirstChar { it.titlecase(Locale.getDefault()) }
            .ifBlank { "Medium" }
    }

    private fun font(resId: Int) = ResourcesCompat.getFont(this, resId)

    private fun showToast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }

    private fun dp(value: Int): Int {
        return (value * resources.displayMetrics.density).toInt()
    }
}
