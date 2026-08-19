package com.gridee.parking.ui.profile

import android.animation.ArgbEvaluator
import android.animation.ValueAnimator
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Color
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.View
import android.view.ViewGroup
import android.view.animation.DecelerateInterpolator
import android.view.inputmethod.InputMethodManager
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.PopupWindow
import android.widget.Toast
import androidx.annotation.ColorRes
import androidx.core.content.ContextCompat
import androidx.core.view.OneShotPreDrawListener
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsAnimationCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.core.view.isVisible
import androidx.core.view.updateLayoutParams
import androidx.core.view.updatePadding
import androidx.core.widget.doAfterTextChanged
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.DefaultItemAnimator
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.gridee.parking.R
import com.gridee.parking.data.api.ApiClient
import com.gridee.parking.data.model.AddSupportTicketMessageRequest
import com.gridee.parking.data.model.SupportTicket
import com.gridee.parking.databinding.ActivitySupportTicketChatBinding
import com.gridee.parking.databinding.ViewChatMessageMenuBinding
import com.gridee.parking.ui.adapters.SupportChatAdapter
import com.gridee.parking.ui.adapters.SupportChatItem
import com.gridee.parking.ui.adapters.SupportConversationBuilder
import com.gridee.parking.ui.adapters.SupportOutgoingDraft
import com.gridee.parking.ui.base.BaseActivity
import com.gridee.parking.ui.views.SkeletonShimmer
import com.gridee.parking.utils.ThemeManager
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.Date
import java.util.Locale

class SupportTicketChatActivity : BaseActivity<ActivitySupportTicketChatBinding>() {

    companion object {
        const val EXTRA_TICKET_ID = "extra_ticket_id"
        const val EXTRA_TICKET_TITLE = "extra_ticket_title"

        private const val TAG = "SupportChat"
        private const val STATE_UNCONFIRMED = "state_unconfirmed_drafts"
        /** Composer drafts, keyed by ticket id. Not the outbox — these are messages
         *  the user never asked to send yet, so they must never be posted on restore. */
        private const val COMPOSER_DRAFT_PREFS = "support_composer_drafts"

        /**
         * The ticket currently on screen, or null. Read by the FCM service so a push
         * about a conversation the user is already reading is dropped rather than
         * notifying them about something they can see.
         */
        @Volatile
        private var foregroundTicketId: String? = null

        fun isForegroundForTicket(ticketId: String): Boolean {
            return foregroundTicketId != null && foregroundTicketId == ticketId
        }

        /**
         * Poll cadence, backing off while nothing is happening.
         *
         * This used to be a flat 1.5s — 40 full-ticket fetches a minute, for the whole
         * time the screen was open, with no ETag so every one transferred the entire
         * thread. Support replies arrive in minutes, not seconds, so the fast tier
         * only needs to cover the window right after you send something. Any change
         * to the ticket drops back to the fastest tier.
         *
         * Resolved and closed tickets need no special case: nothing changes, so they
         * settle at the slowest tier on their own — and if support reopens one, the
         * change is still picked up instead of being missed by a hard stop.
         */
        private val POLL_INTERVALS_MS = longArrayOf(3_000L, 6_000L, 12_000L, 30_000L)
        private const val NEAR_BOTTOM_SLOP_DP = 140
        private val ACTIVE_TICKET_STATUSES = setOf("OPEN", "IN_PROGRESS")
        private val CLOSED_TICKET_STATUSES = setOf("RESOLVED", "CLOSED")

        /** How far below the header the refresh spinner travels before it triggers. */
        private const val SWIPE_SPINNER_TRAVEL_DP = 64

        /** Scroll distance over which the header scrim reaches full opacity. Short
         *  on purpose — a chat is dense, so the scrim has to commit as soon as the
         *  first bubble slides under the title rather than easing in over 120dp. */
        private const val FROST_RANGE_DP = 40f
        /** The bottom scrim's fade tail. The composer's scrim is sized from the panel
         *  rather than fixed, because the panel grows by the keyboard inset — a fixed
         *  height would leave messages visible above the composer once the IME is up. */
        private const val FROST_BOTTOM_TAIL_DP = 28

        // Rendering window. The API returns the whole ticket in one payload, so this
        // is client-side: only the most recent slice becomes list items, and the
        // window widens as the user scrolls up. Keeps the per-poll rebuild and diff
        // bounded no matter how long a thread gets.
        private const val MESSAGE_WINDOW_INITIAL = 50
        private const val MESSAGE_WINDOW_STEP = 50
        /** How close to the top the user must get before the window widens. */
        private const val WINDOW_EXPAND_TRIGGER_POSITION = 3

        // Insertion motion. Short and decelerating: a new message should land, not
        // drift. Change animations are off — when a neighbour's grouping flips, it
        // must rebind silently rather than cross-fade.
        private const val ITEM_ADD_DURATION_MS = 220L
        private const val ITEM_MOVE_DURATION_MS = 220L
        private const val ITEM_REMOVE_DURATION_MS = 160L

        // Ghost-bubble geometry for the loading skeleton, as a fraction of the
        // available width. Cycled so the block reads like a conversation rather
        // than a stack of identical bars.
        private val SKELETON_WIDTH_FRACTIONS = floatArrayOf(0.62f, 0.74f, 0.44f, 0.58f, 0.70f, 0.50f)
        private val SKELETON_TWO_LINE = booleanArrayOf(false, true, false, false, true, false)
        private const val SKELETON_MAX_ROWS = 6
        private const val SKELETON_ROW_HEIGHT_DP = 42
        private const val SKELETON_ROW_HEIGHT_TALL_DP = 64
    }

    private lateinit var ticketId: String
    private var currentTicket: SupportTicket? = null
    private var pendingOutgoing: List<SupportOutgoingDraft> = emptyList()
    private var failedOutgoing: List<SupportOutgoingDraft> = emptyList()
    private var liveTicketJob: Job? = null
    private var isLiveRefreshInFlight = false
    /** Index into POLL_INTERVALS_MS; climbs while the ticket is unchanged. */
    private var pollTier = 0
    /** Drains [pendingOutgoing] serially; see drainSendQueue. */
    private var sendJob: Job? = null
    private var hasRenderedConversation = false
    /** Drafts restored from a killed process, still to be reconciled against the
     *  first server response (see reconcileRestoredDrafts). */
    private var draftsAwaitingReconcile: List<SupportOutgoingDraft> = emptyList()
    private var sendButtonShown: Boolean? = null
    private var sendButtonAnimator: ValueAnimator? = null
    /** One evaluator for the send button's colour crossfade; allocating one per
     *  frame, twice, is pure churn on a path that runs on every keystroke boundary. */
    private val argbEvaluator = ArgbEvaluator()
    private var skeletonAnimator: ValueAnimator? = null
    private var isSkeletonVisible = false
    private var isEmptyStateVisible = false
    /** Subject seeded from the caller so the opening chip can render before the
     *  network answers; replaced by the server's copy once the ticket loads. */
    private var ticketSubject: String = ""

