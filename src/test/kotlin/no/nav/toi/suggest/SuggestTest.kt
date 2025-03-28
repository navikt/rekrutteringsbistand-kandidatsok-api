package no.nav.toi.suggest

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
import org.assertj.core.api.Assertions
import org.json.JSONArray
import org.junit.jupiter.api.*
import org.skyscreamer.jsonassert.JSONAssert
import java.util.*


@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@WireMockTest(httpPort = 10000)
class SuggestTest {
    private val endepunkt = "http://localhost:8080/api/suggest"
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
    fun `Svar på ønsket yrke`(wmRuntimeInfo: WireMockRuntimeInfo) {
        val wireMock = wmRuntimeInfo.wireMock
        val register = wireMock.register(
            WireMock.post("/veilederkandidat_current/_search?typed_keys=true")
                .withRequestBody(
                    WireMock.equalToJson(
                        esRequest("kok", "yrkeJobbonskerObj.styrkBeskrivelse.completion"),
                        true,
                        false
                    )
                )
                .willReturn(
                    WireMock.ok(esSvar)
                )
        )
        val token = lagToken(navIdent = "A123456", groups = listOf(arbeidsgiverrettet))
        val (_, response, result) = Fuel.post(endepunkt)
            .body("""{"query":"kok","type":"ØnsketYrke"}""")
            .leggPåAutensiering(token)
            .responseObject<JsonNode>()

        Assertions.assertThat(response.statusCode).isEqualTo(200)
        JSONAssert.assertEquals(result.get().toPrettyString(), suggestSvar, true)
    }

    @Test
    fun `Svar på kompetanse`(wmRuntimeInfo: WireMockRuntimeInfo) {
        val wireMock = wmRuntimeInfo.wireMock
        wireMock.register(
            WireMock.post("/veilederkandidat_current/_search?typed_keys=true")
                .withRequestBody(
                    WireMock.equalToJson(
                        esRequest("prog", "samletKompetanseObj.samletKompetanseTekst.completion"),
                        true,
                        false
                    )
                )
                .willReturn(
                    WireMock.ok(esSvar)
                )
        )
        val token = lagToken(navIdent = "A123456", groups = listOf(arbeidsgiverrettet))
        val (_, response, result) = Fuel.post(endepunkt)
            .body("""{"query":"prog","type":"Kompetanse"}""")
            .leggPåAutensiering(token)
            .responseObject<JsonNode>()

        Assertions.assertThat(response.statusCode).isEqualTo(200)
        JSONAssert.assertEquals(result.get().toPrettyString(), suggestSvar, true)
    }

    @Test
    fun `Svar på arbeidserfaring`(wmRuntimeInfo: WireMockRuntimeInfo) {
        val wireMock = wmRuntimeInfo.wireMock
        wireMock.register(
            WireMock.post("/veilederkandidat_current/_search?typed_keys=true")
                .withRequestBody(
                    WireMock.equalToJson(
                        esRequest("keln", "yrkeserfaring.stillingstitlerForTypeahead"),
                        true,
                        false
                    )
                )
                .willReturn(
                    WireMock.ok(esSvar)
                )
        )
        val token = lagToken(navIdent = "A123456", groups = listOf(arbeidsgiverrettet))
        val (_, response, result) = Fuel.post(endepunkt)
            .body("""{"query":"keln","type":"Arbeidserfaring"}""")
            .leggPåAutensiering(token)
            .responseObject<JsonNode>()

        Assertions.assertThat(response.statusCode).isEqualTo(200)
        JSONAssert.assertEquals(result.get().toPrettyString(), suggestSvar, true)
    }

    @Test
    fun `Svar på språk`(wmRuntimeInfo: WireMockRuntimeInfo) {
        val wireMock = wmRuntimeInfo.wireMock
        wireMock.register(
            WireMock.post("/veilederkandidat_current/_search?typed_keys=true")
                .withRequestBody(
                    WireMock.equalToJson(
                        esRequest("nor", "sprak.sprakKodeTekst.completion"),
                        true,
                        false
                    )
                )
                .willReturn(
                    WireMock.ok(esSvar)
                )
        )
        val token = lagToken(navIdent = "A123456", groups = listOf(arbeidsgiverrettet))
        val (_, response, result) = Fuel.post(endepunkt)
            .body("""{"query":"nor","type":"Språk"}""")
            .leggPåAutensiering(token)
            .responseObject<JsonNode>()

        Assertions.assertThat(response.statusCode).isEqualTo(200)
        JSONAssert.assertEquals(result.get().toPrettyString(), suggestSvar, true)
    }

