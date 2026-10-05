plugins {
    application
    kotlin("jvm") version "2.4.20"
    kotlin("kapt") version "2.4.20"
    id("io.github.ben-manes.versions") version "0.64.0"
}

group = "no.nav"
version = "1.0-SNAPSHOT"

val mockOAuth2ServerVersion = "6.0.4"
val javalinVersion = "7.2.3"
val javalinOpenApiVersion = "7.2.3"
val junitVersion = "6.1.3"
val resilience4jVersion = "2.4.0"
val jacksonVersion = "3.2.3"

application {
    mainClass.set("no.nav.toi.MainKt")
}

repositories {
    mavenCentral()
    maven("https://github-package-registry-mirror.gc.nav.no/cached/maven-release")
}

dependencies {
    implementation("io.javalin:javalin:$javalinVersion")
    implementation("ch.qos.logback:logback-classic:1.6.5")
    implementation("net.logstash.logback:logstash-logback-encoder:9.0")
    implementation("org.opensearch.client:opensearch-rest-client:3.9.0")
    implementation("org.opensearch.client:opensearch-java:3.10.0")
    implementation(platform("tools.jackson:jackson-bom:$jacksonVersion"))
    // Jackson 2 kommer transitivt via java-jwt, jwks-rsa, opensearch-java og javalin-openapi
    implementation(platform("com.fasterxml.jackson:jackson-bom:2.22.3"))
    implementation("tools.jackson.module:jackson-module-kotlin")
    implementation("com.auth0:java-jwt:4.6.1")
    implementation("com.auth0:jwks-rsa:0.24.1")
    implementation("io.javalin.community.openapi:javalin-openapi-plugin:$javalinOpenApiVersion")
    implementation("io.javalin.community.openapi:javalin-swagger-plugin:$javalinOpenApiVersion")
    implementation("no.nav.common:audit-log:4.2026.10.01_11.30-68a5418edc3c")
    kapt("io.javalin.community.openapi:openapi-annotation-processor:$javalinOpenApiVersion")
    implementation("org.ehcache:ehcache:3.12.0")
    implementation("io.github.resilience4j:resilience4j-retry:$resilience4jVersion")

    testImplementation("org.jetbrains.kotlin:kotlin-test")
    testImplementation("org.assertj:assertj-core:3.27.7")
    testImplementation(platform("org.junit:junit-bom:$junitVersion"))
    testImplementation("org.junit.jupiter:junit-jupiter-api")
    testImplementation("org.junit.jupiter:junit-jupiter-params")
    testImplementation("org.wiremock:wiremock-standalone:3.13.2")
    testImplementation("org.mockito.kotlin:mockito-kotlin:6.4.0")
    testImplementation("no.nav.security:mock-oauth2-server:$mockOAuth2ServerVersion")
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
    testImplementation("org.skyscreamer:jsonassert:1.5.3")
}

kotlin {
    jvmToolchain(25)
}

tasks.test {
    useJUnitPlatform()
}

fun erUstabilVersjon(versjon: String): Boolean {
    val stabilt = listOf("RELEASE", "FINAL", "GA").any { versjon.uppercase().contains(it) }
    val ustabilt = Regex("(?i).*[.-](alpha|beta|rc|cr|m|milestone|preview|snapshot|eap|dev)[.\\d-]*.*").matches(versjon)
    return !stabilt && ustabilt
}

tasks.withType<com.github.benmanes.gradle.versions.updates.DependencyUpdatesTask> {
    rejectVersionIf { erUstabilVersjon(candidate.version) && !erUstabilVersjon(currentVersion) }
}
