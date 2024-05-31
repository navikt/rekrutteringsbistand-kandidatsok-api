package no.nav.toi.kandidatsøk.filter.porteføljefilter

import no.nav.toi.AuthenticatedUser
import no.nav.toi.kandidatsøk.filter.Filter
import no.nav.toi.kandidatsøk.filter.FilterFunksjon
import no.nav.toi.must_
import no.nav.toi.term_
import no.nav.toi.value

fun List<Filter>.medMineBrukereFilter(authenticatedUser: AuthenticatedUser?) = this + MineBrukereFilter(authenticatedUser)

// TODO: Gjør private etter refaktorering
internal class MineBrukereFilter(private val authenticatedUser: AuthenticatedUser?) : Porteføljetype {
    override fun lagESFilterFunksjon(): FilterFunksjon = {
        must_ {
            term_ {
                field("veileder")
                value(authenticatedUser!!.navIdent)
                caseInsensitive(true)
            }
        }
    }
}