package no.nav.toi

import com.auth0.jwk.JwkProviderBuilder
import com.auth0.jwk.SigningKeyNotFoundException
import com.auth0.jwt.JWT
import com.auth0.jwt.JWTVerifier
import com.auth0.jwt.algorithms.Algorithm
import com.auth0.jwt.exceptions.JWTVerificationException
import com.auth0.jwt.exceptions.TokenExpiredException
import com.auth0.jwt.interfaces.DecodedJWT
import com.auth0.jwt.interfaces.RSAKeyProvider
import io.javalin.Javalin
import io.javalin.http.Context
import io.javalin.http.HttpStatus
import io.javalin.http.InternalServerErrorResponse
import io.javalin.http.UnauthorizedResponse
import org.eclipse.jetty.http.HttpHeader
import java.net.URI
import java.security.interfaces.RSAPublicKey
import java.util.*
import java.util.concurrent.TimeUnit

private const val navIdentClaim = "NAVident"

/**
 * Representerer en autensiert bruker
 */
class AuthenticatedUser(
    val navIdent: String,
    val roller: Set<Rolle>,
) {
    companion object {
        fun fromJwt(jwt: DecodedJWT, rolleUuidSpesifikasjon: RolleUuidSpesifikasjon) =
            AuthenticatedUser(
                navIdent = jwt.getClaim(navIdentClaim).asString(),
                roller = jwt.getClaim("groups")
                    .asList(UUID::class.java)
                    .let { rolleUuidSpesifikasjon.rollerForUuider(it) },
            )
    }
}

/**
 * Henter ut en autensiert bruker fra en kontekst. Kaster InternalServerErrorResponse om det ikke finnes en autensiert bruker
 */
fun Context.authenticatedUser() = attribute<AuthenticatedUser>("authenticatedUser")
    ?: run {
        log.error("No authenticated user found!")
        throw InternalServerErrorResponse()
    }

/**
 * Setter opp token-verifisering på en path på Javalin-serveren
 */
fun Javalin.azureAdAuthentication(
    path: String,
    azureAppClientId: String,
    azureOpenidConfigIssuer: String,
    azureOpenidConfigJwksUri: String,
    rolleUuidSpesifikasjon: RolleUuidSpesifikasjon,
): Javalin? {
    val verifier = jwtVerifier(azureOpenidConfigJwksUri, azureOpenidConfigIssuer, azureAppClientId)
    return before(path) {
        val jwt = verifier.verify(it.hentToken())
        it.attribute("authenticatedUser", AuthenticatedUser.fromJwt(jwt, rolleUuidSpesifikasjon))
    }
        .exception(JWTVerificationException::class.java) { e, ctx ->
            when (e) {
                is TokenExpiredException -> log.info("AzureAD-token expired on {}", e.expiredOn)
                else -> log.error("Unexpected exception {} while authenticating AzureAD-token", e::class.simpleName, e)
            }
            ctx.status(HttpStatus.UNAUTHORIZED).result("")
        }.exception(SigningKeyNotFoundException::class.java) { _, ctx ->
            log.warn("Noen prøvde å aksessere endepunkt med en token signert med en falsk issuer")
            ctx.status(HttpStatus.UNAUTHORIZED).result("")
        }
}

/**
 * Henter token ut fra header fra en Context
 */
private fun Context.hentToken(): String {
    val authorizationHeader = header(HttpHeader.AUTHORIZATION.name)
        ?: run {
            log.error("Authorization header missing!")
            throw UnauthorizedResponse()
        }
    if (!authorizationHeader.startsWith("Bearer")) {
        log.error("Authorization header not with 'Bearer ' prefix!")
        throw UnauthorizedResponse()
    }
    return authorizationHeader.removePrefix("Bearer ")
}

/**
 * Setter opp en jwtVerifier som verifiserer token
 */
private fun jwtVerifier(
    azureOpenidConfigJwksUri: String,
    azureOpenidConfigIssuer: String,
    azureAppClientId: String
): JWTVerifier = JWT.require(algorithm(azureOpenidConfigJwksUri))
    .withIssuer(azureOpenidConfigIssuer)
    .withAudience(azureAppClientId)
    .withClaimPresence(navIdentClaim)
    .withClaimPresence("groups")
    .build()

/**
 * Setter opp algoritmen som tokenet skal være signert under
 */
private fun algorithm(azureOpenidConfigJwksUri: String): Algorithm {
    val jwkProvider = jwkProvider(azureOpenidConfigJwksUri)
    return Algorithm.RSA256(object : RSAKeyProvider {
        override fun getPublicKeyById(keyId: String) = jwkProvider.get(keyId).publicKey as RSAPublicKey
        override fun getPrivateKey() = throw IllegalStateException()
        override fun getPrivateKeyId() = throw IllegalStateException()
    })
}

/**
 * Setter opp Jwk-nøkkel for token-verifikasjon
 */
private fun jwkProvider(azureOpenidConfigJwksUri: String) =
    JwkProviderBuilder(URI(azureOpenidConfigJwksUri).toURL())
        .cached(10, 1, TimeUnit.HOURS)
        .build()