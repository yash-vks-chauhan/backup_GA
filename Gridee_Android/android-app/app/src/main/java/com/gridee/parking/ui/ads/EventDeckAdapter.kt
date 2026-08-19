package com.gridee.parking.ui.ads

import android.view.HapticFeedbackConstants
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.core.view.updateLayoutParams
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.widget.ViewPager2
import coil.load
import com.gridee.parking.data.model.CustomAd
import com.gridee.parking.databinding.ItemEventDeckCardBinding
import com.gridee.parking.databinding.ItemEventDeckNativeBinding
import kotlin.math.abs

/**
 * The ad rail on the booking pass: one creative full size with the rest stacked behind it.
 *
 * The rail carries both kinds of paid surface. The AdMob native card is the first page and
 * partner campaigns follow it. They used to sit side by side in one row, which gave AdMob a
 * 120 dp column beside a 235 dp poster — at that width its headline and button were too small
 * to act on, and the poster next to it took every look. Full width one at a time is worth more
 * to both, and it costs nothing: a full-width 16:9 page is within a few dp of the height the
 * side-by-side row already reserved.
 *
 * The pages are identical frames — same radius, same absence of shadow, same disclosure chip
 * in the same corner — so the rail reads as one deck rather than two unrelated widgets.
 *
 * Nothing here draws a headline or a button on a *campaign* card. The artwork is the ad, and
 * everything it cannot say at this size lives in the spotlight a tap opens. The AdMob page is
 * the exception, because its headline and call to action are what the SDK requires it to show.
 */
