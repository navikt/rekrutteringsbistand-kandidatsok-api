package no.nav.toi.kandidatsøk.filter

import no.nav.toi.AuthenticatedUser
import no.nav.toi.kandidatsøk.FilterParametre
import no.nav.toi.kandidatsøk.ModiaKlient
import no.nav.toi.kandidatsøk.filter.fritekstfilter.medFritekstFilter
import org.opensearch.client.opensearch._types.query_dsl.BoolQuery
import org.opensearch.client.util.ObjectBuilder

typealias FilterFunksjon = BoolQuery.Builder.() -> ObjectBuilder<BoolQuery>

class Parameter(private val verdi: Any) {
    fun somString() = verdi as String
    fun somStringListe() = (verdi as List<*>).map { it as String }
    fun somInt() = verdi as Int
}

interface Filter {
    fun erAktiv(): Boolean
    fun lagESFilterFunksjon(): FilterFunksjon
    fun auditLog(navIdent: String, returnerteFødselsnummer: String?) {}
}

fun søkeFilter(authenticatedUser: AuthenticatedUser, modiaKlient: ModiaKlient, filterParametre: FilterParametre) = listOf<Filter>()
    .medArbeidsønskefilter(filterParametre)
    .medInnsatsgruppeFilter(filterParametre)
    .medSpråkFilter(filterParametre)
    .medStedFilter(filterParametre)
    .medArbeidserfaringFilter(filterParametre)
    .medHovedmålFilter(filterParametre)
    .medKompetanseFilter(filterParametre)
    .medFørerkortFilter(filterParametre)
    .medUtdanningFilter(filterParametre)
    .medPrioritertMålgruppeFilter(filterParametre)
    .medFritekstFilter(filterParametre)
    .medPorteføljeFilter(filterParametre, authenticatedUser, modiaKlient)

class Valideringsfeil(msg: String): Exception(msg)