package com.paulesthor.casper

import java.util.Calendar
import java.util.TimeZone

/**
 * A daily window without reminders, e.g. 22:00 → 08:00. [days] are the days the
 * window *starts* on (Calendar.MONDAY..SUNDAY), so Friday 22:00 → Saturday 08:00
 * belongs to Friday.
 */
data class QuietHours(
    val enabled: Boolean = true,
    val startMinute: Int = 22 * 60,
    val endMinute: Int = 8 * 60,
    val days: Set<Int> = ALL_DAYS,
    /** true: reminders wait for the end of the window; false: they are dropped. */
    val postpone: Boolean = true,
) {
    /** When the quiet window containing [time] ends, or null if [time] is not in one. */
    fun endOf(time: Long, zone: TimeZone = TimeZone.getDefault()): Long? {
        if (!enabled) return null
        // The window containing [time] started today or yesterday.
        for (daysBack in 0..1) {
            val start = Calendar.getInstance(zone).apply {
                timeInMillis = time
                add(Calendar.DAY_OF_YEAR, -daysBack)
                set(Calendar.HOUR_OF_DAY, startMinute / 60)
                set(Calendar.MINUTE, startMinute % 60)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }
            if (start.get(Calendar.DAY_OF_WEEK) !in days) continue
            // Wall-clock end (not start + duration), so the night the clocks change still ends at 08:00.
            val end = (start.clone() as Calendar).apply {
                if (endMinute <= startMinute) add(Calendar.DAY_OF_YEAR, 1)
                set(Calendar.HOUR_OF_DAY, endMinute / 60)
                set(Calendar.MINUTE, endMinute % 60)
            }.timeInMillis
            if (time >= start.timeInMillis && time < end) return end
        }
        return null
    }

    companion object {
        val ALL_DAYS = setOf(
            Calendar.MONDAY, Calendar.TUESDAY, Calendar.WEDNESDAY, Calendar.THURSDAY,
            Calendar.FRIDAY, Calendar.SATURDAY, Calendar.SUNDAY,
        )

        fun formatMinute(minute: Int) = "%02d:%02d".format(minute / 60, minute % 60)

        /** Monday first, as in France. */
        val WEEK = listOf(
            Calendar.MONDAY to "L", Calendar.TUESDAY to "M", Calendar.WEDNESDAY to "M", Calendar.THURSDAY to "J",
            Calendar.FRIDAY to "V", Calendar.SATURDAY to "S", Calendar.SUNDAY to "D",
        )
    }
}
