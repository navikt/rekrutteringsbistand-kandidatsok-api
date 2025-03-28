package no.nav.toi.brukertilgang

import com.fasterxml.jackson.databind.JsonNode
import io.javalin.Javalin
import io.javalin.http.HttpStatus
import io.javalin.http.bodyAsClass
import io.javalin.openapi.*
import no.nav.toi.*
import no.nav.toi.SecureLogLogger.Companion.secure
import no.nav.toi.kandidatsøk.ModiaKlient
import org.opensearch.client.opensearch.OpenSearchClient
import org.opensearch.client.opensearch.core.SearchResponse

private const val endepunkt = "/api/brukertilgang"

private data class BrukertilgangRequestDto(
    val fodselsnummer: String?,
    val aktorid: String?,
    val kandidatnr: String?
)

@OpenApi(
    summary = "Oppslag for å sjekke om en ident har tilgang til kandidatdata for en spesifikk bruker",
    operationId = endepunkt,
    tags = [],
    requestBody = OpenApiRequestBody([OpenApiContent(BrukertilgangRequestDto::class)]),
    responses = [OpenApiResponse("200"), OpenApiResponse("400", description = "Bad Request")],
    path = endepunkt,
    methods = [HttpMethod.POST]
)
fun Javalin.handleBrukertilgang(openSearchClient: OpenSearchClient, modiaKlient: ModiaKlient) {
    post(endepunkt) { ctx ->
        val authenticatedUser = ctx.authenticatedUser()
        val request = ctx.bodyAsClass<BrukertilgangRequestDto>()

        log.info("Oppslag for brukertilgang")
        secure(log).info("Brukertilgang-request: $request")
        val result = when {
            request.fodselsnummer != null -> openSearchClient.lookupBrukertilgang(
                "fodselsnummer",
                request.fodselsnummer
            )

            request.aktorid != null -> openSearchClient.lookupBrukertilgang("aktorId", request.aktorid)
            request.kandidatnr != null -> openSearchClient.lookupBrukertilgang("kandidatnr", request.kandidatnr)
            else -> {
                ctx.status(HttpStatus.BAD_REQUEST)
                    .result("Bad Request: minst en identifikator (fodselsnummer, aktorid, kandidatnr) must sendes med")
                return@post
            }
        }

        val kandidat = result.hits().hits().firstOrNull()?.source()
        val orgEnhet = kandidat?.get("orgenhet")?.asText()
        val veileder = kandidat?.get("veilederIdent")?.asText()

        authenticatedUser.verifiserTilgangTilBruker(orgEnhet, veileder, modiaKlient, {})
        ctx.json(result.toResponseJson())
    }
}

private fun OpenSearchClient.lookupBrukertilgang(field: String, value: String): SearchResponse<JsonNode> {
    return search<JsonNode> {
        index(DEFAULT_INDEX)
        query_ {
            term_ { field(field).value(value) }
        }
        source_ {
            includes(
                "veilederIdent",
                "orgenhet"
            )
        }
    }
}
