package app.storyscout.android.data.remote

import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.Path

interface StoryScoutApi {
    @POST("access-sessions")
    suspend fun createAccessSession(@Body body: AccessRequest): AccessSessionDto

    @POST("recordings")
    suspend fun createRecording(
        @Header("Authorization") authorization: String,
        @Body body: CreateRecordingRequest,
    ): RecordingUploadDto

    @GET("recordings/{recordingId}")
    suspend fun getRecording(
        @Path("recordingId") recordingId: String,
        @Header("Authorization") authorization: String,
    ): RecordingDto

    @POST("recordings/{recordingId}/complete")
    suspend fun completeRecording(
        @Path("recordingId") recordingId: String,
        @Header("Authorization") authorization: String,
    ): RecordingDto
}
