package no.nav.toi.kandidatsammendrag

import io.javalin.http.InternalServerErrorResponse
import io.javalin.http.UnauthorizedResponse
import no.nav.toi.AccessTokenClient
import no.nav.toi.erVellykket
import no.nav.toi.httpClient
import no.nav.toi.httpKlientMapper
import no.nav.toi.httpRequest
import tools.jackson.module.kotlin.readValue
import java.net.http.HttpRequest.BodyPublishers
import java.net.http.HttpResponse.BodyHandlers

class LivshendelseKlient(private val url: String, private val accessTokenClient: AccessTokenClient) {
    fun harAdressebeskyttelse(fodselsnummer: String, innkommendeToken: String): Boolean {
        val accessToken = accessTokenClient.hentAccessToken(innkommendeToken)

        val request = httpRequest("$url/adressebeskyttelse")
            .header("Authorization", "Bearer $accessToken")
            .header("Content-Type", "application/json")
            .POST(BodyPublishers.ofString("""{"fnr": "$fodselsnummer"}"""))
            .build()

        val response = httpClient.send(request, BodyHandlers.ofString())

        if (response.statusCode() == 401) throw UnauthorizedResponse("Du har ikke tilgang")
        if (response.statusCode() == 500) throw InternalServerErrorResponse("Noe gikk galt")
        if (!response.erVellykket()) throw RuntimeException("Noe feil skjedde ved henting av adressebeskyttelse, status ${response.statusCode()}")

        return httpKlientMapper.readValue<ResponseAdressebeskyttelse>(response.body()).harAdressebeskyttelse
    }
}


private class ResponseAdressebeskyttelse(
    val harAdressebeskyttelse: Boolean
)