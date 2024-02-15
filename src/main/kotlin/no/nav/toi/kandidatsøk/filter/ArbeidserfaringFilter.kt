package no.nav.toi.kandidatsøk.filter

import no.nav.toi.*
import org.opensearch.client.opensearch._types.query_dsl.Operator

class ArbeidserfaringFilter: Filter {
    private var arbeidsErfaringer: List<String> = emptyList()
    override fun berikMedParameter(hentParameter: (String) -> Parameter?) {
        arbeidsErfaringer=hentParameter("arbeidserfaring")?.somStringListe() ?: arbeidsErfaringer
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
                                    match_ {
                                        field("yrkeserfaring.sokeTitler")
                                        operator(Operator.And)
                                        query(arbeidsErfaring)
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