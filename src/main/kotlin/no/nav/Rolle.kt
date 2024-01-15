package no.nav

import java.util.*

enum class Rolle {
    MODIA_GENERELL,
    MODIA_OPPFØLGING,
}

data class RolleUuidSpesifikasjon(
    private val modiaGenerell: UUID,
    private val modiaOppfølging: UUID,
) {
    fun rolleForUuid(uuid: UUID): Rolle {
        return when (uuid) {
            modiaGenerell -> Rolle.MODIA_GENERELL
            modiaOppfølging -> Rolle.MODIA_OPPFØLGING
            else -> throw IllegalArgumentException("Ukjent rolle-UUID: $uuid")
        }
    }

    fun rollerForUuider(uuider: Collection<UUID>): Set<Rolle> =
        EnumSet.copyOf(uuider.map { rolleForUuid(it) })
}


