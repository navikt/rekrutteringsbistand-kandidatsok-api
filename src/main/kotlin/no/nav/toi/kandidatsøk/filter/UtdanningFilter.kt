package no.nav.toi.kandidatsøk.filter

import no.nav.toi.*
import no.nav.toi.kandidatsøk.FilterParametre
import org.opensearch.client.opensearch._types.query_dsl.BoolQuery
import org.opensearch.client.util.ObjectBuilder

fun List<Filter>.medUtdanningFilter() = this + UtdanningFilter()

private interface UtdanningsNivå {
    fun harStringKode(kode: String): Boolean
    fun esInkluder(): BoolQuery.Builder.() -> ObjectBuilder<BoolQuery>
    fun esEkskluder(): BoolQuery.Builder.() -> ObjectBuilder<BoolQuery>
}

private object Videregående: UtdanningsNivå {
    override fun harStringKode(kode: String) = kode == "videregaende"
    override fun esInkluder() = inkluderUtdanning("[3-4][0-9]*")
    override fun esEkskluder() = ekskluderUtdanning("[5-8][0-9]*")
}
private object Fagskole: UtdanningsNivå {
    override fun harStringKode(kode: String) = kode == "fagskole"
    override fun esInkluder() = inkluderUtdanning("5[0-9]*")
    override fun esEkskluder() = ekskluderUtdanning("[6-8][0-9]*")
}
private object Bachelor: UtdanningsNivå {
    override fun harStringKode(kode: String) = kode == "bachelor"
    override fun esInkluder() = inkluderUtdanning("6[0-9]*")
    override fun esEkskluder() = ekskluderUtdanning("[7-8][0-9]*")
}
private object Master: UtdanningsNivå {
    override fun harStringKode(kode: String) = kode == "master"
    override fun esInkluder() = inkluderUtdanning("7[0-9]*")
    override fun esEkskluder() = ekskluderUtdanning("8[0-9]*")
}
private object Doktorgrad: UtdanningsNivå {
    override fun harStringKode(kode: String) = kode == "doktorgrad"
    override fun esInkluder() = inkluderUtdanning("8[0-9]*")
    override fun esEkskluder(): BoolQuery.Builder.() -> ObjectBuilder<BoolQuery> = {this}
}

private fun inkluderUtdanning(regex: String): BoolQuery.Builder.() -> ObjectBuilder<BoolQuery> = {
    must_ {
        nested_ {
            path("utdanning")
            query_ {
                regexp("utdanning.nusKode", regex)
            }
        }
    }
}

private fun ekskluderUtdanning(regex: String): BoolQuery.Builder.() -> ObjectBuilder<BoolQuery> = {
    mustNot_ {
        nested_ {
            path("utdanning")
            query_ {
                regexp("utdanning.nusKode", regex)
            }
        }
    }
}
private fun String.tilUtdanningsNivå() = listOf(Videregående, Fagskole, Bachelor, Master, Doktorgrad)
    .firstOrNull { it.harStringKode(this) } ?: throw Valideringsfeil("$this er ikke en gyldig utdanningsnivå")

private class UtdanningFilter: Filter {
    private var utdanningsnivå = emptyList<UtdanningsNivå>()
    override fun berikMedParameter(filterParametre: FilterParametre) {
        utdanningsnivå = filterParametre.utdanningsnivå?.map(String::tilUtdanningsNivå) ?: emptyList()
    }

    override fun erAktiv() = utdanningsnivå.isNotEmpty()

    override fun lagESFilterFunksjon(): FilterFunksjon = {
        must_ {
            bool_ {
                apply {
                    utdanningsnivå.forEach { utdanningsNivå ->
                        should_ {
                            bool_ {
                                utdanningsNivå.esInkluder()()
                                utdanningsNivå.esEkskluder()()
                            }
                        }
                    }
                }
            }
        }
    }
}