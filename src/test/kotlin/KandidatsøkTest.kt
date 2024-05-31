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
import org.skyscreamer.jsonassert.JSONAssert
import java.util.*

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@WireMockTest(httpPort = 10000)
class KandidatsøkTest {
    private val authPort = 18306

    private val modiaGenerell = UUID.randomUUID().toString()
    private val modiaOppfølging = UUID.randomUUID().toString()

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
    fun `Kan søke kandidater`(wmRuntimeInfo: WireMockRuntimeInfo) {
        val wireMock = wmRuntimeInfo.wireMock
        mockES(wireMock)
        val navIdent = "A123456"
        val token = lagToken(navIdent = navIdent)
        val (_, response, result) = Fuel.post("http://localhost:8080/api/kandidatsok")
            .body("""{}""")
            .header("Authorization", "Bearer ${token.serialize()}")
            .responseObject<JsonNode>()

        Assertions.assertThat(response.statusCode).isEqualTo(200)
        JSONAssert.assertEquals(KandidatsøkRespons.kandidatsøkRespons, result.get().toPrettyString(), false)

    }

    @Test
    fun `Skal bare ignorere ekstra data`(wmRuntimeInfo: WireMockRuntimeInfo) {
        val wireMock = wmRuntimeInfo.wireMock
        mockES(wireMock)
        val navIdent = "A123456"
        val token = lagToken(navIdent = navIdent)
        val (_, response, result) = Fuel.post("http://localhost:8080/api/kandidatsok")
            .body("""{"ukjentFelt":"skal ignoreres"}""")
            .header("Authorization", "Bearer ${token.serialize()}")
            .responseObject<JsonNode>()

        Assertions.assertThat(response.statusCode).isEqualTo(200)
        JSONAssert.assertEquals(KandidatsøkRespons.kandidatsøkRespons, result.get().toPrettyString(), false)
    }

    @Test
    fun `Kan søke kandidater med paginering`(wmRuntimeInfo: WireMockRuntimeInfo) {
        val wireMock = wmRuntimeInfo.wireMock
        mockES(wireMock, from = 75)
        val navIdent = "A123456"
        val token = lagToken(navIdent = navIdent)
        val (_, response, result) = Fuel.post("http://localhost:8080/api/kandidatsok?side=4")
            .body("""{}""")
            .header("Authorization", "Bearer ${token.serialize()}")
            .responseObject<JsonNode>()

        Assertions.assertThat(response.statusCode).isEqualTo(200)
        JSONAssert.assertEquals(KandidatsøkRespons.kandidatsøkRespons, result.get().toPrettyString(), false)
    }

    @Test
    fun `Kan søke kandidater med mange sider paginering`(wmRuntimeInfo: WireMockRuntimeInfo) {
        val wireMock = wmRuntimeInfo.wireMock
        mockES(wireMock, from = 10000)
        val navIdent = "A123456"
        val token = lagToken(navIdent = navIdent)
        val (_, response, result) = Fuel.post("http://localhost:8080/api/kandidatsok?side=401")
            .body("""{}""")
            .header("Authorization", "Bearer ${token.serialize()}")
            .responseObject<JsonNode>()

        Assertions.assertThat(response.statusCode).isEqualTo(200)
        JSONAssert.assertEquals(KandidatsøkRespons.kandidatsøkRespons, result.get().toPrettyString(), false)
    }

    @Test
    fun `Kan søke kandidater sortert på flest kriterier`(wmRuntimeInfo: WireMockRuntimeInfo) {
        val wireMock = wmRuntimeInfo.wireMock
        mockES(wireMock,sortering = false)
        val navIdent = "A123456"
        val token = lagToken(navIdent = navIdent)
        val (_, response, result) = Fuel.post("http://localhost:8080/api/kandidatsok?sortering=score")
            .body("""{}""")
            .header("Authorization", "Bearer ${token.serialize()}")
            .responseObject<JsonNode>()

        Assertions.assertThat(response.statusCode).isEqualTo(200)
        JSONAssert.assertEquals(KandidatsøkRespons.kandidatsøkRespons, result.get().toPrettyString(), false)
    }

