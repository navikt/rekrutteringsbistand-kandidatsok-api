package no.nav.toi.kandidatsammendrag

import com.github.kittinunf.fuel.Fuel
import com.github.kittinunf.fuel.core.Headers
import com.github.kittinunf.fuel.core.extensions.authentication
import com.github.kittinunf.fuel.core.extensions.jsonBody
import com.github.kittinunf.fuel.jackson.responseObject
import com.github.kittinunf.result.Result
import no.nav.toi.AccessTokenClient

class PdlKlient(private val pdlUrl: String, private val accessTokenClient: AccessTokenClient) {

    fun hentFornavnOgEtternavn(fødselsnummer: String, innkommendeToken: String): NavnOgGradering? {

        val accessToken = accessTokenClient.hentAccessToken(innkommendeToken)
        val graphql = lagGraphQLSpørring(fødselsnummer)

        val (_, response, result) = Fuel.post(pdlUrl)
            .header(Headers.CONTENT_TYPE, "application/json")
            .header("Tema", "GEN")
            .header("Behandlingsnummer", "B346")
            .authentication().bearer(accessToken)
            .jsonBody(graphql)
            .responseObject<Respons>()

        if(response.statusCode == 404) return null

        when (result) {
            is Result.Success -> {
                val person = result.get().data.hentPerson ?: return null
                val fornavnOgMellomnavn = person.navn.first().fornavn + (person.navn.first().mellomnavn?.let { " $it" } ?: "")
                val harAdressebeskyttelse = person.adressebeskyttelse.any { it.gradering != Gradering.UGRADERT }

                return NavnOgGradering(fornavnOgMellomnavn, person.navn.first().etternavn, harAdressebeskyttelse)
            }

            is Result.Failure -> throw RuntimeException("Noe feil skjedde ved henting av navn fra PDL: ", result.getException())
        }
    }

    private fun lagGraphQLSpørring(fødselsnummer: String): String {
        val pesostegn = "$"

        return """
            {
                "query": "query(${'$'}ident: ID!){ hentPerson(ident: ${'$'}ident) {navn(historikk: false) {fornavn mellomnavn etternavn} adressebeskyttelse {gradering}}}",
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

data class NavnOgGradering(
    val fornavn: String,
    val etternavn: String,
    val harAdressebeskyttelse: Boolean
)

private data class HentPerson(
    val navn: List<Navn>,
    val adressebeskyttelse: List<Adressebeskyttelse>
)

private data class Navn(
    val fornavn: String,
    val mellomnavn: String?,
    val etternavn: String
)

private data class Adressebeskyttelse(
    val gradering: Gradering
)

enum class Gradering { STRENGT_FORTROLIG_UTLAND, STRENGT_FORTROLIG, FORTROLIG, UGRADERT }

private data class Error(
    val message: String
)
