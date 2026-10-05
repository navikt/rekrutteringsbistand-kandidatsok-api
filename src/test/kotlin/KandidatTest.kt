import java.net.URI
import java.net.http.HttpRequest
import java.net.http.HttpRequest.BodyPublishers
import java.net.http.HttpResponse.BodyHandlers
import no.nav.toi.testHttpClient
import com.github.tomakehurst.wiremock.client.WireMock
import com.github.tomakehurst.wiremock.junit5.WireMockRuntimeInfo
import com.github.tomakehurst.wiremock.junit5.WireMockTest
import com.nimbusds.jwt.SignedJWT
import no.nav.security.mock.oauth2.MockOAuth2Server
import no.nav.toi.App
import no.nav.toi.AuthenticationConfiguration
import no.nav.toi.RolleUuidSpesifikasjon
import no.nav.toi.kandidatsøk.assertStatuscodeEquals
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
        val response = testHttpClient.send(
            HttpRequest.newBuilder(URI("$endepunkt/arena-kandidatnr"))
                .POST(BodyPublishers.ofString("""{"fodselsnummer":"$fødselsnummer"}"""))
                .build(),
            BodyHandlers.ofString()
        )

        Assertions.assertThat(response.statusCode()).isEqualTo(401)
    }

    @Test
    fun `map fødselsnummer til arenakandidatnummer`(wmRuntimeInfo: WireMockRuntimeInfo) {
        val wireMock = wmRuntimeInfo.wireMock
        val fødselsnummer = "12312312312"
        val kandidatnummer = "PAM123456789"
        wireMock.register(
            WireMock.post("/kandidater/_search?typed_keys=true")
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
        val response = testHttpClient.send(
            HttpRequest.newBuilder(URI("$endepunkt/arena-kandidatnr"))
                .leggPåAutensiering()
                .POST(BodyPublishers.ofString("""{"fodselsnummer":"$fødselsnummer"}"""))
                .build(),
            BodyHandlers.ofString()
        )

        Assertions.assertThat(response.statusCode()).isEqualTo(200)
        JSONAssert.assertEquals(response.body(), """{"arenaKandidatnr": "$kandidatnummer"}""", true)
    }

    @Test
    fun `trenger token for å spørre endepunkt om navn`() {
        val fødselsnummer = "12312312312"
        val response = testHttpClient.send(
            HttpRequest.newBuilder(URI("$endepunkt/navn"))
                .POST(BodyPublishers.ofString("""{"fodselsnummer":"$fødselsnummer"}"""))
                .build(),
            BodyHandlers.ofString()
        )

        Assertions.assertThat(response.statusCode()).isEqualTo(401)
    }

    @Test
    fun `map fødselsnummer til navn`(wmRuntimeInfo: WireMockRuntimeInfo) {
        val wireMock = wmRuntimeInfo.wireMock
        mockAdressebeskyttelse(wireMock)
        val fødselsnummer = "12312312312"
        val fornavn = "Kjæreste"
        val etternavn = "Parodisk"
        mockNavnSøk(wireMock, fødselsnummer, fornavn, etternavn)
        val response = testHttpClient.send(
            HttpRequest.newBuilder(URI("$endepunkt/navn"))
                .leggPåAutensiering()
                .POST(BodyPublishers.ofString("""{"fodselsnummer":"$fødselsnummer"}"""))
                .build(),
            BodyHandlers.ofString()
        )

        Assertions.assertThat(response.statusCode()).isEqualTo(200)
        JSONAssert.assertEquals(
            response.body(),
            """{"fornavn": "$fornavn","etternavn": "$etternavn", "kilde":"REKRUTTERINGSBISTAND"}""",
            true
        )
    }

    @Test
    fun `map fødselsnummer til navn fra PDL om det ikke finnes i ES`(wmRuntimeInfo: WireMockRuntimeInfo) {
        val wireMock = wmRuntimeInfo.wireMock
        mockAdressebeskyttelse(wireMock)
        val fødselsnummer = "12312312312"
        val fornavn = "Kjæreste"
        val mellomnavn: String? = "Mellom"
        val etternavn = "Parodisk"
        wireMock.register(
            WireMock.post("/kandidater/_search?typed_keys=true")
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
                        "query": "query(${'$'}ident: ID!){ hentPerson(ident: ${'$'}ident) {navn(historikk: false) {fornavn mellomnavn etternavn}}}",
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
                          ]
                        }
                      }
                    }
                """.trimIndent()
                    )
                )
        )
        val response = testHttpClient.send(
            HttpRequest.newBuilder(URI("$endepunkt/navn"))
                .leggPåAutensiering()
                .POST(BodyPublishers.ofString("""{"fodselsnummer":"$fødselsnummer"}"""))
                .build(),
            BodyHandlers.ofString()
        )

        Assertions.assertThat(response.statusCode()).isEqualTo(200)
        JSONAssert.assertEquals(
            response.body(),
            """{"fornavn": "$fornavn $mellomnavn","etternavn": "$etternavn", "kilde":"PDL"}""",
            true
        )
    }

    @Test
    fun `fødselsnummer som ikke eksisterer i hverken pdl eller ES returnerer 404`(wmRuntimeInfo: WireMockRuntimeInfo) {
        val wireMock = wmRuntimeInfo.wireMock
        mockAdressebeskyttelse(wireMock)
        val fødselsnummer = "12312312312"
        val fornavn = "Kjæreste"
        val mellomnavn: String? = "Mellom"
        val etternavn = "Parodisk"
        wireMock.register(
            WireMock.post("/kandidater/_search?typed_keys=true")
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
                        "query": "query(${'$'}ident: ID!){ hentPerson(ident: ${'$'}ident) {navn(historikk: false) {fornavn mellomnavn etternavn}}}",
                        "variables": {
                            "ident":"$fødselsnummer"
                        }
                    }
                """.trimIndent(), false, false
                    )
                )
                .willReturn(WireMock.ok(
                    """
                        {
                          "errors": [
                            {
                              "message": "Fant ikke person",
                              "locations": [],
                              "path": [],
                              "extensions": {
                                "code": "not_found",
                                "details": null,
                                "classification": "ExecutionAborted"
                              }
                            }
                          ],
                          "data": {}
                        }
                    """.trimIndent()
                ))
        )
        val response = testHttpClient.send(
            HttpRequest.newBuilder(URI("$endepunkt/navn"))
                .leggPåAutensiering()
                .POST(BodyPublishers.ofString("""{"fodselsnummer":"$fødselsnummer"}"""))
                .build(),
            BodyHandlers.ofString()
        )

        Assertions.assertThat(response.statusCode()).isEqualTo(404)
    }

    @Test
    fun `skal feile om pdl returerer error-code som ikke er not_found`(wmRuntimeInfo: WireMockRuntimeInfo) {
        val wireMock = wmRuntimeInfo.wireMock
        val fødselsnummer = "12312312312"
        wireMock.register(
            WireMock.post("/kandidater/_search?typed_keys=true")
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
                        "query": "query(${'$'}ident: ID!){ hentPerson(ident: ${'$'}ident) {navn(historikk: false) {fornavn mellomnavn etternavn}}}",
                        "variables": {
                            "ident":"$fødselsnummer"
                        }
                    }
                """.trimIndent(), false, false
                    )
                )
                .willReturn(WireMock.ok(
                    """
                        {
                          "errors": [
                            {
                              "message": "Server error",
                              "locations": [],
                              "path": [],
                              "extensions": {
                                "code": "server_error",
                                "details": null,
                                "classification": "ExecutionAborted"
                              }
                            }
                          ],
                          "data": {}
                        }
                    """.trimIndent()
                ))
        )
        val statusCode = testHttpClient.send(
            HttpRequest.newBuilder(URI("$endepunkt/navn"))
                .leggPåAutensiering()
                .POST(BodyPublishers.ofString("""{"fodselsnummer":"$fødselsnummer"}"""))
                .build(),
            BodyHandlers.ofString()
        ).statusCode()

        Assertions.assertThat(statusCode).isEqualTo(500)
    }

    @Test
    fun `modia generell skal ikke ha tilgang til navn`() {
        val token = lagToken(groups = listOf(modiaGenerell))
        val response = gjørKallNavn("123", token)

        Assertions.assertThat(response.statusCode()).isEqualTo(403)
    }

    @Test
    fun `jobbsøkerrettet skal ha tilgang til navn`(wmRuntimeInfo: WireMockRuntimeInfo) {
        val wireMock = wmRuntimeInfo.wireMock
        mockAdressebeskyttelse(wireMock)
        val fødselsnummer = "12345678910"
        mockNavnSøk(wireMock, fødselsnummer, "N", "A")
        val token = lagToken(groups = listOf(jobbsøkerrettet))
        val response = gjørKallNavn(fødselsnummer, token)

        Assertions.assertThat(response.statusCode()).isEqualTo(200)
    }

    @Test
    fun `arbeidsgiverrettet skal ha tilgang til navn`(wmRuntimeInfo: WireMockRuntimeInfo) {
        val wireMock = wmRuntimeInfo.wireMock
        mockAdressebeskyttelse(wireMock)
        val fødselsnummer = "12345678910"
        mockNavnSøk(wireMock, fødselsnummer, "N", "A")
        val token = lagToken(groups = listOf(arbeidsgiverrettet))
        val response = gjørKallNavn(fødselsnummer, token)

        Assertions.assertThat(response.statusCode()).isEqualTo(200)
    }

    @Test
    fun `utvikler skal ha tilgang til navn`(wmRuntimeInfo: WireMockRuntimeInfo) {
        val wireMock = wmRuntimeInfo.wireMock
        mockAdressebeskyttelse(wireMock)
        val fødselsnummer = "12345678910"
        mockNavnSøk(wireMock, fødselsnummer, "N", "A")
        val token = lagToken(groups = listOf(utvikler))
        val response = gjørKallNavn(fødselsnummer, token)

        Assertions.assertThat(response.statusCode()).isEqualTo(200)
    }

    @Test
    fun `om man ikke har gruppetilhørighet skal man ikke få navn`(wmRuntimeInfo: WireMockRuntimeInfo) {
        val token = lagToken(groups = emptyList())
        val response = gjørKallNavn("123", token)

        Assertions.assertThat(response.statusCode()).isEqualTo(403)
    }

    @Test
    fun `modia generell skal ikke ha tilgang til kandidatnummer`() {
        val token = lagToken(groups = listOf(modiaGenerell))
        val response = gjørKallKandidatnummer("123", token)

        Assertions.assertThat(response.statusCode()).isEqualTo(403)
    }

    @Test
    fun `jobbsøkerrettet skal ha tilgang til kandidatnummer`(wmRuntimeInfo: WireMockRuntimeInfo) {
        val wireMock = wmRuntimeInfo.wireMock
        val fødselsnummer = "12345678910"
        mockHentKandidatnummer(wireMock, fødselsnummer, "123")
        val token = lagToken(groups = listOf(jobbsøkerrettet))
        val response = gjørKallKandidatnummer(fødselsnummer, token)

        Assertions.assertThat(response.statusCode()).isEqualTo(200)
    }

    @Test
    fun `arbeidsgiverrettet skal ha tilgang til kandidatnummer`(wmRuntimeInfo: WireMockRuntimeInfo) {
        val wireMock = wmRuntimeInfo.wireMock
        val fødselsnummer = "12345678910"
        mockHentKandidatnummer(wireMock, fødselsnummer, "123")
        val token = lagToken(groups = listOf(arbeidsgiverrettet))
        val response = gjørKallKandidatnummer(fødselsnummer, token)

        Assertions.assertThat(response.statusCode()).isEqualTo(200)
    }

    @Test
    fun `utvikler skal ha tilgang til kandidatnummer`(wmRuntimeInfo: WireMockRuntimeInfo) {
        val wireMock = wmRuntimeInfo.wireMock
        val fødselsnummer = "12345678910"
        mockHentKandidatnummer(wireMock, fødselsnummer, "123")
        val token = lagToken(groups = listOf(utvikler))
        val response = gjørKallKandidatnummer(fødselsnummer, token)

        Assertions.assertThat(response.statusCode()).isEqualTo(200)
    }

    @Test
    fun `om man ikke har gruppetilhørighet skal man ikke få kandidatnummer`(wmRuntimeInfo: WireMockRuntimeInfo) {
        val token = lagToken(groups = emptyList())
        val response = gjørKallKandidatnummer("123", token)

        Assertions.assertThat(response.statusCode()).isEqualTo(403)
    }

    @Test
    fun `hvis person har adressebeskyttelse skal det returneres 403`(wmRuntimeInfo: WireMockRuntimeInfo) {
        val wireMock = wmRuntimeInfo.wireMock
        val token = lagToken()
        mockAdressebeskyttelse(wireMock, true)

        val response = gjørKallNavn("123", token)

        assertStatuscodeEquals(response, 403)
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
        pdlScope = "http://localhost/.default",
        azureUrl = "http://localhost:$authPort/rest/isso/oauth2/access_token",
        modiaContextHolderUrl = "http://localhost/modia",
        modiaContextHolderScope = "http://localhost/.default",
        toiLivshendelseScope = "http://localhost/.default",
        toiLivshendelseUrl = "http://localhost:10000/livshendelse",
        rekrutteringstreffApiClientId = "rekrutteringstreff-api-client-id"
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

    private fun HttpRequest.Builder.leggPåAutensiering() =
        header("Authorization", "Bearer ${lagToken(navIdent = "A123456").serialize()}")

    private fun mockAdressebeskyttelse(wireMock: WireMock, harAdressebeskyttelse: Boolean = false) {
        wireMock.register(
            WireMock.post("/livshendelse/adressebeskyttelse")
                .willReturn(
                    WireMock.ok(
                        """
                            {
                                "harAdressebeskyttelse": $harAdressebeskyttelse
                            }
                        """.trimIndent()
                    )
                )
        )
    }

    private fun mockNavnSøk(
        wireMock: WireMock,
        fødselsnummer: String,
        fornavn: String,
        etternavn: String,
    ) {
        wireMock.register(
            WireMock.post("/kandidater/_search?typed_keys=true")
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

    fun gjørKallNavn(fødselsnummer: String, token: SignedJWT) = testHttpClient.send(
        HttpRequest.newBuilder(URI("$endepunkt/navn"))
            .header("Authorization", "Bearer ${token.serialize()}")
            .POST(BodyPublishers.ofString("""{"fodselsnummer":"$fødselsnummer"}"""))
            .build(),
        BodyHandlers.ofString()
    )

    fun gjørKallKandidatnummer(fødselsnummer: String, token: SignedJWT) = testHttpClient.send(
        HttpRequest.newBuilder(URI("$endepunkt/arena-kandidatnr"))
            .header("Authorization", "Bearer ${token.serialize()}")
            .POST(BodyPublishers.ofString("""{"fodselsnummer":"$fødselsnummer"}"""))
            .build(),
        BodyHandlers.ofString()
    )

    fun mockHentKandidatnummer(
        wireMock: WireMock,
        fødselsnummer: String,
        kandidatnummer: String
    ) =
        wireMock.register(
            WireMock.post("/kandidater/_search?typed_keys=true")
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
