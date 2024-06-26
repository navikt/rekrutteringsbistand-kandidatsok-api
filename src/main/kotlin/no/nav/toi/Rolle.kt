package no.nav.toi

import java.util.*

enum class Rolle {
    JOBBSØKER_RETTET,
    ARBEIDSGIVER_RETTET,
    UTVIKLER
}

/*
    Holder på UUID-ene som brukes for å identifisere roller i Azure AD.
    Det er ulik spesifikasjon for dev og prod.
 */
data class RolleUuidSpesifikasjon(
    private val jobbsøkerrettet: UUID,
    private val arbeidsgiverrettet: UUID,
    private val utvikler: UUID,
) {
    private fun rolleForUuid(uuid: UUID) = when (uuid) {
        jobbsøkerrettet -> Rolle.JOBBSØKER_RETTET
        arbeidsgiverrettet -> Rolle.ARBEIDSGIVER_RETTET
        utvikler -> Rolle.UTVIKLER
        else -> { log.warn("Ukjent rolle-UUID: $uuid"); null }
    }

    fun rollerForUuider(uuider: Collection<UUID>) = uuider.mapNotNull(::rolleForUuid).toSet()
}

