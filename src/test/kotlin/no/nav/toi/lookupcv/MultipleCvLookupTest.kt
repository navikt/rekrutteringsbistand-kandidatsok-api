package no.nav.toi.lookupcv

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.node.ObjectNode
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.github.kittinunf.fuel.Fuel
import com.github.kittinunf.fuel.jackson.responseObject
import com.github.tomakehurst.wiremock.client.WireMock.*
import com.github.tomakehurst.wiremock.junit5.WireMockRuntimeInfo
import com.github.tomakehurst.wiremock.junit5.WireMockTest
import com.nimbusds.jwt.SignedJWT
import no.nav.toi.LokalApp
import org.assertj.core.api.Assertions
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@WireMockTest(httpPort = 10000)
class MultipleCvLookupTest {
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
    fun `Kan hente cver`(wmRuntimeInfo: WireMockRuntimeInfo) {
        val wireMock = wmRuntimeInfo.wireMock
        wireMock.register(
            post("/veilederkandidat_current/_search?typed_keys=true")
                .withRequestBody(equalToJson("""{"query":{"terms":{"kandidatnr":["PAM0xtfrwli5","PAM0123456789","PAM0987654321"]}}}"""))
                .willReturn(
                    ok(CvTestRespons.responseOpenSearch(*CvTestRespons.sourceMultipleCvLookup.toTypedArray()))
                )
        )
        val navIdent = "A123456"
        val token = app.lagToken(navIdent = navIdent, groups = listOf(LokalApp.arbeidsgiverrettet))
        val (_, response, result) = gjørKall(token)

        Assertions.assertThat(response.statusCode).isEqualTo(200)
        Assertions.assertThat(result.get()).isEqualTo(ObjectMapper().readTree(CvTestRespons.multipleResponseCvLookup))
    }

    @Test
    fun `Finner ikke enkelt cv`(wmRuntimeInfo: WireMockRuntimeInfo) {
        val wireMock = wmRuntimeInfo.wireMock
        wireMock.register(
            post("/veilederkandidat_current/_search?typed_keys=true")
                .withRequestBody(equalToJson("""{"query":{"terms":{"kandidatnr":["PAM000000000"]}}}"""))
                .willReturn(
                    ok(CvTestRespons.responseOpensearchIngenTreff)
                )
        )
        val navIdent = "A123456"
        val token = app.lagToken(navIdent = navIdent, groups = listOf(LokalApp.arbeidsgiverrettet))
        val (_, response, result) = Fuel.post("http://localhost:8080/api/multiple-lookup-cv")
            .body("""{"kandidatnr": ["PAM000000000"]}""")
            .header("Authorization", "Bearer ${token.serialize()}")
            .responseObject<JsonNode>()

        Assertions.assertThat(response.statusCode).isEqualTo(200)
        Assertions.assertThat(result.get()).isEqualTo(ObjectMapper().readTree("""
        {
          "hits": {
            "hits": []
          }
        }
    """.trimIndent()))
    }

    @Test
    fun `Om kall feiler under henting av cver fra elasticsearch, får vi HTTP 500`(wmRuntimeInfo: WireMockRuntimeInfo) {
        val wireMock = wmRuntimeInfo.wireMock
        wireMock.register(
            post("/veilederkandidat_current/_search?typed_keys=true")
                .withRequestBody(equalToJson("""{"query":{"terms":{"kandidatnr":["PAM0xtfrwli5","PAM0123456789","PAM0987654321"]}}}"""))
                .willReturn(
                    notFound()
                )
        )
        val navIdent = "A123456"
        val token = app.lagToken(navIdent = navIdent, groups = listOf(LokalApp.arbeidsgiverrettet))
        val (_, response) = gjørKall(token)

        Assertions.assertThat(response.statusCode).isEqualTo(500)
    }

