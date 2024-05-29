package no.nav.toi.kandidatsøk.filter

import io.javalin.http.UnauthorizedResponse
import no.nav.toi.*
import no.nav.toi.kandidatsøk.FilterParametre
import no.nav.toi.kandidatsøk.ModiaKlient

fun List<Filter>.medPorteføljeFilter(authenticatedUser: AuthenticatedUser, modiaKlient: ModiaKlient) =
    this + PorteføljeFilter(authenticatedUser, modiaKlient)

private interface Type {
    fun erAktiv(): Boolean = true
    fun lagESFilterFunksjon(authenticatedUser: AuthenticatedUser?, modiaKlient: ModiaKlient): FilterFunksjon
}

private object MineBrukere : Type {
    override fun lagESFilterFunksjon(authenticatedUser: AuthenticatedUser?, modiaKlient: ModiaKlient): FilterFunksjon = {
        must_ {
            term_ {
                field("veileder")
                value(authenticatedUser!!.navIdent)
                caseInsensitive(true)
            }
        }
    }
}

private class ValgtKontor(private val valgteKontor: List<String>) : Type {
    override fun lagESFilterFunksjon(authenticatedUser: AuthenticatedUser?, modiaKlient: ModiaKlient): FilterFunksjon = {
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

private class MineKontorer : Type {
    override fun lagESFilterFunksjon(authenticatedUser: AuthenticatedUser?, modiaKlient: ModiaKlient): FilterFunksjon = {
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

private class MittKontor(private val orgenhet: String) : Type {
    override fun lagESFilterFunksjon(authenticatedUser: AuthenticatedUser?, modiaKlient: ModiaKlient): FilterFunksjon = {
        must_ {
            term_ {
                field("orgenhet")
                value(orgenhet)
            }
        }
    }
}

private object Alle : Type {
    override fun erAktiv() = false
    override fun lagESFilterFunksjon(authenticatedUser: AuthenticatedUser?, modiaKlient: ModiaKlient): FilterFunksjon = { this }
}

private fun String.typePorteføljeSpørring(
    valgteKontor: () -> List<String>,
    orgenhet: () -> String?
): Type = when (this) {
    "alle" -> Alle
    "mine" -> MineBrukere
    "kontor" -> MittKontor(orgenhet() ?: "")
    "valgte" -> ValgtKontor(valgteKontor())
    "mineKontorer" -> MineKontorer()
    else -> throw Valideringsfeil("$this er ikke en gyldig porteføljetype-spørring")
}

private class PorteføljeFilter(private val authenticatedUser: AuthenticatedUser, private val modiaKlient: ModiaKlient) : Filter {

    private var portefølje: Type = Alle

    override fun berikMedParameter(filterParametre: FilterParametre) {
        portefølje = filterParametre.portefølje?.typePorteføljeSpørring({
            filterParametre.valgtKontor ?: throw Valideringsfeil("Må sende med valgtKontor-variabel også")
        }, { filterParametre.orgenhet }) ?: Alle
    }

    override fun erAktiv() = portefølje.erAktiv()
    override fun lagESFilterFunksjon() = portefølje.lagESFilterFunksjon(authenticatedUser, modiaKlient)
}
