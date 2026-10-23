package com.paulesthor.casper

import android.app.Notification
import androidx.core.app.NotificationCompat

/** What a notification tells us about one conversation. */
data class Extraction(
    val app: String,
    val conversation: String,
    /** Messages written by the other person (or people). */
    val messages: List<CapturedMessage>,
    /** Time of the user's latest own message shown in the notification (an inline reply), if any. */
    val repliedAt: Long? = null,
    /** Which of the user's Instagram accounts received it, when the notification says. */
    val account: String? = null,
)

/**
 * Turns a posted notification into the messages it carries, or null if it
 * isn't a message (likes, downloads, music, "checking for new messages"...).
 * Only messaging apps and missed calls are kept.
 */
object MessageExtractor {

    const val MISSED_CALL_TEXT = "Appel manqué"

    /** [extraApps] are treated as messaging apps too (used to test with `adb shell cmd notification post`). */
    fun extract(
        app: String,
        notification: Notification,
        postTime: Long,
        extraApps: Set<String> = emptySet(),
    ): Extraction? {
        val flags = notification.flags
        // WhatsApp & co. post a summary ("5 messages from 3 chats") on top of the real ones.
        if (flags and Notification.FLAG_GROUP_SUMMARY != 0) return null
        if (flags and Notification.FLAG_ONGOING_EVENT != 0) return null

        val group = AppGroup.of(app)
        if (group == AppGroup.CALLS) return missedCall(app, notification, postTime)
        if (group == null && app !in extraApps) return null

        val style = NotificationCompat.MessagingStyle.extractMessagingStyleFromNotification(notification)
        if (style != null) return fromMessagingStyle(app, notification, style, postTime)

        if (notification.category != Notification.CATEGORY_MESSAGE) return null

        val extras = notification.extras
        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString()?.trim()
        val text = (extras.getCharSequence(Notification.EXTRA_BIG_TEXT)
            ?: extras.getCharSequence(Notification.EXTRA_TEXT))?.toString()?.trim()
        if (title.isNullOrEmpty() || text.isNullOrEmpty()) return null

        // The title is the sender here, so only the sub-text can name the account.
        val account = accountOf(app, notification, candidates = emptyList(), notAccounts = setOf(title))
        val message = CapturedMessage(app, title, title, text, timeOf(notification, postTime), account)
        return Extraction(app, title, listOf(message), account = account)
    }

    private fun missedCall(app: String, notification: Notification, postTime: Long): Extraction? {
        // Incoming/ongoing calls use other categories; only the missed-call notification counts.
        val missed = notification.category == "missed_call" ||
            notification.channelId?.contains("missed", ignoreCase = true) == true
        if (!missed) return null

        val extras = notification.extras
        // Dialers put "Missed call" in the title and the caller in the text.
        val caller = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString()?.trim()
            ?.takeIf { it.isNotEmpty() }
            ?: extras.getCharSequence(Notification.EXTRA_TITLE)?.toString()?.trim()
            ?: return null
        val time = timeOf(notification, postTime)
        return Extraction(app, caller, listOf(CapturedMessage(app, caller, caller, MISSED_CALL_TEXT, time)))
    }

    private fun fromMessagingStyle(
        app: String,
        notification: Notification,
        style: NotificationCompat.MessagingStyle,
        postTime: Long,
    ): Extraction? {
        val me = style.user.name?.toString()
        // Only a group chat is named by its title. In a one-to-one chat the title can be
        // anything: Instagram puts the user's own username there, not the sender's.
        val groupTitle = style.conversationTitle?.toString()?.trim()
            ?.takeIf { it.isNotEmpty() && style.isGroupConversation }
        val title = notification.extras.getCharSequence(Notification.EXTRA_TITLE)?.toString()?.trim()
            ?.takeIf { it.isNotEmpty() && it != me }

        var repliedAt: Long? = null
        val messages = mutableListOf<Pair<String, NotificationCompat.MessagingStyle.Message>>()
        for (message in style.messages) {
            val sender = message.person?.name?.toString()
            val time = if (message.timestamp > 0) message.timestamp else postTime
            // No sender means the message was written by the user (e.g. an inline reply).
            if (sender.isNullOrBlank() || sender == me) {
                repliedAt = maxOf(repliedAt ?: 0, time)
            } else if (!message.text?.toString()?.trim().isNullOrEmpty()) {
                messages += sender to message
            }
        }

        val conversation = groupTitle ?: messages.firstOrNull()?.first ?: title ?: return null
        val account = accountOf(
            app, notification,
            candidates = listOf(style.conversationTitle?.takeUnless { style.isGroupConversation }, me),
            notAccounts = messages.map { it.first }.toSet() + conversation,
        )
        return Extraction(
            app = app,
            conversation = conversation,
            messages = messages.map { (sender, message) ->
                CapturedMessage(
                    app = app,
                    conversation = conversation,
                    sender = sender,
                    text = message.text.toString().trim(),
                    timestamp = if (message.timestamp > 0) message.timestamp else postTime,
                    account = account,
                )
            },
            repliedAt = repliedAt,
            account = account,
        )
    }

    private val NOT_A_NAME = setOf("vous", "you", "moi", "me")

    /**
     * The receiving account, for Instagram only: it shows the user's own username in the
     * notification (sub-text, title, or as "me" in the conversation), never the sender's.
     */
    private fun accountOf(
        app: String,
        notification: Notification,
        candidates: List<CharSequence?>,
        notAccounts: Set<String>,
    ): String? {
        if (AppGroup.of(app) != AppGroup.INSTAGRAM) return null
        val extras = notification.extras
        return (listOf(extras.getCharSequence(Notification.EXTRA_SUB_TEXT)) + candidates +
            extras.getCharSequence(Notification.EXTRA_TITLE))
            .mapNotNull { it?.toString()?.trim()?.removePrefix("@")?.takeIf { name -> name.isNotEmpty() } }
            .firstOrNull { it !in notAccounts && it.lowercase() !in NOT_A_NAME }
    }

    private fun timeOf(notification: Notification, postTime: Long) =
        if (notification.`when` > 0) notification.`when` else postTime
}
