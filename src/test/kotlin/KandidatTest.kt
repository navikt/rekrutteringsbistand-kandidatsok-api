import com.fasterxml.jackson.databind.JsonNode
import com.github.kittinunf.fuel.Fuel
import com.github.kittinunf.fuel.core.Request
import com.github.kittinunf.fuel.jackson.responseObject
import com.github.tomakehurst.wiremock.client.WireMock
import com.github.tomakehurst.wiremock.junit5.WireMockRuntimeInfo
import com.github.tomakehurst.wiremock.junit5.WireMockTest
import com.nimbusds.jwt.SignedJWT
import no.nav.security.mock.oauth2.MockOAuth2Server
import no.nav.toi.App
import no.nav.toi.AuthenticationConfiguration
import no.nav.toi.RolleUuidSpesifikasjon
import no.nav.toi.kandidatsammendrag.Gradering
import org.assertj.core.api.Assertions
import org.junit.jupiter.api.*
import org.skyscreamer.jsonassert.JSONAssert
import java.util.*

private const val endepunkt = "http://localhost:8080/api"

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@WireMockTest(httpPort = 10000)
class KandidatTest {
    private val authPort = 18306

    private val modiaGenerell = UUID.randomUUID().toString()
    private val jobbsøkerrettet = UUID.randomUUID().toString()
    private val arbeidsgiverrettet = UUID.randomUUID().toString()
    private val utvikler = UUID.randomUUID().toString()

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
    fun `trenger token for å spørre endepunkt om arenanummer`() {
        val fødselsnummer = "12312312312"
        val (_, response, result) = Fuel.post("$endepunkt/arena-kandidatnr")
            .body("""{"fodselsnummer":"$fødselsnummer"}""")
            .responseObject<JsonNode>()

        Assertions.assertThat(response.statusCode).isEqualTo(401)
    }

    @Test
    fun `map fødselsnummer til arenakandidatnummer`(wmRuntimeInfo: WireMockRuntimeInfo) {
        val wireMock = wmRuntimeInfo.wireMock
        val fødselsnummer = "12312312312"
        val kandidatnummer = "PAM123456789"
        wireMock.register(
            WireMock.post("/veilederkandidat_current/_search?typed_keys=true")
                .withRequestBody(
                    WireMock.equalToJson(
                        """{"query":{"term":{"fodselsnummer":{"value":"$fødselsnummer"}}},"_source":{"includes":["arenaKandidatnr"]}}""",
                        true,
                        false
                    )
                )
                .willReturn(
                    WireMock.ok(
                        """
                    {
                    	"took": 1,
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
                    		"max_score": 3.2580965,
                    		"hits": [
                    			{
                    				"_index": "veilederkandidat_os4",
                    				"_type": "_doc",
                    				"_id": "$kandidatnummer",
                    				"_score": 3.2580965,
                    				"_source": {
                    					"arenaKandidatnr": "$kandidatnummer"
                    				}
                    			}
                    		]
                    	}
                    }
                """.trimIndent()
                    )
                )
        )
        val (_, response, result) = Fuel.post("$endepunkt/arena-kandidatnr")
            .body("""{"fodselsnummer":"$fødselsnummer"}""")
            .leggPåAutensiering()
            .responseObject<JsonNode>()

        Assertions.assertThat(response.statusCode).isEqualTo(200)
        JSONAssert.assertEquals(result.get().toPrettyString(), """{"arenaKandidatnr": "$kandidatnummer"}""", true)
    }

    @Test
    fun `trenger token for å spørre endepunkt om navn`() {
        val fødselsnummer = "12312312312"
        val (_, response, result) = Fuel.post("$endepunkt/navn")
            .body("""{"fodselsnummer":"$fødselsnummer"}""")
            .responseObject<JsonNode>()

        Assertions.assertThat(response.statusCode).isEqualTo(401)
    }

    @Test
    fun `map fødselsnummer til navn`(wmRuntimeInfo: WireMockRuntimeInfo) {
        val wireMock = wmRuntimeInfo.wireMock
        val fødselsnummer = "12312312312"
        val fornavn = "Kjæreste"
        val etternavn = "Parodisk"
        mockNavnSøk(wireMock, fødselsnummer, fornavn, etternavn)
        val (_, response, result) = Fuel.post("$endepunkt/navn")
            .body("""{"fodselsnummer":"$fødselsnummer"}""")
            .leggPåAutensiering()
            .responseObject<JsonNode>()

        Assertions.assertThat(response.statusCode).isEqualTo(200)
        JSONAssert.assertEquals(
            result.get().toPrettyString(),
            """{"fornavn": "$fornavn","etternavn": "$etternavn", "harAdressebeskyttelse": null, "kilde":"REKRUTTERINGSBISTAND"}""",
            true
        )
    }

