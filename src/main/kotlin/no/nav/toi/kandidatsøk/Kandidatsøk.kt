package no.nav.toi.kandidatsøk

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.databind.JsonNode
import io.javalin.Javalin
import io.javalin.http.Handler
import io.javalin.http.HttpStatus
import io.javalin.http.bodyAsClass
import io.javalin.openapi.*
import no.nav.toi.*
import no.nav.toi.kandidatsøk.filter.*
import no.nav.toi.kandidatsøk.filter.porteføljefilter.medMineBrukereFilter
import no.nav.toi.kandidatsøk.filter.porteføljefilter.medMineKontorerFilter
import no.nav.toi.kandidatsøk.filter.porteføljefilter.medMittKontor
import no.nav.toi.kandidatsøk.filter.porteføljefilter.medValgtKontorerFilter
import org.opensearch.client.opensearch.OpenSearchClient
import org.opensearch.client.opensearch.core.SearchResponse
import kotlin.math.max

private const val endepunkt = "/api/kandidatsok"

@JsonIgnoreProperties(ignoreUnknown = true)
data class FilterParametre(
    val fritekst: String?,
    val portefølje: String?,
    val valgtKontor: List<String>?,
    val innsatsgruppe: List<String>?,
    val ønsketYrke: List<String>?,
    val ønsketSted: List<String>?,
    val borPåØnsketSted: Boolean?,
    val kompetanse: List<String>?,
    val førerkort: List<String>?,
    val prioritertMålgruppe: List<String>?,
    val hovedmål:List<String>?,
    val utdanningsnivå:List<String>?,
    val arbeidserfaring:List<String>?,
    val ferskhet: Int?,
    val språk: List<String>?,
    val orgenhet: String?
)


@OpenApi(
    summary = "Søk på kandidater basert på søketermer",
    operationId = endepunkt,
    tags = [],
    requestBody = OpenApiRequestBody([OpenApiContent(FilterParametre::class)]),
    responses = [OpenApiResponse("200", [OpenApiContent(OpensearchResponse::class)])],
    path = endepunkt,
    methods = [HttpMethod.POST]
)
fun Javalin.handleKandidatSøk(openSearchClient: OpenSearchClient, modiaKlient: ModiaKlient) {
    post(endepunkt, håndterEndepunkt(modiaKlient, openSearchClient))
    post("$endepunkt/minebrukere", håndterEndepunkt(modiaKlient, openSearchClient) { authenticatedUser, _ ->  medMineBrukereFilter(authenticatedUser)})
    post("$endepunkt/valgtekontorer", håndterEndepunkt(modiaKlient, openSearchClient) { _, filterParametre ->  medValgtKontorerFilter(filterParametre)})
    post("$endepunkt/minekontorer", håndterEndepunkt(modiaKlient, openSearchClient) { authenticatedUser, _ ->  medMineKontorerFilter(authenticatedUser, modiaKlient)})
    post("$endepunkt/mittkontor", håndterEndepunkt(modiaKlient, openSearchClient) { _, filterParametre ->  medMittKontor(filterParametre)})
}

private fun håndterEndepunkt(
    modiaKlient: ModiaKlient,
    openSearchClient: OpenSearchClient,
    filterPopuleringsFunksjon: List<Filter>.(AuthenticatedUser?, FilterParametre) -> List<Filter> = { _, _-> this }
) = Handler { ctx ->
    val request = ctx.bodyAsClass<FilterParametre>()
    val sorterting = ctx.queryParam("sortering").tilSortering()
    try {
        val filter = søkeFilter(ctx.authenticatedUser(), modiaKlient, request)
            .filterPopuleringsFunksjon(ctx.authenticatedUser(),request)
            .filter(Filter::erAktiv)
        val filterFunksjoner = filter
            .map(Filter::lagESFilterFunksjon)
        val side = ctx.queryParam("side")?.toInt() ?: 1
        val result = openSearchClient.kandidatSøk(filterFunksjoner, side, sorterting).toResponseJson()
        val navigeringResult =
            openSearchClient.kandidatSøkNavigering(filterFunksjoner, side, sorterting).hentUtKandidatnumre()
        val hits = result.hits
        filter.forEach {
            it.auditLog(
                ctx.authenticatedUser().navIdent,
                hits.hits.map { it._source["fodselsnummer"].asText() }.firstOrNull()
            )
        }

        val kandidater: List<JsonNode> = hits.hits.map { it._source }
        ctx.json(
            KandidatSøkOpensearchResponseMedNavigering(
                kandidater,
                NavigeringRespons(navigeringResult.kandidatnumre),
                hits.total.value
            )
        )
    } catch (e: Valideringsfeil) {
        ctx.status(HttpStatus.BAD_REQUEST)
    }
}


private fun OpenSearchClient.kandidatSøk(filter: List<FilterFunksjon>, side: Int, sorterting: Sortering): SearchResponse<JsonNode> {
    return search<JsonNode> {
        index(DEFAULT_INDEX)
        query_ {
            bool_ {
                apply { filter.forEach{it()} }
            }
        }
        source_ {
            includes(
                "fodselsnummer","fornavn","etternavn","arenaKandidatnr","kvalifiseringsgruppekode","yrkeJobbonskerObj"
                ,"geografiJobbonsker","kommuneNavn","postnummer"
            )
        }
        trackTotalHits(true)
        sorterting.lagSorteringES()()
        size(25)
        from(25*(side-1))
    }
}


private data class KandidatSøkOpensearchResponse(
    val hits: KandidatSøkHits,
)

private data class KandidatSøkOpensearchResponseMedNavigering(
    val kandidater: List<JsonNode>,
    val navigering: NavigeringRespons,
    val antallTotalt: Long
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

private data class NavigeringRespons(
    val kandidatnumre: List<String>
)

private fun  SearchResponse<JsonNode>.hentUtKandidatnumre() = NavigeringRespons(
    hits().hits().map(org.opensearch.client.opensearch.core.search.Hit<JsonNode>::id)
)

private fun OpenSearchClient.kandidatSøkNavigering(filter: List<FilterFunksjon>, side: Int, sorterting: Sortering): SearchResponse<JsonNode> {
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