package no.nav.toi.kandidatsøk.filter

import no.nav.toi.*
import no.nav.toi.kandidatsøk.FilterParametre
import java.time.LocalDate

fun List<Filter>.medPrioritertMålgruppeFilter() = this + PrioritertMålgruppeFilter()

private interface Målgruppe {
    fun harKode(kode: String): Boolean
    fun lagESFilterFunksjon(): FilterFunksjon
}
private object Unge: Målgruppe {
    override fun harKode(kode: String) = kode == "unge"
    override fun lagESFilterFunksjon(): FilterFunksjon = {
        should_ {
            range_ {
                field("fodselsdato")
                gte("now/d-30y")
                lt("now")
            }
        }
    }
}
private object Senior: Målgruppe {
    override fun harKode(kode: String) = kode == "senior"
    override fun lagESFilterFunksjon(): FilterFunksjon = {
        should_ {
            range_ {
                field("fodselsdato")
                gte("now-200y/d")
                lt("now/d-50y")
            }
        }
    }
}
private object HullICv: Målgruppe {
    override fun harKode(kode: String) = kode == "hullICv"
    override fun lagESFilterFunksjon(): FilterFunksjon = {
        should_ {
            bool_ {
                must_ {
                    bool_ {
                        should_ {
                            nested_ {
                                path("yrkeserfaring")
                                query_ {
                                    bool_ {
                                        must_ {
                                            exists_ {
                                                field("yrkeserfaring")
                                            }
                                        }
                                    }
                                }
                            }
                        }
                        should_ {
                            nested_ {
                                path("utdanning")
                                query_ {
                                    bool_ {
                                        must_ {
                                            exists_ {
                                                field("utdanning")
                                            }
                                        }
                                    }
                                }
                            }
                        }
                        should_ {
                            nested_ {
                                path("forerkort")
                                query_ {
                                    bool_ {
                                        must_ {
                                            exists_ {
                                                field("forerkort")
                                            }
                                        }
                                    }
                                }
                            }
                        }
                        should_ {
                            exists_ {
                                field("kursObj")
                            }
                        }
                        should_ {
                            exists_ {
                                field("fagdokumentasjon")
                            }
                        }
                        should_ {
                            exists_ {
                                field("annenerfaringObj")
                            }
                        }
                        should_ {
                            exists_ {
                                field("godkjenninger")
                            }
                        }
                    }
                }
                must_ {
                    bool_ {
                        should_ {
                            bool_ {
                                mustNot_ {
                                    exists_ {
                                        field("perioderMedInaktivitet.startdatoForInnevarendeInaktivePeriode")
                                    }
                                }
                                mustNot_ {
                                    exists_ {
                                        field("perioderMedInaktivitet.sluttdatoerForInaktivePerioderPaToArEllerMer")
                                    }
                                }
                            }
                        }
                        should_ {
                            bool_ {
                                must_ {
                                    exists_ {
                                        field("perioderMedInaktivitet.startdatoForInnevarendeInaktivePeriode")
                                    }
                                }
                                must_ {
                                    bool_ {
                                        should_ {
                                            range_ {
                                                field("perioderMedInaktivitet.startdatoForInnevarendeInaktivePeriode")
                                                lte(LocalDate.now().minusYears(2))
                                            }
                                        }
                                        should_ {
                                            range_ {
                                                field("perioderMedInaktivitet.sluttdatoerForInaktivePerioderPaToArEllerMer")
                                                gte(LocalDate.now().minusYears(3))
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

private fun String.tilMålgruppe() = listOf(Unge, Senior, HullICv).first { it.harKode(this) }

private class PrioritertMålgruppeFilter: Filter {
    private var prioriterteMålgrupper = emptyList<Målgruppe>()
    override fun berikMedParameter(filterParametre: FilterParametre) {
        prioriterteMålgrupper = filterParametre.prioritertMålgruppe?.map(String::tilMålgruppe)  ?: emptyList()
    }

    override fun erAktiv() = prioriterteMålgrupper.isNotEmpty()

    override fun lagESFilterFunksjon(): FilterFunksjon = {
        must_ {
            bool_ {
                apply {
                    prioriterteMålgrupper.map(Målgruppe::lagESFilterFunksjon).forEach { it() }
                }
            }
        }
    }
}