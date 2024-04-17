package no.nav.toi.kandidatsøk.filter.fritekstfilter

import no.nav.toi.kandidatsøk.filter.FilterFunksjon

class Søk(private val søkeOrd: String?) {
    private val type = SøkeType.fraFritekstSøk(søkeOrd)
    fun erAktiv() = type.erAktiv()
    fun lagESFilterFunksjon(): FilterFunksjon {
        return type.lagESFilterFunksjon(søkeOrd)
    }

    fun auditLog(navIdent: String, returnerteFødselsnummer: String?) {
        type.auditLog(søkeOrd, navIdent, returnerteFødselsnummer)
    }
}