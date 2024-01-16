package no.nav

import com.fasterxml.jackson.databind.JsonNode
import org.opensearch.client.opensearch.OpenSearchClient
import org.opensearch.client.opensearch._types.FieldValue

fun OpenSearchClient.lookupCv(fodselsnummer: String) =
    search<JsonNode> {
        index(DEFAULT_INDEX)
        query_ {
            term_ {
                field("fodselsnummer").value(FieldValue.of(fodselsnummer))
            }
        }
        size(1)
    }


fun main() {
    val createOpenSearchClient = createOpenSearchClient()
    val searchResponse = createOpenSearchClient.lookupCv("10428826731") // fnr from dev
    for (i in searchResponse.hits().hits().indices) {
        System.out.println(searchResponse.hits().hits()[i].source())
    }
}