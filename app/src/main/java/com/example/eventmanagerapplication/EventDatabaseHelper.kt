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
    val contactPhone: String = ""
)

class EventDatabaseHelper(context: Context, databaseName: String = "events.db") :
    SQLiteOpenHelper(context, databaseName, null, 2) {
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
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if (oldVersion < 2) {
            listOf("description", "contact_email", "contact_phone").forEach {
                db.execSQL("ALTER TABLE events ADD COLUMN $it TEXT NOT NULL DEFAULT ''")
            }
        }
    }

    fun hasDuplicate(name: String, date: String, excludingId: Long = 0): Boolean =
        readableDatabase.rawQuery(
            "SELECT 1 FROM events WHERE lower(trim(name)) = lower(trim(?)) AND date = ? AND id != ? LIMIT 1",
            arrayOf(name, date, excludingId.toString())
        ).use { it.moveToFirst() }

    private fun checkDuplicate(event: Event) {
        require(!hasDuplicate(event.name, event.date, event.id)) { "Duplicate event name and date" }
    }

    fun getAll(): List<Event> = readableDatabase.query(
        "events", null, null, null, null, null, "id DESC"
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
                        contactPhone = cursor.getString(cursor.getColumnIndexOrThrow("contact_phone"))
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
