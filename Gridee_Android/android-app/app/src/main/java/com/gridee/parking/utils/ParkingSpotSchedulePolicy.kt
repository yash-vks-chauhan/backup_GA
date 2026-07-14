package com.gridee.parking.utils

import com.gridee.parking.data.model.ParkingSpot
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone

object ParkingSpotSchedulePolicy {

    private val QUICK_SPOT_PATTERN = Regex("""(^|[-_\s])quick($|[-_\s])""", RegexOption.IGNORE_CASE)
    private val IST_TIME_ZONE: TimeZone = TimeZone.getTimeZone("Asia/Kolkata")

    // Testing override. Keep false for default time-based behavior.
    private const val FORCE_EVENING_ONLY_FOR_TESTING = false

    private const val MORNING_STANDARD_OPEN_HOUR = 18 // 6:30 PM one day before
    private const val MORNING_STANDARD_OPEN_MINUTE = 30
    private const val MORNING_SESSION_START_HOUR = 7  // 7:30 AM
    private const val MORNING_SESSION_START_MINUTE = 30
    private const val MORNING_CLOSE_HOUR = 12         // 12:30 PM
    private const val MORNING_CLOSE_MINUTE = 30
    private const val QUICK_BOOKING_OPEN_HOUR = 8     // 8:00 AM
    private const val QUICK_BOOKING_CLOSE_HOUR = 11   // 11:30 AM
    private const val QUICK_BOOKING_CLOSE_MINUTE = 30
    private const val AFTERNOON_VISIBLE_FROM_HOUR = 8 // 8:00 AM same day
    private const val AFTERNOON_VISIBLE_FROM_MINUTE = 0
    private const val AFTERNOON_BOOKING_OPEN_HOUR = 8 // 8:00 AM same day
    private const val AFTERNOON_BOOKING_OPEN_MINUTE = 0
    private const val AFTERNOON_SESSION_START_HOUR = 12 // 12:30 PM
    private const val AFTERNOON_SESSION_START_MINUTE = 30
    private const val AFTERNOON_BOOKING_CLOSE_HOUR = 17
    private const val AFTERNOON_BOOKING_CLOSE_MINUTE = 30

    data class HomeFilterAvailability(
        val morningEnabled: Boolean,
        val standardEnabled: Boolean,
        val quickEnabled: Boolean,
        val afternoonEnabled: Boolean
    )

    enum class SlotSession {
        MORNING,
        EVENING,
        UNKNOWN
    }

    fun filterVisibleSpots(
        spots: List<ParkingSpot>,
        now: Calendar = currentTime()
    ): List<ParkingSpot> {
        return spots.filter { isVisibleNow(it, now) }
    }

    fun filterUnsegmentedSpots(spots: List<ParkingSpot>): List<ParkingSpot> {
        return spots.filter { classifySlotSession(it) == SlotSession.UNKNOWN }
    }

    fun filterQuickBookSpots(spots: List<ParkingSpot>): List<ParkingSpot> {
        return spots.filter(::isQuickBookSpot)
    }

    fun isQuickBookSpot(spot: ParkingSpot): Boolean {
        val tokens = listOf(
            spot.name,
            spot.zoneName,
            spot.lotName,
            spot.spotCode,
            spot.id
        ).mapNotNull { value ->
            value?.trim()?.takeIf { it.isNotEmpty() }
        }

        return tokens.any { token -> QUICK_SPOT_PATTERN.containsMatchIn(token) }
    }

    fun isVisibleNow(
        spot: ParkingSpot,
        now: Calendar = currentTime()
    ): Boolean {
        if (FORCE_EVENING_ONLY_FOR_TESTING) {
            return classifySlotSession(spot) == SlotSession.EVENING
        }

        return when (classifySlotSession(spot)) {
            SlotSession.EVENING -> isWithinAfternoonVisibilityWindow(now)
            SlotSession.MORNING -> {
                if (isQuickBookSpot(spot)) {
                    isWithinMorningQuickBookingWindow(now)
                } else {
                    isWithinMorningStandardBookingWindow(now)
                }
            }
            SlotSession.UNKNOWN -> true
        }
    }

    fun canBookNow(
        spot: ParkingSpot,
        now: Calendar = currentTime()
    ): Boolean {
        return when (classifySlotSession(spot)) {
            SlotSession.EVENING -> isWithinAfternoonBookingWindow(now)
            SlotSession.MORNING -> {
                if (isQuickBookSpot(spot)) {
                    isWithinMorningQuickBookingWindow(now)
                } else {
                    isWithinMorningStandardBookingWindow(now)
                }
            }
            SlotSession.UNKNOWN -> true
        }
    }

