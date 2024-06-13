package no.nav.toi.kandidatsøk.filter.porteføljefilter

import io.javalin.http.UnauthorizedResponse
import no.nav.toi.*
import no.nav.toi.kandidatsøk.FilterParametre
import no.nav.toi.kandidatsøk.ModiaKlient
import no.nav.toi.kandidatsøk.filter.Filter
import no.nav.toi.kandidatsøk.filter.FilterFunksjon

fun List<Filter>.medMineKontorerFilter(authenticatedUser: AuthenticatedUser?, modiaKlient: ModiaKlient) = this + MineKontorerFilter(authenticatedUser, modiaKlient)

// TODO: Gjør private etter refaktorering
internal class MineKontorerFilter(private val authenticatedUser: AuthenticatedUser?, private val modiaKlient: ModiaKlient) :
    Porteføljetype {
    override fun lagESFilterFunksjon(): FilterFunksjon = {
        val jwt = authenticatedUser?.jwt ?: throw UnauthorizedResponse()
        val kontorer = modiaKlient.hentModiaEnheter(jwt).map { it.navn }

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