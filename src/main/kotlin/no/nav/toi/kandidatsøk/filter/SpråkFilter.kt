package no.nav.toi.kandidatsøk.filter

import no.nav.toi.*
import no.nav.toi.kandidatsøk.FilterParametre
import org.opensearch.client.opensearch._types.query_dsl.ChildScoreMode
import org.opensearch.client.opensearch._types.query_dsl.Operator

fun List<Filter>.medSpråkFilter() = this + SpråkFilter()

private class SpråkFilter: Filter {
    private var språk = emptyList<String>()
    override fun berikMedParameter(filterParametre: FilterParametre) {
        språk = filterParametre.språk ?: emptyList()
    }

    override fun erAktiv() = språk.isNotEmpty()

    override fun lagESFilterFunksjon(): FilterFunksjon = {
        must_ {
            bool_ {
                apply {
                    språk.forEach {språk ->
                        should_ {
                            nested_ {
                                path("sprak")
                                query_ {
                                    match_ {
                                        field("sprak.sprakKodeTekst")
                                        operator(Operator.And)
                                        query(språk)
                                    }
                                }
                                scoreMode(ChildScoreMode.Sum)
                            }
                        }
                    }
                }
            }
        }
    }
}