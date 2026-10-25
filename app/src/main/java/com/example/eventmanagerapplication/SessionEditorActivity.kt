package com.example.eventmanagerapplication

import android.app.TimePickerDialog
import android.content.Context
import android.content.Intent
import android.database.sqlite.SQLiteException
import android.os.Bundle
import android.view.View
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.example.eventmanagerapplication.databinding.ActivitySessionEditorBinding
import java.util.Calendar
import java.util.Locale

class SessionEditorActivity : AppCompatActivity() {
    private lateinit var binding: ActivitySessionEditorBinding
    private lateinit var database: EventDatabaseHelper
    private var eventId = 0L
    private var sessionId = 0L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySessionEditorBinding.inflate(layoutInflater)
        setContentView(binding.root)
        database = EventDatabaseHelper(this)
        eventId = intent.getLongExtra(EVENT_ID, 0)
        sessionId = intent.getLongExtra(SESSION_ID, 0)
        val sourceSessionId = intent.getLongExtra(SOURCE_SESSION_ID, 0)
        val duplicating = sessionId == 0L && sourceSessionId != 0L
        if (duplicating) binding.sessionFormTitle.setText(R.string.duplicate_session)
        try {
            val event = database.getEvent(eventId)
            if (event == null) {
                unavailable(R.string.error_event_missing)
                return
            }
            binding.sessionEventName.text = getString(R.string.sessions_for_event, event.name)
            if (sessionId != 0L || (duplicating && savedInstanceState == null)) {
                val session = database.getSession(eventId, if (duplicating) sourceSessionId else sessionId)
                if (session == null) {
                    unavailable(R.string.error_session_missing)
                    return
                }
                if (!duplicating) {
                    binding.sessionFormTitle.setText(R.string.edit_session)
                    binding.sessionIdText.text = getString(R.string.session_id, sessionId)
                }
                if (savedInstanceState == null) {
                    populateForm(if (duplicating) session.copy(
                        id = 0, name = getString(R.string.session_copy_name, session.name)
                    ) else session)
                }
            }
        } catch (_: SQLiteException) {
            unavailable(R.string.error_sessions_load)
            return
        }
        binding.chooseTimeButton.setOnClickListener { showTimePicker() }
        binding.saveSessionButton.setOnClickListener { saveSession() }
        binding.cancelSessionButton.setOnClickListener { finish() }
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            val ime = insets.getInsets(WindowInsetsCompat.Type.ime())
            view.setPadding(bars.left, bars.top, bars.right, maxOf(bars.bottom, ime.bottom))
            insets
        }
    }

    private fun populateForm(session: Session) {
        binding.sessionNameInput.setText(session.name)
        binding.sessionTimeInput.setText(session.time)
        binding.sessionSpeakerInput.setText(session.speaker)
        binding.sessionRoomInput.setText(session.room)
        binding.sessionDescriptionInput.setText(session.description)
        binding.sessionTypeSpinner.setSelection(resources.getStringArray(R.array.session_types).indexOf(session.type).coerceAtLeast(0))
    }

    private fun showTimePicker() {
        val time = binding.sessionTimeInput.text.toString().trim()
        val now = Calendar.getInstance()
        val hour = if (SessionValidation.validTime(time)) time.substring(0, 2).toInt() else now.get(Calendar.HOUR_OF_DAY)
        val minute = if (SessionValidation.validTime(time)) time.substring(3, 5).toInt() else now.get(Calendar.MINUTE)
        TimePickerDialog(this, { _, selectedHour, selectedMinute ->
            binding.sessionTimeInput.setText(String.format(Locale.ROOT, "%02d:%02d", selectedHour, selectedMinute))
            fieldError(binding.sessionTimeInput, binding.sessionTimeError, null)
        }, hour, minute, true).show()
    }

    private fun saveSession() {
        val name = binding.sessionNameInput.text.toString().trim()
        val time = binding.sessionTimeInput.text.toString().trim()
        fieldError(binding.sessionNameInput, binding.sessionNameError,
            if (name.isEmpty()) R.string.error_session_name else null)
        fieldError(binding.sessionTimeInput, binding.sessionTimeError,
            if (!SessionValidation.validTime(time)) R.string.error_session_time else null)
        listOf(binding.sessionNameInput, binding.sessionTimeInput).firstOrNull { it.error != null }?.let {
            it.requestFocus()
            return
        }
        val session = Session(
            id = sessionId, eventId = eventId, name = name, time = time,
            type = binding.sessionTypeSpinner.selectedItem.toString(),
            speaker = binding.sessionSpeakerInput.text.toString().trim(),
            room = binding.sessionRoomInput.text.toString().trim(),
            description = binding.sessionDescriptionInput.text.toString().trim()
        )

        try {
            if (database.getEvent(eventId) == null) {
                unavailable(R.string.error_event_missing)
                return
            }
            if (sessionId == 0L) {
                database.insertSession(session)
            } else if (database.updateSession(session) == 0) {
                unavailable(R.string.error_session_missing)
                return
            }
        } catch (_: SQLiteException) {
            Toast.makeText(this, R.string.error_session_save, Toast.LENGTH_LONG).show()
            return
        }
        Toast.makeText(this, if (sessionId == 0L) R.string.session_added else R.string.session_updated, Toast.LENGTH_SHORT).show()
        finish()
    }

    private fun fieldError(input: EditText, error: TextView, message: Int?) {
        input.error = message?.let { getString(it) }
        input.setBackgroundResource(if (message == null) R.drawable.input_background else R.drawable.input_error_background)
        error.visibility = if (message == null) View.GONE else View.VISIBLE
        if (message != null) error.setText(message)
    }

    private fun unavailable(message: Int) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
        finish()
    }

    override fun onDestroy() {
        database.close()
        super.onDestroy()
    }

    companion object {
        private const val EVENT_ID = "event_id"
        private const val SESSION_ID = "session_id"
        private const val SOURCE_SESSION_ID = "source_session_id"

        fun duplicateIntent(context: Context, eventId: Long, sourceSessionId: Long): Intent =
            intent(context, eventId).putExtra(SOURCE_SESSION_ID, sourceSessionId)

        fun intent(context: Context, eventId: Long, sessionId: Long = 0): Intent =
            Intent(context, SessionEditorActivity::class.java)
                .putExtra(EVENT_ID, eventId).putExtra(SESSION_ID, sessionId)
    }
}
