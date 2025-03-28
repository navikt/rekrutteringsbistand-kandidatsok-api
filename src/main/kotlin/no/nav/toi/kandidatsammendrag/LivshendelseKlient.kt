package no.nav.toi.kandidatsammendrag

import com.github.kittinunf.fuel.Fuel
import com.github.kittinunf.fuel.core.Headers
import com.github.kittinunf.fuel.core.extensions.authentication
import com.github.kittinunf.fuel.core.extensions.jsonBody
import com.github.kittinunf.fuel.jackson.responseObject
import io.javalin.http.InternalServerErrorResponse
import io.javalin.http.UnauthorizedResponse
import no.nav.toi.AccessTokenClient

class LivshendelseKlient(private val url: String, private val accessTokenClient: AccessTokenClient) {
    fun harAdressebeskyttelse(fodselsnummer: String, innkommendeToken: String): Boolean {
        val accessToken = accessTokenClient.hentAccessToken(innkommendeToken)

        val (_, response, result) = Fuel.post("$url/adressebeskyttelse")
            .header(Headers.CONTENT_TYPE, "application/json")
            .authentication().bearer(accessToken)
            .jsonBody("""{"fnr": "$fodselsnummer"}""")
            .responseObject<ResponseAdressebeskyttelse>()

        if (response.statusCode == 401) throw UnauthorizedResponse("Du har ikke tilgang")
        if (response.statusCode == 500) throw InternalServerErrorResponse("Noe gikk galt")

        return result.get().harAdressebeskyttelse
    }
}


private class ResponseAdressebeskyttelse(
    val harAdressebeskyttelse: Boolean
)