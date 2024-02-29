package no.nav.toi.kandidatsøk.filter

import no.nav.toi.AuthenticatedUser
import no.nav.toi.must_
import no.nav.toi.term_
import no.nav.toi.value

fun List<Filter>.medPorteføljeFilter() = this + PorteføljeFilter()

private interface Type {
    fun erAktiv(): Boolean = true
    fun lagESFilterFunksjon(navIdent: String?): FilterFunksjon
}

private object MineBrukere: Type {
    override fun lagESFilterFunksjon(navIdent: String?): FilterFunksjon = {
        must_ {
            term_ {
                field("veileder")
                value(navIdent!!)
            }
        }
    }
}

private object IkkeAktiv: Type {
    override fun erAktiv() = false
    override fun lagESFilterFunksjon(navIdent: String?): FilterFunksjon ={this}
}

private fun String.typePorteføljeSpørring() = when(this) {
    "mine" -> MineBrukere
    else -> throw IllegalArgumentException("$this er ikke en gyldig porteføljetype-spørring")
}

private class PorteføljeFilter: Filter {

    private var portefølje: Type = IkkeAktiv
    private var authenticatedUser: AuthenticatedUser? = null

    override fun berikMedParameter(hentParameter: (String) -> Parameter?) {
        portefølje = hentParameter("portefølje")?.somString()?.typePorteføljeSpørring() ?: portefølje
    }

    override fun erAktiv() = portefølje.erAktiv()
    override fun lagESFilterFunksjon() = portefølje.lagESFilterFunksjon(authenticatedUser?.navIdent)
    override fun berikMedAuthenticatedUser(authenticatedUser: AuthenticatedUser) {
        this.authenticatedUser = authenticatedUser
    }
}
