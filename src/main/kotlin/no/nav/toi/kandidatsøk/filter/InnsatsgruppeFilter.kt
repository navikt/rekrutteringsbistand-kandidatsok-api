package no.nav.toi.kandidatsøk.filter

import no.nav.toi.must_
import no.nav.toi.terms

class InnsatsgruppeFilter: Filter {
    private var innsatsgrupper = listOf("BATT","BFORM","IKVAL","VARIG")
    override fun berikMedParameter(hentParameter: (String) -> Any?) {
        innsatsgrupper = (hentParameter("innsatsgruppe") as List<*>?)?.map { it as String } ?: innsatsgrupper
    }

    override fun erAktiv() = true

    override fun lagESFilterFunksjon(): FilterFunksjon = {
        must_ {
            terms("kvalifiseringsgruppekode" to innsatsgrupper)
        }
    }
}