package no.nav.toi.kandidatsøk.filter

import no.nav.toi.bool_
import no.nav.toi.kandidatsøk.FilterParametre
import no.nav.toi.matchPhrase_
import no.nav.toi.must_
import no.nav.toi.should_

fun List<Filter>.medKompetanseFilter(filterParametre: FilterParametre) = this + KompetanseFilter(filterParametre)

private class KompetanseFilter(parametre: FilterParametre): Filter {
    private val kompetanser = parametre.kompetanse ?: emptyList()

    override fun erAktiv() = kompetanser.isNotEmpty()

    override fun lagESFilterFunksjon(): FilterFunksjon = {
        must_ {
            bool_ {
                apply {
                    kompetanser.forEach { kompetanse ->
                        should_ {
                            matchPhrase_ {
                                field("samletKompetanseObj.samletKompetanseTekst")
                                slop(4)
                                query(kompetanse)
                            }
                            matchPhrase_ {
                                field("samletKompetanseObj.samletKompetanseTekst")
                                slop(4)
                                query(kompetanse)
                            }
                        }
                    }
                }
            }
        }
    }
}