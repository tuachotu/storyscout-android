package app.storyscout.android.domain

enum class RecordingState { IDLE, RECORDING, PAUSED, SAVED }
enum class RecordingAction { START, PAUSE, RESUME, STOP, RECORD_AGAIN }

object RecordingStateMachine {
    fun transition(state: RecordingState, action: RecordingAction): RecordingState = when (state to action) {
        RecordingState.IDLE to RecordingAction.START -> RecordingState.RECORDING
        RecordingState.RECORDING to RecordingAction.PAUSE -> RecordingState.PAUSED
        RecordingState.PAUSED to RecordingAction.RESUME -> RecordingState.RECORDING
        RecordingState.RECORDING to RecordingAction.STOP,
        RecordingState.PAUSED to RecordingAction.STOP -> RecordingState.SAVED
        RecordingState.SAVED to RecordingAction.RECORD_AGAIN -> RecordingState.IDLE
        else -> throw IllegalStateException("Invalid recording transition: $state + $action")
    }
}
