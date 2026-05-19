package no.nav.toi.me

import io.javalin.router.JavalinDefaultRoutingApi
import io.javalin.openapi.*
import no.nav.toi.authenticatedUser

private const val endepunktMe = "/api/me"

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
fun JavalinDefaultRoutingApi.handleMe() {
    get(endepunktMe) { ctx ->
        ctx.json(mapOf<String, Any?>(
            "navIdent" to ctx.authenticatedUser().navIdent,
            "roller" to ctx.authenticatedUser().roller.map { it.name }
        ))
    }
}
