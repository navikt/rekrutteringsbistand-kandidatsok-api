package no.nav.toi.kompetanseforslag

import com.fasterxml.jackson.databind.JsonNode
import io.javalin.Javalin
import io.javalin.http.bodyAsClass
import io.javalin.openapi.HttpMethod
import io.javalin.openapi.OpenApi
import io.javalin.openapi.OpenApiContent
import io.javalin.openapi.OpenApiRequestBody
import no.nav.toi.*
import org.opensearch.client.opensearch.OpenSearchClient
import org.opensearch.client.opensearch.core.SearchResponse


private const val endepunkt = "/api/kompetanseforslag"

private data class Yrke(
    val yrke: String,
)

private data class RequestDto(
    val yrker: List<Yrke>
)

data class KompetanseAggregationResponse(
    val aggregations: KompetanseAggregations
)

data class KompetanseAggregations(
    val kompetanse: KompetanseAggregation
)

data class KompetanseAggregation(
    val buckets: List<Bucket>
)

data class Bucket(
    val key: String,
    val doc_count: Int
)
@OpenApi(
    summary = "Forslag til kompetanser basert på yrke",
    operationId = endepunkt,
    tags = [],
    requestBody = OpenApiRequestBody([OpenApiContent(RequestDto::class)]),
    path = endepunkt,
    methods = [HttpMethod.POST]
)
fun Javalin.handleKompetanseforslag(openSearchClient: OpenSearchClient) {
    post(endepunkt) { ctx ->
        ctx.authenticatedUser().verifiserAutorisasjon(Rolle.ARBEIDSGIVER_RETTET,  Rolle.UTVIKLER)
        val request = ctx.bodyAsClass<RequestDto>()
        val result = openSearchClient.lookupKompetanseforslag(request)
        ctx.json(result.toAggregationResponseJson() ?: throw RuntimeException("No aggregations found in response"))
    }
}

private fun OpenSearchClient.lookupKompetanseforslag(params: RequestDto): SearchResponse<JsonNode> {
    return search<JsonNode> {
        index(DEFAULT_INDEX)
        query_ {
            bool_ {
                should_(
                    params.yrker.map { yrke ->
                        {
                            match_ {
                                field("yrkeJobbonskerObj.styrkBeskrivelse")
                                query(yrke.yrke)
                            }
                        }
                    }
                )
            }
        }

        size(0)
        aggregations("kompetanse") {
            it.terms {agg ->
                agg.field("kompetanseObj.kompKodeNavn.keyword")
                agg.size(12)
            }
        }
    }
}

fun SearchResponse<JsonNode>.toAggregationResponseJson(): KompetanseAggregationResponse {
    val buckets = this.aggregations()["kompetanse"]?.sterms()?.buckets()?.array()

   return KompetanseAggregationResponse(
        KompetanseAggregations(
            KompetanseAggregation(
                buckets?.map {
                    Bucket(
                        key = it.key(),
                        doc_count = it.docCount().toInt()
                    )
                } ?: emptyList()
            )
        )
    )

}


