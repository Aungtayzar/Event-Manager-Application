package com.example.eventmanagerapplication

import android.app.Application
import android.database.sqlite.SQLiteException
import java.io.File
import java.io.IOException
import java.util.UUID

class EventManagerApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        // Recover a committed local change if the process stopped before enqueue.
        try {
            EventDatabaseHelper(this).use { database ->
                // A restored database on another installation must not share a
                // cloud identity or overwrite the original installation's data.
                val identityFile = File(noBackupFilesDir, "event-sync-device-id")
                val savedIdentity = if (identityFile.exists()) identityFile.readText() else null
                if (savedIdentity != database.syncState().deviceId) {
                    val newIdentity = UUID.randomUUID().toString()
                    database.resetSyncIdentity(newIdentity)
                    identityFile.writeText(newIdentity)
                }
                val state = database.syncState()
                if (state.enabled && state.needsUpload) EventSyncScheduler.enqueue(this)
            }
        } catch (_: SQLiteException) {
            // The list screen shows the existing database error/retry UI.
        } catch (_: IOException) {
            // A later launch can retry initializing installation identity.
        }
    }
}