    @Test
    fun `modia generell skal ikke ha tilgang til multiplecver`(wmRuntimeInfo: WireMockRuntimeInfo) {
        val token = app.lagToken(groups = listOf(LokalApp.modiaGenerell))
        wmRuntimeInfo.wireMock.register(
            post("/veilederkandidat_current/_search?typed_keys=true")
                .withRequestBody(equalToJson("""{"query":{"terms":{"kandidatnr":["PAM0xtfrwli5","PAM0123456789","PAM0987654321"]}}}"""))
                .willReturn(
                    ok(CvTestRespons.responseOpenSearch(*CvTestRespons.sourceMultipleCvLookup.toTypedArray()))
                )
        )
        val (_, response, _) = gjørKall(token)

        Assertions.assertThat(response.statusCode).isEqualTo(403)
    }

    @Test
    fun `arbeidsgiverrettet skal ha tilgang til cver`(wmRuntimeInfo: WireMockRuntimeInfo) {
        val wireMock = wmRuntimeInfo.wireMock
        wireMock.register(
            post("/veilederkandidat_current/_search?typed_keys=true")
                .withRequestBody(equalToJson("""{"query":{"terms":{"kandidatnr":["PAM0xtfrwli5","PAM0123456789","PAM0987654321"]}}}"""))
                .willReturn(
                    ok(CvTestRespons.responseOpenSearch(*CvTestRespons.sourceMultipleCvLookup.toTypedArray()))
                )
        )
        val token = app.lagToken(groups = listOf(LokalApp.arbeidsgiverrettet))
        val (_, response) = gjørKall(token)

        Assertions.assertThat(response.statusCode).isEqualTo(200)
    }

    @Disabled
    @Test
    fun `jobbsøkerrettet skal ha tilgang til cver dersom hen er kandidatens veileder`(wmRuntimeInfo: WireMockRuntimeInfo) {

        val veiledersIdent = "A000001"
        val annenIdent = "A000002"
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

        val returMedRiktigVeilederFeilKontor = byttVeilederOgKontorForKandidatEsResponse(listOf(annenIdent, veiledersIdent, null), (1..3).map { feilVeiledersOrgenhet })

        wireMock.register(
            post("/veilederkandidat_current/_search?typed_keys=true")
                .withRequestBody(equalToJson("""{"query":{"terms":{"kandidatnr":["PAM0xtfrwli5","PAM0123456789","PAM0987654321"]}}}"""))
                .willReturn(
                    ok(CvTestRespons.responseOpenSearch(*returMedRiktigVeilederFeilKontor.toTypedArray()))
                )
        )

        val token = app.lagToken(groups = listOf(LokalApp.jobbsøkerrettet))
        val (_, response, result) = gjørKall(token)

        Assertions.assertThat(response.statusCode).isEqualTo(200)

        val jsonNode = result.get().first().first()
        assertThat(jsonNode).hasSize(1)
        assertThat(jsonNode.first()["_source"]["kandidatnr"].asText()).isEqualTo("PAM0123456789")
    }

    @Disabled
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

        val returMedRiktigVeilederFeilKontor = byttVeilederOgKontorForKandidatEsResponse((1..3).map { feilVeilederIdent }, listOf(null, feilVeiledersOrgenhet, veiledersOrgenhet))

        wireMock.register(
            post("/veilederkandidat_current/_search?typed_keys=true")
                .withRequestBody(equalToJson("""{"query":{"terms":{"kandidatnr":["PAM0xtfrwli5","PAM0123456789","PAM0987654321"]}}}"""))
                .willReturn(
                    ok(CvTestRespons.responseOpenSearch(*returMedRiktigVeilederFeilKontor.toTypedArray()))
                )
        )

        val token = app.lagToken(groups = listOf(LokalApp.jobbsøkerrettet))
        val (_, response, result) = gjørKall(token)

        Assertions.assertThat(response.statusCode).isEqualTo(200)

