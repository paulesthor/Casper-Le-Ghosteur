package com.paulesthor.arepondre

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
)

class MessageStore(context: Context) :
    SQLiteOpenHelper(context.applicationContext, "messages.db", null, 1) {

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
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit

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
            SELECT app, conversation, COUNT(*), MAX(ts),
                   (SELECT text FROM messages m2
                     WHERE m2.app = m.app AND m2.conversation = m.conversation AND m2.handled = 0
                     ORDER BY ts DESC LIMIT 1)
            FROM messages m
            WHERE handled = 0
            GROUP BY app, conversation
            ORDER BY MAX(ts) DESC
        """.trimIndent()
        return readableDatabase.rawQuery(sql, null).use { c ->
            buildList {
                while (c.moveToNext()) {
                    add(Conversation(c.getString(0), c.getString(1), c.getInt(2), c.getString(4), c.getLong(3)))
                }
            }
        }
    }

    fun pendingMessages(app: String, conversation: String): List<CapturedMessage> =
        readableDatabase.rawQuery(
            "SELECT sender, text, ts FROM messages WHERE app = ? AND conversation = ? AND handled = 0 ORDER BY ts",
            arrayOf(app, conversation),
        ).use { c ->
            buildList {
                while (c.moveToNext()) add(CapturedMessage(app, conversation, c.getString(0), c.getString(1), c.getLong(2)))
            }
        }

    fun markHandled(app: String, conversation: String) {
        writableDatabase.execSQL(
            "UPDATE messages SET handled = 1 WHERE app = ? AND conversation = ?",
            arrayOf(app, conversation),
        )
    }
}
