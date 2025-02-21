package no.nav.toi.kandidatsøk

import com.fasterxml.jackson.databind.JsonNode
import com.github.kittinunf.fuel.Fuel
import com.github.kittinunf.fuel.jackson.responseObject
import com.github.tomakehurst.wiremock.client.WireMock
import com.github.tomakehurst.wiremock.client.WireMock.*
import com.github.tomakehurst.wiremock.junit5.WireMockRuntimeInfo
import com.github.tomakehurst.wiremock.junit5.WireMockTest
import no.nav.security.mock.oauth2.MockOAuth2Server
import no.nav.toi.App
import no.nav.toi.AuthenticationConfiguration
import no.nav.toi.RolleUuidSpesifikasjon
import org.assertj.core.api.Assertions
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import org.skyscreamer.jsonassert.JSONAssert
import java.util.*
import java.util.stream.Stream

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@WireMockTest(httpPort = 10000)
class KandidatsøkTest {
    private val authPort = 18306

    companion object {
        private val modiaGenerell = UUID.randomUUID().toString()
        private val jobbsøkerrettet = UUID.randomUUID().toString()
        private val arbeidsgiverrettet = UUID.randomUUID().toString()
        private val utvikler = UUID.randomUUID().toString()

        private val audience = "iden til applikasjonen"
    }

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

    @ParameterizedTest
    @MethodSource("endepunktSomParameter")
    fun `Kan søke kandidater`(endepunkt: Endepunkt, wmRuntimeInfo: WireMockRuntimeInfo) {
        val wireMock = wmRuntimeInfo.wireMock
        mockES(wireMock, extraTerms = endepunkt.extraTerms)
        endepunkt.ekstraMocking(wireMock)
        val navIdent = "A123456"
        val token = lagToken(navIdent = navIdent)
        val (_, response, result) = Fuel.post("http://localhost:8080/api/kandidatsok/${endepunkt.path}")
            .body("""{${endepunkt.bodyParameter(false)}}""")
            .header("Authorization", "Bearer ${token.serialize()}")
            .responseObject<JsonNode>()

        Assertions.assertThat(response.statusCode).isEqualTo(200)
        JSONAssert.assertEquals(KandidatsøkRespons.kandidatsøkRespons, result.get().toPrettyString(), false)

    }

    @ParameterizedTest
    @MethodSource("endepunktSomParameter")
    fun `Skal bare ignorere ekstra data`(endepunkt: Endepunkt, wmRuntimeInfo: WireMockRuntimeInfo) {
        val wireMock = wmRuntimeInfo.wireMock
        mockES(wireMock, extraTerms = endepunkt.extraTerms)
        endepunkt.ekstraMocking(wireMock)
        val navIdent = "A123456"
        val token = lagToken(navIdent = navIdent)
        val (_, response, result) = Fuel.post("http://localhost:8080/api/kandidatsok/${endepunkt.path}")
            .body("""{"ukjentFelt":"skal ignoreres"${endepunkt.bodyParameter(true)}}""")
            .header("Authorization", "Bearer ${token.serialize()}")
            .responseObject<JsonNode>()

        Assertions.assertThat(response.statusCode).isEqualTo(200)
        JSONAssert.assertEquals(KandidatsøkRespons.kandidatsøkRespons, result.get().toPrettyString(), false)
    }

    @ParameterizedTest
    @MethodSource("endepunktSomParameter")
    fun `Kan søke kandidater med paginering`(endepunkt: Endepunkt, wmRuntimeInfo: WireMockRuntimeInfo) {
        val wireMock = wmRuntimeInfo.wireMock
        mockES(wireMock, from = 75, extraTerms = endepunkt.extraTerms)
        endepunkt.ekstraMocking(wireMock)
        val navIdent = "A123456"
        val token = lagToken(navIdent = navIdent)
        val (_, response, result) = Fuel.post("http://localhost:8080/api/kandidatsok/${endepunkt.path}?side=4")
            .body("""{${endepunkt.bodyParameter(false)}}""")
            .header("Authorization", "Bearer ${token.serialize()}")
            .responseObject<JsonNode>()

        Assertions.assertThat(response.statusCode).isEqualTo(200)
        JSONAssert.assertEquals(KandidatsøkRespons.kandidatsøkRespons, result.get().toPrettyString(), false)
    }

    @ParameterizedTest
    @MethodSource("endepunktSomParameter")
    fun `Kan søke kandidater med mange sider paginering`(endepunkt: Endepunkt, wmRuntimeInfo: WireMockRuntimeInfo) {
        val wireMock = wmRuntimeInfo.wireMock
        mockES(wireMock, from = 10000, extraTerms = endepunkt.extraTerms)
        endepunkt.ekstraMocking(wireMock)
        val navIdent = "A123456"
        val token = lagToken(navIdent = navIdent)
        val (_, response, result) = Fuel.post("http://localhost:8080/api/kandidatsok/${endepunkt.path}?side=401")
            .body("""{${endepunkt.bodyParameter(false)}}""")
            .header("Authorization", "Bearer ${token.serialize()}")
            .responseObject<JsonNode>()

        Assertions.assertThat(response.statusCode).isEqualTo(200)
        JSONAssert.assertEquals(KandidatsøkRespons.kandidatsøkRespons, result.get().toPrettyString(), false)
    }

    @ParameterizedTest
    @MethodSource("endepunktSomParameter")
    fun `Kan søke kandidater sortert på flest kriterier`(endepunkt: Endepunkt, wmRuntimeInfo: WireMockRuntimeInfo) {
        val wireMock = wmRuntimeInfo.wireMock
        mockES(wireMock, sortering = false, extraTerms = endepunkt.extraTerms)
        endepunkt.ekstraMocking(wireMock)
        val navIdent = "A123456"
        val token = lagToken(navIdent = navIdent)
        val (_, response, result) = Fuel.post("http://localhost:8080/api/kandidatsok/${endepunkt.path}?sortering=score")
            .body("""{${endepunkt.bodyParameter(false)}}""")
            .header("Authorization", "Bearer ${token.serialize()}")
            .responseObject<JsonNode>()

        Assertions.assertThat(response.statusCode).isEqualTo(200)
        JSONAssert.assertEquals(KandidatsøkRespons.kandidatsøkRespons, result.get().toPrettyString(), false)
    }

