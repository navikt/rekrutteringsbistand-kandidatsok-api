import com.fasterxml.jackson.databind.JsonNode
import com.github.kittinunf.fuel.Fuel
import com.github.kittinunf.fuel.jackson.responseObject
import com.github.tomakehurst.wiremock.client.WireMock.*
import com.github.tomakehurst.wiremock.junit5.WireMockRuntimeInfo
import com.github.tomakehurst.wiremock.junit5.WireMockTest
import no.nav.toi.App
import no.nav.toi.RolleUuidSpesifikasjon
import no.nav.security.mock.oauth2.MockOAuth2Server
import no.nav.toi.AuthenticationConfiguration
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
        wireMock.register(
            post("/veilederkandidat_current/_search?typed_keys=true")
                .withRequestBody(equalToJson(KandidatsøkRespons.query(), true, false))
                .willReturn(
                    ok(KandidatsøkRespons.esKandidatsøkRespons)
                )
        )
        val navIdent = "A123456"
        val token = lagToken(navIdent = navIdent)
        val (_, response, result) = Fuel.post("http://localhost:8080/api/kandidatsok")
            .body("""{}""")
            .header("Authorization", "Bearer ${token.serialize()}")
            .responseObject<JsonNode>()

        Assertions.assertThat(response.statusCode).isEqualTo(200)
        JSONAssert.assertEquals(result.get().toPrettyString(), KandidatsøkRespons.kandidatsøkRespons, false)
    }

    @Test
    fun `Kan søke kandidater med paginering`(wmRuntimeInfo: WireMockRuntimeInfo) {
        val wireMock = wmRuntimeInfo.wireMock
        wireMock.register(
            post("/veilederkandidat_current/_search?typed_keys=true")
                .withRequestBody(equalToJson(KandidatsøkRespons.query(from = 75), true, false))
                .willReturn(
                    ok(KandidatsøkRespons.esKandidatsøkRespons)
                )
        )
        val navIdent = "A123456"
        val token = lagToken(navIdent = navIdent)
        val (_, response, result) = Fuel.post("http://localhost:8080/api/kandidatsok?side=4")
            .body("""{}""")
            .header("Authorization", "Bearer ${token.serialize()}")
            .responseObject<JsonNode>()

        Assertions.assertThat(response.statusCode).isEqualTo(200)
        JSONAssert.assertEquals(result.get().toPrettyString(), KandidatsøkRespons.kandidatsøkRespons, false)
    }

    @Test
    fun `Kan søke kandidater sortert på flest kriterier`(wmRuntimeInfo: WireMockRuntimeInfo) {
        val wireMock = wmRuntimeInfo.wireMock
        wireMock.register(
            post("/veilederkandidat_current/_search?typed_keys=true")
                .withRequestBody(equalToJson(KandidatsøkRespons.query(sortering = false), true, false))
                .willReturn(
                    ok(KandidatsøkRespons.esKandidatsøkRespons)
                )
        )
        val navIdent = "A123456"
        val token = lagToken(navIdent = navIdent)
        val (_, response, result) = Fuel.post("http://localhost:8080/api/kandidatsok?sortering=score")
            .body("""{}""")
            .header("Authorization", "Bearer ${token.serialize()}")
            .responseObject<JsonNode>()

        Assertions.assertThat(response.statusCode).isEqualTo(200)
        JSONAssert.assertEquals(result.get().toPrettyString(), KandidatsøkRespons.kandidatsøkRespons, false)
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
        wireMock.register(
            post("/veilederkandidat_current/_search?typed_keys=true")
                .withRequestBody(equalToJson(KandidatsøkRespons.query(KandidatsøkRespons.stedTerm), true, false))
                .willReturn(
                    ok(KandidatsøkRespons.esKandidatsøkRespons)
                )
        )
        val navIdent = "A123456"
        val token = lagToken(navIdent = navIdent)
        val (_, response, result) = Fuel.post("http://localhost:8080/api/kandidatsok")
            .body("""{"ønsketSted":["Bodø.NO18.1804","Kristiansund.NO50.5001","Akershus.NO02","Norge.NO"]}""")
            .header("Authorization", "Bearer ${token.serialize()}")
            .responseObject<JsonNode>()

        Assertions.assertThat(response.statusCode).isEqualTo(200)
        JSONAssert.assertEquals(result.get().toPrettyString(), KandidatsøkRespons.kandidatsøkRespons, false)
    }

    @Test
    fun `Kan søke kandidater som bor på sted`(wmRuntimeInfo: WireMockRuntimeInfo) {
        val wireMock = wmRuntimeInfo.wireMock
        wireMock.register(
            post("/veilederkandidat_current/_search?typed_keys=true")
                .withRequestBody(equalToJson(KandidatsøkRespons.query(KandidatsøkRespons.stedMedMåBoPåTerm), true, false))
                .willReturn(
                    ok(KandidatsøkRespons.esKandidatsøkRespons)
                )
        )
        val navIdent = "A123456"
        val token = lagToken(navIdent = navIdent)
        val (_, response, result) = Fuel.post("http://localhost:8080/api/kandidatsok")
            .body("""
                {
                    "ønsketSted":["Oslo.NO03.0301","Bergen.NO46.4601","Norge.NO","Møre og Romsdal.NO15"],
                    "borPåØnsketSted": true
                }""".trimIndent())
            .header("Authorization", "Bearer ${token.serialize()}")
            .responseObject<JsonNode>()

        Assertions.assertThat(response.statusCode).isEqualTo(200)
        JSONAssert.assertEquals(result.get().toPrettyString(), KandidatsøkRespons.kandidatsøkRespons, false)
    }

    @Test
    fun `Kan søke kandidater med arbeidsønskefilter`(wmRuntimeInfo: WireMockRuntimeInfo) {
        val wireMock = wmRuntimeInfo.wireMock
        wireMock.register(
            post("/veilederkandidat_current/_search?typed_keys=true")
                .withRequestBody(equalToJson(KandidatsøkRespons.query(KandidatsøkRespons.arbeidsønskeTerm), true, false))
                .willReturn(
                    ok(KandidatsøkRespons.esKandidatsøkRespons)
                )
        )
        val navIdent = "A123456"
        val token = lagToken(navIdent = navIdent)
        val (_, response, result) = Fuel.post("http://localhost:8080/api/kandidatsok")
            .body("""{"ønsketYrke":["Sauegjeter","Saueklipper"]}""")
            .header("Authorization", "Bearer ${token.serialize()}")
            .responseObject<JsonNode>()

        Assertions.assertThat(response.statusCode).isEqualTo(200)
        JSONAssert.assertEquals(result.get().toPrettyString(), KandidatsøkRespons.kandidatsøkRespons, false)
    }

    @Test
    fun `Kan søke kandidater med innsatsgruppe`(wmRuntimeInfo: WireMockRuntimeInfo) {
        val wireMock = wmRuntimeInfo.wireMock
        wireMock.register(
            post("/veilederkandidat_current/_search?typed_keys=true")
                .withRequestBody(equalToJson(KandidatsøkRespons.query(innsatsgruppeTerm = KandidatsøkRespons.innsatsgruppeTermMedBATTogBFORM), true, false))
                .willReturn(
                    ok(KandidatsøkRespons.esKandidatsøkRespons)
                )
        )
        val navIdent = "A123456"
        val token = lagToken(navIdent = navIdent)
        val (_, response, result) = Fuel.post("http://localhost:8080/api/kandidatsok")
            .body("""{"innsatsgruppe":["BATT","BFORM"]}""")
            .header("Authorization", "Bearer ${token.serialize()}")
            .responseObject<JsonNode>()

        Assertions.assertThat(response.statusCode).isEqualTo(200)
        JSONAssert.assertEquals(result.get().toPrettyString(), KandidatsøkRespons.kandidatsøkRespons, false)
    }

    @Test
    fun `Kan søke kandidater med ANDRE innsatsgrupper`(wmRuntimeInfo: WireMockRuntimeInfo) {
        val wireMock = wmRuntimeInfo.wireMock
        wireMock.register(
            post("/veilederkandidat_current/_search?typed_keys=true")
                .withRequestBody(equalToJson(KandidatsøkRespons.query(innsatsgruppeTerm = KandidatsøkRespons.innsatsgruppeTermMedANDRE), true, false))
                .willReturn(
                    ok(KandidatsøkRespons.esKandidatsøkRespons)
                )
        )
        val navIdent = "A123456"
        val token = lagToken(navIdent = navIdent)
        val (_, response, result) = Fuel.post("http://localhost:8080/api/kandidatsok")
            .body("""{"innsatsgruppe":["ANDRE"]}""")
            .header("Authorization", "Bearer ${token.serialize()}")
            .responseObject<JsonNode>()

        Assertions.assertThat(response.statusCode).isEqualTo(200)
        JSONAssert.assertEquals(result.get().toPrettyString(), KandidatsøkRespons.kandidatsøkRespons, false)
    }

    @Test
    fun `Kan søke kandidater med alle innsatsgrupper`(wmRuntimeInfo: WireMockRuntimeInfo) {
        val wireMock = wmRuntimeInfo.wireMock
        wireMock.register(
            post("/veilederkandidat_current/_search?typed_keys=true")
                .withRequestBody(equalToJson(KandidatsøkRespons.query(innsatsgruppeTerm = KandidatsøkRespons.innsatsgruppeTermMedAlle), true, false))
                .willReturn(
                    ok(KandidatsøkRespons.esKandidatsøkRespons)
                )
        )
        val navIdent = "A123456"
        val token = lagToken(navIdent = navIdent)
        val (_, response, result) = Fuel.post("http://localhost:8080/api/kandidatsok")
            .body("""{"innsatsgruppe":["BATT","BFORM","IKVAL","VARIG","ANDRE"]}""")
            .header("Authorization", "Bearer ${token.serialize()}")
            .responseObject<JsonNode>()

        Assertions.assertThat(response.statusCode).isEqualTo(200)
        JSONAssert.assertEquals(result.get().toPrettyString(), KandidatsøkRespons.kandidatsøkRespons, false)
    }

    @Test
    fun `Søk med innsatsgruppe satt til tom liste skal defaulte til default innsatsgrupper`(wmRuntimeInfo: WireMockRuntimeInfo) {
        val wireMock = wmRuntimeInfo.wireMock
        wireMock.register(
            post("/veilederkandidat_current/_search?typed_keys=true")
                .withRequestBody(equalToJson(KandidatsøkRespons.query(), true, false))
                .willReturn(
                    ok(KandidatsøkRespons.esKandidatsøkRespons)
                )
        )
        val navIdent = "A123456"
        val token = lagToken(navIdent = navIdent)
        val (_, response, result) = Fuel.post("http://localhost:8080/api/kandidatsok")
            .body("""{"innsatsgruppe":[]}""")
            .header("Authorization", "Bearer ${token.serialize()}")
            .responseObject<JsonNode>()

        Assertions.assertThat(response.statusCode).isEqualTo(200)
        JSONAssert.assertEquals(result.get().toPrettyString(), KandidatsøkRespons.kandidatsøkRespons, false)
    }

    @Test
    fun `Kan søke kandidater med målform`(wmRuntimeInfo: WireMockRuntimeInfo) {
        val wireMock = wmRuntimeInfo.wireMock
        wireMock.register(
            post("/veilederkandidat_current/_search?typed_keys=true")
                .withRequestBody(equalToJson(KandidatsøkRespons.query(KandidatsøkRespons.språkTerm), true, false))
                .willReturn(
                    ok(KandidatsøkRespons.esKandidatsøkRespons)
                )
        )
        val navIdent = "A123456"
        val token = lagToken(navIdent = navIdent)
        val (_, response, result) = Fuel.post("http://localhost:8080/api/kandidatsok")
            .body("""{"språk":["Nynorsk","Norsk"]}""")
            .header("Authorization", "Bearer ${token.serialize()}")
            .responseObject<JsonNode>()

        Assertions.assertThat(response.statusCode).isEqualTo(200)
        JSONAssert.assertEquals(result.get().toPrettyString(), KandidatsøkRespons.kandidatsøkRespons, false)
    }

    @Test
    fun `Kan søke kandidater med arbeidserfaring`(wmRuntimeInfo: WireMockRuntimeInfo) {
        val wireMock = wmRuntimeInfo.wireMock
        wireMock.register(
            post("/veilederkandidat_current/_search?typed_keys=true")
                .withRequestBody(equalToJson(KandidatsøkRespons.query(KandidatsøkRespons.arbeidsErfaringTerm), true, false))
                .willReturn(
                    ok(KandidatsøkRespons.esKandidatsøkRespons)
                )
        )
        val navIdent = "A123456"
        val token = lagToken(navIdent = navIdent)
        val (_, response, result) = Fuel.post("http://localhost:8080/api/kandidatsok")
            .body("""{"arbeidserfaring":["Barnehageassistent","Butikkansvarlig"]}""")
            .header("Authorization", "Bearer ${token.serialize()}")
            .responseObject<JsonNode>()

        Assertions.assertThat(response.statusCode).isEqualTo(200)
        JSONAssert.assertEquals(result.get().toPrettyString(), KandidatsøkRespons.kandidatsøkRespons, false)
    }

    @Test
    fun `Kan søke kandidater med nylig arbeidserfaring`(wmRuntimeInfo: WireMockRuntimeInfo) {
        val wireMock = wmRuntimeInfo.wireMock
        wireMock.register(
            post("/veilederkandidat_current/_search?typed_keys=true")
                .withRequestBody(equalToJson(KandidatsøkRespons.query(KandidatsøkRespons.nyligArbeidsErfaringTerm), true, false))
                .willReturn(
                    ok(KandidatsøkRespons.esKandidatsøkRespons)
                )
        )
        val navIdent = "A123456"
        val token = lagToken(navIdent = navIdent)
        val (_, response, result) = Fuel.post("http://localhost:8080/api/kandidatsok")
            .body("""{"arbeidserfaring":["Hvalfanger","Kokk"],"ferskhet":2}""".trimMargin())
            .header("Authorization", "Bearer ${token.serialize()}")
            .responseObject<JsonNode>()

        Assertions.assertThat(response.statusCode).isEqualTo(200)
        JSONAssert.assertEquals(result.get().toPrettyString(), KandidatsøkRespons.kandidatsøkRespons, false)
    }

    @Test
    fun `Kan søke kandidater med hovedmål`(wmRuntimeInfo: WireMockRuntimeInfo) {
        val wireMock = wmRuntimeInfo.wireMock
        wireMock.register(
            post("/veilederkandidat_current/_search?typed_keys=true")
                .withRequestBody(equalToJson(KandidatsøkRespons.query(KandidatsøkRespons.hovedmålTerm), true, false))
                .willReturn(
                    ok(KandidatsøkRespons.esKandidatsøkRespons)
                )
        )
        val navIdent = "A123456"
        val token = lagToken(navIdent = navIdent)
        val (_, response, result) = Fuel.post("http://localhost:8080/api/kandidatsok")
            .body("""{"hovedmål":["SKAFFEA","OKEDELT"]}""")
            .header("Authorization", "Bearer ${token.serialize()}")
            .responseObject<JsonNode>()

        Assertions.assertThat(response.statusCode).isEqualTo(200)
        JSONAssert.assertEquals(result.get().toPrettyString(), KandidatsøkRespons.kandidatsøkRespons, false)
    }

    @Test
    fun `Kan søke kandidater med kompetanse`(wmRuntimeInfo: WireMockRuntimeInfo) {
        val wireMock = wmRuntimeInfo.wireMock
        wireMock.register(
            post("/veilederkandidat_current/_search?typed_keys=true")
                .withRequestBody(equalToJson(KandidatsøkRespons.query(KandidatsøkRespons.kompetanseTerm), true, false))
                .willReturn(
                    ok(KandidatsøkRespons.esKandidatsøkRespons)
                )
        )
        val navIdent = "A123456"
        val token = lagToken(navIdent = navIdent)
        val (_, response, result) = Fuel.post("http://localhost:8080/api/kandidatsok")
            .body("""{"kompetanse":["Fagbrev FU-operatør","Kotlin"]}""")
            .header("Authorization", "Bearer ${token.serialize()}")
            .responseObject<JsonNode>()

        Assertions.assertThat(response.statusCode).isEqualTo(200)
        JSONAssert.assertEquals(result.get().toPrettyString(), KandidatsøkRespons.kandidatsøkRespons, false)
    }

    @Test
    fun `Kan søke kandidater med førerkort`(wmRuntimeInfo: WireMockRuntimeInfo) {
        val wireMock = wmRuntimeInfo.wireMock
        wireMock.register(
            post("/veilederkandidat_current/_search?typed_keys=true")
                .withRequestBody(equalToJson(KandidatsøkRespons.query(KandidatsøkRespons.førerkortTerm), true, false))
                .willReturn(
                    ok(KandidatsøkRespons.esKandidatsøkRespons)
                )
        )
        val navIdent = "A123456"
        val token = lagToken(navIdent = navIdent)
        val (_, response, result) = Fuel.post("http://localhost:8080/api/kandidatsok")
            .body("""{"førerkort":["D - Buss","BE - Personbil med tilhenger"]}""")
            .header("Authorization", "Bearer ${token.serialize()}")
            .responseObject<JsonNode>()

        Assertions.assertThat(response.statusCode).isEqualTo(200)
        JSONAssert.assertEquals(result.get().toPrettyString(), KandidatsøkRespons.kandidatsøkRespons, false)
    }
    @Test
    fun `Kan søke kandidater med utdanning`(wmRuntimeInfo: WireMockRuntimeInfo) {
        val wireMock = wmRuntimeInfo.wireMock
        wireMock.register(
            post("/veilederkandidat_current/_search?typed_keys=true")
                .withRequestBody(equalToJson(KandidatsøkRespons.query(KandidatsøkRespons.utdanningTerm), true, false))
                .willReturn(
                    ok(KandidatsøkRespons.esKandidatsøkRespons)
                )
        )
        val navIdent = "A123456"
        val token = lagToken(navIdent = navIdent)
        val (_, response, result) = Fuel.post("http://localhost:8080/api/kandidatsok")
            .body("""{"utdanningsnivå":["videregaende","bachelor","doktorgrad"]}""")
            .header("Authorization", "Bearer ${token.serialize()}")
            .responseObject<JsonNode>()

        Assertions.assertThat(response.statusCode).isEqualTo(200)
        JSONAssert.assertEquals(result.get().toPrettyString(), KandidatsøkRespons.kandidatsøkRespons, false)
    }
    @Test
    fun `Kan søke kandidater med prioriterte målgrupper`(wmRuntimeInfo: WireMockRuntimeInfo) {
        val wireMock = wmRuntimeInfo.wireMock
        wireMock.register(
            post("/veilederkandidat_current/_search?typed_keys=true")
                .withRequestBody(equalToJson(KandidatsøkRespons.query(KandidatsøkRespons.prioriterteMålgrupperTerm), true, false))
                .willReturn(
                    ok(KandidatsøkRespons.esKandidatsøkRespons)
                )
        )
        val navIdent = "A123456"
        val token = lagToken(navIdent = navIdent)
        val (_, response, result) = Fuel.post("http://localhost:8080/api/kandidatsok")
            .body("""{"prioritertMålgruppe":["senior","unge","hullICv"]}""")
            .header("Authorization", "Bearer ${token.serialize()}")
            .responseObject<JsonNode>()

        Assertions.assertThat(response.statusCode).isEqualTo(200)
        JSONAssert.assertEquals(result.get().toPrettyString(), KandidatsøkRespons.kandidatsøkRespons, false)
    }
    @Test
    fun `Kan søke kandidat med ident`(wmRuntimeInfo: WireMockRuntimeInfo) {
        val wireMock = wmRuntimeInfo.wireMock
        wireMock.register(
            post("/veilederkandidat_current/_search?typed_keys=true")
                .withRequestBody(equalToJson(KandidatsøkRespons.query(KandidatsøkRespons.queryMedIdentTerm), true, false))
                .willReturn(
                    ok(KandidatsøkRespons.esKandidatsøkRespons)
                )
        )
        val navIdent = "A123456"
        val token = lagToken(navIdent = navIdent)
        val (_, response, result) = Fuel.post("http://localhost:8080/api/kandidatsok")
            .body("""{"fritekst":"12345678910"}""")
            .header("Authorization", "Bearer ${token.serialize()}")
            .responseObject<JsonNode>()

        Assertions.assertThat(response.statusCode).isEqualTo(200)
        JSONAssert.assertEquals(result.get().toPrettyString(), KandidatsøkRespons.kandidatsøkRespons, false)
    }
    @Test
    fun `Kan søke kandidat med PAMkandidatnr`(wmRuntimeInfo: WireMockRuntimeInfo) {
        val wireMock = wmRuntimeInfo.wireMock
        wireMock.register(
            post("/veilederkandidat_current/_search?typed_keys=true")
                .withRequestBody(equalToJson(KandidatsøkRespons.query(KandidatsøkRespons.queryMedPAMKandidatnrTerm), true, false))
                .willReturn(
                    ok(KandidatsøkRespons.esKandidatsøkRespons)
                )
        )
        val navIdent = "A123456"
        val token = lagToken(navIdent = navIdent)
        val (_, response, result) = Fuel.post("http://localhost:8080/api/kandidatsok")
            .body("""{"fritekst":"PAM01Z"}""")
            .header("Authorization", "Bearer ${token.serialize()}")
            .responseObject<JsonNode>()

        Assertions.assertThat(response.statusCode).isEqualTo(200)
        JSONAssert.assertEquals(result.get().toPrettyString(), KandidatsøkRespons.kandidatsøkRespons, false)
    }
    @Test
    fun `Kan søke kandidat med arenakandidatnr`(wmRuntimeInfo: WireMockRuntimeInfo) {
        val wireMock = wmRuntimeInfo.wireMock
        wireMock.register(
            post("/veilederkandidat_current/_search?typed_keys=true")
                .withRequestBody(equalToJson(KandidatsøkRespons.query(KandidatsøkRespons.queryMedArenaKandidatnrTerm), true, false))
                .willReturn(
                    ok(KandidatsøkRespons.esKandidatsøkRespons)
                )
        )
        val navIdent = "A123456"
        val token = lagToken(navIdent = navIdent)
        val (_, response, result) = Fuel.post("http://localhost:8080/api/kandidatsok")
            .body("""{"fritekst":"ab123"}""")
            .header("Authorization", "Bearer ${token.serialize()}")
            .responseObject<JsonNode>()

        Assertions.assertThat(response.statusCode).isEqualTo(200)
        JSONAssert.assertEquals(result.get().toPrettyString(), KandidatsøkRespons.kandidatsøkRespons, false)
    }
    @Test
    fun `Kan søke mine kandidater`(wmRuntimeInfo: WireMockRuntimeInfo) {
        val wireMock = wmRuntimeInfo.wireMock
        wireMock.register(
            post("/veilederkandidat_current/_search?typed_keys=true")
                .withRequestBody(equalToJson(KandidatsøkRespons.query(KandidatsøkRespons.mineBrukereTerm), true, false))
                .willReturn(
                    ok(KandidatsøkRespons.esKandidatsøkRespons)
                )
        )
        val navIdent = "A123456"
        val token = lagToken(navIdent = navIdent)
        val (_, response, result) = Fuel.post("http://localhost:8080/api/kandidatsok")
            .body("""{"portefølje":"mine"}""")
            .header("Authorization", "Bearer ${token.serialize()}")
            .responseObject<JsonNode>()

        Assertions.assertThat(response.statusCode).isEqualTo(200)
        JSONAssert.assertEquals(result.get().toPrettyString(), KandidatsøkRespons.kandidatsøkRespons, false)
    }
    @Test
    fun `Kan søke på kontor`(wmRuntimeInfo: WireMockRuntimeInfo) {
        val wireMock = wmRuntimeInfo.wireMock
        wireMock.register(
            post("/veilederkandidat_current/_search?typed_keys=true")
                .withRequestBody(equalToJson(KandidatsøkRespons.query(KandidatsøkRespons.valgtKontorTerm), true, false))
                .willReturn(
                    ok(KandidatsøkRespons.esKandidatsøkRespons)
                )
        )
        val navIdent = "A123456"
        val token = lagToken(navIdent = navIdent)
        val (_, response, result) = Fuel.post("http://localhost:8080/api/kandidatsok")
            .body("""{"portefølje":"kontor","valgtKontor":["NAV Hamar","NAV Lofoten"]}""")
            .header("Authorization", "Bearer ${token.serialize()}")
            .responseObject<JsonNode>()

        Assertions.assertThat(response.statusCode).isEqualTo(200)
        JSONAssert.assertEquals(result.get().toPrettyString(), KandidatsøkRespons.kandidatsøkRespons, false)
    }
    @Test
    fun `Søk på kontor uten valgt kontor satt fører til 400feil`(wmRuntimeInfo: WireMockRuntimeInfo) {
        val wireMock = wmRuntimeInfo.wireMock
        wireMock.register(
            post("/veilederkandidat_current/_search?typed_keys=true")
                .withRequestBody(equalToJson(KandidatsøkRespons.query(KandidatsøkRespons.valgtKontorTerm), true, false))
                .willReturn(
                    ok(KandidatsøkRespons.esKandidatsøkRespons)
                )
        )
        val navIdent = "A123456"
        val token = lagToken(navIdent = navIdent)
        val (_, response, result) = Fuel.post("http://localhost:8080/api/kandidatsok")
            .body("""{"portefølje":"kontor"}""")
            .header("Authorization", "Bearer ${token.serialize()}")
            .responseObject<JsonNode>()

        Assertions.assertThat(response.statusCode).isEqualTo(400)
    }
    @Test
    fun `Kan søke kandidat med fritekstsøk`(wmRuntimeInfo: WireMockRuntimeInfo) {
        val wireMock = wmRuntimeInfo.wireMock
        wireMock.register(
            post("/veilederkandidat_current/_search?typed_keys=true")
                .withRequestBody(equalToJson(KandidatsøkRespons.query(KandidatsøkRespons.queryMedKMultiMatchTerm), true, false))
                .willReturn(
                    ok(KandidatsøkRespons.esKandidatsøkRespons)
                )
        )
        val navIdent = "A123456"
        val token = lagToken(navIdent = navIdent)
        val (_, response, result) = Fuel.post("http://localhost:8080/api/kandidatsok")
            .body("""{"fritekst":"søkeord"}""")
            .header("Authorization", "Bearer ${token.serialize()}")
            .responseObject<JsonNode>()

        Assertions.assertThat(response.statusCode).isEqualTo(200)
        JSONAssert.assertEquals(result.get().toPrettyString(), KandidatsøkRespons.kandidatsøkRespons, false)
    }
    @Test
    fun `Kan søke kandidat med alle filtre på en gang`(wmRuntimeInfo: WireMockRuntimeInfo) {
        val wireMock = wmRuntimeInfo.wireMock
        wireMock.register(
            post("/veilederkandidat_current/_search?typed_keys=true")
                .withRequestBody(equalToJson(KandidatsøkRespons.query(KandidatsøkRespons.stedTerm,
                        KandidatsøkRespons.arbeidsønskeTerm, KandidatsøkRespons.queryMedKMultiMatchTerm,
                        KandidatsøkRespons.utdanningTerm, KandidatsøkRespons.prioriterteMålgrupperTerm,
                        KandidatsøkRespons.nyligArbeidsErfaringTerm, KandidatsøkRespons.hovedmålTerm,
                        KandidatsøkRespons.kompetanseTerm, KandidatsøkRespons.førerkortTerm,
                        KandidatsøkRespons.språkTerm, innsatsgruppeTerm = KandidatsøkRespons.innsatsgruppeTermMedBATTogBFORM),
                    true, false))
                .willReturn(
                    ok(KandidatsøkRespons.esKandidatsøkRespons)
                )
        )
        val navIdent = "A123456"
        val token = lagToken(navIdent = navIdent)
        val (_, response, result) = Fuel.post("http://localhost:8080/api/kandidatsok")
            .body("""
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
                }""".trimIndent())
            .header("Authorization", "Bearer ${token.serialize()}")
            .responseObject<JsonNode>()

        Assertions.assertThat(response.statusCode).isEqualTo(200)
        JSONAssert.assertEquals(result.get().toPrettyString(), KandidatsøkRespons.kandidatsøkRespons, false)
    }

    private fun lagLokalApp() = App(
        port = 8080,
        authenticationConfigurations = listOf(AuthenticationConfiguration(
            audience = "1",
            issuer = "http://localhost:$authPort/default",
            jwksUri = "http://localhost:$authPort/default/jwks",
        )),
        rolleUuidSpesifikasjon = RolleUuidSpesifikasjon(
            modiaGenerell = UUID.fromString(modiaGenerell),
            modiaOppfølging = UUID.fromString(modiaOppfølging),
        ),
        openSearchUsername = "user",
        openSearchPassword = "pass",
        openSearchUri = "http://localhost:10000/opensearch",
    )

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