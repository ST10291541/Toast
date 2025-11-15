package vcmsa.projects.toastapplication

import android.os.Bundle
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import vcmsa.projects.toastapplication.local.AppDatabase
import vcmsa.projects.toastapplication.local.ConnectivityReceiver

class MainActivity : AppCompatActivity() {

    private lateinit var connectivityReceiver: ConnectivityReceiver
    private lateinit var localDatabase: AppDatabase
    private val requestPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted: Boolean ->
            if (isGranted) {
                Log.d("Permission", "Notifications allowed")
            } else {
                Log.d("Permission", "Notifications denied")
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        askNotificationPermission()

        // Initialize once
        connectivityReceiver = ConnectivityReceiver(this)

        // Start observing immediately if desired
        connectivityReceiver.startObserving()

        // Initialize local database
        localDatabase = AppDatabase.getDatabase(this)

        // Launch sync
        lifecycleScope.launch {
            syncOfflineEventsToFirebase()
        }
    }

    private fun askNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    private suspend fun syncOfflineEventsToFirebase() {
        val db = FirebaseFirestore.getInstance()

        val localEvents = withContext(Dispatchers.IO) {
            localDatabase.eventDao().getAllEvents()
        }

        Log.d("Sync", "Found ${localEvents.size} local events to sync.")

        for (event in localEvents) {
            val eventRef = db.collection("events").document(event.id)
            try {
                val document = eventRef.get().await()
                if (document.exists()) {
                    Log.d("Sync", "Event already exists in Firebase: ${event.id}")
                } else {
                    Log.d("Sync", "Event not found in Firebase. Uploading: ${event.id}")
                    val eventMap = hashMapOf(
                        "title" to event.title,
                        "description" to event.description,
                        "date" to event.date,
                        "time" to event.time,
                        "location" to event.location,
                        "category" to event.category,
                        "googleDriveLink" to event.googleDriveLink,
                        "dietaryRequirements" to event.dietaryRequirements
                    )
                    eventRef.set(eventMap).await()
                    val syncedEvent = event.copy(isSynced = true)
                    eventDao.update(syncedEvent)
                    Log.d("Sync", "Event uploaded successfully: ${event.id}")
                }
            } catch (e: Exception) {
                Log.e("Sync", "Failed to sync event ${event.id}", e)
            }
        }

        Log.d("Sync", "Offline events sync completed.")
    }


    override fun onResume() {
        super.onResume()
        // Start observing if you stopped in onPause
        connectivityReceiver.startObserving()
    }

//    override fun onPause() {
//        super.onPause()
//        // Stop observing to avoid memory leaks
//        connectivityReceiver.stopObserving()
//    }

}