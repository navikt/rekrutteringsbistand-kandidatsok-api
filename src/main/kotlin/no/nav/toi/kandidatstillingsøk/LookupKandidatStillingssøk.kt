package no.nav.toi.kandidatstillingsøk

import com.fasterxml.jackson.databind.JsonNode
import io.javalin.Javalin
import io.javalin.http.bodyAsClass
import io.javalin.openapi.*
import no.nav.toi.*
import org.opensearch.client.opensearch.OpenSearchClient
import org.opensearch.client.opensearch._types.FieldValue
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
fun Javalin.handleLookupKandidatStillingssøk(openSearchClient: OpenSearchClient) {
    post(endepunkt) { ctx ->
        val request = ctx.bodyAsClass<RequestDto>()
        val result = openSearchClient.lookupKandidatStillingssøk(request)
        val fodselsnummer = result.hits().hits().firstOrNull()?.source()?.get("fodselsnummer")?.asText()
        if (fodselsnummer != null) {
            AuditLogg.loggOppslagKandidatStillingssøk(fodselsnummer, ctx.authenticatedUser().navIdent)
        }
        ctx.json(result.toResponseJson())
    }
}

private fun OpenSearchClient.lookupKandidatStillingssøk(params: RequestDto): SearchResponse<JsonNode> {
    return search<JsonNode> {
        index(DEFAULT_INDEX)
        query_ {
            term_ { field("kandidatnr").value(FieldValue.of(params.kandidatnr)) }
        }
        source_ {
            includes(
                "geografiJobbonsker",
                "yrkeJobbonskerObj", "kommunenummerstring", "kommuneNavn"
            )
        }
        size(1)
    }
}
