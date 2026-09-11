package com.gridee.parking.utils

import com.gridee.parking.BuildConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class ReleaseAppLogTest {

    @Test
    fun `every log level discards its message in release`() {
        assertFalse("This test must run against the release BuildConfig", BuildConfig.DEBUG)
        var evaluationCount = 0
        val message = {
            evaluationCount++
            "must not be evaluated"
        }

        assertEquals(0, AppLog.d("ReleaseLogTest", message))
        assertEquals(0, AppLog.i("ReleaseLogTest", message))
        assertEquals(0, AppLog.w("ReleaseLogTest", message))
        assertEquals(0, AppLog.e("ReleaseLogTest", message))
        assertEquals(0, evaluationCount)
    }
}
