import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.github.kittinunf.fuel.Fuel
import com.github.kittinunf.fuel.jackson.responseObject
import com.github.tomakehurst.wiremock.client.WireMock.*
import com.github.tomakehurst.wiremock.junit5.WireMockRuntimeInfo
import com.github.tomakehurst.wiremock.junit5.WireMockTest
import no.nav.toi.LokalApp
import org.assertj.core.api.Assertions
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@WireMockTest(httpPort = 10000)
class KandidatStillingssokLookupTest {
    private val app = LokalApp()

    @BeforeAll
    fun setUp() {
        app.start()
    }

    @AfterAll
    fun tearDown() {
        app.close()
    }

    @Test
    fun `Kan hente kandidatStillingssøk`(wmRuntimeInfo: WireMockRuntimeInfo) {
        val wireMock = wmRuntimeInfo.wireMock
        wireMock.register(
            post("/veilederkandidat_current/_search?typed_keys=true")
                .withRequestBody(equalToJson("""
                    {
                      "_source": {
                        "includes": [
                          "geografiJobbonsker",
                          "yrkeJobbonskerObj",
                          "kommunenummerstring",
                          "kommuneNavn",
                          "fodselsnummer"
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
                    ok(CvTestRespons.responseOpenSearch(CvTestRespons.sourceKandidatStillingssøkLookup))
                )
        )
        val navIdent = "A123456"
        val token = app.lagToken(navIdent = navIdent)
        val (_, response, result) = Fuel.post("http://localhost:8080/api/kandidat-stillingssok")
                .body("""{"kandidatnr": "PAM0xtfrwli5"}""")
            .header("Authorization", "Bearer ${token.serialize()}")
            .responseObject<JsonNode>()

        Assertions.assertThat(response.statusCode).isEqualTo(200)
        Assertions.assertThat(result.get()).isEqualTo(ObjectMapper().readTree(CvTestRespons.responseKandidatStillingssøkLookup))
    }

    @Test
    fun `Finner ikke kandidatStillingssøk`(wmRuntimeInfo: WireMockRuntimeInfo) {
        val wireMock = wmRuntimeInfo.wireMock
        wireMock.register(
            post("/veilederkandidat_current/_search?typed_keys=true")
                .withRequestBody(equalToJson("""
                    {
                      "_source": {
                        "includes": [
                          "geografiJobbonsker",
                          "yrkeJobbonskerObj",
                          "kommunenummerstring",
                          "kommuneNavn",
                          "fodselsnummer"
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
        val token = app.lagToken(navIdent = navIdent)
        val (_, response, result) = Fuel.post("http://localhost:8080/api/kandidat-stillingssok")
            .body("""{"kandidatnr": "PAM000000001"}""")
            .header("Authorization", "Bearer ${token.serialize()}")
            .responseObject<JsonNode>()

        Assertions.assertThat(response.statusCode).isEqualTo(200)
        Assertions.assertThat(result.get()).isEqualTo(ObjectMapper().readTree(CvTestRespons.responseIngenTreff))
    }

    @Test
    fun `Om kall feiler under henting av kandidatStillingssøk fra elasticsearch, får vi HTTP 500`(wmRuntimeInfo: WireMockRuntimeInfo) {
        val wireMock = wmRuntimeInfo.wireMock
        wireMock.register(
            post("/veilederkandidat_current/_search?typed_keys=true")
                .withRequestBody(equalToJson("""{"query":{"term":{"kandidatnr":{"value":"PAM0xtfrwli5" }}},"size":1}"""))
                .willReturn(
                    notFound()
                )
        )
        val navIdent = "A123456"
        val token = app.lagToken(navIdent = navIdent)
        val (_, response, result) = Fuel.post("http://localhost:8080/api/kandidat-stillingssok")
            .body("""{"kandidatnr": "PAM0xtfrwli5"}""")
            .header("Authorization", "Bearer ${token.serialize()}")
            .responseObject<JsonNode>()

        Assertions.assertThat(response.statusCode).isEqualTo(500)
    }

    @Test
    fun feil_dersom_ikke_autentisert() {
        val (_, response, _) = Fuel.post("http://localhost:8080/api/kandidat-stillingssok")
            .body("""{"yrker": [{"yrke": "yrke"}]}""")
            .responseObject<JsonNode>()

        Assertions.assertThat(response.statusCode).isEqualTo(401)
    }
}