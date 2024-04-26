import com.fasterxml.jackson.databind.JsonNode
import com.github.kittinunf.fuel.Fuel
import com.github.kittinunf.fuel.jackson.responseObject
import no.nav.security.mock.oauth2.MockOAuth2Server
import no.nav.toi.App
import no.nav.toi.AuthenticationConfiguration
import no.nav.toi.RolleUuidSpesifikasjon
import org.assertj.core.api.Assertions
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import java.util.*
import java.util.stream.Stream

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class Tokentest {
    private val authPort = 18306

    private val utvikler = UUID.randomUUID()

    private val audience = "iden til applikasjonen"

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

    private fun urler(): Stream<Arguments> = Stream.of(
        "http://localhost:8080/api/kandidatsok",
        "http://localhost:8080/api/kandidatsok/navigering",
        "http://localhost:8080/api/lookup-cv"
    ).map { Arguments.of(it) }

    @ParameterizedTest
    @MethodSource("urler")
    fun `krever token for å søke kandidatnumre for navigering`(url: String) {
        val (_, response, _) = Fuel.post(url)
            .body("{}")
            .responseObject<JsonNode>()

        Assertions.assertThat(response.statusCode).isEqualTo(401)
    }

    @ParameterizedTest
    @MethodSource("urler")
    fun `Navigering må ha token med rett issuer`(url: String) {
        val token = lagToken(issuerId = "falskissuer")
        val (_, response, _) = Fuel.post(url)
            .body("""{}""")
            .header("Authorization", "Bearer ${token.serialize()}")
            .responseObject<JsonNode>()

        Assertions.assertThat(response.statusCode).isEqualTo(401)
    }

    @ParameterizedTest
    @MethodSource("urler")
    fun `Navigering må ha navIdent`(url: String) {
        val token = lagToken(claims = mapOf("groups" to listOf(utvikler.toString())))
        val (_, response, _) = Fuel.post(url)
            .body("""{}""")
            .header("Authorization", "Bearer ${token.serialize()}")
            .responseObject<JsonNode>()

        Assertions.assertThat(response.statusCode).isEqualTo(401)
    }

    @ParameterizedTest
    @MethodSource("urler")
    fun `Navigering må ha rett audience`(url: String) {
        val token = lagToken(aud = "Feil aud")
        val (_, response, _) = Fuel.post(url)
            .body("""{}""")
            .header("Authorization", "Bearer ${token.serialize()}")
            .responseObject<JsonNode>()

        Assertions.assertThat(response.statusCode).isEqualTo(401)
    }

    @ParameterizedTest
    @MethodSource("urler")
    fun `Navigering må ikke være utgått`(url: String) {
        val token = lagToken(expiry = -1)
        val (_, response, _) = Fuel.post(url)
            .body("""{}""")
            .header("Authorization", "Bearer ${token.serialize()}")
            .responseObject<JsonNode>()

        Assertions.assertThat(response.statusCode).isEqualTo(401)
    }

    @ParameterizedTest
    @MethodSource("urler")
    fun `Navigering må ha rett algoritme`(url: String) {
        val payload = lagToken().serialize().split(".")[1]

        val (_, response, _) = Fuel.post(url)
            .body("""{}""")
            .header("Authorization", "Bearer eyJ0eXAiOiJKV1QiLCJhbGciOiJub25lIn0.$payload.")
            .responseObject<JsonNode>()

        Assertions.assertThat(response.statusCode).isEqualTo(401)
    }

    private fun lagToken(
        issuerId: String = "http://localhost:$authPort/default",
        aud: String = audience,
        navIdent: String = "A000001",
        groups: List<String> = listOf(utvikler.toString()),
        claims: Map<String, Any> = mapOf("NAVident" to navIdent, "groups" to groups),
        expiry: Long = 3600,
    ) = authServer.issueToken(
        issuerId = issuerId,
        subject = "subject",
        audience = aud,
        claims = claims,
        expiry = expiry
    )

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
            modiaGenerell = UUID.randomUUID(),
            jobbsøkerrettet = UUID.randomUUID(),
            arbeidsgiverrettet = UUID.randomUUID(),
            utvikler = utvikler
        ),
        openSearchUsername = "user",
        openSearchPassword = "pass",
        openSearchUri = "http://localhost:10000",
        pdlUrl = "http://localhost:10000/pdl",
        azureSecret = "secret",
        azureClientId = audience,
        azureUrl = "http://localhost:$authPort",
        pdlScope = "http://localhost/.default"
    )
}