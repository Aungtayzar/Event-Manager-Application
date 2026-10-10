package com.example.eventmanagerapplication

import android.app.DatePickerDialog
import android.os.Bundle
import android.view.View
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.widget.doAfterTextChanged
import com.example.eventmanagerapplication.databinding.ActivityMainBinding
import java.text.ParsePosition
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

// Holds the information entered for one event.
data class Event(
    val name: String,
    val date: String,
    val location: String,
    val organiser: String,
    val type: String
)

class MainActivity : AppCompatActivity() {

    private val events = mutableListOf<Event>()
    private lateinit var binding: ActivityMainBinding

    // Sets up the screen and connects the form controls to their actions.
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.eventDateInput.setOnClickListener { showDatePicker(binding.eventDateInput) }
        binding.addEventButton.setOnClickListener { addEvent() }
        binding.eventNameInput.doAfterTextChanged {
            if (!it.isNullOrBlank()) clearFieldError(binding.eventNameInput, binding.eventNameError)
        }
        binding.eventLocationInput.doAfterTextChanged {
            if (!it.isNullOrBlank()) clearFieldError(binding.eventLocationInput, binding.eventLocationError)
        }
        binding.eventOrganiserInput.doAfterTextChanged {
            if (!it.isNullOrBlank()) clearFieldError(binding.eventOrganiserInput, binding.eventOrganiserError)
        }

        ViewCompat.setOnApplyWindowInsetsListener(binding.main) { view, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            val spacing = (20 * resources.displayMetrics.density).toInt()
            view.setPadding(
                systemBars.left + spacing,
                systemBars.top + spacing,
                systemBars.right + spacing,
                systemBars.bottom + spacing
            )
            insets
        }
    }

    // Lets the user choose a date and displays it in day/month/year format.
    private fun showDatePicker(dateInput: EditText) {
        val today = Calendar.getInstance()
        val picker = DatePickerDialog(
            this,
            { _, year, month, day ->
                val selectedDate = Calendar.getInstance()
                selectedDate.set(year, month, day)
                val dateFormat = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
                dateInput.setText(dateFormat.format(selectedDate.time))
                clearFieldError(binding.eventDateInput, binding.eventDateError)
            },
            today.get(Calendar.YEAR),
            today.get(Calendar.MONTH),
            today.get(Calendar.DAY_OF_MONTH)
        )
        picker.show()
    }

    // Checks the form and adds a valid event to the in-memory list.
    private fun addEvent() {
        val name = binding.eventNameInput.text.toString().trim()
        val date = binding.eventDateInput.text.toString().trim()
        val location = binding.eventLocationInput.text.toString().trim()
        val organiser = binding.eventOrganiserInput.text.toString().trim()

        var hasError = false

        if (name.isEmpty()) {
            showFieldError(binding.eventNameInput, binding.eventNameError, R.string.error_name_required)
            hasError = true
        } else {
            clearFieldError(binding.eventNameInput, binding.eventNameError)
        }

        if (date.isEmpty()) {
            showFieldError(binding.eventDateInput, binding.eventDateError, R.string.error_date_required)
            hasError = true
        } else {
            val dateFormat = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
            dateFormat.isLenient = false
            val parsedDate = dateFormat.parse(date, ParsePosition(0))
            if (parsedDate == null || dateFormat.format(parsedDate) != date) {
                showFieldError(binding.eventDateInput, binding.eventDateError, R.string.error_date_format)
                hasError = true
            } else {
                clearFieldError(binding.eventDateInput, binding.eventDateError)
            }
        }

        if (location.isEmpty()) {
            showFieldError(binding.eventLocationInput, binding.eventLocationError, R.string.error_location_required)
            hasError = true
        } else {
            clearFieldError(binding.eventLocationInput, binding.eventLocationError)
        }

        if (organiser.isEmpty()) {
            showFieldError(binding.eventOrganiserInput, binding.eventOrganiserError, R.string.error_organiser_required)
            hasError = true
        } else {
            clearFieldError(binding.eventOrganiserInput, binding.eventOrganiserError)
        }

        if (hasError) return

        val event = Event(name, date, location, organiser, binding.eventTypeSpinner.selectedItem.toString())
        events.add(event)
        displayEvent(event)
        clearForm()
        Toast.makeText(this, R.string.event_added, Toast.LENGTH_SHORT).show()
    }

    // Highlights a field in red and shows its message directly below it.
    private fun showFieldError(input: EditText, errorText: TextView, messageId: Int) {
        input.setBackgroundResource(R.drawable.input_error_background)
        errorText.setText(messageId)
        errorText.visibility = View.VISIBLE
    }

    // Restores the normal border and hides the error after a field is corrected.
    private fun clearFieldError(input: EditText, errorText: TextView) {
        input.setBackgroundResource(R.drawable.input_background)
        errorText.visibility = View.GONE
    }

    // Adds one event card to the list shown below the form.
    private fun displayEvent(event: Event) {
        val eventCard = layoutInflater.inflate(R.layout.event_item, binding.eventList, false)

        eventCard.findViewById<TextView>(R.id.eventCardName).text = event.name
        eventCard.findViewById<TextView>(R.id.eventCardDetails).text = getString(
            R.string.event_details,
            event.date,
            event.type,
            event.location,
            event.organiser
        )

        binding.emptyEventsText.visibility = android.view.View.GONE
        binding.eventList.addView(eventCard)
    }

    // Clears the text fields and resets the event type after a successful add.
    private fun clearForm() {
        binding.eventNameInput.text.clear()
        binding.eventDateInput.text.clear()
        binding.eventLocationInput.text.clear()
        binding.eventOrganiserInput.text.clear()
        binding.eventTypeSpinner.setSelection(0)
    }
}
