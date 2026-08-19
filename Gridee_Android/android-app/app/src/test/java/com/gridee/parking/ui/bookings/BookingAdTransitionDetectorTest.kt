package com.gridee.parking.ui.bookings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BookingAdTransitionDetectorTest {
    @Test
    fun `booked to active emits check-in transition`() {
        val transitions = BookingAdTransitionDetector.detect(
            previous = mapOf("booking-1" to BookingAdStatus.OTHER),
            current = mapOf("booking-1" to BookingAdStatus.ACTIVE)
        )

        assertEquals(
            listOf(BookingAdTransition("booking-1", BookingAdTarget.ACTIVE)),
            transitions
        )
    }

    @Test
    fun `active to completed emits checkout transition`() {
        val transitions = BookingAdTransitionDetector.detect(
            previous = mapOf("booking-1" to BookingAdStatus.ACTIVE),
            current = mapOf("booking-1" to BookingAdStatus.COMPLETED)
        )

        assertEquals(
            listOf(BookingAdTransition("booking-1", BookingAdTarget.COMPLETED)),
            transitions
        )
    }

    @Test
    fun `unchanged statuses do not replay transitions`() {
        assertTrue(
            BookingAdTransitionDetector.detect(
                previous = mapOf("booking-1" to BookingAdStatus.ACTIVE),
                current = mapOf("booking-1" to BookingAdStatus.ACTIVE)
            ).isEmpty()
        )
    }

    @Test
    fun `newly discovered booking does not invent a transition`() {
        assertTrue(
            BookingAdTransitionDetector.detect(
                previous = emptyMap(),
                current = mapOf("booking-1" to BookingAdStatus.ACTIVE)
            ).isEmpty()
        )
    }

    @Test
    fun `active to cancelled is cancellation not checkout`() {
        val transitions = BookingAdTransitionDetector.detect(
            previous = mapOf("booking-1" to BookingAdStatus.ACTIVE),
            current = mapOf("booking-1" to BookingAdStatus.CANCELLED)
        )

        assertEquals(
            listOf(BookingAdTransition("booking-1", BookingAdTarget.CANCELLED)),
            transitions
        )
    }
}