    @Test
    fun `map fødselsnummer til navn fra PDL om det ikke finnes i ES`(wmRuntimeInfo: WireMockRuntimeInfo) {
        val wireMock = wmRuntimeInfo.wireMock
        val fødselsnummer = "12312312312"
        val fornavn = "Kjæreste"
        val mellomnavn: String? = "Mellom"
        val etternavn = "Parodisk"
        wireMock.register(
            WireMock.post("/veilederkandidat_current/_search?typed_keys=true")
                .withRequestBody(
                    WireMock.equalToJson(
                        """{"query":{"term":{"fodselsnummer":{"value":"$fødselsnummer"}}},"_source":{"includes":["fornavn","etternavn"]}}""",
                        true,
                        false
                    )
                )
                .willReturn(
                    WireMock.ok(
                        """
                    {
                    	"took": 1,
                    	"timed_out": false,
                    	"_shards": {
                    		"total": 3,
                    		"successful": 3,
                    		"skipped": 0,
                    		"failed": 0
                    	},
                    	"hits": {
                    		"total": {
                    			"value": 0,
                    			"relation": "eq"
                    		},
                    		"max_score": 3.2580965,
                    		"hits": []
                    	}
                    }
                """.trimIndent()
                    )
                )
        )
        wireMock.register(
            WireMock.post("/pdl")
                .withRequestBody(
                    WireMock.equalToJson(
                        """
                    {
                        "query": "query(${'$'}ident: ID!){ hentPerson(ident: ${'$'}ident) {navn(historikk: false) {fornavn mellomnavn etternavn} adressebeskyttelse {gradering}}}",
                        "variables": {
                            "ident":"$fødselsnummer"
                        }
                    }
                """.trimIndent(), false, false
                    )
                )
                .willReturn(
                    WireMock.ok(
                        """
                    {
                      "data": {
                        "hentPerson": {
                          "navn": [
                            {
                              "fornavn": "$fornavn",
                              "mellomnavn": "$mellomnavn",
                              "etternavn": "$etternavn"
                            }
                          ],
                          "adressebeskyttelse": {
                            "gradering": "${Gradering.UGRADERT.name}"
                          }
                        }
                      }
                    }
                """.trimIndent()
                    )
                )
        )
        val (_, response, result) = Fuel.post("$endepunkt/navn")
            .body("""{"fodselsnummer":"$fødselsnummer"}""")
            .leggPåAutensiering()
            .responseObject<JsonNode>()

        Assertions.assertThat(response.statusCode).isEqualTo(200)
        JSONAssert.assertEquals(
            result.get().toPrettyString(),
            """{"fornavn": "$fornavn $mellomnavn","etternavn": "$etternavn", "harAdressebeskyttelse": false, "kilde":"PDL"}""",
            true
        )
    }

    @Test
    fun `fødselsnummer som ikke eksisterer i hverken pdl eller ES returnerer 404`(wmRuntimeInfo: WireMockRuntimeInfo) {
        val wireMock = wmRuntimeInfo.wireMock
        val fødselsnummer = "12312312312"
        val fornavn = "Kjæreste"
        val mellomnavn: String? = "Mellom"
        val etternavn = "Parodisk"
        wireMock.register(
            WireMock.post("/veilederkandidat_current/_search?typed_keys=true")
                .withRequestBody(
                    WireMock.equalToJson(
                        """{"query":{"term":{"fodselsnummer":{"value":"$fødselsnummer"}}},"_source":{"includes":["fornavn","etternavn"]}}""",
                        true,
                        false
                    )
                )
                .willReturn(
                    WireMock.ok(
                        """
                    {
                    	"took": 1,
                    	"timed_out": false,
                    	"_shards": {
                    		"total": 3,
                    		"successful": 3,
                    		"skipped": 0,
                    		"failed": 0
                    	},
                    	"hits": {
                    		"total": {
                    			"value": 0,
                    			"relation": "eq"
                    		},
                    		"max_score": 3.2580965,
                    		"hits": []
                    	}
                    }
                """.trimIndent()
                    )
                )
        )
        wireMock.register(
            WireMock.post("/pdl")
                .withRequestBody(
                    WireMock.equalToJson(
                        """
                    {
                        "query": "query(${'$'}ident: ID!){ hentPerson(ident: ${'$'}ident) {navn(historikk: false) {fornavn mellomnavn etternavn} adressebeskyttelse {gradering}}}",
                        "variables": {
                            "ident":"$fødselsnummer"
                        }
                    }
                """.trimIndent(), false, false
                    )
                )
                .willReturn(WireMock.notFound())
        )
        val (_, response, result) = Fuel.post("$endepunkt/navn")
            .body("""{"fodselsnummer":"$fødselsnummer"}""")
            .leggPåAutensiering()
            .responseObject<JsonNode>()

        Assertions.assertThat(response.statusCode).isEqualTo(404)
    }

