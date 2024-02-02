package no.nav.toi.kandidatnavn

import CvTestRespons
import com.github.kittinunf.fuel.Fuel
import com.github.kittinunf.fuel.core.FuelError
import com.github.kittinunf.fuel.core.Request
import com.github.kittinunf.fuel.core.Response
import com.github.kittinunf.fuel.core.extensions.jsonBody
import com.github.kittinunf.result.Result
import com.github.tomakehurst.wiremock.client.WireMock
import com.github.tomakehurst.wiremock.junit5.WireMockRuntimeInfo
import com.github.tomakehurst.wiremock.junit5.WireMockTest
import com.nimbusds.jwt.SignedJWT
//import no.nav.App
//import no.nav.RolleUuidSpesifikasjon
import no.nav.security.mock.oauth2.MockOAuth2Server
import no.nav.toi.App
import no.nav.toi.RolleUuidSpesifikasjon
import org.assertj.core.api.Assertions
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import java.util.*

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@WireMockTest(httpPort = 10000)
class HentKandidatnavnRouteTest {
    private val modiaGenerell = "67a06857-0028-4a90-bf4c-9c9a92c7d733"
    private val modiaOppfølging = "554a66fb-fbec-4b92-90c1-0d9c085c362c"
    private val authPort = 18306
    private val app: App = lagLokalApp()
    private val authServer = MockOAuth2Server()

    @BeforeAll
    fun setUp() {
        app.start()
        authServer.start(port = authPort)
    }

    @AfterAll
    fun tearDown() {
        app.close()
        authServer.shutdown()
    }

    @Test
    fun `Kan hente kandidatnavn`() {
        val etFnr = "55555555555"
        val requestBody = """{"fnr": "$etFnr"}"""
        val (_, response, result) = sendRequest(requestBody)

        Assertions.assertThat(response.statusCode).isEqualTo(200)
        Assertions.assertThat(result.get()).isEqualTo(
            """
            {"fornavn":"anyFornavn","mellomnavn":"anyMellomnavn","etternavn":"anyEtternavn","synligIRekbis":true,"kandidatnr":"anyKandidatnr-$etFnr"}
            """.trimIndent()
        )
    }

    /*
    Gitt at kandidat finnes i Opensearch
    når sender request med kandidatens fnr
    så skal respons være 200 med kandidatens navn osv.
     */
    @Test
    fun `Gitt at kandidaten finnes i Opensearch skal respons være 200 og kandidatens navn`(wmRuntimeInfo: WireMockRuntimeInfo) {
        val wireMock = wmRuntimeInfo.wireMock
        wireMock.register(
            WireMock.post("/veilederkandidat_current/_search?typed_keys=true")
                .withRequestBody(WireMock.equalToJson("""{"query":{"term":{"kandidatnr":{"value":"PAM0xtfrwli5" }}},"size":1}"""))
                .willReturn(
                    WireMock.ok(CvTestRespons.responseOpenSearch("{}")) // TODO
                )
        )


    }


    /*
    Gitt at kandidat ikke finnes i Opensearch men finnes i PDL
    når sender request med kandidatens fnr
    så skal respons være 200 med kandidatens navn osv.
     */

    /*
    Gitt at kandidat ikke finnes i Opensearch og kall mot PDL feiler
    når sender request med kandidatens fnr
    så skal respons være 5xx server error
     */

    /*
    Gitt at kall mot Opensearch feiler
    når sender request med kandidatens fnr
    så skal respons være 5xx server error og vi gjør ikke noe kall mot ikke PDL
     */

    // TODO: Vil vi ha retries? Jeg tenker ja, hvis det koster lite, som jeg tror er tilfellet med resilience4j.

    @Test
    fun `Skal få 400 Bad Request gitt feil request body`() {
        val ikkeJson = ""
        val (_, responseIkkeJson, _) = sendRequest(ikkeJson)
        Assertions.assertThat(responseIkkeJson.statusCode).isEqualTo(400)

        val tomJson = "{}"
        val (_, responseTomJson, _) = sendRequest(tomJson)
        Assertions.assertThat(responseTomJson.statusCode).isEqualTo(400)

        val feilKey = """{"fnrrrrrrrrrr": "55555555555"}"""
        val (_, responseFeilKey, _) = sendRequest(feilKey)
        Assertions.assertThat(responseFeilKey.statusCode).isEqualTo(400)

        val feilValue = """{"fnr": false}"""
        val (_, responseFeilValue, _) = sendRequest(feilValue)
        Assertions.assertThat(responseFeilValue.statusCode).isEqualTo(400)
    }


    private fun lagLokalApp() = App(
        port = 8080,
        azureAppClientId = "1",
        azureOpenidConfigIssuer = "http://localhost:$authPort/default",
        azureOpenidConfigJwksUri = "http://localhost:$authPort/default/jwks",
        rolleUuidSpesifikasjon = RolleUuidSpesifikasjon(
            modiaGenerell = UUID.fromString(modiaGenerell),
            modiaOppfølging = UUID.fromString(modiaOppfølging),
        ),
        openSearchUsername = "user",
        openSearchPassword = "pass",
        openSearchUri = "http://localhost:10000/opensearch",
    )

    private fun token(
        issuerId: String = "http://localhost:$authPort/default",
        aud: String = "1",
        navIdent: String = "A000001",
        claims: Map<String, Any> = mapOf("NAVident" to navIdent, "groups" to listOf(modiaGenerell))
    ): SignedJWT = authServer.issueToken(
        issuerId = issuerId,
        subject = "subject",
        audience = aud,
        claims = claims
    )

    private fun sendRequest(body: String): Triple<Request, Response, Result<String, FuelError>> =
        Fuel.post("http://localhost:8080/api/hent-kandidatnavn")
            .jsonBody(body)
            .header("Authorization", "Bearer ${token().serialize()}")
            .responseString()
}
