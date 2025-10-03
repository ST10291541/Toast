package vcmsa.projects.toastapplication

data class Event(
    val id: String? = null, // Firestore doc ID
    val title: String = "",
    val date: String = "",
    val time: String = "",
    val location: String = "",
    val description: String = "",
    val category: String = "General",
    val dietaryRequirements: List<String> = emptyList(),
    val musicSuggestions: List<String> = emptyList(),
    val googleDriveLink: String = "",
    val hostUserId: String? = null,
    val hostEmail: String? = null,
    val createdAt: String? = null,
    val attendeeCount: Int = 0
)
