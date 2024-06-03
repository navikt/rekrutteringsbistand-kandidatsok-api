package no.nav.toi.kandidatsøk.filter.porteføljefilter

import no.nav.toi.kandidatsøk.FilterParametre
import no.nav.toi.kandidatsøk.filter.Filter
import no.nav.toi.kandidatsøk.filter.FilterFunksjon
import no.nav.toi.must_
import no.nav.toi.term_
import no.nav.toi.value

fun List<Filter>.medMittKontorFilter(filterParametre: FilterParametre) = this + MittKontorFilter(filterParametre)

// TODO: Gjør private etter refaktorering
internal class MittKontorFilter(parametre: FilterParametre) : Porteføljetype {
    private val orgenhet = parametre.orgenhet ?: ""
    override fun lagESFilterFunksjon(): FilterFunksjon = {
        must_ {
            term_ {
                field("orgenhet")
                value(orgenhet)
            }
        }
    }
}