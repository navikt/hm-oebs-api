package no.nav.hjelpemidler

import com.auth0.jwk.JwkProviderBuilder
import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.databind.DeserializationFeature
import io.ktor.application.*
import io.ktor.auth.*
import io.ktor.auth.jwt.*
import io.ktor.client.*
import io.ktor.client.engine.apache.*
import io.ktor.client.request.get
import io.ktor.client.features.json.*
import kotlinx.coroutines.runBlocking
import no.nav.hjelpemidler.configuration.Configuration
import org.apache.http.impl.conn.SystemDefaultRoutePlanner
import java.net.ProxySelector
import java.net.URL
import java.util.concurrent.TimeUnit

private fun httpClientWithProxy() = HttpClient(Apache) {
    install(JsonFeature) {
        serializer = JacksonSerializer { configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false) }
    }
    engine {
        customizeClient {
            setRoutePlanner(SystemDefaultRoutePlanner(ProxySelector.getDefault()))
        }
    }
}

fun Application.installAuthentication() {
    // Load Azure AD config for rest client authentication
    var aadConfig: AzureAdConfig?
    runBlocking {
        aadConfig = AzureAdConfig(
            metadata = httpClientWithProxy().get(Configuration.azureAD["AZURE_APP_WELL_KNOWN_URL"]!!),
            clientId = Configuration.azureAD["AZURE_APP_CLIENT_ID"]!!,
        )
    }

    val jwkProviderAad = JwkProviderBuilder(URL(aadConfig!!.metadata.jwksUri))
        // cache up to 10 JWKs for 24 hours
        .cached(10, 24, TimeUnit.HOURS)
        // if not cached, only allow max 10 different keys per minute to be fetched from external provider
        .rateLimited(10, 1, TimeUnit.MINUTES)
        .build()

    install(Authentication) {
        jwt("aad") {
            verifier(jwkProviderAad, aadConfig!!.metadata.issuer)
            validate { credentials ->
                try {
                    requireNotNull(credentials.payload.audience) {
                        "Auth: Missing audience in token"
                    }
                    require(credentials.payload.audience.contains(aadConfig!!.clientId)) {
                        "Auth: Valid audience not found in claims"
                    }
                    JWTPrincipal(credentials.payload)
                } catch (e: Throwable) {
                    null
                }
            }
        }
    }
}

private data class AzureAdConfig(
    val metadata: Metadata,
    val clientId: String,
) {
    data class Metadata(
        @JsonProperty("issuer") val issuer: String,
        @JsonProperty("jwks_uri") val jwksUri: String,
    )
}
