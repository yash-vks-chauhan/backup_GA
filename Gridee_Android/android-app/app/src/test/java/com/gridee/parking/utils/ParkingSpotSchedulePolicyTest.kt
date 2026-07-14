package com.gridee.parking.utils

import com.gridee.parking.data.model.ParkingSpot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar
import java.util.TimeZone

class ParkingSpotSchedulePolicyTest {

    @Test
    fun `filterQuickBookSpots keeps only quick named spots`() {
        val spots = listOf(
            parkingSpot(id = "1", name = "TP Avenue-Quick"),
            parkingSpot(id = "2", zoneName = "Medical College-Quick"),
            parkingSpot(id = "3", name = "TP Avenue"),
            parkingSpot(id = "4", zoneName = "Medical College")
        )

        val quickSpots = ParkingSpotSchedulePolicy.filterQuickBookSpots(spots)

        assertEquals(listOf("1", "2"), quickSpots.map { it.id })
    }

    @Test
    fun `home filter availability follows booking windows`() {
        assertAvailability(
            hour = 18,
            minute = 29,
            morningEnabled = false,
            standardEnabled = false,
            quickEnabled = false,
            afternoonEnabled = false
        )

        assertAvailability(
            hour = 18,
            minute = 30,
            morningEnabled = true,
            standardEnabled = true,
            quickEnabled = false,
            afternoonEnabled = false
        )

        assertAvailability(
            hour = 7,
            minute = 59,
            morningEnabled = true,
            standardEnabled = true,
            quickEnabled = false,
            afternoonEnabled = false
        )

        assertAvailability(
            hour = 8,
            minute = 0,
            morningEnabled = true,
            standardEnabled = true,
            quickEnabled = true,
            afternoonEnabled = true
        )

        assertAvailability(
            hour = 12,
            minute = 29,
            morningEnabled = true,
            standardEnabled = true,
            quickEnabled = false,
            afternoonEnabled = true
        )

        assertAvailability(
            hour = 12,
            minute = 30,
            morningEnabled = false,
            standardEnabled = false,
            quickEnabled = false,
            afternoonEnabled = true
        )

        assertAvailability(
            hour = 17,
            minute = 30,
            morningEnabled = false,
            standardEnabled = false,
            quickEnabled = false,
            afternoonEnabled = false
        )

        assertAvailability(
            hour = 20,
            minute = 0,
            morningEnabled = true,
            standardEnabled = true,
            quickEnabled = false,
            afternoonEnabled = false
        )
    }

    @Test
    fun `canBookNow respects standard quick and afternoon windows`() {
        val standardMorningSpot = parkingSpot(id = "standard", slotName = "morning", name = "TP Avenue")
        val quickMorningSpot = parkingSpot(id = "quick", name = "TP Avenue Quick")
        val afternoonSpot = parkingSpot(id = "afternoon", slotName = "afternoon", name = "TP Avenue Afternoon")

        assertTrue(ParkingSpotSchedulePolicy.canBookNow(standardMorningSpot, calendarAt(20, 0)))
        assertTrue(ParkingSpotSchedulePolicy.canBookNow(standardMorningSpot, calendarAt(18, 30)))
        assertFalse(ParkingSpotSchedulePolicy.canBookNow(standardMorningSpot, calendarAt(18, 29)))

        assertFalse(ParkingSpotSchedulePolicy.canBookNow(quickMorningSpot, calendarAt(7, 59)))
        assertTrue(ParkingSpotSchedulePolicy.canBookNow(quickMorningSpot, calendarAt(8, 0)))
        assertFalse(ParkingSpotSchedulePolicy.canBookNow(quickMorningSpot, calendarAt(12, 30)))

        assertFalse(ParkingSpotSchedulePolicy.canBookNow(afternoonSpot, calendarAt(7, 59)))
        assertTrue(ParkingSpotSchedulePolicy.canBookNow(afternoonSpot, calendarAt(8, 0)))
        assertTrue(ParkingSpotSchedulePolicy.canBookNow(afternoonSpot, calendarAt(12, 30)))
        assertFalse(ParkingSpotSchedulePolicy.canBookNow(afternoonSpot, calendarAt(17, 30)))
    }

    @Test
    fun `filterVisibleSpots respects quick reveal window and afternoon visibility`() {
        val spots = listOf(
            parkingSpot(id = "standard", slotName = "morning", name = "TP Avenue"),
            parkingSpot(id = "quick", name = "TP Avenue Quick"),
            parkingSpot(id = "afternoon", slotName = "afternoon", name = "TP Avenue Afternoon")
        )

        val beforeEight = ParkingSpotSchedulePolicy.filterVisibleSpots(spots, calendarAt(7, 59))
        assertEquals(listOf("standard"), beforeEight.map { it.id })

        val atEight = ParkingSpotSchedulePolicy.filterVisibleSpots(spots, calendarAt(8, 0))
        assertEquals(listOf("standard", "quick", "afternoon"), atEight.map { it.id })

        val atEveningOpen = ParkingSpotSchedulePolicy.filterVisibleSpots(spots, calendarAt(18, 30))
        assertEquals(listOf("standard"), atEveningOpen.map { it.id })
    }

