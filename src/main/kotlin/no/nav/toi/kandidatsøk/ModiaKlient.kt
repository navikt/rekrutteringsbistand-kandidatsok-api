package no.nav.toi.kandidatsøk

import no.nav.toi.AccessTokenClient
import no.nav.toi.erVellykket
import no.nav.toi.httpClient
import no.nav.toi.httpKlientMapper
import no.nav.toi.httpRequest
import no.nav.toi.medRetry
import org.ehcache.CacheManager
import org.ehcache.config.builders.CacheConfigurationBuilder
import org.ehcache.config.builders.CacheManagerBuilder
import org.ehcache.config.builders.ExpiryPolicyBuilder
import org.ehcache.config.builders.ResourcePoolsBuilder
import tools.jackson.module.kotlin.readValue
import java.net.http.HttpResponse.BodyHandlers
import java.time.Duration
import java.util.*

class ModiaKlient(private val modiaUrl: String, private val accessTokenClient: AccessTokenClient) {

    private val cache = ModiaCacheHjelper().lagCache { token -> fetchModiaEnheter(token) }

    fun hentModiaEnheter(innkommendeToken: String): List<Enhet> = cache(innkommendeToken)

    private fun fetchModiaEnheter(innkommendeToken: String): List<Enhet> {
        val response = medRetry("fetch modia enheter") {
            val accessToken = accessTokenClient.hentAccessToken(innkommendeToken)
            val request = httpRequest("$modiaUrl/api/decorator")
                .header("Authorization", "Bearer $accessToken")
                .header("Content-Type", "application/json")
                .GET()
                .build()
            httpClient.send(request, BodyHandlers.ofString())
        }

        if (response.statusCode() == 404) return emptyList()
        if (!response.erVellykket()) throw RuntimeException("Noe feil skjedde ved henting av brukere, status ${response.statusCode()}")

        return httpKlientMapper.readValue<ModiaPerson>(response.body()).enheter
    }
}

private class ModiaCacheHjelper {
    private val cacheKonfigurasjon = CacheConfigurationBuilder.newCacheConfigurationBuilder(
        String::class.java, ModiaEnheterCacheEntry::class.java,
        ResourcePoolsBuilder.heap(1000)
    ).withExpiry(ExpiryPolicyBuilder.timeToLiveExpiration(Duration.ofMinutes(10)))

    private val cacheManager = CacheManagerBuilder.newCacheManagerBuilder()
        .withCache(
            "modiaPreConfiguredCache",
            cacheKonfigurasjon
        ).build().also(CacheManager::init)

    fun lagCache(getter: (String) -> List<Enhet>): (String) -> List<Enhet> =
        cacheManager.createCache(
            "modiaCache${UUID.randomUUID()}",
            cacheKonfigurasjon
        ).let { cache ->
            { key ->
                val cached = cache.get(key)
                if (cached == null) {
                    val enheter = getter(key)
                    cache.put(key, ModiaEnheterCacheEntry(enheter))
                    enheter
                } else {
                    cached.enheter
                }
            }
        }
}

private class ModiaEnheterCacheEntry(
    val enheter: List<Enhet>
)


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
