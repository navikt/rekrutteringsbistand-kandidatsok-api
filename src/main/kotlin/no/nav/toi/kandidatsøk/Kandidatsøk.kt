package no.nav.toi.kandidatsøk

import com.fasterxml.jackson.databind.JsonNode
import io.javalin.Javalin
import io.javalin.openapi.*
import no.nav.toi.*
import no.nav.toi.kandidatsøk.filter.*
import org.opensearch.client.opensearch.OpenSearchClient
import org.opensearch.client.opensearch._types.SortOrder
import org.opensearch.client.opensearch.core.SearchResponse

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
    get(endepunkt) { ctx ->
        val filter = listOf(StedFilter(), Arbeidsønskefilter(), InnsatsgruppeFilter(), SpråkFilter())
            .onEach { it.berikMedParameter(ctx::queryParam) }
            .filter(Filter::erAktiv)
            .map(Filter::lagESFilterFunksjon)
        val result = openSearchClient.kandidatSøk(filter)
        val fodselsnummer = result.hits().hits().firstOrNull()?.source()?.get("fodselsnummer")?.asText()
        if (fodselsnummer != null) {
            AuditLogg.loggOppslagKandidatStillingssøk(fodselsnummer, ctx.authenticatedUser().navIdent)
        }
        ctx.json(result.toResponseJson())
    }
}

private fun OpenSearchClient.kandidatSøk(filter: List<FilterFunksjon>): SearchResponse<JsonNode> {
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