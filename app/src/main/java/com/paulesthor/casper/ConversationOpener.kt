package com.paulesthor.casper

import android.app.ActivityOptions
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.util.Log
import java.util.concurrent.ConcurrentHashMap

/**
 * Opens a conversation in its own app. Each notification carries the intent its app
 * uses to open that exact chat; Casper keeps it while it runs. If it's gone (Casper
 * was restarted, or the app withdrew it), the app's home screen opens instead.
 */
object ConversationOpener {

    private val intents = ConcurrentHashMap<String, PendingIntent>()

    private fun key(app: String, conversation: String) = "$app|$conversation"

    fun remember(app: String, conversation: String, intent: PendingIntent?) {
        if (intent != null) intents[key(app, conversation)] = intent
    }

    fun intentFor(app: String, conversation: String): PendingIntent? = intents[key(app, conversation)]

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
