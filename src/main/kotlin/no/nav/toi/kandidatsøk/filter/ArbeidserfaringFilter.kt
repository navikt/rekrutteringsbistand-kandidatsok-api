package no.nav.toi.kandidatsøk.filter

import no.nav.toi.*
import no.nav.toi.kandidatsøk.FilterParametre
import org.opensearch.client.opensearch._types.query_dsl.Operator
import org.opensearch.client.opensearch._types.query_dsl.Query

fun List<Filter>.medArbeidserfaringFilter(filterParametre: FilterParametre) = this + ArbeidserfaringFilter(filterParametre)

private class ArbeidserfaringFilter(parametre: FilterParametre): Filter {
    private val arbeidsErfaringer = parametre.arbeidserfaring ?: emptyList()
    private val ferskhet =  parametre.ferskhet

    override fun erAktiv() = arbeidsErfaringer.isNotEmpty()

    override fun lagESFilterFunksjon(): FilterFunksjon = {
        must_ {
            bool_ {
                apply {
                    arbeidsErfaringer.forEach { arbeidsErfaring ->
                        should_ {
                            nested_ {
                                path("yrkeserfaring")
                                query_ {
                                    if (ferskhet == null) {
                                        match(arbeidsErfaring)
                                    }
                                    else {
                                        bool_ {
                                            must_ {
                                                match(arbeidsErfaring)
                                            }
                                            must_ {
                                                range_ {
                                                    field("yrkeserfaring.tilDato")
                                                    gte("now-${ferskhet}y")
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    private fun Query.Builder.match(arbeidsErfaring: String) =
        match_ {
            field("yrkeserfaring.sokeTitler")
            operator(Operator.And)
            query(arbeidsErfaring)
        }
}