package no.nav.toi.kandidatsammendrag

import com.fasterxml.jackson.databind.JsonNode
import io.javalin.Javalin
import io.javalin.http.bodyAsClass
import io.javalin.openapi.*
import no.nav.toi.*
import org.opensearch.client.opensearch.OpenSearchClient
import org.opensearch.client.opensearch.core.SearchResponse

private const val endepunkt = "/api/navn"

private data class KandidatNavnRequestDto(
    val fodselsnummer: String,
)
private enum class Kilde { REKRUTTERINGSBISTAND, PDL }

private data class KandidatNavnResponsDto(
    val fornavn: String,
    val etternavn: String,
    val kilde: Kilde
)

@OpenApi(
    summary = "Oppslag av navn for en enkelt person basert på fødselsnummer",
    operationId = endepunkt,
    tags = [],
    requestBody = OpenApiRequestBody([OpenApiContent(KandidatNavnRequestDto::class)]),
    responses = [OpenApiResponse("200", [OpenApiContent(KandidatNavnResponsDto::class)])],
    path = endepunkt,
    methods = [HttpMethod.POST]
)
fun Javalin.handleKandidatNavn(openSearchClient: OpenSearchClient, pdlKlient: PdlKlient) {
    post(endepunkt) { ctx ->
        val request = ctx.bodyAsClass<KandidatNavnRequestDto>()
        val result = openSearchClient.lookupKandidatNavn(request.fodselsnummer)
        AuditLogg.loggOppslagNavn(request.fodselsnummer, ctx.authenticatedUser().navIdent)
        result.hits().hits().firstOrNull()?.source()?.let {
            ctx.json(KandidatNavnResponsDto(it["fornavn"]!!.asText(), it["etternavn"]!!.asText(), Kilde.REKRUTTERINGSBISTAND))
        } ?: pdlKlient.hentFornavnOgEtternavn(request.fodselsnummer, ctx.authenticatedUser().jwt)?.let { (fornavn, etternavn) ->
            ctx.json(KandidatNavnResponsDto(fornavn,etternavn, Kilde.PDL))
        } ?: ctx.status(404)
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
            includes("fornavn", "etternavn")
        }
    }
}