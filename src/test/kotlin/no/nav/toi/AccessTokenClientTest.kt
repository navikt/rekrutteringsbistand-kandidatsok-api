package no.nav.toi

import com.github.tomakehurst.wiremock.client.WireMock.*
import com.github.tomakehurst.wiremock.junit5.WireMockRuntimeInfo
import com.github.tomakehurst.wiremock.junit5.WireMockTest
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test

@WireMockTest
class AccessTokenClientTest {

    private fun klient(wm: WireMockRuntimeInfo) = AccessTokenClient(
        secret = "hemmelig",
        clientId = "klient-id",
        scope = "api://dev-gcp.toi.noe/.default",
        azureUrl = "${wm.httpBaseUrl}/token",
    )

    @Test
    fun `henter on-behalf-of-token med skjemadata og cacher resultatet`(wm: WireMockRuntimeInfo) {
        wm.wireMock.register(
            post("/token").willReturn(okJson("""{"access_token":"obo-token","expires_in":3600,"token_type":"Bearer"}"""))
        )
        val klient = klient(wm)

        assertThat(klient.hentAccessToken("innkommende")).isEqualTo("obo-token")
        assertThat(klient.hentAccessToken("innkommende")).isEqualTo("obo-token")

        wm.wireMock.verifyThat(
            1, postRequestedFor(urlEqualTo("/token"))
                .withHeader("Content-Type", containing("application/x-www-form-urlencoded"))
                .withFormParam("grant_type", equalTo("urn:ietf:params:oauth:grant-type:jwt-bearer"))
                .withFormParam("client_secret", equalTo("hemmelig"))
                .withFormParam("client_id", equalTo("klient-id"))
                .withFormParam("assertion", equalTo("innkommende"))
                .withFormParam("scope", equalTo("api://dev-gcp.toi.noe/.default"))
                .withFormParam("requested_token_use", equalTo("on_behalf_of"))
        )
    }

    @Test
    fun `prøver tre ganger og kaster exception ved feilsvar`(wm: WireMockRuntimeInfo) {
        wm.wireMock.register(post("/token").willReturn(serverError().withBody("""{"error":"noe gikk galt"}""")))

        assertThatThrownBy { klient(wm).hentAccessToken("innkommende") }
            .isInstanceOf(RuntimeException::class.java)
            .hasMessageContaining("500")

        wm.wireMock.verifyThat(3, postRequestedFor(urlEqualTo("/token")))
    }
}
