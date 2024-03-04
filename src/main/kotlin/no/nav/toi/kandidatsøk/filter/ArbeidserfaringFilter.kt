package no.nav.toi.kandidatsøk.filter

import no.nav.toi.*
import no.nav.toi.kandidatsøk.FilterParametre
import org.opensearch.client.json.JsonData
import org.opensearch.client.opensearch._types.query_dsl.Operator
import org.opensearch.client.opensearch._types.query_dsl.Query

fun List<Filter>.medArbeidserfaringFilter() = this + ArbeidserfaringFilter()

private class ArbeidserfaringFilter: Filter {
    private var arbeidsErfaringer: List<String> = emptyList()
    private var ferskhet: Int? = null
    override fun berikMedParameter(filterParametre: FilterParametre) {
        arbeidsErfaringer=filterParametre.arbeidserfaring ?: emptyList()
        ferskhet=filterParametre.ferskhet
    }

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