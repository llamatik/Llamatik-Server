package com.llamatik.plugins

import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import io.ktor.server.testing.testApplication
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class HttpCompressionTest {

    private val payload = "data: {\"event\":\"delta\",\"text\":\"hello\"}\n\n".repeat(100)

    @Test
    fun `SSE responses are never compressed so deltas are not buffered`() = testApplication {
        application {
            configureHTTP()
            routing {
                get("/sse") { call.respondText(payload, ContentType.Text.EventStream) }
            }
        }
        val response = client.get("/sse") { header(HttpHeaders.AcceptEncoding, "gzip, deflate") }
        assertNull(response.headers[HttpHeaders.ContentEncoding])
    }

    @Test
    fun `regular responses are still compressed`() = testApplication {
        application {
            configureHTTP()
            routing {
                get("/text") { call.respondText(payload, ContentType.Text.Plain) }
            }
        }
        val response = client.get("/text") { header(HttpHeaders.AcceptEncoding, "gzip") }
        assertEquals("gzip", response.headers[HttpHeaders.ContentEncoding])
    }
}
