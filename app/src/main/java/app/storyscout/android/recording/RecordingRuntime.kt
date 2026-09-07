package app.storyscout.android.recording

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class CaptureState { IDLE, RECORDING, PAUSED, READY }

data class RecordingSnapshot(
    val state: CaptureState = CaptureState.IDLE,
    val localId: String? = null,
    val filePath: String? = null,
    val elapsedMillis: Long = 0,
    val runningSinceElapsedRealtime: Long? = null,
    val possibleGap: Boolean = false,
    val message: String? = null,
)

object RecordingRuntime {
    private val mutable = MutableStateFlow(RecordingSnapshot())
    val state = mutable.asStateFlow()
    fun publish(snapshot: RecordingSnapshot) { mutable.value = snapshot }
}
