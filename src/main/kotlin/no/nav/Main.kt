package no.nav

import io.javalin.Javalin
import io.javalin.config.JavalinConfig
import io.javalin.openapi.HttpMethod
import io.javalin.openapi.OpenApi
import io.javalin.openapi.OpenApiContent
import io.javalin.openapi.OpenApiResponse
import io.javalin.openapi.plugin.OpenApiPlugin
import io.javalin.openapi.plugin.OpenApiPluginConfiguration
import io.javalin.openapi.plugin.redoc.ReDocConfiguration
import io.javalin.openapi.plugin.redoc.ReDocPlugin
import io.javalin.openapi.plugin.swagger.SwaggerConfiguration
import io.javalin.openapi.plugin.swagger.SwaggerPlugin

fun main() {
    val app = Javalin.create { config ->
        configureOpenApi(config)
    }

    defineRoutes(app)

    app.start(8080)
}

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
    config.plugins.register(SwaggerPlugin(SwaggerConfiguration()))
    config.plugins.register(ReDocPlugin(ReDocConfiguration()))
}

fun defineRoutes(app: Javalin) {
    app.get("/internal/alive", ::isAliveHandler)
    app.get("/internal/ready", ::isReadyHandler)
    .azureAdAuthentication("/api/*")
    .get("/api/me") { ctx ->
        ctx.json(mapOf<String, Any?>("navIdent" to ctx.authenticatedUser().navIdent))
    }
}

@OpenApi(
    summary = "Sjekk om endepunkt er klart",
    operationId = "isReady",
    tags = [],
    responses = [OpenApiResponse("200", [OpenApiContent(String::class)])],
    path = "internal/ready",
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
    path = "internal/alive",
    methods = [HttpMethod.GET]
)
fun isAliveHandler(ctx: io.javalin.http.Context) {
    ctx.result("isAlive")
}
