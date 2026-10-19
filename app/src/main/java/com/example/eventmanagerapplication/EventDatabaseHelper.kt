package com.example.eventmanagerapplication

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

data class Event(
    val id: Long = 0,
    val name: String,
    val date: String,
    val location: String,
    val organiser: String,
    val type: String,
    val status: String,
    val amount: Double,
    val description: String = "",
    val contactEmail: String = "",
    val contactPhone: String = "",
    val sessionCount: Int = 0
)

class EventDatabaseHelper(context: Context, databaseName: String = "events.db") :
    SQLiteOpenHelper(context, databaseName, null, 3) {
    override fun onConfigure(db: SQLiteDatabase) {
        super.onConfigure(db)
        db.setForeignKeyConstraintsEnabled(true)
    }

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """CREATE TABLE events (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                name TEXT NOT NULL,
                date TEXT NOT NULL,
                location TEXT NOT NULL,
                organiser TEXT NOT NULL,
                type TEXT NOT NULL,
                status TEXT NOT NULL,
                amount REAL NOT NULL,
                description TEXT NOT NULL DEFAULT '',
                contact_email TEXT NOT NULL DEFAULT '',
                contact_phone TEXT NOT NULL DEFAULT ''
            )"""
        )
        createSessionsTable(db)
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if (oldVersion < 2) {
            listOf("description", "contact_email", "contact_phone").forEach {
                db.execSQL("ALTER TABLE events ADD COLUMN $it TEXT NOT NULL DEFAULT ''")
            }
        }
        if (oldVersion < 3) createSessionsTable(db)
    }

    private fun createSessionsTable(db: SQLiteDatabase) {
        db.execSQL(
            """CREATE TABLE sessions (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                event_id INTEGER NOT NULL REFERENCES events(id) ON DELETE CASCADE,
                name TEXT NOT NULL CHECK(length(trim(name)) > 0),
                time TEXT NOT NULL CHECK(time GLOB '[0-2][0-9]:[0-5][0-9]' AND time < '24:00'),
                type TEXT NOT NULL CHECK(type IN ('Workshop', 'Presentation', 'Panel Discussion', 'Networking', 'Keynote', 'Others')),
                speaker TEXT NOT NULL DEFAULT '',
                room TEXT NOT NULL DEFAULT '',
                description TEXT NOT NULL DEFAULT ''
            )"""
        )
        db.execSQL("CREATE INDEX sessions_event_time ON sessions(event_id, time, id)")
    }

    fun hasDuplicate(name: String, date: String, excludingId: Long = 0): Boolean =
        readableDatabase.rawQuery(
            "SELECT 1 FROM events WHERE lower(trim(name)) = lower(trim(?)) AND date = ? AND id != ? LIMIT 1",
            arrayOf(name, date, excludingId.toString())
        ).use { it.moveToFirst() }

    private fun checkDuplicate(event: Event) {
        require(!hasDuplicate(event.name, event.date, event.id)) { "Duplicate event name and date" }
    }

    fun getAll(): List<Event> = queryEvents(null, null)

    fun getEvent(id: Long): Event? = queryEvents("id = ?", arrayOf(id.toString())).singleOrNull()

    private fun queryEvents(selection: String?, args: Array<String>?): List<Event> = readableDatabase.query(
        "events", arrayOf("*", "(SELECT COUNT(*) FROM sessions WHERE event_id = events.id) AS session_count"),
        selection, args, null, null, "id DESC"
    ).use { cursor ->
        buildList {
            while (cursor.moveToNext()) {
                add(
                    Event(
                        id = cursor.getLong(cursor.getColumnIndexOrThrow("id")),
                        name = cursor.getString(cursor.getColumnIndexOrThrow("name")),
                        date = cursor.getString(cursor.getColumnIndexOrThrow("date")),
                        location = cursor.getString(cursor.getColumnIndexOrThrow("location")),
                        organiser = cursor.getString(cursor.getColumnIndexOrThrow("organiser")),
                        type = cursor.getString(cursor.getColumnIndexOrThrow("type")),
                        status = cursor.getString(cursor.getColumnIndexOrThrow("status")),
                        amount = cursor.getDouble(cursor.getColumnIndexOrThrow("amount")),
                        description = cursor.getString(cursor.getColumnIndexOrThrow("description")),
                        contactEmail = cursor.getString(cursor.getColumnIndexOrThrow("contact_email")),
                        contactPhone = cursor.getString(cursor.getColumnIndexOrThrow("contact_phone")),
                        sessionCount = cursor.getInt(cursor.getColumnIndexOrThrow("session_count"))
                    )
                )
            }
        }
    }

    fun insert(event: Event): Long = writableDatabase.run {
        beginTransaction()
        try {
            checkDuplicate(event.copy(id = 0))
            val id = insertOrThrow("events", null, event.values())
            setTransactionSuccessful()
            id
        } finally { endTransaction() }
    }

    fun update(event: Event): Int = writableDatabase.run {
        beginTransaction()
        try {
            checkDuplicate(event)
            val count = update("events", event.values(), "id = ?", arrayOf(event.id.toString()))
            setTransactionSuccessful()
            count
        } finally { endTransaction() }
    }

    fun delete(id: Long): Int = writableDatabase.delete("events", "id = ?", arrayOf(id.toString()))

    fun getSessions(eventId: Long): List<Session> = querySessions("event_id = ?", arrayOf(eventId.toString()))

    fun getSession(eventId: Long, sessionId: Long): Session? = querySessions(
        "event_id = ? AND id = ?", arrayOf(eventId.toString(), sessionId.toString())
    ).singleOrNull()

    private fun querySessions(selection: String, args: Array<String>): List<Session> = readableDatabase.query(
        "sessions", null, selection, args, null, null, "time ASC, id ASC"
    ).use { cursor ->
        buildList {
            while (cursor.moveToNext()) {
                add(Session(
                    id = cursor.getLong(cursor.getColumnIndexOrThrow("id")),
                    eventId = cursor.getLong(cursor.getColumnIndexOrThrow("event_id")),
                    name = cursor.getString(cursor.getColumnIndexOrThrow("name")),
                    time = cursor.getString(cursor.getColumnIndexOrThrow("time")),
                    type = cursor.getString(cursor.getColumnIndexOrThrow("type")),
                    speaker = cursor.getString(cursor.getColumnIndexOrThrow("speaker")),
                    room = cursor.getString(cursor.getColumnIndexOrThrow("room")),
                    description = cursor.getString(cursor.getColumnIndexOrThrow("description"))
                ))
            }
        }
    }

    fun insertSession(session: Session): Long {
        SessionValidation.requireValid(session)
        return writableDatabase.insertOrThrow("sessions", null, session.values())
    }

    fun updateSession(session: Session): Int {
        SessionValidation.requireValid(session)
        return writableDatabase.update("sessions", session.values(), "id = ? AND event_id = ?",
            arrayOf(session.id.toString(), session.eventId.toString()))
    }

    fun deleteSession(eventId: Long, sessionId: Long): Int = writableDatabase.delete(
        "sessions", "id = ? AND event_id = ?", arrayOf(sessionId.toString(), eventId.toString())
    )

    private fun Session.values() = ContentValues().apply {
        put("event_id", eventId)
        put("name", name)
        put("time", time)
        put("type", type)
        put("speaker", speaker)
        put("room", room)
        put("description", description)
    }

    private fun Event.values() = ContentValues().apply {
        put("name", name)
        put("date", date)
        put("location", location)
        put("organiser", organiser)
        put("type", type)
        put("status", status)
        put("amount", amount)
        put("description", description)
        put("contact_email", contactEmail)
        put("contact_phone", contactPhone)
    }
}
