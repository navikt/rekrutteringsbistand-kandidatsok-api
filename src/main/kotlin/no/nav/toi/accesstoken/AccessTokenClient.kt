package no.nav.toi.accesstoken

import com.github.kittinunf.fuel.core.FuelManager
import com.github.kittinunf.fuel.jackson.responseObject
import com.github.kittinunf.result.Result
import org.ehcache.CacheManager
import org.ehcache.config.builders.CacheConfigurationBuilder
import org.ehcache.config.builders.CacheManagerBuilder
import org.ehcache.config.builders.ResourcePoolsBuilder
import org.ehcache.config.units.MemoryUnit
import org.slf4j.LoggerFactory
import java.time.Instant
import java.util.*

private val secureLog = LoggerFactory.getLogger("secureLog")!!

class AccessTokenClient(
    private val secret: String,
    private val clientId: String,
    private val scope: String,
    private val azureUrl: String,
) {
    private val cache = CacheHjelper().lagCache { fetchAccessToken(it).tilEntry() }
    fun hentAccessToken(innkommendeToken: String) = cache.invoke(innkommendeToken).access_token

    private fun fetchAccessToken(token: String): AccessTokenResponse {
        val url = azureUrl

        val formData = listOf(
            "grant_type" to "urn:ietf:params:oauth:grant-type:jwt-bearer",
            "client_secret" to secret,
            "client_id" to clientId,
            "assertion" to token,
            "scope" to scope,
            "requested_token_use" to "on_behalf_of"
        )

        val (_, response, result) = FuelManager()
            .post(url, formData)
            .responseObject<AccessTokenResponse>()

        when (result) {
            is Result.Success -> {
                return result.get()
            }

            is Result.Failure -> {
                secureLog.error(
                    "Noe feil skjedde ved henting av access_token. msg: ${
                        response.body().asString("application/json")
                    }", result.getException()
                )
                throw RuntimeException("Noe feil skjedde ved henting av access_token: ", result.getException())
            }
        }
    }
}

private data class AccessTokenResponse(
    val access_token: String,
    val expires_in: Long
) {
    fun tilEntry() = AccessTokenCacheEntry(access_token, Instant.now().plusSeconds(expires_in - 10))
}

private class AccessTokenCacheEntry(
    val access_token: String,
    private val expiry: Instant
) {
    fun erGåttUt() = Instant.now().isAfter(expiry)
}


private class CacheHjelper {
    private val cacheKonfigurasjon = CacheConfigurationBuilder.newCacheConfigurationBuilder(
        String::class.java, AccessTokenCacheEntry::class.java,
        ResourcePoolsBuilder.heap(666)
    )
    private val cacheManager = CacheManagerBuilder.newCacheManagerBuilder()
        .withCache(
            "preConfiguredCache",
            cacheKonfigurasjon
        ).build().also(CacheManager::init)

    fun lagCache(getter: (String) -> AccessTokenCacheEntry): (String) -> AccessTokenCacheEntry =
        cacheManager.createCache(
            "cache${UUID.randomUUID()}",
            cacheKonfigurasjon
        ).let { cache ->
            { key ->
                if (!cache.containsKey(key) || cache.get(key).erGåttUt()) {
                    cache.put(key, getter(key))
                }
                cache.get(key)
            }
        }
}