    @Test
    fun `Må ha token`() {
        val (_, response, _) = Fuel.post("http://localhost:8080/api/kandidatsok")
            .body("""{}""")
            .responseObject<JsonNode>()

        Assertions.assertThat(response.statusCode).isEqualTo(401)
    }

    @Test
    fun `Må ha gyldig token`() {
        val token = lagToken(issuerId = "falskissuer")
        val (_, response, _) = Fuel.post("http://localhost:8080/api/kandidatsok")
            .body("""{}""")
            .header("Authorization", "Bearer ${token.serialize()}")
            .responseObject<JsonNode>()

        Assertions.assertThat(response.statusCode).isEqualTo(401)
    }

    @Test
    fun `Må ha navIdent`() {
        val token = lagToken(claims = mapOf("groups" to listOf(modiaGenerell)))
        val (_, response, _) = Fuel.post("http://localhost:8080/api/kandidatsok")
            .body("""{}""")
            .header("Authorization", "Bearer ${token.serialize()}")
            .responseObject<JsonNode>()

        Assertions.assertThat(response.statusCode).isEqualTo(401)
    }

    @Test
    fun `Må ha gruppe-tilhørighet`() {
        val token = lagToken(claims = mapOf("NAVident" to "A123456"))
        val (_, response, _) = Fuel.post("http://localhost:8080/api/kandidatsok")
            .body("""{}""")
            .header("Authorization", "Bearer ${token.serialize()}")
            .responseObject<JsonNode>()

        Assertions.assertThat(response.statusCode).isEqualTo(403)
    }

    @Test
    fun `Om kall feiler under henting av cv fra elasticsearch, får vi HTTP 500`(wmRuntimeInfo: WireMockRuntimeInfo) {
        val wireMock = wmRuntimeInfo.wireMock
        wireMock.register(
            post("/veilederkandidat_current/_search?typed_keys=true")
                .withRequestBody(equalToJson(KandidatsøkRespons.query(), true, false))
                .willReturn(
                    notFound()
                )
        )
        val navIdent = "A123456"
        val token = lagToken(navIdent = navIdent)
        val (_, response, result) = Fuel.post("http://localhost:8080/api/kandidatsok")
            .body("""{}""")
            .header("Authorization", "Bearer ${token.serialize()}")
            .responseObject<JsonNode>()

        Assertions.assertThat(response.statusCode).isEqualTo(500)
    }

    @Test
    fun `Kan søke kandidater med stedsfilter`(wmRuntimeInfo: WireMockRuntimeInfo) {
        val wireMock = wmRuntimeInfo.wireMock
        mockES(wireMock,KandidatsøkRespons.stedTerm)
        val navIdent = "A123456"
        val token = lagToken(navIdent = navIdent)
        val (_, response, result) = Fuel.post("http://localhost:8080/api/kandidatsok")
            .body("""{"ønsketSted":["Bodø.NO18.1804","Kristiansund.NO50.5001","Akershus.NO02","Norge.NO"]}""")
            .header("Authorization", "Bearer ${token.serialize()}")
            .responseObject<JsonNode>()

        Assertions.assertThat(response.statusCode).isEqualTo(200)
        JSONAssert.assertEquals(KandidatsøkRespons.kandidatsøkRespons, result.get().toPrettyString(), false)
    }

    @Test
    fun `Kan søke kandidater som bor på sted`(wmRuntimeInfo: WireMockRuntimeInfo) {
        val wireMock = wmRuntimeInfo.wireMock
        mockES(wireMock,KandidatsøkRespons.stedMedMåBoPåTerm)
        val navIdent = "A123456"
        val token = lagToken(navIdent = navIdent)
        val (_, response, result) = Fuel.post("http://localhost:8080/api/kandidatsok")
            .body(
                """
                {
                    "ønsketSted":["Oslo.NO03.0301","Bergen.NO46.4601","Norge.NO","Møre og Romsdal.NO15"],
                    "borPåØnsketSted": true
                }""".trimIndent()
            )
            .header("Authorization", "Bearer ${token.serialize()}")
            .responseObject<JsonNode>()

        Assertions.assertThat(response.statusCode).isEqualTo(200)
        JSONAssert.assertEquals(KandidatsøkRespons.kandidatsøkRespons, result.get().toPrettyString(), false)
    }

