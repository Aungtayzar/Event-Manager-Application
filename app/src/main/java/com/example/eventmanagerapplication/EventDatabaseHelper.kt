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
    val amount: Double
)

class EventDatabaseHelper(context: Context, databaseName: String = "events.db") :
    SQLiteOpenHelper(context, databaseName, null, 1) {
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
                amount REAL NOT NULL
            )"""
        )
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit

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
                        amount = cursor.getDouble(cursor.getColumnIndexOrThrow("amount"))
                    )
                )
            }
        }
    }

    fun insert(event: Event): Long = writableDatabase.insertOrThrow("events", null, event.values())

    fun update(event: Event): Int = writableDatabase.update(
        "events", event.values(), "id = ?", arrayOf(event.id.toString())
    )

    fun delete(id: Long): Int = writableDatabase.delete("events", "id = ?", arrayOf(id.toString()))

    private fun Event.values() = ContentValues().apply {
        put("name", name)
        put("date", date)
        put("location", location)
        put("organiser", organiser)
        put("type", type)
        put("status", status)
        put("amount", amount)
    }
}
