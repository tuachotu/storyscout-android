package app.storyscout.android.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "recordings")
data class RecordingEntity(
    @PrimaryKey val localId: String,
    val filePath: String,
    val originalFilename: String,
    val contentType: String = "audio/mp4",
    val sizeBytes: Long = 0,
    val durationMillis: Long = 0,
    val createdAtMillis: Long,
    val state: String,
    val possibleGap: Boolean = false,
    val statusMessage: String? = null,
    val serverRecordingId: String? = null,
    val uploadUrl: String? = null,
    val uploadMethod: String? = null,
    val uploadHeaders: String? = null,
    val uploadExpiresAtMillis: Long? = null,
) {
    fun requiresServerRecord(): Boolean = serverRecordingId == null
}
