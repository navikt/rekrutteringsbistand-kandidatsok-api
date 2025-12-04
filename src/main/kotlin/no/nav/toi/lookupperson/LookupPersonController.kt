package no.nav.toi.lookupperson

import com.fasterxml.jackson.databind.JsonNode
import io.javalin.Javalin
import io.javalin.http.Context
import io.javalin.http.bodyAsClass
import io.javalin.openapi.HttpMethod
import io.javalin.openapi.OpenApi
import io.javalin.openapi.OpenApiContent
import io.javalin.openapi.OpenApiRequestBody
import io.javalin.openapi.OpenApiResponse
import no.nav.toi.DEFAULT_INDEX
import no.nav.toi.OpensearchResponse
import no.nav.toi.Rolle
import no.nav.toi.authenticatedUser
import no.nav.toi.includes
import no.nav.toi.log
import no.nav.toi.query_
import no.nav.toi.search
import no.nav.toi.source_
import no.nav.toi.term_
import no.nav.toi.value
import org.opensearch.client.opensearch.OpenSearchClient
import org.opensearch.client.opensearch.core.SearchResponse

class LookupPersonController(
    private val openSearchClient: OpenSearchClient,
    javalin: Javalin,
) {
    companion object {
        private const val endepunkt = "/api/lookup-person"
    }

    init {
        javalin.post(endepunkt, handleLookupPersonFraOpensearch())
    }

    @OpenApi(
        summary = "Oppslag av persondata fra opensearch basert på kandidatnummer",
        operationId = endepunkt,
        tags = [],
        requestBody = OpenApiRequestBody([OpenApiContent(KandidatnrRequestDto::class)]),
        responses = [OpenApiResponse("200", [OpenApiContent(PersonInfoDto::class)])],
        path = endepunkt,
        methods = [HttpMethod.POST]
    )
    fun handleLookupPersonFraOpensearch(): (Context) -> Unit = { ctx ->
        log.info("Mottatt request for oppslag av persondata fra opensearch")
        val authenticatedUser = ctx.authenticatedUser()
        authenticatedUser.verifiserAutorisasjon(
            Rolle.ARBEIDSGIVER_RETTET,
            Rolle.UTVIKLER,
            Rolle.JOBBSØKER_RETTET)

        val kandidatnr = ctx.bodyAsClass<KandidatnrRequestDto>().kandidatnr
        val result = openSearchClient.lookupPersonInfo(kandidatnr)
        val kandidat = result.hits().hits().firstOrNull()?.source()

        if(kandidat == null) {
            log.info("Personen finnes ikke i opensearch")
            ctx.status(404).result("Fant ikke personen i opensearch")
        } else {
            val fornavn = kandidat.get("fornavn").asText()
            val etternavn = kandidat.get("etternavn").asText()
            val aktorId = kandidat.get("aktorId").asText()
            val fodselsdato = kandidat.get("fodselsdato").asText()

            val personInfo = PersonInfoDto(
                fornavn = fornavn,
                etternavn = etternavn,
                aktorId = aktorId,
                fodselsdato = fodselsdato
            )
            ctx.status(200)
            ctx.json(personInfo)
        }
    }

    private fun OpenSearchClient.lookupPersonInfo(kandidatnr: String): SearchResponse<JsonNode> =
        search<JsonNode> {
            index(DEFAULT_INDEX)
            query_ {
                term_ {
                    field("kandidatnr").value(kandidatnr)
                }
            }
            source_ {
                includes(
                    "fornavn",
                    "etternavn",
                    "aktorId",
                    "fodselsdato"
                )
            }
        }
}