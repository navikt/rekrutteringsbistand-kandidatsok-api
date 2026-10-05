package no.nav.toi

import io.github.resilience4j.retry.Retry
import io.github.resilience4j.retry.RetryConfig
import tools.jackson.databind.DeserializationFeature
import tools.jackson.module.kotlin.jacksonMapperBuilder
import java.net.ProxySelector
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration

private val timeout: Duration = Duration.ofSeconds(15)

/** Delt klient for utgående HTTP-kall. */
val httpClient: HttpClient = HttpClient.newBuilder()
    .version(HttpClient.Version.HTTP_1_1)
    .connectTimeout(timeout)
    .proxy(ProxySelector.getDefault())
    .followRedirects(HttpClient.Redirect.NORMAL)
    .build()

val httpKlientMapper = jacksonMapperBuilder()
    .medFellesJsonOppsett()
    .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
    .build()

/** HttpClient har ingen timeout på svaret som standard, så requester bør lages herfra. */
fun httpRequest(url: String): HttpRequest.Builder =
    HttpRequest.newBuilder(URI.create(url)).timeout(timeout)

fun HttpResponse<*>.erVellykket() = statusCode() in 200..299

/** Nytt forsøk ved svar utenfor 2xx eller ved exception. Standard i resilience4j er 3 forsøk med 500 ms mellomrom. */
fun <T> medRetry(navn: String, kall: () -> HttpResponse<T>): HttpResponse<T> {
    val config = RetryConfig.custom<HttpResponse<T>>()
        .retryOnResult { !it.erVellykket() }
        .build()
    return Retry.decorateSupplier(Retry.of(navn, config), kall).get()
}
