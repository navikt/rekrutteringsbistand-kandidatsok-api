package no.nav.toi.kandidatsøk.filter.porteføljefilter

import no.nav.toi.*
import no.nav.toi.kandidatsøk.FilterParametre
import no.nav.toi.kandidatsøk.filter.Filter
import no.nav.toi.kandidatsøk.filter.FilterFunksjon
import no.nav.toi.kandidatsøk.filter.Valideringsfeil

fun List<Filter>.medValgtKontorerFilter(filterParametre: FilterParametre) = this + ValgtKontorFilter(filterParametre)

// TODO: Gjør private etter refaktorering
internal class ValgtKontorFilter(filterParametre: FilterParametre) : Porteføljetype {
    private val valgteKontor = filterParametre.valgtKontor ?: throw Valideringsfeil("Må sende med valgtKontor-variabel også")

    override fun lagESFilterFunksjon(): FilterFunksjon = {
        must_ {
            bool_ {
                apply {
                    valgteKontor.forEach { enhetId ->
                        should_ {
                            term_ {
                                field("orgenhet")
                                value(enhetId)
                            }
                        }
                    }
                }
            }
        }
    }
}