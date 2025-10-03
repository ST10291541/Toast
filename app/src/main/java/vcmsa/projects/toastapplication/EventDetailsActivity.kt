package vcmsa.projects.toastapplication

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import com.bumptech.glide.Glide
import com.google.android.material.textfield.TextInputEditText
import com.google.firebase.auth.FirebaseAuth
import vcmsa.projects.toastapplication.network.RetrofitClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import vcmsa.projects.toastapplication.RsvpResponse

class EventDetailsActivity : AppCompatActivity() {

    private lateinit var auth: FirebaseAuth

    private lateinit var eventImage: ImageView
    private lateinit var eventTitle: TextView
    private lateinit var countdownTimer: TextView
    private lateinit var goingCount: TextView
    private lateinit var eventDate: TextView
    private lateinit var eventLocation: TextView
    private lateinit var organizerName: TextView
    private lateinit var aboutDescription: TextView
    private lateinit var dietarySpinner: AutoCompleteTextView
    private lateinit var songInput: TextInputEditText
    private lateinit var btnGoogleDrive: Button
    private lateinit var btnGoing: Button
    private lateinit var btnNotGoing: Button
    private lateinit var btnConfirmedGoing: Button

    private lateinit var event: Event
    private lateinit var btnMaybe: Button


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_event_details)

        auth = FirebaseAuth.getInstance()

        // Initialize views
        initViews()

        // Get event object passed via intent
        event = intent.getSerializableExtra("event") as? Event
            ?: return finish() // close if no event

        bindEventData()

        setupRSVPButtons()
        setupGoogleDriveButton()
    }

    private fun initViews() {
        eventImage = findViewById(R.id.eventImage)
        eventTitle = findViewById(R.id.eventTitle)
        countdownTimer = findViewById(R.id.countdownTimer)
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
    }

    private fun bindEventData() {
        eventTitle.text = event.title
        aboutDescription.text = event.description
        eventDate.text = "${event.date} • ${event.time}"
        eventLocation.text = event.location
        goingCount.text = "🎉 +${event.attendeeCount} people are going"

        // Load event image if you have a URL, else keep default
        Glide.with(this)
            .load(event.googleDriveLink) // or any image URL
            .placeholder(R.drawable.event3)
            .into(eventImage)

        // Populate dietary spinner
        val dietaryAdapter = ArrayAdapter(
            this,
            android.R.layout.simple_dropdown_item_1line,
            event.dietaryRequirements
        )
        dietarySpinner.setAdapter(dietaryAdapter)
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
                                Toast.makeText(this@EventDetailsActivity, "RSVP updated: $status", Toast.LENGTH_SHORT).show()
                                updateButtonVisibility(status)
                            } else {
                                Toast.makeText(this@EventDetailsActivity, "Failed to update RSVP", Toast.LENGTH_SHORT).show()
                            }
                        }
                    } catch (e: Exception) {
                        runOnUiThread {
                            Toast.makeText(this@EventDetailsActivity, "Network error: ${e.message}", Toast.LENGTH_LONG).show()
                        }
                    }
                }
            } else {
                Toast.makeText(this, "Authentication failed", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun updateButtonVisibility(status: String) {
        btnGoing.visibility = if (status == "going") Button.GONE else Button.VISIBLE
        btnNotGoing.visibility = if (status == "notGoing") Button.GONE else Button.VISIBLE
        btnMaybe.visibility = if (status == "maybe") Button.GONE else Button.VISIBLE
        btnConfirmedGoing.visibility = if (status == "going") Button.VISIBLE else Button.GONE
    }

    private fun setupGoogleDriveButton() {
        btnGoogleDrive.setOnClickListener {
            val link = event.googleDriveLink
            if (link.isNotEmpty()) {
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(link))
                startActivity(intent)
            } else {
                Toast.makeText(this, "No Google Drive link available", Toast.LENGTH_SHORT).show()
            }
        }
    }
}