    @Test
    fun `modia generell skal ikke ha tilgang til navn`() {
        val token = lagToken(groups = listOf(modiaGenerell))
        val (_, response) = gjørKallNavn("123", token)

        Assertions.assertThat(response.statusCode).isEqualTo(403)
    }

    @Test
    fun `jobbsøkerrettet skal ha tilgang til navn`(wmRuntimeInfo: WireMockRuntimeInfo) {
        val wireMock = wmRuntimeInfo.wireMock
        val fødselsnummer = "12345678910"
        mockNavnSøk(wireMock, fødselsnummer, "N", "A")
        val token = lagToken(groups = listOf(jobbsøkerrettet))
        val (_, response) = gjørKallNavn(fødselsnummer, token)

        Assertions.assertThat(response.statusCode).isEqualTo(200)
    }

    @Test
    fun `arbeidsgiverrettet skal ha tilgang til navn`(wmRuntimeInfo: WireMockRuntimeInfo) {
        val wireMock = wmRuntimeInfo.wireMock
        val fødselsnummer = "12345678910"
        mockNavnSøk(wireMock, fødselsnummer, "N", "A")
        val token = lagToken(groups = listOf(arbeidsgiverrettet))
        val (_, response) = gjørKallNavn(fødselsnummer, token)

        Assertions.assertThat(response.statusCode).isEqualTo(200)
    }

    @Test
    fun `utvikler skal ha tilgang til navn`(wmRuntimeInfo: WireMockRuntimeInfo) {
        val wireMock = wmRuntimeInfo.wireMock
        val fødselsnummer = "12345678910"
        mockNavnSøk(wireMock, fødselsnummer, "N", "A")
        val token = lagToken(groups = listOf(utvikler))
        val (_, response) = gjørKallNavn(fødselsnummer, token)

        Assertions.assertThat(response.statusCode).isEqualTo(200)
    }

    @Test
    fun `om man ikke har gruppetilhørighet skal man ikke få navn`(wmRuntimeInfo: WireMockRuntimeInfo) {
        val token = lagToken(groups = emptyList())
        val (_, response) = gjørKallNavn("123", token)

        Assertions.assertThat(response.statusCode).isEqualTo(403)
    }

    @Test
    fun `modia generell skal ikke ha tilgang til kandidatnummer`() {
        val token = lagToken(groups = listOf(modiaGenerell))
        val (_, response) = gjørKallKandidatnummer("123", token)

        Assertions.assertThat(response.statusCode).isEqualTo(403)
    }

    @Test
    fun `jobbsøkerrettet skal ha tilgang til kandidatnummer`(wmRuntimeInfo: WireMockRuntimeInfo) {
        val wireMock = wmRuntimeInfo.wireMock
        val fødselsnummer = "12345678910"
        mockHentKandidatnummer(wireMock, fødselsnummer, "123")
        val token = lagToken(groups = listOf(jobbsøkerrettet))
        val (_, response) = gjørKallKandidatnummer(fødselsnummer, token)

        Assertions.assertThat(response.statusCode).isEqualTo(200)
    }

    @Test
    fun `arbeidsgiverrettet skal ha tilgang til kandidatnummer`(wmRuntimeInfo: WireMockRuntimeInfo) {
        val wireMock = wmRuntimeInfo.wireMock
        val fødselsnummer = "12345678910"
        mockHentKandidatnummer(wireMock, fødselsnummer, "123")
        val token = lagToken(groups = listOf(arbeidsgiverrettet))
        val (_, response) = gjørKallKandidatnummer(fødselsnummer, token)

        Assertions.assertThat(response.statusCode).isEqualTo(200)
    }

    @Test
    fun `utvikler skal ha tilgang til kandidatnummer`(wmRuntimeInfo: WireMockRuntimeInfo) {
        val wireMock = wmRuntimeInfo.wireMock
        val fødselsnummer = "12345678910"
        mockHentKandidatnummer(wireMock, fødselsnummer, "123")
        val token = lagToken(groups = listOf(utvikler))
        val (_, response) = gjørKallKandidatnummer(fødselsnummer, token)

        Assertions.assertThat(response.statusCode).isEqualTo(200)
    }

