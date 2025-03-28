package no.nav.toi.kandidatsammendrag

import com.github.kittinunf.fuel.Fuel
import com.github.kittinunf.fuel.core.Headers
import com.github.kittinunf.fuel.core.extensions.authentication
import com.github.kittinunf.fuel.core.extensions.jsonBody
import com.github.kittinunf.fuel.jackson.responseObject
import com.github.kittinunf.result.Result
import io.javalin.http.InternalServerErrorResponse
import io.javalin.http.NotFoundResponse
import no.nav.toi.AccessTokenClient

class PdlKlient(private val pdlUrl: String, private val accessTokenClient: AccessTokenClient) {
    fun hentFornavnOgEtternavn(fødselsnummer: String, innkommendeToken: String): Pair<String, String>? {

        val accessToken = accessTokenClient.hentAccessToken(innkommendeToken)
        val graphql = lagGraphQLSpørring(fødselsnummer)

        val (_, _, result) = Fuel.post(pdlUrl)
            .header(Headers.CONTENT_TYPE, "application/json")
            .header("Tema", "GEN")
            .header("Behandlingsnummer", "B346")
            .authentication().bearer(accessToken)
            .jsonBody(graphql)
            .responseObject<Respons>()

        when (result) {
            is Result.Success -> {
                val respons = result.get()
                if(respons.errors?.isNotEmpty() == true) {
                    if(respons.errors.any { it.extensions.code != "not_found" }) {
                        throw InternalServerErrorResponse("Feil ved henting av navn fra PDL: ${respons.errors.first().message}")
                    }
                    else throw NotFoundResponse("Fant ikke person i PDL")
                }
                return respons.data.hentPerson?.navn?.first()?.let {
                    it.fornavn + (it.mellomnavn?.let { " $it" } ?: "") to it.etternavn
                }
            }

            is Result.Failure -> throw RuntimeException("Noe feil skjedde ved henting av navn fra PDL: ", result.getException())
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
