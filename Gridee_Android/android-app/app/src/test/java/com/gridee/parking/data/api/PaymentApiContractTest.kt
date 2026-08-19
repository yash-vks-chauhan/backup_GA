package com.gridee.parking.data.api

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path

/** Locks the two mobile payment endpoints agreed with the backend. */
class PaymentApiContractTest {

    @Test
    fun initiateUsesOnlyTheJsonBodyEndpoint() {
        val method = ApiService::class.java.methods.single { it.name == "initiatePayment" }
        val endpoint = requireNotNull(method.getAnnotation(POST::class.java))

        assertEquals("api/payments/initiate", endpoint.value)
        assertTrue(method.parameterAnnotations.flatten().any { it is Body })
    }

    @Test
    fun statusUsesOrderIdPathAndLegacyCallbackIsAbsent() {
        val method = ApiService::class.java.methods.single { it.name == "getPaymentStatus" }
        val endpoint = requireNotNull(method.getAnnotation(GET::class.java))

        assertEquals("api/payments/status/{orderId}", endpoint.value)
        assertTrue(
            method.parameterAnnotations.flatten()
                .filterIsInstance<Path>()
                .any { it.value == "orderId" }
        )
        assertFalse(ApiService::class.java.methods.any { it.name == "paymentCallback" })
    }
}
