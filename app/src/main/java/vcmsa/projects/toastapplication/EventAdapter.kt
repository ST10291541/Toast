package vcmsa.projects.toastapplication

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.recyclerview.widget.RecyclerView

class EventAdapter(
    private var eventList: List<Event>,
    private val onItemClick: (Event) -> Unit,
    private var eventGuestMap: Map<String, List<Guest>> = emptyMap()
) : RecyclerView.Adapter<EventAdapter.EventViewHolder>() {

    inner class EventViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val tvEventTitle: TextView = itemView.findViewById(R.id.tvEventTitle)
        private val tvEventDateTime: TextView = itemView.findViewById(R.id.tvEventDateTime)
        private val tvEventLocation: TextView = itemView.findViewById(R.id.tvEventLocation)
        private val tvEventDescription: TextView = itemView.findViewById(R.id.tvEventDescription)
        private val tvEventCategory: TextView = itemView.findViewById(R.id.tvEventCategory)
        private val tvAttendeeCount: TextView = itemView.findViewById(R.id.tvAttendeeCount)
        private val imgEventCategory: ImageView = itemView.findViewById(R.id.imgEventCategory)

        // Link section views
        private val layoutGoogleDrive: LinearLayout = itemView.findViewById(R.id.layoutGoogleDrive)
        private val tvGoogleDriveLink: TextView = itemView.findViewById(R.id.tvGoogleDriveLink)
        private val btnCopyLink: ImageView = itemView.findViewById(R.id.btnCopyLink)
        private val btnShareLink: ImageView = itemView.findViewById(R.id.btnShareLink)
        private val btnShareEvent: ImageView = itemView.findViewById(R.id.btnShareEvent)

        fun bind(event: Event) {
            // Bind main event details
            tvEventTitle.text = event.title
            tvEventDateTime.text = "${event.date} • ${event.time}"
            tvEventLocation.text = event.location
            tvEventDescription.text = event.description
            tvEventCategory.text = event.category

            // 🔹 Get attendees for this event
            val guests = eventGuestMap[event.id] ?: emptyList()
            val goingCount = guests.count { it.status == "going" }
            tvAttendeeCount.text = goingCount.toString()

            // Set category-specific icon
            setCategoryIcon(event.category)

            // Handle Google Drive link visibility
            if (!event.googleDriveLink.isNullOrEmpty()) {
                layoutGoogleDrive.visibility = View.VISIBLE
                tvGoogleDriveLink.text = event.googleDriveLink

                // Copy link functionality
                btnCopyLink.setOnClickListener {
                    copyToClipboard(event.googleDriveLink)
                    showToast("Google Drive link copied to clipboard")
                }

                // Share link functionality
                btnShareLink.setOnClickListener {
                    shareLink(event.googleDriveLink, "Check out this Google Drive link for ${event.title}")
                }
            } else {
                layoutGoogleDrive.visibility = View.GONE
            }

            // 🔹 Share event button logic - KEEPING YOUR ORIGINAL FUNCTIONALITY
            val shareLink = "https://toastapi-dqjl.onrender.com/api/events/share/${event.id}"
            btnShareEvent.setOnClickListener {
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

            // 🔹 Handle item click for event details
            itemView.setOnClickListener {
                onItemClick(event)
            }
        }

        private fun setCategoryIcon(category: String) {
            val iconRes = when (category.lowercase()) {
                "wedding" -> R.drawable.ic_bell
                "party" -> R.drawable.ic_party
                "food" -> R.drawable.ic_home
                "art" -> R.drawable.ic_edit
                "meet-up" -> R.drawable.ic_bell
                else -> R.drawable.ic_bell
            }
            imgEventCategory.setImageResource(iconRes)
        }

        private fun copyToClipboard(text: String) {
            val clipboard = itemView.context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val clip = ClipData.newPlainText("Google Drive Link", text)
            clipboard.setPrimaryClip(clip)
        }

        private fun shareLink(link: String, subject: String) {
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_SUBJECT, subject)
                putExtra(Intent.EXTRA_TEXT, link)
            }
            itemView.context.startActivity(Intent.createChooser(intent, "Share Google Drive Link"))
        }

        private fun showToast(message: String) {
            Toast.makeText(itemView.context, message, Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): EventViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.event_card_item, parent, false) // Using the new card layout
        return EventViewHolder(view)
    }

    override fun onBindViewHolder(holder: EventViewHolder, position: Int) {
        holder.bind(eventList[position])
    }

    override fun getItemCount(): Int = eventList.size

    // 🔹 Update the event list and guest data dynamically
    fun updateData(newList: List<Event>, newGuestMap: Map<String, List<Guest>>) {
        eventList = newList
        eventGuestMap = newGuestMap
        notifyDataSetChanged()
    }
}