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
import com.google.android.material.button.MaterialButton
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import vcmsa.projects.toastapplication.databinding.ActivityCreateEventBinding
import vcmsa.projects.toastapplication.local.EventEntity
import vcmsa.projects.toastapplication.local.EventRepo
import vcmsa.projects.toastapplication.network.RetrofitClient
import java.util.UUID

class CreateEventActivity : AppCompatActivity() {

    private lateinit var binding: ActivityCreateEventBinding
    private lateinit var auth: FirebaseAuth
    private lateinit var eventNameEt: com.google.android.material.textfield.TextInputEditText
    private lateinit var eventDescriptionEt: com.google.android.material.textfield.TextInputEditText
    private lateinit var eventCategoryAutoComplete: AutoCompleteTextView
    private lateinit var eventLocationEt: com.google.android.material.textfield.TextInputEditText
    private lateinit var diet1Et: com.google.android.material.textfield.TextInputEditText
    private lateinit var diet2Et: com.google.android.material.textfield.TextInputEditText
    private lateinit var musicPollCb: CheckBox
    private lateinit var googleDriveEt: com.google.android.material.textfield.TextInputEditText
    private lateinit var createEventBtn: MaterialButton
    private var selectedDate: String? = null // yyyy-MM-dd
    private var selectedTime: String? = null // HH:mm

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Initialize binding first
        binding = ActivityCreateEventBinding.inflate(layoutInflater)
        setContentView(binding.root)

        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        auth = FirebaseAuth.getInstance()
        initViews()
        setupCreateEventButton()

        binding.btnBack.setOnClickListener {
            startActivity(Intent(this, DashboardActivity::class.java))
            finish()
        }
    }

    private fun initViews() {
        // Use binding for all views with correct types
        eventNameEt = binding.eventName
        eventDescriptionEt = binding.eventDescription
        eventCategoryAutoComplete = binding.eventCategory
        eventLocationEt = binding.eventLocation
        diet1Et = binding.diet1
        diet2Et = binding.diet2
        musicPollCb = binding.musicPollCheckbox
        googleDriveEt = binding.googleDriveLink
        createEventBtn = binding.btnCreateEvent

        binding.btnPickDate.setOnClickListener { showDatePicker() }
        binding.btnPickTime.setOnClickListener { showTimePicker() }

        // Setup AutoCompleteTextView for categories
        val categories = arrayOf("Wedding", "Party", "Food", "Art", "Meetup", "General")
        val adapter = ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, categories)
        eventCategoryAutoComplete.setAdapter(adapter)

        // Set hint
        eventCategoryAutoComplete.hint = "Event Category"

        // Remove any focusable/clickable restrictions that might interfere
        eventCategoryAutoComplete.setOnFocusChangeListener { _, hasFocus ->
            if (hasFocus) {
                eventCategoryAutoComplete.showDropDown()
            }
        }

        // Ensure dropdown shows when clicked
        eventCategoryAutoComplete.setOnClickListener {
            eventCategoryAutoComplete.showDropDown()
        }
    }

    private fun showDatePicker() {
        val today = java.util.Calendar.getInstance()
        val year = today.get(java.util.Calendar.YEAR)
        val month = today.get(java.util.Calendar.MONTH)
        val day = today.get(java.util.Calendar.DAY_OF_MONTH)

        DatePickerDialog(this, { _, y, m, d ->
            selectedDate = String.format("%04d-%02d-%02d", y, m + 1, d)
            // Update the date button text - find the TextView inside the LinearLayout
            val dateTextView = binding.btnPickDate.findViewById<TextView>(android.R.id.text1)
            dateTextView?.text = selectedDate
        }, year, month, day).show()
    }

    private fun showTimePicker() {
        val now = java.util.Calendar.getInstance()
        val hour = now.get(java.util.Calendar.HOUR_OF_DAY)
        val minute = now.get(java.util.Calendar.MINUTE)

        TimePickerDialog(this, { _, h, m ->
            selectedTime = String.format("%02d:%02d", h, m)
            // Update the time button text - find the TextView inside the LinearLayout
            val timeTextView = binding.btnPickTime.findViewById<TextView>(android.R.id.text1)
            timeTextView?.text = selectedTime
        }, hour, minute, true).show()
    }

    private fun setupCreateEventButton() {
        binding.btnCreateEvent.setOnClickListener {
            val title = binding.eventName.text.toString().trim()
            val description = binding.eventDescription.text.toString().trim()
            val category = binding.eventCategory.text.toString().trim()
            val location = binding.eventLocation.text.toString().trim()
            val dietary = listOfNotNull(
                binding.diet1.text.toString().takeIf { it.isNotEmpty() },
                binding.diet2.text.toString().takeIf { it.isNotEmpty() }
            )
            val musicPoll = if (binding.musicPollCheckbox.isChecked) listOf("Music Poll Enabled") else emptyList()
            val googleDriveLink = binding.googleDriveLink.text.toString().trim()

            if (title.isEmpty() || description.isEmpty() || location.isEmpty() || category.isEmpty()) {
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
                        if (response.isSuccessful) {
                            // Also sync any previously unsynced events
                            try {
                                repo.syncEventsToFirestore()
                            } catch (e: Exception) {
                                // Silent - sync will retry later
                            }
                            runOnUiThread {
                                Toast.makeText(this@CreateEventActivity, "Event created successfully!", Toast.LENGTH_SHORT).show()
                                finish()
                            }
                        } else {
                            runOnUiThread {
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