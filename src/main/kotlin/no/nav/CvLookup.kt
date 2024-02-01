package no.nav

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import org.opensearch.client.opensearch.OpenSearchClient
import org.opensearch.client.opensearch._types.FieldValue
import org.opensearch.client.opensearch.core.SearchResponse
import org.opensearch.client.opensearch.core.search.SourceConfig
import org.opensearch.client.opensearch.core.search.SourceFilter

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


fun OpenSearchClient.lookupKandidatsammendrag(params: LookupCvParameters): SearchResponse<JsonNode> {

    return search<JsonNode> {
        index(DEFAULT_INDEX)
        query_ {
            term_ { field("kandidatnr").value(FieldValue.of(params.kandidatnr)) }
        }
        source_ {
            includes(
                "fornavn", "etternavn", "arenaKandidatnr", "fodselsdato",
                "fodselsnummer", "adresselinje1", "postnummer", "poststed",
                "epostadresse", "telefon", "veileder", "geografiJobbonsker",
                "yrkeJobbonskerObj", "kommunenummerstring", "kommuneNavn",
                "veilederIdent", "veilederVisningsnavn", "veilederEpost"
            )
        }
        size(1)
    }
}

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