package no.nav.toi.kandidatsøk.filter.porteføljefilter

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

private object Alle : Porteføljetype {
    override fun erAktiv() = false
    override fun lagESFilterFunksjon(): FilterFunksjon = { this }
}

private fun String.typePorteføljeSpørring(
    filterParametre: FilterParametre,
    authenticatedUser: AuthenticatedUser?,
    modiaKlient: ModiaKlient
): Porteføljetype = when (this) {
    "alle" -> Alle
    "mine" -> MineBrukereFilter(authenticatedUser)
    "kontor" -> MittKontorFilter(filterParametre)
    "valgte" -> ValgtKontorFilter(filterParametre)
    "mineKontorer" -> MineKontorerFilter(authenticatedUser, modiaKlient)
    else -> throw Valideringsfeil("$this er ikke en gyldig porteføljetype-spørring")
}

private class PorteføljeFilter(
    parametre: FilterParametre,
    authenticatedUser: AuthenticatedUser,
    modiaKlient: ModiaKlient,
) : Filter {

    private val portefølje = parametre.portefølje?.typePorteføljeSpørring(parametre, authenticatedUser, modiaKlient) ?: Alle

    override fun erAktiv() = portefølje.erAktiv()
    override fun lagESFilterFunksjon() = portefølje.lagESFilterFunksjon()
}