    @Test
    fun `modia generell skal ikke ha tilgang`() {
        val token = lagToken(groups = listOf(modiaGenerell))
        val (_, response, _) = gjørKall(token)

        Assertions.assertThat(response.statusCode).isEqualTo(403)
    }

    @Test
    fun `jobbsøkerrettet skal ha tilgang`(wmRuntimeInfo: WireMockRuntimeInfo) {
        val wireMock = wmRuntimeInfo.wireMock
        mockSuggest(wireMock)
        val token = lagToken(groups = listOf(jobbsøkerrettet))
        val (_, response) = gjørKall(token)

        Assertions.assertThat(response.statusCode).isEqualTo(200)
    }

    @Test
    fun `arbeidsgiverrettet skal ha tilgang`(wmRuntimeInfo: WireMockRuntimeInfo) {
        val wireMock = wmRuntimeInfo.wireMock
        mockSuggest(wireMock)
        val token = lagToken(groups = listOf(arbeidsgiverrettet))
        val (_, response) = gjørKall(token)

        Assertions.assertThat(response.statusCode).isEqualTo(200)
    }

    @Test
    fun `utvikler skal ha tilgang`(wmRuntimeInfo: WireMockRuntimeInfo) {
        val wireMock = wmRuntimeInfo.wireMock
        mockSuggest(wireMock)
        val token = lagToken(groups = listOf(utvikler))
        val (_, response) = gjørKall(token)

        Assertions.assertThat(response.statusCode).isEqualTo(200)
    }

