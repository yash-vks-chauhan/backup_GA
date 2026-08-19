package com.gridee.parking.data.model

import com.google.gson.Gson
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Locks the wire contract with `PaymentController` and the rule that decides whether a top-up
 * counts as paid — the app must never treat an unconfirmed order as credited.
 */
class PaymentModelsTest {

    private val gson = Gson()

    @Test
    fun initiateRequestSerializesTenantContext() {
        val json = gson.toJson(
            PaymentInitiateRequest(
                userId = "user_1",
                amount = 250.0,
                parkingLotId = "lot_9",
                organizationId = "org_3",
                locationId = "loc_5"
            )
        )

        assertTrue(json.contains("\"userId\":\"user_1\""))
        assertTrue(json.contains("\"amount\":250.0"))
        assertTrue(json.contains("\"parkingLotId\":\"lot_9\""))
        assertTrue(json.contains("\"organizationId\":\"org_3\""))
        assertTrue(json.contains("\"locationId\":\"loc_5\""))
    }

    @Test
    fun initiateResponseParsesStrictCashfreeContract() {
        val response = gson.fromJson(
            """
            {
              "orderId": "order_1",
              "paymentSessionId": "session_abc",
              "environment": "PRODUCTION",
              "gateway": "CASHFREE"
            }
            """.trimIndent(),
            PaymentInitiateResponse::class.java
        )

        assertEquals("order_1", response.orderId)
        assertEquals("session_abc", response.paymentSessionId)
        assertEquals(PaymentEnvironment.PRODUCTION, response.paymentEnvironment)
        assertEquals("CASHFREE", response.gateway)
        assertTrue(response.isLaunchable)
    }

    @Test
    fun orderWithoutSessionIdIsNotLaunchable() {
        val noSession = gson.fromJson(
            """{"orderId":"order_1","environment":"PRODUCTION","gateway":"CASHFREE"}""",
            PaymentInitiateResponse::class.java
        )
        assertFalse(noSession.isLaunchable)

        val blankSession = gson.fromJson(
            """{"orderId":"order_1","paymentSessionId":"  ","environment":"PRODUCTION","gateway":"CASHFREE"}""",
            PaymentInitiateResponse::class.java
        )
        assertFalse(blankSession.isLaunchable)
    }

    @Test
    fun unknownEnvironmentOrGatewayCannotOpenCheckout() {
        val wrongEnvironment = gson.fromJson(
            """{"orderId":"o","paymentSessionId":"s","environment":"LIVE","gateway":"CASHFREE"}""",
            PaymentInitiateResponse::class.java
        )
        val wrongGateway = gson.fromJson(
            """{"orderId":"o","paymentSessionId":"s","environment":"PRODUCTION","gateway":"OTHER"}""",
            PaymentInitiateResponse::class.java
        )

        assertFalse(wrongEnvironment.isLaunchable)
        assertFalse(wrongGateway.isLaunchable)
    }

    @Test
    fun onlyBackendConfirmedStatusesCountAsPaid() {
        assertTrue(PaymentStatusResponse(status = "PAID").isPaid)
        assertTrue(PaymentStatusResponse(status = "paid").isPaid)

        assertFalse(PaymentStatusResponse(status = "PENDING").isPaid)
        assertFalse(PaymentStatusResponse(status = "SUCCESS").isPaid)
        assertFalse(PaymentStatusResponse(status = "ACTIVE", walletCredited = true).isPaid)
        assertFalse(PaymentStatusResponse(status = "FAILED").isPaid)
        assertFalse(PaymentStatusResponse(status = "CANCELLED").isPaid)
        assertFalse(PaymentStatusResponse(status = null).isPaid)
    }

    @Test
    fun pendingIsDistinctFromFailedSoTheUserIsNotToldItFailed() {
        assertTrue(PaymentStatusResponse(status = "PENDING").isPending)

        assertFalse(PaymentStatusResponse(status = "ACTIVE").isPending)
        assertFalse(PaymentStatusResponse(status = "FAILED").isPending)
        assertFalse(PaymentStatusResponse(status = "PAID").isPending)
    }

    @Test
    fun statusResponseParsesBackendPayload() {
        val status = gson.fromJson(
            """
            {
              "orderId": "order_1",
              "status": "PAID",
              "message": "Wallet credited",
              "gateway": "CASHFREE",
              "gatewayPaymentId": "cf_pay_1",
              "amount": 250.0,
              "currency": "INR",
              "walletCredited": true,
              "parkingLotName": "Main Lot"
            }
            """.trimIndent(),
            PaymentStatusResponse::class.java
        )

        assertEquals("order_1", status.orderId)
        assertEquals("cf_pay_1", status.gatewayPaymentId)
        assertEquals(250.0, status.amount!!, 0.001)
        assertTrue(status.walletCredited)
        assertTrue(status.isPaid)
    }
}
