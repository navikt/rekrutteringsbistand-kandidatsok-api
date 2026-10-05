package no.nav.toi.kompetanseforslag

import java.net.URI
import java.net.http.HttpRequest
import java.net.http.HttpRequest.BodyPublishers
import java.net.http.HttpResponse.BodyHandlers
import no.nav.toi.testHttpClient
import no.nav.toi.testObjectMapper
import tools.jackson.databind.ObjectMapper
import com.github.tomakehurst.wiremock.client.WireMock
import com.github.tomakehurst.wiremock.client.WireMock.*
import com.github.tomakehurst.wiremock.junit5.WireMockRuntimeInfo
import com.github.tomakehurst.wiremock.junit5.WireMockTest
import com.nimbusds.jwt.SignedJWT
import no.nav.toi.LokalApp
import org.assertj.core.api.Assertions
import org.junit.jupiter.api.*
import java.util.*

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@WireMockTest(httpPort = 10000)
class KompetanseforslagTest {
    private val app = LokalApp()

    @BeforeAll
    fun setUp() {
        app.start()
    }

    @AfterAll
    fun tearDown() {
        app.close()
    }

    @Test
    fun `Kan hente kandidatsammendrag`(wmRuntimeInfo: WireMockRuntimeInfo) {
        val wireMock = wmRuntimeInfo.wireMock
        val esresponse = """
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
                        "value": 9,
                        "relation": "eq"
                    },
                    "max_score": null,
                    "hits": []
                },
                "aggregations": {
                    "sterms#kompetanse": {
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
            post("/kandidater/_search?typed_keys=true")
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
                    ok(esresponse)
                )
        )

        val navIdent = "A123456"
        val token = app.lagToken(navIdent = navIdent, groups = listOf(LokalApp.arbeidsgiverrettet))
        val response = testHttpClient.send(
            HttpRequest.newBuilder(URI("http://localhost:8080/api/kompetanseforslag"))
                .header("Authorization", "Bearer ${token.serialize()}")
                .POST(BodyPublishers.ofString("""
                {
                  "yrker": [
                    {"yrke": "Mat og livsstils videograf"},
                    {"yrke": "Kokk"}
                  ]
                }
            """.trimIndent()))
                .build(),
            BodyHandlers.ofString()
        )

        Assertions.assertThat(response.statusCode()).isEqualTo(200)
        Assertions.assertThat(testObjectMapper.readTree(response.body())).isEqualTo(ObjectMapper().readTree(
            """
              {
                  "aggregations": {
                    "kompetanse": {
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
        ))
    }

    @Test
    fun `Kan får tomt resultat om et ikke finnes kompetanseforslag`(wmRuntimeInfo: WireMockRuntimeInfo) {
        val wireMock = wmRuntimeInfo.wireMock
        val esresponse = """
            {
            	"took": 0,
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
            		"max_score": null,
            		"hits": []
            	},
            	"aggregations": {
            		"sterms#kompetanse": {
            			"doc_count_error_upper_bound": 0,
            			"sum_other_doc_count": 0,
            			"buckets": []
            		}
            	}
            }
        """.trimIndent()

        wireMock.register(
            post("/kandidater/_search?typed_keys=true")
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
                                "yrkeJobbonskerObj.styrkBeskrivelse": {"query": "dMat og livsstils videograf"}
                              }
                            },
                            {
                              "match": {
                                "yrkeJobbonskerObj.styrkBeskrivelse": {"query":"dKokk"}
                              }
                            }
                          ]
                        }
                      },
                      "size": 0
                    }
                """.trimIndent()))
                .willReturn(
                    ok(esresponse)
                )
        )

        val navIdent = "A123456"
        val token = app.lagToken(navIdent = navIdent, groups = listOf(LokalApp.arbeidsgiverrettet))
        val response = testHttpClient.send(
            HttpRequest.newBuilder(URI("http://localhost:8080/api/kompetanseforslag"))
                .header("Authorization", "Bearer ${token.serialize()}")
                .POST(BodyPublishers.ofString("""
                {
                  "yrker": [
                    {"yrke": "dMat og livsstils videograf"},
                    {"yrke": "dKokk"}
                  ]
                }
            """.trimIndent()))
                .build(),
            BodyHandlers.ofString()
        )

        Assertions.assertThat(response.statusCode()).isEqualTo(200)
        Assertions.assertThat(testObjectMapper.readTree(response.body())).isEqualTo(ObjectMapper().readTree(
            """
              {
                  "aggregations": {
                    "kompetanse": {
                      "buckets": []
                    }
                  }
                }
            """.trimIndent()
        ))
    }

    @Test
    fun `Om elasticsearch feiler, skal vi få http 500 feil`(wmRuntimeInfo: WireMockRuntimeInfo) {
        val wireMock = wmRuntimeInfo.wireMock


        wireMock.register(
            post("/kandidater/_search?typed_keys=true")
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
                                "yrkeJobbonskerObj.styrkBeskrivelse": {"query": "skal feile"}
                              }
                            },
                            {
                              "match": {
                                "yrkeJobbonskerObj.styrkBeskrivelse": {"query":"skal feile"}
                              }
                            }
                          ]
                        }
                      },
                      "size": 0
                    }
                """.trimIndent()))
                .willReturn(
                    notFound()
                )
        )

        val navIdent = "A123456"
        val token = app.lagToken(navIdent = navIdent, groups = listOf(LokalApp.arbeidsgiverrettet))
        val response = testHttpClient.send(
            HttpRequest.newBuilder(URI("http://localhost:8080/api/kompetanseforslag"))
                .header("Authorization", "Bearer ${token.serialize()}")
                .POST(BodyPublishers.ofString("""
                {
                  "yrker": [
                    {"yrke": "dMat og livsstils videograf"},
                    {"yrke": "dKokk"}
                  ]
                }
            """.trimIndent()))
                .build(),
            BodyHandlers.ofString()
        )

        Assertions.assertThat(response.statusCode()).isEqualTo(500)
    }

    @Test
    fun feil_dersom_ikke_autentisert() {
        val response = testHttpClient.send(
            HttpRequest.newBuilder(URI("http://localhost:8080/api/kompetanseforslag"))
                .POST(BodyPublishers.ofString("""{"yrker": [{"yrke": "yrke"}]}"""))
                .build(),
            BodyHandlers.ofString()
        )

        Assertions.assertThat(response.statusCode()).isEqualTo(401)
    }

    @Test
    fun `modia generell skal ikke ha tilgang`() {
        val token = app.lagToken(groups = listOf(LokalApp.modiaGenerell))
        val response = gjørKall(token)

        Assertions.assertThat(response.statusCode()).isEqualTo(403)
    }

    @Test
    fun `jobbsøkerettet skal ha tilgang`(wmRuntimeInfo: WireMockRuntimeInfo) {
        val wireMock = wmRuntimeInfo.wireMock
        mockKompetanseforslag(wireMock)
        val token = app.lagToken(groups = listOf(LokalApp.jobbsøkerrettet))
        val response = gjørKall(token)

        Assertions.assertThat(response.statusCode()).isEqualTo(200)
    }

    @Test
    fun `arbeidsgiverrettet skal ha tilgang`(wmRuntimeInfo: WireMockRuntimeInfo) {
        val wireMock = wmRuntimeInfo.wireMock
        mockKompetanseforslag(wireMock)
        val token = app.lagToken(groups = listOf(LokalApp.arbeidsgiverrettet))
        val response = gjørKall(token)

        Assertions.assertThat(response.statusCode()).isEqualTo(200)
    }

    @Test
    fun `utvikler skal ha tilgang`(wmRuntimeInfo: WireMockRuntimeInfo) {
        val wireMock = wmRuntimeInfo.wireMock
        mockKompetanseforslag(wireMock)
        val token = app.lagToken(groups = listOf(LokalApp.utvikler))
        val response = gjørKall(token)

        Assertions.assertThat(response.statusCode()).isEqualTo(200)
    }

    @Test
    fun `om man ikke har gruppetilhørighet skal man ikke ha tilgang`(wmRuntimeInfo: WireMockRuntimeInfo) {
        val token = app.lagToken(groups = emptyList())
        val response = gjørKall(token)

        Assertions.assertThat(response.statusCode()).isEqualTo(403)
    }

    private fun gjørKall(token: SignedJWT) =  testHttpClient.send(
        HttpRequest.newBuilder(URI("http://localhost:8080/api/kompetanseforslag"))
            .header("Authorization", "Bearer ${token.serialize()}")
            .POST(BodyPublishers.ofString("""
                {
                  "yrker": [
                    {"yrke": "Mat og livsstils videograf"},
                    {"yrke": "Kokk"}
                  ]
                }
            """.trimIndent()))
            .build(),
        BodyHandlers.ofString()
    )

    private fun mockKompetanseforslag(wireMock: WireMock) {
        val esresponse = """
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
                        "value": 9,
                        "relation": "eq"
                    },
                    "max_score": null,
                    "hits": []
                },
                "aggregations": {
                    "sterms#kompetanse": {
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
            post("/kandidater/_search?typed_keys=true")
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
                    ok(esresponse)
                )
        )
    }
}
