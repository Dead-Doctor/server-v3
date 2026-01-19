package de.deaddoctor

import de.deaddoctor.Module.Companion.enable
import de.deaddoctor.modules.*
import io.ktor.client.*
import io.ktor.client.engine.apache.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.html.*
import io.ktor.server.http.content.*
import io.ktor.server.netty.*
import io.ktor.server.plugins.autohead.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.plugins.forwardedheaders.*
import io.ktor.server.plugins.statuspages.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.sessions.*
import io.ktor.server.util.*
import io.ktor.server.websocket.*
import kotlinx.html.*
import kotlinx.serialization.json.Json
import org.slf4j.LoggerFactory
import java.io.File
import kotlin.random.Random
import kotlin.time.Duration.Companion.seconds
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation as ContentNegotiationClient

private const val MANIFEST_LOCATION = "/.vite/manifest.json"

lateinit var httpClient: HttpClient
lateinit var server: EmbeddedServer<NettyApplicationEngine, NettyApplicationEngine.Configuration>

lateinit var frontendPath: String
val manifestPath
    get() = "$frontendPath$MANIFEST_LOCATION"
lateinit var persistentDir: File

var envVars: Map<String, String> = mapOf()
private fun tryGetConfig(name: String) = System.getenv(name) ?: envVars[name]
fun getConfig(name: String) =
    tryGetConfig(name) ?: throw NoSuchFieldException("Missing environment variable: $name")

fun main() {
    val envFile = File(".env")
    if (envFile.isFile)
        envVars = envFile.readLines().associate { it.substringBefore("=") to it.substringAfter("=") }

    server = embeddedServer(
        factory = Netty,
        port = tryGetConfig("PORT")?.toInt() ?: 8080,
        host = "0.0.0.0",
        module = Application::module
    )
    server.start(wait = true)
}

fun Application.module() {
    val logger = LoggerFactory.getLogger(javaClass)

    frontendPath = if (!developmentMode) "dist" else "debug"
    persistentDir = if (!developmentMode) File("/app/run") else File("run")
    persistentDir.mkdirs()

    install(ForwardedHeaders)
    install(XForwardedHeaders)
    install(AutoHeadResponse)
    install(ContentNegotiation) {
        json()
    }
    install(Sessions) {
        cookie<UserSession>("user_session", directorySessionStorage(File(persistentDir, ".sessions"), false)) {
            cookie.path = "/"
        }
    }
    httpClient = HttpClient(Apache) {
        install(ContentNegotiationClient) {
            json(Json { ignoreUnknownKeys = true })
        }
    }
    install(WebSockets) {
        pingPeriod = 15.seconds
        timeout = 15.seconds
        maxFrameSize = Long.MAX_VALUE
        masking = false
    }
    installOAuth()
    install(StatusPages) {
        if (this@module.developmentMode) {
            exception<Throwable> { call, cause ->
                call.respondHtmlTemplate(PageLayout(null, call.request.uri, "Internal Server Error"), HttpStatusCode.OK) {
                    content {
                        h1 { +"500" }
                        h3 { +"${cause::class.simpleName}: ${cause.message}" }
                    }
                }
                cause.printStackTrace()
            }
        }
        status(HttpStatusCode.NotFound, HttpStatusCode.InternalServerError) { call, status ->
            call.respondPage("Error ${status.value}") {
                content {
                    h1 { +status.value.toString() }
                    h3 { +status.description }
                }
            }
        }
    }
    val redirectTrailingSlash = createApplicationPlugin("RedirectTrailingSlash") {
        onCall { call ->
            val path = call.request.url.encodedPath
            if (path.length > 1 && path.endsWith('/')) {
                call.respondRedirect(url {
                    takeFrom(call.request.url)
                    encodedPath = path.removeSuffix("/")
                })
            }
        }
    }
    install(redirectTrailingSlash)

    val adjectives = listOf(
        "cool", "top", "supreme", "ultimate", "unbeatable", "unmatched", "unrivaled", "flawless", "perfect",
        "exquisite", "immaculate", "legendary", "incomparable", "phenomenal", "outstanding", "stellar", "fantastic",
        "world-class", "brilliant", "exceptional", "remarkable", "sensational", "incredible", "superb", "astonishing",
        "majestic", "spectacular", "impressive", "dead"
    )
    routing {
        get("/") {
            call.respondPage {
                content {
                    section {
                        h1 {
                            val adjective = adjectives[Random.nextInt(adjectives.size)]
                            if (adjective[0] in "aeiou") +"An "
                            else +"A "
                            b { +adjective }
                            +" website"
                        }
                        h2 {
                            +"Currently mostly "
                            i { +"games" }
                            +" and "
                            i { +"maps" }
                            +" (WIP)"
                        }
                    }
                    section("grid") {
                        div {
                            h3 { +"Play Games" }
                            p { +"Enjoy all the games I have made." }
                            a(href = "/${GameModule.path()}") { +"All Games" }
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
        enable(LobbyModule)
        enable(GameModule)
        routeOAuth()

        if (!developmentMode) {
            staticResources("/", frontendPath, null) {
                exclude { it.file.endsWith(manifestPath) }
            }
        } else {
            staticFiles("/", File(frontendPath), null)
        }
    }
}
