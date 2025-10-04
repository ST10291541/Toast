package vcmsa.projects.toastapplication

import android.content.Intent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import vcmsa.projects.toastapplication.network.RetrofitClient

class EventAdapter(
    private var eventList: MutableList<Event>,
    private val onItemClick: (Event) -> Unit
) : RecyclerView.Adapter<EventAdapter.EventViewHolder>() {

    inner class EventViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val titleText: TextView = itemView.findViewById(R.id.eventTitle)
        val dateText: TextView = itemView.findViewById(R.id.eventDate)
        val timeText: TextView = itemView.findViewById(R.id.eventTime)
        val locationText: TextView = itemView.findViewById(R.id.eventLocation)
        val categoryText: TextView = itemView.findViewById(R.id.eventCategory)
        val shareButton: Button = itemView.findViewById(R.id.btnShare)

        fun bind(event: Event) {
            titleText.text = event.title
            dateText.text = event.date
            timeText.text = event.time
            locationText.text = event.location
            categoryText.text = event.category

            // Open details on item click
            itemView.setOnClickListener {
                onItemClick(event)
            }

            // ✅ Generate the shareable deep link to open app directly
            val shareLink = "${RetrofitClient.BASE_URL}api/events/share/${event.id}"

            shareButton.setOnClickListener {
                val shareIntent = Intent().apply {
                    action = Intent.ACTION_SEND
                    putExtra(
                        Intent.EXTRA_TEXT,
                        "🎉 You're invited to ${event.title}!\nRSVP and submit preferences: $shareLink"
                    )
                    type = "text/plain"
                }
                itemView.context.startActivity(
                    Intent.createChooser(shareIntent, "Share event via")
                )
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
        eventList = newList.toMutableList()
        notifyDataSetChanged()
    }
}
