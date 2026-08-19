package com.gridee.parking.ui.adapters

import com.gridee.parking.data.model.WalletTransaction
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The backend writes a `pending` WALLET_TOP_UP row when a Cashfree order is created, before any
 * money moves. These cases pin down that an abandoned or failed checkout never surfaces as a
 * credit in the user's history.
 */
class WalletTransactionVisibilityTest {

    private fun topUp(status: String?) =
        WalletTransaction(type = "WALLET_TOP_UP", status = status, amount = 100.0)

    @Test
    fun cancelledOrFailedTopUpIsNotListed() {
        assertFalse(WalletTransactionVisibility.isListable(topUp("failed")))
        assertFalse(WalletTransactionVisibility.isListable(topUp("FAILED")))
    }

    @Test
    fun pendingTopUpIsNotListedBecauseNoMoneyHasMoved() {
        assertFalse(WalletTransactionVisibility.isListable(topUp("pending")))
        assertFalse(WalletTransactionVisibility.isListable(topUp(" Pending ")))
    }

    @Test
    fun completedTopUpIsListed() {
        assertTrue(WalletTransactionVisibility.isListable(topUp("completed")))
        assertTrue(WalletTransactionVisibility.isListable(topUp("COMPLETED")))
    }

    @Test
    fun legacyTopUpWithoutStatusIsStillListed() {
        // Rows written before the backend tracked status must not vanish from history.
        assertTrue(WalletTransactionVisibility.isListable(topUp(null)))
        assertTrue(WalletTransactionVisibility.isListable(topUp("")))
    }

    @Test
    fun nonGatewayTypesAreNeverFilteredByStatus() {
        // Booking fees, refunds and ad rewards are settled server-side; their status is
        // bookkeeping, not a payment outcome, so they always show.
        listOf("BOOKING_FEE", "BOOKING_REFUND", "AD_TOP_UP", "WELCOME_BONUS", "PENALTY_FEE").forEach { type ->
            assertTrue(
                "$type should always be listable",
                WalletTransactionVisibility.isListable(
                    WalletTransaction(type = type, status = "pending", amount = 30.0)
                )
            )
        }
    }

    @Test
    fun statusHelpersNormalizeCasingAndWhitespace() {
        assertTrue(WalletTransactionVisibility.isFailed(" FAILED "))
        assertTrue(WalletTransactionVisibility.isPending("Pending"))
        assertTrue(WalletTransactionVisibility.isCompleted("completed"))
        assertFalse(WalletTransactionVisibility.isCompleted(null))
    }
}
