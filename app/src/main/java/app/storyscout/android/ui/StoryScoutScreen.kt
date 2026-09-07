package app.storyscout.android.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.storyscout.android.data.local.RecordingEntity
import app.storyscout.android.domain.SessionCountdown
import app.storyscout.android.domain.formatDuration
import app.storyscout.android.recording.CaptureState
import app.storyscout.android.recording.PlaybackController
import app.storyscout.android.recording.RecordingService

@Composable
fun StoryScoutScreen(state: StoryUiState, model: StoryScoutViewModel, onStart: () -> Unit) {
    if (state.initializing) {
        Box(Modifier.fillMaxSize().background(Color.White)) {
            Text("StoryScout", Modifier.align(Alignment.Center), fontSize = 28.sp, fontWeight = FontWeight.Bold)
            Text("Developed by Vikrant", Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = 24.dp), color = StorySecondary, fontSize = 12.sp)
        }
        return
    }
    Box(Modifier.fillMaxSize().background(Color.White).statusBarsPadding().navigationBarsPadding().imePadding()) {
        Column(
            Modifier.align(Alignment.TopCenter).widthIn(max = 480.dp).fillMaxWidth().verticalScroll(rememberScrollState()).padding(24.dp),
        ) {
            Text("StoryScout", fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(48.dp))
            if (state.session == null) AccessContent(state, model)
            else RecordContent(state, model, onStart)
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun AccessContent(state: StoryUiState, model: StoryScoutViewModel) {
    var code by remember { mutableStateOf("") }
    Text("Step 1 of 2", color = StorySecondary, fontSize = 13.sp)
    Text("Enter your access code", fontWeight = FontWeight.Bold, fontSize = 44.sp, lineHeight = 46.sp, modifier = Modifier.padding(top = 8.dp, bottom = 32.dp))
    Text("Access code", fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(bottom = 8.dp))
    OutlinedTextField(
        value = code, onValueChange = { code = it; model.clearError() }, modifier = Modifier.fillMaxWidth(),
        placeholder = { Text("00000000-0000-0000-0000-000000000000", color = StorySecondary) },
        singleLine = true, keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.None),
    )
    ErrorText(state.error)
    StoryButton(if (state.busy) "Checking…" else "Continue", !state.busy && code.trim().isNotEmpty(), Modifier.padding(top = 12.dp)) { model.validate(code) }
}

@Composable
private fun RecordContent(state: StoryUiState, model: StoryScoutViewModel, onStart: () -> Unit) {
    val session = requireNotNull(state.session)
    SessionMessage(SessionCountdown.at(session.expiresAtMillis, state.nowMillis))
    if (state.recording?.state == "UPLOADED") {
        CompletionContent(state.recording, state, model); return
    }
    Text("Step 2 of 2", color = StorySecondary, fontSize = 13.sp)
    Text("Record your story", fontWeight = FontWeight.Bold, fontSize = 44.sp, lineHeight = 46.sp, modifier = Modifier.padding(top = 8.dp))
    Text("Hi ${session.displayName}.", fontSize = 20.sp, modifier = Modifier.padding(top = 12.dp))
    Text(
        formatDuration(state.elapsedMillis), fontFamily = FontFamily.Monospace, fontSize = 56.sp,
        modifier = Modifier.padding(top = 48.dp, bottom = 24.dp), maxLines = 1,
    )
    when (state.captureState) {
        CaptureState.IDLE -> StoryButton("Start recording", true, onClick = onStart)
        CaptureState.RECORDING -> PairedButtons("Pause", { RecordingService.command(model.getApplication(), RecordingService.ACTION_PAUSE) }, "Stop", { RecordingService.command(model.getApplication(), RecordingService.ACTION_STOP) })
        CaptureState.PAUSED -> PairedButtons("Resume", { RecordingService.command(model.getApplication(), RecordingService.ACTION_RESUME) }, "Stop", { RecordingService.command(model.getApplication(), RecordingService.ACTION_STOP) })
        CaptureState.READY -> state.recording?.let { ReviewContent(it, state, model) }
    }
    ErrorText(state.error ?: state.recording?.statusMessage)
}

@Composable
private fun ReviewContent(recording: RecordingEntity, state: StoryUiState, model: StoryScoutViewModel) {
    var playing by remember { mutableStateOf(false) }
    var confirmDiscard by remember { mutableStateOf(false) }
    val player = remember { PlaybackController() }
    DisposableEffect(Unit) { onDispose(player::stop) }
    Text("Recording ready", fontWeight = FontWeight.SemiBold)
    Text("Your recording is safely stored on this device.", color = StorySecondary, modifier = Modifier.padding(top = 8.dp, bottom = 16.dp))
    if (recording.possibleGap) ErrorText("A recording interruption occurred. The saved audio may contain a gap.")
    StoryButton(if (playing) "Stop playback" else "Play recording", recording.state != "UPLOADING") {
        runCatching { player.toggle(recording.filePath) { playing = player.isPlaying } }
            .onFailure { model.showError("The saved recording could not be played.") }
    }
    Spacer(Modifier.height(16.dp))
    PairedButtons(
        "Record again", { confirmDiscard = true },
        if (recording.state == "UPLOADING") "Uploading…" else "Upload",
        {
            if (requireNotNull(state.session).expiresAtMillis <= state.nowMillis) model.requireNewSession()
            else model.upload(recording.localId)
        },
        enabled = recording.state != "UPLOADING",
    )
    if (recording.state in setOf("WAITING", "UPLOADING", "FAILED")) {
        Text(
            when (recording.state) { "WAITING" -> "Waiting for a connection…"; "UPLOADING" -> "Uploading…"; else -> recording.statusMessage ?: "Upload failed." },
            color = if (recording.state == "FAILED") StoryError else StorySecondary,
            modifier = Modifier.padding(top = 16.dp).semantics { liveRegion = LiveRegionMode.Polite },
        )
    }
    if (confirmDiscard) AlertDialog(
        onDismissRequest = { confirmDiscard = false },
        title = { Text("Record again?") },
        text = { Text("This removes the current recording from this device.") },
        confirmButton = { TextButton(onClick = { confirmDiscard = false; player.stop(); model.discardAndReset(recording) }) { Text("Remove and record again", color = StoryError) } },
        dismissButton = { TextButton(onClick = { confirmDiscard = false }) { Text("Cancel") } },
    )
}

@Composable
private fun CompletionContent(recording: RecordingEntity, state: StoryUiState, model: StoryScoutViewModel) {
    var playing by remember { mutableStateOf(false) }
    val player = remember { PlaybackController() }
    DisposableEffect(Unit) { onDispose(player::stop) }
    Text("Complete", color = StorySecondary, fontSize = 13.sp)
    Text("Recording saved", fontWeight = FontWeight.Bold, fontSize = 44.sp, modifier = Modifier.padding(top = 8.dp))
    Text("Your recording ID is", modifier = Modifier.padding(top = 20.dp))
    Text(recording.serverRecordingId.orEmpty(), fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 4.dp))
    StoryButton(if (playing) "Stop playback" else "Play recording", true, Modifier.padding(top = 24.dp)) {
        runCatching { player.toggle(recording.filePath) { playing = player.isPlaying } }
            .onFailure { model.showError("The saved recording could not be played.") }
    }
    StoryButton(
        if (state.transcriptionLoading) "Fetching…" else "Fetch transcription",
        !state.transcriptionLoading,
        Modifier.padding(top = 12.dp),
    ) { model.fetchTranscription(recording) }
    ErrorText(state.transcriptionError)
    state.transcriptionText?.let { transcription ->
        Text("Transcription", fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 24.dp, bottom = 8.dp))
        SelectionContainer {
            Text(transcription, modifier = Modifier.fillMaxWidth().semantics { liveRegion = LiveRegionMode.Polite })
        }
    }
    StoryButton("Record another", true, Modifier.padding(top = 24.dp)) {
        player.stop()
        model.resetAfterUpload()
    }
}