    @ParameterizedTest
    @MethodSource("endepunktSomParameter")
    fun `Må ha token`(endepunkt: Endepunkt) {
        val (_, response, _) = Fuel.post("http://localhost:8080/api/kandidatsok/${endepunkt.path}")
            .body("""{}""")
            .responseObject<JsonNode>()

        Assertions.assertThat(response.statusCode).isEqualTo(401)
    }

    @ParameterizedTest
    @MethodSource("endepunktSomParameter")
    fun `Må ha gyldig token`(endepunkt: Endepunkt) {
        val token = lagToken(issuerId = "falskissuer")
        val (_, response, _) = Fuel.post("http://localhost:8080/api/kandidatsok/${endepunkt.path}")
            .body("""{}""")
            .header("Authorization", "Bearer ${token.serialize()}")
            .responseObject<JsonNode>()

        Assertions.assertThat(response.statusCode).isEqualTo(401)
    }

    @ParameterizedTest
    @MethodSource("endepunktSomParameter")
    fun `Må ha navIdent`(endepunkt: Endepunkt) {
        val token = lagToken(claims = mapOf("groups" to listOf(modiaGenerell)))
        val (_, response, _) = Fuel.post("http://localhost:8080/api/kandidatsok/${endepunkt.path}")
            .body("""{}""")
            .header("Authorization", "Bearer ${token.serialize()}")
            .responseObject<JsonNode>()

        Assertions.assertThat(response.statusCode).isEqualTo(401)
    }

    enum class Tilgang(val uuid: String) {
        ModiaGenerell(modiaGenerell), Jobbsøkerrettet(jobbsøkerrettet), Arbeidsgiverrettet(arbeidsgiverrettet), Utvikler(
            utvikler
        );
    }

