package no.nav

import io.javalin.Javalin
import io.javalin.http.bodyAsClass
import io.javalin.openapi.*
import org.opensearch.client.opensearch.OpenSearchClient
import org.opensearch.client.opensearch._types.FieldValue

class Routehandler(
    private val openSearchClient: OpenSearchClient,
) {
    companion object {
        private const val endepunktReady = "/internal/ready"
        private const val endepunktAlive = "/internal/alive"
        private const val endepunktMe = "/api/me"
        private const val endepunktLookupCv = "/api/lookup-cv"
        private const val endepunktHentKandidatnavn = "api/hent-kandidatnavn"
    }


    fun defineRoutes(javalin: Javalin) {
        javalin.get(endepunktAlive, ::isAliveHandler)
        javalin.get(endepunktReady, ::isReadyHandler)
        javalin.get(endepunktMe, ::meHandler)
        javalin.post(endepunktLookupCv, ::lookupCvHandler)
        javalin.post(endepunktHentKandidatnavn, ::hentKandidatnavnHandler)
    }

    @OpenApi(
        summary = "Sjekk om endepunkt er klart",
        operationId = "isReady",
        tags = [],
        responses = [OpenApiResponse("200", [OpenApiContent(String::class)])],
        path = endepunktReady,
        methods = [HttpMethod.GET]
    )
    fun isReadyHandler(ctx: io.javalin.http.Context) {
        ctx.result("isReady")
    }

    @OpenApi(
        summary = "Sjekk om endepunkt lever",
        operationId = "isAlive",
        tags = [],
        responses = [OpenApiResponse("200", [OpenApiContent(String::class)])],
        path = endepunktAlive,
        methods = [HttpMethod.GET]
    )
    fun isAliveHandler(ctx: io.javalin.http.Context) {
        ctx.result("isAlive")
    }

    @OpenApi(
        summary = "NAVIdent og roller for innlogget bruker",
        operationId = endepunktMe,
        tags = [],
        responses = [OpenApiResponse("200", [OpenApiContent(Any::class, properties = [
            OpenApiContentProperty(name = "navIdent", type = "string"),
            OpenApiContentProperty(name = "roller", isArray = true, type = "string")
        ])])],
        path = endepunktMe,
        methods = [HttpMethod.GET]
    )
    fun meHandler(ctx: io.javalin.http.Context) {
        ctx.json(mapOf<String, Any?>(
            "navIdent" to ctx.authenticatedUser().navIdent,
            "roller" to ctx.authenticatedUser().roller.map { it.name }
        ))
    }

    @OpenApi(
        summary = "Oppslag av hele CVen til en enkelt person basert på fødselsnummer",
        operationId = endepunktLookupCv,
        tags = [],
        requestBody = OpenApiRequestBody([OpenApiContent(LookupCvParameters::class)]),
        responses = [OpenApiResponse("200", [OpenApiContent(OpensearchResponse::class)])],
        path = endepunktLookupCv,
        methods = [HttpMethod.POST]
    )
    fun lookupCvHandler(ctx: io.javalin.http.Context) {
        val lookupCvParameters = ctx.bodyAsClass<LookupCvParameters>()
        val result = openSearchClient.lookupCv(lookupCvParameters)
        val fodselsnummer = result.hits().hits().firstOrNull()?.source()?.get("fodselsnummer")?.asText()
        if(fodselsnummer != null) {
            AuditLogg.loggOppslagCv(fodselsnummer, ctx.authenticatedUser().navIdent)
        }
        ctx.json(result.toResponseJson())
    }

    fun hentKandidatnavnHandler(ctx: io.javalin.http.Context) {
        val lookupCvParameters = ctx.bodyAsClass<LookupCvParameters>() // Egen DTO for bare fnr?
        val fnr = FieldValue.of(lookupCvParameters.fodselsnummer).stringValue()
        AuditLogg.loggOppslagCv(fnr, ctx.authenticatedUser().navIdent)
        data class KandidatnavnResponsDto(val fornavn: String, val mellomnavn: String, val etternavn: String, val synligIRekbis: Boolean, val kandidatnr: String?)
        ctx.json(KandidatnavnResponsDto("anyFornavn", "anyMellomnavn", "anyEtternavn", true, "anyKandidatnr-$fnr"))
    }
}
