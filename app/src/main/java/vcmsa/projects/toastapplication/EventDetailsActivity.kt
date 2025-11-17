package vcmsa.projects.toastapplication

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import vcmsa.projects.toastapplication.databinding.ActivityEventDetailsBinding
import vcmsa.projects.toastapplication.local.EventEntity

class EventDetailsActivity : AppCompatActivity() {

    private lateinit var binding: ActivityEventDetailsBinding
    private val auth = FirebaseAuth.getInstance()
    private val db = FirebaseFirestore.getInstance()
    private var event: Any? = null // can be Event or EventEntity
    private val guestList = mutableListOf<Guest>()
    private lateinit var guestAdapter: GuestAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Initialize binding
        binding = ActivityEventDetailsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.btnBack.setOnClickListener { finish() }

        guestAdapter = GuestAdapter(guestList)
        binding.guestsRecyclerView.layoutManager = LinearLayoutManager(this)
        binding.guestsRecyclerView.adapter = guestAdapter

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

        binding.eventTitle.text = evTitle
        binding.aboutDescription.text = evDescription
        binding.eventDate.text = evDate
        binding.eventTime.text = evTime
        binding.eventLocation.text = evLocation
        binding.categoryText.text = evCategory

        // Update attendee count
        val attendeeCount = when (event) {
            is Event -> (event as Event).attendeeCount
            is EventEntity -> 0 // Offline events don't have attendee count
            else -> 0
        }
        binding.attendeeCount.text = "$attendeeCount attendees"

        // Update guest count
        binding.guestCount.text = "${guestList.size} guests"

        // Setup Google Drive button
        if (!evGoogleDrive.isNullOrBlank()) {
            binding.btnGoogleDrive.setOnClickListener {
                try {
                    startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(evGoogleDrive)))
                } catch (e: Exception) {
                    Toast.makeText(this, "Cannot open link: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        } else {
            binding.btnGoogleDrive.isEnabled = false
            binding.btnGoogleDrive.alpha = 0.5f
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

        // Update attendee and guest counts
        val goingCount = guestList.count { it.status.equals("going", ignoreCase = true) }
        binding.attendeeCount.text = "$goingCount attendees"
        binding.guestCount.text = "${guestList.size} guests"

        guestAdapter.notifyDataSetChanged()
    }
}