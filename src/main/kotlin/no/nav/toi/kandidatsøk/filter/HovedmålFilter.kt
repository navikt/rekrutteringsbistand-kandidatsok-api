package no.nav.toi.kandidatsøk.filter

import no.nav.toi.must_
import no.nav.toi.terms

class HovedmålFilter: Filter {
    private var hovedMål = emptyList<String>()
    override fun berikMedParameter(hentParameter: (String) -> Parameter?) {
        hovedMål = hentParameter("hovedmål")?.somStringListe() ?: hovedMål
    }

    override fun erAktiv() = hovedMål.isNotEmpty()

    override fun lagESFilterFunksjon(): FilterFunksjon = {
        must_ {
            terms("hovedmaalkode" to hovedMål)
        }
    }
}