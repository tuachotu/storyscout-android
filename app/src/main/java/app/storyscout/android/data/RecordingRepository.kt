package app.storyscout.android.data

import app.storyscout.android.data.local.RecordingDao
import app.storyscout.android.data.local.RecordingEntity
import app.storyscout.android.data.local.SessionStore
import app.storyscout.android.data.local.StoredSession
import app.storyscout.android.data.remote.AccessRequest
import app.storyscout.android.data.remote.ApiClient
import app.storyscout.android.data.remote.CreateRecordingRequest
import app.storyscout.android.data.remote.UploadInstructionsDto
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.time.Instant

enum class UploadOutcome { COMPLETE, RETRY, SESSION_EXPIRED, UPLOAD_URL_EXPIRED, FILE_MISSING }

class RecordingRepository(
    private val api: ApiClient,
    private val dao: RecordingDao,
    private val sessions: SessionStore,
) {
    val recordings: Flow<List<RecordingEntity>> = dao.observeAll()

    suspend fun createSession(guid: String): StoredSession {
        val response = api.service.createAccessSession(AccessRequest(guid))
        return StoredSession(response.accessToken, response.displayName, Instant.parse(response.expiresAt).toEpochMilli())
            .also(sessions::save)
    }

    fun currentSession(): StoredSession? = sessions.current()
    fun clearSession() = sessions.clear()
    suspend fun save(recording: RecordingEntity) = dao.save(recording)
    suspend fun recording(id: String) = dao.get(id)
    suspend fun deleteLocal(id: String) {
        val recording = dao.get(id) ?: return
        if (recording.state != "UPLOADED") File(recording.filePath).delete()
        dao.delete(id)
    }

    suspend fun prepareUpload(localId: String, session: StoredSession): RecordingEntity {
        val current = requireNotNull(dao.get(localId))
        if (!current.requiresServerRecord()) return current
        val file = File(current.filePath)
        require(file.isFile && file.length() > 0) { "The saved recording file is unavailable." }
        val created = api.service.createRecording(
            "Bearer ${session.accessToken}",
            CreateRecordingRequest(file.name, current.contentType, file.length()),
        )
        return current.copy(
            sizeBytes = file.length(),
            state = "WAITING",
            serverRecordingId = created.recording.recordingId,
            uploadUrl = created.upload.url,
            uploadMethod = created.upload.method,
            uploadHeaders = JSONObject(created.upload.headers).toString(),
            uploadExpiresAtMillis = Instant.parse(created.upload.expiresAt).toEpochMilli(),
            statusMessage = null,
        ).also { dao.save(it) }
    }

    suspend fun upload(localId: String, nowMillis: Long = System.currentTimeMillis()): UploadOutcome =
        withContext(Dispatchers.IO) {
            var recording = dao.get(localId) ?: return@withContext UploadOutcome.FILE_MISSING
            val file = File(recording.filePath)
            if (!file.isFile || file.length() == 0L) {
                dao.updateState(localId, "FAILED", "The saved recording file is unavailable.")
                return@withContext UploadOutcome.FILE_MISSING
            }
            val session = sessions.current(nowMillis) ?: run {
                dao.updateState(localId, "WAITING", "Enter your access code again to upload.")
                return@withContext UploadOutcome.SESSION_EXPIRED
            }
            try {
                recording = prepareUpload(localId, session)
                if ((recording.uploadExpiresAtMillis ?: 0) <= nowMillis) {
                    dao.updateState(localId, "FAILED", "Upload link expired. Your recording remains saved.")
                    return@withContext UploadOutcome.UPLOAD_URL_EXPIRED
                }
                dao.updateState(localId, "UPLOADING")
                val headersJson = JSONObject(requireNotNull(recording.uploadHeaders))
                val headers = headersJson.keys().asSequence().associateWith(headersJson::getString)
                api.uploadDirect(
                    UploadInstructionsDto(
                        requireNotNull(recording.uploadUrl), requireNotNull(recording.uploadMethod),
                        headers, Instant.ofEpochMilli(requireNotNull(recording.uploadExpiresAtMillis)).toString(),
                    ),
                    file,
                )
                api.service.completeRecording(requireNotNull(recording.serverRecordingId), "Bearer ${session.accessToken}")
                dao.updateState(localId, "UPLOADED")
                UploadOutcome.COMPLETE
            } catch (error: Exception) {
                dao.updateState(localId, "FAILED", error.message ?: "Upload failed. It will be retried.")
                UploadOutcome.RETRY
            }
        }
}
