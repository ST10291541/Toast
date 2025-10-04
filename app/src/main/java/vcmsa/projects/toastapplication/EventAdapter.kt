package vcmsa.projects.toastapplication

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class EventAdapter(
    private var eventList: List<Event>,
    private val onItemClick: (Event) -> Unit
) : RecyclerView.Adapter<EventAdapter.EventViewHolder>() {

    inner class EventViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val titleText: TextView = itemView.findViewById(R.id.eventTitle)
        val dateText: TextView = itemView.findViewById(R.id.eventDate)
        val timeText: TextView = itemView.findViewById(R.id.eventTime)
        val locationText: TextView = itemView.findViewById(R.id.eventLocation)
        val categoryText: TextView = itemView.findViewById(R.id.eventCategory)
        val attendeeCountText: TextView = itemView.findViewById(R.id.eventAttendeeCount)

        fun bind(event: Event) {
            titleText.text = event.title
            dateText.text = event.date
            timeText.text = event.time
            locationText.text = event.location
            categoryText.text = event.category
            attendeeCountText.text = "Attendees: ${event.attendeeCount}"

            itemView.setOnClickListener {
                onItemClick(event)
            }
        }

    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): EventViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_event_card, parent, false)
        return EventViewHolder(view)
    }

    override fun onBindViewHolder(holder: EventViewHolder, position: Int) {
        holder.bind(eventList[position])
    }

    override fun getItemCount(): Int = eventList.size

    fun updateData(newList: List<Event>) {
        eventList = newList
        notifyDataSetChanged()
    }
}
