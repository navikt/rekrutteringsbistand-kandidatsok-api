package no.nav.toi

import io.javalin.Javalin
import io.javalin.config.JavalinConfig
import io.javalin.http.HttpStatus.BAD_REQUEST
import io.javalin.http.HttpStatus.INTERNAL_SERVER_ERROR
import io.javalin.openapi.plugin.OpenApiPlugin
import io.javalin.openapi.plugin.swagger.SwaggerPlugin
import io.javalin.validation.ValidationException
import no.nav.toi.brukertilgang.handleBrukertilgang
import no.nav.toi.brukertilgang.handleMinekandidatnummer
import no.nav.toi.hullicv.handleHullICv
import no.nav.toi.kandidatsammendrag.*
import no.nav.toi.kandidatstillingsøk.handleLookupKandidatStillingssøk
import no.nav.toi.kandidatsøk.ModiaKlient
import no.nav.toi.kandidatsøk.handleKandidatSøk
import no.nav.toi.kompetanseforslag.handleKompetanseforslag
import no.nav.toi.kuberneteshealth.handleHealth
import no.nav.toi.lookupcv.handleLookupCv
import no.nav.toi.lookupcv.handleMultipleLookupCv
import no.nav.toi.me.handleMe
import no.nav.toi.suggest.handleKontorSuggest
import no.nav.toi.suggest.handleStedSuggest
import no.nav.toi.suggest.handleSuggest
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.util.*


private val noClassLogger = noClassLogger()
val secureLog = LoggerFactory.getLogger("secureLog")!!

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
    azureUrl: String,
    modiaContextHolderUrl: String,
    modiaContextHolderScope: String,
    toiLivshendelseScope: String,
    toiLivshendelseUrl: String
) {

    lateinit var javalin: Javalin

    private val openSearchClient = createOpenSearchClient(
        openSearchUsername = openSearchUsername,
        openSearchPassword = openSearchPassword,
        openSearchUri = openSearchUri,
    )
    private val pdlKlient = PdlKlient(pdlUrl, AccessTokenClient(azureSecret, azureClientId, pdlScope, azureUrl))

    private val modiaClient = ModiaKlient(
        modiaContextHolderUrl,
        AccessTokenClient(azureSecret, azureClientId, modiaContextHolderScope, azureUrl)
    )

    private val livshendelseKlient = LivshendelseKlient(toiLivshendelseUrl, AccessTokenClient(azureSecret, azureClientId, toiLivshendelseScope, azureUrl))

    fun configureOpenApi(config: JavalinConfig) {
        val openApiConfiguration = OpenApiPlugin { openApiConfig ->
            openApiConfig.withDefinitionConfiguration { _, definition ->
                definition.apply {
                    withInfo {
                        it.title = "Kandidatsøk API"
                    }
                }
            }
        }
        config.registerPlugin(openApiConfiguration)
        config.registerPlugin(SwaggerPlugin { swaggerConfiguration ->
            swaggerConfiguration.validatorUrl = null
        })
    }


    fun start() {
        javalin = Javalin.create { config ->
            configureOpenApi(config)
        }

        javalin.handleHealth()
        javalin.handleMe()
        javalin.handleLookupCv(openSearchClient, modiaClient)
        javalin.handleMultipleLookupCv(openSearchClient, modiaClient)
        javalin.handleKandidatSammendrag(openSearchClient, modiaClient)
        javalin.handleKompetanseforslag(openSearchClient)
        javalin.handleLookupKandidatStillingssøk(openSearchClient, modiaClient)
        javalin.handleKandidatSøk(openSearchClient, modiaClient)
        javalin.handleSuggest(openSearchClient)
        javalin.handleStedSuggest(openSearchClient)
        javalin.handleKontorSuggest(openSearchClient)
        javalin.handleKandidatNavn(livshendelseKlient, openSearchClient, pdlKlient)
        javalin.handleKandidatKandidatnr(openSearchClient)
        javalin.handleBrukertilgang(openSearchClient, modiaClient)
        javalin.handleMinekandidatnummer(openSearchClient, modiaClient)
        javalin.handleHullICv(openSearchClient, modiaClient)

        javalin.azureAdAuthentication(
            path = "/api/*",
            authenticationConfigurations = authenticationConfigurations,
            rolleUuidSpesifikasjon = rolleUuidSpesifikasjon,
        )

        val app = javalin.start(port)
        app.exception(ValidationException::class.java) { e, ctx ->
            val httpStatus = BAD_REQUEST
            val requestUrl: String = ctx.req().requestURL.toString()
            val endpointHandlerPath: String = ctx.endpointHandlerPath()
            val httpMethod: String = ctx.req().method
            val msg =
                "Returnerer HTTP respons status $httpStatus. requestUrl=[$requestUrl], httpMethod=[$httpMethod], endpointHandlerPath=[$endpointHandlerPath]"
            log.info(msg, e)
            ctx.status(httpStatus)
        }
        app.exception(Exception::class.java) { e, ctx ->
            val httpStatus = INTERNAL_SERVER_ERROR
            val requestUrl: String = ctx.req().requestURL.toString()
            val endpointHandlerPath: String = ctx.endpointHandlerPath()
            val httpMethod: String = ctx.req().method
            val msg =
                "Returnerer HTTP respons status $httpStatus. requestUrl=[$requestUrl], httpMethod=[$httpMethod], endpointHandlerPath=[$endpointHandlerPath]"
            log.error(msg, e)
            ctx.status(httpStatus)
        }
    }

    fun close() {
        javalin.stop()
    }
}

