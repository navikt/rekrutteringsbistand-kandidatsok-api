package no.nav.toi.hullicv

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.node.ObjectNode
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.github.kittinunf.fuel.Fuel
import com.github.kittinunf.fuel.jackson.responseObject
import com.github.tomakehurst.wiremock.client.WireMock
import com.github.tomakehurst.wiremock.client.WireMock.equalToJson
import com.github.tomakehurst.wiremock.client.WireMock.ok
import com.github.tomakehurst.wiremock.client.WireMock.post
import com.github.tomakehurst.wiremock.junit5.WireMockRuntimeInfo
import com.github.tomakehurst.wiremock.junit5.WireMockTest
import com.nimbusds.jwt.SignedJWT
import no.nav.toi.LokalApp
import org.assertj.core.api.Assertions
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import java.time.LocalDate

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@WireMockTest(httpPort = 10000)
class HullICvTest {
    private val app = LokalApp()

    @BeforeAll
    fun setUp() {
        app.start()
    }

    @AfterAll
    fun tearDown() {
        app.close()
    }

    private fun WireMock.mockES(
        aktorId: String,
        datoForKall: LocalDate,
        finnerTreff: Boolean,
        veilederIdent: String = "Z999123",
        orgenhet: String = "5432"
    ) {
        register(
            post("/kandidater/_search?typed_keys=true")
                .withRequestBody(
                    equalToJson(
                    """
                        {
                        	"_source" : {
                              "includes" : [ "aktorId", "veilederIdent", "orgenhet" ]
                            },
                            "query" : {
                              "bool" : {
                                "must" : [ {
                        		  "bool" : {
                        		    "must" : [ {
                        			  "term" : {
                                        "aktorId" : { "value" : "$aktorId" }
                                      }
                                    } ]
                                  }
                                }, {
                                "bool" : {
                                  "should" : [ {
                                    "bool" : {
                                      "must" : [ {
                                        "bool" : {
                                          "should" : [ {
                                            "nested" : {
                                              "path" : "yrkeserfaring",
                                              "query" : {
                                                "bool" : {
                                                  "must" : [ {
                                                    "exists" : {
                                                      "field" : "yrkeserfaring"
                                                    }
                                                  } ]
                                                }
                                              }
                                            }
                                          }, {
                                            "nested" : {
                                              "path" : "utdanning",
                                              "query" : {
                                                "bool" : {
                                                  "must" : [ {
                                                    "exists" : {
                                                      "field" : "utdanning"
                                                    }
                                                  } ]
                                                }
                                              }
                                            }
                                          }, {
                                            "nested" : {
                                              "path" : "forerkort",
                                              "query" : {
                                                "bool" : {
                                                  "must" : [ {
                                                    "exists" : {
                                                      "field" : "forerkort"
                                                    }
                                                  } ]
                                                }
                                              }
                                            }
                                          }, {
                                            "exists" : {
                                              "field" : "kursObj"
                                            }
                                          }, {
                                            "exists" : {
                                              "field" : "fagdokumentasjon"
                                            }
                                          }, {
                                            "exists" : {
                                              "field" : "annenerfaringObj"
                                            }
                                          }, {
                                            "exists" : {
                                              "field" : "godkjenninger"
                                            }
                                          } ]
                                        }
                                      }, {
                                        "bool" : {
                                          "should" : [ {
                                            "bool" : {
                                              "must_not" : [ {
                                                "exists" : {
                                                  "field" : "perioderMedInaktivitet.startdatoForInnevarendeInaktivePeriode"
                                                }
                                              }, {
                                                "exists" : {
                                                  "field" : "perioderMedInaktivitet.sluttdatoerForInaktivePerioderPaToArEllerMer"
                                                }
                                              } ]
                                            }
                                          }, {
                                            "bool" : {
                                              "must" : [ {
                                                "exists" : {
                                                  "field" : "perioderMedInaktivitet.startdatoForInnevarendeInaktivePeriode"
                                                }
                                              }, {
                                                "bool" : {
                                                  "should" : [ {
                                                    "range" : {
                                                      "perioderMedInaktivitet.startdatoForInnevarendeInaktivePeriode" : {
                                                        "lte" : "${datoForKall.minusYears(2)}"
                                                      }
                                                    }
                                                  }, {
                                                    "range" : {
                                                      "perioderMedInaktivitet.sluttdatoerForInaktivePerioderPaToArEllerMer" : {
                                                        "gte" : "${datoForKall.minusYears(3)}"
                                                      }
                                                    }
                                                  } ]
                                                }
                                              } ]
                                            }
                                          } ]
                                        }
                                      } ]
                                    }
                                  } ]
                                }
                              } ]
                            }
                          },
                          "track_total_hits" : true
                        }
                    """.trimIndent()
                    )
                )
                .willReturn(
                    ok(
                            """
                                {
                                	"took": 7,
                                	"timed_out": false,
                                	"_shards": {
                                		"total": 3,
                                		"successful": 3,
                                		"skipped": 0,
                                		"failed": 0
                                	},
                                	"hits": {
                                		"total": {
                                			"value": ${if(finnerTreff) 1 else 0},
                                			"relation": "eq"
                                		},
                                		"max_score": ${if(finnerTreff) "11.852632" else null},
                                		"hits": [
                                ${if (finnerTreff) { 
                                    """
                                    {
                                        "_index": "kandidater-1",
                                        "_id": "PAM01bgnvfggj",
                                        "_score": 11.852632,
                                        "_source": {
                                            "aktorId": "$aktorId",
                                            "veilederIdent": "$veilederIdent",
                                            "orgenhet": "$orgenhet"
                                        }
                                    }
                                    """.trimIndent()
                                } else ""}
                                		]
                                	}
                                }
                            """.trimIndent()
                    )
                )
        )
        register(
            post("/kandidater/_search?typed_keys=true")
                .withRequestBody(
                    equalToJson(
                    """
                        {
                        	"_source" : {
                              "includes" : [ "aktorId", "veilederIdent", "orgenhet" ]
                            },
                            "query" : {
                              "bool" : {
                                "must" : [ {
                        		  "bool" : {
                        		    "must" : [ {
                        			  "term" : {
                                        "aktorId" : { "value" : "$aktorId" }
                                      }
                                    } ]
                                  }
                                } ]
                            }
                          },
                          "track_total_hits" : true
                        }
                    """.trimIndent()
                    )
                )
                .willReturn(
                    ok(
                            """
                                {
                                	"took": 7,
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
                                		"max_score": 11.852632,
                                		"hits": [
                                        {
                                            "_index": "kandidater-1",
                                            "_id": "PAM01bgnvfggj",
                                            "_score": 11.852632,
                                            "_source": {
                                                "aktorId": "$aktorId",
                                                "veilederIdent": "$veilederIdent",
                                                "orgenhet": "$orgenhet"
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

    @Test
    fun `Om personen ikke er i ES skal det returneres 404`(wmRuntimeInfo: WireMockRuntimeInfo) {
        val wireMock = wmRuntimeInfo.wireMock
        val aktorId = "1234567890123"
        val nå = LocalDate.now()
        wireMock.register(
            post("/kandidater/_search?typed_keys=true")
                .withRequestBody(
                    equalToJson(
                        """
                        {
                        	"_source" : {
                              "includes" : [ "aktorId", "veilederIdent", "orgenhet" ]
                            },
                            "query" : {
                              "bool" : {
                                "must" : [ {
                        		  "bool" : {
                        		    "must" : [ {
                        			  "term" : {
                                        "aktorId" : { "value" : "$aktorId" }
                                      }
                                    } ]
                                  }
                                } ]
                            }
                          },
                          "track_total_hits" : true
                        }
                    """.trimIndent()
                    )
                )
                .willReturn(
                    ok(
                        """
                                {
                                	"took": 7,
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
                                		"max_score": 11.852632,
                                		"hits": []
                                	}
                                }
                            """.trimIndent()
                    )
                )
        )
        val navIdent = "A123456"
        val token = app.lagToken(navIdent = navIdent, groups = listOf(LokalApp.arbeidsgiverrettet))
        val (_, response) = gjørKall(token, aktorId, nå)

        Assertions.assertThat(response.statusCode).isEqualTo(404)
    }

    @Test
    fun `Kan hente når det er hull i cv`(wmRuntimeInfo: WireMockRuntimeInfo) {
        val wireMock = wmRuntimeInfo.wireMock
        val aktorId = "1234567890123"
        val nå = LocalDate.now()
        wireMock.mockES(aktorId = aktorId, datoForKall = nå, finnerTreff = true)
        val navIdent = "A123456"
        val token = app.lagToken(navIdent = navIdent, groups = listOf(LokalApp.arbeidsgiverrettet))
        val (_, response, result) = gjørKall(token, aktorId, nå)

        Assertions.assertThat(response.statusCode).isEqualTo(200)
        Assertions.assertThat(result.get().asText()).isEqualTo("true")
    }

    @Test
    fun `Kan hente når det ikke er hull i cv`(wmRuntimeInfo: WireMockRuntimeInfo) {
        val wireMock = wmRuntimeInfo.wireMock
        val aktorId = "1234567890123"
        val nå = LocalDate.now()
        wireMock.mockES(aktorId = aktorId, datoForKall = nå, finnerTreff = false)
        val navIdent = "A123456"
        val token = app.lagToken(navIdent = navIdent, groups = listOf(LokalApp.arbeidsgiverrettet))
        val (_, response, result) = gjørKall(token, aktorId, nå)

        Assertions.assertThat(response.statusCode).isEqualTo(200)
        Assertions.assertThat(result.get().asText()).isEqualTo("false")
    }

    @Test
    fun `Om kall feiler under henting av cver fra elasticsearch, får vi HTTP 500`(wmRuntimeInfo: WireMockRuntimeInfo) {
        val wireMock = wmRuntimeInfo.wireMock
        val aktorId = "1234567890123"
        wireMock.register(
            post("/veilederkandidat_current/_search?typed_keys=true")
                .willReturn(
                    WireMock.notFound()
                )
        )
        val navIdent = "A123456"
        val token = app.lagToken(navIdent = navIdent, groups = listOf(LokalApp.arbeidsgiverrettet))
        val (_, response) = gjørKall(token, aktorId = aktorId, dato = LocalDate.now())

        Assertions.assertThat(response.statusCode).isEqualTo(500)
    }

    @Test
    fun `modia generell skal ikke ha tilgang til hullicv`(wmRuntimeInfo: WireMockRuntimeInfo) {
        val token = app.lagToken(groups = listOf(LokalApp.modiaGenerell))
        val aktorId = "1234567890123"
        val nå = LocalDate.now()
        wmRuntimeInfo.wireMock.mockES(aktorId, nå, finnerTreff = true)
        val (_, response, _) = gjørKall(token, aktorId, nå)

        Assertions.assertThat(response.statusCode).isEqualTo(403)
    }

    @Test
    fun `arbeidsgiverrettet skal ha tilgang til hullicv`(wmRuntimeInfo: WireMockRuntimeInfo) {
        val token = app.lagToken(groups = listOf(LokalApp.arbeidsgiverrettet))
        val aktorId = "1234567890123"
        val nå = LocalDate.now()
        wmRuntimeInfo.wireMock.mockES(aktorId, nå, finnerTreff = true)
        val (_, response, _) = gjørKall(token, aktorId, nå)

        Assertions.assertThat(response.statusCode).isEqualTo(200)
    }

    @Test
    fun `jobbsøkerrettet skal ha tilgang til hull dersom hen er kandidatens veileder`(wmRuntimeInfo: WireMockRuntimeInfo) {

        val veiledersIdent = "A000001"
        val annenIdent = "A000002"
        val veiledersOrgenhet = "1234"
        val feilVeiledersOrgenhet = "0000"
        val aktorId = "1234567890123"

        val wireMock = wmRuntimeInfo.wireMock
        wireMock.register(
            WireMock.get("/modia/api/decorator")
                .willReturn(
                    WireMock.okJson(
                        """
                {
                    "ident": "$veiledersIdent",
                    "navn": "Tull Tullersen",
                    "fornavn": "Tull",
                    "etternavn": "Tullersen",
                    "enheter": [
                                {
                                    "enhetId": "$veiledersOrgenhet",
                                    "navn": "NAV Feil"
                                }
                            ]
                }
            """.trimIndent()
                    )
                )
        )

        val nå = LocalDate.now()
        wireMock.mockES(aktorId = aktorId, datoForKall = nå, veilederIdent = veiledersIdent, orgenhet = feilVeiledersOrgenhet, finnerTreff = true)

        val token = app.lagToken(groups = listOf(LokalApp.Companion.jobbsøkerrettet))
        val (_, response, result) = gjørKall(token, aktorId, nå)

        Assertions.assertThat(response.statusCode).isEqualTo(200)
    }

    @Test
    fun `jobbsøkerrettet skal ikke ha tilgang til hull dersom hen ikke er kandidatens veileder`(wmRuntimeInfo: WireMockRuntimeInfo) {

        val veiledersIdent = "A000001"
        val annenIdent = "A000002"
        val veiledersOrgenhet = "1234"
        val feilVeiledersOrgenhet = "0000"
        val aktorId = "1234567890123"

        val wireMock = wmRuntimeInfo.wireMock
        wireMock.register(
            WireMock.get("/modia/api/decorator")
                .willReturn(
                    WireMock.okJson(
                        """
                {
                    "ident": "$veiledersIdent",
                    "navn": "Tull Tullersen",
                    "fornavn": "Tull",
                    "etternavn": "Tullersen",
                    "enheter": [
                                {
                                    "enhetId": "$veiledersOrgenhet",
                                    "navn": "NAV Feil"
                                }
                            ]
                }
            """.trimIndent()
                    )
                )
        )

        val nå = LocalDate.now()
        wireMock.mockES(aktorId = aktorId, datoForKall = nå, veilederIdent = annenIdent, orgenhet = feilVeiledersOrgenhet, finnerTreff = false)

        val token = app.lagToken(groups = listOf(LokalApp.Companion.jobbsøkerrettet))
        val (_, response, result) = gjørKall(token, aktorId, nå)

        Assertions.assertThat(response.statusCode).isEqualTo(403)
    }

    @Test
    fun `jobbsøkerrettet skal ha tilgang til cv dersom hen er tilknyttet kandidatens kontor`(wmRuntimeInfo: WireMockRuntimeInfo) {

        val veiledersIdent = "A000001"
        val feilVeilederIdent = "X100000"
        val veiledersOrgenhet = "0403"
        val feilVeiledersOrgenhet = "0000"
        val aktorId = "1234567890123"
        val nå = LocalDate.now()

        val wireMock = wmRuntimeInfo.wireMock
        wireMock.register(
            WireMock.get("/modia/api/decorator")
                .willReturn(
                    WireMock.okJson(
                        """
                {
                    "ident": "$veiledersIdent",
                    "navn": "Tull Tullersen",
                    "fornavn": "Tull",
                    "etternavn": "Tullersen",
                     "enheter": [
                                {
                                    "enhetId": "$veiledersOrgenhet",
                                    "navn": "NAV Hamar"
                                },
                                      {
                                    "enhetId": "1234",
                                    "navn": "NAV ANNET KONTOR"
                                }
                            ]
                }
            """.trimIndent()
                    )
                )
        )

        wireMock.mockES(aktorId = aktorId, datoForKall = nå, veilederIdent = feilVeilederIdent, orgenhet = veiledersOrgenhet, finnerTreff = true)

        val token = app.lagToken(groups = listOf(LokalApp.Companion.jobbsøkerrettet))
        val (_, response, result) = gjørKall(token, aktorId, nå)

        Assertions.assertThat(response.statusCode).isEqualTo(200)
    }

    @Test
    fun `jobbsøkerrettet skal ha tilgang til hullicv dersom hen ikke er kandidatens veileder og ikke er tilknyttet kandidatens kontor, men har også arbeidsgiverrettet rolle`(wmRuntimeInfo: WireMockRuntimeInfo) {
        val veiledersIdent = "A000001"
        val feilVeiledersIdent = "X100000"
        val veiledersOrgenhet = "1234"
        val feilVeiledersOrgenhet = "0000"
        val aktorId = "1234567890123"
        val nå = LocalDate.now()

        val wireMock = wmRuntimeInfo.wireMock
        wireMock.register(
            WireMock.get("/modia/api/decorator")
                .willReturn(
                    WireMock.okJson(
                        """
                {
                    "ident": "$veiledersIdent",
                    "navn": "Tull Tullersen",
                    "fornavn": "Tull",
                    "etternavn": "Tullersen",
                    "enheter": [
                                {
                                    "enhetId": "$veiledersOrgenhet",
                                    "navn": "NAV HAMAR"
                                }
                            ]
                }
            """.trimIndent()
                    )
                )
        )

        wireMock.mockES(aktorId = aktorId, datoForKall = nå, veilederIdent = feilVeiledersIdent, orgenhet = feilVeiledersOrgenhet, finnerTreff = true)

        val token = app.lagToken(groups = listOf(LokalApp.Companion.jobbsøkerrettet, LokalApp.Companion.arbeidsgiverrettet))
        val (_, response) = gjørKall(token, aktorId, nå)

        Assertions.assertThat(response.statusCode).isEqualTo(200)
    }


    @Test
    fun `utvikler skal ha tilgang til hull i cv`(wmRuntimeInfo: WireMockRuntimeInfo) {
        val token = app.lagToken(groups = listOf(LokalApp.utvikler))
        val aktorId = "1234567890123"
        val nå = LocalDate.now()
        wmRuntimeInfo.wireMock.mockES(aktorId, nå, finnerTreff = true)
        val (_, response, _) = gjørKall(token, aktorId, nå)

        Assertions.assertThat(response.statusCode).isEqualTo(200)
    }

    @Test
    fun `om man ikke har gruppetilhørighet skal man ikke få cv`(wmRuntimeInfo: WireMockRuntimeInfo) {
        val token = app.lagToken(groups = emptyList())
        val aktorId = "1234567890123"
        val nå = LocalDate.now()
        wmRuntimeInfo.wireMock.mockES(aktorId, nå, finnerTreff = true)
        val (_, response, _) = gjørKall(token, aktorId, nå)

        Assertions.assertThat(response.statusCode).isEqualTo(403)
    }

    private fun gjørKall(token: SignedJWT,aktorId: String, dato: LocalDate) = Fuel.post("http://localhost:8080/api/har-hull-i-cv")
        .body("""{"aktorId": "$aktorId", "dato": "$dato" }""")
        .header("Authorization", "Bearer ${token.serialize()}")
        .responseObject<JsonNode>()

}