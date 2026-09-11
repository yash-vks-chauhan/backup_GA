package com.gridee.parking.ui.operator

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OperatorTerminalDeliveryTest {

    @Test
    fun `in flight operation cannot be claimed as a terminal result`() {
        val delivery = OperatorTerminalDelivery()
        delivery.register(consumerId = 1L)

        assertFalse(delivery.claim(consumerId = 1L))
        assertFalse(delivery.isClaimed)
    }

    @Test
    fun `completed result remains claimable until explicitly acknowledged elsewhere`() {
        val delivery = OperatorTerminalDelivery()
        delivery.markCompleted()
        delivery.register(consumerId = 1L)

        assertTrue(delivery.claim(consumerId = 1L))
        assertTrue(delivery.isClaimed)
    }

    @Test
    fun `backgrounded consumer releases unacknowledged result for the next screen`() {
        val delivery = OperatorTerminalDelivery()
        delivery.markCompleted()
        delivery.register(consumerId = 1L)
        assertTrue(delivery.claim(consumerId = 1L))

        delivery.release(consumerId = 1L)
        delivery.register(consumerId = 2L)

        assertFalse(delivery.isClaimed)
        assertTrue(delivery.claim(consumerId = 2L))
    }

    @Test
    fun `non owner cannot release or claim another consumers result`() {
        val delivery = OperatorTerminalDelivery()
        delivery.markCompleted()
        delivery.register(consumerId = 1L)

        delivery.release(consumerId = 2L)

        assertFalse(delivery.claim(consumerId = 2L))
        assertTrue(delivery.claim(consumerId = 1L))
    }
}
