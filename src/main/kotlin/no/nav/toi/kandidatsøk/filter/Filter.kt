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
    fun berikMedParameter(filterParametre: FilterParametre)
    fun erAktiv(): Boolean
    fun lagESFilterFunksjon(): FilterFunksjon
    fun auditLog(navIdent: String, returnerteFødselsnummer: String?) {}
    fun berikMedAuthenticatedUser(authenticatedUser: AuthenticatedUser) {}
    fun berikMedModiaKlient(modiaKlient: ModiaKlient) {}
}

fun søkeFilter() = listOf<Filter>()
    .medArbeidsønskefilter()
    .medInnsatsgruppeFilter()
    .medSpråkFilter()
    .medStedFilter()
    .medArbeidserfaringFilter()
    .medHovedmålFilter()
    .medKompetanseFilter()
    .medFørerkortFilter()
    .medUtdanningFilter()
    .medPrioritertMålgruppeFilter()
    .medFritekstFilter()
    .medPorteføljeFilter()

class Valideringsfeil(msg: String): Exception(msg)