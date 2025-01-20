package no.nav.toi.kandidatsammendrag

import com.fasterxml.jackson.databind.JsonNode
import io.javalin.Javalin
import io.javalin.http.bodyAsClass
import io.javalin.openapi.*
import no.nav.toi.*
import org.opensearch.client.opensearch.OpenSearchClient
import org.opensearch.client.opensearch.core.SearchResponse

private const val endepunkt = "/api/navn"

private data class KandidatNavnOgGraderingRequestDto(
    val fodselsnummer: String,
)
private enum class Kilde { REKRUTTERINGSBISTAND, PDL }

private data class KandidatNavnOgGraderingResponsDto(
    val fornavn: String,
    val etternavn: String,
    val harAdressebeskyttelse: Boolean?,
    val kilde: Kilde
)

@OpenApi(
    summary = "Oppslag av navn for en enkelt person basert på fødselsnummer",
    operationId = endepunkt,
    tags = [],
    requestBody = OpenApiRequestBody([OpenApiContent(KandidatNavnOgGraderingRequestDto::class)]),
    responses = [OpenApiResponse("200", [OpenApiContent(KandidatNavnOgGraderingResponsDto::class)])],
    path = endepunkt,
    methods = [HttpMethod.POST]
)
fun Javalin.handleKandidatNavn(openSearchClient: OpenSearchClient, pdlKlient: PdlKlient) {
    post(endepunkt) { ctx ->
        ctx.authenticatedUser().verifiserAutorisasjon(Rolle.UTVIKLER, Rolle.ARBEIDSGIVER_RETTET, Rolle.JOBBSØKER_RETTET)

        val request = ctx.bodyAsClass<KandidatNavnOgGraderingRequestDto>()
        val result = openSearchClient.lookupKandidatNavn(request.fodselsnummer)
        AuditLogg.loggOppslagNavn(request.fodselsnummer, ctx.authenticatedUser().navIdent)
        result.hits().hits().firstOrNull()?.source()?.let {
            ctx.json(KandidatNavnOgGraderingResponsDto(it["fornavn"]!!.asText(), it["etternavn"]!!.asText(), null, Kilde.REKRUTTERINGSBISTAND))
        } ?: pdlKlient.hentFornavnOgEtternavn(request.fodselsnummer, ctx.authenticatedUser().jwt)?.let {
            val harAdressebeskyttelse = it.gradering != Gradering.UGRADERT
            ctx.json(KandidatNavnOgGraderingResponsDto(it.fornavn, it.etternavn, harAdressebeskyttelse, Kilde.PDL))
        } ?: ctx.status(404)
    }
}

private fun OpenSearchClient.lookupKandidatNavn(fodselsnummer: String): SearchResponse<JsonNode> {
    return search<JsonNode> {
        index(DEFAULT_INDEX)
        query_ {
            term_ { field("fodselsnummer").value(fodselsnummer) }
        }
        source_ {
            includes("fornavn", "etternavn")
        }
    }
}
