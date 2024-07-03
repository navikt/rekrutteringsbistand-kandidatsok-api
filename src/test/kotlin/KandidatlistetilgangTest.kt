import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.node.ArrayNode
import com.github.kittinunf.fuel.Fuel
import com.github.kittinunf.fuel.jackson.responseObject
import com.github.tomakehurst.wiremock.client.WireMock
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
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import java.util.*
import java.util.stream.Stream
import kotlin.math.max

private const val endepunkt = "http://localhost:8080/api/kandidatlistetilgang"

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@WireMockTest(httpPort = 10000)
class KandidatlistetilgangTest {
    private val authPort = 18306

    companion object {
        private val modiaGenerell = UUID.randomUUID().toString()
        private val jobbsøkerrettet = UUID.randomUUID().toString()
        private val arbeidsgiverrettet = UUID.randomUUID().toString()
        private val utvikler = UUID.randomUUID().toString()

        private val audience = "iden til applikasjonen"

        val veilederIdent = "A123456"
    }

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

    @ParameterizedTest
    @MethodSource("fungerendeTilganger")
    fun `sjekk endepunkt per tilgang`(tilgang: Tilgang, harTilgang: Boolean, wmRuntimeInfo: WireMockRuntimeInfo) {
        val wireMock = wmRuntimeInfo.wireMock

        if(harTilgang) {
            mockES(wireMock)
            mockDecorator(wireMock)
        }
        val token = lagToken(navIdent = veilederIdent, groups = listOf(tilgang.uuid))
        val (_, response, result) = Fuel.post(endepunkt)
            .body("""["PAM000kanse1","PAM000kanse4","PAMkanikkese","PAM000kanse3","PAMikkekontor","PAMikkebruker","PAM000kanse2"]""")
            .header("Authorization", "Bearer ${token.serialize()}")
            .responseObject<JsonNode>()

        if(harTilgang) {
            Assertions.assertThat(response.statusCode).isEqualTo(200)
            Assertions.assertThat(result.get().isArray).isTrue()
            Assertions.assertThat((result.get() as ArrayNode).map { it.asText() })
                .isEqualTo(listOf("PAM000kanse1", "PAM000kanse4", "PAM000kanse3", "PAM000kanse2"))
        } else {
            Assertions.assertThat(response.statusCode).isEqualTo(403)
        }
    }

    fun fungerendeTilganger() = Stream.of(
        Arguments.of(Tilgang.ModiaGenerell, false),
        Arguments.of(Tilgang.Jobbsøkerrettet, true),
        Arguments.of(Tilgang.Arbeidsgiverrettet, true),
        Arguments.of(Tilgang.Utvikler, true)
    )

    enum class Tilgang(val uuid: String) {
        ModiaGenerell(modiaGenerell), Jobbsøkerrettet(jobbsøkerrettet), Arbeidsgiverrettet(arbeidsgiverrettet), Utvikler(utvikler);
    }

    private fun mockDecorator(wireMock: WireMock) {
        wireMock.register(
            WireMock.get("/modia/api/decorator")
                .willReturn(
                    WireMock.okJson(
                        """
                {
                    "ident": "$veilederIdent",
                    "navn": "Tull Tullersen",
                    "fornavn": "Tull",
                    "etternavn": "Tullersen",
                     "enheter": [
                                {
                                    "enhetId": "1234",
                                    "navn": "NAV Hamar"
                                },
                                {
                                    "enhetId": "5678",
                                    "navn": "NAV Kristiansand"
                                }
                            ]
                }
            """.trimIndent()
                    )
                )
        )
    }

    private fun mockES(
        wireMock: WireMock,
    ) {
        wireMock.register(
            WireMock.post("/veilederkandidat_current/_search?typed_keys=true")
                .withRequestBody(
                    WireMock.equalToJson(
                        KandidatsøkRespons.kandidatlisteValideringQuery(KandidatsøkRespons.mineKontorerTerm, KandidatsøkRespons.mineBrukereTerm),
                        true,
                        false
                    )
                )
                .willReturn(
                    WireMock.ok("""
                        {
            "took": 10,
            "timed_out": false,
            "_shards": {
                "total": 3,
                "successful": 3,
                "skipped": 0,
                "failed": 0
            },
            "hits": {
                "total": {
                    "value": 108,
                    "relation": "eq"
                },
                "max_score": null,
                "hits": [
                    {
                        "_index": "veilederkandidat_os4",
                        "_type": "_doc",
                        "_id": "PAM000kanse1",
                        "_score": null,
                        "sort": [
                            1707134341159
                        ]
                    },
                    {
                        "_index": "veilederkandidat_os4",
                        "_type": "_doc",
                        "_id": "PAM000kanse2",
                        "_score": null,
                        "sort": [
                            1707134341160
                        ]
                    },
                    {
                        "_index": "veilederkandidat_os4",
                        "_type": "_doc",
                        "_id": "PAM000kanse3",
                        "_score": null,
                        "sort": [
                            1707134341161
                        ]
                    },
                    {
                        "_index": "veilederkandidat_os4",
                        "_type": "_doc",
                        "_id": "PAM000kanse4",
                        "_score": null,
                        "sort": [
                            1707134341162
                        ]
                    }
                ]
            }
        }
                    """.trimIndent())
                )
        )
    }

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
            jobbsøkerrettet = UUID.fromString(jobbsøkerrettet),
            arbeidsgiverrettet = UUID.fromString(arbeidsgiverrettet),
            utvikler = UUID.fromString(utvikler)
        ),
        openSearchUsername = "user",
        openSearchPassword = "pass",
        openSearchUri = "http://localhost:10000",
        pdlUrl = "http://localhost:10000/pdl",
        azureSecret = "secret",
        azureClientId = audience,
        azureUrl = "http://localhost:$authPort/rest/isso/oauth2/access_token",
        pdlScope = "http://localhost/.default",
        modiaContextHolderScope = "http://localhost/.default",
        modiaContextHolderUrl = "http://localhost:10000/modia"
    )

    private fun lagToken(
        issuerId: String = "http://localhost:$authPort/default",
        aud: String = audience,
        navIdent: String = "A000001",
        groups: List<String> = listOf(arbeidsgiverrettet),
        claims: Map<String, Any> = mapOf("NAVident" to navIdent, "groups" to groups),
        expiry: Long = 3600
    ) = authServer.issueToken(
        issuerId = issuerId,
        subject = "subject",
        audience = aud,
        claims = claims,
        expiry = expiry
    )
}