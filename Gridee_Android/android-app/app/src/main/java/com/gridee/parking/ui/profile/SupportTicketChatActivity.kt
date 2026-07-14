package com.gridee.parking.ui.profile

import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.View
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
import com.gridee.parking.data.model.AddSupportTicketMessageRequest
import com.gridee.parking.data.model.SupportTicket
import com.gridee.parking.data.model.SupportTicketMessage
import com.gridee.parking.databinding.ActivitySupportTicketChatBinding
import com.gridee.parking.ui.base.BaseActivity
import com.gridee.parking.utils.ThemeManager
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class SupportTicketChatActivity : BaseActivity<ActivitySupportTicketChatBinding>() {

    companion object {
        const val EXTRA_TICKET_ID = "extra_ticket_id"
        const val EXTRA_TICKET_TITLE = "extra_ticket_title"

        private const val LIVE_TICKET_REFRESH_MS = 1_500L
        private val ACTIVE_TICKET_STATUSES = setOf("OPEN", "IN_PROGRESS")
        private val CLOSED_TICKET_STATUSES = setOf("RESOLVED", "CLOSED")
    }

    private enum class MessageTickStatus {
        SENDING,
        DELIVERED,
        SEEN
    }

    private data class PendingSupportMessage(
        val ticketId: String,
        val message: String,
        val sentAt: Date
    )

    private lateinit var ticketId: String
    private var currentTicket: SupportTicket? = null
    private var pendingOutgoingMessages: List<PendingSupportMessage> = emptyList()
    private var liveTicketJob: Job? = null
    private var isLiveRefreshInFlight = false
    private var isSubmittingMessage = false
    private val ticketDateFormat by lazy { SimpleDateFormat("dd MMM, h:mm a", Locale.getDefault()) }
    private val messageTimeFormat by lazy { SimpleDateFormat("h:mm a", Locale.getDefault()) }

    override fun getViewBinding(): ActivitySupportTicketChatBinding {
        return ActivitySupportTicketChatBinding.inflate(layoutInflater)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        window.statusBarColor = ContextCompat.getColor(this, R.color.background_primary)
        WindowInsetsControllerCompat(window, window.decorView).isAppearanceLightStatusBars =
            !ThemeManager.isDarkMode(this)

        ticketId = intent.getStringExtra(EXTRA_TICKET_ID).orEmpty()
        if (ticketId.isBlank()) {
            showToast("Unable to open this ticket")
            finish()
            return
        }

        binding.tvChatTitle.text = intent.getStringExtra(EXTRA_TICKET_TITLE)
            ?.takeIf { it.isNotBlank() }
            ?: "Support ticket"
        binding.tvChatStatus.text = "Loading"
        binding.tvChatMeta.text = "Loading conversation"

        binding.btnBack.setOnClickListener { finish() }
        binding.btnSendReply.setOnClickListener { submitTicketMessage() }

        loadTicket(showLoader = true)
    }

    override fun onStart() {
        super.onStart()
        startLiveTicketUpdates()
    }

    override fun onStop() {
        stopLiveTicketUpdates()
        super.onStop()
    }

    private fun loadTicket(showLoader: Boolean) {
        if (showLoader) {
            binding.progressTicket.isVisible = true
            binding.tvEmptyState.isVisible = false
        }

        lifecycleScope.launch {
            try {
                val response = ApiClient.apiService.getSupportTicket(ticketId)
                val ticket = response.body()
                if (response.isSuccessful && ticket != null) {
                    renderTicket(ticket, shouldScrollToBottom = true)
                } else {
                    showLoadError("Unable to load ticket (${response.code()})")
                }
            } catch (e: Exception) {
                showLoadError(e.message ?: "Unable to load ticket")
            } finally {
                binding.progressTicket.isVisible = false
            }
        }
    }

    private fun showLoadError(message: String) {
        binding.tvEmptyState.text = message
        binding.tvEmptyState.isVisible = true
        binding.replyComposer.isVisible = false
        binding.tvClosedNotice.isVisible = false
    }

    private fun submitTicketMessage() {
        if (isSubmittingMessage) return

        val ticket = currentTicket
        if (ticket == null) {
            showToast("Ticket is still loading")
            return
        }

        if (!canReplyToTicket(ticket)) {
            showToast("This ticket is closed")
            return
        }

        val message = binding.etReply.text.toString().trim()
        if (message.length < 2) {
            binding.etReply.requestFocus()
            showToast("Please enter a reply")
            return
        }

        val pendingMessage = PendingSupportMessage(
            ticketId = ticketId,
            message = message,
            sentAt = Date()
        )
        pendingOutgoingMessages = pendingOutgoingMessages + pendingMessage
        binding.etReply.text.clear()
        renderTicket(ticket, shouldScrollToBottom = true)

        isSubmittingMessage = true
        binding.btnSendReply.isEnabled = false
        binding.btnSendReply.text = "Sending"

        lifecycleScope.launch {
            try {
                val response = ApiClient.apiService.addSupportTicketMessage(
                    ticketId,
                    AddSupportTicketMessageRequest(message = message)
                )

                if (response.isSuccessful && response.body() != null) {
                    pendingOutgoingMessages = pendingOutgoingMessages - pendingMessage
                    renderTicket(response.body()!!, shouldScrollToBottom = true)
                } else {
                    pendingOutgoingMessages = pendingOutgoingMessages - pendingMessage
                    binding.etReply.setText(message)
                    renderTicket(ticket, shouldScrollToBottom = true)
                    showToast("Unable to send reply (${response.code()})")
                }
            } catch (e: Exception) {
                pendingOutgoingMessages = pendingOutgoingMessages - pendingMessage
                binding.etReply.setText(message)
                renderTicket(ticket, shouldScrollToBottom = true)
                showToast(e.message ?: "Unable to send reply")
            } finally {
                isSubmittingMessage = false
                binding.btnSendReply.text = "Send"
                binding.btnSendReply.isEnabled = currentTicket?.let { canReplyToTicket(it) } == true
            }
        }
    }

    private fun startLiveTicketUpdates() {
        if (liveTicketJob?.isActive == true) return

        liveTicketJob = lifecycleScope.launch {
            while (isActive) {
                delay(LIVE_TICKET_REFRESH_MS)
                refreshTicketSilently()
            }
        }
    }

    private fun stopLiveTicketUpdates() {
        liveTicketJob?.cancel()
        liveTicketJob = null
        isLiveRefreshInFlight = false
    }

    private suspend fun refreshTicketSilently() {
        if (isLiveRefreshInFlight) return

        isLiveRefreshInFlight = true
        try {
            val response = ApiClient.apiService.getSupportTicket(ticketId)
            val updatedTicket = response.body()
            if (response.isSuccessful && updatedTicket != null) {
                val previousTicket = currentTicket
                if (previousTicket == null || ticketFingerprint(previousTicket) != ticketFingerprint(updatedTicket)) {
                    val shouldScroll = previousTicket == null ||
                        conversationMessageCount(updatedTicket) > conversationMessageCount(previousTicket)
                    renderTicket(updatedTicket, shouldScrollToBottom = shouldScroll)
                }
            }
        } catch (_: Exception) {
            // Keep live polling quiet; the visible load path reports errors.
        } finally {
            isLiveRefreshInFlight = false
        }
    }

    private fun renderTicket(ticket: SupportTicket, shouldScrollToBottom: Boolean) {
        currentTicket = ticket
        binding.tvEmptyState.isVisible = false

        val title = ticket.subject.ifBlank { "Support ticket" }
        val updatedAt = ticket.updatedAt ?: ticket.createdAt

        binding.tvChatTitle.text = title
        binding.tvChatStatus.text = formatStatusLabel(ticket.status)
        binding.tvChatStatus.setTextColor(statusColor(ticket.status))
        binding.tvChatMeta.text = buildString {
            append(formatPriority(ticket.priority)).append(" priority")
            if (updatedAt != null) append(" - Updated ").append(ticketDateFormat.format(updatedAt))
        }

        val savedMessages = ticket.messages
            .filter { it.message.isNotBlank() }
            .sortedBy { it.sentAt ?: Date(0) }
        val latestAdminSentAt = savedMessages
            .filter { isAdminMessage(it) }
            .mapNotNull { it.sentAt }
            .maxByOrNull { it.time }

        binding.messageList.removeAllViews()
        if (ticket.description.isNotBlank()) {
            addConversationBubble(
                message = ticket.description,
                sentAt = ticket.createdAt,
                isUserMessage = true,
                tickStatus = userMessageTickStatus(ticket.createdAt, latestAdminSentAt)
            )
        }

        savedMessages.forEach { addConversationMessage(it, latestAdminSentAt) }

        pendingOutgoingMessages
            .filter { it.ticketId == ticketId }
            .sortedBy { it.sentAt }
            .forEach { pendingMessage ->
                addConversationBubble(
                    message = pendingMessage.message,
                    sentAt = pendingMessage.sentAt,
                    isUserMessage = true,
                    tickStatus = MessageTickStatus.SENDING
                )
            }

        if (
            ticket.description.isBlank() &&
            savedMessages.isEmpty() &&
            pendingOutgoingMessages.none { it.ticketId == ticketId }
        ) {
            binding.tvEmptyState.text = "No messages yet"
            binding.tvEmptyState.isVisible = true
        }

        val canReply = canReplyToTicket(ticket)
        binding.replyComposer.isVisible = canReply
        binding.tvClosedNotice.isVisible = !canReply
        binding.tvClosedNotice.text = if (isTicketClosed(ticket)) {
            "This ticket is closed. You can still review the conversation."
        } else {
            "Replies are unavailable for this ticket."
        }
        binding.btnSendReply.isEnabled = canReply && !isSubmittingMessage

        if (shouldScrollToBottom) {
            scrollConversationToBottom()
        }
    }

    private fun addConversationMessage(message: SupportTicketMessage, latestAdminSentAt: Date?) {
        val isAdmin = isAdminMessage(message)
        addConversationBubble(
            message = message.message,
            sentAt = message.sentAt,
            isUserMessage = !isAdmin,
            tickStatus = if (isAdmin) null else userMessageTickStatus(message.sentAt, latestAdminSentAt)
        )
    }

    private fun addConversationBubble(
        message: String,
        sentAt: Date?,
        isUserMessage: Boolean,
        tickStatus: MessageTickStatus? = null
    ) {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = if (isUserMessage) Gravity.END else Gravity.START
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = dp(10)
            }
        }

        val bubble = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = createMessageBubbleBackground(isUserMessage)
            setPadding(dp(14), dp(11), dp(14), dp(12))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                if (isUserMessage) {
                    marginStart = dp(42)
                } else {
                    marginEnd = dp(42)
                }
            }
        }

        bubble.addView(TextView(this).apply {
            text = message
            setTextColor(
                if (isUserMessage) Color.BLACK
                else ContextCompat.getColor(this@SupportTicketChatActivity, R.color.text_primary)
            )
            textSize = 14f
            typeface = font(R.font.inter_regular)
            setLineSpacing(0f, 1.22f)
            maxWidth = resources.displayMetrics.widthPixels - dp(118)
        })

        val metaRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL or Gravity.END
            setPadding(0, dp(5), 0, 0)
        }

        metaRow.addView(TextView(this).apply {
            text = sentAt?.let { messageTimeFormat.format(it) }.orEmpty()
            setTextColor(
                if (isUserMessage) Color.rgb(118, 124, 132)
                else ContextCompat.getColor(this@SupportTicketChatActivity, R.color.text_secondary)
            )
            textSize = 11f
            typeface = font(R.font.inter_medium)
            includeFontPadding = false
        })

        if (isUserMessage && tickStatus != null) {
            metaRow.addView(TextView(this).apply {
                text = when (tickStatus) {
                    MessageTickStatus.SENDING -> "✓"
                    MessageTickStatus.DELIVERED,
                    MessageTickStatus.SEEN -> "✓✓"
                }
                setTextColor(
                    when (tickStatus) {
                        MessageTickStatus.SEEN -> Color.BLACK
                        MessageTickStatus.SENDING,
                        MessageTickStatus.DELIVERED -> Color.rgb(118, 124, 132)
                    }
                )
                textSize = 12f
                typeface = font(R.font.inter_semibold)
                includeFontPadding = false
                setPadding(dp(6), 0, 0, 0)
            })
        }

        bubble.addView(metaRow)
        row.addView(bubble)
        binding.messageList.addView(row)
    }

    private fun createMessageBubbleBackground(isUserMessage: Boolean): GradientDrawable {
        return GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = dp(20).toFloat()
            if (isUserMessage) {
                setColor(
                    if (ThemeManager.isDarkMode(this@SupportTicketChatActivity)) {
                        Color.WHITE
                    } else {
                        Color.rgb(245, 247, 250)
                    }
                )
                setStroke(dp(1), ContextCompat.getColor(this@SupportTicketChatActivity, R.color.divider))
            } else {
                setColor(ContextCompat.getColor(this@SupportTicketChatActivity, R.color.background_secondary))
                setStroke(dp(1), ContextCompat.getColor(this@SupportTicketChatActivity, R.color.divider))
            }
        }
    }

    private fun userMessageTickStatus(sentAt: Date?, latestAdminSentAt: Date?): MessageTickStatus {
        return if (sentAt != null && latestAdminSentAt != null && latestAdminSentAt.after(sentAt)) {
            MessageTickStatus.SEEN
        } else {
            MessageTickStatus.DELIVERED
        }
    }

    private fun conversationMessageCount(ticket: SupportTicket): Int {
        return ticket.messages.count { it.message.isNotBlank() } +
            if (ticket.description.isNotBlank()) 1 else 0
    }

    private fun ticketFingerprint(ticket: SupportTicket): String {
        return buildString {
            append(ticket.id).append('|')
            append(ticket.status).append('|')
            append(ticket.updatedAt?.time).append('|')
            append(ticket.resolvedAt?.time).append('|')
            append(ticket.description).append('|')
            ticket.messages.forEach { message ->
                append(message.messageId).append(':')
                append(message.senderRole).append(':')
                append(message.sentAt?.time).append(':')
                append(message.message).append('|')
            }
        }
    }

    private fun scrollConversationToBottom() {
        binding.scrollMessages.post {
            binding.scrollMessages.fullScroll(View.FOCUS_DOWN)
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

    private fun isTicketClosed(ticket: SupportTicket): Boolean {
        return normalizedStatus(ticket.status) in CLOSED_TICKET_STATUSES
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
