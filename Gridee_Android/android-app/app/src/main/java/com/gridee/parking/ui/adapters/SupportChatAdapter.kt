package com.gridee.parking.ui.adapters

import android.content.Context
import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.Rect
import android.graphics.drawable.Drawable
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.text.util.Linkify
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import androidx.annotation.ColorRes
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.gridee.parking.R
import com.gridee.parking.databinding.ItemChatDaySeparatorBinding
import com.gridee.parking.databinding.ItemChatIntroBinding
import com.gridee.parking.databinding.ItemChatMessageBinding
import com.gridee.parking.databinding.ItemChatSystemEventBinding
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.math.cos
import kotlin.math.sin

/**
 * The conversation list.
 *
 * Outgoing and incoming messages are separate view types so a recycled holder never
 * swaps sides: colours, gravity, padding and the two bubble drawables (tailed and
 * untailed) are built once per holder and only selected on bind.
 */
class SupportChatAdapter(
    /** Receives the bubble view (to anchor a menu against) and the item it holds. */
    private val onMessageLongPress: (View, SupportChatItem.Message) -> Unit,
    private val onRetry: (SupportOutgoingDraft) -> Unit
) : ListAdapter<SupportChatItem, RecyclerView.ViewHolder>(DIFF) {

    /**
     * Id of the one message currently showing its timestamp. One at a time, iMessage
     * style — a thread where every bubble carries a time reads like a log, not a
     * conversation. Kept here rather than in the item so the pure builder stays free
     * of view state.
     */
    private var revealedMessageId: String? = null

    private fun toggleTimestamp(id: String) {
        val previous = revealedMessageId
        revealedMessageId = if (previous == id) null else id
        listOf(previous, revealedMessageId)
            .filterNotNull()
            .distinct()
            .forEach { target ->
                val index = currentList.indexOfFirst { it.id == target }
                if (index != -1) notifyItemChanged(index)
            }
    }

    companion object {
        private const val VIEW_TYPE_INTRO = 0
        private const val VIEW_TYPE_SEPARATOR = 1
        private const val VIEW_TYPE_OUTGOING = 2
        private const val VIEW_TYPE_INCOMING = 3
        private const val VIEW_TYPE_EVENT = 4

        /**
         * Bubbles stop short of the full width so the column always reads as a side.
         * The fraction is of the list's *content* width, not the raw screen: measured
         * against the screen it ignored the list's 16dp side padding, so a bubble was
         * allowed to run wider than the column it lives in.
         */
        private const val BUBBLE_WIDTH_FRACTION = 0.76f
        /** …and stop growing entirely on tablets and unfolded foldables. */
        private const val BUBBLE_MAX_WIDTH_DP = 480

        private val DIFF = object : DiffUtil.ItemCallback<SupportChatItem>() {

            override fun areItemsTheSame(old: SupportChatItem, new: SupportChatItem): Boolean {
                if (old is SupportChatItem.Message && new is SupportChatItem.Message) {
                    // An optimistic bubble and the server's confirmation of it are the
                    // same message. Matching them here turns the hand-off into a rebind
                    // (caption flips "Sending…" → "Delivered") instead of a
                    // remove-and-insert, which would blink the bubble off and on.
                    if (old.id == new.id) return true
                    return old.isOutgoing && new.isOutgoing &&
                        old.text == new.text &&
                        (old.isLocalDraft || new.isLocalDraft)
                }
                return old.id == new.id
            }

            override fun areContentsTheSame(old: SupportChatItem, new: SupportChatItem): Boolean {
                return old == new
            }
        }
    }

    override fun getItemViewType(position: Int): Int {
        return when (val item = getItem(position)) {
            is SupportChatItem.Intro -> VIEW_TYPE_INTRO
            is SupportChatItem.DaySeparator -> VIEW_TYPE_SEPARATOR
            is SupportChatItem.SystemEvent -> VIEW_TYPE_EVENT
            is SupportChatItem.Message ->
                if (item.isOutgoing) VIEW_TYPE_OUTGOING else VIEW_TYPE_INCOMING
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return when (viewType) {
            VIEW_TYPE_INTRO ->
                IntroViewHolder(ItemChatIntroBinding.inflate(inflater, parent, false))
            VIEW_TYPE_SEPARATOR ->
                SeparatorViewHolder(ItemChatDaySeparatorBinding.inflate(inflater, parent, false))
            VIEW_TYPE_EVENT ->
                EventViewHolder(ItemChatSystemEventBinding.inflate(inflater, parent, false))
            else -> MessageViewHolder(
                binding = ItemChatMessageBinding.inflate(inflater, parent, false),
                isOutgoing = viewType == VIEW_TYPE_OUTGOING,
                contentWidthPx = contentWidthOf(parent),
                onMessageLongPress = onMessageLongPress,
                onRetry = onRetry,
                onToggleTimestamp = ::toggleTimestamp
            )
        }
    }

    /**
     * The width a bubble actually has to grow into: the list minus its side padding.
     * Read off the list rather than hard-coded so the two can't drift apart.
     *
     * Falls back to the display width when the first holder is created before the
     * list has been measured. The activity is recreated on rotation and unfold (no
     * configChanges for either), so holders — and this width — are rebuilt with it.
     */
    private fun contentWidthOf(parent: ViewGroup): Int {
        val horizontalPadding = parent.paddingStart + parent.paddingEnd
        val measured = parent.width - horizontalPadding
        if (measured > 0) return measured
        return parent.resources.displayMetrics.widthPixels - horizontalPadding
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (val item = getItem(position)) {
            is SupportChatItem.Intro -> (holder as IntroViewHolder).bind(item)
            is SupportChatItem.DaySeparator -> (holder as SeparatorViewHolder).bind(item)
            is SupportChatItem.SystemEvent -> (holder as EventViewHolder).bind(item)
            is SupportChatItem.Message ->
                (holder as MessageViewHolder).bind(item, item.id == revealedMessageId)
        }
    }

    // -----------------------------------------------------------------------
    // View holders
    // -----------------------------------------------------------------------

    class IntroViewHolder(
        private val binding: ItemChatIntroBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        private val dayFormat = SimpleDateFormat("d MMM yyyy", Locale.getDefault())

        fun bind(item: SupportChatItem.Intro) {
            val context = binding.root.context

            binding.tvIntroSubject.isVisible = item.subject.isNotBlank()
            binding.tvIntroSubject.text = item.subject

            // The reference rides on the opened line rather than claiming a row of
            // its own — it is a detail you go looking for, not something the opener
            // should announce. It still needs the date to be worth a line at all, so
            // a ticket with no createdAt drops both.
            binding.tvIntroOpened.isVisible = item.openedAt != null
            item.openedAt?.let { openedAt ->
                val day = dayFormat.format(openedAt)
                binding.tvIntroOpened.text = if (item.reference != null) {
                    context.getString(R.string.support_chat_opened_with_ref, day, item.reference)
                } else {
                    context.getString(R.string.support_chat_opened, day)
                }
            }

            binding.tvIntroExpectation.isVisible = item.awaitingFirstReply
        }
    }

    class EventViewHolder(
        private val binding: ItemChatSystemEventBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        private val timeFormat = SimpleDateFormat("h:mm a", Locale.getDefault())

        fun bind(item: SupportChatItem.SystemEvent) {
            val label = when (item.kind) {
                SupportChatItem.SystemEvent.Kind.RESOLVED -> R.string.support_chat_event_resolved
            }
            binding.tvEvent.text = binding.root.context
                .getString(label, timeFormat.format(item.at))
        }
    }

    class SeparatorViewHolder(
        private val binding: ItemChatDaySeparatorBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        private val context: Context = binding.root.context
        private val timeFormat = SimpleDateFormat("h:mm a", Locale.getDefault())
        private val dayFormat = SimpleDateFormat("d MMM", Locale.getDefault())
        private val dayYearFormat = SimpleDateFormat("d MMM yyyy", Locale.getDefault())
        private val dayColor = ContextCompat.getColor(context, R.color.text_secondary)
        private val timeColor = ContextCompat.getColor(context, R.color.text_tertiary)

        fun bind(item: SupportChatItem.DaySeparator) {
            // Two-tone hierarchy: the day reads first, the time sits back.
            val day = dayLabel(item.at)
            binding.tvSeparator.text = SpannableStringBuilder().apply {
                append(day)
                setSpan(ForegroundColorSpan(dayColor), 0, day.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                val start = length
                append("  ").append(timeFormat.format(item.at))
                setSpan(ForegroundColorSpan(timeColor), start, length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            }
        }

        private fun dayLabel(date: Date): String {
            val now = Calendar.getInstance()
            val then = Calendar.getInstance().apply { time = date }
            val yesterday = (now.clone() as Calendar).apply { add(Calendar.DAY_OF_YEAR, -1) }
            return when {
                isSameDay(now, then) -> context.getString(R.string.today)
                isSameDay(yesterday, then) -> context.getString(R.string.yesterday)
                now.get(Calendar.YEAR) == then.get(Calendar.YEAR) -> dayFormat.format(date)
                else -> dayYearFormat.format(date)
            }
        }

        private fun isSameDay(a: Calendar, b: Calendar): Boolean {
            return a.get(Calendar.YEAR) == b.get(Calendar.YEAR) &&
                a.get(Calendar.DAY_OF_YEAR) == b.get(Calendar.DAY_OF_YEAR)
        }
    }

    class MessageViewHolder(
        private val binding: ItemChatMessageBinding,
        private val isOutgoing: Boolean,
        contentWidthPx: Int,
        private val onMessageLongPress: (View, SupportChatItem.Message) -> Unit,
        private val onRetry: (SupportOutgoingDraft) -> Unit,
        private val onToggleTimestamp: (String) -> Unit
    ) : RecyclerView.ViewHolder(binding.root) {

        private val timeFormat = SimpleDateFormat("h:mm a", Locale.getDefault())

        private val context: Context = binding.root.context
        private val density = context.resources.displayMetrics.density

        private val textColor = color(
            if (isOutgoing) R.color.chat_text_outgoing else R.color.chat_text_incoming
        )
        private val captionColor = color(R.color.text_tertiary)
        private val failedColor = color(R.color.red)

        private val tailWidth = dp(9)
        private val bubblePadStart = dp(16) + if (isOutgoing) 0 else tailWidth
        private val bubblePadEnd = dp(16) + if (isOutgoing) tailWidth else 0
        private val bubblePadVertical = dp(10)
        private val emojiPadHorizontal = dp(4)
        private val emojiPadVertical = dp(2)

        // Both bubble shapes are built once; bind only picks one. The tail side
        // always reserves its width so grouped bubbles stay flush with the tailed
        // one below them.
        private val bubbleWithTail = newBubble(hasTail = true)
        private val bubbleWithoutTail = newBubble(hasTail = false)

        private var boundItem: SupportChatItem.Message? = null
        private var boundFailedDraft: SupportOutgoingDraft? = null

        init {
            val side = if (isOutgoing) Gravity.END else Gravity.START
            (binding.tvMessage.layoutParams as LinearLayout.LayoutParams).gravity = side
            (binding.tvCaption.layoutParams as LinearLayout.LayoutParams).apply {
                gravity = side
                // Sits under the bubble body edge, which is inset by the tail width.
                if (isOutgoing) marginEnd = dp(11) else marginStart = dp(11)
            }
            binding.tvMessage.setTextColor(textColor)
            binding.tvMessage.maxWidth = minOf(
                (contentWidthPx * BUBBLE_WIDTH_FRACTION).toInt(),
                dp(BUBBLE_MAX_WIDTH_DP)
            )
            binding.tvMessage.setLineSpacing(0f, 1.2f)

            binding.tvMessage.setOnLongClickListener { view ->
                val item = boundItem ?: return@setOnLongClickListener false
                view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                onMessageLongPress(view, item)
                true
            }
            binding.tvMessage.setOnClickListener {
                val failed = boundFailedDraft
                if (failed != null) {
                    onRetry(failed)
                } else {
                    // Otherwise a tap reveals when this message was sent. Without it a
                    // message only ever shows a time if a separator happens to precede
                    // it, so most of the thread carries none at all.
                    boundItem?.let { onToggleTimestamp(it.id) }
                }
            }
            binding.tvCaption.setOnClickListener {
                boundFailedDraft?.let(onRetry)
            }
        }

        fun bind(item: SupportChatItem.Message, showTimestamp: Boolean) {
            boundItem = item
            boundFailedDraft = item.failedDraft

            // Grouped bubbles sit tight; a new block gets air above it.
            binding.root.setPadding(
                0,
                if (item.groupedWithPrevious) dp(3) else dp(12),
                0,
                0
            )

            val emojiOnly = isEmojiOnly(item.text)
            binding.tvMessage.text = item.text
            if (emojiOnly) {
                // iMessage detail: pure-emoji messages render large, without a bubble.
                binding.tvMessage.textSize = 38f
                binding.tvMessage.background = null
                binding.tvMessage.setPadding(
                    emojiPadHorizontal, emojiPadVertical, emojiPadHorizontal, emojiPadVertical
                )
            } else {
                binding.tvMessage.textSize = 16f
                binding.tvMessage.background =
                    if (item.groupedWithNext) bubbleWithoutTail else bubbleWithTail
                binding.tvMessage.setPaddingRelative(
                    bubblePadStart, bubblePadVertical, bubblePadEnd, bubblePadVertical
                )
            }

            // Links stay monochrome so the bubble reads clean. Skipped on failed
            // bubbles, where the whole bubble is a retry target.
            if (!emojiOnly && item.failedDraft == null) {
                binding.tvMessage.setLinkTextColor(textColor)
                Linkify.addLinks(binding.tvMessage, Linkify.WEB_URLS)
            }

            binding.tvMessage.alpha = if (item.failedDraft != null) 0.55f else 1f
            binding.tvMessage.isClickable = item.failedDraft != null
            binding.tvMessage.contentDescription = context.getString(
                if (isOutgoing) R.string.support_chat_you_said
                else R.string.support_chat_support_said,
                item.text
            )

            // One caption row serves both jobs: the delivery state, the revealed
            // timestamp, or both joined — so revealing a time never adds a second line
            // and shifts the thread.
            val caption = item.caption
            val stateLabel = caption?.let {
                context.getString(
                    when (it) {
                        SupportDeliveryState.SENDING -> R.string.support_chat_sending
                        SupportDeliveryState.FAILED -> R.string.support_chat_not_delivered
                        SupportDeliveryState.SENT -> R.string.support_chat_sent
                    }
                )
            }
            val timeLabel = item.sentAt?.takeIf { showTimestamp }?.let { timeFormat.format(it) }

            val text = when {
                timeLabel != null && stateLabel != null ->
                    context.getString(R.string.support_chat_time_and_state, timeLabel, stateLabel)
                timeLabel != null -> timeLabel
                else -> stateLabel
            }
            binding.tvCaption.isVisible = text != null
            binding.tvCaption.text = text.orEmpty()
            binding.tvCaption.setTextColor(
                if (caption == SupportDeliveryState.FAILED) failedColor else captionColor
            )
        }

        private fun newBubble(hasTail: Boolean) = MessageBubbleDrawable(
            fillColor = color(
                if (isOutgoing) R.color.chat_bubble_outgoing else R.color.chat_bubble_incoming
            ),
            pressedFillColor = color(
                if (isOutgoing) R.color.chat_bubble_outgoing_pressed
                else R.color.chat_bubble_incoming_pressed
            ),
            // A *cap*, not a fixed radius — the drawable clamps it to half the bubble
            // height. A one-line bubble comes out an exact capsule; a paragraph gets
            // this. The old flat 20dp landed 1dp short of half a one-line bubble's
            // height, so short bubbles read as capsules that didn't quite close.
            cornerRadius = context.resources.getDimension(R.dimen.chat_bubble_radius_max),
            tailWidth = tailWidth.toFloat(),
            isOutgoing = isOutgoing,
            hasTail = hasTail
        )

        private fun color(@ColorRes resId: Int) = ContextCompat.getColor(context, resId)

        private fun dp(value: Int): Int = (value * density).toInt()

        private fun isEmojiOnly(text: String): Boolean {
            val trimmed = text.trim()
            if (trimmed.isEmpty() || trimmed.length > 16) return false
            var emojiCount = 0
            var i = 0
            while (i < trimmed.length) {
                val codePoint = trimmed.codePointAt(i)
                when {
                    // joiners + variation selectors
                    codePoint == 0x200D || codePoint in 0xFE00..0xFE0F -> Unit
                    // Emoji blocks only. The old range started at 0x2190, which swept
                    // in the plain arrows — a message of just "→" rendered at 38sp.
                    codePoint in 0x1F000..0x1FAFF ||  // emoji & pictographs, flags
                        codePoint in 0x2600..0x27BF ||  // misc symbols, dingbats
                        codePoint in 0x2B00..0x2BFF ||  // stars, filled arrows
                        codePoint in 0x2300..0x23FF -> emojiCount++  // watch, hourglass, media
                    Character.isWhitespace(codePoint) -> Unit
                    else -> return false
                }
                i += Character.charCount(codePoint)
            }
            return emojiCount in 1..3
        }
    }
}

/**
 * How far around the bottom-right corner the outline travels before the tail breaks
 * away from it, in degrees clockwise from 3 o'clock.
 *
 * Low values launch the tail off the widest point of the body, which reads as a fin
 * stuck to the side; high values push it under the bubble where it stops pointing at
 * anything. Just under halfway round is where it looks like it grew there.
 */
private const val TAIL_EXIT_ANGLE_DEG = 38f

/**
 * iMessage-style bubble: fully rounded body with a curvy pointed tail on the
 * sender's bottom corner. Grouped bubbles skip the tail (hasTail = false) but still
 * reserve the tail width so the column stays aligned.
 */
private class MessageBubbleDrawable(
    private val fillColor: Int,
    private val pressedFillColor: Int,
    private val cornerRadius: Float,
    private val tailWidth: Float,
    private val isOutgoing: Boolean,
    private val hasTail: Boolean
) : Drawable() {

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = fillColor
    }
    private val path = Path()

    override fun onBoundsChange(bounds: Rect) {
        super.onBoundsChange(bounds)
        rebuildPath(bounds)
    }

    // A long-press is the only thing you can do to a bubble, so it has to look
    // pressable. One perceptual step off the resting fill — enough to confirm the
    // touch, never a flash.
    override fun isStateful(): Boolean = fillColor != pressedFillColor

    override fun onStateChange(state: IntArray): Boolean {
        val isPressed = state.any { it == android.R.attr.state_pressed }
        val target = if (isPressed) pressedFillColor else fillColor
        if (paint.color == target) return false
        paint.color = target
        invalidateSelf()
        return true
    }

    /**
     * The bubble is one continuous clockwise outline (no overlapping sub-shapes, so
     * no seams). It is always built with the tail side on the right, then mirrored
     * horizontally for incoming bubbles.
     */
    private fun rebuildPath(bounds: Rect) {
        path.reset()
        if (bounds.isEmpty) return

        val top = bounds.top.toFloat()
        val bottom = bounds.bottom.toFloat()
        val left = bounds.left.toFloat()
        val right = bounds.right.toFloat()
        val bodyRight = right - tailWidth
        val height = bottom - top
        val radius = minOf(cornerRadius, height / 2f, (bodyRight - left) / 2f)

        if (!hasTail) {
            path.addRoundRect(left, top, bodyRight, bottom, radius, radius, Path.Direction.CW)
        } else {
            // Keep the tip just inside the drawable bounds: a point placed exactly on
            // the final pixel is clipped by Canvas and can render as the thin stray
            // line the old tail produced.
            val tipInset = maxOf(0.75f, tailWidth * 0.10f)
            val tipX = right - tipInset
            val tipY = bottom - tailWidth * 0.30f
            val tipRound = maxOf(0.75f, tailWidth * 0.08f)
            val baseX = (bodyRight - tailWidth * 0.55f).coerceAtLeast(left + radius)

            // The tail now grows out of the bottom-right *curve* instead of off a
            // straight edge. It used to run the right edge down to a fixed height
            // above the bottom and turn out from there, which assumed the corner
            // radius was well under half the bubble height. Now that a short bubble
            // is a true capsule — radius exactly height/2, no straight right edge to
            // run down at all — that produced a flat notch in the side of the pill.
            //
            // So: sweep the corner arc partway, and leave from wherever that lands.
            // One formula covers both cases, because a capsule is just the degenerate
            // corner where the two arcs meet.
            val cornerCenterX = bodyRight - radius
            val cornerCenterY = bottom - radius
            val exitRadians = Math.toRadians(TAIL_EXIT_ANGLE_DEG.toDouble())
            val exitX = cornerCenterX + radius * cos(exitRadians).toFloat()
            val exitY = cornerCenterY + radius * sin(exitRadians).toFloat()

            path.moveTo(left + radius, top)
            path.lineTo(bodyRight - radius, top)
            path.arcTo(bodyRight - 2f * radius, top, bodyRight, top + 2f * radius, 270f, 90f, false)
            // Straight right edge. Zero-length on a capsule, where the two corner
            // arcs meet at the midpoint — harmless, and it keeps the path one shape.
            path.lineTo(bodyRight, cornerCenterY)
            // Follow the corner round to where the tail breaks away.
            path.arcTo(
                bodyRight - 2f * radius, bottom - 2f * radius, bodyRight, bottom,
                0f, TAIL_EXIT_ANGLE_DEG, false
            )
            // Break off the curve and taper to one crisp side-pointing tip. The first
            // control point trails the arc's own direction so the tail reads as
            // growing out of the body rather than being stuck onto it.
            path.cubicTo(
                exitX + tailWidth * 0.18f, exitY + (bottom - exitY) * 0.38f,
                bodyRight + tailWidth * 0.54f, tipY - tailWidth * 0.26f,
                tipX - tipRound, tipY - tipRound * 0.45f
            )
            // Round only the final pixel-scale cusp. The tail still reads as a point,
            // but never as a brittle needle on high-density screens.
            path.quadTo(
                tipX, tipY,
                tipX - tipRound, tipY + tipRound * 0.45f
            )
            path.cubicTo(
                bodyRight + tailWidth * 0.70f, bottom - tailWidth * 0.12f,
                bodyRight + tailWidth * 0.12f, bottom - tailWidth * 0.03f,
                baseX, bottom
            )
            path.lineTo(left + radius, bottom)
            path.arcTo(left, bottom - 2f * radius, left + 2f * radius, bottom, 90f, 90f, false)
            path.lineTo(left, top + radius)
            path.arcTo(left, top, left + 2f * radius, top + 2f * radius, 180f, 90f, false)
            path.close()
        }

        if (!isOutgoing) {
            val mirror = Matrix()
            mirror.setScale(-1f, 1f, bounds.exactCenterX(), 0f)
            path.transform(mirror)
        }
    }

    override fun draw(canvas: Canvas) {
        canvas.drawPath(path, paint)
    }

    override fun setAlpha(alpha: Int) {
        paint.alpha = alpha
        invalidateSelf()
    }

    override fun setColorFilter(colorFilter: ColorFilter?) {
        paint.colorFilter = colorFilter
        invalidateSelf()
    }

    @Deprecated("Deprecated in android.graphics.drawable.Drawable")
    override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
}
