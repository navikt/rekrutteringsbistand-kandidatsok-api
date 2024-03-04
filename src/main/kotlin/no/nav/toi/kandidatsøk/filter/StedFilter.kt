package no.nav.toi.kandidatsøk.filter

import no.nav.toi.*
import no.nav.toi.kandidatsøk.FilterParametre

fun List<Filter>.medStedFilter() = this + StedFilter()

private class StedFilter: Filter {
    private var stedRegex: String? = null
    override fun berikMedParameter(filterParametre: FilterParametre) {
        stedRegex= filterParametre.ønsketSted?.let{
            "$it|${it.split(".")[0]}|${it.substring(0,2)}"
        }
    }

    override fun erAktiv() = stedRegex!=null

    override fun lagESFilterFunksjon(): FilterFunksjon = {
        must_ {
            bool_ {
                should_ {
                    nested_ {
                        path("geografiJobbonsker")
                        query_ {
                            bool_ {
                                should_ {
                                    regexp("geografiJobbonsker.geografiKode", stedRegex!!)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}