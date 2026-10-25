package com.example.eventmanagerapplication

import android.content.Context
import android.content.Intent
import android.database.sqlite.SQLiteException
import android.os.Bundle
import android.view.View
import android.widget.Toast
import android.widget.SearchView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.eventmanagerapplication.databinding.ActivitySessionsBinding
import com.example.eventmanagerapplication.databinding.DialogSessionDetailsBinding

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
            ::showDetails,
            { startActivity(SessionEditorActivity.intent(this, eventId, it.id)) }, ::confirmDelete,
            { startActivity(SessionEditorActivity.duplicateIntent(this, eventId, it.id)) })
        binding.sessionList.layoutManager = LinearLayoutManager(this)
        binding.sessionList.adapter = sessionAdapter
        binding.sessionSearchInput.setQuery(savedInstanceState?.getString(SEARCH_QUERY).orEmpty(), false)
        binding.sessionSearchInput.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String?) = false
            override fun onQueryTextChange(newText: String?): Boolean {
                refreshSessions(resetScroll = true)
                return true
            }
        })
        binding.sessionSearchInput.clearFocus()
        binding.backToEventsButton.setOnClickListener { finish() }
        binding.addSessionButton.setOnClickListener {
            startActivity(SessionEditorActivity.intent(this, eventId))
        }
        binding.retrySessionsButton.setOnClickListener { refreshSessions() }
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            val ime = insets.getInsets(WindowInsetsCompat.Type.ime())
            view.setPadding(bars.left, bars.top, bars.right, maxOf(bars.bottom, ime.bottom))
            insets
        }
    }

    override fun onResume() {
        super.onResume()
        refreshSessions()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putString(SEARCH_QUERY, binding.sessionSearchInput.query.toString())
        super.onSaveInstanceState(outState)
    }

    private fun refreshSessions(resetScroll: Boolean = false) {
        try {
            val event = database.getEvent(eventId)
            if (event == null) {
                Toast.makeText(this, R.string.error_event_missing, Toast.LENGTH_LONG).show()
                finish()
                return
            }
            val query = binding.sessionSearchInput.query.toString()
            val searching = query.isNotBlank()
            val sessions = database.searchSessions(eventId, query)
            binding.sessionsTitle.text = getString(R.string.sessions_for_event, event.name)
            binding.sessionsCount.text = resources.getQuantityString(
                if (searching) R.plurals.matching_session_count else R.plurals.session_count, sessions.size, sessions.size)
            binding.emptySessionsText.setText(if (searching) R.string.no_matching_sessions else R.string.empty_sessions)
            binding.emptySessionsText.visibility = if (sessions.isEmpty()) View.VISIBLE else View.GONE
            binding.retrySessionsButton.visibility = View.GONE
            binding.addSessionButton.isEnabled = true
            sessionAdapter.submitList(sessions) {
                if (resetScroll) binding.sessionList.scrollToPosition(0)
            }
        } catch (_: SQLiteException) {
            binding.emptySessionsText.setText(R.string.error_sessions_load)
            binding.emptySessionsText.visibility = View.VISIBLE
            binding.sessionsCount.text = ""
            binding.retrySessionsButton.visibility = View.VISIBLE
            binding.addSessionButton.isEnabled = false
            sessionAdapter.submitList(emptyList())
        }
    }

    private fun showDetails(session: Session) {
        val details = getString(R.string.full_session_details, session.name,
            getString(R.string.session_summary, session.id, session.time, session.type),
            getString(R.string.session_details,
                session.speaker.ifEmpty { getString(R.string.not_provided) },
                session.room.ifEmpty { getString(R.string.not_provided) },
                session.description.ifEmpty { getString(R.string.not_provided) }))
        val checklist = DialogSessionDetailsBinding.inflate(layoutInflater)
        checklist.sessionDetailsText.text = details
        val completed = try {
            database.getPreparation(eventId, session.id).toMutableSet()
        } catch (_: SQLiteException) {
            Toast.makeText(this, R.string.error_sessions_load, Toast.LENGTH_LONG).show()
            return
        }
        fun updateProgress() {
            checklist.preparationProgress.text = if (completed.size == 4) {
                getString(R.string.preparation_ready)
            } else getString(R.string.preparation_progress, completed.size)
        }
        updateProgress()
        listOf(checklist.roomPrepared, checklist.equipmentTested,
            checklist.speakerConfirmed, checklist.materialsReady).forEachIndexed { item, checkbox ->
            checkbox.isChecked = item in completed
            checkbox.setOnClickListener {
                val checked = checkbox.isChecked
                val saved = try {
                    database.setPreparationItem(eventId, session.id, item, checked)
                } catch (_: SQLiteException) { false }
                if (saved) {
                    if (checked) completed.add(item) else completed.remove(item)
                    updateProgress()
                } else {
                    checkbox.isChecked = item in completed
                    Toast.makeText(this, R.string.error_preparation_save, Toast.LENGTH_LONG).show()
                }
            }
        }
        AlertDialog.Builder(this).setTitle(R.string.details).setView(checklist.root)
            .setNegativeButton(R.string.close, null)
            .setPositiveButton(R.string.edit) { _, _ ->
                startActivity(SessionEditorActivity.intent(this, eventId, session.id))
            }.show()
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
        private const val SEARCH_QUERY = "session_search_query"

        fun intent(context: Context, eventId: Long): Intent =
            Intent(context, SessionsActivity::class.java).putExtra(EVENT_ID, eventId)
    }
}
