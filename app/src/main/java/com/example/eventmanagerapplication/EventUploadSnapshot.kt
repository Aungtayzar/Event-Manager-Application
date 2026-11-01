package com.example.eventmanagerapplication

import org.json.JSONArray
import org.json.JSONObject

data class EventWithSessions(val event: Event, val sessions: List<Session>)

// Immutable snapshot of all events, sessions, and device revision state captured for upload.
data class EventUploadSnapshot(
    val deviceId: String,
    val revision: Long,
    val events: List<EventWithSessions>
) {
    val sessionCount: Int get() = events.sumOf { it.sessions.size }

    // Serializes snapshot into the JSON structure expected by the server sync endpoint.
    fun toJson(): JSONObject = JSONObject().apply {
        put("deviceId", deviceId)
        put("revision", revision)
        put("events", JSONArray().apply {
            events.forEach { (event, sessions) ->
                put(JSONObject().apply {
                    put("id", event.id)
                    put("name", event.name)
                    put("date", event.date)
                    put("location", event.location)
                    put("organiser", event.organiser)
                    put("type", event.type)
                    put("status", event.status)
                    put("amount", event.amount)
                    put("description", event.description)
                    put("contactEmail", event.contactEmail)
                    put("contactPhone", event.contactPhone)
                    put("sessions", JSONArray().apply {
                        sessions.forEach { session ->
                            put(JSONObject().apply {
                                put("id", session.id)
                                put("eventId", session.eventId)
                                put("name", session.name)
                                put("time", session.time)
                                put("type", session.type)
                                put("speaker", session.speaker)
                                put("room", session.room)
                                put("description", session.description)
                            })
                        }
                    })
                })
            }
        })
    }
}

// Data holder for local database sync metadata stored in 'event_sync'.
data class EventSyncState(
    val deviceId: String,
    val revision: Long,
    val uploadedRevision: Long,
    val enabled: Boolean,
    val uploadedAt: Long,
    val lastError: String
) {
    val needsUpload: Boolean get() = revision > uploadedRevision
}
