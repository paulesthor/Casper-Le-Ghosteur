package com.paulesthor.casper

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.os.Bundle
import android.provider.Settings
import android.text.format.DateUtils
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.ImageButton
import android.widget.ListView
import android.widget.TextView
import androidx.core.app.NotificationManagerCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat

class MainActivity : Activity() {

    private lateinit var store: MessageStore
    private lateinit var subtitle: TextView
    private lateinit var permissionCard: View
    private lateinit var list: ListView
    private lateinit var empty: View
    private val adapter = ConversationAdapter()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        store = MessageStore(this)
        setContentView(R.layout.activity_main)

        subtitle = findViewById(R.id.subtitle)
        permissionCard = findViewById(R.id.permission_card)
        list = findViewById(R.id.list)
        empty = findViewById(R.id.empty)

        findViewById<View>(R.id.grant_button).setOnClickListener {
            startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
        }
        list.adapter = adapter
        list.setOnItemClickListener { _, _, position, _ -> showConversation(adapter.getItem(position)) }

        // Android 15+ draws apps edge-to-edge: keep content out of the status and navigation bars.
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.root)) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout())
            view.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            WindowInsetsCompat.CONSUMED
        }
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
        permissionCard.visibility = if (granted) View.GONE else View.VISIBLE

        val conversations = store.pendingConversations()
        adapter.conversations = conversations
        adapter.notifyDataSetChanged()
        empty.visibility = if (conversations.isEmpty()) View.VISIBLE else View.GONE

        subtitle.text = when (conversations.size) {
            0 -> if (granted) "Tout est à jour" else "En attente d'autorisation"
            1 -> "1 personne attend ta réponse"
            else -> "${conversations.size} personnes attendent ta réponse"
        }
    }

    private fun markHandled(c: Conversation) {
        store.markHandled(c.app, c.conversation)
        refresh()
    }

    private fun showConversation(c: Conversation) {
        val body = store.pendingMessages(c.app, c.conversation).joinToString("\n\n") {
            "${it.sender} · ${timeAgo(it.timestamp)}\n${it.text}"
        }
        AlertDialog.Builder(this)
            .setTitle("${c.conversation} · ${appLabel(c.app)}")
            .setMessage(body)
            .setPositiveButton("Répondu ✓") { _, _ -> markHandled(c) }
            .setNegativeButton("Fermer", null)
            .show()
    }

    private fun timeAgo(timestamp: Long): CharSequence = DateUtils.getRelativeTimeSpanString(
        timestamp, System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS, DateUtils.FORMAT_ABBREV_RELATIVE,
    )

    private fun appLabel(pkg: String): String = try {
        packageManager.getApplicationLabel(packageManager.getApplicationInfo(pkg, 0)).toString()
    } catch (e: PackageManager.NameNotFoundException) {
        pkg
    }

    private fun appColor(pkg: String): Int = when (pkg) {
        "com.whatsapp", "com.whatsapp.w4b" -> 0xFF25D366
        "com.instagram.android" -> 0xFFE1306C
        "com.facebook.orca", "com.facebook.mlite" -> 0xFF0A7CFF
        "com.google.android.apps.messaging", "com.samsung.android.messaging", "com.android.mms" -> 0xFF4C8DF6
        in MessageExtractor.DIALER_APPS -> 0xFF34C759
        else -> 0xFF9AA8FF
    }.toInt()

    /** The longer someone has been waiting, the warmer the color. */
    private fun waitingColor(since: Long): Int {
        val hours = (System.currentTimeMillis() - since) / DateUtils.HOUR_IN_MILLIS
        return getColor(
            when {
                hours >= 24 -> R.color.urgent
                hours >= 3 -> R.color.warning
                else -> R.color.text_secondary
            }
        )
    }

    private inner class ConversationAdapter : BaseAdapter() {
        var conversations: List<Conversation> = emptyList()

        override fun getCount() = conversations.size
        override fun getItem(position: Int) = conversations[position]
        override fun getItemId(position: Int) = position.toLong()

        override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
            val view = convertView ?: layoutInflater.inflate(R.layout.item_conversation, parent, false)
            val c = conversations[position]
            val color = appColor(c.app)

            view.findViewById<TextView>(R.id.avatar).text =
                c.conversation.firstOrNull { it.isLetterOrDigit() }?.uppercase() ?: "?"
            view.findViewById<View>(R.id.app_dot).backgroundTintList = ColorStateList.valueOf(color)
            view.findViewById<TextView>(R.id.name).text = c.conversation
            view.findViewById<TextView>(R.id.time).apply {
                text = timeAgo(c.firstTimestamp)
                setTextColor(waitingColor(c.firstTimestamp))
            }
            view.findViewById<TextView>(R.id.app).apply {
                text = appLabel(c.app)
                setTextColor(color)
            }
            view.findViewById<TextView>(R.id.preview).text = c.lastText
            view.findViewById<TextView>(R.id.count).text = c.count.toString()
            view.findViewById<ImageButton>(R.id.done).setOnClickListener { markHandled(c) }
            return view
        }
    }
}
