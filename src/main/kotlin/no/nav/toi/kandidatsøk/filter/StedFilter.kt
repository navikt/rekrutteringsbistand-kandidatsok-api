package no.nav.toi.kandidatsøk.filter

import no.nav.toi.*
import no.nav.toi.kandidatsøk.FilterParametre

fun List<Filter>.medStedFilter() = this + StedFilter()

private class StedFilter: Filter {
    private var stedRegexer = emptyList<String>()
    override fun berikMedParameter(filterParametre: FilterParametre) {
        stedRegexer= filterParametre.ønsketSted?.map{
            val kommuneRegex = if(it.contains(".")) it else "$it.*"
            val fylkeRegex = it.split(".")[0]
            val landRegex = it.substring(0,2)
            "$kommuneRegex|$fylkeRegex|$landRegex"
        } ?: emptyList()
    }

    override fun erAktiv() = stedRegexer.isNotEmpty()

    override fun lagESFilterFunksjon(): FilterFunksjon = {
        must_ {
            bool_ {
                apply {
                    stedRegexer.forEach {stedRegex ->
                        should_ {
                            nested_ {
                                path("geografiJobbonsker")
                                query_ {
                                    bool_ {
                                        should_ {
                                            regexp("geografiJobbonsker.geografiKode", stedRegex)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}