        val jsonNode = result.get().first().first()
        assertThat(jsonNode).hasSize(1)
        assertThat(jsonNode.first()["_source"]["kandidatnr"].asText()).isEqualTo("PAM0987654321")
    }

    @Disabled
    @Test
    fun `jobbsøkerrettet skal ha tilgang til cver dersom hen ikke er kandidatens veileder og ikke er tilknyttet kandidatens kontor, men har også arbeidsgiverrettet rolle`(wmRuntimeInfo: WireMockRuntimeInfo) {
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

        val returMedRiktigVeilederFeilKontor = byttVeilederOgKontorForKandidatEsResponse((1..3).map { feilVeiledersIdent }, (1..3).map { feilVeiledersOrgenhet })

        wireMock.register(
            post("/veilederkandidat_current/_search?typed_keys=true")
                .withRequestBody(equalToJson("""{"query":{"terms":{"kandidatnr":["PAM0xtfrwli5","PAM0123456789","PAM0987654321"]}}}"""))
                .willReturn(
                    ok(CvTestRespons.responseOpenSearch(*returMedRiktigVeilederFeilKontor.toTypedArray()))
                )
        )

        val token = app.lagToken(groups = listOf(LokalApp.jobbsøkerrettet, LokalApp.arbeidsgiverrettet))
        val (_, response, result) = gjørKall(token)

        Assertions.assertThat(response.statusCode).isEqualTo(200)

        val jsonNode = result.get().first().first()
        assertThat(jsonNode).hasSize(3)
        assertThat(jsonNode.map { it["_source"]["kandidatnr"].asText() }).containsExactlyInAnyOrder("PAM0xtfrwli5","PAM0123456789","PAM0987654321")
    }


    @Test
    fun `utvikler skal ha tilgang til cver`(wmRuntimeInfo: WireMockRuntimeInfo) {
        val wireMock = wmRuntimeInfo.wireMock
        wireMock.register(
            post("/veilederkandidat_current/_search?typed_keys=true")
                .withRequestBody(equalToJson("""{"query":{"terms":{"kandidatnr":["PAM0xtfrwli5","PAM0123456789","PAM0987654321"]}}}"""))
                .willReturn(
                    ok(CvTestRespons.responseOpenSearch(*CvTestRespons.sourceMultipleCvLookup.toTypedArray()))
                )
        )
        val token = app.lagToken(groups = listOf(LokalApp.utvikler))
        val (_, response) = gjørKall(token)

        Assertions.assertThat(response.statusCode).isEqualTo(200)
    }

    @Test
    fun `om man ikke har gruppetilhørighet skal man ikke få cv`(wmRuntimeInfo: WireMockRuntimeInfo) {
        val token = app.lagToken(groups = emptyList())
        wmRuntimeInfo.wireMock.register(
            post("/veilederkandidat_current/_search?typed_keys=true")
                .withRequestBody(equalToJson("""{"query":{"terms":{"kandidatnr":["PAM0xtfrwli5","PAM0123456789","PAM0987654321"]}}}"""))
                .willReturn(
                    ok(CvTestRespons.responseOpenSearch(*CvTestRespons.sourceMultipleCvLookup.toTypedArray()))
                )
        )
        val (_, response) = gjørKall(token)

        Assertions.assertThat(response.statusCode).isEqualTo(403)
    }

    private fun byttVeilederOgKontorForKandidatEsResponse(veiledersIdent: List<String?>, kandidatensOrgnummer: List<String?>): List<String> {
        val mapper = jacksonObjectMapper()
        val jsonNodes = CvTestRespons.sourceMultipleCvLookup.map (mapper::readTree)
        jsonNodes.forEachIndexed { index, jsonNode ->
            if(veiledersIdent[index] == null) (jsonNode as ObjectNode).putNull("veilederIdent")
            else (jsonNode as ObjectNode).put("veilederIdent",veiledersIdent[index])
            if(kandidatensOrgnummer[index] == null) (jsonNode as ObjectNode).putNull("orgenhet")
            else jsonNode.put("orgenhet", kandidatensOrgnummer[index])
        }
        return jsonNodes.map ( mapper::writeValueAsString )
    }


    private fun gjørKall(token: SignedJWT) = Fuel.post("http://localhost:8080/api/multiple-lookup-cv")
        .body("""{"kandidatnr": ["PAM0xtfrwli5","PAM0123456789","PAM0987654321"]}""")
        .header("Authorization", "Bearer ${token.serialize()}")
        .responseObject<JsonNode>()

}
