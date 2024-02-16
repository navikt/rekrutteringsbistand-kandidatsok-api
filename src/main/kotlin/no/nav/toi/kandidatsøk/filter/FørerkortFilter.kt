package no.nav.toi.kandidatsøk.filter

import no.nav.toi.*
import org.opensearch.client.opensearch._types.FieldValue
import org.opensearch.client.opensearch._types.query_dsl.ChildScoreMode

fun List<Filter>.medFørerkortFilter() = this + FørerkortFilter()

private interface Førerkort {
    val kode: String
    fun alleSomKanKjøreDetteKortet() = setOf(this)
}
private object LettMotorsykkel: Førerkort {
    override val kode = "A1 - Lett motorsykkel"
    override fun alleSomKanKjøreDetteKortet() = setOf(this) + MellomtungMotorsykkel.alleSomKanKjøreDetteKortet()
}
private object MellomtungMotorsykkel: Førerkort {
    override val kode = "A2 - Mellomtung motorsykkel"
    override fun alleSomKanKjøreDetteKortet() = setOf(this) + TungMotorsykkel.alleSomKanKjøreDetteKortet()
}
private object TungMotorsykkel: Førerkort {
    override val kode = "A - Tung motorsykkel"
}
private object Personbil: Førerkort {
    private val alleSomKanKjøreDetteKortet = (listOf(PersonbilMedTilhenger,LettLastebil,Minibuss)
        .flatMap(Førerkort::alleSomKanKjøreDetteKortet)+this).toSet()
    override val kode = "B - Personbil"
    override fun alleSomKanKjøreDetteKortet() = alleSomKanKjøreDetteKortet
}
private object PersonbilMedTilhenger: Førerkort {
    private val alleSomKanKjøreDetteKortet = (listOf(LettLastebilMedTilhenger,MinibussMedTilhenger)
        .flatMap(Førerkort::alleSomKanKjøreDetteKortet)+this).toSet()
    override val kode = "BE - Personbil med tilhenger"
    override fun alleSomKanKjøreDetteKortet() = alleSomKanKjøreDetteKortet
}
private object LettLastebil: Førerkort {
    private val alleSomKanKjøreDetteKortet = (listOf(LettLastebilMedTilhenger,Lastebil)
        .flatMap(Førerkort::alleSomKanKjøreDetteKortet)+this).toSet()
    override val kode = "C1 - Lett lastebil"
    override fun alleSomKanKjøreDetteKortet() = alleSomKanKjøreDetteKortet
}
private object LettLastebilMedTilhenger: Førerkort {
    override val kode = "C1E - Lett lastebil med tilhenger"
    override fun alleSomKanKjøreDetteKortet() = setOf(this) + LastebilMedTilhenger.alleSomKanKjøreDetteKortet()
}
private object Lastebil: Førerkort {
    override val kode = "C - Lastebil"
    override fun alleSomKanKjøreDetteKortet() = setOf(this) + LastebilMedTilhenger.alleSomKanKjøreDetteKortet()
}
private object LastebilMedTilhenger: Førerkort {
    override val kode = "CE - Lastebil med tilhenger"
}
private object Minibuss: Førerkort {
    private val alleSomKanKjøreDetteKortet = (listOf(MinibussMedTilhenger,Buss)
        .flatMap(Førerkort::alleSomKanKjøreDetteKortet)+this).toSet()
    override val kode = "D1 - Minibuss"
    override fun alleSomKanKjøreDetteKortet() = alleSomKanKjøreDetteKortet
}
private object MinibussMedTilhenger: Førerkort {
    override val kode = "D1E - Minibuss med tilhenger"
    override fun alleSomKanKjøreDetteKortet() = setOf(this) + BussMedTilhenger.alleSomKanKjøreDetteKortet()
}
private object Buss: Førerkort {
    override val kode = "D - Buss"
    override fun alleSomKanKjøreDetteKortet() = setOf(this) + BussMedTilhenger.alleSomKanKjøreDetteKortet()
}
private object BussMedTilhenger: Førerkort {
    override val kode = "DE - Buss med tilhenger"
}
private object Traktor: Førerkort {
    override val kode = "T - Traktor"
}
private object Snøscooter: Førerkort {
    override val kode = "S - Snøscooter"
}
private val alleFørerkort = listOf(
    LettMotorsykkel,
    MellomtungMotorsykkel,
    TungMotorsykkel,
    Personbil,
    PersonbilMedTilhenger,
    LettLastebil,
    LettLastebilMedTilhenger,
    Lastebil,
    LastebilMedTilhenger,
    Minibuss,
    MinibussMedTilhenger,
    Buss,
    BussMedTilhenger,
    Traktor,
    Snøscooter
)
private fun String.somFørerkort() =
    alleFørerkort.firstOrNull { it.kode == this } ?: throw Exception("Ukjent førerkort: $this")

private class FørerkortFilter: Filter {
    private var førerkort = emptySet<Førerkort>()
    override fun berikMedParameter(hentParameter: (String) -> Parameter?) {
        førerkort = hentParameter("førerkort")
            ?.somStringListe()
            ?.map(String::somFørerkort)
            ?.flatMap(Førerkort::alleSomKanKjøreDetteKortet)
            ?.toSet()
            ?: førerkort
    }

    override fun erAktiv() = førerkort.isNotEmpty()

    override fun lagESFilterFunksjon(): FilterFunksjon = {
        must_ {
            bool_ {
                apply {
                    førerkort.forEach { førerkort ->
                        should_ {
                            nested_ {
                                path("forerkort")
                                scoreMode(ChildScoreMode.Sum)
                                query_ {
                                    term_ {
                                        field("forerkort.forerkortKodeKlasse")
                                        value(FieldValue.of(førerkort.kode))
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
