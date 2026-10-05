package no.nav.toi.kandidatsammendrag

import io.javalin.http.InternalServerErrorResponse
import io.javalin.http.NotFoundResponse
import no.nav.toi.AccessTokenClient
import no.nav.toi.erVellykket
import no.nav.toi.httpClient
import no.nav.toi.httpKlientMapper
import no.nav.toi.httpRequest
import tools.jackson.module.kotlin.readValue
import java.net.http.HttpRequest.BodyPublishers
import java.net.http.HttpResponse.BodyHandlers

class PdlKlient(private val pdlUrl: String, private val accessTokenClient: AccessTokenClient) {
    fun hentFornavnOgEtternavn(fødselsnummer: String, innkommendeToken: String): Pair<String, String>? {

        val accessToken = accessTokenClient.hentAccessToken(innkommendeToken)
        val graphql = lagGraphQLSpørring(fødselsnummer)

        val request = httpRequest(pdlUrl)
            .header("Authorization", "Bearer $accessToken")
            .header("Content-Type", "application/json")
            .header("Tema", "GEN")
            .header("Behandlingsnummer", "B346")
            .POST(BodyPublishers.ofString(graphql))
            .build()

        val response = httpClient.send(request, BodyHandlers.ofString())
        if (!response.erVellykket()) {
            throw RuntimeException("Noe feil skjedde ved henting av navn fra PDL, status ${response.statusCode()}")
        }

        val respons = httpKlientMapper.readValue<Respons>(response.body())
        if (respons.errors?.isNotEmpty() == true) {
            if (respons.errors.any { it.extensions.code != "not_found" }) {
                throw InternalServerErrorResponse("Feil ved henting av navn fra PDL: ${respons.errors.first().message}")
            }
            else throw NotFoundResponse("Fant ikke person i PDL")
        }
        return respons.data.hentPerson?.navn?.first()?.let {
            it.fornavn + (it.mellomnavn?.let { " $it" } ?: "") to it.etternavn
        }
    }

    private fun lagGraphQLSpørring(fødselsnummer: String): String {
        val pesostegn = "$"

        return """
            {
                "query": "query(${'$'}ident: ID!){ hentPerson(ident: ${'$'}ident) {navn(historikk: false) {fornavn mellomnavn etternavn}}}",
                "variables": {
                    "ident":"$fødselsnummer"
                }
            }
        """.trimIndent()
    }
}
private data class Respons(
    var data: Data,
    val errors: List<Error>?,
)

private data class Data(
    val hentPerson: HentPerson?,
)

private data class HentPerson(
    val navn: List<Navn>,
)

private data class Navn(
    val fornavn: String,
    val mellomnavn: String?,
    val etternavn: String
)

private data class Error(
    val message: String,
    val extensions: Extensions
)

private data class Extensions(
    val code: String
)
