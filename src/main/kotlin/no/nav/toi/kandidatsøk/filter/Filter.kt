package no.nav.toi.kandidatsøk.filter

import org.opensearch.client.opensearch._types.query_dsl.BoolQuery
import org.opensearch.client.util.ObjectBuilder

typealias FilterFunksjon = BoolQuery.Builder.() -> ObjectBuilder<BoolQuery>

class Parameter(private val verdi: Any) {
    fun somString() = verdi as String
    fun somStringListe() = (verdi as List<*>).map { it as String }
    fun somInt() = verdi as Int
}

interface Filter {
    fun berikMedParameter(hentParameter: (String)->Parameter?)
    fun erAktiv(): Boolean
    fun lagESFilterFunksjon(): FilterFunksjon
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