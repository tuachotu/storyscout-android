package app.storyscout.android

import app.storyscout.android.data.remote.ApiConfiguration
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class ApiConfigurationTest {
    @Test fun normalizesTrailingSlash() {
        assertEquals("https://story-scout.app/api/v1/", ApiConfiguration.normalizedBaseUrl("https://story-scout.app/api/v1", true))
    }

    @Test fun releaseRejectsLoopbackHosts() {
        listOf("localhost", "127.0.0.1", "10.0.2.2", "[::1]").forEach { host ->
            assertThrows(IllegalArgumentException::class.java) { ApiConfiguration.normalizedBaseUrl("http://$host:3001/api/v1/", true) }
        }
    }
}
