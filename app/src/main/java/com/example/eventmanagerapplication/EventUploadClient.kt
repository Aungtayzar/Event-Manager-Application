package com.example.eventmanagerapplication

import org.json.JSONException
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL

class EventUploadException(message: String, val retryable: Boolean) : IOException(message)

class EventUploadClient(private val baseUrl: String = BuildConfig.EVENT_API_BASE_URL) {
    init {
        val uri = URI(baseUrl)
        require(uri.host != null && uri.userInfo == null && uri.query == null && uri.fragment == null &&
            (uri.scheme == "https" || (BuildConfig.DEBUG && uri.scheme == "http"))) {
            "Configure an HTTPS eventApiBaseUrl for release builds."
        }
    }

    fun checkService() {
        val response = request("health.php")
        if (response.opt("success") != true || response.optString("status") != "ready") {
            throw EventUploadException("The upload service is not ready. Check service(eg. xampp or Laragon) and the database.", true)
        }
    }

    fun upload(snapshot: EventUploadSnapshot) {
        val body = snapshot.toJson().toString().toByteArray(Charsets.UTF_8)
        if (body.size > 10 * 1024 * 1024) {
            throw EventUploadException("The upload exceeds the API limit of 10 MiB.", false)
        }
        val response = request("uploadevent.php", body)
        // An HTTP 200 alone must never mark local data as synchronized.
        if (response.opt("success") != true || response.opt("deviceId") != snapshot.deviceId ||
            !matchesInteger(response.opt("revision"), snapshot.revision) ||
            !matchesInteger(response.opt("uploadedEvents"), snapshot.events.size.toLong()) ||
            !matchesInteger(response.opt("uploadedSessions"), snapshot.sessionCount.toLong())) {
            throw EventUploadException("The server did not confirm the uploaded snapshot. Try again.", true)
        }
    }

    private fun matchesInteger(value: Any?, expected: Long): Boolean =
        (value is Int || value is Long) && (value as Number).toLong() == expected

    private fun request(endpoint: String, body: ByteArray? = null): JSONObject {
        val connection = URL(baseUrl.trimEnd('/') + "/" + endpoint).openConnection() as HttpURLConnection
        try {
            connection.connectTimeout = 10_000
            connection.readTimeout = 30_000
            connection.instanceFollowRedirects = false
            connection.setRequestProperty("Accept", "application/json")
            if (body != null) {
                connection.requestMethod = "POST"
                connection.doOutput = true
                connection.setRequestProperty("Content-Type", "application/json; charset=utf-8")
                connection.setFixedLengthStreamingMode(body.size)
                connection.outputStream.use { it.write(body) }
            }
            val status = connection.responseCode
            val retryable = status == 408 || status == 429 || status >= 500
            val stream = if (status in 200..299) connection.inputStream else connection.errorStream
            val bytes = stream?.use { it.readNBytes(64 * 1024 + 1) } ?: byteArrayOf()
            if (bytes.size > 64 * 1024) throw EventUploadException("The server response is too large.", true)
            val response = try {
                JSONObject(bytes.toString(Charsets.UTF_8))
            } catch (_: JSONException) {
                throw EventUploadException("The API returned an unreadable response (HTTP $status). Check the API address.", retryable || status in 200..299)
            }
            if (status != HttpURLConnection.HTTP_OK) {
                val message = response.optJSONObject("error")?.optString("message")
                    ?.takeIf { it.isNotBlank() }?.take(500) ?: "Upload request failed (HTTP $status)."
                throw EventUploadException(message, retryable)
            }
            return response
        } finally {
            connection.disconnect()
        }
    }
}
