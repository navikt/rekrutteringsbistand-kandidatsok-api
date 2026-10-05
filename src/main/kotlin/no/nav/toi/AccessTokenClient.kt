package no.nav.toi

import org.ehcache.CacheManager
import org.ehcache.config.builders.CacheConfigurationBuilder
import org.ehcache.config.builders.CacheManagerBuilder
import org.ehcache.config.builders.ResourcePoolsBuilder
import tools.jackson.module.kotlin.readValue
import java.net.URLEncoder
import java.net.http.HttpRequest.BodyPublishers
import java.net.http.HttpResponse.BodyHandlers
import java.time.Instant
import java.util.*


class AccessTokenClient(
    private val secret: String,
    private val clientId: String,
    private val scope: String,
    private val azureUrl: String,
) {
    private val secureLog = SecureLog(log)
    private val cache = CacheHjelper().lagCache { fetchAccessToken(it).tilEntry() }
    fun hentAccessToken(innkommendeToken: String) = cache.invoke(innkommendeToken).access_token

    private fun fetchAccessToken(token: String): AccessTokenResponse {
        val skjema = mapOf(
            "grant_type" to "urn:ietf:params:oauth:grant-type:jwt-bearer",
            "client_secret" to secret,
            "client_id" to clientId,
            "assertion" to token,
            "scope" to scope,
            "requested_token_use" to "on_behalf_of"
        )

        val request = httpRequest(azureUrl)
            .header("Content-Type", "application/x-www-form-urlencoded")
            .POST(BodyPublishers.ofString(skjema.tilSkjemadata()))
            .build()

        val response = try {
            medRetry("fetch access token") { httpClient.send(request, BodyHandlers.ofString()) }
        } catch (e: Exception) {
            secureLog.error("Noe feil skjedde ved henting av access_token", e)
            throw RuntimeException("Noe feil skjedde ved henting av access_token: ", e)
        }

        if (!response.erVellykket()) {
            secureLog.error("Noe feil skjedde ved henting av access_token. status: ${response.statusCode()} msg: ${response.body()}")
            throw RuntimeException("Noe feil skjedde ved henting av access_token, status ${response.statusCode()}")
        }
        return httpKlientMapper.readValue(response.body())
    }

    private fun Map<String, String>.tilSkjemadata() =
        entries.joinToString("&") { (navn, verdi) ->
            "${URLEncoder.encode(navn, Charsets.UTF_8)}=${URLEncoder.encode(verdi, Charsets.UTF_8)}"
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
