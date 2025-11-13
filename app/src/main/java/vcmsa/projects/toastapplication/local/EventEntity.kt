package vcmsa.projects.toastapplication.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "events")
data class EventEntity(
    @PrimaryKey(autoGenerate = false)
    val id: String, // Use UUID
    val title: String,
    val description: String,
    val date: String,
    val time: String,
    val location: String,
    val category: String,
    val googleDriveLink: String,
    val dietaryRequirements: String,
    val musicSuggestions: String,
    val createdAt: Long,
    val hostUserId: String,
    val isSynced: Boolean = false // true if already uploaded to Firestore
)