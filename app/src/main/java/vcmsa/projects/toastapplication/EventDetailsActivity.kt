package vcmsa.projects.toastapplication

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import com.bumptech.glide.Glide
import com.google.android.material.textfield.TextInputEditText
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.TimeUnit
import vcmsa.projects.toastapplication.network.RetrofitClient

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

    private lateinit var event: Event

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_event_details)

        auth = FirebaseAuth.getInstance()

        initViews()

        // Back button
        btnBack.setOnClickListener { finish() }

        // Get event object from intent
        event = intent.getSerializableExtra("event") as? Event
            ?: return finish()

        bindEventData()
        setupRSVPButtons()
        setupGoogleDriveButton()
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
        countdownTimer = findViewById(R.id.countdownTimer) // optional: add in XML if needed
    }

    private fun bindEventData() {
        eventTitle.text = event.title
        aboutDescription.text = event.description
        eventDate.text = "${event.date} • ${event.time}"
        eventLocation.text = event.location
        goingCount.text = "🎉 +${event.attendeeCount} people are going"

        // Load event image (if URL)
        Glide.with(this)
            .load(if (event.googleDriveLink.contains("http")) event.googleDriveLink else R.drawable.event3)
            .placeholder(R.drawable.event3)
            .into(eventImage)

        // Populate dietary spinner
        val dietaryAdapter = ArrayAdapter(
            this,
            android.R.layout.simple_dropdown_item_1line,
            event.dietaryRequirements
        )
        dietarySpinner.setAdapter(dietaryAdapter)

        // Countdown timer
        countdownTimer.text = getCountdownText(event.date, event.time)
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

    private fun setupRSVPButtons() {
        btnGoing.setOnClickListener { updateRSVP("going") }
        btnNotGoing.setOnClickListener { updateRSVP("notGoing") }
        btnMaybe.setOnClickListener { updateRSVP("maybe") }
    }

    private fun updateRSVP(status: String) {
        val user = auth.currentUser ?: return
        user.getIdToken(true).addOnCompleteListener { task ->
            if (task.isSuccessful) {
                val token = task.result?.token ?: return@addOnCompleteListener
                val authHeader = "Bearer $token"

                CoroutineScope(Dispatchers.IO).launch {
                    try {
                        val rsvp = RsvpResponse(
                            status = status,
                            dietaryChoice = dietarySpinner.text?.toString() ?: "",
                            musicChoice = songInput.text?.toString() ?: ""
                        )

                        val response = RetrofitClient.api.rsvpEvent(authHeader, event.id!!, rsvp)

                        runOnUiThread {
                            if (response.isSuccessful) {
                                Toast.makeText(
                                    this@EventDetailsActivity,
                                    "RSVP updated: $status",
                                    Toast.LENGTH_SHORT
                                ).show()
                                disableRSVPButtons()
                            } else {
                                Toast.makeText(
                                    this@EventDetailsActivity,
                                    "Failed to update RSVP",
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                        }
                    } catch (e: Exception) {
                        runOnUiThread {
                            Toast.makeText(
                                this@EventDetailsActivity,
                                "Network error: ${e.message}",
                                Toast.LENGTH_LONG
                            ).show()
                        }
                    }
                }
            } else {
                Toast.makeText(this, "Authentication failed", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun disableRSVPButtons() {
        btnGoing.isEnabled = false
        btnNotGoing.isEnabled = false
        btnMaybe.isEnabled = false
    }
    private fun setupGoogleDriveButton() {
        btnGoogleDrive.setOnClickListener {
            val link = event.googleDriveLink

            if (link.isNotBlank()) {
                try {
                    // Use ACTION_VIEW to open the link in browser or Drive app
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(link))

                    // Verify that there is an app to handle this intent
                    if (intent.resolveActivity(packageManager) != null) {
                        startActivity(intent)
                    } else {
                        Toast.makeText(
                            this,
                            "No app available to open the link",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                } catch (e: Exception) {
                    Toast.makeText(
                        this,
                        "Failed to open link: ${e.message}",
                        Toast.LENGTH_LONG
                    ).show()
                }
            } else {
                Toast.makeText(this, "No Google Drive link available", Toast.LENGTH_SHORT).show()
            }
        }
    }
}
