package vcmsa.projects.toastapplication

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.firestore.FirebaseFirestore
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.TimeUnit

class EventDetailsActivity : AppCompatActivity() {

    private lateinit var eventTitle: TextView
    private lateinit var countdownTimer: TextView
    private lateinit var goingCount: TextView
    private lateinit var eventDate: TextView
    private lateinit var eventTime: TextView
    private lateinit var eventEndTime: TextView
    private lateinit var eventLocation: TextView
    private lateinit var aboutDescription: TextView
    private lateinit var categoryText: TextView
    private lateinit var dietaryText: TextView
    private lateinit var musicText: TextView
    private lateinit var btnGoogleDrive: Button
    private lateinit var btnBack: ImageButton

    private var event: Event? = null
    private val handler = Handler(Looper.getMainLooper())
    private lateinit var countdownRunnable: Runnable
    private val db = FirebaseFirestore.getInstance()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_event_details)

        initViews()
        btnBack.setOnClickListener { finish() }

        // Get Event object from intent
        val eventFromIntent = intent.getSerializableExtra("event") as? Event
        if (eventFromIntent == null || eventFromIntent.id.isNullOrBlank()) {
            Toast.makeText(this, "Event data not found", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        // Fetch latest event data from Firestore
        db.collection("events")
            .document(eventFromIntent.id)
            .get()
            .addOnSuccessListener { snapshot ->
                snapshot.toObject(Event::class.java)?.let { fetchedEvent ->
                    event = fetchedEvent
                    bindEventData()
                    loadPreferences()
                    loadRSVPCount()
                    startCountdown()
                } ?: run {
                    Toast.makeText(this, "Event not found in database", Toast.LENGTH_SHORT).show()
                    finish()
                }
            }
            .addOnFailureListener {
                Toast.makeText(this, "Failed to load event details", Toast.LENGTH_SHORT).show()
                finish()
            }
    }

    private fun initViews() {
        eventTitle = findViewById(R.id.eventTitle)
        goingCount = findViewById(R.id.goingCount)
        countdownTimer = findViewById(R.id.countdownTimer)
        eventDate = findViewById(R.id.eventDate)
        eventTime = findViewById(R.id.eventTime)
        eventEndTime = findViewById(R.id.eventEndTime)
        eventLocation = findViewById(R.id.eventLocation)
        aboutDescription = findViewById(R.id.aboutDescription)
        categoryText = findViewById(R.id.categoryText)
        dietaryText = findViewById(R.id.dietaryText)
        musicText = findViewById(R.id.musicText)
        btnGoogleDrive = findViewById(R.id.btnGoogleDrive)
        btnBack = findViewById(R.id.btnBack)
    }

    private fun bindEventData() {
        event?.let { ev ->
            eventTitle.text = ev.title
            aboutDescription.text = ev.description
            eventDate.text = "Date: ${ev.date}"
            eventTime.text = "Start: ${ev.time}"
            eventEndTime.text = if (!ev.endTime.isNullOrBlank()) "End: ${ev.endTime}" else "End time not set"
            eventLocation.text = "Location: ${ev.location}"
            categoryText.text = "Category: ${ev.category}"

            if (!ev.googleDriveLink.isNullOrBlank()) {
                btnGoogleDrive.text = "Open Google Drive Folder"
                btnGoogleDrive.isEnabled = true
                btnGoogleDrive.setOnClickListener {
                    try {
                        startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(ev.googleDriveLink)))
                    } catch (e: Exception) {
                        Toast.makeText(this, "Cannot open link: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
                }
            } else {
                btnGoogleDrive.text = "No Google Drive link available"
                btnGoogleDrive.isEnabled = false
            }
        }
    }

    private fun loadPreferences() {
        val evId = event?.id
        if (evId.isNullOrBlank()) return

        db.collection("events")
            .document(evId)
            .collection("preferences")
            .get()
            .addOnSuccessListener { snapshot ->
                val dietaryList = mutableListOf<String>()
                val musicList = mutableListOf<String>()
                snapshot.documents.forEach { doc ->
                    doc.getString("dietaryChoice")?.let { dietaryList.add(it) }
                    doc.getString("musicChoice")?.let { musicList.add(it) }
                }
                dietaryText.text = if (dietaryList.isNotEmpty())
                    "Dietary Requirements: ${dietaryList.joinToString(", ")}"
                else "No dietary preferences"
                musicText.text = if (musicList.isNotEmpty())
                    "Music Suggestions: ${musicList.joinToString(", ")}"
                else "No music suggestions"
            }
    }

    private fun loadRSVPCount() {
        val evId = event?.id
        if (evId.isNullOrBlank()) return

        db.collection("events")
            .document(evId)
            .collection("rsvps")
            .get()
            .addOnSuccessListener { snapshot ->
                val goingCountValue = snapshot.documents.count { it.getString("status") == "going" }
                goingCount.text = "Attendees: $goingCountValue"
            }
    }

    private fun startCountdown() {
        event?.let { ev ->
            countdownRunnable = object : Runnable {
                override fun run() {
                    countdownTimer.text = getCountdownText(ev.date, ev.time, ev.endTime)
                    handler.postDelayed(this, 60000)
                }
            }
            handler.post(countdownRunnable)
        }
    }

    private fun getCountdownText(date: String, startTime: String, endTime: String?): String {
        return try {
            val format = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
            val eventStart = format.parse("$date $startTime")
            val eventEnd = if (!endTime.isNullOrBlank()) format.parse("$date $endTime") else eventStart
            val now = Date()
            val diff = eventStart.time - now.time
            if (diff > 0) {
                val days = TimeUnit.MILLISECONDS.toDays(diff)
                val hours = TimeUnit.MILLISECONDS.toHours(diff) % 24
                val minutes = TimeUnit.MILLISECONDS.toMinutes(diff) % 60
                "$days days, $hours hours, $minutes minutes left"
            } else if (eventEnd != null && now.time < eventEnd.time) {
                "Event in progress until ${SimpleDateFormat("HH:mm", Locale.getDefault()).format(eventEnd)}"
            } else {
                "Event ended"
            }
        } catch (e: Exception) {
            ""
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        if (::countdownRunnable.isInitialized) handler.removeCallbacks(countdownRunnable)
    }
}
