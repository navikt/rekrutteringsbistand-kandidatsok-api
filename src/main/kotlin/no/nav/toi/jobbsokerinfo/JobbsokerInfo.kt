package no.nav.toi.jobbsokerinfo

import com.fasterxml.jackson.databind.JsonNode
import io.javalin.Javalin
import io.javalin.http.bodyAsClass
import io.javalin.openapi.*
import no.nav.toi.*
import org.opensearch.client.opensearch.OpenSearchClient
import org.opensearch.client.opensearch.core.SearchResponse
import java.time.LocalDate
import java.time.Period
import java.time.format.DateTimeParseException

private const val endepunkt = "/api/jobbsoker-info"
private const val MAKS_FNR_PER_KALL = 500

private data class JobbsokerInfoRequestDto(
    val fodselsnumre: List<String>,
)

private data class JobbsokerInfoDto(
    val fodselsnummer: String,
    val navkontor: String?,
    val veilederNavn: String?,
    val veilederNavIdent: String?,
    val alder: Int?,
    val innsatsgruppe: String?,
)

private data class JobbsokerInfoResponsDto(
    val jobbsokerInfo: List<JobbsokerInfoDto>,
)

@OpenApi(
    summary = "Bulk-oppslag av jobbsøkerinfo for en liste fødselsnummer",
    operationId = endepunkt,
    tags = [],
    requestBody = OpenApiRequestBody(
        [OpenApiContent(
            from = JobbsokerInfoRequestDto::class,
            example = """{ "fodselsnumre": ["11111111111", "22222222222"] }"""
        )]
    ),
    responses = [
        OpenApiResponse(
            status = "200",
            content = [OpenApiContent(
                from = JobbsokerInfoResponsDto::class,
                example = """
                {
                    "jobbsokerInfo": [
                        {
                            "fodselsnummer": "11111111111",
                            "navkontor": "Nav Testkontor",
                            "veilederNavn": "Testveileder",
                            "veilederNavIdent": "TEST_IDENT",
                            "alder": 35,
                            "innsatsgruppe": "SITUASJONSBESTEMT_INNSATS"
                        }
                    ]
                }
                """
            )]
        )
    ],
    path = endepunkt,
    methods = [HttpMethod.POST]
)
fun Javalin.handleJobbsokerInfo(openSearchClient: OpenSearchClient) {
    post(endepunkt) { ctx ->
        ctx.authenticatedUser().verifiserAutorisasjon(
            Rolle.JOBBSØKER_RETTET,
            Rolle.ARBEIDSGIVER_RETTET,
            Rolle.UTVIKLER,
        )

        val request = ctx.bodyAsClass<JobbsokerInfoRequestDto>()
        val fodselsnumre = request.fodselsnumre.distinct()

        if (fodselsnumre.isEmpty()) {
            ctx.json(JobbsokerInfoResponsDto(emptyList()))
            return@post
        }
        if (fodselsnumre.size > MAKS_FNR_PER_KALL) {
            throw IllegalArgumentException("Maks $MAKS_FNR_PER_KALL fødselsnumre per kall (mottok ${fodselsnumre.size}).")
        }

        val treff = openSearchClient.hentJobbsokerInfo(fodselsnumre)
            .hits().hits().mapNotNull { it.source() }
            .associateBy { it.get("fodselsnummer").asText() }

        val jobbsokerInfo = fodselsnumre.map { fnr ->
            val source = treff[fnr]
            JobbsokerInfoDto(
                fodselsnummer = fnr,
                navkontor = source?.get("navkontor")?.takeIf { !it.isNull }?.asText(),
                veilederNavn = source?.get("veilederVisningsnavn")?.takeIf { !it.isNull }?.asText(),
                veilederNavIdent = source?.get("veilederIdent")?.takeIf { !it.isNull }?.asText(),
                alder = source?.get("fodselsdato")?.takeIf { !it.isNull }?.asText()?.let(::beregnAlder),
                innsatsgruppe = source?.get("innsatsgruppe")?.takeIf { !it.isNull }?.asText(),
            )
        }

        ctx.json(JobbsokerInfoResponsDto(jobbsokerInfo))
    }
}

private fun OpenSearchClient.hentJobbsokerInfo(fodselsnumre: List<String>): SearchResponse<JsonNode> =
    search<JsonNode> {
        index(DEFAULT_INDEX)
        query_ {
            terms_("fodselsnummer" to fodselsnumre)
        }
        source_ {
            includes(
                "fodselsnummer",
                "navkontor",
                "veilederVisningsnavn",
                "veilederIdent",
                "fodselsdato",
                "innsatsgruppe",
            )
        }
        size(fodselsnumre.size)
    }

private fun beregnAlder(fodselsdato: String): Int? = try {
    val alder = Period.between(LocalDate.parse(fodselsdato), LocalDate.now()).years
    alder.takeIf { it >= 0 }
} catch (_: DateTimeParseException) {
    null
}
