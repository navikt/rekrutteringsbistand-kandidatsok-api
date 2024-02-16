package no.nav.toi.kandidatsøk.filter

import no.nav.toi.*

fun List<Filter>.medStedFilter() = this + StedFilter()

private class StedFilter: Filter {
    private var stedRegex: String? = null
    override fun berikMedParameter(hentParameter: (String) -> Parameter?) {
        stedRegex= hentParameter("sted")?.somString()?.let{
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