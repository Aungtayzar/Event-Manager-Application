package com.example.eventmanagerapplication

import android.app.DatePickerDialog
import android.database.sqlite.SQLiteException
import androidx.recyclerview.widget.LinearLayoutManager
import android.os.Bundle
import android.view.View
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.example.eventmanagerapplication.databinding.ActivityMainBinding
import java.text.ParsePosition
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding
    private lateinit var database: EventDatabaseHelper
    private var editingId: Long? = null
    private lateinit var eventAdapter: EventAdapter

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
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putLong(EDITING_ID, editingId ?: 0L)
        outState.putInt("selected_tab", binding.bottomNavigation.selectedItemId)
        super.onSaveInstanceState(outState)
    }

    override fun onDestroy() {
        database.close()
        super.onDestroy()
    }

    private fun showDatePicker() {
        val selected = Calendar.getInstance()
        val format = SimpleDateFormat("dd/MM/yyyy", Locale.UK).apply { isLenient = false }
        format.parse(binding.eventDateInput.text.toString(), ParsePosition(0))?.let { selected.time = it }
        DatePickerDialog(this, { _, year, month, day ->
            selected.set(year, month, day)
            binding.eventDateInput.setText(format.format(selected.time))
            clearError(binding.eventDateInput, binding.eventDateError)
        }, selected.get(Calendar.YEAR), selected.get(Calendar.MONTH), selected.get(Calendar.DAY_OF_MONTH)).show()
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

    private fun refreshEvents() {
        val events = database.getAll()
        binding.emptyEventsText.visibility = if (events.isEmpty()) View.VISIBLE else View.GONE
        eventAdapter.submitList(events)
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
    }
}
