package vcmsa.projects.toastapplication

import android.content.Intent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class EventAdapter(
    private var eventList: MutableList<Event>,
    private val onItemClick: (Event) -> Unit,
    private var eventGuestMap: Map<String, List<Guest>> = emptyMap()
) : RecyclerView.Adapter<EventAdapter.EventViewHolder>() {

    inner class EventViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val titleText: TextView = itemView.findViewById(R.id.eventTitle)
        private val dateText: TextView = itemView.findViewById(R.id.eventDate)
        private val timeText: TextView = itemView.findViewById(R.id.eventTime)
        private val locationText: TextView = itemView.findViewById(R.id.eventLocation)
        private val categoryText: TextView = itemView.findViewById(R.id.eventCategory)
        private val attendeeCountText: TextView = itemView.findViewById(R.id.attendeeCount)
        private val shareButton: Button = itemView.findViewById(R.id.btnShare)

        fun bind(event: Event) {
            // Bind main event details
            titleText.text = event.title
            dateText.text = event.date
            timeText.text = event.time
            locationText.text = event.location
            categoryText.text = event.category

            // 🔹 Get attendees for this event
            val guests = eventGuestMap[event.id] ?: emptyList()
            val goingCount = guests.count { it.status == "going" }

            attendeeCountText.text = "Attendees: $goingCount"

            // 🔹 Handle item click
            itemView.setOnClickListener { onItemClick(event) }

            // 🔹 Share button logic
            val shareLink = "https://toastapi-dqjl.onrender.com/api/events/share/${event.id}"
            shareButton.setOnClickListener {
                val shareIntent = Intent().apply {
                    action = Intent.ACTION_SEND
                    putExtra(
                        Intent.EXTRA_TEXT,
                        "🎉 You're invited to ${event.title}!\n" +
                                "📅 ${event.date} at ${event.time}\n" +
                                "📍 ${event.location}\n\n" +
                                "RSVP and view details: $shareLink"
                    )
                    type = "text/plain"
                }
                itemView.context.startActivity(Intent.createChooser(shareIntent, "Share event via"))
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

    // 🔹 Update the event list and guest data dynamically
    fun updateData(newList: List<Event>, newGuestMap: Map<String, List<Guest>>) {
        eventList = newList.toMutableList()
        eventGuestMap = newGuestMap
        notifyDataSetChanged()
    }
}
