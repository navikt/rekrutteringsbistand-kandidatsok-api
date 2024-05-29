package no.nav.toi.kandidatsøk

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.databind.JsonNode
import io.javalin.Javalin
import io.javalin.http.HttpStatus
import io.javalin.http.bodyAsClass
import io.javalin.openapi.*
import no.nav.toi.*
import no.nav.toi.kandidatsøk.filter.*
import org.opensearch.client.opensearch.OpenSearchClient
import org.opensearch.client.opensearch.core.SearchResponse

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
    post(endepunkt) { ctx ->
        val request = ctx.bodyAsClass<FilterParametre>()
        val sorterting = ctx.queryParam("sortering").tilSortering()
        try {
            val filter = søkeFilter(ctx.authenticatedUser(), modiaKlient)
                .onEach { it.berikMedParameter(request) }
                .filter(Filter::erAktiv)
            val side = ctx.queryParam("side")?.toInt() ?: 1
            val result = openSearchClient.kandidatSøk(filter.map(Filter::lagESFilterFunksjon), side, sorterting).toResponseJson()
            filter.forEach {
                it.auditLog(
                    ctx.authenticatedUser().navIdent,
                    result.hits.hits.map { it._source["fodselsnummer"].asText() }.firstOrNull()
                )
            }
            ctx.json(result)
        } catch (e: Valideringsfeil) {
            ctx.status(HttpStatus.BAD_REQUEST)
        }
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