    @Test
    fun `Kan søke kandidater med arbeidsønskefilter`(wmRuntimeInfo: WireMockRuntimeInfo) {
        val wireMock = wmRuntimeInfo.wireMock
        mockES(wireMock,KandidatsøkRespons.arbeidsønskeTerm)
        val navIdent = "A123456"
        val token = lagToken(navIdent = navIdent)
        val (_, response, result) = Fuel.post("http://localhost:8080/api/kandidatsok")
            .body("""{"ønsketYrke":["Sauegjeter","Saueklipper"]}""")
            .header("Authorization", "Bearer ${token.serialize()}")
            .responseObject<JsonNode>()

        Assertions.assertThat(response.statusCode).isEqualTo(200)
        JSONAssert.assertEquals(KandidatsøkRespons.kandidatsøkRespons, result.get().toPrettyString(), false)
    }

    @Test
    fun `Kan søke kandidater med innsatsgruppe`(wmRuntimeInfo: WireMockRuntimeInfo) {
        val wireMock = wmRuntimeInfo.wireMock
        mockES(wireMock,innsatsgruppeTerm = KandidatsøkRespons.innsatsgruppeTermMedBATTogBFORM)
        val navIdent = "A123456"
        val token = lagToken(navIdent = navIdent)
        val (_, response, result) = Fuel.post("http://localhost:8080/api/kandidatsok")
            .body("""{"innsatsgruppe":["BATT","BFORM"]}""")
            .header("Authorization", "Bearer ${token.serialize()}")
            .responseObject<JsonNode>()

        Assertions.assertThat(response.statusCode).isEqualTo(200)
        JSONAssert.assertEquals(KandidatsøkRespons.kandidatsøkRespons, result.get().toPrettyString(), false)
    }

    @Test
    fun `Kan søke kandidater med ANDRE innsatsgrupper`(wmRuntimeInfo: WireMockRuntimeInfo) {
        val wireMock = wmRuntimeInfo.wireMock
        mockES(wireMock, innsatsgruppeTerm = KandidatsøkRespons.innsatsgruppeTermMedANDRE)
        val navIdent = "A123456"
        val token = lagToken(navIdent = navIdent)
        val (_, response, result) = Fuel.post("http://localhost:8080/api/kandidatsok")
            .body("""{"innsatsgruppe":["ANDRE"]}""")
            .header("Authorization", "Bearer ${token.serialize()}")
            .responseObject<JsonNode>()

        Assertions.assertThat(response.statusCode).isEqualTo(200)
        JSONAssert.assertEquals(KandidatsøkRespons.kandidatsøkRespons, result.get().toPrettyString(), false)
    }

    @Test
    fun `Kan søke kandidater med alle innsatsgrupper`(wmRuntimeInfo: WireMockRuntimeInfo) {
        val wireMock = wmRuntimeInfo.wireMock
        mockES(wireMock,innsatsgruppeTerm = KandidatsøkRespons.innsatsgruppeTermMedAlle)
        val navIdent = "A123456"
        val token = lagToken(navIdent = navIdent)
        val (_, response, result) = Fuel.post("http://localhost:8080/api/kandidatsok")
            .body("""{"innsatsgruppe":["BATT","BFORM","IKVAL","VARIG","ANDRE"]}""")
            .header("Authorization", "Bearer ${token.serialize()}")
            .responseObject<JsonNode>()

        Assertions.assertThat(response.statusCode).isEqualTo(200)
        JSONAssert.assertEquals(KandidatsøkRespons.kandidatsøkRespons, result.get().toPrettyString(), false)
    }

