package no.nav.toi.kandidatsøk.filter

import no.nav.toi.*
import org.opensearch.client.opensearch._types.query_dsl.Operator

class ArbeidserfaringFilter: Filter {
    private var arbeidsErfaring: String? = null
    override fun berikMedParameter(hentParameter: (String) -> String?) {
        arbeidsErfaring=hentParameter("arbeidserfaring")
    }

    override fun erAktiv() = arbeidsErfaring != null

    override fun lagESFilterFunksjon(): FilterFunksjon = {
        must_ {
            bool_ {
                should_ {
                    nested_ {
                        path("yrkeserfaring")
                        query_ {
                            match_ {
                                field("yrkeserfaring.sokeTitler")
                                operator(Operator.And)
                                query(arbeidsErfaring!!)
                            }
                        }
                    }
                }
            }
        }
    }
}