package com.paulesthor.casper

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat

/**
 * Sends "X is waiting for your answer" notifications. A single alarm is kept for the
 * next reminder due; when it rings, every due reminder is sent and the alarm is moved on.
 */
object ReminderScheduler {

    private const val CHANNEL = "reminders"
    const val SNOOZE_MINUTES = 30

    fun reschedule(context: Context) {
        val now = System.currentTimeMillis()
        val next = dueReminders(context).minOfOrNull { it.second }
        val alarms = context.getSystemService(AlarmManager::class.java)
        val pending = alarmIntent(context)
        if (next == null) {
            alarms.cancel(pending)
        } else {
            // Inexact on purpose: no special permission needed, at most a few minutes late.
            alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, maxOf(next, now + 1_000), pending)
        }
    }

    /** Sends every reminder that is due, then schedules the next one. */
    fun fireDue(context: Context) {
        val store = MessageStore.get(context)
        val now = System.currentTimeMillis()
        for ((conversation, due) in dueReminders(context)) {
            if (due > now) continue
            notify(context, conversation)
            store.saveReminderState(conversation.app, conversation.conversation, ReminderState(lastRemindedAt = now))
        }
        reschedule(context)
    }

    fun snooze(context: Context, app: String, conversation: String) {
        val store = MessageStore.get(context)
        val state = store.reminderState(app, conversation)
        store.saveReminderState(
            app, conversation,
            state.copy(snoozedUntil = System.currentTimeMillis() + SNOOZE_MINUTES * 60_000L),
        )
        cancelNotification(context, app, conversation)
        reschedule(context)
    }

    fun markReplied(context: Context, app: String, conversation: String) {
        MessageStore.get(context).markHandled(app, conversation)
        cancelNotification(context, app, conversation)
        reschedule(context)
    }

    fun cancelNotification(context: Context, app: String, conversation: String) {
        NotificationManagerCompat.from(context).cancel(notificationId(app, conversation))
    }

    private fun dueReminders(context: Context): List<Pair<Conversation, Long>> {
        val store = MessageStore.get(context)
        val rules = store.rules()
        val general = Settings(context).generalRule
        return store.pendingConversations().mapNotNull { c ->
            val rule = Reminders.ruleFor(c.conversation, c.app, rules, general) ?: return@mapNotNull null
            val due = Reminders.nextDue(c.firstTimestamp, rule, store.reminderState(c.app, c.conversation))
                ?: return@mapNotNull null
            c to due
        }
    }

    private fun notify(context: Context, c: Conversation) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            Log.w(MessageListenerService.TAG, "Reminder for ${c.conversation} not shown: notifications not allowed")
            return
        }
        ensureChannel(context)

        val group = AppGroup.orDefault(c.app)
        val waited = Reminders.formatWait(System.currentTimeMillis() - c.firstTimestamp)
        val open = ConversationOpener.intentFor(c.app, c.conversation) ?: PendingIntent.getActivity(
            context, notificationId(c.app, c.conversation),
            Intent(context, MainActivity::class.java)
                .putExtra(MainActivity.EXTRA_OPEN_APP, c.app)
                .putExtra(MainActivity.EXTRA_OPEN_CONVERSATION, c.conversation),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

        val notification = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_ghost)
            .setColor(context.getColor(R.color.accent))
            .setContentTitle("${c.conversation} attend ta réponse depuis $waited")
            .setContentText(if (c.count > 1) "${c.lastText} (+${c.count - 1})" else c.lastText)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(open)
            .addAction(0, "Ouvrir ${group.label}", open)
            .addAction(0, "Dans $SNOOZE_MINUTES min", actionIntent(context, ReminderReceiver.ACTION_SNOOZE, c))
            .addAction(0, "C'est fait", actionIntent(context, ReminderReceiver.ACTION_DONE, c))
            .build()
        NotificationManagerCompat.from(context).notify(notificationId(c.app, c.conversation), notification)
        Log.i(MessageListenerService.TAG, "reminded conversation=${c.conversation} waited=$waited")
    }

    private fun ensureChannel(context: Context) {
        val channel = NotificationChannel(CHANNEL, "Rappels", NotificationManager.IMPORTANCE_HIGH).apply {
            description = "Quand quelqu'un attend ta réponse depuis trop longtemps"
        }
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    private fun actionIntent(context: Context, action: String, c: Conversation): PendingIntent =
        PendingIntent.getBroadcast(
            context, (action + c.app + c.conversation).hashCode(),
            Intent(context, ReminderReceiver::class.java)
                .setAction(action)
                .putExtra(ReminderReceiver.EXTRA_APP, c.app)
                .putExtra(ReminderReceiver.EXTRA_CONVERSATION, c.conversation),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

    private fun alarmIntent(context: Context): PendingIntent =
        PendingIntent.getBroadcast(
            context, 0,
            Intent(context, ReminderReceiver::class.java).setAction(ReminderReceiver.ACTION_CHECK),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

    private fun notificationId(app: String, conversation: String) = "$app|$conversation".hashCode()
}
