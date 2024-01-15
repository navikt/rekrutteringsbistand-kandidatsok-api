plugins {
    application
    kotlin("jvm") version "1.9.21"
    kotlin("kapt") version "1.9.22"
}

group = "no.nav"
version = "1.0-SNAPSHOT"

application {
    mainClass.set("no.nav.MainKt")
}

repositories {
    mavenCentral()
}

dependencies {
    implementation("io.javalin:javalin:5.6.3")
    implementation("ch.qos.logback:logback-classic:1.4.4")
    implementation("net.logstash.logback:logstash-logback-encoder:7.2")
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin:2.14.0")
    implementation("com.auth0:java-jwt:4.4.0")
    implementation("com.auth0:jwks-rsa:0.22.1")
    implementation("io.javalin.community.openapi:javalin-openapi-plugin:5.6.3")
    implementation("io.javalin.community.openapi:javalin-swagger-plugin:5.6.3")
    implementation("io.javalin.community.openapi:javalin-redoc-plugin:5.6.3")
    kapt("io.javalin.community.openapi:openapi-annotation-processor:5.6.3")

    testImplementation("org.jetbrains.kotlin:kotlin-test")
    testImplementation("com.github.kittinunf.fuel:fuel:2.3.1")
    testImplementation("org.assertj:assertj-core:3.23.1")

}

tasks.test {
    useJUnitPlatform()
}

kotlin {
    jvmToolchain(17)
}