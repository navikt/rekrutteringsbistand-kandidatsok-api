package no.nav.toi.lookupcv

import com.fasterxml.jackson.databind.JsonNode
import io.javalin.Javalin
import io.javalin.http.bodyAsClass
import io.javalin.openapi.*
import no.nav.toi.*
import org.opensearch.client.opensearch.OpenSearchClient
import org.opensearch.client.opensearch._types.FieldValue
import org.opensearch.client.opensearch.core.SearchResponse

private const val endepunktLookupCv = "/api/lookup-cv"

private data class LookupCvParameters(
    val kandidatnr: String,
)

@OpenApi(
    summary = "Oppslag av hele CVen til en enkelt person basert på kandidatnummer",
    operationId = endepunktLookupCv,
    tags = [],
    requestBody = OpenApiRequestBody([OpenApiContent(LookupCvParameters::class)]),
    responses = [OpenApiResponse("200", [OpenApiContent(OpensearchResponse::class)])],
    path = endepunktLookupCv,
    methods = [HttpMethod.POST]
)
fun Javalin.lookupCvHandler(openSearchClient: OpenSearchClient) {
    post(endepunktLookupCv) { ctx ->
        val lookupCvParameters = ctx.bodyAsClass<LookupCvParameters>()
        val result = openSearchClient.lookupCv(lookupCvParameters)
        val fodselsnummer = result.hits().hits().firstOrNull()?.source()?.get("fodselsnummer")?.asText()
        if (fodselsnummer != null) {
            AuditLogg.loggOppslagCv(fodselsnummer, ctx.authenticatedUser().navIdent)
        }
        ctx.json(result.toResponseJson())
    }
}

private fun OpenSearchClient.lookupCv(params: LookupCvParameters): SearchResponse<JsonNode> =
    search<JsonNode> {
        index(DEFAULT_INDEX)
        query_ {
            term_ {
                field("kandidatnr").value(FieldValue.of(params.kandidatnr))
            }
        }
        size(1)
    }