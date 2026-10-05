package no.nav.toi.lookupcv

import java.net.URI
import java.net.http.HttpRequest
import java.net.http.HttpRequest.BodyPublishers
import java.net.http.HttpResponse.BodyHandlers
import no.nav.toi.testHttpClient
import no.nav.toi.testObjectMapper
import tools.jackson.databind.ObjectMapper
import tools.jackson.databind.node.ObjectNode
import tools.jackson.module.kotlin.jacksonObjectMapper
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
class CvLookupTest {
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
    fun `Kan hente cv`(wmRuntimeInfo: WireMockRuntimeInfo) {
        val wireMock = wmRuntimeInfo.wireMock
        wireMock.register(
            post("/kandidater/_search?typed_keys=true")
                .withRequestBody(equalToJson("""{"query":{"term":{"kandidatnr":{"value":"PAM0xtfrwli5" }}}}"""))
                .willReturn(
                    ok(CvTestRespons.responseOpenSearch(CvTestRespons.sourceCvLookup))
                )
        )
        val navIdent = "A123456"
        val token = app.lagToken(navIdent = navIdent, groups = listOf(LokalApp.arbeidsgiverrettet))
        val response = gjørKall(token)

        Assertions.assertThat(response.statusCode()).isEqualTo(200)
        Assertions.assertThat(testObjectMapper.readTree(response.body())).isEqualTo(ObjectMapper().readTree(CvTestRespons.responseCvLookup))
    }

    @Test
    fun `Finner ikke cv`(wmRuntimeInfo: WireMockRuntimeInfo) {
        val wireMock = wmRuntimeInfo.wireMock
        wireMock.register(
            post("/kandidater/_search?typed_keys=true")
                .withRequestBody(equalToJson("""{"query":{"term":{"kandidatnr":{"value":"PAM000000000" }}}}"""))
                .willReturn(
                    ok(CvTestRespons.responseOpensearchIngenTreff)
                )
        )
        val navIdent = "A123456"
        val token = app.lagToken(navIdent = navIdent, groups = listOf(LokalApp.arbeidsgiverrettet))
        val response = testHttpClient.send(
            HttpRequest.newBuilder(URI("http://localhost:8080/api/lookup-cv"))
                .header("Authorization", "Bearer ${token.serialize()}")
                .POST(BodyPublishers.ofString("""{"kandidatnr": "PAM000000000"}"""))
                .build(),
            BodyHandlers.ofString()
        )

        Assertions.assertThat(response.statusCode()).isEqualTo(200)
        Assertions.assertThat(testObjectMapper.readTree(response.body())).isEqualTo(ObjectMapper().readTree("""
        {
          "hits": {
            "hits": []
          }
        }
    """.trimIndent()))
    }

    @Test
    fun `Om kall feiler under henting av cv fra elasticsearch, får vi HTTP 500`(wmRuntimeInfo: WireMockRuntimeInfo) {
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
        val response = gjørKall(token)

        Assertions.assertThat(response.statusCode()).isEqualTo(500)
    }

    @Test
    fun `modia generell skal ikke ha tilgang til cv`(wmRuntimeInfo: WireMockRuntimeInfo) {
        val token = app.lagToken(groups = listOf(LokalApp.modiaGenerell))
        wmRuntimeInfo.wireMock.register(
            post("/kandidater/_search?typed_keys=true")
                .withRequestBody(equalToJson("""{"query":{"term":{"kandidatnr":{"value":"PAM0xtfrwli5" }}}}"""))
                .willReturn(
                    ok(CvTestRespons.responseOpenSearch(CvTestRespons.sourceCvLookup))
                )
        )
        val response = gjørKall(token)

        Assertions.assertThat(response.statusCode()).isEqualTo(403)
    }

    @Test
    fun `arbeidsgiverrettet skal ha tilgang til cv`(wmRuntimeInfo: WireMockRuntimeInfo) {
        val wireMock = wmRuntimeInfo.wireMock
        wireMock.register(
            post("/kandidater/_search?typed_keys=true")
                .withRequestBody(equalToJson("""{"query":{"term":{"kandidatnr":{"value":"PAM0xtfrwli5" }}}}"""))
                .willReturn(
                    ok(CvTestRespons.responseOpenSearch(CvTestRespons.sourceCvLookup))
                )
        )
        val token = app.lagToken(groups = listOf(LokalApp.arbeidsgiverrettet))
        val response = gjørKall(token)

        Assertions.assertThat(response.statusCode()).isEqualTo(200)
    }

