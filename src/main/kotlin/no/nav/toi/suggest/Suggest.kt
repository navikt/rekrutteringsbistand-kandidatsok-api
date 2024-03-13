package no.nav.toi.suggest

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.databind.JsonNode
import io.javalin.Javalin
import io.javalin.http.bodyAsClass
import io.javalin.openapi.*
import no.nav.toi.*
import org.opensearch.client.opensearch.OpenSearchClient
import org.opensearch.client.opensearch.core.SearchResponse

private const val endepunkt = "/api/suggest"

private enum class Typer(val field: String) {
    ØnsketYrke("yrkeJobbonskerObj.styrkBeskrivelse.completion"),
    Kompetanse("samletKompetanseObj.samletKompetanseTekst.completion"),
    Arbeidserfaring("yrkeserfaring.stillingstitlerForTypeahead"),
    Språk("sprak.sprakKodeTekst.completion")
}

@JsonIgnoreProperties(ignoreUnknown = true)
private data class Request(
    val query: String,
    val type: Typer,
    val valgtKontor: List<String>?,
    val innsatsgruppe: List<String>?,
    val ønsketYrke: List<String>?,
    val ønsketSted: List<String>?,
    val borPåØnsketSted: Boolean?,
    val kompetanse: List<String>?,
    val førerkort: List<String>?,
    val prioritertMålgruppe: List<String>?,
    val hovedmål:List<String>?,
    val utdanningsnivå:List<String>?,
    val arbeidserfaring:List<String>?,
    val ferskhet: Int?,
    val språk: List<String>?,
    val orgenhet: String?
)


@OpenApi(
    summary = "Få suggestions på søketermer",
    operationId = endepunkt,
    tags = [],
    requestBody = OpenApiRequestBody([OpenApiContent(Request::class)]),
    responses = [OpenApiResponse("200", [OpenApiContent(List::class)])],
    path = endepunkt,
    methods = [HttpMethod.POST]
)
fun Javalin.handleSuggest(openSearchClient: OpenSearchClient) {
    post(endepunkt) { ctx ->
        val request = ctx.bodyAsClass<Request>()
        val result = openSearchClient.suggest(request.query, request.type.field)
        ctx.json(result.tilResponsJson())
    }
}

private fun <TDocument> SearchResponse<TDocument>.tilResponsJson() = suggest()
    .flatMap { it.value }
    .map { it.completion() }
    .flatMap { it.options() }
    .map { it.text() }

private fun OpenSearchClient.suggest(query: String, field: String) = search<JsonNode> {
    index(DEFAULT_INDEX)
    suggest_ {
        suggesters_("forslag") {
            prefix(query)
            completion_ {
                field(field)
                size(15)
                skipDuplicates(true)
            }
        }
    }
    source_ {
        includes()
    }
}