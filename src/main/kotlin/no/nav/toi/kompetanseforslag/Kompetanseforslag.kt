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
import org.opensearch.client.opensearch._types.FieldValue
import org.opensearch.client.opensearch.core.SearchResponse


private const val endepunkt = "/api/kompetanseforslag"

private data class Yrke(
    val yrke: String,
)

private data class RequestDto(
    val yrker: List<Yrke>
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
        val request = ctx.bodyAsClass<RequestDto>()
        val result = openSearchClient.lookupKompetanseforslag(request)
        ctx.json(result.toResponseJson())
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
                                query(FieldValue.of(yrke.yrke))
                            }
                        }
                    }
                )
            }
        }
        size(0)
        aggregations("kompetanse") {
            it.terms {
                it.field("kompetanseObj.kompKodeNavn.keyword")
                it.size(12)
            }
        }
    }
}


/*
{
  "query": {
    "bool": {
      "should": [
        {
          "match": {
            "yrkeJobbonskerObj.styrkBeskrivelse": "Mat og livsstils videograf"
          }
        },
        {
          "match": {
            "yrkeJobbonskerObj.styrkBeskrivelse": "Kokk"
          }
        }
      ]
    }
  },
  "size": 0,
  "aggs": {
    "kompetanse": {
      "terms": {
        "field": "kompetanseObj.kompKodeNavn.keyword",
        "size": 12
      }
    }
  }
}
 */