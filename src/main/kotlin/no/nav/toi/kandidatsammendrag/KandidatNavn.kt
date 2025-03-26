package no.nav.toi.kandidatsammendrag

import com.fasterxml.jackson.databind.JsonNode
import com.github.kittinunf.fuel.Fuel
import com.github.kittinunf.fuel.core.Headers
import com.github.kittinunf.fuel.core.extensions.authentication
import com.github.kittinunf.fuel.core.extensions.jsonBody
import com.github.kittinunf.fuel.jackson.responseObject
import io.javalin.Javalin
import io.javalin.http.InternalServerErrorResponse
import io.javalin.http.UnauthorizedResponse
import io.javalin.http.bodyAsClass
import io.javalin.openapi.*
import no.nav.toi.*
import org.opensearch.client.opensearch.OpenSearchClient
import org.opensearch.client.opensearch.core.SearchResponse

private const val endepunkt = "/api/navn"

private data class KandidatNavnRequestDto(
    val fodselsnummer: String,
)
private enum class Kilde { REKRUTTERINGSBISTAND, PDL }

private data class KandidatNavnResponsDto(
    val fornavn: String,
    val etternavn: String,
    val kilde: Kilde
)

@OpenApi(
    summary = "Oppslag av navn for en enkelt person basert på fødselsnummer",
    operationId = endepunkt,
    tags = [],
    requestBody = OpenApiRequestBody([OpenApiContent(KandidatNavnRequestDto::class)]),
    responses = [OpenApiResponse("200", [OpenApiContent(KandidatNavnResponsDto::class)])],
    path = endepunkt,
    methods = [HttpMethod.POST]
)
fun Javalin.handleKandidatNavn(openSearchClient: OpenSearchClient, pdlKlient: PdlKlient, accessTokenClient: AccessTokenClient) {
    post(endepunkt) { ctx ->
        ctx.authenticatedUser().verifiserAutorisasjon(Rolle.UTVIKLER, Rolle.ARBEIDSGIVER_RETTET, Rolle.JOBBSØKER_RETTET)

        val request = ctx.bodyAsClass<KandidatNavnRequestDto>()
        AuditLogg.loggOppslagNavn(request.fodselsnummer, ctx.authenticatedUser().navIdent)
        if (harAdressebeskyttelse(request.fodselsnummer, accessTokenClient, ctx.authenticatedUser().jwt)) {
            ctx.status(403)
            return@post
        }
        val result = openSearchClient.lookupKandidatNavn(request.fodselsnummer)
        result.hits().hits().firstOrNull()?.source()?.let {
            ctx.json(KandidatNavnResponsDto(it["fornavn"]!!.asText(), it["etternavn"]!!.asText(), Kilde.REKRUTTERINGSBISTAND))
        } ?: pdlKlient.hentFornavnOgEtternavn(request.fodselsnummer, ctx.authenticatedUser().jwt)?.let { (fornavn, etternavn) ->
            ctx.json(KandidatNavnResponsDto(fornavn,etternavn, Kilde.PDL))
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

private fun harAdressebeskyttelse(fodselsnummer: String, accessTokenClient: AccessTokenClient, innkommendeToken: String): Boolean {
    val accessToken = accessTokenClient.hentAccessToken(innkommendeToken)

    val (_, response, result) = Fuel.post("http://toi-livshendelse/adressebeskyttelse")
        .header(Headers.CONTENT_TYPE, "application/json")
        .authentication().bearer(accessToken)
        .jsonBody("""{"fnr": "$fodselsnummer"}""")
        .responseObject<ResponseAdressebeskyttelse>()

    if(response.statusCode == 401) throw UnauthorizedResponse("Du har ikke tilgang")
    if (response.statusCode == 500) throw InternalServerErrorResponse("Noe gikk galt")

    return result.get().harAdressebeskyttelse
}

private class ResponseAdressebeskyttelse(
    val harAdressebeskyttelse: Boolean
)