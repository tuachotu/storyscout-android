package app.storyscout.android

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import app.storyscout.android.ui.StoryScoutScreen
import app.storyscout.android.ui.StoryScoutViewModel
import app.storyscout.android.ui.StoryTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            StoryTheme {
                val model: StoryScoutViewModel = viewModel()
                val state = model.state.collectAsStateWithLifecycle().value
                var pendingStart by remember { mutableStateOf(false) }
                val microphonePermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
                    if (granted) app.storyscout.android.recording.RecordingService.command(this, app.storyscout.android.recording.RecordingService.ACTION_START)
                    else model.showError("Microphone permission was denied. Enable it in Settings to record your story.")
                }
                val requestMicrophoneOrStart = {
                    if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
                        app.storyscout.android.recording.RecordingService.command(this, app.storyscout.android.recording.RecordingService.ACTION_START)
                    } else microphonePermission.launch(Manifest.permission.RECORD_AUDIO)
                }
                val notificationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
                    if (pendingStart) { pendingStart = false; requestMicrophoneOrStart() }
                }
                StoryScoutScreen(state, model, onStart = {
                    if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                        pendingStart = true
                        notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                    } else requestMicrophoneOrStart()
                })
            }
        }
    }
}
