package no.nav.toi.kandidatsøk.filter

import no.nav.toi.*
import no.nav.toi.kandidatsøk.FilterParametre
import org.opensearch.client.opensearch._types.query_dsl.ChildScoreMode
import org.opensearch.client.opensearch._types.query_dsl.Operator

fun List<Filter>.medSpråkFilter() = this + SpråkFilter()

private class SpråkFilter: Filter {
    private var språk: String? = null
    override fun berikMedParameter(filterParametre: FilterParametre) {
        språk = filterParametre.språk
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