package com.gridee.parking.utils

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class VehicleNumberValidatorTest {

    @Test
    fun `accepts Tamil Nadu registration without a letter series`() {
        assertTrue(VehicleNumberValidator.isValid("TN 01 1234"))
        assertTrue(VehicleNumberValidator.isValid("TN-01-1234"))
        assertNull(VehicleNumberValidator.getError("TN011234"))
        assertEquals(VehicleNumberType.REGULAR, VehicleNumberValidator.parseType("TN011234"))
    }

    @Test
    fun `normalizes and formats registration without a letter series`() {
        assertEquals("TN011234", VehicleNumberValidator.normalize(" tn 01 1234 "))
        assertEquals("TN 01 1234", VehicleNumberValidator.formatForDisplay("TN011234"))
    }

    @Test
    fun `continues to accept regular registration with a letter series`() {
        assertTrue(VehicleNumberValidator.isValid("TN 01 AB 1234"))
        assertEquals("TN 01 AB 1234", VehicleNumberValidator.formatForDisplay("TN01AB1234"))
    }

    @Test
    fun `rejects incomplete or malformed registrations`() {
        assertFalse(VehicleNumberValidator.isValid("TN0112AB"))
        assertFalse(VehicleNumberValidator.isValid("TN01"))
        assertFalse(VehicleNumberValidator.isValid("011234"))
    }
}
