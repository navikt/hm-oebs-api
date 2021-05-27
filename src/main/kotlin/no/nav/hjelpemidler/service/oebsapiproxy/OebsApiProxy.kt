package no.nav.hjelpemidler.service.oebsapiproxy

import com.fasterxml.jackson.annotation.JsonFormat
import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.readValue
import mu.KotlinLogging
import no.nav.hjelpemidler.configuration.Configuration
import no.nav.hjelpemidler.service.azure.AzureClient
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.*
import kotlin.time.Duration
import kotlin.time.ExperimentalTime
import kotlin.time.measureTime

private val logg = KotlinLogging.logger {}
private val sikkerlogg = KotlinLogging.logger("tjenestekall")

private val azClient = AzureClient(Configuration.azureAD["AZURE_TENANT_BASEURL"]!! + "/" + Configuration.azureAD["AZURE_APP_TENANT_ID"]!!, Configuration.azureAD["AZURE_APP_CLIENT_ID"]!!, Configuration.azureAD["AZURE_APP_CLIENT_SECRET"]!!)
private var azTokenTimeout: LocalDateTime? = null
private var azToken: String? = null

@ExperimentalTime
class OebsApiProxy {

    private val mapper = ObjectMapper()

    init {
        mapper.registerModule(JavaTimeModule())
    }

    fun batchQueryVedtakResultat(reqs: List<Request>): List<Response> {
        if (Configuration.application["APP_PROFILE"] != "prod") logg.info("DEBUG: batchQueryVedtakResultat: reqs=$reqs")

        var results: List<Response>? = null
        val elapsed: Duration = measureTime {
            val json: String = mapper.writeValueAsString(reqs.toTypedArray())

            if (azTokenTimeout == null || azTokenTimeout?.isBefore(LocalDateTime.now()) == true) {
                val token = azClient.getToken(Configuration.azureAD["AZURE_AD_SCOPE"]!!)
                azToken = token.accessToken
                azTokenTimeout = LocalDateTime.now()
                    .plusSeconds(token.expiresIn - 60 /* 60s leeway => renew 60s before token expiration */)
            }

            val authToken = azToken!!
            val url = Configuration.oebsApiProxy["OEBS_API_PROXY_URL"]!! + "/vedtak-resultat"

            if (Configuration.application["APP_PROFILE"] != "prod") logg.info("DEBUG: Making proxy request with url: $url and json: $json. Token: [MASKED]")

            // Execute request towards graphql API server
            val client: HttpClient = HttpClient.newHttpClient()
            val request: HttpRequest = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Content-Type", "application/json; charset=UTF-8")
                .header("Accept", "application/json")
                .header("X-Correlation-ID", UUID.randomUUID().toString())
                .header("Authorization", "Bearer $authToken")
                .POST(HttpRequest.BodyPublishers.ofString(json))
                .build()
            val httpResponse = client.send(request, HttpResponse.BodyHandlers.ofString())

            // Check response codes
            if (httpResponse.statusCode() < 200 || httpResponse.statusCode() >= 300) {
                val statusCode = httpResponse.statusCode()
                throw Exception("invalid response status code when requesting infotrygd data: req=$reqs statusCode=$statusCode responseBody: ${httpResponse.body()}")
            }

            val jsonResp: String = httpResponse.body()
            if (Configuration.application["APP_PROFILE"] != "prod") logg.info("DEBUG: received response pre-parsing: $jsonResp")

            results = mapper.readValue(jsonResp)
        }

        if (Configuration.application["APP_PROFILE"] != "prod") logg.info("DEBUG: Response received from infotrygd: $results. Total request time elapsed: ${elapsed.inMilliseconds}")

        return results!!.toList()
    }

    data class Request(
        @JsonProperty("id")
        val id: String,
    )

    data class Response(
        @JsonProperty("test1")
        val test1: Request,

        @JsonFormat(shape = JsonFormat.Shape.STRING)
        @JsonProperty("test2")
        val test2: LocalDate? = null, // null initialization required for Klaxon deserialization if not mentioned in response (due to null)
    )
}
