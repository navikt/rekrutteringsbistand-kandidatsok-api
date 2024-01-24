package no.nav

import com.github.kittinunf.fuel.Fuel
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
//    fun `Kan hente kandidatnavn`(wmRuntimeInfo: WireMockRuntimeInfo) {
    fun `Kan hente kandidatnavn`() {
//        val wireMock = wmRuntimeInfo.wireMock
//        wireMock.register(
//            WireMock.post("/veilederkandidat_current/_search?typed_keys=true")
//                .withRequestBody(WireMock.equalToJson("""{"query":{"term":{"kandidatnr":{"value":"PAM0xtfrwli5" }}},"size":1}"""))
//                .willReturn(
//                    WireMock.ok(CvTestRespons.responseOpenSearch)
//                )
//        )

        val requestBody = """{"fnr": "anyFnr"}"""
        val (_, response, result) = Fuel.post("http://localhost:8080/api/hent-kandidatnavn")
            .body(requestBody)
            .header("Authorization", "Bearer ${token().serialize()}")
            .responseString()

        assertThat(response.statusCode).isEqualTo(200)
        assertThat(result.get()).isEqualTo(
            """
            {"fornavn":"anyFornavn","mellomnavn":"anyMellomnavn","etternavn":"anyEtternavn","synligIRekbis":true,"kandidatnr":"anyKandidatnr-anyFnr"}
            """.trimIndent()
        )
    }


    @Test
    fun `Skal få client error gitt feil request body`() {
        val requestBody = """{"fnr": false}"""
        val (_, response, result) = Fuel.post("http://localhost:8080/api/hent-kandidatnavn")
            .body(requestBody)
            .header("Authorization", "Bearer ${token().serialize()}")
            .responseString()

        assertThat(response.statusCode).isEqualTo(400)
//        assertThat(result.get()).isEqualTo(
//            """
//            {"fornavn":"anyFornavn","mellomnavn":"anyMellomnavn","etternavn":"anyEtternavn","synligIRekbis":true,"kandidatnr":"anyKandidatnr-anyFnr"}
//            """.trimIndent()
//        )
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
