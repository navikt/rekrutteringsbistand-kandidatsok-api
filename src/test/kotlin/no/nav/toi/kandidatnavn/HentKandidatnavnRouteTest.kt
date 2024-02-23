package no.nav.toi.kandidatnavn

import CvTestRespons
import com.github.kittinunf.fuel.Fuel
import com.github.kittinunf.fuel.core.FuelError
import com.github.kittinunf.fuel.core.Request
import com.github.kittinunf.fuel.core.Response
import com.github.kittinunf.fuel.core.extensions.jsonBody
import com.github.kittinunf.result.Result
import com.github.tomakehurst.wiremock.client.WireMock
import com.github.tomakehurst.wiremock.junit5.WireMockRuntimeInfo
import com.github.tomakehurst.wiremock.junit5.WireMockTest
import no.nav.toi.LokalApp
import org.assertj.core.api.Assertions
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@WireMockTest(httpPort = 10000)
class HentKandidatnavnRouteTest {
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
    fun `Kan hente kandidatnavn`(wmRuntimeInfo: WireMockRuntimeInfo) {
        val wireMock = wmRuntimeInfo.wireMock
        val expectedToOpensearch =
            """{"_source":{"includes": ["fornavn","etternavn","kandidatnr"]},"query":{"term":{"fodselsnummer":{"value":"55555555555"}}}}"""
        val resultFromOpensearch = opensearchResult(_source)
        wireMock.register(
            WireMock.post("/veilederkandidat_current/_search?typed_keys=true")
                .withRequestBody(WireMock.equalToJson(expectedToOpensearch))
                .willReturn(
                    WireMock.ok(resultFromOpensearch)
                )
        )

        val requestBody = """{"fnr": "55555555555"}"""
        val (_, response, result) = sendRequest(requestBody)

        Assertions.assertThat(response.statusCode).isEqualTo(200)
        Assertions.assertThat(result.get()).isEqualTo(
            """
            {"fornavn":"Oppfyllende Korrekt Boble","etternavn":"Grense","synligIRekbis":true,"kandidatnr":"PAM0104tl64rq"}
            """.trimIndent()
        )
    }

    /*
    Gitt at kandidat finnes i Opensearch
    når sender request med kandidatens fnr
    så skal respons være 200 med kandidatens navn osv.
     */
    @Test
    fun `Gitt at kandidaten finnes i Opensearch skal respons være 200 og kandidatens navn`(wmRuntimeInfo: WireMockRuntimeInfo) {
        val wireMock = wmRuntimeInfo.wireMock
        wireMock.register(
            WireMock.post("/veilederkandidat_current/_search?typed_keys=true")
                .withRequestBody(WireMock.equalToJson("""{"query":{"term":{"kandidatnr":{"value":"PAM0xtfrwli5" }}},"size":1}"""))
                .willReturn(
                    WireMock.ok(CvTestRespons.responseOpenSearch("{}")) // TODO
                )
        )


    }


    /*
    Gitt at kandidat ikke finnes i Opensearch men finnes i PDL
    når sender request med kandidatens fnr
    så skal respons være 200 med kandidatens navn osv.
     */

    /*
    Gitt at kandidat ikke finnes i Opensearch og kall mot PDL feiler
    når sender request med kandidatens fnr
    så skal respons være 5xx server error
     */

    /*
    Gitt at kall mot Opensearch feiler
    når sender request med kandidatens fnr
    så skal respons være 5xx server error og vi gjør ikke noe kall mot ikke PDL
     */

    // TODO: Vil vi ha retries? Jeg tenker ja, hvis det koster lite, som jeg tror er tilfellet med resilience4j.

    @Test
    fun `Skal få 400 Bad Request gitt feil request body`() {
        val ikkeJson = ""
        val (_, responseIkkeJson, _) = sendRequest(ikkeJson)
        Assertions.assertThat(responseIkkeJson.statusCode).isEqualTo(400)

        val tomJson = "{}"
        val (_, responseTomJson, _) = sendRequest(tomJson)
        Assertions.assertThat(responseTomJson.statusCode).isEqualTo(400)

        val feilKey = """{"fnrrrrrrrrrr": "55555555555"}"""
        val (_, responseFeilKey, _) = sendRequest(feilKey)
        Assertions.assertThat(responseFeilKey.statusCode).isEqualTo(400)

        val feilValue = """{"fnr": false}"""
        val (_, responseFeilValue, _) = sendRequest(feilValue)
        Assertions.assertThat(responseFeilValue.statusCode).isEqualTo(400)
    }

    private fun sendRequest(body: String): Triple<Request, Response, Result<String, FuelError>> =
        Fuel.post("http://localhost:8080/api/hent-kandidatnavn")
            .jsonBody(body)
            .header("Authorization", "Bearer ${app.lagToken().serialize()}")
            .responseString()


    private val _source: String = """
        {
          "kandidatnr": "PAM0104tl64rq",
          "etternavn": "Grense",
          "fornavn": "Oppfyllende Korrekt Boble"
        }
    """.trimIndent()

    private fun opensearchResult(_source: String): String =
        """
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
                  "value": 1,
                  "relation": "eq"
                },
                "max_score": 3.4231763,
                "hits": [
                  {
                    "_index": "veilederkandidat_os4",
                    "_type": "_doc",
                    "_id": "PAM0104tl64rq",
                    "_score": 3.4231763,
                    "_source": $_source
                  }
                ]
              }
            }
        """.trimIndent()
}
