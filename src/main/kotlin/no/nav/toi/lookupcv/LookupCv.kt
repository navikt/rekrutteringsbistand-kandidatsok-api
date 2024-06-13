package no.nav.toi.lookupcv

import com.fasterxml.jackson.databind.JsonNode
import io.javalin.Javalin
import io.javalin.http.NotFoundResponse
import io.javalin.http.UnauthorizedResponse
import io.javalin.http.bodyAsClass
import io.javalin.openapi.*
import no.nav.toi.*
import no.nav.toi.kandidatsøk.ModiaKlient
import org.opensearch.client.opensearch.OpenSearchClient
import org.opensearch.client.opensearch.core.SearchResponse

private const val endepunkt = "/api/lookup-cv"

private data class RequestDto(
    val kandidatnr: String,
)

@OpenApi(
    summary = "Oppslag av hele CVen til en enkelt person basert på kandidatnummer",
    operationId = endepunkt,
    tags = [],
    requestBody = OpenApiRequestBody([OpenApiContent(RequestDto::class)]),
    responses = [OpenApiResponse("200", [OpenApiContent(OpensearchResponse::class)])],
    path = endepunkt,
    methods = [HttpMethod.POST]
)
fun Javalin.handleLookupCv(openSearchClient: OpenSearchClient, modiaKlient: ModiaKlient) {
    post(endepunkt) { ctx ->
        val authenticatedUser = ctx.authenticatedUser()
        authenticatedUser.verifiserAutorisasjon(Rolle.ARBEIDSGIVER_RETTET, Rolle.UTVIKLER, Rolle.JOBBSØKER_RETTET)

        val navIdent = authenticatedUser.navIdent
        val result = openSearchClient.lookupCv(ctx.bodyAsClass<RequestDto>())
        val kandidat = result.hits().hits().firstOrNull()?.source()
        val fodselsnummer = kandidat?.get("fodselsnummer")?.asText() ?: throw NotFoundResponse()

        if (Rolle.JOBBSØKER_RETTET in authenticatedUser.roller &&
            !erEgenBrukerEllerKontorenesBruker(kandidat, modiaKlient, authenticatedUser, navIdent)) {
            AuditLogg.loggOppslagCv(fodselsnummer, navIdent, false)
            throw UnauthorizedResponse()
        }

        AuditLogg.loggOppslagCv(fodselsnummer, navIdent, true)
        ctx.json(result.toResponseJson())
    }
}

private fun erEgenBrukerEllerKontorenesBruker(
    kandidat: JsonNode,
    modiaKlient: ModiaKlient,
    authenticatedUser: AuthenticatedUser,
    navIdent: String
): Boolean {
    val orgEnhetKandidat = kandidat.get("orgenhet")?.asText()
    val veilederKandidat = kandidat.get("veileder")?.asText()
    val kontorer = modiaKlient.hentModiaEnheter(authenticatedUser.jwt).map { it.enhetId }

    return !(orgEnhetKandidat != null && veilederKandidat != null && veilederKandidat != navIdent && orgEnhetKandidat !in kontorer)
}


private fun OpenSearchClient.lookupCv(params: RequestDto): SearchResponse<JsonNode> =
    search<JsonNode> {
        index(DEFAULT_INDEX)
        query_ {
            term_ {
                field("kandidatnr").value(params.kandidatnr)
            }
        }
        size(1)
    }