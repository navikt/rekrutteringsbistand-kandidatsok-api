plugins {
    application
    kotlin("jvm") version "2.3.21"
    kotlin("kapt") version "2.3.21"
}

group = "no.nav"
version = "1.0-SNAPSHOT"

val mockOAuth2ServerVersion = "2.1.0"
val fuelVersion = "2.3.1"
val javalinVersion = "7.2.2"
val javalinOpenApiVersion = "7.2.0"
val jupiterVersion = "5.10.2"
val resilience4jVersion = "2.4.0"

application {
    mainClass.set("no.nav.toi.MainKt")
}

repositories {
    mavenCentral()
    maven("https://github-package-registry-mirror.gc.nav.no/cached/maven-release")
}

dependencies {
    implementation("io.javalin:javalin:$javalinVersion")
    implementation("ch.qos.logback:logback-classic:1.5.32")
    implementation("net.logstash.logback:logstash-logback-encoder:9.0")
    implementation("org.opensearch.client:opensearch-rest-client:3.6.0")
    implementation("org.opensearch.client:opensearch-java:3.8.0")
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin:2.21.3")
    implementation("com.auth0:java-jwt:4.5.2")
    implementation("com.auth0:jwks-rsa:0.24.0")
    implementation("io.javalin.community.openapi:javalin-openapi-plugin:$javalinOpenApiVersion")
    implementation("io.javalin.community.openapi:javalin-swagger-plugin:$javalinOpenApiVersion")
    implementation("no.nav.common:audit-log:4.2026.05.05_06.25-f72fab488a93")
    implementation("org.codehaus.janino:janino:3.1.12")
    implementation("com.github.kittinunf.fuel:fuel:$fuelVersion")
    implementation("com.github.kittinunf.fuel:fuel-jackson:$fuelVersion")
    kapt("io.javalin.community.openapi:openapi-annotation-processor:$javalinOpenApiVersion")
    implementation("org.ehcache:ehcache:3.12.0")
    implementation("io.github.resilience4j:resilience4j-retry:$resilience4jVersion")

    testImplementation("org.jetbrains.kotlin:kotlin-test")
    testImplementation("org.assertj:assertj-core:3.23.1")
    testImplementation("org.junit.jupiter:junit-jupiter-api:$jupiterVersion")
    testImplementation("org.junit.jupiter:junit-jupiter-params:$jupiterVersion")
    testImplementation("org.wiremock:wiremock-standalone:3.3.1")
    testImplementation("org.mockito.kotlin:mockito-kotlin:6.3.0")
    testImplementation("no.nav.security:mock-oauth2-server:$mockOAuth2ServerVersion")
    testImplementation("com.fasterxml.jackson.datatype:jackson-datatype-jsr310:2.21.3")
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine:$jupiterVersion")
    testImplementation("org.skyscreamer:jsonassert:1.5.1")
}

kotlin {
    jvmToolchain(25)
}

tasks.test {
    useJUnitPlatform()
}
