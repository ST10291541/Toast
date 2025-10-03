package vcmsa.projects.toastapplication

data class RsvpResponse(
    val status: String,
    val dietaryChoice: String? = null,
    val musicChoice: String? = null
)
