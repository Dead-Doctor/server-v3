package de.deaddoctor

import de.deaddoctor.CSSResource.Companion.getStyles
import de.deaddoctor.CSSResource.Companion.addStyles
import de.deaddoctor.Module.Companion.enable
import de.deaddoctor.modules.ChatModule
import de.deaddoctor.modules.SnakeModule
import de.deaddoctor.modules.TestModule
import de.deaddoctor.modules.WebsocketModule
import io.ktor.client.*
import io.ktor.client.engine.apache.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.application.ApplicationCallPipeline.ApplicationPhase.Plugins
import io.ktor.server.auth.*
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
import io.ktor.server.websocket.*
import kotlinx.css.*
import kotlinx.css.properties.TextDecoration
import kotlinx.css.properties.TextDecorationLine
import kotlinx.html.*
import java.io.File
import java.time.Duration
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
            call.respondTemplate("Ktor Test") {
                h1 { +status.value.toString() }
                h3 { +status.description }
            }
        }
        exception<Throwable> { call, cause ->
            call.respondTemplate("Ktor Test") {
                h1 { +"500" }
                h3 { +"${cause::class.simpleName}: ${cause.message}" }
            }
        }
    }
    routing {
        get("/") {
            call.respondTemplate("Ktor Test") {
                a(href = "/test") { +"Test" }
                a(href = "/ws") { +"Websockets" }
                a(href = "/chat") { +"Chat" }
                a(href = "/snake") { +"Snake" }
            }
        }
        enable(TestModule)
        enable(WebsocketModule)
        enable(ChatModule)
        enable(SnakeModule)
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
        getStyles(styles)
        staticResources("/", "static")
    }
}

suspend fun ApplicationCall.respondTemplate(title: String, head: HEAD.() -> Unit = {}, content: MAIN.() -> Unit) {
    val account = getAccount()
    val uri = request.uri
    respondHtml {
        head {
            title { +title }
            addStyles(styles)
            link(rel = "icon", type = "image/x-icon", href = "/favicon.ico")
            head()
        }
        body {
            header {
                h1 { a(href = "/", classes = "title") { +title } }
                span {
                    if (!account.loggedIn) {
                        a(href = "/login?redirectUrl=${uri.encodeURLParameter()}") { +"Login" }
                    } else {
                        span { +"Hello, ${account.name}" }
                        a(href = "/logout?redirectUrl=${uri.encodeURLParameter()}") { +"Logout" }
                    }
                }
            }
            main {
                content()
            }
        }
    }
}

val mainTag = TagSelector("main")

val styles by CSSRules {
    html {
        height = 100.pct
    }
    body {
        display = Display.flex
        flexDirection = FlexDirection.column
        height = 100.pct
        margin(0.px)
        backgroundColor = Color("#333333")
        color = Color.white
    }
    header {
        display = Display.flex
        justifyContent = JustifyContent.spaceBetween
        alignItems = Align.center
        margin(0.px)
        padding(15.px)
        backgroundColor = Color("#222222")
    }
    mainTag {
        height = 100.pct
        padding(15.px)
    }
    a {
        margin(5.px)
        color = Color.white
        textDecoration = TextDecoration(setOf(TextDecorationLine.underline))
    }
}

/*fun Application.moduleSample() {
    configureSecurity()
    configureHTTP()
    configureSerialization()
    configureTemplating()
    configureSockets()
    configureRouting()
}*/
