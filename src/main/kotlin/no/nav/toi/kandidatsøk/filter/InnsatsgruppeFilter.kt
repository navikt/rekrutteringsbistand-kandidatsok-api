package no.nav.toi.kandidatsøk.filter

import no.nav.toi.kandidatsøk.FilterParametre
import no.nav.toi.must_
import no.nav.toi.terms

fun List<Filter>.medInnsatsgruppeFilter() = this + InnsatsgruppeFilter()

private val lovligeVerdier = listOf("ANDRE","IKVAL","VARIG","BFORM","BATT","IVURD","BKART","OPPFI","VURDI","VURDU")
private val defaultInnsatsgrupper = listOf("BATT","BFORM","IKVAL","VARIG")

private class InnsatsgruppeFilter: Filter {
    private lateinit var innsatsgrupper: List<String>

    override fun berikMedParameter(filterParametre: FilterParametre) {
        val innkommendeParameter = filterParametre.innsatsgruppe?.filter { it in lovligeVerdier }?.flatMap { if(it=="ANDRE") listOf(it,"IVURD","BKART","OPPFI","VURDI","VURDU") else listOf(it) }
        innsatsgrupper = if(innkommendeParameter?.isNotEmpty() == true) innkommendeParameter else defaultInnsatsgrupper
    }

    override fun erAktiv() = true

    override fun lagESFilterFunksjon(): FilterFunksjon = {
        must_ {
            terms("kvalifiseringsgruppekode" to innsatsgrupper)
        }
    }
}