    @Test
    fun `om man ikke har gruppetilhørighet skal man ikke få kandidatnummer`(wmRuntimeInfo: WireMockRuntimeInfo) {
        val token = lagToken(groups = emptyList())
        val (_, response) = gjørKallKandidatnummer("123", token)

        Assertions.assertThat(response.statusCode).isEqualTo(403)
    }

    private fun lagLokalApp() = App(
        port = 8080,
        authenticationConfigurations = listOf(
            AuthenticationConfiguration(
                audience = "1",
                issuer = "http://localhost:$authPort/default",
                jwksUri = "http://localhost:$authPort/default/jwks",
            )
        ),
        rolleUuidSpesifikasjon = RolleUuidSpesifikasjon(
            jobbsøkerrettet = UUID.fromString(jobbsøkerrettet),
            arbeidsgiverrettet = UUID.fromString(arbeidsgiverrettet),
            utvikler = UUID.fromString(utvikler),
        ),
        openSearchUsername = "user",
        openSearchPassword = "pass",
        openSearchUri = "http://localhost:10000",
        pdlUrl = "http://localhost:10000/pdl",
        azureSecret = "secret",
        azureClientId = "1",
        azureUrl = "http://localhost:$authPort/rest/isso/oauth2/access_token",
        pdlScope = "http://localhost/.default",
        modiaContextHolderScope = "http://localhost/.default",
        modiaContextHolderUrl = "http://localhost/modia"
    )

    private fun lagToken(
        issuerId: String = "http://localhost:$authPort/default",
        aud: String = "1",
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

    private fun Request.leggPåAutensiering() =
        header("Authorization", "Bearer ${lagToken(navIdent = "A123456").serialize()}")


    private fun mockNavnSøk(
        wireMock: WireMock,
        fødselsnummer: String,
        fornavn: String,
        etternavn: String,
    ) {
        wireMock.register(
            WireMock.post("/veilederkandidat_current/_search?typed_keys=true")
                .withRequestBody(
                    WireMock.equalToJson(
                        """{"query":{"term":{"fodselsnummer":{"value":"$fødselsnummer"}}},"_source":{"includes":["fornavn","etternavn"]}}""",
                        true,
                        false
                    )
                )
                .willReturn(
                    WireMock.ok(
                        """
                        {
                            "took": 1,
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
                                "max_score": 3.2580965,
                                "hits": [
                                    {
                                        "_index": "veilederkandidat_os4",
                                        "_type": "_doc",
                                        "_id": "PAM123456789",
                                        "_score": 3.2580965,
                                        "_source": {
                                            "fornavn": "$fornavn",
                                            "etternavn": "$etternavn"
                                        }
                                    }
                                ]
                            }
                        }
                    """.trimIndent()
                    )
                )
        )
    }

    fun gjørKallNavn(fødselsnummer: String, token: SignedJWT) = Fuel.post("$endepunkt/navn")
        .body("""{"fodselsnummer":"$fødselsnummer"}""")
        .header("Authorization", "Bearer ${token.serialize()}")
        .responseObject<com.fasterxml.jackson.databind.JsonNode>()

    fun gjørKallKandidatnummer(fødselsnummer: String, token: SignedJWT) = Fuel.post("$endepunkt/arena-kandidatnr")
        .body("""{"fodselsnummer":"$fødselsnummer"}""")
        .header("Authorization", "Bearer ${token.serialize()}")
        .responseObject<JsonNode>()

    fun mockHentKandidatnummer(
        wireMock: WireMock,
        fødselsnummer: String,
        kandidatnummer: String
    ) =
        wireMock.register(
            WireMock.post("/veilederkandidat_current/_search?typed_keys=true")
                .withRequestBody(
                    WireMock.equalToJson(
                        """{"query":{"term":{"fodselsnummer":{"value":"$fødselsnummer"}}},"_source":{"includes":["arenaKandidatnr"]}}""",
                        true,
                        false
                    )
                )
                .willReturn(
                    WireMock.ok(
                        """
                    {
                    	"took": 1,
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
                    		"max_score": 3.2580965,
                    		"hits": [
                    			{
                    				"_index": "veilederkandidat_os4",
                    				"_type": "_doc",
                    				"_id": "$kandidatnummer",
                    				"_score": 3.2580965,
                    				"_source": {
                    					"arenaKandidatnr": "$kandidatnummer"
                    				}
                    			}
                    		]
                    	}
                    }
                """.trimIndent()
                    )
                )
        )
}
