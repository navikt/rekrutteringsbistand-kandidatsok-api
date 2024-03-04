package no.nav.toi.kandidatsøk.filter

import no.nav.toi.*
import no.nav.toi.kandidatsøk.FilterParametre

fun List<Filter>.medStedFilter() = this + StedFilter()

private class Sted(ønsketStedKode: String) {
    private val ønsketSted = ønsketStedKode.substring(ønsketStedKode.indexOf('.')+1)

    private fun kommuneRegex() = if (ønsketSted.contains(".")) ønsketSted else "$ønsketSted.*"
    private fun fylkeRegex() = ønsketSted.split(".")[0]
    private fun landRegex() = ønsketSted.substring(0, 2)
    fun geografiKode() = "${kommuneRegex()}|${fylkeRegex()}|${landRegex()}"
    fun kommunenr() = if (ønsketSted.contains(".")) ønsketSted.split('.').last() else "${ønsketSted.substring(2)}.*"
}

private class StedFilter: Filter {
    private lateinit var steder: List<Sted>
    private var måBoPåSted: Boolean = false

    override fun berikMedParameter(filterParametre: FilterParametre) {
        steder = filterParametre.ønsketSted?.map(::Sted) ?: emptyList()
        måBoPåSted = filterParametre.borPåØnsketSted ?: false
    }

    override fun erAktiv() = steder.isNotEmpty()

    override fun lagESFilterFunksjon(): FilterFunksjon = {
        must_ {
            bool_ {
                apply {
                    steder.forEach { sted ->
                        should_ {
                            nested_ {
                                path("geografiJobbonsker")
                                query_ {
                                    bool_ {
                                        should_ {
                                            regexp("geografiJobbonsker.geografiKode", sted.geografiKode())
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        if(måBoPåSted)
        must_ {
            bool_ {
                apply {
                    steder.forEach { sted ->
                        should_ {
                            regexp("kommunenummerstring", sted.kommunenr())
                        }
                    }
                }
            }
        }
        else this
    }
}