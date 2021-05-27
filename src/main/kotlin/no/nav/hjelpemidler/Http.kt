package no.nav.hjelpemidler

import com.fasterxml.jackson.databind.DeserializationFeature
import io.ktor.application.*
import io.ktor.client.*
import io.ktor.client.engine.apache.*
import io.ktor.client.features.json.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.http.content.*
import io.ktor.response.*
import io.ktor.util.*
import io.ktor.utils.io.*

@KtorExperimentalAPI
fun httpClient() = HttpClient(Apache) {
    install(JsonFeature) {
        serializer = JacksonSerializer { configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false) }
    }
}

suspend fun ApplicationCall.pipeResponse(response: HttpResponse) {
    val proxiedHeaders = response.headers
    val contentType = proxiedHeaders[HttpHeaders.ContentType]
    val contentLength = proxiedHeaders[HttpHeaders.ContentLength]

    respond(object : OutgoingContent.WriteChannelContent() {
        override val contentLength: Long? = contentLength?.toLong()
        override val contentType: ContentType? = contentType?.let { ContentType.parse(it) }
        override val headers: Headers = Headers.build {
            appendAll(proxiedHeaders.filter { key, _ ->
                !key.equals(
                    HttpHeaders.ContentType,
                    ignoreCase = true
                ) && !key.equals(HttpHeaders.ContentLength, ignoreCase = true)
                    && !key.equals(HttpHeaders.TransferEncoding, ignoreCase = true)
            })
        }
        override val status: HttpStatusCode? = response.status
        override suspend fun writeTo(channel: ByteWriteChannel) {

            response.content.copyAndClose(channel)
        }
    })
}

val HttpHeaders.NavCallId: String
    get() = "Nav-Call-Id"