    @Test
    fun `jobbsøkerrettet skal ha tilgang til cv dersom hen er kandidatens veileder`(wmRuntimeInfo: WireMockRuntimeInfo) {

        val veiledersIdent = "A000001"
        val veiledersOrgenhet = "1234"
        val feilVeiledersOrgenhet = "0000"

        val wireMock = wmRuntimeInfo.wireMock
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
                                    "navn": "NAV Feil"
                                }
                            ]
                }
            """.trimIndent()
                    )
                )
        )

        val returMedRiktigVeilederFeilKontor = byttVeilederOgKontorForKandidatEsResponse(veiledersIdent, feilVeiledersOrgenhet)

        wireMock.register(
            post("/kandidater/_search?typed_keys=true")
                .withRequestBody(equalToJson("""{"query":{"term":{"kandidatnr":{"value":"PAM0xtfrwli5" }}}}"""))
                .willReturn(
                    ok(CvTestRespons.responseOpenSearch(returMedRiktigVeilederFeilKontor))
                )
        )

        val token = app.lagToken(groups = listOf(LokalApp.jobbsøkerrettet))
        val response = gjørKall(token)

        Assertions.assertThat(response.statusCode()).isEqualTo(200)
    }

    @Test
    fun `jobbsøkerrettet skal ikke ha tilgang til cv dersom kandidaten ikke har veileder`(wmRuntimeInfo: WireMockRuntimeInfo) {

        val veiledersIdent = "A000001"
        val veiledersOrgenhet = "1234"
        val feilVeiledersOrgenhet = "0000"

        val wireMock = wmRuntimeInfo.wireMock
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
                                    "navn": "NAV Feil"
                                }
                            ]
                }
            """.trimIndent()
                    )
                )
        )

        val returUtenVeilederEllerKontor = byttVeilederOgKontorForKandidatEsResponse(null, null)

        wireMock.register(
            post("/kandidater/_search?typed_keys=true")
                .withRequestBody(equalToJson("""{"query":{"term":{"kandidatnr":{"value":"PAM0xtfrwli5" }}}}"""))
                .willReturn(
                    ok(CvTestRespons.responseOpenSearch(returUtenVeilederEllerKontor))
                )
        )

        val token = app.lagToken(groups = listOf(LokalApp.jobbsøkerrettet))
        val response = gjørKall(token)

        Assertions.assertThat(response.statusCode()).isEqualTo(403)
    }

    @Test
    fun `jobbsøkerrettet skal ha tilgang til cv dersom hen er tilknyttet kandidatens kontor`(wmRuntimeInfo: WireMockRuntimeInfo) {

        val veiledersIdent = "A000001"
        val feilVeilederIdent = "X100000"
        val veiledersOrgenhet = "0403"
        val feilVeiledersOrgenhet = "0000"


        val wireMock = wmRuntimeInfo.wireMock
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
                                },
                                      {
                                    "enhetId": "1234",
                                    "navn": "NAV ANNET KONTOR"
                                }
                            ]
                }
            """.trimIndent()
                    )
                )
        )

        val returMedRiktigVeilederFeilKontor = byttVeilederOgKontorForKandidatEsResponse(feilVeilederIdent, veiledersOrgenhet)

        wireMock.register(
            post("/kandidater/_search?typed_keys=true")
                .withRequestBody(equalToJson("""{"query":{"term":{"kandidatnr":{"value":"PAM0xtfrwli5" }}}}"""))
                .willReturn(
                    ok(CvTestRespons.responseOpenSearch(returMedRiktigVeilederFeilKontor))
                )
        )

        val token = app.lagToken(groups = listOf(LokalApp.jobbsøkerrettet))
        val response = gjørKall(token)

        Assertions.assertThat(response.statusCode()).isEqualTo(200)
    }


    @Test
    fun `jobbsøkerrettet skal ikke ha tilgang til cv dersom hen ikke er kandidatens veileder og ikke er tilknyttet kandidatens kontor`(wmRuntimeInfo: WireMockRuntimeInfo) {
        val veiledersIdent = "A000001"
        val feilVeiledersIdent = "X100000"
        val veiledersOrgenhet = "1234"
        val feilVeiledersOrgenhet = "0000"

        val wireMock = wmRuntimeInfo.wireMock
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
                                    "enhetId": "$veiledersIdent",
                                    "navn": "NAV HAMAR"
                                }
                            ]
                }
            """.trimIndent()
                    )
                )
        )

        val returMedRiktigVeilederFeilKontor = byttVeilederOgKontorForKandidatEsResponse(feilVeiledersIdent, feilVeiledersOrgenhet)

        wireMock.register(
            post("/kandidater/_search?typed_keys=true")
                .withRequestBody(equalToJson("""{"query":{"term":{"kandidatnr":{"value":"PAM0xtfrwli5" }}}}"""))
                .willReturn(
                    ok(CvTestRespons.responseOpenSearch(returMedRiktigVeilederFeilKontor))
                )
        )

        val token = app.lagToken(groups = listOf(LokalApp.jobbsøkerrettet))
        val response = gjørKall(token)

        Assertions.assertThat(response.statusCode()).isEqualTo(403)
    }

    @Test
    fun `jobbsøkerrettet skal ha tilgang til cv dersom hen ikke er kandidatens veileder og ikke er tilknyttet kandidatens kontor, men har også arbeidsgiverrettet rolle`(wmRuntimeInfo: WireMockRuntimeInfo) {
        val veiledersIdent = "A000001"
        val feilVeiledersIdent = "X100000"
        val veiledersOrgenhet = "1234"
        val feilVeiledersOrgenhet = "0000"

        val wireMock = wmRuntimeInfo.wireMock
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
                                    "enhetId": "$veiledersIdent",
                                    "navn": "NAV HAMAR"
                                }
                            ]
                }
            """.trimIndent()
                    )
                )
        )

        val returMedRiktigVeilederFeilKontor = byttVeilederOgKontorForKandidatEsResponse(feilVeiledersIdent, feilVeiledersOrgenhet)

        wireMock.register(
            post("/kandidater/_search?typed_keys=true")
                .withRequestBody(equalToJson("""{"query":{"term":{"kandidatnr":{"value":"PAM0xtfrwli5" }}}}"""))
                .willReturn(
                    ok(CvTestRespons.responseOpenSearch(returMedRiktigVeilederFeilKontor))
                )
        )

        val token = app.lagToken(groups = listOf(LokalApp.jobbsøkerrettet, LokalApp.arbeidsgiverrettet))
        val response = gjørKall(token)

        Assertions.assertThat(response.statusCode()).isEqualTo(200)
    }


    @Test
    fun `utvikler skal ha tilgang til cv`(wmRuntimeInfo: WireMockRuntimeInfo) {
        val wireMock = wmRuntimeInfo.wireMock
        wireMock.register(
            post("/kandidater/_search?typed_keys=true")
                .withRequestBody(equalToJson("""{"query":{"term":{"kandidatnr":{"value":"PAM0xtfrwli5" }}}}"""))
                .willReturn(
                    ok(CvTestRespons.responseOpenSearch(CvTestRespons.sourceCvLookup))
                )
        )
        val token = app.lagToken(groups = listOf(LokalApp.utvikler))
        val response = gjørKall(token)

        Assertions.assertThat(response.statusCode()).isEqualTo(200)
    }

    @Test
    fun `om man ikke har gruppetilhørighet skal man ikke få cv`(wmRuntimeInfo: WireMockRuntimeInfo) {
        val token = app.lagToken(groups = emptyList())
        wmRuntimeInfo.wireMock.register(
            post("/kandidater/_search?typed_keys=true")
                .withRequestBody(equalToJson("""{"query":{"term":{"kandidatnr":{"value":"PAM0xtfrwli5" }}}}"""))
                .willReturn(
                    ok(CvTestRespons.responseOpenSearch(CvTestRespons.sourceCvLookup))
                )
        )
        val response = gjørKall(token)

        Assertions.assertThat(response.statusCode()).isEqualTo(403)
    }

    private fun byttVeilederOgKontorForKandidatEsResponse(veiledersIdent: String?, kandidatensOrgnummer: String?): String {
        val mapper = jacksonObjectMapper()
        val jsonNode = mapper.readTree(CvTestRespons.sourceCvLookup)
        if(veiledersIdent == null) (jsonNode as ObjectNode).putNull("veilederIdent")
        else (jsonNode as ObjectNode).put("veilederIdent",veiledersIdent)
        if(kandidatensOrgnummer == null) (jsonNode as ObjectNode).putNull("orgenhet")
        else jsonNode.put("orgenhet", kandidatensOrgnummer)
        val returMedRiktigVeilederFeilKontor = mapper.writeValueAsString(jsonNode)
        return returMedRiktigVeilederFeilKontor
    }


    private fun gjørKall(token: SignedJWT) = testHttpClient.send(
        HttpRequest.newBuilder(URI("http://localhost:8080/api/lookup-cv"))
            .header("Authorization", "Bearer ${token.serialize()}")
            .POST(BodyPublishers.ofString("""{"kandidatnr": "PAM0xtfrwli5"}"""))
            .build(),
        BodyHandlers.ofString()
    )

}
