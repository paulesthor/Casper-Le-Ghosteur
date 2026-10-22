package com.paulesthor.casper

import android.app.ActivityOptions
import android.app.Notification
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.RemoteInput
import java.util.concurrent.ConcurrentHashMap

/**
 * Opens a conversation in its own app, or answers it without opening the app.
 * Each notification carries the intent its app uses to open that exact chat, and
 * usually a "Reply" button; Casper keeps both while it runs. If they're gone (Casper
 * was restarted, or the app withdrew them), the app's home screen opens instead.
 */
object ConversationOpener {

    private val intents = ConcurrentHashMap<String, PendingIntent>()
    private val replies = ConcurrentHashMap<String, NotificationCompat.Action>()

    private fun key(app: String, conversation: String) = "$app|$conversation"

    fun remember(app: String, conversation: String, intent: PendingIntent?) {
        if (intent != null) intents[key(app, conversation)] = intent
    }

    fun intentFor(app: String, conversation: String): PendingIntent? = intents[key(app, conversation)]

    /** Keeps the notification's "Reply" button (the one with a text field), if it has one. */
    fun rememberReply(app: String, conversation: String, notification: Notification) {
        val actions = (0 until NotificationCompat.getActionCount(notification)).mapNotNull {
            NotificationCompat.getAction(notification, it)
        } + NotificationCompat.WearableExtender(notification).actions
        val withText = actions.filter { action -> action.remoteInputs?.any { it.allowFreeFormInput } == true }
        val reply = withText.firstOrNull { it.semanticAction == NotificationCompat.Action.SEMANTIC_ACTION_REPLY }
            ?: withText.firstOrNull()
        if (reply != null) replies[key(app, conversation)] = reply
    }

    fun canReply(app: String, conversation: String) = replies.containsKey(key(app, conversation))

    /** Sends [text] through the app's own reply button. Returns false if the app no longer accepts it. */
    fun reply(context: Context, app: String, conversation: String, text: String): Boolean {
        val action = replies[key(app, conversation)] ?: return false
        val inputs = action.remoteInputs ?: return false
        val results = Bundle().apply { inputs.forEach { putCharSequence(it.resultKey, text) } }
        val intent = Intent().addFlags(Intent.FLAG_RECEIVER_FOREGROUND)
        RemoteInput.addResultsToIntent(inputs, intent, results)
        return try {
            action.actionIntent?.send(context, 0, intent) ?: return false
            true
        } catch (e: PendingIntent.CanceledException) {
            replies.remove(key(app, conversation))
            Log.i(MessageListenerService.TAG, "Reply button expired for $app")
            false
        }
    }

    /** Returns false if nothing could be opened (the app is not installed). */
    fun open(context: Context, app: String, conversation: String): Boolean {
        intents[key(app, conversation)]?.let { intent ->
            try {
                intent.send(context, 0, null, null, null, null, launchOptions())
                return true
            } catch (e: PendingIntent.CanceledException) {
                intents.remove(key(app, conversation))
                Log.i(MessageListenerService.TAG, "Conversation link expired for $app, opening the app instead")
            }
        }
        val launch = context.packageManager.getLaunchIntentForPackage(app) ?: return false
        context.startActivity(launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        return true
    }

    /** Android 14+ only lets another app's intent open a screen if the sender (Casper, on screen) allows it. */
    private fun launchOptions(): Bundle? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            @Suppress("DEPRECATION")
            ActivityOptions.makeBasic()
                .setPendingIntentBackgroundActivityStartMode(ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED)
                .toBundle()
        } else {
            null
        }
}
