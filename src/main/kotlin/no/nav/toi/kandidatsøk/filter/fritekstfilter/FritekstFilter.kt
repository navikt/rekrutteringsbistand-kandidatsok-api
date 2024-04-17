package no.nav.toi.kandidatsøk.filter.fritekstfilter

import no.nav.toi.kandidatsøk.FilterParametre
import no.nav.toi.kandidatsøk.filter.Filter

fun List<Filter>.medFritekstFilter() = this + FritekstFilter()

private class FritekstFilter: Filter {
    private var søk: Søk = Søk(null)
    override fun berikMedParameter(filterParametre: FilterParametre) {
        søk = Søk(filterParametre.fritekst)
    }

    override fun erAktiv() = søk.erAktiv()

    override fun lagESFilterFunksjon() = søk.lagESFilterFunksjon()

    override fun auditLog(navIdent: String, returnerteFødselsnummer: String?) {
        if(erAktiv())
            søk.auditLog(navIdent, returnerteFødselsnummer)
    }
}