private val fakedingsAuthenticationConfiguration = AuthenticationConfiguration(
    jwksUri = "https://fakedings.intern.dev.nav.no/fake/jwks",
    issuer = "https://fakedings.intern.dev.nav.no/fake/aad",
    audience = "dev-gcp:toi:rekrutteringsbistand-kandidatsok-api",
)

fun main() {
    noClassLogger.info("Starter app.")
    secureLog.info("Starter app. Dette er ment å logges til Securelogs. Hvis du ser dette i den ordinære apploggen er noe galt, og sensitive data kan havne i feil logg.")

    App(
        authenticationConfigurations = listOfNotNull(
            AuthenticationConfiguration(
                audience = getenv("AZURE_APP_CLIENT_ID"),
                issuer = getenv("AZURE_OPENID_CONFIG_ISSUER"),
                jwksUri = getenv("AZURE_OPENID_CONFIG_JWKS_URI"),
            ),
            if (System.getenv("NAIS_CLUSTER_NAME") == "dev-gcp")
                fakedingsAuthenticationConfiguration
            else
                null,
        ),
        rolleUuidSpesifikasjon = RolleUuidSpesifikasjon(
            jobbsøkerrettet = UUID.fromString(getenv("REKRUTTERINGSBISTAND_JOBBSOKERRETTET")),
            arbeidsgiverrettet = UUID.fromString(getenv("REKRUTTERINGSBISTAND_ARBEIDSGIVERRETTET")),
            utvikler = UUID.fromString(getenv("REKRUTTERINGSBISTAND_UTVIKLER"))
        ),
        openSearchUsername = getenv("OPEN_SEARCH_USERNAME"),
        openSearchPassword = getenv("OPEN_SEARCH_PASSWORD"),
        openSearchUri = getenv("OPEN_SEARCH_URI"),
        pdlUrl = getenv("PDL_URL"),
        azureSecret = getenv("AZURE_APP_CLIENT_SECRET"),
        azureClientId = getenv("AZURE_APP_CLIENT_ID"),
        pdlScope = getenv("PDL_SCOPE"),
        azureUrl = getenv("AZURE_OPENID_CONFIG_TOKEN_ENDPOINT"),
        modiaContextHolderUrl = getenv("MODIA_CONTEXT_HOLDER_URL"),
        modiaContextHolderScope = getenv("MODIA_CONTEXT_HOLDER_SCOPE"),
        toiLivshendelseScope = getenv("TOI_LIVSHENDELSE_SCOPE"),
        toiLivshendelseUrl = "http://toi-livshendelse",
    ).start()
}

private fun getenv(key: String) =
    System.getenv(key) ?: throw IllegalArgumentException("Det finnes ingen system-variabel ved navn $key")


val Any.log: Logger
    get() = LoggerFactory.getLogger(this::class.java)

/**
 * Convenience for å slippe å skrive eksplistt navn på Logger når Logger opprettes. Ment å tilsvare Java-måten, hvor
 * Loggernavnet pleier å være pakkenavn+klassenavn på den loggende koden.
 * Brukes til å logging fra Kotlin-kode hvor vi ikke er inne i en klasse, typisk i en "top level function".
 * Kalles fra den filen du ønsker å logg i slik:
 *```
 * import no.nav.yada.noClassLogger
 * private val log: Logger = noClassLogger()
 * fun myTopLevelFunction() {
 *      log.info("yada yada yada")
 *      ...
 * }
 *```
 *
 *@return En Logger hvor navnet er sammensatt av pakkenavnet og filnavnet til den kallende koden
 */
fun noClassLogger(): Logger {
    val callerClassName = Throwable().stackTrace[1].className
    return LoggerFactory.getLogger(callerClassName)
}
