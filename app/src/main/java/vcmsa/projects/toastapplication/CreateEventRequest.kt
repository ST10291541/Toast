package vcmsa.projects.toastapplication

data class CreateEventRequest(
    val title: String,
    val date: String,
    val time: String,
    val location: String,
    val description: String,
    val category: String = "General",
    val dietaryRequirements: List<String> = emptyList(),
    val musicSuggestions: List<String> = emptyList(),
    val googleDriveLink: String = ""
)

