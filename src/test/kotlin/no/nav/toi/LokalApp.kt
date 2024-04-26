package no.nav.toi

import no.nav.security.mock.oauth2.MockOAuth2Server
import java.util.*




private const val authPort = 18306

class LokalApp {
    companion object {
        const val modiaGenerell = "67a06857-0028-4a90-bf4c-9c9a92c7d733"
        const val jobbsøkerrettet = "0dba8374-bf36-4d89-bbba-662447d57b94"
        const val arbeidsgiverrettet = "52bc2af7-38d1-468b-b68d-0f3a4de45af2"
        const val utvikler = "a1749d9a-52e0-4116-bb9f-935c38f6c74a"
    }
    private val app: App = lagLokalApp()
    private val authServer = MockOAuth2Server()

    fun start() {
        app.start()
        authServer.start(port = authPort)
    }

    fun close() {
        app.close()
        authServer.shutdown()
    }

    fun lagToken(
        issuerId: String = "http://localhost:$authPort/default",
        aud: String = "1",
        navIdent: String = "A000001",
        groups: List<String> = listOf(modiaGenerell),
        claims: Map<String, Any> = mapOf("NAVident" to navIdent, "groups" to groups),
        expiry: Long = 3600
    ) = authServer.issueToken(
        issuerId = issuerId,
        subject = "subject",
        audience = aud,
        claims = claims,
        expiry = expiry
    )
}

private fun lagLokalApp() = App(
    port = 8080,
    authenticationConfigurations = listOf(
        AuthenticationConfiguration(
            audience = "1",
            issuer = "http://localhost:$authPort/default",
            jwksUri = "http://localhost:$authPort/default/jwks",
        )
    ),
    rolleUuidSpesifikasjon = RolleUuidSpesifikasjon(
        modiaGenerell = UUID.fromString(LokalApp.modiaGenerell),
        jobbsøkerrettet = UUID.fromString(LokalApp.jobbsøkerrettet),
        arbeidsgiverrettet = UUID.fromString(LokalApp.arbeidsgiverrettet),
        utvikler = UUID.fromString(LokalApp.utvikler)
    ),
    openSearchUsername = "user",
    openSearchPassword = "pass",
    openSearchUri = "http://localhost:10000",
    pdlUrl = "http://localhost:10000/pdl",
    azureSecret = "secret",
    azureClientId = "1",
    azureUrl = "http://localhost:$authPort",
    pdlScope = "http://localhost/.default"
)
