package app.storyscout.android

import app.storyscout.android.data.remote.AccessRequest
import app.storyscout.android.data.remote.ApiClient
import app.storyscout.android.data.remote.CreateRecordingRequest
import app.storyscout.android.data.remote.UploadInstructionsDto
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import java.io.File
import retrofit2.HttpException
import app.storyscout.android.data.remote.userMessage

class ApiClientContractTest {
    private lateinit var server: MockWebServer
    private lateinit var client: ApiClient

    @Before fun setUp() { server = MockWebServer().also { it.start() }; client = ApiClient(server.url("api/v1/").toString(), OkHttpClient()) }
    @After fun tearDown() = server.shutdown()

    @Test fun accessSessionContract() = runTest {
        server.enqueue(MockResponse().setResponseCode(201).setHeader("Content-Type", "application/json").setBody("""{"accessToken":"token","displayName":"Vikrant","expiresAt":"2026-09-06T20:00:00.000Z"}"""))
        client.service.createAccessSession(AccessRequest("00000000-0000-0000-0000-000000000000"))
        val request = server.takeRequest()
        assertEquals("POST", request.method); assertEquals("/api/v1/access-sessions", request.path)
        assertTrue(request.body.readUtf8().contains("\"guid\""))
    }

    @Test fun createUploadAndCompleteHonorContract() = runTest {
        val uploadUrl = server.url("direct-upload").toString()
        server.enqueue(MockResponse().setResponseCode(201).setHeader("Content-Type", "application/json").setBody("""{"recording":{"recordingId":"recording_123","participantName":"Vikrant","createdAt":"2026-09-06T12:00:00Z","completedAt":null,"storageState":"awaitingUpload","originalFilename":"story.m4a","contentType":"audio/mp4","sizeBytes":null},"upload":{"url":"$uploadUrl","method":"PUT","headers":{"Content-Type":"audio/mp4","x-amz-server-side-encryption":"AES256"},"expiresAt":"2026-09-06T12:15:00Z"}}"""))
        val created = client.service.createRecording("Bearer token", CreateRecordingRequest("story.m4a", "audio/mp4", 4))
        val create = server.takeRequest()
        assertEquals("Bearer token", create.getHeader("Authorization")); assertEquals("/api/v1/recordings", create.path)
        assertTrue(create.body.readUtf8().contains("\"sizeBytes\":4"))
        server.enqueue(MockResponse().setResponseCode(200))
        val file = File.createTempFile("story", ".m4a").apply { writeBytes(byteArrayOf(1, 2, 3, 4)) }
        client.uploadDirect(created.upload, file)
        val upload = server.takeRequest()
        assertEquals("PUT", upload.method); assertEquals("audio/mp4", upload.getHeader("Content-Type")); assertEquals("AES256", upload.getHeader("x-amz-server-side-encryption"))
        file.delete()
        server.enqueue(MockResponse().setResponseCode(200).setHeader("Content-Type", "application/json").setBody("""{"recordingId":"recording_123","participantName":"Vikrant","createdAt":"2026-09-06T12:00:00Z","completedAt":"2026-09-06T12:01:00Z","storageState":"ready","originalFilename":"story.m4a","contentType":"audio/mp4","sizeBytes":4}"""))
        client.service.completeRecording("recording_123", "Bearer token")
        val complete = server.takeRequest()
        assertEquals("POST", complete.method); assertEquals("/api/v1/recordings/recording_123/complete", complete.path); assertEquals("Bearer token", complete.getHeader("Authorization"))
    }

    @Test fun fetchTranscriptionHonorsContractAndParsesUnicode() = runTest {
        server.enqueue(
            MockResponse().setResponseCode(200).setHeader("Content-Type", "application/json").setBody(
                """{"recording":{"recordingId":"recording_123","participantName":"Chotu","createdAt":"2026-09-07T18:01:34.261Z","completedAt":"2026-09-07T18:01:35.590Z","storageState":"ready","originalFilename":"story.webm","contentType":"audio/webm","sizeBytes":310068},"transcription":{"text":"जीना के थे बेबी दो।\n"}}""",
            ),
        )

        val response = client.service.getRecordingTranscription("recording_123", "Bearer token")
        val request = server.takeRequest()

        assertEquals("GET", request.method)
        assertEquals("/api/v1/recordings/recording_123/transcription", request.path)
        assertEquals("Bearer token", request.getHeader("Authorization"))
        assertEquals("recording_123", response.recording.recordingId)
        assertEquals("जीना के थे बेबी दो।\n", response.transcription.text)
    }

    @Test fun fetchTranscriptionSurfacesBackendError() = runTest {
        server.enqueue(
            MockResponse().setResponseCode(404).setHeader("Content-Type", "application/json").setBody(
                """{"error":{"code":"TRANSCRIPTION_NOT_FOUND","message":"Transcription is not ready yet.","requestId":"request_123"}}""",
            ),
        )

        try {
            client.service.getRecordingTranscription("recording_123", "Bearer token")
            fail("Expected an HTTP error")
        } catch (error: HttpException) {
            assertEquals("Request failed (404).", error.userMessage())
        }
    }
}
