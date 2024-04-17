package no.nav.toi.kandidatsøk.filter.fritekstfilter

class Søk(søkeOrd: String?) {
    private val type = SøkeType.fraFritekstSøk(søkeOrd)
    fun erAktiv() = type.erAktiv()
    fun lagESFilterFunksjon() = type.lagESFilterFunksjon()

    fun auditLog(navIdent: String, returnerteFødselsnummer: String?) {
        type.auditLog(navIdent, returnerteFødselsnummer)
    }
}