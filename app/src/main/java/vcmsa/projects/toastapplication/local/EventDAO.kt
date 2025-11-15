package vcmsa.projects.toastapplication.local

import androidx.room.*
import vcmsa.projects.toastapplication.local.EventEntity

@Dao
interface EventDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(event: EventEntity)

    @Query("SELECT * FROM events ORDER BY createdAt DESC")
    suspend fun getAllEvents(): List<EventEntity>

    @Query("SELECT * FROM events WHERE isSynced = 0")
    suspend fun getUnsyncedEvents(): List<EventEntity>

    @Query("UPDATE events SET isSynced = 1 WHERE id = :eventId")
    suspend fun markAsSynced(eventId: String)

    @Update
    suspend fun update(event: EventEntity)

    @Query("DELETE FROM events")
    suspend fun deleteAll()
}