package no.nav.toi.kandidatsøk.filter

import no.nav.toi.*

class StedFilter: Filter {
    private var stedRegex: String? = null
    override fun berikMedParameter(hentParameter: (String) -> String?) {
        stedRegex=hentParameter("sted")?.let{
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