package no.nav.toi.suggest

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.databind.JsonNode
import io.javalin.Javalin
import io.javalin.http.bodyAsClass
import io.javalin.openapi.*
import no.nav.toi.*
import org.opensearch.client.opensearch.OpenSearchClient
import org.opensearch.client.opensearch._types.aggregations.StringTermsBucket
import org.opensearch.client.opensearch.core.SearchResponse

private const val endepunkt = "/api/suggest/kontor"

@JsonIgnoreProperties(ignoreUnknown = true)
private data class KontorRequest(
    val query: String,
)

@OpenApi(
    summary = "Få suggestions på kontorsøk",
    operationId = endepunkt,
    tags = [],
    requestBody = OpenApiRequestBody([OpenApiContent(KontorRequest::class)]),
    responses = [OpenApiResponse("200", [OpenApiContent(List::class)])],
    path = endepunkt,
    methods = [HttpMethod.POST]
)
fun Javalin.handleKontorSuggest(openSearchClient: OpenSearchClient) {
    post(endepunkt) { ctx ->
        val request = ctx.bodyAsClass<KontorRequest>()
        val result = openSearchClient.suggest(request.query)
        ctx.json(result.tilResponsJson())
    }
}

private fun SearchResponse<JsonNode>.tilResponsJson() = aggregations()
    .get("suggestions")
    ?.sterms()
    ?.buckets()
    ?.array()
    ?.map(StringTermsBucket::key) ?: throw Exception("Feil i spørring mot elastic search")

private fun OpenSearchClient.suggest(query: String) = search<JsonNode> {
    index(DEFAULT_INDEX)
    query_ {
        matchPhrase_ {
            field("navkontor.text")
            query(query)
            slop(5)
        }
    }
    aggregations_("suggestions") {
        terms_ {
            field("navkontor")
        }
    }
    size(0)
    source(false)
}