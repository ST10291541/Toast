package vcmsa.projects.toastapplication.local

import android.content.Context
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import vcmsa.projects.toastapplication.local.AppDatabase
import vcmsa.projects.toastapplication.local.EventEntity
import java.util.UUID

class EventRepo(context: Context) {

    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()
    private val eventDao = AppDatabase.getDatabase(context).eventDao()

    suspend fun createEventOffline(eventEntity: EventEntity) {
        eventDao.insert(eventEntity)
    }

    suspend fun getLocalEvents(): List<EventEntity> {
        return eventDao.getAllEvents()
    }

    suspend fun syncEventsToFirestore() {
        val currentUser = auth.currentUser ?: return

        // refresh token before syncing
        try {
            currentUser.getIdToken(true).await()
        } catch (e: Exception) {
            e.printStackTrace()
            return  // cannot sync without valid token
        }

        val unsynced = eventDao.getUnsyncedEvents()

        for (event in unsynced) {

            val eventData = hashMapOf(
                "title" to event.title,
                "description" to event.description,
                "date" to event.date,
                "time" to event.time,
                "location" to event.location,
                "category" to event.category,
                "googleDriveLink" to event.googleDriveLink,
                "dietaryRequirements" to event.dietaryRequirements.split(","),
                "musicSuggestions" to event.musicSuggestions.split(","),
                "hostUserId" to event.hostUserId,
                "hostEmail" to currentUser.email,
                "createdAt" to event.createdAt.toString()
            )

            try {
                val docRef = db.collection("events").document(event.id)
                docRef.set(eventData).await()

                // Mark as synced
                eventDao.markAsSynced(event.id)

            } catch (e: Exception) {
                e.printStackTrace()
                // do NOT return; continue syncing the rest
            }
        }
    }
}