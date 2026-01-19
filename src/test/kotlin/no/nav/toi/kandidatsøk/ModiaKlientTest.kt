package no.nav.toi.kandidatsøk

import com.github.tomakehurst.wiremock.client.WireMock.*
import com.github.tomakehurst.wiremock.junit5.WireMockRuntimeInfo
import com.github.tomakehurst.wiremock.junit5.WireMockTest
import no.nav.toi.AccessTokenClient
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@WireMockTest(httpPort = 10002)
class ModiaKlientTest {

    @Test
    fun `hentModiaEnheter cacher resultat basert på token`(wmRuntimeInfo: WireMockRuntimeInfo) {
        val wireMock = wmRuntimeInfo.wireMock
        val accessTokenClient = mock<AccessTokenClient>()
        whenever(accessTokenClient.hentAccessToken(any())).thenReturn("mocked-access-token")

        val modiaKlient = ModiaKlient("http://localhost:10002", accessTokenClient)

        wireMock.register(
            get("/api/decorator")
                .willReturn(
                    okJson(
                        """
                        {
                            "ident": "Z000000",
                            "fornavn": "Test",
                            "etternavn": "Testersen",
                            "enheter": [
                                {
                                    "enhetId": "0403",
                                    "navn": "NAV Hamar"
                                }
                            ]
                        }
                        """.trimIndent()
                    )
                )
        )

        val token = "test-token-1"

        // Første kall - skal hente fra API
        val resultat1 = modiaKlient.hentModiaEnheter(token)
        assertThat(resultat1).hasSize(1)
        assertThat(resultat1[0].enhetId).isEqualTo("0403")
        assertThat(resultat1[0].navn).isEqualTo("NAV Hamar")

        // Andre kall med samme token - skal komme fra cache
        val resultat2 = modiaKlient.hentModiaEnheter(token)
        assertThat(resultat2).hasSize(1)
        assertThat(resultat2[0].enhetId).isEqualTo("0403")

        // Verifiser at API-et bare ble kalt én gang
        wireMock.verifyThat(1, getRequestedFor(urlEqualTo("/api/decorator")))
    }

    @Test
    fun `hentModiaEnheter henter på nytt for forskjellige tokens`(wmRuntimeInfo: WireMockRuntimeInfo) {
        val wireMock = wmRuntimeInfo.wireMock
        val accessTokenClient = mock<AccessTokenClient>()
        whenever(accessTokenClient.hentAccessToken("token-1")).thenReturn("access-token-1")
        whenever(accessTokenClient.hentAccessToken("token-2")).thenReturn("access-token-2")

        val modiaKlient = ModiaKlient("http://localhost:10002", accessTokenClient)

        wireMock.register(
            get("/api/decorator")
                .willReturn(
                    okJson(
                        """
                        {
                            "ident": "Z000000",
                            "fornavn": "Test",
                            "etternavn": "Testersen",
                            "enheter": [
                                {
                                    "enhetId": "0403",
                                    "navn": "NAV Hamar"
                                }
                            ]
                        }
                        """.trimIndent()
                    )
                )
        )

        // Kall med første token
        modiaKlient.hentModiaEnheter("token-1")

        // Kall med andre token - skal trigge nytt API-kall
        modiaKlient.hentModiaEnheter("token-2")

        // Verifiser at API-et ble kalt to ganger (én per unikt token)
        wireMock.verifyThat(2, getRequestedFor(urlEqualTo("/api/decorator")))
    }

    @Test
    fun `hentModiaEnheter returnerer tom liste ved 404`(wmRuntimeInfo: WireMockRuntimeInfo) {
        val wireMock = wmRuntimeInfo.wireMock
        val accessTokenClient = mock<AccessTokenClient>()
        whenever(accessTokenClient.hentAccessToken(any())).thenReturn("mocked-access-token")

        val modiaKlient = ModiaKlient("http://localhost:10002", accessTokenClient)

        wireMock.register(
            get("/api/decorator")
                .willReturn(
                    okJson(
                        """
                        {
                            "ident": "Z000000",
                            "fornavn": "Test",
                            "etternavn": "Testersen",
                            "enheter": []
                        }
                        """.trimIndent()
                    )
                )
        )

        val resultat = modiaKlient.hentModiaEnheter("test-token")

        assertThat(resultat).isEmpty()
    }

    @Test
    fun `cache returnerer samme instans ved gjentatte kall`(wmRuntimeInfo: WireMockRuntimeInfo) {
        val wireMock = wmRuntimeInfo.wireMock
        val accessTokenClient = mock<AccessTokenClient>()
        whenever(accessTokenClient.hentAccessToken(any())).thenReturn("mocked-access-token")

        val modiaKlient = ModiaKlient("http://localhost:10002", accessTokenClient)

        wireMock.register(
            get("/api/decorator")
                .willReturn(
                    okJson(
                        """
                        {
                            "ident": "Z000000",
                            "fornavn": "Test",
                            "etternavn": "Testersen",
                            "enheter": [
                                {
                                    "enhetId": "1001",
                                    "navn": "NAV Kristiansand"
                                },
                                {
                                    "enhetId": "0403",
                                    "navn": "NAV Hamar"
                                }
                            ]
                        }
                        """.trimIndent()
                    )
                )
        )

        val token = "same-token"

        // Gjør mange kall med samme token
        repeat(5) {
            val resultat = modiaKlient.hentModiaEnheter(token)
            assertThat(resultat).hasSize(2)
        }

        // Verifiser at API-et bare ble kalt én gang
        wireMock.verifyThat(1, getRequestedFor(urlEqualTo("/api/decorator")))
    }
}
