package no.nav.toi

import com.fasterxml.jackson.databind.JsonNode
import org.apache.http.HttpHost
import org.apache.http.auth.AuthScope
import org.apache.http.auth.UsernamePasswordCredentials
import org.apache.http.impl.client.BasicCredentialsProvider
import org.opensearch.client.RestClient
import org.opensearch.client.json.JsonData
import org.opensearch.client.json.jackson.JacksonJsonpMapper
import org.opensearch.client.opensearch.OpenSearchClient
import org.opensearch.client.opensearch._types.FieldValue
import org.opensearch.client.opensearch._types.SortOrder
import org.opensearch.client.opensearch._types.query_dsl.*
import org.opensearch.client.opensearch.core.SearchRequest
import org.opensearch.client.opensearch.core.SearchResponse
import org.opensearch.client.opensearch.core.search.*
import org.opensearch.client.transport.rest_client.RestClientTransport
import org.opensearch.client.util.ObjectBuilder
import java.net.URI
import java.time.LocalDate

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

fun SearchRequest.Builder.suggest_(
    body: Suggester.Builder.() -> ObjectBuilder<Suggester>
): SearchRequest.Builder =
    suggest { it.body() }

fun Suggester.Builder.suggesters_(
    key: String,
    body: FieldSuggester.Builder.() -> ObjectBuilder<FieldSuggester>
): Suggester.Builder =
    suggesters(key) { it.body() }

fun FieldSuggester.Builder.completion_(
    body: CompletionSuggester.Builder.() -> ObjectBuilder<CompletionSuggester>
): ObjectBuilder<FieldSuggester> =
    completion { it.body() }

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

fun Query.Builder.multiMatch_(
    body: MultiMatchQuery.Builder.() -> ObjectBuilder<MultiMatchQuery>
): ObjectBuilder<Query> =
    multiMatch { it.body() }

fun TermQuery.Builder.value(value: String): ObjectBuilder<TermQuery> = value(FieldValue.of(value))

fun Query.Builder.bool_(
    body: BoolQuery.Builder.() -> ObjectBuilder<BoolQuery>
): ObjectBuilder<Query> =
    bool { it.body() }

fun BoolQuery.Builder.should_(
    queriesBuilders: List<(Query.Builder.() -> Unit)>
): BoolQuery.Builder = apply {
    val queries = queriesBuilders.map { builder ->
        Query.Builder().apply(builder).build()
    }
    should(queries)
}

fun SearchRequest.Builder.source_(
    body: SourceConfig.Builder.() -> ObjectBuilder<SourceConfig>
): SearchRequest.Builder =
    source { it.body() }

fun SearchRequest.Builder.source(
    value: Boolean
): SearchRequest.Builder =
    source { it.fetch(value) }

fun BoolQuery.Builder.must_(
    body: Query.Builder.() -> ObjectBuilder<Query>
): ObjectBuilder<BoolQuery> =
    must { it.body() }

fun Query.Builder.exists_(
    body: ExistsQuery.Builder.() -> ObjectBuilder<ExistsQuery>
): ObjectBuilder<Query> =
    exists { it.body() }

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

fun RangeQuery.Builder.gte(value: String): ObjectBuilder<RangeQuery> = gte(JsonData.of(value))
fun RangeQuery.Builder.gte(date: LocalDate): ObjectBuilder<RangeQuery> = gte(JsonData.of(date.toString()))
fun RangeQuery.Builder.lte(date: LocalDate): ObjectBuilder<RangeQuery> = lte(JsonData.of(date.toString()))
fun RangeQuery.Builder.lt(value: String): ObjectBuilder<RangeQuery> = lt(JsonData.of(value))

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
