package com.gridee.parking.ui.bookings

internal enum class BookingAdStatus {
    ACTIVE,
    COMPLETED,
    CANCELLED,
    OTHER
}

internal enum class BookingAdTarget {
    ACTIVE,
    COMPLETED,
    CANCELLED
}

internal data class BookingAdTransition(
    val bookingId: String,
    val target: BookingAdTarget
)

/** Pure transition policy shared by the booking UI and its unit tests. */
internal object BookingAdTransitionDetector {
    fun detect(
        previous: Map<String, BookingAdStatus>,
        current: Map<String, BookingAdStatus>
    ): List<BookingAdTransition> {
        return current.mapNotNull { (bookingId, status) ->
            val oldStatus = previous[bookingId] ?: return@mapNotNull null
            when {
                status == BookingAdStatus.ACTIVE && oldStatus != BookingAdStatus.ACTIVE -> {
                    BookingAdTransition(bookingId, BookingAdTarget.ACTIVE)
                }
                status == BookingAdStatus.COMPLETED && oldStatus == BookingAdStatus.ACTIVE -> {
                    BookingAdTransition(bookingId, BookingAdTarget.COMPLETED)
                }
                status == BookingAdStatus.CANCELLED && oldStatus != BookingAdStatus.CANCELLED -> {
                    BookingAdTransition(bookingId, BookingAdTarget.CANCELLED)
                }
                else -> null
            }
        }
    }
}
