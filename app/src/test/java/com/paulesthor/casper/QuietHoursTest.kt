package com.paulesthor.casper

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.util.Calendar
import java.util.TimeZone

class QuietHoursTest {

    private val zone = TimeZone.getTimeZone("Europe/Paris")

    /** Thursday 22 October 2026 at [hour]:[minute], plus [days]. */
    private fun at(hour: Int, minute: Int = 0, days: Int = 0) = Calendar.getInstance(zone).apply {
        clear()
        set(2026, Calendar.OCTOBER, 22, hour, minute)
        add(Calendar.DAY_OF_YEAR, days)
    }.timeInMillis

    private val night = QuietHours(startMinute = 22 * 60, endMinute = 8 * 60)

    @Test
    fun windowAcrossMidnightEndsNextMorning() {
        assertEquals(at(8, days = 1), night.endOf(at(23, 30), zone))
        assertEquals(at(8, days = 1), night.endOf(at(3, days = 1), zone))
        assertEquals(at(8), night.endOf(at(7, 59), zone))
    }

    @Test
    fun outsideTheWindowIsNotQuiet() {
        assertNull(night.endOf(at(8), zone))
        assertNull(night.endOf(at(21, 59), zone))
        assertNull(night.endOf(at(14), zone))
    }

    @Test
    fun daysAreTheDaysTheWindowStarts() {
        // Weekend only: Friday night is not quiet, Saturday night is (into Sunday morning).
        val weekend = night.copy(days = setOf(Calendar.SATURDAY, Calendar.SUNDAY))
        assertNull(weekend.endOf(at(23), zone)) // Thursday
        assertNull(weekend.endOf(at(23, days = 1), zone)) // Friday
        assertEquals(at(8, days = 3), weekend.endOf(at(2, days = 3), zone)) // Sunday 2:00, started Saturday
    }

    @Test
    fun windowWithinTheDayWorks() {
        val lunch = QuietHours(startMinute = 12 * 60, endMinute = 14 * 60)
        assertEquals(at(14), lunch.endOf(at(13), zone))
        assertNull(lunch.endOf(at(15), zone))
    }

    @Test
    fun disabledIsNeverQuiet() {
        assertNull(night.copy(enabled = false).endOf(at(23), zone))
    }
}
