import com.fasterxml.jackson.databind.JsonNode
import com.github.kittinunf.fuel.Fuel
import com.github.kittinunf.fuel.jackson.responseObject
import com.github.tomakehurst.wiremock.client.WireMock
import com.github.tomakehurst.wiremock.junit5.WireMockRuntimeInfo
import com.github.tomakehurst.wiremock.junit5.WireMockTest
import no.nav.toi.LokalApp
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import java.util.*


@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@WireMockTest(httpPort = 10000)
class SikkerhetTest {
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
    fun `kan aksessere usikret endepunkt uten å ha tilganger`() {
        val (_, response) = Fuel.get("http://localhost:8080/internal/alive")
            .response()

        assertThat(response.statusCode).isEqualTo(200)

    }

    @Test
    fun `autentisering feiler om man ikke har token`() {
        val (_, response) = Fuel.get("http://localhost:8080/api/me")
            .response()

        assertThat(response.statusCode).isEqualTo(401)
    }

    @Test
    fun `autentisering feiler om man ikke har navident i token`() {
        val token = app.lagToken(
            claims = mapOf("groups" to listOf(LokalApp.arbeidsgiverrettet))
        )
        println(token.serialize())
        val (_, response) = Fuel.get("http://localhost:8080/api/me")
            .header("Authorization", "Bearer ${token.serialize()}")
            .response()

        assertThat(response.statusCode).isEqualTo(401)
    }

    @Test
    fun `autentisering feiler om man ikke har gyldig token`() {
        val token = app.lagToken(
            issuerId = "fakeissuer",
        )
        val (_, response) = Fuel.get("http://localhost:8080/api/me")
            .header("Authorization", "Bearer ${token.serialize()}")
            .response()

        assertThat(response.statusCode).isEqualTo(401)
    }

    @Test
    fun `autentisering feiler om man token ikke er utstedt for vår applikasjon`() {
        val token = app.lagToken(aud = "feilaudience")
        val (_, response) = Fuel.get("http://localhost:8080/api/me")
            .header("Authorization", "Bearer ${token.serialize()}")
            .response()

        assertThat(response.statusCode).isEqualTo(401)
    }

    @Test
    fun `autentisering fungerer om man har navident i token`() {
        val navIdent = "A123456"
        val token = app.lagToken(navIdent = navIdent)
        println(token.serialize())
        val (_, response, result) = Fuel.get("http://localhost:8080/api/me")
            .header("Authorization", "Bearer ${token.serialize()}")
            .responseObject<JsonNode>()

        assertThat(response.statusCode).isEqualTo(200)
        assertThat(result.get()["navIdent"].asText()).isEqualTo(navIdent)
    }

    @Test
    fun `autentisering fungerer om man har MODIA_GENERELL rolle i token`() {
        val navIdent = "A123456"
        val token = app.lagToken(navIdent = navIdent)
        println(token.serialize())
        val (_, response, result) = Fuel.get("http://localhost:8080/api/me")
            .header("Authorization", "Bearer ${token.serialize()}")
            .responseObject<JsonNode>()

        assertThat(response.statusCode).isEqualTo(200)
        assertThat(result.get()["roller"].get(0).asText()).isEqualTo("ARBEIDSGIVER_RETTET")
    }

    @Test
    fun `opensearch-biblioteket takler forsøk på json-injection`(wmRuntimeInfo: WireMockRuntimeInfo) {
        val wireMock = wmRuntimeInfo.wireMock
        wireMock.register(
            WireMock.post("/kandidater/_search?typed_keys=true")
                .withRequestBody(WireMock.equalToJson("""{"query":{"term":{"kandidatnr":{"value":"\",!xz" }}}}"""))
                .willReturn(
                    WireMock.ok(CvTestRespons.responseOpenSearch(CvTestRespons.sourceCvLookup))
                )
        )
        val token = app.lagToken(groups = listOf(LokalApp.arbeidsgiverrettet))
        val (_, response) = Fuel.post("http://localhost:8080/api/lookup-cv")
            .body("""{"kandidatnr": "\",!xz"}""")
            .header("Authorization", "Bearer ${token.serialize()}")
            .responseObject<JsonNode>()

        assertThat(response.statusCode).isEqualTo(200)
    }
}
