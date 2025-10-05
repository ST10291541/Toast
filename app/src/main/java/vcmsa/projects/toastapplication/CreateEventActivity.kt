package vcmsa.projects.toastapplication

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.Intent
import android.os.Bundle
import android.widget.*
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import vcmsa.projects.toastapplication.network.RetrofitClient

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
    private lateinit var btnPickEndTime: Button
    private var selectedDate: String? = null // yyyy-MM-dd
    private var selectedStartTime: String? = null // HH:mm
    private var selectedEndTime: String? = null // HH:mm
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
        createEventBtn = findViewById(R.id.createEventBtn)
        btnPickDate = findViewById(R.id.btnPickDate)
        btnPickTime = findViewById(R.id.btnPickTime)
        btnPickEndTime = findViewById(R.id.btnPickEndTime)
        contentLayout = findViewById(R.id.contentLayout)

        btnPickDate.setOnClickListener { showDatePicker() }
        btnPickTime.setOnClickListener { showTimePicker(isStart = true) }
        btnPickEndTime.setOnClickListener { showTimePicker(isStart = false) }

        // Optional: populate spinner
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

        val datePicker = DatePickerDialog(this, { _, y, m, d ->
            selectedDate = String.format("%04d-%02d-%02d", y, m + 1, d)
            btnPickDate.text = selectedDate
        }, year, month, day)

        datePicker.show()
    }

    private fun showTimePicker(isStart: Boolean) {
        val now = java.util.Calendar.getInstance()
        val hour = now.get(java.util.Calendar.HOUR_OF_DAY)
        val minute = now.get(java.util.Calendar.MINUTE)

        val timePicker = TimePickerDialog(this, { _, h, m ->
            val formattedTime = String.format("%02d:%02d", h, m)
            if (isStart) {
                selectedStartTime = formattedTime
                btnPickTime.text = "Start: $formattedTime"
            } else {
                selectedEndTime = formattedTime
                btnPickEndTime.text = "End: $formattedTime"
            }
        }, hour, minute, true)

        timePicker.show()
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

            if (selectedDate.isNullOrEmpty() || selectedStartTime.isNullOrEmpty() || selectedEndTime.isNullOrEmpty()) {
                Toast.makeText(this, "Please select a date, start time, and end time", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val apiRequest = CreateEventRequest(
                title = title,
                date = selectedDate!!,
                time = selectedStartTime!!,
                location = location,
                description = description,
                category = category,
                dietaryRequirements = dietary,
                musicSuggestions = musicPoll,
                googleDriveLink = googleDriveLink
            )

            val event = Event(
                title = title,
                description = description,
                date = selectedDate!!,
                time = selectedStartTime!!,
                endTime = selectedEndTime!!,
                location = location,
                category = category,
                googleDriveLink = googleDriveLink,
                dietaryRequirements = dietary,
                musicSuggestions = musicPoll,
                hostEmail = auth.currentUser?.email ?: "",
                hostUserId = auth.currentUser?.uid ?: "",
                createdAt = System.currentTimeMillis().toString()
            )

            // Save to Firestore first, then API, then redirect
            saveEventToFirestore(event) {
                createEvent(apiRequest) {
                    // Redirect after both succeed
                    val intent = Intent(this, MyEventsActivity::class.java)
                    intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK
                    startActivity(intent)
                    finish()
                }
            }
        }
    }

    private fun saveEventToFirestore(event: Event, onSuccess: () -> Unit) {
        val db = FirebaseFirestore.getInstance()
        val docRef = db.collection("events").document()
        event.id = docRef.id
        docRef.set(event)
            .addOnSuccessListener {
                onSuccess()
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "Firestore save failed: ${e.message}", Toast.LENGTH_LONG).show()
            }
    }

    private fun createEvent(event: CreateEventRequest, onSuccess: () -> Unit) {
        val user = FirebaseAuth.getInstance().currentUser

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
                                onSuccess()
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
}
