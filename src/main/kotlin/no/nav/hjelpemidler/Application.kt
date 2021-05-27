package no.nav.hjelpemidler

import io.ktor.server.netty.EngineMain
import io.ktor.application.Application
import io.ktor.application.call
import io.ktor.application.install
import io.ktor.auth.authenticate
import io.ktor.features.CallLogging
import io.ktor.features.ContentNegotiation
import io.ktor.http.ContentType
import io.ktor.http.HeadersBuilder
import io.ktor.jackson.JacksonConverter
import io.ktor.request.header
import io.ktor.request.path
import io.ktor.request.receive
import org.slf4j.event.Level
import io.ktor.response.respondText
import io.ktor.routing.get
import io.ktor.routing.routing
import mu.KotlinLogging
import org.json.simple.JSONObject
import java.util.UUID
import io.ktor.application.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.util.*
import no.nav.hjelpemidler.configuration.Configuration
import no.nav.hjelpemidler.service.azure.AzureClient
import java.time.LocalDateTime

private val logg = KotlinLogging.logger {}
private val sikkerlogg = KotlinLogging.logger("tjenestekall")

private val azClient = AzureClient(Configuration.azureAD["AZURE_TENANT_BASEURL"]!! + "/" + Configuration.azureAD["AZURE_APP_TENANT_ID"]!!, Configuration.azureAD["AZURE_APP_CLIENT_ID"]!!, Configuration.azureAD["AZURE_APP_CLIENT_SECRET"]!!)
private var azTokenTimeout: LocalDateTime? = null
private var azToken: String? = null

fun checkAndRefreshAzureAdToken() {
    if (azTokenTimeout == null || azTokenTimeout?.isBefore(LocalDateTime.now()) == true) {
        val token = azClient.getToken(Configuration.azureAD["AZURE_AD_SCOPE"]!!)
        azToken = token.accessToken
        azTokenTimeout = LocalDateTime.now().plusSeconds(token.expiresIn - 60 /* 60s leeway => renew 60s before token expiration */)
    }
}

fun main(args: Array<String>) {
    logg.info("OEBS api starting...")
    EngineMain.main(args)
    logg.info("OEBS api stopping...")
}

@KtorExperimentalAPI
fun Application.module() {
    installAuthentication()

    install(ContentNegotiation) {
        register(ContentType.Application.Json, JacksonConverter())
    }

    install(CallLogging) {
        level = Level.TRACE
        filter { call ->
            !call.request.path().startsWith("/internal") &&
            !call.request.path().startsWith("/isalive") &&
            !call.request.path().startsWith("/isready")
        }
    }

    /*install(MicrometerMetrics) {
        registry = PrometheusMeterRegistry(
            PrometheusConfig.DEFAULT,
            CollectorRegistry.defaultRegistry,
            Clock.SYSTEM
        )
        meterBinders = listOf(
            ClassLoaderMetrics(),
            JvmMemoryMetrics(),
            JvmGcMetrics(),
            ProcessorMetrics(),
            JvmThreadMetrics(),
            LogbackMetrics(),
            KafkaConsumerMetrics()
        )
    }*/

    routing {
        // Endpoints for Kubernetes unauthenticated health checks

        get("/isalive") {
            call.respondText("ALIVE", ContentType.Text.Plain)
        }

        get("/isready") {
            // if (!ready.get()) return@get call.respondText("NOT READY", ContentType.Text.Plain, HttpStatusCode.ServiceUnavailable)
            call.respondText("READY", ContentType.Text.Plain)
        }

        /* get("/metrics") {
            val names = call.request.queryParameters.getAll("name[]")?.toSet() ?: emptySet()

            call.respondTextWriter(ContentType.parse(TextFormat.CONTENT_TYPE_004)) {
                TextFormat.write004(this, CollectorRegistry.defaultRegistry.filteredMetricFamilySamples(names))
            }
        } */

        // Authenticated database proxy requests
        authenticate("aad") {
            get("/test") {
                checkAndRefreshAzureAdToken()

                if (Configuration.application["APP_PROFILE"]!! == "dev") {
                    log.info("DEBUG: azure ad token for proxy comm is: $azToken")
                }

                val url = "https://hm-oebs-api-proxy.dev-fss-pub.nais.io/test"
                val callId = call.request.header(HttpHeaders.NavCallId) ?: UUID.randomUUID().toString()
                try {
                    val response = httpClient().get<HttpResponse>(url) {
                        val proxiedHeaders = call.request.headers.filter { key, _ ->
                            !key.equals(
                                HttpHeaders.Authorization,
                                ignoreCase = true
                            ) && !key.equals("X-Correlation-ID", ignoreCase = true)
                            && !key.equals(HttpHeaders.ContentLength, ignoreCase = true)
                            && !key.equals(HttpHeaders.TransferEncoding, ignoreCase = true)
                        }

                        val headersBuilder = HeadersBuilder()
                        headersBuilder.appendAll(proxiedHeaders)
                        headersBuilder.append(HttpHeaders.Authorization, "Bearer $azToken")
                        headersBuilder.append("X-Correlation-ID", callId)
                        headers.appendAll(headersBuilder)

                        method = HttpMethod.Post
                        val receiveString = call.receive<JSONObject>()
                        body = receiveString
                    }
                    call.pipeResponse(response)
                } catch (cause: Throwable) {
                    println("Feil i kall mot oebs api proxy -> $cause")
                    cause.printStackTrace()
                }

            }
        }

        // FIXME: Remove non-authenticated endpoint used for testing in dev
        get("/test2") {
            if (Configuration.application["APP_PROFILE"] != "dev") {
                return@get
            }

            checkAndRefreshAzureAdToken()

            if (Configuration.application["APP_PROFILE"]!! == "dev") {
                log.info("DEBUG: azure ad token for proxy comm is: $azToken")
            }

            val url = "https://hm-oebs-api-proxy.dev-fss-pub.nais.io/test"
            val callId = call.request.header(HttpHeaders.NavCallId) ?: UUID.randomUUID().toString()
            try {
                val response = httpClient().get<HttpResponse>(url) {
                    val proxiedHeaders = call.request.headers.filter { key, _ ->
                        !key.equals(
                            HttpHeaders.Authorization,
                            ignoreCase = true
                        ) && !key.equals("X-Correlation-ID", ignoreCase = true)
                        && !key.equals(HttpHeaders.ContentLength, ignoreCase = true)
                        && !key.equals(HttpHeaders.TransferEncoding, ignoreCase = true)
                    }

                    val headersBuilder = HeadersBuilder()
                    headersBuilder.appendAll(proxiedHeaders)
                    headersBuilder.append(HttpHeaders.Authorization, "Bearer $azToken")
                    headersBuilder.append("X-Correlation-ID", callId)
                    headers.appendAll(headersBuilder)

                    method = HttpMethod.Post
                    val receiveString = call.receive<JSONObject>()
                    body = receiveString
                }
                call.pipeResponse(response)
            } catch (cause: Throwable) {
                println("Feil i kall mot oebs api proxy -> $cause")
                cause.printStackTrace()
            }

        }
    }
}