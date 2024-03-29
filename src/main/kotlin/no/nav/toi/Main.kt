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
import no.nav.toi.accesstoken.AccessTokenClient
import no.nav.toi.kandidatsammendrag.PdlKlient
import no.nav.toi.kandidatsammendrag.handleKandidatKandidatnr
import no.nav.toi.kandidatsammendrag.handleKandidatNavn
import no.nav.toi.kandidatsammendrag.handleKandidatSammendrag
import no.nav.toi.kandidatstillingsøk.handleLookupKandidatStillingssøk
import no.nav.toi.kandidatsøk.handleKandidatSøk
import no.nav.toi.kandidatsøk.handleKandidatSøkForNavigering
import no.nav.toi.kompetanseforslag.handleKompetanseforslag
import no.nav.toi.kuberneteshealth.handleHealth
import no.nav.toi.lookupcv.handleLookupCv
import no.nav.toi.me.handleMe
import no.nav.toi.suggest.handleKontorSuggest
import no.nav.toi.suggest.handleStedSuggest
import no.nav.toi.suggest.handleSuggest
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.io.Closeable
import java.util.*


/*
    Oppsett av applikasjon, som kan kjøres av både tester og main-metode.
 */
class App(
    private val port: Int = 8080,
    private val authenticationConfigurations: List<AuthenticationConfiguration>,
    private val rolleUuidSpesifikasjon: RolleUuidSpesifikasjon,
    openSearchUsername: String,
    openSearchPassword: String,
    openSearchUri: String,
    pdlUrl: String,
    azureSecret: String,
    azureClientId: String,
    pdlScope: String,
    azureUrl: String
) : Closeable {

    lateinit var javalin: Javalin

    private val openSearchClient = createOpenSearchClient(
        openSearchUsername = openSearchUsername,
        openSearchPassword = openSearchPassword,
        openSearchUri = openSearchUri,
    )
    private val pdlKlient = PdlKlient(pdlUrl, AccessTokenClient(azureSecret,azureClientId,pdlScope,azureUrl))

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
        javalin.handleKompetanseforslag(openSearchClient)
        javalin.handleLookupKandidatStillingssøk(openSearchClient)
        javalin.handleKandidatSøk(openSearchClient)
        javalin.handleKandidatSøkForNavigering(openSearchClient)
        javalin.handleSuggest(openSearchClient)
        javalin.handleStedSuggest(openSearchClient)
        javalin.handleKontorSuggest(openSearchClient)
        javalin.handleKandidatNavn(openSearchClient, pdlKlient)
        javalin.handleKandidatKandidatnr(openSearchClient)


        javalin.azureAdAuthentication(
            path = "/api/*",
            authenticationConfigurations = authenticationConfigurations,
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

private val fakedingsAuthenticationConfiguration = AuthenticationConfiguration(
    jwksUri = "https://fakedings.intern.dev.nav.no/fake/jwks",
    issuer = "https://fakedings.intern.dev.nav.no/fake/aad",
    audience = "dev-gcp:toi:rekrutteringsbistand-kandidatsok-api",
)

fun main() {
    App(
        authenticationConfigurations = listOfNotNull(
            AuthenticationConfiguration(
                audience = getenv("AZURE_APP_CLIENT_ID"),
                issuer = getenv("AZURE_OPENID_CONFIG_ISSUER"),
                jwksUri  = getenv("AZURE_OPENID_CONFIG_JWKS_URI"),
            ),
            if (System.getenv("NAIS_CLUSTER_NAME") == "dev-gcp")
                fakedingsAuthenticationConfiguration
            else
                null,
        ),
        rolleUuidSpesifikasjon = RolleUuidSpesifikasjon(
            modiaGenerell = UUID.fromString(getenv("MODIA_GENERELL_GRUPPE")),
            modiaOppfølging = UUID.fromString(getenv("MODIA_OPPFOLGING_GRUPPE")!!),
        ),
        openSearchUsername = getenv("OPEN_SEARCH_USERNAME"),
        openSearchPassword = getenv("OPEN_SEARCH_PASSWORD"),
        openSearchUri = getenv("OPEN_SEARCH_URI"),
        pdlUrl = getenv("PDL_URL"),
        azureSecret = getenv("AZURE_APP_CLIENT_SECRET"),
        azureClientId = getenv("AZURE_APP_CLIENT_ID"),
        pdlScope = getenv("PDL_SCOPE"),
        azureUrl = getenv("AZURE_OPENID_CONFIG_TOKEN_ENDPOINT"),
    ).start()
}

private fun getenv(key: String) = System.getenv(key) ?: throw IllegalArgumentException("Det finnes ingen system-variabel ved navn $key")


val Any.log: Logger
    get() = LoggerFactory.getLogger(this::class.java)
