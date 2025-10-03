package vcmsa.projects.toastapplication

data class Event(
    var id: String = "",
    val title: String = "",
    val description: String = "",
    val date: String = "",
    val time: String = "",
    val location: String = "",
    val creatorId: String = "",
    val attendeeCount: Int = 0,
    val googleDriveLink: String = "",
    val dietaryRequirements: List<String> = emptyList()
) : java.io.Serializable