    fun bookingRestrictionMessage(
        spot: ParkingSpot,
        now: Calendar = currentTime()
    ): String? {
        return when (classifySlotSession(spot)) {
            SlotSession.EVENING -> {
                when {
                    minutesOfDay(now) < AFTERNOON_OPEN_MINUTES ->
                        "Afternoon slot booking opens at 8:00 AM on the parking day."
                    minutesOfDay(now) >= AFTERNOON_CLOSE_MINUTES ->
                        "Afternoon slot booking closes at 5:30 PM."
                    else -> null
                }
            }

            SlotSession.MORNING -> {
                if (isQuickBookSpot(spot)) {
                    when {
                        minutesOfDay(now) < QUICK_OPEN_MINUTES ->
                            "Quick slot booking opens at 8:00 AM on the parking day."
                        minutesOfDay(now) >= QUICK_CLOSE_MINUTES &&
                            minutesOfDay(now) < MORNING_STANDARD_OPEN_MINUTES ->
                            "Quick slot booking closes at 11:30 AM."
                        else -> null
                    }
                } else {
                    when {
                        minutesOfDay(now) >= MORNING_CLOSE_MINUTES &&
                            minutesOfDay(now) < MORNING_STANDARD_OPEN_MINUTES ->
                            "Morning slot booking opens at 6:30 PM one day before."
                        else -> null
                    }
                }
            }

            SlotSession.UNKNOWN -> null
        }
    }

    fun minimumAllowedStartTime(
        spot: ParkingSpot,
        reference: Calendar = currentTime()
    ): Calendar? {
        val sessionStart = sessionStartTime(spot, reference) ?: return null
        val sessionEnd = sessionEndTime(spot, reference) ?: return null
        val currentMoment = trimToMinute(reference)

        return when {
            currentMoment.before(sessionStart) -> sessionStart
            currentMoment.before(sessionEnd) -> currentMoment
            else -> sessionStart
        }
    }

    fun sessionEndTime(
        spot: ParkingSpot,
        reference: Calendar = currentTime()
    ): Calendar? {
        val sessionDay = resolveSessionDay(spot, reference) ?: return null

        return when (classifySlotSession(spot)) {
            SlotSession.MORNING -> {
                if (isQuickBookSpot(spot)) {
                    atTime(sessionDay, QUICK_BOOKING_CLOSE_HOUR, QUICK_BOOKING_CLOSE_MINUTE)
                } else {
                    atTime(sessionDay, MORNING_CLOSE_HOUR, MORNING_CLOSE_MINUTE)
                }
            }

            SlotSession.EVENING ->
                atTime(sessionDay, AFTERNOON_BOOKING_CLOSE_HOUR, AFTERNOON_BOOKING_CLOSE_MINUTE)

            SlotSession.UNKNOWN -> null
        }
    }

    fun isStartTimeAllowed(
        spot: ParkingSpot,
        startTime: Date
    ): Boolean {
        val startCalendar = Calendar.getInstance().apply { time = startTime }
        val minStart = minimumAllowedStartTime(spot, startCalendar) ?: return true
        return !startCalendar.before(minStart)
    }

    fun startTimeRestrictionMessage(spot: ParkingSpot): String? {
        return when (classifySlotSession(spot)) {
            SlotSession.MORNING -> {
                if (isQuickBookSpot(spot)) {
                    "Quick slot start time must be 8:00 AM or later."
                } else {
                    "Morning slot start time must be 7:30 AM or later."
                }
            }

            SlotSession.EVENING -> "Afternoon slot start time must be 12:30 PM or later."
            SlotSession.UNKNOWN -> null
        }
    }

    fun currentTime(): Calendar = Calendar.getInstance(IST_TIME_ZONE)

    fun homeFilterAvailability(
        now: Calendar = currentTime()
    ): HomeFilterAvailability {
        val morningEnabled = isWithinMorningStandardBookingWindow(now)
        return HomeFilterAvailability(
            morningEnabled = morningEnabled,
            standardEnabled = morningEnabled,
            quickEnabled = isWithinMorningQuickBookingWindow(now),
            afternoonEnabled = isWithinAfternoonVisibilityWindow(now)
        )
    }

    fun classifySlotSession(spot: ParkingSpot): SlotSession {
        val slotName = spot.slotName
            ?.trim()
            ?.lowercase(Locale.ROOT)
            .orEmpty()

        if (slotName.isNotEmpty()) {
            // Backend now uses "afternoon"; keep "evening" as a legacy alias.
            if (slotName.contains("afternoon") || slotName.contains("evening")) return SlotSession.EVENING
            if (slotName.contains("quick")) return SlotSession.MORNING
            if (slotName.contains("morning")) return SlotSession.MORNING
        }

        val fallbackTokens = listOf(
            spot.name,
            spot.zoneName,
            spot.spotCode,
            spot.id
        ).mapNotNull {
            it?.trim()?.lowercase(Locale.ROOT)?.takeIf { text -> text.isNotEmpty() }
        }

        if (fallbackTokens.any { it.contains("afternoon") || it.contains("evening") }) return SlotSession.EVENING
        if (fallbackTokens.any { it.contains("morning") }) return SlotSession.MORNING
        if (isQuickBookSpot(spot)) return SlotSession.MORNING

        return SlotSession.UNKNOWN
    }

