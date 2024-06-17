import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.github.kittinunf.fuel.Fuel
import com.github.kittinunf.fuel.jackson.responseObject
import com.github.tomakehurst.wiremock.client.WireMock
import com.github.tomakehurst.wiremock.client.WireMock.*
import com.github.tomakehurst.wiremock.junit5.WireMockRuntimeInfo
import com.github.tomakehurst.wiremock.junit5.WireMockTest
import com.nimbusds.jwt.SignedJWT
import no.nav.toi.App
import no.nav.toi.RolleUuidSpesifikasjon
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
                    ok(esresponse)
                )
        )

        val navIdent = "A123456"
        val token = app.lagToken(navIdent = navIdent, groups = listOf(LokalApp.arbeidsgiverrettet))
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
        val (_, response, result) = Fuel.post("http://localhost:8080/api/kompetanseforslag")
            .body("""
                {
                  "yrker": [
                    {"yrke": "dMat og livsstils videograf"},
                    {"yrke": "dKokk"}
                  ]
                }
            """.trimIndent())
            .header("Authorization", "Bearer ${token.serialize()}")
            .responseObject<JsonNode>()

        Assertions.assertThat(response.statusCode).isEqualTo(200)
        Assertions.assertThat(result.get()).isEqualTo(ObjectMapper().readTree(
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
        val (_, response, result) = Fuel.post("http://localhost:8080/api/kompetanseforslag")
            .body("""
                {
                  "yrker": [
                    {"yrke": "dMat og livsstils videograf"},
                    {"yrke": "dKokk"}
                  ]
                }
            """.trimIndent())
            .header("Authorization", "Bearer ${token.serialize()}")
            .responseObject<JsonNode>()

        Assertions.assertThat(response.statusCode).isEqualTo(500)
    }

    @Test
    fun feil_dersom_ikke_autentisert() {
        val (_, response, _) = Fuel.post("http://localhost:8080/api/kompetanseforslag")
            .body("""{"yrker": [{"yrke": "yrke"}]}""")
            .responseObject<JsonNode>()

        Assertions.assertThat(response.statusCode).isEqualTo(401)
    }

    @Test
    fun `modia generell skal ikke ha tilgang`() {
        val token = app.lagToken(groups = listOf(LokalApp.modiaGenerell))
        val (_, response, _) = gjørKall(token)

        Assertions.assertThat(response.statusCode).isEqualTo(403)
    }

    @Test
    fun `jobbsøkerrettet skal ikke ha tilgang`() {
        val token = app.lagToken(groups = listOf(LokalApp.jobbsøkerrettet))
        val (_, response) = gjørKall(token)

        Assertions.assertThat(response.statusCode).isEqualTo(403)
    }

    @Test
    fun `arbeidsgiverrettet skal ha tilgang`(wmRuntimeInfo: WireMockRuntimeInfo) {
        val wireMock = wmRuntimeInfo.wireMock
        mockKompetanseforslag(wireMock)
        val token = app.lagToken(groups = listOf(LokalApp.arbeidsgiverrettet))
        val (_, response) = gjørKall(token)

        Assertions.assertThat(response.statusCode).isEqualTo(200)
    }

    @Test
    fun `utvikler skal ha tilgang`(wmRuntimeInfo: WireMockRuntimeInfo) {
        val wireMock = wmRuntimeInfo.wireMock
        mockKompetanseforslag(wireMock)
        val token = app.lagToken(groups = listOf(LokalApp.utvikler))
        val (_, response) = gjørKall(token)

        Assertions.assertThat(response.statusCode).isEqualTo(200)
    }

    @Test
    fun `om man ikke har gruppetilhørighet skal man ikke ha tilgang`(wmRuntimeInfo: WireMockRuntimeInfo) {
        val token = app.lagToken(groups = emptyList())
        val (_, response) = gjørKall(token)

        Assertions.assertThat(response.statusCode).isEqualTo(403)
    }

    private fun gjørKall(token: SignedJWT) =  Fuel.post("http://localhost:8080/api/kompetanseforslag")
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
                    ok(esresponse)
                )
        )
    }
}