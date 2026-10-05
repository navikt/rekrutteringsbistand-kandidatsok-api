package no.nav.toi.kandidatstillingsøk

import java.net.URI
import java.net.http.HttpRequest
import java.net.http.HttpRequest.BodyPublishers
import java.net.http.HttpResponse.BodyHandlers
import no.nav.toi.testHttpClient
import no.nav.toi.testObjectMapper
import tools.jackson.databind.ObjectMapper
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
            post("/kandidater/_search?typed_keys=true")
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
                      }
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
        val response = testHttpClient.send(
            HttpRequest.newBuilder(URI("http://localhost:8080/api/kandidat-stillingssok"))
                .header("Authorization", "Bearer ${token.serialize()}")
                .POST(BodyPublishers.ofString("""{"kandidatnr": "PAM0xtfrwli5"}"""))
                .build(),
            BodyHandlers.ofString()
        )

        Assertions.assertThat(response.statusCode()).isEqualTo(200)
        Assertions.assertThat(testObjectMapper.readTree(response.body()))
            .isEqualTo(ObjectMapper().readTree(CvTestRespons.responseKandidatStillingssøkLookup))
    }

    @Test
    fun `Finner ikke kandidatStillingssøk`(wmRuntimeInfo: WireMockRuntimeInfo) {
        val wireMock = wmRuntimeInfo.wireMock
        wireMock.register(
            post("/kandidater/_search?typed_keys=true")
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
                      }
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
        val response = testHttpClient.send(
            HttpRequest.newBuilder(URI("http://localhost:8080/api/kandidat-stillingssok"))
                .header("Authorization", "Bearer ${token.serialize()}")
                .POST(BodyPublishers.ofString("""{"kandidatnr": "PAM000000001"}"""))
                .build(),
            BodyHandlers.ofString()
        )

        Assertions.assertThat(response.statusCode()).isEqualTo(200)
        Assertions.assertThat(testObjectMapper.readTree(response.body())).isEqualTo(ObjectMapper().readTree(CvTestRespons.responseIngenTreff))
    }

    @Test
    fun `Om kall feiler under henting av kandidatStillingssøk fra elasticsearch, får vi HTTP 500`(wmRuntimeInfo: WireMockRuntimeInfo) {
        val wireMock = wmRuntimeInfo.wireMock
        wireMock.register(
            post("/kandidater/_search?typed_keys=true")
                .withRequestBody(equalToJson("""{"query":{"term":{"kandidatnr":{"value":"PAM0xtfrwli5" }}}}"""))
                .willReturn(
                    notFound()
                )
        )
        val navIdent = "A123456"
        val token = app.lagToken(navIdent = navIdent, groups = listOf(LokalApp.arbeidsgiverrettet))
        val response = testHttpClient.send(
            HttpRequest.newBuilder(URI("http://localhost:8080/api/kandidat-stillingssok"))
                .header("Authorization", "Bearer ${token.serialize()}")
                .POST(BodyPublishers.ofString("""{"kandidatnr": "PAM0xtfrwli5"}"""))
                .build(),
            BodyHandlers.ofString()
        )

        Assertions.assertThat(response.statusCode()).isEqualTo(500)
    }

    @Test
    fun `modia generell skal ikke ha tilgang til kandidatstillingssok`(wmRuntimeInfo: WireMockRuntimeInfo) {
        val token = app.lagToken(groups = listOf(LokalApp.modiaGenerell))
        val wireMock = wmRuntimeInfo.wireMock
        mockKandidatStillingssøk(wireMock)
        val response = gjørKall(token)

        Assertions.assertThat(response.statusCode()).isEqualTo(403)
    }

    @Test
    fun `jobbsøkerrettet skal ha tilgang til kandidatstillingssok om egen bruker`(wmRuntimeInfo: WireMockRuntimeInfo) {
        val wireMock = wmRuntimeInfo.wireMock
        mockKandidatStillingssøk(wireMock)
        val token = app.lagToken(navIdent = "A100000", groups = listOf(LokalApp.jobbsøkerrettet))
        val response = gjørKall(token)

        Assertions.assertThat(response.statusCode()).isEqualTo(200)
    }

    @Test
    fun `jobbsøkerrettet skal ikke ha tilgang til kandidatstillingssok om ikke egen bruker`(wmRuntimeInfo: WireMockRuntimeInfo) {
        val wireMock = wmRuntimeInfo.wireMock
        mockKandidatStillingssøk(wireMock)
        val token = app.lagToken(navIdent = "ikke_veileder", groups = listOf(LokalApp.jobbsøkerrettet))
        val response = gjørKall(token)

        Assertions.assertThat(response.statusCode()).isEqualTo(403)
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
        val response = gjørKall(token)

        Assertions.assertThat(response.statusCode()).isEqualTo(200)
    }

    @Test
    fun `arbeidsgiverrettet skal ha tilgang til kandidatstillingssok`(wmRuntimeInfo: WireMockRuntimeInfo) {
        val wireMock = wmRuntimeInfo.wireMock
        mockKandidatStillingssøk(wireMock)
        val token = app.lagToken(groups = listOf(LokalApp.arbeidsgiverrettet))
        val response = gjørKall(token)

        Assertions.assertThat(response.statusCode()).isEqualTo(200)
    }

    @Test
    fun `utvikler skal ha tilgang til kandidatstillingssok`(wmRuntimeInfo: WireMockRuntimeInfo) {
        val wireMock = wmRuntimeInfo.wireMock
        mockKandidatStillingssøk(wireMock)
        val token = app.lagToken(groups = listOf(LokalApp.utvikler))
        val response = gjørKall(token)

        Assertions.assertThat(response.statusCode()).isEqualTo(200)
    }

    @Test
    fun `om man ikke har gruppetilhørighet skal man ikke få kandidatstillingssok`(wmRuntimeInfo: WireMockRuntimeInfo) {
        val wireMock = wmRuntimeInfo.wireMock
        mockKandidatStillingssøk(wireMock)
        val token = app.lagToken(groups = emptyList())
        val response = gjørKall(token)

        Assertions.assertThat(response.statusCode()).isEqualTo(403)
    }

    private fun gjørKall(token: SignedJWT) = testHttpClient.send(
        HttpRequest.newBuilder(URI("http://localhost:8080/api/kandidat-stillingssok"))
            .header("Authorization", "Bearer ${token.serialize()}")
            .POST(BodyPublishers.ofString("""{"kandidatnr": "PAM0xtfrwli5"}"""))
            .build(),
        BodyHandlers.ofString()
    )

    private fun mockKandidatStillingssøk(wireMock: WireMock, veileder: String? = "A100000") =
        wireMock.register(
            post("/kandidater/_search?typed_keys=true")
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
                      }
                    }
                """.trimIndent()
                    )
                )
                .willReturn(
                    ok(CvTestRespons.responseOpenSearch(CvTestRespons.sourceKandidatStillingssøkLookup(veileder = veileder)))
                )
        )
}
