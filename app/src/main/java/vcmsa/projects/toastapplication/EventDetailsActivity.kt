package vcmsa.projects.toastapplication

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import com.bumptech.glide.Glide
import com.google.android.material.textfield.TextInputEditText
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.ktx.Firebase
import com.google.firebase.dynamiclinks.ktx.dynamicLinks
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.TimeUnit

class EventDetailsActivity : AppCompatActivity() {

    private lateinit var auth: FirebaseAuth

    private lateinit var eventImage: ImageView
    private lateinit var eventTitle: TextView
    private lateinit var countdownTimer: TextView
    private lateinit var goingCount: TextView
    private lateinit var eventDate: TextView
    private lateinit var eventLocation: TextView
    private lateinit var aboutDescription: TextView
    private lateinit var dietarySpinner: AutoCompleteTextView
    private lateinit var songInput: TextInputEditText
    private lateinit var btnGoogleDrive: Button
    private lateinit var btnGoing: Button
    private lateinit var btnNotGoing: Button
    private lateinit var btnMaybe: Button
    private lateinit var btnBack: ImageButton
    private lateinit var btnSavePreferences: Button

    private lateinit var event: Event
    private val handler = Handler(Looper.getMainLooper())
    private lateinit var countdownRunnable: Runnable

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_event_details)

        auth = FirebaseAuth.getInstance()
        initViews()
        btnBack.setOnClickListener { finish() }

        // 1️⃣ Check if launched via deep link (custom URI scheme)
        intent?.data?.let { uri ->
            val eventId = uri.lastPathSegment
            if (eventId != null) {
                loadEventFromId(eventId)
                return
            }
        }

        // 2️⃣ Check if launched via Firebase Dynamic Link
        Firebase.dynamicLinks.getDynamicLink(intent)
            .addOnSuccessListener { pendingLinkData ->
                val deepLink = pendingLinkData?.link
                deepLink?.lastPathSegment?.let { eventId ->
                    loadEventFromId(eventId)
                }
            }
            .addOnFailureListener { /* ignore */ }

        // 3️⃣ Normal launch via Intent extra
        event = intent.getSerializableExtra("event") as? Event ?: return finish()

        setupActivity()
    }

    private fun setupActivity() {
        bindEventData()
        listenToRSVPChanges()
        setupSaveButton()
        setupRSVPButtons()
        setupGoogleDriveButton()
        startCountdown()
    }

    private fun initViews() {
        eventImage = findViewById(R.id.eventImage)
        eventTitle = findViewById(R.id.eventTitle)
        goingCount = findViewById(R.id.goingCount)
        eventDate = findViewById(R.id.eventDate)
        eventLocation = findViewById(R.id.eventLocation)
        aboutDescription = findViewById(R.id.aboutDescription)
        dietarySpinner = findViewById(R.id.dietarySpinner)
        songInput = findViewById(R.id.songInput)
        btnGoogleDrive = findViewById(R.id.btnGoogleDrive)
        btnGoing = findViewById(R.id.btnGoing)
        btnNotGoing = findViewById(R.id.btnNotGoing)
        btnMaybe = findViewById(R.id.btnMaybe)
        btnBack = findViewById(R.id.btnBack)
        btnSavePreferences = findViewById(R.id.btnSavePreferences)
        countdownTimer = findViewById(R.id.countdownTimer)
    }

    private fun loadEventFromId(eventId: String) {
        val db = FirebaseFirestore.getInstance()
        db.collection("events").document(eventId).get()
            .addOnSuccessListener { doc ->
                val evt = doc.toObject(Event::class.java)?.apply { id = doc.id }
                if (evt != null) {
                    event = evt
                    setupActivity()
                } else {
                    Toast.makeText(this, "Event not found", Toast.LENGTH_SHORT).show()
                    finish()
                }
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "Failed to load event: ${e.message}", Toast.LENGTH_SHORT).show()
                finish()
            }
    }

    private fun bindEventData() {
        eventTitle.text = event.title
        aboutDescription.text = event.description
        eventDate.text = "${event.date} • ${event.time}"
        eventLocation.text = event.location
        goingCount.text = "🎉 ${event.attendeeCount ?: 0} people are going"

        Glide.with(this)
            .load(if (event.googleDriveLink.contains("http")) event.googleDriveLink else R.drawable.event3)
            .placeholder(R.drawable.event3)
            .into(eventImage)

        val dietaryAdapter = ArrayAdapter(
            this,
            android.R.layout.simple_dropdown_item_1line,
            event.dietaryRequirements
        )
        dietarySpinner.setAdapter(dietaryAdapter)
    }

    private fun startCountdown() {
        countdownRunnable = object : Runnable {
            override fun run() {
                countdownTimer.text = getCountdownText(event.date, event.time)
                handler.postDelayed(this, 60000)
            }
        }
        handler.post(countdownRunnable)
    }

    private fun getCountdownText(date: String, time: String): String {
        return try {
            val format = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
            val eventDateTime = format.parse("$date $time")
            val now = Date()
            val diff = eventDateTime.time - now.time
            if (diff > 0) {
                val days = TimeUnit.MILLISECONDS.toDays(diff)
                val hours = TimeUnit.MILLISECONDS.toHours(diff) % 24
                val minutes = TimeUnit.MILLISECONDS.toMinutes(diff) % 60
                "$days days, $hours hours, $minutes minutes left"
            } else {
                "Event started"
            }
        } catch (e: Exception) {
            ""
        }
    }

    private fun setupSaveButton() {
        btnSavePreferences.setOnClickListener {
            val dietaryChoice = dietarySpinner.text?.toString()?.trim()
            val musicChoice = songInput.text?.toString()?.trim()

            if (dietaryChoice.isNullOrEmpty() && musicChoice.isNullOrEmpty()) {
                Toast.makeText(this, "Please enter at least one preference", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val userId = auth.currentUser?.uid ?: return@setOnClickListener
            val db = FirebaseFirestore.getInstance()
            val prefData = mapOf(
                "dietaryChoice" to dietaryChoice,
                "musicChoice" to musicChoice
            )

            db.collection("events")
                .document(event.id!!)
                .collection("preferences")
                .document(userId)
                .set(prefData)
                .addOnSuccessListener {
                    Toast.makeText(this, "Preferences saved successfully", Toast.LENGTH_SHORT).show()
                }
                .addOnFailureListener { e ->
                    Toast.makeText(this, "Failed to save preferences: ${e.message}", Toast.LENGTH_LONG).show()
                }
        }
    }

    private fun setupRSVPButtons() {
        btnGoing.setOnClickListener {
            updateRSVPButtonsUI("going")
            sendRSVP("going")
        }
        btnNotGoing.setOnClickListener {
            updateRSVPButtonsUI("notGoing")
            sendRSVP("notGoing")
        }
        btnMaybe.setOnClickListener {
            updateRSVPButtonsUI("maybe")
            sendRSVP("maybe")
        }
    }

    private fun listenToRSVPChanges() {
        val db = FirebaseFirestore.getInstance()
        db.collection("events")
            .document(event.id!!)
            .collection("rsvps")
            .addSnapshotListener { snapshot, error ->
                if (error != null) return@addSnapshotListener
                if (snapshot != null) {
                    val goingCountValue = snapshot.documents.count { it.getString("status") == "going" }
                    goingCount.text = "🎉 $goingCountValue people are going"
                }
            }
    }

    private fun sendRSVP(status: String) {
        val user = auth.currentUser ?: return
        val userId = user.uid
        val db = FirebaseFirestore.getInstance()

        // Save preferences
        val prefData = mapOf(
            "dietaryChoice" to dietarySpinner.text?.toString(),
            "musicChoice" to songInput.text?.toString()
        )
        db.collection("events").document(event.id!!).collection("preferences")
            .document(userId).set(prefData)

        // Save RSVP status
        val rsvpData = mapOf("status" to status)
        db.collection("events").document(event.id!!).collection("rsvps")
            .document(userId).set(rsvpData)
            .addOnSuccessListener {
                Toast.makeText(this, "RSVP updated: $status", Toast.LENGTH_SHORT).show()
                disableRSVPButtons()
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "Failed to update RSVP: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }

    private fun disableRSVPButtons() {
        btnGoing.isEnabled = false
        btnNotGoing.isEnabled = false
        btnMaybe.isEnabled = false
    }

    private fun updateRSVPButtonsUI(selectedStatus: String) {
        // Reset all buttons
        btnGoing.setBackgroundColor(resources.getColor(R.color.gray))
        btnGoing.setTextColor(resources.getColor(android.R.color.white))

        btnNotGoing.setBackgroundColor(resources.getColor(R.color.white))
        btnNotGoing.setTextColor(resources.getColor(android.R.color.black))

        btnMaybe.setBackgroundColor(resources.getColor(R.color.gray))
        btnMaybe.setTextColor(resources.getColor(android.R.color.white))

        // Highlight selected
        when (selectedStatus) {
            "going" -> btnGoing.setBackgroundColor(resources.getColor(R.color.going_green))
            "notGoing" -> btnNotGoing.setBackgroundColor(resources.getColor(R.color.notgoing_red_dark))
            "maybe" -> btnMaybe.setBackgroundColor(resources.getColor(R.color.mustard_yellow))
        }
    }

    private fun setupGoogleDriveButton() {
        btnGoogleDrive.setOnClickListener {
            val link = event.googleDriveLink
            if (link.isNotBlank()) {
                try {
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(link))
                    if (intent.resolveActivity(packageManager) != null) startActivity(intent)
                    else Toast.makeText(this, "No app available to open the link", Toast.LENGTH_SHORT).show()
                } catch (e: Exception) {
                    Toast.makeText(this, "Failed to open link: ${e.message}", Toast.LENGTH_LONG).show()
                }
            } else {
                Toast.makeText(this, "No Google Drive link available", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacks(countdownRunnable)
    }
}
