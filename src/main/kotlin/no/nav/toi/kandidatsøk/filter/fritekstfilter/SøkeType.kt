package no.nav.toi.kandidatsøk.filter.fritekstfilter

import no.nav.toi.*
import no.nav.toi.kandidatsøk.filter.FilterFunksjon

sealed interface SøkeType {
    fun erAktiv(): Boolean
    fun passendeSøketype(søkeord: String): Boolean
    fun lagESFilterFunksjon(søkeOrd: String?): FilterFunksjon
    fun auditLog(søkeord: String?, navIdent: String, returnerteFødselsnummer: String?)
    companion object {
        fun fraFritekstSøk(fritekstSøk: String?) = if(fritekstSøk == null) NullSøk else
            listOf(IdentSøk, KandidatnummerSøk, MultiMatchSøk).first { it.passendeSøketype(fritekstSøk) }
    }
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

    override fun auditLog(søkeord: String?, navIdent: String, returnerteFødselsnummer: String?) {
        requireNotNull(søkeord)
        AuditLogg.loggSpesifiktKandidatsøk(søkeord, navIdent, søkeord == returnerteFødselsnummer)
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

    override fun auditLog(søkeord: String?, navIdent: String, returnerteFødselsnummer: String?) {
        if(returnerteFødselsnummer!=null) {
            AuditLogg.loggSpesifiktKandidatsøk(returnerteFødselsnummer, navIdent, true)
        }
    }
}
private object MultiMatchSøk: SøkeType {
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

    override fun auditLog(søkeord: String?, navIdent: String, returnerteFødselsnummer: String?) {
        AuditLogg.loggGenereltKandidatsøk(søkeord, navIdent)
    }
}
private object NullSøk: SøkeType {
    override fun erAktiv() = false
    override fun passendeSøketype(søkeord: String): Boolean {
        throw IllegalStateException()
    }
    override fun lagESFilterFunksjon(søkeOrd: String?): FilterFunksjon {
        throw IllegalStateException()
    }

    override fun auditLog(søkeord: String?, navIdent: String, returnerteFødselsnummer: String?) {
        AuditLogg.loggGenereltKandidatsøk(null, navIdent)
    }
}