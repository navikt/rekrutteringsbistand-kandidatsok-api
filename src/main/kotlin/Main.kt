package no.nav

import io.javalin.Javalin

fun main() {
    Javalin.create()
        .get("/internal/ready") { ctx -> ctx.result("ready") }
        .get("/internal/alive") { ctx -> ctx.result("alive") }
        .start(8080)
}