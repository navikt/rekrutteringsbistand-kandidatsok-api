import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.github.kittinunf.fuel.Fuel
import com.github.kittinunf.fuel.jackson.responseObject
import com.github.tomakehurst.wiremock.client.WireMock.*
import com.github.tomakehurst.wiremock.junit5.WireMockRuntimeInfo
import com.github.tomakehurst.wiremock.junit5.WireMockTest
import no.nav.toi.App
import no.nav.toi.RolleUuidSpesifikasjon
import no.nav.security.mock.oauth2.MockOAuth2Server
import org.assertj.core.api.Assertions
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import java.util.*

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@WireMockTest(httpPort = 10000)
class KandidatsammendragLookupTest {
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
    fun `Kan hente kandidatsammendrag`(wmRuntimeInfo: WireMockRuntimeInfo) {
        val wireMock = wmRuntimeInfo.wireMock
        wireMock.register(
            post("/veilederkandidat_current/_search?typed_keys=true")
                .withRequestBody(equalToJson("""
                    {
                      "_source": {
                        "includes": [
                          "fornavn",
                          "etternavn",
                          "arenaKandidatnr",
                          "fodselsdato",
                          "fodselsnummer",
                          "adresselinje1",
                          "postnummer",
                          "poststed",
                          "epostadresse",
                          "telefon",
                          "veilederIdent",
                          "veilederVisningsnavn",
                          "veilederEpost"
                        ]
                      },
                      "query": {
                        "term": {
                          "kandidatnr": {
                            "value": "PAM0xtfrwli5"
                          }
                        }
                      },
                      "size": 1
                    }
                """.trimIndent()))
                .willReturn(
                    ok(CvTestRespons.responseOpenSearch(CvTestRespons.sourceKandidatsammendragLookup))
                )
        )
        val navIdent = "A123456"
        val token = lagToken(navIdent = navIdent)
        val (_, response, result) = Fuel.post("http://localhost:8080/api/kandidatsammendrag")
                .body("""{"kandidatnr": "PAM0xtfrwli5"}""")
            .header("Authorization", "Bearer ${token.serialize()}")
            .responseObject<JsonNode>()

        Assertions.assertThat(response.statusCode).isEqualTo(200)
        Assertions.assertThat(result.get()).isEqualTo(ObjectMapper().readTree(CvTestRespons.responseKandidatsammendragLookup))
    }

    @Test
    fun `Finner ikke kandidatsammendrag`(wmRuntimeInfo: WireMockRuntimeInfo) {
        val wireMock = wmRuntimeInfo.wireMock
        wireMock.register(
            post("/veilederkandidat_current/_search?typed_keys=true")
                .withRequestBody(equalToJson("""
                    {
                      "_source": {
                        "includes": [
                          "fornavn",
                          "etternavn",
                          "arenaKandidatnr",
                          "fodselsdato",
                          "fodselsnummer",
                          "adresselinje1",
                          "postnummer",
                          "poststed",
                          "epostadresse",
                          "telefon",
                          "veilederIdent",
                          "veilederVisningsnavn",
                          "veilederEpost"
                        ]
                      },
                      "query": {
                        "term": {
                          "kandidatnr": {
                            "value": "PAM000000001"
                          }
                        }
                      },
                      "size": 1
                    }
                """.trimIndent()))
                .willReturn(
                    ok(CvTestRespons.responseOpensearchIngenTreff)
                )
        )
        val navIdent = "A123456"
        val token = lagToken(navIdent = navIdent)
        val (_, response, result) = Fuel.post("http://localhost:8080/api/kandidatsammendrag")
            .body("""{"kandidatnr": "PAM000000001"}""")
            .header("Authorization", "Bearer ${token.serialize()}")
            .responseObject<JsonNode>()

        Assertions.assertThat(response.statusCode).isEqualTo(200)
        Assertions.assertThat(result.get()).isEqualTo(ObjectMapper().readTree(CvTestRespons.responseIngenTreff))
    }

    @Test
    fun `Om kall feiler under henting av kandidatsammendrag fra elasticsearch, får vi HTTP 500`(wmRuntimeInfo: WireMockRuntimeInfo) {
        val wireMock = wmRuntimeInfo.wireMock
        wireMock.register(
            post("/veilederkandidat_current/_search?typed_keys=true")
                .withRequestBody(equalToJson("""{"query":{"term":{"kandidatnr":{"value":"PAM0xtfrwli5" }}},"size":1}"""))
                .willReturn(
                    notFound()
                )
        )
        val navIdent = "A123456"
        val token = lagToken(navIdent = navIdent)
        val (_, response, result) = Fuel.post("http://localhost:8080/api/kandidatsammendrag")
            .body("""{"kandidatnr": "PAM0xtfrwli5"}""")
            .header("Authorization", "Bearer ${token.serialize()}")
            .responseObject<JsonNode>()

        Assertions.assertThat(response.statusCode).isEqualTo(500)
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

    private fun lagToken(
        issuerId: String = "http://localhost:$authPort/default",
        aud: String = "1",
        navIdent: String = "A000001",
        claims: Map<String, Any> = mapOf("NAVident" to navIdent, "groups" to listOf(modiaGenerell))
    ) = authServer.issueToken(
        issuerId = issuerId,
        subject = "subject",
        audience = aud,
        claims = claims
    )
}