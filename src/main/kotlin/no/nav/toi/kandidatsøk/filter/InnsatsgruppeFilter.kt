package no.nav.toi.kandidatsøk.filter

import no.nav.toi.kandidatsøk.FilterParametre
import no.nav.toi.must_
import no.nav.toi.terms

fun List<Filter>.medInnsatsgruppeFilter() = this + InnsatsgruppeFilter()

private class InnsatsgruppeFilter: Filter {
    private var innsatsgrupper = listOf("BATT","BFORM","IKVAL","VARIG")
    override fun berikMedParameter(filterParametre: FilterParametre) {
        innsatsgrupper = filterParametre.innsatsgruppe ?: innsatsgrupper
    }

    override fun erAktiv() = true

    override fun lagESFilterFunksjon(): FilterFunksjon = {
        must_ {
            terms("kvalifiseringsgruppekode" to innsatsgrupper)
        }
    }
}