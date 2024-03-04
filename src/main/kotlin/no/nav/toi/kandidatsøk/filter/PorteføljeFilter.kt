package no.nav.toi.kandidatsøk.filter

import no.nav.toi.*

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

private class ValgtKontor(hentParameter: (String) -> Parameter?) : Type {
    private val valgteKontor = hentParameter("valgtKontor")?.somStringListe() ?: emptyList()
    override fun lagESFilterFunksjon(navIdent: String?): FilterFunksjon = {
        must_ {
            bool_ {
                apply {
                    valgteKontor.forEach { kontor ->
                        should_ {
                            term_ {
                                field("navkontor")
                                value(kontor)
                            }
                        }
                    }
                }
            }
        }
    }
}

private object IkkeAktiv: Type {
    override fun erAktiv() = false
    override fun lagESFilterFunksjon(navIdent: String?): FilterFunksjon ={this}
}

private fun String.typePorteføljeSpørring(hentParameter: (String) -> Parameter?) = when(this) {
    "mine" -> MineBrukere
    "kontor" -> ValgtKontor(hentParameter)
    else -> throw IllegalArgumentException("$this er ikke en gyldig porteføljetype-spørring")
}

private class PorteføljeFilter: Filter {

    private var portefølje: Type = IkkeAktiv
    private var authenticatedUser: AuthenticatedUser? = null

    override fun berikMedParameter(hentParameter: (String) -> Parameter?) {
        portefølje = hentParameter("portefølje")?.somString()?.typePorteføljeSpørring(hentParameter) ?: portefølje
    }

    override fun erAktiv() = portefølje.erAktiv()
    override fun lagESFilterFunksjon() = portefølje.lagESFilterFunksjon(authenticatedUser?.navIdent)
    override fun berikMedAuthenticatedUser(authenticatedUser: AuthenticatedUser) {
        this.authenticatedUser = authenticatedUser
    }
}
