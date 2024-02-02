package no.nav.toi

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import org.opensearch.client.opensearch.OpenSearchClient
import org.opensearch.client.opensearch._types.FieldValue
import org.opensearch.client.opensearch.core.SearchResponse

fun OpenSearchClient.lookupKandidatStillingssøk(params: LookupCvParameters): SearchResponse<JsonNode> {

    return search<JsonNode> {
        index(DEFAULT_INDEX)
        query_ {
            term_ { field("kandidatnr").value(FieldValue.of(params.kandidatnr)) }
        }
        source_ {
            includes(
                "arenaKandidatnr", "geografiJobbonsker",
                "yrkeJobbonskerObj", "kommunenummerstring", "kommuneNavn"
            )
        }
        size(1)
    }
}

fun main() {
    val openSearchClient = createOpenSearchClient()
    val searchResponse = openSearchClient.lookupCv(LookupCvParameters("10428826731"))

    println(
        jacksonObjectMapper().writeValueAsString(
            mapOf(
                "hits" to mapOf(
                    "hits" to searchResponse.hits().hits().map { it.source() }
                )
            )))
}