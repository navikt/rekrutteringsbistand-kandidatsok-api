package no.nav.toi.kandidatnavn

import io.javalin.Javalin
import io.javalin.http.bodyValidator
import io.javalin.openapi.*
import no.nav.toi.AuditLogg
import no.nav.toi.authenticatedUser
import org.opensearch.client.opensearch.OpenSearchClient


private const val path = "api/hent-kandidatnavn"

data class RequestDto(val fnr: String) {
    fun erElleveSiffer(): Boolean =
        fnr.all { it.isDigit() } && fnr.length == 11
}

data class ResponseDto(
    val fornavn: String,
    val mellomnavn: String,
    val etternavn: String,
    val synligIRekbis: Boolean,
    val kandidatnr: String?
)


@OpenApi(
    summary = "Finn navn på person primært fra Rekrutteringsbistand sin Opensearch-instans for kandidater og sekundært fra Persondataløsningen (PDL)",
    operationId = path,
    tags = [],
    requestBody = OpenApiRequestBody([OpenApiContent(RequestDto::class)]),
    responses = [OpenApiResponse("200", [OpenApiContent(ResponseDto::class)])],
    path = path,
    methods = [HttpMethod.POST]
)
fun Javalin.handleKandidatnavn(openSearchClient: OpenSearchClient, pdlClient: PdlClient) {
    post(path) { ctx ->
        val validator =
            ctx.bodyValidator<RequestDto>().check({ it.erElleveSiffer() }, "Fnr må være elleve siffer")
        val fnr: String = validator.get().fnr

        AuditLogg.loggHentNavn(fnr, ctx.authenticatedUser().navIdent)
        ctx.json(ResponseDto("anyFornavn", "anyMellomnavn", "anyEtternavn", true, "anyKandidatnr-$fnr"))
    }
}
