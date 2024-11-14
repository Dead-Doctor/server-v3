package de.deaddoctor

import de.deaddoctor.Module.Companion.enable
import de.deaddoctor.modules.*
import io.ktor.client.*
import io.ktor.client.engine.apache.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.application.ApplicationCallPipeline.ApplicationPhase.Plugins
import io.ktor.server.auth.*
import io.ktor.server.engine.*
import io.ktor.server.http.content.*
import io.ktor.server.netty.*
import io.ktor.server.plugins.autohead.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.plugins.forwardedheaders.*
import io.ktor.server.plugins.statuspages.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.sessions.*
import io.ktor.server.websocket.*
import kotlinx.html.*
import java.io.File
import java.time.Duration
import kotlin.random.Random
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation as ContentNegotiationClient

lateinit var httpClient: HttpClient

var envVars: Map<String, String> = mapOf()
private fun tryGetConfig(name: String) = System.getenv(name) ?: envVars[name]
fun getConfig(name: String) =
    tryGetConfig(name) ?: throw NoSuchFieldException("Missing environment variable: $name")

fun main() {
    val envFile = File(".env")
    if (envFile.isFile)
        envVars = envFile.readLines().associate { it.substringBefore("=") to it.substringAfter("=") }

    embeddedServer(
        Netty,
        port = tryGetConfig("PORT")?.toInt() ?: 8080,
        host = "0.0.0.0",
        module = Application::module
    ).start(wait = true)
}

fun Application.module() {
    install(ForwardedHeaders)
    install(XForwardedHeaders)
    install(AutoHeadResponse)
    install(ContentNegotiation) {
        json()
    }
    install(Sessions) {
        cookie<UserSession>("user_session", SessionStorageMemory()) {
            cookie.path = "/"
            cookie.maxAgeInSeconds = 10 * 60
        }
    }
    httpClient = HttpClient(Apache) {
        install(ContentNegotiationClient) {
            json()
        }
    }
    install(WebSockets) {
        pingPeriod = Duration.ofSeconds(15)
        timeout = Duration.ofSeconds(15)
        maxFrameSize = Long.MAX_VALUE
        masking = false
    }
    authentication {
        configureOauth()
    }
    install(StatusPages) {
        status(HttpStatusCode.NotFound, HttpStatusCode.InternalServerError) { call, status ->
            call.respondPage("Ktor Test") {
                content {
                    h1 { +status.value.toString() }
                    h3 { +status.description }
                }
            }
        }
        exception<Throwable> { call, cause ->
            call.respondPage("Ktor Test") {
                content {
                    h1 { +"500" }
                    h3 { +"${cause::class.simpleName}: ${cause.message}" }
                }
            }
        }
    }
    val adjectives = listOf(
        "coolest", "best", "top", "supreme", "ultimate", "unbeatable", "unmatched", "unrivaled", "flawless", "perfect",
        "exquisite", "immaculate", "legendary", "incomparable", "phenomenal", "outstanding", "stellar", "fantastic",
        "world-class", "brilliant", "exceptional", "remarkable", "sensational", "incredible", "superb", "astonishing",
        "majestic", "spectacular", "impressive"
    )
    routing {
        get("/") {
            call.respondPage("deaddoctor") {
                content {
                    section {
                        h1 {
                            +"The "
                            b { +adjectives[Random.nextInt(adjectives.size)] }
                            +" website"
                        }
                        h2 {
                            +"Currently mostly "
                            i { +"games" }
                            +" and "
                            i { +"maps" }
                        }
                    }
                    section("grid") {
                        div {
                            h3 { +"Play Games" }
                            p { +"Enjoy all the games i have made." }
                            a(href = "/games") { +"All Games" }
                        }
                        div {
                            h3 { +"View Maps" }
                            p { +"Maps showing interesting things." }
                            a(href = "/maps") { +"All Maps" }
                        }
                    }
                }
            }
        }
        get("/games") {
            call.respondPage("Games") {
                content {
                    h1 { +"Games" }
                    section(classes = "grid") {
                        a(href = "/${TestModule.path()}") { +"Test" }
                        a(href = "/${WebsocketModule.path()}") { +"Websockets" }
                        a(href = "/${ChatModule.path()}") { +"Chat" }
                        a(href = "/${SnakeModule.path()}") { +"Snake" }
                        a(href = "/${MusicGuesserModule.path()}") { +MusicGuesserModule.NAME }
                    }
                }
            }
        }
        get("/maps") {
            call.respondPage("Games") {
                content {
                    section {
                        h1 { +"Oh, No!" }
                        h2 { +"There are no maps yet" }
                    }
                }
            }
        }
        get("/about") {
            call.respondPage("Games") {
                content {
                    section {
                        h1 { +"About" }
                        h2 { +"...to be done." }
                    }
                }
            }
        }
        enable(TestModule)
        enable(WebsocketModule)
        enable(ChatModule)
        enable(SnakeModule)
        enable(MusicGuesserModule)
        authenticate("discord") {
            get("login") {}
            route("login/callback") {
                intercept(Plugins) {
                    val error = call.request.queryParameters["error"]
                    if (error != null) {
                        // Authorization was denied/canceled
                        call.respondRedirect(OAuthState.url(call.request.queryParameters["state"]))
                        finish()
                    }
                }

                handle {
                    val principal: OAuthAccessTokenResponse.OAuth2 = call.principal()!!
                    call.sessions.set(UserSession.generate(principal))
                    call.respondRedirect(OAuthState.url(principal.state))
                }
            }
        }
        get("logout") {
            call.sessions.clear<UserSession>()
            call.respondRedirect(call.request.queryParameters["redirectUrl"] ?: "/")
        }
        staticResources("/", "static")
    }
}
