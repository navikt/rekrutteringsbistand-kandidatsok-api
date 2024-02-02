package no.nav.toi.kandidatnavn

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.convertValue
import io.javalin.Javalin
import io.javalin.http.HttpStatus.NOT_FOUND
import io.javalin.http.bodyValidator
import io.javalin.openapi.*
import no.nav.toi.*
import org.opensearch.client.opensearch.OpenSearchClient
import org.opensearch.client.opensearch._types.FieldValue
import org.opensearch.client.opensearch.core.SearchResponse


private const val path = "api/hent-kandidatnavn"

data class RequestDto(val fnr: String) {
    fun erElleveSiffer(): Boolean =
        fnr.all { it.isDigit() } && fnr.length == 11
}

data class ResponseDto(
    val fornavn: String,
    val etternavn: String,
    val synligIRekbis: Boolean,
    val kandidatnr: String?
)


@OpenApi(
    summary = "Finn navn på person primært fra Rekrutteringsbistand sin Opensearch-instans for kandidater og sekundært fra Persondataløsningen (PDL)",
    operationId = path,
    tags = [],
    requestBody = OpenApiRequestBody([OpenApiContent(RequestDto::class)]),
    responses = [OpenApiResponse("200", [OpenApiContent(ResponseDto::class)])],
    path = path,
    methods = [HttpMethod.POST]
)
fun Javalin.handleKandidatnavn(openSearchClient: OpenSearchClient, pdlClient: PdlClient) {
    post(path) { ctx ->
        val validator =
            ctx.bodyValidator<RequestDto>().check({ it.erElleveSiffer() }, "Fnr må være elleve siffer")
        val fnr: String = validator.get().fnr
        val searchResponse: SearchResponse<JsonNode> = openSearchClient.hentKandidatnavn(fnr)
        val nTotalHits = searchResponse.hits().total().value()
        if (nTotalHits > 1) {
            throw Exception("searchResponse.hits().total().value() må være <= 1 men er $nTotalHits")
        } else if (nTotalHits == 0L) {
            ctx.status(NOT_FOUND)
        } else {
            val kandidat: JsonNode = searchResponse.hits().hits().firstOrNull()!!.source()!!
            val m: Map<String, String> = ObjectMapper().convertValue(kandidat)
            val responseDto = ResponseDto(
                fornavn = m["fornavn"]!!,
                etternavn = m["etternavn"]!!,
                kandidatnr = m["kandidatnr"],
                synligIRekbis = true
            )
            AuditLogg.loggHentNavn(fnr, ctx.authenticatedUser().navIdent)
            ctx.json(responseDto)
        }
    }
}

private fun OpenSearchClient.hentKandidatnavn(fnr: String): SearchResponse<JsonNode> {
    return search<JsonNode> {
        index(DEFAULT_INDEX)
        query_ {
            term_ {
                field("fodselsnummer").value(FieldValue.of(fnr))
            }
        }
        source_ {
            includes("fornavn", "etternavn", "kandidatnr")
        }
    }
}
