package vcmsa.projects.toastapplication

data class Guest(
    val guestId: String,
    val userName: String,
    val status: String,
    val dietaryChoice: String = "Not specified",
    val musicChoice: String = "Not specified"
)
