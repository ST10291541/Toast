package vcmsa.projects.toastapplication

import android.content.Intent
import android.os.Bundle
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import vcmsa.projects.toastapplication.local.EventRepo

class DashboardActivity : AppCompatActivity() {

    private lateinit var tvUserName: TextView
    private lateinit var tvEventCount: TextView
    private lateinit var imgProfile: ImageView
    private lateinit var auth: FirebaseAuth

    private lateinit var eventsRecyclerView: RecyclerView
    private lateinit var eventAdapter: EventAdapter

    private var allEvents: List<Event> = listOf()
    private var filteredEvents: MutableList<Event> = mutableListOf()
    private val eventGuestMap: MutableMap<String, List<Guest>> = mutableMapOf()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_dashboard)

        val bottomNav = findViewById<LinearLayout>(R.id.bottomNav)
        ViewCompat.setOnApplyWindowInsetsListener(bottomNav) { view, insets ->
            view.setPadding(
                view.paddingLeft,
                view.paddingTop,
                view.paddingRight,
                insets.systemWindowInsetBottom
            )
            insets
        }

        auth = FirebaseAuth.getInstance()
        tvUserName = findViewById(R.id.tvUserName)
        tvEventCount = findViewById(R.id.tvEventCount)
        imgProfile = findViewById(R.id.imgProfile)

        val db = FirebaseFirestore.getInstance()

        //  Load user profile info
        auth.currentUser?.let { user ->
            val userId = user.uid
            val userDocRef = db.collection("users").document(userId)

            userDocRef.get().addOnSuccessListener { document ->
                if (document != null && document.exists()) {
                    val firstName = document.getString("firstName")
                    val profileImageUrl = document.getString("profileImageUri")

                    tvUserName.text = firstName ?: user.email ?: getString(R.string.user_fallback)

                    if (!profileImageUrl.isNullOrEmpty()) {
                        Glide.with(this).load(profileImageUrl).circleCrop().into(imgProfile)
                    } else {
                        user.photoUrl?.let {
                            Glide.with(this).load(it).circleCrop().into(imgProfile)
                        }
                    }
                } else {
                    tvUserName.text = user.email ?: getString(R.string.user_fallback)
                    user.photoUrl?.let {
                        Glide.with(this).load(it).circleCrop().into(imgProfile)
                    }
                }
            }.addOnFailureListener {
                tvUserName.text = user.email ?: getString(R.string.user_fallback)
                user.photoUrl?.let {
                    Glide.with(this).load(it).circleCrop().into(imgProfile)
                }
            }
        } ?: run {
            tvUserName.text = getString(R.string.guest)
        }

        // Navigation buttons
        findViewById<LinearLayout>(R.id.navHome).setOnClickListener {
            startActivity(Intent(this, DashboardActivity::class.java))
        }
        findViewById<LinearLayout>(R.id.navEvents).setOnClickListener {
            startActivity(Intent(this, MyEventsActivity::class.java))
        }
        findViewById<LinearLayout>(R.id.navProfile).setOnClickListener {
            startActivity(Intent(this, ProfileSettingsActivity::class.java))
        }

        findViewById<LinearLayout>(R.id.btnCreateEvent).setOnClickListener {
            startActivity(Intent(this, CreateEventActivity::class.java))
        }
        findViewById<LinearLayout>(R.id.btnMyEvents).setOnClickListener {
            startActivity(Intent(this, MyEventsActivity::class.java))
        }
        findViewById<LinearLayout>(R.id.btnMyProfile).setOnClickListener {
            startActivity(Intent(this, ProfileSettingsActivity::class.java))
        }

        // RecyclerView setup
        eventsRecyclerView = findViewById(R.id.recyclerEvents)
        eventsRecyclerView.layoutManager = LinearLayoutManager(this)

        eventAdapter = EventAdapter(
            eventList = filteredEvents,
            onItemClick = { event ->
                val intent = Intent(this, EventDetailsActivity::class.java)
                intent.putExtra("event", event)
                startActivity(intent)
            },
            eventGuestMap = eventGuestMap
        )
        eventsRecyclerView.adapter = eventAdapter

        // Category filters
        findViewById<LinearLayout>(R.id.chipWedding).setOnClickListener { filterEventsByCategory("Wedding") }
        findViewById<LinearLayout>(R.id.chipParty).setOnClickListener { filterEventsByCategory("Party") }
        findViewById<LinearLayout>(R.id.chipFood).setOnClickListener { filterEventsByCategory("Food") }
        findViewById<LinearLayout>(R.id.chipArt).setOnClickListener { filterEventsByCategory("Art") }
        findViewById<LinearLayout>(R.id.chipMeet).setOnClickListener { filterEventsByCategory("Meet-Up") }
        findViewById<LinearLayout>(R.id.chipGeneral).setOnClickListener { filterEventsByCategory("General") }

        // Load events from Firestore
        loadEvents()
    }

    override fun onResume() {
        super.onResume()
        refreshUserProfile()
        loadEvents() // Reload events when returning to dashboard
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

                        tvUserName.text = firstName ?: user.email ?: "User"

                        if (!profileImageUrl.isNullOrEmpty()) {
                            Glide.with(this).load(profileImageUrl).circleCrop().into(imgProfile)
                        } else {
                            user.photoUrl?.let {
                                Glide.with(this).load(it).circleCrop().into(imgProfile)
                            }
                        }
                    } else {
                        tvUserName.text = user.email ?: "User"
                        user.photoUrl?.let {
                            Glide.with(this).load(it).circleCrop().into(imgProfile)
                        }
                    }
                }
                .addOnFailureListener {
                    tvUserName.text = user.email ?: "User"
                    user.photoUrl?.let {
                        Glide.with(this).load(it).circleCrop().into(imgProfile)
                    }
                }
        }
    }

    private fun loadEvents() {
        val db = FirebaseFirestore.getInstance()
        val currentUserId = auth.currentUser?.uid ?: return

        // Clear previous lists
        allEvents = emptyList()
        filteredEvents.clear()
        eventGuestMap.clear()

        // Step 1: Load offline events first
        CoroutineScope(Dispatchers.IO).launch {
            val offlineEvents = getOfflineEvents().map { it.copy(id = "offline_${it.id}") }

            runOnUiThread {
                allEvents = offlineEvents
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

                    // Merge offline + online, avoiding duplicates
                    val mergedEvents = (offlineEvents + onlineEvents)
                        .distinctBy { it.id.removePrefix("offline_") }

                    allEvents = mergedEvents
                    filteredEvents.clear()
                    filteredEvents.addAll(mergedEvents)
                    eventAdapter.updateData(filteredEvents, eventGuestMap)
                    updateEventCount()

                    // Load guest RSVPs for online events
                    loadGuestDataForEvents(onlineEvents)
                }
                .addOnFailureListener { exception ->
                    Toast.makeText(this@DashboardActivity, "Error loading events: ${exception.message}", Toast.LENGTH_LONG).show()
                }
        }
    }

    private fun loadGuestDataForEvents(events: List<Event>) {
        val db = FirebaseFirestore.getInstance()
        for (event in events) {
            db.collection("events").document(event.id).collection("rsvps")
                .get()
                .addOnSuccessListener { snapshot ->
                    val guests = snapshot.documents.map { doc ->
                        Guest(
                            guestId = doc.id,
                            userName = doc.getString("userName") ?: "Anonymous",
                            status = doc.getString("status") ?: "Not set",
                            dietaryChoice = doc.getString("dietaryChoice") ?: "Not specified",
                            musicChoice = doc.getString("musicChoice") ?: "Not specified"
                        )
                    }
                    eventGuestMap[event.id] = guests
                    eventAdapter.updateData(filteredEvents, eventGuestMap)
                    updateEventCount()
                }
        }
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
        eventAdapter.updateData(filteredEvents, eventGuestMap)
        updateEventCount()
    }

    private fun updateEventCount() {
        val count = filteredEvents.size
        tvEventCount.text = "(${count} ${if (count == 1) "event" else "events"})"
    }
}