import java.time.LocalDate

object KandidatsøkRespons {
    fun query(vararg extraTerms: String, sortering: Boolean = true, innsatsgruppeTerm: String = """{"terms":{"kvalifiseringsgruppekode":["BATT","BFORM","IKVAL","VARIG"]}}""", from: Int = 0) = (extraTerms.toList() + innsatsgruppeTerm)
        .let { terms ->
            """{"_source":{"includes":["fodselsnummer","fornavn","etternavn","arenaKandidatnr","kvalifiseringsgruppekode","yrkeJobbonskerObj","geografiJobbonsker","kommuneNavn","postnummer"]},"from":$from,"query":{"bool":{"must":$terms}},"size":25,"track_total_hits":true${if(sortering)""","sort":[{"tidsstempel":{"order":"desc"}}]""" else ""}}"""
        }

    val stedTerm = """{"bool":{"should":[{"nested":{"path":"geografiJobbonsker","query":{"bool":{"should":[{"regexp":{"geografiJobbonsker.geografiKode":{"value":"NO18.1804|NO18|NO"}}}]}}}},{"nested":{"path":"geografiJobbonsker","query":{"bool":{"should":[{"regexp":{"geografiJobbonsker.geografiKode":{"value":"NO50.5001|NO50|NO"}}}]}}}},{"nested":{"path":"geografiJobbonsker","query":{"bool":{"should":[{"regexp":{"geografiJobbonsker.geografiKode":{"value":"NO02.*|NO02|NO"}}}]}}}},{"nested":{"path":"geografiJobbonsker","query":{"bool":{"should":[{"regexp":{"geografiJobbonsker.geografiKode":{"value":"NO.*|NO|NO"}}}]}}}}]}}"""
    val stedMedMåBoPåTerm = """{"bool": {"should": [{"nested": {"path": "geografiJobbonsker","query": {"bool": {"should": [{"regexp": {"geografiJobbonsker.geografiKode": {"value":"NO03.0301|NO03|NO"}}}]}}}},{"nested": {"path": "geografiJobbonsker","query": {"bool": {"should": [{"regexp": {"geografiJobbonsker.geografiKode": {"value":"NO46.4601|NO46|NO"}}}]}}}},{"nested": {"path": "geografiJobbonsker","query": {"bool": {"should": [{"regexp": {"geografiJobbonsker.geografiKode": {"value":"NO.*|NO|NO"}}}]}}}},{"nested": {"path": "geografiJobbonsker","query": {"bool": {"should": [{"regexp": {"geografiJobbonsker.geografiKode": {"value":"NO15.*|NO15|NO"}}}]}}}}]}},{"bool": {"should": [{"regexp": {"kommunenummerstring": {"value":"0301"}}},{"regexp": {"kommunenummerstring": {"value":"4601"}}},{"regexp": {"kommunenummerstring": {"value":".*"}}},{"regexp": {"kommunenummerstring": {"value":"15.*"}}}]}}"""
    val arbeidsønskeTerm = """{"bool":{"should":[{"match":{"yrkeJobbonskerObj.styrkBeskrivelse":{"query":"Sauegjeter","fuzziness":"0","operator":"and"}}},{"match":{"yrkeJobbonskerObj.sokeTitler":{"query":"Sauegjeter","fuzziness":"0","operator":"and"}}},{"match":{"yrkeJobbonskerObj.styrkBeskrivelse":{"query":"Saueklipper","fuzziness":"0","operator":"and"}}},{"match":{"yrkeJobbonskerObj.sokeTitler":{"query":"Saueklipper","fuzziness":"0","operator":"and"}}}]}}"""
    val innsatsgruppeTermMedBATTogBFORM = """{"terms":{"kvalifiseringsgruppekode":["BATT","BFORM"]}}"""
    val innsatsgruppeTermMedANDRE = """{"terms":{"kvalifiseringsgruppekode":["ANDRE","IVURD","BKART","OPPFI","VURDI","VURDU"]}}"""
    val innsatsgruppeTermMedAlle = """{"terms":{"kvalifiseringsgruppekode":["ANDRE","IKVAL","VARIG","BFORM","BATT","IVURD","BKART","OPPFI","VURDI","VURDU"]}}"""
    val språkTerm = """{"bool":{"should":[{"nested":{"path":"sprak","query":{"match":{"sprak.sprakKodeTekst":{"query":"Nynorsk","operator":"and"}}},"score_mode":"sum"}},{"nested":{"path":"sprak","query":{"match":{"sprak.sprakKodeTekst":{"query":"Norsk","operator":"and"}}},"score_mode":"sum"}}]}}"""
    val arbeidsErfaringTerm = """{"bool":{"should":[{"nested":{"path":"yrkeserfaring","query":{"match":{"yrkeserfaring.sokeTitler":{"query":"Barnehageassistent","operator":"and"}}}}},{"nested":{"path":"yrkeserfaring","query":{"match":{"yrkeserfaring.sokeTitler":{"query":"Butikkansvarlig","operator":"and"}}}}}]}}"""
    val nyligArbeidsErfaringTerm = """{"bool":{"should":[{"nested":{"path":"yrkeserfaring","query":{"bool":{"must":[{"match":{"yrkeserfaring.sokeTitler":{"query":"Hvalfanger","operator":"and"}}},{"range":{"yrkeserfaring.tilDato":{"gte":"now-2y"}}}]}}}},{"nested":{"path":"yrkeserfaring","query":{"bool":{"must":[{"match":{"yrkeserfaring.sokeTitler":{"query":"Kokk","operator":"and"}}},{"range":{"yrkeserfaring.tilDato":{"gte":"now-2y"}}}]}}}}]}}"""
    val hovedmålTerm = """{"terms":{"hovedmaalkode":["SKAFFEA","OKEDELT"]}}"""
    val kompetanseTerm = """{"bool":{"should":[{"match_phrase":{"samletKompetanseObj.samletKompetanseTekst":{"query":"Fagbrev FU-operatør","slop":4}}},{"match_phrase":{"samletKompetanseObj.samletKompetanseTekst":{"query":"Kotlin","slop":4}}}]}}"""
    val førerkortTerm = """{"bool":{"should":[{"nested":{"path":"forerkort","query":{"term":{"forerkort.forerkortKodeKlasse":{"value":"BE - Personbil med tilhenger"}}},"score_mode":"sum"}},{"nested":{"path":"forerkort","query":{"term":{"forerkort.forerkortKodeKlasse":{"value":"D - Buss"}}},"score_mode":"sum"}},{"nested":{"path":"forerkort","query":{"term":{"forerkort.forerkortKodeKlasse":{"value":"C1E - Lett lastebil med tilhenger"}}},"score_mode":"sum"}},{"nested":{"path":"forerkort","query":{"term":{"forerkort.forerkortKodeKlasse":{"value":"CE - Lastebil med tilhenger"}}},"score_mode":"sum"}},{"nested":{"path":"forerkort","query":{"term":{"forerkort.forerkortKodeKlasse":{"value":"D1E - Minibuss med tilhenger"}}},"score_mode":"sum"}},{"nested":{"path":"forerkort","query":{"term":{"forerkort.forerkortKodeKlasse":{"value":"DE - Buss med tilhenger"}}},"score_mode":"sum"}}]}}"""
    val utdanningTerm = """{"bool":{"should":[{"bool":{"must":[{"nested":{"path":"utdanning","query":{"regexp":{"utdanning.nusKode":{"value":"[3-4][0-9]*"}}}}}],"must_not":[{"nested":{"path":"utdanning","query":{"regexp":{"utdanning.nusKode":{"value":"[5-8][0-9]*"}}}}}]}},{"bool":{"must":[{"nested":{"path":"utdanning","query":{"regexp":{"utdanning.nusKode":{"value":"6[0-9]*"}}}}}],"must_not":[{"nested":{"path":"utdanning","query":{"regexp":{"utdanning.nusKode":{"value":"[7-8][0-9]*"}}}}}]}},{"bool":{"must":[{"nested":{"path":"utdanning","query":{"regexp":{"utdanning.nusKode":{"value":"8[0-9]*"}}}}}]}}]}}"""
    val prioriterteMålgrupperTerm = """{"bool":{"should":[{"range":{"fodselsdato":{"gte":"now/d-30y","lt":"now"}}},{"range":{"fodselsdato":{"gte":"now-200y/d","lt":"now/d-50y"}}},{"bool":{"must":[{"bool":{"should":[{"nested":{"path":"yrkeserfaring","query":{"bool":{"must":[{"exists":{"field":"yrkeserfaring"}}]}}}},{"nested":{"path":"utdanning","query":{"bool":{"must":[{"exists":{"field":"utdanning"}}]}}}},{"nested":{"path":"forerkort","query":{"bool":{"must":[{"exists":{"field":"forerkort"}}]}}}},{"exists":{"field":"kursObj"}},{"exists":{"field":"fagdokumentasjon"}},{"exists":{"field":"annenerfaringObj"}},{"exists":{"field":"godkjenninger"}}]}},{"bool":{"should":[{"bool":{"must_not":[{"exists":{"field":"perioderMedInaktivitet.startdatoForInnevarendeInaktivePeriode"}},{"exists":{"field":"perioderMedInaktivitet.sluttdatoerForInaktivePerioderPaToArEllerMer"}}]}},{"bool":{"must":[{"exists":{"field":"perioderMedInaktivitet.startdatoForInnevarendeInaktivePeriode"}},{"bool":{"should":[{"range":{"perioderMedInaktivitet.startdatoForInnevarendeInaktivePeriode":{"lte":"${LocalDate.now().minusYears(2)}"}}},{"range":{"perioderMedInaktivitet.sluttdatoerForInaktivePerioderPaToArEllerMer":{"gte":"${LocalDate.now().minusYears(3)}"}}}]}}]}}]}}]}}]}}"""
    val queryMedIdentTerm = """{"bool":{"should":[{"term":{"aktorId":{"value":"12345678910"}}},{"term":{"fodselsnummer":{"value":"12345678910"}}}]}}"""
    val queryMedArenaKandidatnrTerm = """{"term":{"kandidatnr":{"value":"ab123"}}}"""
    val queryMedPAMKandidatnrTerm = """{"term":{"kandidatnr":{"value":"PAM01Z"}}}"""
    val queryMedKMultiMatchTerm = """{"multi_match":{"query":"søkeord","fields":["fritekst^1","fornavn^1","etternavn^1","yrkeJobbonskerObj.styrkBeskrivelse^1.5","yrkeJobbonskerObj.sokeTitler^1"]}}"""
    val mineBrukereTerm = """{"term": {"veileder": {"value":"A123456"}}}"""
    val valgtKontorTerm = """{"bool":{"should":[{"term":{"navkontor":{"value":"NAV Hamar"}}},{"term":{"navkontor":{"value":"NAV Lofoten"}}}]}}"""
    val kandidatsøkHits = """[
                    {
                        "_index": "veilederkandidat_os4",
                        "_type": "_doc",
                        "_id": "PAM0yg2tn43a",
                        "_score": null,
                        "_source": {
                            "yrkeJobbonskerObj": [
                                {
                                    "styrkBeskrivelse": "Snøbrettlærer",
                                    "sokeTitler": [
                                        "Snøbrettlærer",
                                        "Snowboardlærer",
                                        "Snøbrettlærer"
                                    ],
                                    "primaertJobbonske": false,
                                    "styrkKode": null
                                },
                                {
                                    "styrkBeskrivelse": "Klokker",
                                    "sokeTitler": [
                                        "Klokker",
                                        "Klokker"
                                    ],
                                    "primaertJobbonske": false,
                                    "styrkKode": null
                                }
                            ],
                            "etternavn": "Snøklokke",
                            "postnummer": "2316",
                            "arenaKandidatnr": "PAM0yg2tn43a",
                            "kommuneNavn": "Hamar",
                            "geografiJobbonsker": [
                                {
                                    "geografiKodeTekst": "Oslo",
                                    "geografiKode": "NO03"
                                }
                            ],
                            "fornavn": "Nybakt",
                            "fodselsnummer": "31419334556",
                            "kvalifiseringsgruppekode": "BATT"
                        },
                        "sort": [
                            1707134341159
                        ]
                    },
                    {
                        "_index": "veilederkandidat_os4",
                        "_type": "_doc",
                        "_id": "PAM011x82e2yw",
                        "_score": null,
                        "_source": {
                            "yrkeJobbonskerObj": [],
                            "etternavn": "Veps",
                            "postnummer": "1643",
                            "arenaKandidatnr": "PAM011x82e2yw",
                            "kommuneNavn": "Råde",
                            "geografiJobbonsker": [],
                            "fornavn": "Vennlig",
                            "fodselsnummer": "02497030229",
                            "kvalifiseringsgruppekode": "BATT"
                        },
                        "sort": [
                            1706786611727
                        ]
                    },
                    {
                        "_index": "veilederkandidat_os4",
                        "_type": "_doc",
                        "_id": "PAM0152hb0wr4",
                        "_score": null,
                        "_source": {
                            "yrkeJobbonskerObj": [
                                {
                                    "styrkBeskrivelse": "Avisbud",
                                    "sokeTitler": [
                                        "Avisbud",
                                        "Avisbud",
                                        "Bilagskontrollør (avisbud)",
                                        "Avis- og reklamebrosjyrebud",
                                        "Altmuligmann",
                                        "Avis- og reklamedistributør",
                                        "Utdeler (gratisavis)",
                                        "Reklamebud",
                                        "Reklame- og avisdistributør",
                                        "Bud, utlevering"
                                    ],
                                    "primaertJobbonske": false,
                                    "styrkKode": null
                                }
                            ],
                            "etternavn": "Lukt",
                            "postnummer": "1708",
                            "arenaKandidatnr": "PAM0152hb0wr4",
                            "kommuneNavn": "Sarpsborg",
                            "geografiJobbonsker": [
                                {
                                    "geografiKodeTekst": "Norge",
                                    "geografiKode": "NO"
                                }
                            ],
                            "fornavn": "Redd",
                            "fodselsnummer": "04928797045",
                            "kvalifiseringsgruppekode": "IKVAL"
                        },
                        "sort": [
                            1706527557597
                        ]
                    },
                    {
                        "_index": "veilederkandidat_os4",
                        "_type": "_doc",
                        "_id": "PAM0104tl64rq",
                        "_score": null,
                        "_source": {
                            "yrkeJobbonskerObj": [
                                {
                                    "styrkBeskrivelse": "Bonde",
                                    "sokeTitler": [
                                        "Bonde",
                                        "Bonde",
                                        "Gårdbruker",
                                        "Småbruker (kombinasjonsbruk)",
                                        "Gårdsbruker",
                                        "Jordbruker",
                                        "Gårdsstyrer"
                                    ],
                                    "primaertJobbonske": false,
                                    "styrkKode": null
                                }
                            ],
                            "etternavn": "Grense",
                            "postnummer": "2218",
                            "arenaKandidatnr": "PAM0104tl64rq",
                            "kommuneNavn": "Kongsvinger",
                            "geografiJobbonsker": [
                                {
                                    "geografiKodeTekst": "Haram",
                                    "geografiKode": "NO15.1534"
                                }
                            ],
                            "fornavn": "Oppfyllende Korrekt Boble",
                            "fodselsnummer": "19810297487",
                            "kvalifiseringsgruppekode": "IKVAL"
                        },
                        "sort": [
                            1706522288354
                        ]
                    },
                    {
                        "_index": "veilederkandidat_os4",
                        "_type": "_doc",
                        "_id": "PAM0xtfrwli5",
                        "_score": null,
                        "_source": {
                            "yrkeJobbonskerObj": [
                                {
                                    "styrkBeskrivelse": "Butikkassistent",
                                    "sokeTitler": [
                                        "Butikkassistent",
                                        "Butikkmedarbeider",
                                        "Butikkbetjent",
                                        "Butikkassistent",
                                        "Salgsmedarbeider (butikk)",
                                        "Selger",
                                        "Juniorselger",
                                        "Salgsassistent",
                                        "Salgsperson",
                                        "Salgsmedarbeider",
                                        "Salgskraft",
                                        "Kundeservicemedarbeider (salg)",
                                        "Provisjonsselger",
                                        "Rådgivende selger",
                                        "Salgs- og kunderådgiver",
                                        "Salg- og Kundeservicemedarbeider",
                                        "Salgsspesialist",
                                        "Salg - Kundebehandler"
                                    ],
                                    "primaertJobbonske": false,
                                    "styrkKode": null
                                },
                                {
                                    "styrkBeskrivelse": "Butikkmedarbeider",
                                    "sokeTitler": [
                                        "Butikkmedarbeider",
                                        "Butikkmedarbeider",
                                        "Butikkbetjent",
                                        "Butikkassistent",
                                        "Salgsmedarbeider (butikk)",
                                        "Selger",
                                        "Juniorselger",
                                        "Salgsassistent",
                                        "Salgsperson",
                                        "Salgsmedarbeider",
                                        "Salgskraft",
                                        "Kundeservicemedarbeider (salg)",
                                        "Provisjonsselger",
                                        "Rådgivende selger",
                                        "Salgs- og kunderådgiver",
                                        "Salg- og Kundeservicemedarbeider",
                                        "Salgsspesialist",
                                        "Salg - Kundebehandler"
                                    ],
                                    "primaertJobbonske": false,
                                    "styrkKode": null
                                },
                                {
                                    "styrkBeskrivelse": "Kokkeassistent",
                                    "sokeTitler": [
                                        "Kokkeassistent",
                                        "Hjelpekokk",
                                        "Kokkeassistent",
                                        "Kokk",
                                        "Kafekokk",
                                        "Faglært kokk",
                                        "Kjøkkenassistent",
                                        "Ryddehjelp (serveringssted)",
                                        "Anretningshjelp",
                                        "Crew",
                                        "Kjøkkenhjelp",
                                        "Institusjonskokk",
                                        "Ernæringskokk"
                                    ],
                                    "primaertJobbonske": false,
                                    "styrkKode": null
                                }
                            ],
                            "etternavn": "Klippfisk",
                            "postnummer": "1643",
                            "arenaKandidatnr": "PAM0xtfrwli5",
                            "kommuneNavn": "Råde",
                            "geografiJobbonsker": [
                                {
                                    "geografiKodeTekst": "Drammen",
                                    "geografiKode": "NO06.0602"
                                },
                                {
                                    "geografiKodeTekst": "Svelvik",
                                    "geografiKode": "NO07.0711"
                                },
                                {
                                    "geografiKodeTekst": "Viken",
                                    "geografiKode": "NO30"
                                },
                                {
                                    "geografiKodeTekst": "Oslo",
                                    "geografiKode": "NO03"
                                },
                                {
                                    "geografiKodeTekst": "Fredrikstad",
                                    "geografiKode": "NO01.0106"
                                },
                                {
                                    "geografiKodeTekst": "Ballangen",
                                    "geografiKode": "NO18.1854"
                                }
                            ],
                            "fornavn": "Uklar",
                            "fodselsnummer": "07858597719",
                            "kvalifiseringsgruppekode": "BATT"
                        },
                        "sort": [
                            1706262413137
                        ]
                    },
                    {
                        "_index": "veilederkandidat_os4",
                        "_type": "_doc",
                        "_id": "PAM014b40jscf",
                        "_score": null,
                        "_source": {
                            "yrkeJobbonskerObj": [
                                {
                                    "styrkBeskrivelse": "Kioskmedarbeider",
                                    "sokeTitler": [
                                        "Kioskmedarbeider",
                                        "Kioskmedarbeider",
                                        "Kioskarbeider",
                                        "Butikkonsulent",
                                        "Torghandler",
                                        "Butikkmedarbeider",
                                        "Butikkbetjent",
                                        "Butikkassistent",
                                        "Salgsmedarbeider (butikk)"
                                    ],
                                    "primaertJobbonske": false,
                                    "styrkKode": null
                                }
                            ],
                            "etternavn": "Sverm",
                            "postnummer": "1642",
                            "arenaKandidatnr": "PAM014b40jscf",
                            "kommuneNavn": "Råde",
                            "geografiJobbonsker": [
                                {
                                    "geografiKodeTekst": "Fredrikstad",
                                    "geografiKode": "NO01.0106"
                                },
                                {
                                    "geografiKodeTekst": "Moss",
                                    "geografiKode": "NO01.0104"
                                },
                                {
                                    "geografiKodeTekst": "Rygge",
                                    "geografiKode": "NO01.0136"
                                }
                            ],
                            "fornavn": "From",
                            "fodselsnummer": "12447120117",
                            "kvalifiseringsgruppekode": "BATT"
                        },
                        "sort": [
                            1706186807071
                        ]
                    },
                    {
                        "_index": "veilederkandidat_os4",
                        "_type": "_doc",
                        "_id": "PAM013sewrha8",
                        "_score": null,
                        "_source": {
                            "yrkeJobbonskerObj": [
                                {
                                    "styrkBeskrivelse": "Dameskredder",
                                    "sokeTitler": [
                                        "Dameskredder",
                                        "Dameskredder",
                                        "Skredder",
                                        "Dame- og herreskredder"
                                    ],
                                    "primaertJobbonske": false,
                                    "styrkKode": null
                                }
                            ],
                            "etternavn": "Guvernante",
                            "postnummer": "1640",
                            "arenaKandidatnr": "PAM013sewrha8",
                            "kommuneNavn": "Råde",
                            "geografiJobbonsker": [
                                {
                                    "geografiKodeTekst": "Oslo",
                                    "geografiKode": "NO03"
                                },
                                {
                                    "geografiKodeTekst": "Innlandet",
                                    "geografiKode": "NO34"
                                }
                            ],
                            "fornavn": "Fast",
                            "fodselsnummer": "23427744876",
                            "kvalifiseringsgruppekode": "BATT"
                        },
                        "sort": [
                            1706186573969
                        ]
                    },
                    {
                        "_index": "veilederkandidat_os4",
                        "_type": "_doc",
                        "_id": "PAM01041jy5bu",
                        "_score": null,
                        "_source": {
                            "yrkeJobbonskerObj": [
                                {
                                    "styrkBeskrivelse": "Kjøreskolelærer",
                                    "sokeTitler": [
                                        "Kjøreskolelærer",
                                        "Kjørelærer",
                                        "Kjøreskolelærer",
                                        "Kjøreskoleinstruktør",
                                        "Trafikklærer"
                                    ],
                                    "primaertJobbonske": false,
                                    "styrkKode": null
                                }
                            ],
                            "etternavn": "Fjernsyn",
                            "postnummer": "1640",
                            "arenaKandidatnr": "PAM01041jy5bu",
                            "kommuneNavn": "Råde",
                            "geografiJobbonsker": [
                                {
                                    "geografiKodeTekst": "Akershus",
                                    "geografiKode": "NO02"
                                },
                                {
                                    "geografiKodeTekst": "Viken",
                                    "geografiKode": "NO30"
                                },
                                {
                                    "geografiKodeTekst": "Østfold",
                                    "geografiKode": "NO01"
                                }
                            ],
                            "fornavn": "Possessiv",
                            "fodselsnummer": "07499738492",
                            "kvalifiseringsgruppekode": "BATT"
                        },
                        "sort": [
                            1706185850959
                        ]
                    },
                    {
                        "_index": "veilederkandidat_os4",
                        "_type": "_doc",
                        "_id": "PAM01b5x3j6ud",
                        "_score": null,
                        "_source": {
                            "yrkeJobbonskerObj": [
                                {
                                    "styrkBeskrivelse": "Frisør",
                                    "sokeTitler": [
                                        "Frisør",
                                        "Frisør",
                                        "Frisørsvenn"
                                    ],
                                    "primaertJobbonske": false,
                                    "styrkKode": null
                                },
                                {
                                    "styrkBeskrivelse": "Sauegjeter",
                                    "sokeTitler": [
                                        "Sauegjeter",
                                        "Sauegjeter",
                                        "Gjeter"
                                    ],
                                    "primaertJobbonske": false,
                                    "styrkKode": null
                                },
                                {
                                    "styrkBeskrivelse": "Saueklipper",
                                    "sokeTitler": [
                                        "Saueklipper",
                                        "Saueklipper",
                                        "Sauegjeter"
                                    ],
                                    "primaertJobbonske": false,
                                    "styrkKode": null
                                },
                                {
                                    "styrkBeskrivelse": "Ullklassifisør",
                                    "sokeTitler": [
                                        "Ullklassifisør",
                                        "Ullklassifisør",
                                        "Ullpresser",
                                        "Ullklassifisør, Ullpresse"
                                    ],
                                    "primaertJobbonske": false,
                                    "styrkKode": null
                                }
                            ],
                            "etternavn": "Avslutning",
                            "postnummer": "3478",
                            "arenaKandidatnr": "PAM01b5x3j6ud",
                            "kommuneNavn": "Asker",
                            "geografiJobbonsker": [
                                {
                                    "geografiKodeTekst": "Hamar",
                                    "geografiKode": "NO04.0403"
                                },
                                {
                                    "geografiKodeTekst": "Råde",
                                    "geografiKode": "NO30.3017"
                                },
                                {
                                    "geografiKodeTekst": "Vestby",
                                    "geografiKode": "NO02.0211"
                                }
                            ],
                            "fornavn": "Rettferdig",
                            "fodselsnummer": "25898097334",
                            "kvalifiseringsgruppekode": "BATT"
                        },
                        "sort": [
                            1702467078063
                        ]
                    },
                    {
                        "_index": "veilederkandidat_os4",
                        "_type": "_doc",
                        "_id": "PAM010rnkxtwg",
                        "_score": null,
                        "_source": {
                            "yrkeJobbonskerObj": [
                                {
                                    "styrkBeskrivelse": "Butikkmedarbeider",
                                    "sokeTitler": [
                                        "Butikkmedarbeider",
                                        "Butikkmedarbeider",
                                        "Butikkbetjent",
                                        "Butikkassistent",
                                        "Salgsmedarbeider (butikk)",
                                        "Selger",
                                        "Juniorselger",
                                        "Salgsassistent",
                                        "Salgsperson",
                                        "Salgsmedarbeider",
                                        "Salgskraft",
                                        "Kundeservicemedarbeider (salg)",
                                        "Provisjonsselger",
                                        "Rådgivende selger",
                                        "Salgs- og kunderådgiver",
                                        "Salg- og Kundeservicemedarbeider",
                                        "Salgsspesialist",
                                        "Salg - Kundebehandler"
                                    ],
                                    "primaertJobbonske": false,
                                    "styrkKode": null
                                },
                                {
                                    "styrkBeskrivelse": "Frisør",
                                    "sokeTitler": [
                                        "Frisør",
                                        "Frisør",
                                        "Frisørsvenn"
                                    ],
                                    "primaertJobbonske": false,
                                    "styrkKode": null
                                },
                                {
                                    "styrkBeskrivelse": "Sauegjeter",
                                    "sokeTitler": [
                                        "Sauegjeter",
                                        "Sauegjeter",
                                        "Gjeter"
                                    ],
                                    "primaertJobbonske": false,
                                    "styrkKode": null
                                },
                                {
                                    "styrkBeskrivelse": "Saueklipper",
                                    "sokeTitler": [
                                        "Saueklipper",
                                        "Saueklipper",
                                        "Sauegjeter"
                                    ],
                                    "primaertJobbonske": false,
                                    "styrkKode": null
                                },
                                {
                                    "styrkBeskrivelse": "Ullklassifisør",
                                    "sokeTitler": [
                                        "Ullklassifisør",
                                        "Ullklassifisør",
                                        "Ullpresser",
                                        "Ullklassifisør, Ullpresse"
                                    ],
                                    "primaertJobbonske": false,
                                    "styrkKode": null
                                },
                                {
                                    "styrkBeskrivelse": "Sykepleier",
                                    "sokeTitler": [
                                        "Sykepleier",
                                        "Sykepleier",
                                        "Offentlig godkjent sykepleier",
                                        "Sykepleier ved hjemmetjenesten"
                                    ],
                                    "primaertJobbonske": false,
                                    "styrkKode": null
                                }
                            ],
                            "etternavn": "Alm",
                            "postnummer": "2316",
                            "arenaKandidatnr": "PAM010rnkxtwg",
                            "kommuneNavn": "Hamar",
                            "geografiJobbonsker": [
                                {
                                    "geografiKodeTekst": "Hamar",
                                    "geografiKode": "NO04.0403"
                                },
                                {
                                    "geografiKodeTekst": "Råde",
                                    "geografiKode": "NO30.3017"
                                },
                                {
                                    "geografiKodeTekst": "Vestby",
                                    "geografiKode": "NO02.0211"
                                }
                            ],
                            "fornavn": "Umotivert",
                            "fodselsnummer": "10428826731",
                            "kvalifiseringsgruppekode": "BATT"
                        },
                        "sort": [
                            1702294042484
                        ]
                    },
                    {
                        "_index": "veilederkandidat_os4",
                        "_type": "_doc",
                        "_id": "PAM01309udytt",
                        "_score": null,
                        "_source": {
                            "yrkeJobbonskerObj": [
                                {
                                    "styrkBeskrivelse": "Barnehageassistent",
                                    "sokeTitler": [
                                        "Barnehageassistent",
                                        "Barnehageassistent",
                                        "Friluftsparkassistent",
                                        "Førskolelærer",
                                        "Fagarbeider barn"
                                    ],
                                    "primaertJobbonske": false,
                                    "styrkKode": null
                                }
                            ],
                            "etternavn": "Parykk",
                            "postnummer": "1642",
                            "arenaKandidatnr": "PAM01309udytt",
                            "kommuneNavn": "Råde",
                            "geografiJobbonsker": [
                                {
                                    "geografiKodeTekst": "Viken",
                                    "geografiKode": "NO30"
                                }
                            ],
                            "fornavn": "Opplyst",
                            "fodselsnummer": "04829398279",
                            "kvalifiseringsgruppekode": "BATT"
                        },
                        "sort": [
                            1702042431227
                        ]
                    },
                    {
                        "_index": "veilederkandidat_os4",
                        "_type": "_doc",
                        "_id": "PAM0vav7jc9o",
                        "_score": null,
                        "_source": {
                            "yrkeJobbonskerObj": [
                                {
                                    "styrkBeskrivelse": "Tester",
                                    "sokeTitler": [
                                        "Tester",
                                        "Tester",
                                        "Testleder"
                                    ],
                                    "primaertJobbonske": false,
                                    "styrkKode": null
                                }
                            ],
                            "etternavn": "Melding",
                            "postnummer": "8540",
                            "arenaKandidatnr": "PAM0vav7jc9o",
                            "kommuneNavn": "Narvik",
                            "geografiJobbonsker": [
                                {
                                    "geografiKodeTekst": "Ballangen",
                                    "geografiKode": "NO18.1854"
                                }
                            ],
                            "fornavn": "Ufruktbar",
                            "fodselsnummer": "66870375701",
                            "kvalifiseringsgruppekode": "VARIG"
                        },
                        "sort": [
                            1702041367345
                        ]
                    },
                    {
                        "_index": "veilederkandidat_os4",
                        "_type": "_doc",
                        "_id": "GjI2Q4wBTrJ2jfiJLCtm",
                        "_score": null,
                        "_source": {
                            "yrkeJobbonskerObj": [
                                {
                                    "styrkBeskrivelse": "Butikkassistent",
                                    "sokeTitler": [
                                        "Butikkassistent",
                                        "Butikkmedarbeider",
                                        "Butikkbetjent",
                                        "Butikkassistent",
                                        "Salgsmedarbeider (butikk)",
                                        "Selger",
                                        "Juniorselger",
                                        "Salgsassistent",
                                        "Salgsperson",
                                        "Salgsmedarbeider",
                                        "Salgskraft",
                                        "Kundeservicemedarbeider (salg)",
                                        "Provisjonsselger",
                                        "Rådgivende selger",
                                        "Salgs- og kunderådgiver",
                                        "Salg- og Kundeservicemedarbeider",
                                        "Salgsspesialist",
                                        "Salg - Kundebehandler"
                                    ],
                                    "primaertJobbonske": false,
                                    "styrkKode": null
                                },
                                {
                                    "styrkBeskrivelse": "Butikkmedarbeider",
                                    "sokeTitler": [
                                        "Butikkmedarbeider",
                                        "Butikkmedarbeider",
                                        "Butikkbetjent",
                                        "Butikkassistent",
                                        "Salgsmedarbeider (butikk)",
                                        "Selger",
                                        "Juniorselger",
                                        "Salgsassistent",
                                        "Salgsperson",
                                        "Salgsmedarbeider",
                                        "Salgskraft",
                                        "Kundeservicemedarbeider (salg)",
                                        "Provisjonsselger",
                                        "Rådgivende selger",
                                        "Salgs- og kunderådgiver",
                                        "Salg- og Kundeservicemedarbeider",
                                        "Salgsspesialist",
                                        "Salg - Kundebehandler"
                                    ],
                                    "primaertJobbonske": false,
                                    "styrkKode": null
                                },
                                {
                                    "styrkBeskrivelse": "Kokkeassistent",
                                    "sokeTitler": [
                                        "Kokkeassistent",
                                        "Hjelpekokk",
                                        "Kokkeassistent",
                                        "Kokk",
                                        "Kafekokk",
                                        "Faglært kokk",
                                        "Kjøkkenassistent",
                                        "Ryddehjelp (serveringssted)",
                                        "Anretningshjelp",
                                        "Crew",
                                        "Kjøkkenhjelp",
                                        "Institusjonskokk",
                                        "Ernæringskokk"
                                    ],
                                    "primaertJobbonske": false,
                                    "styrkKode": null
                                }
                            ],
                            "etternavn": "Klippfisk",
                            "postnummer": "1643",
                            "arenaKandidatnr": null,
                            "kommuneNavn": "Råde",
                            "geografiJobbonsker": [
                                {
                                    "geografiKodeTekst": "Drammen",
                                    "geografiKode": "NO06.0602"
                                },
                                {
                                    "geografiKodeTekst": "Svelvik",
                                    "geografiKode": "NO07.0711"
                                },
                                {
                                    "geografiKodeTekst": "Viken",
                                    "geografiKode": "NO30"
                                },
                                {
                                    "geografiKodeTekst": "Oslo",
                                    "geografiKode": "NO03"
                                },
                                {
                                    "geografiKodeTekst": "Fredrikstad",
                                    "geografiKode": "NO01.0106"
                                },
                                {
                                    "geografiKodeTekst": "Ballangen",
                                    "geografiKode": "NO18.1854"
                                }
                            ],
                            "fornavn": "Uklar",
                            "fodselsnummer": "07858597719",
                            "kvalifiseringsgruppekode": "BATT"
                        },
                        "sort": [
                            1701934631768
                        ]
                    },
                    {
                        "_index": "veilederkandidat_os4",
                        "_type": "_doc",
                        "_id": "PAM010t1h9y5w",
                        "_score": null,
                        "_source": {
                            "yrkeJobbonskerObj": [
                                {
                                    "styrkBeskrivelse": "Frisør",
                                    "sokeTitler": [
                                        "Frisør",
                                        "Frisør",
                                        "Frisørsvenn"
                                    ],
                                    "primaertJobbonske": false,
                                    "styrkKode": null
                                },
                                {
                                    "styrkBeskrivelse": "Sauegjeter",
                                    "sokeTitler": [
                                        "Sauegjeter",
                                        "Sauegjeter",
                                        "Gjeter"
                                    ],
                                    "primaertJobbonske": false,
                                    "styrkKode": null
                                },
                                {
                                    "styrkBeskrivelse": "Saueklipper",
                                    "sokeTitler": [
                                        "Saueklipper",
                                        "Saueklipper",
                                        "Sauegjeter"
                                    ],
                                    "primaertJobbonske": false,
                                    "styrkKode": null
                                },
                                {
                                    "styrkBeskrivelse": "Ullklassifisør",
                                    "sokeTitler": [
                                        "Ullklassifisør",
                                        "Ullklassifisør",
                                        "Ullpresser",
                                        "Ullklassifisør, Ullpresse"
                                    ],
                                    "primaertJobbonske": false,
                                    "styrkKode": null
                                }
                            ],
                            "etternavn": "Joker",
                            "postnummer": "8300",
                            "arenaKandidatnr": "PAM010t1h9y5w",
                            "kommuneNavn": "Vågan",
                            "geografiJobbonsker": [
                                {
                                    "geografiKodeTekst": "Hamar",
                                    "geografiKode": "NO04.0403"
                                },
                                {
                                    "geografiKodeTekst": "Råde",
                                    "geografiKode": "NO30.3017"
                                },
                                {
                                    "geografiKodeTekst": "Vestby",
                                    "geografiKode": "NO02.0211"
                                }
                            ],
                            "fornavn": "From",
                            "fodselsnummer": "07897498280",
                            "kvalifiseringsgruppekode": "BATT"
                        },
                        "sort": [
                            1701178008204
                        ]
                    },
                    {
                        "_index": "veilederkandidat_os4",
                        "_type": "_doc",
                        "_id": "PAM015hgdhtth",
                        "_score": null,
                        "_source": {
                            "yrkeJobbonskerObj": [
                                {
                                    "styrkBeskrivelse": "Frisør",
                                    "sokeTitler": [
                                        "Frisør",
                                        "Frisør",
                                        "Frisørsvenn"
                                    ],
                                    "primaertJobbonske": false,
                                    "styrkKode": null
                                },
                                {
                                    "styrkBeskrivelse": "Sauegjeter",
                                    "sokeTitler": [
                                        "Sauegjeter",
                                        "Sauegjeter",
                                        "Gjeter"
                                    ],
                                    "primaertJobbonske": false,
                                    "styrkKode": null
                                },
                                {
                                    "styrkBeskrivelse": "Saueklipper",
                                    "sokeTitler": [
                                        "Saueklipper",
                                        "Saueklipper",
                                        "Sauegjeter"
                                    ],
                                    "primaertJobbonske": false,
                                    "styrkKode": null
                                },
                                {
                                    "styrkBeskrivelse": "Ullklassifisør",
                                    "sokeTitler": [
                                        "Ullklassifisør",
                                        "Ullklassifisør",
                                        "Ullpresser",
                                        "Ullklassifisør, Ullpresse"
                                    ],
                                    "primaertJobbonske": false,
                                    "styrkKode": null
                                }
                            ],
                            "etternavn": "Kjøttbolle",
                            "postnummer": "0688",
                            "arenaKandidatnr": "PAM015hgdhtth",
                            "kommuneNavn": "Oslo",
                            "geografiJobbonsker": [
                                {
                                    "geografiKodeTekst": "Hamar",
                                    "geografiKode": "NO04.0403"
                                },
                                {
                                    "geografiKodeTekst": "Råde",
                                    "geografiKode": "NO30.3017"
                                },
                                {
                                    "geografiKodeTekst": "Vestby",
                                    "geografiKode": "NO02.0211"
                                }
                            ],
                            "fornavn": "Passiv",
                            "fodselsnummer": "28869099653",
                            "kvalifiseringsgruppekode": "BATT"
                        },
                        "sort": [
                            1701177434350
                        ]
                    },
                    {
                        "_index": "veilederkandidat_os4",
                        "_type": "_doc",
                        "_id": "PAM0xdj09ivc",
                        "_score": null,
                        "_source": {
                            "yrkeJobbonskerObj": [
                                {
                                    "styrkBeskrivelse": "Frisør",
                                    "sokeTitler": [
                                        "Frisør",
                                        "Frisør",
                                        "Frisørsvenn"
                                    ],
                                    "primaertJobbonske": false,
                                    "styrkKode": null
                                },
                                {
                                    "styrkBeskrivelse": "Sauegjeter",
                                    "sokeTitler": [
                                        "Sauegjeter",
                                        "Sauegjeter",
                                        "Gjeter"
                                    ],
                                    "primaertJobbonske": false,
                                    "styrkKode": null
                                },
                                {
                                    "styrkBeskrivelse": "Saueklipper",
                                    "sokeTitler": [
                                        "Saueklipper",
                                        "Saueklipper",
                                        "Sauegjeter"
                                    ],
                                    "primaertJobbonske": false,
                                    "styrkKode": null
                                },
                                {
                                    "styrkBeskrivelse": "Ullklassifisør",
                                    "sokeTitler": [
                                        "Ullklassifisør",
                                        "Ullklassifisør",
                                        "Ullpresser",
                                        "Ullklassifisør, Ullpresse"
                                    ],
                                    "primaertJobbonske": false,
                                    "styrkKode": null
                                }
                            ],
                            "etternavn": "Frostnatt",
                            "postnummer": "0692",
                            "arenaKandidatnr": "PAM0xdj09ivc",
                            "kommuneNavn": "Oslo",
                            "geografiJobbonsker": [
                                {
                                    "geografiKodeTekst": "Råde",
                                    "geografiKode": "NO30.3017"
                                },
                                {
                                    "geografiKodeTekst": "Vestby",
                                    "geografiKode": "NO02.0211"
                                },
                                {
                                    "geografiKodeTekst": "Hamar",
                                    "geografiKode": "NO04.0403"
                                }
                            ],
                            "fornavn": "Hevngjerrig",
                            "fodselsnummer": "17838298621",
                            "kvalifiseringsgruppekode": "BATT"
                        },
                        "sort": [
                            1701177432334
                        ]
                    },
                    {
                        "_index": "veilederkandidat_os4",
                        "_type": "_doc",
                        "_id": "PAM0169yf7kiw",
                        "_score": null,
                        "_source": {
                            "yrkeJobbonskerObj": [
                                {
                                    "styrkBeskrivelse": "Frisør",
                                    "sokeTitler": [
                                        "Frisør",
                                        "Frisør",
                                        "Frisørsvenn"
                                    ],
                                    "primaertJobbonske": false,
                                    "styrkKode": null
                                },
                                {
                                    "styrkBeskrivelse": "Sauegjeter",
                                    "sokeTitler": [
                                        "Sauegjeter",
                                        "Sauegjeter",
                                        "Gjeter"
                                    ],
                                    "primaertJobbonske": false,
                                    "styrkKode": null
                                },
                                {
                                    "styrkBeskrivelse": "Saueklipper",
                                    "sokeTitler": [
                                        "Saueklipper",
                                        "Saueklipper",
                                        "Sauegjeter"
                                    ],
                                    "primaertJobbonske": false,
                                    "styrkKode": null
                                },
                                {
                                    "styrkBeskrivelse": "Ullklassifisør",
                                    "sokeTitler": [
                                        "Ullklassifisør",
                                        "Ullklassifisør",
                                        "Ullpresser",
                                        "Ullklassifisør, Ullpresse"
                                    ],
                                    "primaertJobbonske": false,
                                    "styrkKode": null
                                }
                            ],
                            "etternavn": "Ørn",
                            "postnummer": "0690",
                            "arenaKandidatnr": "PAM0169yf7kiw",
                            "kommuneNavn": "Oslo",
                            "geografiJobbonsker": [
                                {
                                    "geografiKodeTekst": "Hamar",
                                    "geografiKode": "NO04.0403"
                                },
                                {
                                    "geografiKodeTekst": "Råde",
                                    "geografiKode": "NO30.3017"
                                },
                                {
                                    "geografiKodeTekst": "Vestby",
                                    "geografiKode": "NO02.0211"
                                }
                            ],
                            "fornavn": "Skamfull",
                            "fodselsnummer": "13898799837",
                            "kvalifiseringsgruppekode": "BATT"
                        },
                        "sort": [
                            1701177431555
                        ]
                    },
                    {
                        "_index": "veilederkandidat_os4",
                        "_type": "_doc",
                        "_id": "PAM015lfkggth",
                        "_score": null,
                        "_source": {
                            "yrkeJobbonskerObj": [
                                {
                                    "styrkBeskrivelse": "Frisør",
                                    "sokeTitler": [
                                        "Frisør",
                                        "Frisør",
                                        "Frisørsvenn"
                                    ],
                                    "primaertJobbonske": false,
                                    "styrkKode": null
                                },
                                {
                                    "styrkBeskrivelse": "Sauegjeter",
                                    "sokeTitler": [
                                        "Sauegjeter",
                                        "Sauegjeter",
                                        "Gjeter"
                                    ],
                                    "primaertJobbonske": false,
                                    "styrkKode": null
                                },
                                {
                                    "styrkBeskrivelse": "Saueklipper",
                                    "sokeTitler": [
                                        "Saueklipper",
                                        "Saueklipper",
                                        "Sauegjeter"
                                    ],
                                    "primaertJobbonske": false,
                                    "styrkKode": null
                                },
                                {
                                    "styrkBeskrivelse": "Ullklassifisør",
                                    "sokeTitler": [
                                        "Ullklassifisør",
                                        "Ullklassifisør",
                                        "Ullpresser",
                                        "Ullklassifisør, Ullpresse"
                                    ],
                                    "primaertJobbonske": false,
                                    "styrkKode": null
                                }
                            ],
                            "etternavn": "Rødfotsule",
                            "postnummer": "1188",
                            "arenaKandidatnr": "PAM015lfkggth",
                            "kommuneNavn": "Oslo",
                            "geografiJobbonsker": [
                                {
                                    "geografiKodeTekst": "Hamar",
                                    "geografiKode": "NO04.0403"
                                },
                                {
                                    "geografiKodeTekst": "Råde",
                                    "geografiKode": "NO30.3017"
                                },
                                {
                                    "geografiKodeTekst": "Vestby",
                                    "geografiKode": "NO02.0211"
                                }
                            ],
                            "fornavn": "Motvillig",
                            "fodselsnummer": "11857998776",
                            "kvalifiseringsgruppekode": "BATT"
                        },
                        "sort": [
                            1701177431533
                        ]
                    },
                    {
                        "_index": "veilederkandidat_os4",
                        "_type": "_doc",
                        "_id": "PAM018t0khbi5",
                        "_score": null,
                        "_source": {
                            "yrkeJobbonskerObj": [
                                {
                                    "styrkBeskrivelse": "Frisør",
                                    "sokeTitler": [
                                        "Frisør",
                                        "Frisør",
                                        "Frisørsvenn"
                                    ],
                                    "primaertJobbonske": false,
                                    "styrkKode": null
                                },
                                {
                                    "styrkBeskrivelse": "Sauegjeter",
                                    "sokeTitler": [
                                        "Sauegjeter",
                                        "Sauegjeter",
                                        "Gjeter"
                                    ],
                                    "primaertJobbonske": false,
                                    "styrkKode": null
                                },
                                {
                                    "styrkBeskrivelse": "Saueklipper",
                                    "sokeTitler": [
                                        "Saueklipper",
                                        "Saueklipper",
                                        "Sauegjeter"
                                    ],
                                    "primaertJobbonske": false,
                                    "styrkKode": null
                                },
                                {
                                    "styrkBeskrivelse": "Ullklassifisør",
                                    "sokeTitler": [
                                        "Ullklassifisør",
                                        "Ullklassifisør",
                                        "Ullpresser",
                                        "Ullklassifisør, Ullpresse"
                                    ],
                                    "primaertJobbonske": false,
                                    "styrkKode": null
                                }
                            ],
                            "etternavn": "Dans",
                            "postnummer": "0682",
                            "arenaKandidatnr": "PAM018t0khbi5",
                            "kommuneNavn": "Oslo",
                            "geografiJobbonsker": [
                                {
                                    "geografiKodeTekst": "Hamar",
                                    "geografiKode": "NO04.0403"
                                },
                                {
                                    "geografiKodeTekst": "Råde",
                                    "geografiKode": "NO30.3017"
                                },
                                {
                                    "geografiKodeTekst": "Vestby",
                                    "geografiKode": "NO02.0211"
                                }
                            ],
                            "fornavn": "Pompøs",
                            "fodselsnummer": "06888699277",
                            "kvalifiseringsgruppekode": "BATT"
                        },
                        "sort": [
                            1701177431478
                        ]
                    },
                    {
                        "_index": "veilederkandidat_os4",
                        "_type": "_doc",
                        "_id": "PAM0ygl1yw5t",
                        "_score": null,
                        "_source": {
                            "yrkeJobbonskerObj": [
                                {
                                    "styrkBeskrivelse": "Frisør",
                                    "sokeTitler": [
                                        "Frisør",
                                        "Frisør",
                                        "Frisørsvenn"
                                    ],
                                    "primaertJobbonske": false,
                                    "styrkKode": null
                                },
                                {
                                    "styrkBeskrivelse": "Sauegjeter",
                                    "sokeTitler": [
                                        "Sauegjeter",
                                        "Sauegjeter",
                                        "Gjeter"
                                    ],
                                    "primaertJobbonske": false,
                                    "styrkKode": null
                                },
                                {
                                    "styrkBeskrivelse": "Saueklipper",
                                    "sokeTitler": [
                                        "Saueklipper",
                                        "Saueklipper",
                                        "Sauegjeter"
                                    ],
                                    "primaertJobbonske": false,
                                    "styrkKode": null
                                },
                                {
                                    "styrkBeskrivelse": "Ullklassifisør",
                                    "sokeTitler": [
                                        "Ullklassifisør",
                                        "Ullklassifisør",
                                        "Ullpresser",
                                        "Ullklassifisør, Ullpresse"
                                    ],
                                    "primaertJobbonske": false,
                                    "styrkKode": null
                                }
                            ],
                            "etternavn": "Matvare",
                            "postnummer": "0693",
                            "arenaKandidatnr": "PAM0ygl1yw5t",
                            "kommuneNavn": "Oslo",
                            "geografiJobbonsker": [
                                {
                                    "geografiKodeTekst": "Hamar",
                                    "geografiKode": "NO04.0403"
                                },
                                {
                                    "geografiKodeTekst": "Råde",
                                    "geografiKode": "NO30.3017"
                                },
                                {
                                    "geografiKodeTekst": "Vestby",
                                    "geografiKode": "NO02.0211"
                                }
                            ],
                            "fornavn": "Handlekraftig",
                            "fodselsnummer": "09850499419",
                            "kvalifiseringsgruppekode": "BATT"
                        },
                        "sort": [
                            1701177431476
                        ]
                    },
                    {
                        "_index": "veilederkandidat_os4",
                        "_type": "_doc",
                        "_id": "PAM0113pumgyv",
                        "_score": null,
                        "_source": {
                            "yrkeJobbonskerObj": [
                                {
                                    "styrkBeskrivelse": "Sauegjeter",
                                    "sokeTitler": [
                                        "Sauegjeter",
                                        "Sauegjeter",
                                        "Gjeter"
                                    ],
                                    "primaertJobbonske": false,
                                    "styrkKode": null
                                },
                                {
                                    "styrkBeskrivelse": "Frisør",
                                    "sokeTitler": [
                                        "Frisør",
                                        "Frisør",
                                        "Frisørsvenn"
                                    ],
                                    "primaertJobbonske": false,
                                    "styrkKode": null
                                },
                                {
                                    "styrkBeskrivelse": "Saueklipper",
                                    "sokeTitler": [
                                        "Saueklipper",
                                        "Saueklipper",
                                        "Sauegjeter"
                                    ],
                                    "primaertJobbonske": false,
                                    "styrkKode": null
                                },
                                {
                                    "styrkBeskrivelse": "Ullklassifisør",
                                    "sokeTitler": [
                                        "Ullklassifisør",
                                        "Ullklassifisør",
                                        "Ullpresser",
                                        "Ullklassifisør, Ullpresse"
                                    ],
                                    "primaertJobbonske": false,
                                    "styrkKode": null
                                }
                            ],
                            "etternavn": "Gallium",
                            "postnummer": "0690",
                            "arenaKandidatnr": "PAM0113pumgyv",
                            "kommuneNavn": "Oslo",
                            "geografiJobbonsker": [
                                {
                                    "geografiKodeTekst": "Hamar",
                                    "geografiKode": "NO04.0403"
                                },
                                {
                                    "geografiKodeTekst": "Råde",
                                    "geografiKode": "NO30.3017"
                                },
                                {
                                    "geografiKodeTekst": "Vestby",
                                    "geografiKode": "NO02.0211"
                                }
                            ],
                            "fornavn": "Melankolsk",
                            "fodselsnummer": "13898297912",
                            "kvalifiseringsgruppekode": "BATT"
                        },
                        "sort": [
                            1701177431388
                        ]
                    },
                    {
                        "_index": "veilederkandidat_os4",
                        "_type": "_doc",
                        "_id": "PAM017uxa86j4",
                        "_score": null,
                        "_source": {
                            "yrkeJobbonskerObj": [
                                {
                                    "styrkBeskrivelse": "Frisør",
                                    "sokeTitler": [
                                        "Frisør",
                                        "Frisør",
                                        "Frisørsvenn"
                                    ],
                                    "primaertJobbonske": false,
                                    "styrkKode": null
                                },
                                {
                                    "styrkBeskrivelse": "Sauegjeter",
                                    "sokeTitler": [
                                        "Sauegjeter",
                                        "Sauegjeter",
                                        "Gjeter"
                                    ],
                                    "primaertJobbonske": false,
                                    "styrkKode": null
                                },
                                {
                                    "styrkBeskrivelse": "Saueklipper",
                                    "sokeTitler": [
                                        "Saueklipper",
                                        "Saueklipper",
                                        "Sauegjeter"
                                    ],
                                    "primaertJobbonske": false,
                                    "styrkKode": null
                                },
                                {
                                    "styrkBeskrivelse": "Ullklassifisør",
                                    "sokeTitler": [
                                        "Ullklassifisør",
                                        "Ullklassifisør",
                                        "Ullpresser",
                                        "Ullklassifisør, Ullpresse"
                                    ],
                                    "primaertJobbonske": false,
                                    "styrkKode": null
                                }
                            ],
                            "etternavn": "Bokklubb",
                            "postnummer": "0686",
                            "arenaKandidatnr": "PAM017uxa86j4",
                            "kommuneNavn": "Oslo",
                            "geografiJobbonsker": [
                                {
                                    "geografiKodeTekst": "Hamar",
                                    "geografiKode": "NO04.0403"
                                },
                                {
                                    "geografiKodeTekst": "Råde",
                                    "geografiKode": "NO30.3017"
                                },
                                {
                                    "geografiKodeTekst": "Vestby",
                                    "geografiKode": "NO02.0211"
                                }
                            ],
                            "fornavn": "Oriental",
                            "fodselsnummer": "08837598290",
                            "kvalifiseringsgruppekode": "BATT"
                        },
                        "sort": [
                            1701177431061
                        ]
                    },
                    {
                        "_index": "veilederkandidat_os4",
                        "_type": "_doc",
                        "_id": "PAM0115fl3ask",
                        "_score": null,
                        "_source": {
                            "yrkeJobbonskerObj": [
                                {
                                    "styrkBeskrivelse": "Frisør",
                                    "sokeTitler": [
                                        "Frisør",
                                        "Frisør",
                                        "Frisørsvenn"
                                    ],
                                    "primaertJobbonske": false,
                                    "styrkKode": null
                                },
                                {
                                    "styrkBeskrivelse": "Sauegjeter",
                                    "sokeTitler": [
                                        "Sauegjeter",
                                        "Sauegjeter",
                                        "Gjeter"
                                    ],
                                    "primaertJobbonske": false,
                                    "styrkKode": null
                                },
                                {
                                    "styrkBeskrivelse": "Saueklipper",
                                    "sokeTitler": [
                                        "Saueklipper",
                                        "Saueklipper",
                                        "Sauegjeter"
                                    ],
                                    "primaertJobbonske": false,
                                    "styrkKode": null
                                },
                                {
                                    "styrkBeskrivelse": "Ullklassifisør",
                                    "sokeTitler": [
                                        "Ullklassifisør",
                                        "Ullklassifisør",
                                        "Ullpresser",
                                        "Ullklassifisør, Ullpresse"
                                    ],
                                    "primaertJobbonske": false,
                                    "styrkKode": null
                                }
                            ],
                            "etternavn": "Pære",
                            "postnummer": "0667",
                            "arenaKandidatnr": "PAM0115fl3ask",
                            "kommuneNavn": "Oslo",
                            "geografiJobbonsker": [
                                {
                                    "geografiKodeTekst": "Hamar",
                                    "geografiKode": "NO04.0403"
                                },
                                {
                                    "geografiKodeTekst": "Råde",
                                    "geografiKode": "NO30.3017"
                                },
                                {
                                    "geografiKodeTekst": "Vestby",
                                    "geografiKode": "NO02.0211"
                                }
                            ],
                            "fornavn": "Velkommen",
                            "fodselsnummer": "15927296591",
                            "kvalifiseringsgruppekode": "BATT"
                        },
                        "sort": [
                            1701177431020
                        ]
                    },
                    {
                        "_index": "veilederkandidat_os4",
                        "_type": "_doc",
                        "_id": "PAM0122ig2auq",
                        "_score": null,
                        "_source": {
                            "yrkeJobbonskerObj": [
                                {
                                    "styrkBeskrivelse": "Sauegjeter",
                                    "sokeTitler": [
                                        "Sauegjeter",
                                        "Sauegjeter",
                                        "Gjeter"
                                    ],
                                    "primaertJobbonske": false,
                                    "styrkKode": null
                                },
                                {
                                    "styrkBeskrivelse": "Saueklipper",
                                    "sokeTitler": [
                                        "Saueklipper",
                                        "Saueklipper",
                                        "Sauegjeter"
                                    ],
                                    "primaertJobbonske": false,
                                    "styrkKode": null
                                },
                                {
                                    "styrkBeskrivelse": "Ullklassifisør",
                                    "sokeTitler": [
                                        "Ullklassifisør",
                                        "Ullklassifisør",
                                        "Ullpresser",
                                        "Ullklassifisør, Ullpresse"
                                    ],
                                    "primaertJobbonske": false,
                                    "styrkKode": null
                                },
                                {
                                    "styrkBeskrivelse": "Frisør",
                                    "sokeTitler": [
                                        "Frisør",
                                        "Frisør",
                                        "Frisørsvenn"
                                    ],
                                    "primaertJobbonske": false,
                                    "styrkKode": null
                                }
                            ],
                            "etternavn": "Kløver",
                            "postnummer": "1188",
                            "arenaKandidatnr": "PAM0122ig2auq",
                            "kommuneNavn": "Oslo",
                            "geografiJobbonsker": [
                                {
                                    "geografiKodeTekst": "Hamar",
                                    "geografiKode": "NO04.0403"
                                },
                                {
                                    "geografiKodeTekst": "Råde",
                                    "geografiKode": "NO30.3017"
                                },
                                {
                                    "geografiKodeTekst": "Vestby",
                                    "geografiKode": "NO02.0211"
                                }
                            ],
                            "fornavn": "Oppjaget",
                            "fodselsnummer": "20877599184",
                            "kvalifiseringsgruppekode": "BATT"
                        },
                        "sort": [
                            1701177430801
                        ]
                    },
                    {
                        "_index": "veilederkandidat_os4",
                        "_type": "_doc",
                        "_id": "PAM0yp25c81t",
                        "_score": null,
                        "_source": {
                            "yrkeJobbonskerObj": [
                                {
                                    "styrkBeskrivelse": "Sauegjeter",
                                    "sokeTitler": [
                                        "Sauegjeter",
                                        "Sauegjeter",
                                        "Gjeter"
                                    ],
                                    "primaertJobbonske": false,
                                    "styrkKode": null
                                },
                                {
                                    "styrkBeskrivelse": "Saueklipper",
                                    "sokeTitler": [
                                        "Saueklipper",
                                        "Saueklipper",
                                        "Sauegjeter"
                                    ],
                                    "primaertJobbonske": false,
                                    "styrkKode": null
                                },
                                {
                                    "styrkBeskrivelse": "Ullklassifisør",
                                    "sokeTitler": [
                                        "Ullklassifisør",
                                        "Ullklassifisør",
                                        "Ullpresser",
                                        "Ullklassifisør, Ullpresse"
                                    ],
                                    "primaertJobbonske": false,
                                    "styrkKode": null
                                },
                                {
                                    "styrkBeskrivelse": "Frisør",
                                    "sokeTitler": [
                                        "Frisør",
                                        "Frisør",
                                        "Frisørsvenn"
                                    ],
                                    "primaertJobbonske": false,
                                    "styrkKode": null
                                }
                            ],
                            "etternavn": "Spasertur",
                            "postnummer": "3478",
                            "arenaKandidatnr": "PAM0yp25c81t",
                            "kommuneNavn": "Asker",
                            "geografiJobbonsker": [
                                {
                                    "geografiKodeTekst": "Hamar",
                                    "geografiKode": "NO04.0403"
                                },
                                {
                                    "geografiKodeTekst": "Råde",
                                    "geografiKode": "NO30.3017"
                                },
                                {
                                    "geografiKodeTekst": "Vestby",
                                    "geografiKode": "NO02.0211"
                                }
                            ],
                            "fornavn": "Patent",
                            "fodselsnummer": "17907096467",
                            "kvalifiseringsgruppekode": "BFORM"
                        },
                        "sort": [
                            1701170557004
                        ]
                    }
                ]"""
    val kandidatsøkRespons = """
        {
            "hits": {
                "total": {
                    "value": 108
                },
                "hits": $kandidatsøkHits
            }
        }
    """.trimIndent()
    val esKandidatsøkRespons = """
        {
            "took": 10,
            "timed_out": false,
            "_shards": {
                "total": 3,
                "successful": 3,
                "skipped": 0,
                "failed": 0
            },
            "hits": {
                "total": {
                    "value": 108,
                    "relation": "eq"
                },
                "max_score": null,
                "hits": $kandidatsøkHits
            }
        }
    """.trimIndent()
}