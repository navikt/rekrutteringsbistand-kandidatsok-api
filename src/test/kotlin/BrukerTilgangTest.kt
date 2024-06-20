import com.fasterxml.jackson.databind.JsonNode
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
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import java.util.*
import java.util.stream.Stream

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@WireMockTest(httpPort = 10000)
class BrukerTilgangTest {
    private val authPort = 18306

    companion object {
        private val modiaGenerell = UUID.randomUUID().toString()
        private val jobbsøkerrettet = UUID.randomUUID().toString()
        private val arbeidsgiverrettet = UUID.randomUUID().toString()
        private val utvikler = UUID.randomUUID().toString()

        private val audience = "iden til applikasjonen"

        val veilederIdent = "A100000"
        val veiledersOrgenhet = "1234"
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

    enum class Tilgang(val uuid: String) {
        ModiaGenerell(modiaGenerell), Jobbsøkerrettet(jobbsøkerrettet), Arbeidsgiverrettet(arbeidsgiverrettet), Utvikler(
            utvikler
        );
    }

    enum class Kandidat(
        val veilederIdent: String,
        val orgEnhet: String
    ) {
        SammeKontorOgVeileder(veilederIdent, veiledersOrgenhet),
        SammeKontorForskjelligVeileder("z999999", veiledersOrgenhet),
        SammeVeilederForskjelligKontor(veilederIdent, "9876"),
        ForskjelligKontorOgVeileder("z999999", "9876"),
    }

    enum class Søkeparameter(val mockESFelt: String, val requestParameter: String){
        Fødselsnummer("fodselsnummer","fodselsnummer"),
        AktørId("aktorId","aktorid"),
        Kandidatnummer("kandidatnr","kandidatnr");
    }

    fun tilgangParametre() = Stream.of(
        listOf(Tilgang.ModiaGenerell, Kandidat.SammeKontorOgVeileder, 403),
        listOf(Tilgang.ModiaGenerell, Kandidat.SammeKontorForskjelligVeileder, 403),
        listOf(Tilgang.ModiaGenerell, Kandidat.SammeVeilederForskjelligKontor, 403),
        listOf(Tilgang.ModiaGenerell, Kandidat.ForskjelligKontorOgVeileder, 403),
        listOf(Tilgang.Jobbsøkerrettet, Kandidat.SammeKontorOgVeileder, 200),
        listOf(Tilgang.Jobbsøkerrettet, Kandidat.SammeKontorForskjelligVeileder, 200),
        listOf(Tilgang.Jobbsøkerrettet, Kandidat.SammeVeilederForskjelligKontor, 200),
        listOf(Tilgang.Jobbsøkerrettet, Kandidat.ForskjelligKontorOgVeileder, 403),
        listOf(Tilgang.Arbeidsgiverrettet, Kandidat.SammeKontorOgVeileder, 200),
        listOf(Tilgang.Arbeidsgiverrettet, Kandidat.SammeKontorForskjelligVeileder, 200),
        listOf(Tilgang.Arbeidsgiverrettet, Kandidat.SammeVeilederForskjelligKontor, 200),
        listOf(Tilgang.Arbeidsgiverrettet, Kandidat.ForskjelligKontorOgVeileder, 200),
        listOf(Tilgang.Utvikler, Kandidat.SammeKontorOgVeileder, 200),
        listOf(Tilgang.Utvikler, Kandidat.SammeKontorForskjelligVeileder, 200),
        listOf(Tilgang.Utvikler, Kandidat.SammeVeilederForskjelligKontor, 200),
        listOf(Tilgang.Utvikler, Kandidat.ForskjelligKontorOgVeileder, 200),
    ).flatMap { (tilgang, kandidat, statusCode) ->
        Stream.of(Søkeparameter.Fødselsnummer, Søkeparameter.AktørId, Søkeparameter.Kandidatnummer).map { søkeparameter ->
            Arguments.of(tilgang, kandidat, statusCode, søkeparameter)
        }
    }


    @ParameterizedTest
    @MethodSource("tilgangParametre")
    fun `tilgang på endepunkt`(
        tilgang: Tilgang,
        kandidat: Kandidat,
        statusCode: Int,
        søkeParameter: Søkeparameter,
        wmRuntimeInfo: WireMockRuntimeInfo
    ) {
        val wireMock = wmRuntimeInfo.wireMock
        mockES(wireMock, kandidat.orgEnhet, kandidat.veilederIdent, søkeParameter.mockESFelt)
        mockDecorator(wireMock)
        val token = lagToken(navIdent = veilederIdent, groups = listOf(tilgang.uuid))
        val (_, response, _) = Fuel.post("http://localhost:8080/api/brukertilgang")
            .body("""{"${søkeParameter.requestParameter}":"12345678910"}""")
            .header("Authorization", "Bearer ${token.serialize()}")
            .response()

        Assertions.assertThat(response.statusCode).isEqualTo(statusCode)
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
                                    "enhetId": "$veiledersOrgenhet",
                                    "navn": "NAV Hamar"
                                }
                            ]
                }
            """.trimIndent()
                    )
                )
        )
    }

    private fun mockES(wireMock: WireMock, orgEnhet: String, veilederIdent: String, mockESFelt: String) {
        wireMock.register(
            WireMock.post("/veilederkandidat_current/_search?typed_keys=true")
                .withRequestBody(
                    WireMock.equalToJson(
                        """{"_source":{"includes":["veilederIdent","orgenhet"]},"query":{"term":{"$mockESFelt":{"value":"12345678910"}}},"size":1}""".trimIndent()
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
                        "value": 1,
                        "relation": "eq"
                    },
                    "max_score": null,
                    "hits": [
                        {
                            "_index": "veilederkandidat_os4",
                            "_type": "_doc",
                            "_id": "PAM0yg2tn43a",
                            "_score": null,
                            "_source": {
                                "orgenhet": "$orgEnhet",
                                "veilederIdent": "$veilederIdent"
                            }
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