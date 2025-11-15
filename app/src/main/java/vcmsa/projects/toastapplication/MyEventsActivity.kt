package vcmsa.projects.toastapplication

import android.app.Activity
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
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.ktx.Firebase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import vcmsa.projects.toastapplication.local.EventRepo

class MyEventsActivity : AppCompatActivity() {

    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseFirestore
    private lateinit var eventsRecyclerView: RecyclerView
    private lateinit var emptyState: View
    private lateinit var tvEventsCount: TextView
    private val eventsList = mutableListOf<Event>()
    private lateinit var adapter: EventAdapter
    private val eventGuestMap = mutableMapOf<String, List<Guest>>()
    private val guestListeners = mutableMapOf<String, ListenerRegistration>()
    private var shouldRefreshEvents = false

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
            onItemClick = { event ->
                val intent = Intent(this, EventDetailsActivity::class.java)
                intent.putExtra("event", event)
                startActivity(intent)
            },
            eventGuestMap = eventGuestMap
        )
        eventsRecyclerView.adapter = adapter

        // Load events once
        loadEvents()

        // FAB to create events
        findViewById<FloatingActionButton>(R.id.fabCreateEvent).setOnClickListener {
            val intent = Intent(this, CreateEventActivity::class.java)
            startActivityForResult(intent, CREATE_EVENT_REQUEST)
        }

        // Back button
        findViewById<ImageButton>(R.id.btnBack).setOnClickListener { finish() }

        // Handle dynamic links
        handleDynamicLinks()
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == CREATE_EVENT_REQUEST && resultCode == Activity.RESULT_OK) {
            loadEvents() // Refresh after creating a new event
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        guestListeners.values.forEach { it.remove() } // remove all listeners
    }

    private fun loadEvents() {
        val currentUserId = auth.currentUser?.uid ?: return

        // Clear lists first
        eventsList.clear()
        eventGuestMap.clear()
        guestListeners.values.forEach { it.remove() }
        guestListeners.clear()

        // Step 1: Load offline events immediately
        CoroutineScope(Dispatchers.IO).launch {
            val offlineEvents = getOfflineEvents().map { it.copy(id = "offline_${it.id}") }

            runOnUiThread {
                eventsList.addAll(offlineEvents)
                adapter.updateData(eventsList, eventGuestMap)
                updateEventsCount()
                updateEmptyState()
            }

            // Step 2: Fetch online events
            db.collection("events")
                .whereEqualTo("hostUserId", currentUserId)
                .get()
                .addOnSuccessListener { result ->
                    val onlineEvents = result.documents.map { doc ->
                        Event(
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
                    }

                    // Remove any offline events that have the same ID as online events
                    val offlineIds = offlineEvents.map { it.id }
                    val mergedEvents = (offlineEvents + onlineEvents)
                        .distinctBy { it.id.removePrefix("offline_") } // treat offline ID as same if online exists

                    eventsList.clear()
                    eventsList.addAll(mergedEvents)

                    // Listen to RSVPs for online events
                    onlineEvents.forEach { listenToGuests(it) }

                    adapter.updateData(eventsList, eventGuestMap)
                    updateEventsCount()
                    updateEmptyState()
                }
                .addOnFailureListener { e ->
                    Toast.makeText(this@MyEventsActivity, "Error loading online events: ${e.message}", Toast.LENGTH_SHORT).show()
                }
        }
    }

    private fun listenToGuests(event: Event) {
        guestListeners[event.id!!]?.remove()

        val rsvpsRef = db.collection("events").document(event.id).collection("rsvps")
        val guestMap = mutableMapOf<String, Guest>()

        val listener = rsvpsRef.addSnapshotListener { snapshot, e ->
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
            val goingCount = guestMap.values.count { it.status.equals("going", ignoreCase = true) }
            eventsList.find { it.id == event.id }?.attendeeCount = goingCount

            adapter.updateData(eventsList, eventGuestMap)
            updateEventsCount()
        }

        guestListeners[event.id!!] = listener
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

    fun onCreateEventClick(view: View) {
        startActivity(Intent(this, CreateEventActivity::class.java))
    }

    private fun handleDynamicLinks() {
        Firebase.dynamicLinks.getDynamicLink(intent)
            .addOnSuccessListener { pendingLinkData ->
                val deepLink: Uri? = pendingLinkData?.link
                deepLink?.lastPathSegment?.let { openEventDetails(it) }
            }
            .addOnFailureListener {
                // silent failure
            }
    }

    private fun openEventDetails(eventId: String) {
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

    private suspend fun getOfflineEvents(): List<Event> {
        val eventEntities = EventRepo(this).getLocalEvents()
        return eventEntities.map { entity ->
            Event(
                id = entity.id,
                title = entity.title,
                description = entity.description,
                date = entity.date,
                time = entity.time,
                location = entity.location,
                category = entity.category,
                createdAt = entity.createdAt.toString(),
                hostEmail = "",
                hostUserId = entity.hostUserId,
                attendeeCount = 0,
                googleDriveLink = entity.googleDriveLink,
                dietaryRequirements = entity.dietaryRequirements.split(","),
                musicSuggestions = entity.musicSuggestions.split(","),
                pollResponses = emptyMap()
            )
        }
    }

    companion object {
        private const val CREATE_EVENT_REQUEST = 1001
    }
}
