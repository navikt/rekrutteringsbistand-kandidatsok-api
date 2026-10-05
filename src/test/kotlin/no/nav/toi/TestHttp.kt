package no.nav.toi

import tools.jackson.databind.json.JsonMapper
import tools.jackson.module.kotlin.jacksonMapperBuilder
import java.net.http.HttpClient

/** Felles klient for tester som kaller appen over HTTP. */
val testHttpClient: HttpClient = HttpClient.newBuilder()
    .version(HttpClient.Version.HTTP_1_1)
    .build()

val testObjectMapper: JsonMapper = jacksonMapperBuilder()
    .accessorNaming(norskeTegnAccessorNaming)
    .build()
