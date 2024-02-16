package no.nav.toi

import com.fasterxml.jackson.databind.JsonNode
import org.apache.http.HttpHost
import org.apache.http.auth.AuthScope
import org.apache.http.auth.UsernamePasswordCredentials
import org.apache.http.impl.client.BasicCredentialsProvider
import org.opensearch.client.RestClient
import org.opensearch.client.json.jackson.JacksonJsonpMapper
import org.opensearch.client.opensearch.OpenSearchClient
import org.opensearch.client.opensearch._types.FieldValue
import org.opensearch.client.opensearch._types.SortOrder
import org.opensearch.client.opensearch._types.query_dsl.*
import org.opensearch.client.opensearch.core.SearchRequest
import org.opensearch.client.opensearch.core.SearchResponse
import org.opensearch.client.opensearch.core.search.SourceConfig
import org.opensearch.client.opensearch.core.search.SourceFilter
import org.opensearch.client.opensearch.core.search.TrackHits
import org.opensearch.client.transport.rest_client.RestClientTransport
import org.opensearch.client.util.ObjectBuilder
import java.net.URI

const val DEFAULT_INDEX = "veilederkandidat_current"

fun createOpenSearchClient(
    openSearchUsername: String = System.getenv("OPEN_SEARCH_USERNAME")!!,
    openSearchPassword: String = System.getenv("OPEN_SEARCH_PASSWORD")!!,
    openSearchUri: String = System.getenv("OPEN_SEARCH_URI")!!,
): OpenSearchClient {
    val httpHost = URI(openSearchUri).run {
        HttpHost(host, port, scheme)
    }

    val credentialsProvider = BasicCredentialsProvider().apply {
        setCredentials(AuthScope(httpHost), UsernamePasswordCredentials(openSearchUsername, openSearchPassword))
    }

    val restClient = RestClient.builder(httpHost)
        .setHttpClientConfigCallback { httpClientBuilder ->
            httpClientBuilder.setDefaultCredentialsProvider(credentialsProvider)
        }
        .build()

    val transport = RestClientTransport(restClient, JacksonJsonpMapper())
    return OpenSearchClient(transport)
}

inline fun <reified T> OpenSearchClient.search(
    crossinline body: SearchRequest.Builder.() -> ObjectBuilder<SearchRequest>
): SearchResponse<T> =
    search({ it.body() }, T::class.java)

fun SearchRequest.Builder.query_(
    body: Query.Builder.() -> ObjectBuilder<Query>
): SearchRequest.Builder =
    query { it.body() }

fun NestedQuery.Builder.query_(
    body: Query.Builder.() -> ObjectBuilder<Query>
): NestedQuery.Builder =
    query { it.body() }

fun Query.Builder.term_(
    body: TermQuery.Builder.() -> ObjectBuilder<TermQuery>
): ObjectBuilder<Query> =
    term { it.body() }

fun SearchRequest.Builder.source_(
    body: SourceConfig.Builder.() -> ObjectBuilder<SourceConfig>
): SearchRequest.Builder =
    source { it.body() }

fun Query.Builder.bool_(
    body: BoolQuery.Builder.() -> ObjectBuilder<BoolQuery>
): ObjectBuilder<Query> =
    bool { it.body() }

fun BoolQuery.Builder.must_(
    body: Query.Builder.() -> ObjectBuilder<Query>
): ObjectBuilder<BoolQuery> =
    must { it.body() }

fun BoolQuery.Builder.mustNot_(
    body: Query.Builder.() -> ObjectBuilder<Query>
): ObjectBuilder<BoolQuery> =
    mustNot { it.body() }

fun BoolQuery.Builder.should_(
    body: Query.Builder.() -> ObjectBuilder<Query>
): ObjectBuilder<BoolQuery> =
    should { it.body() }

fun Query.Builder.matchPhrase_(
    body: MatchPhraseQuery.Builder.() -> ObjectBuilder<MatchPhraseQuery>
): ObjectBuilder<Query> =
    matchPhrase { it.body() }

fun Query.Builder.nested_(
    body: NestedQuery.Builder.() -> ObjectBuilder<NestedQuery>
): ObjectBuilder<Query> =
    nested { it.body() }

fun Query.Builder.match_(
    body: MatchQuery.Builder.() -> ObjectBuilder<MatchQuery>
): ObjectBuilder<Query> =
    match { it.body() }

fun Query.Builder.range_(
    body: RangeQuery.Builder.() -> ObjectBuilder<RangeQuery>
): ObjectBuilder<Query> =
    range { it.body() }

fun MatchQuery.Builder.query(
    field: String
): ObjectBuilder<MatchQuery> =
    query {
        it.stringValue(field)
    }

fun Query.Builder.regexp(
    field: String,
    value: String
): ObjectBuilder<Query> =
    regexp {
        it.field(field)
        it.value(value)
    }

fun Query.Builder.terms(
    fieldAndValue: Pair<String, List<String>>
): ObjectBuilder<Query> = fieldAndValue.let { (field, value) ->
    terms { termsQuery ->
        termsQuery.field(field)
        termsQuery.terms { it.value(value.map(FieldValue::of)) }
    }
}
fun SearchRequest.Builder.trackTotalHits(value: Boolean) = trackTotalHits(TrackHits.Builder().enabled(value).build())

fun SearchRequest.Builder.sort(felt: String, sortOrder: SortOrder) = sort {
    it.field { fieldSort ->
        fieldSort.field(felt)
        fieldSort.order(sortOrder)
    }
}

fun SourceConfig.Builder.includes(vararg includes: String) =
    filter(
        SourceFilter.of { filterBuilder ->
            filterBuilder.includes(includes.toList())
        }
    )


data class OpensearchResponse(
    val hits: Hits,
)

data class Hits(
    val hits: List<Hit>,
)

data class Hit(
    val _source: JsonNode,
)

fun SearchResponse<JsonNode>.toResponseJson(): OpensearchResponse =
    OpensearchResponse(
        hits = Hits(
            hits = hits().hits().mapNotNull {
                it.source()?.let { Hit(it) }
            }
        )
    )
