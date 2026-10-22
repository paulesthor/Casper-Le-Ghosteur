package com.paulesthor.casper

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.view.View
import android.widget.RemoteViews

/** Home screen widgets: the ghost's mood follows how many people are waiting. */
object CasperWidget {

    enum class Mood(val ghost: Int, val background: Int) {
        ASLEEP(R.drawable.ic_ghost_sleep, R.drawable.bg_widget_calm),
        WORRIED(R.drawable.ic_ghost_worried, R.drawable.bg_widget_worried),
        PANICKED(R.drawable.ic_ghost_panic, R.drawable.bg_widget_panic);

        companion object {
            fun of(waiting: Int) = when {
                waiting == 0 -> ASLEEP
                waiting <= 5 -> WORRIED
                else -> PANICKED
            }
        }
    }

    fun label(waiting: Int) = when (waiting) {
        0 -> "Personne ne t'attend"
        1 -> "personne t'attend"
        in 2..5 -> "personnes t'attendent"
        else -> "personnes t'attendent !"
    }

    fun updateAll(context: Context) {
        val manager = AppWidgetManager.getInstance(context)
        for ((provider, layout) in listOf(Small::class.java to R.layout.widget_small, Wide::class.java to R.layout.widget_wide)) {
            val ids = manager.getAppWidgetIds(ComponentName(context, provider))
            if (ids.isNotEmpty()) manager.updateAppWidget(ids, views(context, layout))
        }
    }

    fun views(context: Context, layout: Int): RemoteViews {
        val conversations = MessageStore.get(context).pendingConversations()
        val waiting = conversations.size
        val mood = Mood.of(waiting)
        return RemoteViews(context.packageName, layout).apply {
            setInt(R.id.widget_root, "setBackgroundResource", mood.background)
            setImageViewResource(R.id.widget_ghost, mood.ghost)
            setTextViewText(R.id.widget_count, waiting.toString())
            setTextViewText(R.id.widget_label, label(waiting))
            setViewVisibility(R.id.widget_zz, if (mood == Mood.ASLEEP) View.VISIBLE else View.GONE)
            if (layout == R.layout.widget_wide) {
                val now = System.currentTimeMillis()
                setTextViewText(
                    R.id.widget_names,
                    conversations.take(3).joinToString("\n") { "${it.conversation} · ${Reminders.formatWait(now - it.firstTimestamp)}" },
                )
                setViewVisibility(R.id.widget_names, if (waiting == 0) View.GONE else View.VISIBLE)
            }
            setOnClickPendingIntent(
                R.id.widget_root,
                PendingIntent.getActivity(
                    context, 0, Intent(context, MainActivity::class.java),
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
                ),
            )
        }
    }

    class Small : AppWidgetProvider() {
        override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
            manager.updateAppWidget(ids, views(context, R.layout.widget_small))
        }
    }

    class Wide : AppWidgetProvider() {
        override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
            manager.updateAppWidget(ids, views(context, R.layout.widget_wide))
        }
    }
}
