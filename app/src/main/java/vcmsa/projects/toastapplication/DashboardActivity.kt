package vcmsa.projects.toastapplication

import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.os.Bundle
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.recyclerview.widget.LinearLayoutManager
import com.bumptech.glide.Glide
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import vcmsa.projects.toastapplication.databinding.ActivityDashboardBinding
import vcmsa.projects.toastapplication.local.EventRepo

class DashboardActivity : AppCompatActivity() {

    private lateinit var binding: ActivityDashboardBinding
    private lateinit var auth: FirebaseAuth

    private lateinit var eventAdapter: EventAdapter

    private var allEvents: MutableList<Event> = mutableListOf()
    private var filteredEvents: MutableList<Event> = mutableListOf()
    private val eventGuestMap: MutableMap<String, List<Guest>> = mutableMapOf()
    private val guestListeners: MutableMap<String, ListenerRegistration> = mutableMapOf()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Initialize binding
        binding = ActivityDashboardBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Handle window insets
        ViewCompat.setOnApplyWindowInsetsListener(binding.bottomNav) { view, insets ->
            view.setPadding(
                view.paddingLeft,
                view.paddingTop,
                view.paddingRight,
                insets.systemWindowInsetBottom
            )
            insets
        }

        auth = FirebaseAuth.getInstance()

        // Load user profile info
        loadUserProfile()

        // Navigation buttons
        binding.navHome.setOnClickListener {
            // Already on dashboard, do nothing or refresh
            refreshUserProfile()
            syncOfflineEventsAndLoad()
        }
        binding.navEvents.setOnClickListener {
            startActivity(Intent(this, MyEventsActivity::class.java))
        }
        binding.navProfile.setOnClickListener {
            startActivity(Intent(this, ProfileSettingsActivity::class.java))
        }

        // Action buttons
        binding.btnCreateEvent.setOnClickListener {
            startActivity(Intent(this, CreateEventActivity::class.java))
        }
        binding.btnMyEvents.setOnClickListener {
            startActivity(Intent(this, MyEventsActivity::class.java))
        }
        binding.btnMyProfile.setOnClickListener {
            startActivity(Intent(this, ProfileSettingsActivity::class.java))
        }

        // RecyclerView setup
        binding.recyclerEvents.layoutManager = LinearLayoutManager(this)

        eventAdapter = EventAdapter(
            eventList = filteredEvents,
            onItemClick = { event ->
                val intent = Intent(this, EventDetailsActivity::class.java)
                intent.putExtra("event", event)
                startActivity(intent)
            },
            eventGuestMap = eventGuestMap
        )
        binding.recyclerEvents.adapter = eventAdapter

        // Category filters
        binding.chipWedding.setOnClickListener { filterEventsByCategory("Wedding") }
        binding.chipParty.setOnClickListener { filterEventsByCategory("Party") }
        binding.chipFood.setOnClickListener { filterEventsByCategory("Food") }
        binding.chipArt.setOnClickListener { filterEventsByCategory("Art") }
        binding.chipMeet.setOnClickListener { filterEventsByCategory("Meet-Up") }
        binding.chipGeneral.setOnClickListener { filterEventsByCategory("General") }

