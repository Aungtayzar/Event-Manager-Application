package com.example.eventmanagerapplication

data class Session(
    val id: Long = 0,
    val eventId: Long,
    val name: String,
    val time: String,
    val type: String,
    val speaker: String = "",
    val room: String = "",
    val description: String = ""
)

object SessionValidation {
    private val timePattern = Regex("([01][0-9]|2[0-3]):[0-5][0-9]")
    val types = listOf("Workshop", "Presentation", "Panel Discussion", "Networking", "Keynote", "Others")

    fun validTime(value: String): Boolean = value.matches(timePattern)

    fun requireValid(session: Session) {
        require(session.eventId > 0) { "An event is required" }
        require(session.name.isNotBlank()) { "Session name is required" }
        require(validTime(session.time)) { "Time must use HH:mm" }
        require(session.type in types) { "Choose a session type" }
    }
}