    private fun isWithinMorningStandardBookingWindow(now: Calendar): Boolean {
        val minutes = minutesOfDay(now)
        return minutes >= MORNING_STANDARD_OPEN_MINUTES || minutes < MORNING_CLOSE_MINUTES
    }

    private fun isWithinMorningQuickBookingWindow(now: Calendar): Boolean {
        val minutes = minutesOfDay(now)
        return minutes in QUICK_OPEN_MINUTES until QUICK_CLOSE_MINUTES
    }

    private fun isWithinAfternoonVisibilityWindow(now: Calendar): Boolean {
        val minutes = minutesOfDay(now)
        return minutes in AFTERNOON_VISIBLE_FROM_MINUTES until AFTERNOON_CLOSE_MINUTES
    }

    private fun isWithinAfternoonBookingWindow(now: Calendar): Boolean {
        val minutes = minutesOfDay(now)
        return minutes in AFTERNOON_OPEN_MINUTES until AFTERNOON_CLOSE_MINUTES
    }

    private fun minutesOfDay(calendar: Calendar): Int {
        return calendar.get(Calendar.HOUR_OF_DAY) * 60 + calendar.get(Calendar.MINUTE)
    }

    private fun sessionStartTime(
        spot: ParkingSpot,
        reference: Calendar
    ): Calendar? {
        val sessionDay = resolveSessionDay(spot, reference) ?: return null

        return when (classifySlotSession(spot)) {
            SlotSession.MORNING -> {
                if (isQuickBookSpot(spot)) {
                    atTime(sessionDay, QUICK_BOOKING_OPEN_HOUR, 0)
                } else {
                    atTime(
                        sessionDay,
                        MORNING_SESSION_START_HOUR,
                        MORNING_SESSION_START_MINUTE
                    )
                }
            }

            SlotSession.EVENING ->
                atTime(sessionDay, AFTERNOON_SESSION_START_HOUR, AFTERNOON_SESSION_START_MINUTE)

            SlotSession.UNKNOWN -> null
        }
    }

    private fun resolveSessionDay(
        spot: ParkingSpot,
        reference: Calendar
    ): Calendar? {
        val minutes = minutesOfDay(reference)
        val dayOffset = when (classifySlotSession(spot)) {
            SlotSession.MORNING -> {
                if (isQuickBookSpot(spot)) {
                    if (minutes < QUICK_CLOSE_MINUTES) 0 else 1
                } else {
                    if (minutes < MORNING_CLOSE_MINUTES) 0 else 1
                }
            }

            SlotSession.EVENING -> if (minutes < AFTERNOON_CLOSE_MINUTES) 0 else 1
            SlotSession.UNKNOWN -> return null
        }

        return (reference.clone() as Calendar).apply {
            add(Calendar.DAY_OF_MONTH, dayOffset)
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
    }

    private fun atTime(referenceDay: Calendar, hour: Int, minute: Int): Calendar {
        return (referenceDay.clone() as Calendar).apply {
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
    }

    private fun trimToMinute(reference: Calendar): Calendar {
        return (reference.clone() as Calendar).apply {
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
    }

    private const val MORNING_STANDARD_OPEN_MINUTES =
        MORNING_STANDARD_OPEN_HOUR * 60 + MORNING_STANDARD_OPEN_MINUTE
    private const val QUICK_OPEN_MINUTES = QUICK_BOOKING_OPEN_HOUR * 60
    private const val QUICK_CLOSE_MINUTES = QUICK_BOOKING_CLOSE_HOUR * 60 + QUICK_BOOKING_CLOSE_MINUTE
    private const val MORNING_CLOSE_MINUTES = MORNING_CLOSE_HOUR * 60 + MORNING_CLOSE_MINUTE
    private const val AFTERNOON_VISIBLE_FROM_MINUTES =
        AFTERNOON_VISIBLE_FROM_HOUR * 60 + AFTERNOON_VISIBLE_FROM_MINUTE
    private const val AFTERNOON_OPEN_MINUTES =
        AFTERNOON_BOOKING_OPEN_HOUR * 60 + AFTERNOON_BOOKING_OPEN_MINUTE
    private const val AFTERNOON_CLOSE_MINUTES =
        AFTERNOON_BOOKING_CLOSE_HOUR * 60 + AFTERNOON_BOOKING_CLOSE_MINUTE
}