        // Sync offline events first if online, then load events
        syncOfflineEventsAndLoad()
    }

    override fun onResume() {
        super.onResume()
        refreshUserProfile()
        // Sync offline events first if online, then refresh events
        syncOfflineEventsAndLoad()
    }

    override fun onDestroy() {
        super.onDestroy()
        guestListeners.values.forEach { it.remove() } // remove all listeners
    }

    private fun loadUserProfile() {
        val db = FirebaseFirestore.getInstance()
        auth.currentUser?.let { user ->
            val userId = user.uid
            val userDocRef = db.collection("users").document(userId)

            userDocRef.get().addOnSuccessListener { document ->
                if (document != null && document.exists()) {
                    val firstName = document.getString("firstName")
                    val profileImageUrl = document.getString("profileImageUri")

                    binding.tvUserName.text = firstName ?: user.email ?: getString(R.string.user_fallback)

                    if (!profileImageUrl.isNullOrEmpty()) {
                        Glide.with(this).load(profileImageUrl).circleCrop().into(binding.imgProfile)
                    } else {
                        user.photoUrl?.let {
                            Glide.with(this).load(it).circleCrop().into(binding.imgProfile)
                        }
                    }
                } else {
                    binding.tvUserName.text = user.email ?: getString(R.string.user_fallback)
                    user.photoUrl?.let {
                        Glide.with(this).load(it).circleCrop().into(binding.imgProfile)
                    }
                }
            }.addOnFailureListener {
                binding.tvUserName.text = user.email ?: getString(R.string.user_fallback)
                user.photoUrl?.let {
                    Glide.with(this).load(it).circleCrop().into(binding.imgProfile)
                }
            }
        } ?: run {
            binding.tvUserName.text = getString(R.string.guest)
        }
    }

    private fun refreshUserProfile() {
        val db = FirebaseFirestore.getInstance()
        auth.currentUser?.let { user ->
            val userId = user.uid
            db.collection("users").document(userId).get()
                .addOnSuccessListener { document ->
                    if (document != null && document.exists()) {
                        val firstName = document.getString("firstName")
                        val profileImageUrl = document.getString("profileImageUri")

                        binding.tvUserName.text = firstName ?: user.email ?: "User"

                        if (!profileImageUrl.isNullOrEmpty()) {
                            Glide.with(this).load(profileImageUrl).circleCrop().into(binding.imgProfile)
                        } else {
                            user.photoUrl?.let {
                                Glide.with(this).load(it).circleCrop().into(binding.imgProfile)
                            }
                        }
                    } else {
                        binding.tvUserName.text = user.email ?: "User"
                        user.photoUrl?.let {
                            Glide.with(this).load(it).circleCrop().into(binding.imgProfile)
                        }
                    }
                }
                .addOnFailureListener {
                    binding.tvUserName.text = user.email ?: "User"
                    user.photoUrl?.let {
                        Glide.with(this).load(it).circleCrop().into(binding.imgProfile)
                    }
                }
        }
    }

    private fun loadEvents() {
        val db = FirebaseFirestore.getInstance()
        val currentUserId = auth.currentUser?.uid ?: return

        // Clear previous lists
        allEvents.clear()
        filteredEvents.clear()
        eventGuestMap.clear()
        guestListeners.values.forEach { it.remove() }
        guestListeners.clear()

        // Step 1: Load offline events first
        CoroutineScope(Dispatchers.IO).launch {
            val offlineEvents = getOfflineEvents().map { it.copy(id = "offline_${it.id}") }

            runOnUiThread {
                allEvents.clear()
                allEvents.addAll(offlineEvents)
                filteredEvents.clear()
                filteredEvents.addAll(allEvents)
                eventAdapter.updateData(filteredEvents, eventGuestMap)
                updateEventCount()
            }

            // Step 2: Fetch online events from Firestore
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

                    // Merge offline + online, preferring online events (they have the correct ID)
                    // Put online events first so they're kept when using distinctBy
                    val mergedEvents = (onlineEvents + offlineEvents)
                        .distinctBy { it.id.removePrefix("offline_") }

                    allEvents.clear()
                    allEvents.addAll(mergedEvents)
                    filteredEvents.clear()
                    filteredEvents.addAll(mergedEvents)
                    eventAdapter.updateData(filteredEvents, eventGuestMap)
                    updateEventCount()

                    // Listen to RSVPs for online events (real-time updates)
                    onlineEvents.forEach { listenToGuests(it) }
                }
                .addOnFailureListener { exception ->
                    Toast.makeText(this@DashboardActivity, "Error loading events: ${exception.message}", Toast.LENGTH_LONG).show()
                }
        }
    }

    private fun listenToGuests(event: Event) {
        guestListeners[event.id]?.remove()

        val db = FirebaseFirestore.getInstance()
        val rsvpsRef = db.collection("events").document(event.id).collection("rsvps")
        val guestMap = mutableMapOf<String, Guest>()

        val listener = rsvpsRef.addSnapshotListener { snapshot, e ->
            if (e != null || snapshot == null) return@addSnapshotListener

            guestMap.clear()
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

            // Update guest map - use the actual event ID (without prefix)
            eventGuestMap[event.id] = guestMap.values.toList()
            // Also update with "offline_" prefix if an event with that prefix exists
            val eventWithPrefix = allEvents.find { it.id.removePrefix("offline_") == event.id }
            if (eventWithPrefix != null && eventWithPrefix.id != event.id) {
                eventGuestMap[eventWithPrefix.id] = guestMap.values.toList()
            }

            // Calculate and update attendee count (count guests with status "going")
            val goingCount = guestMap.values.count { it.status.equals("going", ignoreCase = true) }

            // Update attendee count in allEvents list - check both with and without prefix
            allEvents.find { it.id == event.id || it.id.removePrefix("offline_") == event.id }?.attendeeCount = goingCount

            // Update attendee count in filteredEvents list - check both with and without prefix
            filteredEvents.find { it.id == event.id || it.id.removePrefix("offline_") == event.id }?.attendeeCount = goingCount

            // Refresh adapter to show updated attendee counts
            eventAdapter.updateData(filteredEvents, eventGuestMap)
            updateEventCount()
        }

        guestListeners[event.id] = listener
    }

    // Example function to get offline events
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

    private fun filterEventsByCategory(category: String) {
        filteredEvents.clear()
        if (category == "All") {
            filteredEvents.addAll(allEvents)
        } else {
            filteredEvents.addAll(allEvents.filter { it.category.equals(category, ignoreCase = true) })
        }
        // Make sure to preserve attendee counts when filtering
        eventAdapter.updateData(filteredEvents, eventGuestMap)
        updateEventCount()
    }

    private fun updateEventCount() {
        val count = filteredEvents.size
        binding.tvEventCount.text = "(${count} ${if (count == 1) "event" else "events"})"
    }

    private fun syncOfflineEventsAndLoad() {
        if (!isOnline()) {
            // If offline, still try to load events (might have cached data)
            loadEvents()
            return
        }

        val repo = EventRepo(this)
        CoroutineScope(Dispatchers.IO).launch {
            try {
                // Sync unsynced events to Firestore
                // This uses the same document ID (UUID), so it will overwrite if exists, not duplicate
                repo.syncEventsToFirestore()
                // After sync completes, reload events from Firestore
                // Since sync marks events as synced, they won't sync again
                runOnUiThread {
                    loadEvents()
                }
            } catch (e: Exception) {
                // If sync fails, still try to load existing events
                runOnUiThread {
                    loadEvents()
                }
            }
        }
    }

    private fun isOnline(): Boolean {
        val cm = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = cm.activeNetworkInfo
        return network != null && network.isConnected
    }
}