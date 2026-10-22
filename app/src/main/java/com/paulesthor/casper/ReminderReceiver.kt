package com.paulesthor.casper

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** Handles the reminder alarm, the reminder's buttons, and restarting the alarm after a reboot. */
class ReminderReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val app = intent.getStringExtra(EXTRA_APP)
        val conversation = intent.getStringExtra(EXTRA_CONVERSATION)
        when (intent.action) {
            ACTION_CHECK -> ReminderScheduler.fireDue(context)
            ACTION_SNOOZE -> if (app != null && conversation != null) ReminderScheduler.snooze(context, app, conversation)
            ACTION_DONE -> if (app != null && conversation != null) {
                ReminderScheduler.markReplied(context, app, conversation)
                MessageListenerService.onNewMessages?.invoke()
            }
            Intent.ACTION_BOOT_COMPLETED, Intent.ACTION_MY_PACKAGE_REPLACED -> ReminderScheduler.reschedule(context)
        }
    }

    companion object {
        const val ACTION_CHECK = "com.paulesthor.casper.CHECK_REMINDERS"
        const val ACTION_SNOOZE = "com.paulesthor.casper.SNOOZE"
        const val ACTION_DONE = "com.paulesthor.casper.DONE"
        const val EXTRA_APP = "app"
        const val EXTRA_CONVERSATION = "conversation"
    }
}
