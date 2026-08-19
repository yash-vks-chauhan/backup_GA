package com.gridee.parking.data.api

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UnauthorizedInterceptorTest {

    @Test
    fun deadSessionOnlyWhenTokenWasRejected() {
        // 401 on a request that carried a token = the token is invalid/expired.
        assertTrue(UnauthorizedInterceptor.isSessionDead(401, hadAuthHeader = true))
    }

    @Test
    fun forbiddenDoesNotEndSession() {
        // 403 = authenticated but not permitted (wrong role / parking lot). Must NOT log out.
        assertFalse(UnauthorizedInterceptor.isSessionDead(403, hadAuthHeader = true))
    }

    @Test
    fun unauthenticated401DoesNotEndSession() {
        // 401 without a token = e.g. a wrong-password login attempt. Must NOT log out.
        assertFalse(UnauthorizedInterceptor.isSessionDead(401, hadAuthHeader = false))
    }

    @Test
    fun successfulResponsesNeverEndSession() {
        assertFalse(UnauthorizedInterceptor.isSessionDead(200, hadAuthHeader = true))
        assertFalse(UnauthorizedInterceptor.isSessionDead(500, hadAuthHeader = true))
    }
}
