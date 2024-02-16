import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.github.kittinunf.fuel.Fuel
import com.github.kittinunf.fuel.jackson.responseObject
import com.github.tomakehurst.wiremock.client.WireMock.*
import com.github.tomakehurst.wiremock.junit5.WireMockRuntimeInfo
import com.github.tomakehurst.wiremock.junit5.WireMockTest
import no.nav.toi.App
import no.nav.toi.RolleUuidSpesifikasjon
import no.nav.security.mock.oauth2.MockOAuth2Server
import org.assertj.core.api.Assertions
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import java.util.*

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@WireMockTest(httpPort = 10000)
class KompetanseforslagTest {
    private val authPort = 18307

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
    fun `Kan hente kandidatsammendrag`(wmRuntimeInfo: WireMockRuntimeInfo) {
        val wireMock = wmRuntimeInfo.wireMock
        val esresponse = """
            {
            	"took": 19,
            	"timed_out": false,
            	"_shards": {
            		"total": 3,
            		"successful": 3,
            		"skipped": 0,
            		"failed": 0
            	},
            	"hits": {
            		"total": {
            			"value": 9,
            			"relation": "eq"
            		},
            		"max_score": null,
            		"hits": []
            	},
            	"aggregations": {
            		"kompetanse": {
            			"doc_count_error_upper_bound": 0,
            			"sum_other_doc_count": 2,
            			"buckets": [
            				{
            					"key": "Betong",
            					"doc_count": 2
            				},
            				{
            					"key": "Betongarbeid",
            					"doc_count": 2
            				},
            				{
            					"key": "Bransjekunnskap - tømrerarbeid",
            					"doc_count": 2
            				},
            				{
            					"key": "Byggarbeid",
            					"doc_count": 2
            				},
            				{
            					"key": "Bygging av vegger",
            					"doc_count": 2
            				},
            				{
            					"key": "Gulvlegging og tapetsering",
            					"doc_count": 2
            				},
            				{
            					"key": "Kompetanse innen tømrerfaget",
            					"doc_count": 2
            				},
            				{
            					"key": "Snekker- og tømrerarbeid",
            					"doc_count": 2
            				},
            				{
            					"key": "Takarbeid",
            					"doc_count": 2
            				},
            				{
            					"key": "Tømrer (AMO)",
            					"doc_count": 2
            				},
            				{
            					"key": "Administrere kommunikasjon med statlige organer innen næringsmiddelindustrien",
            					"doc_count": 1
            				},
            				{
            					"key": "Fange dyr i feller",
            					"doc_count": 1
            				}
            			]
            		}
            	}
            }
        """.trimIndent()

        wireMock.register(
            post("/veilederkandidat_current/_search?typed_keys=true")
                .withRequestBody(equalToJson("""
                  {
                      "aggregations": {
                        "kompetanse": {
                          "terms": {
                            "field": "kompetanseObj.kompKodeNavn.keyword",
                            "size": 12
                          }
                        }
                      },
                      "query": {
                        "bool": {
                          "should": [
                            {
                              "match": {
                                "yrkeJobbonskerObj.styrkBeskrivelse": {"query": "Mat og livsstils videograf"}
                              }
                            },
                            {
                              "match": {
                                "yrkeJobbonskerObj.styrkBeskrivelse": {"query":"Kokk"}
                              }
                            }
                          ]
                        }
                      },
                      "size": 0
                    }
                """.trimIndent()))
                .willReturn(
                    ok(CvTestRespons.responseOpenSearch(esresponse))
                )
        )

        val navIdent = "A123456"
        val token = lagToken(navIdent = navIdent)
        val (_, response, result) = Fuel.post("http://localhost:8080/api/kompetanseforslag")
            .body("""
                {
                  "yrker": [
                    {"yrke": "Mat og livsstils videograf"},
                    {"yrke": "Kokk"}
                  ]
                }
            """.trimIndent())
            .header("Authorization", "Bearer ${token.serialize()}")
            .responseObject<JsonNode>()

        Assertions.assertThat(response.statusCode).isEqualTo(200)
        Assertions.assertThat(result.get()).isEqualTo(ObjectMapper().readTree(
            """
              {
                "hits": {
                  "hits": [
                    {
                      "_source": {
                        "took": 19,
                        "timed_out": false,
                        "_shards": {
                          "total": 3,
                          "successful": 3,
                          "skipped": 0,
                          "failed": 0
                        },
                        "hits": {
                          "total": {
                            "value": 9,
                            "relation": "eq"
                          },
                          "max_score": null,
                          "hits": []
                        },
                        "aggregations": {
                          "kompetanse": {
                            "doc_count_error_upper_bound": 0,
                            "sum_other_doc_count": 2,
                            "buckets": [
                              {
                                "key": "Betong",
                                "doc_count": 2
                              },
                              {
                                "key": "Betongarbeid",
                                "doc_count": 2
                              },
                              {
                                "key": "Bransjekunnskap - tømrerarbeid",
                                "doc_count": 2
                              },
                              {
                                "key": "Byggarbeid",
                                "doc_count": 2
                              },
                              {
                                "key": "Bygging av vegger",
                                "doc_count": 2
                              },
                              {
                                "key": "Gulvlegging og tapetsering",
                                "doc_count": 2
                              },
                              {
                                "key": "Kompetanse innen tømrerfaget",
                                "doc_count": 2
                              },
                              {
                                "key": "Snekker- og tømrerarbeid",
                                "doc_count": 2
                              },
                              {
                                "key": "Takarbeid",
                                "doc_count": 2
                              },
                              {
                                "key": "Tømrer (AMO)",
                                "doc_count": 2
                              },
                              {
                                "key": "Administrere kommunikasjon med statlige organer innen næringsmiddelindustrien",
                                "doc_count": 1
                              },
                              {
                                "key": "Fange dyr i feller",
                                "doc_count": 1
                              }
                            ]
                          }
                        }
                      }
                    }
                  ]
                }
              }
            """.trimIndent()
        ))
    }

    private fun lagLokalApp() = App(
        port = 8080,
        azureAppClientId = "1",
        azureOpenidConfigIssuer = "http://localhost:$authPort/default",
        azureOpenidConfigJwksUri = "http://localhost:$authPort/default/jwks",
        rolleUuidSpesifikasjon = RolleUuidSpesifikasjon(
            modiaGenerell = UUID.fromString(modiaGenerell),
            modiaOppfølging = UUID.fromString(modiaOppfølging),
        ),
        openSearchUsername = "user",
        openSearchPassword = "pass",
        openSearchUri = "http://localhost:10000/opensearch",
    )

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
}