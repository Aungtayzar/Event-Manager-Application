package com.example.eventmanagerapplication

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import java.util.UUID

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

class EventDatabaseHelper(context: Context, private val databaseName: String = "events.db") :
    SQLiteOpenHelper(context, databaseName, null, 4) {
    private val applicationContext = context.applicationContext
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
        createSyncTable(db)
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if (oldVersion < 2) {
            listOf("description", "contact_email", "contact_phone").forEach {
                db.execSQL("ALTER TABLE events ADD COLUMN $it TEXT NOT NULL DEFAULT ''")
            }
        }
        if (oldVersion < 3) createSessionsTable(db)
        if (oldVersion < 4) createSyncTable(db)
    }

    private fun createSyncTable(db: SQLiteDatabase) {
        db.execSQL("""CREATE TABLE event_sync (
            id INTEGER PRIMARY KEY CHECK(id = 1), device_id TEXT NOT NULL,
            revision INTEGER NOT NULL DEFAULT 1, uploaded_revision INTEGER NOT NULL DEFAULT 0,
            enabled INTEGER NOT NULL DEFAULT 0, uploaded_at INTEGER NOT NULL DEFAULT 0,
            last_error TEXT NOT NULL DEFAULT ''
        )""")
        db.insertOrThrow("event_sync", null, ContentValues().apply {
            put("id", 1)
            put("device_id", UUID.randomUUID().toString())
        })
        // Revisions change in the same transaction as every event/session mutation.
        listOf("events", "sessions").forEach { table ->
            listOf("INSERT", "UPDATE", "DELETE").forEach { operation ->
                db.execSQL("""CREATE TRIGGER ${table}_sync_${operation.lowercase()} AFTER $operation ON $table
                    BEGIN UPDATE event_sync SET revision = revision + 1, last_error = '' WHERE id = 1; END""")
            }
        }
    }

    fun syncState(): EventSyncState = readableDatabase.rawQuery("SELECT * FROM event_sync WHERE id = 1", null).use {
        check(it.moveToFirst())
        EventSyncState(it.getString(it.getColumnIndexOrThrow("device_id")),
            it.getLong(it.getColumnIndexOrThrow("revision")),
            it.getLong(it.getColumnIndexOrThrow("uploaded_revision")),
            it.getInt(it.getColumnIndexOrThrow("enabled")) == 1,
            it.getLong(it.getColumnIndexOrThrow("uploaded_at")),
            it.getString(it.getColumnIndexOrThrow("last_error")))
    }

    fun enableSync() {
        writableDatabase.execSQL("UPDATE event_sync SET enabled = 1 WHERE id = 1")
    }

    fun resetSyncIdentity(deviceId: String) {
        writableDatabase.execSQL("""UPDATE event_sync SET device_id = ?, uploaded_revision = 0,
            enabled = 0, uploaded_at = 0, last_error = '' WHERE id = 1""", arrayOf(deviceId))
    }

    fun uploadSnapshot(): EventUploadSnapshot = writableDatabase.run {
        beginTransaction()
        try {
            val state = syncState()
            val sessions = querySessions("1 = 1", emptyArray()).groupBy { it.eventId }
            val snapshot = EventUploadSnapshot(state.deviceId, state.revision,
                getAll().map { EventWithSessions(it, sessions[it.id].orEmpty()) })
            setTransactionSuccessful()
            snapshot
        } finally { endTransaction() }
    }

    fun markUploaded(revision: Long) {
        writableDatabase.execSQL("""UPDATE event_sync SET
            uploaded_revision = MAX(uploaded_revision, ?), uploaded_at = ?, last_error = ''
            WHERE id = 1 AND revision >= ?""", arrayOf(revision, System.currentTimeMillis(), revision))
    }

    fun recordUploadError(message: String) {
        writableDatabase.update("event_sync", ContentValues().apply { put("last_error", message) }, "id = 1", null)
    }

    private fun scheduleUpload() {
        if (databaseName == "events.db" && syncState().enabled) {
            EventSyncScheduler.enqueue(applicationContext)
        }
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

    fun searchEvents(query: String = "", date: String = "", organiser: String = ""): List<Event> = queryEvents(
        """(instr(lower(name), lower(?)) > 0 OR instr(lower(description), lower(?)) > 0)
            AND (? = '' OR date = ?) AND instr(lower(organiser), lower(?)) > 0""",
        arrayOf(query.trim(), query.trim(), date.trim(), date.trim(), organiser.trim())
    )

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
    }.also { scheduleUpload() }

    fun update(event: Event): Int = writableDatabase.run {
        beginTransaction()
        try {
            checkDuplicate(event)
            val count = update("events", event.values(), "id = ?", arrayOf(event.id.toString()))
            setTransactionSuccessful()
            count
        } finally { endTransaction() }
    }.also { if (it > 0) scheduleUpload() }

    fun delete(id: Long): Int = writableDatabase.delete("events", "id = ?", arrayOf(id.toString()))
        .also { if (it > 0) scheduleUpload() }

    fun getSessions(eventId: Long): List<Session> = querySessions("event_id = ?", arrayOf(eventId.toString()))

    fun searchSessions(eventId: Long, query: String = ""): List<Session> = querySessions(
        "event_id = ? AND (instr(lower(name), lower(?)) > 0 OR instr(lower(description), lower(?)) > 0)",
        arrayOf(eventId.toString(), query.trim(), query.trim())
    )

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
        return writableDatabase.insertOrThrow("sessions", null, session.values()).also { scheduleUpload() }
    }

    fun updateSession(session: Session): Int {
        SessionValidation.requireValid(session)
        return writableDatabase.update("sessions", session.values(), "id = ? AND event_id = ?",
            arrayOf(session.id.toString(), session.eventId.toString())).also { if (it > 0) scheduleUpload() }
    }

    fun deleteSession(eventId: Long, sessionId: Long): Int = writableDatabase.delete(
        "sessions", "id = ? AND event_id = ?", arrayOf(sessionId.toString(), eventId.toString())
    ).also { if (it > 0) scheduleUpload() }

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
