package com.paulesthor.casper

import android.app.Notification
import androidx.core.app.NotificationCompat

/**
 * Turns a posted notification into the messages it carries, or nothing if it
 * isn't a message (likes, downloads, music, "checking for new messages"...).
 */
object MessageExtractor {

    fun extract(app: String, notification: Notification, postTime: Long): List<CapturedMessage> {
        val flags = notification.flags
        // WhatsApp & co. post a summary ("5 messages from 3 chats") on top of the real ones.
        if (flags and Notification.FLAG_GROUP_SUMMARY != 0) return emptyList()
        if (flags and Notification.FLAG_ONGOING_EVENT != 0) return emptyList()

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
