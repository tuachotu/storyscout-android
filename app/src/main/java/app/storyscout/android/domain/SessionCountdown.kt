package app.storyscout.android.domain

import kotlin.math.ceil

sealed interface SessionCountdown {
    data object Hidden : SessionCountdown
    data class Warning(val text: String) : SessionCountdown
    data class Critical(val text: String) : SessionCountdown
    data object Expired : SessionCountdown

    companion object {
        const val WARNING_MILLIS = 30 * 60 * 1000L
        const val CRITICAL_MILLIS = 5 * 60 * 1000L

        fun at(expiresAtMillis: Long, nowMillis: Long): SessionCountdown {
            val remaining = expiresAtMillis - nowMillis
            if (remaining <= 0) return Expired
            if (remaining > WARNING_MILLIS) return Hidden
            val seconds = ceil(remaining / 1000.0).toLong()
            val text = "Session expires in %02d:%02d".format(seconds / 60, seconds % 60)
            return if (remaining <= CRITICAL_MILLIS) Critical(text) else Warning(text)
        }
    }
}

fun formatDuration(durationMillis: Long): String {
    val totalSeconds = durationMillis.coerceAtLeast(0) / 1000
    return "%02d:%02d:%02d".format(totalSeconds / 3600, (totalSeconds / 60) % 60, totalSeconds % 60)
}
