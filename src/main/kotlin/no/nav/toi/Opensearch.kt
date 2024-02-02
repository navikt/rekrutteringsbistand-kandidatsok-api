package no.nav.toi

import com.fasterxml.jackson.databind.JsonNode
import org.apache.http.HttpHost
import org.apache.http.auth.AuthScope
import org.apache.http.auth.UsernamePasswordCredentials
import org.apache.http.impl.client.BasicCredentialsProvider
import org.opensearch.client.RestClient
import org.opensearch.client.json.jackson.JacksonJsonpMapper
import org.opensearch.client.opensearch.OpenSearchClient
import org.opensearch.client.opensearch._types.query_dsl.Query
import org.opensearch.client.opensearch._types.query_dsl.TermQuery
import org.opensearch.client.opensearch.core.SearchRequest
import org.opensearch.client.opensearch.core.SearchResponse
import org.opensearch.client.opensearch.core.search.SourceConfig
import org.opensearch.client.opensearch.core.search.SourceFilter
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

fun Query.Builder.term_(
    body: TermQuery.Builder.() -> ObjectBuilder<TermQuery>
): ObjectBuilder<Query> =
    term { it.body() }

fun SearchRequest.Builder.source_(
    body: SourceConfig.Builder.() -> ObjectBuilder<SourceConfig>
): SearchRequest.Builder =
    source { it.body() }

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
