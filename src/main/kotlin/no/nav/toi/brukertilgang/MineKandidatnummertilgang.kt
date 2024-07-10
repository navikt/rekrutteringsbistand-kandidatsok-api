package no.nav.toi.brukertilgang

import com.fasterxml.jackson.databind.JsonNode
import io.javalin.Javalin
import io.javalin.http.bodyAsClass
import io.javalin.openapi.*
import no.nav.toi.*
import no.nav.toi.kandidatsøk.ModiaKlient
import no.nav.toi.kandidatsøk.filter.Filter
import no.nav.toi.kandidatsøk.filter.porteføljefilter.medMineBrukereFilter
import no.nav.toi.kandidatsøk.filter.porteføljefilter.medMineKontorerFilter
import org.opensearch.client.opensearch.OpenSearchClient
import org.opensearch.client.opensearch.core.SearchResponse
import org.opensearch.client.opensearch.core.search.Hit

private const val endepunkt = "/api/minekandidatnummer"

@OpenApi(
    summary = "Oppslag for å sjekke hvilke kandidater en bruker har tilgang til",
    operationId = endepunkt,
    tags = [],
    requestBody = OpenApiRequestBody([OpenApiContent(Array<String>::class)]),
    responses = [OpenApiResponse("200"), OpenApiResponse("400", description = "Bad Request")],
    path = endepunkt,
    methods = [HttpMethod.POST]
)
fun Javalin.handleMinekandidatnummer(openSearchClient: OpenSearchClient, modiaKlient: ModiaKlient) {
    post(endepunkt) { ctx ->
        val authenticatedUser = ctx.authenticatedUser()
        authenticatedUser.verifiserAutorisasjon(Rolle.JOBBSØKER_RETTET, Rolle.ARBEIDSGIVER_RETTET, Rolle.UTVIKLER)

        val request = ctx.bodyAsClass<List<String>>()
        log.info("Oppslag for minekandidatnummer")
        val result = openSearchClient.kandidatSøk(authenticatedUser, kontorer = modiaKlient.hentModiaEnheter(authenticatedUser.jwt).map { it.navn })

        val kandidaterBrukerKanSe = result.hits().hits().map(Hit<JsonNode>::id)

        ctx.json(request.filter { it in kandidaterBrukerKanSe })
    }
}

private fun OpenSearchClient.kandidatSøk(authenticatedUser: AuthenticatedUser, kontorer: List<String>): SearchResponse<JsonNode> {
    return search<JsonNode> {
        index(DEFAULT_INDEX)
        query_ {
            bool_ {
                should_ {
                    term_ {
                        field("veileder")
                        value(authenticatedUser!!.navIdent)
                        caseInsensitive(true)
                    }
                }
                should_ {
                    bool_ {
                        apply {
                            kontorer.forEach { kontor ->
                                should_ {
                                    term_ {
                                        field("navkontor")
                                        value(kontor)
                                    }
                                }
                            }
                        }
                    }
                }

            }
        }
        source(false)
        trackTotalHits(true)
        from(0)
        size(10000)
    }
}