    @Test
    fun `Søk med innsatsgruppe satt til tom liste skal defaulte til default innsatsgrupper`(wmRuntimeInfo: WireMockRuntimeInfo) {
        val wireMock = wmRuntimeInfo.wireMock
        mockES(wireMock)
        val navIdent = "A123456"
        val token = lagToken(navIdent = navIdent)
        val (_, response, result) = Fuel.post("http://localhost:8080/api/kandidatsok")
            .body("""{"innsatsgruppe":[]}""")
            .header("Authorization", "Bearer ${token.serialize()}")
            .responseObject<JsonNode>()

        Assertions.assertThat(response.statusCode).isEqualTo(200)
        JSONAssert.assertEquals(KandidatsøkRespons.kandidatsøkRespons, result.get().toPrettyString(), false)
    }

    @Test
    fun `Kan søke kandidater med målform`(wmRuntimeInfo: WireMockRuntimeInfo) {
        val wireMock = wmRuntimeInfo.wireMock
        mockES(wireMock,KandidatsøkRespons.språkTerm)
        val navIdent = "A123456"
        val token = lagToken(navIdent = navIdent)
        val (_, response, result) = Fuel.post("http://localhost:8080/api/kandidatsok")
            .body("""{"språk":["Nynorsk","Norsk"]}""")
            .header("Authorization", "Bearer ${token.serialize()}")
            .responseObject<JsonNode>()

        Assertions.assertThat(response.statusCode).isEqualTo(200)
        JSONAssert.assertEquals(KandidatsøkRespons.kandidatsøkRespons, result.get().toPrettyString(), false)
    }

    @Test
    fun `Kan søke kandidater med arbeidserfaring`(wmRuntimeInfo: WireMockRuntimeInfo) {
        val wireMock = wmRuntimeInfo.wireMock
        mockES(wireMock, KandidatsøkRespons.arbeidsErfaringTerm)
        val navIdent = "A123456"
        val token = lagToken(navIdent = navIdent)
        val (_, response, result) = Fuel.post("http://localhost:8080/api/kandidatsok")
            .body("""{"arbeidserfaring":["Barnehageassistent","Butikkansvarlig"]}""")
            .header("Authorization", "Bearer ${token.serialize()}")
            .responseObject<JsonNode>()

        Assertions.assertThat(response.statusCode).isEqualTo(200)
        JSONAssert.assertEquals(KandidatsøkRespons.kandidatsøkRespons, result.get().toPrettyString(), false)
    }

    @Test
    fun `Kan søke kandidater med nylig arbeidserfaring`(wmRuntimeInfo: WireMockRuntimeInfo) {
        val wireMock = wmRuntimeInfo.wireMock
        mockES(wireMock, KandidatsøkRespons.nyligArbeidsErfaringTerm)
        val navIdent = "A123456"
        val token = lagToken(navIdent = navIdent)
        val (_, response, result) = Fuel.post("http://localhost:8080/api/kandidatsok")
            .body("""{"arbeidserfaring":["Hvalfanger","Kokk"],"ferskhet":2}""".trimMargin())
            .header("Authorization", "Bearer ${token.serialize()}")
            .responseObject<JsonNode>()

        Assertions.assertThat(response.statusCode).isEqualTo(200)
        JSONAssert.assertEquals(KandidatsøkRespons.kandidatsøkRespons, result.get().toPrettyString(), false)
    }

    @Test
    fun `Kan søke kandidater med hovedmål`(wmRuntimeInfo: WireMockRuntimeInfo) {
        val wireMock = wmRuntimeInfo.wireMock
        mockES(wireMock,KandidatsøkRespons.hovedmålTerm)
        val navIdent = "A123456"
        val token = lagToken(navIdent = navIdent)
        val (_, response, result) = Fuel.post("http://localhost:8080/api/kandidatsok")
            .body("""{"hovedmål":["SKAFFEA","OKEDELT"]}""")
            .header("Authorization", "Bearer ${token.serialize()}")
            .responseObject<JsonNode>()

        Assertions.assertThat(response.statusCode).isEqualTo(200)
        JSONAssert.assertEquals(KandidatsøkRespons.kandidatsøkRespons, result.get().toPrettyString(), false)
    }

