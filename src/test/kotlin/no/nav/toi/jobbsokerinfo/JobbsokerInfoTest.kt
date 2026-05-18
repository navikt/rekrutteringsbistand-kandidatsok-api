package no.nav.toi.jobbsokerinfo

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.github.tomakehurst.wiremock.client.WireMock.aResponse
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
import java.time.LocalDate
import java.time.Period
import kotlin.test.assertEquals
import kotlin.test.assertNull

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@WireMockTest(httpPort = 10000)
class JobbsokerInfoTest {
    private val endepunkt = "http://localhost:8080/api/jobbsoker-info"
    private val authServer = MockOAuth2Server()
    private val app = LokalApp()

    private val objectMapper = jacksonObjectMapper().apply {
        disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
        enable(DeserializationFeature.ACCEPT_SINGLE_VALUE_AS_ARRAY)
        disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
    }

    private val httpClient: HttpClient = HttpClient.newBuilder()
        .followRedirects(HttpClient.Redirect.ALWAYS)
        .build()

    private data class JobbsokerInfoSvar(
        val fodselsnummer: String,
        val navkontor: String?,
        val veilederNavn: String?,
        val veilederNavIdent: String?,
        val alder: Int?,
        val innsatsgruppe: String?,
    )

    private data class JobbsokerInfoResponsSvar(
        val jobbsokerInfo: List<JobbsokerInfoSvar>,
    )

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
    fun `returnerer alle berikningsfelter fra opensearch`() {
        stubOpensearch(
            """
            {
              "took": 1,
              "timed_out": false,
              "_shards": { "total": 1, "successful": 1, "skipped": 0, "failed": 0 },
              "hits": {
                "total": { "value": 1, "relation": "eq" },
                "max_score": null,
                "hits": [{
                  "_index": "kandidater",
                  "_id": "1",
                  "_score": 1.0,
                  "_source": {
                    "fodselsnummer": "11111111111",
                    "navkontor": "Nav Oslo",
                    "veilederVisningsnavn": "Kari Veileder",
                    "veilederIdent": "Z111111",
                    "fodselsdato": "1990-05-17",
                    "innsatsgruppe": "SITUASJONSBESTEMT_INNSATS"
                  }
                }]
              }
            }
            """.trimIndent()
        )

        val response = post("""{"fodselsnumre":["11111111111"]}""")
        assertEquals(HTTP_OK, response.statusCode())

        val body = objectMapper.readValue(response.body(), JobbsokerInfoResponsSvar::class.java)
        assertEquals(1, body.jobbsokerInfo.size)
        val info = body.jobbsokerInfo.single()
        assertEquals("11111111111", info.fodselsnummer)
        assertEquals("Nav Oslo", info.navkontor)
        assertEquals("Kari Veileder", info.veilederNavn)
        assertEquals("Z111111", info.veilederNavIdent)
        assertEquals(forventetAlder("1990-05-17"), info.alder)
        assertEquals("SITUASJONSBESTEMT_INNSATS", info.innsatsgruppe)
    }

    @Test
    fun `manglende fnr gir rad med null-felter`() {
        stubOpensearch(tomtOpensearchSvar())

        val response = post("""{"fodselsnumre":["22222222222"]}""")
        assertEquals(HTTP_OK, response.statusCode())

        val body = objectMapper.readValue(response.body(), JobbsokerInfoResponsSvar::class.java)
        assertEquals(1, body.jobbsokerInfo.size)
        val info = body.jobbsokerInfo.single()
        assertEquals("22222222222", info.fodselsnummer)
        assertNull(info.navkontor)
        assertNull(info.veilederNavn)
        assertNull(info.veilederNavIdent)
        assertNull(info.alder)
        assertNull(info.innsatsgruppe)
    }