    private var messageWindow = MESSAGE_WINDOW_INITIAL
    private var hasOlderMessages = false
    private var isExpandingWindow = false
    /** Sampled while scrolling so a keyboard-driven resize knows whether the user
     *  was reading the latest message or scrolled back through history. */
    private var wasNearBottom = true

    /** The bottom inset the layout is currently padded for. The IME animation
     *  callback measures each frame against this to know how far behind the
     *  keyboard still is. */
    private var appliedBottomInset = 0
    /** Set while an inset change is deliberately held back until the keyboard
     *  animation finishes (see applyWindowInsets). */
    private var pendingBottomInset: Int? = null
    private var isImeAnimating = false
    private var composerBasePaddingBottom = 0
    /** Support replies that landed while the reader was scrolled away from the
     *  bottom; surfaced as the count on the jump-to-latest control. */
    private var unseenIncomingCount = 0
    private var lastIncomingCount = 0
    private var jumpToLatestShown: Boolean? = null
    private var messageMenu: PopupWindow? = null
    private var connectivityCallback: ConnectivityManager.NetworkCallback? = null
    private var isOfflineBannerShown: Boolean? = null

    private lateinit var chatAdapter: SupportChatAdapter
    private lateinit var chatLayoutManager: LinearLayoutManager

    private val frostRangePx by lazy { FROST_RANGE_DP * resources.displayMetrics.density }

    override fun getViewBinding(): ActivitySupportTicketChatBinding {
        return ActivitySupportTicketChatBinding.inflate(layoutInflater)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // The status bar is deliberately left transparent (BaseActivity's edge-to-edge
        // setup already made it so). Painting it background_primary here would cut the
        // frosted header with a hard band exactly where the gradient should be doing
        // the work — the bar has to sit *inside* the scrim, not above it.
        WindowInsetsControllerCompat(window, window.decorView).isAppearanceLightStatusBars =
            !ThemeManager.isDarkMode(this)

        ticketId = intent.getStringExtra(EXTRA_TICKET_ID).orEmpty()
        if (ticketId.isBlank()) {
            showToast(getString(R.string.unable_to_open_this_ticket))
            finish()
            return
        }

        ticketSubject = intent.getStringExtra(EXTRA_TICKET_TITLE)?.trim().orEmpty()
        updateHeaderIdentity()
        setHeaderStatus(
            getString(R.string.support_chat_status_loading),
            ContextCompat.getColor(this, R.color.text_tertiary)
        )

        binding.btnBack.setOnClickListener { finish() }
        // Both routes to the same sheet: the explicit button, and tapping the identity
        // block — the gesture iOS Messages trained everyone to expect.
        binding.btnDetails.setOnClickListener { showTicketDetails() }
        binding.headerIdentity.setOnClickListener { showTicketDetails() }
        binding.btnSendReply.setOnClickListener { submitComposerMessage() }
        binding.btnStartNewRequest.setOnClickListener {
            startActivity(Intent(this, NewSupportRequestActivity::class.java))
            finish()
        }
        binding.etReply.doAfterTextChanged { updateSendButtonState(animated = true) }
        binding.btnEmptyRetry.setOnClickListener {
            binding.btnEmptyRetry.isVisible = false
            loadTicket(showLoader = true)
        }
        updateSendButtonState(animated = false)

        restoreOutbox(savedInstanceState)
        // Only on a fresh entry. After a rotation the EditText has already restored
        // its own text from the view hierarchy state, and writing over it would put
        // the caret back and lose anything typed since the last onStop.
        if (savedInstanceState == null) restoreComposerDraft()
        setupConversationList()
        applyWindowInsets()
        setupFloatingBars()
        loadTicket(showLoader = true)
    }

    override fun onStart() {
        super.onStart()
        foregroundTicketId = ticketId
        // Coming back to the screen is a reason to check promptly.
        resetPollCadence()
        startLiveTicketUpdates()
        startConnectivityMonitoring()
    }

    override fun onStop() {
        // Compare before clearing: another instance may already have claimed it.
        if (foregroundTicketId == ticketId) foregroundTicketId = null
        stopLiveTicketUpdates()
        stopConnectivityMonitoring()
        persistComposerDraft()
        super.onStop()
    }

