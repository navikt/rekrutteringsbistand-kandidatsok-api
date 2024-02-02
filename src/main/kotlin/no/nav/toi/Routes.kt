package no.nav.toi

import io.javalin.Javalin
import io.javalin.http.bodyAsClass
import io.javalin.openapi.*
import no.nav.toi.kandidatsammendrag.kandidatSammendrag
import no.nav.toi.lookupcv.lookupCvHandler
import org.opensearch.client.opensearch.OpenSearchClient

class Routes(
    private val openSearchClient: OpenSearchClient,
) {
    companion object {
        private const val endepunktReady = "/internal/ready"
        private const val endepunktAlive = "/internal/alive"
        private const val endepunktMe = "/api/me"
        private const val endepunktKandidatStillingssøk = "/api/kandidat-stillingssok"
    }


    fun defineRoutes(javalin: Javalin) {
        javalin.get(endepunktAlive, ::isAliveHandler)
        javalin.get(endepunktReady, ::isReadyHandler)
        javalin.get(endepunktMe, ::meHandler)
        javalin.lookupCvHandler(openSearchClient)
        javalin.kandidatSammendrag(openSearchClient)
        javalin.post(endepunktKandidatStillingssøk, ::lookupKandidatStillingssøkHandler)

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
        responses = [OpenApiResponse(
            "200", [OpenApiContent(
                Any::class, properties = [
                    OpenApiContentProperty(name = "navIdent", type = "string"),
                    OpenApiContentProperty(name = "roller", isArray = true, type = "string")
                ]
            )]
        )],
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
        summary = "Oppslag av kandidat stillingssøk data for en enkelt person basert på kandidatnummer",
        operationId = endepunktKandidatStillingssøk,
        tags = [],
        requestBody = OpenApiRequestBody([OpenApiContent(LookupCvParameters::class)]),
        responses = [OpenApiResponse("200", [OpenApiContent(OpensearchResponse::class)])],
        path = endepunktKandidatStillingssøk,
        methods = [HttpMethod.POST]
    )
    fun lookupKandidatStillingssøkHandler(ctx: io.javalin.http.Context) {
        val lookupKandidatStillingssøkParameters = ctx.bodyAsClass<LookupCvParameters>()
        val result = openSearchClient.lookupKandidatStillingssøk(lookupKandidatStillingssøkParameters)
        val fodselsnummer = result.hits().hits().firstOrNull()?.source()?.get("fodselsnummer")?.asText()
        if (fodselsnummer != null) {
            AuditLogg.loggOppslagKandidatStillingssøk(fodselsnummer, ctx.authenticatedUser().navIdent)
        }
        ctx.json(result.toResponseJson())
    }
}