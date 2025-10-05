package vcmsa.projects.toastapplication

import java.io.Serializable

data class Event(
    var id: String = "",                      // Firestore doc ID
    val title: String = "",
    val description: String = "",
    val date: String = "",
    val time: String = "",               // renamed from 'time'
    val location: String = "",
    val category: String = "",
    val createdAt: String = "",
    val hostEmail: String = "",
    val hostUserId: String = "",              // previously creatorId
    var attendeeCount: Int = 0,
    val googleDriveLink: String = "",
    val dietaryRequirements: List<String> = emptyList(),
    val musicSuggestions: List<String> = emptyList(),
    val pollResponses: Map<String, Any> = emptyMap()
) : Serializable
