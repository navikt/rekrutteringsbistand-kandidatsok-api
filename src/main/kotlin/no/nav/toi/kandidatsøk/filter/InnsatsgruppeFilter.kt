package no.nav.toi.kandidatsøk.filter

import no.nav.toi.kandidatsøk.FilterParametre
import no.nav.toi.must_
import no.nav.toi.terms

fun List<Filter>.medInnsatsgruppeFilter(filterParametre: FilterParametre) = this + InnsatsgruppeFilter(filterParametre)

private val lovligeVerdier = listOf("ANDRE","IKVAL","VARIG","BFORM","BATT","IVURD","BKART","OPPFI","VURDI","VURDU")
private val defaultInnsatsgrupper = listOf("BATT","BFORM","IKVAL","VARIG")

private class InnsatsgruppeFilter(parametre: FilterParametre): Filter {
    private val innsatsgrupper = parametre.innsatsgruppe
        ?.filter { it in lovligeVerdier }
        ?.flatMap { if(it=="ANDRE") listOf(it,"IVURD","BKART","OPPFI","VURDI","VURDU") else listOf(it) }
        .let { if(it?.isNotEmpty() == true) it else defaultInnsatsgrupper }

    override fun erAktiv() = true

    override fun lagESFilterFunksjon(): FilterFunksjon = {
        must_ {
            terms("kvalifiseringsgruppekode" to innsatsgrupper)
        }
    }
}