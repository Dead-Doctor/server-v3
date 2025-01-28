plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.ktor)
    alias(libs.plugins.kotlin.plugin.serialization)
}

group = "de.deaddoctor"
version = "0.0.1"

application {
    mainClass.set("de.deaddoctor.ApplicationKt")

    val isDevelopment: Boolean = project.ext.has("development")
    applicationDefaultJvmArgs = listOf("-Dio.ktor.development=$isDevelopment")
}

repositories {
    mavenCentral()
    maven { url = uri("https://maven.pkg.jetbrains.space/kotlin/p/kotlin/kotlin-js-wrappers") }
}

dependencies {
    implementation(libs.ktor.server.core)
    implementation(libs.ktor.server.auth)
    implementation(libs.ktor.server.sessions)
    implementation(libs.ktor.server.auto.head.response)
    implementation(libs.ktor.server.host.common)
    implementation(libs.ktor.server.forwarded.header)
    implementation(libs.ktor.server.content.negotiation)
    implementation(libs.ktor.server.caching.headers)
    implementation(libs.ktor.server.status.pages)
    implementation(libs.ktor.server.html.builder)
    implementation(libs.ktor.server.websockets)
    implementation(libs.ktor.server.netty)

    implementation(libs.ktor.client.core)
    implementation(libs.ktor.client.apache)
    implementation(libs.ktor.client.content.negotiation)

    implementation(libs.ktor.serialization.kotlinx.json)

    implementation(libs.kotlinx.datetime)
    implementation(libs.kotlinx.html)
    implementation(libs.kotlin.css)

    implementation(libs.logback.classic)
    implementation(libs.bcs)

    testImplementation(libs.ktor.server.test.host)
    testImplementation(libs.kotlin.test.junit)
}

tasks.test {
    useJUnitPlatform()
}