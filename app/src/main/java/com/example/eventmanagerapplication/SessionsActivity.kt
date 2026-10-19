package com.example.eventmanagerapplication

import android.content.Context
import android.content.Intent
import android.database.sqlite.SQLiteException
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.eventmanagerapplication.databinding.ActivitySessionsBinding

class SessionsActivity : AppCompatActivity() {
    private lateinit var binding: ActivitySessionsBinding
    private lateinit var database: EventDatabaseHelper
    private lateinit var sessionAdapter: SessionAdapter
    private var eventId = 0L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySessionsBinding.inflate(layoutInflater)
        setContentView(binding.root)
        database = EventDatabaseHelper(this)
        eventId = intent.getLongExtra(EVENT_ID, 0)
        sessionAdapter = SessionAdapter(
            { startActivity(SessionEditorActivity.intent(this, eventId, it.id)) }, ::confirmDelete)
        binding.sessionList.layoutManager = LinearLayoutManager(this)
        binding.sessionList.adapter = sessionAdapter
        binding.backToEventsButton.setOnClickListener { finish() }
        binding.addSessionButton.setOnClickListener {
            startActivity(SessionEditorActivity.intent(this, eventId))
        }
        binding.retrySessionsButton.setOnClickListener { refreshSessions() }
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            insets
        }
    }

    override fun onResume() {
        super.onResume()
        refreshSessions()
    }

    private fun refreshSessions() {
        try {
            val event = database.getEvent(eventId)
            if (event == null) {
                Toast.makeText(this, R.string.error_event_missing, Toast.LENGTH_LONG).show()
                finish()
                return
            }
            val sessions = database.getSessions(eventId)
            binding.sessionsTitle.text = getString(R.string.sessions_for_event, event.name)
            binding.sessionsCount.text = resources.getQuantityString(R.plurals.session_count, sessions.size, sessions.size)
            binding.emptySessionsText.setText(R.string.empty_sessions)
            binding.emptySessionsText.visibility = if (sessions.isEmpty()) View.VISIBLE else View.GONE
            binding.retrySessionsButton.visibility = View.GONE
            binding.addSessionButton.isEnabled = true
            sessionAdapter.submitList(sessions)
        } catch (_: SQLiteException) {
            binding.emptySessionsText.setText(R.string.error_sessions_load)
            binding.emptySessionsText.visibility = View.VISIBLE
            binding.sessionsCount.text = ""
            binding.retrySessionsButton.visibility = View.VISIBLE
            binding.addSessionButton.isEnabled = false
            sessionAdapter.submitList(emptyList())
        }
    }

    private fun confirmDelete(session: Session) {
        AlertDialog.Builder(this)
            .setMessage(getString(R.string.confirm_delete_session, session.name))
            .setNegativeButton(R.string.cancel, null)
            .setPositiveButton(R.string.delete) { _, _ ->
                try {
                    database.deleteSession(eventId, session.id)
                    refreshSessions()
                    Toast.makeText(this, R.string.session_deleted, Toast.LENGTH_SHORT).show()
                } catch (_: SQLiteException) {
                    Toast.makeText(this, R.string.error_session_delete, Toast.LENGTH_LONG).show()
                }
            }.show()
    }

    override fun onDestroy() {
        database.close()
        super.onDestroy()
    }

    companion object {
        private const val EVENT_ID = "event_id"

        fun intent(context: Context, eventId: Long): Intent =
            Intent(context, SessionsActivity::class.java).putExtra(EVENT_ID, eventId)
    }
}
