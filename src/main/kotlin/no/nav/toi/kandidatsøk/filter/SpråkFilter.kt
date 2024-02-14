package no.nav.toi.kandidatsøk.filter

import no.nav.toi.*
import org.opensearch.client.opensearch._types.query_dsl.ChildScoreMode
import org.opensearch.client.opensearch._types.query_dsl.Operator

class SpråkFilter: Filter {
    private var språk: String? = null
    override fun berikMedParameter(hentParameter: (String) -> Parameter?) {
        språk = hentParameter("sprak")?.somString()
    }

    override fun erAktiv() = språk != null

    override fun lagESFilterFunksjon(): FilterFunksjon = {
        must_ {
            bool_ {
                should_ {
                    nested_ {
                        path("sprak")
                        query_ {
                            match_ {
                                field("sprak.sprakKodeTekst")
                                operator(Operator.And)
                                query(språk!!)
                            }
                        }
                        scoreMode(ChildScoreMode.Sum)
                    }
                }
            }
        }
    }
}