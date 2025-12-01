package no.nav.toi.hullicv

import com.fasterxml.jackson.databind.JsonNode
import io.javalin.Javalin
import io.javalin.http.HttpStatus
import io.javalin.http.bodyAsClass
import io.javalin.openapi.HttpMethod
import io.javalin.openapi.OpenApi
import io.javalin.openapi.OpenApiContent
import io.javalin.openapi.OpenApiRequestBody
import io.javalin.openapi.OpenApiResponse
import no.nav.toi.AuditLogg
import no.nav.toi.DEFAULT_INDEX
import no.nav.toi.Rolle
import no.nav.toi.authenticatedUser
import no.nav.toi.bool_
import no.nav.toi.includes
import no.nav.toi.kandidatsøk.ModiaKlient
import no.nav.toi.must_
import no.nav.toi.query_
import no.nav.toi.search
import no.nav.toi.should_
import no.nav.toi.source_
import no.nav.toi.term_
import no.nav.toi.trackTotalHits
import no.nav.toi.value
import org.opensearch.client.opensearch.OpenSearchClient
import org.opensearch.client.opensearch.core.SearchResponse
import java.time.LocalDate

private const val endepunkt = "/api/har-hull-i-cv"


private data class RequestDto(
    val aktorId: String,
    val dato: String
)

@OpenApi(
    summary = "Sjekk om hull i CV",
    operationId = endepunkt,
    tags = [],
    requestBody = OpenApiRequestBody([OpenApiContent(RequestDto::class)]),
    responses = [OpenApiResponse("200", [OpenApiContent(Boolean::class)])],
    path = endepunkt,
    methods = [HttpMethod.POST]
)
fun Javalin.handleHullICv(openSearchClient: OpenSearchClient, modiaKlient: ModiaKlient) {
    post(endepunkt) { ctx ->
        val authenticatedUser = ctx.authenticatedUser()
        val request = ctx.bodyAsClass<RequestDto>()
        authenticatedUser.verifiserAutorisasjon(Rolle.JOBBSØKER_RETTET, Rolle.ARBEIDSGIVER_RETTET, Rolle.UTVIKLER)
        val response = openSearchClient.hentKandidatFraES(request.aktorId)
        val kandidat = response.hits().hits().firstOrNull()
        if(kandidat == null) {
            ctx.status(HttpStatus.NOT_FOUND)
        } else {
            val source = kandidat.source()
            val orgEnhet = source?.get("orgenhet")?.asText()
            val veileder = source?.get("veilederIdent")?.asText()
            authenticatedUser.verifiserTilgangTilBruker(orgEnhet, veileder, modiaKlient) { permit ->
                AuditLogg.loggOppslagHullICv(request.aktorId, authenticatedUser.navIdent, permit)
            }
            val hullICvResponse = openSearchClient.harHullICv(request.aktorId, LocalDate.parse(request.dato))
            val harHullICv = hullICvResponse.hits().total().value() > 0
            ctx.json(harHullICv)
        }
    }
}

private fun OpenSearchClient.hentKandidatFraES(aktørId: String): SearchResponse<JsonNode> {
    return search<JsonNode> {
        index(DEFAULT_INDEX)
        query_ {
            bool_ {
                must_ {
                    bool_ {
                        must_ {
                            term_ {
                                field("aktorId")
                                value(aktørId)
                            }
                        }
                    }
                }
            }
        }
        source_ {
            includes(
                "aktorId",
                "veilederIdent",
                "orgenhet"
            )
        }
        trackTotalHits(true)
    }
}

private fun OpenSearchClient.harHullICv(aktørId: String, påDato: LocalDate): SearchResponse<JsonNode> {
    return search<JsonNode> {
        index(DEFAULT_INDEX)
        query_ {
            bool_ {
                must_ {
                    bool_ {
                        must_ {
                            term_ {
                                field("aktorId")
                                value(aktørId)
                            }
                        }
                    }
                }
                must_ {
                    bool_ {
                        hullICvEsFunksjon(påDato)()
                    }
                }
            }
        }
        source_ {
            includes(
                "aktorId",
                "veilederIdent",
                "orgenhet"
            )
        }
        trackTotalHits(true)
    }
}