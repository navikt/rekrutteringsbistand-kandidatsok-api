package no.nav.toi.kandidatsammendrag

import io.javalin.Javalin
import io.javalin.http.bodyAsClass
import io.javalin.openapi.*
import no.nav.toi.*

private const val endepunkt = "/api/navn"

private data class KandidatNavnOgGraderingRequestDto(
    val fodselsnummer: String,
)
private enum class Kilde { PDL }

private data class KandidatNavnOgGraderingResponsDto(
    val fornavn: String,
    val etternavn: String,
    val harAdressebeskyttelse: Boolean?,
    val kilde: Kilde
)

@OpenApi(
    summary = "Oppslag av navn for en enkelt person basert på fødselsnummer",
    operationId = endepunkt,
    tags = [],
    requestBody = OpenApiRequestBody([OpenApiContent(KandidatNavnOgGraderingRequestDto::class)]),
    responses = [OpenApiResponse("200", [OpenApiContent(KandidatNavnOgGraderingResponsDto::class)])],
    path = endepunkt,
    methods = [HttpMethod.POST]
)
fun Javalin.handleKandidatNavn(pdlKlient: PdlKlient) {
    post(endepunkt) { ctx ->
        ctx.authenticatedUser().verifiserAutorisasjon(Rolle.UTVIKLER, Rolle.ARBEIDSGIVER_RETTET, Rolle.JOBBSØKER_RETTET)

        val request = ctx.bodyAsClass<KandidatNavnOgGraderingRequestDto>()
        AuditLogg.loggOppslagNavn(request.fodselsnummer, ctx.authenticatedUser().navIdent)
        pdlKlient.hentFornavnOgEtternavn(request.fodselsnummer, ctx.authenticatedUser().jwt)?.let {
            ctx.json(KandidatNavnOgGraderingResponsDto(it.fornavn, it.etternavn, it.harAdressebeskyttelse, Kilde.PDL))
        } ?: ctx.status(404)
    }
}
