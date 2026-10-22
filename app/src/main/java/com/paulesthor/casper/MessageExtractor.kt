package com.paulesthor.casper

import android.app.Notification
import androidx.core.app.NotificationCompat

/**
 * Turns a posted notification into the messages it carries, or nothing if it
 * isn't a message (likes, downloads, music, "checking for new messages"...).
 * Only messaging apps and missed calls are kept.
 */
object MessageExtractor {

    val MESSAGING_APPS = setOf(
        "com.whatsapp",
        "com.whatsapp.w4b",
        "com.instagram.android",
        "com.facebook.orca",
        "com.facebook.mlite",
        "com.google.android.apps.messaging",
        "com.samsung.android.messaging",
        "com.android.mms",
    )

    val DIALER_APPS = setOf(
        "com.google.android.dialer",
        "com.samsung.android.dialer",
        "com.android.dialer",
        "com.android.server.telecom",
    )

    const val MISSED_CALL_TEXT = "📞 Appel manqué"

    /** [extraApps] are treated as messaging apps too (used to test with `adb shell cmd notification post`). */
    fun extract(
        app: String,
        notification: Notification,
        postTime: Long,
        extraApps: Set<String> = emptySet(),
    ): List<CapturedMessage> {
        val flags = notification.flags
        // WhatsApp & co. post a summary ("5 messages from 3 chats") on top of the real ones.
        if (flags and Notification.FLAG_GROUP_SUMMARY != 0) return emptyList()
        if (flags and Notification.FLAG_ONGOING_EVENT != 0) return emptyList()

        if (app in DIALER_APPS) return missedCall(app, notification, postTime)
        if (app !in MESSAGING_APPS && app !in extraApps) return emptyList()

        val style = NotificationCompat.MessagingStyle.extractMessagingStyleFromNotification(notification)
        if (style != null) return fromMessagingStyle(app, notification, style, postTime)

        if (notification.category != Notification.CATEGORY_MESSAGE) return emptyList()

        val extras = notification.extras
        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString()?.trim()
        val text = (extras.getCharSequence(Notification.EXTRA_BIG_TEXT)
            ?: extras.getCharSequence(Notification.EXTRA_TEXT))?.toString()?.trim()
        if (title.isNullOrEmpty() || text.isNullOrEmpty()) return emptyList()

        val time = if (notification.`when` > 0) notification.`when` else postTime
        return listOf(CapturedMessage(app, title, title, text, time))
    }

    private fun missedCall(app: String, notification: Notification, postTime: Long): List<CapturedMessage> {
        // Incoming/ongoing calls use other categories; only the missed-call notification counts.
        val missed = notification.category == "missed_call" ||
            notification.channelId?.contains("missed", ignoreCase = true) == true
        if (!missed) return emptyList()

        val extras = notification.extras
        // Dialers put "Missed call" in the title and the caller in the text.
        val caller = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString()?.trim()
            ?.takeIf { it.isNotEmpty() }
            ?: extras.getCharSequence(Notification.EXTRA_TITLE)?.toString()?.trim()
            ?: return emptyList()
        val time = if (notification.`when` > 0) notification.`when` else postTime
        return listOf(CapturedMessage(app, caller, caller, MISSED_CALL_TEXT, time))
    }

    private fun fromMessagingStyle(
        app: String,
        notification: Notification,
        style: NotificationCompat.MessagingStyle,
        postTime: Long,
    ): List<CapturedMessage> {
        val me = style.user.name?.toString()
        val conversationTitle = style.conversationTitle?.toString()
            ?: notification.extras.getCharSequence(Notification.EXTRA_TITLE)?.toString()

        return style.messages.mapNotNull { message ->
            val sender = message.person?.name?.toString()
            // No sender means the message was written by the user (e.g. an inline reply).
            if (sender.isNullOrBlank() || sender == me) return@mapNotNull null
            val text = message.text?.toString()?.trim()
            if (text.isNullOrEmpty()) return@mapNotNull null
            CapturedMessage(
                app = app,
                conversation = conversationTitle?.trim()?.takeIf { it.isNotEmpty() } ?: sender,
                sender = sender,
                text = text,
                timestamp = if (message.timestamp > 0) message.timestamp else postTime,
            )
        }
    }
}
