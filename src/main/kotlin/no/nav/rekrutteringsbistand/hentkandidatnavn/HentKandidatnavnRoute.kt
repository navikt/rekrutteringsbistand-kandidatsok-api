package no.nav.rekrutteringsbistand.hentkandidatnavn

import io.javalin.http.Context
import io.javalin.http.HandlerType
import io.javalin.http.bodyValidator
import io.javalin.openapi.*
import no.nav.AuditLogg
import no.nav.RouteHandler
import no.nav.authenticatedUser
import org.opensearch.client.opensearch.OpenSearchClient


class HentKandidatnavnRoute(
    private val openSearchClient: OpenSearchClient,
    private val pdlClient: PdlClient
) :
    RouteHandler {
    companion object {
        const val handlerPath = "api/hent-kandidatnavn"

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
    }

    override val path: String = handlerPath

    override val httpMethod: HandlerType = HandlerType.POST


    @OpenApi(
        summary = "Finn navn på person primært fra Rekrutteringsbistand sin Opensearch-instans for kandidater og sekundært fra Persondataløsningen (PDL)",
        operationId = handlerPath,
        tags = [],
        requestBody = OpenApiRequestBody([OpenApiContent(RequestDto::class)]),
        responses = [OpenApiResponse("200", [OpenApiContent(ResponseDto::class)])],
        path = handlerPath,
        methods = [HttpMethod.POST]
    )
    override fun handler(ctx: Context) {
        val validator =
            ctx.bodyValidator<RequestDto>().check({ it.erElleveSiffer() }, "Fnr må være elleve siffer")
        val fnr: String = validator.get().fnr

        AuditLogg.loggHentNavn(fnr, ctx.authenticatedUser().navIdent)
        ctx.json(ResponseDto("anyFornavn", "anyMellomnavn", "anyEtternavn", true, "anyKandidatnr-$fnr"))
    }


}