@Composable
private fun SessionMessage(countdown: SessionCountdown) = when (countdown) {
    SessionCountdown.Hidden -> Unit
    is SessionCountdown.Warning -> Text(countdown.text, color = StorySecondary, fontFamily = FontFamily.Monospace, fontSize = 13.sp, modifier = Modifier.padding(bottom = 12.dp))
    is SessionCountdown.Critical -> Text(countdown.text, color = StoryError, fontWeight = FontWeight.SemiBold, fontFamily = FontFamily.Monospace, fontSize = 13.sp, modifier = Modifier.padding(bottom = 12.dp))
    SessionCountdown.Expired -> Text("Session expired. Enter your access code again before uploading.", color = StoryError, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, modifier = Modifier.padding(bottom = 12.dp))
}

@Composable
private fun ErrorText(message: String?) { if (message != null) Text(message, color = StoryError, modifier = Modifier.padding(top = 12.dp).semantics { liveRegion = LiveRegionMode.Assertive }) }

@Composable
private fun StoryButton(label: String, enabled: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Button(onClick, enabled = enabled, modifier = modifier.fillMaxWidth().height(52.dp), shape = RoundedCornerShape(8.dp),
        colors = ButtonDefaults.buttonColors(containerColor = StoryButton, contentColor = StoryPrimary, disabledContainerColor = StoryButton.copy(alpha = .55f), disabledContentColor = StoryPrimary.copy(alpha = .55f))) {
        if (label.endsWith("…")) CircularProgressIndicator(Modifier.padding(end = 8.dp).height(18.dp), strokeWidth = 2.dp, color = StoryPrimary)
        Text(label, fontWeight = FontWeight.SemiBold, maxLines = 1)
    }
}

@Composable
private fun PairedButtons(left: String, onLeft: () -> Unit, right: String, onRight: () -> Unit, enabled: Boolean = true) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        StoryButton(left, enabled, Modifier.weight(1f), onLeft)
        StoryButton(right, enabled, Modifier.weight(1f), onRight)
    }
}
