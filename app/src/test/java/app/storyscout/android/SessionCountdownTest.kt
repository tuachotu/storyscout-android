package app.storyscout.android

import app.storyscout.android.domain.SessionCountdown
import app.storyscout.android.domain.formatDuration
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SessionCountdownTest {
    private val now = 1_000_000L

    @Test fun hiddenAboveThirtyMinutes() = assertEquals(SessionCountdown.Hidden, SessionCountdown.at(now + 1_800_001, now))
    @Test fun warningAtThirtyMinutes() = assertTrue(SessionCountdown.at(now + 1_800_000, now) is SessionCountdown.Warning)
    @Test fun criticalAtFiveMinutes() = assertTrue(SessionCountdown.at(now + 300_000, now) is SessionCountdown.Critical)
    @Test fun expiredAtZero() = assertEquals(SessionCountdown.Expired, SessionCountdown.at(now, now))
    @Test fun formatsBeyondTwoHours() = assertEquals("02:03:04", formatDuration((2 * 3600 + 3 * 60 + 4) * 1000L))
}
