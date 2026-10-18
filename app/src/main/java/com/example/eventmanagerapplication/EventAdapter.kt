package com.example.eventmanagerapplication

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.eventmanagerapplication.databinding.EventItemBinding

class EventAdapter(
    private val onDetails: (Event) -> Unit,
    private val onEdit: (Event) -> Unit,
    private val onDelete: (Event) -> Unit
) : ListAdapter<Event, EventAdapter.EventHolder>(object : DiffUtil.ItemCallback<Event>() {
    override fun areItemsTheSame(old: Event, new: Event) = old.id == new.id
    override fun areContentsTheSame(old: Event, new: Event) = old == new
}) {
    class EventHolder(val binding: EventItemBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = EventHolder(
        EventItemBinding.inflate(LayoutInflater.from(parent.context), parent, false)
    )

    override fun onBindViewHolder(holder: EventHolder, position: Int) {
        val event = getItem(position)
        with(holder.binding) {
            eventCardName.text = event.name
            eventCardDetails.text = root.context.getString(R.string.event_details,
                event.date, event.type, event.location, event.organiser, event.status, event.amount)
            detailEventButton.setOnClickListener { onDetails(event) }
            editEventButton.setOnClickListener { onEdit(event) }
            deleteEventButton.setOnClickListener { onDelete(event) }
        }
    }
}
