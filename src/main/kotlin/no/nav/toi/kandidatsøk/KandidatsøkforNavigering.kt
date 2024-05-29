package no.nav.toi.kandidatsøk

import com.fasterxml.jackson.databind.JsonNode
import io.javalin.Javalin
import io.javalin.http.HttpStatus
import io.javalin.http.bodyAsClass
import io.javalin.openapi.*
import no.nav.toi.*
import no.nav.toi.kandidatsøk.filter.Filter
import no.nav.toi.kandidatsøk.filter.FilterFunksjon
import no.nav.toi.kandidatsøk.filter.Valideringsfeil
import no.nav.toi.kandidatsøk.filter.søkeFilter
import org.opensearch.client.opensearch.OpenSearchClient
import org.opensearch.client.opensearch.core.SearchResponse
import org.opensearch.client.opensearch.core.search.Hit
import kotlin.math.max

private const val endepunkt = "/api/kandidatsok/navigering"

private data class Respons(
    val antall: Long,
    val kandidatnumre: List<String>
)

@OpenApi(
    summary = "Søk opp kandidatnumre for å bruke til navigering basert på søketermer",
    operationId = endepunkt,
    tags = [],
    requestBody = OpenApiRequestBody([OpenApiContent(FilterParametre::class)]),
    responses = [OpenApiResponse("200", [OpenApiContent(Respons::class)])],
    path = endepunkt,
    methods = [HttpMethod.POST]
)
fun Javalin.handleKandidatSøkForNavigering(openSearchClient: OpenSearchClient, modiaKlient: ModiaKlient) {
    post(endepunkt) { ctx ->
        val request = ctx.bodyAsClass<FilterParametre>()
        val sorterting = ctx.queryParam("sortering").tilSortering()
        try {
            val filter = søkeFilter(ctx.authenticatedUser(), modiaKlient)
                .onEach { it.berikMedParameter(request) }
                .filter(Filter::erAktiv)
            val side = ctx.queryParam("side")?.toInt() ?: 1
            val result = openSearchClient.kandidatSøk(filter.map(Filter::lagESFilterFunksjon), side, sorterting)
            ctx.json(result.hentUtKandidatnumre())
        } catch (e: Valideringsfeil) {
            ctx.status(HttpStatus.BAD_REQUEST)
        }
    }
}

private fun  SearchResponse<JsonNode>.hentUtKandidatnumre() = Respons(
    hits().total().value(),
    hits().hits().map(Hit<JsonNode>::id)
)

private fun OpenSearchClient.kandidatSøk(filter: List<FilterFunksjon>, side: Int, sorterting: Sortering): SearchResponse<JsonNode> {
    return search<JsonNode> {
        index(DEFAULT_INDEX)
        query_ {
            bool_ {
                apply { filter.forEach{it()} }
            }
        }
        source(false)
        trackTotalHits(true)
        sorterting.lagSorteringES()()
        size(500)
        from(max(0, side * 25 - 500 / 2))
    }
}