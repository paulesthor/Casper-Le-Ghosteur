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
    SQLiteOpenHelper(context.applicationContext, "messages.db", null, 4) {

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
                handled_at INTEGER,
                account TEXT,
                UNIQUE(app, conversation, sender, text, ts)
            )
            """.trimIndent()
        )
        createReminderTables(db)
        createAccountsTable(db)
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if (oldVersion < 2) createReminderTables(db)
        if (oldVersion < 3) db.execSQL("ALTER TABLE messages ADD COLUMN handled_at INTEGER")
        if (oldVersion < 4) {
            db.execSQL("ALTER TABLE messages ADD COLUMN account TEXT")
            createAccountsTable(db)
        }
    }

    private fun createAccountsTable(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE accounts (
                app TEXT NOT NULL,
                name TEXT NOT NULL,
                enabled INTEGER NOT NULL DEFAULT 1,
                PRIMARY KEY (app, name)
            )
            """.trimIndent()
        )
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
                        put("account", m.account)
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
    fun markHandled(app: String, conversation: String, upTo: Long = Long.MAX_VALUE): Int =
        handle(app, conversation, upTo, System.currentTimeMillis())

    private fun handle(app: String, conversation: String, upTo: Long, at: Long): Int {
        val changed = writableDatabase.compileStatement(
            "UPDATE messages SET handled = 1, handled_at = ? WHERE app = ? AND conversation = ? AND handled = 0 AND ts <= ?"
        ).run {
            bindLong(1, at)
            bindString(2, app)
            bindString(3, conversation)
            bindLong(4, upTo)
            executeUpdateDelete()
        }
        if (pendingConversation(app, conversation) == null) clearReminderState(app, conversation)
        return changed
    }

    /** What "ignore" changed, so it can be put back exactly as it was. */
    data class Undo(val app: String, val conversation: String, val handledAt: Long, val reminders: ReminderState)

    /** Takes a conversation off the list without answering it. */
    fun ignore(app: String, conversation: String): Undo {
        val undo = Undo(app, conversation, System.currentTimeMillis(), reminderState(app, conversation))
        handle(app, conversation, Long.MAX_VALUE, undo.handledAt)
        return undo
    }

    fun undo(undo: Undo) {
        writableDatabase.execSQL(
            "UPDATE messages SET handled = 0, handled_at = NULL WHERE app = ? AND conversation = ? AND handled_at = ?",
            arrayOf(undo.app, undo.conversation, undo.handledAt),
        )
        if (undo.reminders != ReminderState()) saveReminderState(undo.app, undo.conversation, undo.reminders)
    }

    data class Account(val app: String, val name: String, val enabled: Boolean, val messagesThisWeek: Int)

    /** Remembers one of the user's own accounts (new ones are followed); returns whether it's followed. */
    fun noteAccount(app: String, name: String): Boolean {
        writableDatabase.insertWithOnConflict(
            "accounts", null,
            ContentValues().apply { put("app", app); put("name", name); put("enabled", 1) },
            SQLiteDatabase.CONFLICT_IGNORE,
        )
        return readableDatabase.rawQuery(
            "SELECT enabled FROM accounts WHERE app = ? AND name = ?", arrayOf(app, name),
        ).use { it.moveToFirst() && it.getInt(0) == 1 }
    }

    fun accounts(now: Long = System.currentTimeMillis()): List<Account> =
        readableDatabase.rawQuery(
            """
            SELECT a.app, a.name, a.enabled,
                   (SELECT COUNT(*) FROM messages m WHERE m.app = a.app AND m.account = a.name AND m.ts > ?)
            FROM accounts a ORDER BY a.enabled DESC, a.name COLLATE NOCASE
            """.trimIndent(),
            arrayOf((now - 7 * 24 * 3_600_000L).toString()),
        ).use { c ->
            buildList { while (c.moveToNext()) add(Account(c.getString(0), c.getString(1), c.getInt(2) == 1, c.getInt(3))) }
        }

    /** Stops following an account and takes its pending messages off the list; [undo] puts both back. */
    fun disableAccount(app: String, name: String): Long {
        val at = System.currentTimeMillis()
        writableDatabase.execSQL("UPDATE accounts SET enabled = 0 WHERE app = ? AND name = ?", arrayOf(app, name))
        writableDatabase.execSQL(
            "UPDATE messages SET handled = 1, handled_at = ? WHERE app = ? AND account = ? AND handled = 0",
            arrayOf(at, app, name),
        )
        return at
    }

    fun enableAccount(app: String, name: String, undoDisabledAt: Long? = null) {
        writableDatabase.execSQL("UPDATE accounts SET enabled = 1 WHERE app = ? AND name = ?", arrayOf(app, name))
        if (undoDisabledAt != null) {
            writableDatabase.execSQL(
                "UPDATE messages SET handled = 0, handled_at = NULL WHERE app = ? AND account = ? AND handled_at = ?",
                arrayOf(app, name, undoDisabledAt),
            )
        }
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
