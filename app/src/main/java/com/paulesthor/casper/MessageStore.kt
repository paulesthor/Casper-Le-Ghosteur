package com.paulesthor.casper

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

data class Conversation(
    val app: String,
    val conversation: String,
    val count: Int,
    val lastText: String,
    val lastTimestamp: Long,
    /** When the oldest unanswered message arrived: how long this person has been waiting. */
    val firstTimestamp: Long,
)

/** Someone Casper has seen, with the kinds of apps they wrote from. */
data class Person(val name: String, val groups: Set<AppGroup>)

class MessageStore(context: Context) :
    SQLiteOpenHelper(context.applicationContext, "messages.db", null, 2) {

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE messages (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                app TEXT NOT NULL,
                conversation TEXT NOT NULL,
                sender TEXT NOT NULL,
                text TEXT NOT NULL,
                ts INTEGER NOT NULL,
                handled INTEGER NOT NULL DEFAULT 0,
                UNIQUE(app, conversation, sender, text, ts)
            )
            """.trimIndent()
        )
        createReminderTables(db)
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if (oldVersion < 2) createReminderTables(db)
    }

    private fun createReminderTables(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE rules (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                person TEXT NOT NULL,
                groups TEXT NOT NULL,
                delay_minutes INTEGER NOT NULL,
                repeat_minutes INTEGER NOT NULL,
                enabled INTEGER NOT NULL
            )
            """.trimIndent()
        )
        db.execSQL(
            """
            CREATE TABLE reminder_state (
                app TEXT NOT NULL,
                conversation TEXT NOT NULL,
                last_reminded_at INTEGER,
                snoozed_until INTEGER,
                PRIMARY KEY (app, conversation)
            )
            """.trimIndent()
        )
    }

    /** Apps re-post the whole conversation on every new message, so duplicates are ignored. */
    fun insert(messages: List<CapturedMessage>): Int {
        var inserted = 0
        writableDatabase.run {
            beginTransaction()
            try {
                for (m in messages) {
                    val values = ContentValues().apply {
                        put("app", m.app)
                        put("conversation", m.conversation)
                        put("sender", m.sender)
                        put("text", m.text)
                        put("ts", m.timestamp)
                    }
                    if (insertWithOnConflict("messages", null, values, SQLiteDatabase.CONFLICT_IGNORE) != -1L) {
                        inserted++
                    }
                }
                setTransactionSuccessful()
            } finally {
                endTransaction()
            }
        }
        return inserted
    }

    fun pendingConversations(): List<Conversation> {
        val sql = """
            SELECT app, conversation, COUNT(*), MAX(ts), MIN(ts),
                   (SELECT text FROM messages m2
                     WHERE m2.app = m.app AND m2.conversation = m.conversation AND m2.handled = 0
                     ORDER BY ts DESC LIMIT 1)
            FROM messages m
            WHERE handled = 0
            GROUP BY app, conversation
            ORDER BY MIN(ts)
        """.trimIndent()
        return readableDatabase.rawQuery(sql, null).use { c ->
            buildList {
                while (c.moveToNext()) {
                    add(Conversation(c.getString(0), c.getString(1), c.getInt(2), c.getString(5), c.getLong(3), c.getLong(4)))
                }
            }
        }
    }

    fun pendingConversation(app: String, conversation: String): Conversation? =
        pendingConversations().firstOrNull { it.app == app && it.conversation == conversation }

    fun pendingMessages(app: String, conversation: String): List<CapturedMessage> =
        readableDatabase.rawQuery(
            "SELECT sender, text, ts FROM messages WHERE app = ? AND conversation = ? AND handled = 0 ORDER BY ts",
            arrayOf(app, conversation),
        ).use { c ->
            buildList {
                while (c.moveToNext()) add(CapturedMessage(app, conversation, c.getString(0), c.getString(1), c.getLong(2)))
            }
        }

    /** Marks messages up to [upTo] as answered; later ones stay pending. */
    fun markHandled(app: String, conversation: String, upTo: Long = Long.MAX_VALUE): Int {
        val changed = writableDatabase.compileStatement(
            "UPDATE messages SET handled = 1 WHERE app = ? AND conversation = ? AND handled = 0 AND ts <= ?"
        ).run {
            bindString(1, app)
            bindString(2, conversation)
            bindLong(3, upTo)
            executeUpdateDelete()
        }
        if (pendingConversation(app, conversation) == null) clearReminderState(app, conversation)
        return changed
    }

    /** Everyone seen so far, most recent first, to pick who gets their own reminder. */
    fun knownPeople(): List<Person> {
        val byName = linkedMapOf<String, MutableSet<AppGroup>>()
        readableDatabase.rawQuery(
            "SELECT conversation, app FROM messages GROUP BY conversation, app ORDER BY MAX(ts) DESC",
            null,
        ).use { c ->
            while (c.moveToNext()) {
                byName.getOrPut(c.getString(0)) { mutableSetOf() } += AppGroup.orDefault(c.getString(1))
            }
        }
        return byName.map { (name, groups) -> Person(name, groups) }
    }

    fun rules(): List<ReminderRule> =
        readableDatabase.rawQuery(
            "SELECT id, person, groups, delay_minutes, repeat_minutes, enabled FROM rules ORDER BY person COLLATE NOCASE",
            null,
        ).use { c ->
            buildList {
                while (c.moveToNext()) {
                    add(
                        ReminderRule(
                            id = c.getLong(0),
                            person = c.getString(1),
                            groups = c.getString(2).split(',').mapNotNull { name ->
                                AppGroup.entries.firstOrNull { it.name == name }
                            }.toSet(),
                            delayMinutes = c.getInt(3),
                            repeatMinutes = c.getInt(4),
                            enabled = c.getInt(5) == 1,
                        )
                    )
                }
            }
        }

    fun saveRule(rule: ReminderRule): Long {
        val values = ContentValues().apply {
            put("person", rule.person)
            put("groups", rule.groups.joinToString(",") { it.name })
            put("delay_minutes", rule.delayMinutes)
            put("repeat_minutes", rule.repeatMinutes)
            put("enabled", if (rule.enabled) 1 else 0)
        }
        return if (rule.id == 0L) {
            writableDatabase.insert("rules", null, values)
        } else {
            writableDatabase.update("rules", values, "id = ?", arrayOf(rule.id.toString()))
            rule.id
        }
    }

    fun deleteRule(id: Long) {
        writableDatabase.delete("rules", "id = ?", arrayOf(id.toString()))
    }

    fun reminderState(app: String, conversation: String): ReminderState =
        readableDatabase.rawQuery(
            "SELECT last_reminded_at, snoozed_until FROM reminder_state WHERE app = ? AND conversation = ?",
            arrayOf(app, conversation),
        ).use { c ->
            if (!c.moveToFirst()) return ReminderState()
            ReminderState(
                lastRemindedAt = if (c.isNull(0)) null else c.getLong(0),
                snoozedUntil = if (c.isNull(1)) null else c.getLong(1),
            )
        }

    fun saveReminderState(app: String, conversation: String, state: ReminderState) {
        val values = ContentValues().apply {
            put("app", app)
            put("conversation", conversation)
            put("last_reminded_at", state.lastRemindedAt)
            put("snoozed_until", state.snoozedUntil)
        }
        writableDatabase.insertWithOnConflict("reminder_state", null, values, SQLiteDatabase.CONFLICT_REPLACE)
    }

    private fun clearReminderState(app: String, conversation: String) {
        writableDatabase.delete("reminder_state", "app = ? AND conversation = ?", arrayOf(app, conversation))
    }

    companion object {
        @Volatile
        private var instance: MessageStore? = null

        /** One shared connection for the app, the listener and the alarms. */
        fun get(context: Context): MessageStore =
            instance ?: synchronized(this) { instance ?: MessageStore(context).also { instance = it } }

        /** Tests get a fresh app (and database file) each time; drop the old connection. */
        internal fun resetForTests() {
            instance?.close()
            instance = null
        }
    }
}
