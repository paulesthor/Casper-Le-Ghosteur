package com.paulesthor.casper

/** A reminder setting for one person, or for everyone else when [person] is null. */
data class ReminderRule(
    val id: Long = 0,
    val person: String?,
    val groups: Set<AppGroup> = AppGroup.entries.toSet(),
    val delayMinutes: Int,
    /** 0 = remind only once. */
    val repeatMinutes: Int = 0,
    val enabled: Boolean = true,
)

/** Where a conversation stands with its reminders. */
data class ReminderState(val lastRemindedAt: Long? = null, val snoozedUntil: Long? = null)

object Reminders {

    private const val MINUTE = 60_000L

    /**
     * The rule for a conversation: the person's own rule if they have one (even when
     * paused, which means "no reminders for them"), otherwise the general one.
     */
    fun ruleFor(conversation: String, app: String, rules: List<ReminderRule>, general: ReminderRule): ReminderRule? {
        val group = AppGroup.orDefault(app)
        val own = rules.firstOrNull { it.person.equals(conversation.trim(), ignoreCase = true) && group in it.groups }
        if (own != null) return own.takeIf { it.enabled }
        if (rules.any { it.person.equals(conversation.trim(), ignoreCase = true) }) return null
        return general.takeIf { it.enabled }
    }

    /**
     * When the next reminder is due for a conversation whose oldest unanswered message
     * arrived at [waitingSince], or null if no more reminders should be sent.
     */
    fun nextDue(waitingSince: Long, rule: ReminderRule, state: ReminderState): Long? {
        val last = state.lastRemindedAt
        state.snoozedUntil?.let { if (last == null || it > last) return it }

        val first = waitingSince + rule.delayMinutes * MINUTE
        if (last == null || last < first) return first
        if (rule.repeatMinutes <= 0) return null

        val repeat = rule.repeatMinutes * MINUTE
        val periods = (last - first) / repeat + 1
        return first + periods * repeat
    }

    fun formatDelay(minutes: Int): String = when {
        minutes % (24 * 60) == 0 -> "${minutes / (24 * 60)} j"
        minutes % 60 == 0 -> "${minutes / 60} h"
        minutes > 60 -> "${minutes / 60} h ${minutes % 60}"
        else -> "$minutes min"
    }

    fun formatWait(millis: Long): String {
        val minutes = (millis / MINUTE).coerceAtLeast(1)
        return when {
            minutes < 60 -> "$minutes min"
            minutes < 48 * 60 -> "${minutes / 60} h"
            else -> "${minutes / (24 * 60)} j"
        }
    }

    fun describeRepeat(repeatMinutes: Int): String = when (repeatMinutes) {
        0 -> "une seule fois"
        60 -> "puis toutes les heures"
        else -> "puis toutes les ${formatDelay(repeatMinutes)}"
    }
}
