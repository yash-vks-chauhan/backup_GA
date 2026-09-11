package com.gridee.parking.ui.qr

/** Limits camera-frame status rendering while allowing operation results through immediately. */
internal class ScannerUiUpdateGate(
    private val transientUpdateIntervalMs: Long = 250L
) {
    private var hasRendered = false
    private var lastModeKey: Int = Int.MIN_VALUE
    private var lastOperationKey: Int = Int.MIN_VALUE
    private var lastTitle: String? = null
    private var lastSubtitle: String? = null
    private var lastMeta: String? = null
    private var lastShowProgress: Boolean = false
    private var lastRenderedAtMs: Long = 0L

    init {
        require(transientUpdateIntervalMs >= 0L)
    }

    fun shouldRender(
        modeKey: Int,
        operationKey: Int,
        title: String,
        subtitle: String,
        meta: String?,
        showProgress: Boolean,
        highPriority: Boolean,
        nowMs: Long,
    ): Boolean {
        val unchanged = hasRendered &&
            modeKey == lastModeKey &&
            operationKey == lastOperationKey &&
            title == lastTitle &&
            subtitle == lastSubtitle &&
            meta == lastMeta &&
            showProgress == lastShowProgress
        if (unchanged) return false
        if (!highPriority &&
            hasRendered &&
            nowMs >= lastRenderedAtMs &&
            nowMs - lastRenderedAtMs < transientUpdateIntervalMs
        ) {
            return false
        }

        hasRendered = true
        lastModeKey = modeKey
        lastOperationKey = operationKey
        lastTitle = title
        lastSubtitle = subtitle
        lastMeta = meta
        lastShowProgress = showProgress
        lastRenderedAtMs = nowMs
        return true
    }

    fun reset() {
        hasRendered = false
        lastModeKey = Int.MIN_VALUE
        lastOperationKey = Int.MIN_VALUE
        lastTitle = null
        lastSubtitle = null
        lastMeta = null
        lastShowProgress = false
        lastRenderedAtMs = 0L
    }
}
