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
        val prefsRef = db.collection("events").document(evId).collection("preferences")

        val guestMap = mutableMapOf<String, Guest>()

        // Listen for RSVPs
        rsvpsRef.addSnapshotListener { snapshot, e ->
            if (e != null || snapshot == null) return@addSnapshotListener
            for (doc in snapshot.documents) {
                val guestId = doc.id
                val userName = doc.getString("userName") ?: "Anonymous"
                val status = doc.getString("status") ?: "Not set"
                val current = guestMap[guestId]
                guestMap[guestId] = Guest(
                    guestId,
                    userName,
                    status,
                    current?.dietaryChoice ?: "Not specified",
                    current?.musicChoice ?: "Not specified"
                )
            }
            updateGuestList(guestMap)
        }

        // Listen for Preferences
        prefsRef.addSnapshotListener { snapshot, e ->
            if (e != null || snapshot == null) return@addSnapshotListener
            for (doc in snapshot.documents) {
                val guestId = doc.id
                val dietary = doc.getString("dietaryChoice") ?: "Not specified"
                val music = doc.getString("musicChoice") ?: "Not specified"
                val current = guestMap[guestId]
                if (current != null) {
                    guestMap[guestId] = current.copy(dietaryChoice = dietary, musicChoice = music)
                }
            }
            updateGuestList(guestMap)
        }
    }

    private fun updateGuestList(guestMap: Map<String, Guest>) {
        guestList.clear()
        guestList.addAll(guestMap.values)
        attendeeCountText.text = "Attendees: ${guestList.count { it.status == "going" }}"
        guestAdapter.notifyDataSetChanged()
    }
}
