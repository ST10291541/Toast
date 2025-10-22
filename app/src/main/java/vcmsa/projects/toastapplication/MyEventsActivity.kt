package vcmsa.projects.toastapplication

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.dynamiclinks.ktx.dynamicLinks
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.ktx.Firebase

class MyEventsActivity : AppCompatActivity() {

    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseFirestore
    private lateinit var eventsRecyclerView: RecyclerView
    private lateinit var emptyState: View
    private lateinit var tvEventsCount: TextView
    private val eventsList = mutableListOf<Event>()
    private lateinit var adapter: EventAdapter
    private val eventGuestMap = mutableMapOf<String, List<Guest>>() // guest list per event

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_my_events)

        auth = FirebaseAuth.getInstance()
        db = FirebaseFirestore.getInstance()

        // Initialize views
        eventsRecyclerView = findViewById(R.id.eventsRecyclerView)
        emptyState = findViewById(R.id.emptyState)
        tvEventsCount = findViewById(R.id.tvEventsCount)

        // RecyclerView setup
        eventsRecyclerView.layoutManager = LinearLayoutManager(this)

        adapter = EventAdapter(
            eventList = eventsList,
            onItemClick = { event: Event ->
                val intent = Intent(this, EventDetailsActivity::class.java)
                intent.putExtra("event", event)
                startActivity(intent)
            },
            eventGuestMap = eventGuestMap
        )
        eventsRecyclerView.adapter = adapter

        // Load events from Firestore
        loadEvents()

        // Floating action button to create new events
        findViewById<FloatingActionButton>(R.id.fabCreateEvent).setOnClickListener {
            startActivity(Intent(this, CreateEventActivity::class.java))
        }

        // Back button
        findViewById<ImageButton>(R.id.btnBack).setOnClickListener {
            finish() // Just go back instead of starting new DashboardActivity
        }


        // Handle incoming dynamic links (RSVP links)
        handleDynamicLinks()
    }

    override fun onResume() {
        super.onResume()
        // Refresh events when returning to this activity
        loadEvents()
    }

    private fun loadEvents() {
        val currentUserId = auth.currentUser?.uid ?: return
        db.collection("events")
            .whereEqualTo("hostUserId", currentUserId)
            .get()
            .addOnSuccessListener { result ->
                eventsList.clear()
                eventGuestMap.clear()

                for (doc in result) {
                    val event = Event(
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
                    eventsList.add(event)
                    listenToGuests(event) // fetch RSVPs and preferences for display only
                }

                adapter.updateData(eventsList, eventGuestMap)
                updateEventsCount()
                updateEmptyState()
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "Error loading events: ${e.message}", Toast.LENGTH_SHORT).show()
                updateEmptyState()
            }
    }

    private fun listenToGuests(event: Event) {
        val rsvpsRef = db.collection("events").document(event.id!!).collection("rsvps")
        val guestMap = mutableMapOf<String, Guest>()

        // Listen to RSVPs (read-only)
        rsvpsRef.addSnapshotListener { snapshot, e ->
            if (e != null || snapshot == null) return@addSnapshotListener

            for (doc in snapshot.documents) {
                val guestId = doc.id
                val userName = doc.getString("userName") ?: "Anonymous"
                val status = doc.getString("status") ?: "Not set"
                val dietaryChoice = doc.getString("dietaryChoice") ?: "Not specified"
                val musicChoice = doc.getString("musicChoice") ?: "Not specified"

                guestMap[guestId] = Guest(
                    guestId = guestId,
                    userName = userName,
                    status = status,
                    dietaryChoice = dietaryChoice,
                    musicChoice = musicChoice
                )
            }

            eventGuestMap[event.id!!] = guestMap.values.toList()

            // Update attendee count for this event
            val goingCount = guestMap.values.count { it.status.equals("going", ignoreCase = true) }
            eventsList.find { it.id == event.id }?.attendeeCount = goingCount

            adapter.updateData(eventsList, eventGuestMap)
            updateEventsCount()
        }
    }

    private fun updateEventsCount() {
        tvEventsCount.text = "All Events (${eventsList.size})"
    }

    private fun updateEmptyState() {
        if (eventsList.isEmpty()) {
            emptyState.visibility = View.VISIBLE
            eventsRecyclerView.visibility = View.GONE
        } else {
            emptyState.visibility = View.GONE
            eventsRecyclerView.visibility = View.VISIBLE
        }
    }



    // Handle empty state button click
    fun onCreateEventClick(view: View) {
        startActivity(Intent(this, CreateEventActivity::class.java))
    }

    private fun handleDynamicLinks() {
        Firebase.dynamicLinks.getDynamicLink(intent)
            .addOnSuccessListener { pendingLinkData ->
                val deepLink: Uri? = pendingLinkData?.link
                deepLink?.let {
                    val eventId = it.lastPathSegment
                    openEventDetails(eventId)
                }
            }
            .addOnFailureListener {
                // Silent failure - not critical for main functionality
            }
    }

    private fun openEventDetails(eventId: String?) {
        if (eventId == null) return
        db.collection("events").document(eventId).get()
            .addOnSuccessListener { doc ->
                val event = Event(
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

                val intent = Intent(this, EventDetailsActivity::class.java)
                intent.putExtra("event", event)
                startActivity(intent)
            }
            .addOnFailureListener {
                Toast.makeText(this, "Failed to load event", Toast.LENGTH_SHORT).show()
            }
    }
}