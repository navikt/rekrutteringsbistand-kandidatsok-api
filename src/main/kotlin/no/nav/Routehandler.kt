package no.nav

import io.javalin.Javalin
import io.javalin.openapi.HttpMethod
import io.javalin.openapi.OpenApi
import io.javalin.openapi.OpenApiContent
import io.javalin.openapi.OpenApiResponse

object Routehandler {

    private const val endepunktReady = "internal/ready"
    private const val endepunktAlive = "internal/alive"
    private const val endepunktMe = "api/me"

    fun defineRoutes(javalin: Javalin) {
        javalin.get("/${Routehandler.endepunktAlive}", Routehandler::isAliveHandler)
        javalin.get("/${Routehandler.endepunktReady}", Routehandler::isReadyHandler)
        javalin.get("/${Routehandler.endepunktMe}", Routehandler::meHandler)
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
        log.info("isReadyHandler kalles")
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
        log.info("isAliveHandler kalles")
        ctx.result("isAlive")
    }

    @OpenApi(
        summary = "Me-endepunkt",
        operationId = "me",
        tags = [],
        responses = [OpenApiResponse("200", [OpenApiContent(String::class)])],
        path = endepunktMe,
        methods = [HttpMethod.GET]
    )
    fun meHandler(ctx: io.javalin.http.Context) {
        log.info("Me-endepunkt kalles")
        ctx.json(mapOf<String, Any?>(
            "navIdent" to ctx.authenticatedUser().navIdent,
            "roller" to ctx.authenticatedUser().roller.map { it.name }
        ))
    }
}