    @Test
    fun `Kan søke kandidater med kompetanse`(wmRuntimeInfo: WireMockRuntimeInfo) {
        val wireMock = wmRuntimeInfo.wireMock
        mockES(wireMock, KandidatsøkRespons.kompetanseTerm)
        val navIdent = "A123456"
        val token = lagToken(navIdent = navIdent)
        val (_, response, result) = Fuel.post("http://localhost:8080/api/kandidatsok")
            .body("""{"kompetanse":["Fagbrev FU-operatør","Kotlin"]}""")
            .header("Authorization", "Bearer ${token.serialize()}")
            .responseObject<JsonNode>()

        Assertions.assertThat(response.statusCode).isEqualTo(200)
        JSONAssert.assertEquals(KandidatsøkRespons.kandidatsøkRespons, result.get().toPrettyString(), false)
    }

    @Test
    fun `Kan søke kandidater med førerkort`(wmRuntimeInfo: WireMockRuntimeInfo) {
        val wireMock = wmRuntimeInfo.wireMock
        mockES(wireMock, KandidatsøkRespons.førerkortTerm)
        val navIdent = "A123456"
        val token = lagToken(navIdent = navIdent)
        val (_, response, result) = Fuel.post("http://localhost:8080/api/kandidatsok")
            .body("""{"førerkort":["D - Buss","BE - Personbil med tilhenger"]}""")
            .header("Authorization", "Bearer ${token.serialize()}")
            .responseObject<JsonNode>()

        Assertions.assertThat(response.statusCode).isEqualTo(200)
        JSONAssert.assertEquals(KandidatsøkRespons.kandidatsøkRespons, result.get().toPrettyString(), false)
    }

    @Test
    fun `Kan søke kandidater med utdanning`(wmRuntimeInfo: WireMockRuntimeInfo) {
        val wireMock = wmRuntimeInfo.wireMock
        mockES(wireMock,KandidatsøkRespons.utdanningTerm)
        val navIdent = "A123456"
        val token = lagToken(navIdent = navIdent)
        val (_, response, result) = Fuel.post("http://localhost:8080/api/kandidatsok")
            .body("""{"utdanningsnivå":["videregaende","bachelor","doktorgrad"]}""")
            .header("Authorization", "Bearer ${token.serialize()}")
            .responseObject<JsonNode>()

        Assertions.assertThat(response.statusCode).isEqualTo(200)
        JSONAssert.assertEquals(KandidatsøkRespons.kandidatsøkRespons, result.get().toPrettyString(), false)
    }

    @Test
    fun `Kan søke kandidater med prioriterte målgrupper`(wmRuntimeInfo: WireMockRuntimeInfo) {
        val wireMock = wmRuntimeInfo.wireMock
        mockES(wireMock, KandidatsøkRespons.prioriterteMålgrupperTerm)
        val navIdent = "A123456"
        val token = lagToken(navIdent = navIdent)
        val (_, response, result) = Fuel.post("http://localhost:8080/api/kandidatsok")
            .body("""{"prioritertMålgruppe":["senior","unge","hullICv"]}""")
            .header("Authorization", "Bearer ${token.serialize()}")
            .responseObject<JsonNode>()

        Assertions.assertThat(response.statusCode).isEqualTo(200)
        JSONAssert.assertEquals(KandidatsøkRespons.kandidatsøkRespons, result.get().toPrettyString(), false)
    }

    @Test
    fun `Kan søke kandidat med ident`(wmRuntimeInfo: WireMockRuntimeInfo) {
        val wireMock = wmRuntimeInfo.wireMock
        mockES(wireMock, KandidatsøkRespons.queryMedIdentTerm)
        val navIdent = "A123456"
        val token = lagToken(navIdent = navIdent)
        val (_, response, result) = Fuel.post("http://localhost:8080/api/kandidatsok")
            .body("""{"fritekst":"12345678910"}""")
            .header("Authorization", "Bearer ${token.serialize()}")
            .responseObject<JsonNode>()

        Assertions.assertThat(response.statusCode).isEqualTo(200)
        JSONAssert.assertEquals(KandidatsøkRespons.kandidatsøkRespons, result.get().toPrettyString(), false)
    }

