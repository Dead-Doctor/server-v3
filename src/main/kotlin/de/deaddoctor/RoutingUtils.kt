package de.deaddoctor

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.html.*
import io.ktor.server.plugins.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.css.CSSBuilder
import kotlinx.html.FlowOrMetaDataOrPhrasingContent
import kotlinx.html.link
import kotlinx.html.script
import kotlinx.html.unsafe
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromStream
import kotlinx.serialization.json.encodeToStream
import java.io.File
import java.io.InputStream
import java.io.OutputStream
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

suspend fun ApplicationCall.respondPage(title: String? = null, body: PageLayout.() -> Unit) {
    respondHtmlTemplate(PageLayout(user, request.uri, title), HttpStatusCode.OK, body)
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

@OptIn(ExperimentalSerializationApi::class)
inline fun <reified T> Json.decodeFromResource(stream: InputStream): T = stream.use(::decodeFromStream)

@OptIn(ExperimentalSerializationApi::class)
inline fun <reified T> Json.encodeToResource(value: T, stream: OutputStream) = stream.use { encodeToStream(value, it) }

object ViteBuild {
    private var manifest = parseManifest()

    private fun parseManifest() = Json.decodeFromResource<MutableMap<String, ManifestChunk>>(
        if (!server.application.developmentMode) javaClass.getResourceAsStream("/$manifestPath")!!
        else File(manifestPath).inputStream()
    )

    @Serializable
    data class ManifestChunk(
        val file: String,
        val name: String? = null,
        val src: String? = null,
        val isEntry: Boolean = false,
        val imports: List<String> = emptyList(),
        val css: List<String> = emptyList()
    )

    fun FlowOrMetaDataOrPhrasingContent.addScript(name: String) {
        if (server.application.developmentMode) manifest = parseManifest()
        //TODO: watch manifest.json for file changes. then reload and maybe also notify client to refresh
        val entry = manifest["scripts/$name.ts"] ?: throw IllegalArgumentException("Unknown script with name: $name (scripts/$name.ts)")
        linkCss(entry.css)
        script { type = "module"; src = "/${entry.file}"; }
        for (imported in entry.imports) {
            val chunk = manifest[imported]!!
            linkCss(chunk.css)
            link { rel = "modulepreload"; href = "/${chunk.file}" }
        }
    }
    
    private fun FlowOrMetaDataOrPhrasingContent.linkCss(css: List<String>) {
        for (file in css) {
            link { rel = "stylesheet"; href = "/$file" }
        }
    }
}

val dataEncoder = Json
inline fun <reified T> FlowOrMetaDataOrPhrasingContent.addData(id: String, data: T) {
    script {
        type = "application/json"
        attributes["id"] = "data-$id"
        unsafe {
            +dataEncoder.encodeToString(data)
        }
    }
}