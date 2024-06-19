import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.github.kittinunf.fuel.Fuel
import com.github.kittinunf.fuel.jackson.responseObject
import com.github.tomakehurst.wiremock.client.WireMock
import com.github.tomakehurst.wiremock.client.WireMock.*
import com.github.tomakehurst.wiremock.junit5.WireMockRuntimeInfo
import com.github.tomakehurst.wiremock.junit5.WireMockTest
import com.nimbusds.jwt.SignedJWT
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
                .withRequestBody(
                    equalToJson(
                        """
                    {
                      "_source": {
                        "includes": [
                          "geografiJobbonsker",
                          "yrkeJobbonskerObj",
                          "kommunenummerstring",
                          "kommuneNavn",
                          "fodselsnummer",
                           "veilederIdent", 
                           "orgenhet"
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
                """.trimIndent()
                    )
                )
                .willReturn(
                    ok(CvTestRespons.responseOpenSearch(CvTestRespons.sourceKandidatStillingssøkLookup()))
                )
        )
        val navIdent = "A123456"
        val token = app.lagToken(navIdent = navIdent, groups = listOf(LokalApp.arbeidsgiverrettet))
        val (_, response, result) = Fuel.post("http://localhost:8080/api/kandidat-stillingssok")
            .body("""{"kandidatnr": "PAM0xtfrwli5"}""")
            .header("Authorization", "Bearer ${token.serialize()}")
            .responseObject<JsonNode>()

        Assertions.assertThat(response.statusCode).isEqualTo(200)
        Assertions.assertThat(result.get())
            .isEqualTo(ObjectMapper().readTree(CvTestRespons.responseKandidatStillingssøkLookup))
    }

    @Test
    fun `Finner ikke kandidatStillingssøk`(wmRuntimeInfo: WireMockRuntimeInfo) {
        val wireMock = wmRuntimeInfo.wireMock
        wireMock.register(
            post("/veilederkandidat_current/_search?typed_keys=true")
                .withRequestBody(
                    equalToJson(
                        """
                    {
                      "_source": {
                        "includes": [
                          "geografiJobbonsker",
                          "yrkeJobbonskerObj",
                          "kommunenummerstring",
                          "kommuneNavn",
                          "fodselsnummer",
                           "veilederIdent", 
                           "orgenhet"
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
                """.trimIndent()
                    )
                )
                .willReturn(
                    ok(CvTestRespons.responseOpensearchIngenTreff)
                )
        )
        val navIdent = "A123456"
        val token = app.lagToken(navIdent = navIdent, groups = listOf(LokalApp.arbeidsgiverrettet))
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
        val token = app.lagToken(navIdent = navIdent, groups = listOf(LokalApp.arbeidsgiverrettet))
        val (_, response, result) = Fuel.post("http://localhost:8080/api/kandidat-stillingssok")
            .body("""{"kandidatnr": "PAM0xtfrwli5"}""")
            .header("Authorization", "Bearer ${token.serialize()}")
            .responseObject<JsonNode>()

        Assertions.assertThat(response.statusCode).isEqualTo(500)
    }

    @Test
    fun `modia generell skal ikke ha tilgang til kandidatstillingssok`(wmRuntimeInfo: WireMockRuntimeInfo) {
        val token = app.lagToken(groups = listOf(LokalApp.modiaGenerell))
        val wireMock = wmRuntimeInfo.wireMock
        mockKandidatStillingssøk(wireMock)
        val (_, response, _) = gjørKall(token)

        Assertions.assertThat(response.statusCode).isEqualTo(403)
    }

    @Test
    fun `jobbsøkerrettet skal ha tilgang til kandidatstillingssok om egen bruker`(wmRuntimeInfo: WireMockRuntimeInfo) {
        val wireMock = wmRuntimeInfo.wireMock
        mockKandidatStillingssøk(wireMock)
        val token = app.lagToken(navIdent = "A100000", groups = listOf(LokalApp.jobbsøkerrettet))
        val (_, response) = gjørKall(token)

        Assertions.assertThat(response.statusCode).isEqualTo(200)
    }

    @Test
    fun `jobbsøkerrettet skal ikke ha tilgang til kandidatstillingssok om ikke egen bruker`(wmRuntimeInfo: WireMockRuntimeInfo) {
        val wireMock = wmRuntimeInfo.wireMock
        mockKandidatStillingssøk(wireMock)
        val token = app.lagToken(navIdent = "ikke_veileder", groups = listOf(LokalApp.jobbsøkerrettet))
        val (_, response) = gjørKall(token)

        Assertions.assertThat(response.statusCode).isEqualTo(403)
    }

    @Test
    fun `jobbsøkerrettet skal ha tilgang til kandidatstillingssok om eget kontor`(wmRuntimeInfo: WireMockRuntimeInfo) {
        val veiledersIdent = "A100000"
        val veiledersOrgenhet = "1234"

        val wireMock = wmRuntimeInfo.wireMock
        mockKandidatStillingssøk(wireMock, "A100001")
        wireMock.register(
            get("/modia/api/decorator")
                .willReturn(
                    okJson(
                        """
                {
                    "ident": "$veiledersIdent",
                    "navn": "Tull Tullersen",
                    "fornavn": "Tull",
                    "etternavn": "Tullersen",
                     "enheter": [
                                {
                                    "enhetId": "$veiledersOrgenhet",
                                    "navn": "NAV Hamar"
                                }
                            ]
                }
            """.trimIndent()
                    )
                )
        )
        val token = app.lagToken(navIdent = "A100001", groups = listOf(LokalApp.jobbsøkerrettet))
        val (_, response) = gjørKall(token)

        Assertions.assertThat(response.statusCode).isEqualTo(200)
    }

    @Test
    fun `arbeidsgiverrettet skal ha tilgang til kandidatstillingssok`(wmRuntimeInfo: WireMockRuntimeInfo) {
        val wireMock = wmRuntimeInfo.wireMock
        mockKandidatStillingssøk(wireMock)
        val token = app.lagToken(groups = listOf(LokalApp.arbeidsgiverrettet))
        val (_, response) = gjørKall(token)

        Assertions.assertThat(response.statusCode).isEqualTo(200)
    }

    @Test
    fun `utvikler skal ha tilgang til kandidatstillingssok`(wmRuntimeInfo: WireMockRuntimeInfo) {
        val wireMock = wmRuntimeInfo.wireMock
        mockKandidatStillingssøk(wireMock)
        val token = app.lagToken(groups = listOf(LokalApp.utvikler))
        val (_, response) = gjørKall(token)

        Assertions.assertThat(response.statusCode).isEqualTo(200)
    }

    @Test
    fun `om man ikke har gruppetilhørighet skal man ikke få kandidatstillingssok`(wmRuntimeInfo: WireMockRuntimeInfo) {
        val wireMock = wmRuntimeInfo.wireMock
        mockKandidatStillingssøk(wireMock)
        val token = app.lagToken(groups = emptyList())
        val (_, response) = gjørKall(token)

        Assertions.assertThat(response.statusCode).isEqualTo(403)
    }

    private fun gjørKall(token: SignedJWT) = Fuel.post("http://localhost:8080/api/kandidat-stillingssok")
        .body("""{"kandidatnr": "PAM0xtfrwli5"}""")
        .header("Authorization", "Bearer ${token.serialize()}")
        .responseObject<JsonNode>()

    private fun mockKandidatStillingssøk(wireMock: WireMock, veileder: String? = "A100000") =
        wireMock.register(
            post("/veilederkandidat_current/_search?typed_keys=true")
                .withRequestBody(
                    equalToJson(
                        """
                    {
                      "_source": {
                        "includes": [
                          "geografiJobbonsker",
                          "yrkeJobbonskerObj",
                          "kommunenummerstring",
                          "kommuneNavn",
                          "fodselsnummer",
                          "veilederIdent",
                          "orgenhet"
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
                """.trimIndent()
                    )
                )
                .willReturn(
                    ok(CvTestRespons.responseOpenSearch(CvTestRespons.sourceKandidatStillingssøkLookup(veileder = veileder)))
                )
        )
}