    @Test
    fun `filterUnsegmentedSpots hides morning afternoon standard and quick spots`() {
        val spots = listOf(
            parkingSpot(id = "standard", slotName = "morning", name = "TP Avenue"),
            parkingSpot(id = "quick", slotName = "morning", name = "TP Avenue Quick"),
            parkingSpot(id = "afternoon", slotName = "afternoon", name = "TP Avenue Afternoon"),
            parkingSpot(id = "step", name = "STEP Parking")
        )

        val unsegmented = ParkingSpotSchedulePolicy.filterUnsegmentedSpots(spots)

        assertEquals(listOf("step"), unsegmented.map { it.id })
    }

    @Test
    fun `minimumAllowedStartTime follows active or upcoming slot session`() {
        val standardMorningSpot = parkingSpot(id = "standard", slotName = "morning", name = "TP Avenue")
        val quickMorningSpot = parkingSpot(id = "quick", name = "TP Avenue Quick")
        val afternoonSpot = parkingSpot(id = "afternoon", slotName = "afternoon", name = "TP Avenue Afternoon")

        val morningPreviousEvening = ParkingSpotSchedulePolicy.minimumAllowedStartTime(
            standardMorningSpot,
            calendarAt(18, 45)
        )
        assertNotNull(morningPreviousEvening)
        assertCalendarTime(morningPreviousEvening!!, dayOfMonth = 11, hour = 7, minute = 30)

        val morningSameDay = ParkingSpotSchedulePolicy.minimumAllowedStartTime(
            standardMorningSpot,
            calendarAt(9, 10)
        )
        assertNotNull(morningSameDay)
        assertCalendarTime(morningSameDay!!, dayOfMonth = 10, hour = 9, minute = 10)

        val quickBeforeOpen = ParkingSpotSchedulePolicy.minimumAllowedStartTime(
            quickMorningSpot,
            calendarAt(7, 45)
        )
        assertNotNull(quickBeforeOpen)
        assertCalendarTime(quickBeforeOpen!!, dayOfMonth = 10, hour = 8, minute = 0)

        val afternoonBeforeSession = ParkingSpotSchedulePolicy.minimumAllowedStartTime(
            afternoonSpot,
            calendarAt(8, 15)
        )
        assertNotNull(afternoonBeforeSession)
        assertCalendarTime(afternoonBeforeSession!!, dayOfMonth = 10, hour = 12, minute = 30)

        val afternoonDuringSession = ParkingSpotSchedulePolicy.minimumAllowedStartTime(
            afternoonSpot,
            calendarAt(14, 5)
        )
        assertNotNull(afternoonDuringSession)
        assertCalendarTime(afternoonDuringSession!!, dayOfMonth = 10, hour = 14, minute = 5)
    }

    private fun parkingSpot(
        id: String,
        name: String? = null,
        zoneName: String? = null,
        slotName: String? = null
    ) = ParkingSpot(
        id = id,
        name = name,
        zoneName = zoneName,
        slotName = slotName,
        status = "available"
    )

    private fun assertAvailability(
        hour: Int,
        minute: Int,
        morningEnabled: Boolean,
        standardEnabled: Boolean,
        quickEnabled: Boolean,
        afternoonEnabled: Boolean
    ) {
        val availability = ParkingSpotSchedulePolicy.homeFilterAvailability(calendarAt(hour, minute))

        assertEquals(morningEnabled, availability.morningEnabled)
        assertEquals(standardEnabled, availability.standardEnabled)
        assertEquals(quickEnabled, availability.quickEnabled)
        assertEquals(afternoonEnabled, availability.afternoonEnabled)
    }

    private fun calendarAt(hour: Int, minute: Int): Calendar {
        return Calendar.getInstance(TimeZone.getTimeZone("Asia/Kolkata")).apply {
            set(2026, Calendar.APRIL, 10, hour, minute, 0)
            set(Calendar.MILLISECOND, 0)
        }
    }

    private fun assertCalendarTime(
        calendar: Calendar,
        dayOfMonth: Int,
        hour: Int,
        minute: Int
    ) {
        assertEquals(dayOfMonth, calendar.get(Calendar.DAY_OF_MONTH))
        assertEquals(hour, calendar.get(Calendar.HOUR_OF_DAY))
        assertEquals(minute, calendar.get(Calendar.MINUTE))
    }
}
