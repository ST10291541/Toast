package vcmsa.projects.toastapplication

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.*
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import vcmsa.projects.toastapplication.local.EventEntity
import vcmsa.projects.toastapplication.local.EventRepo
import vcmsa.projects.toastapplication.network.RetrofitClient
import java.util.UUID

class CreateEventActivity : AppCompatActivity() {

    private lateinit var auth: FirebaseAuth
    private lateinit var eventNameEt: EditText
    private lateinit var eventDescriptionEt: EditText
    private lateinit var eventCategorySpinner: Spinner
    private lateinit var eventLocationEt: EditText
    private lateinit var diet1Et: EditText
    private lateinit var diet2Et: EditText
    private lateinit var musicPollCb: CheckBox
    private lateinit var googleDriveEt: EditText
    private lateinit var createEventBtn: Button
    private lateinit var btnPickDate: Button
    private lateinit var btnPickTime: Button
    private var selectedDate: String? = null // yyyy-MM-dd
    private var selectedTime: String? = null // HH:mm
    private lateinit var contentLayout: LinearLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_create_event)

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.scrollView)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        auth = FirebaseAuth.getInstance()
        initViews()
        setupCreateEventButton()

        findViewById<ImageButton>(R.id.btnBack).setOnClickListener {
            startActivity(Intent(this, DashboardActivity::class.java))
            finish()
        }
    }

    private fun initViews() {
        eventNameEt = findViewById(R.id.eventName)
        eventDescriptionEt = findViewById(R.id.eventDescription)
        eventCategorySpinner = findViewById(R.id.eventCategory)
        eventLocationEt = findViewById(R.id.eventLocation)
        diet1Et = findViewById(R.id.diet1)
        diet2Et = findViewById(R.id.diet2)
        musicPollCb = findViewById(R.id.musicPollCheckbox)
        googleDriveEt = findViewById(R.id.googleDriveLink)
        createEventBtn = findViewById(R.id.btnCreateEvent)
        btnPickDate = findViewById(R.id.btnPickDate)
       btnPickTime = findViewById(R.id.btnPickTime)
       contentLayout = findViewById(R.id.contentLayout)

        btnPickDate.setOnClickListener { showDatePicker() }
        btnPickTime.setOnClickListener { showTimePicker() }

        val categories = listOf("Wedding", "Party", "Food", "Art", "Meetup", "General")
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, categories)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        eventCategorySpinner.adapter = adapter
    }

    private fun showDatePicker() {
        val today = java.util.Calendar.getInstance()
        val year = today.get(java.util.Calendar.YEAR)
        val month = today.get(java.util.Calendar.MONTH)
        val day = today.get(java.util.Calendar.DAY_OF_MONTH)

        DatePickerDialog(this, { _, y, m, d ->
            selectedDate = String.format("%04d-%02d-%02d", y, m + 1, d)
            btnPickDate.text = selectedDate
        }, year, month, day).show()
    }

    private fun showTimePicker() {
        val now = java.util.Calendar.getInstance()
        val hour = now.get(java.util.Calendar.HOUR_OF_DAY)
        val minute = now.get(java.util.Calendar.MINUTE)

        TimePickerDialog(this, { _, h, m ->
            selectedTime = String.format("%02d:%02d", h, m)
            btnPickTime.text = selectedTime
        }, hour, minute, true).show()
    }

    private fun setupCreateEventButton() {
        createEventBtn.setOnClickListener {
            val title = eventNameEt.text.toString().trim()
            val description = eventDescriptionEt.text.toString().trim()
            val category = eventCategorySpinner.selectedItem.toString()
            val location = eventLocationEt.text.toString().trim()
            val dietary = listOfNotNull(
                diet1Et.text.toString().takeIf { it.isNotEmpty() },
                diet2Et.text.toString().takeIf { it.isNotEmpty() }
            )
            val musicPoll = if (musicPollCb.isChecked) listOf("Music Poll Enabled") else emptyList()
            val googleDriveLink = googleDriveEt.text.toString().trim()

            if (title.isEmpty() || description.isEmpty() || location.isEmpty()) {
                Toast.makeText(this, "Please fill in all required fields", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            if (selectedDate.isNullOrEmpty() || selectedTime.isNullOrEmpty()) {
                Toast.makeText(this, "Please select a date and time", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val request = CreateEventRequest(
                title = title,
                date = selectedDate!!,
                time = selectedTime!!,
                location = location,
                description = description,
                category = category,
                dietaryRequirements = dietary,
                musicSuggestions = musicPoll,
                googleDriveLink = googleDriveLink
            )

            createEvent(request)
        }
    }

    private fun createEvent(event: CreateEventRequest) {
        val user = FirebaseAuth.getInstance().currentUser
        val repo = EventRepo(this)

        // Check if online first
        if (!isOnline()) {
            CoroutineScope(Dispatchers.IO).launch {
                val localEvent = EventEntity(
                    id = UUID.randomUUID().toString(),
                    title = event.title,
                    description = event.description,
                    date = event.date,
                    time = event.time,
                    location = event.location,
                    category = event.category,
                    googleDriveLink = event.googleDriveLink,
                    dietaryRequirements = event.dietaryRequirements.joinToString(","),
                    musicSuggestions = event.musicSuggestions.joinToString(","),
                    createdAt = System.currentTimeMillis(),
                    hostUserId = user?.uid ?: "unknown",
                    isSynced = false
                )
                repo.createEventOffline(localEvent)
                runOnUiThread {
                    Toast.makeText(this@CreateEventActivity, "Saved offline — will sync later", Toast.LENGTH_SHORT).show()
                    finish()
                }
            }
            return
        }

        // Original online API logic
        user?.getIdToken(true)?.addOnCompleteListener { task ->
            if (task.isSuccessful) {
                val token = task.result?.token ?: return@addOnCompleteListener
                val authHeader = "Bearer $token"

                CoroutineScope(Dispatchers.IO).launch {
                    try {
                        val response = RetrofitClient.api.createEvent(authHeader, event)
                        runOnUiThread {
                            if (response.isSuccessful) {
                                Toast.makeText(this@CreateEventActivity, "Event created successfully!", Toast.LENGTH_SHORT).show()
                                finish()
                            } else {
                                Toast.makeText(this@CreateEventActivity, "Error: ${response.code()} ${response.message()}", Toast.LENGTH_LONG).show()
                            }
                        }
                    } catch (e: Exception) {
                        runOnUiThread {
                            Toast.makeText(this@CreateEventActivity, "Network error: ${e.message}", Toast.LENGTH_LONG).show()
                        }
                    }
                }
            } else {
                Toast.makeText(this, "Authentication failed", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // Helper function to check connectivity
    private fun isOnline(): Boolean {
        val cm = getSystemService(Context.CONNECTIVITY_SERVICE) as android.net.ConnectivityManager
        val network = cm.activeNetworkInfo
        return network != null && network.isConnected
    }
}
