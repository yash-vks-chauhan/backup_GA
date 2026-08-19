package com.gridee.parking.ui.adapters

import com.gridee.parking.data.model.SupportTicket
import com.gridee.parking.data.model.SupportTicketMessage
import java.util.Calendar
import java.util.Date
import java.util.Locale

/**
 * Caption shown under an outgoing bubble.
 *
 * Only states the client can actually stand behind. There is deliberately no "Seen":
 * [SupportTicketMessage] carries no read state, so the old implementation inferred it
 * from whether support had replied later — a receipt the backend cannot support.
 * SENT means the POST succeeded and the server stored the message; nothing more is
 * claimed. Restoring a real "Seen" needs a readAt field plus an endpoint the admin
 * console calls.
 */
enum class SupportDeliveryState { SENDING, SENT, FAILED }

/** A message the user has sent that the server has not confirmed yet. */
data class SupportOutgoingDraft(val message: String, val sentAt: Date)

/**
 * One row in the conversation. The list is built by [SupportConversationBuilder] and
 * handed to the adapter as-is, so every layout decision (grouping, separators, which
 * bubble carries the delivery caption) is resolved once, off the view layer, and is
 * diffable and testable.
 */
sealed class SupportChatItem {

    abstract val id: String

    /**
     * Opening context: what the ticket is about, when it was raised, and the short
     * reference for it.
     *
     * [reference] exists because the full ticket id was never surfaced anywhere in
     * the app — a user on the phone to support had nothing to quote. It is a display
     * tail of the id, not an identifier to send back to the server.
     *
     * [awaitingFirstReply] is the only expectation-setting the screen does. It is a
     * fact the client can actually check (no ADMIN message exists yet), not an
     * estimate — the backend measures no response time.
     */
    data class Intro(
        val subject: String,
        val openedAt: Date?,
        val reference: String?,
        val awaitingFirstReply: Boolean
    ) : SupportChatItem() {
        override val id: String = "intro"
    }

    data class DaySeparator(val at: Date) : SupportChatItem() {
        override val id: String = "sep-${at.time}"
    }

    /**
     * Something that happened to the ticket rather than something someone said.
     *
     * Only emitted for transitions the server actually timestamps. There is no
     * "Support joined" or "Marked in progress" here because the backend records no
     * time for those — placing them would mean inventing a position in the thread.
     *
     * Carries a [Kind], not a label: this file stays free of Context and resources,
     * so the wording (and its translation) belongs to the view holder.
     */
    data class SystemEvent(val kind: Kind, val at: Date) : SupportChatItem() {
        override val id: String = "event-$kind-${at.time}"

        enum class Kind { RESOLVED }
    }

    data class Message(
        override val id: String,
        val text: String,
        val isOutgoing: Boolean,
        val sentAt: Date?,
        /** True while this bubble exists only on the device (sending or failed). */
        val isLocalDraft: Boolean,
        /** Grouping flags decide bubble spacing and which bubble wears the tail. */
        val groupedWithPrevious: Boolean,
        val groupedWithNext: Boolean,
        /** Caption under this bubble, or null for none. */
        val caption: SupportDeliveryState?,
        val failedDraft: SupportOutgoingDraft?
    ) : SupportChatItem()
}

/**
 * Result of building a conversation. [hasOlderMessages] drives the window: only the
 * most recent slice is rendered, and the screen widens it as the user scrolls up.
 */
data class SupportConversation(
    val items: List<SupportChatItem>,
    val isEmpty: Boolean,
    val hasOlderMessages: Boolean
)

/**
 * Turns a ticket (plus whatever is still in flight on the device) into the row list.
 *
 * Pure: no Context, no resources, no views. Everything presentational — date labels,
 * colours, emoji sizing — is the adapter's job.
 */
object SupportConversationBuilder {

    /** Messages further apart than this get a fresh day/time separator between them. */
    private const val TIME_SEPARATOR_GAP_MS = 60 * 60 * 1_000L

    private data class Bubble(
        val id: String,
        val text: String,
        val isOutgoing: Boolean,
        val sentAt: Date?,
        val isLocalDraft: Boolean,
        val state: SupportDeliveryState?,
        val failedDraft: SupportOutgoingDraft?
    )

