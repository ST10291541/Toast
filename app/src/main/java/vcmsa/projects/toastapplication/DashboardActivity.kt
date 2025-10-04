package vcmsa.projects.toastapplication

import android.content.Intent
import android.os.Bundle
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

class DashboardActivity : AppCompatActivity() {

    private lateinit var tvUserName: TextView
    private lateinit var imgProfile: ImageView
    private lateinit var auth: FirebaseAuth

    private lateinit var eventsRecyclerView: RecyclerView
    private lateinit var eventAdapter: EventAdapter

    private var allEvents: List<Event> = listOf()
    private var filteredEvents: MutableList<Event> = mutableListOf()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_dashboard)

        auth = FirebaseAuth.getInstance()
        tvUserName = findViewById(R.id.tvUserName)
        imgProfile = findViewById(R.id.imgProfile)

        val auth = FirebaseAuth.getInstance()
        val db = FirebaseFirestore.getInstance()

        auth.currentUser?.let { user ->
            val userId = user.uid

            // Reference to user's profile in Firestore
            val userDocRef = db.collection("users").document(userId)

            userDocRef.get().addOnSuccessListener { document ->
                if (document != null && document.exists()) {
                    // Get firstName + image
                    val firstName = document.getString("firstName")
                    val profileImageUrl = document.getString("profileImageUri")

                    // Set name
                    if (!firstName.isNullOrEmpty()) {
                        tvUserName.text = firstName
                    } else {
                        // Fallback: if no firstName, show email
                        tvUserName.text = user.email ?: "User"
                    }

                    // Set image
                    if (!profileImageUrl.isNullOrEmpty()) {
                        Glide.with(this)
                            .load(profileImageUrl)
                            .circleCrop()
                            .into(imgProfile)
                    } else {
                        // Fallback: use FirebaseAuth photoUrl if exists
                        user.photoUrl?.let {
                            Glide.with(this).load(it).circleCrop().into(imgProfile)
                        }
                    }
                } else {
                    // No profile in DB → fallback to email
                    tvUserName.text = user.email ?: "User"
                    user.photoUrl?.let {
                        Glide.with(this).load(it).circleCrop().into(imgProfile)
                    }
                }
            }.addOnFailureListener {
                // On error → fallback
                tvUserName.text = user.email ?: "User"
                user.photoUrl?.let {
                    Glide.with(this).load(it).circleCrop().into(imgProfile)
                }
            }
        } ?: run {
            // Not logged in
            tvUserName.text = "Guest"
        }

        // Bottom navigation
        findViewById<LinearLayout>(R.id.navHome).setOnClickListener {
            startActivity(Intent(this, DashboardActivity::class.java))
        }
        findViewById<LinearLayout>(R.id.navEvents).setOnClickListener {
            startActivity(Intent(this, MyEventsActivity::class.java))
        }
        findViewById<LinearLayout>(R.id.navProfile).setOnClickListener {
            startActivity(Intent(this, ProfileSettingsActivity::class.java))
        }

        // More actions
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
        eventAdapter = EventAdapter(filteredEvents) { event ->
            val intent = Intent(this, EventDetailsActivity::class.java)
            intent.putExtra("event", event)
            startActivity(intent)
        }
        eventsRecyclerView.adapter = eventAdapter

        // Category chips
        findViewById<LinearLayout>(R.id.chipWedding).setOnClickListener { filterEventsByCategory("Wedding") }
        findViewById<LinearLayout>(R.id.chipParty).setOnClickListener { filterEventsByCategory("Party") }
        findViewById<LinearLayout>(R.id.chipFood).setOnClickListener { filterEventsByCategory("Food") }
        findViewById<LinearLayout>(R.id.chipArt).setOnClickListener { filterEventsByCategory("Art") }
        findViewById<LinearLayout>(R.id.chipMeet).setOnClickListener { filterEventsByCategory("Meet-Up") }
        findViewById<LinearLayout>(R.id.chipGeneral).setOnClickListener { filterEventsByCategory("General") }

        // Load events from Firestore
        loadEvents()
    }

    private fun loadEvents() {
        val db = FirebaseFirestore.getInstance()
        val currentUserId = auth.currentUser?.uid ?: return

        db.collection("events")
            .whereEqualTo("hostUserId", currentUserId) // Only events created by this user
            .get()
            .addOnSuccessListener { result ->
                allEvents = result.documents.map { doc ->
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

                filteredEvents.clear()
                filteredEvents.addAll(allEvents)
                eventAdapter.updateData(filteredEvents)
            }
            .addOnFailureListener { exception ->
                Toast.makeText(this, "Failed to load events: ${exception.message}", Toast.LENGTH_LONG).show()
            }
    }

    private fun filterEventsByCategory(category: String) {
        filteredEvents.clear()
        filteredEvents.addAll(allEvents.filter { it.category == category })
        eventAdapter.updateData(filteredEvents)
    }
}
