package no.nav.no.nav

import io.javalin.Javalin
import no.nav.authenticatedUser
import no.nav.azureAdAuthentication

fun main() {
    Javalin.create()
        .get("/internal/ready") { ctx -> ctx.result("ready") }
        .get("/internal/alive") { ctx -> ctx.result("alive") }
        .azureAdAuthentication("/api/*")
        .get("/api/me") { ctx ->
            ctx.json(mapOf<String, Any?>("navIdent" to ctx.authenticatedUser().navIdent))
        }
        .start(8080)
}