package no.nav.toi.kuberneteshealth

import io.javalin.router.JavalinDefaultRoutingApi

private const val endepunktReady = "/internal/ready"
private const val endepunktAlive = "/internal/alive"

fun JavalinDefaultRoutingApi.handleHealth() {
    get(endepunktReady) { ctx->
        ctx.result("isReady")
    }
    get(endepunktAlive) { ctx->
        ctx.result("isAlive")
    }
}
