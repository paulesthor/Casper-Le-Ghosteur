package com.paulesthor.arepondre

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.provider.Settings
import android.text.format.DateUtils
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.TextView
import androidx.core.app.NotificationManagerCompat

class MainActivity : Activity() {

    private lateinit var store: MessageStore
    private lateinit var status: TextView
    private lateinit var grantButton: Button
    private lateinit var list: ListView
    private var conversations: List<Conversation> = emptyList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        store = MessageStore(this)

        val padding = (16 * resources.displayMetrics.density).toInt()
        status = TextView(this).apply { setPadding(padding, padding, padding, padding / 2) }
        grantButton = Button(this).apply {
            text = "Autoriser l'accès aux notifications"
            setOnClickListener { startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)) }
        }
        list = ListView(this).apply {
            setOnItemClickListener { _, _, position, _ -> showConversation(conversations[position]) }
        }
        setContentView(LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(status)
            addView(grantButton)
            addView(list)
        })
    }

    override fun onResume() {
        super.onResume()
        MessageListenerService.onNewMessages = { runOnUiThread { refresh() } }
        refresh()
    }

    override fun onPause() {
        MessageListenerService.onNewMessages = null
        super.onPause()
    }

    private fun refresh() {
        val granted = packageName in NotificationManagerCompat.getEnabledListenerPackages(this)
        grantButton.visibility = if (granted) Button.GONE else Button.VISIBLE

        conversations = store.pendingConversations()
        status.text = when {
            !granted -> "L'app n'a pas encore accès aux notifications."
            conversations.isEmpty() -> "Rien en attente 🎉"
            else -> "${conversations.size} conversation(s) en attente de réponse"
        }
        list.adapter = ArrayAdapter(
            this,
            android.R.layout.simple_list_item_1,
            conversations.map {
                val ago = DateUtils.getRelativeTimeSpanString(it.lastTimestamp)
                "${it.conversation} · ${appLabel(it.app)} · ${it.count} msg · $ago\n${it.lastText}"
            },
        )
    }

    private fun showConversation(c: Conversation) {
        val body = store.pendingMessages(c.app, c.conversation).joinToString("\n\n") {
            "${it.sender} (${DateUtils.getRelativeTimeSpanString(it.timestamp)}) :\n${it.text}"
        }
        AlertDialog.Builder(this)
            .setTitle("${c.conversation} — ${appLabel(c.app)}")
            .setMessage(body)
            .setPositiveButton("Répondu ✅") { _, _ ->
                store.markHandled(c.app, c.conversation)
                refresh()
            }
            .setNegativeButton("Fermer", null)
            .show()
    }

    private fun appLabel(pkg: String): String = try {
        packageManager.getApplicationLabel(packageManager.getApplicationInfo(pkg, 0)).toString()
    } catch (e: PackageManager.NameNotFoundException) {
        pkg
    }
}
