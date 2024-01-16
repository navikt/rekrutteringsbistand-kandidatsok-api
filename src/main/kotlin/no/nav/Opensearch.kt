package no.nav

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
import org.opensearch.client.transport.rest_client.RestClientTransport
import org.opensearch.client.util.ObjectBuilder
import java.net.URI

const val DEFAULT_INDEX = "veilederkandidat_current"

fun createOpenSearchClient(
    openSearchUsername: String = System.getenv("OPEN_SEARCH_USERNAME")!!,
    openSearchPassword: String = System.getenv("OPEN_SEARCH_PASSWORD")!!,
    openSearchUri: String = System.getenv("OPEN_SEARCH_URI")!!,
): OpenSearchClient {
    val parsedUri = URI(openSearchUri)
    val host = HttpHost(parsedUri.host, parsedUri.port, parsedUri.scheme)

    val credentialsProvider = BasicCredentialsProvider().apply {
        setCredentials(AuthScope(host), UsernamePasswordCredentials(openSearchUsername, openSearchPassword))
    }

    val restClient = RestClient.builder(host).setHttpClientConfigCallback { httpClientBuilder ->
        httpClientBuilder.setDefaultCredentialsProvider(
            credentialsProvider
        )
    }.build()

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

