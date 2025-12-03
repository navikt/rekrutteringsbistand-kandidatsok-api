package no.nav.toi.lookupperson

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.github.tomakehurst.wiremock.client.WireMock.aResponse
import com.github.tomakehurst.wiremock.client.WireMock.equalToJson
import com.github.tomakehurst.wiremock.client.WireMock.post
import com.github.tomakehurst.wiremock.client.WireMock.stubFor
import com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo
import com.github.tomakehurst.wiremock.junit5.WireMockTest
import no.nav.security.mock.oauth2.MockOAuth2Server
import no.nav.toi.LokalApp
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import java.net.HttpURLConnection.HTTP_FORBIDDEN
import java.net.HttpURLConnection.HTTP_OK
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import kotlin.test.assertEquals

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@WireMockTest(httpPort = 10000)
class LookupPersonControllerTest {
    private val endepunkt = "http://localhost:8080/api/lookup-person"
    private val authServer = MockOAuth2Server()
    private val app = LokalApp()

    private val objectMapper = jacksonObjectMapper().apply {
        registerModule(JavaTimeModule())
        disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
        enable(DeserializationFeature.ACCEPT_SINGLE_VALUE_AS_ARRAY)
        disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
    }

    private val httpClient: HttpClient = HttpClient.newBuilder()
        .followRedirects(HttpClient.Redirect.ALWAYS)
        .build()

    @BeforeAll
    fun setUp() {
        authServer.start()
        app.start()
    }

    @AfterAll
    fun tearDown() {
        app.close()
        authServer.shutdown()
    }

    @Test
    fun `Skal få 404 hvis personen ikke finnes i opensearch`() {
        stubFor(
            post(urlEqualTo("/veilederkandidat_current/_search?typed_keys=true"))
                .withRequestBody(equalToJson("""
                    {"query":{"term":{"kandidatnr":{"value":"1234" }
                    }}
                    ,"_source":{"includes":["fornavn","etternavn","aktorId","fodselsdato"]}}
                    """.trimIndent()))
                .willReturn(
                aResponse()
                    .withStatus(200)
                    .withBody(
                        """
                    {
                        "took": 2,
                        "timed_out": false,
                        "_shards": {
                            "total": 3, 
                            "successful": 3,
                            "skipped": 0,
                            "failed": 0
                        },
                        "hits": {
                            "total": {
                              "value": 0,
                              "relation": "eq"
                            },
                            "max_score": null,
                            "hits": []
                        }
                    }
                    """.trimIndent()
                    )
            )
        )

        val request = HttpRequest.newBuilder()
            .uri(URI(endepunkt))
            .header("Authorization", "Bearer ${app.lagToken(groups = Gruppe.Jobbsøkerrettet.somStringListe).serialize()}")
            .POST(HttpRequest.BodyPublishers.ofString("""{"kandidatnr":"1234"}"""))
            .build()

        val response = httpClient.send(request, HttpResponse.BodyHandlers.ofString())

        assertEquals(response.statusCode(), 404)
    }

    @Test
    fun `Skal få tilbake personen hvis personen finnes i opensearch`() {
        stubKandidatFinnes()

        val request = HttpRequest.newBuilder()
            .uri(URI(endepunkt))
            .header("Authorization", "Bearer ${app.lagToken(groups = Gruppe.Jobbsøkerrettet.somStringListe).serialize()}")
            .POST(HttpRequest.BodyPublishers.ofString("""{"kandidatnr":"1234"}"""))
            .build()

        val response = httpClient.send(request, HttpResponse.BodyHandlers.ofString())
        val personInfo = objectMapper.readValue(response.body(), PersonInfoDto::class.java)

        assertEquals("Ola", personInfo.fornavn)
        assertEquals("Nordmann", personInfo.etternavn)
        assertEquals("134556", personInfo.aktorId)
        assertEquals("1990-01-01", personInfo.fodselsdato)
    }

    fun stubKandidatFinnes() {
        stubFor(
            post(urlEqualTo("/veilederkandidat_current/_search?typed_keys=true"))
                .withRequestBody(equalToJson("""
                    {"query":{"term":{"kandidatnr":{"value":"1234" }
                    }}
                    ,"_source":{"includes":["fornavn","etternavn","aktorId","fodselsdato"]}}
                    """.trimIndent()))
                .willReturn(
                    aResponse()
                        .withStatus(200)
                        .withBody(
                            """
                    {
                        "took": 2,
                        "timed_out": false,
                        "_shards": {
                            "total": 3, 
                            "successful": 3,
                            "skipped": 0,
                            "failed": 0
                        },
                        "hits": {
                            "total": {
                              "value": 1,
                              "relation": "eq"
                            },
                            "max_score": null,
                            "hits": [
                                {
                                    "_index": "kandidater",
                                    "_id": "1",
                                    "_score": 1.0,
                                    "_source": {
                                        "fornavn": "Ola",
                                        "etternavn": "Nordmann",
                                        "aktorId": "134556",
                                        "fodselsdato": "1990-01-01"
                                    }
                                }
                            ]
                        }
                    }
                    """.trimIndent()
                        )
                )
        )
    }

    private fun autorisasjonsCaser() = listOf(
        Arguments.of(endepunkt, Gruppe.Utvikler, HTTP_OK),
        Arguments.of(endepunkt, Gruppe.Arbeidsgiverrettet, HTTP_OK),
        Arguments.of(endepunkt, Gruppe.Jobbsøkerrettet, HTTP_OK),
        Arguments.of(endepunkt, Gruppe.ModiaGenerell, HTTP_FORBIDDEN)
    )

    @ParameterizedTest
    @MethodSource("autorisasjonsCaser")
    fun `Skal håndtere autorisasjon riktig`(endepunkt: String, gruppe: Gruppe, forventetStatuskode: Int) {
        stubKandidatFinnes()

        val request = HttpRequest.newBuilder()
            .uri(URI(endepunkt))
            .header("Authorization", "Bearer ${app.lagToken(groups = gruppe.somStringListe).serialize()}")
            .POST(HttpRequest.BodyPublishers.ofString("""{"kandidatnr":"1234"}"""))
            .build()

        val response = httpClient.send(request, HttpResponse.BodyHandlers.ofString())

        assertEquals(forventetStatuskode, response.statusCode())
    }

    enum class Gruppe(val somStringListe: List<String>) {
        ModiaGenerell(listOf(LokalApp.modiaGenerell)),
        Arbeidsgiverrettet(listOf(LokalApp.arbeidsgiverrettet)),
        Utvikler(listOf(LokalApp.utvikler)),
        Jobbsøkerrettet(listOf(LokalApp.jobbsøkerrettet))
    }

}