    // ---------------------------------------------------------------------
    // Outbox persistence
    //
    // The outbox used to be plain fields, so a rotation — or the process being
    // killed in the background — silently threw away anything that had not sent.
    // ---------------------------------------------------------------------

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        // In-flight and failed drafts are saved together: once we are torn down,
        // an in-flight request's outcome is unknowable, so both are restored as
        // "unconfirmed" and settled by reconcileRestoredDrafts.
        outState.putDrafts(STATE_UNCONFIRMED, pendingOutgoing + failedOutgoing)
    }

    private fun restoreOutbox(savedInstanceState: Bundle?) {
        val restored = savedInstanceState?.getDrafts(STATE_UNCONFIRMED).orEmpty()
        if (restored.isEmpty()) return
        // Held as failed, never auto-resent: re-sending a message that did reach the
        // server would double-post it into a live support thread.
        failedOutgoing = restored
        draftsAwaitingReconcile = restored
    }

    /**
     * Settle restored drafts against what the server actually has.
     *
     * A draft that is already in the ticket did send before we were killed, so it is
     * dropped from the outbox; anything still missing stays as a failed bubble the
     * user can retry. Matches are consumed one-for-one so sending the same text twice
     * doesn't collapse into a single match.
     */
    private fun reconcileRestoredDrafts(ticket: SupportTicket) {
        if (draftsAwaitingReconcile.isEmpty()) return

        val unclaimed = ticket.messages
            .filter { it.message.isNotBlank() }
            .toMutableList()
        val stillMissing = draftsAwaitingReconcile.filter { draft ->
            val match = unclaimed.firstOrNull {
                it.message == draft.message &&
                    (it.sentAt == null || !it.sentAt.before(draft.sentAt))
            }
            if (match != null) unclaimed.remove(match)
            match == null
        }

        failedOutgoing = failedOutgoing.filter { it in stillMissing }
        draftsAwaitingReconcile = emptyList()
    }

    private fun Bundle.putDrafts(key: String, drafts: List<SupportOutgoingDraft>) {
        putStringArray("$key.text", drafts.map { it.message }.toTypedArray())
        putLongArray("$key.at", drafts.map { it.sentAt.time }.toLongArray())
    }

    private fun Bundle.getDrafts(key: String): List<SupportOutgoingDraft> {
        val texts = getStringArray("$key.text") ?: return emptyList()
        val times = getLongArray("$key.at") ?: return emptyList()
        if (texts.size != times.size) return emptyList()
        return texts.mapIndexed { index, text ->
            SupportOutgoingDraft(text, Date(times[index]))
        }
    }

    override fun onDestroy() {
        stopSkeletonPulse()
        sendButtonAnimator?.cancel()
        sendButtonAnimator = null
        // A PopupWindow outlives its activity if left showing — leaks the window token.
        dismissMessageMenu()
        super.onDestroy()
    }

    // ---------------------------------------------------------------------
    // Conversation list
    // ---------------------------------------------------------------------

    private fun setupConversationList() {
        chatAdapter = SupportChatAdapter(
            onMessageLongPress = ::showMessageMenu,
            onRetry = ::retryFailedMessage
        )
        binding.btnJumpToLatest.setOnClickListener {
            unseenIncomingCount = 0
            scrollToLatest(smooth = true)
            updateJumpToLatest()
        }
        // stackFromEnd anchors the thread to the bottom: the newest message hugs the
        // composer, and a short conversation sits down there instead of floating at
        // the top of an empty screen.
        chatLayoutManager = LinearLayoutManager(this).apply { stackFromEnd = true }

        binding.swipeMessages.apply {
            setColorSchemeColors(color(R.color.brand_primary))
            setProgressBackgroundColorSchemeColor(color(R.color.background_secondary))
            setOnRefreshListener {
                // A manual pull is the clearest possible signal that the user is
                // waiting on something, so drop straight back to the fast poll tier.
                resetPollCadence()
                refreshFromPull()
            }
        }

        binding.rvMessages.apply {
            layoutManager = chatLayoutManager
            adapter = chatAdapter
            setHasFixedSize(false)
            itemAnimator = DefaultItemAnimator().apply {
                supportsChangeAnimations = false
                addDuration = ITEM_ADD_DURATION_MS
                moveDuration = ITEM_MOVE_DURATION_MS
                removeDuration = ITEM_REMOVE_DURATION_MS
                changeDuration = 0L
            }
            addOnScrollListener(object : RecyclerView.OnScrollListener() {
                override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                    updateFrostedHeader(recyclerView.computeVerticalScrollOffset())
                    wasNearBottom = isNearBottom()
                    // Reaching the bottom *is* reading them.
                    if (wasNearBottom) unseenIncomingCount = 0
                    updateJumpToLatest()
                    maybeExpandWindow()
                }

                override fun onScrollStateChanged(recyclerView: RecyclerView, newState: Int) {
                    // Dragging the conversation puts the keyboard away — the iOS
                    // Messages gesture, and more discoverable than tap-to-dismiss.
                    if (newState == RecyclerView.SCROLL_STATE_DRAGGING) {
                        hideKeyboard()
                        dismissMessageMenu()
                    }
                }
            })
        }
    }

    /**
     * Widen the rendering window when the user reaches the top of what is currently
     * rendered. The data is already on the device, so this is instant and needs no
     * spinner; prepending above the anchor leaves the scroll position untouched.
     */
    private fun maybeExpandWindow() {
        if (!hasOlderMessages || isExpandingWindow) return
        if (chatLayoutManager.findFirstVisibleItemPosition() > WINDOW_EXPAND_TRIGGER_POSITION) return

        isExpandingWindow = true
        messageWindow += MESSAGE_WINDOW_STEP
        currentTicket?.let { renderTicket(it, forcePinToLatest = false) } ?: run {
            isExpandingWindow = false
        }
    }

    private fun isNearBottom(): Boolean {
        val recycler = binding.rvMessages
        val remaining = recycler.computeVerticalScrollRange() -
            recycler.computeVerticalScrollOffset() -
            recycler.computeVerticalScrollExtent()
        return remaining <= dp(NEAR_BOTTOM_SLOP_DP)
    }

    private fun scrollToLatest(smooth: Boolean) {
        val lastPosition = chatAdapter.itemCount - 1
        if (lastPosition < 0) return
        if (smooth) {
            binding.rvMessages.smoothScrollToPosition(lastPosition)
        } else {
            chatLayoutManager.scrollToPosition(lastPosition)
        }
    }

    // ---------------------------------------------------------------------
    // Jump to latest
    // ---------------------------------------------------------------------

    /**
     * Shown whenever the newest message is off screen. Without it, a reply that
     * lands while you are reading back through the thread is invisible — the list
     * quietly grows below the fold and nothing says so.
     */
    private fun updateJumpToLatest() {
        val show = !isNearBottom() && chatAdapter.itemCount > 0

        binding.tvJumpCount.isVisible = unseenIncomingCount > 0
        if (unseenIncomingCount > 0) {
            binding.tvJumpCount.text = resources.getQuantityString(
                R.plurals.support_chat_new_messages,
                unseenIncomingCount,
                unseenIncomingCount
            )
        }

        if (show == jumpToLatestShown) return
        jumpToLatestShown = show

        val button = binding.btnJumpToLatest
        button.animate().cancel()
        if (show) {
            button.visibility = View.VISIBLE
            button.animate()
                .alpha(1f)
                .scaleX(1f)
                .scaleY(1f)
                .setDuration(180L)
                .setInterpolator(DecelerateInterpolator())
                .start()
        } else {
            button.animate()
                .alpha(0f)
                .scaleX(0.8f)
                .scaleY(0.8f)
                .setDuration(140L)
                .setInterpolator(DecelerateInterpolator())
                .withEndAction { button.visibility = View.INVISIBLE }
                .start()
        }
    }

    // ---------------------------------------------------------------------
    // Message long-press menu
    // ---------------------------------------------------------------------

    /**
     * A long press used to copy silently, which gave no confirmation and offered no
     * other action. This opens a small card anchored to the bubble's near edge — the
     * bubble itself shrinks slightly so it reads as the thing being acted on.
     */
    private fun showMessageMenu(bubble: View, item: SupportChatItem.Message) {
        dismissMessageMenu()

        val menu = ViewChatMessageMenuBinding.inflate(layoutInflater)
        val isFailed = item.failedDraft != null
        menu.rowRetry.isVisible = isFailed
        menu.rowDelete.isVisible = isFailed

        val popup = PopupWindow(
            menu.root,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            true
        ).apply {
            setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            elevation = dp(14).toFloat()
            isOutsideTouchable = true
        }
        messageMenu = popup

        menu.rowCopy.setOnClickListener {
            copyMessage(item.text)
            popup.dismiss()
        }
        menu.rowRetry.setOnClickListener {
            popup.dismiss()
            item.failedDraft?.let { retryFailedMessage(it) }
        }
        menu.rowDelete.setOnClickListener {
            popup.dismiss()
            item.failedDraft?.let { discardFailedMessage(it) }
        }

        // Measure so the card can be placed against the bubble rather than dropped
        // below it — a bubble near the composer has no room underneath.
        menu.root.measure(
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
        )
        val menuWidth = menu.root.measuredWidth
        val menuHeight = menu.root.measuredHeight

        val anchor = IntArray(2).also { bubble.getLocationInWindow(it) }
        val rootHeight = binding.root.height
        val rootWidth = binding.root.width
        val gap = dp(6)
        val spaceBelow = rootHeight - (anchor[1] + bubble.height) - binding.bottomPanel.height
        val placeAbove = spaceBelow < menuHeight + gap

        val y = if (placeAbove) anchor[1] - menuHeight - gap else anchor[1] + bubble.height + gap
        // Align to the bubble's own edge, so the menu belongs to that side of the thread.
        val x = if (item.isOutgoing) anchor[0] + bubble.width - menuWidth else anchor[0]

        popup.showAtLocation(
            binding.root,
            Gravity.NO_GRAVITY,
            x.coerceIn(dp(12), (rootWidth - menuWidth - dp(12)).coerceAtLeast(dp(12))),
            y.coerceAtLeast(dp(12))
        )

        // Grow out of the corner nearest the bubble.
        menu.root.apply {
            pivotX = if (item.isOutgoing) menuWidth.toFloat() else 0f
            pivotY = if (placeAbove) menuHeight.toFloat() else 0f
            alpha = 0f
            scaleX = 0.9f
            scaleY = 0.9f
            animate().alpha(1f).scaleX(1f).scaleY(1f)
                .setDuration(140L)
                .setInterpolator(DecelerateInterpolator())
                .start()
        }

        bubble.animate().scaleX(0.97f).scaleY(0.97f).setDuration(120L).start()
        popup.setOnDismissListener {
            messageMenu = null
            bubble.animate().scaleX(1f).scaleY(1f).setDuration(160L).start()
        }
    }

    private fun dismissMessageMenu() {
        messageMenu?.dismiss()
        messageMenu = null
    }

    /** Drop a message that never sent. Local-only — it never reached the server. */
    private fun discardFailedMessage(draft: SupportOutgoingDraft) {
        failedOutgoing = failedOutgoing - draft
        currentTicket?.let { renderTicket(it, forcePinToLatest = false) }
    }

    // ---------------------------------------------------------------------
    // Floating bars: the conversation scrolls edge-to-edge beneath the
    // frosted header and the composer capsule, so their measured heights
    // become the list's top/bottom padding.
    // ---------------------------------------------------------------------

    /**
     * The root does not fit system windows, so the frosted bars can bleed under the
     * status bar and the gesture bar. Insets are instead added to the two things that
     * actually hold content: the header's top padding and the composer's bottom
     * padding. The list's own padding follows from their measured heights, so nothing
     * here needs to know about bar sizes twice.
     */
    private fun applyWindowInsets() {
        val headerTopPadding = binding.layoutHeader.paddingTop
        composerBasePaddingBottom = binding.bottomPanel.paddingBottom

        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { _, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            val ime = insets.getInsets(WindowInsetsCompat.Type.ime())
            binding.layoutHeader.updatePadding(top = headerTopPadding + bars.top)

            // The keyboard supersedes the gesture bar rather than stacking with it.
            val target = maxOf(bars.bottom, ime.bottom)
            if (!isImeAnimating || target < appliedBottomInset) {
                // Either a normal inset change, or the keyboard is going away. Lay out
                // for the *smaller* inset straight away and hold the furniture up with
                // a translation — that way every frame of the animation translates
                // upward. Translating downward would drag the list's top edge with it
                // and open an empty band under the header on a long thread.
                val holdUp = appliedBottomInset - target
                applyBottomInset(target)
                if (isImeAnimating) setImeTranslation(-holdUp.toFloat())
            } else {
                // Keyboard arriving: keep the current layout and defer, so the list
                // and composer ride up with it rather than teleporting.
                pendingBottomInset = target
            }
            insets
        }

        // Insets are delivered once, already at their final value, the moment the
        // keyboard starts moving — which is why padding alone makes the composer
        // teleport while the keyboard is still sliding. This callback runs on every
        // frame of the IME animation, and moves the bottom furniture by translation
        // only, so nothing re-lays-out mid-flight.
        ViewCompat.setWindowInsetsAnimationCallback(
            binding.root,
            object : WindowInsetsAnimationCompat.Callback(DISPATCH_MODE_STOP) {

                override fun onPrepare(animation: WindowInsetsAnimationCompat) {
                    if (animation.typeMask and WindowInsetsCompat.Type.ime() == 0) return
                    isImeAnimating = true
                }

                override fun onProgress(
                    insets: WindowInsetsCompat,
                    runningAnimations: MutableList<WindowInsetsAnimationCompat>
                ): WindowInsetsCompat {
                    if (!isImeAnimating) return insets
                    val ime = insets.getInsets(WindowInsetsCompat.Type.ime()).bottom
                    val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars()).bottom
                    setImeTranslation(-(maxOf(ime, bars) - appliedBottomInset).toFloat())
                    return insets
                }

                override fun onEnd(animation: WindowInsetsAnimationCompat) {
                    if (animation.typeMask and WindowInsetsCompat.Type.ime() == 0) return
                    isImeAnimating = false

                    val target = pendingBottomInset
                    pendingBottomInset = null
                    if (target == null) {
                        // Normally the hide path, which already converged to zero.
                        // A leftover offset means the deferred inset never arrived —
                        // re-dispatch now that the gate is open rather than snapping
                        // the composer back under a keyboard that is still up.
                        val stranded = binding.bottomPanel.translationY != 0f
                        setImeTranslation(0f)
                        if (stranded) ViewCompat.requestApplyInsets(binding.root)
                        return
                    }
                    // Grow the composer and drop the translation in the same frame:
                    // the pre-draw hook fires after the new padding has been laid
                    // out, so the hand-off is invisible.
                    applyBottomInset(target)
                    OneShotPreDrawListener.add(binding.root) { setImeTranslation(0f) }
                }
            }
        )

        ViewCompat.requestApplyInsets(binding.root)
    }

    private fun applyBottomInset(inset: Int) {
        appliedBottomInset = inset
        binding.bottomPanel.updatePadding(bottom = composerBasePaddingBottom + inset)
    }

    /**
     * Everything anchored to the bottom moves as one during the IME animation — the
     * list included, so the newest message stays glued to the composer instead of
     * jumping to its final position a frame early.
     */
    private fun setImeTranslation(offset: Float) {
        // The swipe container, not the list inside it — translating the child would
        // leave the refresh spinner's parent behind and the spinner would ride up
        // out of alignment with the conversation it belongs to.
        binding.swipeMessages.translationY = offset
        binding.overlayStates.translationY = offset
        binding.viewBottomFrost.translationY = offset
        binding.bottomPanel.translationY = offset
        binding.btnJumpToLatest.translationY = offset
    }

    private fun setupFloatingBars() {
        // Header height changes when insets land and again if the font scale is large,
        // so this tracks layout rather than firing once.
        binding.layoutHeader.addOnLayoutChangeListener { _, _, top, _, bottom, _, oldTop, _, oldBottom ->
            val height = bottom - top
            if (height > 0 && height != oldBottom - oldTop) {
                setConversationPadding(top = height + dp(6))
                // The solid slab covers exactly the header; the tail view below it
                // does the dissolving.
                binding.viewTopFrost.updateLayoutParams { this.height = height }
                // The spinner has to clear the floating header, which sits over the
                // list rather than above it — at the default offset it would spin
                // underneath the title.
                binding.swipeMessages.setProgressViewOffset(
                    false, height, height + dp(SWIPE_SPINNER_TRAVEL_DP)
                )
            }
        }
        binding.bottomPanel.addOnLayoutChangeListener { _, _, top, _, bottom, _, oldTop, _, oldBottom ->
            val height = bottom - top
            if (height > 0 && height != oldBottom - oldTop) {
                setConversationPadding(bottom = height + dp(6))
                binding.viewBottomFrost.updateLayoutParams {
                    this.height = height + dp(FROST_BOTTOM_TAIL_DP)
                }
            }
        }
        // Keep the latest message pinned above the keyboard when the window resizes.
        binding.rvMessages.addOnLayoutChangeListener { _, _, t, _, b, _, oldT, _, oldB ->
            val newHeight = b - t
            val oldHeight = oldB - oldT
            if (oldHeight > 0 && newHeight < oldHeight && wasNearBottom) {
                binding.rvMessages.post { scrollToLatest(smooth = false) }
            }
        }
    }

    /** The list and the state overlay share one optical frame, so they share padding. */
    private fun setConversationPadding(top: Int? = null, bottom: Int? = null) {
        binding.rvMessages.updatePadding(
            top = top ?: binding.rvMessages.paddingTop,
            bottom = bottom ?: binding.rvMessages.paddingBottom
        )
        binding.overlayStates.updatePadding(
            top = top ?: binding.overlayStates.paddingTop,
            bottom = bottom ?: binding.overlayStates.paddingBottom
        )
    }

    /**
     * The header scrim only exists to keep the title legible over moving content, so
     * it stays invisible until content is actually under it. On a short conversation
     * (everything sits above the composer, nothing reaches the header) it never
     * appears at all, and the header simply floats on the page.
     */
    private fun updateFrostedHeader(scrollOffsetPx: Int) {
        val alpha = if (scrollOffsetPx <= 0) {
            0f
        } else {
            val t = 1f - (scrollOffsetPx / frostRangePx).coerceIn(0f, 1f)
            1f - (t * t * t)
        }
        // Slab and tail are one scrim; they must never drift apart.
        binding.viewTopFrost.alpha = alpha
        binding.viewTopFrostTail.alpha = alpha
    }

    // ---------------------------------------------------------------------
    // Loading + live refresh
    // ---------------------------------------------------------------------

    private fun loadTicket(showLoader: Boolean) {
        if (showLoader) {
            showSkeleton()
            setEmptyStateVisible(false)
        }

        lifecycleScope.launch {
            try {
                val response = ApiClient.apiService.getSupportTicket(ticketId)
                val ticket = response.body()
                if (response.isSuccessful && ticket != null) {
                    renderTicket(ticket, forcePinToLatest = true)
                } else {
                    Log.w(TAG, "Ticket load failed: HTTP ${response.code()}")
                    showLoadError()
                }
            } catch (e: Exception) {
                Log.w(TAG, "Ticket load failed", e)
                showLoadError()
            } finally {
                hideSkeleton()
            }
        }
    }

    /**
     * A pull refresh. Deliberately not [loadTicket]: that swaps in the skeleton and
     * would blank a conversation the user is looking at. This keeps what is on screen
     * and only replaces it once the server answers — and on failure leaves it alone
     * entirely, because the thread you already have is better than an error page.
     */
    private fun refreshFromPull() {
        lifecycleScope.launch {
            try {
                refreshTicketSilently()
            } finally {
                // The spinner has to stop whatever happened, including a cancelled
                // scope tearing this coroutine down mid-request.
                binding.swipeMessages.isRefreshing = false
            }
        }
    }

    private fun showLoadError() {
        // Drop the skeleton first so the error settles into an empty page rather than
        // appearing on top of ghost bubbles for a frame.
        hideSkeleton()
        binding.tvEmptyTitle.text = getString(R.string.unable_to_load_this_ticket)
        // Deliberately not the exception text or an HTTP code: neither means anything
        // to the person reading it. The detail goes to logcat instead.
        binding.tvEmptySubtitle.text = getString(R.string.support_chat_load_error_body)
        binding.btnEmptyRetry.isVisible = true
        setEmptyStateVisible(true)
        binding.replyComposer.isVisible = false
        binding.closedPanel.isVisible = false
        SkeletonShimmer.revealView(binding.emptyState)
    }

    private fun startLiveTicketUpdates() {
        // onStart still runs when onCreate bailed out on a missing id; don't poll an
        // endpoint we know can't resolve.
        if (ticketId.isBlank()) return
        if (liveTicketJob?.isActive == true) return

        liveTicketJob = lifecycleScope.launch {
            while (isActive) {
                delay(POLL_INTERVALS_MS[pollTier])
                refreshTicketSilently()
            }
        }
    }

    private fun stopLiveTicketUpdates() {
        liveTicketJob?.cancel()
        liveTicketJob = null
        isLiveRefreshInFlight = false
    }

    /** Something the user did makes a reply likely; listen closely again. */
    private fun resetPollCadence() {
        pollTier = 0
    }

    private suspend fun refreshTicketSilently() {
        if (isLiveRefreshInFlight) return

        isLiveRefreshInFlight = true
        try {
            val response = ApiClient.apiService.getSupportTicket(ticketId)
            val updatedTicket = response.body()
            if (response.isSuccessful && updatedTicket != null) {
                val previousTicket = currentTicket
                val changed = previousTicket == null ||
                    ticketFingerprint(previousTicket) != ticketFingerprint(updatedTicket)
                if (changed) {
                    resetPollCadence()
                    renderTicket(updatedTicket, forcePinToLatest = false)
                } else {
                    // Quiet thread: ease off rather than hammering the same payload.
                    pollTier = (pollTier + 1).coerceAtMost(POLL_INTERVALS_MS.lastIndex)
                }
            }
        } catch (_: Exception) {
            // Keep live polling quiet; the visible load path reports errors. Treat a
            // failure as a quiet tick so a flaky network doesn't turn into a tight
            // retry loop.
            pollTier = (pollTier + 1).coerceAtMost(POLL_INTERVALS_MS.lastIndex)
        } finally {
            isLiveRefreshInFlight = false
        }
    }

    // ---------------------------------------------------------------------
    // Connectivity
    //
    // Polling failures are swallowed on purpose (see refreshTicketSilently), so
    // an offline device looked exactly like a thread nobody was answering. This
    // is the one place the screen tells the difference.
    // ---------------------------------------------------------------------

    private fun startConnectivityMonitoring() {
        if (connectivityCallback != null) return
        val manager = getSystemService(ConnectivityManager::class.java) ?: return

        // Seed from the current network, otherwise the banner only ever appears on
        // the first *change* — open the screen already offline and nothing shows.
        setOfflineBannerVisible(!manager.hasInternet())

        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) = postOfflineState(false)
            override fun onLost(network: Network) {
                // onLost fires per network; losing Wi-Fi while mobile data is up is
                // not being offline, so re-ask rather than trusting the event.
                postOfflineState(!manager.hasInternet())
            }
            override fun onUnavailable() = postOfflineState(true)
        }
        connectivityCallback = callback

        val request = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()
        // Registration can throw if the process is being torn down or the permission
        // was revoked; an unreported banner is not worth crashing a live screen over.
        runCatching { manager.registerNetworkCallback(request, callback) }
            .onFailure { connectivityCallback = null }
    }

    private fun stopConnectivityMonitoring() {
        val callback = connectivityCallback ?: return
        connectivityCallback = null
        val manager = getSystemService(ConnectivityManager::class.java) ?: return
        // Throws if it was never actually registered.
        runCatching { manager.unregisterNetworkCallback(callback) }
    }

    private fun ConnectivityManager.hasInternet(): Boolean {
        val capabilities = getNetworkCapabilities(activeNetwork) ?: return false
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
            capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    }

    /** Network callbacks arrive on a background thread; views must not be touched there. */
    private fun postOfflineState(offline: Boolean) {
        binding.root.post { setOfflineBannerVisible(offline) }
    }

    private fun setOfflineBannerVisible(offline: Boolean) {
        if (offline == isOfflineBannerShown) return
        isOfflineBannerShown = offline

        val banner = binding.bannerOffline
        banner.animate().cancel()
        if (offline) {
            banner.visibility = View.VISIBLE
            banner.alpha = 0f
            banner.translationY = -dp(10).toFloat()
            banner.animate()
                .alpha(1f)
                .translationY(0f)
                .setDuration(200L)
                .setInterpolator(DecelerateInterpolator())
                .start()
        } else {
            banner.animate()
                .alpha(0f)
                .translationY(-dp(10).toFloat())
                .setDuration(160L)
                .setInterpolator(DecelerateInterpolator())
                .withEndAction { banner.visibility = View.INVISIBLE }
                .start()
        }
    }

    // ---------------------------------------------------------------------
    // Loading skeleton — ghost bubbles, not a spinner. Matches the app's one
    // loading idiom: placeholders shaped like the content they stand in
    // for, breath-pulsed in unison, then replaced by a staggered rise-in.
    // ---------------------------------------------------------------------

    private fun showSkeleton() {
        val container = binding.skeletonConversation
        if (container.childCount == 0) {
            // Fill the viewport we have, capped — a screenful of ghosts on a tall
            // phone, fewer on a short one, never more than a conversation would show.
            val available = binding.overlayStates.height -
                binding.overlayStates.paddingTop - binding.overlayStates.paddingBottom
            val rowCount = if (available > 0) {
                (available / dp(SKELETON_ROW_HEIGHT_TALL_DP)).coerceIn(3, SKELETON_MAX_ROWS)
            } else {
                SKELETON_MAX_ROWS
            }
            repeat(rowCount) { index -> container.addView(buildSkeletonBubble(index)) }
        }
        isSkeletonVisible = true
        updateOverlayState()
        skeletonAnimator = SkeletonShimmer.start(container)
    }

    private fun hideSkeleton() {
        if (!isSkeletonVisible) return
        isSkeletonVisible = false
        stopSkeletonPulse()
        updateOverlayState()
    }

    private fun stopSkeletonPulse() {
        skeletonAnimator?.cancel()
        skeletonAnimator = null
    }

    private fun setEmptyStateVisible(visible: Boolean) {
        isEmptyStateVisible = visible
        updateOverlayState()
    }

    /** One place decides what the overlay shows, so the two states can't both win. */
    private fun updateOverlayState() {
        binding.skeletonConversation.isVisible = isSkeletonVisible
        binding.emptyState.isVisible = isEmptyStateVisible && !isSkeletonVisible
        binding.overlayStates.isVisible = isSkeletonVisible || isEmptyStateVisible
        binding.rvMessages.isVisible = !isSkeletonVisible
        // Nothing to refresh while the first load is still running, and a spinner
        // over ghost bubbles is two loading indicators for one wait.
        binding.swipeMessages.isEnabled = !isSkeletonVisible
    }

    /** One ghost bubble, alternating sides and mirroring real bubble geometry. */
    private fun buildSkeletonBubble(index: Int): View {
        val isOutgoing = index % 2 == 0
        val twoLine = SKELETON_TWO_LINE[index % SKELETON_TWO_LINE.size]
        val widthFraction = SKELETON_WIDTH_FRACTIONS[index % SKELETON_WIDTH_FRACTIONS.size]

        val row = FrameLayout(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dp(12) }
        }

        row.addView(View(this).apply {
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dp(20).toFloat()
                setColor(color(R.color.skeleton_base))
            }
            // Tagged so SkeletonShimmer drives every pill on the screen from one animator.
            tag = SkeletonShimmer.PILL_TAG
            layoutParams = FrameLayout.LayoutParams(
                (resources.displayMetrics.widthPixels * widthFraction).toInt(),
                dp(if (twoLine) SKELETON_ROW_HEIGHT_TALL_DP else SKELETON_ROW_HEIGHT_DP)
            ).apply {
                gravity = if (isOutgoing) Gravity.END else Gravity.START
            }
        })
        return row
    }

    // ---------------------------------------------------------------------
    // Sending
    // ---------------------------------------------------------------------

    private fun submitComposerMessage() {
        val ticket = currentTicket
        if (ticket == null) {
            showToast(getString(R.string.ticket_is_still_loading))
            return
        }
        if (!canReplyToTicket(ticket)) {
            showToast(getString(R.string.this_ticket_is_closed))
            return
        }

        val message = binding.etReply.text.toString().trim()
        if (message.isEmpty()) {
            binding.etReply.requestFocus()
            return
        }

        binding.btnSendReply.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
        binding.etReply.text.clear()
        // The message is the outbox's problem now; drop it from the draft store so a
        // later crash can't resurrect it into the composer beside its own bubble.
        persistComposerDraft()
        enqueueOutgoingMessage(SupportOutgoingDraft(message, Date()))
    }

    /**
     * Messages are queued, not gated.
     *
     * Sending used to bail out silently whenever a request was already in flight, so
     * typing two messages quickly dropped the second one with no feedback at all.
     * [pendingOutgoing] is now a real outbox and [drainSendQueue] empties it one at a
     * time — one at a time because a conversation has an order, and the server
     * timestamps on arrival.
     */
    private fun enqueueOutgoingMessage(draft: SupportOutgoingDraft) {
        pendingOutgoing = pendingOutgoing + draft
        // A reply is most likely right after you say something.
        resetPollCadence()
        currentTicket?.let { renderTicket(it, forcePinToLatest = true) }
        drainSendQueue()
    }

    private fun drainSendQueue() {
        if (sendJob?.isActive == true) return

        sendJob = lifecycleScope.launch {
            while (pendingOutgoing.isNotEmpty()) {
                val draft = pendingOutgoing.first()
                val updatedTicket = try {
                    val response = ApiClient.apiService.addSupportTicketMessage(
                        ticketId,
                        AddSupportTicketMessageRequest(message = draft.message)
                    )
                    response.body().takeIf { response.isSuccessful }
                } catch (_: Exception) {
                    null
                }

                pendingOutgoing = pendingOutgoing - draft
                if (updatedTicket == null) {
                    failedOutgoing = failedOutgoing + draft
                }
                // The send response carries the whole updated ticket; fall back to the
                // one on screen when it failed.
                val ticketToRender = updatedTicket ?: currentTicket
                if (ticketToRender == null) break
                renderTicket(ticketToRender, forcePinToLatest = true)
            }
        }
    }

    private fun retryFailedMessage(draft: SupportOutgoingDraft) {
        failedOutgoing = failedOutgoing - draft
        enqueueOutgoingMessage(SupportOutgoingDraft(draft.message, Date()))
    }

    // ---------------------------------------------------------------------
    // Rendering
    // ---------------------------------------------------------------------

    private fun renderTicket(ticket: SupportTicket, forcePinToLatest: Boolean) {
        val firstRender = !hasRenderedConversation
        val shouldPinToLatest = forcePinToLatest || firstRender || isNearBottom()

        currentTicket = ticket
        ticket.subject.trim().takeIf { it.isNotBlank() }?.let { ticketSubject = it }
        updateHeaderIdentity()
        setHeaderStatus(formatStatusLabel(ticket.status), statusColor(ticket.status))
        reconcileRestoredDrafts(ticket)

        // Replies that land while the reader is scrolled away accumulate on the
        // jump-to-latest badge; if we are pinning to the bottom they are seen on
        // arrival and nothing accrues.
        val incomingCount = SupportConversationBuilder.incomingCount(ticket)
        val newIncoming = incomingCount > lastIncomingCount
        if (hasRenderedConversation && newIncoming) {
            // A reply landing while you are on the screen deserves to be felt, not
            // just to appear.
            binding.root.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
            if (!shouldPinToLatest) unseenIncomingCount += incomingCount - lastIncomingCount
        }
        lastIncomingCount = incomingCount
        if (shouldPinToLatest) unseenIncomingCount = 0

        val conversation = SupportConversationBuilder.build(
            ticket = ticket,
            subject = ticketSubject,
            pending = pendingOutgoing,
            failed = failedOutgoing,
            windowSize = messageWindow
        )
        hasOlderMessages = conversation.hasOlderMessages

        if (conversation.isEmpty) {
            binding.tvEmptyTitle.text = getString(R.string.start_the_conversation)
            binding.tvEmptySubtitle.text = getString(R.string.send_a_message_and_our_team)
            // Nothing to retry — this is an empty thread, not a failure.
            binding.btnEmptyRetry.isVisible = false
        }
        setEmptyStateVisible(conversation.isEmpty)

        val canReply = canReplyToTicket(ticket)
        binding.replyComposer.isVisible = canReply
        binding.closedPanel.isVisible = !canReply
        if (!canReply) {
            binding.tvClosedNotice.text = getString(
                if (isTicketClosed(ticket)) R.string.this_ticket_is_closed_you_can
                else R.string.support_chat_replies_unavailable
            )
            binding.btnStartNewRequest.isVisible = isTicketClosed(ticket)
        }
        updateSendButtonState(animated = false)

        if (firstRender) hideSkeleton()
        hasRenderedConversation = true

        // DiffUtil runs off the main thread; everything that depends on the new rows
        // being laid out happens in the commit callback.
        chatAdapter.submitList(conversation.items) {
            isExpandingWindow = false
            if (shouldPinToLatest) {
                scrollToLatest(smooth = !firstRender)
            }
            updateFrostedHeader(binding.rvMessages.computeVerticalScrollOffset())
            wasNearBottom = isNearBottom()
            updateJumpToLatest()
            if (firstRender) {
                if (conversation.isEmpty) {
                    SkeletonShimmer.revealView(binding.emptyState)
                } else {
                    SkeletonShimmer.revealStagger(binding.rvMessages)
                }
            }
        }
    }

    // ---------------------------------------------------------------------
    // Header + composer state
    // ---------------------------------------------------------------------

    /**
     * The header leads with the subject, because on a long thread it is the only
     * thing that says what the conversation is about — it used to live solely in the
     * opener chip, which scrolls away and never comes back.
     *
     * A ticket with no subject falls back to naming the counterparty, so the header
     * is never blank while the first fetch is in flight.
     */
    private fun updateHeaderIdentity() {
        val title = ticketSubject.ifBlank { getString(R.string.support_chat_title) }
        if (binding.tvChatTitle.text?.toString() == title) return
        binding.tvChatTitle.text = title
        // One description for the whole block: read field by field, TalkBack announces
        // the subject and then the identity line as two unrelated labels on one target.
        binding.headerIdentity.contentDescription = title
    }

    /**
     * The status dot is one drawable for the life of the screen. It used to be
     * reallocated on every status update — including on every poll that changed
     * anything — to recolour six device-independent pixels.
     */
    private val statusDotDrawable by lazy {
        GradientDrawable()
            .apply { shape = GradientDrawable.OVAL }
            .also { binding.viewStatusDot.background = it }
    }

    private fun setHeaderStatus(label: String, dotColor: Int) {
        // The second line carries both who you are talking to and where the request
        // has got to; the dot is the status colour swatch in front of them.
        binding.tvChatStatus.text = getString(
            R.string.support_chat_identity_and_status,
            getString(R.string.support_chat_title),
            label
        )
        statusDotDrawable.setColor(dotColor)
    }

    /**
     * The facts underneath the conversation: reference, dates, location, timeline.
     * Needs a loaded ticket — everything it shows comes from the server, and an
     * empty sheet is worse than no sheet.
     */
    private fun showTicketDetails() {
        val ticket = currentTicket
        if (ticket == null) {
            showToast(getString(R.string.ticket_is_still_loading))
            return
        }
        // A double tap on the identity block would otherwise stack two sheets.
        if (supportFragmentManager.findFragmentByTag(TicketDetailsBottomSheet.TAG) != null) return

        TicketDetailsBottomSheet.newInstance(
            ticketId = ticket.id,
            subject = ticketSubject,
            status = ticket.status,
            createdAt = ticket.createdAt,
            updatedAt = ticket.updatedAt,
            location = ticket.parkingLotName
        ).show(supportFragmentManager, TicketDetailsBottomSheet.TAG)
    }

    /**
     * The send button is always on screen; only its colour changes.
     *
     * It used to pop in and out of INVISIBLE with the text, which left the pill's
     * right end empty at rest — space reserved for a control that wasn't there. Now
     * it rests as a muted tile and fills with brand colour once there is something
     * to send, so the affordance is legible before you start typing.
     *
     * [isEnabled] follows the same condition rather than staying true: a button that
     * accepts a tap and does nothing is a dead spot, and TalkBack should say so.
     */
    private fun updateSendButtonState(animated: Boolean) {
        val canReply = currentTicket?.let { canReplyToTicket(it) } != false
        val hasText = binding.etReply.text?.isNotBlank() == true
        val active = canReply && hasText
        binding.btnSendReply.isEnabled = active

        if (active == sendButtonShown) return
        val isFirstPass = sendButtonShown == null
        sendButtonShown = active

        val surfaceTarget = color(if (active) R.color.brand_primary else R.color.circle_icon_bg)
        val iconTarget = color(if (active) R.color.btn_text_on_brand else R.color.text_tertiary)

        sendButtonAnimator?.cancel()
        if (!animated || isFirstPass) {
            applySendButtonColors(surfaceTarget, iconTarget)
            return
        }

        // Crossfade both colours together off one animator — running the card and the
        // icon on separate animations lets them drift a frame apart, and the arrow
        // briefly reads as the wrong colour against its own tile.
        val surfaceFrom = binding.btnSendReply.cardBackgroundColor.defaultColor
        val iconFrom = binding.ivSendIcon.imageTintList?.defaultColor ?: iconTarget
        sendButtonAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 170L
            interpolator = DecelerateInterpolator()
            addUpdateListener { animator ->
                val fraction = animator.animatedValue as Float
                applySendButtonColors(
                    argbEvaluator.evaluate(fraction, surfaceFrom, surfaceTarget) as Int,
                    argbEvaluator.evaluate(fraction, iconFrom, iconTarget) as Int
                )
            }
            start()
        }
    }

    private fun applySendButtonColors(surface: Int, icon: Int) {
        binding.btnSendReply.setCardBackgroundColor(surface)
        binding.ivSendIcon.imageTintList = ColorStateList.valueOf(icon)
    }

    // ---------------------------------------------------------------------
    // Composer draft
    //
    // The outbox survives process death; a half-typed message did not survive
    // simply leaving the screen. Backing out to check a booking reference and
    // coming back cleared everything you had written.
    // ---------------------------------------------------------------------

    private fun draftPreferences() =
        getSharedPreferences(COMPOSER_DRAFT_PREFS, MODE_PRIVATE)

    private fun restoreComposerDraft() {
        // Keyed per ticket: drafts must not leak from one conversation into another.
        val draft = draftPreferences().getString(ticketId, null).orEmpty()
        if (draft.isBlank()) return
        binding.etReply.setText(draft)
        binding.etReply.setSelection(draft.length)
    }

    private fun persistComposerDraft() {
        val draft = binding.etReply.text?.toString().orEmpty()
        draftPreferences().edit().apply {
            // An empty draft is an absence, not a value worth storing.
            if (draft.isBlank()) remove(ticketId) else putString(ticketId, draft)
        }.apply()
    }

    private fun hideKeyboard() {
        if (!binding.etReply.hasFocus()) return
        val imm = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
        imm.hideSoftInputFromWindow(binding.etReply.windowToken, 0)
        binding.etReply.clearFocus()
    }

    private fun copyMessage(text: String) {
        val clipboard = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("Message", text))
        // Android 13+ shows its own clipboard confirmation overlay.
        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.S_V2) {
            showToast(getString(R.string.copied))
        }
    }

    // ---------------------------------------------------------------------
    // Ticket helpers
    // ---------------------------------------------------------------------

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

    private fun statusColor(status: String): Int {
        return when (normalizedStatus(status)) {
            "RESOLVED", "CLOSED" -> color(R.color.text_tertiary)
            "IN_PROGRESS" -> color(R.color.status_text_active)
            else -> color(R.color.brand_primary)
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

    private fun color(@ColorRes resId: Int) = ContextCompat.getColor(this, resId)

    private fun showToast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }

    private fun dp(value: Int): Int {
        return (value * resources.displayMetrics.density).toInt()
    }
}
