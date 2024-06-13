package no.nav.toi.kandidatsøk

import com.github.kittinunf.fuel.Fuel
import com.github.kittinunf.fuel.core.Headers
import com.github.kittinunf.fuel.core.extensions.authentication
import com.github.kittinunf.fuel.jackson.responseObject
import com.github.kittinunf.result.Result
import no.nav.toi.accesstoken.AccessTokenClient

class ModiaKlient(private val modiaUrl: String, private val accessTokenClient: AccessTokenClient) {
    fun hentModiaEnheter(innkommendeToken: String): List<Enhet> {
        val accessToken = accessTokenClient.hentAccessToken(innkommendeToken)
        val (_, response, result) = Fuel.get(modiaUrl + "/api/decorator")
            .header(Headers.CONTENT_TYPE, "application/json")
            .authentication().bearer(accessToken)
            .responseObject<ModiaPerson>()

        if(response.statusCode == 404) return emptyList()

        when (result) {
            is Result.Success -> return result.get().enheter

            is Result.Failure -> throw RuntimeException("Noe feil skjedde ved henting av brukere: ", result.getException())
        }
    }
}


data class ModiaPerson(
    val ident: String,
    val navn: String,
    val fornavn: String,
    val etternavn: String,
    val enheter: List<Enhet>
)

data class Enhet(
    val enhetId: String,
    val navn: String
)