    @Test
    fun `delvis utfylte felter mappes med null der det mangler`() {
        stubOpensearch(
            """
            {
              "took": 1,
              "timed_out": false,
              "_shards": { "total": 1, "successful": 1, "skipped": 0, "failed": 0 },
              "hits": {
                "total": { "value": 1, "relation": "eq" },
                "max_score": null,
                "hits": [{
                  "_index": "kandidater",
                  "_id": "1",
                  "_score": 1.0,
                  "_source": {
                    "fodselsnummer": "33333333333",
                    "navkontor": "Nav Bergen",
                    "veilederVisningsnavn": null,
                    "veilederIdent": null,
                    "fodselsdato": "1985-01-01",
                    "innsatsgruppe": null
                  }
                }]
              }
            }
            """.trimIndent()
        )

        val response = post("""{"fodselsnumre":["33333333333"]}""")
        assertEquals(HTTP_OK, response.statusCode())

        val body = objectMapper.readValue(response.body(), JobbsokerInfoResponsSvar::class.java)
        val info = body.jobbsokerInfo.single()
        assertEquals("Nav Bergen", info.navkontor)
        assertNull(info.veilederNavn)
        assertNull(info.veilederNavIdent)
        assertEquals(forventetAlder("1985-01-01"), info.alder)
        assertNull(info.innsatsgruppe)
    }

    @Test
    fun `tom liste returnerer tom respons uten opensearch-kall`() {
        val response = post("""{"fodselsnumre":[]}""")
        assertEquals(HTTP_OK, response.statusCode())
        val body = objectMapper.readValue(response.body(), JobbsokerInfoResponsSvar::class.java)
        assertEquals(0, body.jobbsokerInfo.size)
    }

    private fun autorisasjonsCaser() = listOf(
        Arguments.of(Gruppe.Utvikler, HTTP_OK),
        Arguments.of(Gruppe.Arbeidsgiverrettet, HTTP_OK),
        Arguments.of(Gruppe.Jobbsøkerrettet, HTTP_OK),
        Arguments.of(Gruppe.ModiaGenerell, HTTP_FORBIDDEN),
    )

    @ParameterizedTest
    @MethodSource("autorisasjonsCaser")
    fun `Skal håndtere autorisasjon riktig`(gruppe: Gruppe, forventetStatuskode: Int) {
        stubOpensearch(tomtOpensearchSvar())

        val request = HttpRequest.newBuilder()
            .uri(URI(endepunkt))
            .header("Authorization", "Bearer ${app.lagToken(groups = gruppe.somStringListe).serialize()}")
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString("""{"fodselsnumre":["99999999999"]}"""))
            .build()

        val response = httpClient.send(request, HttpResponse.BodyHandlers.ofString())
        assertEquals(forventetStatuskode, response.statusCode())
    }

    private fun post(body: String): HttpResponse<String> {
        val request = HttpRequest.newBuilder()
            .uri(URI(endepunkt))
            .header("Authorization", "Bearer ${app.lagToken(groups = Gruppe.Jobbsøkerrettet.somStringListe).serialize()}")
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(body))
            .build()
        return httpClient.send(request, HttpResponse.BodyHandlers.ofString())
    }

    private fun stubOpensearch(body: String) {
        stubFor(
            post(urlEqualTo("/kandidater/_search?typed_keys=true"))
                .willReturn(
                    aResponse()
                        .withStatus(200)
                        .withBody(body)
                )
        )
    }

    private fun tomtOpensearchSvar() = """
        {
          "took": 1,
          "timed_out": false,
          "_shards": { "total": 1, "successful": 1, "skipped": 0, "failed": 0 },
          "hits": {
            "total": { "value": 0, "relation": "eq" },
            "max_score": null,
            "hits": []
          }
        }
    """.trimIndent()

    private fun forventetAlder(fodselsdato: String) = Period.between(LocalDate.parse(fodselsdato), LocalDate.now()).years

    enum class Gruppe(val somStringListe: List<String>) {
        ModiaGenerell(listOf(LokalApp.modiaGenerell)),
        Utvikler(listOf(LokalApp.utvikler)),
        Jobbsøkerrettet(listOf(LokalApp.jobbsøkerrettet)),
        Arbeidsgiverrettet(listOf(LokalApp.arbeidsgiverrettet)),
    }
}
