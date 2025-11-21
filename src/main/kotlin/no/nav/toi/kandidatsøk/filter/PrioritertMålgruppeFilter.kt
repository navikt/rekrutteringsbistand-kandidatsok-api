package no.nav.toi.kandidatsøk.filter

import no.nav.toi.*
import no.nav.toi.hullicv.hullICvEsFunksjon
import no.nav.toi.kandidatsøk.FilterParametre
import java.time.LocalDate

fun List<Filter>.medPrioritertMålgruppeFilter(filterParametre: FilterParametre) = this + PrioritertMålgruppeFilter(filterParametre)

private interface Målgruppe {
    fun harKode(kode: String): Boolean
    fun lagESFilterFunksjon(): FilterFunksjon
}
private object Unge: Målgruppe {
    override fun harKode(kode: String) = kode == "unge"
    override fun lagESFilterFunksjon(): FilterFunksjon = {
        should_ {
            range_ {
                field("fodselsdato")
                gte("now/d-30y")
                lt("now")
            }
        }
    }
}
private object Senior: Målgruppe {
    override fun harKode(kode: String) = kode == "senior"
    override fun lagESFilterFunksjon(): FilterFunksjon = {
        should_ {
            range_ {
                field("fodselsdato")
                gte("now-200y/d")
                lt("now/d-50y")
            }
        }
    }
}
private object HullICv: Målgruppe {
    override fun harKode(kode: String) = kode == "hullICv"
    override fun lagESFilterFunksjon(): FilterFunksjon = hullICvEsFunksjon(LocalDate.now())
}

private fun String.tilMålgruppe() = listOf(Unge, Senior, HullICv).firstOrNull { it.harKode(this) } ?: throw Valideringsfeil("$this er en ukjent målgruppe")

private class PrioritertMålgruppeFilter(parametre: FilterParametre): Filter {
    private val prioriterteMålgrupper = parametre.prioritertMålgruppe?.map(String::tilMålgruppe)  ?: emptyList()

    override fun erAktiv() = prioriterteMålgrupper.isNotEmpty()

    override fun lagESFilterFunksjon(): FilterFunksjon = {
        must_ {
            bool_ {
                apply {
                    prioriterteMålgrupper.map(Målgruppe::lagESFilterFunksjon).forEach { it() }
                }
            }
        }
    }
}