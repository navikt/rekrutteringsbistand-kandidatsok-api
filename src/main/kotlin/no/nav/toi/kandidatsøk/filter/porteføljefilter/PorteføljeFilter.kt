package no.nav.toi.kandidatsøk.filter.porteføljefilter

import io.javalin.http.UnauthorizedResponse
import no.nav.toi.*
import no.nav.toi.kandidatsøk.FilterParametre
import no.nav.toi.kandidatsøk.ModiaKlient
import no.nav.toi.kandidatsøk.filter.Filter
import no.nav.toi.kandidatsøk.filter.FilterFunksjon
import no.nav.toi.kandidatsøk.filter.Valideringsfeil

fun List<Filter>.medPorteføljeFilter(filterParametre: FilterParametre, authenticatedUser: AuthenticatedUser, modiaKlient: ModiaKlient) =
    this + PorteføljeFilter(filterParametre, authenticatedUser, modiaKlient)

internal interface Porteføljetype: Filter {
    override fun erAktiv(): Boolean = true
}

private class MineBrukere(private val authenticatedUser: AuthenticatedUser?) : Porteføljetype {
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

private class ValgtKontor(private val valgteKontor: List<String>) : Porteføljetype {
    override fun lagESFilterFunksjon(): FilterFunksjon = {
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

private class MineKontorer(private val authenticatedUser: AuthenticatedUser?, private val modiaKlient: ModiaKlient) :
    Porteføljetype {
    override fun lagESFilterFunksjon(): FilterFunksjon = {
        val jwt = authenticatedUser?.jwt ?: throw UnauthorizedResponse()
        val kontorer = modiaKlient.hentModiaEnheter(jwt)

        if (kontorer.isEmpty()) throw UnauthorizedResponse()

        must_ {
            bool_ {
                apply {
                    kontorer.forEach { kontor ->
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

private class MittKontor(private val orgenhet: String) : Porteføljetype {
    override fun lagESFilterFunksjon(): FilterFunksjon = {
        must_ {
            term_ {
                field("orgenhet")
                value(orgenhet)
            }
        }
    }
}

private object Alle : Porteføljetype {
    override fun erAktiv() = false
    override fun lagESFilterFunksjon(): FilterFunksjon = { this }
}

private fun String.typePorteføljeSpørring(
    valgteKontor: () -> List<String>,
    orgenhet: () -> String?,
    authenticatedUser: AuthenticatedUser?,
    modiaKlient: ModiaKlient
): Porteføljetype = when (this) {
    "alle" -> Alle
    "mine" -> MineBrukere(authenticatedUser)
    "kontor" -> MittKontor(orgenhet() ?: "")
    "valgte" -> ValgtKontor(valgteKontor())
    "mineKontorer" -> MineKontorer(authenticatedUser, modiaKlient)
    else -> throw Valideringsfeil("$this er ikke en gyldig porteføljetype-spørring")
}

private class PorteføljeFilter(
    parametre: FilterParametre,
    authenticatedUser: AuthenticatedUser,
    modiaKlient: ModiaKlient,
) : Filter {

    private val portefølje = parametre.portefølje?.typePorteføljeSpørring({
        parametre.valgtKontor ?: throw Valideringsfeil("Må sende med valgtKontor-variabel også")
    }, { parametre.orgenhet },authenticatedUser, modiaKlient) ?: Alle

    override fun erAktiv() = portefølje.erAktiv()
    override fun lagESFilterFunksjon() = portefølje.lagESFilterFunksjon()
}
