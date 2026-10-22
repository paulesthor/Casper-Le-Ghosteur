package com.paulesthor.casper

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RemindersTest {

    private val minute = 60_000L
    private val general = ReminderRule(person = null, delayMinutes = 24 * 60)

    @Test
    fun firstReminderComesAfterTheDelay() {
        val rule = ReminderRule(person = "Maman", delayMinutes = 60)
        assertEquals(1000 + 60 * minute, Reminders.nextDue(1000, rule, ReminderState()))
    }

    @Test
    fun oneShotReminderStopsAfterBeingSent() {
        val rule = ReminderRule(person = "Maman", delayMinutes = 60)
        assertNull(Reminders.nextDue(0, rule, ReminderState(lastRemindedAt = 61 * minute)))
    }

    @Test
    fun repeatingReminderComesBackEveryPeriod() {
        val rule = ReminderRule(person = "Maman", delayMinutes = 60, repeatMinutes = 60)
        assertEquals(120 * minute, Reminders.nextDue(0, rule, ReminderState(lastRemindedAt = 61 * minute)))
        assertEquals(180 * minute, Reminders.nextDue(0, rule, ReminderState(lastRemindedAt = 121 * minute)))
    }

    @Test
    fun snoozeWinsOverTheSchedule() {
        val rule = ReminderRule(person = "Maman", delayMinutes = 60)
        val state = ReminderState(lastRemindedAt = 61 * minute, snoozedUntil = 91 * minute)
        assertEquals(91 * minute, Reminders.nextDue(0, rule, state))
    }

    @Test
    fun personRuleBeatsGeneralRuleAndIsMatchedByNameAndApp() {
        val maman = ReminderRule(person = "Maman", groups = setOf(AppGroup.WHATSAPP, AppGroup.CALLS), delayMinutes = 60)

        assertEquals(maman, Reminders.ruleFor("maman", "com.whatsapp", listOf(maman), general))
        assertEquals(maman, Reminders.ruleFor("Maman", "com.google.android.dialer", listOf(maman), general))
        // Maman on Instagram isn't followed: no reminder rather than the general one.
        assertNull(Reminders.ruleFor("Maman", "com.instagram.android", listOf(maman), general))
        assertEquals(general, Reminders.ruleFor("Léa", "com.whatsapp", listOf(maman), general))
    }

    @Test
    fun pausedRulesSendNothing() {
        val paused = ReminderRule(person = "Maman", delayMinutes = 60, enabled = false)
        assertNull(Reminders.ruleFor("Maman", "com.whatsapp", listOf(paused), general))
        assertNull(Reminders.ruleFor("Léa", "com.whatsapp", emptyList(), general.copy(enabled = false)))
    }

    @Test
    fun durationsReadNaturally() {
        assertEquals("30 min", Reminders.formatDelay(30))
        assertEquals("3 h", Reminders.formatDelay(180))
        assertEquals("1 j", Reminders.formatDelay(24 * 60))
        assertEquals("26 h", Reminders.formatWait(26 * 60 * minute))
        assertEquals("2 j", Reminders.formatWait(50 * 60 * minute))
    }
}
