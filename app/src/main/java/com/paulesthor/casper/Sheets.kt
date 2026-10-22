package com.paulesthor.casper

import android.app.Activity
import android.text.Html
import android.text.format.DateFormat
import android.text.format.DateUtils
import android.view.LayoutInflater
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import java.util.Calendar

/** The bottom sheets: a conversation's messages, a person's reminder, and picking a person. */
object Sheets {

    private val DELAYS = listOf(15, 30, 60, 3 * 60, 24 * 60)
    private val REPEATS = listOf(0, 60, 3 * 60)

    fun showConversation(activity: Activity, c: Conversation, onChanged: () -> Unit) {
        val store = MessageStore.get(activity)
        val group = AppGroup.orDefault(c.app)
        val dialog = activity.bottomSheet(R.layout.sheet_conversation)

        dialog.findViewById<View>(R.id.head).apply {
            bindAvatar(c.conversation, group)
            findViewById<TextView>(R.id.name).text = c.conversation
            findViewById<TextView>(R.id.sub).text =
                "${group.label} · attend depuis ${Reminders.formatWait(System.currentTimeMillis() - c.firstTimestamp)}"
        }

        val container = dialog.findViewById<LinearLayout>(R.id.messages)
        var lastDay = ""
        for (m in store.pendingMessages(c.app, c.conversation)) {
            val day = dayLabel(activity, m.timestamp)
            if (day != lastDay) {
                container.addView(TextView(activity, null, 0, R.style.Text_Body).apply {
                    text = day
                    textSize = 11f
                    setPadding(activity.dp(4f), activity.dp(10f), 0, activity.dp(4f))
                })
                lastDay = day
            }
            container.addView(TextView(activity, null, 0, R.style.Text).apply {
                val showSender = m.sender != c.conversation
                text = if (showSender) Html.fromHtml("<b>${Html.escapeHtml(m.sender)}</b><br>${Html.escapeHtml(m.text)}", 0) else m.text
                textSize = 15f
                setLineSpacing(0f, 1.2f)
                background = activity.getDrawable(R.drawable.bg_bubble)
                setPadding(activity.dp(14f), activity.dp(10f), activity.dp(14f), activity.dp(10f))
                layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT)
                    .apply { topMargin = activity.dp(6f); marginEnd = activity.dp(48f) }
            })
        }
        dialog.findViewById<View>(R.id.scroll).limitHeight(380f)

