package no.nav.toi.kandidatsøk.filter.fritekstfilter

import no.nav.toi.*
import no.nav.toi.kandidatsøk.filter.FilterFunksjon

sealed interface SøkeType {
    fun erAktiv(): Boolean
    fun passendeSøketype(): Boolean
    fun lagESFilterFunksjon(): FilterFunksjon
    fun auditLog(navIdent: String, returnerteFødselsnummer: String?)
    companion object {
        fun fraFritekstSøk(fritekstSøk: String?) = if(fritekstSøk == null) NullSøk else
            listOf(::IdentSøk, ::KandidatnummerSøk, ::MultiMatchSøk).map { it(fritekstSøk) }.first { it.passendeSøketype() }
    }
}

private class IdentSøk(søkeord: String?): SøkeType {
    private val søkeord = søkeord ?: throw IllegalArgumentException()
    override fun erAktiv() = true
    override fun passendeSøketype() = Regex("^\\d+$").matches(søkeord) && søkeord.length>10
    override fun lagESFilterFunksjon(): FilterFunksjon = {
        must_ {
            bool_ {
                should_ {
                    term_ {
                        field("aktorId")
                        value(søkeord)
                    }
                }
                should_ {
                    term_ {
                        field("fodselsnummer")
                        value(søkeord)
                    }
                }
            }
        }
    }

    override fun auditLog(navIdent: String, returnerteFødselsnummer: String?) {
        AuditLogg.loggSpesifiktKandidatsøk(søkeord, navIdent, søkeord == returnerteFødselsnummer)
    }
}
private class KandidatnummerSøk(søkeord: String?): SøkeType {
    private val søkeord = søkeord ?: throw IllegalArgumentException()
    override fun erAktiv() = true
    override fun passendeSøketype() = Regex("^[a-zA-Z]{2}[0-9]+").matches(søkeord) || Regex("^PAM[0-9a-zA-Z]+").matches(søkeord)
    override fun lagESFilterFunksjon(): FilterFunksjon = {
        must_ {
            term_ {
                field("kandidatnr")
                value(søkeord)
            }
        }
    }

    override fun auditLog(navIdent: String, returnerteFødselsnummer: String?) {
        if(returnerteFødselsnummer!=null) {
            AuditLogg.loggSpesifiktKandidatsøk(returnerteFødselsnummer, navIdent, true)
        } else {
            AuditLogg.loggGenereltKandidatsøk(søkeord, navIdent)
        }
    }
}
private class MultiMatchSøk(søkeord: String?): SøkeType {
    private val søkeord = søkeord ?: throw IllegalArgumentException()
    override fun erAktiv() = true
    override fun passendeSøketype() = true
    override fun lagESFilterFunksjon(): FilterFunksjon = {
        must_ {
            multiMatch_ {
                query(søkeord)
                fields(listOf("fritekst^1", "fornavn^1", "etternavn^1", "yrkeJobbonskerObj.styrkBeskrivelse^1.5", "yrkeJobbonskerObj.sokeTitler^1"))
            }
        }
    }

    override fun auditLog(navIdent: String, returnerteFødselsnummer: String?) {
        AuditLogg.loggGenereltKandidatsøk(søkeord, navIdent)
    }
}
private data object NullSøk: SøkeType {
    override fun erAktiv() = false
    override fun passendeSøketype(): Boolean {
        throw IllegalStateException()
    }
    override fun lagESFilterFunksjon(): FilterFunksjon {
        throw IllegalStateException()
    }

    override fun auditLog(navIdent: String, returnerteFødselsnummer: String?) {
        AuditLogg.loggGenereltKandidatsøk(null, navIdent)
    }
}