package app.storyscout.android.data.remote

import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import retrofit2.HttpException
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import java.io.File
import org.json.JSONObject

class ApiClient(baseUrl: String, client: OkHttpClient = OkHttpClient()) {
    private val moshi = Moshi.Builder().addLast(KotlinJsonAdapterFactory()).build()
    private val http = client
    val service: StoryScoutApi = Retrofit.Builder()
        .baseUrl(baseUrl)
        .client(client)
        .addConverterFactory(MoshiConverterFactory.create(moshi))
        .build()
        .create(StoryScoutApi::class.java)

    suspend fun uploadDirect(instructions: UploadInstructionsDto, file: File) {
        val body = file.asRequestBody(instructions.headers.entries
            .firstOrNull { it.key.equals("Content-Type", ignoreCase = true) }
            ?.value?.toMediaTypeOrNull())
        val builder = Request.Builder().url(instructions.url).method(instructions.method, body)
        instructions.headers.forEach { (name, value) -> builder.header(name, value) }
        http.newCall(builder.build()).execute().use { response ->
            if (!response.isSuccessful) throw UploadException(response.code)
        }
    }
}

class UploadException(val statusCode: Int) : Exception("The recording upload failed ($statusCode).")

fun Throwable.userMessage(): String = when (this) {
    is HttpException -> runCatching {
        JSONObject(response()?.errorBody()?.string().orEmpty()).getJSONObject("error").getString("message")
    }.getOrElse { "Request failed (${code()})." }
    else -> message ?: "Something went wrong."
}
