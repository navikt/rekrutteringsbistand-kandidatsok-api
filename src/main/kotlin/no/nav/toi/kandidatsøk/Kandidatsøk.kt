package no.nav.toi.kandidatsøk

import com.fasterxml.jackson.databind.JsonNode
import io.javalin.Javalin
import io.javalin.openapi.*
import no.nav.toi.*
import org.opensearch.client.opensearch.OpenSearchClient
import org.opensearch.client.opensearch._types.SortOrder
import org.opensearch.client.opensearch._types.query_dsl.BoolQuery
import org.opensearch.client.opensearch.core.SearchResponse
import org.opensearch.client.util.ObjectBuilder

private const val endepunkt = "/api/kandidatsok"

@OpenApi(
    summary = "Søk på kandidater basert på søketermer",
    operationId = endepunkt,
    tags = [],
    responses = [OpenApiResponse("200", [OpenApiContent(OpensearchResponse::class)])],
    path = endepunkt,
    methods = [HttpMethod.POST]
)
fun Javalin.handleKandidatSøk(openSearchClient: OpenSearchClient) {
    post(endepunkt) { ctx ->
        val filter = listOfNotNull(
            ctx.queryParam("sted")?.let(::stedFilter)
        )
        val result = openSearchClient.kandidatSøk(filter)
        val fodselsnummer = result.hits().hits().firstOrNull()?.source()?.get("fodselsnummer")?.asText()
        if (fodselsnummer != null) {
            AuditLogg.loggOppslagKandidatStillingssøk(fodselsnummer, ctx.authenticatedUser().navIdent)
        }
        ctx.json(result.toResponseJson())
    }
}

private fun stedFilter(geografiKode: String): BoolQuery.Builder.() -> ObjectBuilder<BoolQuery> = {
    must_ {
        bool_ {
            should_ {
                nested_ {
                    path("geografiJobbonsker")
                    query_ {
                        bool_ {
                            should_ {
                                regexp("geografiJobbonsker.geografiKode", "$geografiKode|${geografiKode.split(".")[0]}|${geografiKode.substring(0,2)}")
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun OpenSearchClient.kandidatSøk(filter: List<BoolQuery.Builder.() -> ObjectBuilder<BoolQuery>>): SearchResponse<JsonNode> {
    return search<JsonNode> {
        index(DEFAULT_INDEX)
        query_ {
            bool_ {
                filter.forEach{it()}
                must_ {
                    terms("kvalifiseringsgruppekode" to listOf("BATT","BFORM","IKVAL","VARIG"))
                }
            }
        }
        source_ {
            includes(
                "fodselsnummer","fornavn","etternavn","arenaKandidatnr","kvalifiseringsgruppekode","yrkeJobbonskerObj"
                ,"geografiJobbonsker","kommuneNavn","postnummer"
            )
        }
        trackTotalHits(true)
        sort("tidsstempel", SortOrder.Desc)
        size(25)
        from(0)
    }
}


private data class KandidatSøkOpensearchResponse(
    val hits: KandidatSøkHits,
)

private data class KandidatSøkHits(
    val hits: List<Hit>,
    val total: Total
)

private data class Total(
    val value: Long
)

private fun SearchResponse<JsonNode>.toResponseJson(): KandidatSøkOpensearchResponse =
    KandidatSøkOpensearchResponse(
        hits = KandidatSøkHits(
            total = Total(hits().total().value()),
            hits = hits().hits().mapNotNull {
                it.source()?.let { Hit(it) }
            }
        )
    )