    @Test
    fun `om man ikke har gruppetilhørighet skal man ikke ha tilgang`(wmRuntimeInfo: WireMockRuntimeInfo) {
        val token = lagToken(groups = emptyList())
        val (_, response) = gjørKall(token)

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
            utvikler = UUID.fromString(utvikler)
        ),
        openSearchUsername = "user",
        openSearchPassword = "pass",
        openSearchUri = "http://localhost:10000",
        pdlUrl = "http://localhost:10000/pdl",
        azureSecret = "secret",
        azureClientId = "1",
        pdlScope = "http://localhost/.default",
        azureUrl = "http://localhost:$authPort",
        modiaContextHolderUrl = "http://localhost:10000/modia",
        modiaContextHolderScope = "http://localhost/.default",
        toiLivshendelseScope = "http://localhost/.default",
        toiLivshendelseUrl = "http://localhost:10000/livshendelse"
    )

    private fun lagToken(
        issuerId: String = "http://localhost:$authPort/default",
        aud: String = "1",
        navIdent: String = "A000001",
        groups: List<String> = listOf(modiaGenerell),
        claims: Map<String, Any> = mapOf("NAVident" to navIdent, "groups" to groups),
    ) = authServer.issueToken(
        issuerId = issuerId,
        subject = "subject",
        audience = aud,
        claims = claims
    )

    private fun Request.leggPåAutensiering(token: SignedJWT) =
        header("Authorization", "Bearer ${token.serialize()}")

    private fun esRequest(prefix: String, field: String) = """
    {
      "suggest": {
        "forslag": {
          "prefix": "$prefix",
          "completion": {
            "field": "$field",
            "size": 15,
            "skip_duplicates": true
          }
        }
      },
      "_source": {
        "includes": ["doed"]
      }
    }
""".trimIndent()

    private val suggestSvar = JSONArray(listOf("Kokk","Kokk (skip)","Kokkeassistent","Kokkelærling"))

    // TODO: source burde vært false, og ingen source i svar.. unødvendig hack for å få det til å funke
    private val source = """
    {
      "aktorId": "2740905813038",
      "fodselsnummer": "01825999058",
      "fornavn": "Flink",
      "etternavn": "Kostnad",
      "fodselsdato": "1959-02-01",
      "fodselsdatoErDnr": false,
      "formidlingsgruppekode": "ARBS",
      "epostadresse": null,
      "mobiltelefon": null,
      "harKontaktinformasjon": false,
      "telefon": null,
      "statsborgerskap": null,
      "kandidatnr": "PAM017l0yhd38",
      "arenaKandidatnr": "PAM017l0yhd38",
      "beskrivelse": "",
      "samtykkeStatus": "G",
      "samtykkeDato": "2022-09-26T11:11:09.188+00:00",
      "adresselinje1": "Bjørkveien 3",
      "adresselinje2": "",
      "adresselinje3": "",
      "postnummer": "8300",
      "poststed": "Svolvær",
      "landkode": null,
      "kommunenummer": 1865,
      "kommunenummerkw": 1865,
      "kommunenummerstring": "1865",
      "fylkeNavn": "Nordland",
      "kommuneNavn": "Vågan",
      "disponererBil": null,
      "tidsstempel": "2023-08-09T11:49:03.425+00:00",
      "doed": false,
      "frKode": "0",
      "innsatsgruppe": "SPESIELT_TILPASSET_INNSATS",
      "hovedmal": "SKAFFE_ARBEID",
      "orgenhet": "1860",
      "navkontor": "NAV Lofoten",
      "fritattKandidatsok": null,
      "fritattAgKandidatsok": null,
      "utdanning": [],
      "fagdokumentasjon": [
        {
          "type": "Fagbrev/svennebrev",
          "tittel": "Fagbrev institusjonskokk"
        }
      ],
      "yrkeserfaring": [
        {
          "fraDato": "2013-12-31T23:00:00.000+00:00",
          "tilDato": "2021-04-30T22:00:00.000+00:00",
          "arbeidsgiver": "Radisson Blu Scandinavia",
          "styrkKode": "5120",
          "styrkKode4Siffer": "5120",
          "styrkKode3Siffer": "512",
          "stillingstittel": "Kokk",
          "stillingstitlerForTypeahead": [
            "Kokk",
            "Kafekokk",
            "Faglært kokk"
          ],
          "alternativStillingstittel": "Kokk",
          "sokeTitler": [
            "Crew",
            "Anretningshjelp",
            "Ryddehjelp (serveringssted)",
            "Kjøkkenhjelp",
            "Kokk",
            "Kitchen crew (ekstrahjelp)",
            "Kjøkkenassistent",
            "Kafekokk",
            "Faglært kokk"
          ],
          "organisasjonsnummer": null,
          "naceKode": null,
          "yrkeserfaringManeder": 88,
          "utelukketForFremtiden": false,
          "beskrivelse": "Lage mat til banketter og ala carte.",
          "sted": "Oslo"
        }
      ],
      "kompetanseObj": [
        {
          "fraDato": null,
          "kompKode": null,
          "kompKodeNavn": "Administrere kommunikasjon med statlige organer innen næringsmiddelindustrien",
          "sokeNavn": [
            "Administrere kommunikasjon med statlige organer innen næringsmiddelindustrien",
            "Administrere kommunikasjon med statlige organer innen næringsmiddelindustrien",
            "Koordinere aktiviteter med andre"
          ],
          "alternativtNavn": "Administrere kommunikasjon med statlige organer innen næringsmiddelindustrien",
          "beskrivelse": ""
        },
        {
          "fraDato": null,
          "kompKode": null,
          "kompKodeNavn": "I stand til å legge til, subtrahere, multiplisere og dividere for kassering",
          "sokeNavn": [
            "I stand til å legge til, subtrahere, multiplisere og dividere for kassering",
            "I stand til å legge til, subtrahere, multiplisere og dividere for kassering",
            "Betjene kasse",
            "Betjene kasseapparat",
            "Forståelse av tall",
            "Evne til å arbeide med tall",
            "Utføre numeriske beregninger",
            "Kalkulasjoner",
            "Kalkulasjon"
          ],
          "alternativtNavn": "I stand til å legge til, subtrahere, multiplisere og dividere for kassering",
          "beskrivelse": ""
        }
      ],
      "annenerfaringObj": [],
      "sertifikatObj": [],
      "forerkort": [],
      "sprak": [
        {
          "fraDato": null,
          "sprakKode": null,
          "sprakKodeTekst": "Norsk",
          "alternativTekst": "Norsk",
          "beskrivelse": "Muntlig: FOERSTESPRAAK Skriftlig: FOERSTESPRAAK",
          "ferdighetMuntlig": "FOERSTESPRAAK",
          "ferdighetSkriftlig": "FOERSTESPRAAK"
        },
        {
          "fraDato": null,
          "sprakKode": null,
          "sprakKodeTekst": "Engelsk",
          "alternativTekst": "Engelsk",
          "beskrivelse": "Muntlig: VELDIG_GODT Skriftlig: VELDIG_GODT",
          "ferdighetMuntlig": "VELDIG_GODT",
          "ferdighetSkriftlig": "VELDIG_GODT"
        }
      ],
      "kursObj": [],
      "vervObj": [],
      "geografiJobbonsker": [
        {
          "geografiKodeTekst": "Oslo",
          "geografiKode": "NO03"
        }
      ],
      "yrkeJobbonskerObj": [
        {
          "styrkKode": null,
          "styrkBeskrivelse": "Kokk",
          "sokeTitler": [
            "Kokk",
            "Kokk",
            "Kafekokk",
            "Faglært kokk",
            "Kitchen crew (ekstrahjelp)",
            "Kjøkkenassistent",
            "Ryddehjelp (serveringssted)",
            "Anretningshjelp",
            "Crew",
            "Kjøkkenhjelp"
          ],
          "primaertJobbonske": false
        }
      ],
      "omfangJobbonskerObj": [
        {
          "omfangKode": "HELTID",
          "omfangKodeTekst": "Heltid"
        }
      ],
      "ansettelsesformJobbonskerObj": [
        {
          "ansettelsesformKode": "FAST",
          "ansettelsesformKodeTekst": "Fast"
        }
      ],
      "arbeidstidsordningJobbonskerObj": [],
      "arbeidsdagerJobbonskerObj": [],
      "arbeidstidJobbonskerObj": [
        {
          "arbeidstidKode": "DAGTID",
          "arbeidstidKodeTekst": "Dagtid"
        }
      ],
      "samletKompetanseObj": [
        {
          "samletKompetanseTekst": "Administrere kommunikasjon med statlige organer innen næringsmiddelindustrien"
        },
        {
          "samletKompetanseTekst": "Administrere kommunikasjon med statlige organer innen næringsmiddelindustrien"
        },
        {
          "samletKompetanseTekst": "Koordinere aktiviteter med andre"
        },
        {
          "samletKompetanseTekst": "Betjene kasse"
        },
        {
          "samletKompetanseTekst": "Betjene kasseapparat"
        },
        {
          "samletKompetanseTekst": "Forståelse av tall"
        },
        {
          "samletKompetanseTekst": "Evne til å arbeide med tall"
        },
        {
          "samletKompetanseTekst": "Utføre numeriske beregninger"
        },
        {
          "samletKompetanseTekst": "Kalkulasjoner"
        },
        {
          "samletKompetanseTekst": "Kalkulasjon"
        },
        {
          "samletKompetanseTekst": "Fagbrev institusjonskokk"
        }
      ],
      "totalLengdeYrkeserfaring": 88,
      "synligForArbeidsgiverSok": false,
      "synligForVeilederSok": false,
      "oppstartKode": "ETTER_AVTALE",
      "veileder": "z994545",
      "veilederIdent": "z994545",
      "veilederVisningsnavn": null,
      "veilederEpost": null,
      "godkjenninger": [],
      "perioderMedInaktivitet": {
        "startdatoForInnevarendeInaktivePeriode": "2021-05-01T22:00:00.000+00:00",
        "sluttdatoerForInaktivePerioderPaToArEllerMer": [
          "2013-12-30T23:00:00.000+00:00"
        ]
      }
    }
""".trimIndent()

    private val esSvar = """
    {
    	"took": 2,
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
    	"suggest": {
    		"completion#forslag": [
    			{
    				"text": "kok",
    				"offset": 0,
    				"length": 3,
    				"options": [
    					{
    						"text": "Kokk",
    						"_index": "veilederkandidat_os4",
    						"_type": "_doc",
    						"_id": "PAM017l0yhd38",
    						"_score": 1.0,
                            "_source": $source
    					},
    					{
    						"text": "Kokk (skip)",
    						"_index": "veilederkandidat_os4",
    						"_type": "_doc",
    						"_id": "PAM01bbcr0xhd",
    						"_score": 1.0,
                            "_source": $source
    					},
    					{
    						"text": "Kokkeassistent",
    						"_index": "veilederkandidat_os4",
    						"_type": "_doc",
    						"_id": "PAM0xtfrwli5",
    						"_score": 1.0,
                            "_source": $source
    					},
    					{
    						"text": "Kokkelærling",
    						"_index": "veilederkandidat_os4",
    						"_type": "_doc",
    						"_id": "PAM019w4pxbus",
    						"_score": 1.0,
                            "_source": $source
    					}
    				]
    			}
    		]
    	}
    }
""".trimIndent()

    private fun mockSuggest(wireMock: WireMock) {
        wireMock.register(
            WireMock.post("/veilederkandidat_current/_search?typed_keys=true")
                .withRequestBody(
                    WireMock.equalToJson(
                        esRequest("kok", "yrkeJobbonskerObj.styrkBeskrivelse.completion"),
                        true,
                        false
                    )
                )
                .willReturn(
                    WireMock.ok(esSvar)
                )
        )
    }

    private fun gjørKall(token: SignedJWT) = Fuel.post(endepunkt)
        .body("""{"query":"kok","type":"ØnsketYrke"}""")
        .leggPåAutensiering(token)
        .responseObject<JsonNode>()




}
