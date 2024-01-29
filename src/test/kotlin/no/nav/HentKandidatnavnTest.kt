package no.nav

import com.github.kittinunf.fuel.Fuel
import com.github.kittinunf.fuel.core.FuelError
import com.github.kittinunf.fuel.core.Request
import com.github.kittinunf.fuel.core.Response
import com.github.kittinunf.fuel.core.extensions.jsonBody
import com.github.kittinunf.result.Result
import com.github.tomakehurst.wiremock.junit5.WireMockTest
import com.nimbusds.jwt.SignedJWT
import no.nav.security.mock.oauth2.MockOAuth2Server
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import java.util.*

private const val modiaGenerell = "67a06857-0028-4a90-bf4c-9c9a92c7d733"
private const val modiaOppfølging = "554a66fb-fbec-4b92-90c1-0d9c085c362c"

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@WireMockTest(httpPort = 10000)
class HentKandidatnavnTest {
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
        val (_, response, result) = Fuel.post("http://localhost:8080/api/hent-kandidatnavn")
            .jsonBody(requestBody)
            .header("Authorization", "Bearer ${token().serialize()}")
            .responseString()

        assertThat(response.statusCode).isEqualTo(200)
        assertThat(result.get()).isEqualTo(
            """
            {"fornavn":"anyFornavn","mellomnavn":"anyMellomnavn","etternavn":"anyEtternavn","synligIRekbis":true,"kandidatnr":"anyKandidatnr-$etFnr"}
            """.trimIndent()
        )
    }


    @Test
    fun `Skal få 400 Bad Request gitt feil request body`() {
        fun sendRequest(body: String): Triple<Request, Response, Result<String, FuelError>> =
            Fuel.post("http://localhost:8080/api/hent-kandidatnavn")
                .jsonBody(body)
                .header("Authorization", "Bearer ${token().serialize()}")
                .responseString()

        val ikkeJson = ""
        val (_, responseIkkeJson, _) = sendRequest(ikkeJson)
        assertThat(responseIkkeJson.statusCode).isEqualTo(400)

        val tomJson = "{}"
        val (_, responseTomJson, _) = sendRequest(tomJson)
        assertThat(responseTomJson.statusCode).isEqualTo(400)

        val feilKey = """{"fnrrrrrrrrrr": "55555555555"}"""
        val (_, responseFeilKey, _) = sendRequest(feilKey)
        assertThat(responseFeilKey.statusCode).isEqualTo(400)

        val feilValue = """{"fnr": false}"""
        val (_, responseFeilValue, _) = sendRequest(feilValue)
        assertThat(responseFeilValue.statusCode).isEqualTo(400)
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
}
