package app.storyscout.android

import app.storyscout.android.data.local.RecordingEntity
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DuplicatePreventionTest {
    private fun recording(serverId: String?) = RecordingEntity(
        localId = "local", filePath = "/private/story.m4a", originalFilename = "story.m4a",
        createdAtMillis = 1, state = "SAVED", serverRecordingId = serverId,
    )

    @Test fun createsMetadataOnlyWhenNoServerIdWasPersisted() {
        assertTrue(recording(null).requiresServerRecord())
        assertFalse(recording("recording_123").requiresServerRecord())
    }
}
