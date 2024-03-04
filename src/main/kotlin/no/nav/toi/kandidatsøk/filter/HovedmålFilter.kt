package no.nav.toi.kandidatsøk.filter

import no.nav.toi.kandidatsøk.FilterParametre
import no.nav.toi.must_
import no.nav.toi.terms

fun List<Filter>.medHovedmålFilter() = this + HovedmålFilter()

private class HovedmålFilter: Filter {
    private var hovedMål = emptyList<String>()
    override fun berikMedParameter(filterParametre: FilterParametre) {
        hovedMål = filterParametre.hovedmål ?: emptyList()
    }

    override fun erAktiv() = hovedMål.isNotEmpty()

    override fun lagESFilterFunksjon(): FilterFunksjon = {
        must_ {
            terms("hovedmaalkode" to hovedMål)
        }
    }
}