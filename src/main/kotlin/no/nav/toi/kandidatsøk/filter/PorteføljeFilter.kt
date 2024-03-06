package no.nav.toi.kandidatsøk.filter

import io.javalin.validation.ValidationError
import no.nav.toi.*
import no.nav.toi.kandidatsøk.FilterParametre

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
                caseInsensitive(true)
            }
        }
    }
}

private class ValgtKontor(private val valgteKontor: List<String>) : Type {
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

private class MittKontor(private val orgenhet: String) : Type {
    override fun lagESFilterFunksjon(navIdent: String?): FilterFunksjon = {
        must_ {
            term_ {
                field("orgenhet")
                value(orgenhet)
            }
        }
    }
}

private object Alle: Type {
    override fun erAktiv() = false
    override fun lagESFilterFunksjon(navIdent: String?): FilterFunksjon ={this}
}

private fun String.typePorteføljeSpørring(valgteKontor: () -> List<String>, orgenhet: () -> String?) = when(this) {
    "alle" -> Alle
    "mine" -> MineBrukere
    "kontor" -> MittKontor(orgenhet() ?: "")
    "valgte" -> ValgtKontor(valgteKontor())
    else -> throw Valideringsfeil("$this er ikke en gyldig porteføljetype-spørring")
}

private class PorteføljeFilter: Filter {

    private var portefølje: Type = Alle
    private var authenticatedUser: AuthenticatedUser? = null

    override fun berikMedParameter(filterParametre: FilterParametre) {
        portefølje = filterParametre.portefølje?.typePorteføljeSpørring( { filterParametre.valgtKontor ?: throw Valideringsfeil("Må sende med valgtKontor-variabel også")}, {filterParametre.orgenhet }) ?: Alle
    }

    override fun erAktiv() = portefølje.erAktiv()
    override fun lagESFilterFunksjon() = portefølje.lagESFilterFunksjon(authenticatedUser?.navIdent)
    override fun berikMedAuthenticatedUser(authenticatedUser: AuthenticatedUser) {
        this.authenticatedUser = authenticatedUser
    }
}
