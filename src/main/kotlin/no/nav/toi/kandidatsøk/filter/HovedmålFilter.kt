package no.nav.toi.kandidatsøk.filter

import no.nav.toi.kandidatsøk.FilterParametre
import no.nav.toi.must_
import no.nav.toi.terms

fun List<Filter>.medHovedmålFilter(filterParametre: FilterParametre) = this + HovedmålFilter(filterParametre)

private class HovedmålFilter(parametre: FilterParametre): Filter {
    private val hovedMål = parametre.hovedmål ?: emptyList()

    override fun erAktiv() = hovedMål.isNotEmpty()

    override fun lagESFilterFunksjon(): FilterFunksjon = {
        must_ {
            terms("hovedmal" to hovedMål)
        }
    }
}