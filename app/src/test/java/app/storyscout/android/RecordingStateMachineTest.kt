package app.storyscout.android

import app.storyscout.android.domain.RecordingAction
import app.storyscout.android.domain.RecordingState
import app.storyscout.android.domain.RecordingStateMachine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class RecordingStateMachineTest {
    @Test fun supportsPrimaryFlow() {
        var state = RecordingStateMachine.transition(RecordingState.IDLE, RecordingAction.START)
        state = RecordingStateMachine.transition(state, RecordingAction.PAUSE)
        state = RecordingStateMachine.transition(state, RecordingAction.RESUME)
        state = RecordingStateMachine.transition(state, RecordingAction.STOP)
        assertEquals(RecordingState.SAVED, state)
        assertEquals(RecordingState.IDLE, RecordingStateMachine.transition(state, RecordingAction.RECORD_AGAIN))
    }

    @Test fun rejectsInvalidTransition() {
        assertThrows(IllegalStateException::class.java) { RecordingStateMachine.transition(RecordingState.IDLE, RecordingAction.STOP) }
    }
}
