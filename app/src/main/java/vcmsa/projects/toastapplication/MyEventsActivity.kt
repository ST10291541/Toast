package vcmsa.projects.toastapplication

import android.app.Activity
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.NotificationCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.dynamiclinks.ktx.dynamicLinks
import com.google.firebase.firestore.DocumentChange
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
    private val rsvpListeners = mutableMapOf<String, ListenerRegistration>()

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

        // Start listening for RSVPs to user's events
        setupRsvpNotifications()

        // Sync offline events first if online, then refresh events
        syncOfflineEventsAndLoad()

        // FAB to create events
        findViewById<FloatingActionButton>(R.id.fabCreateEvent).setOnClickListener {
            val intent = Intent(this, CreateEventActivity::class.java)
            startActivityForResult(intent, CREATE_EVENT_REQUEST)
        }

        // Back button
        findViewById<ImageButton>(R.id.btnBack).setOnClickListener { finish() }

        // Handle dynamic links
        handleDynamicLinks()

        requestNotificationPermission()
    }

    // ========== SIMPLE RSVP NOTIFICATION SYSTEM ==========

    private fun setupRsvpNotifications() {
        val currentUserId = auth.currentUser?.uid ?: return

        // Listen for all events where current user is host
        db.collection("events")
            .whereEqualTo("hostUserId", currentUserId)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.e("RSVPNotifications", "Error listening to events: ${error.message}")
                    return@addSnapshotListener
                }

                // Set up listeners for each event's RSVPs
                snapshot?.documents?.forEach { doc ->
                    val eventId = doc.id
                    setupRsvpListenerForEvent(eventId)
                }

                // Clean up listeners for events user no longer hosts
                cleanupRsvpListeners(snapshot?.documents?.map { it.id } ?: emptyList())
            }
    }

    private fun setupRsvpListenerForEvent(eventId: String) {
        // Remove existing listener if any
        rsvpListeners[eventId]?.remove()

        val listener = db.collection("events").document(eventId)
            .collection("rsvps")
            .addSnapshotListener { snapshot, error ->
                if (error != null || snapshot == null) return@addSnapshotListener

                snapshot.documentChanges.forEach { change ->
                    if (change.type == DocumentChange.Type.ADDED) {
                        // New RSVP detected!
                        val guestName = change.document.getString("userName") ?: "Someone"
                        val rsvpStatus = change.document.getString("status") ?: "going"

                        Log.d("RSVPNotifications", "New RSVP: $guestName is $rsvpStatus for event $eventId")

                        // Get event title for the notification
                        db.collection("events").document(eventId).get()
                            .addOnSuccessListener { eventDoc ->
                                val eventTitle = eventDoc.getString("title") ?: "Your Event"
                                showSimpleNotification(guestName, rsvpStatus, eventTitle)
                            }
                            .addOnFailureListener {
                                // If we can't get event title, show generic notification
                                showSimpleNotification(guestName, rsvpStatus, "Your Event")
                            }
                    }
                }
            }

        rsvpListeners[eventId] = listener
    }

    private fun cleanupRsvpListeners(currentEventIds: List<String>) {
        rsvpListeners.keys.filter { !currentEventIds.contains(it) }.forEach { eventId ->
            rsvpListeners[eventId]?.remove()
            rsvpListeners.remove(eventId)
        }
    }

    private fun showSimpleNotification(guestName: String, rsvpStatus: String, eventTitle: String) {
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channelId = "rsvp_notifications"

        // Create notification channel
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "RSVP Notifications",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Notifications when guests RSVP to your events"
                enableVibration(true)
                setShowBadge(true)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            }
            notificationManager.createNotificationChannel(channel)
        }

        // Create intent to open the app
        val intent = Intent(this, MyEventsActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }

        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        // Build simple notification
        val notification = NotificationCompat.Builder(this, channelId)
            .setContentTitle("🎉 New RSVP for $eventTitle!")
            .setContentText("$guestName is $rsvpStatus")
            .setSmallIcon(R.drawable.toast_logo)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()

        // Show notification
        val notificationId = System.currentTimeMillis().toInt()
        notificationManager.notify(notificationId, notification)

        Log.d("RSVPNotification", "Notification shown: $guestName is $rsvpStatus")
    }

    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) !=
                PackageManager.PERMISSION_GRANTED) {

                // Show a rationale dialog first for better UX
                if (shouldShowRequestPermissionRationale(android.Manifest.permission.POST_NOTIFICATIONS)) {
                    AlertDialog.Builder(this)
                        .setTitle("Notification Permission")
                        .setMessage("This app needs notification permission to alert you when guests RSVP to your events.")
                        .setPositiveButton("OK") { _, _ ->
                            requestPermissions(
                                arrayOf(android.Manifest.permission.POST_NOTIFICATIONS),
                                123
                            )
                        }
                        .setNegativeButton("Cancel", null)
                        .show()
                } else {
                    requestPermissions(
                        arrayOf(android.Manifest.permission.POST_NOTIFICATIONS),
                        123
                    )
                }
            }
        }
    }


    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 123) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                Log.d("Notifications", "Notification permission granted")
            } else {
                Log.d("Notifications", "Notification permission denied")
            }
        }
    }

    // ========== EXISTING CODE (UNCHANGED) ==========

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == CREATE_EVENT_REQUEST && resultCode == Activity.RESULT_OK) {
            loadEvents() // Refresh after creating a new event
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        guestListeners.values.forEach { it.remove() } // remove all guest listeners
        rsvpListeners.values.forEach { it.remove() } // remove all RSVP listeners
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

                    // Merge offline + online, preferring online events (they have the correct ID)
                    // Put online events first so they're kept when using distinctBy
                    val mergedEvents = (onlineEvents + offlineEvents)
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

            // Update guest map - use the actual event ID (without prefix)
            eventGuestMap[event.id!!] = guestMap.values.toList()
            // Also update with "offline_" prefix if an event with that prefix exists
            val eventWithPrefix = eventsList.find { it.id.removePrefix("offline_") == event.id }
            if (eventWithPrefix != null && eventWithPrefix.id != event.id) {
                eventGuestMap[eventWithPrefix.id] = guestMap.values.toList()
            }

            val goingCount = guestMap.values.count { it.status.equals("going", ignoreCase = true) }
            // Update attendee count - check both with and without prefix
            eventsList.find { it.id == event.id || it.id.removePrefix("offline_") == event.id }?.attendeeCount = goingCount

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

    companion object {
        private const val CREATE_EVENT_REQUEST = 1001
    }
}