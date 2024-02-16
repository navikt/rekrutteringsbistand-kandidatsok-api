package no.nav.toi.kandidatsøk.filter

import no.nav.toi.*

private interface UtdanningsNivå {
    fun harStringKode(kode: String): Boolean
    fun regexInkluder(): String
    fun regexEkskluder(): String?
}

private object Videregående: UtdanningsNivå {
    override fun harStringKode(kode: String) = kode == "videregaende"
    override fun regexInkluder() = "[3-4][0-9]*"
    override fun regexEkskluder() = "[5-8][0-9]*"
}
private object Fagskole: UtdanningsNivå {
    override fun harStringKode(kode: String) = kode == "fagskole"
    override fun regexInkluder() = "5[0-9]*"
    override fun regexEkskluder() = "[6-8][0-9]*"
}
private object Bachelor: UtdanningsNivå {
    override fun harStringKode(kode: String) = kode == "bachelor"
    override fun regexInkluder() = "6[0-9]*"
    override fun regexEkskluder() = "[7-8][0-9]*"
}
private object Master: UtdanningsNivå {
    override fun harStringKode(kode: String) = kode == "master"
    override fun regexInkluder() = "7[0-9]*"
    override fun regexEkskluder() = "8[0-9]*"
}
private object Doktorgrad: UtdanningsNivå {
    override fun harStringKode(kode: String) = kode == "doktorgrad"
    override fun regexInkluder() = "8[0-9]*"
    override fun regexEkskluder() = null
}

private fun String.tilUtdanningsNivå() = listOf(Videregående, Fagskole, Bachelor, Master, Doktorgrad)
    .firstOrNull { it.harStringKode(this) } ?: throw IllegalArgumentException("$this er ikke en gyldig utdanningsnivå")

class UtdanningFilter: Filter {
    private var utdanningsnivå = emptyList<UtdanningsNivå>()
    override fun berikMedParameter(hentParameter: (String) -> Parameter?) {
        utdanningsnivå = hentParameter("utdanningsnivå")?.somStringListe()?.map(String::tilUtdanningsNivå) ?: emptyList()
    }

    override fun erAktiv() = utdanningsnivå.isNotEmpty()

    override fun lagESFilterFunksjon(): FilterFunksjon = {
        must_ {
            bool_ {
                apply {
                    utdanningsnivå.forEach { utdanningsNivå ->
                        should_ {
                            bool_ {
                                must_ {
                                    nested_ {
                                        path("utdanning")
                                        query_ {
                                            regexp("utdanning.nusKode", utdanningsNivå.regexInkluder())
                                        }
                                    }
                                }
                                utdanningsNivå.regexEkskluder()?.let { regexEksluder ->
                                    mustNot_ {
                                        nested_ {
                                            path("utdanning")
                                            query_ {
                                                regexp("utdanning.nusKode", regexEksluder)
                                            }
                                        }
                                    }
                                } ?: this
                            }
                        }
                    }
                }
            }
        }
    }

}