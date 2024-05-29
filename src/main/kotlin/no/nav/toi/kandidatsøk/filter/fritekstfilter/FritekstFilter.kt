package no.nav.toi.kandidatsøk.filter.fritekstfilter

import no.nav.toi.kandidatsøk.FilterParametre
import no.nav.toi.kandidatsøk.filter.Filter

fun List<Filter>.medFritekstFilter(filterParametre: FilterParametre) = this + FritekstFilter(filterParametre)

private class FritekstFilter(parametre: FilterParametre): Filter {
    private val søk: Søk = Søk(parametre.fritekst)

    override fun erAktiv() = true

    override fun lagESFilterFunksjon() = if(søk.erAktiv()) søk.lagESFilterFunksjon() else {
        {this}
    }

    override fun auditLog(navIdent: String, returnerteFødselsnummer: String?) {
        søk.auditLog(navIdent, returnerteFødselsnummer)
    }
}