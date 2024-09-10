package no.nav.toi.kandidatsøk

import com.github.kittinunf.fuel.Fuel
import com.github.kittinunf.fuel.core.FuelError
import com.github.kittinunf.fuel.core.Headers
import com.github.kittinunf.fuel.core.Request
import com.github.kittinunf.fuel.core.Response
import com.github.kittinunf.fuel.core.extensions.authentication
import com.github.kittinunf.fuel.jackson.responseObject
import com.github.kittinunf.result.Result
import io.github.resilience4j.retry.Retry
import io.github.resilience4j.retry.RetryConfig
import no.nav.toi.accesstoken.AccessTokenClient

class ModiaKlient(private val modiaUrl: String, private val accessTokenClient: AccessTokenClient) {

    companion object {
        private fun withRetry(fetch: () -> Triple<Request, Response, Result<ModiaPerson, FuelError>>): Triple<Request, Response, Result<ModiaPerson, FuelError>> {
            fun isFailure(t: Triple<Request, Response, Result<Any, Exception>>) = t.third is Result.Failure
            val retryConfig =
                RetryConfig.custom<Triple<Request, Response, Result<Any, Exception>>>()
                    .retryOnResult(::isFailure)
                    .maxAttempts(3)
                    .failAfterMaxAttempts(true)
                    .build()
            val retry = Retry.of("fetch access token", retryConfig)
            val fetchWithRetry = Retry.decorateSupplier(retry, fetch)
            return fetchWithRetry.get()
        }
    }

    fun hentModiaEnheter(innkommendeToken: String): List<Enhet> {
        fun fetch(): Triple<Request, Response, Result<ModiaPerson, FuelError>> {
            val accessToken = accessTokenClient.hentAccessToken(innkommendeToken)
            return Fuel.get("$modiaUrl/api/decorator")
                .header(Headers.CONTENT_TYPE, "application/json")
                .authentication().bearer(accessToken)
                .responseObject<ModiaPerson>()
        }
        val (_, response, result) = withRetry(::fetch)

        if(response.statusCode == 404) return emptyList()

        when (result) {
            is Result.Success -> return result.get().enheter

            is Result.Failure -> throw RuntimeException("Noe feil skjedde ved henting av brukere: ", result.getException())
        }
    }
}


data class ModiaPerson(
    val ident: String,
    val fornavn: String,
    val etternavn: String,
    val enheter: List<Enhet>
)

data class Enhet(
    val enhetId: String,
    val navn: String
)