package app.storyscout.android.recording

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaRecorder
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.os.SystemClock
import android.os.storage.StorageManager
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import app.storyscout.android.MainActivity
import app.storyscout.android.R
import app.storyscout.android.StoryScoutApplication
import app.storyscout.android.data.local.RecordingEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import java.io.File
import java.util.UUID

class RecordingService : Service(), MediaRecorder.OnErrorListener {
    private val repository by lazy { (application as StoryScoutApplication).repository }
    private var recorder: MediaRecorder? = null
    private var current: RecordingEntity? = null
    private var startedAtElapsed = 0L
    private var accumulatedMillis = 0L
    private var wakeLock: PowerManager.WakeLock? = null
    private lateinit var audioManager: AudioManager
    private var focusRequest: AudioFocusRequest? = null

    override fun onCreate() {
        super.onCreate()
        createChannel()
        audioManager = getSystemService(AudioManager::class.java)
        wakeLock = getSystemService(PowerManager::class.java)
            .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "StoryScout:Recording").apply { setReferenceCounted(false) }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> startRecording()
            ACTION_PAUSE -> pauseRecording("Recording paused.", false)
            ACTION_RESUME -> resumeRecording()
            ACTION_STOP -> stopRecording(null, false)
        }
        return START_NOT_STICKY
    }

    private fun startRecording() {
        if (recorder != null) return
        val available = getSystemService(StorageManager::class.java).getAllocatableBytes(StorageManager.UUID_DEFAULT)
        if (available < MIN_FREE_BYTES) {
            RecordingRuntime.publish(RecordingSnapshot(message = "Not enough free storage to start recording."))
            stopSelf(); return
        }
        if (!requestAudioFocus()) {
            RecordingRuntime.publish(RecordingSnapshot(message = "The microphone is currently in use by another app."))
            stopSelf(); return
        }
        val id = UUID.randomUUID().toString()
        val directory = File(filesDir, "pending-recordings").apply { mkdirs() }
        val file = File(directory, "story-$id.m4a")
        try {
            @Suppress("DEPRECATION")
            val mediaRecorder = if (Build.VERSION.SDK_INT >= 31) MediaRecorder(this) else MediaRecorder()
            mediaRecorder.setAudioSource(MediaRecorder.AudioSource.MIC)
            mediaRecorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            mediaRecorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            mediaRecorder.setAudioChannels(1)
            mediaRecorder.setAudioSamplingRate(44_100)
            mediaRecorder.setAudioEncodingBitRate(96_000)
            mediaRecorder.setOutputFile(file.absolutePath)
            mediaRecorder.setOnErrorListener(this)
            mediaRecorder.prepare()
            val entity = RecordingEntity(
                localId = id, filePath = file.absolutePath, originalFilename = file.name,
                createdAtMillis = System.currentTimeMillis(), state = "RECORDING",
            )
            current = entity
            runBlocking(Dispatchers.IO) { repository.save(entity) }
            startForeground(NOTIFICATION_ID, notification(false))
            wakeLock?.acquire(3 * 60 * 60 * 1000L)
            mediaRecorder.start()
            recorder = mediaRecorder
            accumulatedMillis = 0
            startedAtElapsed = SystemClock.elapsedRealtime()
            publish(CaptureState.RECORDING)
        } catch (error: Exception) {
            file.delete()
            abandonAudioFocus()
            RecordingRuntime.publish(RecordingSnapshot(message = "Recording could not start: ${error.message}"))
            stopSelf()
        }
    }

    private fun pauseRecording(message: String?, possibleGap: Boolean) {
        if (RecordingRuntime.state.value.state != CaptureState.RECORDING) return
        try {
            recorder?.pause()
            accumulatedMillis += SystemClock.elapsedRealtime() - startedAtElapsed
            current = current?.copy(possibleGap = current!!.possibleGap || possibleGap, statusMessage = message)
            current?.let { runBlocking(Dispatchers.IO) { repository.save(it.copy(durationMillis = accumulatedMillis, state = "PAUSED")) } }
            publish(CaptureState.PAUSED, message)
            getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, notification(true))
        } catch (error: Exception) {
            stopRecording("Recording stopped after an audio interruption. A gap may be present.", true)
        }
    }

    private fun resumeRecording() {
        if (RecordingRuntime.state.value.state != CaptureState.PAUSED) return
        try {
            recorder?.resume()
            startedAtElapsed = SystemClock.elapsedRealtime()
            current?.let { runBlocking(Dispatchers.IO) { repository.save(it.copy(state = "RECORDING")) } }
            publish(CaptureState.RECORDING)
            getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, notification(false))
        } catch (error: Exception) {
            stopRecording("Recording could not resume. A gap may be present.", true)
        }
    }

    private fun stopRecording(message: String?, possibleGap: Boolean) {
        val entity = current ?: return
        val wasRecording = RecordingRuntime.state.value.state == CaptureState.RECORDING
        if (wasRecording) accumulatedMillis += SystemClock.elapsedRealtime() - startedAtElapsed
        try { recorder?.stop() } catch (_: RuntimeException) { }
        recorder?.release(); recorder = null
        val file = File(entity.filePath)
        val final = entity.copy(
            sizeBytes = file.takeIf(File::isFile)?.length() ?: 0,
            durationMillis = accumulatedMillis,
            state = if (file.length() > 0) "SAVED" else "FAILED",
            possibleGap = entity.possibleGap || possibleGap,
            statusMessage = message,
        )
        current = final
        runBlocking(Dispatchers.IO) { repository.save(final) }
        abandonAudioFocus()
        if (wakeLock?.isHeld == true) wakeLock?.release()
        publish(CaptureState.READY, message)
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun publish(state: CaptureState, message: String? = null) {
        RecordingRuntime.publish(
            RecordingSnapshot(
                state = state,
                localId = current?.localId,
                filePath = current?.filePath,
                elapsedMillis = accumulatedMillis,
                runningSinceElapsedRealtime = startedAtElapsed.takeIf { state == CaptureState.RECORDING },
                possibleGap = current?.possibleGap == true,
                message = message,
            )
        )
    }

    private val focusListener = AudioManager.OnAudioFocusChangeListener { change ->
        if (change <= AudioManager.AUDIOFOCUS_LOSS_TRANSIENT) pauseRecording(
            "Recording was paused by an audio interruption. Tap Resume when you are ready.", true,
        )
    }

    private fun requestAudioFocus(): Boolean {
        val request = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_EXCLUSIVE)
            .setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build())
            .setOnAudioFocusChangeListener(focusListener).build().also { focusRequest = it }
        val result = audioManager.requestAudioFocus(request)
        return result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
    }

    private fun abandonAudioFocus() {
        focusRequest?.let(audioManager::abandonAudioFocusRequest)
    }

    private fun notification(paused: Boolean): Notification {
        val open = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val toggleAction = if (paused) ACTION_RESUME else ACTION_PAUSE
        val toggleLabel = if (paused) "Resume" else "Pause"
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_btn_speak_now).setContentTitle("StoryScout")
            .setContentText(if (paused) "Recording paused" else "Recording your story")
            .setOngoing(true).setOnlyAlertOnce(true).setContentIntent(open)
            .addAction(0, toggleLabel, serviceIntent(toggleAction, 1))
            .addAction(0, "Stop", serviceIntent(ACTION_STOP, 2)).build()
    }

    private fun serviceIntent(action: String, code: Int) = PendingIntent.getService(
        this, code, Intent(this, RecordingService::class.java).setAction(action),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    private fun createChannel() {
        val channel = NotificationChannel(CHANNEL_ID, getString(R.string.recording_channel_name), NotificationManager.IMPORTANCE_LOW)
            .apply { description = getString(R.string.recording_channel_description); setSound(null, null) }
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    override fun onError(mr: MediaRecorder?, what: Int, extra: Int) = stopRecording("Recording encountered an error. A gap may be present.", true)
    override fun onBind(intent: Intent?): IBinder? = null
    override fun onDestroy() { if (recorder != null) stopRecording("Recording service stopped unexpectedly. A gap may be present.", true); super.onDestroy() }

    companion object {
        const val ACTION_START = "app.storyscout.recording.START"
        const val ACTION_PAUSE = "app.storyscout.recording.PAUSE"
        const val ACTION_RESUME = "app.storyscout.recording.RESUME"
        const val ACTION_STOP = "app.storyscout.recording.STOP"
        private const val CHANNEL_ID = "storyscout_recording"
        private const val NOTIFICATION_ID = 2401
        private const val MIN_FREE_BYTES = 32L * 1024 * 1024
        fun command(context: Context, action: String) {
            val intent = Intent(context, RecordingService::class.java).setAction(action)
            if (action == ACTION_START) context.startForegroundService(intent) else context.startService(intent)
        }
    }
}
