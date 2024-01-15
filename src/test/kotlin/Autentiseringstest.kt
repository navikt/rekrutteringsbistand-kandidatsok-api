import com.github.kittinunf.fuel.Fuel
import org.assertj.core.api.Assertions
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class Autentiseringstest {

    @Test
    fun `kan aksessere usikret endepunkt uten å ha tilganger`() {
        val (_, response) = Fuel.post("http://localhost:8080/internal/alive")
            .response()

        assertThat(response.statusCode).isEqualTo(200)

    }
    @Test
    fun `autentisering feiler om man ikke har navid`() {
        val (_, response) = Fuel.post("http://localhost:8080/api/me")
            .response()

        assertThat(response.statusCode).isEqualTo(401)

    }
}