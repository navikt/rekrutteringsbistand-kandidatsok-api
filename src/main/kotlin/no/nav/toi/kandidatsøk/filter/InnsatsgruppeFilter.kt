package no.nav.toi.kandidatsøk.filter

import no.nav.toi.kandidatsøk.FilterParametre
import no.nav.toi.must_
import no.nav.toi.terms

fun List<Filter>.medInnsatsgruppeFilter(filterParametre: FilterParametre) = this + InnsatsgruppeFilter(filterParametre)

private val defaultInnsatsgrupper = listOf("SPESIELT_TILPASSET_INNSATS","SITUASJONSBESTEMT_INNSATS","STANDARD_INNSATS","VARIG_TILPASSET_INNSATS","GRADERT_VARIG_TILPASSET_INNSATS")

private val lovligeVerdier = defaultInnsatsgrupper + "HAR_IKKE_GJELDENDE_14A_VEDTAK"

private class InnsatsgruppeFilter(parametre: FilterParametre): Filter {
    private val valgteGyldigeInnsatsgrupper = parametre.innsatsgruppe
        ?.filter { it in lovligeVerdier }
        .let { if(it?.isNotEmpty() == true) it else defaultInnsatsgrupper }

    override fun erAktiv() = true

    override fun lagESFilterFunksjon(): FilterFunksjon = {
        must_ {
            terms("innsatsgruppe" to valgteGyldigeInnsatsgrupper)
        }
    }
}