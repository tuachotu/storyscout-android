package app.storyscout.android.data.remote

import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

object ApiConfiguration {
    private val loopbackHosts = setOf("localhost", "127.0.0.1", "::1", "10.0.2.2")

    fun normalizedBaseUrl(value: String, rejectLoopback: Boolean): String {
        val trimmed = value.trim().trimEnd('/') + "/"
        val url = trimmed.toHttpUrlOrNull() ?: throw IllegalArgumentException("Invalid API base URL")
        require(url.scheme == "http" || url.scheme == "https") { "Invalid API scheme" }
        require(!rejectLoopback || url.host !in loopbackHosts) { "Loopback API URL is not allowed" }
        return url.toString()
    }
}
