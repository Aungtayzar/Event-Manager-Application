package com.example.eventmanagerapplication

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.eventmanagerapplication.databinding.SessionItemBinding

class SessionAdapter(
    private val onDetails: (Session) -> Unit,
    private val onEdit: (Session) -> Unit,
    private val onDelete: (Session) -> Unit,
    private val onDuplicate: (Session) -> Unit
) : ListAdapter<Session, SessionAdapter.SessionHolder>(object : DiffUtil.ItemCallback<Session>() {
    override fun areItemsTheSame(old: Session, new: Session) = old.id == new.id
    override fun areContentsTheSame(old: Session, new: Session) = old == new
}) {
    class SessionHolder(val binding: SessionItemBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = SessionHolder(
        SessionItemBinding.inflate(LayoutInflater.from(parent.context), parent, false)
    )

    override fun onBindViewHolder(holder: SessionHolder, position: Int) {
        val session = getItem(position)
        with(holder.binding) {
            val context = root.context
            sessionCardName.text = session.name
            sessionCardSummary.text = context.getString(R.string.session_summary, session.id, session.time, session.type)
            sessionCardDetails.text = context.getString(R.string.session_details,
                session.speaker.ifEmpty { context.getString(R.string.not_provided) },
                session.room.ifEmpty { context.getString(R.string.not_provided) },
                session.description.ifEmpty { context.getString(R.string.not_provided) })
            duplicateSessionButton.setOnClickListener { onDuplicate(session) }
            editSessionButton.setOnClickListener { onEdit(session) }
            deleteSessionButton.setOnClickListener { onDelete(session) }
            detailSessionButton.setOnClickListener { onDetails(session) }
            root.setOnClickListener { onDetails(session) }
        }
    }
}
