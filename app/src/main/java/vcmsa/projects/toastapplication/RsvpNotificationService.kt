package vcmsa.projects.toastapplication
import android.util.Log
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await
import org.json.JSONObject
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL

class RsvpNotificationService {

    companion object {
        // Get this from Firebase Console -> Project Settings -> Cloud Messaging -> Server key
        private const val SERVER_KEY = "YOUR_SERVER_KEY_HERE"
        private const val FCM_URL = "https://fcm.googleapis.com/fcm/send"

        suspend fun notifyHostOfRsvp(
            eventId: String,
            guestUserId: String,
            guestName: String,
            rsvpStatus: String,
            dietaryChoice: String? = null,
            musicChoice: String? = null
        ) {
            try {
                val db = FirebaseFirestore.getInstance()

                val eventDoc = db.collection("events")
                    .document(eventId)
                    .get()
                    .await()

                if (!eventDoc.exists()) {
                    Log.e("RsvpNotification", "Event not found: $eventId")
                    return
                }

                val hostId = eventDoc.getString("hostId") ?: run {
                    Log.e("RsvpNotification", "Host ID not found in event")
                    return
                }
                val eventName = eventDoc.getString("name") ?: "Your Event"

                val hostDoc = db.collection("users")
                    .document(hostId)
                    .get()
                    .await()

                val fcmToken = hostDoc.getString("fcmToken")

                storeInAppNotification(
                    hostId = hostId,
                    eventId = eventId,
                    eventName = eventName,
                    guestName = guestName,
                    rsvpStatus = rsvpStatus
                )

                if (!fcmToken.isNullOrEmpty()) {
                    sendPushNotification(
                        token = fcmToken,
                        eventName = eventName,
                        guestName = guestName,
                        rsvpStatus = rsvpStatus,
                        eventId = eventId
                    )
                    Log.d("RsvpNotification", "Push notification sent to host")
                }

            } catch (e: Exception) {
                Log.e("RsvpNotification", "Error sending notification", e)
            }
        }

        private fun sendPushNotification(
            token: String,
            eventName: String,
            guestName: String,
            rsvpStatus: String,
            eventId: String
        ) {
            Thread {
                try {
                    val url = URL(FCM_URL)
                    val connection = url.openConnection() as HttpURLConnection

                    connection.requestMethod = "POST"
                    connection.setRequestProperty("Content-Type", "application/json")
                    connection.setRequestProperty("Authorization", "key=$SERVER_KEY")
                    connection.doOutput = true

                    val statusEmoji = when (rsvpStatus.lowercase()) {
                        "attending" -> "✅"
                        "maybe" -> "🤔"
                        "not attending" -> "❌"
                        else -> "📬"
                    }

                    val json = JSONObject().apply {
                        put("to", token)
                        put("priority", "high")

                        put("notification", JSONObject().apply {
                            put("title", "$statusEmoji New RSVP for $eventName")
                            put("body", "$guestName has RSVP'd: $rsvpStatus")
                            put("sound", "default")
                        })

                        put("data", JSONObject().apply {
                            put("type", "rsvp")
                            put("eventId", eventId)
                            put("guestName", guestName)
                            put("rsvpStatus", rsvpStatus)
                        })
                    }

                    OutputStreamWriter(connection.outputStream).use { writer ->
                        writer.write(json.toString())
                        writer.flush()
                    }

                    val responseCode = connection.responseCode
                    Log.d("FCM", "Response code: $responseCode")

                    connection.disconnect()

                } catch (e: Exception) {
                    Log.e("FCM", "Error sending push notification", e)
                }
            }.start()
        }

        private suspend fun storeInAppNotification(
            hostId: String,
            eventId: String,
            eventName: String,
            guestName: String,
            rsvpStatus: String
        ) {
            val db = FirebaseFirestore.getInstance()

            val notification = hashMapOf(
                "userId" to hostId,
                "eventId" to eventId,
                "eventName" to eventName,
                "title" to "New RSVP",
                "message" to "$guestName has RSVP'd: $rsvpStatus",
                "guestName" to guestName,
                "rsvpStatus" to rsvpStatus,
                "type" to "rsvp",
                "read" to false,
                "timestamp" to System.currentTimeMillis()
            )

            db.collection("notifications").add(notification)
        }
    }
}