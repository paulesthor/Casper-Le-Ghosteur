package com.paulesthor.casper

import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log

class MessageListenerService : NotificationListenerService() {

    private val store by lazy { MessageStore(this) }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        if (sbn.packageName == packageName) return
        val messages = try {
            MessageExtractor.extract(sbn.packageName, sbn.notification, sbn.postTime, testApps)
        } catch (e: Exception) {
            Log.w(TAG, "Could not read notification from ${sbn.packageName}", e)
            return
        }
        if (messages.isEmpty()) return

        val inserted = store.insert(messages)
        messages.forEach {
            Log.i(TAG, "captured app=${it.app} conversation=${it.conversation} sender=${it.sender} text=${it.text}")
        }
        if (inserted > 0) onNewMessages?.invoke()
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
