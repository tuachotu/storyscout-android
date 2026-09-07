package app.storyscout.android.data.remote

data class AccessRequest(val guid: String)
data class AccessSessionDto(val accessToken: String, val displayName: String, val expiresAt: String)
data class CreateRecordingRequest(val originalFilename: String, val contentType: String, val sizeBytes: Long)
data class RecordingDto(
    val recordingId: String,
    val participantName: String,
    val createdAt: String,
    val completedAt: String?,
    val storageState: String,
    val originalFilename: String,
    val contentType: String,
    val sizeBytes: Long?,
)
data class UploadInstructionsDto(
    val url: String,
    val method: String,
    val headers: Map<String, String>,
    val expiresAt: String,
)
data class RecordingUploadDto(val recording: RecordingDto, val upload: UploadInstructionsDto)
data class ErrorResponseDto(val error: ErrorDetailsDto)
data class ErrorDetailsDto(val code: String, val message: String, val requestId: String)
