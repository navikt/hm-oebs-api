package no.nav.hjelpemidler.configuration

import com.natpryce.konfig.*

internal object Configuration {

    private fun config() = when (System.getenv("NAIS_CLUSTER_NAME") ?: System.getProperty("NAIS_CLUSTER_NAME")) {
        "dev-gcp" -> ConfigurationProperties.systemProperties() overriding EnvironmentVariables overriding devProperties
        "prod-gcp" -> ConfigurationProperties.systemProperties() overriding EnvironmentVariables overriding prodProperties
        else -> {
            ConfigurationProperties.systemProperties() overriding EnvironmentVariables overriding localProperties
        }
    }

    private val prodProperties = ConfigurationMap(
        mapOf(
            "application.profile" to "prod",

            "AZURE_TENANT_BASEURL" to "https://login.microsoftonline.com",
            "AZURE_AD_SCOPE" to "api://prod-fss.teamdigihot.hm-oebs-api-proxy/.default",

            "OEBS_API_PROXY_URL" to "https://hm-oebs-api-proxy.prod-fss-pub.nais.io",
        )
    )

    private val devProperties = ConfigurationMap(
        mapOf(
            "application.profile" to "dev",

            "AZURE_TENANT_BASEURL" to "https://login.microsoftonline.com",
            "AZURE_AD_SCOPE" to "api://dev-fss.teamdigihot.hm-oebs-api-proxy/.default",

            "OEBS_API_PROXY_URL" to "https://hm-oebs-api-proxy.dev-fss-pub.nais.io",
        )
    )

    private val localProperties = ConfigurationMap(
        mapOf(
            "application.profile" to "local",

            "AZURE_TENANT_BASEURL" to "http://localhost:9099",
            "AZURE_APP_TENANT_ID" to "123",
            "AZURE_APP_CLIENT_ID" to "123",
            "AZURE_APP_CLIENT_SECRET" to "dummy",
            "AZURE_APP_WELL_KNOWN_URL" to "dummy",
            "AZURE_AD_SCOPE" to "123",

            "OEBS_API_PROXY_URL" to "http://localhost:9092",
        )
    )

    val azureAD: Map<String, String> = mapOf(
        "AZURE_TENANT_BASEURL" to config()[Key("AZURE_TENANT_BASEURL", stringType)],
        "AZURE_APP_TENANT_ID" to config()[Key("AZURE_APP_TENANT_ID", stringType)],
        "AZURE_APP_CLIENT_ID" to config()[Key("AZURE_APP_CLIENT_ID", stringType)],
        "AZURE_APP_CLIENT_SECRET" to config()[Key("AZURE_APP_CLIENT_SECRET", stringType)],
        "AZURE_APP_WELL_KNOWN_URL" to config()[Key("AZURE_APP_WELL_KNOWN_URL", stringType)],
        "AZURE_AD_SCOPE" to config()[Key("AZURE_AD_SCOPE", stringType)],
    )

    val oebsApiProxy: Map<String, String> = mapOf(
        "OEBS_API_PROXY_URL" to config()[Key("OEBS_API_PROXY_URL", stringType)],
    )

    val application: Map<String, String> = mapOf(
        "APP_PROFILE" to config()[Key("application.profile", stringType)],
        "HTTP_PORT" to "8080",
    ) + System.getenv().filter { it.key.startsWith("NAIS_") }

}