    @Test
    fun `Kan søke kandidat med PAMkandidatnr`(wmRuntimeInfo: WireMockRuntimeInfo) {
        val wireMock = wmRuntimeInfo.wireMock
        mockES(wireMock,KandidatsøkRespons.queryMedPAMKandidatnrTerm)
        val navIdent = "A123456"
        val token = lagToken(navIdent = navIdent)
        val (_, response, result) = Fuel.post("http://localhost:8080/api/kandidatsok")
            .body("""{"fritekst":"PAM01Z"}""")
            .header("Authorization", "Bearer ${token.serialize()}")
            .responseObject<JsonNode>()

        Assertions.assertThat(response.statusCode).isEqualTo(200)
        JSONAssert.assertEquals(KandidatsøkRespons.kandidatsøkRespons, result.get().toPrettyString(), false)
    }

    @Test
    fun `Kan søke kandidat med arenakandidatnr`(wmRuntimeInfo: WireMockRuntimeInfo) {
        val wireMock = wmRuntimeInfo.wireMock
        mockES(wireMock, KandidatsøkRespons.queryMedArenaKandidatnrTerm)
        val navIdent = "A123456"
        val token = lagToken(navIdent = navIdent)
        val (_, response, result) = Fuel.post("http://localhost:8080/api/kandidatsok")
            .body("""{"fritekst":"ab123"}""")
            .header("Authorization", "Bearer ${token.serialize()}")
            .responseObject<JsonNode>()

        Assertions.assertThat(response.statusCode).isEqualTo(200)
        JSONAssert.assertEquals(KandidatsøkRespons.kandidatsøkRespons, result.get().toPrettyString(), false)
    }

