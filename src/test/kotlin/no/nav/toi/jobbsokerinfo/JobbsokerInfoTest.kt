package no.nav.toi.jobbsokerinfo

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.github.tomakehurst.wiremock.client.WireMock.aResponse
import com.github.tomakehurst.wiremock.client.WireMock.get
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
import java.net.HttpURLConnection.HTTP_BAD_REQUEST
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
    private val rekrutteringstreffApiClientId = "rekrutteringstreff-api-client-id"
    private val frontendClientId = "frontend-client-id"
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
        val orgenhet: String?,
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
                    "orgenhet": "0314",
                    "fodselsdato": "1990-05-17",
                    "innsatsgruppe": "SITUASJONSBESTEMT_INNSATS"
                  }
                }]
              }
            }
            """.trimIndent()
        )

        val response = post("""{"fodselsnumre":["11111111111"]}""", gruppe = Gruppe.Arbeidsgiverrettet)
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
        assertEquals("0314", info.orgenhet)
    }

    @Test
    fun `manglende fnr gir ingen rad`() {
        stubOpensearch(tomtOpensearchSvar())

        val response = post("""{"fodselsnumre":["22222222222"]}""")
        assertEquals(HTTP_OK, response.statusCode())

        val body = objectMapper.readValue(response.body(), JobbsokerInfoResponsSvar::class.java)
        assertEquals(0, body.jobbsokerInfo.size)
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

        val response = post("""{"fodselsnumre":["33333333333"]}""", gruppe = Gruppe.Arbeidsgiverrettet)
        assertEquals(HTTP_OK, response.statusCode())

        val body = objectMapper.readValue(response.body(), JobbsokerInfoResponsSvar::class.java)
        val info = body.jobbsokerInfo.single()
        assertEquals("Nav Bergen", info.navkontor)
        assertNull(info.veilederNavn)
        assertNull(info.veilederNavIdent)
        assertEquals(forventetAlder("1985-01-01"), info.alder)
        assertNull(info.innsatsgruppe)
        assertNull(info.orgenhet)
    }

    @Test
    fun `tom liste returnerer tom respons uten opensearch-kall`() {
        val response = post("""{"fodselsnumre":[]}""")
        assertEquals(HTTP_OK, response.statusCode())
        val body = objectMapper.readValue(response.body(), JobbsokerInfoResponsSvar::class.java)
        assertEquals(0, body.jobbsokerInfo.size)
    }

    @Test
    fun `returnerer bad request ved for mange fodselsnumre`() {
        val fodselsnumre = (1..501).joinToString(",") { "\"${it.toString().padStart(11, '0')}\"" }
        val response = post("""{"fodselsnumre":[$fodselsnumre]}""")

        assertEquals(HTTP_BAD_REQUEST, response.statusCode())
    }

    @Test
    fun `nekter kall som ikke kommer fra rekrutteringstreff-api`() {
        val response = post(
            body = """{"fodselsnumre":["11111111111"]}""",
            clientId = frontendClientId,
        )

        assertEquals(HTTP_FORBIDDEN, response.statusCode())
    }

    @Test
    fun `jobbsokerrettet får jobbsokerinfo når kandidaten er egen bruker`() {
        stubOpensearch(
            opensearchSvar(
                """
                {
                  "fodselsnummer": "44444444444",
                  "navkontor": "Nav Trondheim",
                  "veilederVisningsnavn": "Test Veileder",
                  "veilederIdent": "A000001",
                  "orgenhet": "9999",
                  "fodselsdato": "1991-02-03",
                  "innsatsgruppe": "STANDARD_INNSATS"
                }
                """.trimIndent()
            )
        )

        val response = post("""{"fodselsnumre":["44444444444"]}""", gruppe = Gruppe.Jobbsøkerrettet, navIdent = "A000001")

        assertEquals(HTTP_OK, response.statusCode())
        val body = objectMapper.readValue(response.body(), JobbsokerInfoResponsSvar::class.java)
        assertEquals(listOf("44444444444"), body.jobbsokerInfo.map { it.fodselsnummer })
    }

    @Test
    fun `jobbsokerrettet får jobbsokerinfo når kandidaten er på eget kontor`() {
        stubModiaDecorator(enheter = listOf("1234"))
        stubOpensearch(
            opensearchSvar(
                """
                {
                  "fodselsnummer": "55555555555",
                  "navkontor": "Nav Tromsø",
                  "veilederVisningsnavn": "Annen Veileder",
                  "veilederIdent": "Z999999",
                  "orgenhet": "1234",
                  "fodselsdato": "1988-02-03",
                  "innsatsgruppe": "STANDARD_INNSATS"
                }
                """.trimIndent()
            )
        )

        val response = post("""{"fodselsnumre":["55555555555"]}""", gruppe = Gruppe.Jobbsøkerrettet)

        assertEquals(HTTP_OK, response.statusCode())
        val body = objectMapper.readValue(response.body(), JobbsokerInfoResponsSvar::class.java)
        assertEquals(listOf("55555555555"), body.jobbsokerInfo.map { it.fodselsnummer })
    }

    @Test
    fun `nekter jobbsokerrettet når kandidaten er utenfor egen tilgang`() {
        stubModiaDecorator(enheter = listOf("1234"))
        stubOpensearch(
            opensearchSvar(
                """
                {
                  "fodselsnummer": "66666666666",
                  "navkontor": "Nav Stavanger",
                  "veilederVisningsnavn": "Annen Veileder",
                  "veilederIdent": "Z999999",
                  "orgenhet": "9999",
                  "fodselsdato": "1988-02-03",
                  "innsatsgruppe": "STANDARD_INNSATS"
                }
                """.trimIndent()
            )
        )

        val response = post("""{"fodselsnumre":["66666666666"]}""", gruppe = Gruppe.Jobbsøkerrettet)

        assertEquals(HTTP_FORBIDDEN, response.statusCode())
    }

    @Test
    fun `nekter hele batchen når en kandidat er utenfor egen tilgang`() {
        stubModiaDecorator(enheter = listOf("1234"))
        stubOpensearch(
            opensearchSvar(
                """
                {
                  "fodselsnummer": "77777777777",
                  "navkontor": "Nav Trondheim",
                  "veilederVisningsnavn": "Test Veileder",
                  "veilederIdent": "A000001",
                  "orgenhet": "9999",
                  "fodselsdato": "1991-02-03",
                  "innsatsgruppe": "STANDARD_INNSATS"
                }
                """.trimIndent(),
                """
                {
                  "fodselsnummer": "88888888888",
                  "navkontor": "Nav Stavanger",
                  "veilederVisningsnavn": "Annen Veileder",
                  "veilederIdent": "Z999999",
                  "orgenhet": "9999",
                  "fodselsdato": "1988-02-03",
                  "innsatsgruppe": "STANDARD_INNSATS"
                }
                """.trimIndent()
            )
        )

        val response = post("""{"fodselsnumre":["77777777777","88888888888"]}""", gruppe = Gruppe.Jobbsøkerrettet)

        assertEquals(HTTP_FORBIDDEN, response.statusCode())
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
            .header("Authorization", "Bearer ${app.lagToken(groups = gruppe.somStringListe, claims = tokenClaims(gruppe.somStringListe), clientId = rekrutteringstreffApiClientId).serialize()}")
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString("""{"fodselsnumre":["99999999999"]}"""))
            .build()

        val response = httpClient.send(request, HttpResponse.BodyHandlers.ofString())
        assertEquals(forventetStatuskode, response.statusCode())
    }

    private fun post(
        body: String,
        clientId: String = rekrutteringstreffApiClientId,
        gruppe: Gruppe = Gruppe.Jobbsøkerrettet,
        navIdent: String = "A000001",
    ): HttpResponse<String> {
        val request = HttpRequest.newBuilder()
            .uri(URI(endepunkt))
            .header(
                "Authorization",
                "Bearer ${app.lagToken(groups = gruppe.somStringListe, claims = tokenClaims(gruppe.somStringListe, navIdent), clientId = clientId).serialize()}"
            )
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(body))
            .build()
        return httpClient.send(request, HttpResponse.BodyHandlers.ofString())
    }

    private fun tokenClaims(groups: List<String>, navIdent: String = "A000001") = mapOf(
        "NAVident" to navIdent,
        "groups" to groups,
    )

    private fun stubModiaDecorator(enheter: List<String>) {
        val enheterJson = enheter.joinToString(",") { """{"enhetId":"$it","navn":"Nav Test"}""" }
        stubFor(
            get(urlEqualTo("/modia/api/decorator"))
                .willReturn(
                    aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(
                            """
                            {
                              "ident": "A000001",
                              "fornavn": "Test",
                              "etternavn": "Veileder",
                              "enheter": [$enheterJson]
                            }
                            """.trimIndent()
                        )
                )
        )
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

        private fun opensearchSvar(vararg kilder: String) = """
                {
                    "took": 1,
                    "timed_out": false,
                    "_shards": { "total": 1, "successful": 1, "skipped": 0, "failed": 0 },
                    "hits": {
                        "total": { "value": ${kilder.size}, "relation": "eq" },
                        "max_score": null,
                        "hits": [${kilder.mapIndexed { indeks, kilde -> """{"_index":"kandidater","_id":"$indeks","_score":1.0,"_source":$kilde}""" }.joinToString(",")}]
                    }
                }
        """.trimIndent()

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
