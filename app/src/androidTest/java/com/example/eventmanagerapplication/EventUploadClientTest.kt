package com.example.eventmanagerapplication

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.net.InetAddress
import java.net.ServerSocket
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

@RunWith(AndroidJUnit4::class)
class EventUploadClientTest {
    private val snapshot = EventUploadSnapshot("android-test-device-123", 7,
        listOf(EventWithSessions(Event(id = 1, name = "မြန်မာ Workshop", date = "20/10/2026",
            location = "Hall", organiser = "Alex", type = "Workshop", status = "Upcoming",
            amount = 25.0, contactEmail = "alex@example.com"),
            listOf(Session(id = 1, eventId = 1, name = "Opening", time = "09:00", type = "Presentation")))))

    private val acknowledgement = """{"success":true,"deviceId":"android-test-device-123","revision":7,"uploadedEvents":1,"uploadedSessions":1,"replayed":false}"""

    // A real local HTTP server exercises UTF-8 bytes, headers, response parsing
    // and failure handling without requiring Laragon or internet access.
    private fun respond(status: Int, body: String, action: (EventUploadClient) -> Unit): Pair<String, ByteArray> {
        val executor = Executors.newSingleThreadExecutor()
        ServerSocket(0, 1, InetAddress.getByName("127.0.0.1")).use { server ->
            server.soTimeout = 10_000
            val received = executor.submit<Pair<String, ByteArray>> {
                server.accept().use { socket ->
                    socket.soTimeout = 10_000
                    val input = socket.getInputStream()
                    val headerBytes = ArrayList<Byte>()
                    while (true) {
                        val next = input.read()
                        check(next != -1)
                        headerBytes.add(next.toByte())
                        if (headerBytes.size >= 4 && headerBytes.takeLast(4) == listOf<Byte>(13, 10, 13, 10)) break
                    }
                    val headers = headerBytes.toByteArray().toString(Charsets.US_ASCII)
                    val length = Regex("Content-Length: ([0-9]+)", RegexOption.IGNORE_CASE)
                        .find(headers)?.groupValues?.get(1)?.toInt() ?: 0
                    val request = input.readNBytes(length)
                    val response = body.toByteArray(Charsets.UTF_8)
                    socket.getOutputStream().apply {
                        write("HTTP/1.1 $status Response\r\nContent-Type: application/json\r\nContent-Length: ${response.size}\r\nConnection: close\r\n\r\n".toByteArray())
                        write(response)
                        flush()
                    }
                    headers to request
                }
            }
            try {
                action(EventUploadClient("http://127.0.0.1:${server.localPort}/"))
                return received.get(10, TimeUnit.SECONDS)
            } finally {
                executor.shutdownNow()
            }
        }
    }

    @Test fun postsFullUtf8SnapshotAndAcceptsMatchingAcknowledgement() {
        val (headers, bytes) = respond(200, acknowledgement) { it.upload(snapshot) }
        assertTrue(headers.startsWith("POST /uploadevent.php"))
        assertTrue(headers.lowercase().contains("content-type: application/json; charset=utf-8"))
        val body = JSONObject(bytes.toString(Charsets.UTF_8))
        assertEquals(snapshot.deviceId, body.getString("deviceId"))
        assertEquals(7L, body.getLong("revision"))
        assertEquals("မြန်မာ Workshop", body.getJSONArray("events").getJSONObject(0).getString("name"))
        assertEquals(1, body.getJSONArray("events").getJSONObject(0).getJSONArray("sessions").length())
    }

    @Test fun verifiesServiceBeforeAllowingUpload() {
        val (headers, _) = respond(200, """{"success":true,"status":"ready"}""") { it.checkService() }
        assertTrue(headers.startsWith("GET /health.php"))
    }

    @Test fun wrongRevisionCountsOrFractionalAcknowledgementAreNeverAccepted() {
        listOf(acknowledgement.replace("\"revision\":7", "\"revision\":6"),
            acknowledgement.replace("\"uploadedEvents\":1", "\"uploadedEvents\":0"),
            acknowledgement.replace("\"revision\":7", "\"revision\":7.5")).forEach { body ->
            respond(200, body) { client ->
                try {
                    client.upload(snapshot)
                    fail("Mismatched acknowledgement must not confirm a snapshot")
                } catch (exception: EventUploadException) {
                    assertTrue(exception.retryable)
                }
            }
        }
    }

    @Test fun permanentErrorsStopAndServiceFailuresRetry() {
        listOf(409 to false, 422 to false, 503 to true).forEach { (status, retry) ->
            respond(status, """{"success":false,"error":{"message":"Test error"}}""") { client ->
                try {
                    client.upload(snapshot)
                    fail("HTTP $status must fail")
                } catch (exception: EventUploadException) {
                    assertEquals(retry, exception.retryable)
                    assertEquals("Test error", exception.message)
                }
            }
        }
    }

    @Test fun htmlErrorPageDoesNotConfirmUpload() {
        respond(200, "<html>Laragon</html>") { client ->
            try {
                client.upload(snapshot)
                fail("An HTML page must not confirm a snapshot")
            } catch (exception: EventUploadException) {
                assertTrue(exception.retryable)
            }
        }
    }
}
