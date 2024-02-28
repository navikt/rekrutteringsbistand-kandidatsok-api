package no.nav.toi.kandidatsøk.filter

import no.nav.toi.*

fun List<Filter>.medFritekstFilter() = this + FritekstFilter()

private interface SøkeType {
    fun erAktiv(): Boolean
    fun passendeSøketype(søkeord: String): Boolean
    fun lagESFilterFunksjon(søkeOrd: String?): FilterFunksjon
    fun auditLog(userid: String?, navIdent: String) {}
}

private object IdentSøk: SøkeType {
    override fun erAktiv() = true
    override fun passendeSøketype(søkeord: String) = Regex("^\\d+$").matches(søkeord) && søkeord.length>10
    override fun lagESFilterFunksjon(søkeOrd: String?): FilterFunksjon = {
        must_ {
            bool_ {
                should_ {
                    term_ {
                        field("aktorId")
                        value(søkeOrd!!)
                    }
                }
                should_ {
                    term_ {
                        field("fodselsnummer")
                        value(søkeOrd!!)
                    }
                }
            }
        }
    }

    override fun auditLog(userid: String?, navIdent: String) {
        AuditLogg.loggOppslagKAndidatsøk(userid!!, navIdent)
    }
}
private object KandidatnummerSøk: SøkeType {
    override fun erAktiv() = true
    override fun passendeSøketype(søkeord: String) = Regex("^[a-zA-Z]{2}[0-9]+").matches(søkeord) || Regex("^PAM[0-9a-zA-Z]+").matches(søkeord)
    override fun lagESFilterFunksjon(søkeOrd: String?): FilterFunksjon = {
        must_ {
            term_ {
                field("kandidatnr")
                value(søkeOrd!!)
            }
        }
    }
}
private object MultiMatchSøk:SøkeType {
    override fun erAktiv() = true
    override fun passendeSøketype(søkeord: String) = true
    override fun lagESFilterFunksjon(søkeOrd: String?): FilterFunksjon = {
        must_ {
            multiMatch_ {
                query(søkeOrd)
                fields(listOf("fritekst^1", "fornavn^1", "etternavn^1", "yrkeJobbonskerObj.styrkBeskrivelse^1.5", "yrkeJobbonskerObj.sokeTitler^1"))
            }
        }
    }
}
private object Null: SøkeType {
    override fun erAktiv() = false
    override fun passendeSøketype(søkeord: String): Boolean {
        throw IllegalStateException()
    }
    override fun lagESFilterFunksjon(søkeOrd: String?): FilterFunksjon {
        throw IllegalStateException()
    }
}

private class Søk(private val søkeOrd: String?) {
    private val type = søkeOrd?.tilType() ?: Null
    fun erAktiv() = type.erAktiv()
    fun lagESFilterFunksjon(): FilterFunksjon {
        return type.lagESFilterFunksjon(søkeOrd)
    }

    fun auditLog(navIdent: String) {
        type.auditLog(søkeOrd, navIdent)
    }
}

private fun String.tilType() =
    listOf(IdentSøk, KandidatnummerSøk, MultiMatchSøk).first { it.passendeSøketype(this) }

private class FritekstFilter: Filter {
    private var søk: Søk = Søk(null)
    override fun berikMedParameter(hentParameter: (String) -> Parameter?) {
        søk = Søk(hentParameter("fritekst")?.somString())
    }

    override fun erAktiv() = søk.erAktiv()

    override fun lagESFilterFunksjon() = søk.lagESFilterFunksjon()

    override fun auditLog(navIdent: String) {
        if(erAktiv())
            søk.auditLog(navIdent)
    }
}