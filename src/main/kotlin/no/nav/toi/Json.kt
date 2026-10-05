package no.nav.toi

import io.javalin.json.JavalinJackson3
import tools.jackson.databind.DeserializationFeature
import tools.jackson.databind.introspect.DefaultAccessorNamingStrategy
import tools.jackson.databind.json.JsonMapper

/**
 * Jacksons standardvalidering av accessor-navn godtar ikke at første tegn etter get/is er æ, ø eller å.
 * Uten denne forsvinner for eksempel `ønsketSted` stille ved serialisering.
 */
val norskeTegnAccessorNaming: DefaultAccessorNamingStrategy.Provider =
    DefaultAccessorNamingStrategy.Provider().withFirstCharAcceptance(true, true)

/**
 * Felles regler for JSON: ukjente felt avvises, null eller manglende verdi i primitive felt blir 0/false,
 * og tegn etter selve JSON-dokumentet ignoreres.
 */
fun JsonMapper.Builder.medFellesJsonOppsett(): JsonMapper.Builder = this
    .accessorNaming(norskeTegnAccessorNaming)
    .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
    .disable(DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES)
    .disable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)

/**
 * Javalin bruker Jackson 2 som standard, uansett hva som finnes på classpath. Appen bruker Jackson 3,
 * så mapperen må settes eksplisitt.
 */
fun javalinJsonMapper(): JavalinJackson3 =
    JavalinJackson3().updateMapper { it.medFellesJsonOppsett() }
