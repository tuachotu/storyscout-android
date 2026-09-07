package app.storyscout.android.recording

import android.media.MediaPlayer

class PlaybackController {
    private var player: MediaPlayer? = null
    val isPlaying: Boolean get() = player?.isPlaying == true

    fun toggle(path: String, onChanged: () -> Unit) {
        if (isPlaying) { stop(); onChanged(); return }
        stop()
        player = MediaPlayer().apply {
            setDataSource(path)
            setOnCompletionListener { stop(); onChanged() }
            prepare()
            start()
        }
        onChanged()
    }

    fun stop() { player?.run { if (isPlaying) stop(); release() }; player = null }
}
