package com.example.eventmanagerapplication

import android.app.DatePickerDialog
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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        database = EventDatabaseHelper(this)
        setContentView(binding.root)
        editingId = savedInstanceState?.getLong(EDITING_ID)?.takeIf { it != 0L }
        if (editingId != null) {
            binding.addEventButton.setText(R.string.save_changes)
            binding.cancelEditButton.visibility = View.VISIBLE
        }
        binding.eventDateInput.setOnClickListener { showDatePicker() }
        binding.addEventButton.setOnClickListener { saveEvent() }
        binding.cancelEditButton.setOnClickListener { clearForm() }
        ViewCompat.setOnApplyWindowInsetsListener(binding.main) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            val padding = (20 * resources.displayMetrics.density).toInt()
            view.setPadding(bars.left + padding, bars.top + padding, bars.right + padding, bars.bottom + padding)
            insets
        }
        refreshEvents()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putLong(EDITING_ID, editingId ?: 0L)
        super.onSaveInstanceState(outState)
    }

    override fun onDestroy() {
        database.close()
        super.onDestroy()
    }

    private fun showDatePicker() {
        val selected = Calendar.getInstance()
        val format = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).apply { isLenient = false }
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

        val format = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).apply { isLenient = false }
        val parsed = format.parse(date, ParsePosition(0))
        if (date.isEmpty() || parsed == null || format.format(parsed) != date) {
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
        if (invalid) return

        val event = Event(
            id = editingId ?: 0,
            name = name,
            date = date,
            location = location,
            organiser = organiser,
            type = binding.eventTypeSpinner.selectedItem.toString(),
            status = binding.eventStatusSpinner.selectedItem.toString(),
            amount = amount!!
        )
        val wasEditing = editingId != null
        if (wasEditing) database.update(event) else database.insert(event)
        clearForm()
        refreshEvents()
        Toast.makeText(this, if (wasEditing) R.string.event_updated else R.string.event_added, Toast.LENGTH_SHORT).show()
    }

    private fun editEvent(event: Event) {
        editingId = event.id
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
        binding.eventList.removeAllViews()
        val events = database.getAll()
        binding.emptyEventsText.visibility = if (events.isEmpty()) View.VISIBLE else View.GONE
        events.forEach { event ->
            val card = layoutInflater.inflate(R.layout.event_item, binding.eventList, false)
            card.findViewById<TextView>(R.id.eventCardName).text = event.name
            card.findViewById<TextView>(R.id.eventCardDetails).text = getString(
                R.string.event_details, event.date, event.type, event.location,
                event.organiser, event.status, event.amount
            )
            card.findViewById<View>(R.id.editEventButton).setOnClickListener { editEvent(event) }
            card.findViewById<View>(R.id.deleteEventButton).setOnClickListener { confirmDelete(event) }
            binding.eventList.addView(card)
        }
    }

    private fun showError(input: EditText, error: TextView, message: Int) {
        input.setBackgroundResource(R.drawable.input_error_background)
        error.setText(message)
        error.visibility = View.VISIBLE
    }

    private fun clearError(input: EditText, error: TextView) {
        input.setBackgroundResource(R.drawable.input_background)
        error.visibility = View.GONE
    }

    private fun clearForm() {
        editingId = null
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
