package com.paulesthor.casper

import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log

class MessageListenerService : NotificationListenerService() {

    private val store by lazy { MessageStore.get(this) }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        val extraction = read(sbn) ?: return
        val (app, conversation) = extraction.app to extraction.conversation
        if (extraction.account != null && !store.noteAccount(app, extraction.account)) {
            Log.i(TAG, "skipped app=$app account=${extraction.account} (account not followed)")
            onNewMessages?.invoke()
            return
        }

        val inserted = store.insert(extraction.messages)
        ConversationOpener.remember(app, conversation, sbn.notification.contentIntent)
        ConversationOpener.rememberReply(app, conversation, sbn.notification)
        extraction.messages.forEach {
            Log.i(TAG, "captured app=${it.app} account=${it.account} conversation=${it.conversation} sender=${it.sender} text=${it.text}")
        }

        // The user answered from the notification itself: what came before is answered.
        val replied = extraction.repliedAt?.let { store.markHandled(app, conversation, upTo = it) } ?: 0
        if (replied > 0) {
            Log.i(TAG, "replied app=$app conversation=$conversation (inline reply)")
            if (store.pendingConversation(app, conversation) == null) {
                ReminderScheduler.cancelNotification(this, app, conversation)
            }
        }

        if (inserted > 0 || replied > 0) {
            ReminderScheduler.reschedule(this)
            onNewMessages?.invoke()
        }
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification, rankingMap: RankingMap, reason: Int) {
        // Tapping the notification, or the app clearing it because the chat was opened,
        // means the conversation was seen. Swiping it away doesn't.
        if (reason != REASON_CLICK && reason != REASON_APP_CANCEL && reason != REASON_APP_CANCEL_ALL) return
        if (!Settings(this).openCountsAsReplied) return
        val extraction = read(sbn) ?: return

        val upTo = extraction.messages.maxOfOrNull { it.timestamp } ?: sbn.postTime
        if (store.markHandled(extraction.app, extraction.conversation, upTo) > 0) {
            Log.i(TAG, "replied app=${extraction.app} conversation=${extraction.conversation} (opened, reason=$reason)")
            ReminderScheduler.cancelNotification(this, extraction.app, extraction.conversation)
            ReminderScheduler.reschedule(this)
            onNewMessages?.invoke()
        }
    }

    private fun read(sbn: StatusBarNotification): Extraction? {
        if (sbn.packageName == packageName) return null
        return try {
            MessageExtractor.extract(sbn.packageName, sbn.notification, sbn.postTime, testApps)
        } catch (e: Exception) {
            Log.w(TAG, "Could not read notification from ${sbn.packageName}", e)
            null
        }
    }

    companion object {
        const val TAG = "Casper"

        /** Debug builds also accept fake messages posted with `adb shell cmd notification post`. */
        private val testApps = if (BuildConfig.DEBUG) setOf("com.android.shell") else emptySet()

        /** Set by the visible screen so it can refresh live. */
        @Volatile
        var onNewMessages: (() -> Unit)? = null
    }
}
