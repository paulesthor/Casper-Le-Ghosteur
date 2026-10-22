package com.paulesthor.casper

import android.Manifest
import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings as SystemSettings
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.Switch
import android.widget.TextView
import androidx.core.app.NotificationManagerCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

class MainActivity : Activity() {

    private lateinit var store: MessageStore
    private lateinit var settings: Settings
    private lateinit var pageInbox: View
    private lateinit var pageReminders: View
    private lateinit var tabInbox: TextView
    private lateinit var tabReminders: TextView
    private val adapter = ConversationAdapter()
    private var showingReminders = false
    private lateinit var snack: View
    private val hideSnack = Runnable { snack.visibility = View.GONE }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        store = MessageStore.get(this)
        settings = Settings(this)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        setContentView(R.layout.activity_main)

        pageInbox = findViewById(R.id.page_inbox)
        pageReminders = findViewById(R.id.page_reminders)
        tabInbox = findViewById(R.id.tab_inbox)
        tabReminders = findViewById(R.id.tab_reminders)

        // The background runs behind the system bars; only the content is kept clear of them.
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.content)) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout())
            view.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            WindowInsetsCompat.CONSUMED
        }

        findViewById<View>(R.id.tabbar).background = GlassDrawable(this, 30f, sheet = true)
        findViewById<View>(R.id.permission_card).background = GlassDrawable(this, 26f)
        tabInbox.setOnClickListener { showPage(reminders = false) }
        tabReminders.setOnClickListener { showPage(reminders = true) }

        findViewById<View>(R.id.grant_button).setOnClickListener {
            startActivity(Intent(SystemSettings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
        }
        findViewById<RecyclerView>(R.id.list).apply {
            layoutManager = LinearLayoutManager(this@MainActivity)
            adapter = this@MainActivity.adapter
            ItemTouchHelper(SwipeActions(this@MainActivity, ::ignore, ::reply)).attachToRecyclerView(this)
        }
        snack = findViewById(R.id.snack)
        snack.background = GlassDrawable(this, 27f, sheet = true)
        findViewById<View>(R.id.add_person).setOnClickListener {
            Sheets.showPeople(this) { person ->
                Sheets.showRule(
                    this,
                    ReminderRule(person = person.name, delayMinutes = 60),
                    person.groups,
                ) { refresh() }
            }
        }

        // The ghost on the empty screen floats gently.
        ObjectAnimator.ofFloat(findViewById(R.id.empty_ghost), View.TRANSLATION_Y, 0f, -dp(8f).toFloat()).apply {
            duration = 3000
            repeatCount = ValueAnimator.INFINITE
            repeatMode = ValueAnimator.REVERSE
            start()
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1)
        }

        showPage(reminders = intent.getStringExtra(EXTRA_TAB) == TAB_REMINDERS)
        handleOpenRequest(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleOpenRequest(intent)
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

    /** A reminder was tapped but the conversation's own link was gone: open the app instead. */
    private fun handleOpenRequest(intent: Intent) {
        val app = intent.getStringExtra(EXTRA_OPEN_APP) ?: return
        val conversation = intent.getStringExtra(EXTRA_OPEN_CONVERSATION) ?: return
        intent.removeExtra(EXTRA_OPEN_APP)
        ConversationOpener.open(this, app, conversation)
    }

    private fun ignore(position: Int) {
        val c = adapter.conversations[position]
        val undo = ReminderScheduler.ignore(this, c.app, c.conversation)
        refresh()
        showSnack("<b>${android.text.Html.escapeHtml(c.conversation)}</b> ignoré", "Annuler") {
            ReminderScheduler.undo(this, undo)
            refresh()
        }
    }

    private fun reply(position: Int) {
        val c = adapter.conversations[position]
        // The card springs back; it leaves the list once the answer is sent.
        adapter.notifyItemChanged(position)
        Sheets.showReply(this, c) { name ->
            refresh()
            showSnack("Envoyé à <b>${android.text.Html.escapeHtml(name)}</b>", null, null)
        }
    }

    private fun showSnack(html: String, action: String?, onAction: (() -> Unit)?) {
        snack.removeCallbacks(hideSnack)
        findViewById<TextView>(R.id.snack_text).text = android.text.Html.fromHtml(html, 0)
        findViewById<TextView>(R.id.snack_action).apply {
            visibility = if (action == null) View.GONE else View.VISIBLE
            text = action
            setOnClickListener {
                onAction?.invoke()
                hideSnack.run()
            }
        }
        snack.visibility = View.VISIBLE
        snack.postDelayed(hideSnack, 4000)
    }

    private fun openConversation(c: Conversation) {
        if (!ConversationOpener.open(this, c.app, c.conversation)) {
            Sheets.showConversation(this, c) { refresh() }
        }
    }

    private fun showPage(reminders: Boolean) {
        showingReminders = reminders
        pageInbox.visibility = if (reminders) View.GONE else View.VISIBLE
        pageReminders.visibility = if (reminders) View.VISIBLE else View.GONE
        for ((tab, on) in listOf(tabInbox to !reminders, tabReminders to reminders)) {
            tab.background = if (on) getDrawable(R.drawable.bg_tab_on) else null
            val color = getColor(if (on) R.color.text_primary else R.color.text_secondary)
            tab.setTextColor(color)
            tab.compoundDrawablesRelative[0]?.setTint(color)
        }
        refresh()
    }

    private fun refresh() {
        val conversations = store.pendingConversations()
        tabInbox.text = if (showingReminders && conversations.isNotEmpty()) "À répondre · ${conversations.size}" else "À répondre"
        if (showingReminders) refreshReminders() else refreshInbox(conversations)
    }

    private fun refreshInbox(conversations: List<Conversation>) {
        val granted = packageName in NotificationManagerCompat.getEnabledListenerPackages(this)
        findViewById<View>(R.id.permission_card).visibility = if (granted) View.GONE else View.VISIBLE

        adapter.update(conversations)
        val empty = conversations.isEmpty()
        findViewById<View>(R.id.empty).visibility = if (empty) View.VISIBLE else View.GONE
        findViewById<View>(R.id.inbox_section).visibility = if (empty) View.GONE else View.VISIBLE
        findViewById<View>(R.id.header_ghost).visibility = if (empty) View.INVISIBLE else View.VISIBLE

        findViewById<TextView>(R.id.subtitle).text = when (conversations.size) {
            0 -> if (granted) "Tout est à jour" else "En attente d'autorisation"
            1 -> "1 personne attend ta réponse"
            else -> "${conversations.size} personnes attendent ta réponse"
        }
    }

    private fun refreshReminders() {
        val people = store.knownPeople().associateBy { it.name.lowercase() }
        val container = findViewById<LinearLayout>(R.id.rules)
        container.removeAllViews()
        for (rule in store.rules()) {
            val seenOn = people[rule.person!!.lowercase()]?.groups.orEmpty()
            val row = LayoutInflater.from(this).inflate(R.layout.item_rule, container, false)
            row.findViewById<View>(R.id.avatar_frame).bindAvatar(rule.person, seenOn.singleOrNull())
            bindRuleRow(
                row,
                name = rule.person,
                line1 = "Rappel après <b>${Reminders.formatDelay(rule.delayMinutes)}</b> sans réponse · ${Reminders.describeRepeat(rule.repeatMinutes)}",
                line2 = rule.groups.sortedBy { it.ordinal }.joinToString(", ") { it.label },
                checked = rule.enabled,
                onToggle = { store.saveRule(rule.copy(enabled = it)); ReminderScheduler.reschedule(this) },
                onClick = { Sheets.showRule(this, rule, seenOn) { refresh() } },
            )
            container.addView(row)
        }

        val quiet = settings.quietHours
        findViewById<View>(R.id.quiet_rule).let { row ->
            showIcon(row, R.drawable.ic_moon)
            bindRuleRow(
                row,
                name = "Pas de rappels la nuit",
                line1 = "De <b>${QuietHours.formatMinute(quiet.startMinute)}</b> à <b>${QuietHours.formatMinute(quiet.endMinute)}</b> · ${Sheets.describeDays(quiet.days)}",
                line2 = if (quiet.postpone) "Les rappels attendent la fin de la plage" else "Les rappels sont annulés",
                checked = quiet.enabled,
                onToggle = { settings.quietHours = settings.quietHours.copy(enabled = it); ReminderScheduler.reschedule(this) },
                onClick = { Sheets.showQuiet(this) { refresh() } },
            )
        }

        val general = settings.generalRule
        findViewById<View>(R.id.general_rule).let { row ->
            showIcon(row, R.drawable.ic_people)
            bindRuleRow(
                row,
                name = "Rappel général",
                line1 = "Après <b>${Reminders.formatDelay(general.delayMinutes)}</b> sans réponse · ${Reminders.describeRepeat(general.repeatMinutes)}",
                line2 = null,
                checked = general.enabled,
                onToggle = { settings.generalRule = settings.generalRule.copy(enabled = it); ReminderScheduler.reschedule(this) },
                onClick = { Sheets.showRule(this, settings.generalRule, emptySet()) { refresh() } },
            )
        }

        findViewById<View>(R.id.open_setting).let { row ->
            showIcon(row, R.drawable.ic_eye)
            bindRuleRow(
                row,
                name = "Conversation ouverte = répondu",
                line1 = "Si tu ouvres la conversation dans son app, Casper la retire de la liste",
                line2 = null,
                checked = settings.openCountsAsReplied,
                onToggle = { settings.openCountsAsReplied = it },
                onClick = null,
            )
        }
    }

    private fun showIcon(row: View, icon: Int) {
        row.findViewById<View>(R.id.avatar_frame).visibility = View.GONE
        row.findViewById<android.widget.ImageView>(R.id.icon).apply {
            visibility = View.VISIBLE
            setImageResource(icon)
        }
    }

    private fun bindRuleRow(
        row: View,
        name: String,
        line1: String,
        line2: String?,
        checked: Boolean,
        onToggle: (Boolean) -> Unit,
        onClick: (() -> Unit)?,
    ) {
        val card = row.findViewById<View>(R.id.card)
        card.background = GlassDrawable(this, 26f)
        row.findViewById<TextView>(R.id.name).text = name
        row.findViewById<TextView>(R.id.line1).text = android.text.Html.fromHtml(line1, 0)
        row.findViewById<TextView>(R.id.line2).apply {
            text = line2
            visibility = if (line2 == null) View.GONE else View.VISIBLE
        }
        row.findViewById<Switch>(R.id.toggle).apply {
            setOnCheckedChangeListener(null)
            isChecked = checked
            setOnCheckedChangeListener { _, value -> onToggle(value) }
        }
        if (onClick != null) card.setOnClickListener { onClick() } else card.isClickable = false
    }

    private inner class ConversationAdapter : RecyclerView.Adapter<ConversationAdapter.Holder>() {
        var conversations: List<Conversation> = emptyList()
            private set

        inner class Holder(view: View) : RecyclerView.ViewHolder(view)

        fun update(list: List<Conversation>) {
            conversations = list
            @Suppress("NotifyDataSetChanged")
            notifyDataSetChanged()
        }

        override fun getItemCount() = conversations.size

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
            val view = layoutInflater.inflate(R.layout.item_conversation, parent, false)
            view.findViewById<View>(R.id.card).background = GlassDrawable(this@MainActivity, 26f)
            return Holder(view)
        }

        override fun onBindViewHolder(holder: Holder, position: Int) {
            val view = holder.itemView
            val c = conversations[position]
            view.findViewById<View>(R.id.avatar_frame).bindAvatar(c.conversation, AppGroup.orDefault(c.app))
            view.findViewById<TextView>(R.id.name).text = c.conversation
            view.findViewById<TextView>(R.id.wait).showWaiting(c.firstTimestamp)
            view.findViewById<TextView>(R.id.preview).text = c.lastText
            view.findViewById<TextView>(R.id.count).text = c.count.toString()
            view.findViewById<View>(R.id.card).apply {
                setOnClickListener { openConversation(c) }
                setOnLongClickListener {
                    Sheets.showConversation(this@MainActivity, c) { refresh() }
                    true
                }
            }
            view.findViewById<View>(R.id.done).setOnClickListener {
                ReminderScheduler.markReplied(this@MainActivity, c.app, c.conversation)
                refresh()
            }
        }
    }

    companion object {
        const val EXTRA_OPEN_APP = "open_app"
        const val EXTRA_OPEN_CONVERSATION = "open_conversation"
        const val EXTRA_TAB = "tab"
        const val TAB_REMINDERS = "reminders"
    }
}