    @Test
    fun `Kan søke mine kandidater gammelt endepunkt SKAL SLETTES`(wmRuntimeInfo: WireMockRuntimeInfo) {
        val wireMock = wmRuntimeInfo.wireMock
        mockES(wireMock, KandidatsøkRespons.mineBrukereTerm)
        val navIdent = "A123456"
        val token = lagToken(navIdent = navIdent)
        val (_, response, result) = Fuel.post("http://localhost:8080/api/kandidatsok")
            .body("""{"portefølje":"mine"}""")
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
    fun `Kan søke på kontor gammelt endepunkt SKAL SLETTES`(wmRuntimeInfo: WireMockRuntimeInfo) {
        val wireMock = wmRuntimeInfo.wireMock
        mockES(wireMock, KandidatsøkRespons.valgtKontorTerm)
        val navIdent = "A123456"
        val token = lagToken(navIdent = navIdent)
        val (_, response, result) = Fuel.post("http://localhost:8080/api/kandidatsok")
            .body("""{"portefølje":"valgte","valgtKontor":["NAV Hamar","NAV Lofoten"]}""")
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
    fun `Kan søke på mitt kontor gammelt endepunkt SKAL SLETTES`(wmRuntimeInfo: WireMockRuntimeInfo) {
        val wireMock = wmRuntimeInfo.wireMock
        mockES(wireMock,KandidatsøkRespons.mittKontorTerm)
        val navIdent = "A123456"
        val token = lagToken(navIdent = navIdent)
        val (_, response, result) = Fuel.post("http://localhost:8080/api/kandidatsok")
            .body("""{"portefølje":"kontor","orgenhet":"1234"}""")
            .header("Authorization", "Bearer ${token.serialize()}")
            .responseObject<JsonNode>()

        Assertions.assertThat(response.statusCode).isEqualTo(200)
        JSONAssert.assertEquals(KandidatsøkRespons.kandidatsøkRespons, result.get().toPrettyString(), false)
    }

    @Test
    fun `Søk på kontor uten valgt kontor satt fører til spørring med tom streng gammelt endepunkt SKAL SLETTES`(wmRuntimeInfo: WireMockRuntimeInfo) {
        val wireMock = wmRuntimeInfo.wireMock
        mockES(wireMock, KandidatsøkRespons.mittKontorUtenValgtTerm)
        val navIdent = "A123456"
        val token = lagToken(navIdent = navIdent)
        val (_, response, result) = Fuel.post("http://localhost:8080/api/kandidatsok")
            .body("""{"portefølje":"kontor"}""")
            .header("Authorization", "Bearer ${token.serialize()}")
            .responseObject<JsonNode>()

        Assertions.assertThat(response.statusCode).isEqualTo(200)
        JSONAssert.assertEquals(KandidatsøkRespons.kandidatsøkRespons, result.get().toPrettyString(), false)
    }

    @Test
    fun `Kan søke på mitt kontor`(wmRuntimeInfo: WireMockRuntimeInfo) {
        val wireMock = wmRuntimeInfo.wireMock
        mockES(wireMock,KandidatsøkRespons.mittKontorTerm)
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
    fun `Kan søke på mine kontor gammelt endepunkt SKAL SLETTES`(wmRuntimeInfo: WireMockRuntimeInfo) {
        val wireMock = wmRuntimeInfo.wireMock
        mockES(wireMock, KandidatsøkRespons.mineKontorerTerm)

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


        val navIdent = "A123456"
        val token = lagToken(navIdent = navIdent)
        val (_, response, result) = Fuel.post("http://localhost:8080/api/kandidatsok")
            .body("""{"portefølje":"mineKontorer"}""")
            .header("Authorization", "Bearer ${token.serialize()}")
            .responseObject<JsonNode>()

        Assertions.assertThat(response.statusCode).isEqualTo(200)
        JSONAssert.assertEquals(KandidatsøkRespons.kandidatsøkRespons, result.get().toPrettyString(), false)
    }

    @Test
    fun `Får 401 dersom man søker på mine kontorer uten å ha kontor gammelt endepunkt SKAL SLETTES`(wmRuntimeInfo: WireMockRuntimeInfo) {
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
        val (_, response, result) = Fuel.post("http://localhost:8080/api/kandidatsok")
            .body("""{"portefølje":"mineKontorer"}""")
            .header("Authorization", "Bearer ${token.serialize()}")
            .responseObject<JsonNode>()

        Assertions.assertThat(response.statusCode).isEqualTo(401)
    }

    @Test
    fun `Kan søke på mine kontor`(wmRuntimeInfo: WireMockRuntimeInfo) {
        val wireMock = wmRuntimeInfo.wireMock
        mockES(wireMock, KandidatsøkRespons.mineKontorerTerm)

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

    @Test
    fun `Kan søke kandidat med fritekstsøk`(wmRuntimeInfo: WireMockRuntimeInfo) {
        val wireMock = wmRuntimeInfo.wireMock
        mockES(wireMock,KandidatsøkRespons.queryMedKMultiMatchTerm)
        val navIdent = "A123456"
        val token = lagToken(navIdent = navIdent)
        val (_, response, result) = Fuel.post("http://localhost:8080/api/kandidatsok")
            .body("""{"fritekst":"søkeord"}""")
            .header("Authorization", "Bearer ${token.serialize()}")
            .responseObject<JsonNode>()

        Assertions.assertThat(response.statusCode).isEqualTo(200)
        JSONAssert.assertEquals(KandidatsøkRespons.kandidatsøkRespons, result.get().toPrettyString(), false)
    }

    @Test
    fun `Kan søke kandidat med alle filtre på en gang`(wmRuntimeInfo: WireMockRuntimeInfo) {
        val wireMock = wmRuntimeInfo.wireMock
        mockES(wireMock,
            KandidatsøkRespons.stedTerm,
            KandidatsøkRespons.arbeidsønskeTerm,
            KandidatsøkRespons.queryMedKMultiMatchTerm,
            KandidatsøkRespons.utdanningTerm,
            KandidatsøkRespons.prioriterteMålgrupperTerm,
            KandidatsøkRespons.nyligArbeidsErfaringTerm,
            KandidatsøkRespons.hovedmålTerm,
            KandidatsøkRespons.kompetanseTerm,
            KandidatsøkRespons.førerkortTerm,
            KandidatsøkRespons.språkTerm,
            innsatsgruppeTerm = KandidatsøkRespons.innsatsgruppeTermMedBATTogBFORM
        )
        val navIdent = "A123456"
        val token = lagToken(navIdent = navIdent)
        val (_, response, result) = Fuel.post("http://localhost:8080/api/kandidatsok")
            .body(
                """
                {
                    "fritekst":"søkeord",
                    "ønsketSted":["Bodø.NO18.1804","Kristiansund.NO50.5001","Akershus.NO02","Norge.NO"],
                    "ønsketYrke":["Sauegjeter", "Saueklipper"],
                    "innsatsgruppe":["BATT","BFORM"],
                    "språk":["Nynorsk","Norsk"],
                    "arbeidserfaring":["Hvalfanger","Kokk"],"ferskhet":2,
                    "hovedmål":["SKAFFEA","OKEDELT"],
                    "kompetanse":["Fagbrev FU-operatør","Kotlin"],
                    "førerkort":["D - Buss","BE - Personbil med tilhenger"],
                    "utdanningsnivå":["videregaende","bachelor","doktorgrad"],
                    "prioritertMålgruppe":["senior","unge","hullICv"],
                    "fritekst":"søkeord"
                }""".trimIndent()
            )
            .header("Authorization", "Bearer ${token.serialize()}")
            .responseObject<JsonNode>()

        Assertions.assertThat(response.statusCode).isEqualTo(200)
        JSONAssert.assertEquals(KandidatsøkRespons.kandidatsøkRespons, result.get().toPrettyString(), false)
    }

    @Test
    fun `krever token for å søke kandidatnumre for navigering`() {
        val (_, response, result) = Fuel.post("http://localhost:8080/api/kandidatsok/navigering?side=11")
            .body("{}")
            .responseObject<JsonNode>()

        Assertions.assertThat(response.statusCode).isEqualTo(401)
    }

    @Test
    fun `krever gruppetilhørighet for å søke kandidatnumre for navigering`() {
        val navIdent = "A123456"
        val token = lagToken(claims = mapOf("NAVident" to navIdent))
        val (_, response, result) = Fuel.post("http://localhost:8080/api/kandidatsok/navigering?side=11")
            .body("{}")
            .header("Authorization", "Bearer ${token.serialize()}")
            .responseObject<JsonNode>()

        Assertions.assertThat(response.statusCode).isEqualTo(403)
    }

    private fun lagLokalApp() = App(
        port = 8080,
        authenticationConfigurations = listOf(
            AuthenticationConfiguration(
                audience = "1",
                issuer = "http://localhost:$authPort/default",
                jwksUri = "http://localhost:$authPort/default/jwks",
            )
        ),
        rolleUuidSpesifikasjon = RolleUuidSpesifikasjon(
            modiaGenerell = UUID.fromString(modiaGenerell),
            modiaOppfølging = UUID.fromString(modiaOppfølging),
        ),
        openSearchUsername = "user",
        openSearchPassword = "pass",
        openSearchUri = "http://localhost:10000",
        pdlUrl = "http://localhost:10000/pdl",
        azureSecret = "secret",
        azureClientId = "1",
        azureUrl = "http://localhost:$authPort/rest/isso/oauth2/access_token",
        pdlScope = "http://localhost/.default",
        modiaContextHolderScope = "http://localhost/.default",
        modiaContextHolderUrl = "http://localhost:10000/modia"
    )

    private fun mockES(wireMock: WireMock, vararg extraTerms: String, sortering: Boolean = true, innsatsgruppeTerm: String = """{"terms":{"kvalifiseringsgruppekode":["BATT","BFORM","IKVAL","VARIG"]}}""", from: Int = 0) {
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
                        KandidatsøkRespons.navigeringQuery(extraTerms = extraTerms, sortering, innsatsgruppeTerm, kotlin.math.max(from - 225, 0)),
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