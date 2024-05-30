package no.nav.toi.suggest

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import io.javalin.Javalin
import io.javalin.http.bodyAsClass
import io.javalin.openapi.*
import no.nav.toi.*
import org.opensearch.client.opensearch.OpenSearchClient
import org.opensearch.client.opensearch.core.SearchResponse


private const val endepunkt = "/api/suggest/sted"

@JsonIgnoreProperties(ignoreUnknown = true)
private data class StedRequest(
    val query: String,
)

@OpenApi(
    summary = "Få suggestions på stedsøk",
    operationId = endepunkt,
    tags = [],
    requestBody = OpenApiRequestBody([OpenApiContent(StedRequest::class)]),
    responses = [OpenApiResponse("200", [OpenApiContent(List::class)])],
    path = endepunkt,
    methods = [HttpMethod.POST]
)
fun Javalin.handleStedSuggest(openSearchClient: OpenSearchClient) {
    post(endepunkt) { ctx ->
        val request = ctx.bodyAsClass<StedRequest>()
        val result = openSearchClient.suggest(request.query)
        ctx.json(result.tilResponsJson())
    }
}

private class StedSvar {
    lateinit var geografiKodeTekst: String
    lateinit var geografiKode: String
}

private fun SearchResponse<StedSvar>.tilResponsJson() = suggest()
    .flatMap { it.value }
    .map { it.completion() }
    .flatMap { it.options() }
    .map { it.source() }

private fun OpenSearchClient.suggest(query: String) = search<StedSvar> {
    index(DEFAULT_INDEX)
    suggest_ {
        suggesters_("forslag") {
            prefix(query)
            completion_ {
                field("geografiJobbonsker.geografiKodeTekst.completion")
                size(15)
                skipDuplicates(true)
            }
        }
    }
    source_ {
        includes("geografiJobbonsker")
    }
}