import com.fasterxml.jackson.databind.JsonNode
import com.github.kittinunf.fuel.Fuel
import com.github.kittinunf.fuel.jackson.responseObject
import no.nav.App
import no.nav.RolleUuidSpesifikasjon
import no.nav.security.mock.oauth2.MockOAuth2Server
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import java.util.*

private const val modiaGenerell = "67a06857-0028-4a90-bf4c-9c9a92c7d733"
private const val modiaOppfølging = "554a66fb-fbec-4b92-90c1-0d9c085c362c"


@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class Autentiseringstest {
    private val authPort = 18305

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
        val token = lagToken(
            claims = mapOf("groups" to listOf(modiaGenerell))
        )
        println(token.serialize())
        val (_, response) = Fuel.get("http://localhost:8080/api/me")
            .header("Authorization", "Bearer ${token.serialize()}")
            .response()

        assertThat(response.statusCode).isEqualTo(401)
    }

    @Test
    fun `autentisering feiler om man ikke har gyldig token`() {
        val token = lagToken(
            issuerId = "fakeissuer",
        )
        val (_, response) = Fuel.get("http://localhost:8080/api/me")
            .header("Authorization", "Bearer ${token.serialize()}")
            .response()

        assertThat(response.statusCode).isEqualTo(401)
    }

    @Test
    fun `autentisering feiler om man token ikke er utstedt for vår applikasjon`() {
        val token = lagToken(aud = "feilaudience")
        val (_, response) = Fuel.get("http://localhost:8080/api/me")
            .header("Authorization", "Bearer ${token.serialize()}")
            .response()

        assertThat(response.statusCode).isEqualTo(401)
    }

    @Test
    fun `autentisering fungerer om man har navident i token`() {
        val navIdent = "A123456"
        val token = lagToken(navIdent = navIdent)
        println(token.serialize())
        val (_, response, result) = Fuel.get("http://localhost:8080/api/me")
            .header("Authorization", "Bearer ${token.serialize()}")
            .responseObject<JsonNode>()

        assertThat(response.statusCode).isEqualTo(200)
        assertThat(result.get()["navIdent"].asText()).isEqualTo(navIdent)
        assertThat(result.get()["roller"].get(0).asText()).isEqualTo("MODIA_GENERELL")
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

    private fun lagLokalApp() = App(
        port = 8080,
        azureAppClientId = "1",
        azureOpenidConfigIssuer = "http://localhost:$authPort/default",
        azureOpenidConfigJwksUri = "http://localhost:$authPort/default/jwks",
        rolleUuidSpesifikasjon = RolleUuidSpesifikasjon(
            modiaGenerell = UUID.fromString(modiaGenerell),
            modiaOppfølging = UUID.fromString(modiaOppfølging),
        ),
    )
}