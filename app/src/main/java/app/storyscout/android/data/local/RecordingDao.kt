package app.storyscout.android.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface RecordingDao {
    @Query("SELECT * FROM recordings ORDER BY createdAtMillis DESC")
    fun observeAll(): Flow<List<RecordingEntity>>

    @Query("SELECT * FROM recordings WHERE localId = :id")
    suspend fun get(id: String): RecordingEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun save(recording: RecordingEntity)

    @Query("UPDATE recordings SET state = :state, statusMessage = :message WHERE localId = :id")
    suspend fun updateState(id: String, state: String, message: String? = null)

    @Query("SELECT * FROM recordings WHERE state NOT IN ('UPLOADED') ORDER BY createdAtMillis")
    suspend fun pending(): List<RecordingEntity>

    @Query("DELETE FROM recordings WHERE localId = :id")
    suspend fun delete(id: String)

    @Query("UPDATE recordings SET state = 'FAILED', possibleGap = 1, statusMessage = 'Recording stopped unexpectedly. The saved audio may contain a gap.' WHERE state IN ('RECORDING', 'PAUSED')")
    suspend fun recoverInterruptedCaptures()
}