    enum class Endepunkt(
        val path: String,
        val extraTerms: Array<String> = emptyArray(),
        val bodyParameter: (Boolean) -> String = { "" },
        val ekstraMocking: (WireMock) -> Unit = {}
    ) {
        Alle("alle"),
        MineBrukere("minebrukere", arrayOf(KandidatsøkRespons.mineBrukereTerm)),
        ValgteKontorer("valgtekontorer", arrayOf(KandidatsøkRespons.valgtKontorTerm),
            { (if (it) "," else "") + """"valgtKontor":["NAV Hamar","NAV Lofoten"]""" }),
        MineKontorer("minekontorer", arrayOf(KandidatsøkRespons.mineKontorerTerm), ekstraMocking = ::mockDecorator),
        MittKontor(
            "mittkontor",
            arrayOf(KandidatsøkRespons.mittKontorTerm),
            { (if (it) "," else "") + """"orgenhet":"1234"""" });
    }

    fun endepunktSomParameter() = Endepunkt.entries.stream()

    fun tilgangParametre() = Stream.of(
        Arguments.of(Tilgang.ModiaGenerell, Endepunkt.Alle, 403),
        Arguments.of(Tilgang.ModiaGenerell, Endepunkt.MineBrukere, 403),
        Arguments.of(Tilgang.ModiaGenerell, Endepunkt.ValgteKontorer, 403),
        Arguments.of(Tilgang.ModiaGenerell, Endepunkt.MineKontorer, 403),
        Arguments.of(Tilgang.ModiaGenerell, Endepunkt.MittKontor, 403),
        Arguments.of(Tilgang.Jobbsøkerrettet, Endepunkt.Alle, 403),
        Arguments.of(Tilgang.Jobbsøkerrettet, Endepunkt.MineBrukere, 200),
        Arguments.of(Tilgang.Jobbsøkerrettet, Endepunkt.ValgteKontorer, 403),
        Arguments.of(Tilgang.Jobbsøkerrettet, Endepunkt.MineKontorer, 200),
        Arguments.of(Tilgang.Jobbsøkerrettet, Endepunkt.MittKontor, 200),
        Arguments.of(Tilgang.Arbeidsgiverrettet, Endepunkt.Alle, 200),
        Arguments.of(Tilgang.Arbeidsgiverrettet, Endepunkt.MineBrukere, 200),
        Arguments.of(Tilgang.Arbeidsgiverrettet, Endepunkt.ValgteKontorer, 200),
        Arguments.of(Tilgang.Arbeidsgiverrettet, Endepunkt.MineKontorer, 200),
        Arguments.of(Tilgang.Arbeidsgiverrettet, Endepunkt.MittKontor, 200),
        Arguments.of(Tilgang.Utvikler, Endepunkt.Alle, 200),
        Arguments.of(Tilgang.Utvikler, Endepunkt.MineBrukere, 200),
        Arguments.of(Tilgang.Utvikler, Endepunkt.ValgteKontorer, 200),
        Arguments.of(Tilgang.Utvikler, Endepunkt.MineKontorer, 200),
        Arguments.of(Tilgang.Utvikler, Endepunkt.MittKontor, 200),
    )

    @ParameterizedTest
    @MethodSource("tilgangParametre")
    fun `tilgang på endepunkt`(
        tilgang: Tilgang,
        endepunkt: Endepunkt,
        statusCode: Int,
        wmRuntimeInfo: WireMockRuntimeInfo
    ) {
        val wireMock = wmRuntimeInfo.wireMock
        mockES(wireMock, extraTerms = endepunkt.extraTerms)
        endepunkt.ekstraMocking(wireMock)
        val token = lagToken(navIdent = "A123456", groups = listOf(tilgang.uuid))
        val (_, response, _) = Fuel.post("http://localhost:8080/api/kandidatsok/${endepunkt.path}")
            .body("""{${endepunkt.bodyParameter(false)}}""")
            .header("Authorization", "Bearer ${token.serialize()}")
            .responseObject<JsonNode>()

        Assertions.assertThat(response.statusCode).isEqualTo(statusCode)
    }

    @ParameterizedTest
    @MethodSource("endepunktSomParameter")
    fun `om man ikke har gruppetilhørighet skal man ikke få gjort kandidatsøk`(
        endepunkt: Endepunkt,
        wmRuntimeInfo: WireMockRuntimeInfo
    ) {
        val token = lagToken(groups = emptyList())
        val (_, response, _) = Fuel.post("http://localhost:8080/api/kandidatsok/${endepunkt.path}")
            .body("""{}""")
            .header("Authorization", "Bearer ${token.serialize()}")
            .responseObject<JsonNode>()

        Assertions.assertThat(response.statusCode).isEqualTo(403)
    }

    @ParameterizedTest
    @MethodSource("endepunktSomParameter")
    fun `Om kall feiler under henting av cv fra elasticsearch, får vi HTTP 500`(
        endepunkt: Endepunkt,
        wmRuntimeInfo: WireMockRuntimeInfo
    ) {
        val wireMock = wmRuntimeInfo.wireMock
        wireMock.register(
            post("/veilederkandidat_current/_search?typed_keys=true")
                .withRequestBody(equalToJson(KandidatsøkRespons.query(), true, false))
                .willReturn(
                    notFound()
                )
        )
        endepunkt.ekstraMocking(wireMock)
        val navIdent = "A123456"
        val token = lagToken(navIdent = navIdent)
        val (_, response, result) = Fuel.post("http://localhost:8080/api/kandidatsok/${endepunkt.path}")
            .body("""{${endepunkt.bodyParameter(false)}}""")
            .header("Authorization", "Bearer ${token.serialize()}")
            .responseObject<JsonNode>()

        Assertions.assertThat(response.statusCode).isEqualTo(500)
    }

    @ParameterizedTest
    @MethodSource("endepunktSomParameter")
    fun `Kan søke kandidater med stedsfilter`(endepunkt: Endepunkt, wmRuntimeInfo: WireMockRuntimeInfo) {
        val wireMock = wmRuntimeInfo.wireMock
        mockES(wireMock, extraTerms = endepunkt.extraTerms + KandidatsøkRespons.stedTerm)
        endepunkt.ekstraMocking(wireMock)
        val navIdent = "A123456"
        val token = lagToken(navIdent = navIdent)
        val (_, response, result) = Fuel.post("http://localhost:8080/api/kandidatsok/${endepunkt.path}")
            .body(
                """{"ønsketSted":["Bodø.NO18.1804","Kristiansund.NO50.5001","Akershus.NO02","Norge.NO"]${
                    endepunkt.bodyParameter(
                        true
                    )
                }}"""
            )
            .header("Authorization", "Bearer ${token.serialize()}")
            .responseObject<JsonNode>()

        Assertions.assertThat(response.statusCode).isEqualTo(200)
        JSONAssert.assertEquals(KandidatsøkRespons.kandidatsøkRespons, result.get().toPrettyString(), false)
    }

    @ParameterizedTest
    @MethodSource("endepunktSomParameter")
    fun `Kan søke kandidater som bor på sted`(endepunkt: Endepunkt, wmRuntimeInfo: WireMockRuntimeInfo) {
        val wireMock = wmRuntimeInfo.wireMock
        mockES(wireMock, extraTerms = endepunkt.extraTerms + KandidatsøkRespons.stedMedMåBoPåTerm)
        endepunkt.ekstraMocking(wireMock)
        val navIdent = "A123456"
        val token = lagToken(navIdent = navIdent)
        val (_, response, result) = Fuel.post("http://localhost:8080/api/kandidatsok/${endepunkt.path}")
            .body(
                """
                {
                    "ønsketSted":["Oslo.NO03.0301","Bergen.NO46.4601","Norge.NO","Møre og Romsdal.NO15"],
                    "borPåØnsketSted": true
                    ${endepunkt.bodyParameter(true)}
                }""".trimIndent()
            )
            .header("Authorization", "Bearer ${token.serialize()}")
            .responseObject<JsonNode>()

        Assertions.assertThat(response.statusCode).isEqualTo(200)
        JSONAssert.assertEquals(KandidatsøkRespons.kandidatsøkRespons, result.get().toPrettyString(), false)
    }

    @ParameterizedTest
    @MethodSource("endepunktSomParameter")
    fun `Kan søke kandidater med arbeidsønskefilter`(endepunkt: Endepunkt, wmRuntimeInfo: WireMockRuntimeInfo) {
        val wireMock = wmRuntimeInfo.wireMock
        mockES(wireMock, extraTerms = endepunkt.extraTerms + KandidatsøkRespons.arbeidsønskeTerm)
        endepunkt.ekstraMocking(wireMock)
        val navIdent = "A123456"
        val token = lagToken(navIdent = navIdent)
        val (_, response, result) = Fuel.post("http://localhost:8080/api/kandidatsok/${endepunkt.path}")
            .body("""{"ønsketYrke":["Sauegjeter","Saueklipper"]${endepunkt.bodyParameter(true)}}""")
            .header("Authorization", "Bearer ${token.serialize()}")
            .responseObject<JsonNode>()

        Assertions.assertThat(response.statusCode).isEqualTo(200)
        JSONAssert.assertEquals(KandidatsøkRespons.kandidatsøkRespons, result.get().toPrettyString(), false)
    }

    @ParameterizedTest
    @MethodSource("endepunktSomParameter")
    fun `Kan søke kandidater med innsatsgruppe`(endepunkt: Endepunkt, wmRuntimeInfo: WireMockRuntimeInfo) {
        val wireMock = wmRuntimeInfo.wireMock
        mockES(
            wireMock,
            innsatsgruppeTerm = KandidatsøkRespons.innsatsgruppeTermMedSpesieltOgSituasjonsbestemtInnsats,
            extraTerms = endepunkt.extraTerms
        )
        endepunkt.ekstraMocking(wireMock)
        val navIdent = "A123456"
        val token = lagToken(navIdent = navIdent)
        val (_, response, result) = Fuel.post("http://localhost:8080/api/kandidatsok/${endepunkt.path}")
            .body("""{"innsatsgruppe":["SPESIELT_TILPASSET_INNSATS","SITUASJONSBESTEMT_INNSATS"]${endepunkt.bodyParameter(true)}}""")
            .header("Authorization", "Bearer ${token.serialize()}")
            .responseObject<JsonNode>()

        Assertions.assertThat(response.statusCode).isEqualTo(200)
        JSONAssert.assertEquals(KandidatsøkRespons.kandidatsøkRespons, result.get().toPrettyString(), false)
    }

    @ParameterizedTest
    @MethodSource("endepunktSomParameter")
    fun `Kan søke kandidater med GRADERT_VARIG_TILPASSET_INNSATS innsatsgruppe`(endepunkt: Endepunkt, wmRuntimeInfo: WireMockRuntimeInfo) {
        val wireMock = wmRuntimeInfo.wireMock
        mockES(
            wireMock,
            innsatsgruppeTerm = KandidatsøkRespons.innsatsgruppeTermMedGradertVarigTilpasset,
            extraTerms = endepunkt.extraTerms
        )
        endepunkt.ekstraMocking(wireMock)
        val navIdent = "A123456"
        val token = lagToken(navIdent = navIdent)
        val (_, response, result) = Fuel.post("http://localhost:8080/api/kandidatsok/${endepunkt.path}")
            .body("""{"innsatsgruppe":["GRADERT_VARIG_TILPASSET_INNSATS"]${endepunkt.bodyParameter(true)}}""")
            .header("Authorization", "Bearer ${token.serialize()}")
            .responseObject<JsonNode>()

        Assertions.assertThat(response.statusCode).isEqualTo(200)
        JSONAssert.assertEquals(KandidatsøkRespons.kandidatsøkRespons, result.get().toPrettyString(), false)
    }

    @ParameterizedTest
    @MethodSource("endepunktSomParameter")
    fun `Kan søke kandidater med HAR_IKKE_GJELDENDE_14A_VEDTAK innsatsgruppe`(endepunkt: Endepunkt, wmRuntimeInfo: WireMockRuntimeInfo) {
        val wireMock = wmRuntimeInfo.wireMock
        mockES(
            wireMock,
            innsatsgruppeTerm = KandidatsøkRespons.innsatsgruppeTermMedIkkeVurdert,
            extraTerms = endepunkt.extraTerms
        )
        endepunkt.ekstraMocking(wireMock)
        val navIdent = "A123456"
        val token = lagToken(navIdent = navIdent)
        val (_, response, result) = Fuel.post("http://localhost:8080/api/kandidatsok/${endepunkt.path}")
            .body("""{"innsatsgruppe":["HAR_IKKE_GJELDENDE_14A_VEDTAK"]${endepunkt.bodyParameter(true)}}""")
            .header("Authorization", "Bearer ${token.serialize()}")
            .responseObject<JsonNode>()

        Assertions.assertThat(response.statusCode).isEqualTo(200)
        JSONAssert.assertEquals(KandidatsøkRespons.kandidatsøkRespons, result.get().toPrettyString(), false)
    }

    @ParameterizedTest
    @MethodSource("endepunktSomParameter")
    fun `Kan søke kandidater med alle innsatsgrupper`(endepunkt: Endepunkt, wmRuntimeInfo: WireMockRuntimeInfo) {
        val wireMock = wmRuntimeInfo.wireMock
        mockES(
            wireMock,
            innsatsgruppeTerm = KandidatsøkRespons.innsatsgruppeTermMedAlle,
            extraTerms = endepunkt.extraTerms
        )
        endepunkt.ekstraMocking(wireMock)
        val navIdent = "A123456"
        val token = lagToken(navIdent = navIdent)
        val (_, response, result) = Fuel.post("http://localhost:8080/api/kandidatsok/${endepunkt.path}")
            .body("""{"innsatsgruppe":["SPESIELT_TILPASSET_INNSATS","SITUASJONSBESTEMT_INNSATS","STANDARD_INNSATS","VARIG_TILPASSET_INNSATS","HAR_IKKE_GJELDENDE_14A_VEDTAK", "GRADERT_VARIG_TILPASSET_INNSATS"]${endepunkt.bodyParameter(true)}}""")
            .header("Authorization", "Bearer ${token.serialize()}")
            .responseObject<JsonNode>()

        Assertions.assertThat(response.statusCode).isEqualTo(200)
        JSONAssert.assertEquals(KandidatsøkRespons.kandidatsøkRespons, result.get().toPrettyString(), false)
    }

    @ParameterizedTest
    @MethodSource("endepunktSomParameter")
    fun `Søk med innsatsgruppe satt til tom liste skal defaulte til default innsatsgrupper`(
        endepunkt: Endepunkt,
        wmRuntimeInfo: WireMockRuntimeInfo
    ) {
        val wireMock = wmRuntimeInfo.wireMock
        mockES(wireMock, extraTerms = endepunkt.extraTerms)
        endepunkt.ekstraMocking(wireMock)
        val navIdent = "A123456"
        val token = lagToken(navIdent = navIdent)
        val (_, response, result) = Fuel.post("http://localhost:8080/api/kandidatsok/${endepunkt.path}")
            .body("""{"innsatsgruppe":[]${endepunkt.bodyParameter(true)}}""")
            .header("Authorization", "Bearer ${token.serialize()}")
            .responseObject<JsonNode>()

        Assertions.assertThat(response.statusCode).isEqualTo(200)
        JSONAssert.assertEquals(KandidatsøkRespons.kandidatsøkRespons, result.get().toPrettyString(), false)
    }

    @ParameterizedTest
    @MethodSource("endepunktSomParameter")
    fun `Kan søke kandidater med målform`(endepunkt: Endepunkt, wmRuntimeInfo: WireMockRuntimeInfo) {
        val wireMock = wmRuntimeInfo.wireMock
        mockES(wireMock, extraTerms = endepunkt.extraTerms + KandidatsøkRespons.språkTerm)
        endepunkt.ekstraMocking(wireMock)
        val navIdent = "A123456"
        val token = lagToken(navIdent = navIdent)
        val (_, response, result) = Fuel.post("http://localhost:8080/api/kandidatsok/${endepunkt.path}")
            .body("""{"språk":["Nynorsk","Norsk"]${endepunkt.bodyParameter(true)}}""")
            .header("Authorization", "Bearer ${token.serialize()}")
            .responseObject<JsonNode>()

        Assertions.assertThat(response.statusCode).isEqualTo(200)
        JSONAssert.assertEquals(KandidatsøkRespons.kandidatsøkRespons, result.get().toPrettyString(), false)
    }

    @ParameterizedTest
    @MethodSource("endepunktSomParameter")
    fun `Kan søke kandidater med arbeidserfaring`(endepunkt: Endepunkt, wmRuntimeInfo: WireMockRuntimeInfo) {
        val wireMock = wmRuntimeInfo.wireMock
        mockES(wireMock, extraTerms = endepunkt.extraTerms + KandidatsøkRespons.arbeidsErfaringTerm)
        endepunkt.ekstraMocking(wireMock)
        val navIdent = "A123456"
        val token = lagToken(navIdent = navIdent)
        val (_, response, result) = Fuel.post("http://localhost:8080/api/kandidatsok/${endepunkt.path}")
            .body("""{"arbeidserfaring":["Barnehageassistent","Butikkansvarlig"]${endepunkt.bodyParameter(true)}}""")
            .header("Authorization", "Bearer ${token.serialize()}")
            .responseObject<JsonNode>()

        Assertions.assertThat(response.statusCode).isEqualTo(200)
        JSONAssert.assertEquals(KandidatsøkRespons.kandidatsøkRespons, result.get().toPrettyString(), false)
    }

    @ParameterizedTest
    @MethodSource("endepunktSomParameter")
    fun `Kan søke kandidater med nylig arbeidserfaring`(endepunkt: Endepunkt, wmRuntimeInfo: WireMockRuntimeInfo) {
        val wireMock = wmRuntimeInfo.wireMock
        mockES(wireMock, extraTerms = endepunkt.extraTerms + KandidatsøkRespons.nyligArbeidsErfaringTerm)
        endepunkt.ekstraMocking(wireMock)
        val navIdent = "A123456"
        val token = lagToken(navIdent = navIdent)
        val (_, response, result) = Fuel.post("http://localhost:8080/api/kandidatsok/${endepunkt.path}")
            .body("""{"arbeidserfaring":["Hvalfanger","Kokk"],"ferskhet":2${endepunkt.bodyParameter(true)}}""".trimMargin())
            .header("Authorization", "Bearer ${token.serialize()}")
            .responseObject<JsonNode>()

        Assertions.assertThat(response.statusCode).isEqualTo(200)
        JSONAssert.assertEquals(KandidatsøkRespons.kandidatsøkRespons, result.get().toPrettyString(), false)
    }

    @ParameterizedTest
    @MethodSource("endepunktSomParameter")
    fun `Kan søke kandidater med hovedmål`(endepunkt: Endepunkt, wmRuntimeInfo: WireMockRuntimeInfo) {
        val wireMock = wmRuntimeInfo.wireMock
        mockES(wireMock, extraTerms = endepunkt.extraTerms + KandidatsøkRespons.hovedmålTerm)
        endepunkt.ekstraMocking(wireMock)
        val navIdent = "A123456"
        val token = lagToken(navIdent = navIdent)
        val (_, response, result) = Fuel.post("http://localhost:8080/api/kandidatsok/${endepunkt.path}")
            .body("""{"hovedmål":["SKAFFE_ARBEID","OKE_DELTAKELSE"]${endepunkt.bodyParameter(true)}}""")
            .header("Authorization", "Bearer ${token.serialize()}")
            .responseObject<JsonNode>()

        Assertions.assertThat(response.statusCode).isEqualTo(200)
        JSONAssert.assertEquals(KandidatsøkRespons.kandidatsøkRespons, result.get().toPrettyString(), false)
    }

    @ParameterizedTest
    @MethodSource("endepunktSomParameter")
    fun `Kan søke kandidater med kompetanse`(endepunkt: Endepunkt, wmRuntimeInfo: WireMockRuntimeInfo) {
        val wireMock = wmRuntimeInfo.wireMock
        mockES(wireMock, extraTerms = endepunkt.extraTerms + KandidatsøkRespons.kompetanseTerm)
        endepunkt.ekstraMocking(wireMock)
        val navIdent = "A123456"
        val token = lagToken(navIdent = navIdent)
        val (_, response, result) = Fuel.post("http://localhost:8080/api/kandidatsok/${endepunkt.path}")
            .body("""{"kompetanse":["Fagbrev FU-operatør","Kotlin"]${endepunkt.bodyParameter(true)}}""")
            .header("Authorization", "Bearer ${token.serialize()}")
            .responseObject<JsonNode>()

        Assertions.assertThat(response.statusCode).isEqualTo(200)
        JSONAssert.assertEquals(KandidatsøkRespons.kandidatsøkRespons, result.get().toPrettyString(), false)
    }

    @ParameterizedTest
    @MethodSource("endepunktSomParameter")
    fun `Kan søke kandidater med førerkort`(endepunkt: Endepunkt, wmRuntimeInfo: WireMockRuntimeInfo) {
        val wireMock = wmRuntimeInfo.wireMock
        mockES(wireMock, extraTerms = endepunkt.extraTerms + KandidatsøkRespons.førerkortTerm)
        endepunkt.ekstraMocking(wireMock)
        val navIdent = "A123456"
        val token = lagToken(navIdent = navIdent)
        val (_, response, result) = Fuel.post("http://localhost:8080/api/kandidatsok/${endepunkt.path}")
            .body("""{"førerkort":["D - Buss","BE - Personbil med tilhenger"]${endepunkt.bodyParameter(true)}}""")
            .header("Authorization", "Bearer ${token.serialize()}")
            .responseObject<JsonNode>()

        Assertions.assertThat(response.statusCode).isEqualTo(200)
        JSONAssert.assertEquals(KandidatsøkRespons.kandidatsøkRespons, result.get().toPrettyString(), false)
    }

    @ParameterizedTest
    @MethodSource("endepunktSomParameter")
    fun `Kan søke kandidater med utdanning`(endepunkt: Endepunkt, wmRuntimeInfo: WireMockRuntimeInfo) {
        val wireMock = wmRuntimeInfo.wireMock
        mockES(wireMock, extraTerms = endepunkt.extraTerms + KandidatsøkRespons.utdanningTerm)
        endepunkt.ekstraMocking(wireMock)
        val navIdent = "A123456"
        val token = lagToken(navIdent = navIdent)
        val (_, response, result) = Fuel.post("http://localhost:8080/api/kandidatsok/${endepunkt.path}")
            .body("""{"utdanningsnivå":["videregaende","bachelor","doktorgrad"]${endepunkt.bodyParameter(true)}}""")
            .header("Authorization", "Bearer ${token.serialize()}")
            .responseObject<JsonNode>()

        Assertions.assertThat(response.statusCode).isEqualTo(200)
        JSONAssert.assertEquals(KandidatsøkRespons.kandidatsøkRespons, result.get().toPrettyString(), false)
    }

    @ParameterizedTest
    @MethodSource("endepunktSomParameter")
    fun `Kan søke kandidater med prioriterte målgrupper`(endepunkt: Endepunkt, wmRuntimeInfo: WireMockRuntimeInfo) {
        val wireMock = wmRuntimeInfo.wireMock
        mockES(wireMock, extraTerms = endepunkt.extraTerms + KandidatsøkRespons.prioriterteMålgrupperTerm)
        endepunkt.ekstraMocking(wireMock)
        val navIdent = "A123456"
        val token = lagToken(navIdent = navIdent)
        val (_, response, result) = Fuel.post("http://localhost:8080/api/kandidatsok/${endepunkt.path}")
            .body("""{"prioritertMålgruppe":["senior","unge","hullICv"]${endepunkt.bodyParameter(true)}}""")
            .header("Authorization", "Bearer ${token.serialize()}")
            .responseObject<JsonNode>()

        Assertions.assertThat(response.statusCode).isEqualTo(200)
        JSONAssert.assertEquals(KandidatsøkRespons.kandidatsøkRespons, result.get().toPrettyString(), false)
    }

    @ParameterizedTest
    @MethodSource("endepunktSomParameter")
    fun `Kan søke kandidat med ident`(endepunkt: Endepunkt, wmRuntimeInfo: WireMockRuntimeInfo) {
        val wireMock = wmRuntimeInfo.wireMock
        mockES(wireMock, extraTerms = endepunkt.extraTerms + KandidatsøkRespons.queryMedIdentTerm)
        endepunkt.ekstraMocking(wireMock)
        val navIdent = "A123456"
        val token = lagToken(navIdent = navIdent)
        val (_, response, result) = Fuel.post("http://localhost:8080/api/kandidatsok/${endepunkt.path}")
            .body("""{"fritekst":"12345678910"${endepunkt.bodyParameter(true)}}""")
            .header("Authorization", "Bearer ${token.serialize()}")
            .responseObject<JsonNode>()

        Assertions.assertThat(response.statusCode).isEqualTo(200)
        JSONAssert.assertEquals(KandidatsøkRespons.kandidatsøkRespons, result.get().toPrettyString(), false)
    }

    @ParameterizedTest
    @MethodSource("endepunktSomParameter")
    fun `Kan søke kandidat med PAMkandidatnr`(endepunkt: Endepunkt, wmRuntimeInfo: WireMockRuntimeInfo) {
        val wireMock = wmRuntimeInfo.wireMock
        mockES(wireMock, extraTerms = endepunkt.extraTerms + KandidatsøkRespons.queryMedPAMKandidatnrTerm)
        endepunkt.ekstraMocking(wireMock)
        val navIdent = "A123456"
        val token = lagToken(navIdent = navIdent)
        val (_, response, result) = Fuel.post("http://localhost:8080/api/kandidatsok/${endepunkt.path}")
            .body("""{"fritekst":"PAM01Z"${endepunkt.bodyParameter(true)}}""")
            .header("Authorization", "Bearer ${token.serialize()}")
            .responseObject<JsonNode>()

        Assertions.assertThat(response.statusCode).isEqualTo(200)
        JSONAssert.assertEquals(KandidatsøkRespons.kandidatsøkRespons, result.get().toPrettyString(), false)
    }

    @ParameterizedTest
    @MethodSource("endepunktSomParameter")
    fun `Kan søke kandidat med arenakandidatnr`(endepunkt: Endepunkt, wmRuntimeInfo: WireMockRuntimeInfo) {
        val wireMock = wmRuntimeInfo.wireMock
        mockES(wireMock, extraTerms = endepunkt.extraTerms + KandidatsøkRespons.queryMedArenaKandidatnrTerm)
        endepunkt.ekstraMocking(wireMock)
        val navIdent = "A123456"
        val token = lagToken(navIdent = navIdent)
        val (_, response, result) = Fuel.post("http://localhost:8080/api/kandidatsok/${endepunkt.path}")
            .body("""{"fritekst":"ab123"${endepunkt.bodyParameter(true)}}""")
            .header("Authorization", "Bearer ${token.serialize()}")
            .responseObject<JsonNode>()

        Assertions.assertThat(response.statusCode).isEqualTo(200)
        JSONAssert.assertEquals(KandidatsøkRespons.kandidatsøkRespons, result.get().toPrettyString(), false)
    }

    @Test
    fun `Kan søke mine kandidater`(wmRuntimeInfo: WireMockRuntimeInfo) {
        val wireMock = wmRuntimeInfo.wireMock
        mockES(wireMock, KandidatsøkRespons.mineBrukereTerm)
        val navIdent = "A123456"
        val token = lagToken(navIdent = navIdent)
        val (_, response, result) = Fuel.post("http://localhost:8080/api/kandidatsok/minebrukere")
            .body("""{}""")
            .header("Authorization", "Bearer ${token.serialize()}")
            .responseObject<JsonNode>()

        Assertions.assertThat(response.statusCode).isEqualTo(200)
        JSONAssert.assertEquals(KandidatsøkRespons.kandidatsøkRespons, result.get().toPrettyString(), false)
    }

    @Test
    fun `Kan søke på kontor`(wmRuntimeInfo: WireMockRuntimeInfo) {
        val wireMock = wmRuntimeInfo.wireMock
        mockES(wireMock, KandidatsøkRespons.valgtKontorTerm)
        val navIdent = "A123456"
        val token = lagToken(navIdent = navIdent)
        val (_, response, result) = Fuel.post("http://localhost:8080/api/kandidatsok/valgtekontorer")
            .body("""{"valgtKontor":["NAV Hamar","NAV Lofoten"]}""")
            .header("Authorization", "Bearer ${token.serialize()}")
            .responseObject<JsonNode>()

        Assertions.assertThat(response.statusCode).isEqualTo(200)
        JSONAssert.assertEquals(KandidatsøkRespons.kandidatsøkRespons, result.get().toPrettyString(), false)
    }

    @Test
    fun `Kan søke på mitt kontor`(wmRuntimeInfo: WireMockRuntimeInfo) {
        val wireMock = wmRuntimeInfo.wireMock
        mockES(wireMock, KandidatsøkRespons.mittKontorTerm)
        val navIdent = "A123456"
        val token = lagToken(navIdent = navIdent)
        val (_, response, result) = Fuel.post("http://localhost:8080/api/kandidatsok/mittkontor")
            .body("""{"orgenhet":"1234"}""")
            .header("Authorization", "Bearer ${token.serialize()}")
            .responseObject<JsonNode>()

        Assertions.assertThat(response.statusCode).isEqualTo(200)
        JSONAssert.assertEquals(KandidatsøkRespons.kandidatsøkRespons, result.get().toPrettyString(), false)
    }

    @Test
    fun `Søk på kontor uten valgt kontor satt fører til spørring med tom streng`(wmRuntimeInfo: WireMockRuntimeInfo) {
        val wireMock = wmRuntimeInfo.wireMock
        mockES(wireMock, KandidatsøkRespons.mittKontorUtenValgtTerm)
        val navIdent = "A123456"
        val token = lagToken(navIdent = navIdent)
        val (_, response, result) = Fuel.post("http://localhost:8080/api/kandidatsok/mittkontor")
            .body("""{}""")
            .header("Authorization", "Bearer ${token.serialize()}")
            .responseObject<JsonNode>()

        Assertions.assertThat(response.statusCode).isEqualTo(200)
        JSONAssert.assertEquals(KandidatsøkRespons.kandidatsøkRespons, result.get().toPrettyString(), false)
    }

    @Test
    fun `Kan søke på mine kontor`(wmRuntimeInfo: WireMockRuntimeInfo) {
        val wireMock = wmRuntimeInfo.wireMock
        mockES(wireMock, KandidatsøkRespons.mineKontorerTerm)

        mockDecorator(wireMock)

        val navIdent = "A123456"
        val token = lagToken(navIdent = navIdent)
        val (_, response, result) = Fuel.post("http://localhost:8080/api/kandidatsok/minekontorer")
            .body("""{}""")
            .header("Authorization", "Bearer ${token.serialize()}")
            .responseObject<JsonNode>()

        Assertions.assertThat(response.statusCode).isEqualTo(200)
        JSONAssert.assertEquals(KandidatsøkRespons.kandidatsøkRespons, result.get().toPrettyString(), false)
    }

    @Test
    fun `Får 401 dersom man søker på mine kontorer uten å ha kontor`(wmRuntimeInfo: WireMockRuntimeInfo) {
        val wireMock = wmRuntimeInfo.wireMock

        wireMock.register(
            WireMock.get("/modia/api/decorator")
                .willReturn(
                    okJson(
                        """
                    {
                        "ident": "Z000000",
                        "navn": "Tull Tullersen",
                        "fornavn": "Tull",
                        "etternavn": "Tullersen",
                        "enheter": [
                        ]
                    }
                """.trimIndent()
                    )
                )
        )


        val navIdent = "A123456"
        val token = lagToken(navIdent = navIdent)
        val (_, response, result) = Fuel.post("http://localhost:8080/api/kandidatsok/minekontorer")
            .body("""{}""")
            .header("Authorization", "Bearer ${token.serialize()}")
            .responseObject<JsonNode>()

        Assertions.assertThat(response.statusCode).isEqualTo(401)
    }

    @ParameterizedTest
    @MethodSource("endepunktSomParameter")
    fun `Kan søke kandidat med fritekstsøk`(endepunkt: Endepunkt, wmRuntimeInfo: WireMockRuntimeInfo) {
        val wireMock = wmRuntimeInfo.wireMock
        mockES(wireMock, extraTerms = endepunkt.extraTerms + KandidatsøkRespons.queryMedKMultiMatchTerm)
        endepunkt.ekstraMocking(wireMock)
        val navIdent = "A123456"
        val token = lagToken(navIdent = navIdent)
        val (_, response, result) = Fuel.post("http://localhost:8080/api/kandidatsok/${endepunkt.path}")
            .body("""{"fritekst":"søkeord"${endepunkt.bodyParameter(true)}}""")
            .header("Authorization", "Bearer ${token.serialize()}")
            .responseObject<JsonNode>()

        Assertions.assertThat(response.statusCode).isEqualTo(200)
        JSONAssert.assertEquals(KandidatsøkRespons.kandidatsøkRespons, result.get().toPrettyString(), false)
    }

    @ParameterizedTest
    @MethodSource("endepunktSomParameter")
    fun `Søk kandidat med fritekstsøk som er tom string skal tolkes som om fritekst ikke ble sendt`(
        endepunkt: Endepunkt,
        wmRuntimeInfo: WireMockRuntimeInfo
    ) {
        val wireMock = wmRuntimeInfo.wireMock
        mockES(wireMock, extraTerms = endepunkt.extraTerms)
        endepunkt.ekstraMocking(wireMock)
        val navIdent = "A123456"
        val token = lagToken(navIdent = navIdent)
        val (_, response, result) = Fuel.post("http://localhost:8080/api/kandidatsok/${endepunkt.path}")
            .body("""{"fritekst":""${endepunkt.bodyParameter(true)}}""")
            .header("Authorization", "Bearer ${token.serialize()}")
            .responseObject<JsonNode>()

        Assertions.assertThat(response.statusCode).isEqualTo(200)
        JSONAssert.assertEquals(KandidatsøkRespons.kandidatsøkRespons, result.get().toPrettyString(), false)
    }

    @ParameterizedTest
    @MethodSource("endepunktSomParameter")
    fun `Kan søke kandidat med alle filtre på en gang`(endepunkt: Endepunkt, wmRuntimeInfo: WireMockRuntimeInfo) {
        val wireMock = wmRuntimeInfo.wireMock
        mockES(
            wireMock,
            extraTerms = endepunkt.extraTerms + listOf(
                KandidatsøkRespons.stedTerm,
                KandidatsøkRespons.arbeidsønskeTerm,
                KandidatsøkRespons.queryMedKMultiMatchTerm,
                KandidatsøkRespons.utdanningTerm,
                KandidatsøkRespons.prioriterteMålgrupperTerm,
                KandidatsøkRespons.nyligArbeidsErfaringTerm,
                KandidatsøkRespons.hovedmålTerm,
                KandidatsøkRespons.kompetanseTerm,
                KandidatsøkRespons.førerkortTerm,
                KandidatsøkRespons.språkTerm
            ),
            innsatsgruppeTerm = KandidatsøkRespons.innsatsgruppeTermMedSpesieltOgSituasjonsbestemtInnsats
        )
        endepunkt.ekstraMocking(wireMock)
        val navIdent = "A123456"
        val token = lagToken(navIdent = navIdent)
        val (_, response, result) = Fuel.post("http://localhost:8080/api/kandidatsok/${endepunkt.path}")
            .body(
                """
                {
                    "fritekst":"søkeord",
                    "ønsketSted":["Bodø.NO18.1804","Kristiansund.NO50.5001","Akershus.NO02","Norge.NO"],
                    "ønsketYrke":["Sauegjeter", "Saueklipper"],
                    "innsatsgruppe":["SPESIELT_TILPASSET_INNSATS","SITUASJONSBESTEMT_INNSATS"],
                    "språk":["Nynorsk","Norsk"],
                    "arbeidserfaring":["Hvalfanger","Kokk"],"ferskhet":2,
                    "hovedmål":["SKAFFE_ARBEID","OKE_DELTAKELSE"],
                    "kompetanse":["Fagbrev FU-operatør","Kotlin"],
                    "førerkort":["D - Buss","BE - Personbil med tilhenger"],
                    "utdanningsnivå":["videregaende","bachelor","doktorgrad"],
                    "prioritertMålgruppe":["senior","unge","hullICv"],
                    "fritekst":"søkeord"
                    ${endepunkt.bodyParameter(true)}
                }""".trimIndent()
            )
            .header("Authorization", "Bearer ${token.serialize()}")
            .responseObject<JsonNode>()

        Assertions.assertThat(response.statusCode).isEqualTo(200)
        JSONAssert.assertEquals(KandidatsøkRespons.kandidatsøkRespons, result.get().toPrettyString(), false)
    }

    private fun lagLokalApp() = App(
        port = 8080,
        authenticationConfigurations = listOf(
            AuthenticationConfiguration(
                audience = audience,
                issuer = "http://localhost:$authPort/default",
                jwksUri = "http://localhost:$authPort/default/jwks",
            )
        ),
        rolleUuidSpesifikasjon = RolleUuidSpesifikasjon(
            jobbsøkerrettet = UUID.fromString(jobbsøkerrettet),
            arbeidsgiverrettet = UUID.fromString(arbeidsgiverrettet),
            utvikler = UUID.fromString(utvikler)
        ),
        openSearchUsername = "user",
        openSearchPassword = "pass",
        openSearchUri = "http://localhost:10000",
        pdlUrl = "http://localhost:10000/pdl",
        azureSecret = "secret",
        azureClientId = audience,
        azureUrl = "http://localhost:$authPort/rest/isso/oauth2/access_token",
        pdlScope = "http://localhost/.default",
        modiaContextHolderScope = "http://localhost/.default",
        modiaContextHolderUrl = "http://localhost:10000/modia"
    )

    private fun mockES(
        wireMock: WireMock,
        vararg extraTerms: String,
        sortering: Boolean = true,
        innsatsgruppeTerm: String = """{"terms":{"innsatsgruppe.keyword":["SPESIELT_TILPASSET_INNSATS","SITUASJONSBESTEMT_INNSATS","STANDARD_INNSATS","VARIG_TILPASSET_INNSATS", "GRADERT_VARIG_TILPASSET_INNSATS"]}}""",
        from: Int = 0
    ) {
        wireMock.register(
            post("/veilederkandidat_current/_search?typed_keys=true")
                .withRequestBody(
                    equalToJson(
                        KandidatsøkRespons.query(extraTerms = extraTerms, sortering, innsatsgruppeTerm, from),
                        true,
                        false
                    )
                )
                .willReturn(
                    ok(KandidatsøkRespons.esKandidatsøkRespons)
                )
        )
        wireMock.register(
            post("/veilederkandidat_current/_search?typed_keys=true")
                .withRequestBody(
                    equalToJson(
                        KandidatsøkRespons.navigeringQuery(
                            extraTerms = extraTerms,
                            sortering,
                            innsatsgruppeTerm,
                            kotlin.math.max(from - 225, 0)
                        ),
                        true, false
                    )
                )
                .willReturn(
                    ok(KandidatsøkRespons.esKandidatsøkNavigeringRespons)
                )
        )
    }

    private fun lagToken(
        issuerId: String = "http://localhost:$authPort/default",
        aud: String = audience,
        navIdent: String = "A000001",
        groups: List<String> = listOf(arbeidsgiverrettet),
        claims: Map<String, Any> = mapOf("NAVident" to navIdent, "groups" to groups),
        expiry: Long = 3600
    ) = authServer.issueToken(
        issuerId = issuerId,
        subject = "subject",
        audience = aud,
        claims = claims,
        expiry = expiry
    )
}

private fun mockDecorator(wireMock: WireMock) {
    wireMock.register(
        get("/modia/api/decorator")
            .willReturn(
                okJson(
                    """
                        {
                            "ident": "Z000000",
                            "navn": "Tull Tullersen",
                            "fornavn": "Tull",
                            "etternavn": "Tullersen",
                            "enheter": [
                                {
                                    "enhetId": "0403",
                                    "navn": "NAV Hamar"
                                },
                                {
                                    "enhetId": "1001",
                                    "navn": "NAV Kristiansand"
                                }
                            ]
                        }
                    """.trimIndent()
                )
            )
    )
}
