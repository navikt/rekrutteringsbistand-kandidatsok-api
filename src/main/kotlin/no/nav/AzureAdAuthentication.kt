package no.nav

import com.auth0.jwk.JwkProviderBuilder
import com.auth0.jwt.JWT
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
import org.slf4j.LoggerFactory
import java.net.URI
import java.security.interfaces.RSAPublicKey
import java.util.concurrent.TimeUnit

val azureAppClientId = System.getenv("AZURE_APP_CLIENT_ID")!!
val azureOpenidConfigIssuer = System.getenv("AZURE_OPENID_CONFIG_ISSUER")!!
val azureOpenidConfigJwksUri = System.getenv("AZURE_OPENID_CONFIG_JWKS_URI")!!
val azureAppWellKnownUrl = System.getenv("AZURE_APP_WELL_KNOWN_URL")!!

val jwkProvider = JwkProviderBuilder(URI(azureOpenidConfigJwksUri).toURL())
    .cached(10, 1, TimeUnit.HOURS)
    .build()

val rsaKeyProvider = object: RSAKeyProvider {
    override fun getPublicKeyById(keyId: String) = jwkProvider.get(keyId).publicKey as RSAPublicKey
    override fun getPrivateKey() = throw IllegalStateException()
    override fun getPrivateKeyId() = throw IllegalStateException()
}

val algorithm = Algorithm.RSA256(rsaKeyProvider)

val verifier = JWT.require(algorithm)
    .withIssuer(azureOpenidConfigIssuer)
    .withAudience(azureAppClientId)
    .withClaimPresence("NAVident")
    .build()

class AuthenticatedUser(
    val navIdent: String,
) {
    companion object {
        fun fromJwt(jwt: DecodedJWT) = AuthenticatedUser(
            navIdent = jwt.getClaim("NAVident").asString()
        )
    }
}

private val log = LoggerFactory.getLogger("no.nav.azureAdAuthentication")

fun Context.authenticatedUser() = this.attribute<AuthenticatedUser>("authenticatedUser")
    ?: run {
        log.error("No authenticated user found!")
        throw InternalServerErrorResponse()
    }

fun Javalin.azureAdAuthentication(path: String) =
    this
        .before(path) {
            val authorizationHeader = it.header(HttpHeader.AUTHORIZATION.name)
                ?: run {
                    log.error("Authorization header missing!")
                    throw UnauthorizedResponse()
                }
            if (!authorizationHeader.startsWith("Bearer")) {
                log.error("Authorization header not with 'Bearer ' prefix!")
                throw UnauthorizedResponse()
            }
            val token = authorizationHeader.removePrefix("Bearer ")
            val jwt = verifier.verify(token)
            it.attribute("authenticatedUser", AuthenticatedUser.fromJwt(jwt))
        }
        .exception(JWTVerificationException::class.java) { e, ctx ->
            when (e) {
                is TokenExpiredException -> log.info("AzureAD-token expired on {}", e.expiredOn)
                else -> log.error("Unexpected exception {} while authenticating AzureAD-token", e::class.simpleName, e)
            }
            ctx.status(HttpStatus.UNAUTHORIZED).result("")
        }