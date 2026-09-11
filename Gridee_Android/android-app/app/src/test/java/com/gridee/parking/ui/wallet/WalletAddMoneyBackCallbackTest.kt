package com.gridee.parking.ui.wallet

import androidx.activity.BackEventCompat
import androidx.activity.OnBackPressedDispatcher
import org.junit.Assert.assertEquals
import org.junit.Test

class WalletAddMoneyBackCallbackTest {
    @Test fun `a dispatcher back commits exactly one page close`() {
        var closes = 0
        val dispatcher = OnBackPressedDispatcher()
        dispatcher.addCallback(WalletAddMoneyBackCallback({ false }, { error("No keyboard") }, { closes++ }))
        dispatcher.onBackPressed()
        dispatcher.onBackPressed()
        assertEquals(1, closes)
    }

    @Test fun `first back dismisses keyboard and second back closes the page`() {
        var keyboardVisible = true
        var dismissals = 0
        var closes = 0
        val callback = WalletAddMoneyBackCallback(
            { keyboardVisible },
            { dismissals++; keyboardVisible = false },
            { closes++ },
        )
        callback.handleOnBackPressed()
        assertEquals(1, dismissals)
        assertEquals(0, closes)
        callback.handleOnBackPressed()
        assertEquals(1, closes)
    }

    @Test fun `gesture preview and cancellation do not dismiss or finish anything`() {
        var closes = 0
        val callback = WalletAddMoneyBackCallback({ false }, { error("No keyboard") }, { closes++ })
        callback.handleOnBackStarted(BackEventCompat(0f, 500f, 0f, BackEventCompat.EDGE_LEFT))
        callback.handleOnBackProgressed(BackEventCompat(50f, 500f, 0.4f, BackEventCompat.EDGE_LEFT))
        callback.handleOnBackCancelled()
        assertEquals(0, closes)
        callback.handleOnBackPressed()
        assertEquals(1, closes)
    }

    @Test fun `repeated backs while IME is closing do not accidentally navigate away`() {
        var dismissals = 0
        var closes = 0
        val callback = WalletAddMoneyBackCallback({ true }, { dismissals++ }, { closes++ })
        repeat(3) { callback.handleOnBackPressed() }
        assertEquals(3, dismissals)
        assertEquals(0, closes)
    }
}