    fun build(
        ticket: SupportTicket,
        subject: String,
        pending: List<SupportOutgoingDraft>,
        failed: List<SupportOutgoingDraft>,
        windowSize: Int
    ): SupportConversation {
        val bubbles = collectBubbles(ticket, pending, failed)
        if (bubbles.isEmpty()) {
            return SupportConversation(emptyList(), isEmpty = true, hasOlderMessages = false)
        }

        val hasOlder = bubbles.size > windowSize
        val windowed = if (hasOlder) bubbles.takeLast(windowSize) else bubbles

        val items = mutableListOf<SupportChatItem>()
        // The opener belongs at the true start of the thread. While the view is
        // windowed we are not at the start, so it stays out.
        if (!hasOlder && (subject.isNotBlank() || ticket.createdAt != null)) {
            items += SupportChatItem.Intro(
                subject = subject,
                openedAt = ticket.createdAt,
                reference = shortReference(ticket.id),
                // Counted off the ticket, not the window, so it can't flip to true
                // just because the rendered slice happens to exclude the reply.
                awaitingFirstReply = incomingCount(ticket) == 0
            )
        }

        // Only the newest settled outgoing bubble carries a delivery caption; failed
        // ones always carry their own.
        val captionedIndex = windowed.indexOfLast {
            it.isOutgoing && it.state != null && it.state != SupportDeliveryState.FAILED
        }

        // A resolution belongs at the moment it happened, which is not always the end
        // of the thread — support can reply after resolving. -1 places it first.
        val resolvedAt = ticket.resolvedAt
        val resolvedAfterIndex = resolvedAt?.let { at ->
            windowed.indexOfLast { (it.sentAt ?: Date(0)) <= at }
        }

        windowed.forEachIndexed { index, bubble ->
            val previous = windowed.getOrNull(index - 1)
            val next = windowed.getOrNull(index + 1)

            if (index == 0 && resolvedAfterIndex == -1 && resolvedAt != null) {
                items += SupportChatItem.SystemEvent(
                    SupportChatItem.SystemEvent.Kind.RESOLVED, resolvedAt
                )
            }

            val startsNewBlock = shouldSeparate(previous?.sentAt, bubble.sentAt)
            if (startsNewBlock && bubble.sentAt != null) {
                items += SupportChatItem.DaySeparator(bubble.sentAt)
            }

            // An event interrupts a run of bubbles, so grouping must not reach across it.
            val eventBefore = resolvedAfterIndex == index - 1
            val eventAfter = resolvedAfterIndex == index

            items += SupportChatItem.Message(
                id = bubble.id,
                text = bubble.text,
                isOutgoing = bubble.isOutgoing,
                sentAt = bubble.sentAt,
                isLocalDraft = bubble.isLocalDraft,
                groupedWithPrevious = previous != null && !startsNewBlock && !eventBefore &&
                    previous.isOutgoing == bubble.isOutgoing,
                groupedWithNext = next != null && !eventAfter &&
                    next.isOutgoing == bubble.isOutgoing &&
                    !shouldSeparate(bubble.sentAt, next.sentAt),
                caption = when {
                    bubble.state == SupportDeliveryState.FAILED -> SupportDeliveryState.FAILED
                    index == captionedIndex -> bubble.state
                    else -> null
                },
                failedDraft = bubble.failedDraft
            )

            if (eventAfter && resolvedAt != null) {
                items += SupportChatItem.SystemEvent(
                    SupportChatItem.SystemEvent.Kind.RESOLVED, resolvedAt
                )
            }
        }

        return SupportConversation(items, isEmpty = false, hasOlderMessages = hasOlder)
    }

    /**
     * How many messages support has sent. Counted off the ticket rather than the
     * built rows so it is independent of the rendering window — the jump-to-latest
     * badge must not reset just because the view is showing a narrower slice.
     */
    fun incomingCount(ticket: SupportTicket): Int {
        return ticket.messages.count { it.message.isNotBlank() && isAdminMessage(it) }
    }

    /**
     * A human-quotable tail of the ticket id.
     *
     * Support ids are 24-character hex or a UUID — unreadable over the phone and
     * pointless to print in full. The last six characters are distinctive enough to
     * find a ticket by, and the admin console holds the real id anyway. Returns null
     * for anything too short to shorten, so the opener simply omits the reference
     * rather than showing a stub.
     */
    fun shortReference(id: String?): String? {
        val trimmed = id?.trim()?.takeIf { it.length >= 6 } ?: return null
        return trimmed.takeLast(6).uppercase(Locale.ROOT)
    }

    private fun collectBubbles(
        ticket: SupportTicket,
        pending: List<SupportOutgoingDraft>,
        failed: List<SupportOutgoingDraft>
    ): List<Bubble> {
        val bubbles = mutableListOf<Bubble>()

        val savedMessages = ticket.messages
            .filter { it.message.isNotBlank() }
            .sortedBy { it.sentAt ?: Date(0) }

        // The ticket description is the opening message of the thread.
        if (ticket.description.isNotBlank()) {
            bubbles += Bubble(
                id = "desc",
                text = ticket.description,
                isOutgoing = true,
                sentAt = ticket.createdAt,
                isLocalDraft = false,
                state = SupportDeliveryState.SENT,
                failedDraft = null
            )
        }

        savedMessages.forEachIndexed { index, message ->
            val isAdmin = isAdminMessage(message)
            bubbles += Bubble(
                id = message.messageId?.takeIf { it.isNotBlank() }
                    ?: "m$index-${message.sentAt?.time}",
                text = message.message,
                isOutgoing = !isAdmin,
                sentAt = message.sentAt,
                isLocalDraft = false,
                // The server has it; that is the whole claim.
                state = if (isAdmin) null else SupportDeliveryState.SENT,
                failedDraft = null
            )
        }

        (pending.map { it to SupportDeliveryState.SENDING } +
            failed.map { it to SupportDeliveryState.FAILED })
            .sortedBy { (draft, _) -> draft.sentAt }
            .forEach { (draft, state) ->
                val isFailed = state == SupportDeliveryState.FAILED
                bubbles += Bubble(
                    id = (if (isFailed) "f-" else "p-") + draft.sentAt.time,
                    text = draft.message,
                    isOutgoing = true,
                    sentAt = draft.sentAt,
                    isLocalDraft = true,
                    state = state,
                    failedDraft = draft.takeIf { isFailed }
                )
            }

        return bubbles
    }

    private fun isAdminMessage(message: SupportTicketMessage): Boolean {
        return message.senderRole?.trim()?.uppercase(Locale.getDefault()) == "ADMIN"
    }

    private fun shouldSeparate(previous: Date?, current: Date?): Boolean {
        if (current == null) return false
        if (previous == null) return true
        if (current.time - previous.time > TIME_SEPARATOR_GAP_MS) return true
        val a = Calendar.getInstance().apply { time = previous }
        val b = Calendar.getInstance().apply { time = current }
        return a.get(Calendar.YEAR) != b.get(Calendar.YEAR) ||
            a.get(Calendar.DAY_OF_YEAR) != b.get(Calendar.DAY_OF_YEAR)
    }
}
