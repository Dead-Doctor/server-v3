package de.deaddoctor

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.html.*
import io.ktor.server.plugins.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.css.CSSBuilder
import kotlinx.html.*
import kotlin.reflect.KProperty

interface Module {
    fun path(): String
    fun Route.route()

    companion object {
        fun Routing.enable(module: Module) {
            module.enable(this)
        }
    }

    fun enable(route: Route) {
        route.route(path()) {
            route()
        }
    }
}

val ApplicationRequest.url: Url
    get() = URLBuilder().apply {
        val origin = call.request.origin

        protocol = URLProtocol.byName[origin.scheme] ?: URLProtocol(origin.scheme, 0)
        host = origin.serverHost.substringBefore(":")
        port = origin.serverPort
        encodedPath = call.request.path()
        parameters.appendAll(call.request.queryParameters)

    }.build()

val Url.clean: String
    get() = "${protocolWithAuthority}${encodedPath}"

suspend fun ApplicationCall.respondPage(title: String, body: PageLayout.() -> Unit) {
    val account = getAccount()
    val uri = request.uri
    respondHtmlTemplate(PageLayout(account, uri, title), HttpStatusCode.OK, body)
}

class CSSRules(rules: CSSBuilder.() -> Unit) {
    private val styles = CSSBuilder().apply(rules).toString()
    operator fun getValue(thisRef: Any?, property: KProperty<*>) = CSSResource(property.name, styles)
}

class CSSFile {
    operator fun getValue(thisRef: Any?, property: KProperty<*>) = CSSResource(property.name)
}

class CSSResource(private val name: String, private val styles: String? = null) {
    companion object {
        fun Route.getStyles(cssResource: CSSResource) {
            if (cssResource.styles == null) throw IllegalStateException("Tried to setup get route for static css resource.")
            get("${cssResource.name}.css") {
                call.respondCss(cssResource.styles)
            }
        }

        fun FlowOrMetaDataOrPhrasingContent.addStyles(cssResource: CSSResource, url: Url? = null) {
            val location = url?.clean ?: ""
            link(rel = "stylesheet", href = "$location/${cssResource.name}.css", type = "text/css")
        }
    }
}

suspend fun ApplicationCall.respondCss(css: String) {
    this.respondText(css, ContentType.Text.CSS)
}

fun FlowOrMetaDataOrPhrasingContent.addScript(name: String) {
    script { src = "/${name}.js"; type = "module"; defer = true }
}