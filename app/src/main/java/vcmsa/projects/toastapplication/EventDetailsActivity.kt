package vcmsa.projects.toastapplication

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.firebase.firestore.FirebaseFirestore
import vcmsa.projects.toastapplication.local.EventEntity

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
    private var event: Any? = null // can be Event or EventEntity
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

        val eventFromIntent = intent.getSerializableExtra("event")
        if (eventFromIntent == null) {
            Toast.makeText(this, "Event data not found", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        event = eventFromIntent
        bindEventData()

        // Load guest info for any event with a valid Firebase ID
        val eventId = getEventId()
        if (!eventId.isNullOrBlank()) {
            // Check if event has been synced to Firestore and load latest data
            checkAndLoadSyncedEvent(eventId)
        }
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
        val evTitle = when (event) {
            is Event -> (event as Event).title
            is EventEntity -> (event as EventEntity).title
            else -> "Untitled Event"
        }
        Log.d("EventDetails", "Binding event data - Title: $evTitle")

        val evDescription = when (event) {
            is Event -> (event as Event).description
            is EventEntity -> (event as EventEntity).description
            else -> ""
        }

        val evDate = when (event) {
            is Event -> (event as Event).date
            is EventEntity -> (event as EventEntity).date
            else -> ""
        }

        val evTime = when (event) {
            is Event -> (event as Event).time
            is EventEntity -> (event as EventEntity).time
            else -> ""
        }

        val evLocation = when (event) {
            is Event -> (event as Event).location
            is EventEntity -> (event as EventEntity).location
            else -> ""
        }

        val evCategory = when (event) {
            is Event -> (event as Event).category
            is EventEntity -> (event as EventEntity).category
            else -> ""
        }

        val evGoogleDrive = when (event) {
            is Event -> (event as Event).googleDriveLink
            is EventEntity -> (event as EventEntity).googleDriveLink
            else -> null
        }

        eventTitle.text = evTitle
        aboutDescription.text = evDescription
        eventDate.text = "Date: $evDate"
        eventTime.text = "Start: $evTime"
        eventLocation.text = "Location: $evLocation"
        categoryText.text = "Category: $evCategory"

        if (!evGoogleDrive.isNullOrBlank()) {
            btnGoogleDrive.text = "Open Google Drive Folder"
            btnGoogleDrive.isEnabled = true
            btnGoogleDrive.setOnClickListener {
                try {
                    startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(evGoogleDrive)))
                } catch (e: Exception) {
                    Toast.makeText(this, "Cannot open link: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        } else {
            btnGoogleDrive.text = "No Google Drive link available"
            btnGoogleDrive.isEnabled = false
        }
    }

    private fun getEventId(): String? {
        val rawId = when (event) {
            is Event -> (event as Event).id
            is EventEntity -> (event as EventEntity).id
            else -> null
        }
        // Strip "offline_" prefix if present - events synced to Firestore use the original ID
        val cleanId = rawId?.removePrefix("offline_")
        Log.d("EventDetails", "Raw event ID: $rawId, Clean event ID: $cleanId")
        return cleanId
    }

    private fun checkAndLoadSyncedEvent(eventId: String) {
        Log.d("EventDetails", "Checking for synced event with ID: $eventId")
        // Check if event exists in Firestore (it might have been synced)
        db.collection("events").document(eventId).get()
            .addOnSuccessListener { doc ->
                if (doc.exists()) {
                    Log.d("EventDetails", "Event found in Firestore, loading synced data")
                    // Event exists in Firestore, load the latest data
                    val syncedEvent = Event(
                        id = doc.id,
                        title = doc.getString("title") ?: "",
                        description = doc.getString("description") ?: "",
                        date = doc.getString("date") ?: "",
                        time = doc.getString("time") ?: "",
                        location = doc.getString("location") ?: "",
                        category = doc.getString("category") ?: "",
                        createdAt = doc.getString("createdAt") ?: "",
                        hostEmail = doc.getString("hostEmail") ?: "",
                        hostUserId = doc.getString("hostUserId") ?: "",
                        attendeeCount = (doc.getLong("attendeeCount") ?: 0).toInt(),
                        googleDriveLink = doc.getString("googleDriveLink") ?: "",
                        dietaryRequirements = doc.get("dietaryRequirements") as? List<String> ?: emptyList(),
                        musicSuggestions = doc.get("musicSuggestions") as? List<String> ?: emptyList(),
                        pollResponses = doc.get("pollResponses") as? Map<String, Any> ?: emptyMap()
                    )
                    // Update the event with synced data
                    event = syncedEvent
                    bindEventData()
                    // Load guests and preferences only if event exists in Firestore
                    loadGuestsAndPreferences(eventId)
                } else {
                    Log.d("EventDetails", "Event not found in Firestore (might not be synced yet)")
                    // Event doesn't exist in Firestore yet - don't try to load guests
                    // The event details are already displayed from the local data
                }
            }
            .addOnFailureListener { e ->
                Log.e("EventDetails", "Error checking Firestore for event: ${e.message}", e)
                // If there's an error, don't try to load guests
                // The event details are already displayed from the local data
            }
    }

    private fun loadGuestsAndPreferences(eventId: String) {
        val eventRef = db.collection("events").document(eventId)
        val rsvpsRef = eventRef.collection("rsvps")

        val currentRsvps = mutableMapOf<String, Guest>()
        var pollResponses: Map<String, Map<String, Any>> = emptyMap()

        // Listen to event doc for pollResponses
        eventRef.addSnapshotListener { snapshot, error ->
            if (error != null || snapshot == null || !snapshot.exists()) return@addSnapshotListener
            pollResponses = snapshot.get("pollResponses") as? Map<String, Map<String, Any>> ?: emptyMap()
            updateGuestList(currentRsvps, pollResponses)
        }

        // Listen to RSVPs
        rsvpsRef.addSnapshotListener { snapshot, error ->
            if (error != null || snapshot == null) return@addSnapshotListener

            for (doc in snapshot.documents) {
                val guestId = doc.id
                val userName = doc.getString("userName") ?: "Anonymous"
                val status = doc.getString("status") ?: "Not set"

                val pollData = pollResponses[guestId]
                val dietary = pollData?.get("dietaryChoice") as? String ?: "Not specified"
                val music = pollData?.get("musicChoice") as? String ?: "Not specified"

                currentRsvps[guestId] = Guest(guestId, userName, status, dietary, music)
            }

            // Remove guests no longer in RSVPs
            val existingIds = snapshot.documents.map { it.id }
            currentRsvps.keys.retainAll(existingIds)
            updateGuestList(currentRsvps, pollResponses)
        }
    }

    private fun updateGuestList(
        rsvps: Map<String, Guest>,
        pollResponses: Map<String, Map<String, Any>>
    ) {
        val guests = rsvps.map { (id, guest) ->
            val pollData = pollResponses[id]
            val dietary = pollData?.get("dietaryChoice") as? String ?: guest.dietaryChoice
            val music = pollData?.get("musicChoice") as? String ?: guest.musicChoice
            guest.copy(dietaryChoice = dietary, musicChoice = music)
        }

        guestList.clear()
        guestList.addAll(guests)
        attendeeCountText.text = "Attendees: ${guestList.count { it.status == "going" }}"
        guestAdapter.notifyDataSetChanged()
    }
}
