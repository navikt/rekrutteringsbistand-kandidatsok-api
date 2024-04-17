package no.nav.toi.kandidatsøk.filter.fritekstfilter

import no.nav.toi.kandidatsøk.FilterParametre
import no.nav.toi.kandidatsøk.filter.Filter

fun List<Filter>.medFritekstFilter() = this + FritekstFilter()

private class FritekstFilter: Filter {
    private var søk: Søk = Søk(null)
    override fun berikMedParameter(filterParametre: FilterParametre) {
        søk = Søk(filterParametre.fritekst)
    }

    override fun erAktiv() = true

    override fun lagESFilterFunksjon() = if(søk.erAktiv()) søk.lagESFilterFunksjon() else {
        {this}
    }

    override fun auditLog(navIdent: String, returnerteFødselsnummer: String?) {
        søk.auditLog(navIdent, returnerteFødselsnummer)
    }
}