package no.nav.toi.kandidatsammendrag

import com.fasterxml.jackson.databind.JsonNode
import io.javalin.Javalin
import io.javalin.http.bodyAsClass
import io.javalin.openapi.*
import no.nav.toi.*
import org.opensearch.client.opensearch.OpenSearchClient
import org.opensearch.client.opensearch._types.FieldValue
import org.opensearch.client.opensearch.core.SearchResponse

private const val endepunkt = "/api/kandidatsammendrag"

private data class RequestDTO(
    val kandidatnr: String,
)

@OpenApi(
    summary = "Oppslag av kandidatsammendrag for en enkelt person basert på kandidatnummer",
    operationId = endepunkt,
    tags = [],
    requestBody = OpenApiRequestBody([OpenApiContent(RequestDTO::class)]),
    responses = [OpenApiResponse("200", [OpenApiContent(OpensearchResponse::class)])],
    path = endepunkt,
    methods = [HttpMethod.POST]
)
fun Javalin.kandidatSammendrag(openSearchClient: OpenSearchClient) {
    post(endepunkt) { ctx ->
        val request = ctx.bodyAsClass<RequestDTO>()
        val result = openSearchClient.lookupKandidatsammendrag(request)
        val fodselsnummer = result.hits().hits().firstOrNull()?.source()?.get("fodselsnummer")?.asText()
        if (fodselsnummer != null) {
            AuditLogg.loggOppslagKandidatsammendrag(fodselsnummer, ctx.authenticatedUser().navIdent)
        }
        ctx.json(result.toResponseJson())
    }
}

private fun OpenSearchClient.lookupKandidatsammendrag(params: RequestDTO): SearchResponse<JsonNode> {
    return search<JsonNode> {
        index(DEFAULT_INDEX)
        query_ {
            term_ { field("kandidatnr").value(FieldValue.of(params.kandidatnr)) }
        }
        source_ {
            includes(
                "fornavn", "etternavn", "arenaKandidatnr", "fodselsdato",
                "fodselsnummer", "adresselinje1", "postnummer", "poststed",
                "epostadresse", "telefon", "veileder", "geografiJobbonsker",
                "yrkeJobbonskerObj", "kommunenummerstring", "kommuneNavn",
                "veilederIdent", "veilederVisningsnavn", "veilederEpost"
            )
        }
        size(1)
    }
}