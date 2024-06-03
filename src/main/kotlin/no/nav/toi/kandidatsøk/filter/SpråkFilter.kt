package no.nav.toi.kandidatsøk.filter

import no.nav.toi.*
import no.nav.toi.kandidatsøk.FilterParametre
import org.opensearch.client.opensearch._types.query_dsl.ChildScoreMode
import org.opensearch.client.opensearch._types.query_dsl.Operator

fun List<Filter>.medSpråkFilter(filterParametre: FilterParametre) = this + SpråkFilter(filterParametre)

private class SpråkFilter(parametre: FilterParametre): Filter {
    private val språk = parametre.språk ?: emptyList()

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