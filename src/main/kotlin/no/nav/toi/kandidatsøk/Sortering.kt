package no.nav.toi.kandidatsøk

import no.nav.toi.sort
import org.opensearch.client.opensearch._types.SortOrder
import org.opensearch.client.opensearch.core.SearchRequest


internal interface Sortering {
    fun erAktiv(parameterVerdi: String?): Boolean
    fun lagSorteringES(): SearchRequest.Builder.() -> SearchRequest.Builder
}

private object SisteFørst: Sortering {
    override fun erAktiv(parameterVerdi: String?) = parameterVerdi == null || "nyeste" == parameterVerdi
    override fun lagSorteringES(): SearchRequest.Builder.() -> SearchRequest.Builder = {
        sort("tidsstempel", SortOrder.Desc)
    }
}

private object FlestKriterier: Sortering {
    override fun erAktiv(parameterVerdi: String?) = "score" == parameterVerdi
    override fun lagSorteringES(): SearchRequest.Builder.() -> SearchRequest.Builder = {this}
}

internal fun String?.tilSortering() = listOf(SisteFørst,FlestKriterier).first { it.erAktiv(this) }