class EventDeckAdapter(
    private val onCreativeShown: (CustomAd) -> Unit,
    private val onCreativeFailed: (CustomAd) -> Unit,
    private val onCardClick: (CustomAd) -> Unit,
    private val onNativePageBound: (FrameLayout) -> Unit
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    /** A page of the rail. */
    sealed interface Item {
        /** The AdMob native card. Always first — see [BookingQrPassBottomSheet] on impressions. */
        object Native : Item

        data class Campaign(val ad: CustomAd) : Item
    }

    private var items: List<Item> = emptyList()

    /**
     * Height of the front card in px, from [cardHeightForWidth]. The frame has to be measured
     * off the page width rather than fixed in dp: the same 124 dp card was 2.6:1 on a 360 dp
     * screen and 3:1 on a 411 dp one, so a landscape creative arrived centre-cropped to a
     * strip. Zero means "not measured yet" and the layout's own height stands.
     */
    var cardHeightPx: Int = 0
        set(value) {
            if (field == value) return
            field = value
            notifyDataSetChanged()
        }

    init {
        setHasStableIds(true)
    }

    fun submit(next: List<Item>) {
        items = next
        notifyDataSetChanged()
    }

    override fun getItemCount(): Int = items.size

    override fun getItemViewType(position: Int): Int = when (items[position]) {
        is Item.Native -> TYPE_NATIVE
        is Item.Campaign -> TYPE_CAMPAIGN
    }

    override fun getItemId(position: Int): Long = when (val item = items[position]) {
        is Item.Native -> NATIVE_ITEM_ID
        is Item.Campaign -> item.ad.id.hashCode().toLong()
    }

    /** Whether the rail is currently carrying the AdMob page. */
    fun hasNativePage(): Boolean = items.any { it is Item.Native }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return if (viewType == TYPE_NATIVE) {
            NativeViewHolder(ItemEventDeckNativeBinding.inflate(inflater, parent, false))
        } else {
            DeckViewHolder(ItemEventDeckCardBinding.inflate(inflater, parent, false))
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (val item = items[position]) {
            is Item.Native -> (holder as NativeViewHolder).bind()
            is Item.Campaign -> (holder as DeckViewHolder).bind(item.ad)
        }
    }

    inner class NativeViewHolder(
        private val binding: ItemEventDeckNativeBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind() = with(binding) {
            if (cardHeightPx > 0 && nativePageSlot.layoutParams.height != cardHeightPx) {
                nativePageSlot.updateLayoutParams { height = cardHeightPx }
            }
            onNativePageBound(nativePageSlot)
        }
    }

    inner class DeckViewHolder(
        private val binding: ItemEventDeckCardBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(ad: CustomAd) = with(binding) {
            root.alpha = 0f
            if (cardHeightPx > 0 && cardEventDeck.layoutParams.height != cardHeightPx) {
                cardEventDeck.updateLayoutParams { height = cardHeightPx }
            }
            cardEventDeck.contentDescription = ad.title?.takeIf { it.isNotBlank() }
                ?: root.context.getString(com.gridee.parking.R.string.event_poster_content_description)
            cardEventDeck.setOnClickListener { view ->
                view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                onCardClick(ad)
            }

            ivEventDeck.load(ad.imageUrl) {
                crossfade(false)
                listener(
                    onSuccess = { _, _ ->
                        if (bindingAdapterPosition == RecyclerView.NO_POSITION) return@listener
                        root.animate().cancel()
                        root.animate().alpha(1f).setDuration(180L).start()
                        onCreativeShown(ad)
                    },
                    onError = { _, _ ->
                        if (bindingAdapterPosition == RecyclerView.NO_POSITION) return@listener
                        root.alpha = 0f
                        onCreativeFailed(ad)
                    }
                )
            }
        }
    }

    companion object {

        private const val TYPE_NATIVE = 0
        private const val TYPE_CAMPAIGN = 1

        /** Out of reach of any campaign id's hash, so the two never collide. */
        private const val NATIVE_ITEM_ID = Long.MIN_VALUE

        /**
         * The creative frame: 16:9 landscape, the shape campaign artwork is authored in, so
         * centre-crop trims nothing worth keeping. The AdMob page asks the SDK for landscape
         * media for the same reason — see [BookingQrNativeAdView].
         */
        fun cardHeightForWidth(cardWidthPx: Int): Int = (cardWidthPx * 9f / 16f).toInt()

        /**
         * How tall the pager has to be to hold the front card *and* the two peeking out from
         * under it: each one back is pushed down [STACK_OFFSET_DP] and scaled from its top
         * edge, so the deepest visible card ends lower than the front one does.
         */
        fun deckHeightForCard(cardHeightPx: Int, density: Float): Int {
            val deepest = cardHeightPx * (1f - STACK_SCALE_STEP * MAX_VISIBLE_DEPTH) +
                STACK_OFFSET_DP * density * MAX_VISIBLE_DEPTH
            return maxOf(cardHeightPx, deepest.toInt())
        }

        /**
         * Stacks the pages instead of sliding them: the pages ahead of the current one are
         * held in place, scaled down from their top edge and pushed down a little, so their
         * bottom edges peek out from under the front card like a deck of tickets.
         */
        fun stackTransformer(density: Float) = ViewPager2.PageTransformer { page, position ->
            page.pivotY = 0f
            page.pivotX = page.width / 2f
            when {
                position <= 0f -> {
                    // Current card, and the one being flicked away: it leaves normally and
                    // fades so it does not smear across the card underneath.
                    page.translationX = 0f
                    page.translationY = 0f
                    page.scaleX = 1f
                    page.scaleY = 1f
                    page.alpha = 1f + position.coerceAtLeast(-1f)
                    page.translationZ = 0f
                }
                else -> {
                    // Cards behind: held under the front one rather than waiting off-screen.
                    val depth = position.coerceAtMost(MAX_VISIBLE_DEPTH)
                    page.translationX = -page.width * position
                    page.translationY = STACK_OFFSET_DP * density * depth
                    val scale = 1f - STACK_SCALE_STEP * depth
                    page.scaleX = scale
                    page.scaleY = scale
                    page.alpha = if (position > MAX_VISIBLE_DEPTH + 1f) 0f else 1f
                    page.translationZ = -position
                }
            }
            if (abs(position) > MAX_VISIBLE_DEPTH + 1f) page.alpha = 0f
        }

        private const val STACK_OFFSET_DP = 22f
        private const val STACK_SCALE_STEP = 0.055f
        private const val MAX_VISIBLE_DEPTH = 2f
    }
}
