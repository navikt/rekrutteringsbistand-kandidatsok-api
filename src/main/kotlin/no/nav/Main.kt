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
import org.slf4j.Logger
import org.slf4j.LoggerFactory

const val endepunktReady = "internal/ready"
const val endepunktAlive = "internal/alive"
const val endepunktMe = "api/me"

class App {

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

    companion object {
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
            ctx.json(mapOf<String, Any?>("navIdent" to ctx.authenticatedUser().navIdent))
        }
    }

}

fun Javalin.defineRoutes() {
    get("/$endepunktAlive", App::isAliveHandler)
    get("/$endepunktReady", App::isReadyHandler)
    get("/$endepunktMe", App::meHandler)
    azureAdAuthentication("/api/*")

}


fun main() {
    val app = Javalin.create { config ->
        App().configureOpenApi(config)
    }

    app.defineRoutes()

    app.start(8080)
}


val Any.log: Logger
    get() = LoggerFactory.getLogger(this::class.java)

fun log(name: String): Logger = LoggerFactory.getLogger(name)
