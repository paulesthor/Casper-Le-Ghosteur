package com.paulesthor.casper

import android.content.Context

/** Small preferences that don't belong in the database. */
class Settings(context: Context) {

    private val prefs = context.applicationContext.getSharedPreferences("settings", Context.MODE_PRIVATE)

    /** The reminder for everyone without their own rule. */
    var generalRule: ReminderRule
        get() = ReminderRule(
            person = null,
            delayMinutes = prefs.getInt("general_delay", 24 * 60),
            repeatMinutes = prefs.getInt("general_repeat", 0),
            enabled = prefs.getBoolean("general_enabled", true),
        )
        set(rule) {
            prefs.edit()
                .putInt("general_delay", rule.delayMinutes)
                .putInt("general_repeat", rule.repeatMinutes)
                .putBoolean("general_enabled", rule.enabled)
                .apply()
        }

    /** Opening the conversation in its app (which clears its notification) counts as answering. */
    var openCountsAsReplied: Boolean
        get() = prefs.getBoolean("open_counts_as_replied", true)
        set(value) = prefs.edit().putBoolean("open_counts_as_replied", value).apply()

    var quietHours: QuietHours
        get() = QuietHours(
            enabled = prefs.getBoolean("quiet_enabled", true),
            startMinute = prefs.getInt("quiet_start", 22 * 60),
            endMinute = prefs.getInt("quiet_end", 8 * 60),
            days = prefs.getStringSet("quiet_days", null)?.map { it.toInt() }?.toSet() ?: QuietHours.ALL_DAYS,
            postpone = prefs.getBoolean("quiet_postpone", true),
        )
        set(quiet) {
            prefs.edit()
                .putBoolean("quiet_enabled", quiet.enabled)
                .putInt("quiet_start", quiet.startMinute)
                .putInt("quiet_end", quiet.endMinute)
                .putStringSet("quiet_days", quiet.days.map { it.toString() }.toSet())
                .putBoolean("quiet_postpone", quiet.postpone)
                .apply()
        }
}
