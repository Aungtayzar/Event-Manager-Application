package com.example.eventmanagerapplication

import android.app.DatePickerDialog
import android.Manifest
import android.content.pm.PackageManager
import android.database.sqlite.SQLiteException
import androidx.recyclerview.widget.LinearLayoutManager
import android.os.Bundle
import android.view.View
import android.widget.EditText
import android.widget.SearchView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.example.eventmanagerapplication.databinding.ActivityMainBinding
import com.example.eventmanagerapplication.databinding.DialogEventFiltersBinding
import java.text.ParsePosition
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.text.DateFormat
import java.util.Date
import androidx.work.WorkInfo
import androidx.work.WorkManager

class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding
    private lateinit var database: EventDatabaseHelper
    private var editingId: Long? = null
    private lateinit var eventAdapter: EventAdapter
    private var searchDate = ""
    private var searchOrganiser = ""
    private var uploadRunning = false
    private var uploadQueued = false
    private val localNetworkPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) uploadAllEvents() else binding.uploadStatusText.setText(R.string.upload_permission_denied)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        database = EventDatabaseHelper(this)
        setContentView(binding.root)
        editingId = savedInstanceState?.getLong(EDITING_ID)?.takeIf { it != 0L }
        if (editingId != null) {
            binding.addEventButton.setText(R.string.save_changes)
            binding.cancelEditButton.visibility = View.VISIBLE
            binding.formTitle.setText(R.string.edit)
        }
        eventAdapter = EventAdapter(::showDetails, ::editEvent, ::confirmDelete, ::showSessions)
        binding.eventList.layoutManager = LinearLayoutManager(this)
        binding.eventList.adapter = eventAdapter
        searchDate = savedInstanceState?.getString(SEARCH_DATE).orEmpty()
        searchOrganiser = savedInstanceState?.getString(SEARCH_ORGANISER).orEmpty()
        binding.eventSearchInput.setQuery(savedInstanceState?.getString(SEARCH_QUERY).orEmpty(), false)
        binding.eventSearchInput.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String?) = false
            override fun onQueryTextChange(newText: String?): Boolean {
                refreshEvents(resetScroll = true)
                return true
            }
        })
        binding.eventSearchInput.clearFocus()
        binding.advancedSearchButton.setOnClickListener { showSearchFilters() }
        binding.clearEventSearchButton.setOnClickListener {
            searchDate = ""
            searchOrganiser = ""
            binding.eventSearchInput.setQuery("", false)
            binding.eventSearchInput.clearFocus()
            refreshEvents(resetScroll = true)
        }
        binding.retryEventsButton.setOnClickListener { refreshEvents() }
        binding.uploadEventsButton.setOnClickListener { uploadAllEvents() }
        WorkManager.getInstance(this).getWorkInfosForUniqueWorkLiveData(EventSyncScheduler.WORK_NAME)
            .observe(this) { work ->
                uploadRunning = work.any { it.state == WorkInfo.State.RUNNING }
                uploadQueued = work.any { it.state == WorkInfo.State.ENQUEUED || it.state == WorkInfo.State.BLOCKED }
                refreshUploadStatus()
            }
        binding.bottomNavigation.setOnItemSelectedListener { item ->
            showPage(item.itemId == R.id.nav_add)
            true
        }
        binding.bottomNavigation.selectedItemId = savedInstanceState?.getInt("selected_tab") ?: R.id.nav_events
        binding.eventDateInput.setOnLongClickListener { showDatePicker(); true }
        binding.chooseDateButton.setOnClickListener { showDatePicker() }
        binding.addEventButton.setOnClickListener { saveEvent() }
        binding.cancelEditButton.setOnClickListener { clearForm() }
        ViewCompat.setOnApplyWindowInsetsListener(binding.main) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            val ime = insets.getInsets(WindowInsetsCompat.Type.ime())
            view.setPadding(bars.left, bars.top, bars.right, maxOf(bars.bottom, ime.bottom))
            insets
        }
    }

    override fun onResume() {
        super.onResume()
        refreshEvents()
        refreshUploadStatus()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putLong(EDITING_ID, editingId ?: 0L)
        outState.putInt("selected_tab", binding.bottomNavigation.selectedItemId)
        outState.putString(SEARCH_QUERY, binding.eventSearchInput.query.toString())
        outState.putString(SEARCH_DATE, searchDate)
        outState.putString(SEARCH_ORGANISER, searchOrganiser)
        super.onSaveInstanceState(outState)
    }

    override fun onDestroy() {
        database.close()
        super.onDestroy()
    }

    private fun uploadAllEvents() {
        if (EventSyncWorker.requiresLocalNetwork() && ContextCompat.checkSelfPermission(this,
                Manifest.permission.ACCESS_LOCAL_NETWORK) != PackageManager.PERMISSION_GRANTED) {
            localNetworkPermission.launch(Manifest.permission.ACCESS_LOCAL_NETWORK)
            return
        }
        try {
            database.enableSync()
            EventSyncScheduler.enqueue(this, forceUpload = true)
            uploadQueued = true
            refreshUploadStatus()
        } catch (_: SQLiteException) {
            binding.uploadStatusText.setText(R.string.upload_database_error)
        }
    }

    private fun refreshUploadStatus() {
        binding.uploadEventsButton.isEnabled = !uploadRunning && !uploadQueued
        try {
            val state = database.syncState()
            binding.uploadStatusText.text = when {
                uploadRunning -> getString(R.string.upload_running)
                !state.enabled -> getString(R.string.upload_not_started)
                uploadQueued && !EventSyncWorker.hasNetwork(this) -> getString(R.string.upload_waiting_network)
                uploadQueued && state.lastError.isNotEmpty() -> getString(R.string.upload_retry_pending, state.lastError)
                uploadQueued -> getString(R.string.upload_queued)
                state.lastError.isNotEmpty() -> state.lastError
                !state.needsUpload -> getString(R.string.upload_synced,
                    DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(state.uploadedAt)))
                else -> getString(R.string.upload_pending)
            }
        } catch (_: SQLiteException) {
            binding.uploadStatusText.setText(R.string.upload_database_error)
        }
    }

    private fun showDatePicker(input: EditText = binding.eventDateInput) {
        val selected = Calendar.getInstance()
        val format = SimpleDateFormat("dd/MM/yyyy", Locale.UK).apply { isLenient = false }
        format.parse(input.text.toString(), ParsePosition(0))?.let { selected.time = it }
        DatePickerDialog(this, { _, year, month, day ->
            selected.set(year, month, day)
            input.setText(format.format(selected.time))
            input.error = null
            if (input == binding.eventDateInput) clearError(input, binding.eventDateError)
        }, selected.get(Calendar.YEAR), selected.get(Calendar.MONTH), selected.get(Calendar.DAY_OF_MONTH)).show()
    }

    private fun showSearchFilters() {
        val filters = DialogEventFiltersBinding.inflate(layoutInflater)
        filters.searchDateInput.setText(searchDate)
        filters.searchOrganiserInput.setText(searchOrganiser)
        filters.chooseSearchDateButton.setOnClickListener { showDatePicker(filters.searchDateInput) }
        val dialog = AlertDialog.Builder(this)
            .setTitle(R.string.advanced_search)
            .setView(filters.root)
            .setNegativeButton(R.string.cancel, null)
            .setNeutralButton(R.string.clear_filters) { _, _ ->
                searchDate = ""
                searchOrganiser = ""
                refreshEvents(resetScroll = true)
            }
            .setPositiveButton(R.string.apply_filters, null)
            .create()
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val date = filters.searchDateInput.text.toString().trim()
                if (date.isNotEmpty() && !EventValidation.validDate(date)) {
                    filters.searchDateInput.error = getString(R.string.error_date_format)
                    filters.searchDateInput.requestFocus()
                } else {
                    searchDate = date
                    searchOrganiser = filters.searchOrganiserInput.text.toString().trim()
                    refreshEvents(resetScroll = true)
                    dialog.dismiss()
                }
            }
        }
        dialog.show()
    }

    private fun saveEvent() {
        val name = binding.eventNameInput.text.toString().trim()
        val date = binding.eventDateInput.text.toString().trim()
        val location = binding.eventLocationInput.text.toString().trim()
        val organiser = binding.eventOrganiserInput.text.toString().trim()
        val amount = binding.eventAmountInput.text.toString().trim().toDoubleOrNull()
        var invalid = false
        if (name.isEmpty()) {
            showError(binding.eventNameInput, binding.eventNameError, R.string.error_name_required)
            invalid = true
        } else clearError(binding.eventNameInput, binding.eventNameError)

        if (!EventValidation.validDate(date)) {
            showError(binding.eventDateInput, binding.eventDateError,
                if (date.isEmpty()) R.string.error_date_required else R.string.error_date_format)
            invalid = true
        } else clearError(binding.eventDateInput, binding.eventDateError)

        if (location.isEmpty()) {
            showError(binding.eventLocationInput, binding.eventLocationError, R.string.error_location_required)
            invalid = true
        } else clearError(binding.eventLocationInput, binding.eventLocationError)
        if (organiser.isEmpty()) {
            showError(binding.eventOrganiserInput, binding.eventOrganiserError, R.string.error_organiser_required)
            invalid = true
        } else clearError(binding.eventOrganiserInput, binding.eventOrganiserError)
        if (amount == null || !amount.isFinite() || amount < 0) {
            showError(binding.eventAmountInput, binding.eventAmountError, R.string.error_amount)
            invalid = true
        } else clearError(binding.eventAmountInput, binding.eventAmountError)
        val email = binding.contactEmailInput.text.toString().trim()
        val phone = binding.contactPhoneInput.text.toString().trim()
        if (!EventValidation.validEmail(email)) {
            showError(binding.contactEmailInput, binding.contactEmailError, R.string.error_email)
            invalid = true
        } else clearError(binding.contactEmailInput, binding.contactEmailError)
        if (!EventValidation.validPhone(phone)) {
            showError(binding.contactPhoneInput, binding.contactPhoneError, R.string.error_phone)
            invalid = true
        } else clearError(binding.contactPhoneInput, binding.contactPhoneError)
        if (invalid) {
            listOf(binding.eventNameInput, binding.eventDateInput, binding.eventLocationInput,
                binding.eventOrganiserInput, binding.eventAmountInput, binding.contactEmailInput,
                binding.contactPhoneInput).firstOrNull { it.error != null }?.requestFocus()
            return
        }
        if (database.hasDuplicate(name, date, editingId ?: 0)) {
            showError(binding.eventNameInput, binding.eventNameError, R.string.error_duplicate)
            return
        }

        val event = Event(
            id = editingId ?: 0,
            name = name,
            date = date,
            location = location,
            organiser = organiser,
            type = binding.eventTypeSpinner.selectedItem.toString(),
            status = binding.eventStatusSpinner.selectedItem.toString(),
            amount = amount!!,
            description = binding.eventDescriptionInput.text.toString().trim(),
            contactEmail = email,
            contactPhone = phone
        )
        AlertDialog.Builder(this)
            .setTitle(R.string.review_event)
            .setMessage(eventDetails(event))
            .setNegativeButton(R.string.back_to_edit, null)
            .setPositiveButton(R.string.confirm_save) { _, _ -> persistEvent(event) }
            .show()
    }

    private fun persistEvent(event: Event) {
        try {
            if (event.id != 0L) database.update(event) else database.insert(event)
        } catch (_: IllegalArgumentException) {
            showError(binding.eventNameInput, binding.eventNameError, R.string.error_duplicate)
            return
        } catch (_: SQLiteException) {
            Toast.makeText(this, R.string.error_save, Toast.LENGTH_LONG).show()
            return
        }
        clearForm()
        refreshEvents()
        binding.bottomNavigation.selectedItemId = R.id.nav_events
        Toast.makeText(this, if (event.id != 0L) R.string.event_updated else R.string.event_added, Toast.LENGTH_SHORT).show()
    }

    private fun showPage(form: Boolean) {
        if (!form) {
            currentFocus?.clearFocus()
            androidx.core.view.WindowCompat.getInsetsController(window, binding.root)
                .hide(WindowInsetsCompat.Type.ime())
        }
        binding.formPage.visibility = if (form) View.VISIBLE else View.GONE
        binding.eventsPage.visibility = if (form) View.GONE else View.VISIBLE
    }

    private fun eventDetails(event: Event): String = getString(
        R.string.full_event_details, event.name,
        getString(R.string.event_details, event.date, event.type, event.location, event.organiser, event.status, event.amount),
        getString(R.string.optional_details,
            event.description.ifEmpty { getString(R.string.not_provided) },
            event.contactEmail.ifEmpty { getString(R.string.not_provided) },
            event.contactPhone.ifEmpty { getString(R.string.not_provided) })
    )

    private fun showDetails(event: Event) {
        AlertDialog.Builder(this).setTitle(R.string.details).setMessage(eventDetails(event))
            .setNegativeButton(R.string.close, null)
            .setNeutralButton(R.string.sessions) { _, _ -> showSessions(event) }
            .setPositiveButton(R.string.edit) { _, _ -> editEvent(event) }.show()
    }

    private fun showSessions(event: Event) {
        startActivity(SessionsActivity.intent(this, event.id))
    }

    private fun editEvent(event: Event) {
        clearForm()
        editingId = event.id
        binding.bottomNavigation.selectedItemId = R.id.nav_add
        binding.formTitle.setText(R.string.edit)
        binding.eventDescriptionInput.setText(event.description)
        binding.contactEmailInput.setText(event.contactEmail)
        binding.contactPhoneInput.setText(event.contactPhone)
        binding.eventNameInput.setText(event.name)
        binding.eventDateInput.setText(event.date)
        binding.eventLocationInput.setText(event.location)
        binding.eventOrganiserInput.setText(event.organiser)
        binding.eventAmountInput.setText(event.amount.toString())
        binding.eventTypeSpinner.setSelection(resources.getStringArray(R.array.event_types).indexOf(event.type).coerceAtLeast(0))
        binding.eventStatusSpinner.setSelection(resources.getStringArray(R.array.event_statuses).indexOf(event.status).coerceAtLeast(0))
        binding.addEventButton.setText(R.string.save_changes)
        binding.cancelEditButton.visibility = View.VISIBLE
        binding.eventNameInput.requestFocus()
    }

    private fun confirmDelete(event: Event) {
        AlertDialog.Builder(this)
            .setMessage(getString(R.string.confirm_delete, event.name))
            .setNegativeButton(R.string.cancel, null)
            .setPositiveButton(R.string.delete) { _, _ ->
                database.delete(event.id)
                if (editingId == event.id) clearForm()
                refreshEvents()
                Toast.makeText(this, R.string.event_deleted, Toast.LENGTH_SHORT).show()
            }.show()
    }

    private fun refreshEvents(resetScroll: Boolean = false) {
        val query = binding.eventSearchInput.query.toString()
        val searching = query.isNotBlank() || searchDate.isNotEmpty() || searchOrganiser.isNotEmpty()
        binding.clearEventSearchButton.isEnabled = searching
        binding.eventFilterSummary.visibility =
            if (searchDate.isNotEmpty() || searchOrganiser.isNotEmpty()) View.VISIBLE else View.GONE
        binding.eventFilterSummary.text = getString(R.string.event_filter_summary,
            searchDate.ifEmpty { getString(R.string.any_date) },
            searchOrganiser.ifEmpty { getString(R.string.any_organiser) })
        try {
            val events = database.searchEvents(query, searchDate, searchOrganiser)
            binding.emptyEventsText.setText(if (searching) R.string.no_matching_events else R.string.empty_events)
            binding.emptyEventsText.visibility = if (events.isEmpty()) View.VISIBLE else View.GONE
            binding.eventSearchCount.text = resources.getQuantityString(
                if (searching) R.plurals.matching_event_count else R.plurals.event_count, events.size, events.size)
            binding.retryEventsButton.visibility = View.GONE
            eventAdapter.submitList(events) {
                if (resetScroll) binding.eventList.scrollToPosition(0)
            }
        } catch (_: SQLiteException) {
            binding.emptyEventsText.setText(R.string.error_events_load)
            binding.emptyEventsText.visibility = View.VISIBLE
            binding.eventSearchCount.text = ""
            binding.retryEventsButton.visibility = View.VISIBLE
            eventAdapter.submitList(emptyList())
        }
    }

    private fun showError(input: EditText, error: TextView, message: Int) {
        input.setBackgroundResource(R.drawable.input_error_background)
        error.setText(message)
        error.visibility = View.VISIBLE
        input.error = getString(message)
        error.accessibilityLiveRegion = View.ACCESSIBILITY_LIVE_REGION_POLITE
    }

    private fun clearError(input: EditText, error: TextView) {
        input.setBackgroundResource(R.drawable.input_background)
        error.visibility = View.GONE
        input.error = null
    }

    private fun clearForm() {
        editingId = null
        binding.formTitle.setText(R.string.new_event)
        binding.eventDescriptionInput.text.clear()
        binding.contactEmailInput.text.clear()
        binding.contactPhoneInput.text.clear()
        clearError(binding.contactEmailInput, binding.contactEmailError)
        clearError(binding.contactPhoneInput, binding.contactPhoneError)
        binding.eventNameInput.text.clear()
        binding.eventDateInput.text.clear()
        binding.eventLocationInput.text.clear()
        binding.eventOrganiserInput.text.clear()
        binding.eventAmountInput.text.clear()
        binding.eventTypeSpinner.setSelection(0)
        binding.eventStatusSpinner.setSelection(0)
        binding.addEventButton.setText(R.string.add_event)
        binding.cancelEditButton.visibility = View.GONE
        clearError(binding.eventNameInput, binding.eventNameError)
        clearError(binding.eventDateInput, binding.eventDateError)
        clearError(binding.eventLocationInput, binding.eventLocationError)
        clearError(binding.eventOrganiserInput, binding.eventOrganiserError)
        clearError(binding.eventAmountInput, binding.eventAmountError)
    }

    private companion object {
        const val EDITING_ID = "editing_id"
        const val SEARCH_QUERY = "event_search_query"
        const val SEARCH_DATE = "event_search_date"
        const val SEARCH_ORGANISER = "event_search_organiser"
    }
}
