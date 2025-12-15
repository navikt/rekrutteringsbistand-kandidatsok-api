package no.nav.toi.kandidatsøk.filter

import no.nav.toi.bool_
import no.nav.toi.kandidatsøk.FilterParametre
import no.nav.toi.mustNot_
import no.nav.toi.must_
import no.nav.toi.term_
import org.opensearch.client.opensearch._types.FieldValue
import org.opensearch.client.opensearch._types.query_dsl.Query
import org.opensearch.client.util.ObjectBuilder

fun List<Filter>.medOmfangFilter(filterParametre: FilterParametre) = this + OmfangFilter(filterParametre)

private class OmfangFilter(parametre: FilterParametre): Filter {
    private val omfang = parametre.omfang?.flatMap { if(it == "HELTID_OG_DELTID") listOf("HELTID","DELTID") else listOf(it) } ?: emptyList()

    override fun erAktiv() = omfang.isNotEmpty()

    override fun lagESFilterFunksjon(): FilterFunksjon = {
        must_ {
            bool_ {
                this.apply {
                    listOf("DELTID", "HELTID")
                        .map { it to if (omfang.contains(it)) ::must_ else ::mustNot_ }
                        .forEach { (kode, mustFunksjon) ->
                            mustFunksjon {
                                term_ {
                                    field("omfangJobbonskerObj.omfangKode")
                                    value(FieldValue.of(kode))
                                }
                            }
                        }
                }
            }
        }
    }
}