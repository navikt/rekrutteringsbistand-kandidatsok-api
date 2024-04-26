package no.nav.toi.kandidatsammendrag

import com.fasterxml.jackson.databind.JsonNode
import io.javalin.Javalin
import io.javalin.http.bodyAsClass
import io.javalin.openapi.*
import no.nav.toi.*
import org.opensearch.client.opensearch.OpenSearchClient
import org.opensearch.client.opensearch.core.SearchResponse

private const val endepunkt = "/api/arena-kandidatnr"

private data class KandidatKandidatnrRequestDto(
    val fodselsnummer: String,
)

private data class KandidatKandidatnrResponsDto(
    val arenaKandidatnr: String,
)

@OpenApi(
    summary = "Oppslag av navn for en enkelt person basert på fødselsnummer",
    operationId = endepunkt,
    tags = [],
    requestBody = OpenApiRequestBody([OpenApiContent(KandidatKandidatnrRequestDto::class)]),
    responses = [OpenApiResponse("200", [OpenApiContent(KandidatKandidatnrResponsDto::class)])],
    path = endepunkt,
    methods = [HttpMethod.POST]
)
fun Javalin.handleKandidatKandidatnr(openSearchClient: OpenSearchClient) {
    post(endepunkt) { ctx ->
        ctx.authenticatedUser().verifiserAutorisasjon(Rolle.ARBEIDSGIVER_RETTET,  Rolle.UTVIKLER)
        val request = ctx.bodyAsClass<KandidatKandidatnrRequestDto>()
        val result = openSearchClient.lookupKandidatNavn(request.fodselsnummer)
        result.hits().hits().firstOrNull()?.source()?.get("arenaKandidatnr")
            ?.let(JsonNode::asText)
            ?.let(::KandidatKandidatnrResponsDto)
            ?.let(ctx::json) ?: ctx.status(404)
    }
}

private fun OpenSearchClient.lookupKandidatNavn(fodselsnummer: String): SearchResponse<JsonNode> {
    return search<JsonNode> {
        index(DEFAULT_INDEX)
        query_ {
            term_ { field("fodselsnummer").value(fodselsnummer) }
        }
        size(1)
        source_ {
            includes("arenaKandidatnr")
        }
    }
}