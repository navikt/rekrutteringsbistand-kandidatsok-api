package no.nav.toi

import io.javalin.Javalin
import io.javalin.openapi.*
import no.nav.toi.kandidatsammendrag.kandidatSammendragHandler
import no.nav.toi.kandidatstillingsøk.lookupKandidatStillingssøkHandler
import no.nav.toi.lookupcv.lookupCvHandler
import org.opensearch.client.opensearch.OpenSearchClient

class Routes(
    private val openSearchClient: OpenSearchClient,
) {
    companion object {
        private const val endepunktReady = "/internal/ready"
        private const val endepunktAlive = "/internal/alive"
        private const val endepunktMe = "/api/me"
    }


    fun defineRoutes(javalin: Javalin) {
        javalin.get(endepunktAlive, ::isAliveHandler)
        javalin.get(endepunktReady, ::isReadyHandler)
        javalin.get(endepunktMe, ::meHandler)
        javalin.lookupCvHandler(openSearchClient)
        javalin.kandidatSammendragHandler(openSearchClient)
        javalin.lookupKandidatStillingssøkHandler(openSearchClient)
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
}