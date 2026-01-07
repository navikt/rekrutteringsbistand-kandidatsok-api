package no.nav.toi.lookupcv

import com.fasterxml.jackson.databind.JsonNode
import io.javalin.Javalin
import io.javalin.http.bodyAsClass
import io.javalin.openapi.*
import no.nav.toi.*
import no.nav.toi.kandidatsøk.ModiaKlient
import org.opensearch.client.opensearch.OpenSearchClient
import org.opensearch.client.opensearch.core.SearchResponse
import java.time.LocalDateTime

private const val endepunkt = "/api/multiple-lookup-cv"

private data class MultipleLookupCvRequestDto(
    val kandidatnr: List<String>,
)

@OpenApi(
    summary = "Oppslag av hele CVene til personer basert på kandidatnummer",
    operationId = endepunkt,
    tags = [],
    requestBody = OpenApiRequestBody([OpenApiContent(MultipleLookupCvRequestDto::class)]),
    responses = [OpenApiResponse("200", [OpenApiContent(OpensearchResponse::class)])],
    path = endepunkt,
    methods = [HttpMethod.POST]
)
fun Javalin.handleMultipleLookupCv(openSearchClient: OpenSearchClient, modiaKlient: ModiaKlient) {
    post(endepunkt) { ctx ->
        val startTime = LocalDateTime.now()
        val authenticatedUser = ctx.authenticatedUser()
        authenticatedUser.verifiserAutorisasjon(Rolle.JOBBSØKER_RETTET, Rolle.ARBEIDSGIVER_RETTET, Rolle.UTVIKLER)

        val navIdent = authenticatedUser.navIdent
        val result = openSearchClient.multipleLookupCv(ctx.bodyAsClass<MultipleLookupCvRequestDto>())
        val filtrerteKandidater = result.hits().hits().filter { kandidat ->
            val fodselsnummer = kandidat?.source()?.get("fodselsnummer")?.asText()
            val orgEnhet = kandidat?.source()?.get("orgenhet")?.asText()
            val veileder = kandidat?.source()?.get("veilederIdent")?.asText()
            authenticatedUser.harTilgangTilBruker(orgEnhet, veileder, modiaKlient)
        }

        log.info("Tid brukt på multipleLookupCv 1: ${java.time.Duration.between(startTime, LocalDateTime.now())}")

        val filtrertSearchResponse = SearchResponse.Builder<JsonNode>()
            .took(result.took())
            .timedOut(result.timedOut())
            .shards(result.shards())
            .hits { it.hits(filtrerteKandidater) }
            .aggregations(result.aggregations())
            .build()

        log.info("Tid brukt på multipleLookupCv 2: ${java.time.Duration.between(startTime, LocalDateTime.now())}")

        ctx.json(filtrertSearchResponse.toResponseJson())
        log.info("Tid brukt på multipleLookupCv 3: ${java.time.Duration.between(startTime, LocalDateTime.now())}")
    }
}


private fun OpenSearchClient.multipleLookupCv(params: MultipleLookupCvRequestDto): SearchResponse<JsonNode> =
    search<JsonNode> {
        index(DEFAULT_INDEX)
        query_ {
            terms_("kandidatnr" to params.kandidatnr)
        }
        size(params.kandidatnr.size)
    }



