import com.fasterxml.jackson.databind.JsonNode
import com.github.kittinunf.fuel.Fuel
import com.github.kittinunf.fuel.jackson.responseObject
import com.github.tomakehurst.wiremock.client.WireMock.*
import com.github.tomakehurst.wiremock.junit5.WireMockRuntimeInfo
import com.github.tomakehurst.wiremock.junit5.WireMockTest
import no.nav.toi.App
import no.nav.toi.RolleUuidSpesifikasjon
import no.nav.security.mock.oauth2.MockOAuth2Server
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
            .body("""{"sted":"NO18.1804"}""")
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
            .body("""{"arbeidsonske":"Sauegjeter"}""")
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
            .body("""{"sprak":"Nynorsk"}""")
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
            .body("""{"arbeidserfaring":"Barnehageassistent"}""")
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

    private fun lagLokalApp() = App(
        port = 8080,
        azureAppClientId = "1",
        azureOpenidConfigIssuer = "http://localhost:$authPort/default",
        azureOpenidConfigJwksUri = "http://localhost:$authPort/default/jwks",
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