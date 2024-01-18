package no.nav

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import org.opensearch.client.opensearch.OpenSearchClient
import org.opensearch.client.opensearch._types.FieldValue
import org.opensearch.client.opensearch.core.SearchResponse

data class LookupCvParameters(
    val kandidatnr: String,
)

fun OpenSearchClient.lookupCv(params: LookupCvParameters): SearchResponse<JsonNode> =
    search<JsonNode> {
        index(DEFAULT_INDEX)
        query_ {
            term_ {
                field("kandidatnr").value(FieldValue.of(params.kandidatnr))
            }
        }
        size(1)
    }

fun main() {
    val openSearchClient = createOpenSearchClient()
    val searchResponse = openSearchClient.lookupCv(LookupCvParameters("10428826731")) // fnr from dev

    println(
        jacksonObjectMapper().writeValueAsString(
            mapOf(
                "hits" to mapOf(
                    "hits" to searchResponse.hits().hits().map { it.source() }
                )
        )))

//    for (i in searchResponse.hits().hits().indices) {
//        System.out.println(searchResponse.hits().hits()[i].source())
//    }
}