package com.gridee.parking.ui.wallet

import androidx.activity.OnBackPressedCallback

/** One commit path for toolbar, system button and edge back; never close on gesture preview. */
internal class WalletAddMoneyBackCallback(
    private val isKeyboardVisible: () -> Boolean,
    private val dismissKeyboard: () -> Unit,
    private val closePage: () -> Unit,
) : OnBackPressedCallback(true) {
    private var closing = false

    override fun handleOnBackPressed() {
        if (closing) return
        // The IME normally consumes system back first. This also covers the toolbar and OEM
        // dispatchers that forward back while the keyboard is still visible.
        if (isKeyboardVisible()) {
            dismissKeyboard()
            return
        }
        closing = true
        closePage()
    }
}
