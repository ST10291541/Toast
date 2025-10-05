package vcmsa.projects.toastapplication

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Button
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.firebase.firestore.FirebaseFirestore

class EventDetailsActivity : AppCompatActivity() {

    private lateinit var eventTitle: TextView
    private lateinit var eventDate: TextView
    private lateinit var eventTime: TextView
    private lateinit var eventLocation: TextView
    private lateinit var aboutDescription: TextView
    private lateinit var categoryText: TextView
    private lateinit var attendeeCountText: TextView
    private lateinit var btnGoogleDrive: Button
    private lateinit var btnBack: ImageButton
    private lateinit var guestsRecyclerView: RecyclerView

    private val db = FirebaseFirestore.getInstance()
    private var event: Event? = null
    private val guestList = mutableListOf<Guest>()
    private lateinit var guestAdapter: GuestAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_event_details)

        initViews()
        btnBack.setOnClickListener { finish() }

        guestAdapter = GuestAdapter(guestList)
        guestsRecyclerView.layoutManager = LinearLayoutManager(this)
        guestsRecyclerView.adapter = guestAdapter

        val eventFromIntent = intent.getSerializableExtra("event") as? Event
        if (eventFromIntent == null || eventFromIntent.id.isNullOrBlank()) {
            Toast.makeText(this, "Event data not found", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        event = eventFromIntent
        bindEventData()
        loadGuestsAndPreferences()
    }

    private fun initViews() {
        eventTitle = findViewById(R.id.eventTitle)
        eventDate = findViewById(R.id.eventDate)
        eventTime = findViewById(R.id.eventTime)
        eventLocation = findViewById(R.id.eventLocation)
        aboutDescription = findViewById(R.id.aboutDescription)
        categoryText = findViewById(R.id.categoryText)
        attendeeCountText = findViewById(R.id.attendeeCount)
        btnGoogleDrive = findViewById(R.id.btnGoogleDrive)
        btnBack = findViewById(R.id.btnBack)
        guestsRecyclerView = findViewById(R.id.guestsRecyclerView)
    }

    private fun bindEventData() {
        event?.let { ev ->
            eventTitle.text = ev.title
            aboutDescription.text = ev.description
            eventDate.text = "Date: ${ev.date}"
            eventTime.text = "Start: ${ev.time}"
            eventLocation.text = "Location: ${ev.location}"
            categoryText.text = "Category: ${ev.category}"

            if (!ev.googleDriveLink.isNullOrBlank()) {
                btnGoogleDrive.text = "Open Google Drive Folder"
                btnGoogleDrive.isEnabled = true
                btnGoogleDrive.setOnClickListener {
                    try {
                        startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(ev.googleDriveLink)))
                    } catch (e: Exception) {
                        Toast.makeText(this, "Cannot open link: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
                }
            } else {
                btnGoogleDrive.text = "No Google Drive link available"
                btnGoogleDrive.isEnabled = false
            }
        }
    }

    private fun loadGuestsAndPreferences() {
        val evId = event?.id ?: return
        val rsvpsRef = db.collection("events").document(evId).collection("rsvps")
        val eventRef = db.collection("events").document(evId)

        // Listen for event document (pollResponses)
        eventRef.addSnapshotListener { eventSnapshot, e ->
            if (e != null || eventSnapshot == null || !eventSnapshot.exists()) return@addSnapshotListener
            val pollResponses = eventSnapshot.get("pollResponses") as? Map<String, Map<String, Any>> ?: emptyMap()

            // Now get RSVPs
            rsvpsRef.addSnapshotListener { rsvpSnapshot, rsvpError ->
                if (rsvpError != null || rsvpSnapshot == null) return@addSnapshotListener

                val guests = mutableListOf<Guest>()

                for (rsvpDoc in rsvpSnapshot.documents) {
                    val guestId = rsvpDoc.id
                    val userName = rsvpDoc.getString("userName") ?: "Anonymous"
                    val status = rsvpDoc.getString("status") ?: "Not set"

                    val pollData = pollResponses[guestId]
                    val dietary = pollData?.get("dietaryChoice") as? String ?: "Not specified"
                    val music = pollData?.get("musicChoice") as? String ?: "Not specified"

                    guests.add(Guest(guestId, userName, status, dietary, music))
                }

                guestList.clear()
                guestList.addAll(guests)
                attendeeCountText.text = "Attendees: ${guestList.count { it.status == "going" }}"
                guestAdapter.notifyDataSetChanged()
            }
        }
    }


    private fun updateGuestList(guestMap: Map<String, Guest>) {
        guestList.clear()
        guestList.addAll(guestMap.values)
        attendeeCountText.text = "Attendees: ${guestList.count { it.status == "going" }}"
        guestAdapter.notifyDataSetChanged()
    }
}
