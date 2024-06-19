package no.nav.toi.kandidatstillingsøk

import com.fasterxml.jackson.databind.JsonNode
import io.javalin.Javalin
import io.javalin.http.bodyAsClass
import io.javalin.openapi.*
import no.nav.toi.*
import no.nav.toi.kandidatsøk.ModiaKlient
import org.opensearch.client.opensearch.OpenSearchClient
import org.opensearch.client.opensearch.core.SearchResponse

private const val endepunkt = "/api/kandidat-stillingssok"

private data class RequestDto(
    val kandidatnr: String,
)

@OpenApi(
    summary = "Oppslag av kandidat stillingssøk data for en enkelt person basert på kandidatnummer",
    operationId = endepunkt,
    tags = [],
    requestBody = OpenApiRequestBody([OpenApiContent(RequestDto::class)]),
    responses = [OpenApiResponse("200", [OpenApiContent(OpensearchResponse::class)])],
    path = endepunkt,
    methods = [HttpMethod.POST]
)
fun Javalin.handleLookupKandidatStillingssøk(openSearchClient: OpenSearchClient, modiaKlient: ModiaKlient) {
    post(endepunkt) { ctx ->
        val authenticatedUser = ctx.authenticatedUser()

        val navIdent = authenticatedUser.navIdent
        val result = openSearchClient.lookupKandidatStillingssøk(ctx.bodyAsClass<RequestDto>())
        val kandidat = result.hits().hits().firstOrNull()?.source()
        val fodselsnummer = kandidat?.get("fodselsnummer")?.asText()
        val orgEnhet = kandidat?.get("orgenhet")?.asText()
        val veileder = kandidat?.get("veilederIdent")?.asText()

        authenticatedUser.verifiserTilgangTilBruker(orgEnhet, veileder, modiaKlient) { permit ->
            if (fodselsnummer != null) {
                AuditLogg.loggOppslagKandidatStillingssøk(fodselsnummer, navIdent, permit)
            }
        }
        ctx.json(result.toResponseJson())
    }
}

private fun OpenSearchClient.lookupKandidatStillingssøk(params: RequestDto): SearchResponse<JsonNode> {
    return search<JsonNode> {
        index(DEFAULT_INDEX)
        query_ {
            term_ { field("kandidatnr").value(params.kandidatnr) }
        }
        source_ {
            includes(
                "geografiJobbonsker",
                "yrkeJobbonskerObj", "kommunenummerstring", "kommuneNavn", "fodselsnummer",
                "veilederIdent", "orgenhet"
            )
        }
        size(1)
    }
}
