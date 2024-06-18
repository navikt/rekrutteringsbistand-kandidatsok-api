package no.nav.toi.kandidatsammendrag

import com.fasterxml.jackson.databind.JsonNode
import io.javalin.Javalin
import io.javalin.http.bodyAsClass
import io.javalin.openapi.*
import no.nav.toi.*
import no.nav.toi.kandidatsøk.ModiaKlient
import org.opensearch.client.opensearch.OpenSearchClient
import org.opensearch.client.opensearch.core.SearchResponse

private const val endepunkt = "/api/kandidatsammendrag"

private data class RequestDto(
    val kandidatnr: String,
)

@OpenApi(
    summary = "Oppslag av kandidatsammendrag for en enkelt person basert på kandidatnummer",
    operationId = endepunkt,
    tags = [],
    requestBody = OpenApiRequestBody([OpenApiContent(RequestDto::class)]),
    responses = [OpenApiResponse("200", [OpenApiContent(OpensearchResponse::class)])],
    path = endepunkt,
    methods = [HttpMethod.POST]
)
fun Javalin.handleKandidatSammendrag(openSearchClient: OpenSearchClient, modiaKlient: ModiaKlient) {
    post(endepunkt) { ctx ->

        val request = ctx.bodyAsClass<RequestDto>()
        val result = openSearchClient.lookupKandidatsammendrag(request)
        val kandidat = result.hits().hits().firstOrNull()?.source()
        val fodselsnummer = kandidat?.get("fodselsnummer")?.asText()
        val orgEnhet = kandidat?.get("orgenhet")?.asText()
        val veileder = kandidat?.get("veileder")?.asText()

        ctx.authenticatedUser().verifiserTilgangTilBruker(orgEnhet, veileder, modiaKlient) { permit ->
            if (fodselsnummer != null) {
                AuditLogg.loggOppslagKandidatsammendrag(fodselsnummer, ctx.authenticatedUser().navIdent, permit)
            }
        }
        ctx.json(result.toResponseJson())
    }
}

private fun OpenSearchClient.lookupKandidatsammendrag(params: RequestDto): SearchResponse<JsonNode> {
    return search<JsonNode> {
        index(DEFAULT_INDEX)
        query_ {
            term_ { field("kandidatnr").value(params.kandidatnr) }
        }
        source_ {
            includes(
                "fornavn", "etternavn", "arenaKandidatnr", "fodselsdato",
                "fodselsnummer", "adresselinje1", "postnummer", "poststed",
                "epostadresse", "telefon", "veilederIdent", "veilederVisningsnavn",
                "veilederEpost", "orgenhet"
            )
        }
        size(1)
    }
}