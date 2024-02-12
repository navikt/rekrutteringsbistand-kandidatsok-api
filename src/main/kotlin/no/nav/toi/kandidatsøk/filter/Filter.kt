package no.nav.toi.kandidatsøk.filter

import org.opensearch.client.opensearch._types.query_dsl.BoolQuery
import org.opensearch.client.util.ObjectBuilder

typealias FilterFunksjon = BoolQuery.Builder.() -> ObjectBuilder<BoolQuery>

interface Filter {
    fun berikMedParameter(hentParameter: (String)->String?)
    fun erAktiv(): Boolean
    fun lagESFilterFunksjon(): FilterFunksjon
}

fun søkeFilter() = listOf(Arbeidsønskefilter(), InnsatsgruppeFilter(),SpråkFilter(),StedFilter())