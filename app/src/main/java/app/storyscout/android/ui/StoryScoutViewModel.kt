package app.storyscout.android.ui

import android.app.Application
import android.os.SystemClock
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import app.storyscout.android.StoryScoutApplication
import app.storyscout.android.data.local.RecordingEntity
import app.storyscout.android.data.local.StoredSession
import app.storyscout.android.data.remote.userMessage
import retrofit2.HttpException
import app.storyscout.android.recording.CaptureState
import app.storyscout.android.recording.RecordingRuntime
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import app.storyscout.android.upload.UploadWorker

data class StoryUiState(
    val initializing: Boolean = true,
    val session: StoredSession? = null,
    val recording: RecordingEntity? = null,
    val captureState: CaptureState = CaptureState.IDLE,
    val elapsedMillis: Long = 0,
    val busy: Boolean = false,
    val error: String? = null,
    val transcriptionLoading: Boolean = false,
    val transcriptionText: String? = null,
    val transcriptionError: String? = null,
    val nowMillis: Long = System.currentTimeMillis(),
)

class StoryScoutViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = (application as StoryScoutApplication).repository
    private val transient = MutableStateFlow(StoryUiState(session = repository.currentSession()))
    val state = combine(transient, repository.recordings, RecordingRuntime.state) { ui, recordings, runtime ->
        val persisted = runtime.localId?.let { id -> recordings.firstOrNull { it.localId == id } }
            ?: recordings.firstOrNull { it.state != "UPLOADED" }
        ui.copy(
            initializing = false,
            session = ui.session,
            recording = persisted,
            captureState = if (runtime.localId != null) runtime.state else when (persisted?.state) {
                "RECORDING" -> CaptureState.READY
                "PAUSED" -> CaptureState.READY
                "SAVED", "WAITING", "UPLOADING", "FAILED" -> CaptureState.READY
                else -> CaptureState.IDLE
            },
            elapsedMillis = if (runtime.localId != null) {
                runtime.elapsedMillis + (runtime.runningSinceElapsedRealtime?.let { SystemClock.elapsedRealtime() - it } ?: 0)
            } else persisted?.durationMillis ?: 0,
        )
    }.stateIn(viewModelScope, SharingStarted.Eagerly, transient.value)

    init {
        viewModelScope.launch {
            while (isActive) {
                delay(1_000)
                transient.value = transient.value.copy(nowMillis = System.currentTimeMillis())
            }
        }
    }

    fun validate(code: String) = viewModelScope.launch {
        if (code.isBlank() || transient.value.busy) return@launch
        transient.value = transient.value.copy(busy = true, error = null)
        try {
            val session = repository.createSession(code.trim())
            transient.value = transient.value.copy(session = session, busy = false)
        } catch (error: Exception) {
            transient.value = transient.value.copy(busy = false, error = error.userMessage())
        }
    }

    fun clearError() { transient.value = transient.value.copy(error = null) }
    fun showError(message: String) { transient.value = transient.value.copy(error = message) }
    fun requireNewSession() {
        repository.clearSession()
        transient.value = transient.value.copy(session = null, error = "Your session expired. Your recording remains safely stored on this device.")
    }
    fun upload(localId: String) { UploadWorker.enqueue(getApplication(), localId) }

    fun fetchTranscription(recording: RecordingEntity) = viewModelScope.launch {
        if (transient.value.transcriptionLoading) return@launch
        val session = repository.currentSession(transient.value.nowMillis)
        if (session == null) {
            requireNewSession()
            return@launch
        }
        val recordingId = recording.serverRecordingId ?: run {
            transient.value = transient.value.copy(transcriptionError = "The recording ID is unavailable.")
            return@launch
        }
        transient.value = transient.value.copy(transcriptionLoading = true, transcriptionError = null)
        try {
            val response = repository.fetchTranscription(recordingId, session)
            transient.value = transient.value.copy(
                transcriptionLoading = false,
                transcriptionText = response.transcription.text,
                transcriptionError = null,
            )
        } catch (error: Exception) {
            if (error is HttpException && error.code() in setOf(401, 403)) {
                repository.clearSession()
                transient.value = transient.value.copy(
                    session = null,
                    transcriptionLoading = false,
                    transcriptionError = null,
                    error = "Your session expired. Your recording remains safely stored on this device.",
                )
            } else {
                transient.value = transient.value.copy(
                    transcriptionLoading = false,
                    transcriptionError = error.userMessage(),
                )
            }
        }
    }

    fun discardAndReset(recording: RecordingEntity) = viewModelScope.launch {
        repository.deleteLocal(recording.localId)
        RecordingRuntime.publish(app.storyscout.android.recording.RecordingSnapshot())
    }

    fun resetAfterUpload() {
        transient.value = transient.value.copy(
            transcriptionLoading = false,
            transcriptionText = null,
            transcriptionError = null,
        )
        RecordingRuntime.publish(app.storyscout.android.recording.RecordingSnapshot())
    }
}
