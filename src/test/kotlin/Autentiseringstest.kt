import com.github.kittinunf.fuel.Fuel
import no.nav.App
import no.nav.security.mock.oauth2.MockOAuth2Server
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class Autentiseringstest {

    private val app: App = lagLokalApp()
    private val authServer = MockOAuth2Server()
    private val authPort = 18305

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
        val (_, response) = Fuel.post("http://localhost:8080/api/me")
            .response()

        assertThat(response.statusCode).isEqualTo(401)
    }

    private fun lagLokalApp() = App(
        8080,
        "1",
        "http://localhost:$authPort/default",
        "http://localhost:$authPort/default/jwks"
    )
}
