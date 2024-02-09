package no.nav.toi.kandidatsøk.filter

import no.nav.toi.*
import org.opensearch.client.opensearch._types.query_dsl.Operator

class Arbeidsønskefilter: Filter {
    private var arbeidsønske: String? = null
    override fun berikMedParameter(hentParameter: (String) -> String?) {
        arbeidsønske = hentParameter("arbeidsonske")
    }

    override fun erAktiv() = arbeidsønske!=null

    override fun lagESFilterFunksjon(): FilterFunksjon = {
        must_ {
            bool_ {
                should_ {
                    match_ {
                        field("yrkeJobbonskerObj.styrkBeskrivelse")
                        fuzziness("0")  // TODO: Avklare om string er like greit som number
                        operator(Operator.And)
                        query("Sauegjeter")
                    }
                }
                should_ {
                    match_ {
                        field("yrkeJobbonskerObj.sokeTitler")
                        fuzziness("0")
                        operator(Operator.And)
                        query("Sauegjeter")
                    }
                }
            }
        }
    }
}