package com.gridee.parking.ui.wallet

import org.junit.Assert.assertEquals
import org.junit.Test

class CashfreeCheckoutFailureClassifierTest {

    @Test
    fun `cashfree installer rejection is identified from its stable error code`() {
        assertEquals(
            CashfreeCheckoutFailureClassifier.Kind.UNTRUSTED_INSTALLER,
            classify(code = "installer_package_not_approved"),
        )
    }

    @Test
    fun `legacy not available trusted-source error is identified from its message`() {
        assertEquals(
            CashfreeCheckoutFailureClassifier.Kind.UNTRUSTED_INSTALLER,
            classify(
                status = "NOT_AVAILABLE",
                message = "Package installer is not a trusted source",
            ),
        )
    }

    @Test
    fun `environment or expired-session rejection is not confused with installer rejection`() {
        assertEquals(
            CashfreeCheckoutFailureClassifier.Kind.INVALID_SESSION,
            classify(code = "payment_session_id_invalid"),
        )
    }

    @Test
    fun `user cancellation remains separate from checkout launch failures`() {
        assertEquals(
            CashfreeCheckoutFailureClassifier.Kind.CANCELLED,
            classify(code = "action_cancelled"),
        )
    }

    @Test
    fun `unknown gateway failure stays generic`() {
        assertEquals(
            CashfreeCheckoutFailureClassifier.Kind.OTHER,
            classify(type = "payment_failed", message = "Bank declined the payment"),
        )
    }

    @Test
    fun `inactive order is separated from an unknown checkout failure`() {
        assertEquals(
            CashfreeCheckoutFailureClassifier.Kind.INACTIVE_ORDER,
            classify(code = "order_expired"),
        )
    }

    @Test
    fun `support code contains normalized stable fields but never the free form message`() {
        val diagnostic = CashfreeCheckoutFailureClassifier.diagnose(
            status = "not available",
            code = "payment form/error",
            type = "payment_failed",
            message = "payment_session_id=secret-session-value",
        )

        assertEquals("PAYMENT_FORM_ERROR/PAYMENT_FAILED", diagnostic.supportCode)
        assertEquals(CashfreeCheckoutFailureClassifier.Kind.OTHER, diagnostic.kind)
    }

    private fun classify(
        status: String? = null,
        code: String? = null,
        type: String? = null,
        message: String? = null,
    ) = CashfreeCheckoutFailureClassifier.classify(status, code, type, message)
}