        dialog.findViewById<TextView>(R.id.secondary).apply {
            text = "Ouvrir ${group.label}"
            setOnClickListener {
                dialog.dismiss()
                ConversationOpener.open(activity, c.app, c.conversation)
            }
        }
        dialog.findViewById<TextView>(R.id.primary).apply {
            text = "J'ai répondu"
            setOnClickListener {
                ReminderScheduler.markReplied(activity, c.app, c.conversation)
                dialog.dismiss()
                onChanged()
            }
        }
        dialog.show()
    }

    fun showPeople(activity: Activity, onPicked: (Person) -> Unit) {
        val store = MessageStore.get(activity)
        val followed = store.rules().mapNotNull { it.person?.lowercase() }.toSet()
        val people = store.knownPeople().filter { it.name.lowercase() !in followed }
        val dialog = activity.bottomSheet(R.layout.sheet_people)

        if (people.isEmpty()) {
            dialog.findViewById<TextView>(R.id.hint).text =
                "Personne pour l'instant. Les gens apparaissent ici dès qu'ils t'écrivent."
        }
        val container = dialog.findViewById<LinearLayout>(R.id.people)
        for (person in people) {
            val row = LayoutInflater.from(activity).inflate(R.layout.item_person, container, false)
            row.bindAvatar(person.name, person.groups.singleOrNull())
            row.findViewById<TextView>(R.id.name).text = person.name
            row.findViewById<TextView>(R.id.sub).text = person.groups.joinToString(", ") { it.label }
            row.setOnClickListener {
                dialog.dismiss()
                onPicked(person)
            }
            container.addView(row)
        }
        dialog.findViewById<View>(R.id.scroll).limitHeight(420f)
        dialog.show()
    }

    /**
     * Edits a reminder. [rule] with a null person is the general reminder ("everyone else");
     * [seenOn] lists where the person was seen, for the subtitle.
     */
    fun showRule(activity: Activity, rule: ReminderRule, seenOn: Set<AppGroup>, onSaved: () -> Unit) {
        val store = MessageStore.get(activity)
        val dialog = activity.bottomSheet(R.layout.sheet_rule)
        val general = rule.person == null
        var delay = rule.delayMinutes
        var repeat = rule.repeatMinutes
        val groups = rule.groups.toMutableSet()

        dialog.findViewById<View>(R.id.head).apply {
            if (general) {
                findViewById<TextView>(R.id.avatar).apply {
                    text = ""
                    background = activity.getDrawable(R.drawable.bg_icon_tile)
                    setCompoundDrawablesWithIntrinsicBounds(0, R.drawable.ic_people, 0, 0)
                    setPadding(0, activity.dp(12f), 0, 0)
                }
                bindAvatarBadgeless()
                findViewById<TextView>(R.id.name).text = "Tous les autres"
                findViewById<TextView>(R.id.sub).text = "Ceux qui n'ont pas de rappel à eux"
            } else {
                bindAvatar(rule.person!!, seenOn.singleOrNull())
                findViewById<TextView>(R.id.name).text = rule.person
                findViewById<TextView>(R.id.sub).text =
                    if (seenOn.isEmpty()) "Pas encore vue" else "Vue sur ${seenOn.joinToString(", ") { it.label }}"
            }
        }

        val summary = dialog.findViewById<TextView>(R.id.summary)
        fun refreshSummary() {
            summary.text = Html.fromHtml(describe(activity, rule.person, delay, repeat), 0)
        }

        chips(dialog.findViewById(R.id.delays), DELAYS, { delay }, Reminders::formatDelay) {
            delay = it
            refreshSummary()
        }
        val repeatLabel = { minutes: Int ->
            when (minutes) {
                0 -> "Une seule fois"
                60 -> "Toutes les heures"
                else -> "Toutes les ${Reminders.formatDelay(minutes)}"
            }
        }
        chips(dialog.findViewById(R.id.repeats), REPEATS, { repeat }, repeatLabel) {
            repeat = it
            refreshSummary()
        }

        val apps = dialog.findViewById<FlowLayout>(R.id.apps)
        if (general) {
            apps.visibility = View.GONE
            dialog.findViewById<View>(R.id.apps_label).visibility = View.GONE
        } else {
            for (group in AppGroup.entries) {
                apps.addView(TextView(activity, null, 0, R.style.Text_Chip).apply {
                    text = group.label
                    isSelected = group in groups
                    setOnClickListener {
                        if (group in groups && groups.size > 1) groups -= group else groups += group
                        isSelected = group in groups
                    }
                })
            }
        }
        refreshSummary()

        dialog.findViewById<TextView>(R.id.secondary).apply {
            text = if (rule.id != 0L) "Supprimer" else "Annuler"
            setOnClickListener {
                if (rule.id != 0L) store.deleteRule(rule.id)
                dialog.dismiss()
                ReminderScheduler.reschedule(activity)
                onSaved()
            }
        }
        dialog.findViewById<TextView>(R.id.primary).setOnClickListener {
            val updated = rule.copy(delayMinutes = delay, repeatMinutes = repeat, groups = groups.toSet())
            if (general) Settings(activity).generalRule = updated else store.saveRule(updated)
            dialog.dismiss()
            ReminderScheduler.reschedule(activity)
            onSaved()
        }
        dialog.show()
    }

    private fun View.bindAvatarBadgeless() {
        findViewById<View>(R.id.app_badge).visibility = View.GONE
    }

    private fun chips(
        container: FlowLayout,
        values: List<Int>,
        selected: () -> Int,
        label: (Int) -> String,
        onPick: (Int) -> Unit,
    ) {
        val views = values.map { value ->
            TextView(container.context, null, 0, R.style.Text_Chip).apply {
                text = label(value)
                container.addView(this)
            }
        }
        fun refresh() = views.forEachIndexed { i, v -> v.isSelected = values[i] == selected() }
        views.forEachIndexed { i, v ->
            v.setOnClickListener {
                onPick(values[i])
                refresh()
            }
        }
        refresh()
    }

    /** "If Maman writes to you at 14:00 and you haven't answered, Casper reminds you at 15:00, then at 16:00…" */
    private fun describe(activity: Activity, person: String?, delay: Int, repeat: Int): String {
        val who = if (person == null) "quelqu'un" else "<b>${Html.escapeHtml(person)}</b>"
        val start = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 14)
            set(Calendar.MINUTE, 0)
        }.timeInMillis
        fun at(time: Long): String {
            val clock = DateFormat.getTimeFormat(activity).format(time)
            return if (time - start >= DateUtils.DAY_IN_MILLIS) "demain à $clock" else "à $clock"
        }
        val first = start + delay * 60_000L
        val then = if (repeat > 0) ", puis ${at(first + repeat * 60_000L)}…" else "."
        return "Si $who t'écrit à ${DateFormat.getTimeFormat(activity).format(start)} et que tu n'as pas répondu, " +
            "Casper te relance <b>${at(first)}</b>$then"
    }

    private fun dayLabel(activity: Activity, time: Long): String {
        val clock = DateFormat.getTimeFormat(activity).format(time)
        return when {
            DateUtils.isToday(time) -> "Aujourd'hui, $clock"
            DateUtils.isToday(time + DateUtils.DAY_IN_MILLIS) -> "Hier, $clock"
            else -> DateUtils.formatDateTime(activity, time, DateUtils.FORMAT_SHOW_DATE or DateUtils.FORMAT_SHOW_TIME)
        }
    }
}
