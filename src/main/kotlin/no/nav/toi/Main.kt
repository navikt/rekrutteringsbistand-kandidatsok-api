package no.nav.toi

import io.javalin.Javalin
import io.javalin.config.JavalinConfig
import io.javalin.http.HttpStatus.BAD_REQUEST
import io.javalin.http.HttpStatus.INTERNAL_SERVER_ERROR
import io.javalin.openapi.plugin.OpenApiPlugin
import io.javalin.openapi.plugin.OpenApiPluginConfiguration
import io.javalin.openapi.plugin.swagger.SwaggerConfiguration
import io.javalin.openapi.plugin.swagger.SwaggerPlugin
import io.javalin.validation.ValidationException
import no.nav.toi.kandidatsammendrag.handleKandidatSammendrag
import no.nav.toi.kandidatstillingsøk.handleLookupKandidatStillingssøk
import no.nav.toi.kuberneteshealth.handleHealth
import no.nav.toi.lookupcv.handleLookupCv
import no.nav.toi.me.handleMe
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.io.Closeable
import java.util.*


/*
    Oppsett av applikasjon, som kan kjøres av både tester og main-metode.
 */
class App(
    private val port: Int = 8080,
    private val azureAppClientId: String,
    private val azureOpenidConfigIssuer: String,
    private val azureOpenidConfigJwksUri: String,
    private val rolleUuidSpesifikasjon: RolleUuidSpesifikasjon,
    openSearchUsername: String,
    openSearchPassword: String,
    openSearchUri: String,
) : Closeable {

    lateinit var javalin: Javalin

    private val openSearchClient = createOpenSearchClient(
        openSearchUsername = openSearchUsername,
        openSearchPassword = openSearchPassword,
        openSearchUri = openSearchUri,
    )

    fun configureOpenApi(config: JavalinConfig) {
        val openApiConfiguration = OpenApiPluginConfiguration().apply {
            withDefinitionConfiguration { _, definition ->
                definition.apply {
                    withOpenApiInfo {
                        it.title = "Kandidatsøk API"
                    }
                }
            }
        }
        config.plugins.register(OpenApiPlugin(openApiConfiguration))
        config.plugins.register(SwaggerPlugin(SwaggerConfiguration().apply {
            this.validatorUrl = null
        }
        ))
    }


    fun start() {
        javalin = Javalin.create { config ->
            configureOpenApi(config)
        }

        javalin.handleHealth()
        javalin.handleMe()
        javalin.handleLookupCv(openSearchClient)
        javalin.handleKandidatSammendrag(openSearchClient)
        javalin.handleLookupKandidatStillingssøk(openSearchClient)


        javalin.azureAdAuthentication(
            path = "/api/*",
            azureAppClientId = azureAppClientId,
            azureOpenidConfigIssuer = azureOpenidConfigIssuer,
            azureOpenidConfigJwksUri = azureOpenidConfigJwksUri,
            rolleUuidSpesifikasjon = rolleUuidSpesifikasjon,
        )

        val app = javalin.start(port)
        app.exception(ValidationException::class.java) { e, ctx ->
            log.info("Returnerer 400 Bad Request på grunn av: ${e.errors}", e)
            ctx.json(e.errors).status(BAD_REQUEST)
        }
        app.exception(Exception::class.java) { e, ctx ->
            log.error(e.message, e)
            ctx.status(INTERNAL_SERVER_ERROR)
        }
    }

    override fun close() {
        javalin.close()
    }
}

fun main() {
    App(
        azureAppClientId = System.getenv("AZURE_APP_CLIENT_ID")!!,
        azureOpenidConfigIssuer = System.getenv("AZURE_OPENID_CONFIG_ISSUER")!!,
        azureOpenidConfigJwksUri = System.getenv("AZURE_OPENID_CONFIG_JWKS_URI")!!,
        rolleUuidSpesifikasjon = RolleUuidSpesifikasjon(
            modiaGenerell = UUID.fromString(System.getenv("MODIA_GENERELL_GRUPPE")!!),
            modiaOppfølging = UUID.fromString(System.getenv("MODIA_OPPFOLGING_GRUPPE")!!),
        ),
        openSearchUsername = System.getenv("OPEN_SEARCH_USERNAME")!!,
        openSearchPassword = System.getenv("OPEN_SEARCH_PASSWORD")!!,
        openSearchUri = System.getenv("OPEN_SEARCH_URI")!!,
    ).start()
}


val Any.log: Logger
    get() = LoggerFactory.getLogger(this::class.java)
