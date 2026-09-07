package app.storyscout.android

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.storyscout.android.data.local.RecordingEntity
import app.storyscout.android.data.local.StoryScoutDatabase
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RecordingPersistenceTest {
    @Test fun persistsUploadRecoveryAndMarksInterruptedCapture() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), StoryScoutDatabase::class.java).build()
        val dao = db.recordings()
        dao.save(RecordingEntity(
            localId = "local-1", filePath = "/private/story.m4a", originalFilename = "story.m4a",
            createdAtMillis = 1, state = "RECORDING", serverRecordingId = "recording_123",
            uploadUrl = "https://upload.test/object", uploadMethod = "PUT",
            uploadHeaders = "{\"Content-Type\":\"audio/mp4\"}", uploadExpiresAtMillis = 2,
        ))
        dao.recoverInterruptedCaptures()
        val recovered = dao.get("local-1")!!
        assertEquals("FAILED", recovered.state)
        assertTrue(recovered.possibleGap)
        assertEquals("recording_123", recovered.serverRecordingId)
        assertEquals("https://upload.test/object", recovered.uploadUrl)
        db.close()
    }
}
