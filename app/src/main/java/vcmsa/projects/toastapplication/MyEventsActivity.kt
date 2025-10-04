package vcmsa.projects.toastapplication

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.dynamiclinks.ktx.dynamicLinks
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.ktx.Firebase

class MyEventsActivity : AppCompatActivity() {

    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseFirestore
    private lateinit var eventsRecyclerView: RecyclerView
    private val eventsList = mutableListOf<Event>()
    private lateinit var adapter: EventAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_my_events)

        auth = FirebaseAuth.getInstance()
        db = FirebaseFirestore.getInstance()

        // RecyclerView setup
        eventsRecyclerView = findViewById(R.id.eventsRecyclerView)
        eventsRecyclerView.layoutManager = LinearLayoutManager(this)
        adapter = EventAdapter(eventsList) { event ->
            val intent = Intent(this, EventDetailsActivity::class.java)
            intent.putExtra("event", event)
            startActivity(intent)
        }
        eventsRecyclerView.adapter = adapter

        // Load events from Firestore
        loadEvents()

        // Floating action button to create new events
        findViewById<ExtendedFloatingActionButton>(R.id.fabCreateEvent).setOnClickListener {
            startActivity(Intent(this, CreateEventActivity::class.java))
        }

        // Handle incoming dynamic links (RSVP links)
        handleDynamicLinks()
    }

    private fun loadEvents() {
        val currentUserId = auth.currentUser?.uid ?: return
        db.collection("events")
            .whereEqualTo("hostUserId", currentUserId)
            .get()
            .addOnSuccessListener { result ->
                eventsList.clear()
                for (doc in result) {
                    val event = doc.toObject(Event::class.java).apply { id = doc.id }
                    eventsList.add(event)
                }
                adapter.notifyDataSetChanged()
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "Error loading events: ${e.message}", Toast.LENGTH_SHORT).show()
            }
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
                Toast.makeText(this, "Failed to handle dynamic link", Toast.LENGTH_SHORT).show()
            }
    }

    private fun openEventDetails(eventId: String?) {
        if (eventId == null) return
        db.collection("events").document(eventId).get()
            .addOnSuccessListener { doc ->
                val event = doc.toObject(Event::class.java)?.apply { id = doc.id } ?: return@addOnSuccessListener
                val intent = Intent(this, EventDetailsActivity::class.java)
                intent.putExtra("event", event)
                startActivity(intent)
            }
            .addOnFailureListener {
                Toast.makeText(this, "Failed to load event", Toast.LENGTH_SHORT).show()
            }
    }
}
