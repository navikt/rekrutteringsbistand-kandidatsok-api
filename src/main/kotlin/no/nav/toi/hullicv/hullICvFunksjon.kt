package no.nav.toi.hullicv

import no.nav.toi.bool_
import no.nav.toi.exists_
import no.nav.toi.gte
import no.nav.toi.kandidatsøk.filter.FilterFunksjon
import no.nav.toi.lte
import no.nav.toi.mustNot_
import no.nav.toi.must_
import no.nav.toi.nested_
import no.nav.toi.query_
import no.nav.toi.range_
import no.nav.toi.should_
import java.time.LocalDate

fun hullICvEsFunksjon(påDato: LocalDate): FilterFunksjon = {
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
                                            lte(påDato.minusYears(2))
                                        }
                                    }
                                    should_ {
                                        range_ {
                                            field("perioderMedInaktivitet.sluttdatoerForInaktivePerioderPaToArEllerMer")
                                            gte(påDato.minusYears(3))
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