package com.paulesthor.casper

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

/**
 * Adds a fake message as if it came from WhatsApp, Instagram... (debug builds only).
 *
 *   adb shell "am broadcast -a com.paulesthor.casper.FAKE -p com.paulesthor.casper
 *       --es app whatsapp --es from Lea --es text 'Tu viens samedi ?' [--es group Coloc] [--ei ago 90]"
 *
 * app: whatsapp, instagram, messenger, sms or appel. ago: how many minutes ago it arrived.
 */
class FakeMessageReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val group = when (intent.getStringExtra("app")?.lowercase()) {
            "instagram", "insta" -> AppGroup.INSTAGRAM
            "messenger" -> AppGroup.MESSENGER
            "sms" -> AppGroup.SMS
            "appel", "call", "calls" -> AppGroup.CALLS
            else -> AppGroup.WHATSAPP
        }
        val from = intent.getStringExtra("from") ?: "Test"
        val conversation = intent.getStringExtra("group") ?: from
        val text = if (group == AppGroup.CALLS) MessageExtractor.MISSED_CALL_TEXT else intent.getStringExtra("text") ?: "Coucou"
        val time = System.currentTimeMillis() - intent.getIntExtra("ago", 0) * 60_000L

        val message = CapturedMessage(group.packages.first(), conversation, from, text, time)
        MessageStore.get(context).insert(listOf(message))
        ReminderScheduler.reschedule(context)
        MessageListenerService.onNewMessages?.invoke()
        Log.i(MessageListenerService.TAG, "fake app=${message.app} conversation=$conversation sender=$from text=$text")
    }
}
